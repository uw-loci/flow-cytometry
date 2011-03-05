//
// FlowCytometry.java
//

/*
Server application for flow cytometry with WiscScan.

Copyright (c) 2008, UW-Madison LOCI
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the UW-Madison LOCI nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
*/

package loci.apps.flow;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.ImageWindow;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import java.awt.BorderLayout;
import java.awt.Scrollbar;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Vector;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.ImageWriter;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.services.OMEXMLService;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;
import visad.DataReferenceImpl;
import visad.Display;
import visad.DisplayImpl;
import visad.FlatField;
import visad.FunctionType;
import visad.List1DDoubleSet;
import visad.RealType;
import visad.ScalarMap;
import visad.VisADException;
import visad.java2d.DisplayImplJ2D;

/**
 * TODO
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://dev.loci.wisc.edu/trac/java/browser/trunk/projects/flow-cytometry/src/main/java/loci/apps/flow/Slice.java">Trac</a>,
 * <a href="http://dev.loci.wisc.edu/svn/java/trunk/projects/flow-cytometry/src/main/java/apps/flow/Slice.java">SVN</a></dd></dl>
 *
 * @author Hidayath Ansari
 */
class Slice {
  int num;
  boolean hasParticles;
  int begin;
  int end;

  public Slice(int n) {
    num = n;
    hasParticles = false;
  }

  public void print() {
    if (hasParticles) {
      System.out.println("Slice "+num+" begins with particle "+
        begin+" and ends at "+end);
    }
    else System.out.println("Slice "+num+" has no particles");
  }
}

public class FlowCytometry {

  /** Debugging flag. */
  protected static boolean debug = true;

  private static final int MIN_INTENSITY = 0;
  private static final int MAX_INTENSITY = 255;

  private static final int INTENSITY_AXIS = 0;
  private static final int AREA_AXIS = 1;

  private static final int PIXELS = 0;
  private static final int MICRONS = 1;

  private static ImageJ ij;
  private static ImagePlus imp;
  private static ImageStack stack;
  private static ByteProcessor bp;
  private static ColorModel theCM;
  private static Detector d;

  public static int nSlices;
  private static int nParticles;
  private static JFrame frame;
  private static JCheckBox CBcumulative;
  private static boolean cumulative;
  private static Vector<Double> areaValues;
  private static Vector<Double> intensityValues;
  private static Vector<Double> diameterValues;
  private static Vector<Integer> cellFrameV = new Vector<Integer>();

  private static Vector<Particle> particles;
  private static Vector<Slice> slices;

  //These are pointers to vectors containing actual values
  //to axes as specified in WiscScan.
  private static Vector<Double> XAxisValues;
  private static Vector<Double> YAxisValues;
  private static int xAxis, yAxis;

  private static RealType area, intensity, diameter, xType, yType;
  private static FunctionType fn;
  private static DataReferenceImpl data_ref;
  private static Scrollbar scroll;
  private static DisplayImpl display;
  private static ResultsTable rt;
  private static double maxArea, minArea, maxIntensity, minIntensity;
  private static ScalarMap xMap, yMap;

  private static double pixelMicronSquared;

  private static int resolutionWidth = 0;
  private static int resolutionHeight = 0;
  private static int minX=0, maxX=0, minY=0, maxY=0;
  private static int intensityThreshold=30;
  private static int areaThresholdInPixels=100;
  private static int areaUnit=0;
  private static boolean showParticles=false;
  private static boolean b_logY = false;
  private static Vector<Particle> lastFrameParticles;

  private static Vector<Integer> sliceBegin, sliceEnd;
  // sliceBegin[i] is the minimum identifier of all particles on slice i
  // sliceEnd[i] is the maximum identifier of all particles on slice i

  // For XML metadata
  private static String s_Name, s_Experiment, s_Params, s_Date;

  public static void startImageJ() {
    ij = new ImageJ();
    ij.getBounds();
  }

  public static void incrementSlices() {
    nSlices++;
  }

  public static void showImage(int width, int height, byte[] imageData) {
    //bp = new ByteProcessor(width,height,imageData,
    //  ImageTools.makeColorModel(1, DataBuffer.TYPE_BYTE));
    bp = new ByteProcessor(width,height,imageData, theCM);
    bp.createImage();
    stack.addSlice("Slice "+nSlices, bp);
    imp.setStack("Islet images", stack);
    imp.setSlice(stack.getSize());
    imp.show();

    if (nSlices == 1) {
      stack.deleteSlice(1);
    }
    else if (nSlices == 2) {
      ImageWindow stackwin = imp.getWindow();
      scroll = (Scrollbar) stackwin.getComponent(1);

      AdjustmentListener l = new AdjustmentListener() {
        @SuppressWarnings("synthetic-access")
        @Override
        public void adjustmentValueChanged(AdjustmentEvent arg0) {
          try {
            int slideNum =
              ((Scrollbar) imp.getWindow().getComponent(1)).getValue();
            //for the detected particles window
            if (showParticles) {
              d = new Detector(resolutionWidth,
                intensityThreshold, areaThresholdInPixels);
              d.findParticles(stack.getProcessor(slideNum));
              d.crunchArray();
              Detector.displayImage(d.getFloodArray());
            }

            //for the graph
            //IJ.log("This is slide "+slideNum+
            //  " and particle numbers on this slide go from "+
            //  sliceBegin[slideNum]+" to "+sliceEnd[slideNum]);
            //IJ.log(Integer.toString(((Scrollbar)
            //  Intensity_.this.imp.getWindow().getComponent(1)).getValue()));
            FlowCytometry.data_ref.setData(
              newestGetData(slideNum, cumulative, intensity, fn));
            FlowCytometry.display.reDisplayAll();
            //Intensity_.this.data_ref.setData(
            //  getData(imp.getCurrentSlice(), cumulative, intensity, fn));
          }
          catch (RemoteException e) {
            // ignore exceptions
          }
          catch (VisADException e) {
            // ignore exceptions
          }
        }
      };
      scroll.addAdjustmentListener(l);
    }
  }

  public static void initVars() {
    maxArea=Double.MIN_VALUE;
    minArea=Double.MAX_VALUE;
    maxIntensity = Double.MIN_VALUE;
    minIntensity = Double.MAX_VALUE;
    nSlices = 0;
    nParticles = 0;

    particles = new Vector<Particle>();
    slices = new Vector<Slice>();

    sliceBegin = new Vector<Integer>();
    sliceEnd = new Vector<Integer>();
    areaValues = new Vector<Double>();
    intensityValues = new Vector<Double>();
    diameterValues = new Vector<Double>();

    XAxisValues = intensityValues;
    YAxisValues = areaValues;

    area = RealType.getRealType("Area");
    intensity = RealType.getRealType("Intensity");
    diameter = RealType.getRealType("Diameter");
  }

  public static void init(int width, int height, double pixelsPerMicron) {
    setResolution(width, height);
    s_Date = new java.text.SimpleDateFormat("MM.dd.yyyy hh:mm:ss").format(
      new java.util.Date());

    byte[] r = new byte[256];
    byte[] g = new byte[256];
    byte[] b = new byte[256];

    for(int ii=0 ; ii<256 ; ii++)
    r[ii] = g[ii] = b[ii] = (byte)ii;

    theCM = new IndexColorModel(8, 256, r,g,b);
    imp = new ImagePlus("Islet images",
      new ByteProcessor(resolutionWidth,resolutionHeight));
    stack = new ImageStack(resolutionWidth, resolutionHeight, theCM);
    imp.show();

    Detector.createImageHolder(resolutionWidth, resolutionHeight);

    imp.unlock();

    initVars();

    pixelMicronSquared = 0.149*0.149;
    if (pixelsPerMicron > 0) {
      pixelMicronSquared = pixelsPerMicron*pixelsPerMicron;
    }

    byte[] dummyData = new byte[resolutionWidth*resolutionHeight];
    bp = new ByteProcessor(resolutionWidth,resolutionHeight,dummyData, theCM);
    bp.createImage();
    stack.addSlice("Slice "+nSlices, bp);
    imp.setStack("Islet images", stack);

    imp.setSlice(1);

    try {
      // Display initialization

      display = new DisplayImplJ2D("Graph Display");
      data_ref = new DataReferenceImpl("data_ref");
      data_ref.setData(null);
      display.addReference(data_ref);
      display.getGraphicsModeControl().setScaleEnable(true);
      display.getGraphicsModeControl().setPointSize(3);
      setAxes(0,1);

      frame = new JFrame("Graph Window");
      frame.setLayout(new BorderLayout());

      CBcumulative = new JCheckBox("Cumulative");
      CBcumulative.setMnemonic(KeyEvent.VK_G);
      JPanel bottomPanel = new JPanel();
      bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.LINE_AXIS));
      bottomPanel.add(CBcumulative);

      frame.getContentPane().add(display.getComponent(), BorderLayout.CENTER);
      frame.getContentPane().add(bottomPanel, BorderLayout.SOUTH);

      ItemListener CBitemListener = new ItemListener() {
        @SuppressWarnings("synthetic-access")
        @Override
        public void itemStateChanged(ItemEvent itemEvent) {
          cumulative = itemEvent.getStateChange() == ItemEvent.SELECTED;
          try {
            FlowCytometry.data_ref.setData(newestGetData(
              imp.getCurrentSlice(), cumulative, intensity, fn));
            FlowCytometry.display.reDisplayAll();
          }
          catch (RemoteException e) {
            // ignore exceptions
          }
          catch (VisADException e) {
            // ignore exceptions
          }
        }
      };
      CBcumulative.addItemListener(CBitemListener);

      imp.setSlice(1);
      frame.setSize(600, 600);
      frame.setVisible(true);
    }
    catch (VisADException e) {
      IJ.log("VisAD Exception in init: "+e.getMessage());
    }
    catch (RemoteException re) {
      IJ.log("Remote Exception: "+re.getMessage());
    }
  }

  public static void setAxes(int x, int y) {
    // 0 - Intensity
    // 1 - Area
    // 2 - Diameter
    xAxis = x; yAxis = y;

    switch(xAxis) {
      case 0:
        xType = intensity;
        XAxisValues = intensityValues;
        break;
      case 1:
        xType = area;
        XAxisValues = areaValues;
        break;
      case 2:
        xType = diameter;
        XAxisValues = diameterValues;
        break;
    }

    switch(yAxis) {
      case 0:
        yType = intensity;
        YAxisValues = intensityValues;
        break;
      case 1:
        yType = area;
        YAxisValues = areaValues;
        break;
      case 2:
        yType = diameter;
        YAxisValues = diameterValues;
        break;
    }
    try {
      xMap = new ScalarMap(xType, Display.XAxis);
      yMap = new ScalarMap(yType, Display.YAxis);
      xMap.setRange(getMinX(), getMaxX());
      yMap.setRange(getMinY(), getMaxY());

      fn = new FunctionType(xType, yType);
      display.clearMaps();
      display.addMap(xMap);
      display.addMap(yMap);

    }
    catch (RemoteException e) { e.printStackTrace(); }
    catch (VisADException e) { e.printStackTrace(); }
  }

  // Setters

  public static void setResolution(int width, int height) {
    resolutionWidth = width;
    resolutionHeight = height;
  }

  public static void setXAxis(int option) { xAxis = option; }
  public static void setYAxis(int option) { yAxis = option; }
  public static void setName(String name) { s_Name = name; }
  public static void setExperiment(String exp) { s_Experiment = exp; }
  public static void setParams(String params) { s_Params = params; }
  public static void setMinX(int val) { minX = val; }
  public static void setMaxX(int val) { maxX = val; }
  public static void setMinY(int val) { minY = val; }
  public static void setMaxY(int val) { maxY = val; }
  public static void setIntensityThreshold(int val) {
    intensityThreshold = val;
  }
  public static void setAreaThreshold(int val) {
    if (areaUnit == PIXELS) {
      areaThresholdInPixels = val;
    }
    else if (areaUnit == MICRONS) {
      areaThresholdInPixels = (int) Math.round(val*pixelMicronSquared);
    }
  }
  public static void setAreaUnits(int val) { areaUnit = val; }
  public static void setPixelMicronSquared(double val) {
    pixelMicronSquared = val*val;
  }
  public static void reprocessAll() {
    particles = new Vector<Particle>();
    slices = new Vector<Slice>();
    nParticles = 0;
    nSlices = 0;
    for (int i=0; i<stack.getSize(); i++) {
      incrementSlices();
      newestProcessFrame(i+1);
    }
    updateGraph();
  }

  public static void saveDataWithXML() throws FormatException, IOException {
    ImageWriter iw = new ImageWriter();

    OMEXMLService service = null;
    OMEXMLMetadata omexmlMeta = null;

    Exception exc = null;
    try {
      ServiceFactory factory = new ServiceFactory();
      service = factory.getInstance(OMEXMLService.class);
      omexmlMeta = service.createOMEXMLMetadata();
    }
    catch (DependencyException e) {
      throw new FormatException("Could not create OMEXMLMetadataStore", exc);
    }
    catch (ServiceException e) {
      throw new FormatException("Could not create OMEXMLMetadataStore", exc);
    }

    omexmlMeta.createRoot();

    omexmlMeta.setPixelsSizeX(new PositiveInteger(resolutionWidth), 0);
    omexmlMeta.setPixelsSizeY(new PositiveInteger(resolutionHeight), 0);
    omexmlMeta.setPixelsSizeZ(new PositiveInteger(1), 0);
    omexmlMeta.setPixelsSizeC(new PositiveInteger(1), 0);
    omexmlMeta.setPixelsSizeT(new PositiveInteger(stack.getSize()), 0);
    try {
      omexmlMeta.setPixelsType(PixelType.fromString("uint8"), 0);
    }
    catch (EnumerationException e) {
      throw new FormatException(e);
    }
    omexmlMeta.setPixelsBinDataBigEndian(Boolean.FALSE, 0, 0);
    try {
      omexmlMeta.setPixelsDimensionOrder(DimensionOrder.fromString("XYTZC"), 0);
    }
    catch (EnumerationException e) {
      throw new FormatException(e);
    }
    omexmlMeta.setExperimenterFirstName(s_Name, 0);

    iw.setMetadataRetrieve(omexmlMeta);
    service.populateOriginalMetadata(omexmlMeta, "Experiment", s_Experiment);
    service.populateOriginalMetadata(omexmlMeta, "Parameters", s_Params);
    service.populateOriginalMetadata(omexmlMeta, "Date", s_Date);
    service.populateOriginalMetadata(omexmlMeta,
      "AreaValues", flattenVector(areaValues));
    service.populateOriginalMetadata(omexmlMeta,
      "IntensityValues", flattenVector(intensityValues));

    // setImageCreationDate, setImageName on OME-XML metadata
    iw.setId("testImage.ome.tiff");

    System.out.println(stack.getSize());
    for (int i=1; i<=stack.getSize(); i++) {
      byte[] byteArray = (byte[]) stack.getProcessor(i).getPixels();
      iw.saveBytes(i - 1, byteArray);
    }
    iw.close();
  }

  public static void newestProcessFrame() {
    newestProcessFrame(nSlices);
  }

  public static void newestProcessFrame(int sliceNum) {
    imp.unlock();
    d = new Detector(resolutionWidth,
      intensityThreshold, areaThresholdInPixels);
    d.findParticles(stack.getProcessor(sliceNum));
    lastFrameParticles = d.crunchArray();
    if (showParticles) {
      System.out.println("Processing slice "+sliceNum);
      Detector.displayImage(d.getFloodArray());
    }
    Slice thisSlice = new Slice(nSlices);
    if (lastFrameParticles.size() > 0) {
      thisSlice.hasParticles = true;
      thisSlice.begin = nParticles;
      thisSlice.end = nParticles + lastFrameParticles.size() - 1;
    }

    for (int i = 0; i < lastFrameParticles.size(); i++) {
      Particle thisParticle = lastFrameParticles.get(i);
      thisParticle.setNum(nParticles++);
      thisParticle.setSliceNum(nSlices);
      thisParticle.setPixelsPerMicron(pixelMicronSquared);
      addParticle(thisParticle);

      //thisParticle.print();

      int thisArea = thisParticle.getMicronArea();
      int thisIntensity = thisParticle.getIntensity();
      if (thisArea > maxArea) maxArea = thisArea;
      if (thisArea < minArea) minArea = thisArea;
      if (thisIntensity > maxIntensity) maxIntensity = thisIntensity;
      if (thisIntensity < minIntensity) minIntensity = thisIntensity;
    }

    slices.add(thisSlice);
    System.out.println("Particles size is "+particles.size());
    //for (int i=0; i<particles.size(); i++) particles.get(i).print();
  }

  public static int[] getParticleMicronAreas() {
    int[] micronAreas = new int[lastFrameParticles.size()];
    for (int i = 0; i < micronAreas.length; i++) {
      micronAreas[i] = lastFrameParticles.get(i).getMicronArea();
    }
    return micronAreas;
  }

  private static boolean addParticle(Particle particle) {
    int particleIndex = particles.size()-1;
    if (particles.size() == 0) {
      particles.add(particle);
      return false;
    }
    int lastSliceNum = nSlices-1;
    boolean duplicate = false;
    Particle lastParticle = particles.get(particleIndex);
    while (lastParticle.getSliceNum() == lastSliceNum) {
      duplicate = isSameParticle(particle, lastParticle);
      if (duplicate) {
        lastParticle.deactivate();
        debug("Duplicate particle detected");
        break;
      }
      if (particleIndex == 0) break;
      lastParticle = particles.get(--particleIndex);
    }
    particles.add(particle);
    return duplicate;
  }

  private static boolean isSameParticle(Particle oldP, Particle newP) {
    int nMatches = 0;
    double tolerance = 0.1;
    if (newP.getPixelArea() < (1+tolerance)*oldP.getPixelArea() &&
      newP.getPixelArea() > (1-tolerance)*oldP.getPixelArea())
    {
      nMatches++;
    }
    if (newP.getMeanIntensity() < (1+tolerance)*oldP.getMeanIntensity() &&
      newP.getMeanIntensity() > (1-tolerance)*oldP.getMeanIntensity())
    {
      nMatches++;
    }
    if (newP.getCentroidY() < (1+tolerance)*oldP.getCentroidY() &&
      newP.getCentroidY() > (1-tolerance)*oldP.getCentroidY())
    {
      nMatches++;
    }
    if (newP.getSizeY() < (1+tolerance)*oldP.getSizeY() &&
      newP.getSizeY() > (1-tolerance)*oldP.getSizeY())
    {
      nMatches++;
    }
    if (newP.getCentroidX() < (1+tolerance)*oldP.getCentroidX() &&
      newP.getCentroidX() > (1-tolerance)*oldP.getCentroidX())
    {
      nMatches++;
    }
    if (newP.getSizeX() < (1+tolerance)*oldP.getSizeX() &&
      newP.getSizeX() > (1-tolerance)*oldP.getSizeX())
    {
      nMatches++;
    }

    //Do something with the x-position and flow rate here.

    return nMatches > 2;
  }

  public static void newProcessFrame() {
    imp.unlock();
    ImageProcessor ip = stack.getProcessor(imp.getCurrentSlice());
    double totalArea=0, totalIntensity=0;
    for (int i=0; i<resolutionWidth; i++) {
      for (int j=0; j<resolutionHeight; j++) {
        int value = ip.getPixel(i, j);
        if (value >= intensityThreshold) {
          totalArea++;
          totalIntensity += value;
        }
      }
    }
    IJ.log("Area: "+totalArea);
    IJ.log("Intensity: "+totalIntensity);
    if (totalArea > maxArea) maxArea = totalArea;
    if (totalArea < minArea) minArea = totalArea;
    if (totalIntensity > maxIntensity) maxIntensity = totalIntensity;
    if (totalIntensity < minIntensity) minIntensity = totalIntensity;
    areaValues.add(totalArea);
    if (totalArea != 0) intensityValues.add(totalIntensity/totalArea);
    else intensityValues.add(0.0);
    System.out.println("just added to vectors: "+
      totalArea+ " "+totalIntensity/totalArea);
  }

  public static void processFrame() {
    incrementSlices();
    imp.unlock();
    bp.setThreshold(25, 255, 1);
    IJ.run("Analyze Particles...",
      "size=0-Infinity circularity=0.00-1.00 show=Nothing display clear");
    rt = Analyzer.getResultsTable();
    double totalArea=0, totalIntensity=0;
    for (int j=0; j<rt.getCounter(); j++) {
      double jArea = rt.getValue("Area", j);
      totalArea += jArea;
      totalIntensity += (rt.getValue("Mean", j))*jArea;
    }
    if (totalArea > maxArea) maxArea = totalArea;
    if (totalArea < minArea) minArea = totalArea;
    if (totalIntensity > maxIntensity) maxIntensity = totalIntensity;
    if (totalIntensity < minIntensity) minIntensity = totalIntensity;
    areaValues.add(totalArea);
    intensityValues.add(totalIntensity);
    cellFrameV.add(nSlices);

    sliceBegin.ensureCapacity(2*nSlices);
    sliceEnd.ensureCapacity(2*nSlices);
    sliceBegin.add(nSlices);
    sliceEnd.add(nSlices);
    System.out.println("############: sliceBegin looks like ");
    for (int i=0; i<sliceBegin.size(); i++) {
      System.out.println(sliceBegin.get(i));
    }
  }

  private static double getMinX() {
    double areaRange = (maxArea - minArea);///pixelMicronSquared;

    if (minX !=0) return minX;
    switch (xAxis) {
      case AREA_AXIS:
        return (minArea - 0.05*areaRange);
      case INTENSITY_AXIS:
        return MIN_INTENSITY;
      default:
        return (minArea - 0.05*areaRange);
    }
  }

  private static double getMaxX() {
    double areaRange = (maxArea - minArea);///pixelMicronSquared;

    if (maxX !=0) return maxX;
    switch (xAxis) {
      case AREA_AXIS:
        return (maxArea+0.05*areaRange);
      case INTENSITY_AXIS:
        return MAX_INTENSITY;
      default:
        return (maxArea+0.05*areaRange);
    }
  }

  private static double getMinY() {
    double areaRange = (maxArea - minArea);///pixelMicronSquared;
    double retval;

    if (minY !=0) return minY;
    switch(yAxis) {
      case AREA_AXIS:
        retval = (minArea - 0.05*areaRange);
        break;
      case INTENSITY_AXIS:
        retval = MIN_INTENSITY;
        break;
      default:
        retval = (minArea - 0.05*areaRange);
        break;
    }

    if (b_logY) {
      if (retval < 0) {
        debug("getMinY() returning 0 (in log case)");
        return 0;
      }
      return Math.log(retval);
    }
    return retval;
  }

  private static double getMaxY() {
    double areaRange = (maxArea - minArea);///pixelMicronSquared;
    double retval;

    if (maxY !=0) return maxY;
    switch (yAxis) {
      case AREA_AXIS:
        retval = (maxArea+0.05*areaRange);
        break;
      case INTENSITY_AXIS:
        retval = MAX_INTENSITY;
        break;
      default:
        retval = (maxArea+0.05*areaRange);
        break;
    }

    if (b_logY) {
      if (retval < 0) return 0;
      return Math.log(retval);
    }
    return retval;
  }

  //public static void oldupdateGraph() {
  //  //1.55^2 from the pixels/micron value:
  //  double areaRange = (maxArea - minArea)/pixelMicronSquared;
  //  try {
  //    areaMap.setRange(minArea/pixelMicronSquared - 0.05*areaRange,
  //      maxArea/pixelMicronSquared+0.05*areaRange);
  //    data_ref.setData(newGetData(nSlices, true, intensity, fn));
  //    display.addReference(data_ref);
  //  }
  //  catch (VisADException e) {
  //    IJ.log("VisAD Exception in updateGraph: "+e.getMessage());
  //  }
  //  catch (RemoteException re) {
  //    IJ.log("Remote Exception: "+re.getMessage());
  //  }
  //}

  public static void updateGraph() {
    try {
      xMap.setRange(getMinX(), getMaxX());
      yMap.setRange(getMinY(), getMaxY());
      data_ref.setData(newestGetData(nSlices, true, xType, fn));
      //display.addReference(data_ref);
      display.reDisplayAll(); // Replacing above line with this
    }
    catch (VisADException e) {
      IJ.log("VisAD Exception in updateGraph: "+e.getMessage());
    }
    catch (RemoteException re) {
      IJ.log("Remote Exception: "+re.getMessage());
    }
  }

  public static FlatField newestGetData(int slice, boolean cumul,
    RealType x, FunctionType funcType)
  {
    //slice is NOT zero-indexed.
    double[] xArray, yArray;
    int beginSlice=0, endSlice=slice-1;
    if (!cumul) beginSlice = slice-1;
    int beginIndex = Integer.MAX_VALUE;
    int endIndex = Integer.MIN_VALUE;
    int numParticles=0;
    for (int i=beginSlice; i<=endSlice; i++) {
      if (slices.get(i).hasParticles) {
        if (slices.get(i).begin < beginIndex) beginIndex = slices.get(i).begin;
        if (slices.get(i).end > endIndex) endIndex = slices.get(i).end;
        numParticles += (slices.get(i).end - slices.get(i).begin + 1);
      }
    }

    //System.out.println("beginIndex:"+beginIndex+
    //  " endIndex:"+endIndex+" numParticles:"+numParticles);

    if (numParticles == 0) return null;

    double minXGraph = getMinX(), maxXGraph = getMaxX();
    double minYGraph = getMinY(), maxYGraph = getMaxY();

    double[] newxArray = new double[numParticles];
    double[] newyArray = new double[numParticles];
    int arrayindex = 0;

    for (int i=beginIndex; i<=endIndex; i++) {
      if (particles.get(i).getStatus()) {
        boolean xActive = true, yActive = true;
        double xValue=0, yValue=0;
        switch(xAxis) {
          case INTENSITY_AXIS:
            xValue = particles.get(i).getMeanIntensity();
            if (xValue < minXGraph || xValue > maxXGraph) xActive = false;
            break;
          case AREA_AXIS:
            xValue = particles.get(i).getMicronArea();
            if (xValue < minXGraph || xValue > maxXGraph) xActive = false;
            break;
        }
        switch(yAxis) {
          case INTENSITY_AXIS:
            yValue = particles.get(i).getMeanIntensity();
            yValue = (b_logY ? Math.log(yValue) : yValue);
            if (yValue < minYGraph || yValue > maxYGraph) yActive = false;
            break;
          case AREA_AXIS:
            yValue = particles.get(i).getMicronArea();
            yValue = (b_logY ? Math.log(yValue) : yValue);
            if (yValue < minYGraph || yValue > maxYGraph) yActive = false;
            break;
        }
        if (xActive && yActive) {
          newxArray[arrayindex] = xValue;
          newyArray[arrayindex] = yValue;
          arrayindex++;
        }
      }
    }

    xArray = new double[arrayindex];
    yArray = new double[arrayindex];
    for (int i=0; i<arrayindex; i++) {
      xArray[i] = newxArray[i];
      yArray[i] = newyArray[i];
    }

    /* Old code
    xArray = new double[numParticles];
    yArray = new double[numParticles];

    for (int i=beginIndex; i<=endIndex; i++) {
      switch(xAxis) {
        case INTENSITY_AXIS:
          xArray[i-beginIndex] = particles.get(i).getMeanIntensity();
          break;
        case AREA_AXIS:
          xArray[i-beginIndex] = particles.get(i).getMicronArea();
          break;
      }
      switch(yAxis) {
        case INTENSITY_AXIS:
          yArray[i-beginIndex] = particles.get(i).getMeanIntensity();
          break;
        case AREA_AXIS:
          yArray[i-beginIndex] = particles.get(i).getMicronArea();
          break;
      }
    }
    */
    //for (int i=0; i<xArray.length; i++) {
    //  debug("Now plotting "+Double.toString(xArray[i]) +
    //    " " + Double.toString(yArray[i]));
    //}

    List1DDoubleSet xSet;
    FlatField ff=null;
    if (xArray.length > 0) try {
      xSet = new List1DDoubleSet(xArray, x, null, null);
      ff = new FlatField(funcType, xSet);
      double[][] ff_vals = new double[1][xArray.length];
      ff_vals[0] = yArray;
      ff.setSamples(ff_vals);
    }
    catch (VisADException e) {
      IJ.log("VisAD Exception in newGetData: "+e.getMessage());
    }
    catch (RemoteException re) {
      IJ.log("Remote Exception: "+re.getMessage());
    }
    return ff;
  }

  public static FlatField newGetData(int slice,
    boolean cumul, RealType x, FunctionType funcType)
  {
    //slice is NOT zero-indexed.
    double[] xArray, yArray;
    int beginIndex=0, endIndex;
    if (!cumul) beginIndex = slice-1;
    endIndex = slice-1;
    xArray = new double[endIndex - beginIndex + 1];
    yArray = new double[endIndex - beginIndex + 1];
    for (int i=beginIndex; i<=endIndex; i++) {
      //intensityValues.get(i);
      xArray[i-beginIndex] = XAxisValues.get(i);
      //areaValues.get(i)/pixelMicronSquared;
      yArray[i-beginIndex] = YAxisValues.get(i);
    }
    System.out.println("Plotting for slices between indices "+
      beginIndex+ " and "+endIndex);
    //printVector(XAxisValues);
    //System.out.println("--");
    //printVector(YAxisValues);
    for (int i=beginIndex; i<=endIndex; i++) {
      IJ.log("Now plotting "+Double.toString(xArray[i-beginIndex]) +
        " " + Double.toString(yArray[i-beginIndex]));
    }
    List1DDoubleSet xSet;
    FlatField ff=null;
    if (endIndex >= beginIndex) try {
      xSet = new List1DDoubleSet(xArray, x, null, null);
      ff = new FlatField(funcType, xSet);
      double[][] ff_vals = new double[1][xArray.length];
      ff_vals[0] = yArray;
      ff.setSamples(ff_vals);
    }
    catch (VisADException e) {
      IJ.log("VisAD Exception in newGetData: "+e.getMessage());
    }
    catch (RemoteException re) {
      IJ.log("Remote Exception: "+re.getMessage());
    }
    return ff;
  }

  public static FlatField getData(int slice,
    boolean cumul, RealType x, FunctionType funcType)
  {
    double[] xArray, yArray;
    int beginIndex=0, endIndex;
    if (!cumul) beginIndex = sliceBegin.get(slice);
    endIndex = sliceEnd.get(slice);
    xArray = new double[endIndex - beginIndex + 1];
    yArray = new double[endIndex - beginIndex + 1];
    IJ.log("In getData, begin and end indices are "+
      beginIndex+ " and "+endIndex);
    for (int i=beginIndex; i<=endIndex; i++) {
      //IJ.log("Assigning values in the x and y arrays");
      xArray[i-beginIndex] = intensityValues.elementAt(i);
      yArray[i-beginIndex] = areaValues.elementAt(i);
    }
    IJ.log("Done assigning");
    for (int i=beginIndex; i<=endIndex; i++) {
      IJ.log(Double.toString(xArray[i-beginIndex]) +
        " " + Double.toString(yArray[i-beginIndex]));
    }
    List1DDoubleSet intensitySet;
    FlatField ff=null;
    if (endIndex >= beginIndex) {
      try {
        intensitySet = new List1DDoubleSet(xArray, x, null, null);
        ff = new FlatField(funcType, intensitySet);
        double[][] ff_vals = new double[1][xArray.length];
        ff_vals[0] = yArray;
        ff.setSamples(ff_vals);
      }
      catch (VisADException e) {IJ.log("VisAD Exception: "+e.getMessage());}
      catch (RemoteException re) {IJ.log("Remote Exception: "+re.getMessage());}
    }
    return ff;
  }

  public static void saveValues() throws IOException {
    saveValues("values"+s_Date.substring(0,9));
  }

  public static void saveValues(String filename) throws IOException {
    BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
    bw.write("Area (micron^2)\t\t(pixel^2)\t\tIntensity\t\tFrame\t\tLast");
    bw.newLine();
    bw.newLine();
    System.out.println("Particles size is "+particles.size());
    for (Particle p : particles) {
      bw.write(p.getMicronArea()+"\t\t"+
        p.getPixelArea()+"\t\t"+
        p.getMeanIntensity()+"\t\t"+
        p.getSliceNum()+"\t\t"+
        // NB: The same particle across multiple timepoints will be labeled
        // with Last=no until the final position of that particle, which will
        // be labeled Last=yes.
        (p.getStatus() ? "no" : "yes"));
      bw.newLine();
    }
    bw.flush();
    bw.close();
  }

  public static void showParticles(boolean val) {
    showParticles = val;
    if (val) Detector.impParticles.show();
    else Detector.impParticles.hide();
  }

  public static void processFile(String filename) throws IOException {
    ImagePlus imagePlus = IJ.openImage(filename);
    ImageStack imageStack = imagePlus.getStack(); // TODO: handle exception
    int size = imagePlus.getWidth();

    double PixelsPerMicron = Double.valueOf(IJ.getString(
      "Please enter the pixels/micron value for this analysis", "0.1"));
    pixelMicronSquared = PixelsPerMicron*PixelsPerMicron;

    // Close the other open windows
    if (frame != null) frame.dispose();
    imagePlus.close();

    init(size, size, PixelsPerMicron);
    showParticles(true);
    for (int i=1; i<=imageStack.getSize(); i++) {
      byte[] imageData = (byte[]) imageStack.getPixels(i);
      incrementSlices();
      showImage(size, size, imageData);
      newestProcessFrame(i);
      updateGraph();
    }

    saveValues(filename+".values");
  }

  public static void printVector(Vector<Double> v) {
    for (int i=0; i<v.size(); i++) {
      double dtemp = v.get(i);
      System.out.println(dtemp);
    }
  }

  private static String flattenVector(Vector<Double> v) {
    String retval = "";
    for (int i=0; i<v.size(); i++) {
      retval += v.get(i)+"\n";
    }
    return retval;
  }

  public static void main(String[] args) throws IOException {
    String filename = "";
    if (args.length > 0) filename = args[0];
    else {
      String wd = System.getProperty("user.dir");
      JFileChooser fc = new JFileChooser(wd);
      int rc = fc.showDialog(null, "Select Data File");
      if (rc == JFileChooser.APPROVE_OPTION) {
        File file = fc.getSelectedFile();
        filename = file.getAbsolutePath();
      }
      else {
        System.out.println("File chooser cancel button clicked");
        return;
      }
    }
    intensityThreshold = Integer.valueOf(IJ.getString(
      "Please enter the intensity threshold for this analysis", "30"));
    areaThresholdInPixels = Integer.valueOf(IJ.getString(
      "Please enter the area threshold in pixels", "100"));
    processFile(filename);
    System.out.println("Done. Press Ctrl-C to exit");
  }

  private static void debug(String msg) {
    if (debug) System.out.println("FlowCytometry: " + msg);
  }

  public static void setLogY(boolean b_logy) {
    b_logY = b_logy;
  }

  public static void closeAllWindows() {
    ij.quit();
    frame.dispose();
  }

}
