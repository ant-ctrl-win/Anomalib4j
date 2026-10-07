# Sprint 7 - Ricostruzione dello stato parziale dopo l'interruzione di Codex

Rapporto redatto in sola ispezione sulla radice del repository Anomalib4j.
Nessun file di codice e stato modificato. Il progetto **non e sotto Git**: nella root non esiste `.git`; non e stata eseguita nessuna operazione Git.

## Metodo e limiti

- L'analisi si basa su timestamp, contenuto dei sorgenti, artefatti sotto `target` e log gia presenti.
- Uniche azioni eseguite **ora** (per stabilire lo stato di compilazione, senza rieseguire il training `bottle`):
  - `mvn -B -DskipTests test-compile` -> `BUILD SUCCESS`;
  - `mvn -B -Dtest=BottleEvaluationTest test` -> `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- Queste due azioni hanno aggiornato solo output di build sotto `target` (classi di test), **non** i sorgenti.
- La valutazione reale `BottleRealEvaluationTest` **non** e stata eseguita (avrebbe richiesto il training completo `bottle`).

Le date riportate sono quelle di filesystem osservate (anno 2026).

---

## 1. File introdotti o modificati di recente

### 1.1 File Sprint 7 (piu recenti di tutti)

| File | Timestamp | Dimensione | Natura |
| --- | --- | --- | --- |
| `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/BottleEvaluation.java` | 29/09/2026 16:05:57 | 5.777 B | NUOVO, helper di test |
| `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/BottleEvaluationTest.java` | 29/09/2026 16:05:57 | 1.992 B | NUOVO, test unitari puri |
| `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/BottleRealEvaluationTest.java` | 29/09/2026 16:08:31 | 10.950 B | NUOVO, valutazione reale |
| `.idea/workspace.xml` | 29/09/2026 16:11:34 | 5.005 B | stato IDE (non e codice) |

`BottleRealEvaluationTest.java` e l'ultimo sorgente modificato (16:08), seguito solo dallo stato IDE (16:11): compatibile con un'interruzione subito dopo la scrittura del test, prima di una sua esecuzione.

### 1.2 Contenuto dei file Sprint 7

**`BottleEvaluation.java`** (package `...anomalib.evaluation`, classe `final` package-private). Helper condiviso, nessuna dipendenza nuova:
- `defect(String)` valida il nome `bottle_(good|broken_large|broken_small|contamination)_<n>.png` e ne estrae il difetto;
- `auroc(double[] good, double[] anomaly)` calcolo AUROC a coppie con tie = 0.5 (richiede entrambe le classi non vuote e score finiti);
- `percentile(double[], double)` interpolazione lineare (tipo numpy), non muta l'input;
- `summarize(double[])` accetta esattamente 196 valori (14x14), restituisce `min, mean, max, row, column` del **max grezzo** (primo massimo in caso di pareggio);
- `distribution(String, double[])` riga CSV `gruppo,count,min,p25,median,mean,p75,max`;
- `heatmap(Path, filename, raw, low, high)` disegna una PNG 1000x1060 con scala condivisa `low..high`, senza calibrazione; usa `ImageIO`;
- record `Summary(double min, double mean, double max, int row, int column)` e `Sample(String filename, String defect, float[] features, double[] raw)`.

**`BottleEvaluationTest.java`**: 4 test unitari puri (nessun training/dataset):
- AUROC con inversione di ranking, pareggi e casi invalidi;
- `summarize` con valori grezzi non clampati e primo massimo;
- parsing di tutti i nomi difetto `bottle`;
- percentili interpolati senza mutazione dell'array.

**`BottleRealEvaluationTest.java`**: test reale `evaluatesRawBottleScoresAndMeasuresScoringPaths`, cablaggio dello Sprint 7:
- dataset da system property `anomalib.dataset`, default `../Anomalib4j_md/mvtec-ad-DatasetNinja`;
- output in `target/`;
- scopre i `bottle_*.png` in `test/img`, asserisce **83** immagini, le ordina con i `good` per primi;
- apre `OnnxMobileNetV4Encoder.Variant.SPATIAL_14`, costruisce `ModelDescriptor` (`bottle-normal-v1`, D=10.000, seed=42, `NormalizationPolicy(1e-6)`, version=1, `preprocessingId` dell'encoder);
- allena con `NormalImageTrainer` sulle 209 immagini `bottle_good` di training, asserendo `40_964` osservazioni;
- costruisce `ExplicitVsaScorer` e per ogni immagine di test confronta **compiled `model.filters().scoreCell` vs explicit `explicit.scoreCell`** su tutte le 196 celle, con `assertEquals(..., 1e-9)` e tracciando `maximumError`; asserisce `16_268` confronti;
- `raw[p] = 1 - compiledScore`; score immagine = `summarize(raw).max()`;
- asserisce 20 `good` e 63 anomalie (broken_large 20, broken_small 22, contamination 21);
- scrive `target/bottle-evaluation.csv`, `target/bottle-raw-heatmaps.csv`, `target/bottle-distributions.csv`;
- genera heatmap PNG in `target/bottle-heatmaps/` (good: 2 esempi, altri gruppi: 1 ciascuno) su scala condivisa globale;
- benchmark del **solo scoring** (feature gia estratte, CNN esclusa): warmup + 9 round alternati explicit/compiled, con `measure(...)`, medie per mappa; scrive `target/bottle-benchmark.csv`;
- calcola AUROC, mediane, P25/P75 e speedup; scrive `target/bottle-metrics.txt` e stampa su stdout un blocco che inizia con `SPRINT7_RESULT`;
- inclusi `Runtime.availableProcessors`, versione Java e `os.name` nelle metriche.

### 1.3 File Sprint 6 (gia consolidati, precedenti all'interruzione)

Ultimo set compilato con successo (vedi sezione 4). Timestamp 15:38-15:46:
- main: `onnx/OnnxMobileNetV4Encoder.java`, `onnx/ImageNetPreprocessor.java`, `projection/NormalizedPatchProjection.java`, `training/NormalImageTrainer.java`, `validation/ExplicitVsaScorer.java`, `model/ModelDescriptor.java`, `memory/PositionalMemoryBuilder.java`;
- test: `model/PreprocessingContractTest.java`, `projection/NormalizedPatchProjectionTest.java`, `training/NormalImageTrainerTest.java`, `training/BottleTrainingTest.java`, `onnx/OnnxMobileNetV4EncoderTest.java`, `onnx/ImageNetPreprocessorTest.java`.

Report e log correlati: `SPRINT6_RESULT.md` (15:53:53), `target/sprint6-run.log` (15:53:02), `target/surefire-reports/*` (15:47-15:53).

---

## 2. Stato dello Sprint 7

| Requisito previsto | Stato | Base fattuale |
| --- | --- | --- |
| Valutazione delle 83 immagini `bottle` di test | IMPLEMENTATO MA NON ESEGUITO | codice in `BottleRealEvaluationTest`; nessuna esecuzione, nessun artefatto |
| 20 `good` / 63 anomalie | IMPLEMENTATO MA NON ESEGUITO | asserzioni presenti; conteggi non verificati a runtime |
| Raw heatmap 14x14 | IMPLEMENTATO MA NON ESEGUITO | `BottleEvaluation.summarize`/`heatmap` + `raw[196]`; nessuna PNG prodotta |
| Image score = `max(raw discrepancy)` | IMPLEMENTATO MA NON ESEGUITO | `summarize(...).max()` usato per classificazione/AUROC |
| Image AUROC | PARZIALMENTE IMPLEMENTATO | funzione `auroc` + unit test **eseguito ora** (4/4 verdi); valore AUROC reale **non** calcolato |
| CSV dei risultati | IMPLEMENTATO MA NON ESEGUITO | scritture per `bottle-evaluation.csv`, `bottle-raw-heatmaps.csv`, `bottle-distributions.csv`, `bottle-benchmark.csv`, `bottle-metrics.txt`; nessun file presente |
| Confronto explicit-VSA vs compiled-Adjoint | IMPLEMENTATO MA NON ESEGUITO | confronto e `maximumError` nel test; nessun valore reale |
| Benchmark del solo scoring (senza CNN) | IMPLEMENTATO MA NON ESEGUITO | `measure(...)` su feature pre-estratte, feature-map scoring; nessun risultato |
| Report `SPRINT7_RESULT.md` | NON IMPLEMENTATO | nessun codice scrive il file; il test stampa solo `SPRINT7_RESULT` su stdout e scrive `bottle-metrics.txt`; il file non esiste |

Nota: un test **puro** dello Sprint 7 (`BottleEvaluationTest`) e percorribile senza CNN/dataset ed e stato verificato ora con esito positivo; la parte "reale" del test e l'unica non eseguita.

---

## 3. Risultati già prodotti

Ricerca esaustiva su tutto il repository e sulla cartella genitore dei checkout:

- `SPRINT7_RESULT.md`: **assente**.
- `target/bottle-evaluation.csv` e CSV equivalenti (`bottle-raw-heatmaps.csv`, `bottle-distributions.csv`, `bottle-benchmark.csv`): **assenti**.
- `target/bottle-metrics.txt`: **assente**.
- `target/bottle-heatmaps/` e heatmap diagnostiche: **assenti**.
- test class `evaluation` sotto `target`: **assente prima di questa ispezione** (nessuna traccia di compilazione precedente).
- log Maven dello Sprint 7: **assenti**; esiste solo `target/sprint6-run.log`.
- report Surefire: presenti solo quelli dello Sprint 6 (fino a 15:53); **nessun** report per `evaluation.*`.
- benchmark e artefatti temporanei dello Sprint 7: **assenti**.

**Nessun risultato dello Sprint 7 e quindi disponibile** (immagini processate, AUROC, distribuzioni, errore massimo, tempi, speedup). Gli unici numeri registrati provengono dallo **Sprint 6** (`SPRINT6_RESULT.md` / `sprint6-run.log`): 209 immagini, 418 passaggi, 40.964 osservazioni, 196 archetipi, 588 confronti, `maxError = 9.9475983006414030e-13` (tolleranza `1e-9`), 47 test totali, durata test reale 302,6 s.

---

## 4. Stato compilazione/test

### 4.1 Run precedente (artefatti gia presenti, NON rieseguito)

- Comando registrato in `target/sprint6-run.log`: `mvn test`, terminato `2026-09-29T15:53:02+02:00`.
- Esito: `Tests run: 47, Failures: 0, Errors: 0, Skipped: 0` -> `BUILD SUCCESS`, durata Maven 05:05 min.
- Il run include: adjoint (2+5), calibration (3), inference (5+2), memory (4+4), model (1), onnx (3+4), projection (4+2), training (`BottleTrainingTest` 1, 302,6 s; `NormalImageTrainerTest` 3), validation (4).
- **Il run NON include** i test del package `evaluation`: al momento del run (15:53) i file di valutazione (16:05-16:08) non esistevano ancora.

### 4.2 Verifiche eseguite ora (dichiarate come tali)

- `mvn -B -DskipTests test-compile` -> `BUILD SUCCESS`; compilazione di **20** file di test verso `target/test-classes`, inclusi i tre file `evaluation`. Quindi **il codice dello Sprint 7 compila**.
- `mvn -B -Dtest=BottleEvaluationTest test` -> `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`. Quindi gli helper puri (AUROC, percentili, summarize, parsing difetti) sono corretti sui casi coperti.
- `BottleRealEvaluationTest` risulta **compilato** (`.class` presente) ma **mai eseguito**: nessun artefatto prodotto.

### 4.3 Sintesi

- Compilazione main+test del tree attuale: **OK** (verificato ora).
- Test puri Sprint 7: **OK** (verificato ora).
- Test reale Sprint 7: **NON eseguito** (stato: implementato e compilato, non provato a runtime).
- Ultimo `mvn test` completo documentato: **47/47 verdi**, ma antecedente ai file di valutazione.

---

## 5. Punto di interruzione

### COMPLETATO

- Infrastruttura Sprint 6 stabile e verde (encoder ONNX, preprocessing, trainer, memoria, adjoint, scorer esplicito, statistiche), 47 test.
- Scrittura completa del codice di valutazione Sprint 7: helper `BottleEvaluation`, unit test `BottleEvaluationTest`, test reale `BottleRealEvaluationTest`.
- Verifica (ora) che il codice Sprint 7 **compila** e che i 4 test puri **passano**.

### IN CORSO / PARZIALE

- `BottleRealEvaluationTest` e pronto ma **non eseguito**: nessuna delle metriche reali (AUROC, distribuzioni good/anomaly, errore max explicit vs compiled, tempi explicit/compiled, speedup) e disponibile.
- Nessun artefatto (`bottle-*.csv`, `bottle-metrics.txt`, `bottle-heatmaps/`) e stato prodotto.
- Il deliverable `SPRINT7_RESULT.md` non e generato: il test produce solo output su stdout (`SPRINT7_RESULT`) e `target/bottle-metrics.txt`.

### MANCANTE

- Esecuzione del test reale e raccolta dei risultati.
- Creazione del report `SPRINT7_RESULT.md` (nessun codice attualmente lo scrive).

### PROSSIMA AZIONE MINIMA

Punto tecnico esatto di ripresa per Codex:

1. Eseguire la valutazione reale gia implementata, senza modificare codice:
   `mvn -B -Dtest=BottleRealEvaluationTest test`
   (default dataset `../Anomalib4j_md/mvtec-ad-DatasetNinja`; il test esegue il training delle 209 immagini `bottle_good` e richiede alcuni minuti, come lo Sprint 6).
2. Da quella esecuzione raccogliere `target/bottle-metrics.txt` (che contiene `auroc`, `maxError`, tempi e `speedup`) e i CSV/heatmap prodotti, e tradurli nel file mancante `SPRINT7_RESULT.md`.

In sintesi: la ripresa e al passo "eseguire `BottleRealEvaluationTest` e materializzare `SPRINT7_RESULT.md`", non a livello di scrittura del codice di valutazione (gia completo e compilabile).
