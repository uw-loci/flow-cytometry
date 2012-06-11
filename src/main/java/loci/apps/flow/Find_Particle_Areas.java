package loci.apps.flow;

import java.util.ArrayList;
import java.util.Collections;

import ij.*;
import ij.plugin.Duplicator;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import ij.gui.GenericDialog;
import ij.macro.Interpreter;
import ij.measure.ResultsTable;

public class Find_Particle_Areas implements PlugInFilter {

	private static String myMethod, myResolution;
	private static double thresholdMin;
	private static boolean exclude, doTheSum, dofullStack; 
	private ImagePlus imp, origImage;
	private int stackSize, curSlice;
	private static int sizeMin;
	private int imageRes;
	private static Duplicator duplicator;
	private static TextWindow twindow;
	private static float average, max, min, median;
	private static ArrayList<Float> sliceTable; 


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

			gd.showDialog();
			if (gd.wasCanceled()) return;

			//populate static variables
			myMethod = gd.getNextChoice();
			myResolution = gd.getNextChoice();
			thresholdMin= (double) gd.getNextNumber();
			sizeMin= (int) gd.getNextNumber();
			exclude=gd.getNextBoolean (); 
			doTheSum= gd.getNextBoolean();
			dofullStack = gd.getNextBoolean();

			//begin core program
			Interpreter.batchMode=true;

			if (myResolution.equals("128x128")) imageRes=128;
			else if (myResolution.equals("64x64")) imageRes=64;
			else if (myResolution.equals("256x256")) imageRes=256;
			else if (myResolution.equals("512x512")) imageRes=512;
			else imageRes = 128;

			//check option for analyze full stack, bypass other methods if so.
			//uses original stacked image, not single image
			if (dofullStack){
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

	/**
	 *Main image analysis portion of plugin, analyzes image based on type (intensity/brightfield). 
	 *Results are set up in the RoiManager and ResultsTable, nothing is returned.
	 *
	 * @param imageToAnalyze ImagePlus image to analyze
	 */

	private static void findParticles(ImagePlus imageToAnalyze){
		findParticles(imageToAnalyze, myMethod, thresholdMin, sizeMin, exclude);
	}

	private static void findParticles(ImagePlus imageToAnalyze, String method, double threshMin, int sizeMinimum, boolean excludeEdgeParticles){
		try{
			//if image is of intensity, do related calculations, else do brightfield calculations
			if(method.equals("Intensity")){
				imageToAnalyze.getProcessor().setThreshold(threshMin, 255, ImageProcessor.RED_LUT);
				IJ.run(imageToAnalyze, "Convert to Mask", null);

				if(excludeEdgeParticles) IJ.run(imageToAnalyze, "Analyze Particles...", "size=1-Infinity circularity=0.00-1.00 show=Masks display exclude clear add");
				else IJ.run(imageToAnalyze, "Analyze Particles...", "size=1-Infinity circularity=0.00-1.00 show=Masks display clear add");
			}			
			else{
				IJ.run(imageToAnalyze, "Find Edges", null);
				IJ.run(imageToAnalyze, "Find Edges", null);

				IJ.run(imageToAnalyze, "Gaussian Blur...", "sigma=5");

				IJ.run(imageToAnalyze, "Auto Threshold", "method=Minimum white");

				if(excludeEdgeParticles) IJ.run(imageToAnalyze, "Analyze Particles...", "size="+sizeMinimum+"-Infinity circularity=0.00-1.00 show=Masks display exclude clear include add");
				else IJ.run(imageToAnalyze, "Analyze Particles...", "size="+sizeMinimum+"-Infinity circularity=0.00-1.00 show=Masks display clear include add");

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
				findParticles(duplicator.run(imageToAnalyze, currentSlice, currentSlice), method, threshMin, sizeMinimum, excludeEdgeParticles);
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


	public static float[] inWiscScanMode(ImagePlus imageToAnalyze, boolean isIntensity, boolean excludeOnEdge, double threshMin, int minSize){
		float[] summedPixelAreasArray;	
		Interpreter.batchMode=true;
		try{
			//if image is of intensity, do related calculations, else do brightfield calculations
			if(isIntensity){
				imageToAnalyze.getProcessor().setThreshold(threshMin, 255, ImageProcessor.RED_LUT);
				IJ.run(imageToAnalyze, "Convert to Mask", null);

				if(excludeOnEdge) IJ.run(imageToAnalyze, "Analyze Particles...", "size="+minSize+"-Infinity circularity=0.00-1.00 show=Masks display exclude clear add");
				else IJ.run(imageToAnalyze, "Analyze Particles...", "size="+minSize+"-Infinity circularity=0.00-1.00 show=Masks display clear add");
			}			
			else{
				IJ.run(imageToAnalyze, "Find Edges", null);
				IJ.run(imageToAnalyze, "Find Edges", null);

				IJ.run(imageToAnalyze, "Gaussian Blur...", "sigma=5");

				IJ.run(imageToAnalyze, "Auto Threshold", "method=Minimum white");

				if(excludeOnEdge) IJ.run(imageToAnalyze, "Analyze Particles...", "size="+minSize+"-Infinity circularity=0.00-1.00 show=Masks display exclude clear include add");
				else IJ.run(imageToAnalyze, "Analyze Particles...", "size="+minSize+"-Infinity circularity=0.00-1.00 show=Masks display clear include add");
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