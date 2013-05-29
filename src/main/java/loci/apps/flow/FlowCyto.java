package loci.apps.flow;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.macro.Interpreter;
import ij.measure.Measurements;
import ij.plugin.Duplicator;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.text.TextWindow;

import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.io.IOException;

public class FlowCyto {

	private static ImageJ imagej;
	private static ImagePlus imp, impBF, impIN, maskBF, maskIN, tempBF, tempInt;
	private static ImageProcessor tempIP;
	private static ImageStack stack, bfStack, intStack;//, bfMaskStack, intMaskStack;
	private static ImageStatistics stats;
	private static ByteProcessor bp;
	private static ColorModel theCM;	
	private static Duplicator duplicator;
	private static Roi tempRoi;
	private static TextWindow twindow;
	private static int nSlices, nSlicesBF, nSlicesIN;
	private static long debugTimeStart;
	private static Find_Particle_Areas particleAreas;
	private static ThresholdToSelection tts;

	public static void main(String[] args){
		//for debug only
		startImageJ();
		ImagePlus bfImage = IJ.openImage("C:/Users/Ajeet/Desktop/bigStackBF.tif");
		bfImage.show();
		init("brightfield", bfImage.getHeight(), bfImage.getWidth(), 0.180028);
		nSlicesIN++; nSlicesBF++;
		for (int i = bfImage.getImageStackSize(); i > 0; i--){
			nSlices++;
			impBF = duplicator.run(bfImage, nSlices, nSlices);

			boolean logthis = foundParticle(false, true, 30, 100, 100, 400, 2.2);
			if (logthis) IJ.log("ratio true in slice " + nSlices);
			bfImage.setSlice(nSlices);
		}
		System.out.println("done without error");
	}

	public static void startImageJ(){
		if (imagej == null) imagej = new ImageJ();
		IJ.log(IJ.freeMemory().toString());
	}

	public static void closeAllWindows(){
		if (imagej == null) return;
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

			imagej.dispose();
			imagej = null;

			bp = null;
			stack = null;
			bfStack = null;
			intStack = null;
			tempIP = null;
			bp = null;
			theCM = null;
			tts = null;
			particleAreas = null;
			duplicator = null;
			tempRoi = null;
			IJ.log(IJ.freeMemory().toString());
			IJ.log("J");


			garbageCollect();
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

			twindow = new TextWindow("Brightfield Areas", "Slice \t Status \t Largest BF Particle's Area", "", 800, 300);
			bfStack = new ImageStack(width, height, theCM);
			Interpreter.batchMode=false;
			impBF.show();
		}
		else if (mode.equalsIgnoreCase("intensity")){
			impIN = new ImagePlus("Intensity Images", initBP);
			intStack = new ImageStack(width, height, theCM);
			impIN.unlock();

			intStack.addSlice("Slice "+nSlicesIN, bp);
			impIN.setStack("Intensity images", intStack);
			impIN.setSlice(1);	
			impIN.unlock();

			twindow = new TextWindow("Intensity Areas", "Slice \t Status \t Total Particle INT Area", "", 800, 300);
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

			twindow = new TextWindow("RATIO of Found Particles", "Slice \t Status \t Brightfield Area \t Intensity Area \t RATIO \t Mean Intensity above Threshold \t Total RATIO", "", 800, 300);
			maskBF = new ImagePlus("Brightfield Particle Masks");
			maskIN = new ImagePlus("Intensity Particle Masks");
			tempBF = new ImagePlus();
			tempInt = new ImagePlus();
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

	}

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
			bp.reset();
			bp = null;
		} catch(Throwable e){
			IJ.log("Error with showing image");
			e.printStackTrace();
		}
	}

	public static boolean foundParticle(boolean isIntensityImage, boolean excludeOnEdge, double thresholdMin, int sizeMin, float compareTOLow, float compareTOHigh, double gaussianSigma){
		try{
			ImagePlus tempImp = isIntensityImage? duplicator.run(impIN, nSlicesIN, nSlicesIN):duplicator.run(impBF, nSlicesBF, nSlicesBF);
			String mode = isIntensityImage? "intensity":"brightfield";
			float[] summedPixelAreasArray = new Find_Particle_Areas(tempImp, null, null, mode, thresholdMin, gaussianSigma, sizeMin, excludeOnEdge, false).analyzeIndividualParticles();
			float summedPixelAreas=0;

			if(isIntensityImage) {
				for(int i=0; i<summedPixelAreasArray.length; i++)
					summedPixelAreas += summedPixelAreasArray[i] < ((tempImp.getWidth()*tempImp.getHeight())-100)? summedPixelAreasArray[i]:0;
					tempImp.close();
					if ((summedPixelAreas >= compareTOLow && summedPixelAreas <= compareTOHigh)) {
						twindow.append(nSlicesIN + "\t" + "COLLECTED (Intensity)" + "\t" + summedPixelAreas);
						summedPixelAreasArray = null;
						return true;
					}
					twindow.append(nSlicesIN + "\t" + " " + "\t" + summedPixelAreas);
					return false;
			}
			tempImp.close();
			float largest = 0;
			for(int i=0; i<summedPixelAreasArray.length; i++){
				if (summedPixelAreasArray[i] > largest)
					largest = summedPixelAreasArray[i];
			}
			if ((largest >= compareTOLow && largest <= compareTOHigh)){
				twindow.append(nSlicesBF + "\t" + "COLLECTED (Brightfield)" + "\t" + largest);
				summedPixelAreasArray = null;
				return true;
			}
			twindow.append(nSlicesBF + "\t" + " " + "\t" + largest);
			summedPixelAreasArray = null;
		} catch(Throwable e){
			IJ.log("Problem with calculating particles");
			e.printStackTrace();
		}
		return false;
	}

	public static boolean getRatioBoolean(boolean excludeOnEdge, double thresholdMin, int sizeMin, float compareTOLow, float compareTOHigh, double gaussianSigma, boolean compareUsingMeanIntensity){
		try{
			Interpreter.batchMode=true;
			tempInt = duplicator.run(impIN, nSlicesIN, nSlicesIN);
			tempBF = duplicator.run(impBF, nSlicesBF, nSlicesBF);
			long[] meanIntensities = tempInt.getStatistics().getHistogram();
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

			float ratio=0; double bfAreas=0; double intAreas=0;

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

			ratio = (float) (bfAreas==0? 0:intAreas/bfAreas);
			Interpreter.batchMode=false;
			maskBF.show();
			maskIN.show();

			float intensityPixelCount=0;
			float totalIntensity=0;
			for(int j=(int)thresholdMin;j<meanIntensities.length;j++){
				intensityPixelCount+=meanIntensities[j];
				totalIntensity+=(j*meanIntensities[j]);
			}
			float avgIntensity = totalIntensity/intensityPixelCount;
			totalIntensity = (float) (avgIntensity*ratio);

			tempBF.close();
			tempInt.close();
			if(compareUsingMeanIntensity){
				if(!(totalIntensity>=compareTOLow && totalIntensity<= compareTOHigh)){
					if (ratio!=0)
						twindow.append(nSlicesBF + "\t" + " " + "\t" + bfAreas + "\t" + intAreas + "\t" + ratio + "\t" + avgIntensity + "\t" + totalIntensity);
				}else{
					twindow.append(nSlicesBF + "\t" + "COLLECTED (TOTAL Ratio)" + "\t" + bfAreas + "\t" + intAreas + "\t" + ratio + "\t" + avgIntensity + "\t" + totalIntensity);
					return true;
				}
			} else{
				if(!(ratio>=compareTOLow && ratio<= compareTOHigh)){
					if (ratio!=0)
						twindow.append(nSlicesBF + "\t" + " " + "\t" + bfAreas + "\t" + intAreas + "\t" + ratio + "\t" + avgIntensity + "\t" + totalIntensity);
				}else{
					twindow.append(nSlicesBF + "\t" + "COLLECTED (RATIO)" + "\t" + bfAreas + "\t" + intAreas + "\t" + ratio + "\t" + avgIntensity + "\t" + totalIntensity);
					return true;
				}
			}
		}catch(Throwable e){
			IJ.log("Error in realtime ratio boolean " + e.getMessage().toString());
			e.printStackTrace();
		}
		return false;
	}

	public static void calcTrialRatio(double thresholdMin, int sizeMin, double gaussianSigma){
		final Find_Particle_Areas finder = new Find_Particle_Areas(imp, impBF, impIN, "Brightfield", thresholdMin, gaussianSigma, sizeMin, true, true);
		finder.setFullStackOption(true);
		finder.setPluginMode(true);
		finder.createRatioMask();
	}
}