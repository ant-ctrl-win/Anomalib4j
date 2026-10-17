package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class DatasetMetadata {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final Path dataset;
    private final Map<Long, String> classes = new HashMap<>();
    private final Map<Long, String> tags = new HashMap<>();

    record Image(String filename, String category, String defect, int width, int height) {
        boolean good() { return defect.equals("good"); }
        int pixels() { return Math.multiplyExact(width, height); }
    }

    DatasetMetadata(Path dataset) throws IOException {
        this.dataset = dataset;
        JsonNode meta = JSON.readTree(dataset.resolve("meta.json").toFile());
        if (meta == null) throw new IOException("Missing DatasetNinja metadata");
        readDefinitions(meta.path("classes"), "title", classes);
        readDefinitions(meta.path("tags"), "name", tags);
    }

    private static void readDefinitions(JsonNode entries, String label, Map<Long, String> destination) throws IOException {
        if (!entries.isArray()) throw new IOException("Missing DatasetNinja definitions: " + label);
        for (JsonNode entry : entries) {
            if (!entry.path("id").isIntegralNumber() || !entry.path(label).isTextual()
                    || entry.path(label).asText().isBlank()
                    || destination.put(entry.path("id").asLong(), entry.path(label).asText()) != null)
                throw new IOException("Invalid/duplicate DatasetNinja definition");
        }
    }

    Image inspect(String role, String filename) throws IOException {
        String category = EvaluationLabels.category(filename);
        String defect = EvaluationLabels.defect(filename);
        Path imagePath = dataset.resolve(role + "/img").resolve(filename);
        int width;
        int height;
        try (var input = ImageIO.createImageInputStream(imagePath.toFile())) {
            if (input == null) throw new IOException("Missing image: " + imagePath);
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image: " + imagePath);
            var reader = readers.next();
            try {
                reader.setInput(input);
                width = reader.getWidth(0);
                height = reader.getHeight(0);
            } finally { reader.dispose(); }
        }
        JsonNode ann = JSON.readTree(dataset.resolve(role + "/ann/" + filename + ".json").toFile());
        if (ann == null || width <= 0 || height <= 0
                || !ann.path("size").path("width").isIntegralNumber()
                || !ann.path("size").path("height").isIntegralNumber()
                || ann.path("size").path("width").asInt() != width
                || ann.path("size").path("height").asInt() != height)
            throw new IOException("Image/annotation geometry mismatch: " + filename);
        boolean categoryTag = false;
        boolean goodTag = false;
        if (!ann.path("tags").isArray()) throw new IOException("Missing annotation tags: " + filename);
        for (JsonNode tag : ann.path("tags")) {
            if (!tag.path("tagId").isIntegralNumber()) throw new IOException("Invalid tag ID: " + filename);
            String name = tags.get(tag.path("tagId").asLong(-1));
            if (name == null || !name.equals(tag.path("name").asText()))
                throw new IOException("Unknown/inconsistent annotation tag: " + filename);
            if (MvtecCategories.names().contains(name) && !name.equals(category))
                throw new IOException("Wrong category tag: " + filename);
            categoryTag |= name.equals(category);
            goodTag |= name.equals("good");
        }
        JsonNode objects = ann.path("objects");
        boolean good = defect.equals("good");
        if (!categoryTag || goodTag != good || !objects.isArray()
                || (good ? !objects.isEmpty() : objects.isEmpty()) || (role.equals("train") && !good))
            throw new IOException("Inconsistent normal/anomaly/category labels: " + filename);
        for (JsonNode object : objects) {
            if (!object.path("classId").isIntegralNumber()) throw new IOException("Invalid class ID: " + filename);
            String title = classes.get(object.path("classId").asLong(-1));
            if (title == null || !title.equals(object.path("classTitle").asText())
                    || !title.equals(defect) || !object.path("geometryType").asText().equals("bitmap"))
                throw new IOException("Unknown/inconsistent defect metadata: " + filename);
        }
        return new Image(filename, category, defect, width, height);
    }
}
