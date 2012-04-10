package loci.apps.flow;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.macro.Interpreter;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;

import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.io.IOException;

public class FlowCyto {

	private static ImageJ imagej;
	private static IJ IJ;
	private static ImagePlus imp;
	private static ImageStack stack;
	private static ByteProcessor bp;
	private static ColorModel theCM;	
	private static String s_Name, s_Experiment, s_Params, s_Date;
	private static double pixelMicronSquared;

	@SuppressWarnings("static-access")
	public static void startImageJ() {
		IJ = new IJ();
		imagej = IJ.getInstance();
	}

	public static void closeAllWindows() {
		imp.flush();
		ResultsTable.getResultsTable().reset();
		RoiManager.getInstance2().dispose();
		WindowManager.closeAllWindows();
		imagej.exitWhenQuitting(true);
		imagej.dispose();	
		IJ = null;
		System.gc();
		imagej.quit();
	}

	@SuppressWarnings("static-access")
	public static void init(String mode, int width, int height, double pixelsPerMicron) {
		try{
			s_Date = new java.text.SimpleDateFormat("MM.dd.yyyy hh:mm:ss").format(
					new java.util.Date());
			byte[] r = new byte[256];
			byte[] g = new byte[256];
			byte[] b = new byte[256];

			for(int ii=0 ; ii<256 ; ii++)
				r[ii] = g[ii] = b[ii] = (byte)ii;

			theCM = new IndexColorModel(8, 256, r,g,b);
			mode=mode.toLowerCase();
			if ("brightfield".equals(mode)) {
				IJ.newImage("Brightfield Images", "8-bit", width, height, 0);
//				IJ.runMacro("newImage(\"Brightfield Images\", \"8-bit\", "+width+", "+height+", 0)");			
			}
			else if ("intensity".equals(mode)) {
				IJ.newImage("Intensity Images", "8-bit", width, height, 0);
//				IJ.runMacro("newImage(\"Intensity Images\", \"8-bit\", "+width+", "+height+", 0)");
			}
			else if ("both".equals(mode)) {
				IJ.newImage("Brightfield Images", "8-bit", width, height, 0);
				IJ.newImage("Intensity Images", "8-bit", width, height, 0);
//				IJ.runMacro("newImage(\"Brightfield Images\", \"8-bit\", "+width+", "+height+", 0)");
	//			IJ.runMacro("newImage(\"Intensity Images\", \"8-bit\", "+width+", "+height+", 0)");
			}
			else {
				IJ.newImage("Islet Images", "8-bit", width, height, 0);
//				IJ.runMacro("newImage(\"Islet Images\", \"8-bit\", "+width+", "+height+", 0)");
			}
			if (pixelsPerMicron > 0){ 
				pixelMicronSquared = pixelsPerMicron*pixelsPerMicron;
				IJ.run("Set Scale...", "distance="+width+" known="+((double)width/pixelsPerMicron) +" pixel=1 unit=microns");		
			}
			else pixelMicronSquared = 0.180028*0.180028;
		} catch(Exception e){
			System.err.println("Exception at init method " + e.getLocalizedMessage());
		}

	}

	@SuppressWarnings("static-access")
	public static void openFile(String filename, double PixelsPerMicron) throws IOException {
		startImageJ();
		IJ.run("Bio-Formats Importer", "open=["+filename+"] autoscale color_mode=Default view=Hyperstack stack_order=XYCZT");
	}

	@SuppressWarnings("static-access")
	public static void showImage(String mode, int width, int height, byte[] imageData) {
		try{
			bp = new ByteProcessor(width,height,imageData, theCM);
			//		String mode = "brightfield";
			if ("brightfield".equalsIgnoreCase(mode)) {
				IJ.selectWindow("Brightfield Images");
//				IJ.run("selectWindow(\"Brightfield Images\")");
			}
			else if ("intensity".equalsIgnoreCase(mode)) {
				IJ.selectWindow("Intensity Images");
//				IJ.run("selectWindow(\"Intensity Images\")");
			}
			else {
				IJ.selectWindow("Islet Images");
//				IJ.run("selectWindow(\"Islet Images\")");
			}
			IJ.run("Add Slice");
			imp.setImage(bp.createImage());
			//imp.show();
		} catch(Exception e){
			System.out.println("Error at showImage method " + e.getLocalizedMessage());
		}
	}

	@SuppressWarnings("static-access")
	public static float[] getParticleAreas(boolean isIntensityImage, boolean excludeOnEdge, double thresholdMin, int sizeMin){

		float[] retVal = new float[1];
		Interpreter.batchMode=true;
		try{

			if(isIntensityImage){
				if(excludeOnEdge) IJ.run("Find Particle Areas", "channel=Intensity threshold_minimum="+thresholdMin+" size_minimum="+sizeMin+" exclude_particles_on_edge");
				else IJ.run("Find Particle Areas", "channel=Intensity threshold_minimum="+thresholdMin+" size_minimum="+sizeMin+"");
			}
			else {
				if(excludeOnEdge)IJ.run("Find Particle Areas", "channel=Brightfield threshold_minimum="+thresholdMin+" size_minimum="+sizeMin+" exclude_particles_on_edge");
				else IJ.run("Find Particle Areas", "channel=Brightfield threshold_minimum="+thresholdMin+" size_minimum="+sizeMin+"");
			}

			RoiManager rman = RoiManager.getInstance2();
			ResultsTable rtab = ResultsTable.getResultsTable();

			int lengthOfRoiTable = rman.getCount();

			if (lengthOfRoiTable!=0){

				retVal = new float[lengthOfRoiTable];
				float[] areasArray = rtab.getColumn(rtab.getColumnIndex("Area"));

				if(areasArray!=null){
					for (int i = 0; i < lengthOfRoiTable; i++){
						retVal[i]=areasArray[i];
					}
					rman.dispose();
					rtab.reset();
					return retVal;
				}

				rman.runCommand("Deselect");
				rman.runCommand("Delete");
			}
		}catch (Exception e){
			//fall through
		}
		Interpreter.batchMode=false;
		retVal[0]=0;
		return retVal;
	}

	public static void garbageCollect(){
		System.gc();
	}

}
