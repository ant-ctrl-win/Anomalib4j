package io.github.antctrlwin.anomalib4j.m2;

import java.io.*;
import java.nio.file.*;
import io.github.antctrlwin.anomalib4j.onnx.EncoderSpec;

public final class M2ModelStore {
    private M2ModelStore() { }
    public static String encoderHash() throws IOException {
        return encoderHash(EncoderSpec.SMALL_SPATIAL_14);
    }
    public static String encoderHash(EncoderSpec spec) throws IOException {
        spec.verifiedBytes();
        return spec.sha256();
    }
    public static void write(Path path, M2Model model) throws IOException {
        try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)))) {
            var spec = model.encoderSpec();
            out.writeUTF("anomalib4j-m2/v2");
            out.writeUTF(spec.name()); out.writeUTF(spec.modelId());
            out.writeUTF(model.encoderSha256()); out.writeUTF(spec.preprocessingId());
            out.writeInt(spec.grid().height()); out.writeInt(spec.grid().width()); out.writeInt(spec.grid().channels());
            for (double[] array : new double[][]{model.local().mean(), model.local().effectiveStd(),
                    model.calibration().alpha(), model.calibration().beta()})
                for (double value : array) out.writeDouble(value);
        }
    }
    public static M2Model read(Path path, String expectedEncoderHash) throws IOException {
        return read(path, EncoderSpec.SMALL_SPATIAL_14, expectedEncoderHash);
    }
    public static M2Model read(Path path, EncoderSpec expected) throws IOException {
        return read(path, expected, encoderHash(expected));
    }
    private static M2Model read(Path path, EncoderSpec expected, String expectedEncoderHash) throws IOException {
        try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            String format = in.readUTF();
            EncoderSpec spec;
            switch (format) {
                case "anomalib4j-m2/v1" -> spec = EncoderSpec.SMALL_SPATIAL_14;
                case "anomalib4j-m2/v2" -> {
                    spec = EncoderSpec.valueOf(in.readUTF());
                    if (!in.readUTF().equals(spec.modelId())) throw new IOException("M2 encoder ID mismatch");
                }
                default -> throw new IOException("Unknown M2 archive");
            }
            String hash = in.readUTF();
            if (spec != expected || !hash.equals(expectedEncoderHash) || !in.readUTF().equals(spec.preprocessingId())
                    || in.readInt() != spec.grid().height() || in.readInt() != spec.grid().width()
                    || in.readInt() != spec.grid().channels())
                throw new IOException("M2 encoder/preprocessing/geometry mismatch");
            var local = new M2Model.Local(readArray(in, spec.grid().elements()), readArray(in, spec.grid().elements()));
            var calibration = new M2Model.Calibration(readArray(in, M2Model.CELLS), readArray(in, M2Model.CELLS));
            if (in.read() != -1) throw new IOException("Trailing M2 archive data");
            return new M2Model(spec, hash, local, calibration);
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid M2 state", invalid); }
    }
    private static double[] readArray(DataInputStream in, int size) throws IOException {
        double[] result = new double[size];
        for (int i = 0; i < size; i++) result[i] = in.readDouble();
        return result;
    }
}
