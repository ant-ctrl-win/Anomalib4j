package io.github.antctrlwin.anomalib4j.onnx;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class PreprocessingOptimizationIT {
    @Test void comparesCandidateWithOldPipelineOnSyntheticAndAllBottleImages() throws Exception {
        var csv = new StringBuilder("image,type,variant,floats,different,maxAbsDiff,meanAbsDiff\n");
        long different = 0;
        int[] types = {BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_3BYTE_BGR,
                BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_ARGB_PRE,
                BufferedImage.TYPE_BYTE_GRAY, BufferedImage.TYPE_BYTE_INDEXED};
        for (int type : types) {
            for (int[] shape : new int[][]{{1, 1}, {37, 19}, {224, 224}, {301, 239}}) {
                var image = new BufferedImage(shape[0], shape[1], type);
                for (int y = 0; y < shape[1]; y++) for (int x = 0; x < shape[0]; x++) {
                    image.setRGB(x, y, (((x + y) * 29 & 255) << 24)
                            | ((x * 13 & 255) << 16) | ((y * 17 & 255) << 8) | ((x + y) * 7 & 255));
                }
                different += compare("synthetic-" + type + "-" + shape[0] + "x" + shape[1], image, csv);
                image.flush();
            }
        }
        for (int type : new int[]{BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_3BYTE_BGR}) {
            var parent = new BufferedImage(417, 289, type);
            var random = new java.util.Random(42);
            for (int y = 0; y < parent.getHeight(); y++) for (int x = 0; x < parent.getWidth(); x++) {
                parent.setRGB(x, y, random.nextInt());
            }
            different += compare("subimage-" + type, parent.getSubimage(13, 7, 301, 239), csv);
            parent.flush();
        }
        Path dataset = Path.of(System.getProperty("anomalib.dataset", "../Anomalib4j_md/mvtec-ad-DatasetNinja"));
        var paths = new ArrayList<Path>();
        for (String split : new String[]{"train", "test"}) {
            try (var entries = Files.newDirectoryStream(dataset.resolve(split + "/img"), "bottle_*.png")) {
                for (Path path : entries) if (Files.isRegularFile(path)) paths.add(path);
            }
        }
        paths.sort(Comparator.comparing(Path::toString));
        assertEquals(292, paths.size());
        for (Path path : paths) {
            var image = ImageIO.read(path.toFile());
            assertNotNull(image);
            try { different += compare(path.getParent().getParent().getFileName() + "/" + path.getFileName(), image, csv); }
            finally { image.flush(); }
        }
        Files.createDirectories(Path.of("target/benchmark"));
        Files.writeString(Path.of("target/benchmark/preprocessing-equivalence.csv"), csv);
        assertEquals(0, different, "Guarded candidate must match OLD exactly; inspect equivalence CSV");
    }

    static float[] old(BufferedImage image) {
        var rgb = PreprocessingMicroprofile.materializeRgb(PreprocessingMicroprofile.readRgb(image), image.getWidth(), image.getHeight());
        var resized = PreprocessingMicroprofile.resizeRgb(rgb);
        return PreprocessingMicroprofile.normalizedNchw(PreprocessingMicroprofile.readRgb(resized));
    }

    static float[] direct(BufferedImage image) {
        var resized = PreprocessingMicroprofile.resizeRgb(image);
        return PreprocessingMicroprofile.normalizedNchw(((DataBufferInt) resized.getRaster().getDataBuffer()).getData());
    }

    private static long compare(String name, BufferedImage image, StringBuilder csv) {
        float[] expected = old(image);
        float[] direct = direct(image);
        append(name, image.getType(), "direct-all-types", expected, direct, csv);
        boolean safe = image.getClass() == BufferedImage.class &&
                (image.getType() == BufferedImage.TYPE_INT_RGB || image.getType() == BufferedImage.TYPE_3BYTE_BGR);
        long differences = append(name, image.getType(), "guarded", expected, safe ? direct : old(image), csv);
        return differences + append(name, image.getType(), "production", expected, ImageNetPreprocessor.preprocess(image), csv);
    }

    private static long append(String name, int type, String variant, float[] expected, float[] actual, StringBuilder csv) {
        long count = 0;
        double max = 0, sum = 0;
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            if (Float.floatToIntBits(expected[i]) != Float.floatToIntBits(actual[i])) count++;
            double diff = Math.abs((double) expected[i] - actual[i]);
            max = Math.max(max, diff);
            sum += diff;
        }
        csv.append(String.format(Locale.ROOT, "%s,%d,%s,%d,%d,%.17g,%.17g%n", name, type, variant,
                expected.length, count, max, sum / expected.length));
        return count;
    }
}
