package io.github.antctrlwin.anomalib4j.evaluation;

import io.github.antctrlwin.anomalib4j.m2.M2Model;
import java.io.*;
import java.nio.file.*;
import java.util.*;

final class ScrewIdentityBundle {
    static final String FORMAT="anomalib4j-screw-b-identity-m2-qualification/v1";
    static final String PREPROCESSING="screw-logical-srgb1024-bicubic700-identity-v1/canonical700-to224-bilinear-imagenet-v1";
    static final String FIT_SHA="61d0bfa1fcb5765fa765c9168d4fe928b6b5eca005c3a9826d5a1e3c9e33d9dd";
    static final String CAL_SHA="d89e343b3d8000090f8198900049bc1896a0cfc2e054b15195cf2dff77f5f13f";
    static final String TEST_SHA="3cfc2f129079328aae6ac59d13d4fd967ebfecbdef06fd913c79d9e27ef478c2";
    private ScrewIdentityBundle() { }
    static Map<String,String> fields() {
        var fields=new TreeMap<>(ScrewQualificationBundle.fields());
        for(String key:List.of("centroid","components","framing-freeze-sha256","geometry","holes","orientation","otsu","polarity","reduction","support"))fields.remove(key);
        fields.put("preprocessing",PREPROCESSING);fields.put("geometry","none;no-pose-estimation-or-pose-loaded-for-sampling");
        fields.put("transform","identity;scale=1;cos=1;sin=0;tx=0;ty=0;canvas700x700");
        fields.put("failure","fixed-canonical-matched-FIT-CAL-exclusions-before-CNN;no-score;no-restoration;no-new-geometry-gating;input-integrity-errors-stop");
        fields.put("matched-population","attempted-FIT256/CAL64;usable253/63;FIT-exclude-good096/158/196;CAL-exclude-good179;TEST160");
        fields.put("fit-usable-sha256",FIT_SHA);fields.put("cal-usable-sha256",CAL_SHA);fields.put("test-manifest-sha256",TEST_SHA);
        fields.put("deferred-map","unchanged-native14-upsample700;existing-backproject700-with-identity;upsample1024;no-shortcut");
        return Collections.unmodifiableMap(fields);
    }
    static byte[] encode(M2Model model,Map<String,String> fields) throws Exception {
        byte[] payload=ScrewQualificationBundle.payload(model);var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)) {
            out.writeUTF(FORMAT);out.writeInt(fields.size());for(var e:new TreeMap<>(fields).entrySet()){out.writeUTF(e.getKey());out.writeUTF(e.getValue());}
            out.writeUTF(model.encoderSha256());out.writeUTF(ScrewQualificationBundle.hash(payload));out.writeInt(payload.length);out.write(payload);
        }
        return bytes.toByteArray();
    }
    static void write(Path path,M2Model model) throws Exception {Files.write(path,encode(model,fields()),StandardOpenOption.CREATE_NEW);}
    static M2Model read(Path path,String expectedEncoder) throws Exception {
        if(Files.size(path)>327680)throw new IOException("Oversized identity bundle");
        try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if(!in.readUTF().equals(FORMAT))throw new IOException("Wrong identity bundle format");
            var expected=fields();if(in.readInt()!=expected.size())throw new IOException("Identity field count mismatch");
            var actual=new TreeMap<String,String>();for(int i=0;i<expected.size();i++)if(actual.put(in.readUTF(),in.readUTF())!=null)throw new IOException("Duplicate identity field");
            if(!actual.equals(expected))throw new IOException("Identity preprocessing mismatch");
            String encoder=in.readUTF();if(!encoder.equals(expectedEncoder))throw new IOException("Identity encoder mismatch");
            String hash=in.readUTF();if(in.readInt()!=ScrewQualificationBundle.PAYLOAD_BYTES)throw new IOException("Identity payload size mismatch");
            byte[] payload=in.readNBytes(ScrewQualificationBundle.PAYLOAD_BYTES);
            if(payload.length!=ScrewQualificationBundle.PAYLOAD_BYTES||!ScrewQualificationBundle.hash(payload).equals(hash)||in.read()!=-1)throw new IOException("Corrupt/truncated/trailing identity bundle");
            try(var values=new DataInputStream(new ByteArrayInputStream(payload))) {
                return new M2Model(encoder,new M2Model.Local(array(values,18816),array(values,18816)),new M2Model.Calibration(array(values,196),array(values,196)));
            }
        }catch(IllegalArgumentException invalid){throw new IOException("Invalid identity M2 state",invalid);}
    }
    private static double[] array(DataInputStream in,int n) throws IOException {double[] values=new double[n];for(int i=0;i<n;i++)values[i]=in.readDouble();return values;}
}
