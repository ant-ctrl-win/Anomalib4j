package io.github.antctrlwin.anomalib4j.evaluation;

import java.util.*;

final class GeneralityCoherence {
    private GeneralityCoherence() { }
    record Distribution(int n,double min,double max,double mean,double median,double p25,double p75,double p90,double p95) { }
    static Distribution distribution(double[] values) {
        if(values.length==0)throw new IllegalArgumentException("Empty distribution");
        double[] a=values.clone();for(double v:a)if(!Double.isFinite(v))throw new IllegalArgumentException("Nonfinite statistic");
        Arrays.sort(a);return new Distribution(a.length,a[0],a[a.length-1],Arrays.stream(a).average().orElseThrow(),
                quantile(a,.5),quantile(a,.25),quantile(a,.75),quantile(a,.9),quantile(a,.95));
    }
    private static double quantile(double[] sorted,double q) {
        double p=q*(sorted.length-1);int lo=(int)p,hi=Math.min(sorted.length-1,lo+1);
        return sorted[lo]+(p-lo)*(sorted[hi]-sorted[lo]);
    }
    record Position(int row,int column,double cosineMean,double cosineMedian,double directionalDispersion,
                    double channelVariance,int lowStd1e6,int lowStd1e5,int lowStd1e4,int lowStd001) { }
    record Summary(int images,Distribution pooledPairwiseCosine,Distribution positionCosineMeans,
                   Distribution directionalDispersion,Distribution sampleStd,Distribution positionVariance,
                   Map<String,Long> lowStdCounts,double adjacentFeatureContrast,double looBestCellDrift,
                   double looSameCellFraction,double looCosineGain,List<Position> positions) { }
    static Summary compute(double[][] units) {
        int n=units.length;if(n<2)throw new IllegalArgumentException("At least two images required");
        double[] mean=new double[18816],m2=new double[18816];int seen=0;
        for(double[] row:units) {
            if(row.length!=18816)throw new IllegalArgumentException("Expected HWC14x14x96");
            seen++;
            for(int p=0;p<196;p++) {
                double norm=0;
                for(int c=0;c<96;c++){int at=p*96+c;double value=row[at];norm+=value*value;
                    double delta=value-mean[at];mean[at]+=delta/seen;m2[at]+=delta*(value-mean[at]);}
                if(!Double.isFinite(norm)||Math.abs(norm-1)>1e-12)throw new IllegalArgumentException("Unit descriptor required");
            }
        }
        double[] std=new double[18816],variance=new double[196],cosines=new double[196],dispersion=new double[196];
        for(int i=0;i<std.length;i++)std[i]=Math.sqrt(Math.max(0,m2[i]/(n-1)));
        int pairs=Math.multiplyExact(n,n-1)/2;
        double[] pooled=new double[Math.multiplyExact(pairs,196)],local=new double[pairs];int pooledAt=0;
        var positions=new ArrayList<Position>();
        for(int p=0;p<196;p++) {
            double norm2=0;int[] lows=new int[4];double[] thresholds={1e-6,1e-5,1e-4,.001};
            for(int c=0;c<96;c++){int at=p*96+c;norm2+=mean[at]*mean[at];variance[p]+=std[at]*std[at]/96;
                for(int t=0;t<4;t++)if(std[at]<=thresholds[t])lows[t]++;}
            cosines[p]=(n*norm2-1)/(n-1);dispersion[p]=1-norm2;int k=0;
            for(int i=0;i<n;i++)for(int j=0;j<i;j++) {
                double dot=0;for(int c=0;c<96;c++)dot+=units[i][p*96+c]*units[j][p*96+c];
                local[k++]=dot;pooled[pooledAt++]=dot;
            }
            Arrays.sort(local);positions.add(new Position(p/14,p%14,cosines[p],quantile(local,.5),dispersion[p],variance[p],lows[0],lows[1],lows[2],lows[3]));
        }
        var low=new LinkedHashMap<String,Long>();
        for(double t:new double[]{1e-6,1e-5,1e-4,.001})low.put(Double.toString(t),Arrays.stream(std).filter(v->v<=t).count());
        double contrast=0;long edges=0;
        for(double[] row:units)for(int p=0;p<196;p++)for(int q:new int[]{p%14<13?p+1:-1,p/14<13?p+14:-1})if(q>=0) {
            for(int c=0;c<96;c++){double d=row[p*96+c]-row[q*96+c];contrast+=d*d;}edges++;
        }
        double drift=0,gain=0;int fixed=0,count=0;
        double[] sums=mean.clone();for(int i=0;i<sums.length;i++)sums[i]*=n;
        for(double[] row:units) {
            double[] norm=new double[196];
            for(int p=0;p<196;p++){for(int c=0;c<96;c++){double v=sums[p*96+c]-row[p*96+c];norm[p]+=v*v;}norm[p]=Math.sqrt(norm[p]);}
            for(int y=1;y<13;y++)for(int x=1;x<13;x++) {
                int p=y*14+x;double own=cosine(row,p,p,sums,norm),best=own;int bestCell=p;
                for(int yy=y-1;yy<=y+1;yy++)for(int xx=x-1;xx<=x+1;xx++) {
                    int q=yy*14+xx;double v=cosine(row,p,q,sums,norm);if(v>best){best=v;bestCell=q;}
                }
                drift+=Math.hypot(bestCell%14-x,bestCell/14-y);gain+=best-own;if(bestCell==p)fixed++;count++;
            }
        }
        return new Summary(n,distribution(pooled),distribution(cosines),distribution(dispersion),distribution(std),
                distribution(variance),low,contrast/edges,drift/count,(double)fixed/count,gain/count,positions);
    }
    private static double cosine(double[] row,int p,int q,double[] sums,double[] norm) {
        if(norm[q]<1e-12)return 0;
        double dot=0;for(int c=0;c<96;c++)dot+=row[p*96+c]*(sums[q*96+c]-row[q*96+c]);return dot/norm[q];
    }
}
