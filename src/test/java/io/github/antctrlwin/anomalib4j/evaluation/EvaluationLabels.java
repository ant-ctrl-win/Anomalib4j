package io.github.antctrlwin.anomalib4j.evaluation;

/** Filename syntax only; DatasetMetadata validates category tags and defect taxonomy. */
final class EvaluationLabels {
    private EvaluationLabels() { }

    static String category(String filename) {
        if (filename != null && filename.matches("[a-z0-9]+(?:_[a-z0-9]+)*_[0-9]+\\.png")) {
            for (String category : MvtecCategories.names()) {
                if (filename.startsWith(category + "_")) return category;
            }
        }
        throw new IllegalArgumentException("Invalid MVTec filename: " + filename);
    }

    static String defect(String filename) {
        String category = category(filename);
        String remainder = filename.substring(category.length() + 1, filename.length() - 4);
        int split = remainder.lastIndexOf('_');
        if (split <= 0) throw new IllegalArgumentException("Missing defect/index: " + filename);
        return remainder.substring(0, split);
    }
}
