# PREPROCESSING_MICROPROFILE_RESULT

Microprofilazione JMH del preprocessing + profilo JFR di `fullPreprocessing`.
Nessuna modifica al codice, nessuna ottimizzazione, nessun commit.
I valori riportati sono quelli misurati; non sono reinterpretati.

## Comandi eseguiti

```
mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.includeScope=test" "-Dmdep.outputFile=target/benchmark-classpath.txt"

New-Item -ItemType Directory -Force target/benchmark | Out-Null
$benchmarkCp = "target/test-classes;target/classes;" + (Get-Content -Raw target/benchmark-classpath.txt).Trim()
java -cp $benchmarkCp org.openjdk.jmh.Main 'io.github.antctrlwin.anomalib4j.onnx.PreprocessingMicroprofile.*' -foe true -rf json -rff target/benchmark/preprocessing-microprofile.json -o target/benchmark/preprocessing-microprofile.txt

New-Item -ItemType Directory -Force target/benchmark/preprocessing-jfr | Out-Null
java -cp $benchmarkCp org.openjdk.jmh.Main 'io.github.antctrlwin.anomalib4j.onnx.PreprocessingMicroprofile.fullPreprocessing' -foe true -prof 'jfr:dir=target/benchmark/preprocessing-jfr;configName=profile;stackDepth=128' -rf json -rff target/benchmark/preprocessing-jfr.json -o target/benchmark/preprocessing-jfr.txt
```

Fase di build: **BUILD SUCCESS** — 85 test, 0 failures/0 errors/0 skipped.
JMH `PreprocessingMicroprofile.*`: exit 0, `# Run complete. Total time: 00:06:14`.
JMH JFR `fullPreprocessing`: exit 0, `# Run complete. Total time: 00:01:04`.

Benchmark class: `io.github.antctrlwin.anomalib4j.onnx.PreprocessingMicroprofile`
(6 `@Benchmark` metodi: `sourceRgbExtraction`, `rgbImageMaterialization`, `bicubicResize`,
`resizedRgbExtraction`, `scalingNormalizationNchw`, `fullPreprocessing`).

## MISURATO da JMH — risultati per benchmark (unità: us/op)

| benchmark | mean | p50 | p95 | p99 | unità |
| --- | --- | --- | --- | --- | --- |
| sourceRgbExtraction | 6878.371 | 6782.976 | 7315.456 | 9125.888 | us/op |
| rgbImageMaterialization | 4140.766 | 4055.040 | 4440.064 | 6111.724 | us/op |
| bicubicResize | 1080.375 | 1067.008 | 1118.208 | 1347.584 | us/op |
| resizedRgbExtraction | 207.754 | 204.800 | 219.904 | 271.360 | us/op |
| scalingNormalizationNchw | 90.931 | 85.248 | 90.240 | 108.288 | us/op |
| fullPreprocessing | 12465.685 | 12271.616 | 13362.790 | 16874.865 | us/op |

Nota sulle medie: `sourceRgbExtraction`, `rgbImageMaterialization` e `fullPreprocessing`
presentano code destre con outlier rari; la media JMH è quindi più alta della mediana.
I percentili sopra sono i valori più rappresentativi.

## Confronto `fullPreprocessing` con la baseline precedente

| grandezza | baseline precedente | fullPreprocessing attuale | differenza |
| --- | --- | --- | --- |
| p50 (us/op) | ~12632 | 12271.616 | -360.384 (-2.85%) |
| p95 (us/op) | ~23522 | 13362.790 | -10159.210 (-43.19%) |

Il p50 è in linea (variazione < 3%); il p95 è nettamente inferiore al riferimento precedente.
Nessuna anomalia rispetto alla baseline.

## Tabella diagnostica degli stadi

| stage | p50 | p95 | p99 | note |
| --- | --- | --- | --- | --- |
| sourceRgbExtraction | 6782.976 | 7315.456 | 9125.888 | lettura della sorgente; stadio isolato, coda destra presente |
| rgbImageMaterialization | 4055.040 | 4440.064 | 6111.724 | materializzazione immagine RGB; stadio isolato |
| bicubicResize | 1067.008 | 1118.208 | 1347.584 | resize bicubico; stadio isolato |
| resizedRgbExtraction | 204.800 | 219.904 | 271.360 | estrazione RGB ridimensionata; stadio isolato |
| scalingNormalizationNchw | 85.248 | 90.240 | 108.288 | scaling/normalizzazione NCHW; stadio isolato |
| fullPreprocessing | 12271.616 | 13362.790 | 16874.865 | pipeline completa di preprocessing |

I tempi degli stadi isolati **non** sono sommati meccanicamente: `fullPreprocessing`
misura la pipeline direttamente ed è il riferimento reale; gli stadi isolati hanno valore
solo diagnostico.

## OSSERVATO da JFR — `fullPreprocessing`

Registrazione: `target/benchmark/preprocessing-jfr/.../profile.jfr` (1.827.501 byte),
config `profile`, durata 20 s (comprende warmup e misurazione). La run con profiler ha
misurato `fullPreprocessing` mean 13988.692 us/op, p50 13385.728, p95 17727.488,
p99 21135.360 (valori con overhead del profiler, quindi più alti della run JMH pulita).

### Metodi Java più caldi (ExecutionSample, 985 campioni)

| metodo | campioni | % |
| --- | --- | --- |
| sun.awt.image.ByteInterleavedRaster.getDataElements(int, int, Object) | 254 | 25.79% |
| java.awt.image.ComponentColorModel.extractComponent(Object, int, int) | 152 | 15.43% |
| java.awt.image.DirectColorModel.getDataElements(int, Object) | 113 | 11.47% |
| java.awt.image.ComponentColorModel.getRGBComponent(Object, int) | 105 | 10.66% |
| sun.awt.image.IntegerInterleavedRaster.setDataElements(int, int, Object) | 95 | 9.64% |
| java.awt.image.BufferedImage.getRGB(int, int, int, int, int[], int, int) | 79 | 8.02% |
| sun.awt.image.SunWritableRaster.markDirty() | 68 | 6.90% |
| java.awt.image.BufferedImage.setRGB(int, int, int, int, int[], int, int) | 63 | 6.40% |
| java.awt.image.ComponentColorModel.getRGB(Object) | 24 | 2.44% |
| java.awt.image.PackedColorModel.equals(Object) | 15 | 1.52% |
| io.github.antctrlwin.anomalib4j.onnx.ImageNetPreprocessor.preprocess(BufferedImage) | 2 | 0.20% |

Il tempo campionato è concentrato in chiamate AWT/BufferedImage
(`getRGB`/`setRGB`/raster/ColorModel). Il metodo applicativo `ImageNetPreprocessor.preprocess`
appare solo per 2 campioni diretti.

### Principali sorgenti di allocazione

Per classe (ObjectAllocationSample):

| classe | pressione di allocazione |
| --- | --- |
| int[] | 94.38% |
| float[] | 5.51% |
| (restanti classi) | < 0.1% in totale |

Per sito di allocazione:

| metodo | pressione di allocazione |
| --- | --- |
| java.awt.image.BufferedImage.getRGB(int, int, int, int, int[], int, int) | 64.23% |
| java.awt.image.DataBufferInt.<init>(int) | 30.21% |
| io.github.antctrlwin.anomalib4j.onnx.ImageNetPreprocessor.preprocess(BufferedImage) | 5.51% |

### GC

`jfr view gc-pauses`: **337 pause, tempo totale di pausa 363 ms**, minimo 0.0286 ms,
mediana 0.994 ms, media 1.08 ms, p90 1.85 ms, p95 2.77 ms, p99 4.25 ms, massimo 4.25 ms.
Sono presenti 213 `Young Garbage Collection` e 90 `Old Garbage Collection` (collettore G1);
diverse young GC partono con heap intorno a 450-590 MB e riportano l'heap a valori bassi.

## IPOTESI (non misurate direttamente)

Le voci seguenti sono interpretazioni, distinte dai dati misurati/osservati sopra.

- Il collo di bottiglia del preprocessing è il percorso per-pixel di `BufferedImage`
  (`getRGB`/`setRGB`, raster, `ColorModel`), non il metodo applicativo in sé.
- Le allocazioni `int[]`/`float[]` provengono in gran parte da `BufferedImage.getRGB(...)`
  e dalla creazione del `DataBufferInt`, coerentemente con la pressione di allocazione
  osservata.
- La coda lunga degli stadi isolati e della pipeline è plausibilmente legata ad attività GC
  e ad allocazioni per-pixel; le pause GC osservate sono piccole in valore assoluto
  (max 4.25 ms) ma frequenti (337 in 20 s).
- Eventuali conversioni di formato/copie intermedie: osservati tipi/allocazioni compatibili
  con copie di pixel (array `int[]`), ma JFR non identifica in modo univoco un singolo punto
  di conversione dominante oltre ai siti di allocazione elencati.
- Possibile area di intervento (solo indicata, non proposta): il percorso di accesso ai pixel
  tramite `BufferedImage.getRGB`/`setRGB`.

## Ambiente e configurazione effettiva

- JDK 21.0.9 Temurin (`21.0.9+10-LTS`), VM invoker
  `C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot\bin\java.exe`, VM options `-Xmx2g`.
- JMH 1.37; `@BenchmarkMode` SampleTime, unità microseconds, `@Threads(1)`,
  Warmup 5 iterazioni x 2 s, Measurement 10 iterazioni x 2 s, Timeout 10 min per iterazione,
  `@Fork(2)` con `-Xmx2g`. Parametro `dataset = ../Anomalib4j_md/mvtec-ad-DatasetNinja`.
- Profiler JFR: `jfr:dir=target/benchmark/preprocessing-jfr;configName=profile;stackDepth=128`.

## Artefatti

Mantenuti sotto `target/benchmark/`:

- `preprocessing-microprofile.json` (autorevole), `preprocessing-microprofile.txt`
- `preprocessing-jfr.json`, `preprocessing-jfr.txt`
- `preprocessing-jfr/<benchmark-encoded-path>/profile.jfr` (registrazione JFR)

## Warning / errori

Nessun `ERROR`, `FAILED` o `Exception`. Presente solo la nota informativa JMH sui
Compiler Blackholes (comportamento standard).
