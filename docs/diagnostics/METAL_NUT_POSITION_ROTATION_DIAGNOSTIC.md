# Metal Nut — Diagnostica posizione/rotazione (post-hoc)

Run di riferimento: `final-comparison-20261003` (artefatti congelati, non modificati).
Tipo di analisi: **diagnostica post-hoc in sola lettura**. Nessun training, nessuna modifica a modello, score, mappe, configurazioni, dataset o split. Nuovi artefatti solo sotto `target/comparison/final-comparison-20261003/diagnostics/`.

Script: `tools/prerun/metal_nut_pose_diagnostic.py` (Python, `numpy`/`scipy`/`Pillow`/`matplotlib`/`scikit-image`).

---

## 1. Ipotesi

> Il forte calo di Anomalib4j su Metal Nut (Image AUROC 0.7576, Pixel AUROC 0.6926, AUPRO@0.30 0.3798 contro 0.95–0.99 dei competitor) è dovuto **principalmente** alla sensibilità della memoria/calibrazione posizionale a **rotazione e/o traslazione** dell'oggetto normale.

Stato iniziale: `HYPOTHESIS`.

Un oggetto "good" è un vero negativo: ogni sua immagine con score alto è un **falso positivo** dell'Image AUROC. L'ipotesi sostiene che questi falsi positivi siano spiegati dalla posa (posizione/orientamento) dell'oggetto, poco rappresentata nella memoria posizionale.

---

## 2. Metodo geometrico

Procedura deterministica, applicata in modo identico a training e test:

1. immagine in scala di grigi (700×700);
2. soglia di Otsu → maschera binaria;
3. componente connessa (8-connettività) più grande → oggetto;
4. riempimento dei buchi;
5. centro `(cx, cy)` = baricentro dei pixel oggetto;
6. orientamento = FFT dell'istogramma angolare dei pixel oggetto (360 bin) attorno al centroide; armonica dominante `k`; per **tutte** le 176 normali di training l'armonica dominante è `k = 4` (la rondella ha simmetria di ordine 4). Periodo `360/k = 90°`; orientamento `= -arg(F[4]) / 4 mod 90`;
7. bounding box dalla componente;
8. `object_area_fraction` = pixel oggetto / 700².

Statistiche di localizzazione dello Z (solo per la diagnostica spaziale, non usate per stimare la posa):

- banda di 25 px = dilatazione meno erosione dell'oggetto;
- `z_mass_on_boundary_fraction` = frazione della massa Z positiva nella banda;
- `z_mass_outside_fraction` = frazione di massa Z positiva fuori dall'oggetto;
- `z_argmax_dist_to_centroid_over_radius` = distanza del picco Z dal centroide / raggio oggetto.

**Limiti del metodo:** soglia globale (sensibile a riflessi speculari che possono saturare il bordo), l'armonica `k=4` mod `90°` collassa orientamenti visivamente distinti che differiscono di multipli di 90°, nessuna ground-truth di posa. La procedura è comunque identica tra training e test, quindi adatta a un confronto relativo.

---

## 3. Statistiche di training (176 normali Anomalib4j-fit)

| grandezza | valore |
| --- | --- |
| n | 176 |
| centro medio | (348.49, 352.69) px |
| dev. std. centro | (1.35, 1.19) px |
| `center_distance_mean` dal centro immagine | 3.35 px |
| raggio oggetto (stima) | ~281 px |
| orientamento dominante (mod 90°) | 79.69° |
| dev. std. circolare orientamento | 38.60° |
| `object_area_fraction` medio | 0.5052 |
| `object_area_fraction` std | 0.0028 |

Con `center_std ≈ 1.3 px` e raggio `≈ 281 px`, la **dispersione traslazionale del training è minuscola** (≈ 0.5% del raggio). La dispersione angolare è invece ampia (≈ 38.6° su un periodo di 90°), quindi le orientazioni di training coprono quasi tutta la periodicità dell'oggetto.

---

## 4. Ranking completo dei 22 good

`metal_nut_good_scores.csv` + `metal_nut_good_geometry.csv`. Ordine per image score Z decrescente.

| # | filename | score | center_dist (px) | train_center_dist | nearest_train_center_dist | orient (mod 90°) | ang_dev_nearest_train (°) | z_mass_boundary |
| -: | --- | -: | -: | -: | -: | -: | -: | -: |
| 1 | metal_nut_good_009.png | 9.123 | 3.860 | 0.637 | 0.091 | 3.77 | 0.297 | 0.095 |
| 2 | metal_nut_good_014.png | 5.862 | 3.432 | 0.187 | 0.093 | 65.52 | 0.273 | 0.075 |
| 3 | metal_nut_good_015.png | 5.549 | 5.139 | 1.930 | 0.328 | 13.76 | 0.222 | 0.082 |
| 4 | metal_nut_good_021.png | 4.955 | 1.913 | 1.707 | 0.053 | 37.53 | 0.118 | 0.146 |
| 5 | metal_nut_good_011.png | 4.945 | 3.000 | 0.352 | 0.074 | 62.28 | 0.051 | 0.077 |
| 6 | metal_nut_good_013.png | 4.883 | 3.877 | 2.595 | 0.273 | 80.90 | 0.150 | 0.099 |
| 7 | metal_nut_good_004.png | 4.750 | 2.151 | 3.244 | 0.703 | 73.71 | 0.148 | 0.119 |
| 8 | metal_nut_good_016.png | 4.682 | 4.362 | 5.351 | 2.066 | 67.95 | 0.107 | 0.119 |
| 9 | metal_nut_good_006.png | 4.655 | 1.389 | 2.072 | 0.458 | 64.94 | 0.305 | 0.054 |
| 10 | metal_nut_good_007.png | 4.578 | 3.740 | 0.980 | 0.072 | 10.82 | 0.168 | 0.064 |
| 11 | metal_nut_good_003.png | 4.492 | 6.873 | 3.668 | 1.165 | 9.14 | 0.351 | 0.073 |
| 12 | metal_nut_good_012.png | 3.941 | 2.922 | 0.555 | 0.116 | 19.46 | 0.103 | 0.109 |
| 13 | metal_nut_good_019.png | 3.467 | 0.965 | 3.267 | 0.670 | 73.42 | 0.139 | 0.072 |
| 14 | metal_nut_good_018.png | 3.447 | 2.158 | 1.410 | 0.088 | 48.87 | 0.011 | 0.194 |
| 15 | metal_nut_good_020.png | 3.318 | 3.314 | 0.990 | 0.143 | 72.83 | 0.213 | 0.084 |
| 16 | metal_nut_good_017.png | 3.285 | 3.417 | 1.197 | 0.230 | 55.11 | 0.103 | 0.144 |
| 17 | metal_nut_good_000.png | 3.283 | 3.175 | 1.079 | 0.056 | 38.47 | 0.497 | 0.157 |
| 18 | metal_nut_good_010.png | 3.228 | 2.506 | 1.340 | 0.199 | 89.05 | 1.404 | 0.103 |
| 19 | metal_nut_good_005.png | 3.221 | 5.140 | 1.834 | 0.195 | 45.32 | 0.428 | 0.157 |
| 20 | metal_nut_good_008.png | 3.114 | 4.795 | 2.460 | 0.253 | 74.31 | 0.291 | 0.110 |
| 21 | metal_nut_good_002.png | 2.644 | 2.538 | 0.818 | 0.260 | 24.52 | 0.163 | 0.124 |
| 22 | metal_nut_good_001.png | 2.061 | 3.320 | 0.044 | 0.241 | 26.85 | 0.049 | 0.105 |

Statistiche image score (n = 22): **min 2.0613, max 9.1228, mean 4.2492, median 4.2168, p75 4.8494, p90 5.4893**.

Osservazione immediata: gli score good (2.06–9.12) si sovrappongono ampiamente alla distribuzione degli score delle anomalie (molti difetti 3–8). Il good peggiore è un vero negativo con score più alto di molte anomalie.

---

## 5. Correlazioni score ↔ geometria

`metal_nut_geometry_correlations.csv` (n = 22). Pearson `r`/`p` e Spearman `rho`/`p`.

| variabile vs score | Pearson r | p | Spearman rho | p |
| --- | -: | -: | -: | -: |
| `center_distance` | 0.141 | 0.532 | 0.050 | 0.824 |
| `abs_dx` | -0.057 | 0.801 | -0.090 | 0.691 |
| `abs_dy` | 0.157 | 0.485 | 0.103 | 0.647 |
| `angular_dev_from_dominant` | -0.273 | 0.218 | -0.248 | 0.266 |
| `angular_dev_nearest_train` | -0.083 | 0.714 | -0.060 | 0.789 |
| `angular_dev_from_low_quartile_mean` | 0.116 | 0.608 | 0.275 | 0.216 |
| `distance_from_train_mean_center` | 0.014 | 0.950 | 0.074 | 0.744 |
| `nearest_train_center_distance` | 0.024 | 0.915 | -0.072 | 0.751 |
| `z_mass_on_boundary_fraction` | **-0.326** | 0.139 | **-0.394** | 0.070 |
| `z_mass_outside_fraction` | -0.108 | 0.632 | -0.305 | 0.167 |
| `z_argmax_dist_to_centroid_over_radius` | **-0.362** | 0.097 | -0.322 | 0.143 |
| `object_intensity_mean` | 0.202 | 0.368 | 0.205 | 0.360 |
| `object_intensity_std` | 0.277 | 0.212 | 0.110 | 0.626 |
| `object_bright_pixel_fraction` | **0.332** | 0.131 | 0.043 | 0.848 |

Definizione della deviazione angolare: poiché l'oggetto ha simmetria di ordine 4 (periodo 90°) e il training copre quasi tutta la periodicità, si usano (a) `angular_dev_from_dominant` rispetto all'orientamento dominante di training, (b) `angular_dev_nearest_train` = distanza angolare mod 90° dalla normale di training orientazionalmente più vicina (evita l'assunzione di un unico orientamento "corretto"), (c) `angular_dev_from_low_quartile_mean` rispetto all'orientamento medio del quartile good a score più basso.

**Nessuna correlazione con la posa è statisticamente significativa** (tutti i p > 0.05; n = 22, quindi potere diagnostico, non inferenza). Le correlazioni più forti (in valore assoluto) sono quelle spaziali Z e sono **negative**: più massa Z sul bordo / picco Z più lontano dal centro → **score più basso**, direzione opposta a un effetto "bordo da traslazione".

---

## 6. Esempi top / bottom

Quartile superiore good: `[good_009, good_014, good_015, good_021, good_011]`.
Quartile inferiore good: `[good_010, good_005, good_008, good_002, good_001]`.

Caso più informativo — **good_009 (score 9.12) vs good_010 (score 3.23)**:

| | good_009 | good_010 |
| --- | -: | -: |
| score | 9.123 | 3.228 |
| orientamento (mod 90°) | 3.77° | 89.05° |
| `angular_dev_nearest_train` | 0.297° | 1.404° |
| `center_distance` | 3.86 px | 2.51 px |
| `nearest_train_center_distance` | 0.091 px | 0.199 px |

Le due pose sono **di fatto equivalenti** (entrambe hanno una normale di training entro 1.4° e 0.2 px), eppure lo score differisce di 2.8×. Questo da solo esclude la posa come spiegazione principale.

---

## 7. Heatmap diagnostics

Tavole in `diagnostics/metal_nut_good_visuals/` (5 high + 5 low), ciascuna con immagine, heatmap Z, overlay, centro/orientamento stimati, score, e deviazioni rispetto al training.

- `metal_nut_mean_heatmaps.png`: heatmap media dei due quartili.
- `metal_nut_mean_diff.png`: differenza (high − low) con contorno medio dell'oggetto.
- `metal_nut_mean_native_{low,high}.npy`: media delle mappe native 14×14.

Misure: `mean_low_max = 1.276`, `mean_high_max = 2.259`, `diff_max = 2.422`, `diff_min = -1.418`.

Ispezione visiva:

- Il good a score massimo (good_009) concentra l'eccesso Z in un **blob localizzato** sul lobo/giunzione superiore-sinistro (circa `(285, 235)` assoluto, centroide `(349, 353)`), **non** in un anello di bordo globale né in una regione traslata.
- La differenza high−low mostra eccessi localizzati **in parte all'interno** del contorno dell'oggetto, non un rim alla periferia dell'oggetto.
- I pattern di falso positivo non seguono un anello di bordo né uno spostamento rigido: sono strutture locali di aspetto/intensità.

---

## 8. Risultati per difetto

`metal_nut_per_defect_diagnostics.csv`. Metriche pixel/AUPRO calcolate con il porting Python validato (vedi §11) e quindi confrontabili con l'evaluator congelato.

| difetto | n | Image AUROC | Pixel AUROC | AUPRO@0.30 | mean score | max score |
| --- | -: | -: | -: | -: | -: | -: |
| bent | 25 | 0.5545 | 0.7632 | 0.3604 | 5.42 | 15.65 |
| color | 22 | 0.7190 | 0.7243 | 0.4181 | 7.40 | 22.03 |
| flip | 23 | 0.9012 | 0.6470 | 0.2652 | 6.13 | 10.00 |
| scratch | 23 | 0.8715 | 0.7868 | 0.4963 | 9.96 | 18.06 |

Il difetto **bent** (deformazione/variante di forma) è **vicino al caso** sull'image score (0.55), mentre scratch/color/flip sono parzialmente riconoscibili. È il tipo di difetto, non la posa, a dominare il degrado. La bassa AUPRO diffusa (0.27–0.50) indica scarsa localizzazione anche dove lo score separa.

---

## 9. Evidenze a favore dell'ipotesi

- `center_distance` Pearson `r = +0.141` e `angular_dev_from_low_quartile_mean` `r = +0.116` / `rho = +0.275`: segno coerente con l'ipotesi, ma **piccoli e non significativi**.
- La dispersione angolare del training (38.6°) lascia spazio all'idea che alcune pose possano cadere tra le mode.

Nessuna di queste è sufficientemente forte da sostenere l'ipotesi.

---

## 10. Evidenze contro l'ipotesi

1. **Tutte le pose test sono in-distribution.** Ogni good ha una normale di training entro `1.4°` (mod 90°) e `2.07 px`; `center_distance` massima `6.87 px` su raggio `~281 px` (`≈2.4%`). Non ci sono pose "estreme".
2. **Tutte le correlazioni con posa/rotazione sono debolissime e non significative**, e le più forti hanno **segno opposto** (`z_mass_on_boundary r=-0.33`, `z_argmax_dist r=-0.36`): più massa al bordo → score più basso.
3. **Due good con posa quasi identica hanno score 2.8× diversi** (good_009 9.12 vs good_010 3.23), entrambi con una normale di training a distanza angolare < 1.5°.
4. **Il good a score massimo (good_009) non è una posa anomala**: è un quasi-duplicato orientazionale di una normale di training (`nearest_train_angular_dev 0.30°`, `nearest_train_center_dist 0.09 px`).
5. **La localizzazione del falso positivo è un blob interno localizzato**, non un rim di bordo né una regione scambiata dalla traslazione.
6. **Il tipo di difetto domina il degrado**: bent ≈ caso (0.55), mentre scratch/color/flip restano separabili; un effetto dominante di posa colpirebbe allo stesso modo tutti i difetti.
7. L'unico predittore (comunque debole e non significativo) con segno positivo è l'**aspetto/riflesso** (`object_bright_pixel_fraction r=0.332`, `object_intensity_std r=0.277`), non la geometria.

---

## 11. Classificazione finale dell'ipotesi

**`NOT_SUPPORTED`**.

La sensibilità della memoria/calibrazione posizionale a rotazione/traslazione dell'oggetto normale **non** è la causa principale del calo di Anomalib4j su Metal Nut. Le pose di test sono sostanzialmente indistinguibili da quelle di training, le correlazioni con posizione e orientamento sono nulle (talvolta di segno contrario), e falsi positivi con posa identica hanno score molto diversi. Le evidenze convergono invece verso una componente **di aspetto/texture locale** (riflessi/intensità) e verso la **natura del difetto** (bent quasi non rilevato).

Distinzione dei fattori:

- **traslazione**: nessun effetto rilevabile (correlazioni nulle; offset ≤ 2.4% del raggio).
- **rotazione**: nessun effetto rilevabile (≤ 1.4° dalla normale più vicina; correlazioni nulle).
- **altri fattori (più probabili)**: variabilità di aspetto/illuminazione locale e difficoltà intrinseca su deformazioni di forma (bent).

*Nota di validazione:* il porting Python di `LocalizationMetrics` riproduce **esattamente** l'aggregato congelato (image 0.7575757575757576, pixel 0.6926300242781803, AUPRO 0.3797941159804979, delta assoluto 0.0), quindi le metriche per-difetto sono coerenti con lo scoring ufficiale.

---

## 12. Limiti

- n = 22 good: risultati **diagnostici**, non inferenza statistica definitiva.
- Stima geometrica semplice (Otsu + componente più grande + armonica 4); il bordo speculare può alterare l'area e il baricentro.
- L'orientamento mod 90° (simmetria K=4) collassa pose che differiscono di multipli di 90° e non distingue un orientamento "frontale" da uno "girato".
- Nessuna ground-truth di posa; nessuna analisi per-pixel della texture oltre alle mappe Z.
- Una sola categoria (Metal Nut); i risultati non sono automaticamente estendibili alle altre.
- le mappe anomaly **non** sono state usate per stimare posizione/orientamento (come richiesto); la localizzazione Z è usata solo per descrivere dove cade il falso positivo.

---

## 13. Implicazione architetturale (FUTURE_WORK)

Nessuna modifica al modello in questa fase. Solo come direzione futura da valutare separatamente:

- Indagare la **robustezza all'aspetto/illuminazione locale** (riflessi speculari) piuttosto che la posa normalizzata.
- Verificare se una memoria/testo di **texture locale** o una normalizzazione del contrasto per-immagine riduca i falsi positivi su good_009-like.
- Rivalutare la gestione dei difetti di **forma** (bent), dove lo score è quasi casuale, indipendentemente dalla posa.

Qualsiasi intervento richiede una nuova decisione metodologica e non è autorizzato da questa analisi.

---

## Artefatti prodotti

`target/comparison/final-comparison-20261003/diagnostics/`:

- `metal_nut_good_scores.csv` — ranking dei 22 good con statistiche.
- `metal_nut_good_geometry.csv` — geometria dei 22 good (centro, bbox, orientamento, distanze dal training, statistiche Z).
- `metal_nut_training_geometry.csv` — geometria delle 176 normali di training.
- `metal_nut_geometry_correlations.csv` — correlazioni con p-value.
- `metal_nut_per_defect_diagnostics.csv` — metriche per difetto.
- `metal_nut_pose_summary.json` — riepilogo e validazione del porting.
- `metal_nut_mean_native_low.npy`, `metal_nut_mean_native_high.npy` — medie mappe native 14×14.
- `metal_nut_mean_heatmaps.png`, `metal_nut_mean_diff.png` — heatmap medie e differenza.
- `metal_nut_good_visuals/` — 5 tavole high + 5 low (`high_0N_*.png`, `low_0N_*.png`).

Script: `tools/prerun/metal_nut_pose_diagnostic.py`.
