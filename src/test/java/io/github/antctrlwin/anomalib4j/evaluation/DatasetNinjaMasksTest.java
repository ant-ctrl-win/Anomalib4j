package io.github.antctrlwin.anomalib4j.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.DeflaterOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class DatasetNinjaMasksTest {
    @TempDir Path temporary;

    @Test void placesCompressedAlphaMaskAtXYOriginAndUnionsOverlappingObjects() throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xff000000);
        image.setRGB(1, 0, 0x00ffffff);
        image.setRGB(1, 1, 0xffffffff);
        String compressed = bitmap(image, true);
        Path path = annotation(object(compressed, 1, 2) + "," + object(compressed, 2, 2)
                + "," + object(compressed, 1, 2));
        boolean[] expected = new boolean[20];
        for (int p : new int[]{9, 10, 14, 15}) expected[p] = true;
        assertArrayEquals(expected, DatasetNinjaMasks.read(path, 4, 5, false));
        assertArrayEquals(new boolean[20], DatasetNinjaMasks.read(path, 4, 5, true));
    }

    @Test void decodesPlainPngWithoutAlphaAndEmptyGoodMask() throws Exception {
        var image = new BufferedImage(2, 1, BufferedImage.TYPE_BYTE_GRAY);
        image.setRGB(1, 0, 0xffffffff);
        boolean[] expected = new boolean[20];
        expected[1] = true;
        assertArrayEquals(expected, DatasetNinjaMasks.read(annotation(object(bitmap(image, false), 0, 0)), 4, 5, false));
        assertArrayEquals(new boolean[20], DatasetNinjaMasks.read(annotation(""), 4, 5, true));
    }

    @Test void rejectsMalformedBitmapSizeAndOutOfBoundsOrigin() throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        String data = bitmap(image, false);
        Path outside = annotation(object(data, 3, 4));
        assertThrows(IOException.class, () -> DatasetNinjaMasks.read(outside, 4, 5, false));
        Path negative = annotation(object(data, -1, 0));
        assertThrows(IOException.class, () -> DatasetNinjaMasks.read(negative, 4, 5, false));
        Path bad = annotation(object("not base64!", 0, 0));
        assertThrows(IOException.class, () -> DatasetNinjaMasks.read(bad, 4, 5, false));
        Path sized = annotation("");
        assertThrows(IOException.class, () -> DatasetNinjaMasks.read(sized, 5, 4, true));
    }

    private Path annotation(String objects) throws IOException {
        Path file = Files.createTempFile(temporary, "annotation", ".json");
        return Files.writeString(file, "{\"size\":{\"width\":4,\"height\":5},\"objects\":[" + objects + "]}");
    }

    private static String object(String data, int x, int y) {
        return "{\"geometryType\":\"bitmap\",\"bitmap\":{\"data\":\"" + data + "\",\"origin\":[" + x + "," + y + "]}}";
    }

    private static String bitmap(BufferedImage image, boolean compressed) throws IOException {
        var png = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", png));
        if (!compressed) return Base64.getEncoder().encodeToString(png.toByteArray());
        var bytes = new ByteArrayOutputStream();
        try (var deflater = new DeflaterOutputStream(bytes)) { deflater.write(png.toByteArray()); }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
