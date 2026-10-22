package io.github.antctrlwin.anomalib4j.onnx;

import ai.onnxruntime.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EncoderSpecTest {
    @ParameterizedTest @EnumSource(EncoderSpec.class)
    void resourceContractAndHwcFlatteningMatchOrt(EncoderSpec spec) throws Exception {
        byte[] bytes = spec.verifiedBytes();
        assertEquals(spec.sha256(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        var image = new BufferedImage(137, 91, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 91; y++) for (int x = 0; x < 137; x++)
            image.setRGB(x, y, (x * 977 + y * 65539) & 0xffffff);
        float[] prepared = ImageNetPreprocessor.preprocess(image);
        var environment = OrtEnvironment.getEnvironment();
        try (var encoder = new OnnxMobileNetV4Encoder(spec);
             var options = new OrtSession.SessionOptions();
             var session = environment.createSession(bytes, options);
             var input = OnnxTensor.createTensor(environment, FloatBuffer.wrap(prepared), spec.inputShape());
             var result = session.run(Map.of(spec.inputName(), input))) {
            var inputInfo = (TensorInfo) session.getInputInfo().get(spec.inputName()).getInfo();
            var outputInfo = (TensorInfo) session.getOutputInfo().get(spec.outputName()).getInfo();
            assertArrayEquals(new long[]{1,3,224,224}, inputInfo.getShape());
            assertArrayEquals(new long[]{1,14,14,spec.grid().channels()}, outputInfo.getShape());
            assertEquals(OnnxJavaType.FLOAT, inputInfo.type);
            assertEquals(OnnxJavaType.FLOAT, outputInfo.type);
            float[] flat = encoder.extract(image);
            assertEquals(spec.grid().elements(), flat.length);
            float[][][][] hwc = (float[][][][]) result.get(spec.outputName()).orElseThrow().getValue();
            for (int y = 0; y < 14; y++) for (int x = 0; x < 14; x++) for (int c = 0; c < spec.grid().channels(); c++) {
                int index = (y * 14 + x) * spec.grid().channels() + c;
                assertTrue(Float.isFinite(flat[index]));
                assertEquals(hwc[0][y][x][c], flat[index], 0f);
            }
            assertArrayEquals(flat, encoder.extract(image), 0f);
            assertArrayEquals(flat, encoder.extractPrepared(prepared), 0f);
        }
    }
}
