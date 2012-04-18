package loci.apps.flow;

import ij.IJ;
import ij.ImageJ;
import ij.ImageJApplet;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.macro.Interpreter;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;

import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.io.IOException;


public class FlowCyto {

	private static ImageJ imagej;
	private static IJ IJ;
	private static ImagePlus imp, impBF, impIN;
	private static ImageStack stack, stackBF, stackIN;
	private static int nSlices, nSlicesBF, nSlicesIN;
//	private static ByteProcessor bp;
	private static ShortProcessor sp;
	private static ColorModel theCM;	
	//	private static String s_Name, s_Experiment, s_Params, s_Date, tempImageName;
	private static double pixelMicronSquared;
	private static short[] dummyData;
	private static Duplicator dup;
	private static float sumIntensityAreasHolder;
	private static long debugTimeStart;


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
		sp=null;

		try{
			ResultsTable.getResultsTable().reset();
			RoiManager.getInstance2().dispose();		
		}catch(Exception e){
			//fall through - happens when RoiManager isnt init'ed...
		}

		IJ = null;	

		try{
			//	IJ.run("Close All");
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
			dup = new Duplicator();
			//		s_Date = new java.text.SimpleDateFormat("MM.dd.yyyy hh:mm:ss").format(new java.util.Date());
			byte[] r = new byte[256];
			for(int ii=0 ; ii<256 ; ii++)
				r[ii]=(byte)ii;

			theCM = new IndexColorModel(8, 256, r,r,r);		//8, 256, r,g,b...but r,g,and b are all byte[256], exactly the same...
			ShortProcessor initSP = new ShortProcessor(width,height); 			
			dummyData = new short[width*height];
			sp = new ShortProcessor(width,height,dummyData, theCM);
			sp.createImage();

			mode=mode.toLowerCase();

			if ("brightfield".equals(mode)) {
				impBF = new ImagePlus("Brightfield images",	initSP);
				stackBF = new ImageStack(width,height, theCM);
				impBF.show();
				impBF.unlock();

				stackBF.addSlice("Slice "+nSlicesBF, sp);
				impBF.setStack("Brightfield images", stackBF);
				impBF.setSlice(1);	
				impBF.unlock();
			}
			else if ("intensity".equals(mode)) {
				impIN = new ImagePlus("Intensity images", initSP);
				stackIN = new ImageStack(width,height, theCM);
				impIN.show();
				impIN.unlock();

				stackIN.addSlice("Slice "+nSlicesIN, sp);
				impIN.setStack("Intensity images", stackIN);
				impIN.setSlice(1);
				impIN.unlock();
			}
			else if ("both".equals(mode)) {
				impBF = new ImagePlus("Brightfield images",	initSP);
				stackBF = new ImageStack(width,height, theCM);
				impBF.show();
				impBF.unlock();

				stackBF.addSlice("Slice "+nSlicesBF, sp);
				impBF.setStack("Brightfield images", stackBF);
				impBF.setSlice(1);	
				impBF.unlock();

				impIN = new ImagePlus("Intensity images", initSP);
				stackIN = new ImageStack(width,height, theCM);
				impIN.show();
				impIN.unlock();

				stackIN.addSlice("Slice "+nSlicesIN, sp);
				impIN.setStack("Intensity images", stackIN);
				impIN.setSlice(1);
				impIN.unlock();
			}
			else {
				imp = new ImagePlus("Islet images",	initSP);
				stack = new ImageStack(width,height, theCM);
				imp.show();
				imp.unlock();

				stack.addSlice("Slice "+nSlices, sp);
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
	public static void showImage(int mode, int width, int height, short[] imageData) {
		try{
			long initialTime = System.nanoTime();
		//	bp = new ByteProcessor(width,height,imageData, theCM);
			sp = new ShortProcessor(width, height, imageData, theCM, true);
	//		bp.createImage();

			//brightfield
			if (mode == 1) {
				stackBF.addSlice("Slice "+nSlicesBF, sp);
				impBF.setStack("Brightfield Images", stackBF);
				impBF.setSlice(stackBF.getSize());
				impBF.show();
				impBF.unlock();
				nSlicesBF++;
				//-----------------------FOR DEBUG PURPOSES--------------------//
				IJ.log("brightfield image "+nSlicesBF+" displayed in "+ ((System.nanoTime() - initialTime)/1000000) +"ms");
				//-------------------------------------------------------------//
			}

			//intensity
			else if (mode == 2) {
				stackIN.addSlice("Slice "+nSlicesIN, sp);
				impIN.setStack("Intensity Images", stackIN);
				impIN.setSlice(stackIN.getSize());
				impIN.show();		
				impIN.unlock();
				nSlicesIN++;
				//-----------------------FOR DEBUG PURPOSES--------------------//
				IJ.log("intensity image "+nSlicesIN+" displayed in "+ ((System.nanoTime() - initialTime)/1000000) +"ms");
				//-------------------------------------------------------------//
			}

			//default
			else {
				stack.addSlice("Slice "+nSlices, sp);
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
	public static boolean foundParticle(boolean isIntensityImage, boolean excludeOnEdge, double thresholdMin, int sizeMin, float compareTOLow, float compareTOHigh){

		//-----------------------FOR DEBUG PURPOSES--------------------//
		long initialTime = System.nanoTime();
		IJ.log("Gating method started on slice "+nSlicesIN+" at \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
		//-------------------------------------------------------------//

		float[] summedPixelAreasArray;
		Interpreter.batchMode=true;

		try{
			if(isIntensityImage){				
				try{
					summedPixelAreasArray=Find_Particle_Areas.inWiscScanMode(dup.run(impIN, nSlicesIN, nSlicesIN), true, excludeOnEdge, thresholdMin, sizeMin);

					//-----------------------FOR DEBUG PURPOSES--------------------//
					IJ.log("plugin finished on intensity image "+nSlicesIN+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
					//-------------------------------------------------------------//

				}catch(Exception e){ 
					summedPixelAreasArray=Find_Particle_Areas.inWiscScanMode(dup.run(imp, nSlices, nSlices), true, excludeOnEdge, thresholdMin, sizeMin);

					//-----------------------FOR DEBUG PURPOSES--------------------//
					IJ.log("plugin finished on ISLET DEFAULT image "+nSlicesIN+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
					//-------------------------------------------------------------//

				}
			}
			else {
				try{
					summedPixelAreasArray=Find_Particle_Areas.inWiscScanMode(dup.run(impBF, nSlicesBF, nSlicesBF), false, excludeOnEdge, thresholdMin, sizeMin);

					//-----------------------FOR DEBUG PURPOSES--------------------//
					IJ.log("plugin finished on brightfield image "+nSlicesBF+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
					//-------------------------------------------------------------//

				}catch(Exception e){
					summedPixelAreasArray=Find_Particle_Areas.inWiscScanMode(dup.run(imp, nSlices, nSlices), false, excludeOnEdge, thresholdMin, sizeMin);

					//-----------------------FOR DEBUG PURPOSES--------------------//
					IJ.log("plugin finished on ISLET DEFAULT image "+nSlicesIN+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
					//-------------------------------------------------------------//

				}
			}

			//-----------------------FOR DEBUG PURPOSES--------------------//
			IJ.log("particle areas calculated in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
			//-------------------------------------------------------------//

			for(int i=0; i<summedPixelAreasArray.length;i++)
			{
				if(summedPixelAreasArray[i] >= compareTOLow && summedPixelAreasArray[i] <= compareTOHigh){

					//-----------------------FOR DEBUG PURPOSES--------------------//
					IJ.log("gating boolean -TRUE- calculated in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
					//-------------------------------------------------------------//
					//-----------------------FOR DEBUG PURPOSES--------------------//
					IJ.log("_");
					//-------------------------------------------------------------//

					return true;					
				}



			}

			//-----------------------FOR DEBUG PURPOSES--------------------//
			IJ.log("gating boolean -FALSE- calculated in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
			//-------------------------------------------------------------//
			//-----------------------FOR DEBUG PURPOSES--------------------//
			IJ.log("_");
			//-------------------------------------------------------------//
			return false;
		}catch (Exception e){
			IJ.log("Problem in getting particle areas");
			IJ.log(e.getMessage());
		}
		return false;
	}

	@SuppressWarnings("static-access")
	public static boolean getRatioBoolean(boolean isIntensityImage, boolean excludeOnEdge, double thresholdMin, int sizeMin, float compareTOLow, float compareTOHigh){

		//-----------------------FOR DEBUG PURPOSES--------------------//
		long initialTime = System.nanoTime();
		IJ.log("Pixel areas summing method started on slice "+nSlicesIN+" at \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
		//-------------------------------------------------------------//

		float[] summedPixelAreasArray;
		Interpreter.batchMode=true;

		try{
			if(isIntensityImage){				
				try{
					sumIntensityAreasHolder=0;
					summedPixelAreasArray=Find_Particle_Areas.inWiscScanMode(dup.run(impIN, nSlicesIN, nSlicesIN), true, excludeOnEdge, thresholdMin, sizeMin);
					for(int i=0; i<summedPixelAreasArray.length; i++){
						sumIntensityAreasHolder+=summedPixelAreasArray[i];
					}	

					//-----------------------FOR DEBUG PURPOSES--------------------//
					IJ.log("plugin finished on intensity image "+nSlicesIN+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
					//-------------------------------------------------------------//
					//-----------------------FOR DEBUG PURPOSES--------------------//
					IJ.log("_");
					//-------------------------------------------------------------//

					return false;

				}catch(Exception e){ 
					summedPixelAreasArray=Find_Particle_Areas.inWiscScanMode(dup.run(imp, nSlices, nSlices), true, excludeOnEdge, thresholdMin, sizeMin);
					for(int i=0; i<summedPixelAreasArray.length; i++){
						sumIntensityAreasHolder+=summedPixelAreasArray[i];
					}

					//-----------------------FOR DEBUG PURPOSES--------------------//
					IJ.log("plugin finished on ISLET DEFAULT image "+nSlicesIN+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
					//-------------------------------------------------------------//
					//-----------------------FOR DEBUG PURPOSES--------------------//
					IJ.log("_");
					//-------------------------------------------------------------//

					return false;
				}
			}
			else {
				try{		
					float sumBFPixelAreas=0;
					summedPixelAreasArray=Find_Particle_Areas.inWiscScanMode(dup.run(impBF, nSlicesBF, nSlicesBF), false, excludeOnEdge, thresholdMin, sizeMin);
					for(int i=0; i<summedPixelAreasArray.length; i++){
						sumBFPixelAreas+=summedPixelAreasArray[i];
					}
					if((sumBFPixelAreas!=0) && ((sumIntensityAreasHolder/sumBFPixelAreas) >= compareTOLow) && ((sumIntensityAreasHolder/sumBFPixelAreas) <= compareTOHigh)){

						//-----------------------FOR DEBUG PURPOSES--------------------//
						IJ.log("plugin finished -TRUE- on brightfield ratio image "+nSlicesBF+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
						//-------------------------------------------------------------//
						//-----------------------FOR DEBUG PURPOSES--------------------//
						IJ.log("_");
						//-------------------------------------------------------------//

						return true;
					}

				}catch(Exception e){
					float sumBFPixelAreas=0;
					summedPixelAreasArray=Find_Particle_Areas.inWiscScanMode(dup.run(imp, nSlices, nSlices), false, excludeOnEdge, thresholdMin, sizeMin);
					for(int i=0; i<summedPixelAreasArray.length; i++){
						sumBFPixelAreas+=summedPixelAreasArray[i];
					}
					if((sumBFPixelAreas!=0) && ((sumIntensityAreasHolder/sumBFPixelAreas) >= compareTOLow) && ((sumIntensityAreasHolder/sumBFPixelAreas) <= compareTOHigh)){

						//-----------------------FOR DEBUG PURPOSES--------------------//
						IJ.log("plugin finished TRUE on ISLET DEFAULT image "+nSlicesIN+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
						//-------------------------------------------------------------//
						//-----------------------FOR DEBUG PURPOSES--------------------//
						IJ.log("_");
						//-------------------------------------------------------------//

						return true;
					}

				}
			}

		}catch (Exception e){
			IJ.log("Problem in getting particle areas");
			IJ.log(e.getMessage());
		}

		//-----------------------FOR DEBUG PURPOSES--------------------//
		IJ.log("plugin finished FALSE on brightfield ratio image "+nSlicesBF+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
		//-------------------------------------------------------------//
		//-----------------------FOR DEBUG PURPOSES--------------------//
		IJ.log("_");
		//-------------------------------------------------------------//

		return false;
	}

	public static void garbageCollect(){
		System.gc();
	}

	@SuppressWarnings("static-access")
	public static void logInImageJ(String message){
		IJ.log(message);
	}
	
	//next two methods are intended to be called from WiscScan's C++ components
	@SuppressWarnings("static-access")
	public static void createDebugTimeStartPoint(String message){
		debugTimeStart=System.nanoTime();
		IJ.log(message+" --START noted at \t \t \t"+ (debugTimeStart/1000000) +"ms");
	}

	@SuppressWarnings("static-access")
	public static void createDebugTimeCheckPoint(String message){
		IJ.log(message+" --CHECKPOINT noted at \t \t \t"+ ((System.nanoTime() - debugTimeStart)/1000000) +"ms");
	}


}
