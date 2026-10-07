package io.github.antctrlwin.anomalib4j.evaluation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Category and defect parsing for MVTec-style filenames, e.g.
 * {@code bottle_broken_large_012.png} or {@code metal_nut_flip_003.png}.
 *
 * <p>This is the single source of truth used by the experimental evaluator;
 * it replaces the previous Bottle-only parser without changing metric logic.</p>
 */
final class EvaluationLabels {
    private static final Map<String, List<String>> DEFECTS_BY_CATEGORY = new LinkedHashMap<>();

    static {
        DEFECTS_BY_CATEGORY.put("bottle", List.of("good", "broken_large", "broken_small", "contamination"));
        DEFECTS_BY_CATEGORY.put("metal_nut", List.of("good", "bent", "color", "flip", "scratch"));
    }

    private EvaluationLabels() { }

    static String category(String filename) {
        if (filename != null) {
            for (String category : DEFECTS_BY_CATEGORY.keySet()) {
                if (filename.startsWith(category + "_")) return category;
            }
        }
        throw new IllegalArgumentException("Unknown MVTec category for filename: " + filename);
    }

    static String defect(String filename) {
        if (filename == null || !filename.endsWith(".png")) {
            throw new IllegalArgumentException("Unexpected anomaly filename: " + filename);
        }
        String stem = filename.substring(0, filename.length() - ".png".length());
        for (Map.Entry<String, List<String>> entry : DEFECTS_BY_CATEGORY.entrySet()) {
            String prefix = entry.getKey() + "_";
            if (!stem.startsWith(prefix)) continue;
            String remainder = stem.substring(prefix.length());
            for (String defect : entry.getValue()) {
                String candidate = defect + "_";
                if (remainder.startsWith(candidate) && isIndex(remainder.substring(candidate.length()))) {
                    return defect;
                }
            }
        }
        throw new IllegalArgumentException("Unknown MVTec defect for filename: " + filename);
    }

    private static boolean isIndex(String value) {
        if (value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < '0' || character > '9') return false;
        }
        return true;
    }
}
