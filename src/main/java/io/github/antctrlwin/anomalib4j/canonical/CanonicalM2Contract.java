package io.github.antctrlwin.anomalib4j.canonical;

import java.util.Map;
import java.util.TreeMap;

public final class CanonicalM2Contract {
    public static final String PREPROCESSING = "metal-nut-otsu175-h4-scale1-v1/canonical700-to224-bilinear-imagenet-v1";
    private CanonicalM2Contract() { }

    public static Map<String, String> fields() {
        return Map.copyOf(new TreeMap<>(Map.ofEntries(
            Map.entry("preprocessing", PREPROCESSING),
            Map.entry("source", "metal_nut;700x700;BufferedImage.getRGB default-sRGB;alpha-discarded"),
            Map.entry("geometry", "metal-nut-otsu175-h4-scale1-v1;175x175"),
            Map.entry("reduction", "L=(19595R+38470G+7471B+32768)>>16;horizontal4(sum+2)>>2;vertical4(sum+2)>>2"),
            Map.entry("otsu", "256bins;double-cumulative;first-maximum;foreground-gray>threshold"),
            Map.entry("components", "8-connected-foreground;row-major-first-largest-tie"),
            Map.entry("holes", "4-connected-background-border-flood;fill-unreachable-background"),
            Map.entry("centroid", "filled-mask-double-mean;(reduced+0.5)*4-0.5"),
            Map.entry("harmonic4", "mean-unit-angular-h4;algebraic-double-row-major;origin=(1,0)"),
            Map.entry("orientation", "atan2(im,re)/4;clockwise-degrees;modulo90;representable[-45,45];remove-angle"),
            Map.entry("failure", "filled-area-outside[0.2,0.8];filled-border-contact;h4-strength<0.03;no-fallback"),
            Map.entry("transform", "scale=1;A(p)=R(-theta)*(p-centroid)+(349.5,349.5);canvas700x700"),
            Map.entry("sampler", "B;canonical700-to224-bilinear-imagenet-v1;four-tap-separable-linear"),
            Map.entry("coordinates", "integer-pixel-centers;q=3.125*(u+0.5)-0.5;analytic-inverse-rigid;double-row-restart"),
            Map.entry("boundary", "logical-edge-replication;clamp-tap-indices;unbounded-kernel-center"),
            Map.entry("tensor", "no-byte-quantization;RGB-clip[0,255];(channel/255.0-mean)/std;float32-NCHW[1,3,224,224]"),
            Map.entry("imagenet", "mean=0.485,0.456,0.406;std=0.229,0.224,0.225"),
            Map.entry("encoder", "mobilenetv4_spatial_14x14.onnx;float32-NHWC[1,14,14,96];HWC"),
            Map.entry("m2", "u=x/L2;reject-norm<1e-6;FIT-local-mean/sample-std;std-floor=1e-6;raw=mean96(((u-mu)/std)^2)"),
            Map.entry("calibration", "separate-HELD-OUT-raw-positional-mean/sample-std;floor=1e-6;Z=(raw-alpha)/beta;no-clamp"),
            Map.entry("score", "max196-native-Z;no-localization"),
            Map.entry("deferred-map", "owned-float64-row-major14x14;source-to-canonical-A;no-backprojection")
        )));
    }
}
