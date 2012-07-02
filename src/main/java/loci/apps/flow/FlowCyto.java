package loci.apps.flow;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.macro.Interpreter;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.text.TextWindow;

import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.util.ArrayList;


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
	private static byte[] dummyData;
	private static Duplicator dup;
	private static float sumIntensityAreasHolder;
	private static long debugTimeStart;

	public static void main(String[] args){
		System.out.println("Please start WiscScan");
	}

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

		try{
			//	IJ.run("Close All");
			WindowManager.closeAllWindows();
			imagej.quit();
			garbageCollect();
		}catch(Exception e){
			garbageCollect();
		}
		imagej = null;
		IJ = null;	

	}

	public static void init(String mode, int width, int height, double pixelsPerMicron) {
		try{
			//			long initialTime = System.nanoTime();
			nSlices=0;
			nSlicesBF=0;
			nSlicesIN=0;
			impBF=new ImagePlus();
			impIN=new ImagePlus();
			imp=new ImagePlus();
			dup = new Duplicator();

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
				//				IJ.run("Set Scale...", "distance="+width+" known="+((double)width/pixelsPerMicron) +" pixel=1 unit=microns");
				//-----------------------FOR DEBUG PURPOSES--------------------//
				//IJ.log("ImageJ started for "+mode+" mode in "+ ((System.nanoTime() - initialTime)/1000) +"us");
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

	public static void showImage(int mode, int width, int height, byte[] imageData) {
		try{
			//			long initialTime = System.nanoTime();
			bp = new ByteProcessor(width,height,imageData, theCM);
			bp.createImage();

			//brightfield
			if (mode == 1) {
				stackBF.addSlice("Slice "+nSlicesBF, bp);
				impBF.setStack("Brightfield Images", stackBF);
				impBF.setSlice(stackBF.getSize());
				impBF.show();
				impBF.unlock();
				nSlicesBF++;
				//-----------------------FOR DEBUG PURPOSES--------------------//
				//IJ.log("brightfield image "+nSlicesBF+" displayed in "+ ((System.nanoTime() - initialTime)/1000000) +"ms");
				//-------------------------------------------------------------//
			}

			//intensity
			else if (mode == 2) {
				stackIN.addSlice("Slice "+nSlicesIN, bp);
				impIN.setStack("Intensity Images", stackIN);
				impIN.setSlice(stackIN.getSize());
				impIN.show();		
				impIN.unlock();
				nSlicesIN++;
				//-----------------------FOR DEBUG PURPOSES--------------------//
				//IJ.log("intensity image "+nSlicesIN+" displayed in "+ ((System.nanoTime() - initialTime)/1000000) +"ms");
				//-------------------------------------------------------------//
			}

			//default
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
	public static boolean foundParticle(boolean isIntensityImage, boolean excludeOnEdge, double thresholdMin, int sizeMin, float compareTOLow, float compareTOHigh){

		//-----------------------FOR DEBUG PURPOSES--------------------//
		//long initialTime = System.nanoTime();
		//IJ.log("Gating method started on slice "+nSlicesIN+" at \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
		//-------------------------------------------------------------//

		float sumIntensity=0;
		Interpreter.batchMode=true;

		try{
			float[] summedPixelAreasArray;
			if(isIntensityImage){				
				try{
					//inWiscScanMode(ImagePlus imageToAnalyze, boolean isIntensity, boolean excludeOnEdge, double threshMin, int minSize)
					summedPixelAreasArray=Find_Particle_Areas.inWiscScanMode(dup.run(impIN, nSlicesIN, nSlicesIN), true, excludeOnEdge, thresholdMin, 0);
					for (int i = 0; i < summedPixelAreasArray.length; i++){
						if(summedPixelAreasArray[i] < 16350)
							sumIntensity += summedPixelAreasArray[i];
					}

					//-----------------------FOR DEBUG PURPOSES--------------------//
					//IJ.log("plugin finished on intensity image "+nSlicesIN+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
					//-------------------------------------------------------------//

				}catch(Exception e){ 
					summedPixelAreasArray=Find_Particle_Areas.inWiscScanMode(dup.run(imp, nSlices, nSlices), true, excludeOnEdge, thresholdMin, 0);
					for (int i = 0; i < summedPixelAreasArray.length; i++){
						if(summedPixelAreasArray[i] < 16350)
							sumIntensity += summedPixelAreasArray[i];
					}

					//-----------------------FOR DEBUG PURPOSES--------------------//
					//IJ.log("plugin finished on ISLET DEFAULT image "+nSlicesIN+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
					//-------------------------------------------------------------//

				}
			}
			else {
				try{
					summedPixelAreasArray=Find_Particle_Areas.inWiscScanMode(dup.run(impBF, nSlicesBF, nSlicesBF), false, excludeOnEdge, thresholdMin, sizeMin);

					//-----------------------FOR DEBUG PURPOSES--------------------//
					//IJ.log("plugin finished on brightfield image "+nSlicesBF+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
					//-------------------------------------------------------------//

				}catch(Exception e){
					summedPixelAreasArray=Find_Particle_Areas.inWiscScanMode(dup.run(imp, nSlices, nSlices), false, excludeOnEdge, thresholdMin, sizeMin);

					//-----------------------FOR DEBUG PURPOSES--------------------//
					//IJ.log("plugin finished on ISLET DEFAULT image "+nSlicesIN+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
					//-------------------------------------------------------------//

				}
			}

			//-----------------------FOR DEBUG PURPOSES--------------------//
			//IJ.log("particle areas calculated in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
			//-------------------------------------------------------------//

			if (isIntensityImage){
				if((sumIntensity >= compareTOLow) && (sumIntensity <= compareTOHigh))
					return true;
			}
			else{
				for(int i=0; i<summedPixelAreasArray.length;i++){
					if(((summedPixelAreasArray[i]*0.85) >= compareTOLow) && ((summedPixelAreasArray[i]*0.85) <= compareTOHigh)){	//CORRECTION FACTOR OF 0.85

						//-----------------------FOR DEBUG PURPOSES--------------------//
						//IJ.log("gating boolean -TRUE- calculated in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
						//-------------------------------------------------------------//
						//-----------------------FOR DEBUG PURPOSES--------------------//
						//IJ.log("_");
						//-------------------------------------------------------------//

						return true;					
					}
				}
			}

			//-----------------------FOR DEBUG PURPOSES--------------------//
			//IJ.log("gating boolean -FALSE- calculated in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
			//-------------------------------------------------------------//
			//-----------------------FOR DEBUG PURPOSES--------------------//
			//IJ.log("_");
			//-------------------------------------------------------------//
			return false;
		}catch (Exception e){
			IJ.log("Problem in getting particle areas");
			IJ.log(e.getMessage());
		}
		return false;
	}

	@SuppressWarnings("static-access")
	public static void calcTrialRatio(int sizeMin, double thresholdMin){
		ImagePlus impIN2=new ImagePlus(), tempimp=new ImagePlus();
		ImageStack stackIN2 = new ImageStack(128, 128, theCM);

		float avgBF=0, medBF=0, minBF=0, maxBF=0, avgIN=0, medIN=0, minIN=0, maxIN=0;
		TextWindow twindow = new TextWindow("Estimated Ratio Results...", "", "", 800, 300);

		try{
			//find the particles in BF image, store min max avg and median
			IJ.log("brightfield image area calculation started...");
			impBF.setSlice(1);
			//		IJ.run(impBF, "Find Particle Areas", "channel=Brightfield threshold_minimum=170 size_minimum="+sizeMin+" run_plugin_over_entire_stack");
			float[] resultsArray = Find_Particle_Areas.analyzeFullStackInWiscScanMode(impBF, "brightfield", thresholdMin, sizeMin, false);
			if(resultsArray!=null){
				if (resultsArray.length!=0){
					maxBF = resultsArray[3];
					minBF = resultsArray[2];
					medBF = resultsArray[1];
					avgBF = resultsArray[0];
					IJ.log(maxBF + " " + minBF + " " + medBF + " " + avgBF);
				} else IJ.log("getResults returns an array of lenght 0");
			} else IJ.log("getResults returns null");
			IJ.log("brightfield image areas finished..."); 

			//clone the resultsArray to contain just the slices particles are found in
			IJ.log("getting slice table...");
			float[] foundInSliceArray = new float[resultsArray.length-4];
			for(int i=4; i<resultsArray.length; i++)
				foundInSliceArray[i-4] = resultsArray[i];
			IJ.log("retrieved slice table");

			//duplicate the intensity image stack with ONLY the slices with particles from BF image stack
			//improves accuracy of number since there is lots of scattered noise in realtime collection


			if(foundInSliceArray.length!=0){
				IJ.log("started isolating frames with particles...");
				IJ.log("number of isolated particles: " + foundInSliceArray.length);
				int index;
				for(int i=0; i<foundInSliceArray.length; i++){
					index = (int) foundInSliceArray[i];
					impIN.setSlice(index);
					tempimp = dup.run(impIN, index, index);
					stackIN2.addSlice("slice "+ index, tempimp.getProcessor());
					impIN2.setStack("Particles", stackIN2);
					impIN2.setSlice(stackIN2.getSize());
					impIN2.show();
					impIN2.unlock();
				}				

				//then repeat the med avg max and min calculations on this substack
				IJ.log("intensity image area calculation started...");
				impIN2.setSlice(1);
				IJ.run(impIN2, "Find Particle Areas", "channel=Intensity threshold_minimum=20 size_minimum=0 run_plugin_over_entire_stack");
				resultsArray = Find_Particle_Areas.analyzeFullStackInWiscScanMode(impIN2, "intensity", thresholdMin, sizeMin, false);
				if(resultsArray!=null && resultsArray.length!=0){
					maxIN = resultsArray[3];
					minIN = resultsArray[2];
					medIN = resultsArray[1];
					avgIN = resultsArray[0];
				}
				IJ.log("intensity image areas finished...");

				impIN2.flush();
				impIN2.close();
				tempimp.flush();
				tempimp.close();
				stackIN2=null;
				twindow.append("INTENSITY Average: "+avgIN);
				twindow.append("INTENSITY Median: "+medIN);
				twindow.append("INTENSITY Minimum: "+minIN);
				twindow.append("INTENSITY Maximum: "+maxIN);
				twindow.append("--------------------------------");
				twindow.append("BRIGHTFIELD Average: "+avgBF);
				twindow.append("BRIGHTFIELD Median: "+medBF);
				twindow.append("BRIGHTFIELD Minimum: "+minBF);
				twindow.append("BRIGHTFIELD Maximum: "+maxBF);
				twindow.append("--------------------------------");
				twindow.append("AVERAGE pixel ratio: "+avgIN/avgBF);
				twindow.append("MEDIAN pixel ratio: "+medIN/medBF);		
				twindow.append("MINIMUM pixel ratio: "+minIN/maxBF);
				twindow.append("MAXIMUM pixel ratio: "+maxIN/minBF);
			}
			else{
				IJ.log("foundInSliceArray has zero size");
				twindow.append("No particles found to calculate ratio.");
			}


		} catch (Exception e){
			System.err.println(e.getMessage());
			System.err.println(e.getStackTrace());
			IJ.log("error calculating trial ratio: "+e.getMessage());
			IJ.log(e.getMessage() + "....." + e.getCause());
			IJ.log(e.getStackTrace().toString());
		} catch (Throwable e){
			IJ.log("throwable error");
			IJ.log(e.getMessage().toString());
		}
	}

	public static void calcTrialRatio2(int sizeMin, int threshMin){

	}

	@SuppressWarnings("static-access")
	public static boolean getRatioBoolean(boolean isIntensityImage, boolean excludeOnEdge, double thresholdMin, int sizeMin, float compareTOLow, float compareTOHigh){

		//-----------------------FOR DEBUG PURPOSES--------------------//
		//long initialTime = System.nanoTime();
		//IJ.log("Pixel areas summing method started on slice "+nSlicesIN+" at \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
		//-------------------------------------------------------------//
		Interpreter.batchMode=true;

		try{
			return Find_Particle_Areas.ratioModeInWiscScan(impBF, impIN, thresholdMin, sizeMin, excludeOnEdge, compareTOLow, compareTOHigh, nSlicesBF);
		}catch (Exception e){
			IJ.log("Problem in getting particle RATIO boolean");
			IJ.log(e.getMessage());
		}

		//-----------------------FOR DEBUG PURPOSES--------------------//
		//IJ.log("plugin finished FALSE on brightfield ratio image "+nSlicesBF+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
		//-------------------------------------------------------------//
		//-----------------------FOR DEBUG PURPOSES--------------------//
		//IJ.log("_");
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
