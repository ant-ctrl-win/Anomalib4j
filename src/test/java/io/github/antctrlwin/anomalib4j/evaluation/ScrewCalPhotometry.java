package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.canonical.*;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.util.*;

final class ScrewCalPhotometry {
    enum Family {BRIGHTNESS,CONTRAST,GAMMA}
    static final double[] LEVELS={.8,.9,1,1.1,1.2};
    record Perturbed(int[] rgb,int clippedChannels) { }
    record Stats(int n,double min,double median,double p90,double p95,double max,double mean) { }
    private ScrewCalPhotometry() { }
    static Perturbed perturb(int[] logical,Family family,double level) {
        if(!Arrays.stream(LEVELS).anyMatch(v->v==level))throw new IllegalArgumentException("Unfrozen photometric level");
        int[] out=new int[logical.length];int clipped=0;
        for(int p=0;p<logical.length;p++)for(int shift:new int[]{16,8,0}) {
            double value=(logical[p]>>>shift)&255;
            value=switch(family){case BRIGHTNESS->value*level;case CONTRAST->127.5+(value-127.5)*level;case GAMMA->255*Math.pow(value/255,level);};
            if(value<0||value>255)clipped++;out[p]|=(int)Math.round(Math.max(0,Math.min(255,value)))<<shift;
        }return new Perturbed(out,clipped);
    }
    static int[] view(int[] logical) {
        if(logical.length!=1024*1024)throw new IllegalArgumentException("Native1024 required");
        var rgb=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_RGB);rgb.setRGB(0,0,1024,1024,logical,0,1024);
        try{return GeneralityImages.view(rgb);}finally{rgb.flush();}
    }
    static float[] tensor(int[] view,RigidTransform frozenPose) {
        float[] out=new float[AffineTensorSampler.ELEMENTS];new AffineTensorSampler(AffineTensorSampler.Kernel.BILINEAR).sample(view,frozenPose,out);return out;
    }
    static String hash(float[] tensor) throws Exception {var bytes=ByteBuffer.allocate(tensor.length*4).order(ByteOrder.LITTLE_ENDIAN);for(float v:tensor)bytes.putFloat(v);return ScrewQualificationBundle.hash(bytes.array());}
    static Stats stats(double[] values) {
        if(values.length==0)throw new IllegalArgumentException("Empty summary");double[] a=values.clone();Arrays.sort(a);
        for(double value:a)if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite diagnostic");
        return new Stats(a.length,a[0],quantile(a,.5),quantile(a,.9),quantile(a,.95),a[a.length-1],Arrays.stream(a).average().orElseThrow());
    }
    static double quantile(double[] sorted,double q){double at=q*(sorted.length-1);int lo=(int)at,hi=Math.min(sorted.length-1,lo+1);return sorted[lo]+(at-lo)*(sorted[hi]-sorted[lo]);}
    static double[] ranks(double[] values) {
        Integer[] order=new Integer[values.length];for(int i=0;i<order.length;i++)order[i]=i;Arrays.sort(order,Comparator.comparingDouble(i->values[i]));double[] rank=new double[values.length];
        for(int i=0;i<order.length;){int end=i+1;while(end<order.length&&values[order[end]]==values[order[i]])end++;double average=(i+end-1)*.5+1;for(int j=i;j<end;j++)rank[order[j]]=average;i=end;}return rank;
    }
    static Double spearman(double[] a,double[] b){return ScrewIdentityControl.correlation(ranks(a),ranks(b));}
}
