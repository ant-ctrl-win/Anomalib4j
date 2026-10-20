package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.antctrlwin.anomalib4j.canonical.*;
import io.github.antctrlwin.anomalib4j.m2.M2Model;
import io.github.antctrlwin.anomalib4j.m2.M2ModelStore;
import io.github.antctrlwin.anomalib4j.onnx.OnnxMobileNetV4Encoder;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import static io.github.antctrlwin.anomalib4j.evaluation.ScrewFraming.*;

public final class ScrewFramingCheckpoint {
    private ScrewFramingCheckpoint() { }
    record Row(String image,String population,GeneralityGeometry.Pose pose,Measurement current) { }
    record CalRow(String image,String population,GeneralityGeometry.Pose pose,Measurement framed,boolean outsideFitEnvelope) { }
    private static final String CURRENT="AXIS_WITH_POLARITY",IDENTITY="B_IDENTITY_PAIRED_AXIS_WITH_POLARITY";
    private static final String POLICY="SOURCE_BORDER: reject before CNN, no fallback/no score; retain failure and coverage counts; never call survivor-only metrics complete-dataset validation";
    public static void main(String[] args) throws Exception {
        switch(args[0]) {
            case "fit" -> fit(Path.of(args[1]),Path.of(args[2]));
            case "features" -> features(Path.of(args[1]));
            case "freeze" -> freeze(Path.of(args[1]));
            case "cal" -> cal(Path.of(args[1]));
            case "verify" -> {verify(Path.of(args[1]));System.out.println("SCREW - all retained source/input/stage hashes verified");}
            default -> throw new IllegalArgumentException("Expected fit/features/freeze/cal/verify");
        }
    }
    private static void json(Path root,String name,Object value) throws Exception {ComparisonArtifacts.json(root.resolve(name),value);}
    private static JsonNode read(Path root,String name) throws Exception {return ComparisonArtifacts.JSON.readTree(root.resolve(name).toFile());}
    private static void command(Path root,String phase) throws Exception {
        json(root,"command-"+phase+".json",Map.of("utc",Instant.now().toString(),"command",System.getProperty("sun.java.command"),
                "classpath",System.getProperty("java.class.path"),"java",System.getProperty("java.runtime.version"),"max_heap",Runtime.getRuntime().maxMemory()));
    }
    private static List<GeneralityInputs.Image> images(Path root,String role) throws Exception {
        return Arrays.stream(ComparisonArtifacts.JSON.readValue(root.resolve("images.json").toFile(),GeneralityInputs.Image[].class))
                .filter(i->i.role().equals(role)).toList();
    }
    private static boolean[] mask(GeneralityGeometry estimator) throws Exception {
        var field=GeneralityGeometry.class.getDeclaredField("mask");field.setAccessible(true);return ((boolean[])field.get(estimator)).clone();
    }
    private static String population(GeneralityGeometry.Pose p) {
        return p.state()==GeneralityGeometry.State.OK?"VALID":p.border()?"SOURCE_BORDER":"OTHER_FAILURE";
    }
    private static GeneralityGeometry.Pose pose(GeneralityGeometry estimator,int[] rgb,String name,String role,JsonNode retained) throws Exception {
        var p=estimator.estimate(rgb,"screw").get(GeneralityGeometry.Strategy.AXIS_WITH_POLARITY);
        JsonNode old=null;for(var row:retained)if(row.path("category").asText().equals("screw")&&row.path("role").asText().equals(role)&&row.path("image").asText().equals(name)) {
            if(old!=null)throw new IllegalStateException("Duplicate retained pose");old=row.path("pose");
        }
        if(old==null||!p.equals(ComparisonArtifacts.JSON.treeToValue(old,GeneralityGeometry.Pose.class)))throw new IllegalStateException("Audited pose changed: "+name);
        return p;
    }
    private static List<Shape> loadShapes(Path root,String role,List<Row> rows) throws Exception {
        Path audit=Path.of(read(root,"config.json").path("audit").asText());var retained=read(audit,"poses-train.json");
        var estimator=new GeneralityGeometry();var shapes=new ArrayList<Shape>();
        var current=new Contract("A_CURRENT",1,349.5,349.5,0);
        for(var entry:images(root,role)) {
            if(!NpyMap.sha256(Path.of(entry.path())).equals(entry.sha256()))throw new IllegalStateException("Source image changed");
            var image=ImageIO.read(Path.of(entry.path()).toFile());
            try {
                int[] rgb=GeneralityImages.view(image);var p=pose(estimator,rgb,entry.filename(),role,retained);
                Measurement measurement=null;
                if(p.state()==GeneralityGeometry.State.OK) {
                    var shape=shape(entry.filename(),p,mask(estimator));shapes.add(shape);measurement=measure(shape,current);
                    if(Double.doubleToLongBits(measurement.outsideFraction())!=Double.doubleToLongBits(p.clippedFraction()))
                        throw new IllegalStateException("Center clipping disagrees with frozen audit");
                }
                rows.add(new Row(entry.filename(),population(p),p,measurement));
            } finally {image.flush();}
        }
        return shapes;
    }
    private static void fit(Path audit,Path root) throws Exception {
        Files.createDirectory(root);Files.createDirectory(root.resolve("manifests"));command(root,"fit");
        var source=CanonicalM2Integration.snapshot(Path.of("src"));json(root,"source-hashes.json",source);
        for(var e:source.entrySet()){Path out=root.resolve("workspace").resolve(e.getKey());Files.createDirectories(out.getParent());Files.copy(Path.of(e.getKey()),out);}
        var oldSources=read(audit,"source-hashes.json");
        for(var e:oldSources.properties())if(!e.getValue().asText().equals(source.get(e.getKey())))throw new IllegalStateException("Audited source changed: "+e.getKey());
        var inputs=new TreeMap<String,String>();var entries=GeneralityInputs.read(audit).stream()
                .filter(i->i.category().equals("screw")&&!i.role().equals("test")).toList();
        if(entries.size()!=320)throw new IllegalStateException("Expected320 original training identities");
        json(root,"images.json",entries);
        for(String file:List.of("images.json","poses-train.json","source-hashes.json","input-hashes.json")) {
            Path p=audit.resolve(file);inputs.put(p.toString(),NpyMap.sha256(p));
        }
        var identities=new HashSet<String>();
        for(String role:List.of("fit","calibration")) {
            Path manifest=audit.resolve("manifests/screw-"+role+".txt");var names=Files.readAllLines(manifest);
            if(!entries.stream().filter(i->i.role().equals(role)).map(GeneralityInputs.Image::filename).toList().equals(names)
                    ||names.size()!=(role.equals("fit")?256:64))throw new IllegalStateException("Frozen manifest order/count mismatch");
            if(names.stream().anyMatch(n->!n.matches("screw_good_[0-9]+\\.png")||!identities.add(n)))throw new IllegalStateException("Invalid/overlapping split");
            inputs.put(manifest.toString(),NpyMap.sha256(manifest));Files.copy(manifest,root.resolve("manifests/"+role+".txt"));
        }
        for(var e:entries)if(e.role().equals("fit"))inputs.put(e.path(),e.sha256());
        for(String cohort:List.of(CURRENT,IDENTITY))for(String file:List.of("fit.txt","units.npy","coherence.json")) {
            Path p=audit.resolve("screw/"+cohort+"/"+file);inputs.put(p.toString(),NpyMap.sha256(p));
        }
        Path protocol=Path.of("docs/experiments/SCREW_FRAMING_PROTOCOL.md");Files.copy(protocol,root.resolve("protocol.md"));inputs.put(protocol.toString(),NpyMap.sha256(protocol));
        json(root,"input-hashes.json",inputs);
        var git=new ProcessBuilder("git","rev-parse","HEAD").start();String head=new String(git.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
        if(git.waitFor()!=0)throw new IllegalStateException("Cannot capture HEAD");
        json(root,"config.json",Map.of("entry_head",head,"audit",audit.toString(),"encoder_sha256",M2ModelStore.encoderHash(),
                "margin",MARGIN,"source_border_policy",POLICY,"pose","frozen dark Otsu175/8-component/4-fill/centroid/axis>=.50/abs polarity>=.10; no estimator change",
                "source_view","unchanged GeneralityImages logical-sRGB then bicubic700","m2_fit_cal_score",false,"test_pixels_read",false));
        verify(root);var rows=new ArrayList<Row>();var shapes=loadShapes(root,"fit",rows);
        if(shapes.size()!=253||rows.stream().filter(r->r.population.equals("SOURCE_BORDER")).count()!=3)throw new IllegalStateException("Audited FIT coverage changed");
        var envelope=envelope(shapes);json(root,"fit-population-extents.json",rows);json(root,"fit-envelope.json",envelope);
        var candidates=new ArrayList<Contract>();candidates.add(new Contract("A_CURRENT",1,349.5,349.5,0));
        double centered=centeredScale(envelope,MARGIN),rounded=Math.floor(centered*100)/100;
        candidates.add(new Contract("B_CENTERED_SAFE",centered,349.5,349.5,MARGIN));
        candidates.add(new Contract("B_CENTERED_ROUNDED",rounded,349.5,349.5,MARGIN));
        var place=placement(envelope,1,MARGIN);
        if(place.feasible())candidates.add(movable("C_PLACEMENT",envelope,1,MARGIN));
        double combined=movableScale(envelope,MARGIN);candidates.add(movable("D_SCALE_PLACEMENT",envelope,combined,MARGIN));
        json(root,"candidates.json",candidates);
        var summaries=new LinkedHashMap<String,Object>();
        for(var c:candidates){var measured=shapes.stream().map(s->measure(s,c)).toList();json(root,"fit-"+c.id()+".json",measured);summaries.put(c.id(),summary(measured));}
        json(root,"fit-summaries.json",summaries);
        double half=Math.max(Math.max(-envelope.minX(),envelope.maxX()),Math.max(-envelope.minY(),envelope.maxY()));
        json(root,"analytical.json",Map.of("centered_scale_zero_margin",centeredScale(envelope,0),"centered_scale_safe",centered,
                "placement_scale1_zero_margin",placement(envelope,1,0),"placement_scale1_safe",place,"combined_scale_safe",combined,
                "minimum_symmetric_canvas_zero_margin",(int)Math.ceil(2*half+1),"minimum_symmetric_canvas_safe",(int)Math.ceil(2*(half+MARGIN)+1),
                "literal_current_anchor_expand_right_bottom_possible",envelope.minX()+349.5>=0&&envelope.minY()+349.5>=0));
        Contract selected=candidates.stream().filter(c->c.id().equals(place.feasible()?"C_PLACEMENT":"D_SCALE_PLACEMENT")).findFirst().orElseThrow();
        json(root,"feature-plan.json",Map.of("candidates",List.of(selected),"basis","FIT full-cell support containment, largest scale and midpoint of global feasible placement interval",
                "fit_images",253,"cal_opened",false,"test_opened",false));
        mark(root,"fit");System.out.println("SCREW - FIT256 accounted; valid253; source-border3; feature shortlist "+selected);
    }
    static void verify(Path root) throws Exception {
        for(String name:List.of("source-hashes.json","input-hashes.json","fit-evidence-hashes.json","features-evidence-hashes.json",
                "freeze-evidence-hashes.json","cal-input-hashes.json","cal-evidence-hashes.json"))if(Files.exists(root.resolve(name))) {
            for(var e:read(root,name).properties()) {
                if(!NpyMap.sha256(Path.of(e.getKey())).equals(e.getValue().asText()))throw new IllegalStateException("Changed evidence: "+e.getKey());
                if(name.equals("source-hashes.json")&&!NpyMap.sha256(root.resolve("workspace").resolve(e.getKey())).equals(e.getValue().asText()))
                    throw new IllegalStateException("Changed source archive");
            }
        }
    }
    private static void mark(Path root,String phase) throws Exception {
        verify(root);var hashes=CanonicalM2Integration.snapshot(root);
        hashes.keySet().removeIf(p->p.replace('\\','/').contains("/workspace/"));json(root,phase+"-evidence-hashes.json",hashes);
    }
    private static Contract selected(Path root) throws Exception {
        return ComparisonArtifacts.JSON.treeToValue(read(root,"feature-plan.json").path("candidates").get(0),Contract.class);
    }
    private static void require(Path root,String marker) {
        if(!Files.isRegularFile(root.resolve(marker+"-evidence-hashes.json")))throw new IllegalStateException("Missing prior stage: "+marker);
    }
    private static void features(Path root) throws Exception {
        require(root,"fit");verify(root);command(root,"features");var contract=selected(root);
        if(contract.scale()!=1)throw new IllegalStateException("Scaled feature sampling is not qualified by this rigid-only experiment tool");
        Path audit=Path.of(read(root,"config.json").path("audit").asText());
        var valid=read(root,"fit-population-extents.json");var names=new ArrayList<String>();
        for(var r:valid)if(r.path("population").asText().equals("VALID"))names.add(r.path("image").asText());
        for(String cohort:List.of(CURRENT,IDENTITY))if(!names.equals(Files.readAllLines(audit.resolve("screw/"+cohort+"/fit.txt"))))throw new IllegalStateException("Unmatched retained cohort");
        var reference=NpyMap.read(audit.resolve("screw/"+CURRENT+"/units.npy"));
        if(reference.rows()!=names.size()||reference.columns()!=18816)throw new IllegalStateException("Invalid retained descriptor dimensions");
        var estimator=new GeneralityGeometry();var retained=read(audit,"poses-train.json");var sampler=new AffineTensorSampler(AffineTensorSampler.Kernel.BILINEAR);
        float[] tensor=new float[AffineTensorSampler.ELEMENTS];double[][] units=new double[names.size()][];int at=0;
        try(var encoder=new OnnxMobileNetV4Encoder()) {
            for(var e:images(root,"fit"))if(names.contains(e.filename())) {
                var image=ImageIO.read(Path.of(e.path()).toFile());
                try {
                    int[] rgb=GeneralityImages.view(image);var p=pose(estimator,rgb,e.filename(),"fit",retained);
                    sampler.sample(rgb,p.transform(),tensor);double[] control=M2Model.normalize(encoder.extractPrepared(tensor));
                    if(!Arrays.equals(control,Arrays.copyOfRange(reference.values(),at*18816,(at+1)*18816)))throw new IllegalStateException("Current canonical descriptor changed");
                    sampler.sample(rgb,contract.rigid(p),tensor);units[at]=M2Model.normalize(encoder.extractPrepared(tensor));
                    if(at==0)try(var stream=OnnxMobileNetV4Encoder.class.getResourceAsStream("/models/mobilenetv4_spatial_14x14.onnx")) {
                        if(stream==null)throw new IllegalStateException("No encoder");M2CpuEvidence.verify(stream.readAllBytes(),tensor,root);
                    }
                    at++;if(at%50==0)System.out.println("SCREW - FIT feature coherence - "+at+"/"+names.size());
                } finally {image.flush();}
            }
        }
        if(at!=names.size())throw new IllegalStateException("Incomplete extraction");
        Path out=root.resolve("features");Files.createDirectory(out);Files.write(out.resolve("fit.txt"),names,StandardOpenOption.CREATE_NEW);
        double[] flat=new double[at*18816];for(int i=0;i<at;i++)System.arraycopy(units[i],0,flat,i*18816,18816);
        ComparisonArtifacts.writeMap(out.resolve("units.npy"),flat,at,18816,contract.id(),"14x14x96-unit","FIT-only");
        json(out,"coherence.json",GeneralityCoherence.compute(units));
        json(root,"feature-controls.json",Map.of("matched_names",at,"current_canonical_reencoded",at,"current_descriptor_max_error",0,
                "baseline_retained",read(audit,"screw/"+IDENTITY+"/coherence.json"),"current_retained",read(audit,"screw/"+CURRENT+"/coherence.json"),
                "plan_sha256",NpyMap.sha256(root.resolve("feature-plan.json")),"m2_fit_cal_score",false));
        mark(root,"features");System.out.println("SCREW - feature coherence complete; no CAL/TEST encodings");
    }
    private static void freeze(Path root) throws Exception {
        require(root,"features");verify(root);command(root,"freeze");var c=selected(root);
        var summary=read(root,"fit-summaries.json").path(c.id());
        if(summary.path("support_clipping_cases").asInt()!=0||summary.path("clearance").path("min").asDouble()<MARGIN-1e-9)
            throw new IllegalStateException("FIT contract is unsafe");
        json(root,"selected-contract.json",Map.of("framing",c,"source_border_policy",POLICY,"source_view",read(root,"config.json").path("source_view"),
                "pose",read(root,"config.json").path("pose"),"encoder_sha256",M2ModelStore.encoderHash(),"sampler","unchanged production B; fixed rigid placement; canonical700 to NCHW224",
                "fit_envelope_sha256",NpyMap.sha256(root.resolve("fit-envelope.json")),"feature_coherence_sha256",NpyMap.sha256(root.resolve("features/coherence.json"))));
        json(root,"fit-freeze.json",Map.of("utc",Instant.now().toString(),"contract_sha256",NpyMap.sha256(root.resolve("selected-contract.json")),
                "cal_opened",false,"test_opened",false,"selection_basis","FIT geometry and normal coherence only; no detector fit or anomaly metrics"));
        mark(root,"freeze");System.out.println("SCREW - FIT contract frozen - "+NpyMap.sha256(root.resolve("selected-contract.json")));
    }
    private static void cal(Path root) throws Exception {
        require(root,"freeze");verify(root);command(root,"cal");String hash=read(root,"fit-freeze.json").path("contract_sha256").asText();
        if(!hash.equals(NpyMap.sha256(root.resolve("selected-contract.json"))))throw new IllegalStateException("Frozen contract changed");
        var c=ComparisonArtifacts.JSON.treeToValue(read(root,"selected-contract.json").path("framing"),Contract.class);
        var inputs=new TreeMap<String,String>();for(var e:images(root,"calibration"))inputs.put(e.path(),e.sha256());json(root,"cal-input-hashes.json",inputs);verify(root);
        var rows=new ArrayList<Row>();var shapes=loadShapes(root,"calibration",rows);var envelope=ComparisonArtifacts.JSON.treeToValue(read(root,"fit-envelope.json"),Envelope.class);
        var measured=new HashMap<String,Measurement>();var outside=new HashSet<String>();
        for(var s:shapes) {
            measured.put(s.image(),measure(s,c));var b=s.support();
            if(b.minX()<envelope.minX()||b.maxX()>envelope.maxX()||b.minY()<envelope.minY()||b.maxY()>envelope.maxY())outside.add(s.image());
        }
        var output=rows.stream().map(r->new CalRow(r.image,r.population,r.pose,measured.get(r.image),outside.contains(r.image))).toList();
        json(root,"cal-population-extents.json",output);json(root,"cal-summary.json",Map.of("total",rows.size(),"valid",shapes.size(),
                "source_border",rows.stream().filter(r->r.population.equals("SOURCE_BORDER")).count(),"other_failure",rows.stream().filter(r->r.population.equals("OTHER_FAILURE")).count(),
                "framing",summary(new ArrayList<>(measured.values())),"fit_envelope_violation_images",new TreeSet<>(outside),"contract_sha256",hash,"contract_adjusted",false));
        mark(root,"cal");System.out.println("SCREW - CAL geometry complete - "+shapes.size()+"/"+rows.size()+" valid; frozen contract unchanged");
    }
}
