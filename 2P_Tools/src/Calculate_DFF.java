import ij.measure.ResultsTable;
import ij.plugin.MontageMaker;
import ij.plugin.PlugIn;
import ij.process.*;
import ij.gui.*;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
//import ij.plugin.frame.RoiManager;
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
//import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import ij.text.TextWindow;


/**
 * This class implements a Plugin used to calculate Delta F / F for a given image stack (time series)
 * 
 * Some code for data analysis adapted from Fritjof Helmchens Macro Files
 * Brain Research Institute, University of Zurich
 * 
 * @author Alexander van der Bourg, Brain Research Institute Zurich
 * @version 0.1
 */

public class Calculate_DFF implements PlugIn {

	private ImagePlus stackImg;
	private boolean gaussFlag;
	private boolean imgStabilize;
	private boolean deleteSlice;
	public static int defaultImg1=0;
	private String workDir;
	private ImageStack mosaicStack;
	RoiManager roiwin;
	Font f = new Font("dialog",Font.BOLD,12);

	
	public void renameROIs(){
		RoiManager rm = RoiManager.getInstance();
		int rCount = rm.getCount();
		for (int i=0; i<rCount; i++){
			rm.select(i);
			if(i<=9){
				rm.runCommand("Rename", "0"+Integer.toString(i));
			}
			else {
				rm.runCommand("Rename", Integer.toString(i));
			}
		}
	}
	
	
	//Execute plugin procedures
	@Override
	public void run(String arg) {
		
		final int[] idList = WindowManager.getIDList();
		 
		 if (idList == null){
			 IJ.error("At least one image has to be open!");
			 return;
		 }
		 
		 final String[] imgList = new String[ idList.length];
		 for (int i=0; i<idList.length; ++i){
			 imgList[i] = WindowManager.getImage(idList[i]).getTitle();
		 }
		 if (defaultImg1 >= imgList.length){
			 defaultImg1 =0;
		 }

	//final String stackString = "Recorded 2-Photon time series";
	final String ROIString = "ROIs in .zip-file";

	 
	
	//ROI Panel
	Panel ROIFlowPanel = new Panel(new FlowLayout());
	Panel ROIButtonPanel = new Panel (new GridLayout(1,1));
	Panel ROITextPanel = new Panel(new GridLayout(1,1));
	final Button loadROIButton = new Button("Load ROIs");
	final TextField ROIField = new TextField(ROIString, 30);

	
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

	
	ROIButtonPanel.add(loadROIButton);
	ROITextPanel.add(ROIField);
	ROIFlowPanel.add(ROIButtonPanel);
	ROIFlowPanel.add(ROITextPanel);

	// create Dialog window with default values
	GenericDialog gd = new GenericDialog("Calculate dFF Tool", IJ.getInstance());
	gd.addChoice("Image_Stack:", imgList, imgList[ defaultImg1 ] );
	//gd.addPanel(flowPanel);
	gd.addNumericField("DFF_Minimum (%):", 1, 1);
  	gd.addNumericField("DFF_Maximum (%):", 7,1);
	gd.addNumericField("Start for F0:", 5,1);
	gd.addNumericField("End for F0:", 20,1);
	gd.addNumericField("Gauss_radius:", 2,1);
	//gd.addNumericField("Kalman bias:", 0.8,1);
	gd.addCheckbox("Image_Stabilization", false);
	//gd.addCheckbox("Kalman?", false);
	gd.addCheckbox("Gauss_Filter", false);
	gd.addCheckbox("Delete_first_slice", false);
	gd.addMessage("Plot and save transients for a ROI set (optional):");
	gd.addPanel(ROIFlowPanel);
	gd.showDialog();

	
	if (gd.wasCanceled())
		return;	
	
	int dffmin = (int)gd.getNextNumber();
	int dffmax = (int)gd.getNextNumber();
	int startf0 = (int)gd.getNextNumber();
	int endf0 = (int)gd.getNextNumber();
	int gaussrad = (int)gd.getNextNumber();
	//kalmanbias = (int)gd.getNextNumber();
	imgStabilize = gd.getNextBoolean();
	//kalmanFlag = gd.getNextBoolean();
	gaussFlag = gd.getNextBoolean();
	deleteSlice = gd.getNextBoolean();
	stackImg = WindowManager.getImage( idList[ defaultImg1 = gd.getNextChoiceIndex() ] );
	workDir = stackImg.getOriginalFileInfo().directory;


	
	//Delete first slice in stack:
	if (deleteSlice == true){
		IJ.run(stackImg, "Delete Slice", "");
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
		stackImg.show();
		IJ.run(stackImg, "Image Stabilizer", "transformation=Translation maximum_pyramid_levels=1 " +
				"template_update_coefficient=0.90 maximum_iterations=200 error_tolerance=0.0000001 " +
				"log_transformation_coefficients");
		stackImg.hide();
	}
	
	ImagePlus showCase = stackImg.duplicate();
	
	//TODO: Kalman stack filter ist not stable in Fiji
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
	
	//Duplicate stack:
	ImagePlus duplicateImg = stackImg.duplicate();

	//Apply Gaussian Filter on duplicate image
	if (gaussFlag==true) {
		//duplicateYFP.show();
		IJ.run(duplicateImg, "Gaussian Blur...", "radius="+gaussrad+" stack");
		//duplicateYFP.hide();
	}

	// Background subtraction on image stack
	ImageStatistics istats = duplicateImg.getStatistics();
	int minVal = (int) istats.min;
	int maxVal = (int) istats.max;
	IJ.run(duplicateImg, "Subtract...", "value="+minVal+" stack");
	
	//Calculate Average
	IJ.run(duplicateImg, "Z Project...", "start=1 stop="+duplicateImg.getImageStackSize()+ " projection=[Average Intensity]");
	ImagePlus averageImg = WindowManager.getImage("AVG_"+duplicateImg.getTitle());
	averageImg.setTitle("Average of Stack");
	
	//Calculate F0
	IJ.run(duplicateImg, "Z Project...", "start="+startf0+" stop="+endf0+ " projection=[Average Intensity]");
	ImagePlus f_zero = WindowManager.getImage("AVG_"+duplicateImg.getTitle());
	f_zero.setTitle("F0");
	
	//Calculate DF
	ImageCalculator ic = new ImageCalculator();
	ImagePlus deltaF = ic.run("Subtract create 32-bit stack", duplicateImg, f_zero);
	deltaF.setTitle("DeltaF");
	
	//Calculate DFF
	ic = new ImageCalculator();
	ImagePlus deltaFF = ic.run("Divide create 32-bit stack", deltaF, f_zero);
	deltaFF.setTitle("DeltaF/F");
	
	// Express as relative change in percentage
	IJ.run(deltaFF, "Multiply...", "stack value=100");
	
	//Close all remaining images
	f_zero.changes = false;
	f_zero.close();
	deltaF.changes = false;
	deltaF.close();
	
	//Prepare the Average weighted dFF
	ImageStatistics istatsAvg = averageImg.getStatistics();
	int minAvg = (int) istatsAvg.min;
	int maxAvg = (int) istatsAvg.max;
	ImagePlus averageDup = averageImg.duplicate();
	IJ.run(averageDup, "Subtract...", "value="+minAvg);
	ic = new ImageCalculator();
	ImagePlus deltaFFW = ic.run("Multiply create 32-bit stack", deltaFF, averageDup);
	IJ.run(deltaFFW, "Divide...", "value="+maxAvg+" stack");
	IJ.setMinAndMax(deltaFFW, dffmin, dffmax);
	ImagePlus deltaFFWDuplicate = deltaFFW.duplicate();
	deltaFFWDuplicate.setTitle("dFFDup");
	
	
	//Create an final result images to show user
	IJ.run(duplicateImg, "Conversions...", "scale");
	IJ.setMinAndMax(duplicateImg, minVal, maxVal);
	IJ.run(duplicateImg, "8-bit", "");
	duplicateImg.setTitle("Channel Average");
	//channelAvg.hide();
	//deltaRRWDuplicate.show();
	IJ.run(deltaFFWDuplicate, "8-bit", "");
	//deltaRRWDuplicate.hide();
	
	//Create merged view
	duplicateImg.show();
	deltaFFWDuplicate.show();
	IJ.run(showCase, "8-bit", "");
	showCase.setTitle("Channel");
	showCase.show();
	IJ.run("Merge Image Stacks", "gray=[Channel] red=dFFDup green=*None* blue=*None* keep");
	
	
	//Close temporary images
	deltaFFWDuplicate.changes = false;
	deltaFFWDuplicate.close();
	
	duplicateImg.changes = false;
	duplicateImg.close();
	stackImg.show();
	showCase.changes = false;
	showCase.close();
	//Recalibrate deltaFF histogram
	ImageStatistics deltaFstats = deltaFF.getStatistics();
	int minFF = (int)deltaFstats.min;
	int maxFF = (int)deltaFstats.max;
	IJ.setMinAndMax(deltaFF, minFF, maxFF);
	deltaFF.show();
	averageImg.close();
	
	//Only generate a plot if the ROI-Manager instance is not empty
	if (RoiManager.getInstance() != null){
		//Create a new folder where the results are stored
		String saveDir = workDir+"/dFF_"+stackImg.getShortTitle();
		File resultFolder = new File(saveDir);
		resultFolder.mkdir();
		//Rename attempt
		//renameROIs();
		RoiManager rm = RoiManager.getInstance();
		Roi [] translatedROIs = rm.getRoisAsArray();
		int rCount = rm.getCount();
		for( int i=0; i<rCount; i++){
			deltaFF.setRoi(translatedROIs[i]);
			IJ.run(deltaFF, "Plot Z-axis Profile", "");
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