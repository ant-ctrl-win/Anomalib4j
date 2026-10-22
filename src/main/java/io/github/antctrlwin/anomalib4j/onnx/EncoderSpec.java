package io.github.antctrlwin.anomalib4j.onnx;

import io.github.antctrlwin.anomalib4j.model.GridShape;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public enum EncoderSpec {
    SMALL_SPATIAL_14("mobilenetv4_conv_small.e2400_r224_in1k", "/models/mobilenetv4_spatial_14x14.onnx",
            "48da88a8da5f1f1f1ce2308d19128c0f302498aa1889882263620cad21bdd26e", 96, "input_image", "spatial_features"),
    LARGE_SPATIAL_14("mobilenetv4_conv_large.e500_r256_in1k", "/models/mobilenetv4_large_spatial_14x14.onnx",
            "ac83d397ee76768ba7ea5b2ec054337e07a454286403e27e72c832100021d6c3", 192, "input", "features");

    private final String modelId, resourcePath, sha256, inputName, outputName;
    private final GridShape grid;

    EncoderSpec(String modelId, String resourcePath, String sha256, int channels, String inputName, String outputName) {
        this.modelId = modelId; this.resourcePath = resourcePath; this.sha256 = sha256;
        this.grid = new GridShape(14, 14, channels); this.inputName = inputName; this.outputName = outputName;
    }
    public String modelId() { return modelId; }
    public String resourcePath() { return resourcePath; }
    public String sha256() { return sha256; }
    public String inputName() { return inputName; }
    public String outputName() { return outputName; }
    public int inputWidth() { return 224; }
    public int inputHeight() { return 224; }
    public GridShape grid() { return grid; }
    public String preprocessingId() { return "imagenet-rgb-resize224-bicubic-v1"; }
    public long[] inputShape() { return new long[]{1, 3, inputHeight(), inputWidth()}; }
    public long[] outputShape() { return new long[]{1, grid.height(), grid.width(), grid.channels()}; }

    public byte[] verifiedBytes() throws IOException {
        try (var stream = EncoderSpec.class.getResourceAsStream(resourcePath)) {
            if (stream == null) throw new IOException("Missing encoder resource: " + resourcePath);
            byte[] bytes = stream.readAllBytes();
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!actual.equals(sha256)) throw new IOException("Encoder SHA-256 mismatch: " + name());
            return bytes;
        } catch (NoSuchAlgorithmException failure) { throw new IOException(failure); }
    }
}
