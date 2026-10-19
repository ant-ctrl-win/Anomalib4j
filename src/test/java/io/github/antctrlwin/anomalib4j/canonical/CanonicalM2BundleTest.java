package io.github.antctrlwin.anomalib4j.canonical;

import io.github.antctrlwin.anomalib4j.m2.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalM2BundleTest {
    @TempDir Path temporary;
    static M2Model model(String hash) {
        double[] std = new double[M2Model.ELEMENTS], beta = new double[196];
        Arrays.fill(std, 0.1); Arrays.fill(beta, 0.5);
        return new M2Model(hash, new M2Model.Local(new double[std.length], std),
                new M2Model.Calibration(new double[196], beta));
    }
    @Test void roundTripIsDeterministicAndOwnsState() throws Exception {
        var bundle = new CanonicalM2Bundle(model(M2ModelStore.encoderHash()));
        Path first = temporary.resolve("first.bundle"), second = temporary.resolve("second.bundle");
        CanonicalM2BundleStore.write(first, bundle);
        var read = CanonicalM2BundleStore.read(first, bundle.model().encoderSha256());
        CanonicalM2BundleStore.write(second, read);
        assertEquals(bundle.identity(), read.identity());
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
        assertArrayEquals(bundle.model().local().effectiveStd(), read.model().local().effectiveStd());
        assertArrayEquals(bundle.model().calibration().beta(), read.model().calibration().beta());
        double[] detached = read.model().local().mean(); detached[0] = 5;
        assertEquals(0, read.model().local().mean()[0]);
        assertThrows(FileAlreadyExistsException.class, () -> CanonicalM2BundleStore.write(first, bundle));
    }
    @Test void everyContractFieldIsValidated() throws Exception {
        Path path = temporary.resolve("source");
        CanonicalM2BundleStore.write(path, new CanonicalM2Bundle(model("a".repeat(64))));
        for (String field : CanonicalM2Contract.fields().keySet()) {
            Path changed = temporary.resolve("changed-"+field);
            try (var in = new DataInputStream(Files.newInputStream(path));
                 var out = new DataOutputStream(Files.newOutputStream(changed))) {
                out.writeUTF(in.readUTF()); int count = in.readInt(); out.writeInt(count);
                for (int i = 0; i < count; i++) {
                    String key = in.readUTF(), value = in.readUTF();
                    out.writeUTF(key); out.writeUTF(key.equals(field) ? value+"-unknown" : value);
                }
                in.transferTo(out);
            }
            assertThrows(IOException.class, () -> CanonicalM2BundleStore.read(changed, "a".repeat(64)), field);
        }
    }
    @Test void encoderVersionCorruptionTruncationAndTrailingBytesFail() throws Exception {
        Path path = temporary.resolve("source");
        CanonicalM2BundleStore.write(path, new CanonicalM2Bundle(model("a".repeat(64))));
        assertThrows(IOException.class, () -> CanonicalM2BundleStore.read(path, "b".repeat(64)));
        byte[] original = Files.readAllBytes(path);
        byte[] version = original.clone(); version[CanonicalM2BundleStore.FORMAT.length()+1] = '9';
        byte[] corrupt = original.clone(); corrupt[corrupt.length-5] ^= 1;
        for (byte[] invalid : new byte[][]{version, corrupt, Arrays.copyOf(original, original.length-1),
                Arrays.copyOf(original, original.length+1), new byte[]{0, 9, 'b'}}) {
            Path bad = temporary.resolve("bad"); Files.write(bad, invalid);
            assertThrows(IOException.class, () -> CanonicalM2BundleStore.read(bad, "a".repeat(64)));
        }
    }
    @Test void legacyAndCanonicalArchivesRemainIsolated() throws Exception {
        var model = model(M2ModelStore.encoderHash());
        Path legacy = temporary.resolve("legacy.bin"), canonical = temporary.resolve("canonical.bundle");
        M2ModelStore.write(legacy, model);
        CanonicalM2BundleStore.write(canonical, new CanonicalM2Bundle(model));
        assertThrows(IOException.class, () -> M2ModelStore.read(canonical, model.encoderSha256()));
        assertThrows(IOException.class, () -> CanonicalM2BundleStore.read(legacy, model.encoderSha256()));
        assertArrayEquals(model.calibration().beta(), M2ModelStore.read(legacy, model.encoderSha256()).calibration().beta());
        assertThrows(IOException.class, () -> new CanonicalM2Detector(new CanonicalM2Bundle(model("b".repeat(64)))));
    }
}
