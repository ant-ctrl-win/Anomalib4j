package io.github.antctrlwin.anomalib4j.evaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class LocalizationArtifacts {
    private LocalizationArtifacts() { }

    static Map<String, double[]> readRaw(Path path) throws IOException {
        var maps = new LinkedHashMap<String, double[]>();
        var seen = new LinkedHashMap<String, boolean[]>();
        try (var reader = Files.newBufferedReader(path)) {
            if (!"filename,row,column,raw_discrepancy".equals(reader.readLine())) throw new IOException("Unexpected raw CSV header");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",", -1);
                if (fields.length != 4) throw new IOException("Invalid raw CSV row");
                try {
                    BottleEvaluation.defect(fields[0]);
                    int row = Integer.parseInt(fields[1]);
                    int column = Integer.parseInt(fields[2]);
                    double value = Double.parseDouble(fields[3]);
                    if (row < 0 || row >= 14 || column < 0 || column >= 14 || !Double.isFinite(value)) {
                        throw new IllegalArgumentException("Invalid raw cell");
                    }
                    boolean[] visited = seen.computeIfAbsent(fields[0], key -> new boolean[196]);
                    int cell = row * 14 + column;
                    if (visited[cell]) throw new IllegalArgumentException("Duplicate raw cell");
                    visited[cell] = true;
                    maps.computeIfAbsent(fields[0], key -> new double[196])[cell] = value;
                } catch (IllegalArgumentException invalid) { throw new IOException("Invalid raw CSV row: " + line, invalid); }
            }
        }
        if (maps.isEmpty()) throw new IOException("Empty raw CSV");
        for (var entry : seen.entrySet()) {
            for (boolean present : entry.getValue()) if (!present) throw new IOException("Incomplete raw map: " + entry.getKey());
        }
        return maps;
    }
}
