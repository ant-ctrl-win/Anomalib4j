# Spatial edge artifact audit: Metal Nut

Data: 2026-10-03. Caso congelato: `target/comparison/final-comparison-20261003/anomalib4j/metal_nut/`.

## Esito

- **Coordinate semantics: CORRECT** nelle verifiche di codice, marker e ricostruzione numerica eseguite.
- **Border activations: MIXED**. Sono osservabili differenze del backbone ai bordi e un effetto locale della standardizzazione posizionale. Il campo ricettivo non coincide con la cella disegnata, ma le perturbazioni indicano soprattutto sensibilita allo sfondo vicino per le tre celle inferiori indagate.
- **Bug concreto nella pipeline: nessuno trovato.** L'audit non dimostra assenza universale di difetti; esclude le incongruenze spaziali cercate nei percorsi verificati.
- Le attivazioni influenzano anche il ranking image-level. Non spiegano la maggior parte degli errori osservati su Metal Nut.

La precedente Phase 2 e considerata COMPLETED EXTERNALLY. Questo audit usa lo stato finale del workspace e non riprende i suoi TODO. Fonti: [stato corrente](../PROJECT_STATE.md), [contratto](../benchmark/BENCHMARK_CONTRACT.md), [risultati finali](../benchmark/BENCHMARK_FINAL_RESULT.md), [diagnostica posizione](METAL_NUT_POSITION_ROTATION_DIAGNOSTIC.md), [diagnostica RAW/Z](METAL_NUT_RAW_VS_Z_DIAGNOSTIC.md).

## Perimetro ed evidenze

Tutti i nuovi script, classi diagnostiche, CSV, JSON e PNG sono in [spatial-edge-audit](../../target/comparison/final-comparison-20261003/diagnostics/spatial-edge-audit/), indicata sotto come `AUDIT/`. Nessuna modifica a produzione, training, configurazioni, score o artefatti congelati. Nessun nuovo fitting o benchmark comparativo. Sono state eseguite inferenze diagnostiche sul modello esistente.

`integrity.json` verifica SHA-256 invariati su **818 file**: artefatti del caso Metal Nut, sorgenti Java e strumenti Python presenti alla fotografia iniziale. SHA-256 ONNX: `48da88a8da5f1f1f1ce2308d19128c0f302498aa1889882263620cad21bdd26e`.

I tag **VERIFIED** identificano misure o codice verificato; **INFERENCE** identifica interpretazioni; **OPEN** identifica limiti non risolti. Le soglie Z>2/3/4 sono descrittori diagnostici, non soglie operative scelte per il benchmark. Senza soglia operativa, un good con score elevato non e formalmente un falso positivo di AUROC.

## 1. Indexing end-to-end

**VERIFIED.** Convenzione unica: `p = row * 14 + column`, offset feature `p * 96 + channel`.

| Passaggio | Implementazione verificata | Convenzione |
|---|---|---|
| Input | `ImageNetPreprocessor` | NCHW 1x3x224x224 |
| Output ONNX | `OnnxMobileNetV4Encoder.extract`, grafo congelato | 1x14x14x96; Transpose finale `[0,2,3,1]` da NCHW a NHWC |
| Copia feature | `getFloatBuffer()` in `extract` | Copia sequenziale HWC, nessuno scambio di assi |
| Statistiche VSA | `NormalImageTrainer`, `NormalizedPatchProjection` | Patch da offset `p*C`; statistiche globali per dimensione D, non una tabella spaziale |
| Archetipi | `PositionalMemoryBuilder` | Accumulatore e archetipo a offset `p*D` |
| Filtri | `AdjointCompiler`, `AdjointFilterBank` | Pesi `p*C+c`, bias `p` |
| RAW | `HeldOutBottleCalibration` | `raw[p] = 1 - scoreCell(features,p)` |
| Z | `PositionalRawCalibration` | `(raw[p]-mean[p])/sigma[p]`; CSV row=`p/14`, column=`p%14` |
| Export/reload | `ComparisonArtifacts.writeMap`, `NpyMap.read`, Python `map_io` | NPY C-order float64 little-endian, shape `[height,width]` |
| Upsampling | `ComparisonRunner`, `LocalizationMaps` | Bilineare half-pixel, bordo replicato, output `row*width+column` |
| Overlay | `LocalizationMaps.overlay` | x=`p%width`, y=`p/width`; pannello overlay traslato nel canvas, nessun flip |

Controlli eseguiti con Java/ONNX Runtime esistenti, classi diagnostiche compilate separatamente in `AUDIT/classes`:

- Output diretto della sessione e `encoder.extract` su good_000: **18.816 float, maxAbsDiff 0** (`probe-equivalence.txt`).
- Modello ricaricato dagli artefatti, good_000/009/021: **588 celle RAW e Z**, errore massimo **0** rispetto ai file congelati.
- Explicit VSA indipendente, statistiche e archetipi letti dall'archivio: **24 celle**, errore massimo sul raw compilato **6.252776074688882e-13**. Conteggi archetipi: tutti 176.
- Upsampling Java delle tre mappe Z: **1.470.000 valori**, errore massimo **0** rispetto alle evaluation maps congelate.
- Ricostruzione `(raw-mu)/sigma` su tutte le 22 good: **4.312 celle**, errore massimo **0**.

Evidenza: `frozen-path-verification.json`, `statistics.json`, `AuditFrozen.java`, `AuditProbe.java`.

## 2. Marker sintetici

**VERIFIED.** Mappa 14x14 con valori univoci 1..196: round-trip NPY esatto. Cinque impulsi separati attraversano writer, reader, upsampling Java 700x700 e overlay Java. Anche i pixel RGB del pannello sono verificati numericamente.

| Cella (r,c) | p | Valore univoco | Centroide del plateau massimo dopo resize (x,y) |
|---|---:|---:|---|
| (0,0) | 0 | 1 | (12,12) |
| (0,13) | 13 | 14 | (687,12) |
| (13,0) | 182 | 183 | (12,687) |
| (13,13) | 195 | 196 | (687,687) |
| (4,5) | 61 | 62 | (274.5,224.5) |

Gli angoli hanno plateau da replica del bordo, non un centroide uguale al centro nominale del tile. L'impulso interno raggiunge 0.9801, non 1: il centro half-pixel cade fra quattro pixel. Entrambi sono effetti attesi dell'interpolazione. Nessun transpose, flip o off-by-one rilevato.

Evidenza definitiva: `markers-verified/marker-results.json` e cinque `*.overlay.png`. I file marker parziali nella radice AUDIT appartengono a un primo controllo interrotto per un'aspettativa diagnostica errata sul blending; l'aspettativa e stata corretta al coefficiente effettivo 0.55. Nessuna correzione al codice di overlay.

## 3. Frequenza spaziale sulle 22 good

**VERIFIED.** Outer border = 52 celle; seconda corona = 44; interno residuo = 10x10. Le quattro righe/colonne laterali condividono gli angoli e non vanno sommate. Percentuali aggregate su immagini e celle del gruppo.

| Gruppo | Argmax | Mean Z | Median Z | Max Z | Z>2 | Z>3 | Z>4 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Outer border | 6 | 0.0130 | -0.3347 | 4.9551 | 4.983% | 0.699% | 0.087% |
| Seconda corona | 2 | 0.0250 | -0.3551 | 5.0551 | 6.715% | 1.446% | 0.310% |
| Interno 10x10 | 14 | 0.0614 | -0.1539 | 9.1228 | 5.909% | 2.045% | 0.773% |
| Top row | 0 | -0.0713 | -0.3802 | 3.3654 | 3.896% | 0.649% | 0% |
| Bottom row | 5 | 0.1260 | -0.2999 | 4.9551 | 9.416% | 1.623% | 0.325% |
| Left column | 1 | 0.0046 | -0.3290 | 3.4666 | 4.870% | 0.325% | 0% |
| Right column | 3 | 0.0485 | -0.2418 | 4.9551 | 3.896% | 0.974% | 0% |

Bottom row: **5/22 = 22.73%** degli argmax su **7.14%** delle celle, rapporto **3.18x**. Tutto il bordo: **27.27%** degli argmax su **26.53%** delle celle. L'eccesso e quindi inferiore e localizzato, non generalizzato al perimetro. Il massimo nominale peggiore e interno: good_009, (4,5), Z=9.1228.

Il p binomiale 0.01754 in `statistics.json` assume celle uniformemente scambiabili: e solo descrittivo, post-hoc, con n=22; non costituisce una prova inferenziale valida di causalita del bordo.

Evidenze complete per tutte le 196 posizioni: `cells.csv`, `groups.csv`. Figure: `argmax-frequency.png`, `mean-z.png`, `exceedance-z3.png`.

## 4. Calibration edge statistics

**VERIFIED.** Count=44 in ogni posizione; **zero** posizioni colpite dal floor 1e-6.

| Gruppo | Mean mu | Median mu | Mean sigma | Median sigma | Range sigma | CV sigma fra posizioni |
|---|---:|---:|---:|---:|---|---:|
| Outer border | -70.648 | -74.020 | 18.350 | 15.059 | 1.808..42.249 | 0.783 |
| Nonborder 12x12 | -64.328 | -62.551 | 15.919 | 16.581 | 0.713..35.025 | 0.541 |
| Seconda corona | -58.930 | -56.077 | 21.743 | 22.613 | 6.560..35.025 | 0.338 |
| Interno 10x10 | -66.703 | -65.164 | 13.356 | 14.954 | 0.713..31.768 | 0.589 |
| Bottom row | -71.639 | -74.020 | 16.921 | 14.184 | 2.059..36.494 | 0.819 |

**INFERENCE.** Non emerge sigma sistematicamente inferiore su tutto il bordo: la media e anzi maggiore del nonborder. Alcune celle hanno sigma piccola, altre molto grande. Mu del bordo e mediamente piu negativa, ma l'estremo globale negativo e interno (-107.737). Neppure la frequenza Z>3 e maggiore su tutto il bordo. L'effetto e posizionale, non una regola globale edge-versus-interior.

## 5. Occupancy da immagine

**VERIFIED.** Maschera indipendente dagli score: luminanza, Otsu, componente maggiore con 8-connettivita, riempimento dei buchi. Campionamento a coordinate 25+50*i nelle immagini 700x700, arrotondamento dei centri nominali half-pixel 24.5+50*i. Analizzati separatamente fit176, calibration44, tutti i 220 training normal e 22 test good.

**76/196 celle** sono sempre esterne alla silhouette sia sui 220 training sia sui 22 good; ospitano **7/22 argmax**. Le tre posizioni inferiori richieste ricadono sullo sfondo nell'immagine di controllo. Le figure occupancy sono state ispezionate visivamente.

Correlazione Spearman fra occupancy training e: mu **-0.093**, sigma **-0.336**, mean Z **0.027**, argmax count **0.135**. Correlazioni descrittive su celle spazialmente dipendenti: non supportano una relazione semplice occupancy -> alto Z. Le probabilita di ciascun subset sono in `cells.csv` e `*.occupancy.npy`.

**Limite:** la silhouette riempita include il foro centrale della rondella. Occupancy significa appartenenza all'ingombro geometrico, non necessariamente superficie metallica. I supporti non riempiti sono conservati in `*.support.npy`; la conclusione sullo sfondo esterno inferiore non dipende dal foro. La diagnostica di posa precedente dichiara 8-connettivita, mentre la sua chiamata `ndi.label` senza struttura usa la connettivita predefinita 4; qui la struttura 3x3 e esplicita. Non e un bug di scoring e non viene quantificato retroattivamente il suo effetto sul report precedente.

## 6. Campo ricettivo empirico

**VERIFIED.** Immagine di riferimento good_000, preprocessing Java effettivo. Perturbati uno alla volta i 196 blocchi 16x16 dell'input 224x224. Incremento/decremento RGB comune ai tre canali, ampiezze 0.02 e 0.05 in unita [0,1], trasformato con le std ImageNet. Per ogni blocco: media della variazione L2 della feature nelle due direzioni. Totale **784 forward perturbati**, cinque target osservati insieme.

Quote della somma delle risposte L2, ampiezza 0.05:

| Target | Blocco di picco | Max delta L2 | Blocchi puramente sfondo | Blocchi a maggioranza silhouette | Blocchi >1% del picco |
|---|---|---:|---:|---:|---:|
| (13,13) | (13,12) | 104.948 | 91.45% | 1.24% | 20 |
| (13,2) | (13,3) | 159.729 | 82.50% | 1.64% | 24 |
| (13,4) | (13,4) | 152.789 | 80.49% | 3.33% | 27 |
| (4,5) | (4,5) | 37.206 | 9.80% | 83.39% | 77 |
| (7,7) | (7,7) | 93.512 | 0.27% | 99.40% | 38 |

Le quote non sommano necessariamente a 100%: esistono blocchi misti. Non sono probabilita ne una decomposizione additiva causale dello score. Il controllo (7,7) cade nel foro incluso nella silhouette.

Con ampiezza 0.02, per (13,13) lo sfondo puro pesa 93.37%, la maggioranza silhouette 0.73%. Correlazione dei ranking fra ampiezze >0.9996 sui tre target inferiori, >=0.993 sugli altri due.

**INFERENCE.** Le celle inferiori ricevono anche informazione non locale, ma in questo esperimento domina nettamente lo sfondo vicino, non la rondella. Non basta invocare il receptive field per spiegare tutto come risposta semanticamente corretta all'oggetto.

**OPEN:** una sola immagine e una famiglia di perturbazioni; misura L2 sulle feature prima della normalizzazione CNN, non attribuzione diretta allo score. Intervento sul tensore senza clipping: alcuni pixel positivamente perturbati possono superare 1. Le due ampiezze sono controlli di robustezza, non una misura infinitesimale. Nessuna conclusione causale quantitativa su tutti i 22 good.

Evidenze: `influence.csv`, `influence-summary.csv`, dieci `influence-*.npy`, `influence-maps.png`, `actual-preprocessed-input.png`.

## 7. Padding e controlli sintetici

**VERIFIED.** Grafo ONNX: 46 nodi, 24 Conv, 16 Relu, 5 Add, 1 Transpose. Convoluzioni spaziali con padding simmetrico `[1,1,1,1]` per 3x3 e `[2,2,2,2]` per 5x5; padding zero per 1x1. Nessun nodo di reflection padding o resize. Il parser diagnostico segue i campi dello [schema ONNX](https://github.com/onnx/onnx/blob/main/onnx/onnx.proto); attributi e propagazione sono conservati in `onnx-graph.json`.

Campo ricettivo teorico massimo **239x239**, stride complessivo **16**, primo centro **0.5** in coordinate con centro del primo pixel a 0.5. Anche il supporto teorico centrale supera 224. Il centro nominale del tile visualizzato e invece 8+16*c: questa convenzione di rendering non promette di rappresentare il centro o il supporto esatto della convoluzione. Non e uno swap o un off-by-one nell'indexing.

Controlli forward sintetici 224x224, stesso preprocessing e modello:

| Input | Norma media bordo / interno 10x10 | Distanza coseno media dal vettore medio interno: bordo / interno |
|---|---|---|
| Uniforme RGB128 | 45.764 / 41.906 | 0.18251 / 0.01193 |
| Uniforme + rumore debole | 66.701 / 60.366 | 0.25223 / 0.04165 |
| Gradiente | 67.240 / 49.637 | 0.50570 / 0.01753 |
| Texture background | 87.234 / 80.877 | 0.23827 / 0.04011 |

Rumore: valori interi -2..2, seed42. Gradiente: 64..192 lungo x+y. Background: tassellazione del ritaglio 40x40 superiore sinistro di good_000; possibili discontinuita fra tasselli limitano il controllo.

Sull'uniforme, delta L2 medio rispetto alla feature media interna: **26.346 bordo / 5.593 interno**. Con rumore debole, variazione rispetto all'uniforme: **54.243 / 46.248**.

**INFERENCE.** Il backbone produce una firma spaziale di bordo anche senza oggetto, prima di VSA e Z. Le differenze di direzione, oltre alle norme, impediscono di liquidare il risultato come semplice scala poi rimossa dalla normalizzazione L2. E un comportamento del backbone con il suo padding, non evidenza di padding implementato erroneamente. Non e misurato quanto di ciascun picco reale dipenda separatamente da padding, texture e filtri appresi.

Evidenze: `padding-controls.csv`, quattro `*.input.png`, `*.features.f32`, `*.feature-norms.png`.

## 8. Decomposizione RAW/Z

**VERIFIED.** Tutti i valori sotto sono gli score congelati. Rank RAW = posizione decrescente nella stessa immagine, 1 massimo.

| Good | Cella | RAW | mu | RAW-mu | sigma | Z | Rank RAW |
|---|---|---:|---:|---:|---:|---:|---:|
| 000 | (13,13) | -78.1231 | -84.8832 | 6.7601 | 2.0592 | 3.2829 | 112 |
| 002 | (13,13) | -79.4387 | -84.8832 | 5.4445 | 2.0592 | 2.6440 | 123 |
| 021 | (13,13) | -74.6798 | -84.8832 | 10.2034 | 2.0592 | 4.9551 | 104 |
| 020 | (13,2) | -62.3600 | -77.0180 | 14.6581 | 4.4179 | 3.3179 | 68 |
| 001 | (13,4) | -15.8464 | -73.3584 | 57.5120 | 27.9006 | 2.0613 | 11 |

**INFERENCE.** Due componenti: lo scostamento dalla distribuzione nominale e gia nel RAW, mentre la Z cambia il ranking fra posizioni e rende dominante una cella con RAW assoluto non elevato. Per (13,13) la scala locale piccola conta molto; per (13,4) sigma e grande e lo scostamento RAW e molto maggiore. Non e quindi un solo meccanismo di sigma piccola. Mu negativa non e di per se un problema: conta lo scostamento dalla propria distribuzione di riferimento.

`edge-decomposition.csv` contiene anche le altre celle ricorrenti e i controlli, per tutte le 22 good.

## 9. Impatto scientifico

Risultati congelati Metal Nut: Image AUROC **0.757576**, Pixel AUROC **0.692630**, AUPRO@0.30 **0.379794**. Il confronto RAW precedente era rispettivamente **0.704790 / 0.625562 / 0.345386**: la Z migliora globalmente queste metriche pur rendendo dominanti alcune celle periferiche nei good.

**VERIFIED, image-level.** 93 anomalie x 22 good = 2.046 coppie. La perdita di ranking totale e 496 coppie equivalenti, coerente con `1-496/2046`. I cinque good con argmax inferiore contribuiscono 57, **11.49%** della perdita; i sei con argmax su tutto il bordo 69, **13.91%**. Sono contributi dei good identificati, non guadagni recuperabili rimuovendo il bordo: il massimo successivo potrebbe mantenere gli errori.

**VERIFIED, pixel-level.** Conteggi sulle evaluation maps congelate, senza ricalcolo o modifica delle metriche:

| Insieme e regione | Pixel GT negativi | Pixel Z>3 | Frequenza |
|---|---:|---:|---:|
| 22 good, intera immagine | 10.780.000 | 47.852 | 0.4439% |
| 22 good, ultima fascia 50px | 770.000 | 5.961 | 0.7742% |
| 22 good, perimetro 50px | 2.860.000 | 7.714 | 0.2697% |
| 115 test, intera immagine | 49.747.459 | 842.321 | 1.6932% |
| 115 test, ultima fascia 50px | 4.011.065 | 29.996 | 0.7478% |
| 115 test, perimetro 50px | 14.867.537 | 114.452 | 0.7698% |
| 115 test, interno 500x500 | 23.222.775 | 612.903 | 2.6392% |

Qui GT negativo significa assenza di difetto, inclusa superficie sana dell'oggetto; non equivale allo sfondo geometrico della sezione occupancy. Le regioni si sovrappongono e non vanno sommate.

**INFERENCE.** Il bordo inferiore pesa sulla coda nominale e sul ranking image-level. Tuttavia rappresenta solo il **3.56%** dei pixel GT negativi con Z>3 nell'intero test. L'interno contiene errori piu estesi e picchi maggiori. Il fenomeno non appare dominante rispetto agli altri failure mode. Non e stimato un delta causale Pixel AUROC/AUPRO: richiederebbe un controfattuale che questo audit non applica.

## 10. Classificazione finale, limiti e artefatti

**CORRECT / MIXED.** L'indexing e coerente. Il backbone ha effetti spaziali di bordo dimostrabili con input uniforme; le celle inferiori sono fortemente sensibili al background locale nella prova empirica; la calibrazione modifica il peso relativo delle loro escursioni RAW. Il solo campo ricettivo teorico non spiega la dominanza osservata. Nessun bug concreto trovato nel percorso frozen.

Restano **OPEN** la separazione causale delle componenti backbone/texture/padding/calibrazione e la generalizzazione delle perturbazioni oltre good_000. Nessuna modifica al modello viene proposta da questo audit.

File nuovi: questo report; in AUDIT i sei helper `audit_stats.py`, `inspect_graph.py`, `analyze_probes.py`, `AuditMarkers.java`, `AuditProbe.java`, `AuditFrozen.java`, le rispettive classi diagnostiche e gli artefatti elencati nelle sezioni. Python usa la venv prerequisiti esistente; Java usa classpath e runtime esistenti, senza dipendenze aggiunte. Il modello ricaricato ricompila i filtri in RAM dagli archetipi salvati, senza apprendimento o scrittura nell'archivio.

Verifiche eseguite: marker round-trip/overlay con assertion; sessione diretta versus encoder; frozen RAW/Z e upsampling versus artefatti; explicit VSA versus Adjoint; statistiche e occupancy; perturbazioni forward; controlli sintetici; hash degli 818 file protetti. Non e stata eseguita la suite Maven completa, non necessaria per un audit privo di modifiche ai sorgenti.
