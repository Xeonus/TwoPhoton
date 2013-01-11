import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
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
	
	public void renameROIs() {
		RoiManager rm = RoiManager.getInstance();
		int rCount = rm.getCount();
		for (int i = 0; i < rCount; i++) {
			rm.select(i);
			if (i <= 9) {
				rm.runCommand("Rename", "0" + Integer.toString(i));
			} else {
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

		/*
		 * //Create GUI elements final String ogbString =
		 * "High Resolution Scan of Imaged Area"; final String srString =
		 * "Low Resolution Image Series";
		 * 
		 * //Panel to load images and ROI Panel flowPanel = new Panel(new
		 * FlowLayout()); Panel myLoadPanel = new Panel(new GridLayout(2,1));
		 * final Button loadOGBButton = new Button("Load OGB Stack"); final
		 * Button loadSRButton = new Button("Load SR Stack"); Panel myTextPanel
		 * = new Panel(new GridLayout(2,1)); final TextField OGBField = new
		 * TextField(ogbString, 30); final TextField SRField = new
		 * TextField(srString, 30);
		 */

		// Removed Loadbuttons for batch processing compability
		/*
		 * //Load buttons loadOGBButton.addActionListener(new ActionListener() {
		 * public void actionPerformed(ActionEvent e) {
		 * IJ.showStatus("Loading OGB Stack"); IJ.open(); ImagePlus img1 =
		 * IJ.getImage(); ogbImg = img1; ogbImg.hide(); //Case: Convert to 8-bit
		 * if (img1.getBitDepth() != 8){ ogbImg.show(); IJ.run("8-bit");
		 * ogbImg.hide(); } OGBField.setText(img1.getTitle()); originalName =
		 * ogbImg.getShortTitle(); } });
		 * 
		 * loadSRButton.addActionListener(new ActionListener() { public void
		 * actionPerformed(ActionEvent e) {
		 * IJ.showStatus("Loading Sulforhodamine 101 Stack"); IJ.open();
		 * ImagePlus img2 = IJ.getImage(); srImg = img2; srImg.hide(); //Case:
		 * Convert to 8-bit if ( img2.getBitDepth() != 8) { srImg.show();
		 * IJ.run("8-bit"); srImg.hide();
		 * 
		 * } SRField.setText(img2.getTitle()); } });
		 */

		// Select output folder
		Panel directoryPanel = new Panel(new FlowLayout());
		Panel myDirectoryPanel = new Panel(new GridLayout(2, 1));
		final Button loadDirectoryButton = new Button("Output Direcotry");
		Panel directoryTextPanel = new Panel(new GridLayout(2, 1));
		final TextField directoryField = new TextField("Output Directory", 30);
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

		/*
		 * Load Buttons panel myTextPanel.add(OGBField);
		 * myTextPanel.add(SRField); myLoadPanel.add(loadOGBButton);
		 * myLoadPanel.add(loadSRButton); flowPanel.add(myLoadPanel);
		 * flowPanel.add(myTextPanel);
		 */

		// Directory panel
		directoryTextPanel.add(directoryField);
		myDirectoryPanel.add(loadDirectoryButton);
		directoryPanel.add(myDirectoryPanel);
		directoryPanel.add(directoryTextPanel);

		// remove radio buttons
		/*
		 * String seriesString = "Cell detection on time series"; String
		 * stack3DString = "Cell detection on 3D stack"; JRadioButton
		 * stackButton = new JRadioButton(stack3DString); JRadioButton
		 * seriesButton = new JRadioButton(seriesString);
		 * stackButton.setSelected(true); ButtonGroup group = new ButtonGroup();
		 * group.add(stackButton); group.add(seriesButton);
		 * 
		 * 
		 * //Add Buttons to new panel below load buttons Panel radioPanel = new
		 * Panel(new GridLayout(0,1)); radioPanel.add(stackButton);
		 * radioPanel.add(seriesButton);
		 */

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
		// save 3D stack in analyze format
		if (saveDir == null) {
			IJ.showMessage("no output directory specified");
			return;
		} else {
			workingImg.show();
			//IJ.run(workingImg, "Enhance Contrast", "saturated=0.35");
			workingImg.setTitle("TODO_" + originalName);
			IJ.saveAs(workingImg, "Tiff", saveDir + "/TODO_" + originalName
					+ ".tiff");
			IJ.saveAs(workingImg, "Tiff", saveDir + "/enhancedContrast_"
					+ originalName + ".tiff");
		}

		/**Extracting exe file from resources/
		 * Generating input and outputstreams
		 * Execute 2dsegment in output folder
		 * Check if segmentation worked and clean up folder
		**/
		IJ.log("Executing gradient flow algorithm");
		
		
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
			// TODO: change to tif
			// wait is neccessary not to call a forked process that waitFor can
			// not handle (waitfor won't).
			proc = rt.exec("cmd /C start /wait " + saveDir
					+ "segmentation.exe " + saveDir + "TODO_"+originalName+".tif");
			proc.waitFor();
		} catch (IOException e1) {
			IJ.error("Could not execute command in cmd.exe");
			e1.printStackTrace();
		} catch (InterruptedException iex) {
			iex.printStackTrace();
		}

		// Clean up directory and continue processing
		IJ.log("Processing finished");
		IJ.log("Cleaning up data");
		File toDelete = new File(saveDir + "/segmentation.exe");
		boolean success = toDelete.delete();
		if (!success) {
			IJ.error("Could not delete segmentation.exe");
		}
		
		// Inform user where files have been stored
		// TODO: rename files and store rest in a new folder maybe?
		IJ.log("Files saved to output folder.");

		// Extract Regions of interest
		// Make image Binary and count particles, then show it on the averaged
		// image
		File chkSeg = new File(saveDir + "RegionCellNucleiSegGVF_"+"TODO_"+ originalName+ ".tif");
		if(chkSeg.exists()){
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
			IJ.log("RoiSet.zip has been saved to output folder");
			RoiManager rm = RoiManager.getInstance();
			rm.runCommand("Save", saveDir+originalName+"_RoiSet.zip");
			srImg.hide();
			rm.runCommand("Show All");
			srImg.show();
			
			
			
			
		} else {
			IJ.error("The segmentation files have not been saved!");
			return;
		}
		
		//Show ROIs on original image
		

	}
}
