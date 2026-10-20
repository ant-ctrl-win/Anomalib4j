package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.canonical.RigidTransform;
import java.util.*;

final class ScrewFraming {
    static final double MARGIN = 4;
    private ScrewFraming() { }
    record Point(double x,double y) { }
    record Bounds(double minX,double maxX,double minY,double maxY) {
        double width(){return maxX-minX;}
        double height(){return maxY-minY;}
    }
    record Shape(String image,GeneralityGeometry.Pose pose,List<Point> centers,double supportRadius,Bounds support) { }
    record Contract(String id,double scale,double targetX,double targetY,double margin) {
        Contract {
            if(!Double.isFinite(scale)||scale<=0||scale>1||!Double.isFinite(targetX)||!Double.isFinite(targetY)
                    ||!Double.isFinite(margin)||margin<0||margin>=349.5)throw new IllegalArgumentException("Invalid fixed framing");
        }
        RigidTransform rigid(GeneralityGeometry.Pose pose) {
            if(scale!=1)throw new IllegalStateException("Production B accepts rigid transforms only");
            var a=pose.transform();return new RigidTransform(a.cos(),a.sin(),a.tx()+(targetX-349.5),a.ty()+(targetY-349.5));
        }
    }
    record Measurement(String image,Bounds centers,Bounds support,double width,double height,
                       double left,double right,double top,double bottom,double clearance,int cells,
                       int outside,int tipOutside,int headOutside,double outsideFraction,int supportOutside,
                       double supportOutsideFraction,double occupancy) { }
    record Envelope(double minX,double maxX,double minY,double maxY,String minXImage,String maxXImage,
                    String minYImage,String maxYImage) { }
    record Placement(double minTargetX,double maxTargetX,double minTargetY,double maxTargetY,boolean feasible) { }
    record Distribution(int n,double min,double p05,double p50,double p95,double max,double mean) { }

    static Shape shape(String name,GeneralityGeometry.Pose pose,boolean[] mask) {
        if(mask.length!=175*175)throw new IllegalArgumentException("Expected175 mask");
        var a=RigidTransform.center(pose.cx(),pose.cy(),pose.angle());var points=new ArrayList<Point>();
        for(int p=0;p<mask.length;p++)if(mask[p]) {
            double x=4*(p%175)+1.5,y=4*(p/175)+1.5;
            points.add(new Point(a.canonicalX(x,y)-349.5,a.canonicalY(x,y)-349.5));
        }
        if(points.isEmpty())throw new IllegalArgumentException("Empty silhouette");
        double radius=2*(Math.abs(a.cos())+Math.abs(a.sin()));
        Bounds centers=bounds(points,1,0,0,0);
        return new Shape(name,pose,List.copyOf(points),radius,new Bounds(centers.minX-radius,centers.maxX+radius,
                centers.minY-radius,centers.maxY+radius));
    }
    private static Bounds bounds(List<Point> points,double scale,double tx,double ty,double radius) {
        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minY=minX,maxY=maxX;
        for(var p:points){double x=scale*p.x+tx,y=scale*p.y+ty;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        return new Bounds(minX-radius,maxX+radius,minY-radius,maxY+radius);
    }
    static Measurement measure(Shape shape,Contract c) {
        var centers=bounds(shape.centers,c.scale,c.targetX,c.targetY,0);
        double r=c.scale*shape.supportRadius;var support=bounds(shape.centers,c.scale,c.targetX,c.targetY,r);
        int outside=0,tip=0,head=0,supportOutside=0;
        for(var p:shape.centers){double x=c.scale*p.x+c.targetX,y=c.scale*p.y+c.targetY;
            if(x<0||x>699||y<0||y>699){outside++;if(p.x<0)tip++;else head++;}
            if(x-r<0||x+r>699||y-r<0||y+r>699)supportOutside++;
        }
        double left=support.minX,right=699-support.maxX,top=support.minY,bottom=699-support.maxY;
        return new Measurement(shape.image,centers,support,support.width(),support.height(),left,right,top,bottom,
                Math.min(Math.min(left,right),Math.min(top,bottom)),shape.centers.size(),outside,tip,head,
                (double)outside/shape.centers.size(),supportOutside,(double)supportOutside/shape.centers.size(),
                shape.centers.size()*16*c.scale*c.scale/(700.0*700));
    }
    static Envelope envelope(List<Shape> shapes) {
        if(shapes.isEmpty())throw new IllegalArgumentException("Empty FIT");
        Shape loX=Collections.min(shapes,Comparator.comparingDouble(s->s.support.minX)),hiX=Collections.max(shapes,Comparator.comparingDouble(s->s.support.maxX));
        Shape loY=Collections.min(shapes,Comparator.comparingDouble(s->s.support.minY)),hiY=Collections.max(shapes,Comparator.comparingDouble(s->s.support.maxY));
        return new Envelope(loX.support.minX,hiX.support.maxX,loY.support.minY,hiY.support.maxY,loX.image,hiX.image,loY.image,hiY.image);
    }
    static Placement placement(Envelope e,double scale,double margin) {
        double loX=margin-scale*e.minX,hiX=699-margin-scale*e.maxX;
        double loY=margin-scale*e.minY,hiY=699-margin-scale*e.maxY;
        return new Placement(loX,hiX,loY,hiY,loX<=hiX&&loY<=hiY);
    }
    static double centeredScale(Envelope e,double margin) {
        return Math.min(1,(349.5-margin)/Math.max(Math.max(-e.minX,e.maxX),Math.max(-e.minY,e.maxY)));
    }
    static double movableScale(Envelope e,double margin) {
        return Math.min(1,Math.min((699-2*margin)/(e.maxX-e.minX),(699-2*margin)/(e.maxY-e.minY)));
    }
    static Contract movable(String id,Envelope e,double scale,double margin) {
        var p=placement(e,scale,margin);
        if(p.minTargetX-p.maxTargetX>1e-10||p.minTargetY-p.maxTargetY>1e-10)throw new IllegalArgumentException("Infeasible fixed placement");
        return new Contract(id,scale,(p.minTargetX+p.maxTargetX)/2,(p.minTargetY+p.maxTargetY)/2,margin);
    }
    static Distribution distribution(double[] values) {
        if(values.length==0)throw new IllegalArgumentException("Empty statistics");
        double[] a=values.clone();for(double v:a)if(!Double.isFinite(v))throw new IllegalArgumentException("Nonfinite statistic");Arrays.sort(a);
        return new Distribution(a.length,a[0],quantile(a,.05),quantile(a,.5),quantile(a,.95),a[a.length-1],Arrays.stream(a).average().orElseThrow());
    }
    private static double quantile(double[] a,double q){double at=q*(a.length-1);int lo=(int)at,hi=Math.min(a.length-1,lo+1);return a[lo]+(at-lo)*(a[hi]-a[lo]);}
    static Map<String,Object> summary(List<Measurement> rows) {
        var out=new LinkedHashMap<String,Object>();out.put("images",rows.size());
        out.put("center_clipping_cases",rows.stream().filter(r->r.outside>0).count());
        out.put("support_clipping_cases",rows.stream().filter(r->r.supportOutside>0).count());
        out.put("outside_count",distribution(rows.stream().mapToDouble(Measurement::outside).toArray()));
        out.put("tip_outside_count",distribution(rows.stream().mapToDouble(Measurement::tipOutside).toArray()));
        out.put("head_outside_count",distribution(rows.stream().mapToDouble(Measurement::headOutside).toArray()));
        out.put("outside_fraction",distribution(rows.stream().mapToDouble(Measurement::outsideFraction).toArray()));
        out.put("support_outside_fraction",distribution(rows.stream().mapToDouble(Measurement::supportOutsideFraction).toArray()));
        out.put("clearance",distribution(rows.stream().mapToDouble(Measurement::clearance).toArray()));
        out.put("occupancy",distribution(rows.stream().mapToDouble(Measurement::occupancy).toArray()));
        out.put("width700",distribution(rows.stream().mapToDouble(Measurement::width).toArray()));
        out.put("height700",distribution(rows.stream().mapToDouble(Measurement::height).toArray()));
        out.put("width224",distribution(rows.stream().mapToDouble(r->r.width*224/700).toArray()));
        out.put("height224",distribution(rows.stream().mapToDouble(r->r.height*224/700).toArray()));return out;
    }
}
