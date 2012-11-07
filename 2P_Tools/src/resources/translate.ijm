//This macro runs the translation defined in the plugin over the stack. 
//In this way, we can run the analysis in batch mode.
//The macro only is able to run with the plugin

if (nImages==0){
	print("No images are open");
}
else {
	img_list = newArray(nImages);
	for (i=1; i<=nImages; i++) {
        	selectImage(i);
		img_list[i-1]=getTitle();
	}
}

Dialog.create("Translation tool");
//Numbers set for use with plugin, image IDs should always be the same for each run
Dialog.addChoice("to_register",img_list, img_list[5]);
Dialog.addChoice("reference", img_list, img_list[1]);
Dialog.show(); 

img_name1 = Dialog.getChoice();
img_name2 = Dialog.getChoice();

translateStack(img_name1, img_name2);



function translateStack(stackName, hiResName) {

	setBatchMode(true);
	selectWindow(stackName);
	Stack.getDimensions(width, height, channels, slices, frames);
	selectWindow(hiResName);
	hiHeight = getHeight();
	hiWidth = getWidth();

	selectWindow(stackName);
	//run("Size...", "width=" +hiWidth+" height=" +hiHeight+" depth="+slices+" constrain interpolation=None");
	run("Size...", "width=" +hiWidth+" depth="+slices+" constrain interpolation=None");
	setSlice(1);
	selectWindow(stackName);
	newStackWidth = getWidth();
        newStackHeight = getHeight();
        //run("Select All");
        run("Copy");
        //we have to update the size so non quadratic images are catched
       	//newImage("slice", "16-bit Black", hiWidth, hiHeight, 1);
       	newImage("slice", "16-bit Black", newStackWidth, newStackHeight, 1);
        run("Paste");
        //run("Size...", "width=" +hiHeight+" height=" +hiWidth+" depth=1 constrain interpolation=None");
	rename("active");
	run("Descriptor-based registration (2d/3d)", 
				"first_image=active second_image="+hiResName+" reapply");
	selectWindow("overlay active ... "+hiResName);
	sliceHeight= getHeight();
	sliceWidth = getWidth();
	run("Split Channels");
	selectWindow("C1-overlay active ... "+hiResName);
	run("Copy");
	run("Close");
	newImage("Translated stack", "16-bit Black", sliceWidth, sliceHeight, slices);
	selectWindow("Translated stack");
	setSlice(1);
	run("Paste");
	//selectWindow("active");
	//close();
	for (j=2; j<slices+1; j++) {
		selectWindow(stackName);
		//run("Size...", "width=" +hiWidth+" height=" +hiHeight+" depth="+slices+" constrain interpolation=None");
                setSlice(j);
                run("Copy");
               //Image needs unique name, otherwise the first image will be copied and translated
               	//newImage("active"+toString(j), "16-bit Black", hiWidth, hiHeight, 1);
               	newImage("active"+toString(j), "16-bit Black", newStackWidth, newStackHeight, 1);
               	selectWindow("active"+toString(j));
                run("Paste");
               // run("Size...", "width=" + hiHeight+ 
				//" height=" +hiWidth+" depth=1 constrain interpolation=None");

		run("Descriptor-based registration (2d/3d)", 
				"first_image=active"+toString(j)+" second_image="+hiResName+" reapply");
		selectWindow("overlay active"+toString(j)+" ... "+hiResName);
		run("Split Channels");
		selectWindow("C1-overlay active"+toString(j)+" ... "+hiResName);
		run("Copy");
		run("Close");
		//sliceHeight= getHeight();
		//sliceWidth = getWidth();
		selectWindow("Translated stack");
		setSlice(j);
		run("Paste");
		selectWindow("active"+toString(j));
		close();
                 
	}
	selectWindow(stackName);
	close();
	selectWindow("Translated stack");
	//selectWindow("active");
	//run("Save");
	setBatchMode(false);

}