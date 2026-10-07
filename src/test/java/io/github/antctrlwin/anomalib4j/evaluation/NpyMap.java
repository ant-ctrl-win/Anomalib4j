package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal reader for the frozen lossless anomaly-map format
 * {@code npy+json/v1}: a NumPy {@code .npy} array plus a JSON metadata sidecar.
 *
 * <p>Only the two dtypes the pipeline writes are supported: little-endian
 * {@code float32} ({@code <f4}) and {@code float64} ({@code <f8}), C-order 2-D.
 * The format is documented and produced by {@code tools/prerun/map_io.py}.</p>
 */
final class NpyMap {
    private static final byte[] MAGIC = {(byte) 0x93, 'N', 'U', 'M', 'P', 'Y'};
    private static final Pattern DESCR = Pattern.compile("'descr'\\s*:\\s*'([^']+)'");
    private static final Pattern FORTRAN = Pattern.compile("'fortran_order'\\s*:\\s*(True|False)");
    private static final Pattern SHAPE = Pattern.compile("'shape'\\s*:\\s*\\(([^)]*)\\)");

    record Map(double[] values, int rows, int columns, String descr) { }

    record Sidecar(String format, String filename, String geometryId, String scoreKind, String dtype, int[] shape,
                   String array, String sha256) { }

    private NpyMap() { }

    static Map read(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length < 10) throw new IOException("Not a .npy file (too short): " + path);
        for (int i = 0; i < MAGIC.length; i++) if (bytes[i] != MAGIC[i]) throw new IOException("Bad .npy magic: " + path);

        int major = bytes[6] & 0xff;
        int headerStart;
        int headerLength;
        if (major == 1) {
            if (bytes.length < 10) throw new IOException("Truncated .npy header: " + path);
            headerStart = 10;
            headerLength = (bytes[8] & 0xff) | ((bytes[9] & 0xff) << 8);
        } else if (major == 2 || major == 3) {
            if (bytes.length < 12) throw new IOException("Truncated .npy header: " + path);
            headerStart = 12;
            headerLength = (bytes[8] & 0xff) | ((bytes[9] & 0xff) << 8)
                    | ((bytes[10] & 0xff) << 16) | ((bytes[11] & 0xff) << 24);
        } else {
            throw new IOException("Unsupported .npy major version " + major + ": " + path);
        }
        if (headerLength < 0 || headerStart + headerLength > bytes.length) throw new IOException("Invalid .npy header length: " + path);

        String header = new String(bytes, headerStart, headerLength, StandardCharsets.ISO_8859_1);
        String descr = single(DESCR, header, path);
        boolean fortran = Boolean.parseBoolean(single(FORTRAN, header, path));
        if (fortran) throw new IOException("Fortran-ordered .npy is not supported: " + path);
        int[] shape = shape(header, path);
        if (shape.length != 2) throw new IOException("Anomaly map must be 2-D, got " + shape.length + ": " + path);

        int elementSize = switch (descr) {
            case "<f4" -> 4;
            case "<f8" -> 8;
            default -> throw new IOException("Unsupported .npy dtype " + descr + ": " + path);
        };
        int count = Math.multiplyExact(shape[0], shape[1]);
        int dataStart = headerStart + headerLength;
        int dataLength = Math.multiplyExact(count, elementSize);
        if (bytes.length - dataStart != dataLength) throw new IOException("Invalid .npy payload length: " + path);

        ByteBuffer buffer = ByteBuffer.wrap(bytes, dataStart, dataLength).order(ByteOrder.LITTLE_ENDIAN);
        double[] values = new double[count];
        if (elementSize == 4) {
            for (int i = 0; i < count; i++) values[i] = buffer.getFloat();
        } else {
            for (int i = 0; i < count; i++) values[i] = buffer.getDouble();
        }
        return new Map(values, shape[0], shape[1], descr);
    }

    static Sidecar readSidecar(Path arrayPath) throws IOException {
        Path sidecar = arrayPath.resolveSibling(stripExtension(arrayPath.getFileName().toString()) + ".json");
        if (!Files.isRegularFile(sidecar)) throw new IOException("Missing map sidecar: " + sidecar);
        JsonNode node = new ObjectMapper().readTree(Files.readString(sidecar));
        JsonNode shapeNode = node.path("shape");
        int[] shape = new int[shapeNode.size()];
        for (int i = 0; i < shape.length; i++) shape[i] = shapeNode.get(i).asInt();
        return new Sidecar(
                node.path("format").asText(),
                node.path("filename").asText(),
                node.path("geometry_id").asText(),
                node.path("score_kind").asText(),
                node.path("dtype").asText(),
                shape,
                node.path("array").asText(),
                node.path("sha256").asText());
    }

    static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            try (var stream = Files.newInputStream(path)) {
                int read;
                while ((read = stream.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 unavailable", impossible);
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static String single(Pattern pattern, String header, Path path) throws IOException {
        Matcher matcher = pattern.matcher(header);
        if (!matcher.find()) throw new IOException("Missing " + pattern + " in .npy header: " + path);
        return matcher.group(1);
    }

    private static int[] shape(String header, Path path) throws IOException {
        String content = single(SHAPE, header, path).replace(" ", "");
        if (content.isEmpty()) return new int[0];
        String[] parts = content.split(",");
        int length = 0;
        for (String part : parts) if (!part.isEmpty()) length++;
        int[] shape = new int[length];
        int index = 0;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            try {
                shape[index++] = Integer.parseInt(part);
            } catch (NumberFormatException invalid) {
                throw new IOException("Invalid .npy shape " + content + ": " + path, invalid);
            }
        }
        return shape;
    }
}
