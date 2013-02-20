import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.measure.ResultsTable;
import ij.plugin.ImageCalculator;
import ij.plugin.MontageMaker;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.text.TextWindow;

import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * This class implements a Plugin used to register a high resolution two-photon
 * image with a low resolution image stack (two-photon time series) and
 * transforms ROIs defined in the high-res stack accordingly. In a second stage,
 * the data can be analyzed.
 * 
 * 
 * @author Alexander van der Bourg, Brain Research Institute Zurich
 * @version 0.3
 */

public class ROI_Transformation implements PlugIn {
	private ImagePlus lowResStack;
	private ImagePlus hiResImg;
	private RoiManager hiResRois;
	private ImageStack stack;
	private ImageStack mosaicStack;
	public static int defaultImg1 = 0;
	public static int defaultImg2 = 1;
	private ImagePlus lowResOriginal;
	private static boolean chkItem;
	private static boolean transdff;
	private static boolean chkcalc;
	private boolean imgStabilize;
	private Roi Resmerged;
	private String workDir;
	public String[] operators = { "Translation (2d)", "Affine (2d)",
			"Rigid (2d)" };
	public static int operator;
	Font f = new Font("dialog", Font.BOLD, 12);
	RoiManager roiwin;
	public int detSize;
	public int nOfN;
	public ImagePlus finalImage;
	public ImagePlus channel1;
	public ImagePlus transdFF;
	public String resourcePath;
	private boolean deleteSlice;

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

	public static Roi mergeRois(Roi[] RoiList) {
		/**
		 * This method returns a merged Roi (ShapeRoi) from a ROI array
		 * 
		 * @param RoiList
		 *            The ROI Array that has to be merged
		 * @return mergedROIs A ShapeROI is returned
		 */

		ShapeRoi mergedROIs;
		// storing variable
		ShapeRoi aL;
		mergedROIs = new ShapeRoi(RoiList[0]);
		// Iterate over all of them and combine them into one
		for (int i = 1; i < RoiList.length; ++i) {
			aL = new ShapeRoi(RoiList[i]);
			// Casting single Rois to merged ROIs
			mergedROIs = new ShapeRoi(mergedROIs);
			mergedROIs = mergedROIs.or(aL);
		}
		return mergedROIs;

	}

	// Loads a text file from within a JAR file using getResourceAsStream().
	String getText(String path) {
		String text = "";
		try {
			// get the text resource as a stream
			InputStream is = getClass().getResourceAsStream(path);
			if (is == null) {
				IJ.showMessage("JAR Loader", "File not found in JAR at " + path);
				return "";
			}
			InputStreamReader isr = new InputStreamReader(is);
			StringBuffer sb = new StringBuffer();
			char[] b = new char[8192];
			int n;
			// read a block and append any characters
			while ((n = isr.read(b)) > 0)
				sb.append(b, 0, n);
			// display the text in a TextWindow
			text = sb.toString();
		} catch (IOException e) {
			String msg = e.getMessage();
			if (msg == null || msg.equals(""))
				msg = "" + e;
			IJ.showMessage("JAR Loader", msg);
		}
		return text;
	}

	// Execute PlugIn procedures.
	@Override
	public void run(String arg) {
		if (IJ.versionLessThan("1.34p"))
			return;

		final String ROIString = "ROIs in .zip format";

		// Panel to load images and ROI
		Panel flowPanel = new Panel(new FlowLayout());
		Panel myLoadPanel = new Panel(new GridLayout(1, 1));
		final Button loadROIButton = new Button("Load ROIs");
		Panel myTextPanel = new Panel(new GridLayout(1, 1));
		final TextField ROIField = new TextField(ROIString, 30);

		loadROIButton.addActionListener(new ActionListener() {
			@Override
			@SuppressWarnings("static-access")
			public void actionPerformed(ActionEvent e) {
				IJ.showStatus("loading image stack");
				// close all previous RoiManagers
				if (RoiManager.getInstance() != null) {
					RoiManager rm = RoiManager.getInstance();
					rm.close();
				}
				// Create new one
				roiwin = new RoiManager();
				IJ.open();
				hiResRois = roiwin.getInstance();
				int count = roiwin.getCount();
				String scount = Integer.toString(count);
				ROIField.setText(scount + " ROIs loaded");
			}
		});

		myTextPanel.add(ROIField);
		myLoadPanel.add(loadROIButton);
		flowPanel.add(myLoadPanel);
		flowPanel.add(myTextPanel);

		final int[] idList = WindowManager.getIDList();

		if (idList == null || idList.length < 2) {
			IJ.error("At least one high-resolution image and a stack have to be open!");
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

		// Generate dialog box
		GenericDialog gd = new GenericDialog(
				"ROI Transformation Tool", IJ.getInstance());
		gd.addChoice("High_Resolution Image", imgList, imgList[defaultImg1]);
		gd.addChoice("Time_Series (to register)", imgList, imgList[defaultImg2]);
		gd.addPanel(flowPanel);
		gd.addMessage("Image Registration:", f);
		gd.addCheckbox("Perform_Registration", chkItem);
		gd.addChoice("Registration_Method:", operators, operators[operator]);
		gd.addNumericField("Approx_size_of_detection (px):", 5, 1);
		gd.addNumericField("Number_of_neighbors:", 5, 1);
		gd.addCheckbox("Image_Stabilization", imgStabilize);
		gd.addCheckbox("Delete_first_slice?", deleteSlice);
		gd.addMessage("Data analysis:", f);
		// gd.addCheckbox("Create dF/F of original stack", chkdff);
		gd.addCheckbox("Create dF/F of registered stack", transdff);
		gd.addCheckbox("Save dF/F analysis of ROIs to folder", chkcalc);
		gd.showDialog();
		String resourcePath = "/resources/";

		if (gd.wasCanceled())
			return;

		// Retrieve Images
		hiResImg = WindowManager.getImage(idList[defaultImg1 = gd
				.getNextChoiceIndex()]);
		if (hiResImg.getBitDepth() != 8) {
			hiResImg.show();
			IJ.run(hiResImg, "8-bit", "");
			hiResImg.hide();
		}

		lowResStack = WindowManager.getImage(idList[defaultImg2 = gd
				.getNextChoiceIndex()]);
		lowResOriginal = lowResStack.duplicate();
		if (lowResStack.getBitDepth() != 8) {
			lowResStack.show();
			IJ.run(lowResStack, "8-bit", "");
			lowResStack.hide();
		}
		// Image Registration
		chkItem = gd.getNextBoolean();
		// Image Stabilization
		imgStabilize = gd.getNextBoolean();
		// Delete first slice
		deleteSlice = gd.getNextBoolean();
		// Create DFF of translated stack
		transdff = gd.getNextBoolean();
		// Create plot on DFF
		chkcalc = gd.getNextBoolean();
		// get detection size
		detSize = (int) gd.getNextNumber();
		// Number of nearest neighbors
		nOfN = (int) gd.getNextNumber();
		// get registration type
		String regType = gd.getNextChoice();

		
		// Working directory
		workDir = hiResImg.getOriginalFileInfo().directory;

		// Average pixels the lowResStack
		stack = lowResStack.getStack();

		// Check if hiResRois have been added:
		if (hiResRois == null) {
			IJ.error("No ROIs have been loaded!");
			return;
		}

		// set transDff to true for plotting.
		if (chkcalc == true) {
			if (transdff == false) {
				transdff = true;
			}
		}

		// Delete first slice in stack:
		if (deleteSlice == true) {
			IJ.run(lowResStack, "Delete Slice", "");
			// stackImg.getStack().deleteSlice(1);
			lowResStack.hide();
		}

		// Stabilize Source image stack (movie)
		if (imgStabilize == true) {
			lowResStack.show();
			IJ.run(lowResStack,
					"Image Stabilizer",
					"transformation=Translation maximum_pyramid_levels=1 "
							+ "template_update_coefficient=0.90 maximum_iterations=200 error_tolerance=0.0000001 "
							+ "log_transformation_coefficients");
			lowResStack.hide();
		}

		// First step: Calculate average pixel intensity image
		int[] sum;
		// takes pixels of one slice
		byte[] pixels;
		int dimension = stack.getWidth() * stack.getHeight();
		sum = new int[dimension];
		IJ.showProgress(0, 100);
		// get the pixels of each slice in the stack
		for (int i = 1; i <= stack.getSize(); i++) {
			pixels = (byte[]) stack.getPixels(i);
			// add the value of each pixel an the corresponding position of the
			// sum array
			for (int j = 0; j < dimension; j++) {
				sum[j] += 0xff & pixels[j];
			}
		}
		byte[] average = new byte[dimension];
		// divide each entry by the number of slices
		for (int j = 0; j < dimension; j++) {
			average[j] = (byte) ((sum[j] / stack.getSize()) & 0xff);
		}

		// Create new averaged image
		ImageProcessor bp;
		bp = new ByteProcessor(stack.getWidth(), stack.getHeight(), average);
		ImagePlus result = new ImagePlus("Average", bp);
		result.show();
		// Adapt lowRes image do HiRes Size
		int hRwidth = hiResImg.getWidth();
		int hRheight = hiResImg.getHeight();

		// If height != width, the proportions of the averaged image are still
		// correct, because of constrained ratio command
		IJ.run(result, "Size...", "width=" + Integer.toString(hRwidth)
				+ " height=" + Integer.toString(hRheight)
				+ " constrain interpolation=None");

		IJ.showStatus("Created average image");
		// Get ROIs as an array
		Roi[] roiArray;
		roiArray = hiResRois.getRoisAsArray();
		// Merge the ROIs
		Roi merged = mergeRois(roiArray);

		// If checkbox is ticked, perform the image registration and translation
		// of the ROIs
		IJ.showStatus("ROIs merged");
		if (chkItem == true) {
			hiResImg.show();
			IJ.showStatus("Step 1/3: Register source to averaged stack image");
			IJ.showProgress(25, 100);
			// Define parameters to large input string for Descriptor-based
			// registration plugin
			IJ.run("Descriptor-based registration (2d/3d)",
					"first_image=Average second_image="
							+ hiResImg.getTitle()
							+ " brightness_of=Medium approximate_size=["
							+ detSize
							+ " px]  type_of_detections=[Minima & Maxima] transformation_model=["
							+ regType
							+ "] images_pre-alignemnt=[Approxmiately aligned] number_of_neighbors="
							+ nOfN
							+ " redundancy=1 significance=3 allowed_error_for_ransac=5 choose_registration_channel_for_image_1=1 choose_registration_channel_for_image_2=1 create_overlayed");
			ImagePlus regImg = WindowManager.getImage("overlay Average ... "
					+ hiResImg.getTitle());
			// Inform the user if the registration failed and terminate:
			if (regImg == null) {
				IJ.showMessage("No Registration found. Change parameters!");
				result.changes = false;
				result.close();
				hiResImg.changes = false;
				hiResImg.close();
				return;
			}

			regImg.setTitle("Registration of source and target");
			IJ.showProgress(30, 100);
			// hiResImg.show();
			hiResImg.setRoi(merged);
			IJ.run(hiResImg, "Create Mask", "");
			ImagePlus mask = WindowManager.getImage("Mask");
			///
			mask.show();
			result.show();

			// Step 2: Find transformation matrix from source -> low res average
			IJ.showStatus("Step 2/3: translating mask to overlap space");
			IJ.showProgress(40, 100);
			IJ.run("Descriptor-based registration (2d/3d)",
					"first_image=Average second_image=Mask reapply");

			ImagePlus step1 = WindowManager
					.getImage("overlay Average ... Mask");
			IJ.run(step1, "Split Channels", "");
			ImagePlus transM = WindowManager
					.getImage("C2-overlay Average ... Mask");
			IJ.run(transM, "Select All", "");
			// IJ.run(transM, "Specify...", "x=0");
			channel1 = WindowManager.getImage("C1-overlay Average ... Mask");
			channel1.hide();

			// Step 3: tranform based on alignment before
			IJ.showStatus("Step 3/3: translating mask to original stack space");
			transM.setTitle("translated_mask");
			
			transM.show();
			IJ.run(transM, "Convert to Mask", "");

			/*
			 * Transformation of lowRes Image stackStep 1: do first
			 * transformation, use image dimensions of transformed image to
			 * create new stackStep 2: iterate over all images in stack, by
			 * reapplying registration.Run Step 2 in batch mode (use a macro and
			 * assign a thread)
			 */
			ImageStack lowResDuplicate = lowResOriginal.getStack();
			int stackSize = lowResDuplicate.getSize();
			ImageProcessor stackSlice;
			// get first image in stack
			stackSlice = lowResDuplicate.getProcessor(1);
			ImagePlus sliceImg = new ImagePlus("slice1", stackSlice);
			sliceImg.show();
			///
			//sliceImg.hide();

			// resize image to high resolution dimensions
			IJ.run(sliceImg,
					"Size...",
					"width=" + Integer.toString(hiResImg.getWidth())
							+ " height="
							+ Integer.toString(hiResImg.getHeight())
							+ " depth=" + Integer.toString(stackSize)
							+ " constrain interpolation=None");
			// Reapply registration performed on average and high res source
			IJ.run("Descriptor-based registration (2d/3d)",
					"first_image=slice1 second_image=" + hiResImg.getTitle()
							+ " reapply");

			ImagePlus slices11 = WindowManager.getImage("overlay slice1 ... "
					+ hiResImg.getTitle());
			IJ.run(slices11, "Split Channels", "");
			sliceImg.changes = false;
			sliceImg.close();
			ImagePlus transStackSlice = WindowManager
					.getImage("C1-overlay slice1 ... " + hiResImg.getTitle());

			// Create the new translated stack
			ImageStack newStack = new ImageStack(transStackSlice.getWidth(),
					transStackSlice.getHeight());
			newStack.addSlice("1", transStackSlice.getChannelProcessor());
			ImagePlus waste1 = WindowManager.getImage("C2-overlay slice1 ... "
					+ hiResImg.getTitle());
			waste1.changes = false;
			waste1.close();
			transStackSlice.changes = false;
			transStackSlice.close();
			IJ.showStatus("Registering image stack");
			lowResOriginal.show();
			hiResImg.show();

			/*
			 * THREAD BASED REGISTRATION OF IMAGE STACKNote: The only possible
			 * way to hide images generated by the descriptor based analysis
			 */
			Thread thread = Thread.currentThread();
			thread.setName("Run$_create_image");
			Macro.setOptions(thread, "img_name1='" + lowResOriginal.getTitle()
					+ "' img_name2='" + hiResImg.getTitle() + "'");
			// IJ.runMacroFile("translate",
			// "to_register="+lowResOriginal.getTitle()+" reference="+hiResImg.getTitle()+"'");
			// IJ.runMacroFile("translate", "");
			IJ.runMacro(getText(resourcePath + "translate.ijm"),
					"to_register=" + lowResOriginal.getTitle() + " reference="
							+ hiResImg.getTitle() + "'");
			Macro.setOptions(thread, null);

			// Transform Rois to stack configuration
			IJ.showStatus("Transforming ROIs to stack");
			IJ.showProgress(80, 100);
			roiwin.runCommand("Select All");
			roiwin.runCommand("Delete");
			IJ.run(transM, "Analyze Particles...",
					"size=0-Infinity circularity=0.0-1.00 show=Nothing include add");
			ImagePlus resizeImg;
			resizeImg = IJ.createImage("Resize placeholder", "8-bit White",
					hRwidth, hRheight, 1);

			// Combine the ROIs again so we can create a mask
			Roi[] roiResArray;
			roiResArray = roiwin.getRoisAsArray();

			// Merge the ROIs
			Resmerged = mergeRois(roiResArray);
			resizeImg.setRoi(Resmerged);
			// the average image is not shown for now.
			// result.setRoi(Resmerged);
			channel1.setRoi(Resmerged);
			IJ.showProgress(100, 100);
			IJ.showStatus("Registration complete");

			channel1.setTitle("ROI on registered average");
			// Reset LUT to Gray scales
			// channel1.show();
			IJ.run(channel1, "Grays", "");
			transM.close();
			IJ.run(finalImage, "Grays", "");
			if (transdff == false) {
				result.changes = false;
				result.close();
			}
			mask.close();
			hiResImg.setTitle("ROI on original");

			ImagePlus fastStack = WindowManager.getImage("Translated stack");
			fastStack.setRoi(Resmerged);
			// Close average
			result.changes = false;
			result.close();
		}

		// create Delta F/F of translated stack
		if (transdff == true) {
			// Step 1: Subtract the average image from image stack:
			ImageCalculator ic = new ImageCalculator();
			ImagePlus finalImage = WindowManager.getImage("Translated stack");
			// Background subtraction:
			ImageStatistics istats = finalImage.getStatistics();
			int minVal = (int) istats.min;
			// First subtract background:
			IJ.run(finalImage, "Select All", "");
			IJ.run(finalImage, "Subtract...",
					"value=" + Integer.toString(minVal) + " stack");

			// Recalculate Average in easier way than above and in 16bit
			IJ.run(finalImage, "Z Project...",
					"start=1 stop=" + finalImage.getImageStackSize()
							+ " projection=[Average Intensity]");
			ImagePlus transavg_16bit = WindowManager.getImage("AVG_"
					+ finalImage.getTitle());

			IJ.run(channel1, "Select All", "");
			ImagePlus transdF = ic.run("Substract create 32-bit stack",
					finalImage, transavg_16bit);

			ic = new ImageCalculator();
			transdFF = ic.run("Divide create 32-bit stack", transdF,
					transavg_16bit);
			// Express as percentage change:
			IJ.run(transdFF, "Multiply...", "stack value=100");
			IJ.run(transdFF, "Enhance Contrast", "saturated=0.35");
			transdFF.setTitle("dF/F of translated time series");
			transdFF.show();
			transavg_16bit.changes = false;
			transavg_16bit.close();
			result.changes = false;
			result.close();
			// Reset the selection on the showcase images
			channel1.setRoi(Resmerged);
			// enhance images for visual purposes.
			// ImageStatistics finalstats = finalImage.getStatistics();
			// IJ.setMinAndMax(finalImage, (int)finalstats.min,
			// (int)finalstats.max);
			IJ.run(finalImage, "Enhance Contrast", "saturated=0.35");
			finalImage.setRoi(Resmerged);
			transdFF.setRoi(Resmerged);

		}

		if (chkcalc == true) {
			// Create a new folder where the results are stored
			String saveDir = workDir + "/Measurements_"
					+ lowResOriginal.getShortTitle();
			File resultFolder = new File(saveDir);
			resultFolder.mkdir();
			RoiManager rm = RoiManager.getInstance();
			Roi[] translatedROIs = rm.getRoisAsArray();
			int rCount = rm.getCount();
			for (int i = 0; i < rCount; i++) {
				transdFF.setRoi(translatedROIs[i]);
				IJ.run(transdFF, "Plot Z-axis Profile", "");
				// save plot here, because it is the active window instance
				ImagePlus plotImg = WindowManager.getCurrentImage();
				// mosaicStack.addSlice("ROI"+ Integer.toString(i),
				// plotImg.getProcessor());
				IJ.saveAs(plotImg, "Jpeg",
						saveDir + "/ROI" + Integer.toString(i) + ".jpg");
				// ImagePlus plotSlice = IJ.openImage(saveDir+"/ROI"+
				// Integer.toString(i)+".jpg");
				plotImg.changes = false;
				plotImg.close();
				ResultsTable resT = ResultsTable.getResultsTable();
				try {
					resT.saveAs(saveDir + "/ROI" + Integer.toString(i) + ".csv");
				}
				// If we want to save, we have to throw an exception if it fails
				catch (IOException e) {
					IJ.log("Can not find working directory");
				}
				// Close ResultsTable
				TextWindow tw = ResultsTable.getResultsWindow();
				tw.close(false);
			}
			// get first slice and initialize stack with parameters
			ImagePlus initialSlice = IJ.openImage(saveDir + "/ROI"
					+ Integer.toString(0) + ".jpg");
			// Draw the name of the ROI at a nice location
			initialSlice.getChannelProcessor().setFont(f);
			initialSlice.getChannelProcessor().drawString(
					"ROI" + Integer.toString(0), 60, 240);
			mosaicStack = new ImageStack(initialSlice.getWidth(),
					initialSlice.getHeight());
			mosaicStack.addSlice("ROI" + Integer.toString(0),
					initialSlice.getChannelProcessor());
			// iterate over the rest of the stack
			for (int j = 1; j < rCount; j++) {
				ImagePlus plotSlice = IJ.openImage(saveDir + "/ROI"
						+ Integer.toString(j) + ".jpg");
				// Draw the name of the ROI at a nice location
				plotSlice.getChannelProcessor().setFont(f);
				plotSlice.getChannelProcessor().drawString(
						"ROI " + Integer.toString(j), 60, 240);
				mosaicStack.addSlice("ROI" + Integer.toString(j),
						plotSlice.getChannelProcessor());
				plotSlice.changes = false;
				plotSlice.close();
			}
			MontageMaker mn = new MontageMaker();
			ImagePlus mosaicImp = new ImagePlus("ROI Plots", mosaicStack);
			mosaicImp.updateAndDraw();
			// //find smallest square grid configuration for mosaic
			int mosRows = 1 + (int) Math
					.floor(Math.sqrt(mosaicStack.getSize()));
			int mosCols = mosRows - 1;
			while (mosRows * mosCols < mosaicStack.getSize())
				mosCols++;
			mn.makeMontage(mosaicImp, mosRows, mosCols, 1.0, 1,
					mosaicStack.getSize(), 1, 1, false);

		}

	}
}
