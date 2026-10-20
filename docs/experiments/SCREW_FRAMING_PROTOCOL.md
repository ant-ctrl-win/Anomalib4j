# Screw framing feasibility protocol

Frozen before FIT measurement, 2026-10-20. Geometry, source conversion, encoder,
production B, M2 and Metal Nut remain unchanged. Only test-source tools are added.
No M2 fit/calibration/score, annotation, TEST pixel access or quality metric.

- Population: exact retained seed42 Screw 256 FIT / 64 CAL manifests from
  `canonical-generality-audit-20261020-validated`, checked against that run.
  All inputs and failures remain in per-image records. Source-border contact is
  an incomplete-acquisition failure, not canonical clipping. No silent fallback,
  score or exclusion from future detector coverage accounting is permitted.
- Pose: reuse `GeneralityGeometry` unchanged; dark Otsu, first largest8 component,
  4-background fill, centroid, second moments, anisotropy >=.50, absolute end-mass
  polarity >=.10, same branch/failure rules. Read its filled175 mask in test code.
- Coordinates: the filled reduced cell center is `(4*x+1.5,4*y+1.5)` in source700.
  Apply the existing directed rotation around the existing centroid. Head is +x,
  tip -x. Preserve center-based outside counts/fractions, with side split at the
  canonical anchor; also retain conservative full-cell support (each source cell
  has half-width2 before rotation). This is a segmentation proxy, not proof that
  every physical object pixel is segmented. Counts are reduced cells, not native
  image pixels. Occupancy is `filledCells*16*scale^2/700^2`.
- Containment: conservative support must fit inside [0,699]^2. A fixed4-pixel
  margin on each border is used for safe contracts, independent of measured CAL.
  Report analytical scale bounds both at zero and4 margin. No image-wise scale
  or placement. Quantiles use linear interpolation on sorted observations.
- Families: A scale1 at349.5; B largest scale <=1 at349.5, plus a scale rounded
  downward to hundredths; C scale1 with global target at the midpoint of the
  feasible interval; D largest scale <=1 permitting one global target, with
  midpoint placement. Report all geometry candidates, no parameter sweep.
- Canvas diagnostic: report a symmetrically enlarged, re-centered square at
  scale1, and separately whether expanding only right/bottom while retaining
  the literal target349.5 can ever cure negative coordinates.
- Feature stage: choose at most two safe candidates by containment/largest
  scale, preferring placement-only if feasible. Freeze the shortlist before
  extraction. Use exact matched253 valid FIT names, retained B_IDENTITY and
  current canonical units, unchanged encoder and diagnostic coherence formulas.
  No fitting or detector selection by anomaly performance. Production B may be
  used directly for scale1 placements. Any needed scaled-coordinate diagnostic
  must be separately identified and tested without changing production B.
- Freeze one contract using FIT alone, then run all64 CAL once without adjusting
  it. Report clipping, support clearance and FIT envelope violations. Any new
  unsafe CAL case blocks qualification. Source-border failures are explicitly
  rejected before CNN; future qualification must retain failure/coverage counts
  and cannot silently assign scores or report survivor-only full-dataset metrics.
- Decision: GO only if fixed framing preserves valid FIT/CAL with modest cost,
  coherence retains most of its audited gain, and failure policy is explicit;
  otherwise MAYBE or NO_GO as defined by the checkpoint request. This protocol
  does not authorize production Screw integration or any M2 qualification run.

Evidence is CREATE_NEW under a new root, with source copies/hashes, retained
input hashes, manifests, poses/extents, contracts, units/coherence, contract
freeze hash, CAL records, exact commands and stage hashes. Source/input guards
are checked between stages. Preserve older evidence and do not use `mvn clean`.
