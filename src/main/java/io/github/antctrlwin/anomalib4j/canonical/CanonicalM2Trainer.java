package io.github.antctrlwin.anomalib4j.canonical;

import ai.onnxruntime.OrtException;
import io.github.antctrlwin.anomalib4j.m2.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import javax.imageio.ImageIO;

/** Streams two explicit, disjoint source-image manifests through the inference preprocessing path. */
public final class CanonicalM2Trainer {
    private CanonicalM2Trainer() { }
    public static CanonicalM2Bundle fit(List<Path> fit, List<Path> calibration) throws IOException, OrtException {
        if (fit.size() < 2 || calibration.size() < 2) throw new IllegalArgumentException("Two observations per role required");
        var seen = new HashSet<Path>();
        for (var role : List.of(fit, calibration)) for (Path path : role)
            if (!seen.add(path.toRealPath())) throw new IllegalArgumentException("Duplicate or overlapping FIT/CAL input");
        String hash = M2ModelStore.encoderHash();
        try (var input = new CanonicalM2Input()) {
            var local = new M2Moments(M2Model.ELEMENTS);
            for (Path path : fit) local.add(unit(input, path));
            var fitted = M2Trainer.uncalibrated(hash, local);
            var heldOut = new M2Moments(M2Model.CELLS);
            for (Path path : calibration) heldOut.add(fitted.raw(unit(input, path)));
            return new CanonicalM2Bundle(M2Trainer.calibrated(fitted, heldOut));
        }
    }
    private static double[] unit(CanonicalM2Input input, Path path) throws IOException, OrtException {
        var image = ImageIO.read(path.toFile());
        if (image == null) throw new IOException("Unsupported image: " + path);
        try { return M2Model.normalize(input.extract(image).features()); }
        finally { image.flush(); }
    }
}
