package io.github.antctrlwin.anomalib4j.canonical;

import org.junit.jupiter.api.Test;
import java.awt.Point;
import java.awt.image.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalRgbAcquisitionTest {
    static BufferedImage offset(int width,int height) {
        int stride=3*(width+9)+5, offset=37;
        byte[] bytes=new byte[offset+stride*(height+4)+101]; Arrays.fill(bytes,(byte)0xa5);
        var buffer=new DataBufferByte(bytes,bytes.length-offset,offset);
        var raster=Raster.createInterleavedRaster(buffer,width+4,height+3,stride,3,new int[]{2,1,0},new Point(0,0));
        var child=raster.createWritableChild(2,1,width,height,0,0,null);
        return new BufferedImage(new BufferedImage(1,1,BufferedImage.TYPE_3BYTE_BGR).getColorModel(),child,false,null);
    }
    static void check(BufferedImage image,boolean fast) {
        int w=image.getWidth(),h=image.getHeight();
        int[] expected=image.getRGB(0,0,w,h,null,0,w),actual=new int[w*h];
        byte[] before=image.getRaster().getDataBuffer() instanceof DataBufferByte b?b.getData().clone():null;
        assertEquals(fast,CanonicalRgbAcquisition.tryBgr(image,actual));
        if (fast) assertArrayEquals(expected,actual);
        Arrays.fill(actual,7); CanonicalRgbAcquisition.read(image,actual); assertArrayEquals(expected,actual);
        if (before!=null) assertArrayEquals(before,((DataBufferByte)image.getRaster().getDataBuffer()).getData());
    }
    @Test void normalOffsetPaddingSubimageAndTranslatedChildMatchEveryLogicalPixel() {
        var parent=new BufferedImage(41,39,BufferedImage.TYPE_3BYTE_BGR);
        var translated=parent.getRaster().createWritableChild(7,11,23,19,0,0,null);
        var random=new Random(42);
        for (var image:List.of(new BufferedImage(23,19,BufferedImage.TYPE_3BYTE_BGR),offset(23,19),
                parent.getSubimage(3,5,23,19),new BufferedImage(parent.getColorModel(),translated,false,null))) {
            int[] rgb=new int[23*19]; for(int i=0;i<rgb.length;i++)rgb[i]=random.nextInt();
            image.setRGB(0,0,23,19,rgb,0,23); check(image,true);
        }
    }
    @Test void allChannelValuesExtremesAndRandomPatternsMatch() {
        var image=new BufferedImage(256,256,BufferedImage.TYPE_3BYTE_BGR);
        int[] values=new int[256*256];
        for(int g=0;g<256;g++)for(int r=0;r<256;r++)values[g*256+r]=(r<<16)|(g<<8)|((r^g)&255);
        image.setRGB(0,0,256,256,values,0,256); check(image,true);
        for(int color:new int[]{0,0xffffff,0xff0000,0x00ff00,0x0000ff,0x010203,0xfe8081}) {
            image.setRGB(0,0,color); check(image,true);
        }
    }
    @Test void otherRepresentationsAlphaCustomColorAndOverriddenImagesUseFallback() {
        for(int type:new int[]{BufferedImage.TYPE_INT_RGB,BufferedImage.TYPE_INT_ARGB,BufferedImage.TYPE_4BYTE_ABGR,
                BufferedImage.TYPE_BYTE_GRAY,BufferedImage.TYPE_BYTE_INDEXED}) {
            var image=new BufferedImage(13,17,type); image.setRGB(3,4,0x30123456); check(image,false);
        }
        var alternate=new ComponentColorModel(java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_LINEAR_RGB),
                false,false,java.awt.Transparency.OPAQUE,DataBuffer.TYPE_BYTE);
        check(new BufferedImage(alternate,alternate.createCompatibleWritableRaster(13,17),false,null),false);
        var subclass=new BufferedImage(13,17,BufferedImage.TYPE_3BYTE_BGR) { };
        check(subclass,false);
    }
    @Test void canonicalOutputsMatchForEquivalentBgrOffsetAndAlphaFallbackSources() {
        var original=CanonicalPreprocessorTest.cross(); int[] pixels=original.getRGB(0,0,700,700,null,0,700);
        var processor=new CanonicalPreprocessor(AffineTensorSampler.Kernel.BILINEAR);
        float[] expected=new float[AffineTensorSampler.ELEMENTS]; var pose=processor.prepare(original,expected);
        for(var image:List.of(new BufferedImage(700,700,BufferedImage.TYPE_3BYTE_BGR),offset(700,700),
                new BufferedImage(700,700,BufferedImage.TYPE_INT_ARGB))) {
            image.setRGB(0,0,700,700,pixels,0,700); float[] actual=new float[expected.length];
            assertEquals(pose,processor.prepare(image,actual)); assertArrayEquals(expected,actual);
        }
        assertThrows(IllegalArgumentException.class,()->processor.prepare(new BufferedImage(699,700,5),expected));
        assertThrows(IllegalArgumentException.class,()->CanonicalRgbAcquisition.read(original,new int[3]));
    }
}
