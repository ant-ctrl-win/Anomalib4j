# Anomalib4j: decisioni tecniche documentate

Registro riconciliato il 2026-10-02. La data e quella della registrazione, non dell'approvazione originaria quando questa non e disponibile. Riporta scelte attuate e decisioni operative esplicitamente confermate dall'utente, distinguendole dalle esecuzioni ancora da svolgere. Stato corrente in [PROJECT_STATE.md](PROJECT_STATE.md).

Aggiornamento del 2026-10-03: decisioni D10-D14 dalla nuova richiesta esplicita dell'utente; contract documentale, nessuna nuova esecuzione.

Aggiornamento del 2026-10-07: decisione D15 di riconciliazione finale post-hoc; nessuna modifica a codice di produzione ne al benchmark congelato.

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
