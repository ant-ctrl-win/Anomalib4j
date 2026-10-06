# Anomalib4j — Risultato del benchmark comparativo finale

Stato: **COMPLETE**. Run scientifico finale eseguito secondo il contract congelato
[BENCHMARK_CONTRACT.md](BENCHMARK_CONTRACT.md). Nessun algoritmo, configurazione, split,
iperparametro o preprocessing e stato modificato; nessun tuning sui risultati; nessun commit,
reset, revert o clean; nessun artefatto storico sovrascritto.

Il risultato non va interpretato come superiorita generale di un algoritmo. Le metriche per
categoria sono autorevoli; la macro-media e secondaria. L'interpretazione resta dell'operatore.

## 1. Run-id

| Campo | Valore |
| --- | --- |
| run-id | `final-comparison-20261003` |
| directory | `target/comparison/final-comparison-20261003/` |
| created_at (manifest) | `2026-10-03T14:08:16Z` |
| modalita | run finale, `smoke=false` |
| seed | 42 |
| ordine casi | anomalib4j/bottle, padim/bottle, patchcore/bottle, anomalib4j/metal_nut, padim/metal_nut, patchcore/metal_nut |
| execution | serial |
| method_order | anomalib4j, padim, patchcore |
| second_process_order | patchcore, padim, anomalib4j |
| dataset | `<DATASET_ROOT>/mvtec-ad-DatasetNinja` |
| RESULT.md | `target/comparison/final-comparison-20261003/RESULT.md` |

## 2. Ambiente e hardware

Da `hardware.json` e `environment/`:

| Campo | Valore |
| --- | --- |
| CPU | AMD Ryzen AI 9 HX 370 w/ Radeon 890M |
| core | 12 fisici / 24 logici |
| RAM | 67.772.403.712 byte (~63,1 GiB) |
| OS | Microsoft Windows 11 Pro build 26200 |
| power | Bilanciato (GUID 381b4222-f694-41f0-9685-ff5bb260df2e) |
| background_cpu | 1 |
| Java | Temurin 21.0.9 |
| Maven | 3.9.14 |
| Anomalib | 2.6.2 (editable), commit `cc5f400a4a4b1b14b5a3ee5078063c250a8f522e` |
| PyTorch | 2.13.0+cpu |
| ORT | 1.30.0 (provider CPU; worker effettivi `unknown`, documentato) |

Sono archiviati: identita del workspace (`workspace/`), `hardware.json`, `environment/prerun/`,
`manifest.json`, `checksums.sha256`, `dataset-hashes.json`, `source-hashes.json`.

## 3. Configurazioni dei tre metodi

Configurazioni congelate (identiche fra categorie), da `config.json`:

| Aspetto | Anomalib4j | PaDiM | PatchCore |
| --- | --- | --- | --- |
| Spazio | CNN MobileNetV4 SPATIAL_14 (14x14x96) | resnet18 layer1/2/3 | wide_resnet50_2 layer2/3 |
| Pesi | ONNX locale (hash in PROJECT_STATE) | timm `resnet18.a1_in1k` | timm `wide_resnet50_2.racm_in1k` |
| Detector | VSA per posizione + Adjoint | gaussian, `n_features=100` (448→100), reg. 0.01I/1e-5I | coreset 0.1, k=9, brute-force PyTorch, no FAISS |
| Score | max positional Z sui 196 patch | max mappa nativa smoothed | max distanza NN patch (fino a 9 vicini) |
| Mappa nativa | 14x14 | 256x256, bilinear | 256x256, nearest + blur |
| Preprocessing | imagenet-rgb-resize224-bicubic | Resize 256 bilinear antialias + Normalize ImageNet, crop=null | idem |
| Precisone | float64 (Z), float32 (CNN) | float32 | float32 |

Nessun Engine/datamodule con `SAME_AS_TEST`: il fitting usa il loop nativo verificato
(train-good ordinato → accumulo → `fit()`), senza validation derivata dal test e senza
PostProcessor appreso. `pred_score` competitor e lo score nativo (`score_kind=native_raw`);
l'Anomalib4j usa `score_kind=positional_z`. Mappe native ed evaluation-space separate.

## 4. Data budget

| Categoria | Anomalib4j fit | Anomalib4j calibrazione | PaDiM/PatchCore fit | Test |
| --- | ---: | ---: | ---: | ---: |
| Bottle | 167 | 42 | 209 | 83 (20 good / 63 anomalie) |
| Metal Nut | 176 | 44 | 220 | 115 (22 good / 93 anomalie) |

Tutti i budget pieni rispettati; nessuna riduzione di coreset, batch o dataset per far passare i fit.
Manifest, split disgiunti e hash verificati all'init e prima di ogni caso.

## 5. Risultati qualita per categoria

Evaluator armonizzato unico (`EvaluationLabels` + `DatasetNinjaMasks` + `LocalizationMetrics`,
convenzioni `LOCALIZATION_CONVENTIONS.md`). Lo score immagine e lo score nativo del metodo, mai
`max(mappa)`.

### Bottle (83 test)

| Metodo | Image AUROC | Pixel AUROC | AUPRO@0.30 | regioni | foreground | background |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| anomalib4j | 1.0 | 0.9616640201342855 | 0.8774472899967225 | 68 | 3.886.731 | 63.343.269 |
| padim | 0.9976190476190476 | 0.9777331583457916 | 0.9224892181832084 | 68 | 3.886.731 | 63.343.269 |
| patchcore | 1.0 | 0.985401790942364 | 0.9443365748663434 | 68 | 3.886.731 | 63.343.269 |

La baseline canonica Anomalib4j Bottle (Z-167+42, seed 42) e riprodotta esattamente
(Image 1.0, Pixel 0.9616640201342855, AUPRO 0.8774472899967225): nessuna regressione.

### Metal Nut (115 test)

| Metodo | Image AUROC | Pixel AUROC | AUPRO@0.30 | regioni | foreground | background |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| anomalib4j | 0.7575757575757576 | 0.6926300242781803 | 0.3797941159804979 | 132 | 6.602.541 | 49.747.459 |
| padim | 0.9521016617790812 | 0.9407049168572144 | 0.8513500160866296 | 132 | 6.602.541 | 49.747.459 |
| patchcore | 0.9975562072336266 | 0.9866853122744941 | 0.9403126659305793 | 132 | 6.602.541 | 49.747.459 |

Nessun NaN/Inf in `predictions.csv`; conteggi predizioni = test attesi per ogni caso.
`metrics-native.json` resta `open` con reason esplicita (l'evaluator nativo del framework non e
invocato; le metriche primarie sono quelle armonizzate).

## 6. Macro-media (secondaria)

Media aritmetica sulle due categorie verificate, da `macro-summary.csv`. Solo secondaria; non e
una ROC su categorie concatenate.

| Metodo | categorie verificate | Image AUROC macro | Pixel AUROC macro | AUPRO@0.30 macro |
| --- | ---: | ---: | ---: | ---: |
| anomalib4j | 2 | 0.878787878788 | 0.827147022206 | 0.628620702989 |
| padim | 2 | 0.974860354699 | 0.959219037602 | 0.886919617135 |
| patchcore | 2 | 0.998778103617 | 0.986043551608 | 0.942324620398 |

## 7. Latenza (p50/p95/p99)

Protocolli non equivalenti per campionamento: JMH SampleTime (5x2 s warmup, 10x2 s measurement,
2 fork, 1 caller) per Anomalib4j; protocollo Python per i competitor (finestre equivalenti,
2 processi, batch 1, preprocessing incluso; decode/I/O/export/evaluator esclusi). Non si afferma
identita statistica fra JMH e campioni Python. Unita: microsecondi.

### Anomalib4j

| Categoria | Regione | campioni | mean | p50 | p95 | p99 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| bottle | detectionInference | 10521 | 3815.23 | 3723.264 | 4358.144 | 5159.32 |
| bottle | localizationInference | 4156 | 9670.29 | 9486.336 | 10850.304 | 11771.08 |
| metal_nut | detectionInference | 10715 | 3742.99 | 3694.592 | 4112.384 | 4767.744 |
| metal_nut | localizationInference | 6833 | 5878.32 | 5775.36 | 7503.872 | 8601.6 |

### PaDiM

| Categoria | Regione | campioni | mean | p50 | p95 | p99 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| bottle | native_full | 723 | 56012.82 | 55798.5 | 59891.6 | 64334.77 |
| bottle | full_resolution | 702 | 57826.71 | 57602.45 | 61188.56 | 64545.893 |
| metal_nut | native_full | 907 | 44560.96 | 44377.6 | 50197.13 | 55505.89 |
| metal_nut | full_resolution | 909 | 44483.93 | 44463.3 | 49724.64 | 54591.10 |

### PatchCore

| Categoria | Regione | campioni | mean | p50 | p95 | p99 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| bottle | native_full | 178 | 246751.48 | 246361.85 | 252117.645 | 259699.575 |
| bottle | full_resolution | 176 | 249549.47 | 247424.45 | 272782.625 | 289998.3 |
| metal_nut | native_full | 192 | 224216.92 | 216680.8 | 261590.33 | 281397.244 |
| metal_nut | full_resolution | 183 | 232012.37 | 222925.3 | 264784.98 | 280119.848 |

Campioni grezzi conservati in `latency-process-*.csv`, `latency-samples.csv`,
`latency-summary.json`; per Anomalib4j anche `jmh.json` e `runtime-jmh-*.json`.

## 8. Fitting / model construction

Da `fitting.json` (tempi in secondi). Anomalib4j: fit + calibrazione. Competitor: una sola passata
di fit; batch 32 nativo, loader sincrono (0 worker), decode/I-O inclusi nel fitting, import di
startup esclusi.

| Caso | fit | calibrazione | init | fitting | ready-to-infer |
| --- | ---: | ---: | ---: | ---: | ---: |
| anomalib4j/bottle | 167 | 42 | 0.2256 | 313.0422 | 313.2679 |
| anomalib4j/metal_nut | 176 | 44 | 0.1820 | 367.4881 | 367.6701 |
| padim/bottle | 209 | — | 0.3253 | 11.3489 | 11.6742 |
| padim/metal_nut | 220 | — | 0.1721 | 9.5795 | 9.7517 |
| patchcore/bottle | 209 | — | 0.9057 | 516.9167 | 517.8224 |
| patchcore/metal_nut | 220 | — | 0.9070 | 507.1314 | 508.0385 |

Stato modello serializzato e verificato (`model-state.json`, `native-state.pt`,
`learning-state.bin`, `descriptor.json`); il reload e stato esercitato nell'infer e produce
raw/Z identici.

## 9. Memoria

Monitor di processo Windows (`Measure-ProcessMemory.ps1`), working set / private bytes /
lifetime peak working set in byte e MiB (2^20). I missing restano `null`/`reason`, mai zero;
sono esclusi da medie e picchi e contati. Picchi di finestra distinti dai lifetime peak.

| Caso | righe | missing | baseline WS/priv (MiB) | fitting WS/priv (MiB) | steady WS/priv (MiB) |
| --- | ---: | ---: | ---: | ---: | ---: |
| anomalib4j/bottle | 1619 | 0 | 43,7 / 1135,0 | 941,6 / 1245,9 | 980,9 / 1213,6 |
| anomalib4j/metal_nut | 1468 | 1 | 43,9 / 1135,7 | 902,5 / 1258,6 | 960,9 / 1230,6 |
| padim/bottle | 286 | 0 | 506,1 / 2003,1 | 1530,8 / 3199,9 | 740,7 / 2261,7 |
| padim/metal_nut | 279 | 0 | 573,7 / 2048,0 | 1647,8 / 3202,3 | 732,4 / 2258,9 |
| patchcore/bottle | 2365 | 0 | 564,5 / 2054,2 | 3321,6 / 5031,1 | 1028,8 / 2658,1 |
| patchcore/metal_nut | 1888 | 1 | 543,3 / 2036,7 | 3730,4 / 5509,3 | 1034,5 / 2564,2 |

Intervallo di campionamento richiesto 100 ms; osservato ~210–795 ms per l'overhead CIM. La memoria
e logica di processo, non RSS di macchina; i picchi transitori possono sfuggire.

## 10. Failure e missing

- Nessun fallimento di fit o infer. Nessun OOM. Nessuna metrica mancante.
- Missing di campionamento memoria: anomalib4j/metal_nut 1 riga, patchcore/metal_nut 1 riga,
  motivo `process_not_found` (processo terminato tra due campioni). Conservate come `null`,
  contate, non convertite in zero.
- Nessun download di rete durante il run (gate offline + hash pesi attivo).
- Nessuna configurazione scientifica modificata durante il run; nessun file storico sovrascritto
  (run-id nuovo).

## 11. Limiti di comparabilita

- Allocazioni di training differenti: Anomalib4j 167/176 normal, PaDiM/PatchCore 209/220.
- Backbone, preprocessing e precisione differenti fra i metodi.
- Anomalib4j usa uno Z posizionale held-out; i competitor usano score nativi del framework.
- Risoluzioni di output native differenti (14x14 contro 256x256) e threading nativo differente.
- Due sole categorie: non rappresentano l'intera MVTec AD.
- Bottle ha gia influenzato scelte precedenti del progetto.
- Latenza su input ripetuto, non throughput sostenuto; memoria logica di processo, non RSS.
- Le versioni di implementazione limitano le affermazioni; ORT effective workers `unknown`.
- Protocolli di campionamento latenza JMH e Python non statisticamente identici.

## 12. Anomalie operative emerse

- Nei `config.json` di run il campo `frozen_configuration.status` conserva la stringa
  `source_defaults_and_small_smoke_verified_not_final_results`, ora storicamente imprecisa
  (i risultati sono finali). E un'etichetta descrittiva nei metadati del caso, non un parametro
  scientifico; non e stata modificata per non riscrivere artefatti gia prodotti.
- L'overhead del monitor CIM allunga l'intervallo di campionamento oltre i 100 ms richiesti.
- Il conteggio campioni di latenza differisce fra JMH (osservazioni pesate dall'istogramma) e
  Python (chiamate cronometrate); documentato, non equiparato.

## 13. Artefatti prodotti

Per ogni caso `target/comparison/final-comparison-20261003/<method>/<category>/`:
`config.json`, `fitting.json`, `training-inputs.json`, `validation.md`, `model-state.json`,
`payload.json`, stato del modello (`native-state.pt` per i competitor, `descriptor.json` +
`learning-state.bin` per Anomalib4j), `predictions.csv`, `metrics-unified.json`,
`metrics-native.json`, `latency-samples.csv`, `latency-summary.json`, `latency-process-*.csv`,
`memory.csv`, `memory-summary.json`, `run.log`, `phases.csv`, `fit.log`, `inference.log`,
`evaluation.log`, `latency-*.log`, `runtime-*.json`, e `maps/` con mappe native ed
evaluation-space per ogni immagine di test (`.npy` + sidecar `.json`, formato `npy+json/v1`).

Al livello del run: `contract.md`, `manifest.json`, `hardware.json`, `checksums.sha256`,
`dataset-hashes.json`, `source-hashes.json`, `environment/`, `workspace/`, `data/`,
`summary-by-category.csv`, `macro-summary.csv`, `RESULT.md`.

Conteggi: 6 casi; 83 mappe evaluation Bottle per metodo, 115 Metal Nut per metodo; 84/116 righe
`predictions.csv` (header incluso) per ciascun caso.
