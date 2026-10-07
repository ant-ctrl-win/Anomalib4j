# Sprint 7 - Valutazione reale `bottle` (raw heatmap, AUROC, benchmark di scoring)

Report dei risultati **misurati** dall'esecuzione del test gia implementato.
Nessuna modifica al codice e stata apportata prima dell'esecuzione.
Nessuna metrica e stata reinterpretata: i valori sotto riportano fedelmente gli artefatti prodotti dal test.

## Comando eseguito

```powershell
mvn -B -Dtest=BottleRealEvaluationTest test
```

- Directory: radice del repository
- Dataset (default del test): `../Anomalib4j_md/mvtec-ad-DatasetNinja`
- Log completo: `target/sprint7-run.log`

## Esito Maven e test

| Voce | Valore |
| --- | --- |
| Test eseguito | `io.github.antctrlwin.anomalib4j.evaluation.BottleRealEvaluationTest` |
| Tests run | 1 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Time elapsed (test) | 392,4 s |
| Esito Maven | `BUILD SUCCESS` |
| Total time | 06:34 min |
| Terminato | 2026-09-29T16:23:17+02:00 |

Report Surefire:
- `target/surefire-reports/io.github.antctrlwin.anomalib4j.evaluation.BottleRealEvaluationTest.txt`
- `target/surefire-reports/TEST-io.github.antctrlwin.anomalib4j.evaluation.BottleRealEvaluationTest.xml`

Estratto Surefire:

```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 392.4 s
```

## Immagini e conteggi

| Voce | Valore |
| --- | --- |
| Immagini di test processate | 83 |
| `good` | 20 |
| anomalie | 63 |
| `broken_large` | 20 |
| `broken_small` | 22 |
| `contamination` | 21 |
| Immagini normali di training | 209 |
| Passaggi immagine di training | 418 |
| Osservazioni statistiche | 40.964 |
| Confronti explicit/compiled su celle | 16.268 = 83 x 196 |

Le 83 immagini ordinate dal test: prima i `good`, poi le anomalie per nome file.
`target/bottle-evaluation.csv` contiene 84 righe (header + 83 immagini), `target/bottle-raw-heatmaps.csv` contiene 16.269 righe (header + 83 x 196 = 16.268 celle).

## Image AUROC

| Metrica | Valore |
| --- | --- |
| Image AUROC (good vs anomaly) | `0.98571428571428580` |

## Distribuzione degli image score (raw = 1 - score compilato)

Gli image score sono `max(raw discrepancy)`, non clampati. Valori come da `target/bottle-distributions.csv`.

| group | count | min | p25 | median | mean | p75 | max |
| --- | --- | --- | --- | --- | --- | --- | --- |
| good | 20 | -85.898898536340330 | -84.509458431951590 | -82.467519017799730 | -79.504323382619720 | -77.724674230531630 | -44.451788418221920 |
| broken_large | 20 | -41.309245976216170 | -25.565396354784557 | -13.986330982023520 | -7.2197146178273455 | 11.006183524948224 | 38.937249118137395 |
| broken_small | 22 | -66.275325395535130 | -46.090287687102130 | -33.576225748128046 | -30.835524196452980 | -16.298510465044927 | 15.801243117894440 |
| contamination | 21 | -70.594092129870280 | -58.194186821042490 | -46.885038782640514 | -32.186518336891520 | -16.466704435342272 | 49.352996644026880 |
| anomaly | 63 | -70.594092129870280 | -46.581604726753840 | -26.318277946117510 | -23.788773170686260 | -10.975549879226653 | 49.352996644026880 |

Scala di visualizzazione condivisa usata dalle heatmap: `displayMin = -113.36993126538826`, `displayMax = 49.352996644026880`.

Median e quartili sono quelli gia prodotti dal test (interpolazione lineare, tipo numpy); riportati qui senza modifica.

## Errore explicit-VSA vs compiled-Adjoint

| Metrica | Valore |
| --- | --- |
| Confronti totali su celle | 16.268 |
| Errore assoluto massimo | `1.3073986337985843e-12` |
| Tolleranza asserita dal test | `1e-9` |

## Benchmark del solo scoring (CNN esclusa, feature gia estratte)

Tempi per singola feature map (196 celle), da `target/bottle-metrics.txt`.

| Metrica | Valore (ns per feature map) |
| --- | --- |
| explicit VSA - mediana | 651.084.200,00000000 |
| explicit VSA - p25 | 650.741.925,00000000 |
| explicit VSA - p75 | 653.109.425,00000000 |
| compiled Adjoint - mediana | 13.012,109375000000 |
| compiled Adjoint - p25 | 12.959,570312500000 |
| compiled Adjoint - p75 | 13.177,929687500000 |
| Speedup misurato | 50.036,791210110770 |

Il test esegue warmup e 9 round alternati explicit/compiled, con `measure(...)`; i singoli round sono in `target/bottle-benchmark.csv`:

| round | explicit_ns_per_map | compiled_ns_per_map |
| --- | --- | --- |
| 1 | 655.423.800,00000000 | 13.177,929687500000 |
| 2 | 657.983.300,00000000 | 13.294,042968750000 |
| 3 | 651.084.200,00000000 | 12.034,765625000000 |
| 4 | 649.008.150,00000000 | 12.174,511718750000 |
| 5 | 649.082.200,00000000 | 13.012,109375000000 |
| 6 | 650.874.225,00000000 | 13.183,300781250000 |
| 7 | 653.109.425,00000000 | 12.976,074218750000 |
| 8 | 652.178.625,00000000 | 13.032,128906250000 |
| 9 | 650.741.925,00000000 | 12.959,570312500000 |

## Configurazione Java/CPU riportata dal benchmark

| Voce | Valore |
| --- | --- |
| java | `21.0.9+10-LTS` |
| os | `Windows 11` |
| processors | 24 |

## Heatmap generate

Percorso: `target/bottle-heatmaps/`. Generate 5 PNG (good: 2 esempi; `broken_large`, `broken_small`, `contamination`: 1 ciascuno), scala condivisa `displayMin..displayMax`.

| File | Dimensione |
| --- | --- |
| `bottle_good_000.png` | 127.245 B |
| `bottle_good_001.png` | 124.352 B |
| `bottle_broken_large_000.png` | 128.372 B |
| `bottle_broken_small_000.png` | 126.083 B |
| `bottle_contamination_000.png` | 127.362 B |

## Artefatti prodotti

| Artefatto | Presenza | Note |
| --- | --- | --- |
| `target/bottle-metrics.txt` | presente | blocco metriche stampato come `SPRINT7_RESULT` |
| `target/bottle-evaluation.csv` | presente | 84 righe (header + 83) |
| `target/bottle-raw-heatmaps.csv` | presente | 16.269 righe (header + 16.268 celle) |
| `target/bottle-distributions.csv` | presente | 5 righe (header + 4 gruppi + anomaly) |
| `target/bottle-benchmark.csv` | presente | 10 righe (header + 9 round) |
| `target/bottle-heatmaps/` | presente | 5 PNG |
| `target/surefire-reports/...BottleRealEvaluationTest.txt` | presente | 1 test, 0 failure |
| `target/surefire-reports/TEST-...BottleRealEvaluationTest.xml` | presente | report JUnit |
| `target/sprint7-run.log` | presente | log completo Maven + stdout del test |

Estratto stdout del test (blocco `SPRINT7_RESULT`):

```
images=83
good=20
anomaly=63
auroc=0.98571428571428580
maxError=1.3073986337985843e-12
comparisons=16268
explicitMedianNs=651084200.00000000
compiledMedianNs=13012.109375000000
speedup=50036.791210110770
explicitP25Ns=650741925.00000000
explicitP75Ns=653109425.00000000
compiledP25Ns=12959.570312500000
compiledP75Ns=13177.929687500000
displayMin=-113.36993126538826
displayMax=49.352996644026880
java=21.0.9+10-LTS
os=Windows 11
processors=24
```

## Anomalie o failure osservate

Nessuna. Il test `BottleRealEvaluationTest` e passato al primo tentativo (`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`). Nessuna eccezione, nessun confronto explicit/compiled oltre tolleranza, nessun artefatto mancante.

## Perimetro

Solo esecuzione e documentazione dello Sprint 7 gia implementato. Non sono state introdotte modifiche architetturali o funzionali; non sono state aggiunte pixel AUROC, AUPRO, fine-tuning, modello 28x28, cropping/allineamento, nuove metriche o refactoring. Il dataset e stato soltanto letto; il modello e costruito in memoria durante il test e non serializzato.
