package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.antctrlwin.anomalib4j.canonical.*;
import io.github.antctrlwin.anomalib4j.m2.*;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;

/** Explicit real-data parity entry point, outside automatic dataset-free unit tests. */
public final class CanonicalPhase1Parity {
    private static final ObjectMapper JSON = new ObjectMapper();
    private CanonicalPhase1Parity() { }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]), pilot = Path.of(args[1]);
        var geometry = new MetalNutGeometryEstimator();
        var originalTransforms = JSON.readTree(pilot.resolve("geometry.json").toFile());
        var rotationTransforms = JSON.readTree(pilot.resolve("rotation-geometry.json").toFile());
        double maxCenter = 0, maxAngle = 0, maxStrength = 0, maxTransform = 0;
        int count = 0;
        var records = new ArrayList<Map<String, Object>>();
        for (JsonNode reference : JSON.readTree(root.resolve("geometry-reference.json").toFile())) {
            Path source = Path.of(reference.path("source").asText());
            require(NpyMap.sha256(source).equals(reference.path("source_sha256").asText()), "Source hash");
            var image = ImageIO.read(source.toFile());
            int[] rgb = image.getRGB(0, 0, 700, 700, null, 0, 700);
            var actual = geometry.estimate(rgb);
            var expected = reference.path("pose");
            require(actual.status().name().equalsIgnoreCase(expected.path("status").asText()), "Status " + source);
            require(actual.reason().name().equalsIgnoreCase(expected.path("reason").asText()), "Reason " + source);
            String prefix = reference.path("prefix").asText();
            require(Arrays.equals(geometry.reducedImage(), Files.readAllBytes(Path.of(prefix+".gray"))), "Reduced gray " + source);
            require(Arrays.equals(geometry.selectedComponent(), booleanBytes(prefix+".component")), "Selected component " + source);
            require(Arrays.equals(geometry.filledMask(), booleanBytes(prefix+".filled")), "Filled mask " + source);
            double center = Math.max(Math.abs(actual.cx()-expected.path("cx").asDouble()),
                    Math.abs(actual.cy()-expected.path("cy").asDouble()));
            double angle = Math.abs(actual.angle()-expected.path("angle").asDouble());
            double modulo = Math.abs(((angle+45)%90+90)%90-45);
            require(center <= 1e-6 && modulo <= 1e-6 && angle <= 1e-6, "Frozen pose tolerance " + source);
            double strength = Math.abs(actual.harmonic4()-expected.path("harmonic4").asDouble());
            maxCenter = Math.max(maxCenter, center); maxAngle = Math.max(maxAngle, angle);
            maxStrength = Math.max(maxStrength, strength);
            var transform = actual.transform();
            JsonNode saved = count < 335 ? originalTransforms.get(count) : rotationTransforms.get(count-335);
            String savedName = count < 335 ? saved.path("image").asText() : saved.path("perturbed_image").asText();
            require(source.getFileName().toString().equals(savedName), "Transform manifest order");
            JsonNode matrix = saved.path("forward");
            require(matrix.size() == 6, "Retained affine shape");
            double[] coefficients = {transform.cos(), -transform.sin(), transform.tx(), transform.sin(), transform.cos(), transform.ty()};
            double matrixError = 0;
            for (int i = 0; i < 6; i++) matrixError = Math.max(matrixError, Math.abs(coefficients[i]-matrix.get(i).asDouble()));
            require(matrixError <= 1e-6, "Retained source-to-canonical affine " + source);
            maxTransform = Math.max(maxTransform, matrixError);
            records.add(Map.of("source", source.toString(), "geometry", actual, "center_error", center, "angle_error", angle));
            count++;
        }
        require(count == 423, "All335+88 geometry cases required");
        JSON.writerWithDefaultPrettyPrinter().writeValue(root.resolve("java-geometry-records.json").toFile(), records);
        JSON.writerWithDefaultPrettyPrinter().writeValue(root.resolve("j0-result.json").toFile(), Map.of(
                "cases", count, "exact_gray_component_filled", true, "max_center_error", maxCenter,
                "max_angle_error_degrees", maxAngle, "max_harmonic_error", maxStrength, "max_affine_coefficient_error", maxTransform));
        System.out.println("PHASE1 - J0 - complete - " + count);
        bridge(root, pilot);
    }

    private static boolean[] booleanBytes(String file) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(file));
        boolean[] values = new boolean[bytes.length];
        for (int i = 0; i < values.length; i++) { require(bytes[i] == 0 || bytes[i] == 1, "Boolean mask"); values[i] = bytes[i] == 1; }
        return values;
    }

    private static void bridge(Path root, Path pilot) throws Exception {
        var units = NpyMap.read(root.resolve("bridge-units.npy"));
        var maps = NpyMap.read(root.resolve("bridge-z.npy"));
        var model = M2ModelStore.read(pilot.resolve("model/model.bin"), M2ModelStore.encoderHash());
        double maxUnits = 0, maxZ = 0;
        int count = 0, testMaps = 0;
        try (var encoder = new OnnxMobileNetV4Encoder()) {
            for (JsonNode reference : JSON.readTree(root.resolve("bridge-reference.json").toFile())) {
                Path source = Path.of(reference.path("source").asText());
                require(NpyMap.sha256(source).equals(reference.path("source_sha256").asText()), "Canonical PNG hash");
                double[] actual = M2Model.normalize(encoder.extract(ImageIO.read(source.toFile())));
                for (int i = 0; i < actual.length; i++) {
                    double error = Math.abs(actual[i]-units.values()[count*M2Model.ELEMENTS+i]);
                    require(error <= 1e-12, "Retained encoder units"); maxUnits = Math.max(maxUnits, error);
                }
                if (reference.path("role").asText().equals("test")) {
                    double[] z = model.calibrate(model.raw(actual));
                    for (int i = 0; i < z.length; i++) {
                        double expected = maps.values()[count*196+i], error = Math.abs(z[i]-expected);
                        require(error <= 1e-8+1e-10*Math.abs(expected), "Retained native Z"); maxZ = Math.max(maxZ, error);
                    }
                    testMaps++;
                }
                count++;
            }
        }
        require(count == units.rows() && count == maps.rows(), "Bridge manifest coverage");
        JSON.writerWithDefaultPrettyPrinter().writeValue(root.resolve("j1-result.json").toFile(), Map.of(
                "canonical_images", count, "test_maps", testMaps, "max_unit_error", maxUnits, "max_z_error", maxZ));
        System.out.println("PHASE1 - J1-lite - complete - " + count);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
