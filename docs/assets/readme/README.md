# README gallery: retained evidence

These six cards derive from anomalous MVTec AD TEST images and saved Z maps.
They do not represent new inference, evaluation or fitting. Category metrics in
the public README describe the whole category, not the illustrated image.

## Sources and reproduction

- Five generic cards: strict M2-P from
  `target/ablation/local-models-20261017/M2/<category>/`.
- Metal Nut: **M2 + semantic registration**, qualified canonical B from
  `target/experiments/java-canonical-m2-integration-20261019-validated/evaluation/`.
  Bundle SHA-256:
  `8771287ad2777083c8a14f3e862142b9f36f4ef56dcbdec46cf156f30ebf9bb0`.
- [provenance.json](provenance.json) records exact image/map paths, byte hashes,
  sidecars, prediction/config/metric evidence, runtime versions and renderer hash.
  The original-coordinate maps are consumed as saved, including Metal Nut's
  retained backprojection; they are not regenerated here.

From the repository root, with the retained campaign roots and external
DatasetNinja export restored:

```shell
uv run tools/readme/render_examples.py --dataset /path/to/mvtec-ad-DatasetNinja
```

The [renderer](../../../tools/readme/render_examples.py) verifies source and map
hashes, identities, geometry, native maxima and the B bundle before rendering.
Reruns in the recorded environment verify identical PNG bytes and provenance;
different existing outputs are rejected. An ordinary clone contains the public
cards, not the ignored campaign roots or full dataset needed to reproduce them.

## Selection and display

The five required generic categories and Metal Nut were fixed before viewing
the cards; optional Carpet was excluded at that point. Within each declared
defect family, the first lexical TEST filename was selected, without score ranking:
Bottle/broken_large, Leather/cut, Tile/crack, Grid/broken, Zipper/broken_teeth,
Metal Nut/bent. Responses outside defects remain visible.

Each 752x484 card shows the complete original frame and an overlay. Display
resize only: RGB Lanczos to 360x360; saved original-coordinate Z to float32,
bilinear to 360x360. All six use magma with `t = clip(Z / 12, 0, 1)` and
`opacity = 0.65 * t`; RGB is rounded from
`input * (1-opacity) + 255 * magma(t) * opacity`. Negative Z is transparent;
Z >=12 saturates. No crop, manual regions, per-image min/max normalization,
ground-truth mask or changes to scientific maps/metrics. This is a display scale,
not a probability, operating threshold or equality of category distributions.

## Attribution

Source images: **MVTec AD, MVTec Software GmbH**,
[dataset source](https://www.mvtec.com/company/research/datasets/mvtec-ad).
The external export's `LICENSE.md` identifies
[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/).
These resized/annotated/overlaid figure adaptations are distributed under that
license. The repository's Apache 2.0 code license does not replace the images'
license. The provenance file hashes the local license evidence.
