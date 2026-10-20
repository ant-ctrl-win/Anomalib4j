package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.antctrlwin.anomalib4j.canonical.RigidTransform;
import io.github.antctrlwin.anomalib4j.m2.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

final class ScrewCalInputs {
    static final Path IDENTITY=Path.of("target/experiments/screw-b-identity-control-20261020-validated"),CANONICAL=Path.of("target/experiments/screw-canonical-m2-qualification-20261020");
    static final Path HEAD=Path.of("target/experiments/screw-head-response-analysis-20261020/head-support.json");
    record CalCase(String image,RigidTransform pose,String sourceSha256,String identityTensorSha256) { }
    private ScrewCalInputs() { }
    static JsonNode read(Path root,String file) throws Exception {return ComparisonArtifacts.JSON.readTree(root.resolve(file).toFile());}
    static void json(Path root,String file,Object value) throws Exception {ComparisonArtifacts.json(root.resolve(file),value);}
    static List<CalCase> cases(Path root) throws Exception {return List.of(ComparisonArtifacts.JSON.readValue(root.resolve("cal-cases.json").toFile(),CalCase[].class));}
    static ScrewCalAccess access(Path root) throws Exception {var paths=new ArrayList<Path>();for(var e:read(root,"input-hashes.json").properties())paths.add(Path.of(e.getKey()));return new ScrewCalAccess(paths);}
    static Path base(String variant){return switch(variant){case "identity"->IDENTITY;case "canonical"->CANONICAL;default->throw new IllegalArgumentException("Unknown model");};}
    static Path bundle(String variant){return base(variant).resolve(variant.equals("identity")?"screw-b-identity.m2":"screw.canonical-m2");}
    static M2Model model(String variant,ScrewCalAccess access) throws Exception {
        Path path=access.require(bundle(variant));return variant.equals("identity")?ScrewIdentityBundle.read(path,M2ModelStore.encoderHash()):ScrewQualificationBundle.read(path,M2ModelStore.encoderHash());
    }
    static ScrewHeadSupport.Bands bands(Path root) throws Exception {return ComparisonArtifacts.JSON.readValue(root.resolve("head-support.json").toFile(),ScrewHeadSupport.Bands.class);}
    static void prepare(Path root) throws Exception {
        Files.createDirectory(root);var inputPaths=new ArrayList<Path>(List.of(CANONICAL.resolve("config.json"),IDENTITY.resolve("input-hashes.json"),
                IDENTITY.resolve("manifests/calibration-attempted.txt"),IDENTITY.resolve("manifests/calibration-usable.txt"),CANONICAL.resolve("calibration-usable.txt"),
                CANONICAL.resolve("calibration-observations.json"),IDENTITY.resolve("calibration-observations.json"),bundle("identity"),bundle("canonical"),HEAD,
                Path.of("docs/experiments/SCREW_CAL_PHOTOMETRIC_PROTOCOL.md"),Path.of("docs/experiments/M2_ROBUSTNESS_PLAN.md")));
        var git=new ProcessBuilder("git","rev-parse","HEAD").start();String head=new String(git.getInputStream().readAllBytes()).trim();if(git.waitFor()!=0)throw new IllegalStateException("HEAD unavailable");Files.writeString(root.resolve("entry-head.txt"),head,StandardOpenOption.CREATE_NEW);
        for(String variant:List.of("identity","canonical"))for(String suffix:List.of("units","raw","native-z","image-scores"))for(String ext:List.of(".npy",".json"))inputPaths.add(base(variant).resolve("calibration-"+suffix+ext));
        var access=new ScrewCalAccess(inputPaths);var config=access.json(CANONICAL.resolve("config.json"));Path dataset=Path.of(config.path("dataset").asText());
        var attempted=Files.readAllLines(access.require(IDENTITY.resolve("manifests/calibration-attempted.txt")));var usable=Files.readAllLines(access.require(IDENTITY.resolve("manifests/calibration-usable.txt")));ScrewCalAccess.population(attempted,usable);
        if(!NpyMap.sha256(access.require(IDENTITY.resolve("manifests/calibration-usable.txt"))).equals(ScrewIdentityBundle.CAL_SHA)||!usable.equals(Files.readAllLines(access.require(CANONICAL.resolve("calibration-usable.txt")))))throw new IllegalStateException("CAL manifest mismatch");
        var canonical=access.json(CANONICAL.resolve("calibration-observations.json"));var identity=access.json(IDENTITY.resolve("calibration-observations.json"));var expected=access.json(IDENTITY.resolve("input-hashes.json"));var expectedSources=new HashMap<Path,String>();
        for(var e:expected.properties())expectedSources.put(Path.of(e.getKey()).toAbsolutePath().normalize(),e.getValue().asText());
        var cases=new ArrayList<CalCase>();for(String name:usable) {
            JsonNode c=null,i=null;for(var row:canonical)if(row.path("image").asText().equals(name))c=row;for(var row:identity)if(row.path("image").asText().equals(name))i=row;
            Path source=ScrewCalAccess.source(dataset,name);if(c==null||i==null||!ScrewHeadSupport.usable(c)||!i.path("state").asText().equals("OK")||!expectedSources.containsKey(source))throw new IllegalStateException("Missing usable CAL evidence");
            cases.add(new CalCase(name,ScrewHeadSupport.transform(c),expectedSources.get(source),i.path("tensorSha256").asText()));inputPaths.add(source);
        }
        access=new ScrewCalAccess(inputPaths);var hashes=new TreeMap<String,String>();for(Path path:inputPaths)hashes.put(path.toString(),access.hash(path));
        for(var c:cases)if(!access.hash(ScrewCalAccess.source(dataset,c.image)).equals(c.sourceSha256))throw new IllegalStateException("CAL source changed");
        json(root,"input-hashes.json",hashes);json(root,"cal-cases.json",cases);Files.write(root.resolve("calibration-attempted.txt"),attempted,StandardOpenOption.CREATE_NEW);Files.write(root.resolve("calibration-usable.txt"),usable,StandardOpenOption.CREATE_NEW);
        Files.copy(access.require(HEAD),root.resolve("head-support.json"));Files.copy(access.require(Path.of("docs/experiments/SCREW_CAL_PHOTOMETRIC_PROTOCOL.md")),root.resolve("protocol.md"));
        json(root,"config.json",Map.of("dataset",dataset.toString(),"attempted",64,"usable",63,"excluded",List.of("screw_good_179.png"),"geometry_estimation",false,"scope","CAL_NORMAL_ONLY"));
        json(root,"perturbations.json",Map.of("families",ScrewCalPhotometry.Family.values(),"levels",ScrewCalPhotometry.LEVELS,"contrast_midpoint",127.5,"gamma","255*pow(v/255,gamma)","rounding","Math.round(clamp(double,0,255))","stage","logical-default-sRGB-native1024-before-bicubic700","chaining",false));
        var modelHashes=new TreeMap<String,String>();for(String variant:List.of("identity","canonical")) {
            modelHashes.put(variant,access.hash(bundle(variant)));var model=model(variant,access);var local=model.local();var calibration=model.calibration();var stats=new ArrayList<Map<String,Object>>();
            double[] stdMedians=new double[196],betas=calibration.beta();for(int p=0;p<196;p++){double[] std=Arrays.copyOfRange(local.effectiveStd(),p*96,(p+1)*96);stdMedians[p]=ScrewCalPhotometry.stats(std).median();double[] coefficients=std.clone();for(int c=0;c<96;c++)coefficients[c]=1/(96*std[c]*std[c]*betas[p]);stats.add(Map.of("cell",p,"std_median",stdMedians[p],"std_min",Arrays.stream(std).min().orElseThrow(),"alpha",calibration.alpha()[p],"beta",betas[p],"coefficient_median",ScrewCalPhotometry.stats(coefficients).median()));}
            double[] sortedStd=stdMedians.clone(),sortedBeta=betas.clone();Arrays.sort(sortedStd);Arrays.sort(sortedBeta);
            json(root,variant+"-cell-state.json",stats);json(root,variant+"-state-quartiles.json",Map.of("std_lower_quartile",ScrewCalPhotometry.quantile(sortedStd,.25),"beta_lower_quartile",ScrewCalPhotometry.quantile(sortedBeta,.25)));
        }
        if(!modelHashes.get("identity").equals("0ca27deba30f59805ac0e9c6dfe17c4e67abc9c4f63ac6e36d0584080addf26c")||!modelHashes.get("canonical").equals("cdf64b691d790213b9608f42b0f5d4d4006ec7b93267e7a14384f3d39843a0d6"))throw new IllegalStateException("Unexpected model identity");
        var sources=CanonicalM2Integration.snapshot(Path.of("src"));json(root,"source-hashes.json",sources);var copies=new TreeMap<String,String>();
        for(var e:sources.entrySet())if(Path.of(e.getKey()).getFileName().toString().startsWith("ScrewCal")){Path out=root.resolve("workspace").resolve(e.getKey());Files.createDirectories(out.getParent());Files.copy(Path.of(e.getKey()),out);copies.put(out.toString(),e.getValue());}json(root,"source-copy-hashes.json",copies);
        json(root,"freeze.json",Map.of("utc",Instant.now().toString(),"model_hashes",modelHashes,"cal_manifest_sha256",ScrewIdentityBundle.CAL_SHA,"pose_sha256",NpyMap.sha256(root.resolve("cal-cases.json")),"support_sha256",NpyMap.sha256(root.resolve("head-support.json")),"protocol_sha256",NpyMap.sha256(root.resolve("protocol.md")),"perturbation_scoring_started",false,"java",System.getProperty("java.runtime.version")));
        json(root,"prepare-access-audit.json",access.reads());mark(root,"prepare");System.out.println("CAL_PHOTOMETRIC - CAL63 poses/models/protocol frozen; excluded good179 retained");
    }
    static void verify(Path root) throws Exception {
        var access=access(root);for(var e:read(root,"input-hashes.json").properties())if(!access.hash(Path.of(e.getKey())).equals(e.getValue().asText()))throw new IllegalStateException("Changed CAL input: "+e.getKey());
        for(String file:List.of("source-hashes.json","source-copy-hashes.json","prepare-evidence-hashes.json","baseline-evidence-hashes.json","scoring-evidence-hashes.json","summary-evidence-hashes.json"))if(Files.exists(root.resolve(file)))for(var e:read(root,file).properties())if(!NpyMap.sha256(Path.of(e.getKey())).equals(e.getValue().asText()))throw new IllegalStateException("Changed frozen source/output: "+e.getKey());
        if(!read(root,"source-hashes.json").equals(ComparisonArtifacts.JSON.valueToTree(CanonicalM2Integration.snapshot(Path.of("src")))))throw new IllegalStateException("Source set changed");
    }
    static void mark(Path root,String phase) throws Exception {verify(root);var hashes=CanonicalM2Integration.snapshot(root);hashes.keySet().removeIf(p->p.replace('\\','/').contains("/workspace/"));json(root,phase+"-evidence-hashes.json",hashes);}
}
