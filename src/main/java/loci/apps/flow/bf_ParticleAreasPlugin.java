package loci.apps.flow;
// DELETE the package script above, and place in WiscScan/plugins
import ij.*;
import ij.plugin.*;
import ij.plugin.frame.*;
import ij.measure.*;
import ij.macro.*;

public class bf_ParticleAreasPlugin implements PlugIn {

	public void run(String arg) {
//	  ImagePlus img = WindowManager.getCurrentImage();
	
	  Interpreter.batchMode=true;
	  IJ.run("Duplicate...", "title=Duplicate");
	  IJ.run("Find Edges");
	  IJ.run("Find Edges");
	  IJ.run("Gaussian Blur...", "sigma=5");
	  IJ.run("Auto Threshold", "method=Minimum white");
	  IJ.run("Analyze Particles...", "size=0-Infinity circularity=0.00-1.00 show=Masks exclude clear add");
	  IJ.run("Create Selection");
/*	  IJ.makeRectangle(2,2,img.getWidth()-4, img.getHeight()-4);
	  IJ.run("Crop");
	  IJ.run("Create Selection");
	  IJ.run("Create Mask");
	  IJ.run("Fill Holes");
	  IJ.run("Create Selection");
*/
	  IJ.selectWindow("Mask of Duplicate");
	  WindowManager.removeWindow(WindowManager.getFrontWindow());

	  IJ.selectWindow("Duplicate");
	  WindowManager.removeWindow(WindowManager.getFrontWindow());
//	  Interpreter.batchMode=false;

	  IJ.run("Restore Selection");
//	  IJ.run("ROI Manager...");
//	  IJ.runMacro("roiManager(\"add\")",null);

//	  img = WindowManager.getCurrentImage();
//	  Roi roi = img.getRoi();

//	if(roi.getType()==Roi.COMPOSITE){
//	  IJ.runMacro("roiManager(\"Split\")",null);
//	  IJ.runMacro("roiManager(\"Select\", 0)",null);
//	  IJ.runMacro("roiManager(\"Delete\")",null);
//	}
	  IJ.runMacro("roiManager(\"Measure\")",null);
//	  IJ.runMacro("roiManager(\"Show All\")",null);	
	  Interpreter.batchMode=false;

//	  WindowManager.closeAllWindows();

/*		RoiManager rm = RoiManager.getInstance();
		ResultsTable rt = ResultsTable.getResultsTable();


		Integer lenghtOfRoiTable = rm.getRoisAsArray().length;
		if (lenghtOfRoiTable == (Integer) null) IJ.showMessage("Null value at lenghtOfRoiTable");
		String asdf= lenghtOfRoiTable.toString();
		IJ.showMessage("My_Plugin",asdf);

		Integer[] retVal = new Integer[lenghtOfRoiTable];
		float[] temp = rt.getColumn(rt.getColumnIndex("Area"));
		if (temp==null) IJ.showMessage("Null value at getColumn(1)");

		for (int i = 0; i < lenghtOfRoiTable; i++){
			retVal[i]=(int)temp[i];
		}

		asdf = retVal[0].toString();
		if(asdf!=null) IJ.showMessage(asdf);
		else IJ.showMessage("My_Plugin","S... hit the fan");

		ImagePlus img = WindowManager.getCurrentImage();
		rm.dispose();
		rt.reset();
		img.flush();
		img.close();
*/




	}

}