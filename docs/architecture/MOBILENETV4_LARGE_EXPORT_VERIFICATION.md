# MobileNetV4 Conv Large: external export verification

Recorded 2026-10-22. The external verification below was performed in Google
Colab and supplied by the artifact's author. Its notebook/raw report has not yet
been copied into this repository. These are attributed external results, not a
claim that PyTorch parity was rerun locally.

## Supplied artifact and external checks

- Upstream: `mobilenetv4_conv_large.e500_r256_in1k`, pretrained/frozen; cut `blocks.2`.
- Resource: `src/main/resources/models/mobilenetv4_large_spatial_14x14.onnx`.
- SHA-256: `ac83d397ee76768ba7ea5b2ec054337e07a454286403e27e72c832100021d6c3`.
- Size: 13,578,076 bytes (approximately 12.95 MiB).
- 93 graph nodes; 108 initializers; 3,335,376 initializer values.
  This counts the actual exported initializer elements, not the full upstream classifier.
- Input: float32 NCHW `[1,3,224,224]`; output: float32 NHWC `[1,14,14,192]`.
- ONNX checker: PASS. Executed provider: `CPUExecutionProvider`.

Full-output PyTorch versus ONNX Runtime parity, shape `(1,14,14,192)`:

| Input | Maximum absolute error | Mean absolute error |
|---|---:|---:|
| Zeros | 1.487731934e-04 | 1.086541761e-05 |
| Constant 0.25 | 2.975463867e-04 | 1.589900967e-05 |
| Random uniform [-1,1], seed 123456 | 1.020431519e-04 | 4.991259630e-06 |

Worst relative-scale case: constant 0.25. Maximum error / maximum output:
4.126262359e-06; mean error / mean output: 4.500836473e-06.
External verdict: **PASS**, not bit-exact; errors are a few ppm relative to output scale.
No additional pass tolerance is inferred from this summary.

Colab environment: PyTorch 2.11.0+cpu, timm 1.0.30, ONNX 1.23.0,
ONNX Runtime 1.31.0. This differs from the Java ORT dependency 1.30.0.

## Independent local inspection

Local ONNX inspection confirms the byte size, SHA-256, 93 nodes, 108 initializers,
3,335,376 initializer elements, float32 input/output geometry and ONNX checker PASS.
Graph I/O names are **`input` / `features`**. Output ends with transpose
`perm=[0,2,3,1]`, establishing contiguous HWC flattening at the Java boundary.
Java loading/shape and regression checks are recorded in the integration report.
No local PyTorch-versus-ONNX rerun is claimed.

Small remains a separate resource/identity with output `[1,14,14,96]` and names
`input_image` / `spatial_features`; its exact checkpoint/export provenance remains
unresolved as documented in the [existing contract](ONNX_MODEL_CONTRACT.md).
