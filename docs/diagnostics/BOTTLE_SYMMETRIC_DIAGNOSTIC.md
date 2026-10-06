# Bottle symmetric diagnostic (post-hoc)

Diagnostica post-hoc **simmetrica** a quella gia' svolta su Metal Nut, applicata a Bottle
per verificare se gli stessi failure mode tecnici (attivazioni periferiche/background del
backbone e riordinamento/amplificazione introdotti dalla calibrazione posizionale Z)
esistono anche in una categoria in cui Anomalib4j funziona bene.

Vincoli rispettati: nessuna modifica a modello, training, benchmark finale, configurazioni,
score, split o artefatti congelati; nessun nuovo fitting; nessun tuning. Analisi esclusivamente
in lettura sugli artefatti del run `final-comparison-20261003`.

Input usati:

- `target/comparison/final-comparison-20261003/anomalib4j/bottle/` (predizioni, mappe `native`/`raw`/`evaluated`, `parameters.csv`);
- dataset `mvtec-ad-DatasetNinja` (solo per stimare l'occupazione geometrica oggetto/sfondo, indipendente dalle anomaly map);
- confronto metodologico con `docs/diagnostics/METAL_NUT_RAW_VS_Z_DIAGNOSTIC.md` e `docs/diagnostics/SPATIAL_EDGE_ARTIFACT_AUDIT.md`.

Artefatti numerici prodotti:

`target/comparison/final-comparison-20261003/diagnostics/bottle-symmetric/`

- `bottle_raw_vs_z_metrics.csv`
- `bottle_raw_vs_z_per_defect.csv`
- `bottle_good_raw_vs_z.csv`
- `bottle_spatial_groups.csv`
- `bottle_sigma_cells.csv`
- `bottle_symmetric_summary.json`

Script (in sola lettura, scrive solo nella cartella `diagnostics/`):
`tools/prerun/bottle_symmetric_diagnostic.py`.

## 1. Definizioni e metodo

Semantica di scoring (invariata, dal run congelato):

- `raw[p] = 1 - compiledScore[p]` (map 14x14)
- `z[p] = (raw[p] - mu[p]) / max(sigma[p], 1e-6)` (map 14x14)
- `image_score` (Bottle) = `max(z)`; `score_kind = positional_z`
- mappa `evaluated` = upsample bilineare half-pixel border-replicate di `z` a 900x900
  (`LocalizationMaps.upsample`).

Per il confronto causale RAW vs Z:

- **RAW image score** = `max(raw_map)` (map `raw` gia' esportata);
- **RAW map a risoluzione 900x900** = upsample di `raw` con lo **stesso** identico algoritmo
  usato per `z` (riprodotto in numpy);
- validazione dell'upsample: la mappa numpy di `z` coincide con `<stem>.evaluated.npy`
  prodotta dal runner Java con **max_abs = 0.0**;
- evaluator pixel/AUPRO = port fedele di `LocalizationMetrics.java` (FPR_LIMIT 0.30,
  regioni 8-connected, integrazione PRO), gia' validato esatto contro l'aggregato congelato.

Stima geometrica oggetto/sfondo (indipendente dalle anomaly map): scala di grigi -> soglia di
Otsu (l'oggetto Bottle e' la classe scura) -> componente 8-connected piu' grande -> riempimento
buchi -> griglia 14x14 con bordi di cella `round(i*900/14)`. Una cella e' `always_background`
se non contiene mai oggetto su tutti i 167 normali di fit + i 20 good.

Gruppi: `outer_border` (52 celle), `second_ring` (44), `interior` (100), `top_row`/`bottom_row`/
`left_col`/`right_col` (14 celle ciascuno), `always_background` (43), `object_cells` (153).

## 2. RAW vs positional Z su Bottle

| Metrica | RAW | Z | delta (Z - RAW) |
| --- | ---: | ---: | ---: |
| Image AUROC | 0.9857142857142858 | 1.0 | +0.0142857 |
| Pixel AUROC | 0.9569268263721693 | 0.9616640201342855 | +0.0047372 |
| AUPRO@0.30 | 0.8650976456361172 | 0.8774472899967225 | +0.0123496 |

foreground = 3.886.731 px, background = 63.343.269 px, regioni = 68 (identici per RAW e Z).

Osservazioni:

- i valori **RAW** riproducono esattamente le baseline Bottle RAW documentate
  (Image `0.9857142857142858`, Pixel `0.9569268263721693`, AUPRO `0.8650976456361172`);
- i valori **Z** riproducono esattamente l'aggregato congelato del benchmark;
- la Z **migliora** tutte e tre le metriche, ma di poco (delta <= 0.015). Su Bottle il
  segnale e' gia' quasi saturo anche prima della calibrazione posizionale.

## 3. Metriche RAW e Z per difetto

| Difetto | n | Image RAW | Image Z | Pixel RAW | Pixel Z | AUPRO RAW | AUPRO Z | mean score RAW | mean score Z |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| broken_large | 20 | 1.0 | 1.0 | 0.9711032732641026 | 0.9792344962122604 | 0.9092199594601008 | 0.927975266066969 | -7.2435 | 51.3486 |
| broken_small | 22 | 0.9840909090909091 | 1.0 | 0.9859193758240618 | 0.9885307745176721 | 0.9270748684334233 | 0.939542807706098 | -30.9184 | 36.0159 |
| contamination | 21 | 0.9738095238095238 | 1.0 | 0.9481534179516362 | 0.9563799923875808 | 0.8537035384357702 | 0.8802996866651941 | -32.2982 | 57.1124 |

`mean_good_raw = -79.50`, `mean_good_z = 4.7130`. Tutti e tre i difetti raggiungono Image Z = 1.0;
la Z porta a perfezione il `broken_small` (0.984 -> 1.0). La separazione RAW e' gia' enorme
(good ~ -79 vs difetti ~ -7 / -31 / -32), quindi la Z non e' necessaria per separare.

## 4. Analisi dei 20 good

Distribuzione score (Z): min 2.656396250889016, p75 5.473495504285719, p90 6.483583022854289,
median 4.426844554497485, mean 4.712751793838482, max 8.74699940093038.

Correlazione `max_raw` vs `max_z` sui good (n=20):

- Pearson r = **+0.6816** (p = 0.00093, significativa)
- Spearman rho = +0.3624 (p = 0.116)

Segno **opposto** a Metal Nut (r = -0.4595). Su Bottle la Z **preserva** e in parte rafforza
il ranking RAW dei good; non lo riscrive.

Riordinamenti piu' forti (rank Z - rank RAW):

| filename | max_raw | rank RAW | max_z | rank Z | shift | argmax RAW | argmax Z |
| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| good_006 | -43.949 | 1 | 8.747 | 1 | 0 | (6,6) | (6,6) |
| good_018 | -73.132 | 2 | 7.400 | 2 | 0 | (8,6) | (8,6) |
| good_010 | -75.404 | 3 | 6.382 | 3 | 0 | (11,7) | (6,11) |
| good_003 | -85.254 | 19 | 4.875 | 8 | +11 | (2,7) | (13,7) |
| good_004 | -84.268 | 15 | 5.869 | 5 | +10 | (5,3) | (5,3) |
| good_001 | -77.810 | 6 | 2.860 | 19 | -13 | (11,7) | (6,2) |
| good_005 | -78.456 | 7 | 2.656 | 20 | -13 | (7,6) | (6,9) |

`good_rank_shift_abs_max = 13`, mediana 4. I primi due good (006, 018) restano in testa sia in
RAW sia in Z, e per entrambi la cella di argmax Z coincide con quella di argmax RAW: i vertici
del ranking sono genuinamente guidati dal RAW. I riordinamenti riguardano la parte centrale/bassa
della distribuzione e non cambiano l'esito (nessun good supera il difetto piu' debole).

Nota rispetto a Metal Nut: la' `good_009` aveva RAW minimo (rank 22) e Z massimo (rank 1) per
amplificazione di una cella a varianza minima; qui **non** esiste un caso analogo (il massimo
good per Z e' anche il massimo per RAW).

## 5. Statistiche spaziali 14x14 (medie sui 20 good)

Nessuna cella raggiunge mean Z > 2: `cells_mean_z_gt2 = gt3 = gt4 = 0` per **tutti** i gruppi.
La mean Z per cella massima e' 1.287 (interior). L'argmax si distribuisce 14 interior,
4 outer_border, 2 second_ring: il bordo e' **sotto-rappresentato** rispetto alla sua quota di
celle (26.5% delle celle -> 27% degli argmax contando i sottogruppi; ma 6/20 = 30% se aggregati).

| Gruppo | n | mean mu | mean sigma | min sigma | mean cell mean Z | max cell mean Z | argmax | obj fraction |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| outer_border | 52 | -100.938 | 1.0497 | 0.19066 | -0.02134 | 0.87136 | 4 | 0.0392 |
| second_ring | 44 | -99.687 | 1.1635 | 0.52188 | 0.15890 | 0.75536 | 2 | 0.5172 |
| interior | 100 | -93.851 | 1.9255 | 0.48508 | 0.13822 | 1.28729 | 14 | 0.9704 |
| top_row | 14 | -100.762 | 0.9446 | 0.19066 | -0.12596 | 0.56535 | 0 | 0.0325 |
| bottom_row | 14 | -100.903 | 0.9569 | 0.20505 | 0.09362 | 0.87136 | 2 | 0.0440 |
| left_col | 14 | -101.052 | 0.8622 | 0.19066 | -0.07253 | 0.69081 | 2 | 0.0303 |
| right_col | 14 | -98.916 | 1.2419 | 0.28964 | -0.04891 | 0.53893 | 0 | 0.0388 |
| always_background | 43 | -99.888 | 0.9538 | 0.19066 | 0.09018 | 0.87136 | 3 | 0.0 |
| object_cells | 153 | -96.241 | 1.6818 | 0.48508 | 0.10344 | 1.28729 | 17 | 0.7963 |

## 6. Calibration statistics (196 celle)

`mu`: min -111.618, median -96.094, mean -97.041, max -86.767.
`sigma` (effective): min 0.19065769809561303, p10 0.6869084309555233, p25 0.9177820297811553,
median 1.4017774161065435, mean 1.5220728692657932, p75 1.8465416471189025,
p90 2.5337639297621255, max 5.297134268612971. Il floor 1e-6 non e' mai attivato.

Border vs interior: mu piu' negativo sul bordo (`outer_border` -100.94, `always_background` -99.89)
che all'interno (`interior` -93.85); sigma piu' piccola sul bordo (1.05 / 0.95) che all'interno (1.93).
Il background e' quindi associato a RAW molto bassi (compiledScore alto) e a varianza piccola.

10 celle a sigma minima (sono tutte celle d'angolo/bordo):

| cella | mu | sigma | argmax (good) |
| --- | ---: | ---: | ---: |
| (0,0) | -93.408 | 0.19066 | 0 |
| (13,0) | -94.823 | 0.20505 | 0 |
| (0,13) | -92.373 | 0.28964 | 0 |
| (1,0) | -96.478 | 0.41949 | 1 |
| (13,12) | -91.316 | 0.45672 | 1 |
| (13,1) | -97.584 | 0.45921 | 0 |
| (11,2) | -100.290 | 0.48508 | 0 |
| (1,13) | -91.339 | 0.48631 | 0 |
| (9,12) | -97.859 | 0.52188 | 0 |
| (13,10) | -103.962 | 0.52944 | 0 |

Celle che generano piu' spesso l'argmax Z sui good: (6,11) con 2 good; poi singole
(6,6), (8,6), (1,4), (5,3), (13,12), (10,9), (13,7), (3,0), (9,3). Le celle a varianza minima
non dominano gli argmax (solo 2 celle su 10 ospitano 1 argmax ciascuna).

## 7. Background occupancy (stima geometrica)

- `always_background` = 43 celle (mai oggetto su 167 fit + 20 good);
- `object_cells` = 153 celle;
- occupazione media dell'oggetto = 0.6216.

Correlazioni sull'occupazione (196 celle):

- occ vs `mu`: r = **+0.6735** (p = 2.9e-27), rho = +0.609
- occ vs `sigma`: r = **+0.4920** (p = 2.4e-13), rho = +0.665
- occ vs mean Z (good): r = +0.0446 (p = 0.535, ns)
- occ vs argmax frequency: r = +0.0927 (p = 0.196, ns)

Quindi la calibrazione **dipende fortemente** dall'occupazione (mu piu' alto e sigma piu' grande
dove c'e' oggetto), ma l'**attivazione Z dei good non dipende dall'occupazione** (correlazioni ns).
Nota: su Metal Nut la correlazione occ-sigma era **negativa** (-0.336); su Bottle e' positiva
(+0.492), quindi la struttura di calibrazione oggetto/sfondo e' di segno diverso.

## 8. Attivazioni periferiche/background: quantificazione

Immagine:

- `image_object_only_z = 1.0` e `image_object_only_raw = 0.9857142857142858`
  -> usando **solo** le celle oggetto l'Image AUROC e' **identica** a quella full-image:
  le celle di background non cambiano l'esito a livello immagine.

Pixel:

- azzerando le celle `always_background` nella mappa full-resolution prima dell'evaluator:
  Pixel AUROC 0.9602735356936828 (full 0.9616640201342855), AUPRO@0.30 **0.8866570764323092**
  (full 0.8774472899967225, leggermente piu' alta). La variazione e' ~0.001-0.009 e di segno
  misto: il background e' ininfluente e non peggiora le metriche pixel.

Pressione di attivazione:

- good: 121.068 pixel con Z>3 su 16.200.000 (0.747%); di cui 18.629 su background
  (15.4% dei pixel good con Z>3) e 102.439 su oggetto; mean Z background 0.09527 vs oggetto 0.10201
  (praticamente identiche).
- difetti: 8.647.854 pixel con Z>3 su 51.030.000 (16.95%); di cui 368.418 su background (4.3%)
  e 8.279.436 su oggetto (**95.7%**).

In sintesi: un'attivazione periferica/background **esiste** anche su Bottle (3/20 argmax su celle
always-background, ~18.6k pixel good con Z>3 su background), ma e' **marginale** rispetto al segnale
anomalo reale, che mette il 95.7% della sua massa Z>3 sull'oggetto.

## 9. Bottle vs Metal Nut

Confronto quantitativo (entrambi self-training `anomalib4j`, run `final-comparison-20261003`):

| Dimensione | Bottle | Metal Nut |
| --- | --- | --- |
| Qualita' RAW | Image 0.9857, Pixel 0.9569, AUPRO 0.8651 | Image 0.7048, Pixel 0.6256, AUPRO 0.3454 |
| Effetto della Z | +0.0143 / +0.0047 / +0.0123 (piccolo, positivo); corr(max_raw,max_z) **+0.682** | +0.0528 / +0.0671 / +0.0344 (piu' grande ma resta debole); corr **-0.459** |
| Falsi positivi good | max good 8.747 < min difetto 11.240 (margine 2.49); nessun good supera un difetto; Z non riscrive il ranking | max good 9.123 > molti difetti; AUROC 0.758; `good_009` amplificato dalla Z (cella (4,5)) |
| Border/background activations | presenti ma marginali: 3/20 argmax su background, 15.4% dei pixel good Z>3 su background, ablazione ininfluente | presenti e piu' incidenti: 7/22 argmax su background (bottom row 5/22), firma di bordo del backbone |
| Capacita' per tipo di difetto | tutti i difetti ~1.0 Image; broken_large 0.979 Pixel, broken_small 0.989, contamination 0.956 | bent 0.555, color 0.719, flip 0.901, scratch 0.872 (bent quasi a caso) |
| Segnale anomalo vs rumore nominale | segnale domina: 95.7% della massa Z>3 dei difetti sull'oggetto; good mean Z ~0.10 | segnale debole: separazione RAW gia' scarsa prima della calibrazione |

## Classificazione finale

**SAME_TECHNICAL_EFFECTS_BUT_SIGNAL_DOMINATES**

Motivazione (criteri espliciti, non assunti):

1. **Gli effetti tecnici esistono anche su Bottle**: pressione di attivazione periferica/background
   (3 dei 20 argmax Z cadono su celle sempre-background; ~18.6k pixel good con Z>3 sul background),
   e la calibrazione dipende fortemente dall'occupazione (occ-mu +0.674, occ-sigma +0.492).
2. **Ma non compromettono il risultato**: `image_object_only == image full` (1.0), l'ablazione del
   background lascia Pixel AUROC/AUPRO invariati (anzi AUPRO leggermente migliore), e il 95.7% della
   massa Z>3 dei difetti e' sull'oggetto contro il 15.4% dei good.
3. **La Z non danneggia Bottle**: migliora tutte le metriche e correla positivamente con il ranking
   RAW dei good (+0.682), a differenza di Metal Nut (-0.459).

Cautela: gli effetti sono "gli stessi in natura" (attivazioni periferiche del backbone +
calibrazione sensibile all'occupazione + riordinamento Z), ma la **magnitudine e il segno** di
alcune relazioni differiscono da Metal Nut (es. occ-sigma positivo vs negativo). La conclusione
e' quindi limitata alle due categorie analizzate e non e' una legge generale.

## Limiti

- Analisi **osservazionale post-hoc**: si confrontano RAW e Z sulle stesse immagini, non si
  interviene sul modello; nessuna causalita' forte, solo decomposizione del comportamento.
- Score a livello immagine = `max` su mappe 14x14: il massimo e' sensibile a singole celle.
- Occupazione stimata con Otsu + componente piu' grande: sufficiente per distinguere
  oggetto/sfondo, non e' una segmentazione di precisione (l'oggetto Bottle e' compatto e scuro,
  quindi robusta, ma non perfetta su riflessi).
- 20 good e 63 difetti: le correlazioni sono diagnostiche, non inferenza statistica definitiva.
- Il confronto con Metal Nut riusa gli stessi metodi ma su una categoria e geometria diverse
  (900x900 vs 700x700, oggetto chiaro su scuro vs scuro su chiaro).
- Non sono state rieseguite l'audit ONNX/receptive-field (gia' VERIFIED su Metal Nut; nessuna
  discrepanza concreta e' emersa qui).

## Artefatti

- `target/comparison/final-comparison-20261003/diagnostics/bottle-symmetric/bottle_raw_vs_z_metrics.csv`
- `target/comparison/final-comparison-20261003/diagnostics/bottle-symmetric/bottle_raw_vs_z_per_defect.csv`
- `target/comparison/final-comparison-20261003/diagnostics/bottle-symmetric/bottle_good_raw_vs_z.csv`
- `target/comparison/final-comparison-20261003/diagnostics/bottle-symmetric/bottle_spatial_groups.csv`
- `target/comparison/final-comparison-20261003/diagnostics/bottle-symmetric/bottle_sigma_cells.csv`
- `target/comparison/final-comparison-20261003/diagnostics/bottle-symmetric/bottle_symmetric_summary.json`
- script: `tools/prerun/bottle_symmetric_diagnostic.py`
