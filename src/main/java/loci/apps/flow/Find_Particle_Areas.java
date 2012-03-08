package loci.apps.flow;

import ij.*;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.*;
import ij.process.ImageProcessor;
import ij.gui.GenericDialog;
import ij.macro.*;

public class Find_Particle_Areas implements PlugInFilter {

	protected ImagePlus image;

	@Override
	public int setup(String arg, ImagePlus image) {

		this.image = image;
		return DOES_8G | NO_CHANGES;
	}


	@Override
	public void run(ImageProcessor arg0) {
		try{

			GenericDialog gd = new GenericDialog("Calculate Particle Areas");
			String [] methods={"Brightfield", "Intensity"};
			gd.addChoice("Channel", methods, methods[0]);
			gd.addMessage ("Special paramters; (thresholdMax = 255, SizeMax=Infinity)");
			gd.addNumericField ("Threshold_Minimum",  170, 0);
			gd.addNumericField ("Size_Minimum",  0, 0);
			gd.addCheckbox("Exclude_Particles_on_Edge",true);

			gd.showDialog();
			if (gd.wasCanceled()) return;

			String myMethod= gd.getNextChoice ();
			double thresholdMin= (double) gd.getNextNumber();
			int sizeMin= (int) gd.getNextNumber();
			boolean exclude=gd.getNextBoolean (); 

			Interpreter.batchMode=true;

			ImageProcessor duplicatedArg0 = arg0.duplicate();
			ImagePlus imp = new ImagePlus("duplicate", duplicatedArg0); // has only one slice

			if(myMethod.equals("Intensity")){
				imp.getProcessor().setThreshold(thresholdMin, 255, ImageProcessor.RED_LUT);
/*				IJ.run(imp, "Threshold...", null);
				IJ.runMacro("setAutoThreshold(\"Default dark\")", null);
				IJ.runMacro("setThreshold("+thresholdMin+", 255)", null);
*/				IJ.run(imp, "Convert to Mask", null);

				if(exclude) IJ.run(imp, "Analyze Particles...", "size="+sizeMin+"-Infinity circularity=0.00-1.00 show=Masks display exclude clear include add");
				else IJ.run(imp, "Analyze Particles...", "size="+sizeMin+"-Infinity circularity=0.00-1.00 show=Masks display clear include add");
			}			
			else{
				IJ.run(imp, "Find Edges", null);
				IJ.run(imp, "Find Edges", null);

				IJ.run(imp, "Gaussian Blur...", "sigma=5");

				IJ.run(imp, "Auto Threshold", "method=Minimum white");

				if(exclude) IJ.run(imp, "Analyze Particles...", "size="+sizeMin+"-Infinity circularity=0.00-1.00 show=Masks exclude clear add");
				else IJ.run(imp, "Analyze Particles...", "size="+sizeMin+"-Infinity circularity=0.00-1.00 show=Masks clear add");

			}
			if(RoiManager.getInstance().getCount()>0)
				IJ.runMacro("roiManager(\"Measure\")",null);

			imp.close();

			Interpreter.batchMode=false;

		} catch(Exception e){
			e.printStackTrace();

		}
		Interpreter.batchMode=false;

	}

}