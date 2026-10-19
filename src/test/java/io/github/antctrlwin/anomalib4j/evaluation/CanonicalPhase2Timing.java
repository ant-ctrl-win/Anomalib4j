package io.github.antctrlwin.anomalib4j.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.antctrlwin.anomalib4j.canonical.*;
import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import javax.imageio.ImageIO;

public final class CanonicalPhase2Timing {
    private static volatile double sink;
    private final BufferedImage[] images = new BufferedImage[8];
    private final int[][] pixels = new int[8][];
    private final RigidTransform[] poses = new RigidTransform[8];
    private final int[] geometryPixels = new int[700*700];
    private final float[] tensor = new float[AffineTensorSampler.ELEMENTS];
    private final MetalNutGeometryEstimator estimator = new MetalNutGeometryEstimator();
    private final AffineTensorSampler[] samplers = new AffineTensorSampler[3];
    private final CanonicalPreprocessor[] preprocessors = new CanonicalPreprocessor[3];

    private CanonicalPhase2Timing(Path manifest) throws Exception {
        int n = 0;
        for (var row : new ObjectMapper().readTree(manifest.toFile())) if (row.path("role").asText().equals("fit") && n < 8) {
            images[n] = ImageIO.read(Path.of(row.path("source").asText()).toFile());
            pixels[n] = images[n].getRGB(0,0,700,700,null,0,700);
            poses[n] = estimator.estimate(pixels[n]).transform(); n++;
        }
        if (n != 8) throw new IllegalArgumentException("Eight frozen FIT timing fixtures required");
        for (int i = 0; i < 3; i++) {
            var kernel = CanonicalPhase2Bridge.kernel("BCD".substring(i,i+1));
            samplers[i] = new AffineTensorSampler(kernel); preprocessors[i] = new CanonicalPreprocessor(kernel);
        }
    }

    private void operation(int stage, int fixture) {
        if (stage == 0) {
            images[fixture].getRGB(0,0,700,700,geometryPixels,0,700);
            var pose = estimator.estimate(geometryPixels);
            if (pose.status() != CanonicalGeometry.Status.SUCCESS) throw new IllegalStateException("Timing pose failure");
            sink = pose.cx()+pose.angle();
        } else if (stage <= 3) {
            samplers[stage-1].sample(pixels[fixture], poses[fixture], tensor);
            sink = tensor[0]+tensor[23456]+tensor[tensor.length-1];
        } else {
            var pose = preprocessors[stage-4].prepare(images[fixture], tensor);
            if (pose.status() != CanonicalGeometry.Status.SUCCESS) throw new IllegalStateException("Timing pose failure");
            sink = pose.cx()+tensor[0]+tensor[23456]+tensor[tensor.length-1];
        }
    }

    public static void main(String[] args) throws Exception {
        var benchmark = new CanonicalPhase2Timing(Path.of(args[0]));
        Path output = Path.of(args[1]); int fork = Integer.parseInt(args[2]);
        var bean = ManagementFactory.getThreadMXBean();
        com.sun.management.ThreadMXBean allocations = bean instanceof com.sun.management.ThreadMXBean b && b.isThreadAllocatedMemorySupported() ? b : null;
        if (allocations != null && !allocations.isThreadAllocatedMemoryEnabled()) allocations.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        long start = System.nanoTime();
        while (System.nanoTime()-start < 3_000_000_000L)
            for (int stage = 0; stage < 7; stage++) for (int i = 0; i < 8; i++) benchmark.operation(stage,i);
        var csv = new StringBuilder("fork,round,stage,candidate,batch,ns_per_image,bytes_per_image\n");
        for (int round = 0; round < 9; round++) for (int offset = 0; offset < 7; offset++) {
            int stage = (offset+round+fork)%7;
            long before = allocations == null ? -1 : allocations.getThreadAllocatedBytes(thread);
            start = System.nanoTime();
            for (int i = 0; i < 16; i++) benchmark.operation(stage,i%8);
            double ns = (System.nanoTime()-start)/16.0;
            long after = allocations == null ? -1 : allocations.getThreadAllocatedBytes(thread);
            String scope = stage == 0 ? "geometry" : stage <= 3 ? "tensor" : "total";
            String candidate = stage == 0 ? "shared" : "BCD".substring((stage-1)%3,(stage-1)%3+1);
            csv.append(fork).append(',').append(round).append(',').append(scope).append(',').append(candidate)
                    .append(",16,").append(ns).append(',').append(before < 0 || after < 0 ? -1 : (after-before)/16.0).append('\n');
        }
        Files.writeString(output, csv, StandardOpenOption.CREATE_NEW);
        System.out.println("PHASE2 - TIMING - fork "+fork+" - complete - sink "+sink);
    }
}
