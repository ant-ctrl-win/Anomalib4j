package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.antctrlwin.anomalib4j.canonical.AffineTensorSampler;
import io.github.antctrlwin.anomalib4j.m2.*;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;

public final class ScrewM2Qualification {
    private ScrewM2Qualification() { }
    record Observation(String image,String role,String label,String defect,ScrewQualificationInput.Prepared preprocessing,Double score) { }
    record Encoded(List<Observation> attempted,List<String> usable,List<double[]> units) { }
    public static void main(String[] args) throws Exception {
        switch(args[0]) {
            case "prepare" -> prepare(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]),Path.of(args[4]));
            case "train" -> train(Path.of(args[1]));
            case "test" -> test(Path.of(args[1]));
            case "evaluate" -> evaluate(Path.of(args[1]));
            case "diagnose" -> diagnose(Path.of(args[1]));
            case "verify" -> {verify(Path.of(args[1]));System.out.println("SCREW M2 - all source/input/stage identities verified");}
            default -> throw new IllegalArgumentException("Expected prepare/train/test/evaluate/diagnose/verify");
        }
    }
    private static void json(Path root,String name,Object value) throws Exception {ComparisonArtifacts.json(root.resolve(name),value);}
    private static JsonNode read(Path root,String name) throws Exception {return ComparisonArtifacts.JSON.readTree(root.resolve(name).toFile());}
    private static void command(Path root,String phase) throws Exception {
        json(root,"command-"+phase+".json",Map.of("utc",Instant.now().toString(),"command",System.getProperty("sun.java.command"),
                "classpath",System.getProperty("java.class.path"),"java",System.getProperty("java.runtime.version"),"heap",Runtime.getRuntime().maxMemory()));
    }
    private static List<GeneralityInputs.Image> images(Path root,String role) throws Exception {
        return Arrays.stream(ComparisonArtifacts.JSON.readValue(root.resolve("images.json").toFile(),GeneralityInputs.Image[].class)).filter(i->i.role().equals(role)).toList();
    }
    private static Path configPath(Path root,String key) throws Exception {return Path.of(read(root,"config.json").path(key).asText());}
    private static void prepare(Path dataset,Path framing,Path baseline,Path root) throws Exception {
        ScrewFramingCheckpoint.verify(framing);
        if(!NpyMap.sha256(framing.resolve("selected-contract.json")).equals(ScrewQualificationInput.FRAMING_SHA))throw new IllegalStateException("Wrong frozen framing");
        var frozen=ComparisonArtifacts.JSON.treeToValue(read(framing,"selected-contract.json").path("framing"),ScrewFraming.Contract.class);
        if(frozen.scale()!=1||frozen.targetX()!=ScrewQualificationInput.FRAMING.targetX()||frozen.targetY()!=ScrewQualificationInput.FRAMING.targetY()||frozen.margin()!=4)
            throw new IllegalStateException("Frozen framing numbers changed");
        Files.createDirectory(root);Files.createDirectory(root.resolve("manifests"));command(root,"prepare");
        var sources=CanonicalM2Integration.snapshot(Path.of("src"));json(root,"source-hashes.json",sources);
        for(var entry:sources.entrySet()){Path out=root.resolve("workspace").resolve(entry.getKey());Files.createDirectories(out.getParent());Files.copy(Path.of(entry.getKey()),out);}
        Path audit=Path.of(read(framing,"config.json").path("audit").asText());
        var images=GeneralityInputs.read(audit).stream().filter(i->i.category().equals("screw")).toList();
        if(images.size()!=480)throw new IllegalStateException("Expected480 Screw source identities");json(root,"images.json",images);
        var inputs=new TreeMap<String,String>();
        for(Path p:List.of(audit.resolve("images.json"),framing.resolve("selected-contract.json"),framing.resolve("fit-freeze.json"),
                framing.resolve("fit-population-extents.json"),framing.resolve("cal-population-extents.json"),framing.resolve("features/units.npy"),
                framing.resolve("features/fit.txt"),framing.resolve("features/coherence.json"),
                baseline.resolve("M2/screw/metrics-unified.json"),baseline.resolve("M2/screw/predictions.csv"),baseline.resolve("M2/screw/config.json"),
                baseline.resolve("M2/metal_nut/metrics-unified.json"),Path.of("target/comparison/m0-all15-baseline-20261016/anomalib4j/screw/metrics-unified.json"),
                Path.of("target/experiments/java-canonicalization-phase2-20261019/B/model/metrics-unified.json")))inputs.put(p.toString(),NpyMap.sha256(p));
        var seen=new HashSet<String>();
        for(String role:List.of("fit","calibration","test")) {
            Path manifest=role.equals("test")?baseline.resolve("data/screw/test.txt"):framing.resolve("manifests/"+role+".txt");
            var names=Files.readAllLines(manifest);int expected=switch(role){case "fit"->256;case "calibration"->64;case "test"->160;default->throw new IllegalStateException();};
            if(names.size()!=expected||!images.stream().filter(i->i.role().equals(role)).map(GeneralityInputs.Image::filename).toList().equals(names))throw new IllegalStateException("Manifest order/count mismatch");
            for(String name:names)if(!name.equals(Path.of(name).getFileName().toString())||!EvaluationLabels.category(name).equals("screw")||!seen.add(role.equals("test")?"test/"+name:"train/"+name))
                throw new IllegalArgumentException("Unsafe/overlapping identity");
            if(role.equals("test")&&!NpyMap.sha256(manifest).equals("3cfc2f129079328aae6ac59d13d4fd967ebfecbdef06fd913c79d9e27ef478c2"))throw new IllegalStateException("Wrong TEST manifest");
            Files.copy(manifest,root.resolve("manifests/"+role+".txt"));inputs.put(manifest.toString(),NpyMap.sha256(manifest));
        }
        for(var image:images) {
            Path actual=dataset.resolve((image.role().equals("test")?"test":"train")+"/img/"+image.filename()).toAbsolutePath().normalize();
            if(!actual.equals(Path.of(image.path()).toAbsolutePath().normalize())||image.size()!=1024)throw new IllegalStateException("Dataset/source identity changed");
            if(!image.role().equals("test"))inputs.put(actual.toString(),image.sha256());
        }
        Path protocol=Path.of("docs/experiments/SCREW_CANONICAL_M2_PROTOCOL.md");Files.copy(protocol,root.resolve("evaluation-protocol.md"));inputs.put(protocol.toString(),NpyMap.sha256(protocol));
        json(root,"input-hashes.json",inputs);json(root,"preprocessing-contract.json",ScrewQualificationBundle.fields());
        var git=new ProcessBuilder("git","rev-parse","HEAD").start();String head=new String(git.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
        if(git.waitFor()!=0)throw new IllegalStateException("Cannot capture HEAD");
        json(root,"config.json",Map.of("entry_head",head,"dataset",dataset.toString(),"framing",framing.toString(),"baseline",baseline.toString(),
                "encoder_sha256",M2ModelStore.encoderHash(),"preprocessing",ScrewQualificationBundle.PREPROCESSING,"test_pixels_opened",false));
        mark(root,"prepare");System.out.println("SCREW M2 - protocol/contract/source/manifests frozen; TEST unopened");
    }
    static void verify(Path root) throws Exception {
        for(String name:List.of("source-hashes.json","input-hashes.json","test-input-hashes.json","baseline-map-hashes.json", "prepare-evidence-hashes.json",
                "train-evidence-hashes.json","test-evidence-hashes.json","metrics-evidence-hashes.json","diagnostics-evidence-hashes.json"))if(Files.exists(root.resolve(name))) {
            for(var entry:read(root,name).properties()) {
                if(!NpyMap.sha256(Path.of(entry.getKey())).equals(entry.getValue().asText()))throw new IllegalStateException("Changed source/input/output: "+entry.getKey());
                if(name.equals("source-hashes.json")&&!NpyMap.sha256(root.resolve("workspace").resolve(entry.getKey())).equals(entry.getValue().asText()))throw new IllegalStateException("Changed archived source");
            }
        }
        if(Files.exists(root.resolve("source-hashes.json"))&&!read(root,"source-hashes.json").equals(ComparisonArtifacts.JSON.valueToTree(CanonicalM2Integration.snapshot(Path.of("src")))))
            throw new IllegalStateException("Source set changed after freeze");
    }
    private static void mark(Path root,String phase) throws Exception {
        verify(root);var hashes=CanonicalM2Integration.snapshot(root);hashes.keySet().removeIf(p->p.replace('\\','/').contains("/workspace/"));json(root,phase+"-evidence-hashes.json",hashes);
    }
    static void requireStage(Path root,String stage) {
        if(!Files.isRegularFile(root.resolve(stage+"-evidence-hashes.json")))throw new IllegalStateException("Missing preceding stage: "+stage);
    }
    private static Encoded encode(Path root,String role,OnnxMobileNetV4Encoder encoder,M2Model model) throws Exception {
        var attempted=new ArrayList<Observation>();var usable=new ArrayList<String>();var units=new ArrayList<double[]>();
        var input=new ScrewQualificationInput();float[] tensor=new float[AffineTensorSampler.ELEMENTS];
        for(var entry:images(root,role)) {
            if(!NpyMap.sha256(Path.of(entry.path())).equals(entry.sha256()))throw new IllegalStateException("Source image changed");
            var image=ImageIO.read(Path.of(entry.path()).toFile());
            try {
                var prepared=input.prepare(image,entry.filename(),tensor);Double score=null;
                if(prepared.usable()) {
                    double[] unit=M2Model.normalize(encoder.extractPrepared(tensor));units.add(unit);usable.add(entry.filename());
                    if(model!=null)score=M2Model.imageScore(model.calibrate(model.raw(unit)));
                }
                String defect=EvaluationLabels.defect(entry.filename());
                attempted.add(new Observation(entry.filename(),role,defect.equals("good")?"good":"anomaly",defect,prepared,score));
            } finally {if(image!=null)image.flush();}
            if(attempted.size()%50==0)System.out.println("SCREW M2 - "+role+" - attempted "+attempted.size()+"; usable "+usable.size());
        }
        json(root,role+"-observations.json",attempted);Files.write(root.resolve(role+"-usable.txt"),usable,StandardOpenOption.CREATE_NEW);
        writeUnits(root.resolve(role+"-units.npy"),units);return new Encoded(attempted,usable,units);
    }
    static Map<String,Object> coverage(List<Observation> rows) {
        var groups=new TreeMap<String,Map<String,Integer>>();var failures=new ArrayList<Observation>();int usable=0;
        for(var row:rows) {
            boolean ok=row.preprocessing.usable();if(ok)usable++;else failures.add(row);
            var g=groups.computeIfAbsent(row.defect,k->new TreeMap<>(Map.of("attempted",0,"usable",0,"failed",0)));
            g.merge("attempted",1,Integer::sum);g.merge(ok?"usable":"failed",1,Integer::sum);
        }
        return Map.of("attempted",rows.size(),"usable",usable,"failed",failures.size(),"failures",failures,"by_defect",groups,
                "metric_scope",usable==rows.size()?"FULL_POPULATION":"SURVIVOR_ONLY");
    }
    private static void requireTrainingCoverage(Path root,String role,Encoded encoded) throws Exception {
        var old=read(configPath(root,"framing"),role.equals("fit")?"fit-population-extents.json":"cal-population-extents.json");
        if(old.size()!=encoded.attempted.size())throw new IllegalStateException("Training population mismatch");
        for(int i=0;i<old.size();i++) {
            var row=encoded.attempted.get(i);var reference=old.get(i);
            if(!row.image.equals(reference.path("image").asText())||!row.preprocessing.pose().equals(ComparisonArtifacts.JSON.treeToValue(reference.path("pose"),GeneralityGeometry.Pose.class)))
                throw new IllegalStateException("Frozen training pose changed");
            boolean expected=reference.path("population").asText().equals("VALID");if(expected!=row.preprocessing.usable())throw new IllegalStateException("Failure policy changed");
        }
        if(encoded.usable.size()!=(role.equals("fit")?253:63))throw new IllegalStateException("Frozen usable coverage mismatch");
        json(root,role+"-coverage.json",coverage(encoded.attempted));
    }
    private static void train(Path root) throws Exception {
        requireStage(root,"prepare");verify(root);command(root,"train");Encoded fit,cal;
        try(var encoder=new OnnxMobileNetV4Encoder()){fit=encode(root,"fit",encoder,null);cal=encode(root,"calibration",encoder,null);}
        requireTrainingCoverage(root,"fit",fit);requireTrainingCoverage(root,"calibration",cal);
        Path framing=configPath(root,"framing");var reference=NpyMap.read(framing.resolve("features/units.npy"));
        if(!fit.usable.equals(Files.readAllLines(framing.resolve("features/fit.txt")))||reference.rows()!=253||reference.columns()!=18816)throw new IllegalStateException("Unmatched FIT feature control");
        for(int i=0;i<253;i++)if(!Arrays.equals(fit.units.get(i),Arrays.copyOfRange(reference.values(),i*18816,(i+1)*18816)))throw new IllegalStateException("Frozen framing descriptors changed");
        var moments=M2Trainer.fitStatistics(fit.units);var fitted=M2Trainer.uncalibrated(M2ModelStore.encoderHash(),moments);
        var calibration=M2Trainer.calibrationStatistics(fitted,cal.units);var model=M2Trainer.calibrated(fitted,calibration);
        for(var entry:Map.of("local-mean",moments.mean(),"local-sample-std",moments.sampleStd(),"local-effective-std",moments.effectiveStd(),
                "alpha",calibration.mean(),"beta-sample-std",calibration.sampleStd(),"beta-effective-std",calibration.effectiveStd()).entrySet())
            array(root.resolve(entry.getKey()+".npy"),entry.getValue(),entry.getValue().length==18816?196:1,"model-statistic");
        scoreArrays(root,"fit",fit.units,model);scoreArrays(root,"calibration",cal.units,model);
        Path bundle=root.resolve("screw.canonical-m2");ScrewQualificationBundle.write(bundle,model);
        var reloaded=ScrewQualificationBundle.read(bundle,M2ModelStore.encoderHash());
        if(!Arrays.equals(ScrewQualificationBundle.payload(model),ScrewQualificationBundle.payload(reloaded)))throw new IllegalStateException("Bundle numerical reload mismatch");
        json(root,"model-summary.json",Map.of("local_mean",ScrewFraming.distribution(moments.mean()),"local_sample_std",ScrewFraming.distribution(moments.sampleStd()),
                "local_effective_std",ScrewFraming.distribution(moments.effectiveStd()),"alpha",ScrewFraming.distribution(calibration.mean()),
                "beta_sample_std",ScrewFraming.distribution(calibration.sampleStd()),"beta_effective_std",ScrewFraming.distribution(calibration.effectiveStd()),
                "local_floor_count",Arrays.stream(moments.sampleStd()).filter(s->s<1e-6).count(),"beta_floor_count",Arrays.stream(calibration.sampleStd()).filter(s->s<1e-6).count(),
                "fit_descriptor_control_max_error",0,"reload_payload_max_error",0));
        json(root,"train-freeze.json",Map.of("utc",Instant.now().toString(),"bundle_identity",NpyMap.sha256(bundle),"bundle_bytes",Files.size(bundle),
                "payload_sha256",ScrewQualificationBundle.hash(ScrewQualificationBundle.payload(model)),"protocol_sha256",NpyMap.sha256(root.resolve("evaluation-protocol.md")),
                "contract_sha256",NpyMap.sha256(root.resolve("preprocessing-contract.json")),"test_manifest_sha256",NpyMap.sha256(root.resolve("manifests/test.txt")),"test_opened",false));
        mark(root,"train");System.out.println("SCREW M2 - FIT253/CAL63 frozen; bundle reload exact; TEST unopened");
    }
    private static void scoreArrays(Path root,String role,List<double[]> units,M2Model model) throws Exception {
        double[] raw=new double[units.size()*196],z=raw.clone(),max=new double[units.size()];
        for(int i=0;i<units.size();i++){var r=model.raw(units.get(i));var scores=model.calibrate(r);System.arraycopy(r,0,raw,i*196,196);System.arraycopy(scores,0,z,i*196,196);max[i]=M2Model.imageScore(scores);}
        array(root.resolve(role+"-raw.npy"),raw,units.size(),"raw");array(root.resolve(role+"-native-z.npy"),z,units.size(),"positional_z");array(root.resolve(role+"-image-scores.npy"),max,units.size(),"native-max");
    }
    private static void array(Path path,double[] values,int rows,String kind) throws Exception {ComparisonArtifacts.writeMap(path,values,rows,values.length/rows,path.getFileName().toString(),ScrewQualificationBundle.PREPROCESSING,kind);}
    private static void writeUnits(Path path,List<double[]> units) throws Exception {
        if(units.isEmpty())return;double[] flat=new double[units.size()*18816];for(int i=0;i<units.size();i++)System.arraycopy(units.get(i),0,flat,i*18816,18816);array(path,flat,units.size(),"unit-features");
    }
    private static void test(Path root) throws Exception {
        requireStage(root,"train");verify(root);command(root,"test");var inputs=new TreeMap<String,String>();Path dataset=configPath(root,"dataset");
        for(var image:images(root,"test")) {
            inputs.put(image.path(),image.sha256());Path ann=dataset.resolve("test/ann/"+image.filename()+".json");
            if(Files.exists(ann))inputs.put(ann.toString(),NpyMap.sha256(ann));
            else if(!EvaluationLabels.defect(image.filename()).equals("good"))throw new IllegalStateException("Missing anomaly annotation");
        }
        json(root,"test-input-hashes.json",inputs);verify(root);
        var model=ScrewQualificationBundle.read(root.resolve("screw.canonical-m2"),M2ModelStore.encoderHash());Encoded encoded;
        try(var encoder=new OnnxMobileNetV4Encoder()){encoded=encode(root,"test",encoder,model);}
        json(root,"test-coverage.json",coverage(encoded.attempted));if(!encoded.units.isEmpty())scoreArrays(root,"test",encoded.units,model);
        Path evaluation=root.resolve("evaluation");Files.createDirectory(evaluation);
        String geometry="screw-1024x1024-canonical700-backprojected";
        var csv=new StringBuilder("run_id,method,category,filename,label,defect,image_score,score_kind,native_map_path,eval_map_path,dtype,shape,geometry_id,image_sha256\n");int at=0;
        for(var row:encoded.attempted)if(row.preprocessing.usable()) {
            double[] z=model.calibrate(model.raw(encoded.units.get(at++)));String stem=row.image.substring(0,row.image.length()-4),nativePath="maps/"+stem+".native.npy",fullPath="maps/"+stem+".evaluated.npy";
            if(M2Model.imageScore(z)!=row.score)throw new IllegalStateException("Native image score mismatch");
            ComparisonArtifacts.writeMap(evaluation.resolve(nativePath),z,14,14,row.image,geometry,"native");
            ComparisonArtifacts.writeMap(evaluation.resolve(fullPath),ScrewQualificationInput.evaluatedMap(z,row.preprocessing.transform(),1024),1024,1024,row.image,geometry,"evaluated");
            csv.append(String.join(",","screw-canonical-m2-qualification-20261020","M2","screw",row.image,row.label,row.defect,Double.toString(row.score),
                    "positional_z",nativePath,fullPath,"float64","14x14",geometry,inputs.get(images(root,"test").stream().filter(i->i.filename().equals(row.image)).findFirst().orElseThrow().path()))).append('\n');
        }
        Files.writeString(evaluation.resolve("predictions.csv"),csv,StandardOpenOption.CREATE_NEW);Files.write(evaluation.resolve("test.txt"),encoded.usable,StandardOpenOption.CREATE_NEW);
        json(evaluation,"config.json",Map.of("preprocessing",ScrewQualificationBundle.fields(),"bundle_identity",NpyMap.sha256(root.resolve("screw.canonical-m2")),
                "attempted",160,"usable",encoded.usable.size(),"failed",160-encoded.usable.size(),"metric_scope",encoded.usable.size()==160?"FULL_POPULATION":"SURVIVOR_ONLY",
                "protocol_sha256",NpyMap.sha256(root.resolve("evaluation-protocol.md"))));
        var successful=encoded.attempted.stream().filter(o->o.preprocessing.usable()).toList();
        json(root,"test-framing.json",Map.of("support",successful.isEmpty()?Map.of():ScrewFraming.summary(successful.stream().map(o->o.preprocessing.support()).toList()),
                "margin_violations",successful.stream().filter(o->!o.preprocessing.framingState().equals("SAFE")).toList(),"contract_adjusted",false));
        mark(root,"test");System.out.println("SCREW M2 - TEST attempted160; usable"+encoded.usable.size()+"; maps and geometry frozen");
    }
    private static void evaluate(Path root) throws Exception {
        requireStage(root,"test");verify(root);command(root,"evaluate");Path evaluation=root.resolve("evaluation");
        var coverage=read(root,"test-coverage.json");boolean good=coverage.path("by_defect").path("good").path("usable").asInt()>0;
        if(!good||coverage.path("usable").asInt()==coverage.path("by_defect").path("good").path("usable").asInt()) {
            json(root,"metrics-unavailable.json",Map.of("coverage",coverage,"reason","Survivors do not contain both image classes; no imputation"));mark(root,"metrics");return;
        }
        ComparisonEvaluator.evaluate(evaluation,configPath(root,"dataset"),evaluation.resolve("test.txt"),"M2","screw");
        var canonical=read(evaluation,"metrics-unified.json");var baseline=read(configPath(root,"baseline"),"M2/screw/metrics-unified.json");
        var deltas=new TreeMap<String,Double>();for(String metric:List.of("image_auroc","pixel_auroc","aupro030"))deltas.put(metric,canonical.path(metric).asDouble()-baseline.path(metric).asDouble());
        if(coverage.path("usable").asInt()==160)for(String key:List.of("n_good","n_anomaly","regions","foreground_pixels","background_pixels"))
            if(canonical.path(key).asLong()!=baseline.path(key).asLong())throw new IllegalStateException("Evaluator population mismatch: "+key);
        json(root,"comparison.json",Map.of("canonical",canonical,"retained_noncanonical_m2",baseline,"absolute_deltas",deltas,"coverage",coverage,
                "scope",coverage.path("metric_scope").asText(),"baseline_coverage","256 FIT/64 CAL/160 TEST; canonical253 FIT/63 CAL usable"));
        mark(root,"metrics");System.out.println("SCREW M2 - primary metrics frozen - "+deltas);
    }
    private static void diagnose(Path root) throws Exception {
        requireStage(root,"metrics");verify(root);command(root,"diagnose");Path baseline=configPath(root,"baseline").resolve("M2/screw"),dataset=configPath(root,"dataset");
        json(root,"baseline-map-hashes.json",CanonicalM2Integration.snapshot(baseline));
        CanonicalPhase2Bridge.main(new String[]{"defects",root.resolve("evaluation").toString(),dataset.toString(),root.resolve("canonical-per-defect.json").toString()});
        CanonicalPhase2Bridge.main(new String[]{"defects",baseline.toString(),dataset.toString(),root.resolve("baseline-per-defect.json").toString()});
        mark(root,"diagnostics");System.out.println("SCREW M2 - per-defect diagnostics complete; primary metrics unchanged");
    }
}
