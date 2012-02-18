package loci.apps.flow;


import ij.*;
import ij.plugin.*;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.*;
import ij.process.ImageProcessor;
import ij.measure.*;
import ij.macro.*;

//make plugin filter

public class bf_ParticleAreasPlugin implements PlugInFilter {

	protected ImagePlus image;
	@Override
	public int setup(String arg, ImagePlus image) {

		this.image = image;
		return DOES_8G | NO_CHANGES;
	}

	@Override
	public void run(ImageProcessor arg0) {
		try{
			Interpreter.batchMode=true;

			//Duplicator dup = new Duplicator();
			//ImagePlus duplicate = dup.run(image);
			//TODO use duplicate image in all "run"s, 
			IJ.log("Started scan");//TEMP			
			ImageProcessor duplicatedArg0 = arg0.duplicate();
			ImagePlus imp = new ImagePlus("duplicate", duplicatedArg0); // has only one slice
			IJ.log("Duplicate ImageProcessor and ImagePlus successfully created");//TEMP
			
			//IJ.run(imp, "Duplicate...", "title=Duplicate");
			IJ.run(imp, "Find Edges", null);
			IJ.run(imp, "Find Edges", null);
			IJ.log("Ran Find Edges twice...");//TEMP
			
			IJ.run(imp, "Gaussian Blur...", "sigma=5");
			IJ.log("Gaussian Blur with sigma 5");//TEMP
			
			IJ.run(imp, "Auto Threshold", "method=Minimum white");
			IJ.log("thresholded ImagePlus duplicate with Auto Threshold Minimum");//TEMP
			
			IJ.run(imp, "Analyze Particles...", "size=0-Infinity circularity=0.00-1.00 show=Masks exclude clear add");
			IJ.log("Analyzed particles, loaded to ROI Manager");//TEMP
			
			IJ.log("ROI count = " + RoiManager.getInstance().getCount());//TEMP
			if(RoiManager.getInstance().getCount()>0)
				IJ.runMacro("roiManager(\"Measure\")",null);
			
			IJ.log("Successfully measured, finished all calculations");//TEMP

			//IJ.selectWindow("Duplicate");
			//WindowManager.removeWindow(WindowManager.getFrontWindow());
			imp.close();
			IJ.log("Closed ImagePlus duplicate.");//TEMP
			IJ.log("Done.");//TEMP
			
			Interpreter.batchMode=false;

		} catch(Exception e){
			e.printStackTrace();
			//String stackTrace = DebugTools.printStackTrace(e);
			//IJ.handleException(e);
		}
		Interpreter.batchMode=false;
//REPLACE SIZE IN ANALYZE PARTICLE TO ACCOMODATE MIN AND MAX

	}

}