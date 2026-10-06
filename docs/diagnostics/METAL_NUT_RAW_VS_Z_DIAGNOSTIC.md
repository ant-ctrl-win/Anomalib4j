# Metal Nut — Diagnosi causale RAW vs positional Z

- **Run congelato**: `final-comparison-20261003`
- **Caso**: `target/comparison/final-comparison-20261003/anomalib4j/metal_nut/`
- **Natura**: analisi diagnostica post-hoc, read-only. Nessuna modifica a modello, score, mappe, configurazioni, dataset, split. Nessun nuovo training, nessun re-run del benchmark, nessun tuning.
- **Script**: `tools/prerun/raw_vs_z_diagnostic.py` (riusa il porting validato dell'evaluator congelato da `tools/prerun/metal_nut_pose_diagnostic.py`).
- **Output numerici**: `target/comparison/final-comparison-20261003/diagnostics/raw-vs-z/`.

## 1. Definizioni e semantica

Due spazi di punteggio, entrambi già esportati per ciascuna delle 115 immagini test:

- `raw[p] = 1 - compiledScore(features, p)` — 14×14, file `<stem>.raw.npy`.
- `z[p] = (raw[p] - mu[p]) / max(sigma_sample[p], 1e-6)` — 14×14, file `<stem>.native.npy`.

La mappa valutata a piena risoluzione `<stem>.evaluated.npy` (700×700) è `LocalizationMaps.upsample(z, 14, 14, 700, 700)`.
Lo `image_score` in `predictions.csv` è `max(z)` (`score_kind = positional_z`).

Per confrontare RAW e Z **con lo stesso protocollo congelato**:
- `raw_image_score = max(raw.npy)`;
- la mappa pixel RAW è `upsample(raw, 14, 14, 700, 700)` con la stessa procedura deterministica usata per Z (bilineare half-pixel, align_corners=false, bordi replicate);
- l'upsample RAW è stato verificato identico bit-per-bit agli `.evaluated.npy` prodotti da Java: `max_abs = 0.0`.

Il porting Python dell'evaluator (`LocalizationMetrics` + `DatasetNinjaMasks`) riproduce esattamente le metriche congelate per Z (delta 0.0 su Image/Pixel/AUPRO@0.30), quindi i valori RAW per difetto sono confrontabili.

## 2. Metriche aggregate RAW vs Z

| Metrica | RAW | Z (congelato) | Delta (Z − RAW) |
| --- | ---: | ---: | ---: |
| Image AUROC | 0.7047898338220919 | 0.7575757575757576 | +0.0528 |
| Pixel AUROC | 0.625562015711313 | 0.6926300242781803 | +0.0671 |
| AUPRO@0.30 | 0.3453858679360738 | 0.3797941159804979 | +0.0344 |

Regioni 132, foreground 6 602 541 px, background 49 747 459 px (identici per RAW e Z: stesse maschere).

**Lettura**: la calibrazione posizionale Z **migliora** tutte e tre le metriche aggregate. Il collo di bottiglia aggregato è quindi **prima della calibrazione**, in `raw = 1 - compiledScore`. La positional Z non è la causa del degrado aggregato Metal Nut.

## 3. RAW vs Z per difetto

| difetto | n | image_raw | image_z | pixel_raw | pixel_z | aupro_raw | aupro_z | mean_raw | mean_z |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| bent | 25 | 0.6455 | 0.5545 | 0.8502 | 0.7632 | 0.4777 | 0.3604 | 18.616 | 5.424 |
| color | 22 | 0.5289 | 0.7190 | 0.7029 | 0.7243 | 0.3164 | 0.4181 | 15.669 | 7.398 |
| flip | 23 | 0.9664 | 0.9012 | 0.5780 | 0.6470 | 0.1952 | 0.2652 | 31.789 | 6.134 |
| scratch | 23 | 0.6759 | 0.8715 | 0.7858 | 0.7868 | 0.4078 | 0.4963 | 18.888 | 9.963 |

Osservazioni:
- **bent**: la separazione image-level è scarsa in **entrambi** gli spazi (RAW 0.645, Z 0.555), mentre la pixel-level RAW è discreta (0.850): la localizzazione del difetto funziona ma lo score-immagine non lo separa dai good. → limite di rappresentazione pre-calibrazione.
- **flip**: RAW separa già bene a livello immagine (0.966), Z peggiora leggermente (0.901) ma resta alto.
- **color / scratch**: Z migliora la separazione image-level rispetto a RAW.
- `mean_good_raw` = 13.64, cioè lo score RAW medio dei good è già alto e si sovrappone ampiamente alle anomalie: la debolezza nasce nel raw.

## 4. Falsi positivi sui good

### 4.1 Ranking completo (22 good, ordinati per max Z)

| filename | max_raw | rank_raw | max_z | rank_z | raw_argmax | z_argmax |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| metal_nut_good_009.png | -2.6704 | 22 | 9.1228 | 1 | (11,2) | (4,5) |
| metal_nut_good_014.png | 4.9472 | 20 | 5.8616 | 2 | (9,1) | (6,10) |
| metal_nut_good_015.png | 27.3251 | 2 | 5.5486 | 3 | (7,11) | (7,11) |
| metal_nut_good_021.png | 20.4775 | 3 | 4.9551 | 4 | (7,13) | (13,13) |
| metal_nut_good_011.png | 10.0099 | 14 | 4.9451 | 5 | (8,0) | (6,10) |
| metal_nut_good_013.png | 11.0872 | 13 | 4.8826 | 6 | (11,10) | (7,3) |
| metal_nut_good_004.png | 7.7604 | 17 | 4.7499 | 7 | (9,2) | (10,7) |
| metal_nut_good_016.png | 9.2951 | 16 | 4.6821 | 8 | (9,1) | (8,7) |
| metal_nut_good_006.png | 13.7314 | 12 | 4.6552 | 9 | (12,9) | (6,3) |
| metal_nut_good_007.png | 7.4561 | 18 | 4.5778 | 10 | (11,3) | (9,4) |
| metal_nut_good_003.png | 9.6562 | 15 | 4.4922 | 11 | (3,1) | (12,2) |
| metal_nut_good_012.png | 29.7951 | 1 | 3.9414 | 12 | (11,6) | (7,10) |
| metal_nut_good_019.png | 5.1143 | 19 | 3.4666 | 13 | (10,1) | (11,0) |
| metal_nut_good_018.png | 15.9533 | 10 | 3.4467 | 14 | (6,13) | (6,3) |
| metal_nut_good_020.png | 2.3137 | 21 | 3.3179 | 15 | (10,1) | (13,2) |
| metal_nut_good_017.png | 18.9621 | 7 | 3.2847 | 16 | (5,13) | (1,5) |
| metal_nut_good_000.png | 15.8614 | 11 | 3.2829 | 17 | (7,13) | (13,13) |
| metal_nut_good_010.png | 18.1181 | 8 | 3.2279 | 18 | (11,11) | (8,3) |
| metal_nut_good_005.png | 19.0284 | 6 | 3.2211 | 19 | (6,13) | (7,5) |
| metal_nut_good_008.png | 19.4941 | 5 | 3.1136 | 20 | (9,2) | (6,10) |
| metal_nut_good_002.png | 20.0185 | 4 | 2.6440 | 21 | (4,1) | (13,13) |
| metal_nut_good_001.png | 16.3542 | 9 | 2.0613 | 22 | (8,13) | (13,4) |

Statistiche Z: min 2.0613, max 9.1228, mean 4.2492, median 4.2168, p75 4.8494, p90 5.4893.
Statistiche RAW: min -2.6704, max 29.7951, mean 13.6404.

**Il ranking RAW e il ranking Z sono fortemente discordanti**: `corr(max_raw, max_z)` di Pearson `r = -0.4595` (p = 0.0314), Spearman `rho = -0.3710` (p = 0.0892). Il segno **negativo** significa che le immagini con raw più alto tendono ad avere Z più basso: la calibrazione riordina i good in modo sostanziale, non li preserva.

Esempi di riordino estremo:
- `good_012`: `max_raw` 29.80 (rank_raw **1**) → `max_z` 3.94 (rank_z 12).
- `good_009`: `max_raw` -2.67 (rank_raw **22**, il più basso) → `max_z` 9.12 (rank_z **1**).
- `good_014`: rank_raw 20 → rank_z 2. `good_005`: rank_raw 6 → rank_z 19.

### 4.2 Focus `good_009` e `good_010`

| immagine | max_raw | rank_raw | max_z | rank_z | raw_argmax | z_argmax | raw@z_argmax | mu@z_argmax | sigma@z_argmax |
| --- | ---: | ---: | ---: | ---: | --- | --- | ---: | ---: | ---: |
| good_009 | -2.6704 | 22 | 9.1228 | 1 | (11,2) | (4,5) | -39.3335 | -88.3182 | 5.3695 |
| good_010 | 18.1181 | 8 | 3.2279 | 18 | (11,11) | (8,3) | -11.4916 | -65.5568 | 16.7492 |

**Causa di `good_009`**: il suo massimo Z non deriva da un raw elevato (anzi il suo raw map è il **più debole** dei 22 good). Alla cella `(4,5)`:
`z = (-39.3335 - (-88.3182)) / 5.3695 = 48.9847 / 5.3695 = 9.1228`.
Il raw locale è sì sopra la media di training `mu` di quella cella, ma il contributo decisivo è la combinazione **`mu` molto negativo + `sigma` piccolo (5.37, ben sotto la mediana 16.44)**, che amplifica un raw assoluto modesto. È un caso di **Z-amplification** a livello di cella, non di raw elevato.

`good_010` mostra il comportamento opposto: raw relativamente alto (rank 8) ma Z basso (rank 18) perché la cella `(8,3)` ha `sigma = 16.75` (vicino alla mediana), quindi il raw elevato non viene amplificato.

## 5. Distribuzione posizionale di sigma (196 celle)

Floor 1e-6 mai attivato (`floored = false` su tutte le 196 celle), quindi `sigma_effective = sigma_sample`.

| stat | sigma | mu |
| --- | ---: | ---: |
| min | 0.7131 | -107.7369 |
| p10 | 2.8940 | — |
| p25 | 6.5600 | -77.4639 |
| median | 16.4388 | -66.6930 |
| mean | 16.5639 | -66.0046 |
| p75 | 24.0473 | -50.8931 |
| p90 | 30.4790 | — |
| max | 42.2492 | -32.7100 |
| std | 10.4621 | 18.2072 |

Celle a sigma più piccolo (tutte centrali):

| cella (row,col) | sigma | mu |
| --- | ---: | ---: |
| (7,8) | 0.7131 | -96.38 |
| (7,6) | 1.1053 | — |
| (8,7) | 1.1457 | -96.00 |
| (7,5) | 1.1751 | -100.51 |
| (6,5) | 1.1811 | — |
| (8,6) | 1.2108 | — |
| (7,7) | 1.2182 | — |
| (6,8) | 1.2514 | — |
| (6,7) | 1.4785 | — |
| (6,6) | 1.5528 | — |

Celle che generano più spesso il massimo Z sui 22 good:

| cella | n good | mu | sigma |
| --- | ---: | ---: | ---: |
| (6,10) | 3 (`014`, `011`, `008`) | -73.95 | 11.42 |
| (13,13) | 3 (`021`, `000`, `002`) | -84.88 | 2.059 |
| (6,3) | 2 (`006`, `018`) | -75.52 | 14.18 |
| (4,5) | 1 (`009`) | -88.32 | 5.369 |
| (7,11) | 1 (`015`) | -55.75 | 14.97 |
| (7,3) | 1 (`013`) | -70.93 | 15.34 |
| (10,7) | 1 (`004`) | -62.25 | 11.80 |
| (8,7) | 1 (`016`) | -96.00 | 1.146 |
| (9,4) | 1 (`007`) | -69.80 | 16.28 |
| (12,2) | 1 (`003`) | -82.76 | 13.81 |

I falsi positivi **non** sono concentrati solo nelle celle a sigma minimo (le 10 celle a sigma più basso, tutte centrali, generano solo una minoranza dei massimi); però una cella a bassa varianza, `(13,13)` con `sigma = 2.059`, ne ospita 3. La correlazione `max_z` vs `sigma@z_argmax` è debole e non significativa (`r = -0.128`, p = 0.570), così come `max_raw` vs `sigma@z_argmax` (`r = 0.129`, p = 0.568): **sigma piccola non è un driver sistematico dei falsi positivi**.

## 6. Diagnosi

### 6.1 Falsi positivi sui good — `MIXED`

- **Componente dominante pre-calibrazione**: la separazione raw sui good è già debole e **peggiore** di Z (raw image 0.705 < Z 0.758; raw pixel 0.626 < Z 0.693; raw AUPRO 0.345 < Z 0.380). Il degrado aggregato nasce **prima della positional Z**, in `raw = 1 - compiledScore`.
- **Componente Z-amplification su singole immagini**: la Z riordina violentemente i good (`corr(max_raw, max_z) = -0.46`, p = 0.031); `good_009` passa da ultimo a primo per amplificazione a una cella con `mu` molto negativo e `sigma` piccolo. Quindi la calibrazione non è neutra e può trasformare un raw map globalmente debole in uno score-immagine elevato.
- Classificazione: **MIXED** (limite pre-calibrazione a livello aggregato + amplificazione posizionale su casi specifici).

### 6.2 Failure bent — `RAW_REPRESENTATION_LIMIT`

- `bent`: image_raw 0.645 e image_z 0.555, entrambi vicini al caso (0.5). Anche nello spazio raw la separazione image-level del difetto è scarsa.
- La pixel-level raw è discreta (0.850) e la Z la peggiora (0.763): le mappe localizzano il difetto ma lo score-immagine non lo distingue dai good.
- La causa è quindi nel **raw representation / aggregazione image-level**, non nella calibrazione posizionale. Classificazione: **RAW_REPRESENTATION_LIMIT**.

## 7. Risposta alla domanda causale

Delle tre opzioni proposte:

1. **prima della calibrazione, nel `raw = 1 - compiledScore`** → è la causa dominante del degrado aggregato;
2. **applicazione della positional Z** → non causa il degrado aggregato (migliora tutte le metriche), ma introduce un effetto secondario di rescaling/reshuffle su singoli good (`MIXED`, con `good_009` come esempio massimo);
3. **failure mode differenti per good e bent** → confermato: i good sono un problema di soglia/overlap dello score, i bent un problema di rappresentazione image-level del difetto. Sono failure mode distinti.

In sintesi: **il degrado Metal Nut nasce principalmente pre-calibrazione nel raw**, con un contributo secondario (non aggregato) di amplificazione posizionale su alcuni good.

## 8. Limiti

- Analisi **osservazionale** su un unico run congelato: decompone RAW vs Z ma non esegue interventi causali (nessuna controprova con calibrazione rimossa/alterata, vietata dai vincoli).
- Le metriche pixel RAW usano l'upsample deterministico dello stesso protocollo; sono quindi la "RAW localizzata", non una mappa RAW nativa a piena risoluzione.
- `n = 22` good: le correlazioni sono diagnostiche, non inferenza statistica definitiva.
- 14×14 = 196 celle: `max` è sensibile alla cella singola; i valori di `mu`/`sigma` sono quelli della calibrazione congelata (count = 44 su tutti i fit normal).
- Lo studio è limitato a Metal Nut / MobileNetV4 SPATIAL_14; nessuna generalizzazione ad altre categorie o encoder.
- Nessuna modifica a modello, score, mappe, configurazioni, dataset o split.

## 9. Artefatti prodotti

In `target/comparison/final-comparison-20261003/diagnostics/raw-vs-z/`:

| file | contenuto |
| --- | --- |
| `metal_nut_raw_vs_z_metrics.csv` | metriche aggregate RAW vs Z |
| `metal_nut_raw_vs_z_per_defect.csv` | tabella per difetto |
| `metal_nut_good_raw_vs_z.csv` | ranking completo dei 22 good (raw/z/rank/posizioni/µ/σ) |
| `metal_nut_sigma_cells.csv` | le 196 celle di calibrazione (mu, sigma, count, floored) |
| `metal_nut_raw_vs_z_summary.json` | sintesi + classificazioni |

Script: `tools/prerun/raw_vs_z_diagnostic.py`.
