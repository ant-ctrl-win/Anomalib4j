package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.canonical.CanonicalRgbAcquisition;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.util.HexFormat;

final class GeneralityImages {
    private GeneralityImages() { }
    static float[] legacyTensor(BufferedImage image) throws ReflectiveOperationException {
        var method=Class.forName("io.github.antctrlwin.anomalib4j.onnx.ImageNetPreprocessor").getDeclaredMethod("preprocess",BufferedImage.class);
        method.setAccessible(true);return (float[])method.invoke(null,image);
    }
    static int[] view(BufferedImage image) {
        if(image.getWidth()!=image.getHeight())throw new IllegalArgumentException("Square audit sources only");
        BufferedImage rgb=image;
        boolean direct=image.getClass()==BufferedImage.class
                &&(image.getType()==BufferedImage.TYPE_INT_RGB||image.getType()==BufferedImage.TYPE_3BYTE_BGR)
                &&!image.getColorModel().hasAlpha()&&image.getColorModel().getColorSpace().isCS_sRGB();
        if(!direct) {
            int n=image.getWidth();int[] logical=new int[Math.multiplyExact(n,n)];CanonicalRgbAcquisition.read(image,logical);
            rgb=new BufferedImage(n,n,BufferedImage.TYPE_INT_RGB);rgb.setRGB(0,0,n,n,logical,0,n);
        }
        if(image.getWidth()!=700) {
            BufferedImage source=rgb;rgb=new BufferedImage(700,700,BufferedImage.TYPE_INT_RGB);var g=rgb.createGraphics();
            try {g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(source,0,0,700,700,null);
            }finally{g.dispose();}
        }
        int[] result=new int[700*700];CanonicalRgbAcquisition.read(rgb,result);return result;
    }
    record Perturbation(double dxFraction,double dyFraction,double angle) { }
    static final Perturbation[] PERTURBATIONS={new Perturbation(-.02,0,0),new Perturbation(.02,0,0),
            new Perturbation(0,-.02,0),new Perturbation(0,.02,0),new Perturbation(0,0,-10),
            new Perturbation(0,0,-5),new Perturbation(0,0,5),new Perturbation(0,0,10)};
    static BufferedImage perturb(BufferedImage source,Perturbation change) {
        int n=source.getWidth();if(n!=source.getHeight())throw new IllegalArgumentException("Square source");
        int[] rgb=new int[n*n],out=new int[n*n];CanonicalRgbAcquisition.read(source,rgb);
        double center=(n-1)*.5,c=Math.cos(Math.toRadians(change.angle)),s=Math.sin(Math.toRadians(change.angle));
        for(int y=0;y<n;y++)for(int x=0;x<n;x++) {
            double xx=x-center-change.dxFraction*n,yy=y-center-change.dyFraction*n;
            double px=Math.clamp(c*xx+s*yy+center,0,n-1),py=Math.clamp(-s*xx+c*yy+center,0,n-1);
            int x0=(int)Math.floor(px),y0=(int)Math.floor(py),x1=Math.min(n-1,x0+1),y1=Math.min(n-1,y0+1),pixel=0;
            double fx=px-x0,fy=py-y0;
            for(int shift=16;shift>=0;shift-=8) {
                double a=(rgb[y0*n+x0]>>>shift)&255,b=(rgb[y0*n+x1]>>>shift)&255;
                double d=(rgb[y1*n+x0]>>>shift)&255,e=(rgb[y1*n+x1]>>>shift)&255;
                int value=(int)Math.round((a*(1-fx)+b*fx)*(1-fy)+(d*(1-fx)+e*fx)*fy);pixel|=value<<shift;
            }
            out[y*n+x]=pixel;
        }
        var image=new BufferedImage(n,n,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,n,n,out,0,n);return image;
    }
    static double[] expectedCenter(GeneralityGeometry.Pose pose,Perturbation change) {
        double c=Math.cos(Math.toRadians(change.angle)),s=Math.sin(Math.toRadians(change.angle));
        double x=pose.cx()-349.5,y=pose.cy()-349.5;
        return new double[]{c*x-s*y+349.5+change.dxFraction*700,s*x+c*y+349.5+change.dyFraction*700};
    }
    static double angleError(double error,double period){return Math.abs(error-period*Math.floor(error/period+.5));}
    static String pixelHash(int[] rgb) throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        for(int p:rgb){digest.update((byte)(p>>>16));digest.update((byte)(p>>>8));digest.update((byte)p);}
        return HexFormat.of().formatHex(digest.digest());
    }
    static double gradient(float[] tensor) {
        double sum=0;int count=0;
        for(int c=0;c<3;c++)for(int y=0;y<224;y++)for(int x=0;x<224;x++) {
            int p=c*224*224+y*224+x;
            if(x<223){double d=tensor[p+1]-tensor[p];sum+=d*d;count++;}
            if(y<223){double d=tensor[p+224]-tensor[p];sum+=d*d;count++;}
        }
        return sum/count;
    }
}
