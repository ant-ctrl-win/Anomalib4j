package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.m2.*;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

public final class CanonicalPilotBridge {
    private CanonicalPilotBridge() { }
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "extract" -> extract(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
            case "score" -> score(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
            case "evaluate" -> evaluate(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
            default -> throw new IllegalArgumentException("Unknown canonical pilot action");
        }
    }
    private static void extract(Path images, Path manifest, Path output) throws Exception {
        var names = Files.readAllLines(manifest);
        double[] units = new double[names.size()*M2Model.ELEMENTS];
        try (var encoder = new OnnxMobileNetV4Encoder()) {
            for (int i = 0; i < names.size(); i++) {
                var image = ImageIO.read(images.resolve(names.get(i)).toFile());
                double[] unit = M2Model.normalize(encoder.extract(image));
                System.arraycopy(unit, 0, units, i*M2Model.ELEMENTS, M2Model.ELEMENTS);
            }
        }
        ComparisonArtifacts.writeMap(output, units, names.size(), M2Model.ELEMENTS, "features", "700-canonical-14x14", "units");
    }
    private static void score(Path archive, Path features, Path output) throws Exception {
        var model = M2ModelStore.read(archive, M2ModelStore.encoderHash());
        var units = NpyMap.read(features);
        if (units.columns() != M2Model.ELEMENTS) throw new IllegalArgumentException("Unit shape mismatch");
        double[] maps = new double[units.rows()*196];
        for (int i = 0; i < units.rows(); i++) {
            double[] unit = Arrays.copyOfRange(units.values(), i*M2Model.ELEMENTS, (i+1)*M2Model.ELEMENTS);
            System.arraycopy(model.calibrate(model.raw(unit)), 0, maps, i*196, 196);
        }
        ComparisonArtifacts.writeMap(output, maps, units.rows(), 196, "scores", "canonical-pilot", "native");
    }
    private static void evaluate(Path dataset, Path directory, Path inputs) throws Exception {
        var names = Files.readAllLines(inputs.resolve("test.txt"));
        var transforms = NpyMap.read(inputs.resolve("test-transforms.npy"));
        var maps = NpyMap.read(directory.resolve("native-z.npy"));
        if (maps.rows() != names.size() || maps.columns() != 196 || transforms.rows() != names.size() || transforms.columns() != 6)
            throw new IllegalArgumentException("Map/transform ordering mismatch");
        var metadata = new DatasetMetadata(dataset);
        var csv = new StringBuilder("run_id,method,category,filename,label,defect,image_score,score_kind,native_map_path,eval_map_path,dtype,shape,geometry_id,image_sha256\n");
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            var info = metadata.inspect("test", name);
            if (!info.category().equals("metal_nut") || info.width()!=700 || info.height()!=700)
                throw new IllegalArgumentException("Pilot expects Metal Nut 700x700");
            double[] z = Arrays.copyOfRange(maps.values(), i*196, (i+1)*196);
            double[] transform = Arrays.copyOfRange(transforms.values(), i*6, (i+1)*6);
            double[] full = backproject(LocalizationMaps.upsample(z, 14, 14, 700, 700), 700, transform);
            String stem = name.substring(0, name.length()-4), nativePath = "maps/"+stem+".native.npy", fullPath = "maps/"+stem+".evaluated.npy";
            String geometry = "metal_nut-700x700-canonical-backprojected";
            ComparisonArtifacts.writeMap(directory.resolve(nativePath), z, 14, 14, name, geometry, "native");
            ComparisonArtifacts.writeMap(directory.resolve(fullPath), full, 700, 700, name, geometry, "evaluated");
            csv.append(String.join(",", "metal-nut-canonicalization", "M2", "metal_nut", name, info.good()?"good":"anomaly",
                info.defect(), Double.toString(M2Model.imageScore(z)), "positional_z", nativePath, fullPath, "float64", "14x14",
                geometry, NpyMap.sha256(dataset.resolve("test/img").resolve(name)))).append('\n');
        }
        Files.writeString(directory.resolve("predictions.csv"), csv, StandardOpenOption.CREATE_NEW);
        ComparisonEvaluator.evaluate(directory, dataset, inputs.resolve("test.txt"), "M2", "metal_nut");
    }
    static double[] backproject(double[] canonical, int size, double[] forward) {
        if (canonical.length != size*size || forward.length != 6) throw new IllegalArgumentException("Projection shape mismatch");
        double[] original = new double[canonical.length];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            double sx = Math.max(0, Math.min(size-1, forward[0]*x+forward[1]*y+forward[2]));
            double sy = Math.max(0, Math.min(size-1, forward[3]*x+forward[4]*y+forward[5]));
            int x0 = (int)Math.floor(sx), y0 = (int)Math.floor(sy), x1 = Math.min(size-1,x0+1), y1 = Math.min(size-1,y0+1);
            double dx = sx-x0, dy = sy-y0;
            original[y*size+x] = (canonical[y0*size+x0]*(1-dx)+canonical[y0*size+x1]*dx)*(1-dy)
                    +(canonical[y1*size+x0]*(1-dx)+canonical[y1*size+x1]*dx)*dy;
        }
        return original;
    }
}
