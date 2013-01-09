


import ij.measure.ResultsTable;
import ij.plugin.MontageMaker;
import ij.plugin.PlugIn;
import ij.process.*;
import ij.gui.*;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
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

import ij.plugin.ImageCalculator;
import ij.plugin.frame.RoiManager;
import ij.text.TextWindow;


/**
 * This plugin is a first version to implement Delta R over R
 * It requires an image stack to be translated. In a future step, this plugin will be included in a general
 * ImageJ Plugin toolbox.
 * 
 * Code is implemented based on Fritjof Helmchens Macros.
 */
public class Calculate_DRR implements PlugIn {
	/**
	 * This method gets called by ImageJ / Fiji.
	 *
	 * @param arg can be specified in plugins.config
	 */

	private ImagePlus stackImgCFP;
	private ImagePlus stackImgYFP;
	//private boolean kalmanFlag;
	private boolean gaussFlag;
	//private int kalmanbias;
	private boolean imgStabilize;
	private boolean deleteSlice;
	private String workDir;
	private ImageStack mosaicStack;
	public static int defaultImg1=0;
	public static int defaultImg2=1;
	RoiManager roiwin;
	Font f = new Font("dialog",Font.BOLD,12);

	
	//Run methods in plugin
	@Override
	public void run(String arg) {
		
		
		
		final int[] idList = WindowManager.getIDList();
		 
			if (idList == null || idList.length < 2 ){
				IJ.error("At least two images have to be open!");
				return;
			}
		 
		final String[] imgList = new String[ idList.length];
		for (int i=0; i<idList.length; ++i){
			 imgList[i] = WindowManager.getImage(idList[i]).getTitle();
		}
		 if (defaultImg1 >= imgList.length || defaultImg2 >= imgList.length ){
			 defaultImg1 =0;
			 defaultImg2 =1;
		 }
		

	//final String stackStringCFP = "Image Stack of CFP channel";
	//final String stackStringYFP = "Image Stack of YFP channel";
	final String ROIString = "ROIs in .zip-file";

	//Panels to load images (GFP and YFP channels)
	//Panel flowPanel = new Panel(new FlowLayout());
	//Panel myLoadPanel = new Panel(new GridLayout(2,1));
	//final Button loadCFPButton = new Button("Load CFP stack");
	//final Button loadYFPButton = new Button ("Load YFP stack");
	//Panel myTextPanel = new Panel(new GridLayout(2,1));
	//final TextField CFPTextField = new TextField(stackStringCFP, 30);
	//final TextField YFPTextField = new TextField(stackStringYFP, 30); 
	
	
	//ROI Panel
	Panel ROIFlowPanel = new Panel(new FlowLayout());
	Panel ROIButtonPanel = new Panel (new GridLayout(1,1));
	Panel ROITextPanel = new Panel(new GridLayout(1,1));
	final Button loadROIButton = new Button("Load ROIs");
	final TextField ROIField = new TextField(ROIString, 30);


	//Load buttons
	/*
	loadCFPButton.addActionListener(new ActionListener() {
	public void actionPerformed(ActionEvent e) {
		IJ.showStatus("loading image stack for CFP");
		IJ.open();
		ImagePlus img1 = IJ.getImage();
		stackImgCFP = img1;
		stackImgCFP.hide();
		CFPTextField.setText(img1.getTitle());
		}
	});


	loadYFPButton.addActionListener(new ActionListener() {
	public void actionPerformed(ActionEvent e) {
		IJ.showStatus("loading image stack for YFP");
		IJ.open();
		ImagePlus img2 = IJ.getImage();
		stackImgYFP = img2;
		stackImgYFP.hide();
		workDir = img2.getOriginalFileInfo().directory;
		YFPTextField.setText(img2.getTitle());
		}
	});
	*/
	
	loadROIButton.addActionListener(new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			IJ.showStatus("loading image stack");
			//close all previous RoiManagers
			if (RoiManager.getInstance() != null){
				RoiManager rm = RoiManager.getInstance();
				rm.close();	 
			}
			//Create new one
			roiwin = new RoiManager();
			IJ.open();
			//imgRois = roiwin.getInstance();
			int count = roiwin.getCount();
			String scount = Integer.toString(count);
			ROIField.setText(scount +" ROIs loaded");
			}
		});



	//myTextPanel.add(CFPTextField);
	//myTextPanel.add(YFPTextField);
	//myLoadPanel.add(loadCFPButton);
	//myLoadPanel.add(loadYFPButton);
	//flowPanel.add(myLoadPanel);
	//flowPanel.add(myTextPanel);		
	
	ROIButtonPanel.add(loadROIButton);
	ROITextPanel.add(ROIField);
	ROIFlowPanel.add(ROIButtonPanel);
	ROIFlowPanel.add(ROITextPanel);

	// create Dialog window with default values
	GenericDialog gd = new GenericDialog("Calculate dRR Tool", IJ.getInstance());
	//gd.addPanel(flowPanel);
    gd.addChoice("CFP Time Series", imgList, imgList[ defaultImg1 ] );        
    gd.addChoice("YFP Time Series", imgList, imgList[ defaultImg2 ] );
	gd.addNumericField("DRR Minimum (%):", 1, 1);
  	gd.addNumericField("DRR Maximum (%):", 7,1);
	gd.addNumericField("Start for R0:", 5,1);
	gd.addNumericField("End for R0:", 20,1);
	gd.addNumericField("Gauss radius:", 2,1);
	//gd.addNumericField("Kalman bias:", 0.8,1);
	gd.addCheckbox("Apply Image Stabilization", false);
	//gd.addCheckbox("Kalman?", false);
	gd.addCheckbox("Apply Gauss Filter", false);
	gd.addCheckbox("Delete first slice?", false);
	gd.addMessage("Plot and save transients for a ROI set (optional):");
	gd.addPanel(ROIFlowPanel);
	gd.showDialog();

	
	if (gd.wasCanceled())
		return;	
	

	int drrmin = (int)gd.getNextNumber();
	int drrmax = (int)gd.getNextNumber();
	int startr0 = (int)gd.getNextNumber();
	int endr0 = (int)gd.getNextNumber();
	int gaussrad = (int)gd.getNextNumber();
	//kalmanbias = (int)gd.getNextNumber();
	imgStabilize = gd.getNextBoolean();
	//kalmanFlag = gd.getNextBoolean();
	gaussFlag = gd.getNextBoolean();
	deleteSlice = gd.getNextBoolean();
	
	//TODO: duplicate original images and open these at end of session again!
	//retrieve selected Images:
	stackImgCFP = WindowManager.getImage( idList[ defaultImg1 = gd.getNextChoiceIndex() ] );
	stackImgYFP = WindowManager.getImage( idList[ defaultImg2 = gd.getNextChoiceIndex() ] ); 
	workDir = stackImgCFP.getOriginalFileInfo().directory;

	
	//Delete first slice in stack:
	if (deleteSlice == true){
		IJ.run(stackImgYFP, "Delete Slice", "");
		IJ.run(stackImgCFP, "Delete Slice", "");
		//stackImg.getStack().deleteSlice(1);
	}
	/*
	 * Apply optional filters: 
	 * Image Stabilizer (found in resources)
	 * Kalman Filter
	 * Gaussian filter
	 */
	
	//Run Image Stabilizer on YFP Image Stack
	if (imgStabilize==true) {
		stackImgYFP.show();
		IJ.run(stackImgYFP, "Image Stabilizer", "transformation=Translation maximum_pyramid_levels=1 " +
				"template_update_coefficient=0.90 maximum_iterations=200 error_tolerance=0.0000001 " +
				"log_transformation_coefficients");
		stackImgYFP.hide();
		//reapply filter on CFP channel
		stackImgCFP.show();
		IJ.run(stackImgCFP, "Image Stabilizer Log Applier", " ");
		stackImgCFP.hide();
	}
	
	//TODO: Kalman stack filter ist not stable in fiji
	//Apply Kalman Filter 
	/*
	if (kalmanFlag==true) {
		//To avoid 
		stackImgYFP.show();
		IJ.run(stackImgYFP, "Kalman Stack Filter", "acquisition_noise=.5 bias="+kalmanbias);
		stackImgYFP.hide();
		//reapply on other channel
		stackImgCFP.show();
		IJ.run(stackImgCFP, "Kalman Stack Filter", "acquisition_noise=.5 bias="+kalmanbias);
	}
	*/
	
	//Duplicate YFP stack:
	ImagePlus duplicateYFP = stackImgYFP.duplicate();

	//Apply Gaussian Filter on duplicate image
	if (gaussFlag==true) {
		//duplicateYFP.show();
		IJ.run(duplicateYFP, "Gaussian Blur...", "radius="+gaussrad+" stack");
		//duplicateYFP.hide();
	}

	// background subtraction Stack 1, here finding the minimum value and taking this as bkg
	ImageStatistics istatsYFP = duplicateYFP.getStatistics();
	int minValYFP = (int) istatsYFP.min;
	//duplicateYFP.show();
	IJ.run(duplicateYFP, "Subtract...", "value="+minValYFP+" stack");
	//duplicateYFP.hide();
	
	//Calculate Average
	IJ.run(duplicateYFP, "Z Project...", "start=1 stop="+duplicateYFP.getImageStackSize()+ " projection=[Average Intensity]");
	ImagePlus averageYFP = WindowManager.getImage("AVG_"+duplicateYFP.getTitle());
	averageYFP.setTitle("Avg");

	
	//duplicate the CFP image and apply a gauss filter on it
	ImagePlus duplicateCFP = stackImgCFP.duplicate();
	if (gaussFlag==true) {
		//duplicateCFP.show();
		IJ.run(duplicateCFP, "Gaussian Blur...", "radius="+gaussrad+" stack");
		//duplicateCFP.hide();
	}
	
	// Background subtraction of second duplicated image (duplicatedCFP)
	ImageStatistics istatsCFP = duplicateCFP.getStatistics();
	int minValCFP = (int) istatsCFP.min;
	//duplicateCFP.show();
	IJ.run(duplicateCFP, "Subtract...", "value="+minValCFP+" stack");
	//duplicateCFP.hide();

	//Calculate DRR
	//First: YFP/CFP
	ImageCalculator ic = new ImageCalculator();
	ImagePlus ratioR = ic.run("Divide create 32-bit stack", duplicateYFP, duplicateCFP);
	ratioR.setTitle("Ratio");
	
	//Calculate R0
	IJ.run(ratioR, "Z Project...", "start="+startr0+" stop="+endr0+ " projection=[Average Intensity]");
	ImagePlus ratio_zero = WindowManager.getImage("AVG_"+ratioR.getTitle());
	ratio_zero.setTitle("R0");
	
	//Calculate DeltaR
	ic = new ImageCalculator();
	ImagePlus deltaR = ic.run("Subtract create 32-bit stack", ratioR, ratio_zero);
	deltaR.setTitle("DeltaR");
	
	//Calculate DeltaR/R
	ic = new ImageCalculator();
	ImagePlus deltaRR = ic.run("Divide create 32-bit stack", deltaR, ratio_zero);
	deltaRR.setTitle("dRR");
	// Express as relative change in percentage
	IJ.run(deltaRR, "Multiply...", "stack value=100");
	
	//Close all remaining images
	ratioR.changes = false;
	ratioR.close();
	ratio_zero.changes = false;
	ratio_zero.close();
	deltaR.changes = false;
	deltaR.close();
	
	//Prepare the Average weighted dRR
	ImageStatistics istatsAvg = averageYFP.getStatistics();
	int minAvg = (int) istatsAvg.min;
	int maxAvg = (int) istatsAvg.max;
	IJ.run(averageYFP, "Subtract...", "value="+minAvg);
	ic = new ImageCalculator();
	ImagePlus deltaRRW = ic.run("Multiply create 32-bit stack", deltaRR, averageYFP);
	IJ.run(deltaRRW, "Divide...", "value="+maxAvg+" stack");
	IJ.setMinAndMax(deltaRRW, drrmin, drrmax);
	ImagePlus deltaRRWDuplicate = deltaRRW.duplicate();
	deltaRRWDuplicate.setTitle("dRRDup");
	
	
	//Create an final result images to show user
	ic = new ImageCalculator();
	ImagePlus channelAvg = ic.run("Average create stack", stackImgYFP, stackImgCFP);
	channelAvg.setTitle("Channel Average");
	ImageStatistics istatschannel = channelAvg.getStatistics();
	int minchannel = (int) istatschannel.min;
	int maxchannel = (int) istatschannel.max;
	IJ.setMinAndMax(channelAvg, minchannel, maxchannel);
	//channelAvg.show();
	IJ.run(channelAvg, "Conversions...", "scale");
	IJ.run(channelAvg, "8-bit", "");
	//channelAvg.hide();
	//deltaRRWDuplicate.show();
	IJ.run(deltaRRWDuplicate, "8-bit", "");
	//deltaRRWDuplicate.hide();
	
	//Create merged view
	channelAvg.show();
	deltaRRWDuplicate.show();
	IJ.run("Merge Image Stacks", "gray=[Channel Average] red=dRRDup green=*None* blue=*None* keep");
	
	
	//Close temporary images
	deltaRRWDuplicate.changes = false;
	deltaRRWDuplicate.close();
	averageYFP.changes = false;
	averageYFP.close();
	
	channelAvg.changes = false;
	channelAvg.close();
	
	deltaRR.setTitle("Delta R/R");
	//Recalibrate deltaFF histogram
	ImageStatistics deltaRstats = deltaRR.getStatistics();
	int minRR = (int)deltaRstats.min;
	int maxRR = (int)deltaRstats.max;
	IJ.setMinAndMax(deltaRR, minRR, maxRR);
	deltaRR.show();
	stackImgCFP.show();
	stackImgYFP.show();
	
	//Only generate a plot if the ROI-Manager instance is not empty
	if (RoiManager.getInstance() != null){
		//Create a new folder where the results are stored
		String saveDir = workDir+"/MeasurementsdRR_"+stackImgYFP.getShortTitle();
		File resultFolder = new File(saveDir);
		resultFolder.mkdir();
		RoiManager rm = RoiManager.getInstance();
		Roi [] translatedROIs = rm.getRoisAsArray();
		int rCount = rm.getCount();
		for( int i=0; i<rCount; i++){
			deltaRR.setRoi(translatedROIs[i]);
			IJ.run(deltaRR, "Plot Z-axis Profile", "");
			//save plot here, because it is the active window instance
			ImagePlus plotImg = WindowManager.getCurrentImage();
			//mosaicStack.addSlice("ROI"+ Integer.toString(i), plotImg.getProcessor());
			IJ.saveAs(plotImg, "Jpeg", saveDir+"/ROI"+ Integer.toString(i)+".jpg");
			//ImagePlus plotSlice = IJ.openImage(saveDir+"/ROI"+ Integer.toString(i)+".jpg");
			plotImg.changes = false;
			plotImg.close();
			ResultsTable resT = ResultsTable.getResultsTable();
			try{
			resT.saveAs(saveDir +"/ROI"+ Integer.toString(i)+".csv");
			}
			//If we want to save, we have to throw an exception if it fails
			catch (IOException e){
				IJ.log("Can not find working directory");
			}
			//Close ResultsTable
			TextWindow tw = ResultsTable.getResultsWindow();
			tw.close(false);
		}
		//get first slice and initialize stack with parameters
		ImagePlus initialSlice = IJ.openImage(saveDir+"/ROI"+ Integer.toString(0)+".jpg");
		//Draw the name of the ROI at a nice location
		initialSlice.getChannelProcessor().setFont(f);
		initialSlice.getChannelProcessor().drawString("ROI"+ Integer.toString(0), 60, 240);
		mosaicStack = new ImageStack(initialSlice.getWidth(), initialSlice.getHeight());
		mosaicStack.addSlice("ROI"+ Integer.toString(0), initialSlice.getChannelProcessor());
		
		//iterate over the rest of the stack
		for(int j=1; j<rCount; j++){
			ImagePlus plotSlice = IJ.openImage(saveDir+"/ROI"+ Integer.toString(j)+".jpg");
			//Draw the name of the ROI at a nice location
			plotSlice.getChannelProcessor().setFont(f);
			plotSlice.getChannelProcessor().drawString("ROI"+ Integer.toString(j), 60, 240);
			mosaicStack.addSlice("ROI"+ Integer.toString(j), plotSlice.getChannelProcessor());
			plotSlice.changes = false;
			plotSlice.close();
		}
		MontageMaker mn = new MontageMaker();
		ImagePlus mosaicImp = new ImagePlus("ROI Plots", mosaicStack);
		mosaicImp.updateAndDraw();
		////find smallest square grid configuration for mosaic
		int mosRows= 1+(int)Math.floor(Math.sqrt(mosaicStack.getSize()));
		int mosCols=mosRows-1;
		while (mosRows*mosCols<mosaicStack.getSize())
			mosCols++;
		mn.makeMontage(mosaicImp, mosRows, mosCols, 1.0, 1, mosaicStack.getSize(), 1, 1, false);
		
		
	}

	}
}
