package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.antctrlwin.anomalib4j.canonical.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalPhase2BridgeTest {
    @TempDir Path temporary;
    private static ObjectNode reference(CanonicalGeometry pose) {
        var node = new ObjectMapper().createObjectNode();
        node.put("status","success"); node.put("reason","ok");
        node.put("cx",pose.cx()); node.put("cy",pose.cy()); node.put("angle",pose.angle());
        var a = pose.transform();
        var forward = node.putArray("forward");
        for (double value : new double[]{a.cos(),-a.sin(),a.tx(),a.sin(),a.cos(),a.ty()}) forward.add(value);
        return node;
    }

    private static CanonicalGeometry pose() {
        return new CanonicalGeometry(CanonicalGeometry.Status.SUCCESS,CanonicalGeometry.Reason.OK,340.25,351.75,23.25,.5,.14,200);
    }

    @Test void candidateMappingIsFrozenAndUnknownCandidateIsRejected() {
        assertEquals(AffineTensorSampler.Kernel.BILINEAR,CanonicalPhase2Bridge.kernel("B"));
        assertEquals(AffineTensorSampler.Kernel.BICUBIC,CanonicalPhase2Bridge.kernel("C"));
        assertEquals(AffineTensorSampler.Kernel.WIDENED_TENT,CanonicalPhase2Bridge.kernel("D"));
        assertThrows(IllegalArgumentException.class,()->CanonicalPhase2Bridge.kernel("E"));
    }

    @Test void retainedGeometryAndAllSixCoefficientsAreChecked() {
        var pose = pose(); var expected = reference(pose);
        assertDoesNotThrow(()->CanonicalPhase2Bridge.requireGeometry(pose,expected));
        expected.withArray("forward").set(2,new ObjectMapper().getNodeFactory().numberNode(10));
        assertThrows(IllegalStateException.class,()->CanonicalPhase2Bridge.requireGeometry(pose,expected));
    }

    @Test void equivalentButDifferentBranchAndSourceFailuresStop() {
        var pose = pose(); var expected = reference(pose);
        expected.put("angle",pose.angle()+90);
        assertThrows(IllegalStateException.class,()->CanonicalPhase2Bridge.requireGeometry(pose,expected));
        var failed = new CanonicalGeometry(CanonicalGeometry.Status.FAILURE,CanonicalGeometry.Reason.AREA_OR_BORDER,349.5,349.5,0,0,0,0);
        assertThrows(IllegalStateException.class,()->CanonicalPhase2Bridge.requireGeometry(failed,reference(pose)));
    }

    @Test void singleDefectDiagnosticMatchesUnchangedUnifiedEvaluator() throws Exception {
        Path dataset = temporary.resolve("dataset"), directory = Files.createDirectory(temporary.resolve("case"));
        DatasetFixture.metadata(dataset,"metal_nut","flip");
        String good = "metal_nut_good_000.png", bad = "metal_nut_flip_000.png";
        DatasetFixture.image(dataset,"test",good,4,3); DatasetFixture.image(dataset,"test",bad,4,3);
        Path manifest = temporary.resolve("test.txt"); Files.write(manifest,List.of(good,bad));
        Files.writeString(directory.resolve("config.json"),"{}");
        double[] anomalous = new double[12]; anomalous[0] = 5;
        ComparisonArtifacts.writeMap(directory.resolve("good.npy"),new double[12],3,4,good,"fixture","evaluated");
        ComparisonArtifacts.writeMap(directory.resolve("bad.npy"),anomalous,3,4,bad,"fixture","evaluated");
        Files.writeString(directory.resolve("predictions.csv"),
                "run_id,method,category,filename,label,defect,image_score,score_kind,native_map_path,eval_map_path,dtype,shape,geometry_id,image_sha256\n"
                +"fixture,M2,metal_nut,"+good+",good,good,0,native_z,,good.npy,float64,14x14,fixture,x\n"
                +"fixture,M2,metal_nut,"+bad+",anomaly,flip,1,native_z,,bad.npy,float64,14x14,fixture,x\n");
        ComparisonEvaluator.evaluate(directory,dataset,manifest,"M2","metal_nut");
        Path output = temporary.resolve("per-defect.json");
        CanonicalPhase2Bridge.main(new String[]{"defects",directory.toString(),dataset.toString(),output.toString()});
        var mapper = new ObjectMapper();
        var expected = mapper.readTree(directory.resolve("metrics-unified.json").toFile());
        var actual = mapper.readTree(output.toFile()).get(0);
        for (String field : List.of("image_auroc","pixel_auroc","aupro030","n_good","n_anomaly","regions","foreground_pixels","background_pixels"))
            assertEquals(expected.path(field).asDouble(),actual.path(field).asDouble(),field);
        assertEquals("flip",actual.path("defect").asText());
    }
}
