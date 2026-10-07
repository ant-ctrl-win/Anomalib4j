# Sprint 8 - Validazione reale della localizzazione (`bottle`)

Report dei risultati **misurati** dalla validazione dello Sprint 8 implementato da Astra.
Nessuna modifica al codice e stata apportata prima, durante o dopo l'esecuzione.
Nessuna metrica e stata reinterpretata: i valori sotto riportano fedelmente gli artefatti prodotti dal test.

- Directory: radice del repository
- Checkpoint Git corrente: `8f08201 docs: add repository-first agent workflow`
- Dataset (default del test): `../Anomalib4j_md/mvtec-ad-DatasetNinja`
- Eseguito il: 2026-09-30T09:31:11+02:00

---

## VALIDAZIONE

### Comando eseguito (esattamente come richiesto)

```powershell
mvn -B "-Dtest=*Test,BottleLocalizationIT,!BottleTrainingTest,!BottleRealEvaluationTest" "-DargLine=-Xmx2g" test
```

### Esito Maven e test

| Voce | Valore |
| --- | --- |
| Tests run | **65** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **0** |
| Durata (Surefire, somma dei test) | ~12 s |
| Total time Maven | 13.281 s |
| Esito Maven | **BUILD SUCCESS** |

### Dettaglio per classe

| Classe | Tests run | Failures | Errors | Skipped | Time |
| --- | --- | --- | --- | --- | --- |
| `adjoint.AdjointCompilerTest` | 2 | 0 | 0 | 0 | 0.052 s |
| `adjoint.AdjointEquivalenceTest` | 5 | 0 | 0 | 0 | 0.014 s |
| `calibration.HeatmapCalibratorTest` | 3 | 0 | 0 | 0 | 0.009 s |
| `evaluation.BottleEvaluationTest` | 4 | 0 | 0 | 0 | 0.009 s |
| `evaluation.BottleLocalizationIT` | 1 | 0 | 0 | 0 | 10.73 s |
| `evaluation.DatasetNinjaMasksTest` | 3 | 0 | 0 | 0 | 0.033 s |
| `evaluation.LocalizationArtifactsTest` | 2 | 0 | 0 | 0 | 0.013 s |
| `evaluation.LocalizationMapsTest` | 3 | 0 | 0 | 0 | 0.015 s |
| `evaluation.LocalizationMetricsTest` | 6 | 0 | 0 | 0 | 0.009 s |
| `inference.HeatmapEngineTest` | 5 | 0 | 0 | 0 | 0.035 s |
| `inference.HeatmapResultTest` | 2 | 0 | 0 | 0 | 0.007 s |
| `memory.PositionalMemoryBuilderTest` | 4 | 0 | 0 | 0 | 0.009 s |
| `memory.ProjectionStatisticsAccumulatorTest` | 4 | 0 | 0 | 0 | 0.008 s |
| `model.PreprocessingContractTest` | 1 | 0 | 0 | 0 | 0.004 s |
| `onnx.ImageNetPreprocessorTest` | 3 | 0 | 0 | 0 | 0.045 s |
| `onnx.OnnxMobileNetV4EncoderTest` | 4 | 0 | 0 | 0 | 0.240 s |
| `projection.DenseRademacherProjectionTest` | 4 | 0 | 0 | 0 | 0.012 s |
| `projection.NormalizedPatchProjectionTest` | 2 | 0 | 0 | 0 | 0.002 s |
| `training.NormalImageTrainerTest` | 3 | 0 | 0 | 0 | 0.133 s |
| `validation.ProjectionDiagnosticsTest` | 4 | 0 | 0 | 0 | 0.176 s |

Come previsto dalla convenzione, `BottleTrainingTest` e `BottleRealEvaluationTest` sono stati esclusi; `BottleLocalizationIT` e stato incluso esplicitamente ed eseguito (non parte del normale `mvn test` in quanto classe `*IT`).

- Report Surefire: `target/surefire-reports/io.github.antctrlwin.anomalib4j.evaluation.BottleLocalizationIT.txt` e `.../TEST-...BottleLocalizationIT.xml`

### Verifica convenzioni vs `LOCALIZATION_CONVENTIONS.md`

Confronto diretto tra quanto documentato e quanto implementato (nessuna reinterpretazione; solo lettura del codice).

| Convenzione documentata | Implementazione | Esito |
| --- | --- | --- |
| Ground truth: unione dei bitmap DatasetNinja all'origine `[x,y]`, risoluzione originale | `DatasetNinjaMasks.read` unisce con `|=` a `(y+row)*width + x+col` | conforme |
| Supporto PNG base64 diretto e PNG in zlib | Controllo firma PNG, altrimenti `InflaterInputStream` (cap 64 MiB) | conforme |
| Con alpha foreground = alpha != 0; senza alpha = RGB != nero | `alpha ? (argb>>>24)!=0 : (argb & 0x00ffffff)!=0` | conforme |
| Dimensioni/origini incoerenti -> errore | Verifica `size.width/height` e bounds dell'origine -> `IOException` | conforme |
| Immagini good -> maschera vuota | `if (good) return mask;` (array vuoto) | conforme |
| Predizione: raw Sprint 7, bilineare half-pixel `(dest+0.5)*src/dst-0.5`, bordi replicati (`align_corners=false`), nessuna trasformazione | `LocalizationMaps.upsample` con `Math.clamp` e interpolazione lineare, score non modificati | conforme |
| Pixel AUROC: ROC globale su tutti i pixel delle 83 immagini (good inclusi), score crescente = anomalia | `LocalizationMetrics.calculate` usa tutti i pixel e definisce positivo `region != 0` | conforme |
| Sweep esatto di tutti i valori distinti, pareggi insieme, integrazione trapezoidale | Sort + grouping `while (scores[end]==threshold)`, `auc += (fpr-oldFpr)*(oldTpr+tpr)/2` | conforme |
| PRO: componenti connesse a 8 vicini, separate per immagine | BFS 8-neighborhood dentro `add(...)`, `regions` azzerate per immagine | conforme |
| Media non pesata della frazione rilevata per componente (ogni regione pesa uguale) | `regionWeights[id] = 1.0/area/(areas.size()-1)` | conforme |
| FPR: FP / tutti i pixel background del test set (good incluse), soglie distinte `score >= threshold` partendo da nessun positivo | scansione decrescente da score massimo, `fpr = fp/negatives`, `negatives = used - positives` | conforme |
| AUPRO: area trapezoidale PRO/FPR in `[0,0.30]`, interpolazione lineare a 0.30, divisione per 0.30, segmenti verticali area zero | Area aggiunta solo `if (fpr > oldFpr && oldFpr < FPR_LIMIT)`, `interpolate` al limite, `proArea / FPR_LIMIT` | conforme |
| CSV curva PRO interpolato ogni 0.001; a segmenti verticali riporta l'estremo superiore | `sampledPro[sample] = interpolate(oldFpr, oldPro, fpr, pro, sample*.001)`; 301 campioni | conforme |
| Jackson solo nei test, nessuna dipendenza runtime | `jackson-databind:2.21.7` con `scope=test` in `pom.xml` | conforme |
| `BottleLocalizationIT` non eseguito da `mvn test`, legge `target/bottle-raw-heatmaps.csv` dello Sprint 7, verifica nomi + 196 celle + regressione image AUROC 1e-12, nessun encoder/training | Classe `*IT`; `assertEquals(names, rawMaps.keySet())`; `LocalizationArtifacts` impone 196 celle; `assertEquals(.9857142857142858, imageAuRoc, 1e-12)`; nessuna chiamata encoder | conforme |
| Output in `target/bottle-localization/`: `metrics.txt`, `pro-curve.csv`, quattro PNG | Presenti: `metrics.txt`, `pro-curve.csv`, 4 PNG | conforme |
| Scala colori comune derivata dalle mappe raw, solo visualizzazione | `low`/`high` calcolati su tutte le mappe, `Shared display scale` disegnata | conforme |

---

## METRICHE

Valori misurati da `BottleLocalizationIT` (fedeli a `target/bottle-localization/metrics.txt`).

| Metrica | Valore |
| --- | --- |
| Pixel AUROC | `0.95694957509359390` |
| AUPRO@0.30 (`auPro030`) | `0.86534746572585690` |
| Image AUROC (regressione Sprint 7) | `0.98571428571428580` |
| Numero immagini | 83 |
| Immagini `good` | 20 |
| Immagini anomalia | 63 |
| Pixel totali valutati | 67.230.000 (= 83 x 900 x 900) |
| Pixel anomali (positivi) | 3.886.731 |
| Pixel normali (negativi) | 63.343.269 |
| Regioni ground-truth (componenti 8-conn.) | 68 |
| Connettivita | 8 |
| Upsampling | `bilinear-half-pixel-border-replicate` |
| Soglie | `all-distinct-scores-ties-grouped` |
| Limite FPR | 0.30 |
| Normalizzazione AUPRO | `area/0.30` |

Composizione del dataset di test (verificata su disco, 83 immagini):

| Difetto | Immagini |
| --- | --- |
| `broken_large` | 20 |
| `broken_small` | 22 |
| `contamination` | 21 |
| `good` | 20 |

### Regressione image score Sprint 7

**Confermata invariata.** Il test esegue `assertEquals(.9857142857142858, imageAuRoc, 1e-12, "Sprint 7 image-score regression")` ed e passato. Il valore coincide esattamente con lo Sprint 7 (`SPRINT7_RESULT.md` -> `0.98571428571428580`; `PROJECT_STATE.md` -> `0.9857142857142858`). Gli image score Sprint 7 sono quindi invariati.

### Risultati separati per difetto

**Non prodotti.** Il test calcola le metriche solo globalmente sulle 83 immagini; non emette Pixel AUROC / AUPRO per `broken_large`, `broken_small`, `contamination`. I quattro PNG di esempio coprono una immagine per categoria (piu `good`), ma non sono metriche per categoria.

---

## ARTEFATTI

Prodotti in `target/bottle-localization/`:

| File | Dimensione | Contenuto |
| --- | --- | --- |
| `metrics.txt` | 414 B | 15 righe di riepilogo metriche |
| `pro-curve.csv` | 8.141 B | header + 301 righe (`fpr` 0.000..0.300 passo 0.001, `pro`) |
| `bottle_good_000.png` | 2.112.436 B | overlay: originale / ground truth / mappa raw |
| `bottle_broken_large_000.png` | 2.350.232 B | overlay |
| `bottle_broken_small_000.png` | 2.164.711 B | overlay |
| `bottle_contamination_000.png` | 2.249.923 B | overlay |

Ogni overlay contiene 3 pannelli (`Original`, `Ground truth (red)`, `Raw bilinear map (blue to red)`) su scala colore condivisa derivata dalle mappe raw, usata esclusivamente per visualizzazione.

Report Surefire prodotti:

- `target/surefire-reports/io.github.antctrlwin.anomalib4j.evaluation.BottleLocalizationIT.txt`
- `target/surefire-reports/TEST-io.github.antctrlwin.anomalib4j.evaluation.BottleLocalizationIT.xml`

Input fidato dello Sprint 7 usato (non rigenerato): `target/bottle-raw-heatmaps.csv` (844.255 B, 2026-09-29 16:22:42).

Nessun file CSV/TXT/PNG aggiuntivo e stato prodotto dalla localizzazione.

---

## DIFF

Stato Git rispetto al checkpoint `8f08201 docs: add repository-first agent workflow`.

### File modificati (tracciati)

| File | Diff |
| --- | --- |
| `pom.xml` | +6 righe |

`git diff --stat`:

```
 pom.xml | 6 ++++++
 1 file changed, 6 insertions(+)
```

Diff `pom.xml`:

```diff
     <dependencies>
+        <dependency>
+            <groupId>com.fasterxml.jackson.core</groupId>
+            <artifactId>jackson-databind</artifactId>
+            <version>2.21.7</version>
+            <scope>test</scope>
+        </dependency>
         <dependency>
             <groupId>com.microsoft.onnxruntime</groupId>
             <artifactId>onnxruntime</artifactId>
```

### File nuovi (untracked)

| File | Scope atteso |
| --- | --- |
| `LOCALIZATION_CONVENTIONS.md` | documentazione |
| `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/BottleLocalizationIT.java` | IT localizzazione |
| `.../evaluation/DatasetNinjaMasks.java` | ground truth |
| `.../evaluation/DatasetNinjaMasksTest.java` | unit test |
| `.../evaluation/LocalizationArtifacts.java` | lettura CSV raw |
| `.../evaluation/LocalizationArtifactsTest.java` | unit test |
| `.../evaluation/LocalizationMaps.java` | upsampling/overlay |
| `.../evaluation/LocalizationMapsTest.java` | unit test |
| `.../evaluation/LocalizationMetrics.java` | metriche |
| `.../evaluation/LocalizationMetricsTest.java` | unit test |

### Modifiche inattese fuori dallo scope Sprint 8

**Nessuna.** L'unico file tracciato modificato e `pom.xml` (dipendenza Jackson test-only, coerente con `LOCALIZATION_CONVENTIONS.md`). Non risultano modifiche a codice `src/main`, preprocessing, scoring, normalizzazione, proiezione, bundling o identita del modello. Nessun file e in stage (`git diff --cached` vuoto).

---

## EVENTUALI PROBLEMI

Nessun problema osservato. Tutti i 65 test passano (`Failures: 0`, `Errors: 0`, `Skipped: 0`), la regressione image AUROC Sprint 7 e confermata a `1e-12`, e le convenzioni implementate corrispondono a `LOCALIZATION_CONVENTIONS.md`.

Unica nota non bloccante: le metriche per singolo tipo di difetto (`broken_large`, `broken_small`, `contamination`) non sono prodotte dallo Sprint 8; e disponibile solo l'aggregato globale sulle 83 immagini.
