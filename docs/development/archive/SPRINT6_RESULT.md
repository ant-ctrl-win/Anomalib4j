# Sprint 6 - primo modello bottle da immagini reali

## Configurazione

- Dataset: `<DATASET_ROOT>/mvtec-ad-DatasetNinja`.
- Selezione: esclusivamente `train/img/bottle_good_*.png`, ordinamento per nome.
- Encoder frozen: `mobilenetv4_conv_small.e2400_r224_in1k`, variante `SPATIAL_14`.
- Preprocessing: `imagenet-rgb-resize224-bicubic-v1`.
- Feature map: HWC `14 x 14 x 96`, 196 patch.
- Proiezione: `DenseRademacherProjection`, D = 10.000, seed = 42L.
- Normalizzazione: L2 con denominatore `max(norm, 1e-6)`.
- Statistiche: Welford globale per dimensione, varianza campionaria N - 1,
  floor della deviazione standard = `1e-8`.

## Implementazione

`NormalImageTrainer` riceve encoder, descrittore, proiezione e floor. Elabora
una sola immagine alla volta, prima per le statistiche e poi per la memoria.
La lista dei percorsi viene copiata prima del primo passaggio e riutilizzata
nel secondo. Le statistiche congelate sono legate al descrittore.

Il risultato contiene statistiche, memoria posizionale e filtri compilati
dall'`AdjointCompiler` esistente, direttamente accettati da `HeatmapEngine`.
L'encoder appartiene al chiamante, che deve chiuderlo dopo l'uso.

`ExplicitVsaScorer` mantiene disponibile il percorso di riferimento con
proiezione, standardizzazione e prodotto scalare contro l'archetipo.
Usa buffer privati riutilizzati e richiede un'istanza per thread.

### Refactoring matematico

`NormalizedPatchProjection` raccoglie la trasformazione pre-Z-score usata da
trainer, builder e scorer esplicito. Conserva l'aritmetica precedente:
proiezione delle feature float grezze in double, divisione per la norma CNN
con epsilon, poi divisione per `sqrt(D)`. Non viene creata una feature
normalizzata in float, evitando un arrotondamento aggiuntivo.

Il test della trasformazione verifica uguaglianza esatta con la precedente
sequenza di operazioni su patch normali, nulle e sotto epsilon. Restano
inalterate le formule del bundling, del bias e dei pesi Adjoint.

`ModelDescriptor` include `preprocessingId` nell'uguaglianza. Il costruttore
precedente resta disponibile con identita `unspecified`; il trainer reale
richiede l'identita esatta dell'encoder. Artefatti con preprocessing diverso
non sono quindi intercambiabili.

## Verifiche

Esecuzione del 29 settembre 2026, comando `mvn test`: **BUILD SUCCESS**.

| Misura | Risultato osservato |
| --- | --- |
| Immagini normali distinte | 209 |
| Passaggi immagine completati | 418, due per immagine |
| Osservazioni Welford | 40.964 = 209 x 196 |
| Archetipi costruiti | 196, ciascuno con 209 osservazioni |
| Dimensioni di ciascun archetipo | 10.000 |
| Confronti explicit-VSA / compiled-Adjoint | 588 |
| Errore assoluto massimo osservato | `9.9475983006414030e-13` |
| Tolleranza richiesta nel test | `1e-9` |
| Test finali | 47, zero failure, zero errori, zero skipped |
| Durata del test reale | 302,6 secondi |
| Durata Maven complessiva | 5 minuti e 5 secondi |

Le statistiche e gli score verificati sono finiti. Tutte le 196 celle hanno
superato il controllo della norma unitaria. Il modello compilato e stato
accettato dal costruttore di `HeatmapEngine`.

La suite precedente contava 40 test, tutti superati prima delle modifiche.
I sette test aggiunti portano il totale a 47. Il controllo del refactoring
ha verificato anche che il builder conservi la validazione dell'intera
feature map e il commit atomico dei suoi accumulatori.

I test aggiunti coprono discovery, input non validi, compatibilita dei
contratti, due passaggi, statistiche confrontate con un calcolo statico
indipendente e scoring esplicito contro compilato.

Il test reale `BottleTrainingTest` richiede tutte le 209 immagini e confronta
tutte le 196 celle delle immagini `000`, `104` e `208`: 588 confronti con
tolleranza assoluta `1e-9`. Controlla anche finitezza delle statistiche e degli
score, conteggi per cella e norma unitaria degli archetipi entro `1e-10`.

### Riproduzione

```powershell
mvn test
```

Il dataset predefinito e nella cartella sorella
`../Anomalib4j_md/mvtec-ad-DatasetNinja`. Per un'altra posizione:

```powershell
mvn "-Danomalib.dataset=C:\percorso\mvtec-ad-DatasetNinja" test
```

Il test reale fa parte della suite e fallisce esplicitamente se il dataset
manca o non contiene 209 immagini. Il log della verifica di questo sprint
e in `target/sprint6-run.log`; i report JUnit sono in `target/surefire-reports`.

### API minima

```java
try (var encoder = new OnnxMobileNetV4Encoder()) {
    var descriptor = new ModelDescriptor(
            "bottle-normal-v1", encoder.modelId(), encoder.variant().name(),
            encoder.grid(), 10_000, 42L, new NormalizationPolicy(1e-6), 1,
            encoder.preprocessingId());
    var projection = new DenseRademacherProjection(
            descriptor.grid().channels(), descriptor.vsaDimensions(),
            descriptor.projectionSeed());
    var paths = NormalImageTrainer.discoverBottleImages(datasetRoot);
    var model = new NormalImageTrainer(encoder, descriptor, projection, 1e-8)
            .train(paths);
    var engine = new HeatmapEngine(model.filters());
    var explicit = new ExplicitVsaScorer(model.memory(), model.statistics(),
            projection, descriptor.normalization());
}
```

## File creati o modificati

Percorsi relativi a `src/main/java/io/github/antctrlwin/anomalib4j/`:

- Modificato `model/ModelDescriptor.java`.
- Modificato `onnx/OnnxMobileNetV4Encoder.java`.
- Modificato `memory/PositionalMemoryBuilder.java`.
- Creato `projection/NormalizedPatchProjection.java`.
- Creato `training/NormalImageTrainer.java`.
- Creato `validation/ExplicitVsaScorer.java`.

Percorsi relativi a `src/test/java/io/github/antctrlwin/anomalib4j/`:

- Creato `model/PreprocessingContractTest.java`.
- Creato `projection/NormalizedPatchProjectionTest.java`.
- Creato `training/NormalImageTrainerTest.java`.
- Creato `training/BottleTrainingTest.java`.

Creato questo report `SPRINT6_RESULT.md`. Nessuna dipendenza aggiunta.

## Perimetro e limiti

Nessun errore di training o di equivalenza rilevato. Il collegamento MCP del
comando lungo e scaduto dopo 300 secondi; Maven ha continuato e terminato
correttamente. Il risultato e stato verificato dal log completo e dal report
JUnit `io.github.antctrlwin.anomalib4j.training.BottleTrainingTest.txt`, senza
riavviare il training.

Il modello viene costruito e compilato in memoria durante il test; la fine
del processo non lo conserva su disco. Il dataset viene soltanto letto.
Le tre immagini usate per il confronto sono normali di training: la verifica
certifica l'equivalenza numerica dei due percorsi, non la generalizzazione
o l'accuratezza di rilevamento delle anomalie.

Non vengono lette immagini anomale, maschere o annotazioni JSON. Calibrazione,
metriche di anomaly detection, fine-tuning e serializzazione restano fuori
dal perimetro richiesto.
