package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;

final class ScrewHeadState {
    record Model(double[] mean,double[] std,double[] alpha,double[] beta) { }
    private ScrewHeadState() { }
    static Model model(Path root) throws Exception {
        return new Model(NpyMap.read(root.resolve("local-mean.npy")).values(),NpyMap.read(root.resolve("local-sample-std.npy")).values(),
                NpyMap.read(root.resolve("alpha.npy")).values(),NpyMap.read(root.resolve("beta-effective-std.npy")).values());
    }
    static Map<String,Double> cell(Model model,int p) {
        double norm=0;double[] s=new double[96],coefficient=new double[96];
        for(int c=0;c<96;c++){int k=p*96+c;norm+=model.mean[k]*model.mean[k];s[c]=model.std[k];coefficient[c]=1/(96*Math.max(s[c],1e-6)*Math.max(s[c],1e-6)*model.beta[p]);}
        return Map.of("mean_norm",Math.sqrt(norm),"sample_std_median",ScrewHeadSupport.summary(s).median(),
                "alpha",model.alpha[p],"beta",model.beta[p],"z_gain",1/model.beta[p],"residual_coefficient_median",ScrewHeadSupport.summary(coefficient).median());
    }
    static Map<String,Object> normal(Path root,JsonNode fit,ScrewHeadSupport.Bands bands) throws Exception {
        var output=new LinkedHashMap<String,Object>();var model=model(root);boolean identity=root.getFileName().toString().contains("identity");
        var perCell=new ArrayList<Map<String,Object>>();
        for(String region:List.of("head","neck","thread")) {
            int[] weights=new int[196];
            for(var row:fit)if(ScrewHeadSupport.usable(row))for(int p:ScrewHeadSupport.cells(bands,identity?ScrewHeadSupport.transform(row):ScrewIdentityControl.IDENTITY,region))weights[p]++;
            var features=new TreeMap<String,List<Double>>();for(String key:List.of("mean_norm","sample_std","alpha","beta","z_gain","residual_coefficient"))features.put(key,new ArrayList<>());
            for(int p=0;p<196;p++)if(weights[p]>0) {
                var stats=cell(model,p);var row=new LinkedHashMap<String,Object>();row.put("region",region);row.put("cell",p);row.put("fit_membership_count",weights[p]);row.putAll(stats);perCell.add(row);
                for(int i=0;i<weights[p];i++) {
                    for(String key:List.of("mean_norm","alpha","beta","z_gain"))features.get(key).add(stats.get(key));
                    for(int c=0;c<96;c++){double s=model.std[p*96+c];features.get("sample_std").add(s);features.get("residual_coefficient").add(1/(96*Math.max(s,1e-6)*Math.max(s,1e-6)*model.beta[p]));}
                }
            }
            var summary=new LinkedHashMap<String,Object>();summary.put("cell_weights",weights);
            for(var entry:features.entrySet())summary.put(entry.getKey(),ScrewHeadSupport.summary(entry.getValue().stream().mapToDouble(Double::doubleValue).toArray()));
            for(String role:List.of("fit","calibration")) {
                var raw=NpyMap.read(root.resolve(role+"-raw.npy"));var z=NpyMap.read(root.resolve(role+"-native-z.npy"));
                JsonNode observations=ScrewHeadAnalysis.read(ScrewHeadAnalysis.CANONICAL,role+"-observations.json");
                var rs=new ArrayList<Double>();var zs=new ArrayList<Double>();var maxima=new ArrayList<Double>();int index=0;
                for(var row:observations)if(ScrewHeadSupport.usable(row)) {
                    int[] selected=ScrewHeadSupport.cells(bands,identity?ScrewHeadSupport.transform(row):ScrewIdentityControl.IDENTITY,region);double max=Double.NEGATIVE_INFINITY;
                    for(int p:selected){rs.add(raw.values()[index*196+p]);double value=z.values()[index*196+p];zs.add(value);max=Math.max(max,value);}index++;
                    if(selected.length>0)maxima.add(max);
                }
                if(index!=raw.rows())throw new IllegalStateException("Normal row alignment mismatch");
                summary.put(role+"_raw",ScrewHeadSupport.summary(rs.stream().mapToDouble(Double::doubleValue).toArray()));
                summary.put(role+"_z",ScrewHeadSupport.summary(zs.stream().mapToDouble(Double::doubleValue).toArray()));
                summary.put(role+"_band_max_z",ScrewHeadSupport.summary(maxima.stream().mapToDouble(Double::doubleValue).toArray()));
            }
            output.put(region,summary);
        }
        output.put("per_cell",perCell);return output;
    }
    static Map<String,Object> pose(JsonNode row) {
        var b=ScrewHeadSupport.support(row);var a=ScrewHeadSupport.transform(row);var p=row.path("preprocessing").path("pose");
        double x=b.path("maxX").asDouble(),y=(b.path("minY").asDouble()+b.path("maxY").asDouble())/2;
        double sx=a.sourceX(x,y),sy=a.sourceY(x,y);
        return Map.ofEntries(Map.entry("canonical_head_x",x),Map.entry("canonical_head_y",y),Map.entry("identity_head_x",sx),Map.entry("identity_head_y",sy),
                Map.entry("canonical_head_column",(x+.5)/50-.5),Map.entry("canonical_head_row",(y+.5)/50-.5),
                Map.entry("identity_head_column",(sx+.5)/50-.5),Map.entry("identity_head_row",(sy+.5)/50-.5),
                Map.entry("head_canvas_margin",699-x),Map.entry("canonical_head_extent_y",b.path("maxY").asDouble()-b.path("minY").asDouble()),
                Map.entry("anisotropy",p.path("anisotropy").asDouble()),Map.entry("abs_polarity",Math.abs(p.path("polarity").asDouble())),Map.entry("angle",p.path("angle").asDouble()));
    }
}
