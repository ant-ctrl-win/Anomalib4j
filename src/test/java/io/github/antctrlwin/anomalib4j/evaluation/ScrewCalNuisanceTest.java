package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.canonical.RigidTransform;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ScrewCalNuisanceTest {
    @TempDir Path temporary;
    @Test void factorOnePreservesEveryLogicalChannelExactly() {
        int[] rgb=new int[256];for(int i=0;i<256;i++)rgb[i]=(i<<16)|((255-i)<<8)|i;
        for(var family:ScrewCalPhotometry.Family.values())assertArrayEquals(rgb,ScrewCalPhotometry.perturb(rgb,family,1).rgb());
    }
    @Test void formulasFollowExistingProtocolWithSingleClipAndRounding() {
        for(var family:ScrewCalPhotometry.Family.values())for(double level:ScrewCalPhotometry.LEVELS) {
            var image=new BufferedImage(224,224,BufferedImage.TYPE_INT_RGB);int[] rgb=new int[224*224];for(int i=0;i<rgb.length;i++)rgb[i]=((i%256)<<16)|(((i+17)%256)<<8)|((i+91)%256);image.setRGB(0,0,224,224,rgb,0,224);
            var old=RobustnessTransforms.apply(image,RobustnessTransforms.Family.valueOf(family.name()),level);
            assertArrayEquals(old.getRGB(0,0,224,224,null,0,224),Arrays.stream(ScrewCalPhotometry.perturb(rgb,family,level).rgb()).map(v->v|0xff000000).toArray());image.flush();old.flush();
        }
    }
    @Test void brightnessAndContrastClipRatherThanWrapAndGammaHasCorrectDirection() {
        assertEquals(0xffffff,ScrewCalPhotometry.perturb(new int[]{0xffffff},ScrewCalPhotometry.Family.BRIGHTNESS,1.2).rgb()[0]);
        assertEquals(3,ScrewCalPhotometry.perturb(new int[]{0},ScrewCalPhotometry.Family.CONTRAST,1.2).clippedChannels());
        assertEquals(0x666666,ScrewCalPhotometry.perturb(new int[]{0x808080},ScrewCalPhotometry.Family.BRIGHTNESS,.8).rgb()[0]);
        int gamma=(int)Math.round(255*Math.pow(128/255.0,1.2));assertEquals(gamma,ScrewCalPhotometry.perturb(new int[]{0x808080},ScrewCalPhotometry.Family.GAMMA,1.2).rgb()[0]&255);
    }
    @Test void factorOneNativeGrayBridgeAndFrozenPoseNeedNoSegmentation() {
        var gray=new BufferedImage(1024,1024,BufferedImage.TYPE_BYTE_GRAY);for(int y=0;y<1024;y++)for(int x=0;x<1024;x++)gray.getRaster().setSample(x,y,0,(x+y)%256);
        int[] logical=gray.getRGB(0,0,1024,1024,null,0,1024),reference=GeneralityImages.view(gray);var pose=RigidTransform.center(330,360,37);
        for(var family:ScrewCalPhotometry.Family.values())assertArrayEquals(ScrewCalPhotometry.tensor(reference,pose),ScrewCalPhotometry.tensor(ScrewCalPhotometry.view(ScrewCalPhotometry.perturb(logical,family,1).rgb()),pose));
        var uniform=new int[1024*1024];assertDoesNotThrow(()->ScrewCalPhotometry.tensor(ScrewCalPhotometry.view(uniform),pose));
    }
    @Test void frozenPoseReusedWithoutMutatingItAcrossPhotometricLevels() {
        int[] logical=new int[1024*1024];Arrays.fill(logical,0x8899aa);var pose=new RigidTransform(1,0,20,-12);
        for(var family:ScrewCalPhotometry.Family.values())for(double level:ScrewCalPhotometry.LEVELS) {
            int[] view=ScrewCalPhotometry.view(ScrewCalPhotometry.perturb(logical,family,level).rgb());float[] expected=new float[3*224*224];new io.github.antctrlwin.anomalib4j.canonical.AffineTensorSampler(io.github.antctrlwin.anomalib4j.canonical.AffineTensorSampler.Kernel.BILINEAR).sample(view,pose,expected);
            assertArrayEquals(expected,ScrewCalPhotometry.tensor(view,pose));assertEquals(new RigidTransform(1,0,20,-12),pose);
        }
    }
    @Test void allowlistRejectsForbiddenDataBeforeAnyFilesystemRead() throws Exception {
        Path cal=temporary.resolve("train/img/screw_good_008.png");var access=new ScrewCalAccess(List.of(cal));
        assertThrows(IllegalArgumentException.class,()->access.image(temporary.resolve("test/img/screw_good_008.png")));
        assertThrows(IllegalArgumentException.class,()->access.array(temporary.resolve("evaluation/maps/foreign.npy")));assertTrue(access.reads().isEmpty());
        assertThrows(IllegalArgumentException.class,()->new ScrewCalAccess(List.of(temporary.resolve("test/img/foreign.png"))));
        assertEquals(cal.toAbsolutePath().normalize(),access.require(cal));
        assertThrows(IllegalArgumentException.class,()->ScrewCalAccess.source(temporary,"../test/foreign.png"));
        assertThrows(IllegalArgumentException.class,()->ScrewCalAccess.source(temporary,"screw_good_179.png"));
    }
    @Test void exactPopulationKeepsSourceBorderExclusionAndOrdering() {
        var attempted=new ArrayList<String>();for(int i=128;i<192;i++)attempted.add("screw_good_%03d.png".formatted(i));var usable=attempted.stream().filter(n->!n.equals("screw_good_179.png")).toList();
        ScrewCalAccess.population(attempted,usable);assertThrows(IllegalArgumentException.class,()->ScrewCalAccess.population(attempted,attempted));var reverse=new ArrayList<>(usable);Collections.reverse(reverse);assertThrows(IllegalArgumentException.class,()->ScrewCalAccess.population(attempted,reverse));
    }
    @Test void quantilesAndSpearmanHandleLinearInterpolationAndTies() {
        assertEquals(2.7,ScrewCalPhotometry.stats(new double[]{0,1,2,3}).p90(),1e-15);
        assertArrayEquals(new double[]{1,2.5,2.5,4},ScrewCalPhotometry.ranks(new double[]{1,2,2,3}));
        assertEquals(-1,ScrewCalPhotometry.spearman(new double[]{1,2,3},new double[]{3,2,1}),1e-15);
        assertNull(ScrewCalPhotometry.spearman(new double[]{1,1,1},new double[]{2,3,4}));
    }
    @Test void winnerChangeAndFrozenScaleAccountingSumToPairedAmplification() throws Exception {
        var i=ComparisonArtifacts.JSON.readTree("{\"delta_max_z\":2,\"raw_growth_at_winner\":6,\"beta_at_winner\":2,\"winner_switch_penalty\":-1}");
        var c=ComparisonArtifacts.JSON.readTree("{\"delta_max_z\":7,\"raw_growth_at_winner\":8,\"beta_at_winner\":1,\"winner_switch_penalty\":-1}");
        var result=ScrewCalSummary.pair(i,c);assertEquals(5.0,result.get("amplification"));assertEquals(1.0,result.get("raw_term"));assertEquals(4.0,result.get("scaling_term"));assertEquals(0.0,result.get("winner_switch_term"));
    }
    @Test void scoringCannotStartWithoutFrozenPrerequisite() {
        assertThrows(IllegalStateException.class,()->ScrewCalNuisance.main(new String[]{"run",temporary.toString()}));
    }
}
