package loci.apps.flow;

import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.util.ArrayList;
import java.util.Collections;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.plugin.Duplicator;
import ij.plugin.ImageCalculator;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import ij.gui.GenericDialog;
import ij.macro.Interpreter;
import ij.measure.Measurements;
import ij.measure.ResultsTable;

public class Find_Particle_Areas implements PlugInFilter {

	private static String myMethod, myResolution;
	private static double thresholdMin;
	private static boolean exclude, doTheSum, dofullStack, multipleImagesAvail, doRatio; 
	private ImagePlus imp, origImage, bfImp, intImp;
	private static ImagePlus intMask, bfMask;
	private int stackSize, curSlice;
	private static int sizeMin;
	private int imageRes;
	private static Duplicator duplicator;
	private static TextWindow twindow;
	private static float average, max, min, median;
	private static ArrayList<Float> sliceTable; 
	private static ParticleAnalyzer partanlzr;



	/**
	 * Original setup method called by ImageJ when plugin is run.
	 *
	 * @param arg String arg0
	 * @param image ImagePlus image to run calculation on
	 */
	@Override
	public int setup(String arg, ImagePlus image) {
		origImage = image;
		return DOES_8G | NO_CHANGES;
	}

	/**
	 * Main program that is run by calling this plugin. Plugin prompts user with
	 * options, then calculates desired results. Options include: brightfield/intensity 
	 * image being used, threshold minimum if intensity, size minimum if brightfield, 
	 * to exclude particles on the edge during analysis, to show the summed result of
	 * analysis on single image, and/or to run calculation over entire stack if image 
	 * is a stack.
	 *
	 * @param arg0 ImageProcessor object created when plugin is started.
	 * @param DialogOptions list of options displayed in dialog box, not actually 
	 * passed in as parameter.
	 */
	@Override
	public void run(ImageProcessor arg0) {
		try{
			//get information from user about image and desired analysis

			GenericDialog gd = new GenericDialog("Calculate Particle Areas");
			String [] methods={"Brightfield", "Intensity"};
			String [] resolutions={"64x64", "128x128", "256x256", "512x512"};
			gd.addChoice("Channel:", methods, methods[0]);
			gd.addChoice("Resolution:", resolutions, resolutions[1]);
			gd.addMessage ("Special paramters; (thresholdMax = 255, SizeMax=Infinity)");
			gd.addNumericField ("Threshold_Minimum",  170, 0);
			gd.addNumericField ("Size_Minimum",  0, 0);
			gd.addCheckbox("Exclude_Particles_on_Edge",true);
			gd.addCheckbox("Show_summed_areas",false);
			gd.addCheckbox("Run_Plugin_Over_Entire_Stack", false);

			int[] wList = WindowManager.getIDList();
			if(wList.length>=2){
				String[] availImages = new String[wList.length];
				for (int i=0; i<wList.length; i++) {
					imp = WindowManager.getImage(wList[i]);
					availImages[i] = imp!=null?imp.getTitle():"";
				}
				gd.addCheckbox("Calculate Ratio between:", false);
				gd.addChoice("BRIGHTFIELD image:", availImages, availImages[0]);
				gd.addChoice("INTENSITY image:", availImages, availImages[1]);
				multipleImagesAvail = true;
			} else 
				multipleImagesAvail = false;


			gd.showDialog();
			if (gd.wasCanceled()) return;

			//populate static variables
			myMethod = gd.getNextChoice();
			myResolution = gd.getNextChoice();
			thresholdMin= (double) gd.getNextNumber();
			sizeMin= (int) gd.getNextNumber();
			exclude=gd.getNextBoolean(); 
			doTheSum= gd.getNextBoolean();
			dofullStack = gd.getNextBoolean();
			if(multipleImagesAvail) doRatio= gd.getNextBoolean();


			//begin core program
			Interpreter.batchMode=true;

			if (myResolution.equals("128x128")) imageRes=128;
			else if (myResolution.equals("64x64")) imageRes=64;
			else if (myResolution.equals("256x256")) imageRes=256;
			else if (myResolution.equals("512x512")) imageRes=512;
			else imageRes = 128;

			//check option for analyze full stack, bypass other methods if so.
			//uses original stacked image, not single image
			if(doRatio) {
				int index = gd.getNextChoiceIndex();
				bfImp = WindowManager.getImage(wList[index]);
				index = gd.getNextChoiceIndex();
				intImp = WindowManager.getImage(wList[index]);
				createRatioMask(dofullStack);
			}			
			else if(dofullStack){
				stackSize = origImage.getStackSize();
				curSlice= origImage.getCurrentSlice();
				analyzeFullStack(origImage.duplicate());
			} 
			else{
				//create a duplicate and run calculations on it to preserve original image
				imp = new ImagePlus("duplicate", arg0.duplicate()); // has only one slice
				findParticles(imp);
				if (doTheSum)
					doTheSum(true);

				imp.close();
			}
			Interpreter.batchMode=false;
			IJ.log("plugin done");

		} catch(Exception e){
			IJ.showMessage("Error with plugin.");
			IJ.log(e.getLocalizedMessage());
			Interpreter.batchMode=false;
		}
	}

	private ImagePlus createRatioMask(boolean doFullStack){
		return createRatioMask(bfImp, intImp, thresholdMin, sizeMin, exclude, imageRes, doFullStack, true);
	}

	private static ImagePlus createRatioMask(ImagePlus bfImage, ImagePlus intImage, double threshMin, int sizeMin, boolean excludeOnEdge, int imageRes, boolean doFullStack, boolean showMask){
		//initialize
		ImagePlus tempInt = null, tempBF = null;
		intMask = new ImagePlus("Cell Intensity inside Outlines");
		bfMask = new ImagePlus("Cell Outlines");
		ImageCalculator ic = new ImageCalculator();
		Duplicator dup = new Duplicator();
		ImageStack intMaskStack = null, bfMaskStack = null;

		//we dont need the colormodel or stack if image is not going to be displayed (i.e. in WiscScan operations, we process only
		// one image at a time, and wouldn't want a new image window appear for each of 3000+ images)
		if(showMask){
			byte[] r = new byte[256];
			for(int ii=0 ; ii<256 ; ii++)
				r[ii]=(byte)ii;
			ColorModel theCM = new IndexColorModel(8, 256, r,r,r);
			intMaskStack = new ImageStack(imageRes, imageRes, theCM);
			bfMaskStack = new ImageStack(imageRes, imageRes, theCM);
		} 	

		for(int i=bfImage.getCurrentSlice(); i<=bfImage.getStackSize(); i++){
			bfImage.setSlice(i);
			intImage.setSlice(i);

			findParticles(dup.run(bfImage, i, i), "Brightfield", 0, sizeMin, excludeOnEdge, !doFullStack, !doFullStack);
			tempBF = partanlzr.getOutputImage();		//temp now holds mask from brightfield image 
			findParticles(dup.run(intImage, i, i), "Intensity", threshMin, 1, false, false, false);
			tempInt = ic.run("AND create", tempBF, partanlzr.getOutputImage());		//temp now holds the bf mask AND'ed with the intensity mask

			if(showMask){
				bfMaskStack.addSlice("Slice "+i, tempBF.getProcessor());
				bfMask.setStack("Cell Outlines", bfMaskStack);
				bfMask.setSlice(i);
				bfMask.unlock();
				
				intMaskStack.addSlice("Slice "+i, tempInt.getProcessor());
				intMask.setStack("Cell Intensity inside Outlines", intMaskStack);
				intMask.setSlice(i);
				intMask.unlock();
			} else intMask=tempInt;
			//goes through calculation only once if we dont want to analyze the full stack
			if(!doFullStack) i=bfImage.getStackSize()+1;
		}
		//bfImage.close();
		//intImage.close();
		if(showMask){
			Interpreter.batchMode=false;
			intMask.show();
			bfMask.show();
			Interpreter.batchMode=true;
		}
		return intMask;
	}

	private static void initParticleAnalyzer(boolean isIntensity, boolean excludeOnEdge, double sizeMinimum, boolean addToManager, boolean showResults){
		ResultsTable rt = ResultsTable.getResultsTable();
		if(rt==null) rt = new ResultsTable();

		int options = 0;
		if (excludeOnEdge) options |= ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
		if (addToManager) options |= ParticleAnalyzer.ADD_TO_MANAGER;
		if (showResults) options |= ParticleAnalyzer.SHOW_RESULTS | ParticleAnalyzer.SHOW_SUMMARY;
		options |= ParticleAnalyzer.CLEAR_WORKSHEET;
		options |= ParticleAnalyzer.SHOW_MASKS;
		
		if(isIntensity){
			partanlzr = new ParticleAnalyzer(options, Measurements.AREA|Measurements.PERIMETER, rt, 1, Double.POSITIVE_INFINITY);
		} else{
			options |= ParticleAnalyzer.INCLUDE_HOLES|ParticleAnalyzer.CLEAR_WORKSHEET|ParticleAnalyzer.SHOW_MASKS;
			partanlzr = new ParticleAnalyzer(options, Measurements.AREA|Measurements.PERIMETER, rt, sizeMinimum, Double.POSITIVE_INFINITY);
		}		
	}

	/**
	 *Main image analysis portion of plugin, analyzes image based on type (intensity/brightfield). 
	 *Results are set up in the RoiManager and ResultsTable, nothing is returned.
	 *
	 * @param imageToAnalyze ImagePlus image to analyze
	 */

	private static void findParticles(ImagePlus imageToAnalyze){
		findParticles(imageToAnalyze, myMethod, thresholdMin, sizeMin, exclude, true, true);
	}

	/**
	 *Main image analysis portion of plugin, analyzes image based on type (intensity/brightfield). 
	 *Results are set up in the RoiManager and ResultsTable, nothing is returned.
	 *
	 * @param imageToAnalyze ImagePlus image to analyze
	 * @param method if "Intensity" does intensity analysis, otherwise does brightfield analyzis
	 * @param threshMin	minimum threshold to set, only applies to Intensity images
	 * @param sizeMinimum minimum particle size for detection, only applies to brightfield
	 * @param excludeEdgeParticles ignore particles on the edge of image if true
	 */

	private static void findParticles(ImagePlus imageToAnalyze, String method, double threshMin, int sizeMinimum, boolean excludeEdgeParticles, boolean addToManager, boolean showResults){
		try{
			if(method.equalsIgnoreCase("Intensity")){
				initParticleAnalyzer(true, excludeEdgeParticles, sizeMinimum, addToManager, showResults);
				imageToAnalyze.getProcessor().setThreshold(threshMin, 255, ImageProcessor.RED_LUT);
				IJ.run(imageToAnalyze, "Convert to Mask", null);

				partanlzr.analyze(imageToAnalyze);
			}	
			else{
				initParticleAnalyzer(false, excludeEdgeParticles, sizeMinimum, addToManager, showResults);
				IJ.run(imageToAnalyze, "Find Edges", null);
				//		IJ.run(imageToAnalyze, "Find Edges", null);		//commenting because this gives a little worse estimation
				IJ.run(imageToAnalyze, "Gaussian Blur...", "sigma=2");	//sigma value changed from 5 to 2 for better estimation
				IJ.run(imageToAnalyze, "Auto Threshold", "method=Minimum white");

				partanlzr.analyze(imageToAnalyze);
			} 	

		}catch(Exception e){
			IJ.showMessage("Error with finding particles");
			IJ.log(e.getMessage());

		}
	}

	/**
	 *Calculates sum of particle areas in current image using values present in 
	 *ResultsTable. For this to function as desired, this method must be called 
	 *right after findParticleAreas().
	 *
	 * @param popUp boolean, if true create message window in ImageJ showing summed value
	 * @return summedValue the sum of all "Area" values in ResultsTable
	 */

	private static float doTheSum(boolean popUp){
		float retVal=0;		
		try{
			//only if there are values in the results table
			if(ResultsTable.getResultsTable().getCounter()>0){
				//get the values under the column "Area"
				float[] temp = ResultsTable.getResultsTable().getColumn(ResultsTable.getResultsTable().getColumnIndex("Area"));
				for (int i = 0; i < temp.length; i++){
					retVal+=temp[i];
				}
				//if user desires a pop up message box, do so to display summed pixels on single image
				if (popUp) 
					IJ.showMessage("Sum of all particle areas: "+retVal+" (pixels)");
			}
		} catch(Exception e){
			IJ.showMessage("Error with doTheSum");
			IJ.log(e.getMessage());
		}
		return retVal;
	}

	private void analyzeFullStack(ImagePlus imageToAnalyze){
		analyzeFullStack(origImage, imageToAnalyze, curSlice, stackSize, imageRes, myMethod, thresholdMin, sizeMin, exclude);
	}

	/**
	 * For conducting analysis on image if image is a stack. Creates a custom 
	 * user-friendly results table containing summed values for slices with particle.
	 *
	 * @param imageToAnalyze ImagePlus stack to run analysis sequence over
	 */

	private static void analyzeFullStack(ImagePlus originalImage, ImagePlus imageToAnalyze, int currentSlice, int fullStackSize, int imageResolution, String method, double threshMin, int sizeMinimum, boolean excludeEdgeParticles){
		//init
		sliceTable = new ArrayList<Float>();
		duplicator = new Duplicator();
		float particleValue=0;
		ArrayList<Float> totalParticleValues=new ArrayList<Float>();
		int numEntries=0;
		average=0; median=0; min=0; max=0;
		//	sliceTable.clear();
		try{
			twindow.close(true);		//if previous text window is open then this will prompt to save and close...will throw excpetion if first time
		} catch (Exception e){
			//fall through				//if no such text window exists yet, fall through and create one.
		}
		twindow = new TextWindow("Found Particles", " \t Slice \t \tPixel Area", "", 800, 300);

		try{
			//while there are images left in the stack from current position
			while(currentSlice<=fullStackSize){	
				//find particles on current slice
				findParticles(duplicator.run(imageToAnalyze, currentSlice, currentSlice), method, threshMin, sizeMinimum, excludeEdgeParticles, true, true);
				//get sum of pixels of particles on current slice
				particleValue = doTheSum(false);
				//sometimes if no particle is found, result says the area is the entire image
				if (particleValue == imageResolution*imageResolution) particleValue = 0;
				//create custom results table entry (text window), add value to an array for further analysis
				if (particleValue!=0){
					twindow.append("Particle(s) found in slice    \t"+ currentSlice+ "\t    with a total pixel area of    \t"+particleValue);
					totalParticleValues.add(particleValue);
					sliceTable.add((float) currentSlice);
					numEntries++;
				}
				//set image to next slice
				currentSlice++;
				try{
					imageToAnalyze.setSlice(currentSlice);
					if(originalImage!=null){
						originalImage.setSlice(currentSlice);
					}
				} finally{
					//do nothing...this means we've reached the end of the stack
				}

			} 
			//sort the values in array to find median
			Collections.sort(totalParticleValues);
			particleValue=0;
			//sum the values in array to find average
			for(int i=0; i<totalParticleValues.size(); i++){
				particleValue+=totalParticleValues.get(i);
			}
			//make custom entry for average median min and max
			if(numEntries!=0){
				average = (particleValue/numEntries);
				twindow.append("Average particle pixel area over    \t"+ numEntries + "\t    slices is    \t"+average);
				if(totalParticleValues!=null){
					median = (totalParticleValues.get(numEntries/2));
					min = (totalParticleValues.get(0));
					max = (totalParticleValues.get(totalParticleValues.size()-1));
					twindow.append("Median particle pixel area from    \t"+ numEntries + "\t    slices is    \t"+median);
					twindow.append("MIN particle pixel area over    \t"+ numEntries + "\t    slices is    \t"+min);
					twindow.append("MAX particle pixel area over    \t"+ numEntries + "\t    slices is    \t"+max);
				}
			}
			else twindow.append("No particls found to average...");	
			imageToAnalyze.flush();
			imageToAnalyze.close();
			//			IJ.log("Size of sliceTable: " + Integer.toString(sliceTable.size()) + "\n");
			for (int i=0; i<sliceTable.size(); i++){
				IJ.log("Found in slice : " + sliceTable.get(i).toString());
			}
			IJ.log("--------finished running plugin over entire stack--------");

		} catch (IndexOutOfBoundsException e){
			IJ.log("Finished processing image stack with index error.");
		} catch (Exception e){
			IJ.showMessage("Error with processing stack.");
			IJ.log(e.getMessage());
		}
	}


	/*-------------------------------------------------------------------------------------------------------
	public boolean showDialog() {
		int[] wList = WindowManager.getIDList();
		if (wList==null || wList.length<2) {
			error();
			return false;
		}
		String[] titles = new String[wList.length];
		for (int i=0; i<wList.length; i++) {
			ImagePlus imp = WindowManager.getImage(wList[i]);
			titles[i] = imp!=null?imp.getTitle():"";
		}

		GenericDialog gd = new GenericDialog("Combiner");
		gd.addChoice("Stack1:", titles, titles[0]);
		gd.addChoice("Stack2:", titles, titles[1]);
		gd.addCheckbox("Combine Vertically", false);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		int[] index = new int[3];
		int index1 = gd.getNextChoiceIndex();
		int index2 = gd.getNextChoiceIndex();
		imp1 = WindowManager.getImage(wList[index1]);
		imp2 = WindowManager.getImage(wList[index2]);
		vertical = gd.getNextBoolean();
		return true;
	}
//-------------------------------------------------------------------------------------------------------
	 */
	public static float[] inWiscScanMode(ImagePlus imageToAnalyze, boolean isIntensity, boolean excludeOnEdge, double threshMin, int minSize){
		float[] summedPixelAreasArray;	
		Interpreter.batchMode=true;
		try{
			//if image is of intensity, do related calculations, else do brightfield calculations
			if(isIntensity){
				findParticles(imageToAnalyze, "intensity", threshMin, 0, false, true, true);
			}			
			else{
				findParticles(imageToAnalyze, "brightfield", 0, minSize, excludeOnEdge, true, true);
			} 

			//get the pixel areas for all particles in this image as array
			ResultsTable resTab = ResultsTable.getResultsTable();
			if(resTab.getCounter()>0){
				//get the values under the column "Area"
				imageToAnalyze.close();
				return summedPixelAreasArray = resTab.getColumn(resTab.getColumnIndex("Area"));
			}
		}catch(Exception e){
			IJ.showMessage("Error with finding particles plugin");
			IJ.log(e.getMessage());
			//fall through
		}
		summedPixelAreasArray=new float[1];
		summedPixelAreasArray[0]=0;
		Interpreter.batchMode=false;
		return summedPixelAreasArray;
	}

	//does not work after plugin is done
	public static float[] getResults(){
		float[] retVal = new float[4];
		retVal[0] = average;
		retVal[1] = median;
		retVal[2] = min;
		retVal[3] = max;
		return retVal;
	}

	//does not work after plugin is done
	public static ArrayList<Float> getSliceTable(){
		return sliceTable;
	}

	//TODO RatioMode
	public static boolean ratioModeInWiscScan(ImagePlus bfImage, ImagePlus intImage, double threshMin, int sizeMin, boolean excludeOnEdge, float compareTOLow, float compareTOHigh, int debugSliceNumber){
		//create both masks
		createRatioMask(bfImage, intImage, threshMin, sizeMin, excludeOnEdge, bfImage.getHeight(), false, false);
		//at this point, both masks are made and the outline of the cell is the first ROI in the ROI Manager
		//get the cell's total intensity as a ROI too
		RoiManager rm = RoiManager.getInstance2();
		IJ.run(intMask, "Create Selection", null);
		rm.addRoi(intMask.getRoi());
		rm.runCommand("Measure");
		//first entry in ResultsTable will be of cell's pixel area
		//second entry in ResultsTable will be of cell's pixel count of total intensity
		ResultsTable rt = ResultsTable.getResultsTable();
		float[] areas = rt.getColumn(rt.getColumnIndex("Area"));
		//if cell intensity per cell area is within set limits, return true
		IJ.log("Slice " + debugSliceNumber + " has ratio of: " + (areas[1]/areas[0]));
		if((areas[1]/areas[0])>=compareTOLow && (areas[1]/areas[0])<= compareTOHigh) return true;
		
		return false;
	}
	public static float[] analyzeFullStackInWiscScanMode(ImagePlus imageToAnalyze, String method, double threshMin, int sizeMinimum, boolean excludeEdgeParticles){
		//private void analyzeFullStack(ImagePlus originalImage, ImagePlus imageToAnalyze, int currentSlice, int fullStackSize, int imageResolution){
		imageToAnalyze.setSlice(1);
		analyzeFullStack(null, imageToAnalyze, imageToAnalyze.getCurrentSlice(), imageToAnalyze.getStackSize(), imageToAnalyze.getHeight(), method, threshMin, sizeMinimum, excludeEdgeParticles);
		ArrayList<Float> temp = getSliceTable();
		float[] tmp2 = getResults();
		float[] retVal = new float[tmp2.length + temp.size()];

		retVal[0]=tmp2[0];
		retVal[1]=tmp2[1];
		retVal[2]=tmp2[2];
		retVal[3]=tmp2[3];
		for(int i=0; i<temp.size(); i++) 
			retVal[i+4]=temp.get(i);
		return retVal;

	}
}