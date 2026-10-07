package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ComparisonEvaluatorTest {
    @TempDir Path temporary;

    @Test void commonEvaluatorUsesNativeScoreAndReadsLosslessMapsAndMasks() throws Exception {
        Path directory = temporary.resolve("case");
        Path dataset = temporary.resolve("dataset");
        Files.createDirectories(directory);
        Files.createDirectories(dataset.resolve("test/ann"));
        Path manifest = temporary.resolve("test.txt");
        Files.writeString(manifest, "metal_nut_good_000.png\nmetal_nut_flip_000.png\n");
        Files.writeString(directory.resolve("config.json"), "{}");
        var bitmap = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        bitmap.setRGB(0, 0, 0xffffffff);
        var png = new ByteArrayOutputStream();
        ImageIO.write(bitmap, "png", png);
        String object = "{\"geometryType\":\"bitmap\",\"bitmap\":{\"origin\":[0,0],\"data\":\""
                + Base64.getEncoder().encodeToString(png.toByteArray()) + "\"}}";
        double[] normal = new double[700 * 700];
        Arrays.fill(normal, 100);
        double[] abnormal = new double[700 * 700];
        abnormal[0] = 1;
        for (String defect : new String[]{"good", "flip"}) {
            String filename = "metal_nut_" + defect + "_000.png";
            Files.writeString(dataset.resolve("test/ann/" + filename + ".json"),
                    "{\"size\":{\"width\":700,\"height\":700},\"objects\":[" + (defect.equals("good") ? "" : object) + "]}");
            ComparisonArtifacts.writeMap(directory.resolve(filename + ".npy"), defect.equals("good") ? normal : abnormal,
                    700, 700, filename, "fixture", "evaluated");
        }
        Files.writeString(directory.resolve("predictions.csv"), "run_id,method,category,filename,label,defect,image_score,score_kind,native_map_path,eval_map_path,dtype,shape,geometry_id,image_sha256\n"
                + "fixture,patchcore,metal_nut,metal_nut_good_000.png,good,good,2,native_raw,,metal_nut_good_000.png.npy,float32,256x256,fixture,x\n"
                + "fixture,patchcore,metal_nut,metal_nut_flip_000.png,anomaly,flip,9,native_raw,,metal_nut_flip_000.png.npy,float32,256x256,fixture,x\n");
        ComparisonEvaluator.evaluate(directory, dataset, manifest, "patchcore", "metal_nut");
        var metrics = ComparisonArtifacts.JSON.readTree(directory.resolve("metrics-unified.json").toFile());
        assertEquals(1, metrics.path("image_auroc").asDouble(), 0);
        assertEquals(1, metrics.path("regions").asInt());
        assertEquals(1, metrics.path("foreground_pixels").asLong());
        assertEquals(979999, metrics.path("background_pixels").asLong());
        assertTrue(metrics.path("pixel_auroc").asDouble() > .49);
    }

    @Test void rejectsMapHashAndGeometryMismatch() throws Exception {
        Path path = temporary.resolve("fixture.npy");
        ComparisonArtifacts.writeMap(path, new double[]{2}, 1, 1, "metal_nut_flip_000.png", "geometry", "evaluated");
        assertThrows(IllegalArgumentException.class, () -> ComparisonEvaluator.requireMap(path, NpyMap.read(path), NpyMap.readSidecar(path), "metal_nut_flip_000.png", "wrong", 1));
        Files.write(path, new byte[]{0}, java.nio.file.StandardOpenOption.APPEND);
        assertThrows(java.io.IOException.class, () -> NpyMap.read(path));
    }
}
