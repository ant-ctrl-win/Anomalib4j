# Benchmark Phase 2 - Implementazione

Stato: **READY_FOR_FINAL_RUN** (implementazione completa, smoke verificati; nessun benchmark
scientifico finale eseguito). Vedi [BENCHMARK_PHASE2_COMPLETION_AUDIT.md](BENCHMARK_PHASE2_COMPLETION_AUDIT.md)
per l'audit di completamento.

Questo documento descrive l'architettura minimal del runner comparativo Phase 2 e i comandi per
eseguirlo caso per caso, secondo [BENCHMARK_CONTRACT.md](BENCHMARK_CONTRACT.md). Non introduce
decisioni scientifiche nuove: configurazioni, iperparametri, split, seed e dataset sono quelli
gia congelati in Phase 1 e nella preparazione Phase 2.

## 1. Architettura implementata

Il runner e composto da due lati che condividono lo stesso layout su disco:

- **Lato Java (Anomalib4j):** classi di orchestrazione nel package di test
  `io.github.antctrlwin.anomalib4j.evaluation` (`ComparisonRunner`, `ComparisonEvaluator`,
  `ComparisonModel`, `ComparisonArtifacts`) e la suite JMH `ComparisonBenchmark`.
- **Lato Python (competitor):** `tools/comparison/` orchestra Anomalib 2.6.2 nativo (PaDiM e
  PatchCore) tramite il venv verificato, scrive gli stessi artefatti e riusa gli stessi helper di
  persistenza/memoria.
- **Ponte comune:** manifest di split in `docs/benchmark/benchmark-prerun/data/`, formato mappe
  `npy+json/v1`, predictions CSV a 14 colonne, `Metriche` armonizzate via evaluator Java.

Principi applicati:

- un solo caso per invocazione (`method` x `category`); nessuna esecuzione "tutti i casi";
- preprocessing dentro la regione timed; decode/I/O/export/evaluator fuori dal timed;
- nessuna cache di feature usata per abbassare il costo di costruzione dichiarato;
- nessun `mvn clean`; nessun download a runtime (gate offline + hash);
- valori mancanti = `null` con `reason`, mai `0`.

## 2. File modificati e creati

### Java (test scope)

Creati in `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/`:

| File | Ruolo |
| --- | --- |
| `ComparisonArtifacts.java` | Scrittura JSON/CSV, `phases.csv`, affinity, export mappe `npy+json/v1` |
| `ComparisonModel.java` | Persistenza/ricarica stato appreso (descriptor + learning-state binario) |
| `ComparisonRunner.java` | Adattatore Anomalib4j: `fit`, `infer`, `memory` |
| `ComparisonEvaluator.java` | Valutatore armonizzato (Image/Pixel AUROC, AUPRO030) |
| `EvaluationLabels.java` | Parser categoria/difetto generico Bottle/Metal Nut |
| `NpyMap.java` | Lettore/verifica mappe `.npy` + sidecar JSON |
| `HeldOutBottleCalibration.java` | Split/score di riferimento riusato dall'adattatore |
| `PositionalRawCalibration.java` | Calibrazione Z per posizione (filtri) |
| test: `ComparisonModelTest`, `ComparisonArtifactsTest`, `ComparisonEvaluatorTest`, `EvaluationLabelsTest`, `NpyMapTest`, `ResizeEquivalenceTest` | Verifiche mirate |

Creato in `src/jmh/java/io/github/antctrlwin/anomalib4j/evaluation/`:

| File | Ruolo |
| --- | --- |
| `ComparisonBenchmark.java` | JMH SampleTime per `detectionInference` e `localizationInference` |

Modificati (minimi):

| File | Modifica |
| --- | --- |
| `pom.xml` | Profilo `benchmark` (JMH 1.37) + `build-helper-maven-plugin` per `src/jmh/java` |
| `BottleEvaluation.java` | `defect(...)` delega a `EvaluationLabels.defect(...)` |
| `LocalizationMaps.java` | Overload `overlay(..., String scoreLabel)` |
| `ImageNetPreprocessor.java` | Fast path su `DataBufferInt` (equivalente, solo performance) |

### Python e script

Creati in `tools/comparison/`:

| File | Ruolo |
| --- | --- |
| `run.py` | CLI/entry point (`init`, `fit`, `infer`, `evaluate`, `latency`, `memory`, `summarize`) |
| `common.py` | Costanti, `Case`, manifest, hashing, `java_command`, init/validazione run |
| `worker.py` | Worker competitor: fit/infer/latency/memory |
| `native.py` | Costruzione e fit nativo Anomalib 2.6.2 (PaDiM/PatchCore) |
| `timing.py` | Harness di latenza Python (p50/p95/p99) |
| `results.py` | Merge latenza + `summarize_run` (summary-by-category, macro-summary, RESULT.md) |
| `memory.py` | Monitor memoria + aggregazione (missing/null con reason) |
| `test_runner.py` | Test unitari del runner Python |
| `bootstrap.ps1` | Compila e genera `target/comparison-classpath.txt` in modo riproducibile |

Modificato:

| File | Modifica |
| --- | --- |
| `tools/prerun/Measure-ProcessMemory.ps1` | Robustezza: non abortisce mai, righe `status`/`reason`, contatori mancanti vuoti (mai 0), `Export-Csv` garantito in `finally` |

## 3. Comandi esatti

Preparazione una volta per run (dalla root del repository):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\comparison\bootstrap.ps1
target\prerun\anomalib-2.6.2\.venv\Scripts\python.exe tools\comparison\test_runner.py
target\prerun\anomalib-2.6.2\.venv\Scripts\python.exe tools\comparison\run.py init --run-id <run-id>
```

Su run reale (`--smoke` assente = budget pieno). Per ogni coppia `method`/`category`:

```powershell
# 1. fitting
target\prerun\anomalib-2.6.2\.venv\Scripts\python.exe tools\comparison\run.py fit    --run-id <run-id> --method <method> --category <category>
# 2. reload/stato pronto + 3. quality inference/export
target\prerun\anomalib-2.6.2\.venv\Scripts\python.exe tools\comparison\run.py infer  --run-id <run-id> --method <method> --category <category>
# 4. unified evaluation (rifiutata se --smoke)
target\prerun\anomalib-2.6.2\.venv\Scripts\python.exe tools\comparison\run.py evaluate --run-id <run-id> --method <method> --category <category>
# 5. latency (competitor: --process 0/1 per i due processi di misura)
target\prerun\anomalib-2.6.2\.venv\Scripts\python.exe tools\comparison\run.py latency  --run-id <run-id> --method <method> --category <category> [--process N]
# 6. memory
target\prerun\anomalib-2.6.2\.venv\Scripts\python.exe tools\comparison\run.py memory   --run-id <run-id> --method <method> --category <category>
```

Smoke ridotto (primissime 2 immagini di ogni manifest, latenza abbreviata):

```powershell
... run.py init    --run-id <run-id> --smoke
... run.py fit     --run-id <run-id> --method <method> --category <category> --smoke
... run.py infer   --run-id <run-id> --method <method> --category <category> --smoke
... run.py latency --run-id <run-id> --method <method> --category <category> --smoke
... run.py memory  --run-id <run-id> --method <method> --category <category> --smoke
```

Sintesi finale (dopo le metriche):

```powershell
target\prerun\anomalib-2.6.2\.venv\Scripts\python.exe tools\comparison\run.py summarize --run-id <run-id>
```

`--method` ∈ {`anomalib4j`, `padim`, `patchcore`}; `--category` ∈ {`bottle`, `metal_nut`}.

Test Java mirati:

```powershell
mvn -q -Pbenchmark "-Dtest=EvaluationLabelsTest,BottleEvaluationTest,LocalizationArtifactsTest,ResizeEquivalenceTest,NpyMapTest,ComparisonModelTest,ComparisonArtifactsTest,ComparisonEvaluatorTest" test
```

## 4. Fit / reload flow

**Anomalib4j (`ComparisonRunner fit`):** encoder `SPATIAL_14`; descriptor con seed 42 e decodifica
`NormalizationPolicy(1e-6)`; proiezione `DenseRademacherProjection(96, 10000, 42)`;
`NormalImageTrainer` sul manifest `anomalib4j-fit.txt`; calibrazione su `anomalib4j-calibration.txt`;
`PositionalRawCalibration.fit(..., 1e-6)`. Scrive `fitting.json`, `parameters.csv`,
`parameter-summary.csv`, `payload.json`, `runtime.json`; `seal_model` produce `model-state.json`.
Il reload (`ComparisonModel.load` in `infer`) ricompila i filtri e ri-deriva la calibrazione dallo
stato serializzato, verificando magic/dimensioni/celle.

**Competitor (`worker.py` -> `native.py`):** costruzione nativa `Padim` (resnet18, layer1/2/3,
`pre_trained=True`, `n_features=None`) o `Patchcore` (wide_resnet50_2, layer2/3, coreset 0.1,
`num_neighbors=9`), tutti con `post_processor=False`, `evaluator=False`, `visualizer=False`,
`seed_everything(42)` e rifiuto della CUDA. Fit nativo sul manifest `competitor-fit.txt` (tutte le
209/220 normal) a batch 32 sotto `no_grad`, poi `module.fit()`. Salva `native-state.pt` +
`payload.json`; `seal_model` produce `model-state.json`; il reload avviene in `infer` via
`load_state_dict(torch.load(weights_only=True))` + `eval()`.

Nessun `Engine`/`datamodule`; nessuna validation derivata dal test (`SAME_AS_TEST` non usato);
nessun `PostProcessor` appreso sul test.

## 5. Quality / export flow

`infer` carica il modello sigillato, itera le immagini (manifest `test.txt`, oppure le prime 2 di
`competitor-fit.txt` in smoke), per ciascuna:

1. verifica della geometria attesa;
2. inferenza nativa (preprocessing incluso);
3. export della **native map** e, lato Anomalib4j, della **raw map**; lato competitor la mappa
   nativa 256x256;
4. costruzione della **evaluation-space map** con resize bilineare half-pixel/bordi replicati
   (Java `LocalizationMaps.upsample`, Python `F.interpolate(bilinear, align_corners=False)`) alla
   risoluzione originale (900x900 / 700x700);
5. scrittura della riga `predictions.csv` (14 colonne) con `image_sha256` dell'immagine.

Native ed evaluation map restano file separati in `maps/`, in formato `npy+json/v1` (dtype e shape
preservati, nessun PNG colorato come input metriche). Score e kind sono espliciti:
Anomalib4j `score_kind=positional_z` (image score = massimo della mappa Z); competitor
`score_kind=native_raw` (image score = `pred_score` nativo).

## 6. Evaluator

`ComparisonEvaluator` (Java) legge `predictions.csv` e le evaluation map, verifica identita
(filename, method, category, label, defect via `EvaluationLabels`), shape 900/700, formato
`npy+json/v1`, `score_kind=evaluated` e hash SHA-256 del sidecar. Costruisce la maschera con
`DatasetNinjaMasks.read` e calcola le metriche con `LocalizationMetrics` (Image AUROC, Pixel AUROC,
AUPRO030, regioni/foreground/background). Scrive `metrics-unified.json` (status `verified`) e
`metrics-native.json` (status `open` con reason: l'evaluator nativo del framework non viene
invocato; le metriche primarie sono quelle armonizzate).

`evaluate` rifiuta esplicitamente le run smoke. Il wiring e coperto dal test sintetico
`ComparisonEvaluatorTest` (Metal Nut `good` + `flip` con maschera).

## 7. Latency

- **Java:** JMH `ComparisonBenchmark` con `SampleTime`, `@Threads(1)`, warmup 5x2s, measurement
  10x2s, 2 fork, `-Xmx2g`; regioni `detectionInference` e `localizationInference` (la seconda
  include il resize alla risoluzione originale). `latency` invoca JMH via `-rf json -rff` e in smoke
  usa `-wi1 -i1 -w100ms -r100ms -f1`. Summary JMH da `rawDataHistogram` (fork -> iterazioni).
- **Python:** `timing.py` riproduce finestre/repetizioni equivalenti con timer monotono,
  regioni `native_full` e `full_resolution`; `results.py` produce `latency-samples.csv` e
  `latency-summary.json` con p50/p95/p99, mean e numero campioni. In smoke lo status e
  `smoke_only`.

La baseline Java esistente (`PerformanceBaseline.java`) non e stata alterata metodologicamente.

## 8. Memory

`memory` lancia il workload in un processo separato con un gate file, mentre
`tools/prerun/Measure-ProcessMemory.ps1` campiona l'albero parent/child (CIM per la gerarchia,
`Get-Process` per working set, private bytes e lifetime peak working set) a intervalli dichiarati.

Comportamento sui dati mancanti (requisito del contract, riga 157-159):

- contatori non leggibili restano **vuoti (null)**, mai `0`;
- ogni riga ha `status` (`ok`/`partial`/`missing`) e `reason`
  (`process_not_found`, `process_terminated_between_samples`, `cim_unavailable`,
  `start_time_unavailable`, `working_set_unavailable`, ...);
- lo script non abortisce mai su processo terminato o CIM non disponibile e scrive il CSV in
  `finally`;
- `memory.py` separa righe complete e mancanti, le conta (`missing_process_rows`,
  `status_counts`, `missing_reasons`, `missing_counters`) e le **esclude** da tutte le medie/picchi;
  i massimi restano `null` quando la finestra e vuota.

## 9. Artifact layout

Coerente con il contract (ora marcato IMPLEMENTED):

```text
target/comparison/<run-id>/
  contract.md, manifest.json, hardware.json, checksums.sha256
  source-hashes.json, dataset-hashes.json
  environment/ , data/<category>/ , workspace/
  <method>/<category>/
    config.json, model-state.json
    predictions.csv, maps/ (native, raw/evaluated separate)
    metrics-unified.json, metrics-native.json
    latency-samples.csv, latency-summary.json
    fitting.json, memory.csv, memory-summary.json
    validation.md, run.log, phases.csv
  summary-by-category.csv, macro-summary.csv, RESULT.md
```

`run.py summarize` produce i tre artefatti di sintesi. Campi non disponibili = cella vuota/`null`
con `reason`, mai `0`.

## 10. Test e smoke effettuati

| Verifica | Comando | Esito |
| --- | --- | --- |
| Test Java mirati | `mvn -q -Pbenchmark "-Dtest=..." test` | 8 classi, 19 test, 0 failures |
| Test Python runner | `python tools\comparison\test_runner.py` | `{"tests": 4, "status": "passed"}` |
| Init run smoke | `run.py init --run-id phase2-completion-smoke-20261003 --smoke` | Ok |
| Fit+infer 6 casi | `run.py fit/infer ... --smoke` per i 6 method/category | Ok, exit 0 |
| Latency Java | `run.py latency --method anomalib4j --category metal_nut --smoke` | `jmh.json` + summary |
| Latency Python | `run.py latency --method patchcore --category metal_nut --smoke` | summary `smoke_only` |
| Memory | `run.py memory --method padim --category metal_nut --smoke` e `--method anomalib4j --category bottle --smoke` | missing contati, nessuno zero |
| Summarize | `run.py summarize --run-id ...` | summary/macro/RESULT con `null`+reason |

Evidenze: `target/comparison/phase2-completion-smoke-20261003/`.

## 11. Problemi trovati e corretti

1. **Monitor memoria fragile:** `ErrorActionPreference='Stop'` poteva interrompere lo script su
   processo morente prima di `Export-Csv`, lasciando un CSV assente/vuoto. Corretto con try/catch,
   `Read-Counter`, `status`/`reason` e `Export-Csv` in `finally`.
2. **Valori mancanti non etichettati:** aggiunte colonne `status`/`reason` e parsing difensivo in
   `memory.py`; i missing sono contati ed esclusi, mai convertiti in `0`.
3. **Aggregazione di sintesi assente:** implementato `summarize_run` (summary-by-category.csv,
   macro-summary.csv, RESULT.md) con `null`+reason e macro solo su categorie verificate.
4. **Classpath esterno non riproducibile:** `target/comparison-classpath.txt` era generato
   manualmente; aggiunto `tools/comparison/bootstrap.ps1`.
5. **`evaluate` su smoke:** reso esplicito il rifiuto (ContractError); il wiring resta verificato
   dal test sintetico dell'evaluator.

## 12. OPEN residui

- Esecuzione del **run finale** (fit a budget 209/220, 198 test per metodo, JMH 5x2s + 10x2s + 2
  fork) e popolamento di `metrics-unified.json` veri: solo allora `summarize` produrra valori
  numerici.
- Backfill opzionale del `DECISION_LOG.md` per le convenzioni tecniche emerse (formato mappe,
  schema predictions, classpath bootstrap), se l'operatore lo ritiene una decisione.
- Location durevole delle evidenze `target/` (rischio `mvn clean`): da decidere in fase di
  presentazione.
- Numero worker ORT effettivo resta `unknown` dichiarato (API non lo espone); carico/power/affinity
  dei worker da registrare al run.

## 13. Blocker

Nessun blocker. L'implementazione e `READY_FOR_FINAL_RUN`: non restano scelte progettuali o
modifiche di codice necessarie per eseguire il benchmark completo.
