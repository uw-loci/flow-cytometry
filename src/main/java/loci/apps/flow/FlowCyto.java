package loci.apps.flow;

import ij.IJ;
import ij.ImageJ;
import ij.ImageJApplet;
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
	private static ImagePlus imp, impBF, impIN;
	private static ImageStack stack, stackBF, stackIN;
	private static int nSlices, nSlicesBF, nSlicesIN;
	private static ByteProcessor bp;
	private static ColorModel theCM;	
	//	private static String s_Name, s_Experiment, s_Params, s_Date, tempImageName;
	private static double pixelMicronSquared;
	private static RoiManager rman;
	private static ResultsTable rtab;
	private static byte[] dummyData;


	@SuppressWarnings("static-access")
	public static void startImageJ() {
		IJ = new IJ();
		imagej = IJ.getInstance();
		if(imagej==null || (imagej!=null && !imagej.isShowing()))
			new ImageJ();
	}

	public static void closeAllWindows() {
		imp.close();
		impBF.close();
		impIN.close();
		bp=null;
		try{
			ResultsTable.getResultsTable().reset();
			RoiManager.getInstance2().dispose();		
		}catch(Exception e){
			//fall through - happens when RoiManager isnt init'ed...
		}

		IJ = null;	

		try{
			IJ.run("Close All");
			WindowManager.closeAllWindows();
			imagej.quit();
			garbageCollect();
		}catch(Exception e){
			garbageCollect();
		}
	}

	@SuppressWarnings("static-access")
	public static void init(String mode, int width, int height, double pixelsPerMicron) {
		try{
			//		timer = new Timestamp(0);
			long initialTime = System.nanoTime();
			nSlices=0;
			nSlicesBF=0;
			nSlicesIN=0;
			impBF=new ImagePlus();
			impIN=new ImagePlus();
			imp=new ImagePlus();
			//		s_Date = new java.text.SimpleDateFormat("MM.dd.yyyy hh:mm:ss").format(new java.util.Date());
			byte[] r = new byte[256];
			for(int ii=0 ; ii<256 ; ii++)
				r[ii]=(byte)ii;

			theCM = new IndexColorModel(8, 256, r,r,r);
			ByteProcessor initBP = new ByteProcessor(width,height); 			
			dummyData = new byte[width*height];
			bp = new ByteProcessor(width,height,dummyData, theCM);
			bp.createImage();

			mode=mode.toLowerCase();

			if ("brightfield".equals(mode)) {
				impBF = new ImagePlus("Brightfield images",	initBP);
				stackBF = new ImageStack(width,height, theCM);
				impBF.show();
				impBF.unlock();

				stackBF.addSlice("Slice "+nSlicesBF, bp);
				impBF.setStack("Brightfield images", stackBF);
				impBF.setSlice(1);	
				impBF.unlock();
			}
			else if ("intensity".equals(mode)) {
				impIN = new ImagePlus("Intensity images", initBP);
				stackIN = new ImageStack(width,height, theCM);
				impIN.show();
				impIN.unlock();

				stackIN.addSlice("Slice "+nSlicesIN, bp);
				impIN.setStack("Intensity images", stackIN);
				impIN.setSlice(1);
				impIN.unlock();
			}
			else if ("both".equals(mode)) {
				impBF = new ImagePlus("Brightfield images",	initBP);
				stackBF = new ImageStack(width,height, theCM);
				impBF.show();
				impBF.unlock();

				stackBF.addSlice("Slice "+nSlicesBF, bp);
				impBF.setStack("Brightfield images", stackBF);
				impBF.setSlice(1);	
				impBF.unlock();

				impIN = new ImagePlus("Intensity images", initBP);
				stackIN = new ImageStack(width,height, theCM);
				impIN.show();
				impIN.unlock();

				stackIN.addSlice("Slice "+nSlicesIN, bp);
				impIN.setStack("Intensity images", stackIN);
				impIN.setSlice(1);
				impIN.unlock();
			}
			else {
				imp = new ImagePlus("Islet images",	initBP);
				stack = new ImageStack(width,height, theCM);
				imp.show();
				imp.unlock();

				stack.addSlice("Slice "+nSlices, bp);
				imp.setStack("Islet images", stack);
				imp.setSlice(1);	
				imp.unlock();
			}
			if (pixelsPerMicron > 0){ 
				pixelMicronSquared = pixelsPerMicron*pixelsPerMicron;
				IJ.run("Set Scale...", "distance="+width+" known="+((double)width/pixelsPerMicron) +" pixel=1 unit=microns");
				//-----------------------FOR DEBUG PURPOSES--------------------//
				IJ.log("ImageJ started for "+mode+" mode in "+ ((System.nanoTime() - initialTime)/1000) +"us");
				//-------------------------------------------------------------//
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
			long initialTime = System.nanoTime();
			bp = new ByteProcessor(width,height,imageData, theCM);
			bp.createImage();
			mode = mode.toLowerCase();
			if ("brightfield".equals(mode)) {
				stackBF.addSlice("Slice "+nSlicesBF, bp);
				impBF.setStack("Brightfield Images", stackBF);
				impBF.setSlice(stackBF.getSize());
				impBF.show();
				impBF.unlock();
				nSlicesBF++;
				//-----------------------FOR DEBUG PURPOSES--------------------//
				IJ.log("brightfield image "+nSlicesBF+" displayed in "+ ((System.nanoTime() - initialTime)/1000) +"us");
				//-------------------------------------------------------------//
			}
			else if ("intensity".equals(mode)) {
				stackIN.addSlice("Slice "+nSlicesIN, bp);
				impIN.setStack("Intensity Images", stackIN);
				impIN.setSlice(stackIN.getSize());
				impIN.show();		
				impIN.unlock();
				nSlicesIN++;
				//-----------------------FOR DEBUG PURPOSES--------------------//
				IJ.log("intensity image "+nSlicesIN+" displayed in "+ ((System.nanoTime() - initialTime)/1000) +"us");
				//-------------------------------------------------------------//
			}
			else {
				stack.addSlice("Slice "+nSlices, bp);
				imp.setStack("Islet Images", stack);
				imp.setSlice(stack.getSize());
				imp.show();
				imp.unlock();
				nSlices++;
			}

		} catch(Exception e){
			System.err.println("Error at showImage method " + e.getLocalizedMessage());
		}
	}

	@SuppressWarnings("static-access")
	public static float[] getParticleAreas(boolean isIntensityImage, boolean excludeOnEdge, double thresholdMin, int sizeMin){
		long initialTime = System.nanoTime();
		Interpreter.batchMode=true;

		try{

			if(isIntensityImage){				
				try{
					if(excludeOnEdge) IJ.run(impIN, "Find Particle Areas", "channel=Intensity threshold_minimum="+thresholdMin+" size_minimum="+sizeMin+" exclude_particles_on_edge");
					else IJ.run(impIN, "Find Particle Areas", "channel=Intensity threshold_minimum="+thresholdMin+" size_minimum="+sizeMin+"");
					//-----------------------FOR DEBUG PURPOSES--------------------//
					IJ.log("plugin finished on intensity image "+nSlicesIN+" in "+ ((System.nanoTime() - initialTime)/1000) +"us");
					//-------------------------------------------------------------//

				}catch(Exception e){
					if(excludeOnEdge)IJ.run(imp, "Find Particle Areas", "channel=Brightfield threshold_minimum="+thresholdMin+" size_minimum="+sizeMin+" exclude_particles_on_edge");
					else IJ.run(imp, "Find Particle Areas", "channel=Brightfield threshold_minimum="+thresholdMin+" size_minimum="+sizeMin+"");
				}
			}
			else {
				try{
					if(excludeOnEdge)IJ.run(impBF, "Find Particle Areas", "channel=Brightfield threshold_minimum="+thresholdMin+" size_minimum="+sizeMin+" exclude_particles_on_edge");
					else IJ.run(impBF, "Find Particle Areas", "channel=Brightfield threshold_minimum="+thresholdMin+" size_minimum="+sizeMin+"");
					//-----------------------FOR DEBUG PURPOSES--------------------//
					IJ.log("plugin finished on brightfield image "+nSlicesBF+" in "+ ((System.nanoTime() - initialTime)/1000) +"us");
					//-------------------------------------------------------------//

				}catch(Exception e){
					if(excludeOnEdge)IJ.run(imp, "Find Particle Areas", "channel=Brightfield threshold_minimum="+thresholdMin+" size_minimum="+sizeMin+" exclude_particles_on_edge");
					else IJ.run(imp, "Find Particle Areas", "channel=Brightfield threshold_minimum="+thresholdMin+" size_minimum="+sizeMin+"");
				}
			}

			rman = RoiManager.getInstance2();
			if(rman.getCount()>0){
				rman.runCommand("Measure");
				rtab = ResultsTable.getResultsTable();

				float[] areasArray = rtab.getColumn(rtab.getColumnIndex("Area"));

				if(areasArray!=null){

		//			rtab.reset();
		//			rman.runCommand("Deselect");
		//			rman.runCommand("Delete");

					//-----------------------FOR DEBUG PURPOSES--------------------//
					IJ.log("particle areas calculated in "+ ((System.nanoTime() - initialTime)/1000) +"us");
					//-------------------------------------------------------------//

					return areasArray;
				}

				rman.runCommand("Deselect");
				rman.runCommand("Delete");		
			}
		}catch (Exception e){
			//fall through
		}

		float[] defaultVal = new float[1];
		Interpreter.batchMode=false;
		defaultVal[0]=0;
		return defaultVal;
	}

	public static void garbageCollect(){
		System.gc();
	}

	public static void logInImageJ(String message){
		IJ.log(message);
	}

}
