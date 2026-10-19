package io.github.antctrlwin.anomalib4j.canonical;

import io.github.antctrlwin.anomalib4j.m2.M2Model;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

public final class CanonicalM2BundleStore {
    public static final String FORMAT = "anomalib4j-metal-nut-canonical-m2/v1";
    public static final int PAYLOAD_BYTES = 304192;
    private CanonicalM2BundleStore() { }

    public static void write(Path path, CanonicalM2Bundle bundle) throws IOException {
        byte[] bytes = encode(bundle.model());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static CanonicalM2Bundle read(Path path, String expectedEncoderSha256) throws IOException {
        if (Files.size(path) > 327680) throw new IOException("Oversized canonical bundle");
        try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if (!FORMAT.equals(in.readUTF())) throw new IOException("Unknown canonical bundle format/version");
            var expected = CanonicalM2Contract.fields();
            if (in.readInt() != expected.size()) throw new IOException("Canonical contract field count mismatch");
            var actual = new TreeMap<String, String>();
            for (int i = 0; i < expected.size(); i++) {
                if (actual.put(in.readUTF(), in.readUTF()) != null) throw new IOException("Duplicate contract field");
            }
            if (!actual.equals(expected)) throw new IOException("Canonical preprocessing/model contract mismatch");
            String encoder = in.readUTF();
            if (!encoder.equals(expectedEncoderSha256)) throw new IOException("Canonical encoder mismatch");
            String hash = in.readUTF();
            if (in.readInt() != PAYLOAD_BYTES) throw new IOException("Canonical M2 payload size mismatch");
            byte[] payload = in.readNBytes(PAYLOAD_BYTES);
            if (payload.length != PAYLOAD_BYTES || !digest(payload).equals(hash)) throw new IOException("Corrupt canonical M2 payload");
            if (in.read() != -1) throw new IOException("Trailing canonical bundle data");
            try (var numbers = new DataInputStream(new ByteArrayInputStream(payload))) {
                var local = new M2Model.Local(array(numbers, M2Model.ELEMENTS), array(numbers, M2Model.ELEMENTS));
                var cal = new M2Model.Calibration(array(numbers, M2Model.CELLS), array(numbers, M2Model.CELLS));
                return new CanonicalM2Bundle(new M2Model(encoder, local, cal));
            }
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid canonical M2 state", invalid); }
    }

    static String identity(M2Model model) { return digest(encode(model)); }

    private static byte[] encode(M2Model model) {
        try {
            var payload = new ByteArrayOutputStream(PAYLOAD_BYTES);
            try (var out = new DataOutputStream(payload)) {
                for (double[] values : new double[][] {model.local().mean(), model.local().effectiveStd(),
                        model.calibration().alpha(), model.calibration().beta()})
                    for (double value : values) out.writeDouble(value);
            }
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeUTF(FORMAT);
                var contract = new TreeMap<>(CanonicalM2Contract.fields());
                out.writeInt(contract.size());
                for (Map.Entry<String, String> entry : contract.entrySet()) {
                    out.writeUTF(entry.getKey()); out.writeUTF(entry.getValue());
                }
                out.writeUTF(model.encoderSha256()); out.writeUTF(digest(payload.toByteArray()));
                out.writeInt(PAYLOAD_BYTES); payload.writeTo(out);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
    }
    private static double[] array(DataInputStream in, int size) throws IOException {
        double[] values = new double[size];
        for (int i = 0; i < size; i++) values[i] = in.readDouble();
        return values;
    }
    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
