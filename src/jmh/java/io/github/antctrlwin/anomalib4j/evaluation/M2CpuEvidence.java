package io.github.antctrlwin.anomalib4j.evaluation;

import ai.onnxruntime.*;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.*;

/** Untimed provider verification; measured sessions never enable profiling. */
final class M2CpuEvidence {
    private M2CpuEvidence() { }
    static void verify(byte[] model, float[] nchw, Path directory) throws Exception {
        var env = OrtEnvironment.getEnvironment();
        try (var options = new OrtSession.SessionOptions()) {
            options.enableProfiling(directory.resolve("cpu-proof-" + ProcessHandle.current().pid()).toString());
            try (var session = env.createSession(model, options);
                 var input = OnnxTensor.createTensor(env, FloatBuffer.wrap(nchw), new long[]{1, 3, 224, 224})) {
                try (var result = session.run(Map.of("input_image", input))) {
                    if (result.size() != 1) throw new IllegalStateException("Unexpected model outputs");
                }
                Path trace = Path.of(session.endProfiling());
                var events = ComparisonArtifacts.JSON.readTree(trace.toFile());
                var active = new HashSet<String>();
                for (var event : events) {
                    String provider = event.path("args").path("provider").asText("");
                    if (!provider.isEmpty()) active.add(provider);
                }
                if (!active.equals(Set.of("CPUExecutionProvider")))
                    throw new IllegalStateException("Unexpected active node providers: " + active);
            }
        }
    }
}
