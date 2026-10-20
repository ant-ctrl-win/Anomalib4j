package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.canonical.*;
import io.github.antctrlwin.anomalib4j.m2.M2Model;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import static io.github.antctrlwin.anomalib4j.evaluation.GeneralityGeometry.*;

public final class GeneralityAudit {
    private GeneralityAudit() { }
    record PoseRow(String category,String role,String image,Strategy strategy,Pose pose) { }
    record PerturbRow(String category,String image,Strategy strategy,GeneralityImages.Perturbation change,
                      Pose original,Pose perturbed,double centroidError,Double angularError,boolean polarityFlip,String pixelSha256) { }
    record Features(String image,double[] unit,double gradient,double rawNormMean) { }
    public static void main(String[] args) throws Exception {
        switch(args[0]) {
            case "geometry" -> geometry(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]));
            case "features" -> features(Path.of(args[1]));
            case "anomalies" -> anomalies(Path.of(args[1]));
            default -> throw new IllegalArgumentException("Expected geometry/features/anomalies");
        }
    }
    private static void geometry(Path dataset,Path baseline,Path root) throws Exception {
        var images=GeneralityInputs.initialize(dataset,baseline,root);var estimator=new GeneralityGeometry();
        var poses=new ArrayList<PoseRow>();var perturbations=new ArrayList<PerturbRow>();
        var sampled=new HashMap<String,Integer>();
        for(var entry:images) {
            if(entry.role().equals("test")||entry.category().equals("grid"))continue;
            var image=ImageIO.read(Path.of(entry.path()).toFile());int[] rgb=GeneralityImages.view(image);
            var original=estimator.estimate(rgb,entry.category());
            for(var pose:original.entrySet())poses.add(new PoseRow(entry.category(),entry.role(),entry.filename(),pose.getKey(),pose.getValue()));
            if(entry.role().equals("fit")&&sampled.merge(entry.category(),1,Integer::sum)<=12) {
                for(var change:GeneralityImages.PERTURBATIONS) {
                    var transformed=GeneralityImages.perturb(image,change);int[] pixels=GeneralityImages.view(transformed);
                    var changed=estimator.estimate(pixels,entry.category());String hash=GeneralityImages.pixelHash(pixels);
                    for(var strategy:original.keySet()) {
                        var a=original.get(strategy);var b=changed.get(strategy);double[] expected=GeneralityImages.expectedCenter(a,change);
                        Double angle=null;boolean flip=false;
                        if(strategy!=Strategy.CENTER_ONLY&&a.state()==State.OK&&b.state()==State.OK) {
                            double period=switch(strategy){case H4 -> 90;case AXIS -> 180;case AXIS_WITH_POLARITY -> 360;case CENTER_ONLY -> throw new IllegalStateException();};
                            angle=GeneralityImages.angleError(b.angle()-a.angle()-change.angle(),period);
                            flip=strategy==Strategy.AXIS_WITH_POLARITY&&angle>90;
                        }
                        perturbations.add(new PerturbRow(entry.category(),entry.filename(),strategy,change,a,b,
                                Math.hypot(b.cx()-expected[0],b.cy()-expected[1]),angle,flip,hash));
                    }
                    transformed.flush();
                }
            }
            image.flush();
        }
        ComparisonArtifacts.json(root.resolve("poses-train.json"),poses);
        ComparisonArtifacts.json(root.resolve("perturbations.json"),perturbations);
        GeneralityInputs.verify(root);
        ComparisonArtifacts.json(root.resolve("geometry-complete.json"),Map.of("poses",poses.size(),"perturbations",perturbations.size(),"test_pixels_read",false));
        System.out.println("GENERALITY - geometry complete - "+poses.size()+" poses; "+perturbations.size()+" perturbation rows; TEST unopened");
    }
    private static Map<String,List<Strategy>> plan(Path root) throws Exception {
        if(!Files.exists(root.resolve("geometry-complete.json")))throw new IllegalStateException("Geometry must precede CNN");
        var plan=ComparisonArtifacts.JSON.readTree(root.resolve("screen-plan.json").toFile());
        var result=new LinkedHashMap<String,List<Strategy>>();
        for(String category:GeneralityInputs.CATEGORIES) {
            var selected=plan.path("candidates").path(category);if(!selected.isArray())throw new IllegalArgumentException("Missing frozen candidate list");
            var values=new ArrayList<Strategy>();
            for(var v:selected){var strategy=Strategy.valueOf(v.asText());
                if(!GeneralityGeometry.strategies(category).contains(strategy)||values.contains(strategy))throw new IllegalArgumentException("Unplanned strategy");values.add(strategy);}
            result.put(category,values);
        }
        return result;
    }
    private static void features(Path root) throws Exception {
        GeneralityInputs.verify(root);var plan=plan(root);
        String planHash=NpyMap.sha256(root.resolve("screen-plan.json"));
        ComparisonArtifacts.json(root.resolve("feature-start.json"),Map.of("screen_plan_sha256",planHash,"test_pixels_read",false,
                "ort",ai.onnxruntime.OrtEnvironment.getEnvironment().getVersion(),"available_providers",ai.onnxruntime.OrtEnvironment.getAvailableProviders().toString()));
        var sampler=new AffineTensorSampler(AffineTensorSampler.Kernel.BILINEAR);var estimator=new GeneralityGeometry();
        float[] tensor=new float[AffineTensorSampler.ELEMENTS];
        try(var encoder=new OnnxMobileNetV4Encoder()) {
            for(String category:GeneralityInputs.CATEGORIES) {
                var fit=GeneralityInputs.read(root).stream().filter(e->e.category().equals(category)&&e.role().equals("fit")).toList();
                var groups=new LinkedHashMap<String,List<Features>>();groups.put("LEGACY",new ArrayList<>());groups.put("B_IDENTITY",new ArrayList<>());
                for(var strategy:plan.get(category))groups.put(strategy.name(),new ArrayList<>());
                var excluded=new ArrayList<Map<String,Object>>();
                for(var entry:fit) {
                    var image=ImageIO.read(Path.of(entry.path()).toFile());int[] rgb=GeneralityImages.view(image);
                    var poses=category.equals("grid")?Map.<Strategy,Pose>of():estimator.estimate(rgb,category);
                    float[] legacy=GeneralityImages.legacyTensor(image);
                    groups.get("LEGACY").add(encode(encoder,entry.filename(),legacy));
                    sampler.sample(rgb,new RigidTransform(1,0,0,0),tensor);groups.get("B_IDENTITY").add(encode(encoder,entry.filename(),tensor));
                    if(category.equals("metal_nut")&&groups.get("LEGACY").size()==1) {
                        try(var stream=OnnxMobileNetV4Encoder.class.getResourceAsStream("/models/mobilenetv4_spatial_14x14.onnx")) {
                            if(stream==null)throw new IllegalStateException("Encoder unavailable");M2CpuEvidence.verify(stream.readAllBytes(),tensor,root);
                        }
                    }
                    for(var strategy:plan.get(category)) {
                        var pose=poses.get(strategy);
                        if(pose.state()==State.OK){sampler.sample(rgb,pose.transform(),tensor);groups.get(strategy.name()).add(encode(encoder,entry.filename(),tensor));}
                        else excluded.add(Map.of("image",entry.filename(),"strategy",strategy,"state",pose.state()));
                    }
                    image.flush();
                }
                for(var group:groups.entrySet()) {
                    if(group.getValue().size()<2)continue;saveFeatures(root.resolve(category),group.getKey(),group.getValue());
                    if(!group.getKey().equals("LEGACY")&&!group.getKey().equals("B_IDENTITY")&&group.getValue().size()!=fit.size()) {
                        var names=new HashSet<>(group.getValue().stream().map(Features::image).toList());
                        for(String base:List.of("LEGACY","B_IDENTITY"))saveFeatures(root.resolve(category),base+"_PAIRED_"+group.getKey(),
                                groups.get(base).stream().filter(f->names.contains(f.image())).toList());
                    }
                }
                if(category.equals("metal_nut"))metalControl(groups.get("H4"),root);
                ComparisonArtifacts.json(root.resolve(category+"/coverage.json"),Map.of("fit",fit.size(),"excluded",excluded,
                        "candidate_counts",groups.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().size()))));
                System.out.println("GENERALITY - FIT coherence - "+category+" complete");
            }
        }
        GeneralityInputs.verify(root);
        ComparisonArtifacts.json(root.resolve("features-complete.json"),Map.of("screen_plan_sha256",planHash,"m2_fit_cal_score",false));
    }
    private static Features encode(OnnxMobileNetV4Encoder encoder,String name,float[] tensor) throws Exception {
        float[] features=encoder.extractPrepared(tensor);double sum=0;
        for(int p=0;p<196;p++){double norm=0;for(int c=0;c<96;c++){double v=features[p*96+c];norm+=v*v;}sum+=Math.sqrt(norm);}
        return new Features(name,M2Model.normalize(features),GeneralityImages.gradient(tensor),sum/196);
    }
    private static void saveFeatures(Path root,String name,List<Features> rows) throws Exception {
        Path out=root.resolve(name);Files.createDirectory(out);
        Files.write(out.resolve("fit.txt"),rows.stream().map(Features::image).toList(),StandardOpenOption.CREATE_NEW);
        double[][] units=rows.stream().map(Features::unit).toArray(double[][]::new);
        double[] flat=new double[rows.size()*18816];for(int i=0;i<units.length;i++)System.arraycopy(units[i],0,flat,i*18816,18816);
        ComparisonArtifacts.writeMap(out.resolve("units.npy"),flat,rows.size(),18816,name,"14x14x96-unit","FIT-only");
        ComparisonArtifacts.json(out.resolve("coherence.json"),GeneralityCoherence.compute(units));
        ComparisonArtifacts.json(out.resolve("representation.json"),Map.of("tensor_gradient",GeneralityCoherence.distribution(rows.stream().mapToDouble(Features::gradient).toArray()),
                "raw_descriptor_norm_mean",GeneralityCoherence.distribution(rows.stream().mapToDouble(Features::rawNormMean).toArray())));
    }
    private static void metalControl(List<Features> rows,Path root) throws Exception {
        Path retained=Path.of("target/experiments/java-canonicalization-phase2-20261019/B/inputs");
        if(!rows.stream().map(Features::image).toList().equals(Files.readAllLines(retained.resolve("fit.txt"))))throw new IllegalStateException("Metal Nut FIT order drift");
        var reference=NpyMap.read(retained.resolve("fit.npy"));
        for(int i=0;i<rows.size();i++)if(!Arrays.equals(rows.get(i).unit(),Arrays.copyOfRange(reference.values(),i*18816,(i+1)*18816)))
            throw new IllegalStateException("Metal Nut retained descriptor difference");
        ComparisonArtifacts.json(root.resolve("metal-control.json"),Map.of("images",rows.size(),"descriptor_max_error",0,"quality_campaign",false));
    }
    private static void anomalies(Path root) throws Exception {
        GeneralityInputs.verify(root);plan(root);
        var start=ComparisonArtifacts.JSON.readTree(root.resolve("feature-start.json").toFile());
        if(!Files.exists(root.resolve("features-complete.json"))||!start.path("screen_plan_sha256").asText().equals(NpyMap.sha256(root.resolve("screen-plan.json"))))
            throw new IllegalStateException("Frozen screening must precede TEST");
        var estimator=new GeneralityGeometry();var poses=new ArrayList<PoseRow>();
        for(var entry:GeneralityInputs.read(root))if(entry.role().equals("test")&&!entry.category().equals("grid")) {
            var image=ImageIO.read(Path.of(entry.path()).toFile());var values=estimator.estimate(GeneralityImages.view(image),entry.category());
            for(var row:values.entrySet())poses.add(new PoseRow(entry.category(),EvaluationLabels.defect(entry.filename()),entry.filename(),row.getKey(),row.getValue()));
            image.flush();
        }
        ComparisonArtifacts.json(root.resolve("poses-test.json"),poses);GeneralityInputs.verify(root);
        ComparisonArtifacts.json(root.resolve("anomalies-complete.json"),Map.of("poses",poses.size(),"screen_plan_sha256",NpyMap.sha256(root.resolve("screen-plan.json")),"test_encodings",0));
        System.out.println("GENERALITY - blind TEST geometry complete - "+poses.size()+" rows; no TEST encodings or M2");
    }
}
