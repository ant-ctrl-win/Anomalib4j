# Screw CAL-only photometric nuisance protocol

Frozen2026-10-20 before new encoding. Diagnostic only; no refit, recalibration,
geometry estimation, corrective normalization, production change, timing or commit.
Only63 usable CAL normals; good179 remains excluded from64 attempted identities.
No TEST image/map access, directory scanning or copying prior campaign trees.

1. Reuse exact identity/canonical bundles, CAL order, original canonical transforms
   and the prior normal-only head/neck/thread bands. Keep every transform fixed.
   Explicit allowlisted input files and train/img source paths are the only data
   reads; record accesses. Save selected CAL observations only, never mixed images
   metadata, TEST manifests/maps or earlier report/result copies in this root.
2. Read logical default-sRGB RGB via the existing acquisition contract, then apply
   each perturbation independently at native1024, before unchanged bicubic700/B224.
   For each channel v in[0,255], double arithmetic: brightness=v*f;
   contrast=127.5+(v-127.5)*f; gamma=255*pow(v/255,g). Clamp to[0,255], then
   Java Math.round once to uint8; channel order RGB, alpha discarded, no linear-light
   conversion. Existing RobustnessTransforms defines these formulas; only the
   user-requested application stage differs from the earlier224 protocol.
3. Exactly brightness/contrast/gamma and levels .80,.90,1,1.10,1.20. No other
   transformation, stochastic component, chained perturbation or parameter search.
4. Before nonidentity factors, evaluate all three1.00 controls on both models.
   Require bit-exact retained normalized descriptors, raw/Z/native max arrays and
   identity tensor hashes for all63. Canonical prior tensor hashes are absent;
   compare against unchanged B applied to the unperturbed decoded source view and
   frozen transform, and disclose this executable-contract reference. No invented
   historical tensor reference. Any baseline disagreement stops the experiment.
5. Persist63x18816 normalized units and63x196 raw/Z arrays per family/level/model,
   tensor hashes, fixed transforms, clipping counts and native maximum records.
   Always use unchanged encoder/M2 normalize/raw/calibrate/native max.
6. Report linear quantiles median/p90/p95/max (also min/mean) of max Z/raw and
   signed baseline deltas. Count cells strictly above3/5/10 and image fractions;
   these are descriptive, not operating thresholds. Pair amplification is
   (C_level-C_1)-(I_level-I_1). Retain all per-image values and max origins.
7. At each perturbed Z-winning cell retain raw,alpha,beta,Z and baseline raw/Z.
   Global delta equals deltaRaw/beta plus baselineZ_at_winner-baselineGlobalMax.
   Paired amplification splits into (deltaRawC-deltaRawI)/betaI,
   deltaRawC*(1/betaC-1/betaI), and the difference of winner-switch penalties.
   Alpha is fixed within each model; different-cell/model terms are arithmetic
   bookkeeping, not separately identified causal interventions or hybrid scorers.
8. Use frozen band membership at cell centers. Identity centers are transported
   through retained original pose for labeling only; canonical uses fixed bands.
   Other is not necessarily physical background. Do not alter bands or scoring.
9. For each level retain counts of C/I larger absolute response changes and signed
   amplification. At family level compare each image's maximum absolute change
   across four nonidentity levels; retain ties and all63 names. Worst five are
   unique images ranked by their largest signed paired amplification, retaining
   its level, both baselines/responses, cells and semantic origins.
10. Around1, retain centered derivative(S_1.10-S_.90)/.20 and maximum absolute
    one-sided secant=max(abs(S_.90-S_1),abs(S_1.10-S_1))/.10. Report both, since a
    U-shaped response can cancel in the centered derivative. No nonlinear fit.
11. At all196 cells compare mean absolute/local one-sided Z sensitivity across63
    images with frozen channel-std median, beta and median1/(96*std^2*beta), using
    tie-aware Spearman correlations. Quartile categories for winning-cell low std/
    low beta use each frozen model's196-cell lower quartiles, fixed before encoding.
    They are descriptive fitted-state ranks, not model or detector thresholds.
12. Retained units make channel diagnostics cheap: at each winner, compute signed
    changes of squared standardized residual/96 versus baseline; report positive
    contribution count, top5 positive growth share and effective participation.
    Keep negative contributions and exact total; no feature reweighting.
13. Freeze sources, allowlist/hashes, protocol, CAL population, transforms, bands,
    fitted-state ranks and models before baseline. Freeze baseline before factors,
    then all scores before summary/decision. Classify exactly one of the four
    user-specified photometric-sensitivity outcomes, without inventing a new gate.
    No TEST validation or deployment/generalization claim follows from CAL reuse.
