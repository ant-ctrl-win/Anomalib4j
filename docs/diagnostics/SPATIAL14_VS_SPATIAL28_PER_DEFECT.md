# SPATIAL_14 vs SPATIAL_28 — analisi per tipo di difetto (Bottle)

Analisi dei risultati **gia' esistenti** per i difetti `broken_large`,
`broken_small`, `contamination`, calcolata esclusivamente dagli artefatti
presenti sotto `target/` e dal dataset MVTec gia' disponibile. Nessun
training, nessuna riesecuzione dei test reali lunghi, nessuna modifica al
codice sorgente, nessun commit.

## Artefatti di input effettivamente utilizzati

| Artefatto | Ruolo |
| --- | --- |
| `target/bottle-raw-heatmaps.csv` | mappe raw SPATIAL_14 (`14x14`, 16.268 celle = 83 x 196) |
| `target/bottle-spatial28/raw-heatmaps.csv` | mappe raw SPATIAL_28 (`28x28`, 65.072 celle = 83 x 784) |
| `target/bottle-localization/metrics.txt` | riferimento aggregato congelato SPATIAL_14 |
| `target/bottle-spatial28/metrics.csv`, `target/bottle-spatial28/comparison.md` | riferimento aggregato congelato SPATIAL_28 |
| `../Anomalib4j_md/mvtec-ad-DatasetNinja/test/img/bottle_*.png` | 83 immagini di test |
| `../Anomalib4j_md/mvtec-ad-DatasetNinja/test/ann/bottle_*.png.json` | ground truth bitmap per immagine |
| `target/classes`, `target/test-classes` | helper di localizzazione gia' compilati (`LocalizationMetrics`, `LocalizationMaps`, `DatasetNinjaMasks`, `BottleEvaluation`) |

Nessun artefatto necessario risulta mancante. Gli image score sono stati
ricavati dalle mappe raw come `max(raw)`, coerenti con
`target/bottle-evaluation.csv` e `target/bottle-spatial28/image-scores.csv`.

## Sottoinsiemi analizzati

Le 20 immagini `good` sono il gruppo normale comune a tutti i sottoinsiemi.
Il background di ciascun sottoinsieme include quindi le good.

| Difetto | Immagini difetto | Good | Totale immagini | Regioni GT (8-conn.) | Pixel foreground | Pixel background | Pixel totali |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| broken_large | 20 | 20 | 40 | 20 | 1.895.796 | 30.504.204 | 32.400.000 |
| broken_small | 22 | 20 | 42 | 26 | 546.762 | 33.473.238 | 34.020.000 |
| contamination | 21 | 20 | 41 | 22 | 1.444.173 | 31.765.827 | 33.210.000 |

Controllo di coerenza: regioni 20 + 26 + 22 = 68 e foreground
1.895.796 + 546.762 + 1.444.173 = 3.886.731, identici ai valori aggregati
congelati dell'intero test set (`regions=68`, `positivePixels=3886731`).

## Formule e convenzioni adottate

Convenzioni riprese **identiche** da `LOCALIZATION_CONVENTIONS.md` (Sprint 8)
e riutilizzate tramite gli helper di localizzazione gia' compilati:

- Predizione: raw bilineare con coordinate half-pixel
  `(dest + 0.5) * sourceSize / destSize - 0.5`, bordi replicati
  (`align_corners=false`). Nessuna trasformazione degli score.
- `image score = max(raw)` (come negli Sprint precedenti).
- Image AUROC: confronto del solo difetto con le **stesse 20 good**
  (good = normale, difetto = anomalia), pareggi a 0.5.
- Pixel AUROC: ROC globale su tutti i pixel del sottoinsieme (difetto + 20
  good); le good contribuiscono al background. Sweep esatto di tutti i
  valori distinti, pareggi raggruppati, integrazione trapezoidale.
- PRO: componenti connesse a 8 vicini nella maschera unita, separate per
  immagine; ogni regione pesa allo stesso modo (media non pesata della
  frazione di pixel rilevati; peso `1/area/numRegioni`).
- FPR: falsi positivi / tutti i pixel background del sottoinsieme, good
  incluse. Soglie distinte percorse con `score >= threshold`.
- AUPRO@0.30: area trapezoidale della curva PRO/FPR in `[0, 0.30]`,
  interpolazione lineare a 0.30, divisione per 0.30.

Nessuno smoothing, nessuna calibrazione, nessuna soglia fissa, nessuna
normalizzazione per immagine sono state introdotte.

### Validazione dell'implementazione di analisi

Ricalcolando le metriche aggregate dell'**intero** test set (83 immagini)
dalle stesse mappe raw, sono stati riprodotti **esattamente** i valori
congelati:

| Variante | Pixel AUROC (ricalcolata) | AUPRO@0.30 (ricalcolato) | Riferimento congelato |
| --- | --- | --- | --- |
| SPATIAL_14 | 0.95694957509359390 | 0.86534746572585690 | `metrics.txt` (match) |
| SPATIAL_28 | 0.90140233533244440 | 0.70555613590835210 | `metrics.csv` (match) |

## Tabella completa delle metriche

Delta = SPATIAL_28 - SPATIAL_14.

| Difetto | Metrica | SPATIAL_14 | SPATIAL_28 | delta(28-14) |
| --- | --- | ---: | ---: | ---: |
| broken_large | Image AUROC | 1.00000000000000000 | 1.00000000000000000 | +0.00000000000000000 |
| broken_large | Pixel AUROC | 0.97109885607028160 | 0.86892200644940820 | -0.10217684962087300 |
| broken_large | AUPRO@0.30 | 0.90923111016251470 | 0.68427862437112130 | -0.22495248579139300 |
| broken_small | Image AUROC | 0.98409090909090910 | 1.00000000000000000 | +0.01590909090909090 |
| broken_small | Pixel AUROC | 0.98598208649969380 | 0.89690411232217150 | -0.08907797417752230 |
| broken_small | AUPRO@0.30 | 0.92820710448661160 | 0.67690850947077000 | -0.25129859501584200 |
| contamination | Image AUROC | 0.97380952380952380 | 1.00000000000000000 | +0.02619047619047620 |
| contamination | Pixel AUROC | 0.94814448170603860 | 0.94622739161375950 | -0.00191709009227914 |
| contamination | AUPRO@0.30 | 0.85305518137127610 | 0.77415141202863740 | -0.07890376934263880 |

Vista compatta per difetto:

| Difetto | Image 14 | Image 28 | Pixel 14 | Pixel 28 | AUPRO 14 | AUPRO 28 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| broken_large | 1.000000 | 1.000000 | 0.971099 | 0.868922 | 0.909231 | 0.684279 |
| broken_small | 0.984091 | 1.000000 | 0.985982 | 0.896904 | 0.928207 | 0.676909 |
| contamination | 0.973810 | 1.000000 | 0.948144 | 0.946227 | 0.853055 | 0.774151 |

## Interpretazione

**Solo dato misurato.**

- Image AUROC: il 28 non peggiora mai; pareggia su `broken_large` (14 era
  gia' a 1.0) e migliora su `broken_small` (+0.0159) e `contamination`
  (+0.0262). Su questo sottoinsieme il 28 raggiunge 1.0 per tutti e tre i
  difetti.
- Pixel AUROC: il 28 peggiora in tutti e tre i gruppi. Il calo maggiore e'
  su `broken_large` (-0.1022), segue `broken_small` (-0.0891); su
  `contamination` e' quasi nullo (-0.0019).
- AUPRO@0.30: il 28 peggiora in tutti e tre i gruppi. Il calo maggiore e'
  su `broken_small` (-0.2513), segue `broken_large` (-0.2250);
  `contamination` cala meno (-0.0789).

Risposte ai quesiti, basate esclusivamente sui numeri:

1. Quale difetto beneficia del 28x28: solo a livello *image*, `contamination`
   (+0.0262) e `broken_small` (+0.0159); `broken_large` resta invariato.
2. Quale perde maggiormente: a livello *pixel* `broken_large` (-0.1022); a
   livello *AUPRO* `broken_small` (-0.2513).
3. `broken_small` mostra un vantaggio dalla maggiore granularita'? **No,
   non nelle metriche di localizzazione.** Il suo AUPRO e' il calo piu'
   grande dei tre e anche il pixel AUROC cala (-0.0891). L'unico segnale
   positivo per `broken_small` e' l'image AUROC (+0.0159).
4. Il peggioramento AUPRO del 28x28 e' concentrato in un tipo di difetto o
   generale? E' **generale nel segno** (tutti e tre calano), ma non
   uniforme in ampiezza: massimo su `broken_small` e `broken_large`,
   molto minore su `contamination`. Non e' quindi attribuibile a un solo
   tipo di difetto, ma `contamination` e' comparativamente robusto.

**Lettura plausibile (non prova causale).**

I numeri mostrano una decoupling tra livello immagine e livello
localizzazione: il 28 migliora/pareggia il ranking `max(raw)` per immagine,
ma peggiora l'allineamento spaziale (pixel AUROC e AUPRO) su tutti i difetti.
Due spiegazioni plausibili, da non attribuire allo stage CNN senza ulteriori
esperimenti:

- La griglia 28x28 ha 4x le celle (784 vs 196): l'operatore `max` su piu'
  celle ha piu' occasioni di catturare un valore estremo, il che puo'
  migliorare l'image score senza che le mappe siano spazialmente migliori.
- Le mappe 28x28 sono un artefatto di uno stage diverso; la loro struttura
  spaziale, dopo upsampling bilineare a 900x900, puo' concentrare o
  disallineare gli score rispetto alla ground truth piu' di quanto faccia il
  14x14. `contamination` sembra meno sensibile a questo effetto.

`SPATIAL_28` e' uno stage a 64 canali piu' precoce, `SPATIAL_14` a 96
canali: le differenze **non** possono essere attribuite alla sola
risoluzione spaziale.

## Note di perimetro

- Nessun file sorgente modificato; nessun training; nessun test lungo
  rieseguito; nessun commit.
- L'analisi ha riutilizzato gli helper gia' compilati in `target/` e le
  convenzioni congelate, verificando la riproduzione esatta degli aggregati
  di riferimento.
- CSV opzionale: `target/bottle-per-defect/spatial14_vs_spatial28_per_defect.csv`.
