package io.github.antctrlwin.anomalib4j.canonical;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import javax.imageio.ImageIO;

public final class GeometryProfileEvidence {
    private static volatile CanonicalGeometry consumed;
    private GeometryProfileEvidence() { }
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "prepare" -> prepare(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]),Path.of(args[4]));
            case "parity" -> parity(Path.of(args[1]),Path.of(args[2]));
            case "profile" -> profile(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]));
            default -> throw new IllegalArgumentException("Expected prepare/parity/profile");
        }
    }
    private static void prepare(Path root, Path phase2, Path dataset, Path phase1) throws Exception {
        Files.createDirectory(root);
        var hashes=GeometryProfileFixtures.snapshot(Path.of("src"));
        GeometryProfileFixtures.json(root.resolve("source-hashes.json"),hashes);
        var inputs=new TreeMap<String,String>();
        var retained=GeometryProfileFixtures.JSON.readTree(phase1.resolve("input-hashes.json").toFile());
        for (var entry:retained.properties()) {
            Path path=Path.of(entry.getKey());
            if (!GeometryProfileFixtures.sha(path).equals(entry.getValue().asText())) throw new IllegalStateException("Retained Phase 1 input drift");
            inputs.put(path.toString(),entry.getValue().asText());
        }
        for (String file:List.of("geometry-reference.json","bridge-reference.json","bridge-units.npy","bridge-z.npy")) {
            Path path=phase1.resolve(file); inputs.put(path.toString(),GeometryProfileFixtures.sha(path));
        }
        Path manifest=phase2.resolve("source-inputs.json"); inputs.put(manifest.toString(),GeometryProfileFixtures.sha(manifest));
        GeometryProfileFixtures.json(root.resolve("input-hashes.json"),inputs);
        var fixtures=new GeometryProfileFixtures(phase2,dataset);
        GeometryProfileFixtures.json(root.resolve("fixtures.json"),fixtures.metadata); fixtures.close();
        var git=new ProcessBuilder("git","rev-parse","HEAD").start();
        String commit=new String(git.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
        if (git.waitFor()!=0) throw new IllegalStateException("Cannot capture repository identity");
        GeometryProfileFixtures.json(root.resolve("run.json"),Map.of("entry_commit",commit,"category","metal_nut",
                "source_entries",hashes.size(),"input_entries",inputs.size(),"phase1",phase1.toString(),"phase2",phase2.toString(),
                "dataset",dataset.toString(),"production_modified",false,"onnx_invoked",false));
        System.out.println("GEOMETRY PROFILE - PREPARE - complete - frozen sources/inputs; 9 matched BGR fixtures");
    }
    private static void parity(Path root, Path phase1) throws Exception {
        GeometryProfileFixtures.verify(root);
        var original=new MetalNutGeometryEstimator(); var mirror=new GeometryProfileStages();
        int count=0;
        for (var row:GeometryProfileFixtures.JSON.readTree(phase1.resolve("geometry-reference.json").toFile())) {
            Path source=Path.of(row.path("source").asText());
            if (!GeometryProfileFixtures.sha(source).equals(row.path("source_sha256").asText())) throw new IllegalStateException("Source input changed");
            var image=ImageIO.read(source.toFile()); int[] rgb=image.getRGB(0,0,700,700,null,0,700);
            var actual=original.estimate(rgb); var staged=mirror.estimate(rgb);
            if (!actual.equals(staged) || !Arrays.equals(original.reducedImage(),mirror.reducedImage())
                    || !Arrays.equals(original.selectedComponent(),mirror.selectedComponent())
                    || !Arrays.equals(original.filledMask(),mirror.filledMask())) throw new IllegalStateException("Staged/production parity failed: "+source);
            if (actual.status()==CanonicalGeometry.Status.SUCCESS && !actual.transform().equals(staged.transform()))
                throw new IllegalStateException("Staged transform parity failed");
            image.flush(); count++;
        }
        if (count!=423) throw new IllegalStateException("All335+88 required");
        GeometryProfileFixtures.json(root.resolve("staged-parity.json"),Map.of("cases",count,"exact_geometry",true,
                "exact_gray",true,"exact_component",true,"exact_filled",true,"exact_transform",true));
        Path reference=root.resolve("reference-parity"); Files.createDirectory(reference);
        for (String file:List.of("geometry-reference.json","bridge-reference.json","bridge-units.npy","bridge-z.npy"))
            Files.copy(phase1.resolve(file),reference.resolve(file));
        GeometryProfileFixtures.verify(root);
        System.out.println("GEOMETRY PROFILE - STAGED PARITY - complete - 423 exact; reference runner input copy prepared");
    }
    private static void profile(Path root, Path phase2, Path dataset) throws Exception {
        GeometryProfileFixtures.verify(root);
        var fixtures=new GeometryProfileFixtures(phase2,dataset);
        var estimator=new MetalNutGeometryEstimator(); int[] rgb=new int[700*700]; int index=0;
        long warmup=System.nanoTime()+Duration.ofSeconds(5).toNanos();
        while (System.nanoTime()<warmup) {
            fixtures.images[index].getRGB(0,0,700,700,rgb,0,700); consumed=estimator.estimate(rgb); index=(index+1)%9;
        }
        try (var recording=new Recording(Configuration.getConfiguration("profile"))) {
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
            recording.start(); long end=System.nanoTime()+Duration.ofSeconds(30).toNanos(); long operations=0;
            while (System.nanoTime()<end) {
                fixtures.images[index].getRGB(0,0,700,700,rgb,0,700); consumed=estimator.estimate(rgb); index=(index+1)%9; operations++;
            }
            recording.stop(); recording.dump(root.resolve("geometry.jfr"));
            GeometryProfileFixtures.json(root.resolve("jfr-run.json"),Map.of("seconds",30,"operations",operations,
                    "scope","unchanged production getRGB+estimate; diagnostic recording, not primary latency", "last_geometry",consumed));
        }
        fixtures.close(); GeometryProfileFixtures.verify(root);
        System.out.println("GEOMETRY PROFILE - JFR - complete");
    }
}
