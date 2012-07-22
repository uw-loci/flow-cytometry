///////////////////////////////////////////////////////////////////////////////
// Title:            Find_Particle_Areas
// Description:		 ImageJ plugin to isolate cell particles in images and image
//					 stacks. Intended to be used in realtime and offline analysis
//					 of flow cytometry experiments. 
//
// 					 Three main functions: determining sum pixel area of cell or
//					 particle in brightfield images, determining sum pixel 
//					 intensity count in intensity images, and calculating ratio
//					 of particle intensity per particle area.
//
// Author:           Ajeetesh Vivekanandan, UW-Madison LOCI
// Contact:			 ajeet.vivekanandan@gmail.com
// Web:				 loci.wisc.edu
//
///////////////////////////////////////////////////////////////////////////////
/**
 * @author Ajeet Vivekanandan 
 * @author UW-Madison LOCI
 */

package loci.apps.flow;

import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.macro.Interpreter;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.ImageCalculator;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.text.TextWindow;


public class Find_Particle_Areas implements PlugInFilter {
	private ImagePlus imp, bfImpOrig, intImpOrig, bfImp, intImp;
	private String myMethod;
	private double thresholdMin, gaussianSigma;
	private boolean excludeOnEdge, checkIndividualParticles, doFullStack, multipleImagesAvail, doRatio, inPlugInMode;
	private int sizeMin, currSlice, stackSize;
	private Duplicator duplicator;
	private TextWindow twindow;
	private ParticleAnalyzer particleAnalyzer;
	private ResultsTable rt;
	private ImageCalculator ic;
	private GaussianBlur gb;

	public Find_Particle_Areas(){
	}

	public Find_Particle_Areas(ImagePlus image, ImagePlus brightfieldImage, ImagePlus intensityImage, String method, double minThresh, double sigma, int minSize, boolean excludeEdge, boolean ratioMode){
		imp = image;
		bfImpOrig = brightfieldImage;
		intImpOrig = intensityImage;
		bfImp = bfImpOrig.duplicate();
		intImp = intImpOrig.duplicate();
		myMethod = method;
		thresholdMin = minThresh;
		gaussianSigma = sigma;
		sizeMin = minSize;
		excludeOnEdge = excludeEdge;
		doRatio = ratioMode;
		checkIndividualParticles = !ratioMode;
		doFullStack=false;
		inPlugInMode = false;
		multipleImagesAvail = (bfImp==null||intImp==null)? false:true;
	}

	public void setFullStackOption(boolean option){
		doFullStack = option;
	}

	public static void main(String[] args){
//		new ImageJ();
//		new IJ();
//		ImagePlus bfImage = IJ.openImage("C:/Users/Ajeet/Desktop/s2-bf.tif");
//		ImagePlus intImage = IJ.openImage("C:/Users/Ajeet/Desktop/s2-int.tif");
//		bfImage.show();
//		intImage.show();
//		Find_Particle_Areas fpa = new Find_Particle_Areas();
//		fpa.run(null);
	}

	/**
	 * Original setup method called by ImageJ when plugin is run.
	 *
	 * @param arg String arg0
	 * @param image ImagePlus image to run calculation on
	 */
	public int setup(String arg, ImagePlus image) {
		imp = image;
		return DOES_8G | NO_CHANGES;
	}

	public void run(ImageProcessor arg0) {
		if(!createDialog()){
			//	IJ.showMessage("Please enter or choose correct parameters");
			return;
		}
		if(doRatio){
			createRatioMask();
			//clean up
			bfImp.close();
			intImp.close();
		} else if (doFullStack){
			analyzeSingleStack();
		} else if (checkIndividualParticles){
			IJ.log(analyzeIndividualParticles()[0] + "");
		}

		Interpreter.batchMode=false;
	}

	private boolean createDialog(){
		try{
			GenericDialog gd = new GenericDialog("Calculate Particle Areas");
			String [] methods={"Brightfield", "Intensity"};
			gd.addChoice("Channel:", methods, methods[0]);
			gd.addMessage ("Special paramters; (thresholdMax = 255, SizeMax=Infinity)");
			gd.addNumericField ("Threshold_Minimum",  30, 0);
			gd.addNumericField ("Size_Minimum",  100, 0);
			gd.addNumericField ("Gaussian_Sigma",  2.2, 0);
			gd.addCheckbox("Exclude_Particles_on_Edge",true);
			gd.addCheckbox("Check_Individual_Particles",false);
			gd.addCheckbox("Run_Plugin_Over_Entire_Stack", false);

			//below is basically: if there area more than one image open, allow the
			//	"calculate ratio between" option and populate option boxes
			int[] wList = WindowManager.getIDList();
			if(wList!=null && wList.length>=2){
				String[] availImages = new String[wList.length];
				for (int i=0; i<wList.length; i++) {
					imp = WindowManager.getImage(wList[i]);
					availImages[i] = imp!=null?imp.getTitle():"";
				}
				gd.addMessage ("Check box below to calculate intensity/area ratio for two images.");
				gd.addCheckbox("Calculate Ratio between:", false);
				gd.addChoice("BRIGHTFIELD image:", availImages, availImages[0]);
				gd.addChoice("INTENSITY image:", availImages, availImages[1]);
				multipleImagesAvail = true;
			} else 
				multipleImagesAvail = false;

			gd.showDialog();
			if (gd.wasCanceled()) return false;

			myMethod = gd.getNextChoice();
			thresholdMin= gd.getNextNumber();
			sizeMin= (int) gd.getNextNumber();
			gaussianSigma=gd.getNextNumber();
			excludeOnEdge=gd.getNextBoolean(); 
			checkIndividualParticles= gd.getNextBoolean();
			doFullStack = gd.getNextBoolean();
			doRatio = multipleImagesAvail? gd.getNextBoolean():false;
			//set up all required objects 
			if(doRatio){
				int index = gd.getNextChoiceIndex();
				bfImpOrig = WindowManager.getImage(wList[index]);
				bfImp = bfImpOrig.duplicate();
				index = gd.getNextChoiceIndex();
				intImpOrig = WindowManager.getImage(wList[index]);
				intImp = intImpOrig.duplicate();
			} else if(doFullStack){
				stackSize = imp.getStackSize();
				currSlice = imp.getCurrentSlice();
			} 
			rt = new ResultsTable();
			inPlugInMode = true;
			Interpreter.batchMode=true;
			return true;
		}catch(Throwable e){
			IJ.log("Error encountered while parsing dialog box.");
			IJ.log(e.getStackTrace().toString());
			Interpreter.batchMode=false;
		}
		return false;
	}

	/**
	 * Creates a ratio mask stack for both brightfield and intensity images. Intensity image masks contain only
	 * the pixels above threshold INSIDE the brightfield image's cell outline (if there is a cell). 
	 * 
	 * @return ImagePlus[] array of 2 ImagePlus objects, array[0] = brightfield Image, array[1] = intensity Image
	 */
	public ImagePlus[] createRatioMask(){

		//If we're using this plugin as a class for some other class/main instead of through ImageJ, assume 
		//	that class/main will handle how to display the data, just return the masks as an array...
		//	Otherwise create and display info in a TextWindow below
		if(inPlugInMode){
			twindow = new TextWindow("RATIO of Found Particles", "Slice \t Brightfield Area \t Intensity Area \t RATIO", "", 800, 300);
		}

		double ratio=0, bfAreas=0, intAreas=0;
		ImagePlus tempInt = null, tempBF = null, 
				bfMask = new ImagePlus("Cell Outlines"), intMask = new ImagePlus("Cell Intensity inside Outlines"),
				duplicatedBF = null, duplicatedInt = null;
		ImageStack intMaskStack = null, bfMaskStack = null;
		ImageProcessor tempIP;
		ImageStatistics stats;
		duplicator = new Duplicator();
		ic = new ImageCalculator();
		gb = new GaussianBlur();
		Roi tempRoi = null;
		ThresholdToSelection tts = new ThresholdToSelection();
		ArrayList<Float> resultsBF = new ArrayList<Float>();
		ArrayList<Float> resultsIN = new ArrayList<Float>();
		ArrayList<Float> resultsRATIO = new ArrayList<Float>();

		//only needed if this method is executed through ImageJ
		if(inPlugInMode){

			byte[] r = new byte[256];
			for(int ii=0 ; ii<256 ; ii++)
				r[ii]=(byte)ii;
			ColorModel theCM = new IndexColorModel(8, 256, r,r,r);
			intMaskStack = new ImageStack(intImp.getWidth(), intImp.getHeight(), theCM);
			bfMaskStack = new ImageStack(bfImp.getWidth(), bfImp.getHeight(), theCM);
		}

		for (int i=bfImp.getCurrentSlice(); i<=bfImp.getStackSize(); i++){
			try{
				//have the user be able to observe which image the plugin is at
				bfImpOrig.setSlice(i);
				intImpOrig.setSlice(i);

				//ALWAYS create duplicates for findParticles(...), otherwise ImagePlus objects get creates that are never closed
				//	Using Duplicator() to get single slice, whereas ImagePlus.duplicate() duplicates entire stack.
				duplicatedBF = duplicator.run(bfImp, i, i);

				tempBF = findParticles(duplicatedBF, false, false);

				duplicatedInt = duplicator.run(intImp, i, i);

				tempInt = findParticles(duplicatedInt, true, false);

				ic.run("AND", tempInt, tempBF);

				//clean up
				duplicatedBF.close();
				duplicatedInt.close();

				//once again, assume if this method is not called by ImageJ, then the caller will handle the data
				if(inPlugInMode){
					ratio=0; bfAreas=0; intAreas=0;

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

					addImageToStack(bfMask, tempIP, bfMaskStack);

					//GET total intensity particles' area
					tempIP = tempInt.getProcessor();
					tempIP.setThreshold(1, 255, ImageProcessor.BLACK_AND_WHITE_LUT);
					tempRoi = tts.convert(tempIP);
					if(tempRoi!=null) {
						tempIP.setRoi(tempRoi);
						stats = ImageStatistics.getStatistics(tempIP, Measurements.AREA, tempBF.getCalibration());
						intAreas = stats.area;
					}

					addImageToStack(intMask, tempIP, intMaskStack);

					ratio = bfAreas==0? 0:intAreas/bfAreas;
					if (ratio!=0){
						resultsBF.add((float) bfAreas);
						resultsIN.add((float) intAreas);
						resultsRATIO.add((float) ratio);
						twindow.append(i + "\t" + bfAreas + "\t" + intAreas + "\t" + ratio);
					}
					Interpreter.batchMode=false;
					bfMask.show();
					intMask.show();
					Interpreter.batchMode=true;

				}
				if(!doFullStack) i = bfImp.getStackSize()+1;
			}catch(NullPointerException e){
				IJ.log("Null value encountered in slice " + i);
			}catch(Throwable e){
				IJ.log("Error creating accurate mask on slice " + i);
			}
		}

		ImagePlus[] returnImages = new ImagePlus[2];
		returnImages[0] = tempBF;
		returnImages[1] = tempInt;

		if(inPlugInMode){

			Collections.sort(resultsBF);
			Collections.sort(resultsIN);
			Collections.sort(resultsRATIO);

			float sumBF = 0;
			for (float s : resultsBF) sumBF += s;
			float sumIN = 0;
			for (float s : resultsIN) sumIN += s;
			float sumRatio = 0;
			for (float s : resultsRATIO) sumRatio += s;

			twindow.append("Avg" + "\t" + sumBF/resultsBF.size() + "\t" + sumIN/resultsIN.size() + "\t" + sumRatio/resultsRATIO.size());
			twindow.append("Min" + "\t" + resultsBF.get(0) + "\t" + resultsIN.get(0) + "\t" + resultsRATIO.get(0));
			twindow.append("Max" + "\t" + resultsBF.get(resultsBF.size()-1) + "\t" + resultsIN.get(resultsIN.size()-1) + "\t" + resultsRATIO.get(resultsRATIO.size()-1));
			twindow.append("Med" + "\t" + resultsBF.get(resultsBF.size()/2) + "\t" + resultsIN.get(resultsIN.size()/2) + "\t" + resultsRATIO.get(resultsRATIO.size()/2));

		}
		else Interpreter.batchMode=false;
		intMaskStack=null;
		bfMaskStack=null;
		tempInt = null;
		tempBF = null;
		return returnImages;

	}

	public ImagePlus findParticles(ImagePlus imageToAnalyze, boolean intensityImage, boolean showResults){
		try{
			if(intensityImage){
				initParticleAnalyzer(true, showResults);
				imageToAnalyze.getProcessor().setThreshold(thresholdMin, 255, ImageProcessor.BLACK_AND_WHITE_LUT);
				particleAnalyzer.analyze(imageToAnalyze);
				return particleAnalyzer.getOutputImage();
			} else{
				initParticleAnalyzer(false, showResults);
				IJ.run(imageToAnalyze, "Find Edges", null);
				double accuracy = (imageToAnalyze.getProcessor() instanceof ByteProcessor || imageToAnalyze.getProcessor() instanceof ColorProcessor) ?
						0.002 : 0.0002;
				gb.blurGaussian(imageToAnalyze.getProcessor(), gaussianSigma, gaussianSigma, accuracy);
				imageToAnalyze.getProcessor().setAutoThreshold("Minimum", true, ImageProcessor.BLACK_AND_WHITE_LUT);
				particleAnalyzer.analyze(imageToAnalyze);
				imageToAnalyze = particleAnalyzer.getOutputImage();
				//copy and paste below code once more if an underestimation is desired
				((ByteProcessor)(imageToAnalyze.getProcessor())).erode(1, 0);
			}
			return imageToAnalyze;
		} catch(Throwable e){
			IJ.log("Error encountered while finding particles");
			IJ.log(e.getStackTrace().toString());
			IJ.log(e.getMessage().toString());
		}
		return null;
	}

	private void initParticleAnalyzer(boolean intensityImage, boolean showResults){
		int options = 0;
		if (excludeOnEdge) options |= ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
		if (showResults) options |= ParticleAnalyzer.SHOW_RESULTS;
		options |= ParticleAnalyzer.CLEAR_WORKSHEET;
		options |= ParticleAnalyzer.SHOW_MASKS;

		if(intensityImage){
			particleAnalyzer = new ParticleAnalyzer(options, Measurements.AREA, rt, 1, Double.POSITIVE_INFINITY);
		} else{
			options |= ParticleAnalyzer.INCLUDE_HOLES|ParticleAnalyzer.CLEAR_WORKSHEET|ParticleAnalyzer.SHOW_MASKS;
			particleAnalyzer = new ParticleAnalyzer(options, Measurements.AREA, rt, sizeMin, Double.POSITIVE_INFINITY);
		}		
	}

	public void addImageToStack(ImagePlus mainImage, ImageProcessor imageToAdd, ImageStack correspondingStack){
		//very useful to add any single ImagePlus to an existing stack
		correspondingStack.addSlice("", imageToAdd);
		mainImage.setStack(correspondingStack);
		mainImage.setSlice(mainImage.getStackSize());
		mainImage.unlock();		
	}

	public void analyzeSingleStack(){
		twindow = new TextWindow("Found Particles", " \t Slice \t \tPixel Area", "", 800, 300);
		duplicator = new Duplicator();
		ImagePlus currentImage, tempDuplicate;
		ImageProcessor tempIP;
		ImageStatistics stats;
		Roi tempRoi;
		ThresholdToSelection tts = new ThresholdToSelection();
		double area = 0;

		try{
			currSlice=imp.getCurrentSlice(); stackSize=imp.getStackSize();
			if(myMethod.equalsIgnoreCase("intensity")){
				while(currSlice <= stackSize){
					imp.setSlice(currSlice);
					tempDuplicate = duplicator.run(imp, currSlice, currSlice);
					currentImage = findParticles(tempDuplicate, true, false);
					tempDuplicate.close();
					tempIP = currentImage.getProcessor();
					tempIP.setThreshold(1, 255, ImageProcessor.BLACK_AND_WHITE_LUT);
					tempRoi = tts.convert(tempIP);
					if(tempRoi!=null) {
						tempIP.setRoi(tempRoi);
						stats = ImageStatistics.getStatistics(tempIP, Measurements.AREA, currentImage.getCalibration());					//originally Measurements.MEAN
						area = stats.area;
					}

					if(area!=0){
						twindow.append("Particle(s) found in slice    \t"+ currSlice+ "\t    with a total pixel area of    \t"+area);
					}
					currSlice++;
				}
			} else{
				while(currSlice <= stackSize){
					imp.setSlice(currSlice);
					tempDuplicate = duplicator.run(imp, currSlice, currSlice);
					currentImage = findParticles(tempDuplicate, false, false);
					tempDuplicate.close();
					tempIP = currentImage.getProcessor();
					tempIP.setThreshold(1, 255, ImageProcessor.BLACK_AND_WHITE_LUT);
					tempRoi = tts.convert(tempIP);
					if(tempRoi!=null) {
						tempIP.setRoi(tempRoi);
						stats = ImageStatistics.getStatistics(tempIP, Measurements.AREA, currentImage.getCalibration());					//originally Measurements.MEAN
						area = stats.area;
					}

					if(area!=0){
						twindow.append("Particle(s) found in slice    \t"+ currSlice+ "\t    with a total pixel area of    \t"+area);
					}
					currSlice++;
				}
			}
		}catch(Throwable e){
			IJ.log("Error encountered while analyzing full stack.");
			IJ.log(e.getStackTrace().toString());
		}

	}

	public float[] analyzeIndividualParticles(){
		rt = new ResultsTable();
		duplicator = new Duplicator();
		try{
			ImagePlus duplicateImage = duplicator.run(imp, imp.getCurrentSlice(), imp.getCurrentSlice());
			if(myMethod.equalsIgnoreCase("intensity")) 
				findParticles(duplicateImage, true, true);
			else findParticles(duplicateImage, false, false);
			duplicateImage.close();
			if (rt.getCounter()>0)
				IJ.log("Counter: " + rt.getCounter());
			return rt.getColumn(rt.getColumnIndex("Area"));
		}catch(Throwable e){
			IJ.log("Error encountered while analyzing individual particles.");
			IJ.log(e.getStackTrace().toString());
		}
		return new float[1];
	}




}



























//
//package loci.apps.flow;
//
//import java.awt.Polygon;
//import java.awt.image.ColorModel;
//import java.awt.image.IndexColorModel;
//import java.util.ArrayList;
//import java.util.Collections;
//
//import ij.IJ;
//import ij.ImageJ;
//import ij.ImagePlus;
//import ij.ImageStack;
//import ij.WindowManager;
//import ij.plugin.Duplicator;
//import ij.plugin.ImageCalculator;
//import ij.plugin.filter.Analyzer;
//import ij.plugin.filter.GaussianBlur;
//import ij.plugin.filter.PlugInFilter;
//import ij.plugin.filter.ParticleAnalyzer;
//import ij.plugin.filter.ThresholdToSelection;
//import ij.plugin.frame.RoiManager;
//import ij.process.ByteProcessor;
//import ij.process.ColorProcessor;
//import ij.process.ImageProcessor;
//import ij.process.ImageStatistics;
//import ij.text.TextWindow;
//import ij.gui.GenericDialog;
//import ij.gui.Roi;
//import ij.macro.Interpreter;
//import ij.measure.Measurements;
//import ij.measure.ResultsTable;
//
//public class Find_Particle_Areas implements PlugInFilter {
//
//	private static String myMethod, myResolution;
//	private static double thresholdMin, gaussianSigma;
//	private static boolean exclude, doTheSum, dofullStack, multipleImagesAvail, doRatio; 
//	private ImagePlus imp, origImage, bfImp, intImp;
//	private static ImagePlus intMask, bfMask;
//	private int stackSize, curSlice;
//	private static int sizeMin;
//	private int imageRes;
//	private static Duplicator duplicator;
//	private static TextWindow twindow;
//	private static float average, max, min, median;
//	private static ArrayList<Float> sliceTable; 
//	private static ParticleAnalyzer partanlzr;
//	private static ResultsTable rt;
//	private static RoiManager rm;
//
//	public static void main(String[] args){
//		new ImageJ();
//		ImagePlus image = IJ.openImage("C:/Users/Ajeet/Desktop/singleBFparticle.tif");
//		IJ.runPlugIn(image, "ij.Find_Particle_Areas", "arg hi");
//		image.show();
//		WindowManager.addWindow(image.getWindow());
//	}
//
//
//	/**
//	 * Original setup method called by ImageJ when plugin is run.
//	 *
//	 * @param arg String arg0
//	 * @param image ImagePlus image to run calculation on
//	 */
//	@Override
//	public int setup(String arg, ImagePlus image) {
//		origImage = image;
//		return DOES_8G | NO_CHANGES;
//	}
//
//	/**
//	 * Main program that is run by calling this plugin. Plugin prompts user with
//	 * options, then calculates desired results. Options include: brightfield/intensity 
//	 * image being used, threshold minimum if intensity, size minimum if brightfield, 
//	 * to exclude particles on the edge during analysis, to show the summed result of
//	 * analysis on single image, and/or to run calculation over entire stack if image 
//	 * is a stack.
//	 *
//	 * @param arg0 ImageProcessor object created when plugin is started.
//	 * @param DialogOptions list of options displayed in dialog box, not actually 
//	 * passed in as parameter.
//	 */
//	@Override
//	public void run(ImageProcessor arg0) {
//		try{
//			//get information from user about image and desired analysis
//
//			GenericDialog gd = new GenericDialog("Calculate Particle Areas");
//			String [] methods={"Brightfield", "Intensity"};
//			String [] resolutions={"64x64", "128x128", "256x256", "512x512"};
//			gd.addChoice("Channel:", methods, methods[0]);
//			gd.addChoice("Resolution:", resolutions, resolutions[1]);
//			gd.addMessage ("Special paramters; (thresholdMax = 255, SizeMax=Infinity)");
//			gd.addNumericField ("Threshold_Minimum",  30, 0);
//			gd.addNumericField ("Size_Minimum",  100, 0);
//			gd.addNumericField ("Gaussian_Sigma",  2.2, 0);
//			gd.addCheckbox("Exclude_Particles_on_Edge",true);
//			gd.addCheckbox("Show_summed_areas",false);
//			gd.addCheckbox("Run_Plugin_Over_Entire_Stack", false);
//
//			int[] wList = WindowManager.getIDList();
//			if(wList.length>=2){
//				String[] availImages = new String[wList.length];
//				for (int i=0; i<wList.length; i++) {
//					imp = WindowManager.getImage(wList[i]);
//					availImages[i] = imp!=null?imp.getTitle():"";
//				}
//				gd.addCheckbox("Calculate Ratio between:", false);
//				gd.addChoice("BRIGHTFIELD image:", availImages, availImages[0]);
//				gd.addChoice("INTENSITY image:", availImages, availImages[1]);
//				multipleImagesAvail = true;
//			} else 
//				multipleImagesAvail = false;
//
//			gd.showDialog();
//			if (gd.wasCanceled()) return;
//
//			//populate static variables
//			myMethod = gd.getNextChoice();
//			myResolution = gd.getNextChoice();
//			thresholdMin= gd.getNextNumber();
//			sizeMin= (int) gd.getNextNumber();
//			gaussianSigma=gd.getNextNumber();
//			exclude=gd.getNextBoolean(); 
//			doTheSum= gd.getNextBoolean();
//			dofullStack = gd.getNextBoolean();
//			if(multipleImagesAvail) doRatio= gd.getNextBoolean();
//
//
//			//begin core program
//			Interpreter.batchMode=true;
//			rt = new ResultsTable();
//			rm = new RoiManager(true);
//
//			if (myResolution.equals("128x128")) imageRes=128;
//			else if (myResolution.equals("64x64")) imageRes=64;
//			else if (myResolution.equals("256x256")) imageRes=256;
//			else if (myResolution.equals("512x512")) imageRes=512;
//			else imageRes = 128;
//
//			//check options, bypass other methods.
//			//uses original stacked image, not single image
//			if(doRatio) {
//				int index = gd.getNextChoiceIndex();
//				bfImp = WindowManager.getImage(wList[index]);
//				index = gd.getNextChoiceIndex();
//				intImp = WindowManager.getImage(wList[index]);
//				createRatioMask(dofullStack);
//			}			
//			else if(dofullStack){
//				stackSize = origImage.getStackSize();
//				curSlice= origImage.getCurrentSlice();
//				analyzeFullStack(origImage.duplicate());
//			} 
//			else{
//				//create a duplicate and run calculations on it to preserve original image
//				imp = new ImagePlus("duplicate", arg0.duplicate()); // has only one slice
//				findParticles(imp);
//				if (doTheSum)
//					doTheSum(true);
//
//				imp.close();
//			}
//			Interpreter.batchMode=false;
//			IJ.log("plugin done");
//
//		} catch(Exception e){
//			IJ.showMessage("Error with plugin.");
//			IJ.log(e.getMessage());
//			Interpreter.batchMode=false;
//		}
//	}
//	/**
//	 * Creates a ratio mask stack for both brightfield and intensity images. Intensity image masks contain only
//	 * the pixels above threshold INSIDE the brightfield image's cell outline (if there is a cell). 
//	 *
//	 * @param doFullStack Run analysis on single image if false, run over entire stack if true
//	 */
//	private ImagePlus createRatioMask(boolean doFullStack){
//		return createRatioMask(bfImp, intImp, thresholdMin, sizeMin, exclude, doFullStack, true, gaussianSigma);
//	}
//
//	/**
//	 * Creates a ratio mask stack for both brightfield and intensity images. Intensity image masks contain only
//	 * the pixels above threshold INSIDE the brightfield image's cell outline (if there is a cell). 
//	 *
//	 * @param bfImage Brightfield image or image stack to be analyzed
//	 * @param intImage Intensity image or image stack to be analyzed
//	 * @param threshMin Minimum threshold for intensity image, max is set to 255
//	 * @param sizeMin Minimum size limit for particles in brightfield image, max is positive infinity
//	 * @param excludeOnEdge Option to exclude particles on edge of images
//	 * @param doFullStack Run analysis on single image if false, run over entire stack if true
//	 * @param showMasks Display mask stacks of bf and intensity masks if true
//	 */
//	private static ImagePlus createRatioMask(ImagePlus bfImageOrig, ImagePlus intImageOrig, double threshMin, int sizeMin, boolean excludeOnEdge, boolean doFullStack, boolean showMask, double sigmaGaussian){
//		try{
//			//-----------------------FOR DEBUG PURPOSES--------------------//
//			long initialTime = System.nanoTime();
//			long loopTime = initialTime;
//			IJ.log("createRatioMasks started");
//			//-------------------------------------------------------------//
//			if(myMethod != null){
//				twindow = new TextWindow("RATIO of Found Particles", "Slice \t Brightfield Area \t Intensity Area \t RATIO", "", 800, 300);
//			}
//			IJ.log("CHECKPOINT MASK A in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			//initialize
//			ImagePlus tempInt = null, tempBF = null, bfImage=bfImageOrig.duplicate(), intImage=intImageOrig.duplicate();
//			intMask = new ImagePlus("Cell Intensity inside Outlines");
//			bfMask = new ImagePlus("Cell Outlines");
//			ImageCalculator ic = new ImageCalculator();
//			duplicator = new Duplicator();
//			ImageStack intMaskStack = null, bfMaskStack = null;
//			float ratio = 0, bfAreas = 0, intAreas = 0;
//			float[] areas = null;
//			IJ.log("CHECKPOINT MASK B in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			//dont need the colormodel or stack if image is not going to be displayed (i.e. WiscScan operations process only
//			// one image at a time, and wouldn't want a new image window appear for each of 3000+ images)
//			if(showMask){
//				byte[] r = new byte[256];
//				for(int ii=0 ; ii<256 ; ii++)
//					r[ii]=(byte)ii;
//				ColorModel theCM = new IndexColorModel(8, 256, r,r,r);
//				intMaskStack = new ImageStack(intImage.getWidth(), intImage.getHeight(), theCM);
//				bfMaskStack = new ImageStack(bfImage.getWidth(), bfImage.getHeight(), theCM);
//			} 	
//			IJ.log("CHECKPOINT MASK C in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			//for every image in the stack (image stacks are not zero indexed)
//			for(int i=bfImage.getCurrentSlice(); i<=bfImage.getStackSize(); i++){
//
//				loopTime = System.nanoTime();
//				bfImageOrig.setSlice(i);
//				intImageOrig.setSlice(i);
//				bfImage = duplicator.run(bfImageOrig, i, i);
//				intImage = duplicator.run(intImageOrig, i, i);
//				
//				IJ.log("CHECKPOINT MASK loop started " +((System.nanoTime() - loopTime)/1000000) +"ms");
//				//create the masks
//				tempBF = findParticles(bfImage, "Brightfield", 0, sizeMin, excludeOnEdge, true, true, sigmaGaussian);
//				//tempBF holds mask from brightfield image
//				IJ.log("CHECKPOINT MASK E in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//				tempInt = ic.run("AND create", tempBF, findParticles(duplicator.run(intImage, i, i), "Intensity", threshMin, 1, false, false, false, sigmaGaussian));		
//				//tempINT holds the bf mask AND'ed with the intensity mask
//				
//				IJ.log("CHECKPOINT MASK F in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//				ImageProcessor ip = bfMask.getProcessor();
//
//				if(showMask && myMethod != null){
//					//reset ratio to zero at beginning of each analysis for each image
//					ratio=0;
//					//create the bf and intensity mask image stacks
//					bfMaskStack.addSlice("Slice "+i, tempBF.getProcessor());
//					IJ.log("CHECKPOINT MASK G in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//					bfMask.setStack("B: Cell Outlines", bfMaskStack);
//					IJ.log("CHECKPOINT MASK H in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//					bfMask.setSlice(i);
//					IJ.log("CHECKPOINT MASK I in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//					bfMask.unlock();
//					
//					IJ.log("CHECKPOINT MASK J in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//					intMaskStack.addSlice("Slice "+i, tempInt.getProcessor());
//					IJ.log("CHECKPOINT MASK K in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//					intMask.setStack("I: Cell Intensity inside Outlines", intMaskStack);
//					IJ.log("CHECKPOINT MASK L in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//					intMask.setSlice(i);
//					IJ.log("CHECKPOINT MASK M in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//					intMask.unlock();
//					IJ.log("CHECKPOINT MASK N in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//
//					bfAreas = 0;
//					intAreas = 0;
//					//create bf selection and get area of brightfield particles
//					IJ.run(bfMask, "Create Selection", null);
//				//	ThresholdToSelection tts = new ThresholdToSelection();
//				//	Roi bfROI = new ThresholdToSelection().convert(bfMask.getProcessor());
//					IJ.log("CHECKPOINT MASK O in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//				//	int measurements = Analyzer.getMeasurements();
//				//	bfMask=partanlzr.getOutputImage();
//				//	double convexarea = getArea(bfROI.getPolygon());
//				//	bfROI = bfMask.getRoi();
//					if(bfMask.getRoi()!=null){
//						IJ.log("CHECKPOINT MASK P in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//						rm.addRoi(bfMask.getRoi());
//						IJ.log("CHECKPOINT MASK Q in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//						rm.runCommand("Measure");
//						IJ.log("CHECKPOINT MASK R in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//						areas = rt.getColumn(rt.getColumnIndex("Area"));
//						IJ.log("CHECKPOINT MASK S in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//						bfAreas = areas[areas.length-1];
//						IJ.log("CHECKPOINT MASK T: bfAreas "+ bfAreas + " in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//					}
//					IJ.log("CHECKPOINT MASK U in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//					//create intensity selections and get area of intensity particles
//					IJ.run(intMask, "Create Selection", null);
//					IJ.log("CHECKPOINT MASK V in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//					if(intMask.getRoi()!=null && bfAreas != 0){
//					//	ImageStatistics statsINT = intMask.getStatistics();
//						rm.addRoi(intMask.getRoi());
//						IJ.log("CHECKPOINT MASK W in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//						rm.runCommand("Measure");
//						IJ.log("CHECKPOINT MASK X in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//						areas = rt.getColumn(rt.getColumnIndex("Area"));
//						IJ.log("CHECKPOINT MASK Y in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//						intAreas = areas[areas.length-1];
//						IJ.log("CHECKPOINT MASK Z in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//						ratio = intAreas/bfAreas;
//						IJ.log("CHECKPOINT MASK AA: ratio " + ratio + "in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//					}
//					//append to custom results window if ratio isnt zero
//					if (ratio!=0){
//						twindow.append(bfImage.getCurrentSlice() + "\t" + bfAreas + "\t" + intAreas + "\t" + ratio);
//					}
//
//				} 
//				//if we're not showing the image, we're storing masked bf and intensity, and returning the masked intensity image
//				else {
//					IJ.log("CHECKPOINT MASK BB in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//					intMask=tempInt;
//					bfMask = tempBF;
//					IJ.log("CHECKPOINT MASK CC in " +((System.nanoTime() - loopTime)/1000000) +"ms");
//				}
//				//go through calculation only once if we dont want to analyze the full stack
//				if(!doFullStack) i=bfImage.getStackSize()+1;
//				bfImage.close();
//				intImage.close();
//				IJ.log("CHECKPOINT MASK loop ended " +((System.nanoTime() - loopTime)/1000000) +"ms");
//			}
//			IJ.log("CHECKPOINT MASK BB in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			if(showMask && myMethod!=null){		//simple check to see if we're in plugin mode or realtime mode...we dont want 3000+ individual images pop up
//				Interpreter.batchMode=false;
//				bfMask.show();
//				intMask.show();
//				Interpreter.batchMode=true;
//			}
//			return intMask;
//
//		}catch (Exception e){
//			IJ.log(e.getMessage());
//			IJ.log("Error with creating masks.");
//			IJ.log(e.getCause().toString());
//			IJ.showMessage("Error with creating masks.");
//		}
//		return null;
//	}
//
//    private static double getArea(Polygon p) {
//        if (p==null) return 0;
//        int areas = 0;
//        int iminus1;
//        for (int i=0; i<p.npoints; i++) {
//            iminus1 = i-1;
//            if (iminus1<0) iminus1=p.npoints-1;
//            areas += (p.xpoints[i]+p.xpoints[iminus1])*(p.ypoints[i]-p.ypoints[iminus1]);
//        }
//        return (Math.abs(areas/2.0));
//    }
//	
//	
//	/**
//	 *Initialize Particle Analyzer based on specifications of brightfield or intensity image, and other options
//	 *
//	 * @param isIntensity true if image being analyzed is intensity image
//	 * @param excludeOnEdge ignore particles on the edge of image if true
//	 * @param sizeMinimum minimum particle size for detection, only applies to brightfield
//	 * @param addToManager add any particles found to ROI Manager if true
//	 * @param showResults show measurements in Results Table if true 
//	 */
//	private static void initParticleAnalyzer(boolean isIntensity, boolean excludeOnEdge, double sizeMinimum, boolean addToManager, boolean showResults){
//		int options = 0;
//		if (excludeOnEdge) options |= ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
//		if (addToManager) options |= ParticleAnalyzer.ADD_TO_MANAGER;
//		if (showResults) options |= ParticleAnalyzer.SHOW_RESULTS;
//		options |= ParticleAnalyzer.CLEAR_WORKSHEET;
//		options |= ParticleAnalyzer.SHOW_MASKS;
//
//		if(isIntensity){
//			partanlzr = new ParticleAnalyzer(options, Measurements.AREA|Measurements.PERIMETER, rt, 1, Double.POSITIVE_INFINITY);
//		} else{
//			options |= ParticleAnalyzer.INCLUDE_HOLES|ParticleAnalyzer.CLEAR_WORKSHEET|ParticleAnalyzer.SHOW_MASKS;
//			partanlzr = new ParticleAnalyzer(options, Measurements.AREA|Measurements.PERIMETER, rt, sizeMinimum, Double.POSITIVE_INFINITY);
//		}		
//	}
//
//	/**
//	 *Main image analysis portion of plugin, analyzes image based on type (intensity/brightfield). 
//	 *Results are set up in the RoiManager and ResultsTable, nothing is returned.
//	 *
//	 * @param imageToAnalyze ImagePlus image to analyze
//	 */
//
//	private static ImagePlus findParticles(ImagePlus imageToAnalyze){
//		return findParticles(imageToAnalyze, myMethod, thresholdMin, sizeMin, exclude, true, true,gaussianSigma);
//	}
//
//	/**
//	 *Main image analysis portion of plugin, analyzes image based on type (intensity/brightfield). 
//	 *Results are set up in the RoiManager and ResultsTable, nothing is returned.
//	 *
//	 * @param imageToAnalyze ImagePlus image to analyze
//	 * @param method if "Intensity" does intensity analysis, otherwise does brightfield analyzis
//	 * @param threshMin	minimum threshold to set, only applies to Intensity images
//	 * @param sizeMinimum minimum particle size for detection, only applies to brightfield
//	 * @param excludeEdgeParticles ignore particles on the edge of image if true
//	 * @param sigma Sigma value for brightfield images' Gaussian Blur
//	 */
//
//	private static ImagePlus findParticles(ImagePlus imageToAnalyze, String method, double threshMin, int sizeMinimum, boolean excludeEdgeParticles, boolean addToManager, boolean showResults, double sigma){
//		try{
//			long initialTime = System.nanoTime();
//			IJ.log("findParticles method");
//			if(method.equalsIgnoreCase("Intensity")){
//				IJ.log("CHECKPOINT A in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				initParticleAnalyzer(true, excludeEdgeParticles, sizeMinimum, addToManager, showResults);
//				IJ.log("CHECKPOINT B in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				imageToAnalyze.getProcessor().setThreshold(threshMin, 255, ImageProcessor.BLACK_AND_WHITE_LUT);
//				IJ.log("CHECKPOINT C in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				//IJ.run(imageToAnalyze, "Convert to Mask", null);
//				partanlzr.analyze(imageToAnalyze);
//				IJ.log("CHECKPOINT D in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				imageToAnalyze.close();
//				IJ.log("INTENSITY findParticle method ended in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				return partanlzr.getOutputImage();
//			}	
//			else{
//				IJ.log("CHECKPOINT F in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				initParticleAnalyzer(false, excludeEdgeParticles, sizeMinimum, addToManager, showResults);
//				IJ.log("CHECKPOINT G in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				IJ.run(imageToAnalyze, "Find Edges", null);
//				IJ.log("CHECKPOINT H in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				//		IJ.run(imageToAnalyze, "Find Edges", null);		//commenting because this gives a little worse estimation
//				IJ.log("CHECKPOINT I in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				double accuracy = (imageToAnalyze.getProcessor() instanceof ByteProcessor || imageToAnalyze.getProcessor() instanceof ColorProcessor) ?
//						0.002 : 0.0002;
//				new GaussianBlur().blurGaussian(imageToAnalyze.getProcessor(), sigma, sigma, accuracy);
//				//IJ.run(imageToAnalyze, "Gaussian Blur...", "sigma="+sigma);	//sigma value changed from 5 to 2 for better estimation
//
//
//				IJ.log("CHECKPOINT J in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				imageToAnalyze.getProcessor().setAutoThreshold("Minimum", true, ImageProcessor.BLACK_AND_WHITE_LUT);
//				IJ.log("CHECKPOINT K in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				//IJ.run(imageToAnalyze, "Convert to Mask", null);
//				IJ.log("CHECKPOINT L in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				partanlzr.analyze(imageToAnalyze);
//				IJ.log("CHECKPOINT M in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				imageToAnalyze = partanlzr.getOutputImage();
//				IJ.log("CHECKPOINT N in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//				((ByteProcessor)(imageToAnalyze.getProcessor())).erode(1, 0);
//				//		((ByteProcessor)(imageToAnalyze.getProcessor())).erode(1, 0);
//				//		((ByteProcessor)(imageToAnalyze.getProcessor())).erode(1, 0);
//				//enable above erode commands if necessary
//			}
//			IJ.log("BRIGHTFIELD findParticles method ended in " +((System.nanoTime() - initialTime)/1000000) +"ms");
//			return imageToAnalyze;	
//
//		}catch(Exception e){
//			IJ.showMessage("Error with finding particles");
//			IJ.log(e.getMessage());
//
//		}
//		return null;
//	}
//
//	/**
//	 *Calculates sum of particle areas in current image using values present in 
//	 *ResultsTable. For this to function as desired, this method must be called 
//	 *right after findParticleAreas().
//	 *
//	 * @param popUp boolean, if true create message window in ImageJ showing summed value
//	 * @return summedValue the sum of all "Area" values in ResultsTable
//	 */
//
//	private static float doTheSum(boolean popUp){
//		float retVal=0;		
//		try{
//			//only if there are values in the results table
//			if(rt.getCounter()>0){
//				//get the values under the column "Area"
//				float[] temp = rt.getColumn(rt.getColumnIndex("Area"));
//				for (int i = 0; i < temp.length; i++){
//					retVal+=temp[i];
//				}
//				//if user desires a pop up message box, do so to display summed pixels on single image
//				if (popUp) 
//					IJ.showMessage("Sum of all particle areas: "+retVal+" (pixels)");
//			}
//		} catch(Exception e){
//			IJ.showMessage("Error with doTheSum");
//			IJ.log(e.getMessage());
//		}
//		return retVal;
//	}
//
//	private void analyzeFullStack(ImagePlus imageToAnalyze){
//		analyzeFullStack(origImage, imageToAnalyze, curSlice, stackSize, imageRes, myMethod, thresholdMin, sizeMin, exclude);
//	}
//
//	/**
//	 * For conducting analysis on image if image is a stack. Creates a custom 
//	 * user-friendly results table containing summed values for slices with particle.
//	 *
//	 * @param originalImage only included to set original image stack's slice to currently analyzing image 
//	 * @param imageToAnalyze ImagePlus stack to run analysis sequence over
//	 * @param currentSlice current slice of stack being analyzed
//	 * @param fullStackSize size of full stack
//	 * @param imageResolution resolution of image being analyzed
//	 * @param threshMin	minimum threshold to set, only applies to Intensity images
//	 * @param sizeMinimum minimum particle size for detection, only applies to brightfield
//	 * @param excludeEdgeParticles ignore particles on the edge of image if true
//	 */
//
//	private static void analyzeFullStack(ImagePlus originalImage, ImagePlus imageToAnalyze, int currentSlice, int fullStackSize, int imageResolution, String method, double threshMin, int sizeMinimum, boolean excludeEdgeParticles){
//		//init
//		sliceTable = new ArrayList<Float>();
//		duplicator = new Duplicator();
//		float particleValue=0;
//		ArrayList<Float> totalParticleValues=new ArrayList<Float>();
//		int numEntries=0;
//		average=0; median=0; min=0; max=0;
//		//	sliceTable.clear();
//		try{
//			twindow.close(true);		//if previous text window is open then this will prompt to save and close...will throw excpetion if first time
//		} catch (Exception e){
//			//fall through				//if no such text window exists yet, fall through and create one.
//		}
//		twindow = new TextWindow("Found Particles", " \t Slice \t \tPixel Area", "", 800, 300);
//		rt = new ResultsTable();
//		rm = new RoiManager(true);
//		float[] areas = null;
//		try{
//			//while there are images left in the stack from current position
//			while(currentSlice<=fullStackSize){	
//				//find particles on current slice
//				ImagePlus tempImage = findParticles(duplicator.run(imageToAnalyze, currentSlice, currentSlice), method, threshMin, sizeMinimum, excludeEdgeParticles, true, true,gaussianSigma);
//				rm.runCommand("Delete");
//				IJ.run(tempImage, "Create Selection", null);				
//				if(tempImage.getRoi()!=null){
//					rm.addRoi(tempImage.getRoi());
//					rm.runCommand("Measure");
//					areas = rt.getColumn(rt.getColumnIndex("Area"));
//					particleValue = areas[areas.length-1];
//				}
//				//sometimes if no particle is found, result says the area is the entire image
//				if (particleValue == imageResolution*imageResolution) particleValue = 0;
//				//create custom results table entry (text window), add value to an array for further analysis
//				if (particleValue!=0){
//					twindow.append("Particle(s) found in slice    \t"+ currentSlice+ "\t    with a total pixel area of    \t"+particleValue);
//					totalParticleValues.add(particleValue);
//					sliceTable.add((float) currentSlice);
//					numEntries++;
//				}
//				//set image to next slice
//				currentSlice++;
//				try{
//					imageToAnalyze.setSlice(currentSlice);
//					if(originalImage!=null){
//						originalImage.setSlice(currentSlice);
//					}
//				} finally{
//					//do nothing...this means we've reached the end of the stack
//				}
//
//			} 
//			//sort the values in array to find median
//			Collections.sort(totalParticleValues);
//			particleValue=0;
//			//sum the values in array to find average
//			for(int i=0; i<totalParticleValues.size(); i++){
//				particleValue+=totalParticleValues.get(i);
//			}
//			//make custom entry for average median min and max
//			if(numEntries!=0){
//				average = (particleValue/numEntries);
//				twindow.append("Average particle pixel area over    \t"+ numEntries + "\t    slices is    \t"+average);
//				if(totalParticleValues!=null){
//					median = (totalParticleValues.get(numEntries/2));
//					min = (totalParticleValues.get(0));
//					max = (totalParticleValues.get(totalParticleValues.size()-1));
//					twindow.append("Median particle pixel area from    \t"+ numEntries + "\t    slices is    \t"+median);
//					twindow.append("MIN particle pixel area over    \t"+ numEntries + "\t    slices is    \t"+min);
//					twindow.append("MAX particle pixel area over    \t"+ numEntries + "\t    slices is    \t"+max);
//				}
//			}
//			else twindow.append("No particls found to average...");	
//			imageToAnalyze.flush();
//			imageToAnalyze.close();
//			//			IJ.log("Size of sliceTable: " + Integer.toString(sliceTable.size()) + "\n");
//			for (int i=0; i<sliceTable.size(); i++){
//				IJ.log("Found in slice : " + sliceTable.get(i).toString());
//			}
//			IJ.log("--------finished running plugin over entire stack--------");
//
//		} catch (IndexOutOfBoundsException e){
//			IJ.log("Finished processing image stack with index error.");
//		} catch (Exception e){
//			IJ.showMessage("Error with processing stack.");
//			IJ.log(e.getMessage());
//		}
//	}
//
//	//NOTE: calling these methods directly from WiscScan's Java as opposed to through ImageJ for both speed and function
//	// -its more efficient to get in between the processes and get specific values that the process needs instead of
//	// -running IJ.run("Find_Particle_Areas")...
//	/**
//	 * To be used only by WiscScan Java calls, for any mode but ratio or estimate ratio
//	 *
//	 * @param imageToAnalyze ImagePlus image to analyze
//	 * @param isIntensity true if imageToAnalyze is an intensity image
//	 * @param excludeOnEdge ignore particles on the edge of image if true
//	 * @param threshMin	minimum threshold to set, only applies to Intensity images
//	 * @param minSize minimum particle size for detection, only applies to brightfield
//	 */
//	public static float[] inWiscScanMode(ImagePlus imageToAnalyze, boolean isIntensity, boolean excludeOnEdge, double threshMin, int minSize, double sigmaGaussian){
//		float[] summedPixelAreasArray;	
//		Interpreter.batchMode=true;
//		rt = new ResultsTable();
//		rm = new RoiManager(true);
//		try{
//			//if image is of intensity, do related calculations, else do brightfield calculations
//			if(isIntensity){
//				findParticles(imageToAnalyze, "intensity", threshMin, 0, false, true, true, sigmaGaussian);
//			}			
//			else{
//				findParticles(imageToAnalyze, "brightfield", 0, minSize, excludeOnEdge, true, true, sigmaGaussian);
//			} 
//
//			//get the pixel areas for all particles in this image as array
//
//			if(rt.getCounter()>0){
//				//get the values under the column "Area"
//				imageToAnalyze.close();
//				return summedPixelAreasArray = rt.getColumn(rt.getColumnIndex("Area"));
//			}
//		}catch(Exception e){
//			Interpreter.batchMode=false;
//			IJ.showMessage("Error with finding particles plugin in WiscScan Mode");
//			IJ.log(e.getMessage());
//			//fall through
//		}
//		summedPixelAreasArray=new float[1];
//		summedPixelAreasArray[0]=0;
//		return summedPixelAreasArray;
//	}
//
//	/**
//	 * To be used only by WiscScan Java calls, for only ratio or estimate ratio modes
//	 *
//	 * @param bfImage Brightfield image or image stack to be analyzed
//	 * @param intImage Intensity image or image stack to be analyzed
//	 * @param threshMin Minimum threshold for intensity image, max is set to 255
//	 * @param sizeMin Minimum size limit for particles in brightfield image, max is positive infinity
//	 * @param excludeOnEdge Option to exclude particles on edge of images
//	 */
//	public static ImagePlus[] ratioModeInWiscScan(ImagePlus bfImage, ImagePlus intImage, double threshMin, int sizeMin, boolean excludeOnEdge, double sigmaGaussian){
//
//		//create both masks
//		try{
//			rt = new ResultsTable();
//			rm = new RoiManager(true);
//			createRatioMask(bfImage, intImage, threshMin, sizeMin, excludeOnEdge, false, false, sigmaGaussian);
//
//			ImagePlus[] returnImage = new ImagePlus[2];
//			returnImage[0]=bfMask;
//			returnImage[1]=intMask;
//			//		bfMask.show();
//			//		intMask.show();
//			return returnImage;
//
//		} catch(Exception e){
//			Interpreter.batchMode=false;
//			IJ.log("Error in realtime Ratio mode.");
//			IJ.log(e.getLocalizedMessage() + "....." + e.toString());
//		}
//		return null;
//	}
//
//}