


import ij.measure.Calibration;
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
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
import ij.util.Tools;

/**
 * This class implements a Plugin used to calculate Delta R / R for a given image stack (time series)
 * 
 * Some code for data analysis adapted from Fritjof Helmchens Macro Files
 * Brain Research Institute, University of Zurich
 * 
 * @author Alexander van der Bourg, Brain Research Institute Zurich
 * @version 1.0
 */

public class Calculate_DRR implements PlugIn {
	/**
	 * This method gets called by ImageJ / Fiji.
	 *
	 * @param arg can be specified in plugins.config
	 */

	private ImagePlus stackImgCFP;
	private ImagePlus stackImgYFP;
	private boolean gaussFlag;
	private boolean imgStabilize;
	private boolean deleteSlice;
	private String workDir;
	private ImageStack mosaicStack;
	public static int defaultImg1=0;
	public static int defaultImg2=1;
	private ImageStack resultPlots;
	RoiManager roiwin;
	Font f = new Font("dialog",Font.BOLD,12);

	
	
	
	/**
	 * Method to calculate the Z-axis profile of an image with a roi selection
	 * 
	 * @param roi
	 * @param toAnalyze
	 * @param minThreshold
	 * @param maxThreshold 
	 */
	
	float[] getZAxisProfile(Roi roi, ImagePlus toAnalyze, double minThreshold, double maxThreshold) {
		ImageStack stack = toAnalyze.getStack();
		int size = stack.getSize();
		float[] values = new float[size];
		Calibration cal = toAnalyze.getCalibration();
		Analyzer analyzer = new Analyzer(toAnalyze);
		@SuppressWarnings("static-access")
		int measurements = analyzer.getMeasurements();
		//int current = toAnalyze.getCurrentSlice();
		for (int i=1; i<=size; i++) {
			toAnalyze.setSlice(i);
			ImageProcessor ip = stack.getProcessor(i);
			if (minThreshold!=ImageProcessor.NO_THRESHOLD)
				ip.setThreshold(minThreshold,maxThreshold,ImageProcessor.NO_LUT_UPDATE);
			ip.setRoi(roi);
			ImageStatistics stats = ImageStatistics.getStatistics(ip, measurements, cal);
			analyzer.saveResults(stats, roi);			
			//analyzer.displayResults();
			values[i-1] = (float)stats.mean;
		}
		return values;
	}	
	
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
	GenericDialog gd = new GenericDialog("Calculate dRR Tool", IJ.getInstance());
	//gd.addPanel(flowPanel);
    gd.addChoice("CFP Time Series", imgList, imgList[ defaultImg1 ] );        
    gd.addChoice("YFP Time Series", imgList, imgList[ defaultImg2 ] );
	gd.addNumericField("DRR_Minimum (%):", 1, 1);
  	gd.addNumericField("DRR_Maximum (%):", 7,1);
	gd.addNumericField("Start for R0:", 5,1);
	gd.addNumericField("End for R0:", 20,1);
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

	
	stackImgCFP = WindowManager.getImage( idList[ defaultImg1 = gd.getNextChoiceIndex() ] );
	stackImgYFP = WindowManager.getImage( idList[ defaultImg2 = gd.getNextChoiceIndex() ] ); 
	workDir = stackImgCFP.getOriginalFileInfo().directory;

	
	//Delete first slice in stack:
	if (deleteSlice == true){
		IJ.run(stackImgYFP, "Delete Slice", "");
		IJ.run(stackImgCFP, "Delete Slice", "");
		//stackImg.getStack().deleteSlice(1);
	}
	
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
	IJ.run(duplicateYFP, "Subtract...", "value="+minValYFP+" stack");
	duplicateYFP.hide();
	
	//Calculate Average
	IJ.run(duplicateYFP, "Z Project...", "start=1 stop="+duplicateYFP.getImageStackSize()+ " projection=[Average Intensity]");
	ImagePlus averageYFP = WindowManager.getImage("AVG_"+duplicateYFP.getTitle());
	averageYFP.setTitle("Avg");
	averageYFP.hide();

	
	//duplicate the CFP image and apply a gauss filter on it
	ImagePlus duplicateCFP = stackImgCFP.duplicate();
	if (gaussFlag==true) {
		IJ.run(duplicateCFP, "Gaussian Blur...", "radius="+gaussrad+" stack");
	}
	
	// Background subtraction of second duplicated image (duplicatedCFP)
	ImageStatistics istatsCFP = duplicateCFP.getStatistics();
	int minValCFP = (int) istatsCFP.min;
	IJ.run(duplicateCFP, "Subtract...", "value="+minValCFP+" stack");
	duplicateCFP.hide();

	//Calculate DRR
	//First: YFP/CFP
	ImageCalculator ic = new ImageCalculator();
	ImagePlus ratioR = ic.run("Divide create 32-bit stack", duplicateYFP, duplicateCFP);
	ratioR.setTitle("Ratio");
	ratioR.hide();
	
	//Calculate R0
	IJ.run(ratioR, "Z Project...", "start="+startr0+" stop="+endr0+ " projection=[Average Intensity]");
	ImagePlus ratio_zero = WindowManager.getImage("AVG_"+ratioR.getTitle());
	ratio_zero.setTitle("R0");
	ratio_zero.hide();
	
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
	deltaRRWDuplicate.hide();
	
	
	//Create an final result images to show user
	ic = new ImageCalculator();
	ImagePlus channelAvg = ic.run("Average create stack", stackImgYFP, stackImgCFP);
	channelAvg.setTitle("Channel Average");
	ImageStatistics istatschannel = channelAvg.getStatistics();
	int minchannel = (int) istatschannel.min;
	int maxchannel = (int) istatschannel.max;
	IJ.setMinAndMax(channelAvg, minchannel, maxchannel);
	IJ.run(channelAvg, "Conversions...", "scale");
	IJ.run(channelAvg, "8-bit", "");
	IJ.run(deltaRRWDuplicate, "8-bit", "");
	
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
		String saveDir = workDir+"/dRR_"+stackImgYFP.getShortTitle();
		File resultFolder = new File(saveDir);
		resultFolder.mkdir();
		
		RoiManager rm = RoiManager.getInstance();
		Roi [] translatedROIs = rm.getRoisAsArray();
		
		//Check if RoiManager contains ROIs
		if(translatedROIs.length ==0){
			IJ.error("No ROIs could be found!");
			return;
		}
		
		
		int rCount = rm.getCount();
		ResultsTable fancy = new ResultsTable();
		float[] x = new float[deltaRR.getNSlices()];
		
		
		for( int i=0; i<rCount; i++){
			//Set ROI
			deltaRR.setRoi(translatedROIs[i]);
			IJ.showStatus("Extracting trace data: ");
			IJ.showProgress(1.0*i/(translatedROIs.length));
			
			//Calibrate measurements
			double minThreshold = deltaRR.getProcessor().getMinThreshold();
			double maxThreshold = deltaRR.getProcessor().getMaxThreshold();
			float [] y = getZAxisProfile(translatedROIs[i], deltaRR, minThreshold, maxThreshold);
			
			//Initialize Table with zero values
			if(i==0){
				//float[] x = new float[y.length];
				for (int n=0; n<x.length; n++)
					x[n] = n+1;
				//First add slice label
				for(int m=0; m<x.length; m++){
					fancy.incrementCounter();
					fancy.addValue(translatedROIs[i].getName(), x[m]);
				}
			}
			//Dirty way to prevent the creation of an obsolete last data column. 
			if(i==translatedROIs.length){
				break;
			}
			//now add values to resultstable
			for (int h=0; h<y.length; h++ ){
				//Set values for table elements
				fancy.addValue(translatedROIs[i].getName(), y[h]);
				fancy.setValue(i, h, y[h]);
			}
			
			//We create a plot of the measurement, but do not show it
			//instead we create an imageplus reference for later and add it to a stack
			String xAxisLabel = "Slice";
			Plot plot = new Plot("Roi "+Integer.toString(i), xAxisLabel, "Mean", x, y);
			double ymin = ProfilePlot.getFixedMin();
			double ymax= ProfilePlot.getFixedMax();
			if (!(ymin==0.0 && ymax==0.0)) {
				double[] a = Tools.getMinMax(x);
				double xmin=a[0]; double xmax=a[1];
				plot.setLimits(xmin, xmax, ymin, ymax);
			}
			ImagePlus plotSlice = plot.getImagePlus();
			if( i==0){
				resultPlots = new ImageStack(plotSlice.getWidth(), plotSlice.getHeight());
			}
			resultPlots.addSlice(plotSlice.getProcessor());

		}
		
		//Show a table with the combined results
		fancy.show("Combined Results");
		
		//now save the fancy table
		try{

			fancy.saveAs(saveDir +"/dRRData.csv");

		}
		//If we want to save, we have to throw an exception if it fails
		catch (IOException e){
			IJ.log("Can not save to working directory!");
		}
		
		
		//new method: create a plot on my own now and do not save images!
		//we have now resultplots and use this to generate the mosaic
		//ImagePlus initialSlice = new ImagePlus("ini", resultPlots.getProcessor(0));
		for(int j=1; j<rCount; j++){
			resultPlots.getProcessor(j).setFont(f);
			resultPlots.getProcessor(j).drawString("ROI"+ Integer.toString(j), 60, 240);
		}
		mosaicStack = resultPlots;
		
		MontageMaker mn = new MontageMaker();
		ImagePlus mosaicImp = new ImagePlus("ROI Plots", mosaicStack);
		mosaicImp.updateAndDraw();
		mosaicImp.show();
		//IJ.saveAs(mosaicImp, "Tiff", traceDir+"/ROI Plots.tif");
		
		////find smallest square grid configuration for mosaic
		int mosRows= 1+(int)Math.floor(Math.sqrt(mosaicStack.getSize()));
		int mosCols=mosRows-1;
		while (mosRows*mosCols<mosaicStack.getSize())
			mosCols++;
		mn.makeMontage(mosaicImp, mosRows, mosCols, 1.0, 1, mosaicStack.getSize(), 1, 1, false);
		mosaicImp.changes = false;
		mosaicImp.close();
		ImagePlus collage = WindowManager.getImage("Montage");
		IJ.saveAs(collage, "Jpeg", saveDir +"/Montage.jpg");
		
	}

	}
}