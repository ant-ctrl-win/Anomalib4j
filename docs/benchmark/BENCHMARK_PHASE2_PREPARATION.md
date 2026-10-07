# Phase 2 preparation: chiusura dei FIX_BEFORE_RUN preparatori

Data: 2026-10-03. Tipo: chiusura dei soli fix semplici e preparatori emersi dall'[audit Phase 1](BENCHMARK_PHASE1_AUDIT.md). Nessun benchmark finale eseguito, nessun training scientifico, nessuna metrica comparativa, nessuna configurazione scientifica o iperparametro modificato, nessun commit/reset/clean. Il runner comparativo completo **non** e implementato: qui sono chiusi solo i prerequisiti.

Esito: **COMPLETATO**. I sei `FIX_BEFORE_RUN` semplici sono chiusi (o, per il percorso di fit, verificati e documentati dal sorgente). Nessun blocker nuovo.

## 1. Fix chiusi

| # audit | Fix | Esito | Evidenza |
|---|---|---|---|
| 1 | Equivalenza numerica resize evaluation-space Python vs Java | CHIUSO | `ResizeEquivalenceTest`, `docs/benchmark/benchmark-prerun/resize-equivalence.json` |
| 2 | Parser categoria/difetto non piu Bottle-only | CHIUSO | `EvaluationLabels`, `EvaluationLabelsTest` |
| 3 | Percorso di fit nativo competitor verificato | CHIUSO (sorgente) | questo documento, sez. 6; sorgente 2.6.2 |
| 4 | Offline enforcement + verifica hash pesi | CHIUSO | `tools/prerun/offline_env.py` |
| 5 | Formato lossless per le mappe | CONGELATO | `tools/prerun/map_io.py`, `NpyMap`, `docs/benchmark/benchmark-prerun/map-format/` |
| 6 | Cattura identita workspace prima delle metriche | CHIUSO | `tools/prerun/workspace_identity.py` |

Non sono stati toccati `BENCHMARK_CONTRACT.md`, le configurazioni, i pesi, il dataset, la matematica di scoring, ne il training Anomalib4j.

## 2. File modificati/creati

Creati:
- `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/EvaluationLabels.java`
- `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/EvaluationLabelsTest.java`
- `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/ResizeEquivalenceTest.java`
- `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/NpyMap.java`
- `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/NpyMapTest.java`
- `tools/prerun/resize_equivalence.py`
- `tools/prerun/offline_env.py`
- `tools/prerun/map_io.py`
- `tools/prerun/workspace_identity.py`
- `docs/benchmark/benchmark-prerun/resize-equivalence.json` (oracolo sintetico, generato)
- `docs/benchmark/benchmark-prerun/map-format/{native_4x5,evaluated_2x3}.{npy,json}` e `fixtures.json` (fixture, generate)

Modificati:
- `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/BottleEvaluation.java`: `defect(String)` ora delega a `EvaluationLabels.defect(...)`; rimossa la regex Bottle-only. La logica delle metriche e invariata.

Nessun altro file di produzione e stato toccato.

## 3. Test eseguiti e risultati

Solo test mirati, nessuna full suite, nessun IT pesante.

```
mvn -q "-Dtest=EvaluationLabelsTest,BottleEvaluationTest,LocalizationArtifactsTest,ResizeEquivalenceTest,NpyMapTest" test
```

| Test | Run | Esito |
|---|---|---|
| `EvaluationLabelsTest` | 5 | PASS |
| `BottleEvaluationTest` | 4 | PASS |
| `LocalizationArtifactsTest` | 2 | PASS |
| `ResizeEquivalenceTest` | 1 | PASS |
| `NpyMapTest` | 2 | PASS |

Gate Python (`target/prerun/anomalib-2.6.2/.venv/Scripts/python.exe`, offline, nessun download):
- `tools/prerun/map_io.py` -> genera e rilegge le fixture: `{"fixtures": 2, "format": "npy+json/v1"}`, asserzioni interne su metadata e dtype superate.
- `tools/prerun/offline_env.py` -> `{"weights": 2, "network_blocked": true}`.
- `tools/prerun/offline_env.py --self-check` -> `{"weights": 2, "negative_cases": 2}` (rifiuta peso mancante e hash errato).
- `tools/prerun/workspace_identity.py --output <temp>` -> HEAD/branch catturati, 0 file critici mancanti.

## 4. Equivalenza resize numerica

Convenzione congelata: `torch.nn.functional.interpolate(..., mode="bilinear", align_corners=False)` (Anomalib) vs `LocalizationMaps.upsample` (Java, half-pixel, clamp ai bordi). L'oracolo e generato in float64 da `tools/prerun/resize_equivalence.py` su 9 fixture sintetiche (gradienti, asimmetrie, bordi/impulsi, 1x1, up/downsample, casi tall/wide). Nessun algoritmo e stato modificato per farli coincidere.

Tolleranza esplicita: **1e-9** (assoluta).

| Fixture | max abs | mean abs |
|---|---|---|
| gradient_3x2_to_7x5 | 1.332e-15 | 3.410e-16 |
| asymmetric_2x3_to_6x6 | 5.551e-16 | 6.322e-17 |
| ramp_4x4_to_9x9 | 1.776e-15 | 4.287e-16 |
| single_pixel_1x1_to_4x4 | 0.000e+00 | 0.000e+00 |
| downsample_5x3_to_3x5 | 8.882e-16 | 2.072e-16 |
| downsample_4x4_to_2x2 | 0.000e+00 | 0.000e+00 |
| tall_1x5_to_4x7 | 0.000e+00 | 0.000e+00 |
| wide_5x1_to_3x6 | 1.776e-15 | 2.220e-16 |
| border_impulse_4x4_to_9x9 | 7.105e-15 | 9.206e-16 |
| **SUMMARY (9 casi)** | **7.105e-15** | **4.160e-16** |

Esito: differenza massima ~7e-15, ben sotto la tolleranza; le due implementazioni sono numericamente equivalenti. La rigenerazione dell'oracolo e un'azione esplicita (`tools/prerun/resize_equivalence.py`), non un side effect del test.

## 5. Parser categoria/difetto supportato

`EvaluationLabels` e l'unica fonte di verita per il parsing MVTec-style; `BottleEvaluation.defect` vi delega.

| Categoria | Difetti ammessi |
|---|---|
| `bottle` | `good`, `broken_large`, `broken_small`, `contamination` |
| `metal_nut` | `good`, `bent`, `color`, `flip`, `scratch` |

Regole: stem = filename senza `.png`; match del prefisso `<categoria>_`; poi match del difetto piu lungo, richiedendo che il resto sia solo cifre ASCII (match piu lungo: `broken_large`/`broken_small` prima di un eventuale `broken`). Input `null`, categoria sconosciuta, difetto sconosciuto o indice non numerico -> `IllegalArgumentException`. I test coprono entrambe le categorie, il longest-match e i casi di rifiuto.

## 6. Percorso di fit nativo competitor verificato

Verificato leggendo il sorgente in `target/prerun/anomalib-2.6.2/src/anomalib/`. I moduli sono PyTorch Lightning module; il datamodule `MVTecAD` di default usa `val_split_mode=ValSplitMode.SAME_AS_TEST` (`data/datamodules/image/mvtecad.py:146`), quindi **non** si deve passare da `Engine`/datamodule (rischio di validazione e PostProcessor appresi sul test).

Loop nativo controllato da usare nella Phase 2 (senza Engine, senza validation, senza PostProcessor appreso):

1. Costruire il modulo dalla config congelata con `post_processor=False`, `evaluator=False`, `visualizer=False`.
2. `module.model.train()`.
3. Per ogni immagine normal del manifest `fit`, in ordine: decode RGB; `tensor = module.pre_processor.transform(image).unsqueeze(0)` (Resize 256x256 bilinear `antialias=True` + Normalize ImageNet, nessun crop); `module.model(tensor)`.
   - PaDiM: `PadimModel.forward` in training accoda gli embedding a `self.memory_bank` (`padim/torch_model.py:184-186`). E cio che fa `training_step` (`padim/lightning_model.py:154`).
   - PatchCore: `PatchcoreModel.forward` in training accoda i patch embedding a `self.embedding_store` (`patchcore/torch_model.py:175-177`, `DynamicBufferMixin`). E cio che fa `training_step` (`patchcore/lightning_model.py:255`).
4. `module.model.eval()`.
5. `module.fit()`:
   - PaDiM: `padim/lightning_model.py:159-162` -> `self.model.fit()` (`padim/torch_model.py:222-241`): `torch.vstack(memory_bank)`, `gaussian.fit`, svuota il bank.
   - PatchCore: `patchcore/lightning_model.py:259-266` -> `self.model.subsample_embedding(self.coreset_sampling_ratio)` (`patchcore/torch_model.py:254-284`): `torch.vstack(embedding_store)` + `KCenterGreedy.sample_coreset`.
6. Inferenza raw: `module.model.eval()(preprocessed_batch)` restituisce `InferenceBatch` con mappa nativa 256x256 float32 e score, prima di ogni PostProcessor.

Nota: `MemoryBankMixin` (`components/base/memory_bank_module.py:69-85`) chiamerebbe `fit()` automaticamente negli hook Lightning; chiamare `module.fit()` direttamente e il percorso controllato e sufficiente.

Limite: questo e una verifica di percorso dal sorgente, non l'esecuzione dei due fitting a budget pieno. La completabilita su 209/220 e 167/176 resta da misurare nella Phase 2.

## 7. Strategia offline / hash

`tools/prerun/offline_env.py` (solo stdlib), pensato per essere importato dal runner:

```python
from offline_env import activate
summary = activate()   # configura env + guard di rete + verifica pesi
```

- `configure_environment()`: `HF_HOME`/`TORCH_HOME` puntano alla cache gia verificata `target/prerun/weights`; impone `HF_HUB_OFFLINE=1`, `TRANSFORMERS_OFFLINE=1`, `HF_HUB_DISABLE_TELEMETRY=1`.
- `enforce_no_network()`: audit hook di processo che rifiuta `socket.connect`, `socket.getaddrinfo`, `socket.sendto`, poi autoprobe.
- `verify_weights()`: per ogni file dichiarato in `docs/benchmark/benchmark-prerun/weights.json` verifica esistenza, `bytes` e SHA-256; in caso di peso mancante o hash diverso solleva `OfflineError` (fallimento netto, nessun download).
- `activate()` combina i tre passi. Eseguito come gate: 2 pesi verificati, rete bloccata. `--self-check` verifica anche i casi negativi.

Pesi verificati: `resnet18.a1_in1k` (46.807.446 byte, sha256 `80c49dee...7b89c612`) e `wide_resnet50_2.racm_in1k` (275.835.296 byte, sha256 `03b71d65...ec812c3000`).

Limite invariato: e una guardia di processo, non una firewall di sistema.

## 8. Formato mappe scelto

Decisione: **`npy+json/v1`**.

- Array: NumPy `.npy` v1.0, C-order, little-endian, `float32` o `float64`. Formato standard, gia disponibile nell'ambiente (numpy), self-describing per dtype e shape, preserva i valori float esatti. Il Python usa `numpy.save`/`numpy.load`; il Java ha un lettore minimo dedicato.
- Metadata: sidecar JSON con lo stesso stem del `.npy`, campi `format`, `filename`, `geometry_id`, `score_kind` (`native`/`evaluated`), `dtype`, `shape`, `array`, `sha256` (hash dei byte del `.npy`).
- Nessuna PNG colorizzata e mai input di metrica; la PNG di `LocalizationMaps.overlay` resta solo visualizzazione.

Layout nei run: `maps/<stem>.native.npy` + `.json` e `maps/<stem>.evaluated.npy` + `.json`; `predictions.csv` referenzia `native_map_path`/`eval_map_path`.

Helper:
- Python `tools/prerun/map_io.py`: `save_map(...)` / `load_map(...)` (con verifica di consenso tra array e sidecar).
- Java `NpyMap.java`: `read(Path)` (supporta `<f4`/`<f8`, header v1/v2/v3, C-order), `readSidecar(Path)`, `sha256(Path)`.

Fixture committate in `docs/benchmark/benchmark-prerun/map-format/`: `native_4x5` (float32, geometry `bottle-900x900-256x256`) e `evaluated_2x3` (float64, geometry `metal_nut-700x700-256x256`), piu `fixtures.json`. `NpyMapTest` dimostra la lettura lossless, i metadata e l'hash.

Questo chiude il punto "formato mappe da congelare" del contract, che qui viene registrato senza modificare `BENCHMARK_CONTRACT.md` (vedi OPEN).

## 9. Workspace identity capture

`tools/prerun/workspace_identity.py --output <dir>` (sola lettura, rifiuta di sovrascrivere una directory non vuota, non fa commit/reset/clean). Il runner lo invoca prima delle metriche finali.

Cattura:
- `git-status.txt`: `git status --porcelain=v1`.
- `git-diff.patch`: `git diff HEAD` (tutte le modifiche non committate dei file tracciati).
- `untracked.txt`: `git ls-files --others --exclude-standard`.
- `workspace.json`: timestamp UTC, versione git, `head`, `branch`, `describe`, status, conteggi, e SHA-256/taglia dei file critici.

File critici hashati: `AGENTS.md`, `ONNX_MODEL_CONTRACT.md`, `docs/benchmark/BENCHMARK_CONTRACT.md`, `docs/PROJECT_STATE.md`, `docs/benchmark/benchmark-prerun/{source,configurations,weights,data-sha256,resize-equivalence}.json`, `docs/benchmark/benchmark-prerun/map-format/fixtures.json`, `pom.xml`, `ImageNetPreprocessor.java`, gli ONNX encoder, `EvaluationLabels.java`, `LocalizationMaps.java`, `LocalizationMetrics.java`.

Esempio di esecuzione (cattura di prova, poi rimossa): `head=83c4197f8771e5ec21db1bcd53508e0d00a4add6`, `branch=main`, `describe=sprint9-spatial28-comparison-dirty`, 38 voci modificate, 81 untracked, 0 file critici mancanti. I conteggi variano con i file aggiunti in questa preparazione; non e stata eseguita alcuna pulizia del workspace.

## 10. OPEN residui per il vero runner Phase 2

1. **Runner comparativo**: adattatore competitor (lettura manifest, decode in-RAM, preprocessing nativo nella regione timed, fit nativo, export score/mappa raw) e adattatore Anomalib4j per **Metal Nut** (non ancora implementato).
2. **Fitting nativo a budget pieno** su 209/220 e 167/176: esecuzione e `fitting.json` (qui solo percorso verificato dal sorgente).
3. **Export predizioni/mappe** con `npy+json/v1` e layout `maps/`, incluse le convenzioni di naming e il collegamento in `predictions.csv`.
4. **Evaluator comune**: cablare `EvaluationLabels` + `LocalizationMaps`/`LocalizationMetrics`/`DatasetNinjaMasks` per categoria (Bottle e Metal Nut) e image AUROC da `pred_score` nativo.
5. **Harness latenza**: JMH `SampleTime` (5x2s warmup / 10x2s measurement / 2 fork) lato Java e equivalente p50/p95/p99 lato Python.
6. **Cattura memoria**: integrazione di `Measure-ProcessMemory.ps1` con marcatori di fase e PID dei workload reali; non confondere working set/private bytes/peak con heap Java o byte dei tensori.
7. **Layout artefatti**: `target/comparison/<run-id>/` secondo `BENCHMARK_CONTRACT.md` sez. 8.
8. **Invio di `workspace_identity.py`** sul run-id immediatamente prima delle metriche finali; la risoluzione dello stato "dirty" (commit o archiviazione) resta una scelta dell'utente.
9. **Backfill documentale**: registrare `npy+json/v1` in `BENCHMARK_CONTRACT.md` sez. 8 ed eventualmente in `DECISION_LOG.md`; qui non e stato fatto per non modificare contract/log senza richiesta.
10. **Limiti da registrare al run** (invariati): worker ORT effettivo unknown, sampling memoria a 100 ms non garantito, peak working set lifetime vs solo-fit, differenza di allocazioni di training 167/176 vs 209/220, provenance export ONNX encoder.

## Riferimenti

- [Audit Phase 1](BENCHMARK_PHASE1_AUDIT.md)
- [Contract](BENCHMARK_CONTRACT.md)
- [Verifica pre-run Phase 1](BENCHMARK_PRERUN_VERIFICATION.md)
- [Stato](../PROJECT_STATE.md)
