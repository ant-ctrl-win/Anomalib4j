package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.canonical.RigidTransform;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScrewHeadAnalysisTest {
    @Test void normalMedianBandsKeepDirectedHeadAndHalfOpenBoundaries() throws Exception {
        // Given normal geometry only, with one explicitly rejected case.
        var rows=ComparisonArtifacts.JSON.readTree("[{\"preprocessing\":{\"pose\":{\"state\":\"OK\"},\"support\":{\"support\":{\"minX\":0,\"maxX\":600,\"minY\":200,\"maxY\":400}}}},{\"preprocessing\":{\"pose\":{\"state\":\"SOURCE_BORDER\"}}}]");
        // When fixed normal support is derived.
        var bands=ScrewHeadSupport.define(rows);
        // Then +x final quarter is head, with disjoint controls and no TEST dependency.
        assertEquals(new ScrewHeadSupport.Bounds(450,600,200,400),bands.head());
        assertEquals("head",bands.region(450,300));assertEquals("neck",bands.region(449,300));
        assertEquals("thread",bands.region(299,300));assertEquals("other",bands.region(600,300));
    }
    @Test void nativeMembershipTransportsIdentityCellsWithoutChangingScores() {
        var bands=new ScrewHeadSupport.Bands(new ScrewHeadSupport.Bounds(500,650,250,450),new ScrewHeadSupport.Bounds(350,500,250,450),new ScrewHeadSupport.Bounds(50,350,250,450));
        var a=new RigidTransform(1,0,100,0);
        assertEquals("head",ScrewHeadSupport.region(bands,a,5*14+8));
        assertEquals("neck",ScrewHeadSupport.region(bands,ScrewIdentityControl.IDENTITY,5*14+8));
        assertEquals(-1,ScrewHeadSupport.cell(-1,0));assertEquals(0,ScrewHeadSupport.cell(0,0));assertEquals(195,ScrewHeadSupport.cell(699,699));
    }
    @Test void decompositionSeparatesRawCenteringAndScaleExactly() {
        var d=ScrewHeadSupport.decompose(new double[]{8,2,3},new double[]{9,4,2});
        assertEquals(.5,d.delta(),1e-15);assertEquals(d.delta(),d.raw()+d.alpha()+d.beta(),1e-15);
        assertThrows(IllegalArgumentException.class,()->ScrewHeadSupport.decompose(new double[]{1,0,0},new double[]{1,0,1}));
    }
    @Test void maxOriginAndHeadRankPreserveFirstTieAndGlobalRule() {
        double[] z=new double[196];z[20]=9;z[21]=9;z[50]=5;
        var r=ScrewHeadSupport.response(z,new int[]{50,51});
        assertEquals(20,r.get("argmax"));assertEquals(3,r.get("head_rank"));assertEquals(-4.0,r.get("head_minus_global"));
        assertArrayEquals(new int[]{20,21,50,0,1},(int[])r.get("top5_cells"));
    }
    @Test void surroundingRingUsesChebyshevDistanceAndExcludesAllGroundTruth() {
        boolean[] mask=new boolean[25];mask[12]=true;
        var ring=ScrewHeadPixels.ring(mask,5,1);int count=0;for(boolean value:ring)if(value)count++;
        assertEquals(8,count);assertFalse(ring[12]);assertTrue(ring[6]);assertFalse(ring[0]);
    }
    @Test void componentPeaksFollowEvaluatorEightConnectivity() {
        boolean[] mask=new boolean[25];mask[0]=true;mask[6]=true;mask[24]=true;double[] scores=new double[25];scores[0]=2;scores[6]=4;scores[24]=3;
        assertEquals(java.util.List.of(4.0,3.0),ScrewHeadPixels.componentPeaks(scores,mask));
    }
    @Test void diagnosticMaskCoordinatesPreserveIdentityAndExposeBoundaryCells() {
        boolean[] gt=new boolean[1024*1024];gt[0]=true;gt[gt.length-1]=true;
        assertArrayEquals(gt,ScrewHeadPixels.pull(gt,1024,ScrewIdentityControl.IDENTITY));
        var cells=ScrewHeadPixels.nativeOverlap(gt,ScrewIdentityControl.IDENTITY);assertTrue(cells[0]);assertTrue(cells[195]);
        int count=0;for(boolean value:cells)if(value)count++;assertEquals(2,count);
    }
    @Test void descriptiveImageStatisticsDoNotInventContrastOnUniformSurface() {
        double[] image=new double[16];java.util.Arrays.fill(image,.5);boolean[] selection=new boolean[16];selection[5]=selection[6]=selection[9]=selection[10]=true;
        var stats=ScrewHeadPixels.imageStats(image,selection);assertEquals(4,stats.get("selected_pixels"));assertEquals(0.0,stats.get("gradient_energy"));assertEquals(0.0,stats.get("contrast_to_ring"));
    }
    @Test void peakPercentileIncludesTiesWithoutSelectingAnOperatingThreshold() {
        assertEquals(.75,ScrewHeadPixels.percentile(new double[]{1,2,2,3},2));
    }
    @Test void originalSummariesRetainExactGtAndSurroundingResponse() {
        boolean[] mask=new boolean[1024*1024];mask[512*1024+512]=true;double[] map=new double[mask.length];map[512*1024+512]=7;
        var stats=ScrewHeadPixels.original(map,mask);assertEquals(7.0,stats.get("gt_mean"));assertEquals(0.0,stats.get("ring_mean"));assertEquals(1.0,stats.get("gt_peak_percentile"));assertEquals(true,stats.get("global_peak_inside_gt"));assertEquals(1,stats.get("components"));
    }
    @Test void testAnalysisRequiresNormalFreeze() throws Exception {
        var path=java.nio.file.Files.createTempDirectory("screw-head-order-");
        try {assertThrows(IllegalStateException.class,()->ScrewHeadAnalysis.main(new String[]{"analyze",path.toString()}));}
        finally {java.nio.file.Files.delete(path);}
    }
}
