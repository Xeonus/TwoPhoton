import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.io.DirectoryChooser;
import ij.plugin.ImageCalculator;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * This class implements a Plugin used to segment neuronal somatas for a given image stack (time series)
 * 
 * Some code for data analysis adapted from Fritjof Helmchens Macro Files
 * Brain Research Institute, University of Zurich
 * 
 * @author Alexander van der Bourg, Brain Research Institute Zurich
 * @version 1.0
 */

public class Series_Segmentation implements PlugIn {

	// Fields for computation
	private ImagePlus srImg;
	private ImagePlus ogbImg;
	private String saveDir;
	private String originalName;
	private static String OSType;
	public static int defaultImg1 = 0;
	public static int defaultImg2 = 1;
	public ImagePlus workingImg;
	Font f = new Font("dialog", Font.BOLD, 12);
	InputStream exeIS;
	OutputStream dest;
	private Process proc;
	private String [] coreDlls; 

	// Checkbox items
	private boolean localContrast;
	private boolean pseudoFlat;
	private boolean stackCheck;

	// Numeric fields
	private int localWidth;
	private double satPerc;
	private int gaussR;
	private InputStream stream;

	// OS Checkup
	public static boolean isWindows() {
		OSType = System.getProperty("os.name");
		return OSType.startsWith("Windows");
	}

	/**
	 * Method to subtract one image from another by calling standard imagej
	 * procedure
	 * 
	 * @param img1
	 *            : source image
	 * @param img2
	 *            : image subtracted from source image
	 */
	public ImagePlus subtractCreate(ImagePlus img1, ImagePlus img2) {
		ImageCalculator ic = new ImageCalculator();
		ImagePlus subImg = ic.run("Subtract create stack", img1, img2);
		//subImg.show();
		return subImg;
	}
	
	public void renameROIs(){
		RoiManager rm = RoiManager.getInstance();
		int rCount = rm.getCount();
		for (int i=0; i<rCount; i++){
			rm.select(i);
			//The first entry should be set at 01
			//if(i==0){
			//	rm.runCommand("Rename", "01");
			//}
			if(i<=9){
				rm.runCommand("Rename", "0"+Integer.toString(i));
			}
			else {
				rm.runCommand("Rename", Integer.toString(i));
			}
		}
	}

	/**
	 * Enhance Local Contrast This method enhances contrast locally either
	 * through local square histograms or through global histogram
	 * 
	 * @param img1
	 *            : Input image
	 * @param width
	 *            : local enhance contrast square
	 * @param stackHist
	 *            : if true: enhance based on local histogram
	 * @param satPercentage
	 *            : Saturation percentage
	 */
	public ImagePlus enhanceLocalContrast(ImagePlus img1, int width,
			double satPercentage, boolean stackHist) {
		// we have to make sure that we do not enhance the contrast over and
		// over again, when stackCheck=true!!
		// duplicate original image
		ImagePlus enhanced = img1.duplicate();
		// ImagePlus enhancedStack;
		int imgWidth = img1.getWidth();
		int imgHeight = img1.getHeight();
		// maximum number of squares
		double nmax = Math.floor(Math.max(imgWidth, imgHeight) / width) + 1;
		// ROI Coordinates
		int xx = 0;
		int yy = 0;
		IJ.log("Enhancing local contrast...");
		for (int i = 0; i < nmax; i++) {
			// IJ.log("Iteration"+i/nmax*100+"% complete");
			for (int j = 0; j < nmax; j++) {
				// Create ROI
				Roi rect = new Roi(xx, yy, width, width);
				enhanced.setRoi(rect);
				// TODO: keep in mind to check with radio buttons if it is
				// stack!
				if (stackHist == true) {
					IJ.run(enhanced, "Enhance Contrast...", "saturated="
							+ satPercentage + " normalize process_all use");
				} else {
					IJ.run(enhanced, "Enhance Contrast...", "saturated="
							+ satPercentage + " normalize process_all");
				}
				xx += width;
			}
			xx = 0;
			yy += width;
		}
		// }
		return enhanced;
	}

	/**
	 * Method to create a pseudo flat field image of the source image Subtract a
	 * gaussian blur image from the source image
	 * 
	 * @param img1
	 * @param gaussR
	 */
	public ImagePlus pseudoFlatField(ImagePlus img1, int gaussR) {
		// Create gaussian blur of source img1
		ImagePlus gaussImg = img1.duplicate();
		IJ.run(gaussImg, "Gaussian Blur...", "radius=" + gaussR + " stack");
		// then subtract that image from img1
		ImagePlus pseudoFlat = subtractCreate(img1, gaussImg);
		gaussImg.changes = false;
		gaussImg.close();
		return pseudoFlat;
	}

	public ImagePlus makeBinary(ImagePlus img) {

		// first convert image to 8bit:
		ImagePlus binaryImg = img.duplicate();
		binaryImg.show();
		IJ.run(binaryImg, "8-bit", "");
		binaryImg.hide();

		byte[] pixels;
		int dimension = img.getWidth() * img.getHeight();
		int[] pixelVals = new int[dimension];

		// get pixels from image
		ImageProcessor ip = binaryImg.getProcessor();
		pixels = (byte[]) ip.getPixels();
		// Iterate over all of them to retrieve int values
		for (int i = 0; i < dimension; i++) {
			pixelVals[i] = 0xff & pixels[i];
		}
		byte[] binVals = new byte[dimension];
		// Iterate over pixels and every pixel >0 is set to 255
		// and convert them to byte types again
		for (int j = 0; j < dimension; j++) {
			if (pixelVals[j] > 0) {
				pixelVals[j] = 255;
				binVals[j] = (byte) (pixelVals[j] & 0xff);
			} else {
				// if value is zero just convert it
				binVals[j] = (byte) (pixelVals[j] & 0xff);
			}
		}

		// now generate binary image
		ImageProcessor bin;
		bin = new ByteProcessor(img.getWidth(), img.getHeight(), binVals);
		ImagePlus binResult = new ImagePlus("Binary Segmentation", bin);
		return binResult;
	}

	// Execute PlugIn Procedures
	@Override
	public void run(String arg) {

		// Check if Plugin runs on Windows.
		if (isWindows() == false) {
			IJ.showMessage("This Plugin is only supported on Windows machines!");
			return;
		}

		final int[] idList = WindowManager.getIDList();

		if (idList == null || idList.length < 2) {
			IJ.error("At least two images have to be opened!");
			return;
		}

		final String[] imgList = new String[idList.length];
		for (int i = 0; i < idList.length; ++i) {
			imgList[i] = WindowManager.getImage(idList[i]).getTitle();
		}
		if (defaultImg1 >= imgList.length || defaultImg2 >= imgList.length) {
			defaultImg1 = 0;
			defaultImg2 = 1;
		}

		// Select output folder
		Panel directoryPanel = new Panel(new FlowLayout());
		Panel myDirectoryPanel = new Panel(new GridLayout(2, 1));
		final Button loadDirectoryButton = new Button("Output Directory");
		Panel directoryTextPanel = new Panel(new GridLayout(2, 1));
		final TextField directoryField = new TextField("Path to save files", 30);
		// directoryField.setText("Output directory path");

		loadDirectoryButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				IJ.showStatus("Choose output Direcotry");
				DirectoryChooser dirC = new DirectoryChooser(
						"Select output path");
				saveDir = dirC.getDirectory();

				directoryField.setText(saveDir);
			}
		});

		// Directory panel
		directoryTextPanel.add(directoryField);
		myDirectoryPanel.add(loadDirectoryButton);
		directoryPanel.add(myDirectoryPanel);
		directoryPanel.add(directoryTextPanel);


		// Generate dialog box
		GenericDialog gd = new GenericDialog(
				"Automatic Cell Detection in 2D Time Series", IJ.getInstance());
		gd.addChoice("OGB channel", imgList, imgList[defaultImg1]);
		gd.addChoice("Sulforhodamine channel", imgList, imgList[defaultImg2]);
		// gd.addPanel(flowPanel);
		// gd.addPanel(radioPanel);
		gd.addMessage("Enhance Contrast for Segmentation:", f);
		gd.addCheckbox("Enhance_Local Contrast", localContrast);
		gd.addNumericField("Local_Square_Length:", 20.0, 1);
		gd.addNumericField("Saturation_Percentage:", 0.4, 1);
		// gd.addMessage("Local Mean Intensity:", f);

		// gd.addMessage("Pseudo-flat Field:", f);
		gd.addCheckbox("Pseudo-flat_Field Correction", pseudoFlat);
		gd.addNumericField("Gauss_Radius:", 20.0, 1);
		gd.addPanel(directoryPanel);
		gd.showDialog();

		if (gd.wasCanceled())
			return;

		//Retrieve values from dialog box
		localContrast = gd.getNextBoolean();
		localWidth = (int) gd.getNextNumber();
		satPerc = gd.getNextNumber();
		pseudoFlat = gd.getNextBoolean();
		gaussR = (int) gd.getNextNumber();

		// retrieve selected Images:
		ogbImg = WindowManager.getImage(idList[defaultImg1 = gd
				.getNextChoiceIndex()]);
		originalName = ogbImg.getShortTitle();
		srImg = WindowManager.getImage(idList[defaultImg2 = gd
				.getNextChoiceIndex()]);

		// 2D segmentation procedures
		workingImg = subtractCreate(ogbImg, srImg);
		// Pseudo Flat Field
		if (pseudoFlat == true) {
			workingImg = pseudoFlatField(workingImg, gaussR);
		}
		// Enhance local contrast
		if (localContrast == true) {
			stackCheck = true;
			workingImg = enhanceLocalContrast(workingImg, localWidth, satPerc,
					stackCheck);
		}
		//If user input not specified, save to default folder Segmentation
		if (saveDir == null) {
			saveDir = ogbImg.getOriginalFileInfo().directory + "Segmentation\\";
			File resultFolder = new File(saveDir);
			resultFolder.mkdir();
			//return;
		}
		
		workingImg.show();
		//IJ.run(workingImg, "Enhance Contrast", "saturated=0.35");
		workingImg.setTitle("TODO_" + originalName);
		IJ.saveAs(workingImg, "Tiff", saveDir + "/TODO_" + originalName
				+ ".tiff");
		IJ.saveAs(workingImg, "Tiff", saveDir + "/enhancedContrast_"
				+ originalName + ".tiff");
		

		/**Extracting exe file from resources/
		 * Generating input and outputstreams
		 * Execute 2dsegment in output folder
		 * Check if segmentation worked and clean up folder
		**/
		IJ.log("Executing gradient flow algorithm");
		
		//Extracting additional core dll files for 2dsegment.exe
		CodeSource src = getClass().getProtectionDomain().getCodeSource();
		List<String> fileList = new ArrayList<String>();
		if (src != null){
			URL jar = src.getLocation();
			try {
				ZipInputStream zip = new ZipInputStream(jar.openStream());
				ZipEntry ze = null;
				while((ze = zip.getNextEntry()) != null){
					String entryName = ze.getName();
					if(entryName.endsWith(".dll")){
						fileList.add(entryName.substring(18));
					}
				}
				coreDlls = fileList.toArray(new String [fileList.size()]);
			
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				IJ.error("Jar location not found!");
			}
			
		}
		
		//Now copy dlls to destination
		for(int i=0; i<fileList.size(); i++){
			InputStream coreDll = getClass().getResourceAsStream("/resources/coreDLL/" +coreDlls[i]);
			
			OutputStream coreDllOut;
			int readBytes;
			byte[] buffer = new byte[4096];
			try {
				coreDllOut = new FileOutputStream(new File(saveDir+"/"
						+ coreDlls[i]));
				while ((readBytes = coreDll.read(buffer)) > 0) {
					coreDllOut.write(buffer, 0, readBytes);
				}
				coreDllOut.flush();
				coreDllOut.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				IJ.error("Can not move core dll files!");
				// return;
			}
			try {
				coreDll.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		
		
		//if either dimension is below 100px we have to use alternative exe file
		if(ogbImg.getHeight() < 100 || ogbImg.getWidth() < 100){
			stream = getClass().getResourceAsStream(
					"/resources/2dSmallSegment.exe");
			IJ.log("Executing segmentation for small images");
		}else{
			
			stream = getClass().getResourceAsStream(
				"/resources/2dsegment.exe");
		}
		

		if (stream == null) {
			IJ.showMessage("Can not find Segmentation Executable!");
			return;
		}
		
		OutputStream resStreamOut;
		int readBytes;
		byte[] buffer = new byte[4096];
		try {
			resStreamOut = new FileOutputStream(new File(saveDir
					+ "/segmentation.exe"));
			while ((readBytes = stream.read(buffer)) > 0) {
				resStreamOut.write(buffer, 0, readBytes);
			}
			resStreamOut.flush();
			resStreamOut.close();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			IJ.error("Can not move executable files!");
			// return;
		}
		try {
			stream.close();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		// Now try to execute the segmentation.exe and give the paths in cmd.exe
		Runtime rt = Runtime.getRuntime();
		
		try {
			
			//Create a bat file and execut the commands.
			//We need to escape the string set to avoid deletion of white space!
			String cmdStrings = "cmd /C " + "\"\""+saveDir
					+ "segmentation.exe\""+" "+ "\""+ saveDir + "TODO_"+originalName+".tif\"\"";
			String batName = "segmenter.bat";
			
			FileWriter fstream = new FileWriter(saveDir+batName, true);
			BufferedWriter out = new BufferedWriter(fstream);
			out.write(cmdStrings);
			out.close();
			
			//Execute bat file
			proc = rt.exec(saveDir + "/segmenter.bat");
			proc.waitFor();
			
			
		} catch (IOException e1) {
			IJ.error("Could not execute segmenter.bat");
			e1.printStackTrace();
		} catch (InterruptedException iex) {
			iex.printStackTrace();
		}

		// Clean up directory and continue processing
		File toDelete = new File(saveDir + "/segmentation.exe");
		boolean success = toDelete.delete();
		if (!success) {
			IJ.error("Could not delete segmentation.exe files");
		}
		//Delete dll Files
		for(int i=0; i<coreDlls.length; i++){
			File dllDelete = new File(saveDir + "/"+coreDlls[i]);
			boolean status = dllDelete.delete();
			if(!status){
				IJ.error("Could not delete core dll files");
			}
			
		}
		//Delete bat file
		File batF = new File (saveDir+"/segmenter.bat");
		if(batF.exists()){
			boolean batDel = batF.delete();
			if(!batDel){
				IJ.error("Could not delete segmenter.bat");
			}
		}
		
		// Inform user where files have been stored
		// TODO: rename files and store rest in a new folder maybe?

		// Extract Regions of interest
		// Make image Binary and count particles, then show it on the averaged
		// image
		File chkSeg = new File(saveDir + "RegionCellNucleiSegGVF_"+"TODO_"+ originalName+ ".tif");
		if(chkSeg.exists()){
			IJ.log("Processing finished");
			IJ.log("Cleaned up data");
			ImagePlus segmented = IJ.openImage(saveDir + "RegionCellNucleiSegGVF_"
				+ "TODO_" + originalName + ".tif");
			ImagePlus binarySegmentation = makeBinary(segmented);
			binarySegmentation.show();
			IJ.run(binarySegmentation, "Analyze Particles...",
				"size=0-Infinity circularity=0.00-1.00 show=Nothing include add");
			
			binarySegmentation.changes = false;
			binarySegmentation.close();
			
			//Rename ROIs
			renameROIs();
			//and show them in the original image
			workingImg.changes = false;
			workingImg.close();
			ogbImg.show();
			IJ.log("RoiSet.zip has been saved to: " + saveDir);
			RoiManager rm = RoiManager.getInstance();
			rm.runCommand("Save", saveDir+originalName+"_RoiSet.zip");
			srImg.hide();
			rm.runCommand("Show All");
			srImg.show();
			ogbImg.show();
				
			
		} else {
			IJ.error("The segmentation has not been performed!");
			return;
		}
		

	}
}
