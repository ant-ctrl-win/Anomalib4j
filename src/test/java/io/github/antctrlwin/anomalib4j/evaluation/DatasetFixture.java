package io.github.antctrlwin.anomalib4j.evaluation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

final class DatasetFixture {
    private DatasetFixture() { }
    static void metadata(Path root, String category, String defect) throws Exception {
        Files.createDirectories(root);
        Files.writeString(root.resolve("meta.json"), """
                {"classes":[{"id":10,"title":"%s"}],
                 "tags":[{"id":1,"name":"%s"},{"id":2,"name":"good"}]}
                """.formatted(defect, category));
    }
    static String tags(String category, boolean good) {
        return "[{\"tagId\":1,\"name\":\"" + category + "\"}"
                + (good ? ",{\"tagId\":2,\"name\":\"good\"}" : "") + "]";
    }
    static void image(Path root, String role, String name, int width, int height) throws Exception {
        Files.createDirectories(root.resolve(role + "/img"));
        Files.createDirectories(root.resolve(role + "/ann"));
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png",
                root.resolve(role + "/img/" + name).toFile());
        String category = EvaluationLabels.category(name);
        String defect = EvaluationLabels.defect(name);
        boolean good = defect.equals("good");
        var pixel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        pixel.setRGB(0, 0, 0xffffffff);
        var png = new java.io.ByteArrayOutputStream();
        ImageIO.write(pixel, "png", png);
        String objects = good ? "[]" : """
                [{"classId":10,"classTitle":"%s","geometryType":"bitmap",
                  "bitmap":{"origin":[0,0],"data":"%s"}}]
                """.formatted(defect, java.util.Base64.getEncoder().encodeToString(png.toByteArray()));
        Files.writeString(root.resolve(role + "/ann/" + name + ".json"),
                "{\"size\":{\"width\":" + width + ",\"height\":" + height
                + "},\"tags\":" + tags(category, good) + ",\"objects\":" + objects + "}");
    }
}
