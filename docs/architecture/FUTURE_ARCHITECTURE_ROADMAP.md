# Anomalib4j v2: future architecture roadmap

Data: 2026-10-04. Stato: **DESIGN / FUTURE_WORK**, senza autorizzazione implicita a implementare o eseguire esperimenti.

La fase post-hoc e chiusa. Le raccomandazioni sotto conservano il loro ruolo di proposte storiche per una fase futura separata, non di prerequisiti alla release. Il percorso di lettura corrente parte dalla [panoramica tecnica](../TECHNICAL_OVERVIEW.md).

## Raccomandazione

Usare **B come strumento di diagnosi controllata** e **A come candidata di prodotto a bassa latenza**. Prima di costruire una gerarchia completa, separare tre domande: quanto perde il max, quanto manca alla rappresentazione e quanto perde il detector con un solo archetipo. Unire subito nuovi layer, pooling, fine-tuning e crop impedirebbe di rispondere.

Il primo esperimento futuro consigliato e una piccola ablazione dell'aggregazione sulle mappe esistenti, senza cambiare la localizzazione. Il primo esperimento architetturale e invece **PaDiM embeddings congelati -> scorer VSA/Adjoint**, confrontato con Mahalanobis sugli stessi descrittori. Il controllo scientifico piu forte sulla compressione della memoria e **PatchCore embeddings -> kNN versus VSA/Adjoint**, con gli accorgimenti della sezione 6.

La candidata v2 da portare eventualmente in Java e una sola MobileNetV4 con ramo **14x14 locale + 7x7 regionale**, scoring compilato separato e fusione tardiva. Un descrittore globale e opzionale: non e gia dimostrato che risolva `bent`.

### Stati e fonti

- **VERIFIED:** codice o artefatti ispezionati; per i numeri storici significa verifica documentale, non nuova esecuzione.
- **INFERENCE:** conclusione ragionata a partire dalle evidenze.
- **HYPOTHESIS:** beneficio da testare.
- **OPEN / FUTURE_WORK:** decisione o verifica mancante.
- **PROPOSED:** protocollo candidato, non nuovo contratto congelato.

Fonti del progetto: [PROJECT_STATE](../PROJECT_STATE.md), [benchmark finale](../benchmark/BENCHMARK_FINAL_RESULT.md), [posa Metal Nut](../diagnostics/METAL_NUT_POSITION_ROTATION_DIAGNOSTIC.md), [RAW/Z Metal Nut](../diagnostics/METAL_NUT_RAW_VS_Z_DIAGNOSTIC.md), [audit spaziale](../diagnostics/SPATIAL_EDGE_ARTIFACT_AUDIT.md), [diagnostica Bottle](../diagnostics/BOTTLE_SYMMETRIC_DIAGNOSTIC.md), [sensibilita all'esclusione del bordo](../diagnostics/BORDER_EXCLUSION_SENSITIVITY.md). Sono stati letti anche il contratto ONNX e i sorgenti locali dei competitor. Nessun modello, benchmark, configurazione, codice o artefatto frozen e stato modificato; viene creato soltanto questo documento.

**Nota di riconciliazione (2026-10-07).** Alcune domande qui marcate OPEN sono state **parzialmente testate** da un'ablazione post-hoc interna sulle stesse feature CNN congelate: M1/M2/M3 sono ora baseline interne misurate e la modellazione diretta della memoria locale CNN non e piu puramente ipotetica. Nessuna e stata adottata come v2 e il benchmark congelato resta invariato. Dettagli in [LOCAL_MEMORY_ABLATION_RESULT.md](../diagnostics/LOCAL_MEMORY_ABLATION_RESULT.md) e [POSITIONAL_GLOBAL_VARIANCE_DIAGNOSTIC.md](../diagnostics/POSITIONAL_GLOBAL_VARIANCE_DIAGNOSTIC.md). Le idee di tangent space e statistica direzionale restano FUTURE / OPTIONAL, non lavoro corrente.

## 1. Verified limitations

### Qualita del sistema attuale

Valori del modello held-out effettivamente usato nel run finale, non della precedente variante Bottle full-209:

| Categoria/spazio | Image AUROC | Pixel AUROC | AUPRO@0.30 |
|---|---:|---:|---:|
| Bottle RAW, fit167 | 0.985714 | 0.956927 | 0.865098 |
| Bottle Z, fit167/cal42 | 1.000000 | 0.961664 | 0.877447 |
| Metal Nut RAW, fit176 | 0.704790 | 0.625562 | 0.345386 |
| Metal Nut Z, fit176/cal44 | 0.757576 | 0.692630 | 0.379794 |

**VERIFIED:** la Z migliora l'aggregato di entrambe le categorie; su Metal Nut non recupera una separazione gia debole nel RAW. `bent` ha Image RAW 0.6455, Pixel RAW 0.8502 e AUPRO RAW 0.4777; dopo Z, 0.5545 / 0.7632 / 0.3604.

**INFERENCE:** esiste segnale locale per `bent`, ma Pixel AUROC 0.85 non equivale a localizzazione risolta: AUPRO 0.48 resta modesta. Il divario Image/Pixel motiva un test dell'aggregazione, non prova che manchi esclusivamente un global descriptor. Le due metriche ordinano popolazioni diverse, immagini e pixel.

**VERIFIED:** nessun ramo globale esplicito, nessun fine-tuning industriale, un archetipo per posizione e image score max. **OPEN:** non e ancora separato il contributo di backbone, singolo archetipo, normalizzazione L2, standardizzazione VSA, posizione e pooling. Dire che il limite e pre-Z non identifica automaticamente il backbone come unica causa. L'ablazione post-hoc successiva ha pero mostrato che cambiare il solo modello di memoria/statistica, con **le stesse feature congelate**, puo cambiare materialmente la localizzazione; backbone, posa e struttura globale restano contributori possibili, ma non gli unici ([LOCAL_MEMORY_ABLATION_RESULT.md](../diagnostics/LOCAL_MEMORY_ABLATION_RESULT.md)).

### Posa, bordo e riflessi

- **VERIFIED:** l'audit spaziale non trova transpose/flip o errori di indexing; riscontri explicit/compiled nell'ordine di 1e-12. Padding e sensibilita allo sfondo esistono prima della Z.
- **VERIFIED:** esclusione di tutte le celle periferiche su tutte le immagini Metal Nut: Image Z 0.757576 -> 0.756109. Non e un rimedio materialmente utile per la detection aggregata.
- **INFERENCE:** la posa come spiegazione dominante non e sostenuta dai controlli disponibili. Non e dimostrata un'invarianza generale: stima geometrica approssimata, rotazione modulo 90 gradi, 22 good.
- **HYPOTHESIS:** illuminazione e specularita possono contribuire all'instabilita interna. Nessun intervento controllato ha ancora attribuito loro causalmente il degrado Metal Nut.

### Precisazioni sulle fonti

Non vengono propagati alcuni eccessi interpretativi dei report:

1. `PROJECT_STATE` contiene passaggi storici su una sola categoria, assenza di modello serializzato e benchmark da eseguire, superati dal suo aggiornamento finale e dagli artefatti del run completo.
2. Il report border-exclusion descrive 69/496 coppie come una precedente rimozione del bordo sui soli good. Nell'audit spaziale quel numero era invece la perdita associata ai sei good con argmax periferico, **senza rimozione**. Non e un guadagno causale o un limite superiore generale. Il controfattuale successivo resta informativo: perdita 496 -> 499.
3. Ridurre il supporto pixel cambia foreground, background e regioni valutate: i delta Pixel/AUPRO dello studio border-exclusion sono sensibilita post-hoc, non miglioramenti comparabili full-image del modello.
4. Bottle non conserva esattamente il ranking RAW dopo Z: il suo stesso report riporta spostamenti fino a 13 posizioni. Conserva la separazione good/anomaly. L'ablazione background varia le metriche pixel, seppure poco; non sono letteralmente invariate.
5. I 43 always-background Bottle e i 76 Metal Nut usano campionamento/supporti diversi: non costituiscono un confronto quantitativo perfettamente simmetrico. Non-significativita di correlazioni non prova assenza di effetto.

## 2. What the current benchmark actually proves

**VERIFIED:** il sistema compilato funziona, replica il percorso VSA e offre un compromesso qualita/latenza diverso dai due sistemi nativi. Non prova superiorita generale della VSA o della singola componente Adjoint rispetto a tutti i detector.

| Sistema | Bottle Image / Pixel / AUPRO | Metal Nut Image / Pixel / AUPRO |
|---|---|---|
| Anomalib4j Z | 1.000 / 0.962 / 0.877 | 0.758 / 0.693 / 0.380 |
| PaDiM | 0.998 / 0.978 / 0.922 | 0.952 / 0.941 / 0.851 |
| PatchCore | 1.000 / 0.985 / 0.944 | 0.998 / 0.987 / 0.940 |

Latenze p50 **del run finale**, millisecondi, stessa macchina:

| Sistema | Bottle detection/native | Bottle full-resolution | Metal Nut detection/native | Metal Nut full-resolution |
|---|---:|---:|---:|---:|
| Anomalib4j | 3.723 | 9.486 | 3.695 | 5.775 |
| PaDiM | 55.799 | 57.602 | 44.378 | 44.463 |
| PatchCore | 246.362 | 247.424 | 216.681 | 222.925 |

Il percorso native competitor include gia la mappa 256x256; la detection Java non include l'upsampling full-resolution. Per localizzazione, la colonna full-resolution e il confronto piu vicino. I 2.789/7.463 ms appartengono a una precedente run Bottle, non vanno mischiati con questi risultati. Circa 12 us per 196 filtri resta una misura interna storica del kernel, non del detector completo.

**Limiti:** backbone/preprocessing diversi, budget fit diversi, JMH versus timer Python, threading ORT effettivo non attestato come un solo worker. PatchCore usa ricerca brute-force PyTorch, non un indice ottimizzato. Le latenze non isolano il vantaggio causale della VSA. Due categorie e ripetuti usi diagnostici del test non sono una prova di generalizzazione.

Non esiste neppure un vantaggio uniforme gia dimostrato in ogni risorsa: fitting Anomalib4j circa 313/367 s, PaDiM 11/10 s, PatchCore 517/507 s. Working set steady Java circa 961-981 MiB, PaDiM 732-741 MiB. Piccoli filtri compilati non implicano automaticamente un processo Java piu piccolo. Fonte: [benchmark finale, sezioni 7-11](../benchmark/BENCHMARK_FINAL_RESULT.md).

## 3. Design principles for v2

1. **Una variabile per confronto:** rappresentazione, detector e aggregazione sono assi distinti.
2. **Compilazione come invariante verificabile:** per ogni nuovo descriptor u, preservare `A^T((R*u_n/sqrt(D)-mu)/sigma) = W^T*u_n+b`. Non chiamare questo passaggio ricostruzione inversa della CNN.
3. **Un solo forward condiviso quando possibile:** piu uscite dalla stessa gerarchia; niente tre encoder duplicati per tre scale.
4. **Costo totale misurato:** backbone profondo, trasferimenti output, fitting e memoria oltre ai dot product. Il grafo 14x14 attuale e troncato: ottenere 7x7 non e gratuito.
5. **Detection e localization distinte:** un global score puo migliorare Image AUROC senza fornire una mappa. Non spalmarlo uniformemente sulla heatmap per simulare localizzazione.
6. **Fusioni calibrate fuori dal test:** scale diverse non hanno score direttamente confrontabili. Anche massimizzare su piu rami aumenta le occasioni di attivazione nominale.
7. **Metriche scientifiche invariate:** Image AUROC, Pixel AUROC globale e AUPRO@0.30 secondo `LOCALIZATION_CONVENTIONS`; intera immagine, stesse maschere e gestione dei pareggi. Distanza Mahalanobis, similarita VSA, top-k e Z sono funzioni di scoring, non nuove metriche scientifiche.
8. **Identita riproducibile:** hash checkpoint/export, nomi e layout delle uscite, layer, normalizzazioni, subset e calibrazione. Una nuova rappresentazione richiede nuove statistiche/archetipi/filtri; il modello frozen resta un riferimento separato.

## 4. Native Evolution strategy

### 4.1 Locale, regionale e globale

**PROPOSED:** mantenere 14x14x96; osservare un'uscita piu profonda 7x7 con layout; confrontare successivamente un ramo pooled globale. E una **prima approssimazione**, non una soluzione dimostrata alla forma o alla specularita.

Il codice timm installato (`mobilenetv3.py`, `_gen_mobilenet_v4`) descrive per conv_small uno stage 7x7x128 seguito da espansione 1x1 a 7x7x960. `forward_features` termina a 960 canali; il GAP di questo tensore produce **960**, mentre la testa successiva puo produrre **1280 pre-logits**. Sono rappresentazioni diverse. Nessuna delle due e un'uscita dei due ONNX locali oggi congelati. La corrispondenza dei nuovi pesi con il prefisso ONNX esistente e **OPEN**, da verificare prima di un esperimento che pretenda di cambiare solo la profondita.

**GAP conserva abbastanza forma?** Non e garantito. Per `g = mean_p(F_p)`, qualsiasi permutazione dei vettori F_p lascia g invariato: il pooling perde il layout esplicito. Le feature profonde possono gia codificare forma e contesto e quindi g puo distinguere alcune deformazioni; una testa classificativa ImageNet non e pero stata addestrata a preservare proprio `bent`. Nessuna trasformazione 960 -> 1280 dopo GAP recupera il layout perso.

**7x7 puo essere piu utile?** Si, come ipotesi: mantiene 49 posizioni e consente coerenza fra regioni. Non significa 49 finestre indipendenti: i campi ricettivi si sovrappongono. Anche il 14x14 attuale ha supporto teorico massimo 239x239 su input 224. Il problema non e semplicemente assenza fisica di contesto, ma come quel contesto viene rappresentato e usato dallo score.

Per distinguere profondita da downsampling, il futuro controllo corretto e: 14x14 attuale, suo pooling deterministico a 7x7, vero stage profondo 7x7, GAP del medesimo stage. Non confrontare tre rami insieme senza queste ablazioni.

### 4.2 Fusione

Preferire inizialmente **scorer indipendenti e fusione tardiva**. Ogni scala ha proprie statistiche VSA, archetipi, filtri e calibrazione held-out. Il ramo locale produce la mappa; il regionale fornisce una seconda mappa grossolana e/o uno score immagine. Valutare prima i rami separati, poi una regola di fusione predefinita.

Concatenare 96 canali locali e 960 profondi interpolati a ogni cella produce C=1056 e ripete il vettore coarse: e compatibile con Adjoint, ma aumenta fitting/output/memoria e rende la scala con piu canali potenzialmente dominante nella L2 comune. Non e la prima opzione. Una normalizzazione per ramo sarebbe una nuova scelta da dichiarare e controllare.

Un gate globale che sopprime anomalie locali puo cancellare piccoli scratch; una fusione max puo aumentare i picchi nominali. Nessuna delle due e automaticamente robusta.

### 4.3 Aggregazione oltre max

| Metodo | Ipotesi affrontata | Limite | Costo aggiunto |
|---|---|---|---|
| Top-k mean | Un difetto esteso genera piu celle coerenti di un picco isolato | Diluisce difetti piccoli; k dipende dalla griglia | Basso, selezione su P valori |
| Percentile alto | Ridurre dipendenza da un solo estremo | Puo ignorare interamente una piccola anomalia | Basso |
| Pooling robusto regionale | Premiare supporto locale coerente | Una regione speculare puo essere coerente anch'essa; introduce scala spaziale | Basso/medio |
| Local + regional/global score | Distinguere consistenza di forma da singolo picco | Rami non confrontabili senza calibrazione; global puo perdere difetti locali | Basso nel detector, backbone aggiuntivo da misurare |

Il pooling immagine da solo lascia **identiche** Pixel AUROC e AUPRO se la mappa non cambia. Un eventuale miglioramento Image sarebbe utile ma non risolverebbe la bassa localizzazione Metal Nut.

### 4.4 Relazioni VSA

Una proposta lineare, ad esempio `h = sum_p B_p R u_p`, con binding posizionale **fisso** B_p, conserva l'ordine nel codice e permette di compilare un readout in una somma di pesi per patch. Non introduce automaticamente interazioni fra patch: una somma lineare di evidenze non e un verificatore generale di simmetria.

Per simmetria e relazioni fra regioni si possono considerare coppie speculari, differenze fra regioni o binding fra due vettori dipendenti dall'immagine. Una differenza lineare mantiene una compilazione possibile; il prodotto/binding di due osservazioni introduce termini bilineari. Un singolo filtro affine W non li rappresenta in generale. Norme, matching o normalizzazione dell'intero bundle aggiungono ulteriore non linearita.

**HYPOTHESIS / LONG TERM:** relazioni di forma utili per `bent`; alto valore research se comparate con semplici differenze CNN fra le stesse regioni. Una codifica posizionale VSA da sola non dimostra un vantaggio rispetto a pesi CNN posizionali.

### 4.5 Crop e piramidi

Quattro quadranti dell'immagine originale, ciascuno ridimensionato a 224x224, aumentano soprattutto la risoluzione dei dettagli. Ogni forward vede meno oggetto; non aggiungono contesto globale. Quattro crop nativi 224x224 senza resize potrebbero neppure coprire l'immagine originale 700/900: il contratto dei crop va esplicitato.

Per mantenere contesto occorre anche la vista intera: quattro crop + full image significano cinque forward, non uno. Overlap riduce discontinuita ma duplica calcolo, introduce stitching e necessita memoria posizionale coerente in coordinate dell'immagine. Una piramide comporta un forward per scala. Sono candidate per piccoli difetti di superficie, meno motivate dal solo `bent`; **DEPRIORITIZED** prima di provare una gerarchia a singolo forward.

## 5. Anomalib-Derived Hybrid strategy

Riutilizzare l'estrattore di descrittori, non presumere di ereditare l'intera qualita del metodo. I competitor combinano layer, pooling, distribuzione/memoria e generazione della mappa. I risultati nativi non dicono quale componente sia decisiva.

Sorgente locale verificato: `target/prerun/anomalib-2.6.2`, HEAD pulito `cc5f400a4a4b1b14b5a3ee5078063c250a8f522e`, release 2.6.2. Configurazioni in [configurations.json](../benchmark/benchmark-prerun/configurations.json) e [prerun verification](../benchmark/BENCHMARK_PRERUN_VERIFICATION.md). Le sezioni seguenti descrivono questa implementazione esatta, non tutte le varianti dei paper.

Architettura concettuale:

```text
preprocessing e CNN congelati del controllo
  -> feature di piu layer, allineamento, descriptor
  -> [detector nativo] oppure [VSA memory -> compiled scorer]
  -> patch anomaly scores
  -> generazione mappa dichiarata + image aggregation dichiarata
  -> stesso evaluator scientifico
```

Per Java, Conv/pooling/concat/resize possono stare nel grafo ONNX e restituire HWC. Export, interpolazione e preprocessing richiedono equivalenza numerica con il tensore Python: un export ONNX non trasferisce da solo l'antialias del resize esterno torchvision. Il vantaggio iniziale e poter studiare il detector sulle feature salvate **prima** di spendere tempo nel porting Java.

## 6. PatchCore insertion-point analysis

### Percorso verificato

Input 256 -> wide_resnet50_2 -> layer2 **512x32x32**, layer3 **1024x16x16** -> AvgPool2d(3,1,1) su ciascun layer -> interpolazione **bilinear** layer3 a 32x32 -> concatenazione **1536x32x32** -> flatten **1024x1536**.

Fit: pooling di tutte le patch nominali in una memoria **non posizionale**, selezione coreset 10% con KCenterGreedy. La SparseRandomProjection del selettore aiuta a scegliere il coreset: non e la nostra proiezione VSA e il banco conservato rimane nello spazio dei descriptor.

Inferenza: distanza Euclidea **1-NN per patch** -> 32x32 distanze -> resize **nearest** a 256 -> Gaussian blur sigma 4 per la mappa. Image score: massimo 1-NN pesato usando fino a 9 vicini del suo nearest neighbour nella memoria, `weight = 1-softmax(distances)[0]`. Non media di 9 distanze per ogni patch, non massimo della mappa smoothed. Questa precisazione e piu accurata della sintesi della tabella del report finale.

Fonti verificate: [torch_model.py al commit usato](https://github.com/open-edge-platform/anomalib/blob/cc5f400a4a4b1b14b5a3ee5078063c250a8f522e/src/anomalib/models/image/patchcore/torch_model.py#L170), [generazione mappa](https://github.com/open-edge-platform/anomalib/blob/cc5f400a4a4b1b14b5a3ee5078063c250a8f522e/src/anomalib/models/image/patchcore/anomaly_map.py#L55). Il riferimento concettuale e [Roth et al., PatchCore](https://arxiv.org/abs/2106.08265).

### Punto di inserimento

**Dopo `generate_embedding` / `reshape_embedding`, prima di `nearest_neighbors`.** Conservare esattamente i 1536 numeri per patch. Nel ramo sperimentale sostituire coreset/memoria/search con apprendimento VSA su quei descriptor e scoring compilato. La proiezione avviene nel fitting; in inferenza bastano normalizzazione del descriptor e filtri.

Il ramo ibrido non puo riutilizzare la pesatura nativa PatchCore senza conservare la memoria e la ricerca che vuole sostituire. Deve dichiarare il proprio image score. Per un'ablazione pulita aggiungere una vista controllata con **max non pesato per entrambi**, distinta dal riferimento nativo. Anche la generazione della mappa va mantenuta uguale nel confronto controllato; eventuale rimozione del blur e un'altra ablazione.

### Cosa si perde potenzialmente

PatchCore conserva molte modalita nominali e consente match fra posizioni. Un singolo archetipo posizionale comprime distribuzione, multimodalita e tolleranza spaziale. Migliori feature non assicurano che questa compressione sia sufficiente. Non e possibile stimare ora una percentuale credibile di qualita persa.

Con K archetipi, ciascun score resta compilabile, ma scegliere il migliore costa O(K*P*C); non si riduce in generale a un unico W. Una memoria condivisa versus posizionale e una scelta indipendente da controllare, non un dettaglio di porting.

### Quanto e pulito il confronto?

**Buono per misurare il compromesso detector sulle stesse feature; insufficiente da solo per attribuire un beneficio alla VSA.** Servono:

1. Stessi checkpoint, input preprocessati e tensor descriptor, verificati numericamente.
2. Stesso subset fit per i due detector: proposta 167/176, con 42/44 riservati alla calibrazione; i risultati nativi full-209/220 restano riferimenti separati.
3. Prima RAW con stesso readout/max e map generator; poi Z come fattore separato. Non confondere detector, calibrazione e pooling in un solo delta.
4. Il kNN nativo non L2-normalizza come il nostro scorer. Mantenere il nativo come riferimento e aggiungere un controllo kNN sugli stessi descriptor L2-normalizzati. La normalizzazione puo rimuovere informazione di ampiezza utile.
5. Per attribuzione specifica VSA, aggiungere un prototipo diretto CNN con uguale budget, normalizzazione e posizionalita. Altrimenti si testa compressione del detector, non necessita della proiezione casuale. Questa e una **futura** ablazione di ricerca, non implementata qui.
6. Misurare detector-only su feature gia pronte e poi sistema completo. Una WRN50 rimane costosa anche eliminando il kNN.

Il successo sarebbe conservare una parte sostanziale della qualita delle feature PatchCore con memoria/scoring molto minori. Un fallimento indicherebbe che il readout/memoria compressa non sfrutta quelle feature; non dimostrerebbe che ogni modello VSA e inadeguato.

## 7. PaDiM insertion-point analysis

Input 256 -> resnet18 layer1 **64x64x64**, layer2 **128x32x32**, layer3 **256x16x16** -> resize **nearest** dei layer inferiori a 64x64 -> concat 448 -> selezione fissa salvata di **100 canali** -> **100x64x64**.

Fit: media e covarianza per ciascuna delle 4096 posizioni, regolarizzazione 0.01I e ulteriore 1e-5I prima dell'inversione. Inference: `sqrt((u-m)^T Sigma^-1 (u-m))` -> bilinear 256 -> blur sigma 4. Image score = massimo di questa mappa smoothed.

Fonti: [PadimModel](https://github.com/open-edge-platform/anomalib/blob/cc5f400a4a4b1b14b5a3ee5078063c250a8f522e/src/anomalib/models/image/padim/torch_model.py#L160), [anomaly map](https://github.com/open-edge-platform/anomalib/blob/cc5f400a4a4b1b14b5a3ee5078063c250a8f522e/src/anomalib/models/image/padim/anomaly_map.py#L70), [Gaussian fit](https://github.com/open-edge-platform/anomalib/blob/cc5f400a4a4b1b14b5a3ee5078063c250a8f522e/src/anomalib/models/components/stats/multi_variate_gaussian.py#L129), [Defard et al., PaDiM](https://arxiv.org/abs/2011.08785).

**Punto di inserimento:** output di `generate_embedding`, **dopo la stessa selezione `idx`** e prima di Gaussian fit/Mahalanobis. Usare 100 canali nel primo controllo; usare 448 sarebbe contemporaneamente cambiare il descriptor. Per l'ibrido si apprendono archetipi per posizione sui 100 canali e si compila lo scorer; il map generator puo essere mantenuto identico per isolare il detector.

**Vantaggio rispetto all'ibrido PatchCore:** entrambi sono posizionali, dimensionalita piu bassa, backbone resnet18 meno oneroso; confronto piu semplice. **Rischio:** si elimina la covarianza fra canali, sostituendo una distanza quadratica con un readout affine sulla direzione normalizzata. L'Adjoint corrente non e una compilazione esatta della Mahalanobis; ne e un detector alternativo. Conservare la covarianza piena richiederebbe una diversa classe di scorer e non va promesso come O(C).

Il collo di bottiglia offline resta rilevante: 4096 posizioni invece di 196. Per il primo esperimento si conservano risoluzione e subset canali, senza ridurli silenziosamente per comodita. Eventuali compromessi successivi vanno chiamati nuove configurazioni.

## 8. Costi e confronto diretto

### Ordini di grandezza, non benchmark

P posizioni, C canali, D=10000. Filtri double circa `8*P*(C+1)` byte; archetipi double `8*P*D`; proiezione densa di un'immagine circa P*C*D accumuli firmati **per passata**. Statistiche e archetipi richiedono passate distinte nella pipeline corrente. Questi conteggi non sono FLOP o latenze misurate.

| Descriptor | P x C | Filtri runtime | Soli archetipi offline | Accumuli di proiezione / passata / immagine |
|---|---:|---:|---:|---:|
| Attuale | 196x96 | 0.145 MiB | 14.95 MiB | 188 milioni |
| Native14 + 7x7x960 + GAP960, rami separati | 196x96 + 49x960 + 1x960 | 0.512 MiB | 18.77 MiB | 668 milioni |
| PatchCore descriptor | 1024x1536 | 12.01 MiB | 78.13 MiB | 15.73 miliardi |
| PaDiM selected descriptor | 4096x100 | 3.16 MiB | 312.50 MiB | 4.10 miliardi |

Esclusi R, accumulatori, CNN, buffer intermedi, Z e overhead runtime. Per il ramo 7x7x128 i costi sono inferiori al caso 960 mostrato; la scelta del layer richiede un contratto nuovo. Il numero di coefficienti del caso nativo 960 e circa 3.55 volte l'attuale, senza considerare il costo dei blocchi CNN aggiunti.

Per confronto, il payload detector congelato Metal Nut e circa 132 MiB per PatchCore e 158 MiB per PaDiM, da `payload.json`; non e la memoria totale del processo. Per l'ibrido il fitting VSA puo diventare il limite pratico anche se il runtime e piccolo. Non promettere fitting piu veloce di PaDiM.

### Valutazione delle proposte

Benefici di qualita sotto = **HYPOTHESIS**; costi qualitativi rispetto all'attuale sistema.

| Proposta | Failure mode / beneficio atteso | CPU inference | Memoria / fitting | Rischio scientifico |
|---|---|---|---|---|
| Pooling robusto | Picchi isolati, possibile vantaggio su difetti estesi; nessun recupero di informazione mancante | Quasi invariata | Trascurabile / nessun nuovo fit per readout fisso | Basso come diagnosi, medio per regressione su piccoli difetti |
| A:14+7, global opzionale | Semantica regionale e layout; possibile migliore separazione bent | Crescita da blocchi profondi; singolo forward limita duplicazione | Bassa runtime; fit maggiore | Medio: ImageNet profondo puo perdere sensibilita fine |
| B-PaDiM descriptor+Adjoint | Verifica se multiscala rende sufficiente lo scorer | Backbone piu grande; detector O(PC) | Runtime moderata; fit D*P*C elevato | Medio/alto: perdita covarianza |
| B-PatchCore descriptor+Adjoint | Verifica compressione della memoria su feature che funzionano bene | Risparmio search plausibile; WRN resta | Molto meno banco runtime; fit elevato | Alto: multimodalita e non-posizionalita perse |
| Piccolo K archetipi | Normali multimodali collassate dal singolo bundle | K volte costo scorer | K volte memoria; assegnazione/fit aggiuntivi | Medio/alto; scelta K e modello nominale |
| Relazioni VSA | Configurazione, simmetria, deformazioni | Basso se lineari; non garantito con prodotti/matching | Dipende dal numero di relazioni | Alto: novelty non equivale a utilita |
| Crop/piramidi | Dettagli persi nel resize | Multipli forward | Buffer e fit per viste | Medio: non affronta necessariamente bent |
| Adaptation fotometrica | Instabilita nominale per luce/riflessi | Potenzialmente invariata a modello congelato | Training piu costoso; nuovi modelli | Alto: cancellazione di segnali anomali |

| Proposta | Java/ONNX e Adjoint | Training necessario | Complessita/rischio ingegneristico | Valore portfolio/research |
|---|---|---|---|---|
| Pooling | Java semplice; filtri invariati | Nessun fine-tuning | Basso | Diagnosi rigorosa, originalita limitata |
| A:14+7(+global) | Nuovo ONNX multi-output; ogni ramo compilabile | Refit VSA/Z, CNN frozen | Medio: provenienza pesi/export | Buon sistema Java spiegabile con budget misurato |
| B-PaDiM | Export resnet18+descriptor; affine sostitutivo compilabile | Refit, nessun backprop | Medio | Ablazione controllata leggibile |
| B-PatchCore | Export WRN+pooling+resize; scorer compilabile | Refit, nessun backprop | Medio/alto: ampiezza feature/fit | Forte risultato se quantifica perdita qualita contro risparmio |
| K archetipi | K filtri compilabili, selezione esterna | Fit/assegnazione, non necessariamente backprop | Medio | Rilevante solo con controllo diretto CNN |
| Relazioni | Solo relazioni lineari fisse entrano nel filtro corrente | Nuova memoria; possibile training | Alto | Potenzialmente originale, risultato incerto |
| Crop/piramidi | Encoder riusabile, geometria/stitching nuovi | Refit per protocollo viste | Medio/alto | Valore applicativo, debole attribuzione VSA |
| Adaptation | Freeze+export dopo training; poi rifare tutta la memoria | Fine-tuning/SSL | Alto: drift/export/augmentation | Utile se validato su acquisizioni indipendenti |

**Risposte dirette:** miglior rischio/beneficio come primo ibrido = PaDiM 100; miglior controllo di compressione su feature forti = PatchCore 1536; migliore probabilita di conservare latenza attuale = evoluzione nativa a singolo forward, dopo il test economico di pooling. Nessuna delle tre affermazioni e una previsione numerica di AUROC.

## 9. Backbone / domain-adaptation strategy

Ordine proposto:

1. **MobileNetV4 multi-layer frozen:** massima continuita; verificare prefisso 14 invariato prima di attribuire risultati alla gerarchia.
2. **ResNet18 frozen multiscala:** controllo gia funzionante nel benchmark, non necessariamente scelta finale piu veloce.
3. **Un solo backbone lightweight alternativo**, ad esempio MobileNetV3, solo se i due controlli precedenti indicano un limite di rappresentazione. Non una ricerca indiscriminata di backbone. La progettazione mobile rende la famiglia candidata, non garantisce superiorita in anomaly detection o ORT CPU ([MobileNetV3](https://arxiv.org/abs/1905.02244), [MobileNetV4](https://arxiv.org/abs/2404.10518)).

Usare feature di piu layer e fine-tuning sono esperimenti diversi. La prima opzione non richiede backprop; un backbone allenato su ImageNet puo comunque conservare feature inadatte al dominio.

**Normal-only adaptation:** consistenza fra trasformazioni fotometriche controllate, teacher frozen o vincoli di preservazione del descriptor sono candidati. La sola minimizzazione della dispersione nominale puo collassare le feature e rendere invisibili anomalie non viste. Valutare diversita/rank delle feature e sensibilita ai difetti, non soltanto varianza dei good.

**Self-supervised:** utile se esistono dati nominali diversificati e trasformazioni semanticamente giustificate; positive pairs non devono equiparare automaticamente graffi, decolorazioni o deformazioni a normali. Le trasformazioni geometriche che eliminano `bent` come segnale sono particolarmente rischiose.

**Supervised:** richiede anomalie etichettate o un dataset industriale distinto; cambia il regime informativo del confronto. Mai addestrare su difetti test MVTec e presentare il risultato come normal-only. Dopo qualsiasi adaptation: freeze encoder, nuova identita, statistiche/archetipi/Adjoint/Z ricostruiti soltanto sui subset consentiti.

## 10. Photometric / specular robustness

**Fisica plausibile, non diagnosi causale del dataset.** Un metallo e otticamente opaco ma puo riflettere specularmente; opacita e assenza di highlight non sono equivalenti. L'intensita riflessa dipende da normali locali, direzione di osservazione, illuminazione e rugosita. Piccole variazioni di normale possono spostare un highlight e modificare fortemente il pattern locale. Il vetro combina riflessione e trasmissione/rifrazione: anch'esso puo essere difficile. Non esiste una regola per cui Bottle debba essere intrinsecamente piu facile. Fonti fisiche: [conductor BRDF](https://pbr-book.org/4ed/Reflection_Models/Conductor_BRDF), [dielectric BSDF](https://pbr-book.org/4ed/Reflection_Models/Dielectric_BSDF), [microfacet theory](https://pbr-book.org/4ed/Reflection_Models/Roughness_Using_Microfacet_Theory).

**INFERENCE sul caso:** silhouette, setup di acquisizione, tipo/estensione dei difetti e segnali gia separabili possono favorire Bottle. **HYPOTHESIS:** Metal Nut puo essere piu sensibile all'accoppiamento geometria-illuminazione; la sola correlazione fra luminosita e score non lo dimostra. Un `bent` altera proprio le normali: rendere invarianti a tutti i riflessi puo cancellare il difetto da rilevare.

| Intervento futuro | Cosa verifica | Rischio da controllare |
|---|---|---|
| Exposure, gamma, contrast entro intervalli documentati | Stabilita a variazioni fotometriche globali | Clipping, color difetti attenuati, gamma non equivalente a nuova luce |
| Variazioni lente di illuminazione | Dipendenza da gradienti/shading nominali | Possono imitare una deformazione |
| Highlight locali | Sensibilita a picchi luminosi | Possono coprire scratch o creare pseudo-difetti |
| Riflessioni sintetiche | Possibile robustezza a specularita | Blob luminosi 2D non sono un simulatore fisico; domain gap |
| Preprocessing anti-riflesso | Riduzione di variabilita prima delle feature | Perdita irreversibile di anomalie superficiali e colore |

Priorita: prima stress test su normali con identita/geometria preservate, poi verifica che i difetti mantengano separazione; idealmente acquisizioni ripetute dello stesso pezzo sotto luce controllata. Le perturbazioni sintetiche non sostituiscono queste coppie. Anti-riflesso, inpainting o normalizzazione aggressiva per immagine sono **DEPRIORITIZED**. La polarizzazione in acquisizione sarebbe un esperimento hardware separato, non preprocessing software equivalente.

## 11. Ranked roadmap

Stime di impegno **qualitative**, per uno sviluppatore che conosce il repository, escluse attese compute e raccolta dati; non preventivi. Ogni fase richiede una nuova autorizzazione. Le priorita massimizzano informazione prima di complessita.

| Ordine | Classe richiesta | Proposta | Impegno plausibile | Informazione e criterio di arresto |
|---:|---|---|---|---|
| 1 | **A. HIGH VALUE / LOW RISK** | Max versus un top-k/percentile predefinito sulle mappe attuali | 1-2 giorni | Se non migliora separazione bent senza regressione evidente sui piccoli difetti, non moltiplicare k sullo stesso test |
| 2 | **A. HIGH VALUE / LOW RISK** come controllo | PaDiM 100: Mahalanobis versus scorer compilato, descriptor identici | 3-5 giorni + fitting | Se il gap resta ampio, non investire subito nel porting Java del modello ibrido |
| 3 | **B. HIGH VALUE / RESEARCH RISK** | PatchCore 1536: kNN versus compilato, controlli di normalizzazione/memoria | 4-8 giorni + fitting | Se il singolo archetipo perde molta qualita, registrare il limite; non aggiungere fusioni per nasconderlo |
| 4 | **B. HIGH VALUE / RESEARCH RISK** | Native14+7; GAP solo dopo confronto dei rami | 4-8 giorni se pesi/export recuperabili | Fermare il ramo se non aggiunge qualita o supera budget CPU senza beneficio giustificato |
| 5 | **B. HIGH VALUE / RESEARCH RISK** | Pochi archetipi e stress fotometrico, due esperimenti separati | 3-7 giorni ciascuno | K utile solo se supera il controllo CNN; augmentation utile solo se preserva sensibilita ai difetti |
| 6 | **C. LONG TERM** | Normal-only adaptation/SSL; relazioni VSA di forma | Settimane | Stop con collapse, necessaria supervisione test o algebra/costo non piu sostenibili |
| 7 | **D. DEPRIORITIZED** | Multi-crop/piramidi, anti-riflesso, alignment esteso, ricerca molti backbone | Costo crescente | Riprendere solo con evidenza specifica di risoluzione, luce o posa mancante |

La seconda riga e low risk per il valore informativo e il riuso dei sorgenti, non per una probabilita gia nota di successo qualitativo. La Native Evolution rimane il candidato piu vicino a un prodotto veloce; viene dopo i controlli per evitare di costruire una soluzione al problema sbagliato.

## 12. Recommended first experiment e regole decisionali

### E0: aggregazione, prima di nuovo modello

**PROPOSED:** su mappe congelate RAW e Z, confronto appaiato fra max e media delle 10 celle piu alte (circa 5% delle 196). Un percentile 95 puo essere riportato come terzo readout diagnostico, con convenzione quantile dichiarata prima del calcolo. Nessuna selezione del migliore per categoria; nessuna nuova mappa, calibrazione o smoothing.

Riportare Image AUROC aggregata e per difetto su Bottle/Metal Nut, coppie good/anomaly riordinate e intervalli bootstrap per immagine; per il per-difetto usare sempre lo stesso insieme good. Pixel/AUPRO rimangono i riferimenti invariati. Questo verifica l'ipotesi di picchi isolati contro anomalie estese, non la sufficienza delle feature per un detector ideale.

Il test e gia stato osservato e ha motivato l'ipotesi: l'esito e **esplorativo post-hoc**, anche se k viene ora fissato. Una conferma richiede dati non usati per progettare la scelta o una successiva valutazione esterna congelata. Non fare una ricerca di k sul test e chiamarla validazione.

### E1/E2: separare feature e detector

E1 usa esattamente i 100 canali e gli indici PaDiM salvati, stesso fit 167/176 per entrambi; E2 usa i 1536 descriptor PatchCore. Protocolli e controlli come sezioni 6-7. Confronto primario controllato RAW; la positional Z e una seconda dimensione, con held-out 42/44. I riferimenti nativi finali restano in una tabella distinta.

Per il confronto VSA-specifico, un prototipo diretto CNN e indispensabile: un readout compilato e gia affine nelle feature normalizzate. Un eventuale vantaggio operativo di VSA puo risiedere in apprendimento, composizione o aggiornamenti della memoria, ma non e provato dalla sola possibilita di scrivere lo stesso filtro W nello spazio CNN. L'ablazione post-hoc ha ora misurato baseline CNN locali dirette (M1/M2) e dispersione dopo proiezione VSA (M3) sulle stesse feature congelate, riducendo la parte puramente ipotetica di questo confronto ([LOCAL_MEMORY_ABLATION_RESULT.md](../diagnostics/LOCAL_MEMORY_ABLATION_RESULT.md)).

### Criteri concreti, da congelare prima di future run

- **Gate tecnico:** equivalenza descriptor Python/ONNX e explicit/compiled con tolleranze dichiarate; split senza leakage; nessun risultato se il contratto feature cambia fra fit e infer.
- **Gate informativo:** se E0 non aiuta, fermare il filone pooling sul test corrente; se il detector compilato resta debole sulle feature forti, indagare memoria/readout prima del fine-tuning.
- **Gate di utilita PROPOSED:** considerare promettente una modifica solo con guadagno Metal Nut almeno 0.02 assoluto su Image AUROC o AUPRO, senza perdita >0.01 sulle altre metriche aggregate o su Bottle. Sono margini ingegneristici proposti, non significativita statistica; riportare intervalli e tutti i difetti, anche in caso di gate superato. Risultati entro il rumore restano inconclusivi.
- **Gate CPU PROPOSED per candidata nativa:** p50/p95 detection e localization non oltre 2x la baseline frozen nella stessa sessione di misura, stessa macchina/perimetro. Il fattore 2 e un budget di progetto da ratificare, non una previsione; il controllo WRN scientifico puo superarlo ma non viene promosso automaticamente a prodotto.
- **Gate di attribuzione:** se il prototipo CNN raggiunge gli stessi risultati/costi, non dichiarare beneficio specifico VSA; preservare il risultato utile di compilazione e documentarne il limite.

## 13. Proposed v2 architecture e cosa non prioritizzare

**Candidata condizionata ai risultati, non specifica implementativa approvata:**

```text
immagine -> preprocessing con identita -> UNA CNN frozen multi-output
                                         | 14x14 locale
                                         | 7x7 regionale
                                         ` GAP opzionale
apprendimento: descriptor -> statistiche VSA -> memoria -> Adjoint
runtime:      descriptor -> L2 -> filtri compilati -> RAW -> Z held-out
                                         | mappa locale/regionale dichiarata
                                         ` aggregazione immagine predefinita
```

Localizzazione: locale come riferimento, regionale valutato separatamente e poi eventualmente fuso con geometria dichiarata. Globale: solo ramo image-level finche non esiste un meccanismo di localizzazione validato. Runtime senza proiezione VSA, senza dipendenza da un banco crescente con N; K archetipi ammessi solo con budget esplicito. Il ramo multiscala derivato da PaDiM/PatchCore rimane uno strumento di controllo e un'alternativa se la qualita giustifica il costo.

**Non prioritizzare:**

- implementare subito 14+7+global+crop+fine-tuning;
- mascherare il bordo come soluzione a Metal Nut;
- ripetere la sola ottimizzazione dei 12 us di Adjoint;
- assumere che un vettore 1280 contenga piu informazione spaziale di 7x7;
- sostituire kNN/Mahalanobis e continuare a chiamare il risultato PatchCore/PaDiM equivalente;
- rimuovere normali variabili dal training o correggere riflessi usando mappe test;
- cercare molte fusioni, k, layer o sigma sui test gia analizzati;
- promettere la latenza attuale usando WRN50, quattro crop o un grafo piu profondo senza misurarlo;
- introdurre relazioni non lineari mantenendo la promessa di un unico filtro affine esatto;
- cambiare supporto pixel, regioni o AUPRO per rendere migliore la v2.

## 14. README-ready Future Work

> Future work will separate representation quality, memory capacity and image-level aggregation through controlled ablations. We plan to compare the compiled VSA scorer with PaDiM and PatchCore detectors on identical frozen descriptors, then evaluate a single-pass local/regional MobileNet hierarchy. Optional global features and photometric adaptation will be retained only if they improve anomaly sensitivity without erasing surface defects or losing the CPU latency advantage. Current results validate explicit/compiled equivalence and a useful quality–latency trade-off on Bottle, while Metal Nut exposes substantial limitations. They do not yet establish a VSA-specific advantage over direct CNN-space memory. All future comparisons will preserve the Image AUROC, Pixel AUROC and AUPRO@0.30 evaluation conventions and keep exploratory results separate from the frozen benchmark.

## 15. Feature-space selection and subject-specific compilation

Aggiunta di design del 2026-10-04. **PROPOSED / FUTURE_WORK**: nessuna implementazione, nuova misura, modifica del modello o degli artefatti frozen. Questa sezione formalizza il problema della selezione delle feature e completa le sezioni 3-7 e 12; non converte la roadmap in un task esecutivo.

### 15.1 Mathematical clarification of CNN/VSA compiled equivalence

#### Equivalenza dello score, non biiezione degli spazi

Sia x in R^C la feature CNN di una patch e sia `v = phi(x)` la rappresentazione effettivamente fornita alla parte affine. Nel modello corrente:

```text
phi(x) = x / max(||x||_2, epsilon)
L      = diag(1/sigma_VSA) R / sqrt(D)
d      = -diag(1/sigma_VSA) mu_VSA
h      = L phi(x) + d
s_p    = A_p^T h = (L^T A_p)^T phi(x) + A_p^T d
W_p    = L^T A_p
b_p    = A_p^T d
```

**DERIVAZIONE ESATTA:** `s_p = W_p^T phi(x) + b_p` per ogni input nel dominio definito, se L, d e A_p sono fissi durante l'inferenza. In floating point l'ordine delle operazioni puo introdurre piccoli scarti; l'equivalenza algebrica non promette identita bit-per-bit. I controlli del progetto verificano numericamente questa identita sui casi documentati.

L'argomento vale anche per matrici rettangolari, non ortogonali o di rango ridotto. Non richiede Johnson-Lindenstrauss, quasi-isometria, inversione di R o ricostruzione di x. Una mappa lineare da R^96 a R^10000 non e una biiezione fra i due spazi: se R ha rango pieno puo essere iniettiva **sulla propria immagine**, un sottospazio di dimensione 96, ma non suriettiva su R^10000. Inoltre la normalizzazione L2 identifica direzioni con ampiezze diverse fuori dalla regione epsilon. L'Adjoint trasferisce un funzionale di scoring; non ricostruisce l'osservazione originaria.

**Condizioni sufficienti:** composizione di trasformazioni affini fissate dopo il fitting; readout lineare/affine; medesima geometria, ordine canali e policy di normalizzazione fra training e inferenza; statistiche finite con denominatori non nulli; archetipi congelati per quella versione del modello. Apprendimento non lineare offline e consentito: una volta ottenuto un archetipo costante, il suo readout puo ancora essere compilato.

#### Cosa entra nel filtro

R, fattore 1/sqrt(D), media e std VSA congelate, archetipo normalizzato offline e bias entrano in W_p e b_p. Lo stesso vale per centering/whitening lineare con statistiche fisse, selezione fissa di canali, permutazioni, binding con un vettore costante quando l'operatore e lineare nel dato, e combinazioni lineari a pesi fissi di score. Il floor applicato offline alla std diventa una costante del modello.

Anche la positional Z corrente puo essere assorbita **algebricamente**. Distinguendo le sue statistiche m_p, t_p da quelle VSA:

```text
raw_p = 1 - s_p
Z_p   = (raw_p - m_p) / t_p
      = (-W_p / t_p)^T phi(x) + (1 - b_p - m_p) / t_p
```

Questo non propone una modifica al codice: la separazione RAW/Z corrente resta utile per audit e diagnosi. Pesi compilati in Z richiederebbero comunque conservare l'identita e le statistiche necessarie a ricostruire RAW. Aggiornare statistiche o archetipi richiede una nuova compilazione coerente; non vale riutilizzare W di una versione precedente.

La normalizzazione della CNN resta **fuori** dalla fusione affine: il kernel attuale calcola esattamente `dot(W,x)/max(norm(x),epsilon)+b`. Lo score e affine in phi(x), non in x. BatchNorm in modalita evaluation con statistiche fisse e affine; BatchNorm con statistiche del batch corrente, LayerNorm/InstanceNorm dipendenti dall'input e normalizzazioni per immagine non sono costanti assorbibili in un solo W.

#### Adapter lineare: ordine delle operazioni determinante

Se `u = Mx + c` e il detector successivo e `a^T u + beta`, allora:

```text
s(x) = (M^T a)^T x + a^T c + beta
```

M puo essere diagonale, sparse, low-rank o densa: per un singolo score affine non occorre materializzare u in inferenza. Vale anche con `u = M phi(x) + c`, conservando phi nel runtime. Per piu readout si compila ciascuna colonna; l'aumento del numero di score resta un costo.

Se invece la pipeline e `x -> Mx -> L2 -> detector`, come accadrebbe fornendo ingenuamente l'output dell'adapter all'attuale normalizzazione CNN:

```text
s(x) = (M^T a)^T x / max(||Mx||_2, epsilon_u) + beta
||Mx||_2^2 = x^T M^T M x
```

Il numeratore si compila, il denominatore in generale no. Sostituirlo con ||x|| cambia il modello. Non proponiamo di introdurre una forma quadratica densa nella hot path.

| M prima di L2 | Runtime residuo per la norma esatta | Implicazione |
|---|---|---|
| Selezione di k canali | Somma dei quadrati dei k canali | O(k); non norma di tutti i canali originari |
| Gating diagonale g | `sqrt(sum_j g_j^2 x_j^2)` | O(C), possibile loop condiviso; non completamente eliminato |
| M sparse | Calcolare Mx o equivalente esatto | Dipende dai nonzero; un Gram puo diventare denso |
| M low-rank = U V^T | Calcolare V^T x e norma trasformata | Costo legato al rango, non zero |
| M densa piccola | Calcolare Mx e norma | O(dim(u)*C), da includere nella latenza |
| M con M^T M = alpha I | `sqrt(alpha)*||x||` | Caso speciale; epsilon va trasformato coerentemente, non assunto per una R casuale |

Un bias c prima della L2 rende la norma `||Mx+c||` ancora dipendente dall'input. Anche spostare la L2 prima dell'adapter, per renderlo piu facilmente compilabile, e una **nuova architettura**, non una trasformazione equivalente della precedente. La specifica del futuro feature compiler deve dichiarare l'ordine esatto, comprese epsilon e coordinate della norma.

#### Dove finisce la compilazione in un solo filtro

| Operazione | Cosa resta esatto | Cosa non si riduce a un unico affine |
|---|---|---|
| K archetipi | Ognuno dei K score si compila | Max/min/argmax tra loro resta una selezione non lineare |
| Binding con ruolo fisso | Se lineare nel dato, entra nella matrice | Il nome binding da solo non garantisce linearita |
| Prodotto/binding di due feature variabili | Eventuale readout in uno spazio espanso | Termini bilineari non diventano un filtro lineare sulle feature originali |
| kNN, matching, attention dipendente dall'input | Eventuali sottoparti lineari | Ricerca, assegnazione e pesi dipendenti dal dato |
| Norme di vettori VSA/query, clipping, soglie, binarizzazione | Parti prima/dopo possono essere compilate separatamente | Il risultato complessivo non e generalmente affine |
| Softmax su logits | I logits possono essere compilati | Softmax resta nel runtime |
| Pooling medio o pesi fissi | Lineare se applicato alle stesse variabili con stessa geometria | Max, top-k, percentile o pesi adattivi restano esterni |

Dire che la fusione in **un solo filtro** non vale non significa perdere l'equivalenza di ogni sottoparte compilabile. Per esempio, K filtri piu lo stesso max riproducono esattamente i K confronti espliciti piu max. Il costo e O(K*C) per patch, non quello di un singolo archetipo. Una normalizzazione VSA dipendente dall'input puo essere mantenuta per conservare equivalenza, ma non scompare per sola trasposizione.

### 15.2 Why feature quality becomes the central problem

**INFERENCE:** eliminato il costo della proiezione in inferenza, diventa particolarmente importante capire quali informazioni arrivano al readout. Aumentare da 96 a 10000 coordinate tramite una trasformazione lineare non crea informazione nuova sull'immagine. Se due osservazioni producono lo stesso descriptor, nessuno scorer deterministico di quel descriptor puo distinguerle. Se risultano distinte ma non separabili dal readout scelto, il problema puo essere la capacita del detector, non assenza di informazione.

Bottle RAW separa bene; Metal Nut RAW separa male nell'attuale modello. **VERIFIED** come comportamento del sistema; **OPEN** come attribuzione esclusiva allo spazio CNN. Il RAW comprende normalizzazione, statistiche, archetipo e scoring. Un censimento deve quindi evitare la conclusione circolare: "le feature sono buone perche lo scorer che preferiamo funziona". L'ablazione post-hoc ha mostrato che, con le stesse feature, cambiare il solo modello di memoria/statistica cambia la localizzazione: l'attribuzione esclusiva del limite Metal Nut allo spazio CNN non e quindi giustificata.

Il problema progettuale e congiunto: **informazione presente, accessibilita al readout, stabilita ai fattori irrilevanti e costo per estrarla**. Uno spazio utile per classificare identita puo essere inadatto a rilevare microdifetti. Una feature sensibile all'illuminazione puo essere inutile per riconoscere la stessa persona, ma essenziale per distinguere un graffio o una superficie piegata. Nuisance significa variabile irrilevante **per il compito dichiarato**, non variazione visiva in generale.

### 15.3 Subject-specific feature compiler: concetto e confini

**PROPOSED:** una procedura offline che seleziona una rappresentazione e produce un contratto verificabile, non una nuova rete universale o un sistema che sceglie feature guardando il test.

```text
compito + dominio + dati di sviluppo consentiti + budget CPU/memoria
  -> inventario limitato di backbone/layer congelati
  -> censimento di informazione, stabilita e costi
  -> singolo layer / subset / pesi / adapter / pochi rami
  -> congelamento della rappresentazione e delle normalizzazioni
  -> apprendimento della memoria o del detector
  -> compilazione delle sole sottoparti affini
  -> calibrazione held-out + verifica numerica + valutazione separata
```

Output concettuale: checkpoint e hash; punti di uscita/layout; indici selezionati; M e bias eventuali; statistiche usate per selezione e normalizzazione; ordine delle operazioni; dipendenze non lineari residue; geometria di ciascun ramo; dati/split ammessi; readout e aggregazione; costo misurato quando autorizzato. Selezione e compilation sono operazioni distinte: una configurazione puo risultare utile ma costosa o non riducibile a un solo affine.

Il modello del soggetto/dominio e appreso soltanto su dati autorizzati. Non e una selezione adattiva dei canali per ciascuna immagine test: quella introdurrebbe un gate dipendente dall'input e richiederebbe un'altra analisi matematica.

### 15.4 Feature selection and linear adaptation strategies

#### Scegliere il livello di rappresentazione

| Sorgente | Informazione candidata | Rischio | Criterio utile |
|---|---|---|---|
| Shallow/intermediate | Colore, bordi, texture fine | Rumore/fotometria, alta risoluzione e memoria | Sensibilita a difetti locali versus variazioni nominali |
| 14x14 attuale | Dettaglio intermedio con disposizione spaziale | Un solo stage/readout puo essere insufficiente | Riferimento fisso per tutti i confronti |
| 7x7 profondo | Pattern regionali, contesto e layout coarse | Perdita di dettaglio, maggiore profondita | Capacita regionale e informazione aggiuntiva rispetto al 14x14 |
| Pooled/global | Identita o configurazione complessiva gia codificata | Layout esplicito perso, piccoli difetti diluiti | Probe image-level e controllo su difetti estesi |
| Altro backbone | Rappresentazione complementare | Cambio simultaneo di costo e preprocessing | Guadagno a budget comparabile, non solo maggiore AUROC |

La dimensione non misura da sola la qualita. Anche confrontare 7x7 e 14x14 cambia profondita, risoluzione e canali: il pooling del 14x14 a 7x7 resta un controllo utile. Per i layer non ancora esportati serve prima verificare la continuita dei pesi; il censimento non puo presumere la disponibilita di un nuovo ONNX equivalente.

#### Criteri semplici e interpretabili

Per una feature j e trasformazioni T che preservano il target, definire sui dati di sviluppo:

```text
V_j = Var(x_j)                         # variabilita osservata
N_j = E[(x_j(T(I)) - x_j(I))^2]        # sensibilita alla nuisance scelta
F_j = sum_k pi_k (mean_kj-mean_j)^2 /
      (sum_k pi_k var_kj + lambda)     # rapporto Fisher con classi reali
```

Per mappe locali il confronto sotto T geometrica richiede prima corrispondenza spaziale; altrimenti N_j misura anche il trasferimento del contenuto a un'altra cella. Quantificare separatamente variazione di norma e direzione, dato che la L2 corrente elimina parte dell'informazione di ampiezza. Lambda, scala dei canali e convenzione delle varianze vanno fissati sul solo sviluppo.

| Metodo | Uso sensato | Cosa non permette di concludere |
|---|---|---|
| Varianza per canale | Individuare saturazione, canali costanti e scala numerica | Alta varianza puo essere nuisance; bassa varianza sui good puo rendere il canale prezioso per anomalie |
| Fisher / between-within | Ranking interpretabile con classi o difetti di sviluppo etichettati | Non esiste separazione good/anomaly stimabile da una sola classe senza assunzioni |
| Stabilita sotto augmentation | Scartare fragilita evidenti rispetto a T giustificate | Stabilita perfetta puo derivare da collapse o insensibilita al difetto |
| Correlazione/pruning | Ridurre duplicati su dati di sviluppo, controllando perdita di probe | Correlazione nei good non implica ridondanza sulle anomalie future |
| Sparse channel selection | Budget esplicito, subset riproducibili, interpretabilita | Selezione aggressiva puo eliminare feature rare ma utili |
| Gating diagonale | Pesare canali con pochi parametri | Con L2 successiva cambia anche la norma; non e sempre costo runtime zero |
| Adapter sparse/low-rank | Combinare canali con capacita limitata e regolarizzazione | Il rango ridotto puo perdere direzioni critiche; serve evidenza aggiuntiva rispetto al subset |
| Piccola M densa | Ruotare/ripesare un sottospazio compatto | Molti parametri rispetto a pochi esempi, overfitting e norma residua |

Non usare una graduatoria unica del tipo "massima varianza e minima sensibilita". Selezionare una piccola frontiera fra utilita, robustezza, dimensione e costo, tenendo sempre il descriptor originale e un subset casuale di uguale dimensione come controlli. La selezione per canale dipende dalla base delle feature; una direzione discriminativa puo essere distribuita su molti canali e non comparire fra i migliori presi singolarmente.

Con etichette sufficienti, un probe lineare con regolarizzazione o pesi sparse puo guidare la selezione. Con pochi esempi, preferire un numero limitato di candidati, stime regolarizzate e stabilita del subset fra fold. Un adapter che migliora solo il training o cambia completamente a ogni split non e una rappresentazione affidabile.

**Limite di capacita:** un adapter lineare seguito da un readout affine libero non amplia la classe dei classificatori lineari nello spazio originale. Puo imporre una regolarizzazione, favorire apprendimento con pochi dati o cambiare il modello di memoria; non crea una frontiera non lineare per sola fusione. I benefici eventuali vanno attribuiti a questi effetti, non al numero di coordinate intermedie.

#### Multi-layer: scegliere, concatenare o separare

- **Singolo layer:** riferimento piu semplice e facile attribuzione; interrompere la ricerca se soddisfa il budget/compito.
- **Subset da piu layer:** utile se aggiunge informazione verificabile oltre al migliore layer singolo; allineamento e ordine dei canali devono essere congelati.
- **Rami indipendenti:** preservano scale e normalizzazioni proprie. Una combinazione fissa dei readout e compilabile per ramo; restano le singole norme e l'eventuale aggregazione non lineare.
- **Descriptor multiscala concatenato:** approccio simile a PaDiM/PatchCore, con interpolazioni/pooling specifici gia descritti nelle sezioni 6-7. Una L2 comune lega i contributi dei layer; normalizzarli separatamente e un altro modello.

GAP, average pooling e interpolazione a geometria fissa sono operatori lineari sui tensori gia estratti. Possono essere assorbiti in un readout lineare compatibile, ma non eliminano l'estrazione CNN o una successiva norma non lineare. Eliminare canali dall'output non rende automaticamente piu veloci le convoluzioni che li producono. Una M assorbita puo anche trasformare un piccolo readout in un vettore di pesi denso nello spazio originale: confrontare costo del filtro fuso e dell'adapter fattorizzato, specialmente con molti score.

### 15.5 Normal-only, supervised e few-shot labelled

| Regime | Dati consentiti per scegliere feature | Criteri applicabili | Evidenza non disponibile |
|---|---|---|---|
| Supervised | Classi/difetti etichettati di training e validation separati | Fisher, probe lineare, separazione between/within, errori per classe | Generalizzazione a difetti o acquisizioni non osservati |
| Few-shot labelled | Pochi esempi per classe/condizione; fold separati per oggetto/acquisizione | Probe regolarizzato, diagonal gating, pochi subset, varianza fra split | Stime stabili di grandi covarianze o adapter densi senza forte regolarizzazione |
| Normal-only anomaly | Solo normali per selezione, fitting e calibrazione | Stabilita, copertura di modi nominali, ridondanza, costo, probe di identita/condizione solo se etichettate | Vera separazione good/anomaly e sensibilita a difetti sconosciuti |

**Principio:** nel normal-only la colonna good/anomaly del censimento resta `N/A during selection`. Non si allena un linear probe good-versus-anomaly senza anomalie consentite. Un compito ausiliario o una perturbazione sintetica puo essere una proxy dichiarata, non una misura di separazione rispetto ai difetti reali. Anomalie sintetiche non devono diventare surrettiziamente la definizione di tutto cio che il modello sa rilevare.

Suddivisione futura proposta: dividere il subset destinato al fit in fold di sviluppo per la selezione; tutte le augmentation e immagini dello stesso pezzo/sessione rimangono nel medesimo fold. Congelare layer, subset e M prima del refit sull'intero subset fit. Il subset di calibrazione 42/44 resta escluso dalla selezione e serve soltanto alla Z finale. Se occorrono altri budget, definirli in un nuovo protocollo, non alterare retroattivamente lo split frozen.

Anomalie con etichette pixel di **sviluppo esterno** possono supportare probe locali supervised; questo va etichettato come regime supervisionato distinto. I difetti del test Bottle/Metal Nut non sono dati di selezione. Il loro uso ripetuto per scegliere layer o pesi produrrebbe adattamento al benchmark anche senza gradienti.

### 15.6 Proposed feature census

**Scopo:** localizzare informazione utile prima di impegnarsi in VSA, Mahalanobis o kNN. Non esiste tuttavia una misura totalmente indipendente dal readout: un probe lineare valuta l'accessibilita lineare, non tutta l'informazione possibile. Un esito negativo non dimostra che nessun detector non lineare possa usare quello spazio.

Per ogni sorgente e trasformazione candidata, una scheda futura dovrebbe contenere:

| Campo | Misura proposta | Vincolo/interpretazione |
|---|---|---|
| Identita | Hash, layer, shape, layout, preprocessing, normalizzazione | Riproducibilita prima del ranking |
| Separazione good/anomaly | Image AUROC, Pixel AUROC, AUPRO con readout dichiarato | Solo su development etichettato ammesso o valutazione finale dopo freeze; N/A nella selezione normal-only |
| Stabilita good | Dispersione entro stessa identita/condizione e fra sessioni, code dei residui held-out | Non confondere ampia diversita nominale con rumore da eliminare |
| Fotometria | Drift L2 e angolare, cambiamenti score per exposure/gamma/luce/highlight | Intervalli e clipping documentati; stress test, non prova fisica di specularita |
| Geometria | Errore di equivarianza dopo riallineamento per mappe; stabilita di identita per global | Il layout deve seguire l'oggetto; non richiedere identita pixel a pixel dopo uno spostamento |
| Locale/globale | Probe e risultati per difetti piccoli, estesi e di forma | Nel normal-only reale la capacita sui difetti resta OPEN fino alla valutazione consentita |
| Linear probe | Un modello semplice regolarizzato con budget di selezione fisso | Supervised/few-shot; N/A per good-versus-anomaly normal-only |
| Ridondanza | Correlazioni fra canali, rango effettivo, delta del probe aggiungendo un layer | Non basta bassa correlazione per provare complementarita utile |
| Dimensione/memoria | P, C, output bytes, parametri, memoria offline e runtime | Tenere separati descriptor, modello e processo |
| CPU | Preprocessing+encoder, costruzione descriptor, readout e end-to-end | Stesso hardware/perimetro; no stime ricavate solo da C |
| Incertezza | Variazione fra fold/semi, intervalli per immagine/pezzo | Patch della stessa immagine non sono repliche indipendenti |

Il modello di memoria non va fatto variare durante il primo ranking delle sorgenti. Dopo una shortlist ristretta, si possono confrontare readout diversi sulle **stesse** feature, a budget dati e normalizzazioni comparabili. Classificazione di identita, detection di anomalia e localizzazione richiedono righe/obiettivi distinti, non un unico indicatore sintetico.

**Esperimento futuro a massimo valore informativo:** un censimento appaiato di pochi spazi frozen, con lo stesso readout semplice e lo stesso protocollo, seguito da un confronto di readout sulle sole sorgenti selezionate senza test leakage. In concreto: 14x14 attuale come controllo e i due descriptor multiscala PaDiM/PatchCore gia attestati; eventuali 7x7/GAP entrano solo dopo la verifica della provenienza/export. Prima si misura stabilita/costo sui normali; quando esistono etichette di sviluppo ammesse si aggiunge il probe lineare. In assenza di tali etichette la selezione rimane normal-only e la separazione sui difetti si misura soltanto dopo il freeze, come valutazione finale o esplorativa dichiarata.

Questo esperimento risponde alla domanda piu importante: **cambiando solo la sorgente percettiva, quanto recupera lo stesso readout, e quanto resta recuperabile cambiando il detector a feature fisse?** Non attribuire a M o alla VSA un cambiamento dovuto al preprocessing o al budget di dati. L'identita explicit/compiled resta un controllo numerico, non un'ulteriore ipotesi di qualita da ottimizzare. Il test economico di pooling della sezione 12 rimane un possibile controllo preliminare; il censimento e la priorita scientifica qui proposta, non un nuovo task autorizzato.

### 15.7 What remains specifically valuable about VSA

Distinguere quattro contributi:

| Contributo | Cosa comprende | Controllo necessario |
|---|---|---|
| Rappresentazione | Backbone, layer, subset, pesi, adapter e geometria | Stesso detector/readout su rappresentazioni diverse |
| Modello di memoria | Media, distribuzione, K archetipi, banca di esempi, politica di aggiornamento | Stesse feature e stesso budget dati |
| Inferenza compilata | Eliminazione di trasformazioni affini esplicite e materiali intermedi | Stesso modello prima/dopo compilazione, stessa uscita |
| Specificamente VSA | Vantaggio di una codifica/operazione VSA oltre alternative dirette | Controllo CNN comparabile per capacita, operazioni, costo e regime di apprendimento |

**Conclusione matematica:** uno scorer CNN che usa esattamente i W,b compilati deve avere la stessa prestazione, salvo errori numerici. Non e una scoperta sperimentale che le due versioni abbiano la stessa AUROC; e il comportamento previsto. Un prototipo CNN appreso direttamente, invece, puo avere W diversi: il suo confronto misura una differenza di apprendimento/memoria, non la validita della compilazione.

| Proprieta candidata VSA | Interesse concreto | Perche non basta a rivendicare un vantaggio esclusivo |
|---|---|---|
| Memory construction/bundling | Accumuli componibili, memoria con dimensione controllata | Anche medie e statistiche CNN si aggiornano senza backprop |
| Incremental/few-shot updates | Aggiornare archetipi e ricompilare senza riaddestrare encoder | Servono curve esempi/qualita, costo di update e controllo diretto CNN |
| Multiple archetypes | Conservare modalita nominali differenti | Non esclusivo VSA; il costo di selezione resta e va confrontato con prototipi ordinari |
| Compositionality e binding | Codificare ruoli, attributi, associazioni in uno spazio comune | Binding fisso lineare puo ridursi a pesi CNN; prodotti relazionali reali cambiano la classe di scorer |
| Multimodal representations | Associare visione, simboli o altri sensori con operazioni comuni | Richiede allineamento e compiti verificabili; non discende dall'alta dimensionalita |
| Symbolic/relational operations | Interrogare, combinare e aggiornare memorie strutturate | Occorre dimostrare correttezza e utilita contro rappresentazioni relazionali semplici |

Per aggiornamenti incrementali, distinguere **statistiche VSA congelate** da statistiche adattate online. Nel primo caso l'accumulatore dell'archetipo puo essere aggiornato nello stesso spazio e il filtro ricompilato; cambiare mu/sigma/M cambia lo spazio e richiede sufficiente stato storico o ricostruzione coerente. Anche la calibrazione puo diventare obsoleta. La disponibilita di somme aggiornabili non dimostra da sola assenza di forgetting o stabilita degli score.

**HYPOTHESIS / OPEN:** il valore piu interessante della VSA potrebbe essere nel ciclo di vita e nelle operazioni della memoria, non nell'aggiunta di coordinate al readout affine. Non sono ancora verificati in questo progetto vantaggi few-shot, incremental, multimodali o relazionali. La compilazione resta un risultato ingegneristico reale, ma e una proprieta generale delle composizioni affini, non esclusiva della VSA.

### 15.8 PlantVillage observation and implications for Anomalib4j v2

L'uso fuori dominio del MobileNetV2 fine-tuned PlantVillage per oggetti, persone e luoghi e **un'osservazione riferita dall'utente**. I documenti canonici non contengono qui un protocollo verificato con numerosita, split, metriche e controlli sufficiente a trasformarla in risultato generale.

**Spiegazioni plausibili:** layer intermedi conservano detector di bordi, texture e forme riutilizzabili; il fine-tuning puo ripesare parti della rappresentazione senza cancellare tutto il pretraining; la diversita delle feature puo lasciare accessibile informazione non usata dalla testa PlantVillage. Non si assume che il fine-tuning sia una semplice matrice di reweighting: aggiorna una rete non lineare e puo anche deteriorare il trasferimento.

La trasferibilita fra compiti e la sua dipendenza dal layer/dominio sono documentate da [Yosinski et al., 2014](https://proceedings.neurips.cc/paper_files/paper/2014/hash/532a2f85b6977104bc93f8580abbb330-Abstract.html). La possibile distorsione di feature preaddestrate durante fine-tuning e discussa da [Kumar et al., 2022](https://arxiv.org/abs/2202.10054). Questi lavori rendono plausibile la domanda, ma non verificano l'encoder PlantVillage specifico o le prestazioni riportate.

**Controllo futuro**, se quel caso venisse riaperto: checkpoint generico versus fine-tuned, stessi layer/preprocessing/readout/dati fuori dominio, valutazione separata e costi uguali. Successo su pochi soggetti acquisiti in condizioni simili puo dipendere anche da sfondo, pose o facilita dei casi. Non inventiamo qui risultati, robustezza o universalita.

Per Anomalib4j v2, la conseguenza e progettare **un contratto di feature selezionato per il compito**, non fissare a priori che ogni soggetto debba usare 14+7+global. Un singolo layer puo bastare; una gerarchia si giustifica con complementarita misurata. Ogni candidato deve dichiarare quali passaggi sono compilabili, quali norme/selezioni restano a runtime e quanto costa estrarre le feature, oltre al costo del filtro.

Condizioni di arresto: nessuna promozione di un subset che migliora solo sui dati usati per sceglierlo; nessuna conclusione sulla detection da sola stabilita nominale; nessuna attribution VSA quando un controllo diretto equivalente spiega il risultato; nessuna espansione automatica a molti backbone se un censimento ristretto non separa ancora le ipotesi. Restano invariati i criteri di comparabilita delle metriche della sezione 3 e il confine tra esplorazione e conferma.

### 15.9 Concise README-ready paragraph

> A future subject-specific feature compiler will select layers, channel subsets or constrained adapters using task-appropriate development data, robustness checks and a CPU budget. Fixed affine transformations can be absorbed into CNN-space scoring weights; input-dependent normalization, matching and nonlinear relations remain explicit. The current Adjoint identity is score equivalence, not a bijection or evidence that expansion to 10,000 dimensions creates discriminative information. We will separate representation quality, memory design and compilation gains, and investigate VSA-specific value through controlled memory updates and compositional tasks. Normal-only feature selection will not use test anomalies; anomaly separation will be assessed only after the representation is frozen.
