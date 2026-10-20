package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.antctrlwin.anomalib4j.canonical.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

final class ScrewHeadPixels {
    private ScrewHeadPixels() { }
    static boolean[] nativeOverlap(boolean[] gt,RigidTransform a) {
        if(gt.length!=1024*1024)throw new IllegalArgumentException("Expected original1024 GT");boolean[] result=new boolean[196];
        for(int p=0;p<gt.length;p++)if(gt[p]){double x=(p%1024+.5)*700/1024-.5,y=(p/1024+.5)*700/1024-.5;int cell=ScrewHeadSupport.cell(a.canonicalX(x,y),a.canonicalY(x,y));if(cell>=0)result[cell]=true;}
        return result;
    }
    static boolean[] ring(boolean[] mask,int size,int radius) {
        if(mask.length!=size*size||radius<1)throw new IllegalArgumentException("Invalid diagnostic ring");int n=size+1;int[] prefix=new int[n*n];
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){int p=(y+1)*n+x+1;prefix[p]=(mask[y*size+x]?1:0)+prefix[p-1]+prefix[p-n]-prefix[p-n-1];}
        boolean[] ring=new boolean[mask.length];for(int y=0;y<size;y++)for(int x=0;x<size;x++)if(!mask[y*size+x]) {
            int l=Math.max(0,x-radius),r=Math.min(size,x+radius+1),t=Math.max(0,y-radius),b=Math.min(size,y+radius+1);
            ring[y*size+x]=prefix[b*n+r]-prefix[t*n+r]-prefix[b*n+l]+prefix[t*n+l]>0;
        }return ring;
    }
    static Map<String,Object> original(double[] map,boolean[] mask) {
        boolean[] surrounding=ring(mask,1024,8);var inside=new ArrayList<Double>();var outside=new ArrayList<Double>();
        for(int p=0;p<map.length;p++){if(mask[p])inside.add(map[p]);if(surrounding[p])outside.add(map[p]);}
        double[] gt=inside.stream().mapToDouble(Double::doubleValue).toArray(),near=outside.stream().mapToDouble(Double::doubleValue).toArray();
        var g=ScrewHeadSupport.summary(gt);var r=ScrewHeadSupport.summary(near);var components=componentPeaks(map,mask);double[] percentiles=components.stream().mapToDouble(v->percentile(map,v)).toArray();
        return Map.ofEntries(Map.entry("gt_pixels",gt.length),Map.entry("ring_pixels",near.length),Map.entry("gt_mean",g.mean()),Map.entry("gt_median",g.median()),Map.entry("gt_peak",g.max()),
                Map.entry("ring_mean",r.mean()),Map.entry("ring_median",r.median()),Map.entry("gt_minus_ring_mean",g.mean()-r.mean()),Map.entry("gt_peak_percentile",percentile(map,g.max())),
                Map.entry("global_peak_inside_gt",mask[ScrewIdentityControl.firstArgmax(map)]),Map.entry("components",components.size()),Map.entry("component_peak_percentiles",percentiles));
    }
    static double percentile(double[] values,double value) {long count=0;for(double v:values)if(v<=value)count++;return (double)count/values.length;}
    static List<Double> componentPeaks(double[] map,boolean[] mask) {
        int size=(int)Math.sqrt(mask.length);if(size*size!=mask.length||map.length!=mask.length)throw new IllegalArgumentException("Square matched maps required");
        boolean[] seen=new boolean[mask.length];int[] queue=new int[mask.length];var peaks=new ArrayList<Double>();
        for(int p=0;p<mask.length;p++)if(mask[p]&&!seen[p]) {
            int start=0,end=0;queue[end++]=p;seen[p]=true;double max=map[p];
            while(start<end){int q=queue[start++],x=q%size,y=q/size;max=Math.max(max,map[q]);
                for(int yy=Math.max(0,y-1);yy<=Math.min(size-1,y+1);yy++)for(int xx=Math.max(0,x-1);xx<=Math.min(size-1,x+1);xx++){
                    int at=yy*size+xx;if(mask[at]&&!seen[at]){seen[at]=true;queue[end++]=at;}
                }
            }peaks.add(max);
        }return peaks;
    }
    static boolean[] pull(boolean[] nativeMask,int size,RigidTransform transform) {
        boolean[] out=new boolean[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++) {
            double u=(x+.5)*700/size-.5,v=(y+.5)*700/size-.5;
            int sx=(int)Math.round((transform.sourceX(u,v)+.5)*1024/700-.5),sy=(int)Math.round((transform.sourceY(u,v)+.5)*1024/700-.5);
            if(sx>=0&&sx<1024&&sy>=0&&sy<1024)out[y*size+x]=nativeMask[sy*1024+sx];
        }return out;
    }
    private static boolean[] headMask(ScrewHeadSupport.Bands bands,RigidTransform pose) {
        boolean[] mask=new boolean[1024*1024];for(int p=0;p<mask.length;p++){double x=(p%1024+.5)*700/1024-.5,y=(p/1024+.5)*700/1024-.5;mask[p]=bands.head().contains(pose.canonicalX(x,y),pose.canonicalY(x,y));}return mask;
    }
    static Map<String,Object> imageStats(double[] luminance,boolean[] selection) {
        int size=(int)Math.sqrt(luminance.length);boolean[] around=ring(selection,size,Math.max(1,(int)Math.round(8.0*size/1024)));var selected=new ArrayList<Double>();var surrounding=new ArrayList<Double>();
        double gradient=0;int edges=0;var cells=new HashSet<Integer>();double mx=0,my=0;
        for(int p=0;p<selection.length;p++){
            if(around[p])surrounding.add(luminance[p]);if(!selection[p])continue;selected.add(luminance[p]);int x=p%size,y=p/size;mx+=x;my+=y;cells.add(Math.min(13,y*14/size)*14+Math.min(13,x*14/size));
            if(x+1<size&&selection[p+1]){double d=luminance[p+1]-luminance[p];gradient+=d*d;edges++;}
            if(y+1<size&&selection[p+size]){double d=luminance[p+size]-luminance[p];gradient+=d*d;edges++;}
        }
        var out=new LinkedHashMap<String,Object>();out.put("selected_pixels",selected.size());out.put("occupied14_bins",cells.size());out.put("gradient_edges",edges);out.put("gradient_energy",edges==0?null:gradient/edges);
        if(selected.isEmpty()){out.put("mean",null);out.put("contrast_to_ring",null);out.put("principal_minor_width_pixels",null);return out;}
        var stats=ScrewHeadSupport.summary(selected.stream().mapToDouble(Double::doubleValue).toArray());out.put("mean",stats.mean());out.put("median",stats.median());
        out.put("contrast_to_ring",surrounding.isEmpty()?null:stats.mean()-surrounding.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
        mx/=selected.size();my/=selected.size();double xx=0,yy=0,xy=0;for(int p=0;p<selection.length;p++)if(selection[p]){double x=p%size-mx,y=p/size-my;xx+=x*x;yy+=y*y;xy+=x*y;}
        double minor=(xx+yy-Math.hypot(xx-yy,2*xy))/(2*selected.size());out.put("principal_minor_width_pixels",Math.sqrt(12*Math.max(0,minor)));return out;
    }
    private static double[] luminance(int[] rgb) {double[] out=new double[rgb.length];for(int p=0;p<rgb.length;p++)out[p]=(.299*((rgb[p]>>>16)&255)+.587*((rgb[p]>>>8)&255)+.114*(rgb[p]&255))/255;return out;}
    private static int[] tensorRgb(float[] tensor) {
        int[] out=new int[224*224];double[] mean={.485,.456,.406},std={.229,.224,.225};for(int p=0;p<out.length;p++)for(int c=0;c<3;c++)out[p]|=((int)Math.round(Math.clamp(tensor[c*out.length+p]*std[c]+mean[c],0,1)*255))<<(16-c*8);return out;
    }
    private static double[] tensorLuminance(float[] tensor) {
        int n=224*224;double[] out=new double[n];for(int p=0;p<n;p++)out[p]=.299*(tensor[p]*.229+.485)+.587*(tensor[n+p]*.224+.456)+.114*(tensor[2*n+p]*.225+.406);return out;
    }
    static void resample(Path root) throws Exception {
        if(!Files.exists(root.resolve("analysis-evidence-hashes.json")))throw new IllegalStateException("Analysis freeze required");ScrewHeadAnalysis.verify(root);
        var bands=ScrewHeadAnalysis.bands(root);Path dataset=Path.of(ScrewHeadAnalysis.read(ScrewHeadAnalysis.CANONICAL,"config.json").path("dataset").asText());
        var rows=new ArrayList<Map<String,Object>>();var hashes=new ArrayList<Map<String,Object>>();var montage=new BufferedImage(4*300,8*300,BufferedImage.TYPE_INT_RGB);var graphics=montage.createGraphics();graphics.setColor(Color.WHITE);graphics.fillRect(0,0,montage.getWidth(),montage.getHeight());int index=0;
        try {for(var name:ScrewHeadAnalysis.read(root,"representatives.json")) {
            String image=name.asText(),role=EvaluationLabels.defect(image).equals("scratch_head")?"test":"calibration";JsonNode observation=null,input=null,identityObservation=null;
            for(var row:ScrewHeadAnalysis.read(ScrewHeadAnalysis.CANONICAL,role+"-observations.json"))if(row.path("image").asText().equals(image))observation=row;
            for(var row:ScrewHeadAnalysis.read(ScrewHeadAnalysis.CANONICAL,"images.json"))if(row.path("filename").asText().equals(image)&&row.path("role").asText().equals(role))input=row;
            for(var row:ScrewHeadAnalysis.read(ScrewHeadAnalysis.IDENTITY,role+"-observations.json"))if(row.path("image").asText().equals(image))identityObservation=row;
            if(observation==null||input==null||identityObservation==null)throw new IllegalStateException("Missing frozen representative");
            var pose=ScrewHeadSupport.transform(observation);var source=ImageIO.read(Path.of(input.path("path").asText()).toFile());int[] nativeRgb=source.getRGB(0,0,1024,1024,null,0,1024),view=GeneralityImages.view(source);source.flush();
            boolean[] selected=role.equals("test")?DatasetNinjaMasks.read(dataset.resolve("test/ann/"+image+".json"),1024,1024,false):headMask(bands,pose);
            int stageIndex=0;for(String stage:List.of("logical1024","view700","identity224","canonical224")) {
                boolean tensorStage=stage.endsWith("224"),canonical=stage.equals("canonical224");int size=tensorStage?224:stage.equals("view700")?700:1024;var a=canonical?pose:ScrewIdentityControl.IDENTITY;
                double[] lum;int[] rgb;
                if(tensorStage) {float[] tensor=new float[AffineTensorSampler.ELEMENTS];new AffineTensorSampler(AffineTensorSampler.Kernel.BILINEAR).sample(view,a,tensor);
                    var bytes=ByteBuffer.allocate(tensor.length*4).order(ByteOrder.LITTLE_ENDIAN);for(float value:tensor)bytes.putFloat(value);String hash=ScrewQualificationBundle.hash(bytes.array());
                    if(!canonical&&!hash.equals(identityObservation.path("tensorSha256").asText()))throw new IllegalStateException("Identity tensor reference mismatch");
                    hashes.add(Map.of("image",image,"stage",stage,"tensor_sha256",hash,"retained_reference",!canonical));lum=tensorLuminance(tensor);rgb=tensorRgb(tensor);
                }else {rgb=size==1024?nativeRgb:view;lum=luminance(rgb);}
                boolean[] mask=size==1024?selected:pull(selected,size,a);var record=new LinkedHashMap<String,Object>();record.put("image",image);record.put("role",role);record.put("stage",stage);record.putAll(imageStats(lum,mask));rows.add(record);
                var preview=new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB);preview.setRGB(0,0,size,size,rgb,0,size);boolean[] head=size==1024?headMask(bands,pose):pull(headMask(bands,pose),size,a);
                int minX=size,minY=size,maxX=0,maxY=0;for(int p=0;p<head.length;p++)if(head[p]){minX=Math.min(minX,p%size);maxX=Math.max(maxX,p%size);minY=Math.min(minY,p/size);maxY=Math.max(maxY,p/size);}
                int left=stageIndex*300,top=index*300;graphics.drawImage(preview,left,top+30,left+290,top+290,minX,minY,maxX+1,maxY+1,null);graphics.setColor(Color.BLACK);graphics.drawString(image+" "+stage,left+3,top+16);preview.flush();stageIndex++;
            }index++;
        }}finally{graphics.dispose();}
        ImageIO.write(montage,"png",root.resolve("head-resampling-contact.png").toFile());montage.flush();ScrewHeadAnalysis.json(root,"resampling-records.json",rows);ScrewHeadAnalysis.json(root,"tensor-reference-checks.json",hashes);
        ScrewHeadAnalysis.json(root,"resampling-summary.json",ScrewHeadAnalysis.summaries(rows,"role","stage"));ScrewHeadAnalysis.mark(root,"resampling");System.out.println("HEAD_DIAGNOSTIC - eight frozen representatives sampled; no CNN inference");
    }
}
