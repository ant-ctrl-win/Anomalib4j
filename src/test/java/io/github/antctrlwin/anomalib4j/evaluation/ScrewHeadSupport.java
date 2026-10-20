package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.antctrlwin.anomalib4j.canonical.RigidTransform;
import java.util.*;
import java.util.function.ToDoubleFunction;

final class ScrewHeadSupport {
    record Bounds(double minX,double maxX,double minY,double maxY) {
        Bounds {if(!(minX<maxX&&minY<maxY))throw new IllegalArgumentException("Empty diagnostic support");}
        boolean contains(double x,double y){return x>=minX&&x<maxX&&y>=minY&&y<maxY;}
    }
    record Bands(Bounds head,Bounds neck,Bounds thread) {
        String region(double x,double y){return head.contains(x,y)?"head":neck.contains(x,y)?"neck":thread.contains(x,y)?"thread":"other";}
        Bounds named(String region){return switch(region){case "head"->head;case "neck"->neck;case "thread"->thread;default->throw new IllegalArgumentException("Unknown band");};}
    }
    record Summary(int n,double min,double p05,double median,double p95,double max,double mean) { }
    record Decomposition(double raw,double alpha,double beta,double delta) { }
    private ScrewHeadSupport() { }
    static Summary summary(double[] values) {
        var d=ScrewFraming.distribution(values);return new Summary(d.n(),d.min(),d.p05(),d.p50(),d.p95(),d.max(),d.mean());
    }
    static double[] values(JsonNode rows,ToDoubleFunction<JsonNode> field) {
        var result=new ArrayList<Double>();for(var row:rows)if(usable(row))result.add(field.applyAsDouble(row));
        return result.stream().mapToDouble(Double::doubleValue).toArray();
    }
    static boolean usable(JsonNode row){return row.path("preprocessing").path("pose").path("state").asText().equals("OK");}
    static JsonNode support(JsonNode row){return row.path("preprocessing").path("support").path("support");}
    static Bands define(JsonNode fit) {
        double left=summary(values(fit,r->support(r).path("minX").asDouble())).median;
        double right=summary(values(fit,r->support(r).path("maxX").asDouble())).median;
        double top=summary(values(fit,r->support(r).path("minY").asDouble())).median;
        double bottom=summary(values(fit,r->support(r).path("maxY").asDouble())).median;
        double half=(left+right)/2,quarter=left+.75*(right-left);
        return new Bands(new Bounds(quarter,right,top,bottom),new Bounds(half,quarter,top,bottom),new Bounds(left,half,top,bottom));
    }
    static RigidTransform transform(JsonNode row) {
        var a=row.path("preprocessing").path("transform");return new RigidTransform(a.path("cos").asDouble(),a.path("sin").asDouble(),a.path("tx").asDouble(),a.path("ty").asDouble());
    }
    static double center(int cell){return (cell+.5)*50-.5;}
    static int cell(double x,double y) {
        int col=(int)Math.floor((x+.5)/50),row=(int)Math.floor((y+.5)/50);
        return col<0||col>=14||row<0||row>=14?-1:row*14+col;
    }
    static String region(Bands bands,RigidTransform semantic,int cell) {
        double x=center(cell%14),y=center(cell/14);return bands.region(semantic.canonicalX(x,y),semantic.canonicalY(x,y));
    }
    static int[] cells(Bands bands,RigidTransform semantic,String band) {
        return java.util.stream.IntStream.range(0,196).filter(p->region(bands,semantic,p).equals(band)).toArray();
    }
    static Decomposition decompose(double[] identity,double[] canonical) {
        double ri=identity[0],ai=identity[1],bi=identity[2],rc=canonical[0],ac=canonical[1],bc=canonical[2];
        if(bi<=0||bc<=0)throw new IllegalArgumentException("Positive beta required");
        return new Decomposition((rc-ri)/bi,-(ac-ai)/bi,(rc-ac)*(1/bc-1/bi),(rc-ac)/bc-(ri-ai)/bi);
    }
    static int[] top(double[] z) {
        return java.util.stream.IntStream.range(0,z.length).boxed().sorted(Comparator.<Integer>comparingDouble(p->z[p]).reversed().thenComparingInt(p->p)).mapToInt(Integer::intValue).limit(5).toArray();
    }
    static Map<String,Object> response(double[] z,int[] head) {
        if(z.length!=196||head.length==0)throw new IllegalArgumentException("Native14 and nonempty head required");
        int global=ScrewIdentityControl.firstArgmax(z),best=head[0];for(int p:head)if(z[p]>z[best])best=p;
        int rank=1;for(double v:z)if(v>z[best])rank++;
        return Map.of("global_max",z[global],"argmax",global,"head_max",z[best],"head_argmax",best,"head_rank",rank,
                "head_minus_global",z[best]-z[global],"top5_cells",top(z),"top5_z",Arrays.stream(top(z)).mapToDouble(p->z[p]).toArray());
    }
    static double[] row(NpyMap.Map array,int row) {return Arrays.copyOfRange(array.values(),row*array.columns(),(row+1)*array.columns());}
}
