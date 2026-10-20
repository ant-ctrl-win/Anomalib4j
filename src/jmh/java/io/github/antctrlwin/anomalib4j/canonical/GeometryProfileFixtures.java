package io.github.antctrlwin.anomalib4j.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

public final class GeometryProfileFixtures {
    static final ObjectMapper JSON = new ObjectMapper();
    final BufferedImage[] images = new BufferedImage[9];
    final int[][] packed = new int[9][];
    final GeometryProfileStages[] stages = new GeometryProfileStages[9];
    final boolean[][] selected = new boolean[9][];
    final List<Map<String,Object>> metadata = new ArrayList<>();

    GeometryProfileFixtures(Path phase2, Path dataset) throws Exception {
        var rows = JSON.readTree(phase2.resolve("source-inputs.json").toFile());
        var fixtures = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        for (var row : rows) if (row.path("role").asText().equals("fit") && fixtures.size() < 8) fixtures.add(row);
        for (var row : rows) if (row.path("role").asText().equals("test") && row.path("image").asText().equals("metal_nut_bent_011.png")) fixtures.add(row);
        if (fixtures.size() != 9) throw new IllegalStateException("Frozen eight FIT plus bent_011 required");
        for (int i = 0; i < 9; i++) {
            var row = fixtures.get(i);
            Path path = dataset.resolve(i < 8 ? "train/img" : "test/img").resolve(row.path("image").asText());
            if (!sha(path).equals(row.path("sha256").asText())) throw new IllegalStateException("Fixture source changed: "+path);
            images[i] = ImageIO.read(path.toFile());
            if (images[i].getWidth()!=700 || images[i].getHeight()!=700 || images[i].getType()!=BufferedImage.TYPE_3BYTE_BGR)
                throw new IllegalStateException("Matched source700 TYPE_3BYTE_BGR required");
            packed[i] = images[i].getRGB(0,0,700,700,null,0,700);
            stages[i] = new GeometryProfileStages(); var pose = stages[i].estimate(packed[i]);
            var production = new MetalNutGeometryEstimator();
            if (!pose.equals(production.estimate(packed[i])) || pose.status()!=CanonicalGeometry.Status.SUCCESS
                    || !Arrays.equals(stages[i].reducedImage(),production.reducedImage())
                    || !Arrays.equals(stages[i].selectedComponent(),production.selectedComponent())
                    || !Arrays.equals(stages[i].filledMask(),production.filledMask())) throw new IllegalStateException("Staged fixture parity failed");
            selected[i] = stages[i].selectedComponent();
            metadata.add(Map.of("source",path.toString(),"sha256",sha(path),"type",images[i].getType(),
                    "angle",pose.angle(),"cx",pose.cx(),"cy",pose.cy(),"stress",i==8));
        }
    }
    static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    static Map<String,String> snapshot(Path root) throws Exception {
        var hashes = new TreeMap<String,String>();
        try (var files = Files.walk(root)) {
            for (Path path : files.filter(Files::isRegularFile).sorted().toList()) hashes.put(path.toString(),sha(path));
        }
        return hashes;
    }
    static void verify(Path root) throws Exception {
        for (String name : List.of("source-hashes.json","input-hashes.json")) {
            var hashes = JSON.readTree(root.resolve(name).toFile());
            for (var entry : hashes.properties()) {
                if (!sha(Path.of(entry.getKey())).equals(entry.getValue().asText())) throw new IllegalStateException("Provenance drift: "+entry.getKey());
            }
        }
    }
    static void json(Path path, Object value) throws Exception {
        Files.writeString(path,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value)+"\n",StandardOpenOption.CREATE_NEW);
    }
    void close() { for (var image : images) image.flush(); }
}
