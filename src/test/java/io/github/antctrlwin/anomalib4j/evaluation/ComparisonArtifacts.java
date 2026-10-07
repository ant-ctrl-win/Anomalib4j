package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;

final class ComparisonArtifacts {
    static final ObjectMapper JSON = new ObjectMapper();
    private ComparisonArtifacts() { }

    static void json(Path path, Object value) throws IOException {
        Files.writeString(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n",
                StandardOpenOption.CREATE_NEW);
    }

    static void phase(Path directory, String name) throws IOException {
        Path path = directory.resolve("phases.csv");
        if (!Files.exists(path)) Files.writeString(path, "timestamp_utc,pid,phase\n", StandardOpenOption.CREATE_NEW);
        Files.writeString(path, Instant.now() + "," + ProcessHandle.current().pid() + "," + name + "\n",
                StandardOpenOption.APPEND);
    }

    static long affinity() throws Exception {
        Process process = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                "(Get-Process -Id " + ProcessHandle.current().pid() + ").ProcessorAffinity.ToInt64()").start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) throw new IOException("Unable to capture workload affinity");
        return Long.parseLong(output);
    }

    static void writeMap(Path path, double[] values, int rows, int columns, String filename,
                         String geometry, String kind) throws IOException {
        if (values.length != Math.multiplyExact(rows, columns)) throw new IllegalArgumentException("Map shape mismatch");
        String header = "{'descr': '<f8', 'fortran_order': False, 'shape': (" + rows + ", " + columns + "), }";
        header += " ".repeat((64 - ((10 + header.length() + 1) % 64)) % 64) + "\n";
        var bytes = ByteBuffer.allocate(10 + header.length() + values.length * Double.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put(new byte[]{(byte) 0x93, 'N', 'U', 'M', 'P', 'Y', 1, 0});
        bytes.putShort((short) header.length());
        bytes.put(header.getBytes(StandardCharsets.US_ASCII));
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite map");
            bytes.putDouble(value);
        }
        Files.createDirectories(path.getParent());
        Files.write(path, bytes.array(), StandardOpenOption.CREATE_NEW);
        json(path.resolveSibling(path.getFileName().toString().replace(".npy", ".json")), Map.of(
                "format", "npy+json/v1", "filename", filename, "geometry_id", geometry, "score_kind", kind,
                "dtype", "float64", "shape", new int[]{rows, columns}, "array", path.getFileName().toString(),
                "sha256", NpyMap.sha256(path)));
    }
}
