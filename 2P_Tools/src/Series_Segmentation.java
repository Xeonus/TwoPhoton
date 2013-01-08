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


public class Series_Segmentation implements PlugIn {

	private ImagePlus srImg;
	private ImagePlus ogbImg;
	private String saveDir;
	private String originalName;
	private static String OSType;
	public static int defaultImg1=0;
	public static int defaultImg2=1;
	public ImagePlus workingImg;
	Font f = new Font("dialog",Font.BOLD,12);
	InputStream exeIS;
	OutputStream dest;
	
	//Checkbox items
	private boolean localContrast;
	private boolean pseudoFlat;
	private boolean stackCheck;
	private boolean meanInt;
	
	//Numeric fields
	private int localWidth;
	private double satPerc;
	private int gaussR;
	
	//OS Checkup
	public static boolean isWindows(){
		OSType = System.getProperty("os.name");
		return OSType.startsWith("Windows");
	}
	
	/**Method to subtract one image from another
	 * by calling standard imagej procedure
	 * @param img1: source image
	 * @param img2: image subtracted from source image
	 */
	public ImagePlus subtractCreate(ImagePlus img1, ImagePlus img2){
		ImageCalculator ic = new ImageCalculator();
		ImagePlus subImg = ic.run("Subtract create stack", img1, img2);
		subImg.show();
		return subImg;
	}
	
	/**
	 * Method to subtract the mean intensity of a stack from every slice
	 * Only usable on 3D-stacks!
	 * @param img1
	 * @return img1 - stackMean
	 */
	public ImagePlus subtractMeanIntensity(ImagePlus img1){
		//first calculate median of stack
		int nOfSlices = img1.getStackSize();
		IJ.run(img1, "Z Project...", "start=1 stop="+nOfSlices+" projection=[Median]");
		ImagePlus meanImg = WindowManager.getImage("MED_"+img1.getTitle());
		meanImg.show();
		ImagePlus meanSubtracted = subtractCreate(img1, meanImg);
		return meanSubtracted;
	}
	
	
	/**Enhance Local Contrast
	 * This method enhances contrast locally either through local square histograms
	 * or through global histogram
	 * 
	 * @param img1: Input image
	 * @param width: local enhance contrast square
	 * @param stackHist: if true: enhance based on local histogram
	 * @param satPercentage: Saturation percentage
	 */
	public ImagePlus enhanceLocalContrast(ImagePlus img1, int width, double satPercentage, boolean stackHist){
		//we have to make sure that we do not enhance the contrast over and over again, when stackCheck=true!!
		//duplicate original image
		ImagePlus enhanced = img1.duplicate();
		//ImagePlus enhancedStack;
		int imgWidth = img1.getWidth();
		int imgHeight = img1.getHeight();
		//maximum number of squares
		double nmax = Math.floor(Math.max(imgWidth, imgHeight)/width)+1;
		//ROI Coordinates
		int xx=0;
		int yy=0;
			IJ.log("Enhancing local contrast...");
			for(int i=0; i<nmax; i++){
				//IJ.log("Iteration"+i/nmax*100+"% complete");
				for(int j=0; j<nmax; j++){
					//Create ROI
					Roi rect = new Roi(xx, yy, width, width);
					enhanced.setRoi(rect);
					//TODO: keep in mind to check with radio buttons if it is stack!
					if(stackHist == true){
						IJ.run(enhanced, "Enhance Contrast...", "saturated="+satPercentage+" normalize process_all use");
					}
					else{
						IJ.run(enhanced, "Enhance Contrast...", "saturated="+satPercentage+" normalize process_all");
					}
					xx+=width;
				}
				xx=0;
				yy+=width;
			}
		//}
		return enhanced;
	}
	
	/**
	 * Method to create a pseudo flat field image of the source image
	 * Subtract a gaussian blur image from the source image
	 * @param img1
	 * @param gaussR
	 */
	public ImagePlus pseudoFlatField(ImagePlus img1, int gaussR){
		//Create gaussian blur of source img1
		ImagePlus gaussImg = img1.duplicate();
		IJ.run(gaussImg, "Gaussian Blur...", "radius="+gaussR+" stack");
		//then subtract that image from img1
		ImagePlus pseudoFlat = subtractCreate(img1, gaussImg);
		return pseudoFlat;
	}
	
	//Execute PlugIn Procedures
	public void run (String arg){
		
		//Check if Plugin runs on Windows.
		 if (isWindows() == false){
			 IJ.showMessage("This Plugin is only supported on Windows machines!");
			 return;
		 }
		 
		 
		 final int[] idList = WindowManager.getIDList();
		 
		 if (idList == null || idList.length < 2 ){
			 IJ.error("At least two images have to be opened!");
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
		 
		 /*
		 //Create GUI elements
		 final String ogbString = "High Resolution Scan of Imaged Area";
		 final String srString = "Low Resolution Image Series";

		 //Panel to load images and ROI
		 Panel flowPanel = new Panel(new FlowLayout());
		 Panel myLoadPanel = new Panel(new GridLayout(2,1));
		 final Button loadOGBButton = new Button("Load OGB Stack");
		 final Button loadSRButton = new Button("Load SR Stack");
		 Panel myTextPanel = new Panel(new GridLayout(2,1));
		 final TextField OGBField = new TextField(ogbString, 30);
		 final TextField SRField = new TextField(srString, 30);
		  */

		 
		 //Removed Loadbuttons for batch processing compability
		 /*
		 //Load buttons
		 loadOGBButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				IJ.showStatus("Loading OGB Stack");
				IJ.open();
				ImagePlus img1 = IJ.getImage();
				ogbImg = img1;
				ogbImg.hide();
				//Case: Convert to 8-bit
				if (img1.getBitDepth() != 8){
					ogbImg.show();
					IJ.run("8-bit");
					ogbImg.hide();
				}
				OGBField.setText(img1.getTitle());
				originalName = ogbImg.getShortTitle();
				}
			});

			loadSRButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				IJ.showStatus("Loading Sulforhodamine 101 Stack");
				IJ.open();
				ImagePlus img2 = IJ.getImage();
				srImg = img2;
				srImg.hide();
				//Case: Convert to 8-bit
				if ( img2.getBitDepth() != 8) {
					srImg.show();
					IJ.run("8-bit");
					srImg.hide();
					
				}
				SRField.setText(img2.getTitle());
				}
			});
			*/
			
			//Select output folder
			 Panel directoryPanel = new Panel(new FlowLayout());
			 Panel myDirectoryPanel = new Panel(new GridLayout(2,1));
			 final Button loadDirectoryButton = new Button("Output Direcotry");
			 Panel directoryTextPanel = new Panel(new GridLayout(2,1));
			 final TextField directoryField = new TextField("Output Directory", 30);
			 //directoryField.setText("Output directory path");
			 
			 loadDirectoryButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						IJ.showStatus("Choose output Direcotry");
						DirectoryChooser dirC = new DirectoryChooser("Select output path");
						saveDir = dirC.getDirectory();
		
						directoryField.setText(saveDir);
						}
					});

			/*Load Buttons panel 
			myTextPanel.add(OGBField);
			myTextPanel.add(SRField);
			myLoadPanel.add(loadOGBButton);
			myLoadPanel.add(loadSRButton);
			flowPanel.add(myLoadPanel);
			flowPanel.add(myTextPanel);
			*/
			 
			//Directory panel
			directoryTextPanel.add(directoryField);
			myDirectoryPanel.add(loadDirectoryButton);
			directoryPanel.add(myDirectoryPanel);
			directoryPanel.add(directoryTextPanel);
			
			
			//remove radio buttons
			/*
			String seriesString = "Cell detection on time series";
			String stack3DString = "Cell detection on 3D stack";
			JRadioButton stackButton = new JRadioButton(stack3DString);
			JRadioButton seriesButton = new JRadioButton(seriesString);
			stackButton.setSelected(true);
			ButtonGroup group = new ButtonGroup();
			group.add(stackButton);
			group.add(seriesButton);
			
			
			//Add Buttons to new panel below load buttons
			Panel radioPanel = new Panel(new GridLayout(0,1));
			radioPanel.add(stackButton);
			radioPanel.add(seriesButton);
			 */
			
			
			//Generate dialog box
			GenericDialog gd = new GenericDialog("Automatic Cell Detection in 3D Stacks", IJ.getInstance());
	        gd.addChoice("OGB channel", imgList, imgList[ defaultImg1 ] );        
	        gd.addChoice("Sulforhodamine channel", imgList, imgList[ defaultImg2 ] );
			//gd.addPanel(flowPanel);
			//gd.addPanel(radioPanel);
			gd.addMessage("Enhanced Local Contrast:", f);
			gd.addCheckbox("Apply Enhanced Local Contrast", localContrast);
			gd.addNumericField("Local Square Length:", 20.0, 1);
			gd.addNumericField("Saturation Percentage:", 0.4,1);
			gd.addMessage("Local Mean Intensity:", f);
			gd.addCheckbox("Enhance Local Mean Intensity (3D: recommended)", meanInt);
			gd.addMessage("Pseudo-flat Field:", f);
			gd.addCheckbox("Apply Pseudo-flat Field Correction", pseudoFlat);
			gd.addNumericField("Gauss Radius:", 20.0, 1);
			gd.addPanel(directoryPanel);
			
			gd.showDialog();
			//String resourcePath = "/resources/";
			
			
			if (gd.wasCanceled())
				return;
			
			
			//rRetrieve values from dialog box
			localContrast = gd.getNextBoolean();
			meanInt = gd.getNextBoolean();
			localWidth = (int) gd.getNextNumber();
			satPerc = gd.getNextNumber();
			pseudoFlat = gd.getNextBoolean();
			gaussR = (int) gd.getNextNumber();
			
			//retrieve selected Images:
			ogbImg = WindowManager.getImage( idList[ defaultImg1 = gd.getNextChoiceIndex() ] );
			srImg = WindowManager.getImage( idList[ defaultImg2 = gd.getNextChoiceIndex() ] ); 
			

			
			//Procedures based on radio button chosen:
			//TODO: find out which order of the procedures gives best results for contrast enhancements.
			/*
			if (seriesButton.isSelected() == true){
				//TODO: apply stack procedure. pass boolean to methods and deal in every method with stacks or 3d
				stackCheck = false;
				workingImg = subtractCreate(ogbImg, srImg);
				if(localContrast == true){
					workingImg = enhanceLocalContrast(workingImg, localWidth, satPerc, stackCheck);
				}
				
				//Enhance local mean intensity
				if(meanInt==true){
					workingImg = subtractMeanIntensity(workingImg);
				}
				
				if(pseudoFlat == true){
					workingImg = pseudoFlatField(workingImg, gaussR);
				}
				
				//save time series in analyze format
				if(saveDir == null){
					IJ.showMessage("no output directory specified");
					return;
				}
				else{
					workingImg.show();
					IJ.run(workingImg, "Enhance Contrast", "saturated=0.35");
					workingImg.setTitle("TODO");
					IJ.run("Analyze... ", saveDir+"/TODO.img");
					IJ.saveAs(workingImg, "Tiff", saveDir+"/enhancedContrast_"+originalName+".tiff");
				}
			}
			else {
			*/
				//3D segmentation
				workingImg = subtractCreate(ogbImg, srImg);
				//Local Mean Intensity
				if(meanInt==true){
					workingImg = subtractMeanIntensity(workingImg);
				}
				//Pseudo Flat Field
				if(pseudoFlat == true){
					workingImg = pseudoFlatField(workingImg, gaussR);
				}
				//Enhance local contrast
				if(localContrast == true){
					stackCheck=true;
					workingImg = enhanceLocalContrast(workingImg, localWidth, satPerc, stackCheck);
				}
				//save 3D stack in analyze format
				if(saveDir == null){
					IJ.showMessage("no output directory specified");
					return;
				}
				else{
					//TODO: suppress save window prompt!
					workingImg.show();
					IJ.run(workingImg, "Enhance Contrast", "saturated=0.35");
					workingImg.setTitle("TODO");
					IJ.run("Analyze... ", saveDir+"TODO.img");
					IJ.saveAs(workingImg, "Tiff", saveDir+"/enhancedContrast_"+originalName+".tiff");
				}
				
			//}
			
			//TODO: pack this code into a method!
			//TODO: Method must pass segmentation argument -> 2D or 3D methods! Extract methods at home from ZFIQ.
			//We try something totally different: Because we store an executable in a jar file, we need
			//to copy it from our resources to the destination folder, execute it from there and then silently
			//delete it again :)
			IJ.log("Executing gradient flow algorithm");
			InputStream stream = getClass().getResourceAsStream("/resources/3dsegment_16bit.exe");
		    if (stream == null) {
		        IJ.showMessage("Can not find Segmentation Executable!");
		        return;
		    }
		    OutputStream resStreamOut;
		    int readBytes;
		    byte[] buffer = new byte[4096];
		    try {
		        resStreamOut = new FileOutputStream(new File(saveDir+"/segmentation.exe"));
		        while ((readBytes = stream.read(buffer)) > 0) {
		            resStreamOut.write(buffer, 0, readBytes);
		        }
		        resStreamOut.flush();
		        resStreamOut.close();
		    } catch (IOException e1) {
		        // TODO Auto-generated catch block
		        //e1.printStackTrace();
		    	IJ.showMessage("Can not write executable file!");
		    	//return;
		    }
		    try {
				stream.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		    
		   //Now try to execute the segmentation.exe and give the paths in cmd.exe
		    Runtime rt = Runtime.getRuntime();
		    try {
				rt.exec("cmd.exe /c start cmd.exe /k "+saveDir+"/segmentation.exe "+ saveDir +"/TODO.img");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		    //now delete the segmentation.exe file in folder
		    //File toDelete = new File(saveDir+"/segmentation.exe");
		    //boolean success = toDelete.delete();
		    //if(!success){
		    	//IJ.showMessage("Failed to delete segmentation executable in output directory");
		    	//return;
		    //}
			

	}
}


