package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.antctrlwin.anomalib4j.canonical.*;
import io.github.antctrlwin.anomalib4j.m2.*;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

public final class BgrFastPathParity {
    private BgrFastPathParity() { }
    public static void main(String[] args) throws Exception {
        Path phase2=Path.of(args[0]), phase1=Path.of(args[1]), integrated=Path.of(args[2]),dataset=Path.of(args[3]),root=Path.of(args[4]);
        Files.createDirectory(root);
        var sources=CanonicalM2Integration.snapshot(Path.of("src"));
        ComparisonArtifacts.json(root.resolve("source-hashes.json"),sources);
        var inputs=new TreeMap<String,String>();
        var retained=ComparisonArtifacts.JSON.readTree(phase1.resolve("input-hashes.json").toFile());
        for(var entry:retained.properties()) {
            if(!NpyMap.sha256(Path.of(entry.getKey())).equals(entry.getValue().asText()))throw new IllegalStateException("Retained input drift");
            inputs.put(entry.getKey(),entry.getValue().asText());
        }
        inputs.putAll(CanonicalM2Integration.snapshot(phase2.resolve("B")));
        for(Path path:List.of(phase2.resolve("source-inputs.json"),phase1.resolve("geometry-reference.json"),
                integrated.resolve("metal-nut.canonical-m2")))inputs.put(path.toString(),NpyMap.sha256(path));
        ComparisonArtifacts.json(root.resolve("input-hashes.json"),inputs);
        Path archive=integrated.resolve("metal-nut.canonical-m2");
        var bundle=CanonicalM2BundleStore.read(archive,M2ModelStore.encoderHash());
        var rows=ComparisonArtifacts.JSON.readTree(phase2.resolve("source-inputs.json").toFile());
        var units=new HashMap<String,NpyMap.Map>(); var counters=new HashMap<String,Integer>();
        for(String role:List.of("fit","calibration","test","rotation")) {
            units.put(role,NpyMap.read(phase2.resolve("B/inputs/"+role+".npy"))); counters.put(role,0);
        }
        var original=new BgrGenericReference.Input();
        var optimized=new CanonicalPreprocessor(AffineTensorSampler.Kernel.BILINEAR);
        Field estimatorField=CanonicalPreprocessor.class.getDeclaredField("estimator"); estimatorField.setAccessible(true);
        var estimator=(MetalNutGeometryEstimator)estimatorField.get(optimized);
        Field histogram=MetalNutGeometryEstimator.class.getDeclaredField("histogram"); histogram.setAccessible(true);
        var threshold=MetalNutGeometryEstimator.class.getDeclaredMethod("threshold"); threshold.setAccessible(true);
        float[] before=new float[AffineTensorSampler.ELEMENTS],after=new float[before.length]; int[] pixels=new int[700*700];
        var testNames=new ArrayList<String>(); var transforms=new ArrayList<double[]>(); var testMaps=new ArrayList<double[]>();
        int count=0; var types=new TreeMap<Integer,Integer>();
        try(var encoder=new OnnxMobileNetV4Encoder();var detector=new CanonicalM2Detector(bundle)) {
            for(JsonNode row:rows) {
                String role=row.path("role").asText(),name=row.path("image").asText();
                Path source=role.equals("rotation")?Path.of(row.path("source").asText()):dataset.resolve(role.equals("test")?"test/img":"train/img").resolve(name);
                if(!NpyMap.sha256(source).equals(row.path("sha256").asText()))throw new IllegalStateException("Source drift");
                var image=ImageIO.read(source.toFile()); types.merge(image.getType(),1,Integer::sum);
                var oldPose=original.prepare(image,before); var pose=optimized.prepare(image,after);
                CanonicalRgbAcquisition.read(image,pixels);
                require(Arrays.equals(original.rgb,pixels),"logical ARGB "+source);
                require(oldPose.equals(pose),"geometry "+source); CanonicalPhase2Bridge.requireGeometry(pose,row.path("expected"));
                require(Arrays.equals(original.geometry.reducedImage(),estimator.reducedImage()),"gray");
                require(Arrays.equals((int[])histogram.get(original.geometry),(int[])histogram.get(estimator)),"histogram");
                require(threshold.invoke(original.geometry).equals(threshold.invoke(estimator)),"Otsu");
                require(Arrays.equals(original.geometry.selectedComponent(),estimator.selectedComponent()),"component");
                require(Arrays.equals(original.geometry.filledMask(),estimator.filledMask()),"filled");
                require(oldPose.transform().equals(pose.transform()),"affine"); require(Arrays.equals(before,after),"B tensor");
                float[] oldFeatures=encoder.extractPrepared(before),features=encoder.extractPrepared(after);
                require(Arrays.equals(oldFeatures,features),"encoder descriptors");
                double[] unit=M2Model.normalize(features); int n=counters.compute(role,(k,v)->v+1)-1;
                require(Arrays.equals(unit,Arrays.copyOfRange(units.get(role).values(),n*M2Model.ELEMENTS,(n+1)*M2Model.ELEMENTS)),"retained B descriptors");
                double[] oldZ=bundle.model().score(oldFeatures),z=bundle.model().score(features);
                require(Arrays.equals(oldZ,z),"native map"); require(M2Model.imageScore(oldZ)==M2Model.imageScore(z),"image score");
                if(role.equals("test")) {
                    var result=detector.inferWithMap(image);
                    require(Arrays.equals(result.nativeMap(),z),"integrated map");
                    require(result.score()==M2Model.imageScore(z) && detector.detectScore(image)==result.score(),"integrated score");
                    testNames.add(name);testMaps.add(result.nativeMap()); var a=result.sourceToCanonical();
                    transforms.add(new double[]{a.cos(),-a.sin(),a.tx(),a.sin(),a.cos(),a.ty()});
                }
                image.flush();count++;
            }
        }
        require(count==423 && testNames.size()==115,"frozen coverage");
        for(var entry:units.entrySet())require(counters.get(entry.getKey())==entry.getValue().rows(),"role count/order");
        Path evaluation=root.resolve("evaluation"),evalInputs=root.resolve("inputs");Files.createDirectory(evaluation);Files.createDirectory(evalInputs);
        Files.write(evalInputs.resolve("test.txt"),testNames,StandardOpenOption.CREATE_NEW);
        ComparisonArtifacts.writeMap(evalInputs.resolve("test-transforms.npy"),flatten(transforms),115,6,"TEST","source-to-canonical","affine");
        ComparisonArtifacts.writeMap(evaluation.resolve("native-z.npy"),flatten(testMaps),115,196,"TEST","canonical-B","native");
        ComparisonArtifacts.json(evaluation.resolve("config.json"),Map.of("model_identity",bundle.identity(),"archive_sha256",NpyMap.sha256(archive),"fit_performed",false));
        CanonicalPilotBridge.main(new String[]{"evaluate",dataset.toString(),evaluation.toString(),evalInputs.toString()});
        var metrics=ComparisonArtifacts.JSON.readTree(evaluation.resolve("metrics-unified.json").toFile());
        var expected=ComparisonArtifacts.JSON.readTree(phase2.resolve("B/model/metrics-unified.json").toFile());
        for(String key:List.of("image_auroc","pixel_auroc","aupro030","regions","foreground_pixels","background_pixels","n_good","n_anomaly"))
            require(metrics.get(key).asDouble()==expected.get(key).asDouble(),"retained metric "+key);
        Path reference=root.resolve("reference-parity");Files.createDirectory(reference);
        for(String name:List.of("geometry-reference.json","bridge-reference.json","bridge-units.npy","bridge-z.npy"))Files.copy(phase1.resolve(name),reference.resolve(name));
        CanonicalPhase1Parity.main(new String[]{reference.toString(),"target/experiments/m2-metal-nut-canonicalization-20261019"});
        for(var entry:sources.entrySet())require(NpyMap.sha256(Path.of(entry.getKey())).equals(entry.getValue()),"source stability");
        for(var entry:inputs.entrySet())require(NpyMap.sha256(Path.of(entry.getKey())).equals(entry.getValue()),"input stability");
        ComparisonArtifacts.json(root.resolve("parity-complete.json"),Map.of("cases",423,"test_cases",115,"roles",counters,"image_types",types,
                "exact_pixels_geometry_histogram_otsu_tensor_descriptors_maps_scores",true,"metric_max_error",0,"model_identity",bundle.identity(),
                "archive",archive.toString(),"fit_performed",false));
        System.out.println("BGR FAST PATH - PARITY - 423/423 exact;115 integrated TEST maps/scores/metrics exact; no refit");
    }
    private static double[] flatten(List<double[]> rows) {return rows.stream().flatMapToDouble(Arrays::stream).toArray();}
    private static void require(boolean condition,String message) {if(!condition)throw new IllegalStateException("Parity failed: "+message);}
}
