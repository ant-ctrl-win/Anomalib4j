package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.canonical.CanonicalM2Contract;
import io.github.antctrlwin.anomalib4j.m2.M2Model;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

final class ScrewQualificationBundle {
    static final String FORMAT="anomalib4j-screw-canonical-m2-qualification/v1";
    static final String PREPROCESSING="screw-logical-srgb1024-bicubic700-otsu175-axis-polarity-scale1-safe-placement-v1/canonical700-to224-bilinear-imagenet-v1";
    static final int PAYLOAD_BYTES=304192;
    private ScrewQualificationBundle() { }
    static Map<String,String> fields() {
        var map=new TreeMap<>(CanonicalM2Contract.fields());map.remove("harmonic4");
        map.put("preprocessing",PREPROCESSING);map.put("source","screw;native1024x1024;logical-default-sRGB-before-resize;alpha-discarded");
        map.put("source-view","unchanged-GeneralityImages.view;Graphics2D-bicubic/quality-to700;logical-RGB-conversion-first");
        map.put("geometry","unchanged-GeneralityGeometry-Screw;175x175");
        map.put("otsu","256bins;double-cumulative;first-maximum;foreground-gray<=threshold");
        map.put("orientation","second-moment-axis=0.5*atan2(2xy,xx-yy);clockwise-degrees;anisotropy=hypot(xx-yy,2xy)/(xx+yy);floor0.50");
        map.put("polarity","outer25-percent-projected-end-mass;(plus-minus)/(plus+minus);abs-floor0.10;negative-add180;directed[-90,270];heavier-end+x");
        map.put("failure","nonempty;filled-area[0.005,0.95];no-filled-border-contact;anisotropy>=0.50;abs-polarity>=0.10;reject-before-CNN;no-score;no-fallback;retain-coverage");
        map.put("transform","scale=1;A(p)=R(-theta)*(p-centroid)+(395.50859765961616,349.2832413193618);canvas700x700");
        map.put("framing-freeze-sha256",ScrewQualificationInput.FRAMING_SHA);
        map.put("support","unchanged-filled175;full4x4-cell-support;four-pixel-margin-diagnostic-only;no-new-rejection-rule");
        map.put("deferred-map","qualification-only;native14-to700-bilinear;existing-forward-A-backproject700;bilinear700-to-original1024");
        return Collections.unmodifiableMap(map);
    }
    static byte[] payload(M2Model model) throws IOException {
        var bytes=new ByteArrayOutputStream(PAYLOAD_BYTES);
        try(var out=new DataOutputStream(bytes)){for(double[] values:new double[][]{model.local().mean(),model.local().effectiveStd(),model.calibration().alpha(),model.calibration().beta()})
            for(double value:values)out.writeDouble(value);}
        return bytes.toByteArray();
    }
    static byte[] encode(M2Model model,Map<String,String> contract) throws Exception {
        byte[] payload=payload(model);var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)) {
            out.writeUTF(FORMAT);out.writeInt(contract.size());
            for(var e:new TreeMap<>(contract).entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue());}
            out.writeUTF(model.encoderSha256());out.writeUTF(hash(payload));out.writeInt(payload.length);out.write(payload);
        }
        return bytes.toByteArray();
    }
    static void write(Path path,M2Model model) throws Exception {Files.write(path,encode(model,fields()),StandardOpenOption.CREATE_NEW);}
    static String hash(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static M2Model read(Path path,String expectedEncoder) throws Exception {
        if(Files.size(path)>327680)throw new IOException("Oversized Screw bundle");
        try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if(!in.readUTF().equals(FORMAT))throw new IOException("Wrong Screw qualification format");
            var expected=fields();if(in.readInt()!=expected.size())throw new IOException("Screw contract field count mismatch");
            var actual=new TreeMap<String,String>();
            for(int i=0;i<expected.size();i++)if(actual.put(in.readUTF(),in.readUTF())!=null)throw new IOException("Duplicate Screw contract field");
            if(!actual.equals(expected))throw new IOException("Screw preprocessing contract mismatch");
            String encoder=in.readUTF();if(!encoder.equals(expectedEncoder))throw new IOException("Screw encoder mismatch");
            String hash=in.readUTF();if(in.readInt()!=PAYLOAD_BYTES)throw new IOException("Screw payload length mismatch");
            byte[] payload=in.readNBytes(PAYLOAD_BYTES);
            if(payload.length!=PAYLOAD_BYTES||!hash(payload).equals(hash)||in.read()!=-1)throw new IOException("Corrupt/truncated/trailing Screw bundle");
            try(var values=new DataInputStream(new ByteArrayInputStream(payload))) {
                return new M2Model(encoder,new M2Model.Local(array(values,18816),array(values,18816)),new M2Model.Calibration(array(values,196),array(values,196)));
            }
        } catch(IllegalArgumentException invalid){throw new IOException("Invalid Screw M2 state",invalid);}
    }
    private static double[] array(DataInputStream in,int n) throws IOException {double[] a=new double[n];for(int i=0;i<n;i++)a[i]=in.readDouble();return a;}
}
