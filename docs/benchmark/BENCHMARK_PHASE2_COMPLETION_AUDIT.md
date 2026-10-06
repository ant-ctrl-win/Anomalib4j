# Benchmark Phase 2 - Audit di completamento

Esito sintetico: **READY_FOR_FINAL_RUN**

Nessun **BLOCKER**. L'implementazione Phase 2 avviata da Codex e stata ispezionata, completata nei
punti mancanti e verificata con test mirati e smoke ridotti. **Non** e stato eseguito il benchmark
scientifico finale. Nessun commit/reset/clean/stash; il lavoro di Codex e stato conservato.

Riferimenti: [BENCHMARK_PHASE2_IMPLEMENTATION.md](BENCHMARK_PHASE2_IMPLEMENTATION.md),
[BENCHMARK_CONTRACT.md](BENCHMARK_CONTRACT.md), [PROJECT_STATE.md](../PROJECT_STATE.md).

## 1. Conformita al contract (audit Codex)

| Controllo | Esito | Evidenza |
| --- | --- | --- |
| Nessun tuning/configurazione scientifica cambiata | OK | `configurations.json`, seed 42, iperparametri invariati |
| Solo Bottle + Metal Nut | OK | `run.py --category` choices limitate; manifest Phase 1 |
| Split corretti | OK | `validate_manifests`: 167/42/83 e 176/44/115, disgiunti, unione = competitor |
| PaDiM/PatchCore su percorso nativo verificato | OK | `native.py` usa `module.model(...)` + `module.fit()`, no `Engine` |
| Nessun Engine/datamodule `SAME_AS_TEST` | OK | costruzione manuale, `post_processor/evaluator/visualizer=False` |
| `pred_score` competitor nativo | OK | `worker.py`: `image_score = result.score.item()`, `score_kind=native_raw` |
| Native/evaluation map separate | OK | `maps/<stem>.native.npy` vs `.evaluated.npy` (+ `.raw.npy` Anomalib4j) |
| Formato `npy+json/v1` | OK | `ComparisonArtifacts.writeMap`, `NpyMap`, sidecar con sha256 |
| Preprocessing dentro la regione timed | OK | `timing.py`/JMH: preprocessing incluso; decode fuori |
| Decode/I/O/export/evaluator fuori dal timed | OK | outline in `worker.py`/`ComparisonBenchmark` |
| JMH esistente non alterato metodologicamente | OK | `PerformanceBaseline.java` invariato; nuovo `ComparisonBenchmark` |
| Nessun `mvn clean` | OK | solo `test-compile`/`test` in `bootstrap.ps1` ed esecuzioni test |
| Nessun download runtime | OK | `offline_env` + `competitor_gate` (IP assente, hash, commit upstream) |
| Nessuna cache feature che falsi fit/inference | OK | fit su immagini; nessuna cache usata per abbassare i costi |
| Nessuna sostituzione dei missing con zero | OK | `null`+`reason` in predictions/summary/memory |

## 2. Problemi trovati e correzioni

### 2.1 Monitor memoria (problema aperto lasciato da Codex)

Problema: il monitor poteva intercettare un processo in terminazione, ottenere un contatore vuoto o
fallire l'accesso a proprieta di processo prima di scrivere il CSV; l'aggregazione non etichettava
il campione come incompleto.

Correzione:

- `tools/prerun/Measure-ProcessMemory.ps1` riscritto: nessun abort, `Export-Csv` garantito in
  `finally`, CIM in try/catch (`cim_unavailable`), `Read-Counter` che restituisce `null` (mai `0`),
  colonne `status` (`ok`/`partial`/`missing`) e `reason`, `elapsed_ms` invariante (punto decimale).
- `tools/comparison/memory.py`: parsing difensivo, separazione righe complete/mancanti,
  `missing_process_rows`, `status_counts`, `missing_reasons`, `missing_counters`; le righe mancanti
  sono **escluse** da medie e picchi e **contate**; massimi `null` se la finestra e vuota.

Verifica su processo reale che termina: `mem-smoke` -> 24 righe `ok` + 10 `missing`, aggregazione
`observed 34 / complete 24 / missing 10`, nessuno zero. Nei run del runner: padim/metal_nut
`observed 50 / complete 48 / missing 2` (`process_not_found`,
`process_terminated_between_samples`); anomalib4j/bottle `observed 23 / complete 22 / missing 1`.

### 2.2 Altri completamenti

- **Aggregazione di sintesi assente:** implementato `summarize_run` in `results.py`
  (`summary-by-category.csv`, `macro-summary.csv`, `RESULT.md`) con `null`+`reason` e macro solo su
  categorie verificate; aggiunta azione `summarize` a `run.py`.
- **Classpath non riproducibile:** aggiunto `tools/comparison/bootstrap.ps1` che compila e scrive
  `target/comparison-classpath.txt` (prima generato esternamente). Eseguito con successo.
- **`evaluate` su smoke:** reso esplicito il rifiuto (`ContractError`).

## 3. Test e smoke passati

| Verifica | Esito |
| --- | --- |
| Test Java mirati (8 classi: EvaluationLabels, BottleEvaluation, LocalizationArtifacts, ResizeEquivalence, NpyMap, ComparisonModel, ComparisonArtifacts, ComparisonEvaluator) | 19 test, 0 failures |
| `tools/comparison/test_runner.py` | `{"tests": 4, "status": "passed"}` |
| `run.py init --smoke` | manifest/hash/hardware/checksums ok |
| `fit` + `infer` per i 6 method/category (`--smoke`) | exit 0 tutti; predictions 14 colonne; map native/evaluated separate |
| Latency Java abbreviata (`anomalib4j`/`metal_nut`) | JMH smoke + `latency-summary.json` |
| Latency Python abbreviata (`patchcore`/`metal_nut`) | `latency-summary.json` (`smoke_only`) |
| Memory smoke (`padim`/`metal_nut`, `anomalib4j`/`bottle`) | CSV + summary, missing contati, nessuno zero |
| `run.py summarize` | summary/macro/RESULT con `null`+reason |

Run smoke di riferimento: `target/comparison/phase2-completion-smoke-20261003/`.

## 4. Verifica dei sei casi (wiring)

Per ciascuna coppia method/category il flusso `fit -> infer -> evaluate -> latency -> memory` e
cablasto; `evaluate` e `latency`/`memory` sono rifiutati/abbreviati in smoke come da contract.
`evaluate` reale non e stato eseguito (rifiuta lo smoke) ed e coperto dal test sintetico
dell'evaluator.

| method | category | fit | reload+infer | evaluate wiring | latency | memory |
| --- | --- | --- | --- | --- | --- | --- |
| anomalib4j | bottle | smoke OK | smoke OK | test sintetico | JMH path | smoke OK |
| anomalib4j | metal_nut | smoke OK | smoke OK | test sintetico | smoke OK | path |
| padim | bottle | smoke OK | smoke OK | test sintetico | smoke OK (worker) | path |
| padim | metal_nut | smoke OK | smoke OK | test sintetico | path | smoke OK |
| patchcore | bottle | smoke OK | smoke OK | test sintetico | path | path |
| patchcore | metal_nut | smoke OK | smoke OK | test sintetico | smoke OK | path |

## 5. Artifact layout e campi non disponibili

Layout verificato conforme al contract (`target/comparison/<run-id>/`), con
`summary-by-category.csv`, `macro-summary.csv`, `RESULT.md` a livello radice. Nei file di sintesi
del run smoke le celle non misurate sono **vuote/`null` con `reason`**, mai `0`. La grafia
`VERIFIED_RESULT`/`FAILED`/`OPEN` con reason e rispettata (`metrics-native.json` = `open` con
reason).

## 6. Blocker

Nessun blocker.

## 7. Classificazione finale

**READY_FOR_FINAL_RUN.** Non restano scelte progettuali o modifiche di codice necessarie per
eseguire il benchmark completo. Resta solo l'esecuzione del run scientifico finale (fit a budget
pieno, 198 test per metodo, JMH completo, evaluate reale, summarize) e le attivita di
presentazione/archiviazione, che sono fuori dal perimetro di questo task.
