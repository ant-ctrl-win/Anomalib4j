# Benchmark comparativo CPU: contract

Versione documentale 2, 2026-10-03, aggiornata dopo Phase 1. **DECIDED:** vincoli dell'utente. **VERIFIED:** riscontri locali e smoke identificati nel [report pre-run](BENCHMARK_PRERUN_VERIFICATION.md). **PROPOSED / TO VERIFY:** dettagli residui del futuro runner. Phase 1 ha preparato ambiente CPU, pesi e sonde tecniche; il benchmark finale non e implementato/eseguito. Nessuna nuova linea di ricerca.

## 1. Obiettivo, scope e chiusura

Misurare il compromesso qualita/latenza/memoria di Anomalib4j, PaDiM e PatchCore su CPU dello stesso hardware, batch 1, nelle sole categorie Bottle e Metal Nut. Il risultato riguarda queste implementazioni e configurazioni, non tutti i possibili sistemi che portano lo stesso nome.

Confronto **PRIMARY Native/System**: preprocessing, backbone, detector e runtime propri. Dataset/test, convenzioni di valutazione e macchina comuni sono requisiti minimi anche del primario. Non implicano backbone, allocazione dati o thread equivalenti.

**SECONDARY / DEFERRED:** ulteriori controlli a backbone o threading comuni, solo se indispensabili per risolvere una concreta ambiguita del primario. Nessuna ablation VSA/CNN nel primo ciclo.

Dopo i sei casi: sintesi e limiti, README/repository per pubblicazione/presentazione, chiusura di questa fase. Non occorre che Anomalib4j vinca.

## 2. Categorie, dati e budget

**VERIFIED il 2026-10-03:** enumerazione locale delle immagini, dimensioni da header PNG e presenza annotazioni, coerenti con [MVTEC_DATASET_CENSUS.md](MVTEC_DATASET_CENSUS.md). Dataset immutato: `<DATASET_ROOT>/mvtec-ad-DatasetNinja`.

| Categoria | Normal disponibili | Anomalib4j fit/calibration | PaDiM/PatchCore fit consentito | Test good/anomaly | Risoluzione originale |
| --- | ---: | ---: | ---: | ---: | --- |
| bottle | 209 | 167 / 42 | 209 | 20 / 63 (83) | 900x900 |
| metal_nut | 220 | 176 / 44 | 220 | 22 / 93 (115) | 700x700 |

Difetti: Bottle broken_large 20, broken_small 22, contamination 21; Metal Nut bent 25, color 22, flip 23, scratch 23. Nessuna annotazione omonima mancante. Il censimento riporta 132 oggetti-bitmap Metal Nut: non chiamarli 132 componenti connesse PRO; quelle vanno calcolate dopo l'unione delle maschere.

**DECIDED:** Metal Nut selezionata prima del run per budget comparabile e variabilita di posa/rotazione. Questo razionale non e un risultato misurato sul detector.

Split Anomalib4j: filename ordinati con `String.compareTo`; `Collections.shuffle(list, new java.util.Random(42L))`; prime 167/176 per fit, restanti 42/44 per Z, ordine shuffled conservato. Non sostituire lo shuffle con Python Random(42): stessa cifra non implica stessa permutazione. Salvare manifest ordinati, verificarne unione/disgiunzione. Bottle deve riprodurre i manifest canonici seed 42. Metal Nut e **DECIDED ma non IMPLEMENTED/VERIFIED come training**.

Statistica VSA, archetipi e filtri ricevono solo fit; Z riceve solo calibration. Nessun test, incluso test-good, contribuisce a fitting, soglie, normalizzazioni apprese, model selection o scelta degli iperparametri. Per i competitor verificare/disabilitare qualsiasi split o postprocessor automatico che apprenda sul test; mantenere fitting nativo su tutte le normal dichiarate e documentare ogni eventuale requisito aggiuntivo prima di procedere.

Budget comune = total normal images available. Non dichiarare uguale numero di immagini di fitting: 167 vs 209 e 176 vs 220 sono allocazioni differenti, accettate nel Native/System. Nessun esperimento equal-budget piu restrittivo.

## 3. Metodi e configurazioni congelate

### Anomalib4j: VERIFIED per Bottle, specifica per Metal Nut

- MobileNetV4 frozen SPATIAL_14, 14x14x96 HWC; input RGB 224x224 bicubic ImageNet, NCHW; preprocessing ottimizzato gia validato, stesso contratto.
- Rademacher D=10000 seed 42; epsilon L2 CNN 1e-6; Welford campionario floor VSA std 1e-8; archetipi posizionali L2; Adjoint invariato.
- Raw = 1 - compiledScore. Z posizionale con sigma campionaria e floor 1e-6. Image score = max(Z) sulla griglia. Nessun clamp, sigmoid, smoothing, normalizzazione per immagine o soglia.
- Upsampling Z 14x14 verso risoluzione originale, half-pixel/bordi replicati. Bottle 900x900, Metal Nut 700x700: non forzare 900x900 sulla seconda categoria.
- Encoder hash e descriptor in [PROJECT_STATE.md](../PROJECT_STATE.md). Runtime storico Java 21.0.9, ORT 1.30.0; congelare l'ambiente effettivo del confronto.
- Riferimento scientifico Bottle primario = held-out seed 42: Image 1.0, Pixel 0.9616640201342855, AUPRO 0.8774472899967225. Full-209 e multi-seed solo contesto, non righe intercambiabili.

**VERIFIED:** gli helper correnti `HeldOutBottleCalibration` e `PerformanceBaseline` vincolano filename, cardinalita e fixture Bottle. Non esiste ancora il runner Metal Nut richiesto. Un futuro adattatore sperimentale dovra applicare il medesimo contratto senza cambiare la baseline; nessun codice e scritto in questa attivita.

### Competitor: VERIFIED nel checkout e nello smoke Phase 1

| Campo da congelare | PaDiM | PatchCore |
| --- | --- | --- |
| Implementazione candidata | Anomalib 2.6.2 | Anomalib 2.6.2 |
| Tag/commit esatto e sorgenti | `lib/v2.6.2`, `cc5f400a4a4b1b14b5a3ee5078063c250a8f522e` | Stesso checkout open-edge-platform/anomalib |
| Python/PyTorch/torchvision/altre dipendenze | Python 3.11.15, torch 2.13.0+cpu, torchvision 0.28.0+cpu, timm 1.0.28; freeze allegato | Stesso ambiente da uv.lock della release |
| Backbone/pesi/layer | resnet18, timm `resnet18.a1_in1k`, layer1/2/3, pre_trained=True | wide_resnet50_2, timm `wide_resnet50_2.racm_in1k`, layer2/3, pre_trained=True |
| Preprocessing nativo | RGB float32 [0,1], resize bilinear 256x256 antialias=True, ImageNet mean/std, nessun crop | Identico default Anomalib; niente center crop paper-specific |
| Detector | n_features=None risolto a 100 su 448; Gaussian per posizione | coreset 0.1, num_neighbors=9, float32; NN brute-force PyTorch, query chunk 1024, nessun FAISS |
| Fitting | CPU seed 42, fit gaussiano nativo; default datamodule batch 32 verificato nel sorgente | CPU seed 42, KCenterGreedy/SparseRandomProjection eps=0.9; stesso default batch 32 |
| Inferenza | eval/no_grad float32, intra/inter-op osservati 12/12, blur sigma 4 nativo | Stessi dtype/thread/blur; pesatura score nativa |
| Export raw | `module.model(preprocessed_batch)` -> InferenceBatch, prima di PostProcessor | Stessa API; score [1], mappa [1,1,256,256] |

Fonti ora presenti in `target/prerun/anomalib-2.6.2`, checkout pulito; ambiente isolato `.venv` creato con `uv sync --frozen --extra cpu --no-dev --python 3.11`. Identita/configurazioni, pesi e freeze effettivo in [benchmark-prerun/](benchmark-prerun/), con dettagli nel report. Nessun parametro scelto dopo metriche. Il default datamodule `val_split_mode=SAME_AS_TEST` non deve attivare validazione/PostProcessor appresi sul test: il futuro runner usa manifest train-good e le API native di fit direttamente. Worker data-loader e confine RAM vanno registrati nella Phase 2; lo smoke batch 1 non certifica il fitting completo batch 32.

**VERIFIED, API da preservare:** usare `module.pre_processor.transform`, cioe la trasformazione del callback Lightning nativo. `PreProcessor.forward()` usa invece `export_transform` con antialias=False: non e il percorso Native/System scelto. I metadati timm dei pesi suggeriscono altre risoluzioni/crop per classificazione, ma non sono il preprocessing Anomalib effettivo. Non sostituirli al default della release.

PatchCore originale Amazon Science/FAISS e solo **FUTURE_WORK**; non aggiungerlo al primo ciclo. Non cambiare versione candidata o implementazione silenziosamente se 2.6.2 non e disponibile/compatibile.

## 4. Output e valutazione armonizzata

Per ogni immagine conservare filename/hash, categoria, label, difetto, raw image score nativo, raw anomaly map, shape/dtype e geometria. Per Anomalib4j conservare sia discrepancy che Z; la tabella primaria usa max(Z) e mappa Z. Qui 'raw competitor' significa prima delle calibrazioni/clamp visuali del framework, non prima di passaggi intrinseci al detector.

**VERIFIED:** `.model.eval()(batch)` restituisce InferenceBatch prima del PostProcessor; smoke offline finito e CPU float32. PaDiM: Mahalanobis, upsampling bilinear align_corners=False, blur sigma 4/kernel 33 reflect-same; score = massimo della mappa smussata ([1,1]). PatchCore: NN Euclideo, score del patch piu distante pesato con vicini di supporto ([1]); mappa distanze k=1, upsampling nearest e stesso blur nativo. Smoothing/reweighting/upsampling intrinseci restano inclusi, non sono calibrazione visuale da rimuovere.

Non imporre `max(map)` ai competitor: usare il loro image score raw nativo, con convenzione score maggiore = anomalia verificata nel codice. Nessuna selezione dello score piu favorevole dopo i risultati.

Evaluator comune secondo [LOCALIZATION_CONVENTIONS.md](LOCALIZATION_CONVENTIONS.md):

- GT unione bitmap DatasetNinja alla posizione originale; good vuote. Mantenere le label di `flip` Metal Nut, senza riallinearle o reinterpretarle.
- Image AUROC per categoria, pareggi a 0.5; Pixel AUROC globale per categoria, tutte le good incluse e pareggi raggruppati.
- PRO su componenti 8-connesse dopo unione; ogni regione pesa uguale. FPR su tutti i pixel background della categoria.
- AUPRO esatto con trapezi/interpolazione all'estremo 0.30 e divisione per 0.30; nessuna approssimazione a soglie fisse sostitutiva.
- Mappe allineate alla GT originale tramite il bilineare half-pixel/bordi replicati quando e un semplice resize. Conservare la mappa nativa; niente resize ripetuto se gia allineata. Nessuna normalizzazione per immagine o calibrazione aggiuntiva comune.
- **VERIFIED / gate geometrico chiuso per queste configurazioni:** entrambi usano resize dell'intera immagine verso la tupla 256x256, senza crop o padding geometrico. Anche input rettangolare 300x500 diventa 256x256 (aspect ratio non preservato); le categorie scelte sono quadrate. Mappe native 256x256; mapping evaluation-space = resize bilineare half-pixel/bordi replicati a 900x900 o 700x700, verificato nello smoke. Il padding reflect del blur e interno alla mappa e non cambia il campo visivo. Nessun recupero inventato di regioni non osservate.

Tabelle principali per categoria, tre metriche per ciascuno dei tre metodi. Macro-media aritmetica Bottle/Metal Nut solo sintesi secondaria, mai ROC su categorie concatenate. Metriche native framework in file/tabella distinta con proprio evaluator/configurazione; non mescolarle alle armonizzate.

## 5. Regioni temporali

Input primario gia decodificato in RAM, risoluzione originale, batch 1. Includere eventuali conversioni dall'oggetto decodificato al formato richiesto dal preprocessing. Non precaricare feature o tensori gia normalizzati al posto dell'immagine nel tempo end-to-end.

| Regione | Include | Esclude / nota |
| --- | --- | --- |
| Initialization | Costruzione oggetti/runtime/sessione, caricamento locale pesi quando richiesto | Download escluso e preparato prima; dichiarare import/process startup incluso o separato |
| Fitting/model construction | Preprocessing training, backbone e costruzione nativa detector; per Java entrambe le fasi statistiche/archetipi, compilazione e fit Z | Inferenza test/evaluator esclusi; riportare decode/I/O training separatamente o dichiararne inclusione |
| Ready-to-infer | Misura wall-clock diretta da stato iniziale dichiarato a modello pronto, compresi initialization/fit/calibrazione obbligatoria | Non somma delle mediane delle fasi; se non misurabile riportare N/A con motivo |
| Detection | Percorso nativo immagine -> score realmente separato, se esiste | N/A per competitor che producono sempre anche mappe; niente scorciatoie artificiali |
| Native full inference (primario) | Preprocessing nativo + backbone + detector + generazione raw score e mappa, allocazioni/cleanup ordinari | I/O, decode, export, metriche, overlay, logging non necessario esclusi |
| Full-resolution boundary, se diverso | Percorso precedente + sola trasformazione geometrica della mappa alla risoluzione originale | Registrare separatamente, senza chiamarlo native se l'adattatore e esterno |

Per Anomalib4j il riferimento full e `localizationInference`, inclusa la mappa originale 900x900 (700x700 per Metal Nut); detection e diagnostica separata. Se competitor producono score+map insieme, confrontare quel percorso con localization, dichiarando shape e differenze di perimetro. Se la loro mappa nativa ha risoluzione diversa, aggiungere il tempo full-resolution direttamente misurato per un confronto allo stesso confine di output. Non sottrarre mediane e non attribuire al detector un vantaggio dovuto soltanto a output piu piccoli. L'evaluator numerico resta sempre fuori dai tempi; il costo dell'adattatore geometrico e identificato esplicitamente.

Sessioni/modelli preparati una volta e riutilizzati; stato fitting non aggiornato nel test. Consumare output reali (Blackhole Java; meccanismo equivalente Python verificato), eval/inference mode nativo, operazione completata prima di fermare il timer. Nessuna cache di risultati per filename e nessuna precomputazione del backbone in setup end-to-end.

### Ripetizioni e fixture

**PROPOSED / TO VERIFY per il futuro harness:** preservare il JMH corrente, SampleTime, 5x2s warmup, 10x2s measurement, 2 processi/fork indipendenti, un caller. Python: finestre/repetizioni equivalenti, timer monotono ad alta risoluzione, campioni per chiamata e overhead del timer documentato. Non affermare identita fra campionamento JMH e harness Python; riportare per-processo e aggregati p50/p95/p99, mean, numero campioni e metodo di aggregazione. Non scartare code/GC, non sottrarre overhead arbitrari.

Fixture di latenza preregistrate: `bottle_good_000.png` (baseline storica) e `metal_nut_good_000.png`, identiche fra metodi e precaricate. Accuracy usa tutti gli 83/115 test. La latenza repeated-input non descrive la distribuzione su tutto il dataset, il cold start o throughput concorrente. Conservare vecchi numeri come contesto; il confronto finale richiede condizioni contemporanee documentate, non una riga storica accostata senza verifica ambientale a run nuove.

Esecuzioni seriali sulla macchina; registrare ordine, carico e power profile. Fissare l'ordine dei metodi prima di partire, alternandolo nei due processi di misura per limitare l'effetto dell'ordine. In caso di errore tecnico ripetere con motivazione e conservare gli artefatti del fallimento, non scegliere la run piu veloce.

## 6. Fitting e memoria

Per ogni metodo/categoria misurare almeno una costruzione completa da modello non fittato, seed 42; i costi di setup dei fork non devono entrare nei campioni di inferenza. Specificare passaggi sul dataset, fit batch, feature caching, stato iniziale e finale; non usare una cache di feature per abbassare il costo di costruzione dichiarato. L'eventuale riuso dello stato gia fittato per inferenza va identificato e verificato.

Memoria, in byte e MiB (2^20), con strumento e intervallo di campionamento:

- **Persistent/model state:** dimensione pesi backbone e stato detector/calibrazione separati, piu totale necessario per inferenza. Distinguere file serializzati, tensori/array logici e memoria processo. Non richiedere una nuova API di persistenza Java: se assente, riportare payload logico misurato/derivato dalle strutture e limite sulla dimensione disco. Gli archetipi necessari solo al training non vanno confusi coi filtri runtime.
- **Steady-state process memory:** RSS/working set e private bytes, ove disponibili, dopo warmup e durante inferenza; Java heap da solo non include ORT nativo. Registrare valore stabile e intervallo, GC policy e buffer residenti. La memoria delle immagini predecodificate va quantificata o indicata nel perimetro, non nascosta come memoria modello.
- **Peak fitting memory:** picco processo, inclusi worker/figli se presenti, da prima di initialization attraverso fit/Z fino a ready. Riportare baseline iniziale e picco assoluto; un delta e solo supplementare. Campionamento puo perdere picchi brevi: annotare risoluzione o contatore high-water utilizzato.

Memoria sotto misura in una sessione separata dai tempi. **VERIFIED:** helper esterno `tools/prerun/Measure-ProcessMemory.ps1`, Get-Process per working set/private bytes/lifetime peak working set, CIM per albero parent/child, timestamp e identita PID/start-time. Osservati processi Python e Java. Picco fitting = massimo campionato nella finestra marcata; non somma dei picchi individuali. Lifetime peak e contatore separato, puo precedere il fit. Polling richiesto 100 ms, intervallo effettivo dai timestamp (CIM aggiunge costo); figli brevissimi o orfani possono sfuggire. Il runner deve iniziare l'osservazione prima del fit e mantenerla fino al completamento. Non equivalenza RSS/heap/tensor-bytes. Fitting fallito/OOM resta risultato tecnico, non autorizza tuning.

## 7. Threading e ambiente

**DECIDED:** CPU default/documentata di ogni runtime; un caller non equivale a un worker nativo. Nessuna falsa etichetta single-thread. Controlled-thread SECONDARY / DEFERRED.

Manifest obbligatorio: CPU/model, core fisici/logici, RAM, OS/build, affinity, alimentazione/power profile, carico di fondo, JDK/JVM flags/heap/GC, ORT/provider/session settings, Python, torch intra/inter-op effettivi, OMP/MKL environment, framework/dipendenze e device. Valori non leggibili = unknown con motivo. Provider CPU esplicito, niente GPU. Anomalib4j storico HX 370 (12 core/24 logical), Java 21.0.9/ORT 1.30.0: riverificare al run senza inventare equivalenza ambientale.

**VERIFIED Phase 1:** HX 370, 12/24, RAM 67.772.403.712 byte, Windows 11 Pro build 26200, profilo Bilanciato; affinity osservatore 0xFFFFFF. PyTorch CPU-only, intra/inter 12/12, OMP/MKL override assenti. Java 21.0.9, JMH un caller e -Xmx2g; sonda JVM con questo heap usa G1. ORT espone CPU e AZURE, nessuna GPU; encoder crea SessionOptions default senza aggiungere provider o configurare intra/inter-op. Numero worker ORT effettivo unknown, API corrente non lo espone. Affinity dei futuri worker e carico/power al run finale da registrare, non dedotti dall'osservatore. La sonda JShell ha flag debug propri e non e un benchmark JMH.

## 8. Riproducibilita e artefatti

Congelare prima delle metriche finali: contract revision/hash, commit workspace e diff non committed necessario, framework versione/tag/commit, lock delle dipendenze risolte, configurazioni complete per metodo (stesse fra categorie), seed 42 e varianti obbligatorie motivate, identita/hash pesi, manifest train/test e split ordinati, hash immagini/annotazioni, hardware manifest e comandi esatti. Il solo HEAD Java attuale non descrive tutte le modifiche locali; archiviarne identita senza includere segreti.

Layout **IMPLEMENTED** (`tools/comparison/`, run-id smoke `phase2-completion-smoke-20261003`), senza sovrascrivere gli artefatti storici:

```text
target/comparison/<run-id>/
  contract.md, manifest.json, hardware.json, checksums.sha256
  environment/                 # versioni, lock, comandi, log
  data/<category>/             # train/test manifest, fit/calibration ordinati
  <method>/<category>/
    config.json, model-state.json
    predictions.csv, maps/     # native ed evaluation-space separati
    metrics-unified.json, metrics-native.json
    latency-samples.csv, latency-summary.json
    fitting.json, memory.csv, memory-summary.json
    validation.md, run.log
  summary-by-category.csv, macro-summary.csv, RESULT.md
```

Campi minimi:

- predictions: run_id, method, category, filename, label, defect, image_score, score_kind, native_map_path, eval_map_path, dtype, shape, geometry_id, image_sha256. Mappe lossless con metadata, formato congelato `npy+json/v1` (array NumPy `.npy` v1.0 C-order little-endian float32/float64 + sidecar JSON con filename/geometry_id/score_kind/dtype/shape/array/sha256), precisione originale conservata; no PNG colorato come input metriche. `image_sha256` lega la riga al byte esatto dell'immagine, identico fra metodi per la stessa fixture.
- metrics: method/category, dataset/config hash, n_good/n_anomaly, Image/Pixel AUROC, AUPRO030, numero regioni/foreground/background, evaluator identity, output kind.
- latency: process/fork, iteration, sample, region, duration_ns, fixture hash, output shape, timing mode; warmup distinto ed escluso. Summary con unita e campioni.
- fitting: init/fit/Z/index-build quando separabili, ready_direct, boundaries e I/O policy; null+reason se fase non separabile. Non assegnare zero a una misura assente.
- memory: timestamp, phase, pid/process scope, counter, bytes, sampling_ms; stato backbone/detector/calibration e tipo di stima separati.
- status: VERIFIED_RESULT, FAILED oppure OPEN, con ragione; vietato riempire celle mancanti con baseline di un altro split.

Nella sintesi finale associare ogni numero a run/config/category. Archiviare le evidenze necessarie oltre `target/` prima della pubblicazione: `mvn clean` le rimuove. Location durevole da decidere nel lavoro di presentazione, senza creare ora un sistema di storage.

## 9. Fasi esecutive e gate

0. **Completata nel precedente task:** decisioni, censimento e contract.
1. **VERIFIED, Phase 1 completata:** checkout/tag, ambiente CPU da lock, pesi/hash, export raw, geometria, manifest Java e strumenti memoria verificati; solo piccolo smoke train-good, nessuna accuracy/latency finale. Evidenze in [BENCHMARK_PRERUN_VERIFICATION.md](BENCHMARK_PRERUN_VERIFICATION.md). Identificati anche i limiti residui da registrare nel runner, senza inventare valori runtime.
2. **Task futuro di implementazione minima:** adattatori dati/score e runner sperimentali; preservare la baseline Java. Verifiche mirate di split, nessun leakage, output finiti/completi, coordinate e convenzioni evaluator. Smoke tecnico breve senza selezione iperparametri sul test.
3. **Fitting e valutazione:** un modello per metodo/categoria secondo budget, 198 test complessivi per metodo; metriche per categoria, nessun tuning. Verificare regressione Bottle contro il suo protocollo seed 42; eventuali scostamenti vanno diagnosticati, non corretti cambiando il metodo.
4. **Performance/memoria:** run seriali con input residenti, warmup e ripetizioni; misurare tutte le regioni applicabili. Non riaprire ottimizzazioni preventive. Conservare output, errori e perimetri.
5. **Sintesi e chiusura:** sei righe interpretabili, limiti, README/repository pronti alla presentazione, chiusura della fase. Un risultato negativo non avvia automaticamente nuove ricerche.

## 10. Completion criteria e limiti

Benchmark concluso solo con, per tutti i 3 metodi x 2 categorie, qualita armonizzata, localization/full inference latency, fitting/model construction e memoria riproducibili e interpretabili, con configurazioni e artefatti identificabili. Detection puo essere N/A se non separabile nativamente; ready-to-infer puo essere N/A motivato se non misurabile direttamente, senza sostituirlo con somme. Una misura obbligatoria mancante significa risultato parziale/OPEN, non completamento fittizio.

Limiti da mantenere nella presentazione: allocazioni training diverse; backbone/preprocessing e precisione diversi; Anomalib4j Z held-out contro score nativi competitor; risoluzioni di output/native threading diversi; due categorie non rappresentano tutto MVTec; Bottle ha gia influenzato scelte precedenti; repeated-input latency non e throughput; memoria logica non e RSS; versioni delle implementazioni delimitano i claim.

**FUTURE_WORK / DEFERRED:** ablation VSA/prototipi CNN, few-shot/incremental, nuovi dataset/categorie, tuning, alignment, fine-tuning, SPATIAL_28/fusion, altri calibratori, EfficientAD, PatchCore originale/FAISS, controlled-thread e backbone comune, ottimizzazioni ulteriori, nuovi framework o CLI. Una necessita tecnica indispensabile al benchmark va motivata e limitata al problema, non usata per aprire una diramazione.

## Riferimenti locali

[Stato](../PROJECT_STATE.md), [decisioni](../DECISION_LOG.md), [censimento](MVTEC_DATASET_CENSUS.md), [convenzioni](LOCALIZATION_CONVENTIONS.md), [held-out](HELDOUT_CALIBRATION_VALIDATION.md), [benchmark Java](../development/archive/PERFORMANCE_BENCHMARK_VALIDATION.md), [risultati baseline](../development/PERFORMANCE_BASELINE_RESULT.md), [ottimizzazione verificata](../development/archive/PREPROCESSING_OPTIMIZATION_RESULT.md), [contratto ONNX](../architecture/ONNX_MODEL_CONTRACT.md), [verifica Phase 1 e riferimenti sorgente](BENCHMARK_PRERUN_VERIFICATION.md).
