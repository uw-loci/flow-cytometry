package loci.apps.flow;
// DELETE the package script above, and place in WiscScan/plugins
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
			Duplicator dup = new Duplicator();
			ImagePlus duplicate = dup.run(image);
			//TODO use duplicate image in all "run"s, 
			IJ.run("Duplicate...", "title=Duplicate");
			IJ.run("Find Edges");
			IJ.run("Find Edges");
			IJ.run("Gaussian Blur...", "sigma=5");
			IJ.run("Auto Threshold", "method=Minimum white");
			IJ.run("Analyze Particles...", "size=0-Infinity circularity=0.00-1.00 show=Masks exclude clear add");
			IJ.runMacro("roiManager(\"Measure\")",null);
		} catch(Exception e){

		}
		Interpreter.batchMode=false;


	}

}