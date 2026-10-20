package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.canonical.*;
import java.util.*;

final class GeneralityGeometry {
    enum Strategy { CENTER_ONLY, AXIS, AXIS_WITH_POLARITY, H4 }
    enum State { OK, SEGMENTATION_FAILURE, WEAK_AXIS, WEAK_POLARITY }
    record Pose(State state, double cx, double cy, double angle, double rawAxis,
                double area, double anisotropy, double polarity, double h4,
                boolean border, double clippedFraction) {
        RigidTransform transform() {
            if (state!=State.OK) throw new IllegalStateException("Untrusted pose");
            return RigidTransform.center(cx,cy,angle);
        }
    }
    private final MetalNutGeometryEstimator reduction=new MetalNutGeometryEstimator();
    private final int[] labels=new int[175*175],queue=new int[175*175];
    private final boolean[] mask=new boolean[175*175],outside=new boolean[175*175];

    static List<Strategy> strategies(String category) {
        return switch(category) {
            case "metal_nut" -> List.of(Strategy.H4);
            case "bottle" -> List.of(Strategy.CENTER_ONLY,Strategy.AXIS_WITH_POLARITY);
            case "screw" -> List.of(Strategy.AXIS_WITH_POLARITY);
            case "hazelnut","cable" -> List.of(Strategy.CENTER_ONLY,Strategy.AXIS);
            case "grid" -> List.of();
            default -> throw new IllegalArgumentException("Outside six-category audit");
        };
    }
    Map<Strategy,Pose> estimate(int[] rgb,String category) {
        var control=reduction.estimate(rgb);
        if(category.equals("metal_nut")) System.arraycopy(reduction.filledMask(),0,mask,0,mask.length);
        else segment(reduction.reducedImage(),category.equals("bottle")||category.equals("screw"));
        return moments(category,control);
    }
    private void segment(byte[] gray,boolean dark) {
        int[] histogram=new int[256]; for(byte b:gray)histogram[b&255]++;
        double total=0;for(int i=0;i<256;i++)total+=i*histogram[i];
        double weight=0,sum=0,best=-1;int threshold=0;
        for(int i=0;i<256;i++) {
            weight+=histogram[i];sum+=i*histogram[i];double d=weight*(gray.length-weight),n=total*weight-sum*gray.length;
            double v=d>0?n*n/d:0;if(v>best){best=v;threshold=i;}
        }
        Arrays.fill(labels,0);int label=0,largest=0,largestSize=0;
        for(int p=0;p<gray.length;p++) {
            if(labels[p]!=0 || !foreground(gray[p],threshold,dark))continue;
            int head=0,tail=1;queue[0]=p;labels[p]=++label;
            while(head<tail) {
                int at=queue[head++],x=at%175,y=at/175;
                for(int yy=Math.max(0,y-1);yy<=Math.min(174,y+1);yy++)
                    for(int xx=Math.max(0,x-1);xx<=Math.min(174,x+1);xx++) {
                        int q=yy*175+xx;
                        if(labels[q]==0 && foreground(gray[q],threshold,dark)){labels[q]=label;queue[tail++]=q;}
                    }
            }
            if(tail>largestSize){largestSize=tail;largest=label;}
        }
        for(int p=0;p<mask.length;p++)mask[p]=largest!=0 && labels[p]==largest;
        Arrays.fill(outside,false);int tail=0;
        for(int p=0;p<mask.length;p++)if((p%175==0||p%175==174||p/175==0||p/175==174)&&!mask[p]) {
            outside[p]=true;queue[tail++]=p;
        }
        int head=0;
        while(head<tail) {
            int p=queue[head++];
            if(p%175>0)tail=visit(p-1,tail);if(p%175<174)tail=visit(p+1,tail);
            if(p>=175)tail=visit(p-175,tail);if(p<175*174)tail=visit(p+175,tail);
        }
        for(int p=0;p<mask.length;p++)mask[p]=!outside[p];
    }
    private static boolean foreground(byte gray,int threshold,boolean dark) {return dark?(gray&255)<=threshold:(gray&255)>threshold;}
    private int visit(int p,int tail){if(!mask[p]&&!outside[p]){outside[p]=true;queue[tail++]=p;}return tail;}
    private Map<Strategy,Pose> moments(String category,CanonicalGeometry control) {
        int count=0;double sx=0,sy=0;boolean border=false;
        for(int p=0;p<mask.length;p++)if(mask[p]){count++;sx+=p%175;sy+=p/175;border|=p%175==0||p%175==174||p/175==0||p/175==174;}
        double cx=count>0?sx/count:87,cy=count>0?sy/count:87,xx=0,xy=0,yy=0;
        for(int p=0;p<mask.length;p++)if(mask[p]){double x=p%175-cx,y=p/175-cy;xx+=x*x;xy+=x*y;yy+=y*y;}
        double anis=(xx+yy)>0?Math.hypot(xx-yy,2*xy)/(xx+yy):0;
        double axis=Math.toDegrees(.5*Math.atan2(2*xy,xx-yy)),c=Math.cos(Math.toRadians(axis)),s=Math.sin(Math.toRadians(axis));
        double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
        for(int p=0;p<mask.length;p++)if(mask[p]){double t=(p%175-cx)*c+(p/175-cy)*s;lo=Math.min(lo,t);hi=Math.max(hi,t);}
        int minus=0,plus=0;
        for(int p=0;p<mask.length;p++)if(mask[p]){double t=(p%175-cx)*c+(p/175-cy)*s;
            if(t<=lo+.25*(hi-lo))minus++;if(t>=hi-.25*(hi-lo))plus++;}
        double polarity=plus+minus>0?(double)(plus-minus)/(plus+minus):0,area=(double)count/mask.length;
        boolean valid=count>0&&area>=.005&&area<=.95&&!border;
        var result=new EnumMap<Strategy,Pose>(Strategy.class);
        for(var strategy:strategies(category)) {
            State state=valid?State.OK:State.SEGMENTATION_FAILURE;
            double angle=switch(strategy){case CENTER_ONLY -> 0;case AXIS -> axis;
                case AXIS_WITH_POLARITY -> polarity<0?axis+180:axis;case H4 -> control.angle();};
            if(strategy==Strategy.H4)state=control.status()==CanonicalGeometry.Status.SUCCESS?State.OK:State.SEGMENTATION_FAILURE;
            else if(state==State.OK&&strategy!=Strategy.CENTER_ONLY) {
                if(anis<(category.equals("screw")?.50:.10))state=State.WEAK_AXIS;
                else if(strategy==Strategy.AXIS_WITH_POLARITY&&Math.abs(polarity)<.10)state=State.WEAK_POLARITY;
            }
            double sourceX=(cx+.5)*4-.5,sourceY=(cy+.5)*4-.5;int clipped=0;
            var transform=RigidTransform.center(sourceX,sourceY,angle);
            for(int p=0;p<mask.length;p++)if(mask[p]) {
                double x=transform.canonicalX((p%175+.5)*4-.5,(p/175+.5)*4-.5);
                double y=transform.canonicalY((p%175+.5)*4-.5,(p/175+.5)*4-.5);
                if(x<0||x>699||y<0||y>699)clipped++;
            }
            result.put(strategy,new Pose(state,sourceX,sourceY,angle,axis,area,anis,polarity,control.harmonic4(),border,count>0?(double)clipped/count:0));
        }
        return result;
    }
}
