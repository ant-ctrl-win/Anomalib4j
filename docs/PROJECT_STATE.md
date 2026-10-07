# Anomalib4j: stato canonico corrente

Riconciliazione tecnica del 2026-10-02; aggiornamento del perimetro benchmark del 2026-10-03. Repository: directory radice del progetto Anomalib4j (checkout locale).

Questo documento descrive il workspace ispezionato, inclusi file non ancora versionati. Sostituisce come fotografia corrente il vecchio `PROJECT_STATE.md` nella radice; non modifica il contratto di `AGENTS.md`. Codice, test e artefatti prevalgono sulla memoria dell'handoff locale (non pubblicato). I report storici restano riferimenti dei singoli esperimenti.

## Stato corrente (riconciliazione finale, 2026-10-07)

**DECIDED / VERIFIED:** fase scientifica chiusa; presentazione portfolio e panoramica tecnica consolidate il 2026-10-07. Pubblicazione/commit restano separati.

- **benchmark comparativo completato** (`final-comparison-20261003`, congelato e invariato);
- **indagine post-hoc mirata completata** (due diagnosi verificate, interne e post-hoc);
- **riconciliazione finale della documentazione / presentazione portfolio** completata; nessun commit o push in questa attivita.

Nessun ulteriore esperimento scientifico e richiesto per questa fase. **M2 e una baseline sperimentale alternativa, non il modello di produzione v2 selezionato.** Il benchmark congelato resta invariato.

### Sintesi A / B / C

**A. Compilazione dello score.** Il contributo validato resta: apprendere una memoria VSA, compilare algebricamente il suo score affine e valutare lo scorer equivalente direttamente nello spazio delle feature CNN. Score VSA esplicito e scorer compilato restano numericamente equivalenti entro l'errore floating-point documentato.

**B. Modello M0.** Il detector del benchmark e una baseline di prova volutamente semplice. Il suo archetipo posizionale codifica una direzione media posizionale standardizzata rispetto alla popolazione globale pooled; **non** modella esplicitamente la dispersione nominale entro posizione. E una scelta di modellazione, non un errore della compilazione Adjoint.

**C. Risultato post-hoc M2.** Su feature CNN congelate e sugli split esistenti, l'ablazione interna verificata ha trovato che M2 (media locale CNN piu deviazione standard per posizione/coordinata) migliora la localizzazione su Bottle e Metal Nut. Dopo la calibrazione Z posizionale, M1 controlla gia la distanza isotropa dalla media non unitaria; la differenza M1-Z vs M2-Z sostiene quindi l'utilita della pesatura anisotropa per coordinata nelle condizioni misurate. E un risultato interno, post-hoc e su categorie gia osservate: **non** e prova indipendente di generalizzazione e **non** sostituisce il benchmark congelato.

Supporto canonico: [POSITIONAL_GLOBAL_VARIANCE_DIAGNOSTIC.md](diagnostics/POSITIONAL_GLOBAL_VARIANCE_DIAGNOSTIC.md) e [LOCAL_MEMORY_ABLATION_RESULT.md](diagnostics/LOCAL_MEMORY_ABLATION_RESULT.md). Riconciliazione: [FINAL_POSTHOC_RECONCILIATION.md](development/FINAL_POSTHOC_RECONCILIATION.md).

**Nota sulle affermazioni storiche.** I passaggi sotto che descrivono il benchmark come \"da eseguire\", il perimetro come \"una sola categoria\" o la domanda \"VSA vs memoria CNN diretta\" come completamente non verificata sono **fotografie di fasi precedenti**, superate dal run finale e dall'indagine post-hoc. Restano come storia del progetto; lo stato corrente e quello sopra.

La gerarchia di lettura corrente e [README italiano](../README.md), [traduzione inglese](../README.en.md), [panoramica tecnica](TECHNICAL_OVERVIEW.md), poi report di evidenza. Le note storiche su Git, persistenza e vecchie priorita non descrivono il workspace attuale.

## Significato degli stati

- **VERIFIED:** riscontro nel codice o negli artefatti conservati. Per misure storiche significa verifica documentale, non nuova esecuzione.
- **IMPLEMENTED:** componente presente; non implica validazione di ogni possibile uso.
- **DECIDED:** scelta sostenuta dal contratto, dal protocollo implementato e documentato o da una conferma esplicita dell'utente.
- **HYPOTHESIS:** spiegazione o beneficio da verificare sperimentalmente.
- **OPEN:** evidenza o decisione ancora mancante.
- **REJECTED:** alternativa scartata nel perimetro specificato.
- **DEFERRED:** lavoro esplicitamente rinviato; nessuna autorizzazione implicita a implementarlo.
- **PROPOSED / TO VERIFY:** configurazione candidata da verificare e congelare prima dell'esecuzione.
- **FUTURE_WORK:** escluso dalla chiusura di questa fase, salvo necessita documentata del benchmark.

## Obiettivo e decisioni storiche — HISTORICAL / SUPERSEDED ove indicato

**DECIDED:** esplorare `CNN -> VSA learning/memory -> compiled CNN-space scoring`, preservando l'equivalenza numerica tra confronto esplicito VSA e filtro Adjoint. Java/ONNX e la separazione tra apprendimento e inferenza fanno parte dell'implementazione corrente.

**VERIFIED:** la riconciliazione documentale richiesta e completata. Non sono stati rieseguiti training, test o benchmark, ne modificati sorgenti in questa attivita.

**DECIDED, conferma esplicita dell'utente del 2026-10-02:** il task corrente canonico e **definire ed eseguire il primo benchmark comparativo CPU sullo stesso hardware di Anomalib4j contro baseline esistenti appropriate, inizialmente PaDiM/PatchCore, senza ulteriori ottimizzazioni interne preventive**. L'ottimizzazione interna e stata completata e il filone performance interno e chiuso per questa fase. Il benchmark comparativo non viene eseguito in questo aggiornamento documentale.

Questa decisione e successiva a `POST_BENCHMARK_STRATEGY.md` e ne supera l'ordine operativo proposto. E coerente con le sezioni 11.7 e 16.4 dell'handoff locale (non pubblicato). **OPEN / DEFERRED (poi parzialmente testata):** `VSA vs direct latent-space memory/prototype` resta una questione di ricerca, non il task corrente ne un prerequisito al confronto esterno. L'ablazione post-hoc di questa fase ha misurato baseline CNN locali dirette (M1/M2) e dispersione dopo proiezione VSA (M3) e le ha confrontate con M0 sulle stesse feature congelate; si veda [LOCAL_MEMORY_ABLATION_RESULT.md](diagnostics/LOCAL_MEMORY_ABLATION_RESULT.md).

**DECIDED, 2026-10-03:** il benchmark finale comprende esclusivamente `bottle` e `metal_nut`, Anomalib4j e le implementazioni candidate PaDiM/PatchCore di Anomalib 2.6.2. Confronto primario Native/System CPU: preprocessing, backbone, detector e runtime propri, configurazioni congelate prima dei risultati e uguali fra categorie. Il confronto controllato resta SECONDARY / DEFERRED. Specifica in [BENCHMARK_CONTRACT.md](benchmark/BENCHMARK_CONTRACT.md).

**VERIFIED / DECIDED:** censimento locale riconfermato: Bottle 209 normal (167 fit + 42 calibrazione), 83 test; Metal Nut 220 normal e 115 test (22 good/93 anomalie), 700x700. Per Metal Nut e deciso lo split 176 fit + 44 calibrazione, sort filename e shuffle Java Random(42), con identica architettura SPATIAL_14 e positional Z. E una specifica nuova, non un modello o risultato gia validato. PaDiM/PatchCore possono usare rispettivamente tutte le 209/220 normal per fitting nativo: allocazioni differenti, non protocolli equivalenti.

**DECIDED:** dopo il confronto, sintetizzare risultati e limiti, preparare README/repository per presentazione e chiudere questa fase del progetto. Nuove categorie, ottimizzazioni e questioni scientifiche sono FUTURE_WORK salvo indispensabili al benchmark. Non e richiesto dimostrare superiorita di Anomalib4j.

**VERIFIED:** l'attivita del 2026-10-03 produce soltanto il contract e la riconciliazione documentale. Nessuna installazione o implementazione competitor, nessuna esecuzione di training/test/benchmark. Configurazioni esterne e adattatori sono ancora da verificare; vedere [resoconto](benchmark/BENCHMARK_CONTRACT_RECONCILIATION.md).

**VERIFIED, successiva Phase 1 del 2026-10-03:** su nuova autorizzazione esplicita e stato creato l'ambiente CPU isolato Anomalib 2.6.2 dal tag `lib/v2.6.2`, commit `cc5f400a4a4b1b14b5a3ee5078063c250a8f522e`, usando il lock upstream. PaDiM/PatchCore verificati nel sorgente e in un piccolo smoke offline solo train-good: fit completabile, raw float32 finiti e geometria 256x256 verso 900/700 chiusa. Pesi/hash, freeze, manifest split Java 167+42/176+44 e monitor memoria Windows conservati. Nessun risultato comparativo o modifica Java. [Report pre-run](benchmark/BENCHMARK_PRERUN_VERIFICATION.md).

**DECIDED / prossimo task non eseguito:** Phase 2, implementazione minima del runner secondo il contract, poi run finale separato. Rimangono da acquisire manifest runtime per ciascun run, costi/memoria a budget completo e controlli dell'adattatore/evaluator; non riaprono la scelta di modello o iperparametri. Nessuna nuova decisione scientifica.

**VERIFIED, preparazione Phase 2 del 2026-10-03:** i `FIX_BEFORE_RUN` semplici dell'audit Phase 1 sono chiusi senza eseguire il benchmark ne il runner: equivalenza numerica del resize evaluation-space Python/Java (9 fixture, max diff 7.1e-15, tolleranza 1e-9), parser categoria/difetto generalizzato a Bottle e Metal Nut, percorso di fit nativo competitor verificato dal sorgente 2.6.2, gate offline+hash pesi (`tools/prerun/offline_env.py`), formato mappe lossless congelato `npy+json/v1` con lettore Java, e cattura read-only dell'identita del workspace. Metriche, iperparametri, contract e dataset invariati. [Report preparazione](benchmark/BENCHMARK_PHASE2_PREPARATION.md).

**VERIFIED, implementazione Phase 2 del 2026-10-03:** runner comparativo minimo implementato e cablato per tutti e sei i casi method/categoria (Bottle/Metal Nut x anomalib4j/PaDiM/PatchCore), senza eseguire il benchmark finale. Adattatore Java basato sui manifest con fit/reload/export ridotto, evaluator armonizzato su score/immagine nativo e mappe evaluation-space, worker Python su Anomalib 2.6.2 nativo, timing JMH/Python e monitor memoria. Test Java mirati (19 test) e test Python (`tools/comparison/test_runner.py`, 4 test) passati; smoke ridotti fit+infer su tutti i casi, latency abbreviata e memory smoke con processi terminati. Il monitor memoria distingue missing/null+reason e non inventa zeri; sintesi `summary-by-category.csv`/`macro-summary.csv`/`RESULT.md` con `null`+reason. Classificazione: READY_FOR_FINAL_RUN; resta solo il run scientifico finale. [Implementazione](benchmark/BENCHMARK_PHASE2_IMPLEMENTATION.md), [audit completamento](benchmark/BENCHMARK_PHASE2_COMPLETION_AUDIT.md).

**VERIFIED, run comparativo finale del 2026-10-03:** eseguito il benchmark scientifico completo, run-id `final-comparison-20261003`, senza smoke e senza modifiche a codice, configurazioni, split o preprocessing. Sei casi method/categoria a budget pieno (Bottle test 83, Metal Nut test 115; Anomalib4j 167+42/176+44, PaDiM/PatchCore 209/220), evaluator armonizzato unico, latenza JMH/Python e monitor memoria. Bottle Anomalib4j riproduce esattamente la baseline canonica (Image 1,0; Pixel 0,9616640201342855; AUPRO 0,8774472899967225), nessuna regressione. Missing di memoria e celle mancanti restano `null`+reason, mai zero. Nessun download, nessun file storico sovrascritto. [Risultato](benchmark/BENCHMARK_FINAL_RESULT.md).

## Architettura e invarianti

**IMPLEMENTED / VERIFIED:** Java 21, Maven; ONNX Runtime 1.30.0 in produzione. JUnit Jupiter 5.11.4 e Jackson 2.21.7 nei test; JMH 1.37 nel profilo `benchmark`.

Configurazione Bottle di riferimento:

| Parametro | Valore |
| --- | --- |
| Encoder frozen | MobileNetV4, `SPATIAL_14` |
| Feature map | HWC, 14 x 14 x 96; 196 posizioni |
| Proiezione | Rademacher densa, D = 10000, seed = 42 |
| Normalizzazione CNN | L2, epsilon = 1e-6 |
| Statistiche VSA | Welford, varianza campionaria, floor std = 1e-8 |
| Memoria | un archetipo continuo L2 unitario per posizione |
| Raw discrepancy | `1 - compiledScore`; non limitata a [0,1] |
| Image score scientifico | massimo delle 196 discrepanze raw o Z |

**DECIDED:** per patch x e archetipo A, con statistiche VSA mu/sigma:

```text
x_n = x / max(||x||, epsilon)
y = R x_n / sqrt(D)
z = (y - mu) / sigma
score_explicit = A^T z
q = A / sigma
W = R^T q / sqrt(D)
b = -mu^T q
score_compiled = W^T x_n + b
```

La compilazione trasferisce lo score nello spazio CNN: non ricostruisce un vettore CNN mediante un'inversa di R. Non serve approssimare la norma di Rx per questa identita. Nessuna binarizzazione o forma quadratica nella hot path.

**IMPLEMENTED:** `NormalImageTrainer` costruisce statistiche e memoria da immagini normali; `PositionalMemoryBuilder` accumula vettori standardizzati e normalizza il bundling; `AdjointCompiler` produce i filtri. Array interni protetti nelle banche immutabili. Sessione ONNX riutilizzata dall'encoder.

### Componenti e collocazione

| Stato | Componenti | Collocazione relativa a `src/` |
| --- | --- | --- |
| IMPLEMENTED | GridShape, ModelDescriptor, NormalizationPolicy | `main/java/io/github/antctrlwin/anomalib4j/model/` |
| IMPLEMENTED | ProjectionStrategy, DenseRademacherProjection, NormalizedPatchProjection | `main/java/io/github/antctrlwin/anomalib4j/projection/` |
| IMPLEMENTED | Welford, ProjectionStatistics, PositionalMemoryBuilder/Bank | `main/java/io/github/antctrlwin/anomalib4j/memory/` |
| IMPLEMENTED | AdjointCompiler/FilterBank, PatchScoreKernel | `main/java/io/github/antctrlwin/anomalib4j/{adjoint,inference}/` |
| IMPLEMENTED | HeatmapEngine/Result/Calibration, HeatmapCalibrator | `main/java/io/github/antctrlwin/anomalib4j/{inference,calibration}/` |
| IMPLEMENTED | Encoder, preprocessing, NormalImageTrainer, scorer esplicito e diagnostica rho | `main/java/io/github/antctrlwin/anomalib4j/{onnx,training,validation}/` |
| IMPLEMENTED | Metriche, maschere, overlay, positional Z, held-out e multi-seed | `test/java/io/github/antctrlwin/anomalib4j/evaluation/` |
| IMPLEMENTED | PerformanceBaseline, EncoderMicroprofile, PreprocessingMicroprofile, equivalenza OLD/NEW | `jmh/java/io/github/antctrlwin/anomalib4j/` |

**VERIFIED, distinzione importante:** la positional Z scientifica e implementata da `PositionalRawCalibration` negli helper di test e riutilizzata dai benchmark. Non e la `HeatmapCalibration` di produzione. `HeatmapEngine` conserva il raw, ma produce anche un buffer visivo clamped [0,1], con soglie globali se fornite. Il protocollo scientifico usa raw/Z senza clamp; non va sostituito con quel buffer visivo.

## Modelli e preprocessing

**VERIFIED:** presenti entrambi gli encoder ONNX; SHA-256 ricalcolati durante questa riconciliazione:

| Asset in `src/main/resources/models/` | Output | SHA-256 |
| --- | --- | --- |
| `mobilenetv4_spatial_14x14.onnx` | NHWC 1x14x14x96 | `48da88a8da5f1f1f1ce2308d19128c0f302498aa1889882263620cad21bdd26e` |
| `mobilenetv4_spatial_28x28.onnx` | NHWC 1x28x28x64 | `23ee797ff10396f5eab8b4f00bec3520a573246448262f3c07c0a88f44e9aaf3` |

Output/contratto: [ONNX_MODEL_CONTRACT.md](architecture/ONNX_MODEL_CONTRACT.md) e `OnnxMobileNetV4Encoder`. L'hash identifica i byte locali; non dimostra da solo la provenienza dal checkpoint timm dichiarato. **OPEN:** catena completa checkpoint/export e riproducibilita esterna dei pesi.

**VERIFIED:** input float NCHW 1x3x224x224, RGB, resize bicubic, scaling /255 e mean/std ImageNet. Identita `imagenet-rgb-resize224-bicubic-v1`. Nessun nuovo crop o alignment.

**IMPLEMENTED / VERIFIED:** preprocessing ottimizzato con rendering diretto verso TYPE_INT_RGB 224x224 per istanze esatte di `BufferedImage`, TYPE_INT_RGB o TYPE_3BYTE_BGR, sRGB senza alpha. Altri casi conservano conversione full-resolution via getRGB/setRGB. Lettura del raster DataBufferInt del target, stessa aritmetica di normalizzazione.

**VERIFIED:** CSV `target/benchmark/preprocessing-equivalence.csv`: per variante guarded e produzione, 318 casi, 47.867.904 float confrontati, zero differenze, maxAbsDiff = meanAbsDiff = 0. Casi: 24 combinazioni sintetiche, 2 subimage e 292 immagini Bottle (209+83). Validazione empirica sul runtime usato, non prova per ogni ColorModel/JVM possibile.

**REJECTED:** rendering diretto indiscriminato su tutti i tipi: 1.331.179 float differenti, maxAbsDiff 4.464285850524902; incompatibilita su alpha/grayscale. Il fallback fa parte della correttezza, non e lavoro residuo.

**HISTORICAL / SUPERSEDED:** la precedente assenza di persistenza completa e superata dal percorso ComparisonModel del benchmark: stato serializzato e reload con RAW/Z identici sono documentati nel [risultato finale, sezione 8](benchmark/BENCHMARK_FINAL_RESULT.md). Gli asset ONNX restano gli encoder; il modello appreso e conservato separatamente negli artefatti locali del run.

## Evidenze scientifiche

**VERIFIED:** report/artefatti Bottle: 209 training-good, 83 test (20 good, 63 anomalie: 20 broken_large, 22 broken_small, 21 contamination). Le convenzioni di localizzazione contano 68 regioni, 3.886.731 pixel foreground e 63.343.269 background. Dataset esterno: `<DATASET_ROOT>/mvtec-ad-DatasetNinja`; vedere [censimento](benchmark/MVTEC_DATASET_CENSUS.md).

| Esperimento | Image AUROC | Pixel AUROC | AUPRO@0.30 |
| --- | ---: | ---: | ---: |
| SPATIAL_14 full-209 raw | 0.9857142857142858 | 0.9569495750935939 | 0.8653474657258569 |
| SPATIAL_14 full-209 Z in-sample | 1.0 | 0.9611017698505884 | 0.8766695388953096 |
| SPATIAL_14 raw-167, split seed 42 | 0.9857142857142858 | 0.9569268263721693 | 0.8650976456361172 |
| SPATIAL_14 Z-167+42, split seed 42 | 1.0 | 0.9616640201342855 | 0.8774472899967225 |
| SPATIAL_28 full-209 raw | 1.0 | 0.9014023353324444 | 0.7055561359083521 |

Fonti: [Sprint 7](development/SPRINT7_RESULT.md), [Sprint 8](development/SPRINT8_RESULT.md), [Sprint 9](development/archive/SPRINT9_RESULT.md), [in-sample](benchmark/POSITIONAL_CALIBRATION_RESULT.md), [held-out](benchmark/HELDOUT_CALIBRATION_RESULT.md), CSV `target/bottle-spatial14-heldout-calibration/metrics.csv` e rispettive directory di valutazione.

**VERIFIED:** held-out: ordinamento filename, `Collections.shuffle` con `Random(seed)`, prime 167 per statistiche VSA/archetipi/filtri, ultime 42 esclusivamente per calibrazione. Z usa media e std campionaria per posizione, floor 1e-6, senza clamp/smoothing/soglie. Confronto causale appaiato: Z-167+42 meno raw-167; non full-209.

**VERIFIED:** cinque split seed 1..5, proiezione sempre seed 42:

| Metrica | Media raw | Media Z | Media delta | Min delta | Max delta |
| --- | ---: | ---: | ---: | ---: | ---: |
| Image AUROC | 0.985714286 | 1.0 | 0.014285714 | 0.014285714 | 0.014285714 |
| Pixel AUROC | 0.956977480 | 0.960423021 | 0.003445541 | 0.001457293 | 0.005024785 |
| AUPRO@0.30 | 0.865513312 | 0.876453205 | 0.010939893 | 0.005243657 | 0.016051970 |

Deviazione campionaria delta: image 0; pixel 0.0016604095; AUPRO 0.0042791739. Tutti i segni sono positivi. Fonte: [report multi-seed](benchmark/HELDOUT_MULTI_SEED_RESULT.md) e `target/bottle-spatial14-heldout-multiseed/{summary,per-seed,delta-signs}.csv`. Sono split sovrapposti sullo stesso test, non cinque dataset indipendenti.

**VERIFIED:** equivalenza explicit/compiled: Sprint 6 circa 9.95e-13; Sprint 7 massimo 1.3073986337985843e-12 sulle 16.268 celle delle 83 immagini, entro 1e-9. Questo verifica l'algebra implementata sui dati testati; non dimostra superiorita scientifica su altri metodi.

**DECIDED / VERIFIED:** convenzioni in [LOCALIZATION_CONVENTIONS.md](benchmark/LOCALIZATION_CONVENTIONS.md): bitmap GT uniti; ROC pixel globale incluse good, pareggi raggruppati; componenti 8-connesse con peso uguale per PRO; FPR su tutto il background; integrazione esatta fino a 0.30 e divisione per 0.30. Upsampling bilineare half-pixel con bordi replicati; Z prima dell'upsampling. Colori degli overlay solo diagnostici.

**VERIFIED:** SPATIAL_28 peggiora la localizzazione per tutti e tre i difetti ([analisi per difetto](diagnostics/SPATIAL14_VS_SPATIAL28_PER_DEFECT.md)). Cambia anche stage CNN e numero canali (64 contro 96): non e un'ablazione della sola risoluzione. Interpretazioni qualitative in [PATCH_GRID_OVERLAY_REVIEW.md](diagnostics/PATCH_GRID_OVERLAY_REVIEW.md) non provano causalita.

## Prestazioni correnti e limiti delle misure

**VERIFIED:** JSON `target/benchmark/preprocessing-optimized-full.json`, coerente con [PREPROCESSING_OPTIMIZATION_RESULT.md](development/archive/PREPROCESSING_OPTIMIZATION_RESULT.md):

| Percorso | p50 | p95 | p99 |
| --- | ---: | ---: | ---: |
| Preprocessing completo | 1.380352 ms | 1.456128 ms | 1.73027328 ms |
| Encoder extract completo | 2.752512 ms | 3.387392 ms | 4.0820736 ms |
| Detection: encoder + Adjoint + Z + max | 2.789376 ms | 3.489792 ms | 4.29088768 ms |
| Localization: detection + upsampling | 7.462912 ms | 8.847360 ms | 10.141696 ms |

**VERIFIED:** baseline corrente riconfermata dai report: preprocessing p50 circa 1.380 ms, `fullExtract` 2.753 ms, `detectionInference` 2.789 ms, `localizationInference` 7.463 ms; compiled Adjoint p50 12 us per 196 celle da [PERFORMANCE_BASELINE_RESULT.md](development/PERFORMANCE_BASELINE_RESULT.md). La misura Adjoint resta quella della precedente run JMH, non una nuova misura nella run ottimizzata a quattro percorsi.

Hardware/runtime documentati: AMD Ryzen AI 9 HX 370, Windows 11, Temurin Java 21.0.9, ORT 1.30.0. JMH SampleTime, un thread chiamante, batch 1, warmup 5x2s, measurement 10x2s, 2 fork. Fixture Bottle good 000 gia in RAM. Modello held-out 167+42 seed 42 preparato in setup. Disk I/O, decode e model building esclusi dalle regioni cronometrate.

**VERIFIED:** vecchi p50 detection 19.824640 ms, localization 24.444928 ms, fullPreprocessing 12.271616 ms (microprofile, come riportato nel confronto OLD/NEW). Rapporti vecchio/nuovo circa 7.107x, 3.276x, 8.890x. Il guadagno end-to-end e misurato, non ricavato sommando tempi isolati.

**VERIFIED, storico:** [PERFORMANCE_BASELINE_RESULT.md](development/PERFORMANCE_BASELINE_RESULT.md) misura Adjoint circa 12 us/196 celle, positional Z circa 0.2 us, upsampling circa 3 ms. Non sono nuove misure della run ottimizzata a quattro benchmark. [ENCODER_MICROPROFILE_RESULT.md](development/archive/ENCODER_MICROPROFILE_RESULT.md): session.run circa 0.55 ms nel suo perimetro. [PREPROCESSING_MICROPROFILE_RESULT.md](development/archive/PREPROCESSING_MICROPROFILE_RESULT.md) documenta getRGB/setRGB e conversione full-size come costo dominante precedente.

**VERIFIED, confronto interno:** Sprint 7 explicit VSA circa 651 ms/map contro compiled circa 13 us/map. Confronta due percorsi dello stesso modello. Non misura un vantaggio contro PaDiM, PatchCore o EfficientAD.

**OPEN / limiti:** un thread JMH non impone un solo worker nativo ORT; session options correnti non fissano intra/inter-op a 1. I percentili sono del workload ripetuto, non un SLA o throughput sostenuto. Non sommare/sottrarre mediane isolate per stimare il costo puro della CNN. Nessuna equivalenza statistica formale fra i vecchi 13 us e JMH 12 us.

## Test, riproducibilita e stato Git — fotografia storica

**VERIFIED:** test presenti per algebra, proiezione/trasposta, Welford, builder/compiler, contratti, encoder/preprocessing, calibrazione, metriche e split. [PREPROCESSING_OPTIMIZATION_RESULT.md](development/archive/PREPROCESSING_OPTIMIZATION_RESULT.md) riporta 86 test passati nell'esecuzione selezionata. Gli XML Surefire attualmente conservati contengono 33 suite/93 test, zero failure/error/skipped; possono mescolare esecuzioni diverse e non certificano una singola run da 93 test. Nessun test rieseguito in questa riconciliazione.

Comando rapido documentato (esclude i due test reali lunghi):

```powershell
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test
```

Il normale `mvn test` include `BottleTrainingTest` e `BottleRealEvaluationTest`: non considerarlo uno smoke rapido. Gli esperimenti `*IT` sono opt-in. Comandi completi nei rispettivi file `*_VALIDATION.md`, in particolare [ottimizzazione](development/archive/PREPROCESSING_OPTIMIZATION_VALIDATION.md), [benchmark](development/archive/PERFORMANCE_BENCHMARK_VALIDATION.md), [held-out](benchmark/HELDOUT_CALIBRATION_VALIDATION.md). Non eseguire `clean` per semplice consultazione: `target/` contiene evidenze generate, non sostituisce un archivio versionato.

**HISTORICAL / SUPERSEDED, fotografia del 2026-10-02:** HEAD `83c4197f8771e5ec21db1bcd53508e0d00a4add6`, branch main, tag `sprint9-spatial28-comparison`. Tag Sprint 7 sul commit `5caca96`; commit `8f0820178392e8c42736f9cd554e6311bc6dc01c` del 2026-09-30, `docs: add repository-first agent workflow`, e antenato di HEAD. Verificati con `git log`, `git show-ref`, `git merge-base --is-ancestor` e `git show`.

**HISTORICAL / SUPERSEDED, workspace del 2026-10-02:** prima di quella attivita risultavano modificati `pom.xml`, `ImageNetPreprocessor.java`, `LocalizationMaps.java`; non versionati numerosi report, `src/jmh/` e gli helper/test di calibrazione. HEAD non ricostruisce quindi da solo i risultati correnti. Questa attivita aggiunge soltanto i due documenti in `docs/`; nessun commit.

## Discrepanze, limiti e informazioni non verificabili

1. **VERIFIED, documenti obsoleti:** root `PROJECT_STATE.md` dice che localizzazione/28x28 non sono stabiliti; report, codice e CSV successivi li verificano. Root `TASK.md` e ancora Sprint 9. `AGENTS.md` rimanda al vecchio stato: resta un problema di discoverability, segnalato senza modificare altri file.
2. **DECIDED, discrepanza di roadmap risolta:** l'utente ha confermato il 2026-10-02 la decisione successiva a `POST_BENCHMARK_STRATEGY.md`: chiusura del filone performance interno, poi benchmark comparativo CPU same-hardware inizialmente PaDiM/PatchCore. L'ablazione VSA/CNN resta OPEN / DEFERRED. L'ordine e deciso; l'esecuzione del confronto resta da svolgere.
3. **VERIFIED, perimetro API:** il divieto di clamp riguarda lo score scientifico; l'API visuale di produzione fa clamp. La Z posizionale non e ancora un'API pubblica di produzione.
4. **VERIFIED, terminologia benchmark:** `sessionRunAndClose` chiude il Result a ogni operazione, non la sessione; questa viene chiusa in teardown. La descrizione del report encoder va letta con questa correzione.
5. **OPEN, provenienza:** ONNX locale identificato dagli hash; export/checkpoint originale non completamente attestato. La precedente assenza di detector serializzato verificato e **SUPERSEDED** dal reload del benchmark finale (sezione 8).
6. **OPEN, memoria esterna:** risultati storici PlantVillage/Android, motivazioni professionali e revisione HDC in un'altra chat sono raccontati dall'handoff; questa ispezione non li ha verificati con sorgenti esterni al repository. L'attribuzione delle esecuzioni a una specifica persona/agente non e provata dal solo artefatto. La priorita operativa, invece, e stata confermata direttamente dall'utente.
7. **OPEN, validita esterna (affermazione storica superata):** la fotografia precedente parlava di una sola categoria valutata, nessun confronto locale contro sistemi esterni, nessuna prova di few-shot/continual learning o invarianza alla posa. Il confronto esterno con PaDiM/PatchCore su Bottle e Metal Nut e stato eseguito ([risultato](benchmark/BENCHMARK_FINAL_RESULT.md)); few-shot/continual e invarianza alla posa restano non dimostrati. La generalizzazione oltre le categorie osservate resta non stabilita.

## Ipotesi e priorita storiche — fase corrente chiusa

- **OPEN / DEFERRED, ora parzialmente misurata:** `VSA vs direct latent-space memory/prototype` resta una questione scientifica aperta e rinviata. L'ablazione post-hoc ha pero misurato baseline CNN locali dirette sulle stesse feature congelate, fornendo un primo confronto interno (non indipendente, su categorie gia osservate); si veda [LOCAL_MEMORY_ABLATION_RESULT.md](diagnostics/LOCAL_MEMORY_ABLATION_RESULT.md).
- **HYPOTHESIS / FUTURE_WORK:** eventuali rimedi di alignment restano esclusi. Metal Nut e stata scelta prima del benchmark per osservare il comportamento con maggiore variabilita di posa, senza tuning dedicato.
- **DECIDED:** filone performance interno chiuso per questa fase; nessuna ulteriore ottimizzazione preventiva prima del confronto esterno.
- **FUTURE_WORK / DEFERRED:** fine-tuning, fusion 14+28, alignment, nuovi metodi di calibrazione, CLI e nuovi formati di persistenza non indispensabili al benchmark. README e preparazione repository sono invece previsti dopo il confronto.
- **DECIDED, task di progetto:** benchmark comparativo CPU same-hardware Bottle/Metal Nut contro PaDiM/PatchCore, secondo [BENCHMARK_CONTRACT.md](benchmark/BENCHMARK_CONTRACT.md). Phase 1 chiude fonti/configurazioni 2.6.2, export raw, mapping geometrico, pesi, ambiente, split e strategia memoria. **Preparazione Phase 2 chiusa:** equivalenza resize, parser categoria/difetto, percorso fit competitor verificato, gate offline/hash, formato mappe `npy+json/v1` e cattura identita workspace ([report](benchmark/BENCHMARK_PHASE2_PREPARATION.md)). **Implementazione Phase 2 chiusa:** runner comparativo e adattatori per i sei casi cablati e verificati con smoke; sintesi con `null`+reason; classificazione READY_FOR_FINAL_RUN ([implementazione](benchmark/BENCHMARK_PHASE2_IMPLEMENTATION.md), [audit](benchmark/BENCHMARK_PHASE2_COMPLETION_AUDIT.md)). **Run finale COMPLETE:** eseguito `final-comparison-20261003` a budget pieno senza tuning; [risultato](benchmark/BENCHMARK_FINAL_RESULT.md). **Prossimo passo:** sintesi, README e preparazione repository, quindi chiusura della fase. EfficientAD e altre categorie sono FUTURE_WORK.
- **Stato di pubblicazione corrente:** includere documenti e sorgenti post-hoc nel successivo commit autorizzato; `target/`, dataset, sessioni e handoff restano esclusi. La dipendenza dagli artefatti locali e descritta nella panoramica tecnica. Nessuna nuova indagine e richiesta per la release.

Le decisioni gia sostenute da codice/protocolli sono raccolte in [DECISION_LOG.md](DECISION_LOG.md). L'handoff rimane memoria consultabile, non fonte capace di sovrascrivere queste evidenze.
