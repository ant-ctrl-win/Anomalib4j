# Anomalib4j

**Italiano** | [English](README.en.md)

> Pipeline Java per il rilevamento di anomalie nella visione industriale, con inferenza ONNX,
> modellazione statistica locale, valutazione riproducibile e benchmark delle prestazioni CPU.
> L'obiettivo è mostrare come una pipeline moderna di visual anomaly detection possa essere integrata in un ambiente Java con inferenza ONNX, stato statistico persistente, localizzazione e benchmark riproducibili.

1.27 MB encoder · ~304 KB/category model · 2.90 ms CPU detection

- **Inferenza e valutazione native Java**, con Java 21 e ONNX Runtime.
- **MobileNetV4 Conv Small congelato**, con **feature spaziali 14x14x96**.
- **Adattamento alla categoria usando solo campioni normali**, senza fine-tuning della CNN.
- **Valutazione su tutte le 15 categorie MVTec AD**, con input e metriche registrati
  e misure CPU su hardware reale.

**Risultati selezionati per categoria MVTec AD**

| Categoria | Image AUROC |
|---|---:|
| Bottle | 1.000 |
| Leather | 0.998 |
| Tile | 0.981 |
| Grid | 0.962 |
| Zipper | 0.960 |

**CPU p50:** detection completa **2.90 ms**; localizzazione Bottle 900x900
**7.42 ms**. [Perimetro di misura](#prestazioni-cpu).

## Il progetto

Molti workflow di computer vision sono centrati su Python. Anomalib4j integra
modelli di visione preaddestrati come componenti software in una pipeline Java
testata: preprocessing, estrazione di feature spaziali, modellazione statistica,
persistenza del modello, scoring delle anomalie e valutazione quantitativa.

Java gestisce inferenza e calcolo delle metriche; il tooling Python orchestra
le campagne, i controlli di parità numerica e la raccolta delle evidenze.

## Pipeline

```text
Immagine
  |
Preprocessing RGB / normalizzazione ImageNet
  |
MobileNetV4 Conv Small / ONNX Runtime
  |
Descrittori spaziali 14x14x96
  |
Modello statistico locale M2
  |
Score di anomalia + mappa delle anomalie
  |
Valutazione / output applicativo
```

M2 modella l'aspetto normale in ogni posizione spaziale:

1. Normalizza ciascun descrittore locale.
2. Stima media e deviazione standard campionaria per posizione e canale
   dalle immagini normali **FIT**.
3. Calibra le discrepanze locali usando immagini normali **CAL** separate e held-out.
4. Produce una mappa standardizzata 14x14; il suo massimo è lo score dell'immagine.

L'encoder rimane congelato. L'adattamento alla categoria stima statistiche anziché
ottimizzare i pesi CNN. Il preprocessing ridimensiona l'intera immagine RGB a
224x224 con interpolazione bicubica e applica la normalizzazione ImageNet.
Gli score Z sono discrepanze standardizzate, non probabilità.

[Definizione del modello e validazione numerica](docs/benchmark/JAVA_M2_RESULT_20261018.md#selection-and-implementation).

## Risultati MVTec AD

### Risultati selezionati per categoria

Esempi rappresentativi del detector generico validato, non valori aggregati:

| Categoria | Image AUROC | Pixel AUROC | AUPRO@0.30 |
|---|---:|---:|---:|
| Bottle | 1.000000 | 0.975697 | 0.915738 |
| Leather | 0.998302 | 0.991365 | 0.961311 |
| Tile | 0.980519 | 0.940407 | 0.849164 |
| Grid | 0.961571 | 0.945783 | 0.858965 |
| Zipper | 0.959821 | 0.960991 | 0.882107 |

### Aggregato sull'intero dataset

**Macro-media con uguale peso per categoria su tutte le 15 categorie MVTec AD**,
con il detector M2 generico e MobileNetV4 Conv Small:

| Metrica | Macro-media |
|---|---:|
| Image AUROC | **0.871803** |
| Pixel AUROC | **0.931016** |
| AUPRO@0.30 | **0.805887** |

La valutazione comprende 2,900 normali FIT, 729 normali CAL held-out e 1,725
immagini TEST. Ogni categoria ha lo stesso peso; le prestazioni variano fra categorie.
Image AUROC misura il ranking delle immagini, mentre Pixel AUROC e AUPRO
misurano la localizzazione. [Convenzioni di valutazione](docs/benchmark/LOCALIZATION_CONVENTIONS.md)
ed [evidenze dei risultati completi](docs/EXPERIMENT_INDEX.md#latest-completed-campaigns).

## Prestazioni CPU

Misure JMH dirette su **AMD Ryzen AI 9 HX 370 / Windows 11 Pro**, Java 21,
ONNX Runtime 1.30.0. È stata verificata l'esecuzione dei nodi ONNX su
`CPUExecutionProvider`.

| Perimetro misurato | p50 | Media | p95 |
|---|---:|---:|---:|
| Estrazione encoder: tensore preprocessato -> feature spaziali | **0.641024 ms** | 0.671423 ms | 0.819200 ms |
| Detection generica completa | **2.904064 ms** | 2.910571 ms | 3.637248 ms |
| Localizzazione completa Bottle 900x900 | **7.421952 ms** | 7.280697 ms | 8.812954 ms |

I percorsi completi partono da un'immagine già decodificata e da modello/sessione
pronti. La detection include preprocessing, encoder, normalizzazione dei
descrittori, M2 e score immagine; la localizzazione produce anche la mappa 900x900.
Sono esclusi decode, I/O su disco e caricamento di modello/sessione.
Ogni percorso completo è misurato direttamente.

JMH usa un chiamante, due fork, cinque warmup da 2 secondi e dieci iterazioni
di misura da 2 secondi per fork. Sono latenze per richiesta su desktop,
non throughput della camera.
[Misure dettagliate e riproducibilità](docs/EXPERIMENT_INDEX.md#latest-completed-campaigns).

### Sistemi di riferimento sullo stesso hardware

| Sistema | Localizzazione Bottle 900x900 p50 |
|---|---:|
| Anomalib4j / MobileNetV4 Small | **7.42 ms** |
| PaDiM | 57.60 ms |
| PatchCore | 247.42 ms |

Sono pipeline complete e indipendenti, misurate sullo **stesso hardware fisico**.
I riferimenti Anomalib 2.6.2 usano ResNet18 per PaDiM e WideResNet50-2 per
PatchCore, con le proprie configurazioni di pipeline. Non è un microbenchmark
dello scorer a parità di backbone.
Strumenti e date di misura differiscono: i valori sono riferimenti ingegneristici
pratici, non affermazioni causali sui singoli algoritmi.

Gli artefatti di deployment sono compatti: **1,269,313 byte** per l'encoder ONNX
troncato e **304,381 byte** (circa 304 KB) per ogni archivio M2 di categoria.
Encoder e stato di categoria sono salvati separatamente; sono dimensioni di file.

## Esempi visivi

Gli esempi seguenti mostrano il detector M2 generico su oggetti, texture e strutture periodiche. 
Tutti e cinque usano la pipeline senza registrazione.

<table>
<tr>
<td width="50%" align="center">
<strong>Bottle</strong><br>
<img src="docs/assets/readme/bottle_example.png" alt="Bottle, contenitore rotto: immagine TEST originale e overlay delle anomalie M2" width="100%"><br>
<sub>Image AUROC 1.000000 · broken_large</sub>
</td>
<td width="50%" align="center">
<strong>Leather</strong><br>
<img src="docs/assets/readme/leather_example.png" alt="Leather, taglio: immagine TEST originale e overlay delle anomalie M2" width="100%"><br>
<sub>Image AUROC 0.998302 · cut</sub>
</td>
</tr>
<tr>
<td width="50%" align="center">
<strong>Tile</strong><br>
<img src="docs/assets/readme/tile_example.png" alt="Tile, crepa: immagine TEST originale e overlay delle anomalie M2" width="100%"><br>
<sub>Image AUROC 0.980519 · crack</sub>
</td>
<td width="50%" align="center">
<strong>Grid</strong><br>
<img src="docs/assets/readme/grid_example.png" alt="Grid, struttura rotta: immagine TEST originale e overlay delle anomalie M2" width="100%"><br>
<sub>Image AUROC 0.961571 · broken</sub>
</td>
</tr>
<tr>
<td colspan="2" align="center">
<strong>Zipper</strong><br>
<img src="docs/assets/readme/zipper_example.png" alt="Zipper, denti rotti: immagine TEST originale e overlay delle anomalie M2" width="500"><br>
<sub>Image AUROC 0.959821 · broken_teeth</sub>
</td>
</tr>
</table>

Le metriche descrivono la categoria, non il singolo esempio. Ogni pannello affianca
l'immagine TEST originale e il suo overlay conservato. Tutti usano l'intervallo
di visualizzazione fisso **Z [0,12]**, senza normalizzazione min/max per immagine;
il clipping riguarda solo il display.
[Identità delle immagini, hash delle mappe e provenienza del rendering](docs/assets/readme/README.md).

## Registrazione geometrica

**La modellazione posizionale è più efficace per texture e oggetti geometricamente
coerenti o registrati.** Una feature normale spostata può altrimenti essere
confrontata con il riferimento spaziale sbagliato.

Per categorie con geometria consistente, Anomalib4j supporta una fase opzionale di 
registrazione geometrica prima dell'estrazione delle feature. 
La posa viene canonicalizzata utilizzando lo stesso contratto in fitting, 
calibrazione e inferenza. L'integrazione è stata validata su Metal Nut.
[Contratto di registrazione e integrazione Java](docs/experiments/JAVA_CANONICAL_M2_INTEGRATION.md).

## Aspetti ingegneristici

- **Inferenza Java 21:** sessioni ONNX Runtime riutilizzabili, preprocessing
  ImageNet, estrazione di feature spaziali e mappe locali delle anomalie.
- **Adattamento statistico:** fitting su soli normali e calibrazione held-out
  indipendente, con validazione esplicita di descrittori e varianze.
- **Persistenza validata:** i modelli binari versionati rifiutano hash encoder,
  identità di preprocessing e geometrie incompatibili, oltre a stati numerici non validi.
- **Infrastruttura dati e valutazione:** label guidate dai metadati, manifest
  FIT/CAL/TEST deterministici, geometria originale e valutazione all-15 MVTec AD.
- **Validazione numerica:** controlli di parità Java/Python per statistiche,
  calibrazione, score e mappe; test JUnit su contratti e persistenza.
- **Prestazioni e riproducibilità:** misure JMH, profiling dei provider eseguiti,
  orchestrazione CLI, hash di sorgenti/input e artefatti dei risultati conservati.

## Quick Start


```shell
git clone https://github.com/ant-ctrl-win/Anomalib4j.git
cd Anomalib4j
mvn -B compile
mvn -B -Pbenchmark '-Dtest=M2BenchmarkSmokeTest' test
```

## Full validation

```shell
mvn -B -Pbenchmark '-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest' test
```

Questi comandi clonano e compilano la pipeline, eseguono la suite senza dataset
esterno e uno smoke test della pipeline. Lo smoke test usa immagini e stato del
modello sintetici con esecuzione ONNX reale, detection e localizzazione 900x900.
Non misura la latenza del benchmark. I due test esclusi richiedono dati Bottle
esterni; il profilo `benchmark` compila l'harness senza avviare una campagna JMH.

Per immagini reali occorre prima generare o ripristinare il modello statistico della categoria.
La valutazione completa usa la [CLI di campagna](tools/ablation/java_m2.py),
un [export MVTec AD con layout DatasetNinja](docs/benchmark/MVTEC_DATASET_CENSUS.md)
esterno e manifest, hash dei dati e output di riferimento ripristinati.
L'[indice delle evidenze](docs/EXPERIMENT_INDEX.md) documenta questi prerequisiti
e i percorsi degli artefatti. Le evidenze generate sono in `target/`, ignorato da Git:
preservarle quando si riproducono le misure.

## Struttura del repository

```text
src/main/java/              Encoder Java, detector M2 e registrazione
src/main/resources/models/ Risorse dei modelli ONNX
src/test/java/              Test, gestione dataset e valutazione
src/jmh/java/               Benchmark JMH e verifica del provider CPU
tools/                      Utility per campagne, parità e riproducibilità
docs/                       Architettura, benchmark e provenienza delle figure
```

## Documentazione di approfondimento

- [Contratto del modello ONNX e del preprocessing](docs/architecture/ONNX_MODEL_CONTRACT.md)
- [Modello M2 e validazione numerica](docs/benchmark/JAVA_M2_RESULT_20261018.md#selection-and-implementation)
- [Convenzioni di localizzazione e metriche](docs/benchmark/LOCALIZATION_CONVENTIONS.md)
- [Layout e copertura del dataset](docs/benchmark/MVTEC_DATASET_CENSUS.md)
- [Indice di valutazione, benchmark e riproducibilità](docs/EXPERIMENT_INDEX.md)

## Licenza

Codice: [Apache License 2.0](LICENSE).

Le figure della galleria sono adattamenti ridimensionati e con overlay di immagini
MVTec AD, attribuiti a **MVTec Software GmbH** e distribuiti sotto
[CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/).
[Fonti, trasformazioni e attribuzione](docs/assets/readme/README.md).
Il dataset si ottiene separatamente.
