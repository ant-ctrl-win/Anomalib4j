# Anomalib4j: decisioni tecniche documentate

Registro riconciliato il 2026-10-02. La data e quella della registrazione, non dell'approvazione originaria quando questa non e disponibile. Riporta scelte attuate e decisioni operative esplicitamente confermate dall'utente, distinguendole dalle esecuzioni ancora da svolgere. Stato corrente in [PROJECT_STATE.md](PROJECT_STATE.md).

Aggiornamento del 2026-10-03: decisioni D10-D14 dalla nuova richiesta esplicita dell'utente; contract documentale, nessuna nuova esecuzione.

Aggiornamento del 2026-10-07: decisione D15 di riconciliazione finale post-hoc; nessuna modifica a codice di produzione ne al benchmark congelato.

Aggiornamento del 2026-10-16: [D16](#d16---repository-first-preparation-for-the-new-experimental-phase) definisce la nuova fase attiva. Le voci precedenti conservano lo stato al momento della registrazione.

## D01 - Compilare il confronto VSA nello spazio CNN

**DECIDED / IMPLEMENTED.** Apprendimento in VSA, inferenza con `W^T x_n + b`, `W = R^T(A/sigma)/sqrt(D)`, `b = -mu^T(A/sigma)`.

- Motivazione: eliminare la proiezione runtime conservando lo score.
- Evidenza: `AdjointCompiler`, `PatchScoreKernel`, test di equivalenza, [Sprint 6](development/archive/SPRINT6_RESULT.md), [Sprint 7](development/SPRINT7_RESULT.md), errore reale massimo circa 1.3e-12.
- Alternative: forward esplicito mantenuto come riferimento; ricostruzione inversa e forma quadratica non appartengono alla pipeline scelta.
- Riferimento storico: checkpoint `5caca96`, tag `sprint7-bottle-baseline`; data iniziale della decisione non attestata separatamente.

## D02 - Conservare un contratto deterministico e continuo

**DECIDED / IMPLEMENTED.** Rademacher densa D=10000, seed 42 conforme a java.util.Random, estrazione canale esterno/dimensione VSA interna; normalizzazione L2 CNN epsilon 1e-6, statistiche Welford campionarie con std floor 1e-8, archetipi continui L2 unitari.

- Motivazione: coerenza tra apprendimento, compilazione e riferimento esplicito; identificazione del modello mediante descriptor.
- Evidenza: `AGENTS.md`, classi projection/memory/model e rispettivi test.
- Alternative: binarizzazione e Achlioptas non implementati nella baseline; non trattarli come equivalenti gia validati.
- Data originaria non disponibile; presente nel checkpoint Sprint 7.

## D03 - Valutare raw e localizzazione senza trasformazioni visuali

**DECIDED / IMPLEMENTED.** `raw=1-score`; image score `max(raw)`. Pixel ROC globale, PRO su regioni 8-connesse, AUPRO normalizzato in [0,0.30]; bilineare half-pixel con bordi replicati.

- Motivazione: protocollo riproducibile senza soglie scelte sul test o distorsione da clamp.
- Evidenza: [LOCALIZATION_CONVENTIONS.md](benchmark/LOCALIZATION_CONVENTIONS.md), `LocalizationMetrics`, `LocalizationMaps`, relativi test.
- Alternative: top-k, smoothing, normalizzazione per immagine, calibrazione visuale [0,1] esclusi dal protocollo scientifico corrente.
- Riferimento storico: `f4132a1`, tag `sprint8-bottle-localization`.

## D04 - Mantenere SPATIAL_14 come riferimento

**DECIDED.** SPATIAL_28 resta variante sperimentale, non sostituisce il riferimento di localizzazione 14x14x96.

- Motivazione: 28x28 migliora Image AUROC a 1 ma peggiora Pixel AUROC e AUPRO, anche per ciascun difetto Bottle.
- Evidenza: [Sprint 9](development/archive/SPRINT9_RESULT.md), [analisi per difetto](diagnostics/SPATIAL14_VS_SPATIAL28_PER_DEFECT.md), configurazione dei successivi esperimenti.
- Alternative: sostituzione automatica con griglia piu densa non adottata; fusione multiscala DEFERRED. Stage e canali diversi impediscono di attribuire tutto alla densita.
- Riferimento storico del confronto: `83c4197`, tag `sprint9-spatial28-comparison`.

## D05 - Separare calibrazione posizionale e costruzione del modello

**DECIDED / IMPLEMENTED.** Dopo la prova in-sample sui 209 good, confronto appaiato held-out: sort filename, shuffle Java Random(seed), 167 per statistiche/archetipi/filtri, 42 per media/std raw per posizione. Floor sigma=1e-6, `Z=(raw-mu)/sigma`, image score max(Z).

- Motivazione: misurare l'effetto di Z senza confrontare modelli addestrati su numerosita diverse e senza far contribuire le 42 calibration alle statistiche VSA.
- Evidenza: `HeldOutBottleCalibration`, `PositionalRawCalibration`, [held-out](benchmark/HELDOUT_CALIBRATION_RESULT.md), [multi-seed](benchmark/HELDOUT_MULTI_SEED_RESULT.md). Delta positivi per seed split 1..5; seed Rademacher invariato.
- Alternative: full-209 in-sample conservato come riferimento dichiarato, non stima indipendente; test-good esclusi dal fitting. Cross-validation/leave-one-out e nuovi calibratori DEFERRED.
- Data/commit originario non attestati: helper e report ancora non versionati al momento della riconciliazione.

## D06 - Misurare inferenza separatamente dal model building

**DECIDED / IMPLEMENTED.** JMH SampleTime, batch 1, thread chiamante 1, warmup 5x2s, measurement 10x2s, fork 2; immagine precaricata, sessioni e modello in setup; consumo output con Blackhole.

- Motivazione: separare latenza operativa da I/O, decode e apprendimento; confrontare stage e percorsi completi senza somme artificiali di mediane.
- Evidenza: `src/jmh/`, [PERFORMANCE_BENCHMARK_VALIDATION.md](development/archive/PERFORMANCE_BENCHMARK_VALIDATION.md), JSON in `target/benchmark/`.
- Alternative: singola chiamata senza warmup non adottata; throughput sostenuto e ORT realmente single-thread non dimostrati da questa configurazione.
- Data/commit originario non attestati; codice JMH ancora non versionato.

## D07 - Accelerare il preprocessing con fast-path vincolato e fallback

**DECIDED / IMPLEMENTED.** Eliminare la conversione RGB full-resolution per BufferedImage standard INT_RGB/3BYTE_BGR, sRGB opachi; conservare il vecchio percorso sugli altri formati. Leggere direttamente il raster int del target 224x224.

- Motivazione: costo dominante getRGB/setRGB, senza alterare i float forniti all'encoder.
- Evidenza: `ImageNetPreprocessor`, `PreprocessingOptimizationIT`, [validazione](development/archive/PREPROCESSING_OPTIMIZATION_VALIDATION.md), [risultato](development/archive/PREPROCESSING_OPTIMIZATION_RESULT.md), CSV equivalenza: 47.867.904 float e zero differenze per produzione; detection p50 19.824640 -> 2.789376 ms.
- Alternativa **REJECTED:** rendering diretto universale, differenze su alpha/grayscale (maxAbsDiff 4.46428585). Ulteriori ottimizzazioni **DEFERRED** dopo questa strategia, coerentemente con lo stop richiesto per lo sprint.
- Data/commit originario non attestati; sorgente modificato ma non committed nel workspace corrente.

## D08 - Stato canonico basato sul repository

**DECIDED, 2026-10-02, richiesta corrente.** Mantenere `docs/PROJECT_STATE.md` come stato corrente verificabile; conservare l'handoff come memoria non autorevole.

- Motivazione: root PROJECT_STATE/TASK sono obsoleti rispetto ai risultati; i claim della conversazione devono avere un riscontro o restare OPEN.
- Evidenza: confronto documenti, codice, hash ONNX, CSV, JSON JMH, report test e stato Git svolto in questa attivita.
- Alternative: copiare l'handoff come verita o sovrascrivere i report storici non adottate. I riferimenti dal vecchio AGENTS allo stato root restano da riallineare in un intervento successivo.
- Nessun commit eseguito. La priorita operativa successivamente confermata dall'utente e registrata in D09.

## D09 - Chiudere la performance interna e procedere al confronto esterno

**DECIDED, conferma esplicita dell'utente del 2026-10-02.** Completata l'ottimizzazione interna, chiudere il filone performance interno per questa fase. Il task corrente e **definire ed eseguire il primo benchmark comparativo CPU sullo stesso hardware di Anomalib4j contro baseline esistenti appropriate, inizialmente PaDiM/PatchCore, senza ulteriori ottimizzazioni interne preventive**.

- Motivazione: misurare il posizionamento della pipeline ottimizzata rispetto a metodi esistenti prima di aprire altro lavoro interno.
- Evidenza: decisione successiva a `POST_BENCHMARK_STRATEGY.md`, ricordata nell'handoff locale (non pubblicato), sezioni 11.7 e 16.4, e ora confermata direttamente dall'utente. [PREPROCESSING_OPTIMIZATION_RESULT.md](development/archive/PREPROCESSING_OPTIMIZATION_RESULT.md) verifica p50 preprocessing 1.380352 ms, fullExtract 2.752512 ms, detection 2.789376 ms, localization 7.462912 ms. [PERFORMANCE_BASELINE_RESULT.md](development/PERFORMANCE_BASELINE_RESULT.md) conserva la misura compiled Adjoint p50 12 us per 196 celle.
- Alternative: l'ordine proposto in `POST_BENCHMARK_STRATEGY.md` (ablazione prima) e superato come roadmap operativa. `VSA vs direct latent-space memory/prototype` resta **OPEN / DEFERRED research question**; non e il task corrente e non viene reinterpretata scientificamente.
- Stato esecutivo: confronto esterno ancora da definire ed eseguire; questo aggiornamento modifica soltanto i documenti canonici e non avvia benchmark.

## D10 - Concludere questa fase dopo il benchmark

**DECIDED, 2026-10-03.** Benchmark, sintesi dei risultati/limiti, README e preparazione repository per presentazione, poi chiusura della fase.

- Motivazione: ottenere un risultato tecnico presentabile con un perimetro finito.
- Evidenza: istruzione esplicita dell'utente; nessun risultato di confronto ancora disponibile.
- Alternative: nuove ricerche, ottimizzazioni e diramazioni sono FUTURE_WORK, salvo indispensabili per completare correttamente il benchmark. Nessun requisito di superiorita.

## D11 - Congelare Bottle e Metal Nut, configurazioni e budget

**DECIDED, 2026-10-03.** Due sole categorie: Bottle e Metal Nut. Stessa configurazione per metodo su entrambe, fissata prima dei risultati. Anomalib4j mantiene SPATIAL_14 e positional Z, seed 42; Bottle 167+42, Metal Nut 176+44.

- Motivazione: seconda categoria con budget comparabile e variabilita di posa/rotazione, selezionata prima dell'esecuzione per limitare cherry picking. Maggiore difficolta per questo modello resta da misurare.
- Evidenza: censimento e lettura locale di filename/header PNG confermano 209/220 training-good, 83/115 test. Il protocollo Metal Nut e una specifica nuova, non una validazione.
- Alternative: altre categorie e tuning per categoria esclusi. PaDiM/PatchCore possono usare tutte le normal nel fitting nativo; nessun esperimento equal-fit-budget aggiuntivo. Le allocazioni sono esplicitamente differenti.

## D12 - Confronto primario Native/System

**DECIDED, 2026-10-03.** Confrontare Anomalib4j con PaDiM/PatchCore candidati di Anomalib 2.6.2, ciascuno con preprocessing, backbone, detector e runtime propri su CPU dello stesso hardware.

- Motivazione: misurare il compromesso reale qualita/latenza/memoria delle implementazioni selezionate.
- Evidenza: richiesta dell'utente; dettagli effettivi della release esterna PROPOSED / TO VERIFY, non presenti nelle fonti locali ispezionate.
- Alternative: backbone comune e threading controllato SECONDARY / DEFERRED; non trasformare i competitor. PatchCore Amazon Science/FAISS ed EfficientAD sono FUTURE_WORK.

## D13 - Metriche armonizzate e perimetri nativi espliciti

**DECIDED, 2026-10-03.** Esportare score/map raw prima delle trasformazioni visuali quando possibile; Image AUROC, Pixel AUROC e AUPRO@0.30 per categoria con le convenzioni consolidate. Macro-media solo secondaria; metriche framework separate.

- Motivazione: evitare mescolanza di evaluator e confronti score-only artificiali.
- Evidenza: `LOCALIZATION_CONVENTIONS.md`, benchmark Java e nuova richiesta; export competitor da verificare prima di misurare.
- Alternative: nessun max-map sostitutivo dello score nativo competitor; detection separata solo se il metodo offre davvero quella hot path. Native map e full-resolution map hanno perimetri distinti da dichiarare.

## D14 - Misurare costi completi e chiudere sei casi interpretabili

**DECIDED, 2026-10-03.** Registrare initialization, fitting, ready-to-infer misurato quando disponibile, full inference, stato persistente, memoria stabile e picco fitting. CPU default/documentata, input decodificato in RAM, batch 1; escludere I/O/decode/evaluator dai tempi primari di inferenza.

- Motivazione: rendere interpretabili i sei casi metodo/categoria senza equiparare thread chiamanti e worker nativi o heap e memoria processo.
- Evidenza: limiti JMH/ORT gia documentati; protocollo futuro in [BENCHMARK_CONTRACT.md](benchmark/BENCHMARK_CONTRACT.md).
- Alternative: nessuna falsa garanzia single-thread, nessun tempo ready-to-infer ricavato dalla somma di mediane. Se una misura necessaria manca, segnalare OPEN/incompletezza invece di dichiarare il benchmark concluso.

## D15 - Accettare le diagnosi post-hoc e chiudere il ramo per questa release

**DECIDED, 2026-10-07, riconciliazione finale.** Accettare le due diagnosi post-hoc verificate entro il loro perimetro dichiarato e chiudere il ramo di ricerca post-hoc per la release corrente.

- **Accettazione documentale:** [POSITIONAL_GLOBAL_VARIANCE_DIAGNOSTIC.md](diagnostics/POSITIONAL_GLOBAL_VARIANCE_DIAGNOSTIC.md) e [LOCAL_MEMORY_ABLATION_RESULT.md](diagnostics/LOCAL_MEMORY_ABLATION_RESULT.md) sono accettate come supporto canonico, interne e post-hoc, sulle categorie gia osservate.
- **Benchmark M0 preservato:** il benchmark congelato `final-comparison-20261003` e la baseline M0 restano invariati; nessuna metrica, configurazione o artefatto frozen viene modificato.
- **M2 registrata come baseline sperimentale:** M2 (media locale CNN piu deviazione standard per posizione/coordinata) dimostra l'utilita della dispersione nominale locale e della pesatura anisotropa per coordinata nelle condizioni misurate. Dopo la calibrazione Z posizionale, M1-Z vs M2-Z isola questo contributo anisotropo rispetto al solo centroide/livello isotropo.
- **Nessuna promozione automatica:** M2 non diventa automaticamente una nuova architettura di produzione v2; resta una baseline alternativa sperimentale.
- **Ramo chiuso:** il filone post-hoc e chiuso per la release corrente. Lavoro su tangent space, statistica direzionale, modelli di covarianza ricchi o nuove categorie richiede una fase futura con perimetro autorizzato separatamente.
- **Motivazione:** consolidare un risultato verificato senza riscrivere retroattivamente D01-D14, preservando la validita della compilazione dello score e del benchmark.
- **Evidenza:** i due report diagnostici e i relativi script/harness di sola lettura su produzione e artefatti congelati.
- **Alternative:** promuovere subito M2 a v2, o riaprire nuove categorie/tangent-space senza nuovo perimetro, non adottate.

### Registrazione della presentazione portfolio — 2026-10-07

Su richiesta dell'autore: README.md italiano come ingresso principale, README.en.md traduzione completa, README.it.md rimando compatibile; docs/TECHNICAL_OVERVIEW.md raccoglie la spiegazione tecnica. M2 e presentato come miglior detector attuale per localizzazione nelle categorie misurate, separato dalla pipeline e dai tempi M0. La data di D15 e quella confermata per la riconciliazione/registrazione finale; non retrodata il benchmark del 2026-10-03. Nessun nuovo esperimento, cambio alla produzione, commit o push.

## D16 - Repository-first preparation for the new experimental phase

**DECIDED, 2026-10-16, explicit user request.** Resume with repository/infrastructure
preparation for a fresh full-dataset M0 baseline and controlled spatial and
illumination/reflection robustness evaluation, preserving the CPU inference
advantage as an objective to measure.

- **Current implementation:** M0 remains the Java baseline. M2 is the strongest
  retained localization experiment on Bottle and Metal Nut, implemented in Python;
  it is not the current Java detector or a selected production v2.
- **Supersession:** D09-D15's previous-phase workflow/closure and two-category
  scope do not constrain the new preparation phase. Their scientific results,
  frozen splits and benchmark contract remain historical evidence unchanged.
  No retrospective promotion of M2 or renaming of M1/M2/M3 is intended.
- **Next task:** make evaluation infrastructure category-agnostic and capable of
  running unchanged M0 across all 15 MVTec AD categories. No full-dataset run
  or controlled robustness campaign is complete. This reconciliation implements
  documentation only, not infrastructure or experiments.
- **Canonical hierarchy:** AGENTS -> current PROJECT_STATE -> EXPERIMENT_INDEX ->
  relevant report -> TECHNICAL_OVERVIEW/code as needed. README is a public
  introduction. Chat history and ignored handoff/session files are not authoritative.
- **Reproduction boundary:** frozen source identity differs from current HEAD;
  local ignored artifacts and the external dataset are prerequisites, not clone
  contents. Exact ONNX checkpoint/export provenance remains unresolved.
  Implemented bicubic/ImageNet preprocessing is established and is not an
  unresolved implementation blocker.
- **History preservation:** the former mixed state/history document is retained
  as [historical snapshot](development/archive/PROJECT_STATE_20261007.md).
  Earlier log statements about uncommitted files, future execution or closure
  describe their recording time, not current status.
- **Evidence:** [current snapshot](PROJECT_STATE.md), [experiment index](EXPERIMENT_INDEX.md),
  [frozen result](benchmark/BENCHMARK_FINAL_RESULT.md) and
  [local-memory ablation](diagnostics/LOCAL_MEMORY_ABLATION_RESULT.md).
  No algorithm, numerical result, saved artifact or benchmark execution changes.

## D17 - Category-agnostic M0 evaluation infrastructure

Date: 2026-10-16. Infrastructure preparation, not a performance experiment.
M0 remains the Java baseline; M2 remains the retained Python localization experiment.
The core comparison workflow uses the shared 15-category resource, DatasetNinja
metadata and original image geometry. Historical diagnostics remain scoped to
their original categories. New campaigns generate their own manifests and never
overwrite frozen evidence.

Use lexical ordering followed by Java Random seeded shuffle, with fit count
`floor(4*N/5)` clamped to `[1,N-2]`; reject fewer than three normal images.
At seed 42 this reproduces the retained Bottle/Metal Nut manifests exactly.
Keep at least two calibration observations for sample variance. Projection seed,
preprocessing, scoring, interpolation and metric definitions remain unchanged.
See [execution contract](benchmark/FULL_DATASET_EXECUTION.md). The active next task
is dataset validation and a fresh full-dataset baseline; no such run is claimed here.

## D18 - Completed all-15 ablation selects M2; separate Java implementation

Date: 2026-10-18. Supersedes D17's active next-task and Python-only M2 status.
The local `m0-all15-baseline-20261016` and `local-models-20261017` campaigns are
complete. M2 leads the retained macro Image/Pixel/AUPRO results; exact V1 does
not outperform C1 on those macro metrics. Select M2 for Java implementation and
CPU-cost validation. This selection is based on the observed benchmark, not an
independent new generalization test or a robustness claim.

Preserve M0, its VSA/Adjoint invariant and historical results. Implement M2 in a
separate package, with FIT local diagonal statistics, HELD-OUT positional Z,
native max and unchanged localization/evaluation. Persist only inference state.
Require Python/Java fit, score and reload parity before timing; retained Bottle,
Metal Nut and Hazelnut satisfy that gate with zero quality-metric deltas.
Measure matched Java M0/M2 and separate stages on CPU; do not transfer M0's
historical latency to M2. Do not launch a new all-15 scientific campaign here.
See [the Java M2 report](benchmark/JAVA_M2_RESULT_20261018.md) for exact evidence,
execution-provider verification, commands and measurement status.

## D19 - Measure frozen Java M2 robustness before selecting a correction

Date: 2026-10-18. Explicit user authorization to measure controlled geometry and
photometry, superseding D18's active validation-only scope. Production M2 and M0
remain unchanged. The [pre-run protocol](experiments/M2_ROBUSTNESS_PLAN.md) fixes
all transforms, replication/bilinear sampling, calibration-maximum diagnostic
reference, paired summaries and the sole interior-ring diagnostic.

Completed all 15 categories, 467 normal tests, 19,147 image/level pairs, with
bit-identical identity maps and Java/Python calibration parity. No anomaly-quality
campaign or JMH rerun. [Result](benchmark/M2_ROBUSTNESS_RESULT_20261018.md) records
broad rotation/scale sensitivity, translation sensitivity and severe category-specific
photometric responses, especially Bottle. No operational FPR claim follows from
the diagnostic reference, whose original-normal exceedance is already substantial.

The evidence recommends exactly one subsequent rotation/scale canonicalization
ablation. This is a recommendation, not an implemented correction or an approved
multifix program; Bottle's photometric limitation remains explicit. No claim of
reflection robustness, alignment efficacy or changed CPU performance is made.

## D20 - Keep M2-P; test oracle geometry before pose estimation

Date: 2026-10-19. Explicit user instruction supersedes the retained
[spatial-correspondence report](benchmark/M2_SPATIAL_CORRESPONDENCE_RESULT_20261018.md)'s
recommendation to promote/refine N1. M2-P remains the selected Java detector;
N1/G remain historical experiments. Do not continue them or alter M2 mathematics.
Authorize only known rotation/scale perturbations followed by their exact inverse
parameters, with the existing resampling contract, normal images and frozen models.
No retraining, anomaly-quality rerun, JMH or real canonicalizer implementation.

Completed [oracle experiment](benchmark/M2_ORACLE_CANONICALIZATION_RESULT_20261019.md):
all 15 categories, 467 normals, 4,670 triples; 1,401 bit-exact identity maps and
4,670 exact retained score pairs. Verdict **B - PARTIALLY SUPPORTED**. Shrink/restore
scale recovers strongly, but texture rotations and enlargement round trips leave
important residuals. Interior exclusion reduces many errors without removing them;
simple RGB-error correlations do not establish border/resampling contributions.

One recommended next step: a controlled round-trip resampling/boundary diagnostic
on unchanged M2-P before implementing pose estimation. This is a recommendation,
not an implemented next phase. Production, prior artifacts and historical numerical
results remain unchanged; the older report's recommendation is preserved as history.

## D21 - Metal Nut pilot and Java canonicalization Phase 1

Date: 2026-10-19. Explicit user requests supersede D20's recommended diagnostic
and the pilot's phase-closing recommendation as active workflow. Preserve their
historical evidence. M2-P remains the selected detector; no N1/G continuation.

The [Metal Nut pilot](benchmark/M2_METAL_NUT_CANONICALIZATION_RESULT_20261019.md)
supports alignment plus refitting within its measured scope. Its12.308ms Python
cost failed its frozen2ms gate; the subsequent user instruction states that this
gate is not a product requirement. No retroactive change to the pilot's NO-GO,
metrics, timing or artifacts is intended.

Authorize only J0 geometry parity, J1-lite retained reference bridge and J2 scalar
B/C/D tensor infrastructure/correctness from the
[architecture review](experiments/JAVA_CANONICALIZATION_OPTIMIZATION_PLAN.md).
The [Phase 1 checkpoint](experiments/JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE1.md)
passes all423 real geometry cases and eight retained encodings/four maps, with
167 Java and40 Python tests passing. The prepared encoder seam preserves legacy
outputs; M2 math, M0, preprocessing and historical scientific results are preserved.
Strict historical source guards remain intact and reject changed source identity.

Stop before refitting/scientifically selecting B/C/D, full Metal Nut/all-category
quality runs, timing campaigns, production localization or commits. C/D scientific
qualification is the next separately authorized task. Canonical bundle identity,
fast detector integration and optional localization remain later checkpoints;
the new preprocessing IDs alone do not make old archives compatible.

## D22 - Metal Nut Phase 2 qualifies B/C/D and selects B

Date: 2026-10-19. Explicit user request supersedes D21's stop before scientific
qualification and permits B to win on evidence. Freeze the
[Phase 2 protocol](experiments/JAVA_CANONICALIZATION_PHASE2_PROTOCOL.md) before
evaluation, preserve all Phase 1 production definitions and fit/calibrate each
candidate independently on176/44 normals, with115 tests and88 source rotations.

[Completed result](experiments/JAVA_CANONICALIZATION_IMPLEMENTATION_PHASE2.md):
all three pass the six full-precision gates. Select **B/fused bilinear** for the
next Metal Nut canonical integration checkpoint. B/C meet the frozen practical
near-equivalence margins; B is simpler and cheaper with slightly better AUPRO,
coherence and aggregate rotation delta. C has a small Image AUROC advantage;
D has tighter FIT distributions/lower signed median rotation shifts but higher
cost, lower primary quality and a worse+10-degree tail. Preserve those tradeoffs.

Measured total preprocessing medians are B6.729813,C8.847606,D20.321069ms,
statistics of batch averages in an early matched three-JVM harness. They are not
complete detector or JMH latency; no2ms gate or summed timing claim is applied.
One CSV summary parsing error was repaired after all measurements, with original
source versions/hashes retained. No scientific/timing body changed or reran.

Production M0/M2 and default behavior remain unchanged. Recommend only the next
separately authorized B canonical bundle/fast detector checkpoint. No production
localization, optimization, other categories, new detector or commit is included.

## D23 - Integrate qualified Metal Nut B with a distinct canonical M2 bundle

Date: 2026-10-20, checkpoint authorized2026-10-19. Explicit user instruction
supersedes D22's integration stop. B/fused bilinear is fixed for this checkpoint;
C/D are not production integration candidates. Preserve geometry, sampling,
encoder bytes, M2 equations, calibration and metrics. No optimization, production
backprojection, all-15 run or commit is authorized here.

[Completed integration](experiments/JAVA_CANONICAL_M2_INTEGRATION.md): a separate
versioned Metal Nut canonical bundle validates every preprocessing field plus
encoder/state hashes. FIT176/CAL44 and inference share the same Java B boundary.
Fresh state, all115 native maps/fast scores and three quality metrics reproduce
retained Phase 2 B exactly. The fast API returns native max; a second API owns
only native Z, transform, dimensions and identity for deferred localization.
Geometry failure throws before encoding; no stale tensor or unaligned fallback.
Sequential workspaces require separate instances or external serialization for
concurrent callers. Legacy M2 loading/default behavior and M0 remain unchanged.

Matched warm-JVM CPU JMH directly measures fast-decision p50/mean/p95
10.108928/10.042803/12.315034ms. Geometry p50 4.718592ms is the largest measured
preprocessing component; B tensor p50 is1.404928ms. No summed-latency claim or
arbitrary latency gate is applied. One preprocessing fork failed before measurement
because Windows reused a PID; only that missing fork was replaced. Original
results/source are retained; a later untimed evidence-directory fix plus regression
test does not change measured bodies.180 Java/44 Python tests pass; node profiles
verify CPU execution despite Azure appearing among available providers.

Exactly one next optimization target: Metal Nut geometry, profiling its internal
costs first under the frozen scientific contract. This is a recommendation for a
separately authorized task, not an optimization implemented in this checkpoint.
Production original-frame localization and other-category canonicalization remain
deferred. ONNX export/checkpoint provenance remains unresolved.

## D24 - Measure geometry composition before optimizing acquisition

Date:2026-10-20. Explicit measurement-only request follows D23; no production
geometry, sampler, encoder, M2, API or persistence change is authorized here.
The [geometry profile](experiments/JAVA_CANONICAL_GEOMETRY_PROFILE.md) retains
two-fork JMH controls/components, same9 BGR fixtures, JFR and all423 frozen
geometry cases. Production source and all captured source/input hashes are
unchanged;183 Java tests pass. No quality/all-category campaign or commit.

Unchanged whole geometry p50/mean/p95 is5.537792/7.061223/16.363520ms under this
campaign's conditions, with broad tails. Bulk getRGB p50 4.243456ms is the largest
scope; luminance/BOX and component traversal follow. JFR corroborates generic
raster/color conversion. Separate timings and percentages are not an additive
budget, and the older integrated timing is not overwritten or treated as a
matched performance regression.

Narrow D23's next target to exactly one first optimization: contract-preserving
TYPE_3BYTE_BGR source acquisition replacing generic bulk getRGB. Its roughly4ms
typical measured scope is an optimistic scope-removal ceiling, not promised
savings or a detector latency projection; required replacement work remains
unmeasured. This recommendation requires separate implementation authorization.
Geometry mathematics, topology, rounding, affine conventions and scientific
behavior remain frozen. No raster fast path or geometry optimization is performed.

## D25 - Replace validated BGR acquisition; preserve the scientific contract

Date:2026-10-20. Explicit user instruction authorizes exactly D24's first source
acquisition optimization. The [completed result](experiments/JAVA_CANONICAL_BGR_FAST_PATH.md)
uses direct acquisition only for validated built-in TYPE_3BYTE_BGR layouts, with
generic getRGB fallback. One packed source buffer still serves the unchanged
geometry estimator and B sampler. A shared direct BGR source interface would
change both qualified implementations; choose the explicitly permitted minimal
packed replacement and measure it first. No other optimization is included.

The logical default-sRGB ARGB contract remains exact, including subimage, padded,
translated and nonzero data-buffer-offset cases. The JDK interleaved raster's
actual getDataElements addressing is authoritative; tests caught and corrected an
initial extra-offset error before real parity/timing. Assertions/tolerances were
not weakened. All423 real inputs reproduce pixels through scores exactly; all115
TEST outputs/metrics reproduce the existing bundle, with no refit.187 Java tests
pass. Geometry, B sampling, encoder, M2 equations/calibration, bundle identity,
default unaligned M2 and M0 are preserved. No production localization or commit.

Two-fork JMH directly measures complete detectScore p50/mean/p95
4.423680/4.363740/5.816320ms vs matched generic10.272768/10.173606/12.992512ms:
56.94/57.11/55.23% lower. Historical integration10.108928/10.042803/12.315034ms
remains separate evidence. Acquisition p50 falls4.141056->0.343040ms; new geometry
and preprocessing p50 are1.146880/2.359296ms. These scopes are not an additive
budget; sequential trials and material fork variation limit universal speedup
claims. All155 source/resource and1,617 retained input hashes stay stable;16 CPU
profiles verify400 CPU node events. No source change follows measurement.

The approximately306KB/op decision allocation remains; encoder/descriptor/M2/ORT
ownership optimization is explicitly deferred. Recommend only measuring remaining
B tensor sampling within optimized preprocessing before selecting another change.
Other-category canonicalization, localization and exact ONNX export/checkpoint
provenance remain unresolved/deferred as previously recorded.

## D26 - Screen category geometry before generalizing the canonical detector

Date:2026-10-20. Explicit user instruction requests a six-category generality audit
and defers D25's next performance investigation. No production optimization,
sampler/CNN/M2/calibration/persistence change or new category integration is included.
The [audit](experiments/CANONICALIZATION_GENERALITY_AUDIT.md) and
[frozen protocol](experiments/CANONICALIZATION_GENERALITY_AUDIT_PROTOCOL.md)
separate a reusable pose->B->encoder->strict-local architecture from the estimator,
symmetry, failure and framing assumptions that remain category specific.

Metal Nut H4 is GO/already qualified; all 176 FIT descriptors reproduce retained B
exactly. Bottle center and Screw axis/polarity are MAYBE. Bottle is end-on/circular,
not a side-view head-bottom silhouette; its centering gain is tiny and two anomalous
contaminants trigger border failure. Screw has strong recovery, all160 trusted TEST
poses and a paired cosine gain .469488->.782811, but every valid FIT silhouette
clips under fixed scale1 centroid placement and four training sources touch the
source border. Do not treat small clipped area as preservation of tip information.

Tested Hazelnut center/axis, Cable rigid registration and whole-object Grid pose
are NO-GO. Hazelnut centering worsens cosine .711013->.667944, adding AXIS worsens
matched center .660303->.624708, and bright print can become the foreground. Cable
has mostly weak axes, negligible centering gain and a defect-sensitive broken-ring
anchor. Grid has no finite object centroid. A successful numeric pose and controlled
equivariance do not prove correct semantic registration or anomaly preservation.

One audit-adapter grayscale conversion discrepancy was diagnosed with a red/green
logical-pixel identity test before features/TEST. The unsuffixed geometry-only run
retains its original source/protocol/outputs; the corrected `-validated` run alone
supports screening. Geometry rules were not tuned on TEST. Final evidence retains
162 stable source/resource and 2,347 input hashes;197 Java tests pass. No new M2
fit/calibration/score or anomaly metric was computed; M0 and production M2 remain.

Immediate new M2 qualifications: none. Recommend exactly a **Screw FIT-only framing
feasibility checkpoint**, keeping axis/polarity/B fixed and source-border cases
accounted for, before separately authorizing full M2 qualification. Reject further
rotation work for the audited near-radial/cross-section/texture strategies in this
phase; no universal estimator/framework or production promotion is justified.

## D27 - Screw fixed placement passes the framing checkpoint

Date:2026-10-20. The [FIT-first framing checkpoint](experiments/SCREW_CANONICAL_FRAMING_FEASIBILITY.md)
completes D26's recommended prerequisite. Audited Screw geometry, logical-RGB700
view, B sampler, CNN and M2 remain unchanged. At scale1 a single global target
`(395.50859765961616,349.2832413193618)` contains all253 valid FIT silhouette
supports with8.712pixels minimum clearance. Centered scaling would instead
require<=.8932347449 for the frozen4-pixel margin; it is not selected.

FIT-only contract SHA-256 is
`e4af7c946a0ac980a12936541e2825b262e8ff6082b52690613a14b214ee6928`.
Subsequent63 valid CAL cases have no clipping and>=7.310pixels clearance.
One CAL head extent exceeds the empirical FIT maximum by1.402pixels but remains
inside the frozen margin; no parameter is adjusted. Matched safe FIT cosine
.784784 retains the audited registration benefit; LOO same-cell gain retains95.91%.

**GO_FOR_M2_QUALIFICATION**, not production promotion. Explicit source-border
policy: reject before CNN/no score/no unaligned fallback, retain failures and
coverage, never silently report survivor-only metrics as complete validation.
All256/64 training identities remain accounted for, including3/1 source-border
failures. The reduced silhouette remains a proxy for semantic object support.

Next is separately authorized/frozen Screw preprocessing identity and unchanged
M2 qualification on the original FIT/CAL/TEST manifests, with all failures reported.
No fitting/calibration/scoring, TEST pixel access, anomaly metrics, new timing,
localization, generalized registry or production source change occurred here.
206 Java tests pass;165 source/resource hashes remain stable. D26's original
generality findings and historical reports remain evidence for their recorded
framing, not silently reinterpreted as safe-frame detector results.
