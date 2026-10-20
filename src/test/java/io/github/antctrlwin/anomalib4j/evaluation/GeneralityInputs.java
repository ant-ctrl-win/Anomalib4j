package io.github.antctrlwin.anomalib4j.evaluation;

import java.io.DataInputStream;
import java.nio.file.*;
import java.util.*;

final class GeneralityInputs {
    static final List<String> CATEGORIES=List.of("metal_nut","bottle","screw","hazelnut","cable","grid");
    record Image(String category,String role,String filename,String path,String sha256,int size) { }
    private GeneralityInputs() { }
    static List<Image> initialize(Path dataset,Path baseline,Path output) throws Exception {
        Files.createDirectory(output);Files.createDirectory(output.resolve("manifests"));
        var sources=CanonicalM2Integration.snapshot(Path.of("src"));
        ComparisonArtifacts.json(output.resolve("source-hashes.json"),sources);
        for(String source:sources.keySet()) {
            Path retained=output.resolve("workspace").resolve(source);Files.createDirectories(retained.getParent());Files.copy(Path.of(source),retained);
        }
        var frozenDataset=ComparisonArtifacts.JSON.readTree(baseline.resolve("dataset-hashes.json").toFile());
        var frozenManifests=ComparisonArtifacts.JSON.readTree(baseline.resolve("manifest-hashes.json").toFile());
        var inputs=new TreeMap<String,String>();var images=new ArrayList<Image>();
        for(String category:CATEGORIES) {
            Files.createDirectory(output.resolve(category));var used=new HashSet<String>();int fit=0,cal=0;
            for(String role:List.of("fit","calibration","test")) {
                String filename=role.equals("test")?"test.txt":"anomalib4j-"+role+".txt";
                String relative="data/"+category+"/"+filename;Path manifest=baseline.resolve(relative);
                String hash=NpyMap.sha256(manifest);
                if(!hash.equals(frozenManifests.path(relative).asText()))throw new IllegalStateException("Frozen manifest drift");
                inputs.put(manifest.toString(),hash);Files.copy(manifest,output.resolve("manifests/"+category+"-"+role+".txt"));
                for(String name:Files.readAllLines(manifest)) {
                    if(!name.equals(Path.of(name).getFileName().toString())||!EvaluationLabels.category(name).equals(category))
                        throw new IllegalArgumentException("Unsafe/wrong category identity");
                    if(!role.equals("test")&&!EvaluationLabels.defect(name).equals("good"))throw new IllegalArgumentException("Normal FIT/CAL only");
                    String source=(role.equals("test")?"test":"train")+"/img/"+name;
                    if(!used.add(source))throw new IllegalStateException("Split overlap/duplicate");
                    Path path=dataset.resolve(source).toAbsolutePath().normalize();String imageHash=NpyMap.sha256(path);
                    if(!imageHash.equals(frozenDataset.path(source).asText()))throw new IllegalStateException("Frozen dataset drift: "+path);
                    int width,height;
                    try(var stream=new DataInputStream(Files.newInputStream(path))) {
                        if(stream.readLong()!=0x89504e470d0a1a0aL||stream.readInt()!=13||stream.readInt()!=0x49484452)
                            throw new IllegalArgumentException("Expected PNG IHDR");
                        width=stream.readInt();height=stream.readInt();
                    }
                    if(width!=height)throw new IllegalArgumentException("Square audit inputs required");
                    images.add(new Image(category,role,name,path.toString(),imageHash,width));inputs.put(path.toString(),imageHash);
                    if(role.equals("fit"))fit++;if(role.equals("calibration"))cal++;
                }
            }
            if(fit!=(int)Math.floor(.8*(fit+cal)))throw new IllegalStateException("Historical split changed");
        }
        Path protocol=Path.of("docs/experiments/CANONICALIZATION_GENERALITY_AUDIT_PROTOCOL.md");
        Files.copy(protocol,output.resolve("protocol.md"));inputs.put(protocol.toString(),NpyMap.sha256(protocol));
        for(Path path:List.of(baseline.resolve("dataset-hashes.json"),baseline.resolve("manifest-hashes.json"),
                Path.of("target/experiments/java-canonicalization-phase2-20261019/B/inputs/fit.npy"),
                Path.of("target/experiments/java-canonicalization-phase2-20261019/B/inputs/fit.txt")))inputs.put(path.toString(),NpyMap.sha256(path));
        ComparisonArtifacts.json(output.resolve("input-hashes.json"),inputs);
        ComparisonArtifacts.json(output.resolve("images.json"),images);
        ComparisonArtifacts.json(output.resolve("config.json"),Map.of("entry_head",gitHead(),"categories",CATEGORIES,
                "strategies",CATEGORIES.stream().collect(java.util.stream.Collectors.toMap(c->c,GeneralityGeometry::strategies)),
                "protocol_sha256",NpyMap.sha256(protocol),"fit_cal_split","retained seed42 floor(0.8*N)",
                "native_to_view","legacy logical-sRGB color conversion, then Graphics2D bicubic/quality to700; native700 not resized",
                "sampler","unchanged B; identity control included","m2_fit_cal_score",false,
                "java",System.getProperty("java.runtime.version"),"encoder_sha256",io.github.antctrlwin.anomalib4j.m2.M2ModelStore.encoderHash()));
        return images;
    }
    private static String gitHead() throws Exception {
        var process=new ProcessBuilder("git","rev-parse","HEAD").start();String out=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
        if(process.waitFor()!=0)throw new IllegalStateException("Unable to record HEAD");return out;
    }
    static List<Image> read(Path root) throws Exception {
        return List.of(ComparisonArtifacts.JSON.readValue(root.resolve("images.json").toFile(),Image[].class));
    }
    static void verify(Path root) throws Exception {
        for(String file:List.of("source-hashes.json","input-hashes.json")) {
            var hashes=ComparisonArtifacts.JSON.readTree(root.resolve(file).toFile());
            for(var entry:hashes.properties()) {
                if(!NpyMap.sha256(Path.of(entry.getKey())).equals(entry.getValue().asText()))throw new IllegalStateException("Source/input changed: "+entry.getKey());
                if(file.equals("source-hashes.json")&&!NpyMap.sha256(root.resolve("workspace").resolve(entry.getKey())).equals(entry.getValue().asText()))
                    throw new IllegalStateException("Retained source changed: "+entry.getKey());
            }
        }
    }
}
