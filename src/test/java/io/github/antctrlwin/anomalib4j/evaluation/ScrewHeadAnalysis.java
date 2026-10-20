package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class ScrewHeadAnalysis {
    static final Path CANONICAL=Path.of("target/experiments/screw-canonical-m2-qualification-20261020");
    static final Path IDENTITY=Path.of("target/experiments/screw-b-identity-control-20261020-validated");
    private ScrewHeadAnalysis() { }
    static JsonNode read(Path root,String file) throws Exception {return ComparisonArtifacts.JSON.readTree(root.resolve(file).toFile());}
    static void json(Path root,String file,Object value) throws Exception {ComparisonArtifacts.json(root.resolve(file),value);}
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[1]);switch(args[0]) {
            case "prepare"->prepare(root);
            case "analyze"->analyze(root);
            case "resample"->ScrewHeadPixels.resample(root);
            case "verify"->{verify(root);System.out.println("HEAD_DIAGNOSTIC - all source/input/output guards verified");}
            default->throw new IllegalArgumentException("Expected prepare/analyze/resample/verify");
        }
    }
    private static void prepare(Path root) throws Exception {
        Files.createDirectory(root);var source=CanonicalM2Integration.snapshot(Path.of("src"));json(root,"source-hashes.json",source);
        for(var e:read(IDENTITY,"source-hashes.json").properties())if(!e.getValue().asText().equals(source.get(e.getKey())))throw new IllegalStateException("Prior source changed");
        for(var e:source.entrySet()){Path p=root.resolve("workspace").resolve(e.getKey());Files.createDirectories(p.getParent());Files.copy(Path.of(e.getKey()),p);}
        var inputs=CanonicalM2Integration.snapshot(CANONICAL);inputs.putAll(CanonicalM2Integration.snapshot(IDENTITY));
        for(String manifest:List.of("input-hashes.json","test-input-hashes.json"))for(var e:read(IDENTITY,manifest).properties())inputs.put(e.getKey(),e.getValue().asText());
        Path protocol=Path.of("docs/experiments/SCREW_HEAD_RESPONSE_PROTOCOL.md");inputs.put(protocol.toString(),NpyMap.sha256(protocol));Files.copy(protocol,root.resolve("protocol.md"));
        json(root,"input-hashes.json",inputs);
        var fit=read(CANONICAL,"fit-observations.json");var bands=ScrewHeadSupport.define(fit);json(root,"head-support.json",bands);
        for(String role:List.of("fit","calibration","test"))Files.copy(IDENTITY.resolve("manifests/"+role+"-usable.txt"),root.resolve(role+"-usable.txt"));
        var reps=new ArrayList<String>();for(String role:List.of("calibration","test")) {
            var names=Files.readAllLines(root.resolve(role+"-usable.txt")).stream().filter(n->role.equals("calibration")||EvaluationLabels.defect(n).equals("scratch_head")).sorted().toList();
            for(int i=0;i<4;i++)reps.add(names.get(i*(names.size()-1)/3));
        }
        json(root,"representatives.json",reps);json(root,"identity-normal-statistics.json",ScrewHeadState.normal(IDENTITY,fit,bands));json(root,"canonical-normal-statistics.json",ScrewHeadState.normal(CANONICAL,fit,bands));
        var modelHashes=Map.of("identity",NpyMap.sha256(IDENTITY.resolve("screw-b-identity.m2")),"canonical",NpyMap.sha256(CANONICAL.resolve("screw.canonical-m2")));
        var git=new ProcessBuilder("git","rev-parse","HEAD").start();String head=new String(git.getInputStream().readAllBytes()).trim();if(git.waitFor()!=0)throw new IllegalStateException("HEAD unavailable");
        json(root,"normal-freeze.json",Map.of("utc",Instant.now().toString(),"entry_head",head,"model_hashes",modelHashes,"support_sha256",NpyMap.sha256(root.resolve("head-support.json")),
                "protocol_sha256",NpyMap.sha256(protocol),"representatives_sha256",NpyMap.sha256(root.resolve("representatives.json")),"test_responses_read",false,"test_gt_read",false));
        mark(root,"normal");System.out.println("HEAD_DIAGNOSTIC - normal support/state/representatives frozen before TEST analysis");
    }
    static void verify(Path root) throws Exception {
        for(String f:List.of("source-hashes.json","input-hashes.json","normal-evidence-hashes.json","analysis-evidence-hashes.json","resampling-evidence-hashes.json"))if(Files.exists(root.resolve(f)))for(var e:read(root,f).properties())
            if(!NpyMap.sha256(Path.of(e.getKey())).equals(e.getValue().asText()))throw new IllegalStateException("Changed source/evidence "+e.getKey());
        if(!read(root,"source-hashes.json").equals(ComparisonArtifacts.JSON.valueToTree(CanonicalM2Integration.snapshot(Path.of("src")))))throw new IllegalStateException("Source set changed");
        for(var e:read(root,"source-hashes.json").properties())if(!NpyMap.sha256(root.resolve("workspace").resolve(e.getKey())).equals(e.getValue().asText()))throw new IllegalStateException("Source copy changed");
    }
    static void mark(Path root,String phase) throws Exception {verify(root);var hashes=CanonicalM2Integration.snapshot(root);hashes.keySet().removeIf(p->p.replace('\\','/').contains("/workspace/"));json(root,phase+"-evidence-hashes.json",hashes);}
    static ScrewHeadSupport.Bands bands(Path root) throws Exception {return ComparisonArtifacts.JSON.readValue(root.resolve("head-support.json").toFile(),ScrewHeadSupport.Bands.class);}
    private static void analyze(Path root) throws Exception {
        if(!Files.exists(root.resolve("normal-evidence-hashes.json")))throw new IllegalStateException("Normal freeze required");verify(root);
        var bands=bands(root);var names=Files.readAllLines(root.resolve("test-usable.txt"));var obs=read(CANONICAL,"test-observations.json");
        var iz=NpyMap.read(IDENTITY.resolve("test-native-z.npy"));var cz=NpyMap.read(CANONICAL.resolve("test-native-z.npy"));
        var ir=NpyMap.read(IDENTITY.resolve("test-raw.npy"));var cr=NpyMap.read(CANONICAL.resolve("test-raw.npy"));var im=ScrewHeadState.model(IDENTITY);var cm=ScrewHeadState.model(CANONICAL);
        var responses=new ArrayList<Map<String,Object>>();var decompositions=new ArrayList<Map<String,Object>>();var poses=new ArrayList<Map<String,Object>>();var originals=new ArrayList<Map<String,Object>>();
        Path dataset=Path.of(read(CANONICAL,"config.json").path("dataset").asText());int i=0;
        for(var row:obs) {
            String image=row.path("image").asText(),defect=row.path("defect").asText();if(!image.equals(names.get(i)))throw new IllegalStateException("TEST order mismatch");
            var a=ScrewHeadSupport.transform(row);var mask=DatasetNinjaMasks.read(dataset.resolve("test/ann/"+image+".json"),1024,1024,defect.equals("good"));
            for(String variant:List.of("identity","canonical")) {
                boolean identity=variant.equals("identity");var semantic=identity?a:ScrewIdentityControl.IDENTITY;double[] z=ScrewHeadSupport.row(identity?iz:cz,i),raw=ScrewHeadSupport.row(identity?ir:cr,i);
                var record=new LinkedHashMap<String,Object>();record.put("image",image);record.put("defect",defect);record.put("variant",variant);
                record.putAll(ScrewHeadSupport.response(z,ScrewHeadSupport.cells(bands,semantic,"head")));int max=ScrewIdentityControl.firstArgmax(z);record.put("max_origin",ScrewHeadSupport.region(bands,semantic,max));
                for(String region:List.of("head","neck","thread")) {int[] selected=ScrewHeadSupport.cells(bands,semantic,region);record.put(region+"_raw_max",Arrays.stream(selected).mapToDouble(p->raw[p]).max().orElseThrow());}
                boolean[] overlap=ScrewHeadPixels.nativeOverlap(mask,identity?ScrewIdentityControl.IDENTITY:a);var overlapValues=new ArrayList<Double>();var headOverlap=new ArrayList<Double>();
                for(int p=0;p<196;p++)if(overlap[p]){overlapValues.add(z[p]);if(ScrewHeadSupport.region(bands,semantic,p).equals("head"))headOverlap.add(z[p]);}
                record.put("gt_overlap_cells",overlapValues.size());record.put("head_gt_cells",headOverlap.size());
                record.put("gt_overlap_z",overlapValues.isEmpty()?null:ScrewHeadSupport.summary(overlapValues.stream().mapToDouble(Double::doubleValue).toArray()));
                record.put("head_gt_z",headOverlap.isEmpty()?null:ScrewHeadSupport.summary(headOverlap.stream().mapToDouble(Double::doubleValue).toArray()));responses.add(record);
                if(!defect.equals("good")){Path directory=identity?IDENTITY:CANONICAL;var full=NpyMap.read(directory.resolve("evaluation/maps/"+image.substring(0,image.length()-4)+".evaluated.npy"));
                    var original=new LinkedHashMap<String,Object>();original.put("image",image);original.put("defect",defect);original.put("variant",variant);original.putAll(ScrewHeadPixels.original(full.values(),mask));originals.add(original);}
            }
            for(int p:ScrewHeadSupport.cells(bands,ScrewIdentityControl.IDENTITY,"head")) {
                int q=ScrewHeadSupport.cell(a.sourceX(ScrewHeadSupport.center(p%14),ScrewHeadSupport.center(p/14)),a.sourceY(ScrewHeadSupport.center(p%14),ScrewHeadSupport.center(p/14)));
                if(q<0)throw new IllegalStateException("Head pairing outside identity view");double[] v={ir.values()[i*196+q],im.alpha()[q],im.beta()[q]},w={cr.values()[i*196+p],cm.alpha()[p],cm.beta()[p]};
                var d=ScrewHeadSupport.decompose(v,w);if(Math.abs(d.delta()-d.raw()-d.alpha()-d.beta())>1e-10)throw new IllegalStateException("Calibration identity failed");
                decompositions.add(Map.of("image",image,"defect",defect,"canonical_cell",p,"identity_cell",q,"identity_raw_alpha_beta",v,"canonical_raw_alpha_beta",w,"identity_z",iz.values()[i*196+q],"canonical_z",cz.values()[i*196+p],"components",d));
            }
            var pose=new LinkedHashMap<String,Object>();pose.put("image",image);pose.put("role","test");pose.put("defect",defect);pose.putAll(ScrewHeadState.pose(row));poses.add(pose);i++;
            if(i%40==0)System.out.println("HEAD_DIAGNOSTIC - retained TEST analyzed "+i+"/160");
        }
        for(String role:List.of("fit","calibration"))for(var row:read(CANONICAL,role+"-observations.json"))if(ScrewHeadSupport.usable(row)) {
            var pose=new LinkedHashMap<String,Object>();pose.put("image",row.path("image").asText());pose.put("role",role);pose.put("defect","good");pose.putAll(ScrewHeadState.pose(row));poses.add(pose);
        }
        json(root,"native-responses.json",responses);json(root,"calibration-decomposition.json",decompositions);json(root,"original-response.json",originals);json(root,"pose-records.json",poses);
        json(root,"native-summary.json",summaries(responses,"defect","variant"));json(root,"original-summary.json",summaries(originals,"defect","variant"));json(root,"pose-summary.json",summaries(poses,"role","defect"));
        var expanded=new ArrayList<Map<String,Object>>();for(var d:decompositions){var v=new LinkedHashMap<String,Object>(d);v.putAll(ComparisonArtifacts.JSON.convertValue(d.get("components"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));expanded.add(v);}
        json(root,"calibration-summary.json",summaries(expanded,"defect","defect"));mark(root,"analysis");
    }
    static List<Map<String,Object>> summaries(List<Map<String,Object>> records,String first,String second) {
        var nodes=ComparisonArtifacts.JSON.valueToTree(records);var groups=new TreeMap<String,List<JsonNode>>();
        for(var row:nodes)groups.computeIfAbsent(row.path(first).asText()+"/"+row.path(second).asText(),k->new ArrayList<>()).add(row);
        var result=new ArrayList<Map<String,Object>>();for(var entry:groups.entrySet()) {
            var out=new LinkedHashMap<String,Object>();out.put("group",entry.getKey());out.put("n",entry.getValue().size());var fields=new TreeSet<String>();for(var row:entry.getValue())row.fieldNames().forEachRemaining(fields::add);
            for(String field:fields) {var values=new ArrayList<Double>();var counts=new TreeMap<String,Integer>();
                for(var row:entry.getValue()){var value=row.path(field);if(value.isNumber())values.add(value.asDouble());else if(value.isBoolean()||field.equals("max_origin"))counts.merge(value.asText(),1,Integer::sum);}
                if(!values.isEmpty())out.put(field,ScrewHeadSupport.summary(values.stream().mapToDouble(Double::doubleValue).toArray()));if(!counts.isEmpty())out.put(field,counts);
            }result.add(out);
        }return result;
    }
}
