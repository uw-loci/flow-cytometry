///////////////////////////////////////////////////////////////////////////////
// Title:            FlowCytoTest
// Description:		 JUnit test verifying that the FlowCyto class works.
//
// Author:           Johannes Schindelin, UW-Madison LOCI
// Web:				 loci.wisc.edu
//
///////////////////////////////////////////////////////////////////////////////

package loci.apps.flow;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import ij.IJ;
import ij.ImagePlus;

import java.net.URL;

import org.junit.Test;

/**
 * Unit tests for the FlowCyto package.
 * 
 * @author Johannes Schindelin 
 * @author UW-Madison LOCI
 */
public class FlowCytoTest {

	@Test
	public void testFoundParticle() {
		initImagePair("positive_sample_bf.tif", "positive_sample_int.tif");
		assertTrue(FlowCyto.foundParticle(false, true, 0, 100, 100, 5000, 2.2));
		initImagePair("negative_sample_bf.tif", "negative_sample_int.tif");
		assertFalse(FlowCyto.foundParticle(false, true, 0, 100, 100, 5000, 2.2));
	}

	private void initImagePair(final String brightfieldFilename, final String intensityFilename) {
		// we cannot do this in a loop because FlowCyto.init() resets the slice counters
		final ImagePlus impBF = openImage(brightfieldFilename);
		final ImagePlus impIN = openImage(intensityFilename);

		assertNotNull(impBF);
		assertNotNull(impIN);

		FlowCyto.init("brightfield", impBF.getWidth(), impBF.getHeight(), 1.2);
		FlowCyto.init("intensity", impBF.getWidth(), impBF.getHeight(), 1.2);

		FlowCyto.showImage("brightfield", impBF.getWidth(), impBF.getHeight(), (byte[])impBF.getProcessor().getPixels());
		FlowCyto.showImage("intensity", impIN.getWidth(), impIN.getHeight(), (byte[])impIN.getProcessor().getPixels());
	}

	private ImagePlus openImage(final String resource) {
		final URL url = getClass().getResource("/" + resource);
		if (url == null || !"file".equals(url.getProtocol())) return null;
		return IJ.openImage(url.getPath());
	}
}
