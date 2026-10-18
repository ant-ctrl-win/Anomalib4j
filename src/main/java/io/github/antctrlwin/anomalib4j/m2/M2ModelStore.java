package io.github.antctrlwin.anomalib4j.m2;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;

public final class M2ModelStore {
    private M2ModelStore() { }
    public static String encoderHash() throws IOException {
        try (var stream = OnnxMobileNetV4Encoder.class.getResourceAsStream("/models/mobilenetv4_spatial_14x14.onnx")) {
            if (stream == null) throw new IOException("Missing encoder resource");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        } catch (NoSuchAlgorithmException failure) { throw new IOException(failure); }
    }
    public static void write(Path path, M2Model model) throws IOException {
        try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)))) {
            out.writeUTF("anomalib4j-m2/v1");
            out.writeUTF(model.encoderSha256()); out.writeUTF(M2Model.PREPROCESSING);
            out.writeInt(14); out.writeInt(14); out.writeInt(96);
            for (double[] array : new double[][]{model.local().mean(), model.local().effectiveStd(),
                    model.calibration().alpha(), model.calibration().beta()})
                for (double value : array) out.writeDouble(value);
        }
    }
    public static M2Model read(Path path, String expectedEncoderHash) throws IOException {
        try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if (!in.readUTF().equals("anomalib4j-m2/v1")) throw new IOException("Unknown M2 archive");
            String hash = in.readUTF();
            if (!hash.equals(expectedEncoderHash) || !in.readUTF().equals(M2Model.PREPROCESSING)
                    || in.readInt() != 14 || in.readInt() != 14 || in.readInt() != 96)
                throw new IOException("M2 encoder/preprocessing/geometry mismatch");
            var local = new M2Model.Local(readArray(in, M2Model.ELEMENTS), readArray(in, M2Model.ELEMENTS));
            var calibration = new M2Model.Calibration(readArray(in, M2Model.CELLS), readArray(in, M2Model.CELLS));
            if (in.read() != -1) throw new IOException("Trailing M2 archive data");
            return new M2Model(hash, local, calibration);
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid M2 state", invalid); }
    }
    private static double[] readArray(DataInputStream in, int size) throws IOException {
        double[] result = new double[size];
        for (int i = 0; i < size; i++) result[i] = in.readDouble();
        return result;
    }
}
