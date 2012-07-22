package loci.apps.flow;

import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.io.IOException;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.Roi;
import ij.macro.Interpreter;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.text.TextWindow;

public class FlowCyto {

	private static IJ IJ;
	private static ImageJ imagej;
	private static ImagePlus imp, impBF, impIN, maskBF, maskIN, tempBF, tempInt;
	private static ImageProcessor tempIP;
	private static ImageStack stack, bfStack, intStack;//, bfMaskStack, intMaskStack;
	private static ImageStatistics stats;
	private static ByteProcessor bp;
	private static ColorModel theCM;	
	private static Duplicator duplicator;
	private static ResultsTable rt;
	private static RoiManager rm;
	private static Roi tempRoi;
	private static TextWindow twindow;
	private static double pixelsPerMicronSquared;
	private static int nSlices, nSlicesBF, nSlicesIN;
	private static long debugTimeStart;
	private static Find_Particle_Areas particleAreas;
	private static ThresholdToSelection tts;

	public static void main(String[] args){
		startImageJ();
		IJ.log("lol");
		@SuppressWarnings("static-access")
		ImagePlus bfImage = IJ.openImage("C:/Users/Ajeet/Desktop/s2-bf.tif");
		@SuppressWarnings("static-access")
		ImagePlus intImage = IJ.openImage("C:/Users/Ajeet/Desktop/s2-int.tif");
		IJ.log("haha");
		duplicator = new Duplicator();
		init("both", bfImage.getHeight(), bfImage.getWidth(), 0.180028);
		nSlices++; nSlicesBF++; nSlicesIN++;
		impBF = duplicator.run(bfImage, 295, 295);
		impIN = duplicator.run(intImage, 295, 295);
		impBF.show();
		impIN.show();

		boolean haha = getRatioBoolean(true, 30, 300, (float) 0.01, 1, 2.2);
		System.out.println("boolean = " + haha);
	}

	@SuppressWarnings("static-access")
	public static void startImageJ(){
		IJ = new IJ();
		imagej = new ImageJ();
		IJ.log(IJ.freeMemory().toString());
	}

	@SuppressWarnings("static-access")
	public static void closeAllWindows(){
		try{
		IJ.log(IJ.freeMemory().toString());
		if(imp!=null) imp.close();
		if(impBF!=null) impBF.close();
		if(impIN!=null) impIN.close();
		if(maskBF!=null) maskBF.close();
		if(maskIN!=null) maskIN.close();
		if(tempBF!=null) tempBF.close();
		if(tempInt!=null) tempInt.close();
		if(twindow!=null) twindow.close();
		
		bp = null;
		rt = null;
		rm = null;
		stack = null;
		bfStack = null;
		intStack = null;
//		bfMaskStack = null;
//		intMaskStack = null;
		tempIP = null;
		bp = null;
		theCM = null;
		tts = null;
		particleAreas = null;
		duplicator = null;
		tempRoi = null;
		IJ.log(IJ.freeMemory().toString());
//		if(imagej.quitting()){
//			IJ.log("I1");
//			imagej.quit();
//		}
		IJ.log("J");


		garbageCollect();
		imagej = null;
		IJ = null;	
		} catch(Throwable e){
			IJ.log("Error closing all windows");
			IJ.log(e.getMessage().toString());
		}
	}

	public static void openFile(String filename, double pixelsPerMicron) throws IOException{
		startImageJ();
		ij.IJ.run("Bio-Formats Importer", "open=["+filename+"] autoscale color_mode=Default view=Hyperstack stack_order=XYCZT");
	}

	public static void garbageCollect(){
		System.gc();
	}

	public static void logInImageJ(String message){
		ij.IJ.log(message);
	}

	public static void createDebugTimeStartPoint(String message){
		debugTimeStart=System.nanoTime();
		ij.IJ.log(message+" --START noted at \t \t \t"+ (debugTimeStart/1000000) +"ms");
	}

	public static void createDebugTimeCheckPoint(String message){
		ij.IJ.log(message+" --CHECKPOINT noted at \t \t \t"+ ((System.nanoTime() - debugTimeStart)/1000000) +"ms");
	}


	@SuppressWarnings("static-access")
	public static void init(String mode, int width, int height, double pixelsPerMicron){

		nSlices = 0; nSlicesBF = 0; nSlicesIN = 0;
		duplicator = new Duplicator();

		byte[] r = new byte[256];
		for(int ii=0 ; ii<256 ; ii++)
			r[ii]=(byte)ii;
		theCM = new IndexColorModel(8, 256, r,r,r);

		ByteProcessor initBP = new ByteProcessor(width,height); 			
		byte[] dummyData = new byte[width*height];
		bp = new ByteProcessor(width,height,dummyData, theCM);
		bp.createImage();

		if(mode.equalsIgnoreCase("brightfield")){
			impBF = new ImagePlus("Brightfield Images", initBP);
			bfStack = new ImageStack(width, height, theCM);
			impBF.unlock();

			bfStack.addSlice("Slice "+nSlicesBF, bp);
			impBF.setStack("Brightfield images", bfStack);
			impBF.setSlice(1);	
			impBF.unlock();

			bfStack = new ImageStack(width, height, theCM);
			Interpreter.batchMode=false;
			impBF.show();
		}
		else if (mode.equalsIgnoreCase("intensity")){
			impIN = new ImagePlus("Intensity Images", initBP);
			intStack = new ImageStack(width, height, theCM);
			impIN.unlock();

			intStack.addSlice("Slice "+nSlicesIN, bp);
			impIN.setStack("Intensity images", bfStack);
			impIN.setSlice(1);	
			impIN.unlock();

			intStack = new ImageStack(width, height, theCM);
			Interpreter.batchMode=false;
			impIN.show();
		}
		else if (mode.equalsIgnoreCase("both")){
			impBF = new ImagePlus("Brightfield Images", initBP);
			bfStack = new ImageStack(width, height, theCM);
			impBF.unlock();

			bfStack.addSlice("Slice "+nSlicesBF, bp);
			impBF.setStack("Brightfield images", bfStack);
			impBF.setSlice(1);	
			impBF.unlock();

			impIN = new ImagePlus("Intensity Images", initBP);
			intStack = new ImageStack(width, height, theCM);

			impIN.unlock();

			intStack.addSlice("Slice "+nSlicesIN, bp);
			impIN.setStack("Intensity images", bfStack);
			impIN.setSlice(1);	
			impIN.unlock();

			twindow = new TextWindow("RATIO of Found Particles", "Slice \t Brightfield Area \t Intensity Area \t RATIO \t Status", "", 800, 300);
			maskBF = new ImagePlus("Brightfield Particle Masks");
			maskIN = new ImagePlus("Intensity Particle Masks");
			tempBF = new ImagePlus();
			tempInt = new ImagePlus();
			rt = new ResultsTable();
			tts = new ThresholdToSelection();

			bfStack = new ImageStack(width, height, theCM);
			intStack = new ImageStack(width, height, theCM);
			Interpreter.batchMode=false;
			impBF.show();
			impIN.show();
		}
		else {
			imp = new ImagePlus("Images", initBP);
			stack = new ImageStack(width, height, theCM);
			imp.unlock();

			stack.addSlice("Slice "+nSlices, bp);
			imp.setStack("Images", stack);
			imp.setSlice(1);	
			imp.unlock();

			stack = new ImageStack(width, height, theCM);
			Interpreter.batchMode=false;
			imp.show();
		}

		pixelsPerMicronSquared = pixelsPerMicron>0? pixelsPerMicron:0.180028*0.180028;
	}

	@SuppressWarnings("static-access")
	public static void showImage(String mode, int width, int height, byte[] imageData){
		try{
			bp = new ByteProcessor(width, height, imageData, theCM);
			bp.createImage();

			if(mode.equalsIgnoreCase("brightfield")){
				bfStack.addSlice("Slice "+nSlicesBF, bp);
				impBF.setStack("Brightfield Images", bfStack);
				impBF.setSlice(bfStack.getSize());
				Interpreter.batchMode=false;
				impBF.show();
				Interpreter.batchMode=true;
				impBF.unlock();
				nSlicesBF++;
			}
			else if (mode.equalsIgnoreCase("intensity")){
				intStack.addSlice("Slice "+nSlicesIN, bp);
				impIN.setStack("Intensity Images", intStack);
				impIN.setSlice(intStack.getSize());
				Interpreter.batchMode=false;
				impIN.show();
				Interpreter.batchMode=true;
				impIN.unlock();
				nSlicesIN++;
			}
			else {
				stack.addSlice("Slice "+nSlices, bp);
				imp.setStack(stack);
				imp.setSlice(stack.getSize());
				Interpreter.batchMode=false;
				imp.show();
				Interpreter.batchMode=true;
				imp.unlock();
				nSlices++;
			}

		} catch(Throwable e){
			IJ.log("Error with showing image");
			e.printStackTrace();
		}
	}

	@SuppressWarnings("static-access")
	public static boolean foundParticle(boolean isIntensityImage, boolean excludeOnEdge, double thresholdMin, int sizeMin, float compareTOLow, float compareTOHigh, double gaussianSigma){
		try{
			ImagePlus tempImp = isIntensityImage? duplicator.run(impIN, nSlicesIN, nSlicesIN):duplicator.run(impBF, nSlicesBF, nSlicesBF);
			String mode = isIntensityImage? "intensity":"brightfield";
			particleAreas = new Find_Particle_Areas(tempImp, null, null, mode, thresholdMin, gaussianSigma, sizeMin, excludeOnEdge, false);
			float[] summedPixelAreasArray = particleAreas.analyzeIndividualParticles();
			float summedPixelAreas=0;

			if(isIntensityImage) {
				for(int i=0; i<summedPixelAreasArray.length; i++)
					summedPixelAreas += summedPixelAreasArray[i] < ((tempImp.getWidth()*tempImp.getHeight())-100)? summedPixelAreasArray[i]:0;
					tempImp.close();
					return (summedPixelAreas >= compareTOLow && summedPixelAreas <= compareTOHigh);
			}
			tempImp.close();
			for(int i=0; i<summedPixelAreasArray.length; i++){
				if (summedPixelAreasArray[i] >= compareTOLow && summedPixelAreasArray[i] <= compareTOHigh)
					return true;
			}
		} catch(Throwable e){
			IJ.log("Problem with calculating particles");
			e.printStackTrace();
		}
		return false;
	}

	@SuppressWarnings("static-access")
	public static boolean getRatioBoolean(boolean excludeOnEdge, double thresholdMin, int sizeMin, float compareTOLow, float compareTOHigh, double gaussianSigma){
		try{
			Interpreter.batchMode=true;
			tempInt = duplicator.run(impIN, nSlicesIN, nSlicesIN);
			tempBF = duplicator.run(impBF, nSlicesBF, nSlicesBF);
			particleAreas = new Find_Particle_Areas(null, tempBF, tempInt, null, thresholdMin, gaussianSigma, sizeMin, excludeOnEdge, true);
			ImagePlus[] masks = particleAreas.createRatioMask();
			tempInt.close();
			tempBF.close();
			tempBF = masks[0];
			tempInt = masks[1];
			masks[0].close();
			masks[1].close();
			ImageStack intMaskStack = new ImageStack(tempInt.getWidth(), tempInt.getHeight(), theCM);
			ImageStack bfMaskStack = new ImageStack(tempBF.getWidth(), tempBF.getHeight(), theCM);

			double ratio=0; double bfAreas=0; double intAreas=0;

			//GET total brightfield particles' area
			tempIP = tempBF.getProcessor();
			//this step is necessary to reset the processor's threshold for the ThresholdToSelection below
			tempIP.setThreshold(1, 255, ImageProcessor.BLACK_AND_WHITE_LUT);
			tempRoi = tts.convert(tempIP);
			if(tempRoi!=null) {
				tempIP.setRoi(tempRoi);
				stats = ImageStatistics.getStatistics(tempIP, Measurements.AREA, tempBF.getCalibration());
				bfAreas = stats.area;
			}

			particleAreas.addImageToStack(maskBF, tempIP, bfMaskStack);

			//GET total intensity particles' area
			tempIP = tempInt.getProcessor();
			tempIP.setThreshold(1, 255, ImageProcessor.BLACK_AND_WHITE_LUT);
			tempRoi = tts.convert(tempIP);
			if(tempRoi!=null) {
				tempIP.setRoi(tempRoi);
				stats = ImageStatistics.getStatistics(tempIP, Measurements.AREA, tempBF.getCalibration());
				intAreas = stats.area;
			}

			particleAreas.addImageToStack(maskIN, tempIP, intMaskStack);
			
			ratio = bfAreas==0? 0:intAreas/bfAreas;
			Interpreter.batchMode=false;
			maskBF.show();
			maskIN.show();
//			intMaskStack = null;
//			bfMaskStack = null;
			if(!(ratio>=compareTOLow && ratio<= compareTOHigh)){
				if (ratio!=0)
					twindow.append(nSlicesBF + "\t" + bfAreas + "\t" + intAreas + "\t" + ratio);
			}else{
				twindow.append(nSlicesBF + "\t" + bfAreas + "\t" + intAreas + "\t" + ratio + "\t" + "Particle COLLECTED");
				return true;
			}
		}catch(Throwable e){
			IJ.log("Error in realtime ratio boolean " + e.getMessage());
			e.printStackTrace();
		}
		return false;
	}

	public static void calcTrialRatio(){

	}

}



















//package loci.apps.flow;
////WindowManager.addWindow(iamge.getWindow());
//import ij.IJ;
//import ij.ImageJ;
//import ij.ImagePlus;
//import ij.ImageStack;
//import ij.WindowManager;
//import ij.macro.Interpreter;
//import ij.measure.ResultsTable;
//import ij.plugin.Duplicator;
//import ij.plugin.frame.RoiManager;
//import ij.process.ByteProcessor;
//import ij.text.TextWindow;
//
//import java.awt.image.ColorModel;
//import java.awt.image.IndexColorModel;
//import java.io.IOException;
//
//
//public class FlowCyto {
//
//	private static IJ IJ;
//	private static ImageJ imagej;
//	private static ImagePlus imp, impBF, impIN, maskIN, maskBF;
//	private static ImageStack stack, stackBF, stackIN, intMaskStack, bfMaskStack;
//	private static ByteProcessor bp;
//	private static ColorModel theCM;	
//	private static Duplicator dup;
//	private static ResultsTable rt;
//	private static RoiManager rm;
//	private static TextWindow twindow;
//	private static int nSlices, nSlicesBF, nSlicesIN;
//	private static double pixelMicronSquared;
//	private static byte[] dummyData;
//	private Find_Particle_Areas findParticleAreas;
//	private static long debugTimeStart;
//
//	public static void main(String[] args){
//		System.out.println("Please start WiscScan");
//	}
//
//	@SuppressWarnings("static-access")
//	public static void startImageJ() {
//		IJ = new IJ();
//		imagej = IJ.getInstance();
//		if(imagej==null || (imagej!=null && !imagej.isShowing()))
//			new ImageJ();
//	}
//
//	public static void closeAllWindows() {
//		try{
//			imp.close();
//			impBF.close();
//			impIN.close();
//			bp=null;
//
//			ResultsTable.getResultsTable().reset();
//			RoiManager.getInstance2().dispose();		
//		}catch(Exception e){
//			//fall through - happens when RoiManager isnt init'ed...
//		}
//		try{
//			//	IJ.run("Close All");
//			WindowManager.closeAllWindows();
//			imagej.quit();
//			garbageCollect();
//		}catch(Exception e){
//			garbageCollect();
//		}
//		imagej = null;
//		IJ = null;	
//
//	}
//
//	public static void init(String mode, int width, int height, double pixelsPerMicron) {
//		try{
//			//			long initialTime = System.nanoTime();
//			nSlices=0;
//			nSlicesBF=0;
//			nSlicesIN=0;
//			impBF=new ImagePlus();
//			impIN=new ImagePlus();
//			imp=new ImagePlus();
//			dup = new Duplicator();
//			rt = new ResultsTable();	
//			rm = new RoiManager(true);	
//
//			byte[] r = new byte[256];
//			for(int ii=0 ; ii<256 ; ii++)
//				r[ii]=(byte)ii;
//
//			theCM = new IndexColorModel(8, 256, r,r,r);
//			ByteProcessor initBP = new ByteProcessor(width,height); 			
//			dummyData = new byte[width*height];
//			bp = new ByteProcessor(width,height,dummyData, theCM);
//			bp.createImage();
//
//			mode=mode.toLowerCase();
//
//			if ("brightfield".equals(mode)) {
//				impBF = new ImagePlus("Brightfield images",	initBP);
//				stackBF = new ImageStack(width,height, theCM);
//				impBF.show();
//				impBF.unlock();
//
//				stackBF.addSlice("Slice "+nSlicesBF, bp);
//				impBF.setStack("Brightfield images", stackBF);
//				impBF.setSlice(1);	
//				impBF.unlock();
//				WindowManager.addWindow(impBF.getWindow());
//			}
//			else if ("intensity".equals(mode)) {
//				impIN = new ImagePlus("Intensity images", initBP);
//				stackIN = new ImageStack(width,height, theCM);
//				impIN.show();
//				impIN.unlock();
//
//				stackIN.addSlice("Slice "+nSlicesIN, bp);
//				impIN.setStack("Intensity images", stackIN);
//				impIN.setSlice(1);
//				impIN.unlock();
//				WindowManager.addWindow(impIN.getWindow());
//			}
//			else if ("both".equals(mode)) {
//				impBF = new ImagePlus("Brightfield images",	initBP);
//				stackBF = new ImageStack(width,height, theCM);
//				impBF.show();
//				impBF.unlock();
//
//				stackBF.addSlice("Slice "+nSlicesBF, bp);
//				impBF.setStack("Brightfield images", stackBF);
//				impBF.setSlice(1);	
//				impBF.unlock();
//
//				impIN = new ImagePlus("Intensity images", initBP);
//				stackIN = new ImageStack(width,height, theCM);
//				impIN.show();
//				impIN.unlock();
//
//				stackIN.addSlice("Slice "+nSlicesIN, bp);
//				impIN.setStack("Intensity images", stackIN);
//				impIN.setSlice(1);
//				impIN.unlock();
//				try{
//					twindow.close(true);		//if previous text window is open then this will prompt to save and close...will throw excpetion if first time
//				} catch (Exception e){
//					//fall through				//if no such text window exists yet, fall through and create one.
//				}
//				twindow = new TextWindow("RATIO of Found Particles", "Slice \t Brightfield Area \t Intensity Area \t RATIO \t Status", "", 800, 300);
//				intMaskStack = new ImageStack(impIN.getWidth(), impIN.getHeight(), theCM);
//				bfMaskStack = new ImageStack(impBF.getWidth(), impBF.getHeight(), theCM);
//				maskBF = new ImagePlus("Cell Outlines");
//				maskIN = new ImagePlus("Cell Intensity inside Outlines");
//
//				IJ.run("Set Measurements...", "area mean min redirect=None decimal=3");
//				WindowManager.addWindow(impBF.getWindow());
//				WindowManager.addWindow(impIN.getWindow());
//				WindowManager.addWindow(maskBF.getWindow());
//				WindowManager.addWindow(maskIN.getWindow());
//
//			}
//			else {
//				imp = new ImagePlus("Islet images",	initBP);
//				stack = new ImageStack(width,height, theCM);
//				imp.show();
//				imp.unlock();
//
//				stack.addSlice("Slice "+nSlices, bp);
//				imp.setStack("Islet images", stack);
//				imp.setSlice(1);	
//				imp.unlock();
//				WindowManager.addWindow(imp.getWindow());
//			}
//			if (pixelsPerMicron > 0){ 
//				pixelMicronSquared = pixelsPerMicron*pixelsPerMicron;
//				//				IJ.run("Set Scale...", "distance="+width+" known="+((double)width/pixelsPerMicron) +" pixel=1 unit=microns");
//				//-----------------------FOR DEBUG PURPOSES--------------------//
//				//IJ.log("ImageJ started for "+mode+" mode in "+ ((System.nanoTime() - initialTime)/1000) +"us");
//				//-------------------------------------------------------------//
//			}
//			else pixelMicronSquared = 0.180028*0.180028;
//		} catch(Exception e){
//			System.err.println("Exception at init method " + e.getLocalizedMessage());
//		}
//
//	}
//
//	@SuppressWarnings("static-access")
//	public static void openFile(String filename, double PixelsPerMicron) throws IOException {
//		startImageJ();
//		IJ.run("Bio-Formats Importer", "open=["+filename+"] autoscale color_mode=Default view=Hyperstack stack_order=XYCZT");
//	}
//
//	public static void showImage(int mode, int width, int height, byte[] imageData) {
//		try{
//			//			long initialTime = System.nanoTime();
//			bp = new ByteProcessor(width,height,imageData, theCM);
//			bp.createImage();
//
//			//brightfield
//			if (mode == 1) {
//				stackBF.addSlice("Slice "+nSlicesBF, bp);
//				impBF.setStack("Brightfield Images", stackBF);
//				impBF.setSlice(stackBF.getSize());
//				impBF.show();
//				impBF.unlock();
//				nSlicesBF++;
//				//-----------------------FOR DEBUG PURPOSES--------------------//
//				//IJ.log("brightfield image "+nSlicesBF+" displayed in "+ ((System.nanoTime() - initialTime)/1000000) +"ms");
//				//-------------------------------------------------------------//
//			}
//
//			//intensity
//			else if (mode == 2) {
//				stackIN.addSlice("Slice "+nSlicesIN, bp);
//				impIN.setStack("Intensity Images", stackIN);
//				impIN.setSlice(stackIN.getSize());
//				impIN.show();		
//				impIN.unlock();
//				nSlicesIN++;
//				//-----------------------FOR DEBUG PURPOSES--------------------//
//				//IJ.log("intensity image "+nSlicesIN+" displayed in "+ ((System.nanoTime() - initialTime)/1000000) +"ms");
//				//-------------------------------------------------------------//
//			}
//
//			//default
//			else {
//				stack.addSlice("Slice "+nSlices, bp);
//				imp.setStack("Islet Images", stack);
//				imp.setSlice(stack.getSize());
//				imp.show();
//				imp.unlock();
//				nSlices++;
//			}
//
//		} catch(Exception e){
//			System.err.println("Error at showImage method " + e.getLocalizedMessage());
//		}
//	}
//
//	@SuppressWarnings("static-access")
//	public static boolean foundParticle(boolean isIntensityImage, boolean excludeOnEdge, double thresholdMin, int sizeMin, float compareTOLow, float compareTOHigh, double gaussianSigma){
//
//		//-----------------------FOR DEBUG PURPOSES--------------------//
//		//long initialTime = System.nanoTime();
//		//IJ.log("Gating method started on slice "+nSlicesIN+" at \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
//		//-------------------------------------------------------------//
//
//		float sumIntensity=0;
//		Interpreter.batchMode=true;
//
//		try{
//			float[] summedPixelAreasArray;
//			if(isIntensityImage){				
//				try{
//					//inWiscScanMode(ImagePlus imageToAnalyze, boolean isIntensity, boolean excludeOnEdge, double threshMin, int minSize)
//					summedPixelAreasArray=Find_Particle_Areas.inWiscScanMode(dup.run(impIN, nSlicesIN, nSlicesIN), true, excludeOnEdge, thresholdMin, 0, gaussianSigma);
//					for (int i = 0; i < summedPixelAreasArray.length; i++){
//						if(summedPixelAreasArray[i] < 16350)
//							sumIntensity += summedPixelAreasArray[i];
//					}
//
//					//-----------------------FOR DEBUG PURPOSES--------------------//
//					//IJ.log("plugin finished on intensity image "+nSlicesIN+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
//					//-------------------------------------------------------------//
//
//				}catch(Exception e){ 
//					summedPixelAreasArray=Find_Particle_Areas.inWiscScanMode(dup.run(imp, nSlices, nSlices), true, excludeOnEdge, thresholdMin, 0, gaussianSigma);
//					for (int i = 0; i < summedPixelAreasArray.length; i++){
//						if(summedPixelAreasArray[i] < 16350)
//							sumIntensity += summedPixelAreasArray[i];
//					}
//
//					//-----------------------FOR DEBUG PURPOSES--------------------//
//					//IJ.log("plugin finished on ISLET DEFAULT image "+nSlicesIN+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
//					//-------------------------------------------------------------//
//
//				}
//			}
//			else {
//				try{
//					summedPixelAreasArray=Find_Particle_Areas.inWiscScanMode(dup.run(impBF, nSlicesBF, nSlicesBF), false, excludeOnEdge, thresholdMin, sizeMin, gaussianSigma);
//
//					//-----------------------FOR DEBUG PURPOSES--------------------//
//					//IJ.log("plugin finished on brightfield image "+nSlicesBF+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
//					//-------------------------------------------------------------//
//
//				}catch(Exception e){
//					summedPixelAreasArray=Find_Particle_Areas.inWiscScanMode(dup.run(imp, nSlices, nSlices), false, excludeOnEdge, thresholdMin, sizeMin, gaussianSigma);
//
//					//-----------------------FOR DEBUG PURPOSES--------------------//
//					//IJ.log("plugin finished on ISLET DEFAULT image "+nSlicesIN+" in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
//					//-------------------------------------------------------------//
//
//				}
//			}
//
//			//-----------------------FOR DEBUG PURPOSES--------------------//
//			//IJ.log("particle areas calculated in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
//			//-------------------------------------------------------------//
//
//			if (isIntensityImage){
//				if((sumIntensity >= compareTOLow) && (sumIntensity <= compareTOHigh))
//					return true;
//			}
//			else{
//				for(int i=0; i<summedPixelAreasArray.length;i++){
//					if(((summedPixelAreasArray[i]*0.85) >= compareTOLow) && ((summedPixelAreasArray[i]*0.85) <= compareTOHigh)){	//CORRECTION FACTOR OF 0.85
//
//						//-----------------------FOR DEBUG PURPOSES--------------------//
//						//IJ.log("gating boolean -TRUE- calculated in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
//						//-------------------------------------------------------------//
//						//-----------------------FOR DEBUG PURPOSES--------------------//
//						//IJ.log("_");
//						//-------------------------------------------------------------//
//
//						return true;					
//					}
//				}
//			}
//
//			//-----------------------FOR DEBUG PURPOSES--------------------//
//			//IJ.log("gating boolean -FALSE- calculated in \t \t \t"+ ((System.nanoTime() - initialTime)/1000000) +"ms");
//			//-------------------------------------------------------------//
//			//-----------------------FOR DEBUG PURPOSES--------------------//
//			//IJ.log("_");
//			//-------------------------------------------------------------//
//			return false;
//		}catch (Exception e){
//			IJ.log("Problem in getting particle areas");
//			IJ.log(e.getMessage());
//		}
//		return false;
//	}
//
//	@SuppressWarnings("static-access")
//	public static void calcTrialRatio(int sizeMin, double thresholdMin, double gaussSigma){	
//		IJ.run("Find Particle Areas", "threshold_minimum="+thresholdMin+" size_minimum="+sizeMin+" gaussian_sigma="+gaussSigma+" exclude_particles_on_edge " +
//				"run_plugin_over_entire_stack calculate brightfield=[Brightfield images] intensity=[Intensity Images]");
//	}
//
//	@SuppressWarnings("static-access")
//	public static boolean getRatioBoolean(boolean isIntensityImage, boolean excludeOnEdge, double thresholdMin, int sizeMin, float compareTOLow, float compareTOHigh, double gaussianSigma){
//
//		//-----------------------FOR DEBUG PURPOSES--------------------//
//		long initialTime = System.nanoTime();
//		IJ.log("getRatioBoolean started on slice "+nSlicesIN);
//		//-------------------------------------------------------------//
//		Interpreter.batchMode=true;
//
//		rm = new RoiManager(true);
//		rt = new ResultsTable();
//
//		try{
//			IJ.log("CHECKPOINT A in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			ImagePlus[] masks = Find_Particle_Areas.ratioModeInWiscScan(impBF, impIN, thresholdMin, sizeMin, excludeOnEdge, gaussianSigma);
//			if(masks == null || masks[0]==null || masks[1]==null){
//				throw new NullPointerException();
//			}
//			ImagePlus bfMask = masks[0];
//			ImagePlus intMask = masks[1];
//			float ratio = 0;
//			float[] areas;
//			float bfAreas = 0;
//			float intAreas = 0;
//			IJ.log("CHECKPOINT B in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//
//			//create the bf and intensity mask image stacks
//			bfMaskStack.addSlice("Slice "+nSlicesBF, bfMask.getProcessor());
//			IJ.log("CHECKPOINT flowcyto_G in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			maskBF.setStack("B: Cell Outlines", bfMaskStack);
//			IJ.log("CHECKPOINT flowcyto_H in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			maskBF.setSlice(nSlicesBF);
//			IJ.log("CHECKPOINT flowcyto_I in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			maskBF.unlock();
//			IJ.log("CHECKPOINT flowcyto_J in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			intMaskStack.addSlice("Slice "+nSlicesIN, intMask.getProcessor());
//			IJ.log("CHECKPOINT flowcyto_K in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			maskIN.setStack("I: Cell Intensity inside Outlines", intMaskStack);
//			IJ.log("CHECKPOINT flowcyto_L in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			maskIN.setSlice(nSlicesBF);
//			IJ.log("CHECKPOINT flowcyto_M in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			maskIN.unlock();
//			IJ.log("CHECKPOINT flowcyto_N in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			//	bfMask.close();
//			//	intMask.close();
//			masks[0].close();
//			masks[1].close();
//			masks = null;
//			bfAreas = 0;
//			intAreas = 0;
//			//create bf selection and get area of brightfield particles
//			IJ.run(bfMask, "Create Selection", null);
//			IJ.log("CHECKPOINT flowcyto_O in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			if(bfMask.getRoi()!=null){
//				IJ.log("CHECKPOINT flowcyto_P in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				rm.addRoi(bfMask.getRoi());
//				IJ.log("CHECKPOINT flowcyto_Q in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				rm.runCommand("Measure");
//				IJ.log("CHECKPOINT flowcyto_R in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				areas = rt.getColumn(rt.getColumnIndex("Area"));
//				IJ.log("CHECKPOINT flowcyto_S in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				bfAreas = areas[areas.length-1];
//				IJ.log("CHECKPOINT flowcyto_T: bfAreas "+ bfAreas + " in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			}
//			IJ.log("CHECKPOINT flowcyto_U in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			//create intensity selections and get area of intensity particles
//			IJ.run(intMask, "Create Selection", null);
//			IJ.log("CHECKPOINT flowcyto_V in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			if(intMask.getRoi()!=null && bfAreas != 0){
//				rm.addRoi(intMask.getRoi());
//				IJ.log("CHECKPOINT flowcyto_W in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				rm.runCommand("Measure");
//				IJ.log("CHECKPOINT flowcyto_X in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				areas = rt.getColumn(rt.getColumnIndex("Area"));
//				IJ.log("CHECKPOINT flowcyto_Y in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				intAreas = areas[areas.length-1];
//				IJ.log("CHECKPOINT flowcyto_Z in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				ratio = intAreas/bfAreas;
//				IJ.log("CHECKPOINT flowcyto_AA: ratio " + ratio + "in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			}
//			//-----------------------FOR DEBUG PURPOSES--------------------//
//			IJ.log("Finished major ratio calc in \t \t \t "+ ((System.nanoTime() - initialTime)/1000000) +"ms");
//			//-------------------------------------------------------------//
//			Interpreter.batchMode=false;
//			maskBF.show();
//			maskIN.show();
//			Interpreter.batchMode=true;
//			if(!(ratio>=compareTOLow && ratio<= compareTOHigh)){
//				if (ratio!=0)
//					twindow.append(nSlicesBF + "\t" + bfAreas + "\t" + intAreas + "\t" + ratio);
//				IJ.log("CHECKPOINT P");
//			} else{
//				twindow.append(nSlicesBF + "\t" + bfAreas + "\t" + intAreas + "\t" + ratio + "\t" + "Particle COLLECTED");
//				IJ.log("CHECKPOINT Q");
//				//-----------------------FOR DEBUG PURPOSES--------------------//
//				IJ.log("ratio returning true in \t \t \t "+ ((System.nanoTime() - initialTime)/1000000) +"ms");
//				//-------------------------------------------------------------//
//				return true;
//			}
//
//		}catch (Exception e){
//			IJ.log("Problem in getting particle RATIO boolean");
//			IJ.log(e.getMessage());
//		}
//		//-----------------------FOR DEBUG PURPOSES--------------------//
//		IJ.log("ratio FALSE in \t \t \t "+ ((System.nanoTime() - initialTime)/1000000) +"ms");
//		//-------------------------------------------------------------//
//		return false;
//	}
//
//	public static void garbageCollect(){
//		System.gc();
//	}
//
//	@SuppressWarnings("static-access")
//	public static void logInImageJ(String message){
//		IJ.log(message);
//	}
//
//	//next two methods are intended to be called from WiscScan's C++ components
//	@SuppressWarnings("static-access")
//	public static void createDebugTimeStartPoint(String message){
//		debugTimeStart=System.nanoTime();
//		IJ.log(message+" --START noted at \t \t \t"+ (debugTimeStart/1000000) +"ms");
//	}
//
//	@SuppressWarnings("static-access")
//	public static void createDebugTimeCheckPoint(String message){
//		IJ.log(message+" --CHECKPOINT noted at \t \t \t"+ ((System.nanoTime() - debugTimeStart)/1000000) +"ms");
//	}
//
//
//}
