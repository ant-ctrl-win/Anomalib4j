package io.github.antctrlwin.anomalib4j.canonical;

import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.util.Objects;

/** Acquires the exact default-sRGB ARGB words used by BufferedImage.getRGB. */
public final class CanonicalRgbAcquisition {
    private static final Class<?> BGR_RASTER = new BufferedImage(1,1,BufferedImage.TYPE_3BYTE_BGR).getRaster().getClass();
    private static final ColorSpace SRGB = ColorSpace.getInstance(ColorSpace.CS_sRGB);
    private CanonicalRgbAcquisition() { }

    public static void read(BufferedImage image, int[] destination) {
        Objects.requireNonNull(image); Objects.requireNonNull(destination);
        if (destination.length != Math.multiplyExact(image.getWidth(),image.getHeight()))
            throw new IllegalArgumentException("Expected one destination word per pixel");
        if (!tryBgr(image,destination)) image.getRGB(0,0,image.getWidth(),image.getHeight(),destination,0,image.getWidth());
    }

    static boolean tryBgr(BufferedImage image, int[] destination) {
        if (image.getClass()!=BufferedImage.class || image.getType()!=BufferedImage.TYPE_3BYTE_BGR) return false;
        var raster=image.getRaster(); var color=image.getColorModel();
        if (raster.getClass()!=BGR_RASTER || color.getClass()!=ComponentColorModel.class
                || color.getColorSpace()!=SRGB || color.hasAlpha() || color.isAlphaPremultiplied()
                || color.getNumComponents()!=3 || color.getTransferType()!=DataBuffer.TYPE_BYTE
                || color.getComponentSize(0)!=8 || color.getComponentSize(1)!=8 || color.getComponentSize(2)!=8
                || raster.getSampleModel().getClass()!=PixelInterleavedSampleModel.class
                || !(raster.getDataBuffer() instanceof DataBufferByte buffer) || buffer.getNumBanks()!=1) return false;
        var samples=(PixelInterleavedSampleModel)raster.getSampleModel();
        if (samples.getNumBands()!=3 || samples.getDataType()!=DataBuffer.TYPE_BYTE) return false;
        int pixel=samples.getPixelStride(), scan=samples.getScanlineStride();
        int red=samples.getOffset(0,0,0), green=samples.getOffset(0,0,1), blue=samples.getOffset(0,0,2);
        if (pixel<3 || scan<=0 || red<0 || green<0 || blue<0 || red>=pixel || green>=pixel || blue>=pixel
                || red==green || red==blue || green==blue) return false;
        byte[] data=buffer.getData();
        // The validated JDK interleaved raster's getDataElements uses band offsets without DataBuffer.getOffset().
        long first=-(long)raster.getSampleModelTranslateY()*scan
                -(long)raster.getSampleModelTranslateX()*pixel;
        long last=first+(long)(image.getHeight()-1)*scan+(long)(image.getWidth()-1)*pixel;
        if (first+Math.min(red,Math.min(green,blue))<0 || last+Math.max(red,Math.max(green,blue))>=data.length) return false;
        int out=0, row=(int)first;
        for (int y=0;y<image.getHeight();y++,row+=scan) {
            int at=row;
            for (int x=0;x<image.getWidth();x++,at+=pixel)
                destination[out++]=0xff000000|((data[at+red]&255)<<16)|((data[at+green]&255)<<8)|(data[at+blue]&255);
        }
        return true;
    }
}
