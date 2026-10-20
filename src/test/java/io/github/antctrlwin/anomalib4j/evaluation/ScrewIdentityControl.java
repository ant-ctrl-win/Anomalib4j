package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.antctrlwin.anomalib4j.canonical.*;
import io.github.antctrlwin.anomalib4j.m2.*;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;

public final class ScrewIdentityControl {
    static final RigidTransform IDENTITY=new RigidTransform(1,0,0,0);
    private static final List<String> METRICS=List.of("image_auroc","pixel_auroc","aupro030");
    private ScrewIdentityControl() { }
    record Observation(String image,String role,String state,String reason,String label,String defect,Double score,String tensorSha256) { }
    record Encoded(List<Observation> attempted,List<String> names,List<double[]> units) { }
    record MapRelationship(String image,String defect,Double correlation,double hotspotDistance,double imageScoreDelta) { }
    public static void main(String[] args) throws Exception {
        switch(args[0]) {
            case "prepare" -> prepare(Path.of(args[1]),Path.of(args[2]));
            case "train" -> train(Path.of(args[1]));
            case "test" -> test(Path.of(args[1]));
            case "evaluate" -> evaluate(Path.of(args[1]));
            case "diagnose" -> diagnose(Path.of(args[1]));
            case "verify" -> {verify(Path.of(args[1]));System.out.println("B_IDENTITY - all source/input/output guards verified");}
            default -> throw new IllegalArgumentException("Expected prepare/train/test/evaluate/diagnose/verify");
        }
    }
    static void tensor(BufferedImage image,float[] destination) {
        if(image==null||image.getWidth()!=1024||image.getHeight()!=1024)throw new IllegalArgumentException("Native Screw1024 required");
        new AffineTensorSampler(AffineTensorSampler.Kernel.BILINEAR).sample(GeneralityImages.view(image),IDENTITY,destination);
    }
    static Set<String> exclusions(String role) {
        return switch(role) {
            case "fit" -> Set.of("screw_good_096.png","screw_good_158.png","screw_good_196.png");
            case "calibration" -> Set.of("screw_good_179.png");
            case "test" -> Set.of();
            default -> throw new IllegalArgumentException("Unknown matched role");
        };
    }
    static void requirePopulation(String role,List<String> attempted,List<String> usable) {
        int expected=switch(role){case "fit"->256;case "calibration"->64;case "test"->160;default->throw new IllegalArgumentException("Unknown role");};
        if(attempted.size()!=expected||new HashSet<>(attempted).size()!=attempted.size()
                ||!attempted.stream().filter(n->!exclusions(role).contains(n)).toList().equals(usable)
                ||!attempted.containsAll(exclusions(role)))throw new IllegalStateException("Matched population/order changed");
        for(String name:attempted)if(!name.equals(Path.of(name).getFileName().toString())||!EvaluationLabels.category(name).equals("screw")
                ||(!role.equals("test")&&!EvaluationLabels.defect(name).equals("good")))throw new IllegalArgumentException("Invalid matched identity");
    }
    private static JsonNode read(Path root,String file) throws Exception {return ComparisonArtifacts.JSON.readTree(root.resolve(file).toFile());}
    private static void json(Path root,String file,Object value) throws Exception {ComparisonArtifacts.json(root.resolve(file),value);}
    private static Path setting(Path root,String key) throws Exception {return Path.of(read(root,"config.json").path(key).asText());}
    private static List<String> names(Path root,String role,boolean usable) throws Exception {return Files.readAllLines(root.resolve("manifests/"+role+(usable?"-usable":"-attempted")+".txt"));}
    private static List<GeneralityInputs.Image> images(Path root,String role) throws Exception {
        return Arrays.stream(ComparisonArtifacts.JSON.readValue(root.resolve("images.json").toFile(),GeneralityInputs.Image[].class)).filter(i->i.role().equals(role)).toList();
    }
    private static void command(Path root,String stage) throws Exception {json(root,"command-"+stage+".json",Map.of("utc",Instant.now().toString(),"command",System.getProperty("sun.java.command"),
            "classpath",System.getProperty("java.class.path"),"java",System.getProperty("java.runtime.version"),"heap",Runtime.getRuntime().maxMemory()));}
    private static void prepare(Path canonical,Path root) throws Exception {
        Files.createDirectory(root);Files.createDirectory(root.resolve("manifests"));command(root,"prepare");
        var sources=CanonicalM2Integration.snapshot(Path.of("src"));json(root,"source-hashes.json",sources);
        for(var e:read(canonical,"source-hashes.json").properties())if(!e.getValue().asText().equals(sources.get(e.getKey())))throw new IllegalStateException("Canonical source changed");
        for(var e:sources.entrySet()){Path out=root.resolve("workspace").resolve(e.getKey());Files.createDirectories(out.getParent());Files.copy(Path.of(e.getKey()),out);}
        json(root,"canonical-evidence-hashes.json",CanonicalM2Integration.snapshot(canonical));
        var config=read(canonical,"config.json");Path dataset=Path.of(config.path("dataset").asText()),baseline=Path.of(config.path("baseline").asText());
        Path framing=Path.of(config.path("framing").asText()),audit=Path.of(read(framing,"config.json").path("audit").asText());
        Files.copy(canonical.resolve("images.json"),root.resolve("images.json"));var inputs=new TreeMap<String,String>();
        for(String role:List.of("fit","calibration","test")) {
            Path all=canonical.resolve("manifests/"+role+".txt"),usable=canonical.resolve(role+"-usable.txt");
            var attemptedNames=Files.readAllLines(all);var usableNames=Files.readAllLines(usable);requirePopulation(role,attemptedNames,usableNames);
            String expected=switch(role){case "fit"->ScrewIdentityBundle.FIT_SHA;case "calibration"->ScrewIdentityBundle.CAL_SHA;case "test"->ScrewIdentityBundle.TEST_SHA;default->throw new IllegalStateException();};
            if(!NpyMap.sha256(usable).equals(expected))throw new IllegalStateException("Wrong frozen usable manifest");
            Files.copy(all,root.resolve("manifests/"+role+"-attempted.txt"));Files.copy(usable,root.resolve("manifests/"+role+"-usable.txt"));
            inputs.put(all.toString(),NpyMap.sha256(all));inputs.put(usable.toString(),NpyMap.sha256(usable));
            if(!images(root,role).stream().map(GeneralityInputs.Image::filename).toList().equals(attemptedNames))throw new IllegalStateException("Source order mismatch");
            for(var image:images(root,role))if(!role.equals("test"))inputs.put(image.path(),image.sha256());
        }
        Path control=audit.resolve("screw/B_IDENTITY_PAIRED_AXIS_WITH_POLARITY");
        for(Path file:List.of(control.resolve("units.npy"),control.resolve("fit.txt"),control.resolve("coherence.json"),
                framing.resolve("features/coherence.json"),baseline.resolve("M2/screw/metrics-unified.json"),baseline.resolve("baseline-dataset-hashes.json")))inputs.put(file.toString(),NpyMap.sha256(file));
        Path protocol=Path.of("docs/experiments/SCREW_B_IDENTITY_CONTROL_PROTOCOL.md");Files.copy(protocol,root.resolve("protocol.md"));inputs.put(protocol.toString(),NpyMap.sha256(protocol));
        Files.copy(canonical.resolve("evaluation-protocol.md"),root.resolve("canonical-evaluation-protocol.md"));
        json(root,"input-hashes.json",inputs);json(root,"identity-contract.json",ScrewIdentityBundle.fields());
        var git=new ProcessBuilder("git","rev-parse","HEAD").start();String head=new String(git.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();if(git.waitFor()!=0)throw new IllegalStateException("No HEAD");
        json(root,"config.json",Map.of("entry_head",head,"canonical",canonical.toString(),"dataset",dataset.toString(),"baseline",baseline.toString(),
                "identity_reference",control.toString(),"safe_coherence",framing.resolve("features/coherence.json").toString(),"encoder_sha256",M2ModelStore.encoderHash(),"pose_estimation",false));
        mark(root,"prepare");System.out.println("B_IDENTITY - matched manifests/contract/protocol frozen; no pose estimation");
    }
    static void verify(Path root) throws Exception {
        for(String file:List.of("source-hashes.json","input-hashes.json","canonical-evidence-hashes.json","test-input-hashes.json",
                "prepare-evidence-hashes.json","train-evidence-hashes.json","test-evidence-hashes.json","metrics-evidence-hashes.json","diagnostics-evidence-hashes.json"))if(Files.exists(root.resolve(file))) {
            for(var e:read(root,file).properties()) {
                if(!NpyMap.sha256(Path.of(e.getKey())).equals(e.getValue().asText()))throw new IllegalStateException("Changed source/input/output: "+e.getKey());
                if(file.equals("source-hashes.json")&&!NpyMap.sha256(root.resolve("workspace").resolve(e.getKey())).equals(e.getValue().asText()))throw new IllegalStateException("Changed source copy");
            }
        }
        if(Files.exists(root.resolve("source-hashes.json"))&&!read(root,"source-hashes.json").equals(ComparisonArtifacts.JSON.valueToTree(CanonicalM2Integration.snapshot(Path.of("src")))))throw new IllegalStateException("Source set changed");
    }
    private static void mark(Path root,String stage) throws Exception {verify(root);var hashes=CanonicalM2Integration.snapshot(root);hashes.keySet().removeIf(p->p.replace('\\','/').contains("/workspace/"));json(root,stage+"-evidence-hashes.json",hashes);}
    static void requireStage(Path root,String stage) {if(!Files.isRegularFile(root.resolve(stage+"-evidence-hashes.json")))throw new IllegalStateException("Missing stage: "+stage);}
    private static Encoded encode(Path root,String role,OnnxMobileNetV4Encoder encoder,M2Model model) throws Exception {
        var rows=new ArrayList<Observation>();var usable=new ArrayList<String>();var units=new ArrayList<double[]>();
        float[] tensor=new float[AffineTensorSampler.ELEMENTS],reference=tensor.clone();var sampler=new AffineTensorSampler(AffineTensorSampler.Kernel.BILINEAR);
        for(var image:images(root,role)) {
            if(exclusions(role).contains(image.filename())) {rows.add(new Observation(image.filename(),role,"MATCHED_EXCLUSION","FROZEN_CANONICAL_SOURCE_BORDER_EXCLUSION","good","good",null,null));continue;}
            if(!NpyMap.sha256(Path.of(image.path())).equals(image.sha256()))throw new IllegalStateException("Source hash mismatch");
            var decoded=ImageIO.read(Path.of(image.path()).toFile());
            try {
                tensor(decoded,tensor);sampler.sample(GeneralityImages.view(decoded),new RigidTransform(1,0,0,0),reference);
                if(!Arrays.equals(tensor,reference))throw new IllegalStateException("Audited B identity tensor mismatch");
                double[] unit=M2Model.normalize(encoder.extractPrepared(tensor));usable.add(image.filename());units.add(unit);
                Double score=model==null?null:M2Model.imageScore(model.calibrate(model.raw(unit)));String defect=EvaluationLabels.defect(image.filename());
                var bytes=ByteBuffer.allocate(tensor.length*4).order(ByteOrder.LITTLE_ENDIAN);for(float value:tensor)bytes.putFloat(value);
                rows.add(new Observation(image.filename(),role,"OK","NO_POSE_REQUIRED",defect.equals("good")?"good":"anomaly",defect,score,ScrewQualificationBundle.hash(bytes.array())));
            }finally{if(decoded!=null)decoded.flush();}
            if(rows.size()%50==0)System.out.println("B_IDENTITY - "+role+" - attempted"+rows.size()+"; usable"+usable.size());
        }
        requirePopulation(role,rows.stream().map(Observation::image).toList(),usable);
        if(!usable.equals(names(root,role,true)))throw new IllegalStateException("Usable order drift");
        json(root,role+"-observations.json",rows);json(root,role+"-coverage.json",Map.of("attempted",rows.size(),"usable",usable.size(),"excluded",rows.size()-usable.size(),"new_failures",0,"exclusion_names",new TreeSet<>(exclusions(role)),"pose_estimated",false));
        double[] flat=new double[units.size()*18816];for(int i=0;i<units.size();i++)System.arraycopy(units.get(i),0,flat,i*18816,18816);array(root.resolve(role+"-units.npy"),flat,units.size(),"unit-features");
        return new Encoded(rows,usable,units);
    }
    private static void array(Path file,double[] values,int rows,String kind) throws Exception {ComparisonArtifacts.writeMap(file,values,rows,values.length/rows,file.getFileName().toString(),ScrewIdentityBundle.PREPROCESSING,kind);}
    private static void scores(Path root,String role,List<double[]> units,M2Model model) throws Exception {
        double[] raw=new double[units.size()*196],z=raw.clone(),max=new double[units.size()];
        for(int i=0;i<units.size();i++){var r=model.raw(units.get(i));var s=model.calibrate(r);System.arraycopy(r,0,raw,i*196,196);System.arraycopy(s,0,z,i*196,196);max[i]=M2Model.imageScore(s);}
        array(root.resolve(role+"-raw.npy"),raw,units.size(),"raw");array(root.resolve(role+"-native-z.npy"),z,units.size(),"positional_z");array(root.resolve(role+"-image-scores.npy"),max,units.size(),"native-max");
    }
    private static void train(Path root) throws Exception {
        requireStage(root,"prepare");verify(root);command(root,"train");Encoded fit,cal;
        try(var encoder=new OnnxMobileNetV4Encoder()){fit=encode(root,"fit",encoder,null);cal=encode(root,"calibration",encoder,null);}
        Path control=setting(root,"identity_reference");var retained=NpyMap.read(control.resolve("units.npy"));
        if(retained.rows()!=253||retained.columns()!=18816||!fit.names.equals(Files.readAllLines(control.resolve("fit.txt"))))throw new IllegalStateException("Wrong retained identity control");
        for(int i=0;i<253;i++)if(!Arrays.equals(fit.units.get(i),Arrays.copyOfRange(retained.values(),i*18816,(i+1)*18816)))throw new IllegalStateException("Retained identity descriptor mismatch");
        var coherence=GeneralityCoherence.compute(fit.units.toArray(double[][]::new));
        if(!sameCoherence(coherence,read(control,"coherence.json")))throw new IllegalStateException("Identity coherence drift");
        json(root,"fit-coherence.json",coherence);json(root,"canonical-safe-coherence.json",ComparisonArtifacts.JSON.readTree(setting(root,"safe_coherence").toFile()));
        var moments=M2Trainer.fitStatistics(fit.units);var fitted=M2Trainer.uncalibrated(M2ModelStore.encoderHash(),moments);
        var calibration=M2Trainer.calibrationStatistics(fitted,cal.units);var model=M2Trainer.calibrated(fitted,calibration);
        for(var e:Map.of("local-mean",moments.mean(),"local-sample-std",moments.sampleStd(),"local-effective-std",moments.effectiveStd(),"alpha",calibration.mean(),"beta-sample-std",calibration.sampleStd(),"beta-effective-std",calibration.effectiveStd()).entrySet())array(root.resolve(e.getKey()+".npy"),e.getValue(),e.getValue().length==18816?196:1,"model-statistic");
        scores(root,"fit",fit.units,model);scores(root,"calibration",cal.units,model);Path bundle=root.resolve("screw-b-identity.m2");ScrewIdentityBundle.write(bundle,model);
        if(!Arrays.equals(ScrewQualificationBundle.payload(model),ScrewQualificationBundle.payload(ScrewIdentityBundle.read(bundle,M2ModelStore.encoderHash()))))throw new IllegalStateException("Reload mismatch");
        var canonical=ScrewQualificationBundle.read(setting(root,"canonical").resolve("screw.canonical-m2"),M2ModelStore.encoderHash());
        if(Arrays.equals(ScrewQualificationBundle.payload(model),ScrewQualificationBundle.payload(canonical)))throw new IllegalStateException("Unexpected canonical state reuse");
        json(root,"model-summary.json",Map.of("local_sample_std",ScrewFraming.distribution(moments.sampleStd()),"alpha",ScrewFraming.distribution(calibration.mean()),"beta_sample_std",ScrewFraming.distribution(calibration.sampleStd()),
                "local_floor_count",Arrays.stream(moments.sampleStd()).filter(v->v<1e-6).count(),"beta_floor_count",Arrays.stream(calibration.sampleStd()).filter(v->v<1e-6).count(),"tensor_reference_images",316,"tensor_reference_max_error",0,"retained_fit_descriptor_max_error",0,"reload_max_error",0));
        json(root,"train-freeze.json",Map.of("utc",Instant.now().toString(),"bundle_identity",NpyMap.sha256(bundle),"bundle_bytes",Files.size(bundle),"payload_sha256",ScrewQualificationBundle.hash(ScrewQualificationBundle.payload(model)),
                "contract_sha256",NpyMap.sha256(root.resolve("identity-contract.json")),"protocol_sha256",NpyMap.sha256(root.resolve("protocol.md")),"fit_usable_sha256",ScrewIdentityBundle.FIT_SHA,"cal_usable_sha256",ScrewIdentityBundle.CAL_SHA,"test_manifest_sha256",ScrewIdentityBundle.TEST_SHA,"test_opened",false));
        mark(root,"train");System.out.println("B_IDENTITY - fresh FIT253/CAL63 frozen; exact retained controls; TEST unopened");
    }
    static boolean sameCoherence(GeneralityCoherence.Summary actual,JsonNode retained) throws Exception {
        return ComparisonArtifacts.JSON.readTree(ComparisonArtifacts.JSON.writeValueAsBytes(actual)).equals(retained);
    }
    private static void test(Path root) throws Exception {
        requireStage(root,"train");verify(root);command(root,"test");var hashes=read(setting(root,"canonical"),"test-input-hashes.json");json(root,"test-input-hashes.json",hashes);verify(root);
        var model=ScrewIdentityBundle.read(root.resolve("screw-b-identity.m2"),M2ModelStore.encoderHash());Encoded encoded;
        try(var encoder=new OnnxMobileNetV4Encoder()){encoded=encode(root,"test",encoder,model);}scores(root,"test",encoded.units,model);
        Path evaluation=root.resolve("evaluation");Files.createDirectory(evaluation);String geometry="screw-1024x1024-B-identity700-chain";
        var csv=new StringBuilder("run_id,method,category,filename,label,defect,image_score,score_kind,native_map_path,eval_map_path,dtype,shape,geometry_id,image_sha256\n");int i=0;
        var imageHashes=new HashMap<String,String>();for(var entry:images(root,"test"))imageHashes.put(entry.filename(),entry.sha256());
        for(var row:encoded.attempted) {
            double[] z=model.calibrate(model.raw(encoded.units.get(i++)));if(M2Model.imageScore(z)!=row.score)throw new IllegalStateException("Score changed");
            String stem=row.image.substring(0,row.image.length()-4),nativePath="maps/"+stem+".native.npy",fullPath="maps/"+stem+".evaluated.npy";
            ComparisonArtifacts.writeMap(evaluation.resolve(nativePath),z,14,14,row.image,geometry,"native");
            ComparisonArtifacts.writeMap(evaluation.resolve(fullPath),ScrewQualificationInput.evaluatedMap(z,IDENTITY,1024),1024,1024,row.image,geometry,"evaluated");
            csv.append(String.join(",","screw-b-identity-control-20261020","M2","screw",row.image,row.label,row.defect,Double.toString(row.score),"positional_z",nativePath,fullPath,"float64","14x14",geometry,imageHashes.get(row.image))).append('\n');
        }
        Files.writeString(evaluation.resolve("predictions.csv"),csv,StandardOpenOption.CREATE_NEW);Files.write(evaluation.resolve("test.txt"),encoded.names,StandardOpenOption.CREATE_NEW);
        json(evaluation,"config.json",Map.of("preprocessing",ScrewIdentityBundle.fields(),"bundle_identity",NpyMap.sha256(root.resolve("screw-b-identity.m2")),"attempted",160,"usable",160,"failed",0,"metric_scope","FULL_POPULATION","same_evaluation_operators",true));
        mark(root,"test");System.out.println("B_IDENTITY - all160 TEST scored; original1024 maps frozen");
    }
    static List<Map<String,Object>> comparison(JsonNode historical,JsonNode identity,JsonNode canonical) {
        var rows=new ArrayList<Map<String,Object>>();for(String metric:METRICS) {
            for(JsonNode node:List.of(historical,identity,canonical))if(!node.path(metric).isNumber()||!Double.isFinite(node.path(metric).asDouble()))throw new IllegalArgumentException("Missing/nonfinite metric: "+metric);
            double h=historical.path(metric).asDouble(),b=identity.path(metric).asDouble(),c=canonical.path(metric).asDouble();
            rows.add(Map.of("metric",metric,"historical",h,"b_identity",b,"canonical",c,"historical_to_identity",b-h,"identity_to_canonical",c-b));
        }return rows;
    }
    private static void evaluate(Path root) throws Exception {
        requireStage(root,"test");verify(root);command(root,"evaluate");Path evaluation=root.resolve("evaluation");
        ComparisonEvaluator.evaluate(evaluation,setting(root,"dataset"),evaluation.resolve("test.txt"),"M2","screw");
        var identity=read(evaluation,"metrics-unified.json");var canonical=read(setting(root,"canonical"),"evaluation/metrics-unified.json");var historical=read(setting(root,"baseline"),"M2/screw/metrics-unified.json");
        for(String key:List.of("n_good","n_anomaly","regions","foreground_pixels","background_pixels"))if(identity.path(key).asLong()!=canonical.path(key).asLong()||identity.path(key).asLong()!=historical.path(key).asLong())throw new IllegalStateException("Unmatched evaluator population");
        json(root,"three-way-primary.json",comparison(historical,identity,canonical));mark(root,"metrics");System.out.println("B_IDENTITY - primary three-way metrics frozen");
    }
    static Double correlation(double[] a,double[] b) {
        if(a.length==0||a.length!=b.length)throw new IllegalArgumentException("Matched map dimensions required");
        double ma=Arrays.stream(a).average().orElseThrow(),mb=Arrays.stream(b).average().orElseThrow(),aa=0,bb=0,ab=0;
        for(int i=0;i<a.length;i++){double x=a[i]-ma,y=b[i]-mb;aa+=x*x;bb+=y*y;ab+=x*y;}
        return aa==0||bb==0?null:Math.clamp(ab/Math.sqrt(aa*bb),-1,1);
    }
    static int firstArgmax(double[] values) {if(values.length==0)throw new IllegalArgumentException("Empty map");int best=0;for(int i=1;i<values.length;i++)if(values[i]>values[best])best=i;return best;}
    private static void diagnose(Path root) throws Exception {
        requireStage(root,"metrics");verify(root);command(root,"diagnose");Path canonical=setting(root,"canonical");
        CanonicalPhase2Bridge.main(new String[]{"defects",root.resolve("evaluation").toString(),setting(root,"dataset").toString(),root.resolve("identity-per-defect.json").toString()});
        var groups=new ArrayList<Map<String,Object>>();var historic=read(canonical,"baseline-per-defect.json");var canon=read(canonical,"canonical-per-defect.json");
        for(var identity:read(root,"identity-per-defect.json")) {
            JsonNode h=null,c=null;String group=identity.path("defect").asText();for(var row:historic)if(row.path("defect").asText().equals(group))h=row;for(var row:canon)if(row.path("defect").asText().equals(group))c=row;
            if(h==null||c==null)throw new IllegalStateException("Missing matched defect");
            for(String key:List.of("n_good","n_anomaly","regions","foreground_pixels","background_pixels"))if(identity.path(key).asLong()!=h.path(key).asLong()||identity.path(key).asLong()!=c.path(key).asLong())throw new IllegalStateException("Defect coverage mismatch");
            groups.add(Map.of("defect",group,"n_good",identity.path("n_good").asInt(),"n_anomaly",identity.path("n_anomaly").asInt(),"metrics",comparison(h,identity,c)));
        }
        json(root,"three-way-defects.json",groups);var oldRows=new HashMap<String,String[]>();
        for(String line:Files.readAllLines(canonical.resolve("evaluation/predictions.csv")).subList(1,161)){var row=line.split(",",-1);oldRows.put(row[3],row);}
        var relationships=new ArrayList<MapRelationship>();
        for(String line:Files.readAllLines(root.resolve("evaluation/predictions.csv")).subList(1,161)) {
            var row=line.split(",",-1);var old=oldRows.get(row[3]);if(old==null)throw new IllegalStateException("Unmatched map");
            var identity=NpyMap.read(root.resolve("evaluation").resolve(row[9]));var canonicalMap=NpyMap.read(canonical.resolve("evaluation").resolve(old[9]));
            if(identity.rows()!=1024||identity.columns()!=1024||canonicalMap.rows()!=1024||canonicalMap.columns()!=1024)throw new IllegalStateException("Original map shape mismatch");
            int a=firstArgmax(identity.values()),b=firstArgmax(canonicalMap.values());
            relationships.add(new MapRelationship(row[3],row[5],correlation(identity.values(),canonicalMap.values()),Math.hypot(a%1024-b%1024,a/1024-b/1024),Double.parseDouble(old[6])-Double.parseDouble(row[6])));
        }
        json(root,"map-relationships.json",relationships);var summaries=new TreeMap<String,Object>();
        for(String group:relationships.stream().map(MapRelationship::defect).distinct().toList()) {
            var selected=relationships.stream().filter(r->r.defect.equals(group)).toList();double[] corr=selected.stream().filter(r->r.correlation!=null).mapToDouble(MapRelationship::correlation).toArray();
            summaries.put(group,Map.of("images",selected.size(),"nonconstant_maps",corr.length,"correlation",corr.length==0?Map.of():ScrewFraming.distribution(corr),
                    "hotspot_distance_original_pixels",ScrewFraming.distribution(selected.stream().mapToDouble(MapRelationship::hotspotDistance).toArray()),"canonical_minus_identity_score",ScrewFraming.distribution(selected.stream().mapToDouble(MapRelationship::imageScoreDelta).toArray())));
        }
        json(root,"map-relationship-summary.json",summaries);mark(root,"diagnostics");System.out.println("B_IDENTITY - matched defect/map diagnostics frozen; no pose values read");
    }
}
