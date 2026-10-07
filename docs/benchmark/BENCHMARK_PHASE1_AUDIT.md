# Audit Phase 1 del benchmark comparativo

Data: 2026-10-03. Tipo: audit in sola lettura. Nessun sorgente, dataset, contract o dipendenza modificati. Nessun fitting, benchmark, metrica comparativa o test eseguito. Le uniche azioni sono state letture e verifiche locali non distruttive (conteggi, hash SHA-256, confronto manifest, `git status`, lettura del checkout Anomalib e dei sorgenti Java dell'evaluator).

Riferimenti letti: `docs/PROJECT_STATE.md`, `docs/DECISION_LOG.md`, `docs/benchmark/BENCHMARK_CONTRACT.md`, `docs/benchmark/BENCHMARK_PRERUN_VERIFICATION.md`, i file in `docs/benchmark/benchmark-prerun/` e gli helper in `tools/prerun/`.

## Esito sintetico

**READY_WITH_FIXES**

La Phase 1 chiude correttamente i gate di sorgente/ambiente/pesi/raw/geometria/split/thread/memoria nei limiti dichiarati: non esiste alcun blocker tecnico per iniziare la Phase 2 di implementazione del runner. Restano pero alcuni `FIX_BEFORE_RUN` che devono essere chiusi durante l'implementazione e comunque prima delle misure finali. Nessuno di essi riapre il design del benchmark.

## Tabella dei controlli

| # | Controllo | Risultato | Categoria |
| --- | --- | --- | --- |
| 1 | Anomalib 2.6.2: tag/commit, config PaDiM/PatchCore, freeze | Coerente e verificato localmente | NO_ISSUE |
| 2 | Pretrained weights: hash, assenza download implicito | Verificato; offline e a livello di processo, non firewall di sistema | NO_ISSUE |
| 3 | Raw outputs: score/map/shape/dtype/semantica prima del postprocessing | Documentato e confermato nel sorgente; fit reale a budget completo non ancora esercitato | NO_ISSUE (con fix) |
| 4 | Geometria: nessun crop, mapping 256 -> 900/700, Bottle/Metal Nut | Gate chiuso; equivalenza numerica resize Python/Java non ancora dimostrata | NO_ISSUE (con fix) |
| 5 | Split: 167+42 / 176+44, Random(42), disgiunti/completi, nessun test nel fit | Verificato localmente | NO_ISSUE |
| 6 | Thread/runtime: manifest Java/ORT e Python/PyTorch | Documentato; worker ORT unknown resta limite esplicito | NO_ISSUE |
| 7 | Memory tooling: helper Windows Java+Python, contatori distinti | Verificato su processi reali | NO_ISSUE |
| 8 | Phase 2 readiness: componenti mancanti | Nessun blocker; gap di implementazione noti | FIX_BEFORE_RUN |

## Blocker

Nessuno. Non sono emersi elementi che impediscano di implementare correttamente la Phase 2.

## Fix obbligatori prima del run (FIX_BEFORE_RUN)

1. **Equivalenza numerica del resize evaluation-space Python vs Java.** Il report e lo smoke verificano solo shape e finitezza del mapping `256x256 -> 900/700` con `torch.nn.functional.interpolate(..., mode="bilinear", align_corners=False)` (`tools/prerun/verify_competitors.py:119-124`). La convenzione Java e `LocalizationMaps.upsample` (`src/test/java/io/github/antctrlwin/anomalib4j/evaluation/LocalizationMaps.java:13-32`), half-pixel con clamp ai bordi. Le due formule sono formalmente coerenti, ma la coincidenza numerica va provata su fixture sintetiche prima di calcolare metriche reali, come gia previsto dal contract (sez. 8 e sez. 4).
2. **Parsing dei difetti Metal Nut.** `BottleEvaluation.defect` lancia su qualunque filename non `bottle_(good|broken_large|broken_small|contamination)_N.png` (`BottleEvaluation.java:17-22`). Metal Nut usa `bent`, `color`, `flip`, `scratch`: serve un parser generico per categoria (o un adattatore dedicato) prima di produrre le label di test.
3. **Fit nativo reale a budget completo.** Lo smoke costruisce i competitor con `post_processor=False, evaluator=False, visualizer=False`, alimenta `native(...)` su due sole immagini train-good e poi chiama `module.fit()` (`verify_competitors.py:83-104`). E una sonda tecnica, non il loop nativo con manifest e batch 32. Prima delle misure va confermato che il percorso di fit usato sia quello nativo e che non attivi `val_split_mode=SAME_AS_TEST`/PostProcessor appresi sul test.
4. **Modalita offline e hash pesi nel runner.** La cache HF locale e i due `model.safetensors` sono presenti e con hash coerenti, ma la garanzia offline dello smoke e a livello di processo (`sys.addaudithook` + `HF_HUB_OFFLINE`/`TRANSFORMERS_OFFLINE`, `verify_competitors.py:30-46`), non una policy di sistema. Il runner deve impostare `HF_HOME`/`TORCH_HOME` verso la cache verificata, restare offline e ricontrollare gli SHA prima di partire.
5. **Formato lossless di export di score/map.** Il contract lo lascia esplicitamente "da congelare" (`BENCHMARK_CONTRACT.md:154`). Va deciso e implementato un formato mappa lossless con metadata (native ed evaluation-space separati), non PNG colorato.
6. **Cattura dell'identita del workspace prima delle metriche.** Il workspace e sporco e non ricostruisce da solo i risultati: `pom.xml`, `src/main/java/.../ImageNetPreprocessor.java`, `src/test/java/.../LocalizationMaps.java` sono modificati e gran parte di `docs/`, `tools/`, `src/jmh/`, degli helper di calibrazione e degli esperimenti non e versionata (`git status --porcelain`, 32 voci). HEAD `83c4197`. Il contract richiede di archiviare commit+diff non committed prima delle misure finali (`BENCHMARK_CONTRACT.md:133`).

## Limiti solo documentali (DOCUMENT_ONLY)

- **Worker ORT effettivo unknown.** Non osservabile con l'API di produzione; impedisce solo un claim single-native-thread e non blocca il confronto Native/System (`BENCHMARK_PRERUN_VERIFICATION.md:174`; `BENCHMARK_CONTRACT.md:129`). Confermato: `runtime.jsh` espone provider e flag JVM, non il numero di worker.
- **Freeze con percorso editable assoluto.** `environment.freeze.txt` contiene `-e <LOCAL_PROJECT_ROOT>/target/prerun/anomalib-2.6.2`; per riprodurre serve ricreare il checkout al commit e usare il suo `uv.lock`, non puntare a una cartella arbitraria (`BENCHMARK_PRERUN_VERIFICATION.md:24`).
- **Copertura offline non firewall.** Il controllo rete vale per il processo/percorso Python testato, senza `hf_xet` (`BENCHMARK_PRERUN_VERIFICATION.md:120`).
- **Monitoring memoria campionato.** 100 ms richiesto ma non garantito (CIM/scheduling); picchi brevi, figli orfani o PID riusati possono sfuggire; `PeakWorkingSet64` e lifetime del processo, non picco del solo fit (`BENCHMARK_PRERUN_VERIFICATION.md:111`).
- **Metal Nut DECIDED ma non IMPLEMENTED/VERIFIED come training.** Split e configurazione definiti, nessun modello Metal Nut addestrato o validato (`BENCHMARK_CONTRACT.md:28`).
- **Allocazioni training diverse accettate.** Anomalib4j 167/176 vs competitor 209/220 normal: non e un difetto, va solo dichiarato (`BENCHMARK_CONTRACT.md:32`).
- **Provenienza export encoder Java.** Gli hash ONNX identificano i byte locali; la catena checkpoint/export timm resta OPEN (`PROJECT_STATE.md:99`).
- **Affinity/power/carico/loader al run finale.** Non deducibili dall'osservatore statico; da registrare al run (`BENCHMARK_CONTRACT.md:129`).

## Componenti minimi che Phase 2 deve implementare

1. **Competitor adapter (PaDiM/PatchCore):** lettura manifest, decode in RAM, preprocessing nativo dentro la regione cronometrata, fit nativo, export score/map raw.
2. **Metal Nut Anomalib4j adapter:** stesso contratto SPATIAL_14 + positional Z, split da manifest, senza riusare il path Bottle-only.
3. **Export prediction/map lossless** con filename/hash, label, difetto, `score_kind`, shape/dtype/geometria.
4. **Adapter verso l'evaluator comune:** riuso di `LocalizationMetrics` (AUROC pixel/AUPRO) e `DatasetNinjaMasks`; parser difetti per categoria; image AUROC dai `pred_score` nativi.
5. **Timing harness:** preservare JMH per Java (SampleTime, 5x2s/10x2s, 2 fork); harness Python con condizioni equivalenti e aggregazione p50/p95/p99.
6. **Memory capture integration:** marcatori di fase, osservazione prima di init/fit, tracciamento dei PID reali del workload, finestra steady-state.
7. **Artifact layout** `target/comparison/<run-id>/` con `manifest.json`, `hardware.json`, `checksums.sha256`, predictions, maps, metriche, latenza e memoria.

## Evidenze effettivamente verificate

Documenti: `docs/PROJECT_STATE.md`, `docs/DECISION_LOG.md`, `docs/benchmark/BENCHMARK_CONTRACT.md`, `docs/benchmark/BENCHMARK_PRERUN_VERIFICATION.md`.

`docs/benchmark/benchmark-prerun/`: `configurations.json`, `source.json`, `environment.json`, `environment.freeze.txt` (78 righe), `weights.json`, `hardware.json`, `smoke.json`, `data-sha256.json`, `java-runtime.txt`, `java-runtime-stderr.txt` (vuoto), `memory-python-smoke.csv`, `memory-java-smoke.csv`, `memory-monitor-self-smoke.csv`, `memory-python-final-check.csv`, e i manifest `data/{bottle,metal_nut}/{competitor-fit,anomalib4j-fit,anomalib4j-calibration,test}.txt`.

Helper: `tools/prerun/splits.jsh`, `runtime.jsh`, `Measure-ProcessMemory.ps1`, `Verify-MemoryTool.ps1`, `verify_competitors.py`.

Verifiche locali eseguite in questo audit:

- **Sorgente Anomalib:** checkout `target/prerun/anomalib-2.6.2` con HEAD = tag dereferenziato = `cc5f400a4a4b1b14b5a3ee5078063c250a8f522e`, working tree pulito; `uv.lock` presente con SHA-256 `F33FF8DFF6F60CD3E6F7FD23C854D661F474804F465BCAACBEFCDE612309A103`, identico a `source.json`.
- **Pesi:** entrambi i `model.safetensors` presenti; SHA-256 ricalcolati `80c49dee...c612` (resnet18, 46.807.446 byte) e `03b71d65...3000` (wide_resnet50_2, 275.835.296 byte), identici a `weights.json`. Snapshot `491b427b...` e `30f73ace...` coerenti.
- **Manifest:** Bottle 209 = 167+42, test 83; Metal Nut 220 = 176+44, test 115; fit e calibration disgiunti (intersezione 0), nessun duplicato, unione uguale a `competitor-fit`; hash SHA-256 di tutti e otto i manifest identici a `data-sha256.json`; lo split Bottle coincide esattamente e nello stesso ordine con i manifest canonici `target/bottle-spatial14-heldout-calibration/{archetype-training,calibration}.txt`.
- **Split Java:** `splits.jsh:18` usa `Collections.shuffle(names, new Random(42L))` e legge solo `train/img`; il test manifest e separato.
- **Metrica di riferimento Bottle:** `target/bottle-spatial14-heldout-calibration/metrics.csv` riporta `CALIBRATED_167+42` Image 1.0, Pixel 0.9616640201342855, AUPRO 0.8774472899967225, identici al riferimento del contract (`BENCHMARK_CONTRACT.md:43`).
- **Sorgente upstream (gate geometria/raw):** `patchcore/anomaly_map.py:82` usa `F.interpolate(patch_scores, size=...)` con mode di default `nearest`; `patchcore/lightning_model.py:211-221` ha `center_crop_size` default `None` (nessun `CenterCrop`); `padim/anomaly_map.py:110-114` usa bilinear `align_corners=False`; `padim/torch_model.py:127/142` risolve `n_features` via `_N_FEATURES_DEFAULTS` e `torch.randperm`; `padim/torch_model.py:238` fit gaussiano su `memory_bank`.
- **Sorgenti evaluator Java:** `LocalizationMetrics.java` (AUROC pixel/AUPRO, regioni 8-connesse), `LocalizationMaps.java:13-32` (bilinear half-pixel bordi replicati), `DatasetNinjaMasks.java` (bitmap generici, passthrough good), `BottleEvaluation.java:17-22` (parsing difetti Bottle-only), `HeldOutBottleCalibration.java:38/49` (209/167 e regex Bottle), `PositionalRawCalibration.java` (Z per posizione, sigma campionaria, floor).
- **Memory CSV:** presenti e leggibili, con colonne `timestamp_utc, elapsed_ms, pid, start_time_utc, root_pid, working_set_bytes, private_bytes, lifetime_peak_working_set_bytes`; 21/68/4/21 righe rispettivamente.
- **Stato Git:** HEAD `83c4197f8771e5ec21db1bcd53508e0d00a4add6`; 3 file modificati e numerosi file/cartelle non versionati (vedi fix 6).

## Recommended Phase 2 scope

1. Congelare un formato mappa lossless con metadata e separare native map da evaluation-space map.
2. Implementare l'adattatore competitor da manifest (PaDiM/PatchCore), con preprocessing nativo `module.pre_processor.transform`, fit nativo e modalita offline forzata + ricontrollo hash pesi.
3. Implementare l'adattatore Metal Nut Anomalib4j con lettura manifest, SPATIAL_14, positional Z e stessa pipeline di inferenza; non riusare il path Bottle-only.
4. Generalizzare il parsing dei difetti per categoria e riusare `LocalizationMetrics`/`DatasetNinjaMasks`; image AUROC dai `pred_score` nativi, non da `max(map)` imposto.
5. Verificare su fixture sintetiche l'equivalenza numerica del resize evaluation-space Python vs `LocalizationMaps.upsample` prima di ogni metrica.
6. Preservare JMH per Java e costruire l'harness Python equivalente (SampleTime, 5x2s/10x2s, 2 processi, p50/p95/p99).
7. Integrare il monitor memoria con marcatori di fase, avvio prima di init/fit e tracciamento dei PID reali del workload.
8. Produrre il layout `target/comparison/<run-id>/` con manifest, hardware, checksums e status per caso.
9. Eseguire un solo smoke tecnico breve (nessuna selezione iperparametri, nessun leakage dal test), poi i run finali separati.
10. Archiviare commit+diff del workspace e i limiti residui (worker ORT unknown, sampling dei picchi, allocazioni diverse) prima delle misure finali.
