package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.onnx.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class EncoderFeatureDumpTest {
    @TempDir Path temp;
    @ParameterizedTest @EnumSource(EncoderSpec.class)
    void selectedEncoderPreservesManifestOrderAndWritesMatchingMetadata(EncoderSpec spec) throws Exception {
        Path dataset=temp.resolve("dataset"), output=temp.resolve("features"), manifest=temp.resolve("fit.txt");
        Files.createDirectories(dataset.resolve("train/img"));
        String name="bottle_good_007.png";
        var image=new BufferedImage(31,23,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<23;y++)for(int x=0;x<31;x++)image.setRGB(x,y,(x*257+y*65539)&0xffffff);
        Path source=dataset.resolve("train/img/"+name); ImageIO.write(image,"png",source.toFile());
        Files.writeString(manifest,name+"\n");
        PositionalVarianceFeatureDump.main(new String[]{dataset.toString(),output.toString(),manifest.toString(),"bottle","train",spec.name()});
        var metadata=ComparisonArtifacts.JSON.readTree(output.resolve("meta.json").toFile());
        assertEquals(spec.sha256(),metadata.path("encoderSha256").asText());
        assertEquals(spec.modelId(),metadata.path("encoderId").asText());
        assertEquals(spec.grid().channels(),metadata.path("grid").path("channels").asInt());
        assertEquals(name+"\n",Files.readString(output.resolve("images.txt")));
        byte[] bytes=Files.readAllBytes(output.resolve("features.npy"));
        var buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int header=Short.toUnsignedInt(buffer.getShort(8));
        assertTrue(new String(bytes,10,header,StandardCharsets.US_ASCII).contains("(1, 14, 14, "+spec.grid().channels()+","));
        assertEquals(spec.grid().elements()*Float.BYTES,bytes.length-10-header);
        buffer.position(10+header);
        try(var encoder=new OnnxMobileNetV4Encoder(spec)) {
            for(float expected:encoder.extract(ImageIO.read(source.toFile()))) assertEquals(expected,buffer.getFloat(),0f);
        }
    }
}
