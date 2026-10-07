package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.model.GridShape;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

/**
 * Read-only diagnostic harness. Re-encodes the fit-normal split with the frozen
 * MobileNetV4 encoder and writes the raw CNN patch descriptors (HWC float32) for
 * post-hoc statistical analysis. It changes no detector component and only writes
 * derived arrays under the caller-provided output directory.
 *
 * <p>Usage: {@code PositionalVarianceFeatureDump <dataset> <outputDir> <manifest> <category> [role]}
 * where manifest lists image file names resolved as {@code <dataset>/<role>/img/<name>} and
 * {@code role} defaults to {@code train} (backward compatible).</p>
 */
public final class PositionalVarianceFeatureDump {
    private PositionalVarianceFeatureDump() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 4 || args.length > 5) {
            throw new IllegalArgumentException("Expected: <dataset> <outputDir> <manifest> <category> [role]");
        }
        Path dataset = Path.of(args[0]);
        Path output = Path.of(args[1]);
        Path manifest = Path.of(args[2]);
        String category = args[3];
        String role = args.length == 5 ? args[4] : "train";
        if (!role.equals("train") && !role.equals("test")) {
            throw new IllegalArgumentException("role must be train or test");
        }
        Files.createDirectories(output);

        List<String> names = Files.readAllLines(manifest).stream().filter(s -> !s.isBlank()).toList();
        try (var encoder = new OnnxMobileNetV4Encoder(OnnxMobileNetV4Encoder.Variant.SPATIAL_14)) {
            GridShape grid = encoder.grid();
            int elements = grid.elements();
            float[] features = new float[Math.multiplyExact(names.size(), elements)];
            int offset = 0;
            for (String name : names) {
                BufferedImage image = ImageIO.read(dataset.resolve(role + "/img/" + name).toFile());
                if (image == null) throw new IllegalArgumentException("Cannot decode " + name);
                float[] extracted = encoder.extract(image);
                image.flush();
                if (extracted.length != elements) throw new IllegalStateException("Unexpected feature size");
                System.arraycopy(extracted, 0, features, offset, elements);
                offset += elements;
            }
            writeNpyFloat32(output.resolve("features.npy"), features,
                    new int[]{names.size(), grid.height(), grid.width(), grid.channels()});
        }
        Files.writeString(output.resolve("images.txt"), String.join("\n", names) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(output.resolve("meta.json"), ComparisonArtifacts.JSON.writeValueAsString(Map.of(
                "category", category,
                "role", role,
                "variant", OnnxMobileNetV4Encoder.Variant.SPATIAL_14.name(),
                "encoderId", "mobilenetv4_conv_small.e2400_r224_in1k",
                "preprocessingId", "imagenet-rgb-resize224-bicubic-v1",
                "grid", Map.of("height", 14, "width", 14, "channels", 96),
                "count", names.size(),
                "dtype", "float32",
                "layout", "N,H,W,C")) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("Dumped " + names.size() + " fit feature maps to " + output);
    }

    private static void writeNpyFloat32(Path path, float[] values, int[] shape) throws IOException {
        StringBuilder header = new StringBuilder("{'descr': '<f4', 'fortran_order': False, 'shape': (");
        for (int i = 0; i < shape.length; i++) {
            header.append(shape[i]);
            if (i < shape.length - 1) header.append(", ");
        }
        header.append(", ), }");
        header.append(" ".repeat((64 - ((10 + header.length() + 1) % 64)) % 64)).append('\n');
        ByteBuffer bytes = ByteBuffer.allocate(10 + header.length() + values.length * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        bytes.put(new byte[]{(byte) 0x93, 'N', 'U', 'M', 'P', 'Y', 1, 0});
        bytes.putShort((short) header.length());
        bytes.put(header.toString().getBytes(StandardCharsets.US_ASCII));
        for (float value : values) bytes.putFloat(value);
        Files.write(path, bytes.array(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
