# Preprocessing: una strategia, fast-path RGB con fallback

## Causa concreta

Il vecchio percorso eseguiva getRGB su tutti i pixel della sorgente, allocava un int[]
full-resolution e una TYPE_INT_RGB full-resolution, poi setRGB e infine resize a 224x224.
I risultati JMH/JFR precedenti attribuiscono alle due fasi isolate p50 rispettivamente
6.782976 ms e 4.055040 ms, contro 12.271616 ms del preprocessing completo. Sono misure
diagnostiche, non componenti da sommare per predire una latenza end-to-end.

## Modifica implementata e perimetro

Una sola strategia principale: eliminare la conversione intermedia full-resolution quando
la sorgente e una BufferedImage standard TYPE_INT_RGB o TYPE_3BYTE_BGR, senza alpha e sRGB.
In questi casi la sorgente viene disegnata direttamente nel target TYPE_INT_RGB 224x224.
Per gli altri tipi resta il percorso originale getRGB -> TYPE_INT_RGB -> setRGB -> resize.
La verifica della classe esatta esclude sottoclassi che potrebbero ridefinire getRGB.

Il target e sempre creato internamente con new BufferedImage(224,224,TYPE_INT_RGB): il suo
DataBufferInt e quindi contiguo, senza offset di sottoimmagine o stride esterno. Leggiamo
direttamente questo buffer invece di chiamare getRGB sul target. I bit RGB usati dalla
normalizzazione sono gli stessi; il byte alto non viene usato. Non si accede direttamente
al raster della sorgente, che puo essere una sottoimmagine.

Restano identici bicubic, rendering quality, dimensioni, formule double con cast float finale,
mean/std ImageNet, layout NCHW e identificatore del preprocessing. Nessuna modifica a encoder,
modello ONNX, VSA, Adjoint, calibrazione, evaluation o localization; nessuna API pubblica nuova.

File modificato:
- src/main/java/io/github/antctrlwin/anomalib4j/onnx/ImageNetPreprocessor.java

File creati:
- src/jmh/java/io/github/antctrlwin/anomalib4j/onnx/PreprocessingOptimizationIT.java
- PREPROCESSING_OPTIMIZATION_VALIDATION.md

Artefatti di verifica sotto target/benchmark:
- preprocessing-equivalence.csv
- preprocessing-optimization-smoke.json
- preprocessing-optimization-smoke.txt

Le modifiche gia presenti nel repository prima di questo lavoro sono state preservate.

## Correttezza prima della sostituzione

Prima di modificare la produzione, un test separato ha confrontato la candidata diretta
e la candidata con guardia contro la composizione delle fasi OLD gia conservate nel
PreprocessingMicroprofile. Nessuna tolleranza permissiva: confronto dei bit float.

Dataset del confronto:
- 24 combinazioni originali: TYPE_INT_RGB, TYPE_3BYTE_BGR, TYPE_INT_ARGB,
  TYPE_INT_ARGB_PRE, TYPE_BYTE_GRAY, TYPE_BYTE_INDEXED; dimensioni 1x1, 37x19,
  224x224, 301x239; stesso pattern sintetico dei test precedenti;
- 2 sottoimmagini 301x239 estratte da immagini random 417x289, offset (13,7),
  una INT_RGB e una 3BYTE_BGR, seed random 42;
- tutte le 292 Bottle reali: 209 train e 83 test, lette con ImageIO senza conversione
  preliminare. Tutte decodificate TYPE_3BYTE_BGR, quindi tutte ammesse al fast-path.

| Variante rispetto a OLD | Immagini | Float confrontati | Float differenti | maxAbsDiff | meanAbsDiff |
| --- | --- | --- | --- | --- | --- |
| Rendering diretto indiscriminato, scartato | 318 | 47,867,904 | 1,331,179 | 4.464285850524902 | 0.029905321776865612 |
| Candidata con guardia, prima della sostituzione | 318 | 47,867,904 | 0 | 0 | 0 |
| Produzione NEW dopo la sostituzione | 318 | 47,867,904 | 0 | 0 | 0 |

La media e la media assoluta su tutti i float della variante, non solo su quelli differenti.
I totali per variante non vanno sommati come osservazioni indipendenti.

Il rendering indiscriminato differisce nei nove casi ARGB (type 2), ARGB_PRE (type 3)
e BYTE_GRAY (type 10), ciascuno alle dimensioni 37x19, 224x224 e 301x239.
Nessuna Bottle differisce. Gli errori non sono trascurabili: il renderer gestisce alpha
e conversioni colore diversamente dall'estrazione sRGB e dalla rimozione alpha originali.
Non si e cercata una tolleranza per accettarli. Anche INDEXED, pur identico nel campione,
resta sul fallback per non generalizzare a palette non verificate.

Il CSV contiene ogni immagine, tipo, variante, conteggio e differenza. Le varianti
guarded e production devono entrambe avere zero differenze perche il test passi.
Il corpus e una verifica empirica ampia sul JDK/Java2D corrente, non una prova universale
su qualsiasi backend grafico o futura versione Java.

## Test eseguiti

Prima della modifica:
`mvn -B -Pbenchmark -Dtest=PreprocessingOptimizationIT test`: 1 test superato.

Dopo la modifica:
`mvn -B -Pbenchmark "-Dtest=*Test,PreprocessingOptimizationIT,!BottleTrainingTest,!BottleRealEvaluationTest" test`:
**86 test, zero failure/error/skipped, BUILD SUCCESS**. Inclusi i test precedenti di
preprocessing e l'equivalenza fra encoder isolato e produzione. Nessun training Bottle.

## Smoke benchmark

Eseguiti soltanto PreprocessingMicroprofile.fullPreprocessing e EncoderMicroprofile.fullExtract,
con SampleTime, thread/batch 1, un fork, warmup 1 x 300 ms e measurement 2 x 300 ms.
Sessione ONNX creata in setup; fixture Bottle originale in memoria.

| Metodo | p50 us/op | p95 us/op |
| --- | --- | --- |
| fullPreprocessing | 1368.064 | 1456.3328 |
| fullExtract | 2965.504 | 6576.5376 |

Entrambi completati senza errori. Rispetto ai p50 storici di circa 12.27 ms e 19.96 ms,
il segnale e favorevole. Le configurazioni di durata differiscono: non e una stima definitiva
del fattore di accelerazione. Detection e localization non sono state eseguite nello smoke,
per evitare il model building prolungato. Il loro miglioramento resta da misurare.

## Comando OpenCode: validazione e quattro benchmark completi

PowerShell, dalla radice del repository:

```powershell
mvn -B -Pbenchmark "-Dtest=*Test,PreprocessingOptimizationIT,!BottleTrainingTest,!BottleRealEvaluationTest" test org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.includeScope=test" "-Dmdep.outputFile=target/benchmark-classpath.txt"
if ($LASTEXITCODE -ne 0) { throw 'Equivalence or compilation failed' }
New-Item -ItemType Directory -Force target/benchmark | Out-Null
$benchmarkCp = "target/test-classes;target/classes;" + (Get-Content -Raw target/benchmark-classpath.txt).Trim()
$benchmarks = 'io.github.antctrlwin.anomalib4j.(onnx.PreprocessingMicroprofile.fullPreprocessing|onnx.EncoderMicroprofile.fullExtract|evaluation.PerformanceBaseline.detectionInference|evaluation.PerformanceBaseline.localizationInference)$'
java -cp $benchmarkCp org.openjdk.jmh.Main $benchmarks -foe true -rf json -rff target/benchmark/preprocessing-optimized-full.json -o target/benchmark/preprocessing-optimized-full.txt
if ($LASTEXITCODE -ne 0) { throw 'JMH failed' }
```

Le annotazioni mantengono SampleTime, thread/batch 1, warmup 5 x 2 s,
measurement 10 x 2 s, forks 2. Sono selezionati esattamente i quattro metodi richiesti.
Il building del modello nei due benchmark end-to-end resta in setup, fuori dal timing.
Gli output storici non vengono sovrascritti. Confrontare p50/p95/p99 e ambiente con
preprocessing-microprofile.json, encoder-microprofile.json e baseline.json.

## Fallback, problemi aperti e stop rule

- Alpha, premultiplied alpha, grayscale, indexed, altri formati/custom/sottoclassi:
  conversione full-resolution originale, seguita dallo stesso resize e dalla lettura
  diretta del target interno. Non e promesso un guadagno equivalente per questi formati.
- La correttezza misurata e esatta, ma va ricontrollata cambiando JDK/backend Java2D.
- Le fasi isolate del vecchio PreprocessingMicroprofile restano riferimenti OLD;
  fullPreprocessing chiama NEW. Non attribuire a NEW la somma dei vecchi micro-stadi.
- Il guadagno definitivo detection/localization e aperto fino al comando completo;
  anche code/GC e variabilita tra run devono essere considerate. ONNX conserva i
  thread nativi predefiniti: un caller JMH non implica un solo thread nativo.
- Nessuna seconda strategia o ottimizzazione e stata implementata. Se il confronto
  completo non conferma un beneficio utile end-to-end, o emergono regressioni di
  equivalenza/robustezza, documentare l'esito e chiudere questo filone senza altro tuning.
- Nessun commit effettuato.
