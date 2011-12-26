package loci.apps.flow;

import ij.*;
//import ij.process.*;
import ij.gui.*;
//import java.awt.*;
import ij.plugin.*;
//import ij.plugin.frame.*;
import ij.macro.*;

public class bf_ParticleAreasPlugin implements PlugIn {

	public void run(String arg) {
	  ImagePlus img = WindowManager.getCurrentImage();
	
	  Interpreter.batchMode=true;
	  IJ.run("Duplicate...", "title=Duplicate");
	  IJ.run("Find Edges");
	  IJ.run("Find Edges");
	  IJ.run("Gaussian Blur...", "sigma=5");
	  IJ.run("Auto Threshold", "method=Minimum");
	  IJ.makeRectangle(2,2,img.getWidth()-4, img.getHeight()-4);
	  IJ.run("Crop");
	  IJ.run("Create Selection");
	  IJ.run("Create Mask");
	  IJ.run("Fill Holes");
	  IJ.run("Create Selection");

	  IJ.selectWindow("Mask");
	  WindowManager.removeWindow(WindowManager.getFrontWindow());

	  IJ.selectWindow("Duplicate");
	  WindowManager.removeWindow(WindowManager.getFrontWindow());
//	  Interpreter.batchMode=false;

	  IJ.run("Restore Selection");
	  IJ.run("ROI Manager...");
	  IJ.runMacro("roiManager(\"add\")",null);

	  img = WindowManager.getCurrentImage();
	  Roi roi = img.getRoi();

	if(roi.getType()==Roi.COMPOSITE){
	  IJ.runMacro("roiManager(\"Split\")",null);
	  IJ.runMacro("roiManager(\"Select\", 0)",null);
	  IJ.runMacro("roiManager(\"Delete\")",null);
	}
	  IJ.runMacro("roiManager(\"Measure\")",null);
	  IJ.runMacro("roiManager(\"Show All\")",null);	
	  Interpreter.batchMode=false;

//	  WindowManager.closeAllWindows();
	}

}
