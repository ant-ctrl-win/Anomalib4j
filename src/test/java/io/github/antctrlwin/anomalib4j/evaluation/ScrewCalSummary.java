package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import java.util.function.ToDoubleFunction;

final class ScrewCalSummary {
    private ScrewCalSummary() { }
    private static ScrewCalPhotometry.Stats stats(List<JsonNode> rows,ToDoubleFunction<JsonNode> field){return ScrewCalPhotometry.stats(rows.stream().mapToDouble(field).toArray());}
    private static List<JsonNode> rows(JsonNode node){var result=new ArrayList<JsonNode>();node.forEach(result::add);return result;}
    private static String key(JsonNode r){return r.path("family").asText()+"/"+r.path("level").asDouble()+"/"+r.path("image").asText();}
    static Map<String,Object> pair(JsonNode i,JsonNode c) {
        double di=i.path("delta_max_z").asDouble(),dc=c.path("delta_max_z").asDouble(),ri=i.path("raw_growth_at_winner").asDouble(),rc=c.path("raw_growth_at_winner").asDouble(),bi=i.path("beta_at_winner").asDouble(),bc=c.path("beta_at_winner").asDouble();
        double raw=(rc-ri)/bi,scale=rc*(1/bc-1/bi),switching=c.path("winner_switch_penalty").asDouble()-i.path("winner_switch_penalty").asDouble();
        if(Math.abs(dc-di-raw-scale-switching)>1e-10)throw new IllegalStateException("Paired amplification identity mismatch");
        var out=new LinkedHashMap<String,Object>();out.put("image",i.path("image").asText());out.put("family",i.path("family").asText());out.put("level",i.path("level").asDouble());out.put("amplification",dc-di);out.put("raw_term",raw);out.put("scaling_term",scale);out.put("winner_switch_term",switching);
        for(String field:List.of("max_z","baseline_max_z","delta_max_z","max_raw","raw_at_winner","alpha_at_winner","beta_at_winner","max_cell","top5_positive_growth_share","positive_effective_participation")){out.put("identity_"+field,i.path(field).asDouble());out.put("canonical_"+field,c.path(field).asDouble());}
        out.put("identity_origin",i.path("origin").asText());out.put("canonical_origin",c.path("origin").asText());return out;
    }
    static void run(Path root) throws Exception {
        if(!Files.exists(root.resolve("scoring-evidence-hashes.json")))throw new IllegalStateException("Frozen scoring phase required");ScrewCalInputs.verify(root);
        var all=rows(ScrewCalInputs.read(root,"baseline-records.json"));all.addAll(rows(ScrewCalInputs.read(root,"perturbed-records.json")));
        var groups=new TreeMap<String,List<JsonNode>>();var identities=new HashMap<String,JsonNode>();
        for(var row:all){groups.computeIfAbsent(row.path("family").asText()+"/"+row.path("level").asDouble()+"/"+row.path("variant").asText(),k->new ArrayList<>()).add(row);if(row.path("variant").asText().equals("identity"))identities.put(key(row),row);}
        var responses=new ArrayList<Map<String,Object>>();var paired=new ArrayList<Map<String,Object>>();
        for(var entry:groups.entrySet()) {
            var out=new LinkedHashMap<String,Object>();out.put("condition",entry.getKey());out.put("images",entry.getValue().size());
            for(String field:List.of("max_z","max_raw","delta_max_z","delta_max_raw","raw_growth_at_winner","same_cell_z_growth","winner_switch_penalty","beta_at_winner","top5_positive_growth_share","positive_effective_participation","clipped_channels"))out.put(field,stats(entry.getValue(),r->r.path(field).asDouble()));
            var origins=new TreeMap<String,Integer>();for(var r:entry.getValue())origins.merge(r.path("origin").asText(),1,Integer::sum);out.put("origins",origins);
            for(int level:new int[]{3,5,10}){String f="cells_above_"+level;out.put(f,stats(entry.getValue(),r->r.path(f).asDouble()));out.put("fraction_any_above_"+level,entry.getValue().stream().filter(r->r.path(f).asLong()>0).count()/63.0);}
            responses.add(out);
        }
        for(var row:all)if(row.path("variant").asText().equals("canonical"))paired.add(pair(identities.get(key(row)),row));
        var pairNodes=rows(ComparisonArtifacts.JSON.valueToTree(paired));var pairGroups=new TreeMap<String,List<JsonNode>>();for(var r:pairNodes)pairGroups.computeIfAbsent(r.path("family").asText()+"/"+r.path("level").asDouble(),k->new ArrayList<>()).add(r);
        var pairSummary=new ArrayList<Map<String,Object>>();for(var entry:pairGroups.entrySet()) {
            var out=new LinkedHashMap<String,Object>();out.put("condition",entry.getKey());for(String field:List.of("amplification","raw_term","scaling_term","winner_switch_term","identity_delta_max_z","canonical_delta_max_z"))out.put(field,stats(entry.getValue(),r->r.path(field).asDouble()));
            out.put("canonical_absolute_change_larger",entry.getValue().stream().filter(r->Math.abs(r.path("canonical_delta_max_z").asDouble())>Math.abs(r.path("identity_delta_max_z").asDouble())).count());out.put("identity_absolute_change_larger",entry.getValue().stream().filter(r->Math.abs(r.path("identity_delta_max_z").asDouble())>Math.abs(r.path("canonical_delta_max_z").asDouble())).count());out.put("positive_signed_amplification",entry.getValue().stream().filter(r->r.path("amplification").asDouble()>0).count());pairSummary.add(out);
        }
        var stability=new ArrayList<Map<String,Object>>();var worst=new ArrayList<JsonNode>();
        for(var family:ScrewCalPhotometry.Family.values()) {
            var byImage=new TreeMap<String,List<JsonNode>>();for(var r:pairNodes)if(r.path("family").asText().equals(family.name())&&r.path("level").asDouble()!=1)byImage.computeIfAbsent(r.path("image").asText(),k->new ArrayList<>()).add(r);
            int canonicalLarger=0,identityLarger=0,ties=0;var selected=new ArrayList<JsonNode>();
            for(var entry:byImage.entrySet()){double c=entry.getValue().stream().mapToDouble(r->Math.abs(r.path("canonical_delta_max_z").asDouble())).max().orElseThrow(),i=entry.getValue().stream().mapToDouble(r->Math.abs(r.path("identity_delta_max_z").asDouble())).max().orElseThrow();if(c>i)canonicalLarger++;else if(i>c)identityLarger++;else ties++;selected.add(entry.getValue().stream().max(Comparator.comparingDouble(r->r.path("amplification").asDouble())).orElseThrow());}
            selected.sort(Comparator.comparingDouble((JsonNode r)->r.path("amplification").asDouble()).reversed().thenComparing(r->r.path("image").asText()));worst.addAll(selected.subList(0,5));stability.add(Map.of("family",family.name(),"images",byImage.size(),"canonical_peak_absolute_change_larger",canonicalLarger,"identity_peak_absolute_change_larger",identityLarger,"ties",ties));
        }
        ScrewCalInputs.json(root,"response-summary.json",responses);ScrewCalInputs.json(root,"paired-records.json",paired);ScrewCalInputs.json(root,"paired-summary.json",pairSummary);ScrewCalInputs.json(root,"family-stability.json",stability);ScrewCalInputs.json(root,"worst-five-per-family.json",worst);
        sensitivities(root,all);ScrewCalInputs.mark(root,"summary");System.out.println("CAL_PHOTOMETRIC - all family/level pairs, slopes, state ranks and worst cases summarized");
    }
    private static void sensitivities(Path root,List<JsonNode> records) throws Exception {
        var slopes=new ArrayList<Map<String,Object>>();var slopeSummaries=new ArrayList<Map<String,Object>>();var cells=new ArrayList<Map<String,Object>>();var relationships=new ArrayList<Map<String,Object>>();var buckets=new ArrayList<Map<String,Object>>();
        for(var family:ScrewCalPhotometry.Family.values())for(String variant:List.of("identity","canonical")) {
            var lo=NpyMap.read(ScrewCalNuisance.condition(root,family,.9,variant).resolve("z.npy"));var zero=NpyMap.read(ScrewCalNuisance.condition(root,family,1,variant).resolve("z.npy"));var hi=NpyMap.read(ScrewCalNuisance.condition(root,family,1.1,variant).resolve("z.npy"));var names=Files.readAllLines(root.resolve("calibration-usable.txt"));
            var selected=records.stream().filter(r->r.path("family").asText().equals(family.name())&&r.path("variant").asText().equals(variant)).toList();
            var central=new double[63];var oneSided=new double[63];
            for(int i=0;i<63;i++){String image=names.get(i);double a=0,b=0,c=0;for(var r:selected)if(r.path("image").asText().equals(image)){double level=r.path("level").asDouble();if(level==.9)a=r.path("max_z").asDouble();else if(level==1)b=r.path("max_z").asDouble();else if(level==1.1)c=r.path("max_z").asDouble();}central[i]=(c-a)/.2;oneSided[i]=Math.max(Math.abs(a-b),Math.abs(c-b))/.1;slopes.add(Map.of("family",family.name(),"variant",variant,"image",image,"centered_slope",central[i],"max_absolute_one_sided",oneSided[i]));}
            slopeSummaries.add(Map.of("family",family.name(),"variant",variant,"centered_slope",ScrewCalPhotometry.stats(central),"max_absolute_one_sided",ScrewCalPhotometry.stats(oneSided)));
            var state=ScrewCalInputs.read(root,variant+"-cell-state.json");var quartiles=ScrewCalInputs.read(root,variant+"-state-quartiles.json");double[] sensitivity=new double[196],std=new double[196],beta=new double[196],coefficient=new double[196];
            for(int p=0;p<196;p++){double sum=0,absCentral=0;for(int i=0;i<63;i++){int at=i*196+p;sum+=Math.max(Math.abs(lo.values()[at]-zero.values()[at]),Math.abs(hi.values()[at]-zero.values()[at]))/.1;absCentral+=Math.abs(hi.values()[at]-lo.values()[at])/.2;}sensitivity[p]=sum/63;std[p]=state.get(p).path("std_median").asDouble();beta[p]=state.get(p).path("beta").asDouble();coefficient[p]=state.get(p).path("coefficient_median").asDouble();cells.add(Map.of("family",family.name(),"variant",variant,"cell",p,"mean_max_absolute_one_sided",sensitivity[p],"mean_abs_centered_slope",absCentral/63,"std_median",std[p],"beta",beta[p],"coefficient_median",coefficient[p]));}
            var relation=new LinkedHashMap<String,Object>();relation.put("family",family.name());relation.put("variant",variant);relation.put("cells",196);relation.put("spearman_std",ScrewCalPhotometry.spearman(sensitivity,std));relation.put("spearman_beta",ScrewCalPhotometry.spearman(sensitivity,beta));relation.put("spearman_coefficient",ScrewCalPhotometry.spearman(sensitivity,coefficient));relationships.add(relation);
            var counts=new TreeMap<String,Integer>();for(var r:selected)if(r.path("level").asDouble()!=1){int p=r.path("max_cell").asInt();boolean lowStd=std[p]<=quartiles.path("std_lower_quartile").asDouble(),lowBeta=beta[p]<=quartiles.path("beta_lower_quartile").asDouble();counts.merge(lowStd?(lowBeta?"both":"std_only"):(lowBeta?"beta_only":"neither"),1,Integer::sum);}buckets.add(Map.of("family",family.name(),"variant",variant,"winning_cells",counts));
        }
        ScrewCalInputs.json(root,"sensitivity-records.json",slopes);ScrewCalInputs.json(root,"sensitivity-summary.json",slopeSummaries);ScrewCalInputs.json(root,"cell-sensitivity.json",cells);ScrewCalInputs.json(root,"state-sensitivity-relationships.json",relationships);ScrewCalInputs.json(root,"winning-state-quartiles.json",buckets);
    }
}
