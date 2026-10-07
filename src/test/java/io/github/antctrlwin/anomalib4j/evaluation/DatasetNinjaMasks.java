package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.InflaterInputStream;

final class DatasetNinjaMasks {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private DatasetNinjaMasks() { }

    static boolean[] read(Path annotation, int width, int height, boolean good) throws IOException {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Positive image dimensions required");
        JsonNode root = JSON.readTree(annotation.toFile());
        if (root == null || integer(root.path("size").path("width")) != width
                || integer(root.path("size").path("height")) != height) {
            throw new IOException("Annotation size does not match image: " + annotation);
        }
        boolean[] mask = new boolean[Math.multiplyExact(width, height)];
        if (good) return mask;
        JsonNode objects = root.path("objects");
        if (!objects.isArray()) throw new IOException("Missing objects array: " + annotation);
        for (JsonNode object : objects) {
            if (!object.path("geometryType").asText().equals("bitmap")) {
                throw new IOException("Unsupported annotation geometry: " + annotation);
            }
            JsonNode bitmap = object.path("bitmap");
            JsonNode origin = bitmap.path("origin");
            if (!origin.isArray() || origin.size() != 2 || !bitmap.path("data").isTextual()) {
                throw new IOException("Invalid bitmap or origin: " + annotation);
            }
            int x = integer(origin.get(0));
            int y = integer(origin.get(1));
            byte[] bytes;
            try { bytes = Base64.getDecoder().decode(bitmap.get("data").textValue()); }
            catch (IllegalArgumentException invalid) { throw new IOException("Invalid bitmap base64", invalid); }
            if (!png(bytes)) {
                try (var inflated = new InflaterInputStream(new ByteArrayInputStream(bytes))) {
                    bytes = inflated.readNBytes(64 * 1024 * 1024 + 1);
                }
                if (bytes.length > 64 * 1024 * 1024 || !png(bytes)) throw new IOException("Invalid or oversized bitmap PNG");
            }
            var decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded == null) throw new IOException("Undecodable bitmap PNG");
            try {
                if (x < 0 || y < 0 || (long) x + decoded.getWidth() > width || (long) y + decoded.getHeight() > height) {
                    throw new IOException("Bitmap exceeds image bounds: " + annotation);
                }
                boolean alpha = decoded.getColorModel().hasAlpha();
                for (int row = 0; row < decoded.getHeight(); row++) {
                    for (int col = 0; col < decoded.getWidth(); col++) {
                        int argb = decoded.getRGB(col, row);
                        boolean foreground = alpha ? (argb >>> 24) != 0 : (argb & 0x00ffffff) != 0;
                        mask[(y + row) * width + x + col] |= foreground;
                    }
                }
            } finally { decoded.flush(); }
        }
        return mask;
    }

    private static boolean png(byte[] bytes) {
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if (bytes[i] != signature[i]) return false;
        return true;
    }

    private static int integer(JsonNode value) throws IOException {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) throw new IOException("Expected integer coordinate/size");
        return value.intValue();
    }
}
