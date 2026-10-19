# M2 spatial correspondence — risultato finale

Data: 2026-10-18  
Repository: `C:\Users\Ion\IdeaProjects\Anomalib4j`  
Branch: `main`  
HEAD: `f60040f Add controlled M2 robustness benchmark`  
Campagna: `target/ablation/m2-spatial-correspondence-20261018`  
Modello: M2 production invariato; M2-P/M2-N1/M2-G sono varianti di corrispondenza spaziale valutate separatamente.  
Commit: nessuno.

## 1. Stato campagna

La campagna è **completa**: `complete.json` contiene tutte le 15 categorie e `wall_seconds = 815.8959515999886` (13m35.90s).

Categorie: `bottle, cable, capsule, carpet, grid, hazelnut, leather, metal_nut, pill, screw, tile, toothbrush, transistor, wood, zipper`.

Verifiche eseguite sugli artifact:

- 15/15 categorie con `M2-P`, `M2-N1`, `M2-G`.
- 15/15 con quality record `status=verified`.
- 15/15 con `alpha.npy`, `beta.npy`, `calibration-z.npy`, `raw.npy`, `native-z.npy`, `correspondence.csv`, `robustness.csv`.
- `M2-N1`/`M2-G`: 15/15 con `evaluate.log` e `metrics-unified.json`.
- `M2-P`: 15/15 con `parity.json` e `metrics-retained.json`; l'assenza di `run.log` per P è attesa, non è un artifact mancante.
- Robustness: ogni `robustness.csv` contiene esattamente `n_normals × 22 = 467 × 22 = 10.274` righe; totale 30.822 righe method-level sulle 15 categorie.
- `correspondence.csv`: righe = numero di immagini test della categoria.
- Zipper era l'ultima categoria: è completa per tutti e tre i metodi, non è stata rilanciata.
- Scansione di tutti i `.log` per `Exception|ERROR|FAILED|Traceback|mismatch|Nonfinite|IllegalState|IllegalArgument`: **zero occorrenze**.
- Nessun failure di campagna; nessuna categoria rilanciata.
- Timing scorer-only già presente in `timing-0.csv`, `timing-1.csv`, `timing-2.csv` e nei rispettivi log; non è stato rilanciato.

Provenienza sorgenti:

- `source-hashes.json` contiene 127 file; 126 hash-match.
- Unico mismatch: `src/test/java/io/github/antctrlwin/anomalib4j/evaluation/SpatialCorrespondenceTest.java` (mtime `2026-10-18T21:37:07`, classe compilata `21:37:25`, fine campagna `21:47:20`).
- Tutti i file di scoring/produzione, `SpatialCorrespondence.java`, runner, robustness, timing, `LocalAblationBridge`, tool e piano hash-matchano la campagna.
- Il mismatch riguarda solo il sorgente del test, non lo scorer; i risultati non sono invalidati. Il test è stato ricompilato ed eseguito nella validazione finale (sezione 14).
- Non è stata rilevata alcuna discrepanza matematica né alcuna modifica allo scorer che renda invalidi i risultati.

## 2. M2-P parity

Tutte le 15 categorie hanno `exact_java_baseline=true`; `exact_retained_robustness_pairs` coincide con `n_normals × 22`. Gli errori massimi assoluti sono a livello floating-point (massimo `1.82e-12` raw, `1.36e-12` native-z).

| category | max abs raw error | max abs native-z error | exact retained pairs |
|---|---:|---:|:---:|
| bottle | 1.82e-12 | 1.36e-12 | 440 |
| cable | 8.88e-15 | 2.13e-14 | 1276 |
| capsule | 1.78e-14 | 1.42e-13 | 506 |
| carpet | 7.11e-15 | 2.84e-14 | 616 |
| grid | 7.11e-15 | 4.97e-14 | 462 |
| hazelnut | 4.44e-15 | 1.6e-14 | 880 |
| leather | 1.78e-14 | 8.53e-14 | 704 |
| metal_nut | 8.88e-15 | 2.49e-14 | 484 |
| pill | 1.24e-14 | 2.13e-14 | 572 |
| screw | 7.11e-15 | 2.66e-14 | 902 |
| tile | 7.11e-15 | 1.95e-14 | 726 |
| toothbrush | 2.84e-14 | 1.99e-13 | 264 |
| transistor | 2.13e-14 | 5.68e-14 | 1320 |
| wood | 5.33e-15 | 2.49e-14 | 418 |
| zipper | 1.78e-14 | 6.39e-14 | 704 |

Calibrazione: P usa la calibrazione M2 originale; N1 e G hanno calibrazione separata, ottenuta esclusivamente dai normal held-out. In tutte le 15 categorie `alpha` P, N1 e G differiscono; le righe di `calibration-z` corrispondono al numero di held-out normal (esempi: bottle 42, cable 45, toothbrush 12).

## 3. Quality all-15 P/N1/G

### Image AUROC

| category | M2-P | M2-N1 | M2-G |
|---|---:|---:|---:|
| bottle | 1.0000 | 1.0000 | 1.0000 |
| cable | 0.8182 | 0.8171 | 0.7768 |
| capsule | 0.8472 | 0.8249 | 0.8249 |
| carpet | 0.9462 | 0.9470 | 0.9510 |
| grid | 0.9616 | 0.9574 | 0.7878 |
| hazelnut | 0.6175 | 0.6186 | 0.7371 |
| leather | 0.9983 | 0.9983 | 0.9925 |
| metal_nut | 0.8456 | 0.7674 | 0.7346 |
| pill | 0.8129 | 0.7881 | 0.8025 |
| screw | 0.5649 | 0.5807 | 0.5759 |
| tile | 0.9805 | 0.9820 | 0.9798 |
| toothbrush | 0.9028 | 0.8694 | 0.8694 |
| transistor | 0.9067 | 0.8846 | 0.8063 |
| wood | 0.9149 | 0.9140 | 0.8930 |
| zipper | 0.9598 | 0.9533 | 0.9404 |

### Pixel AUROC

| category | M2-P | M2-N1 | M2-G |
|---|---:|---:|---:|
| bottle | 0.9757 | 0.9757 | 0.9741 |
| cable | 0.8589 | 0.8876 | 0.8454 |
| capsule | 0.9680 | 0.9688 | 0.9682 |
| carpet | 0.9856 | 0.9859 | 0.9866 |
| grid | 0.9458 | 0.9413 | 0.9268 |
| hazelnut | 0.8959 | 0.8983 | 0.9008 |
| leather | 0.9914 | 0.9914 | 0.9929 |
| metal_nut | 0.8493 | 0.8045 | 0.7666 |
| pill | 0.8873 | 0.8904 | 0.8920 |
| screw | 0.8640 | 0.8547 | 0.8134 |
| tile | 0.9404 | 0.9411 | 0.9451 |
| toothbrush | 0.9593 | 0.9545 | 0.9568 |
| transistor | 0.9527 | 0.9524 | 0.8778 |
| wood | 0.9301 | 0.9317 | 0.9349 |
| zipper | 0.9610 | 0.9585 | 0.9561 |

### AUPRO@0.30

| category | M2-P | M2-N1 | M2-G |
|---|---:|---:|---:|
| bottle | 0.9157 | 0.9153 | 0.9099 |
| cable | 0.6796 | 0.7271 | 0.6726 |
| capsule | 0.8144 | 0.8241 | 0.8268 |
| carpet | 0.9181 | 0.9193 | 0.9242 |
| grid | 0.8590 | 0.8509 | 0.7937 |
| hazelnut | 0.7813 | 0.7914 | 0.8030 |
| leather | 0.9613 | 0.9618 | 0.9700 |
| metal_nut | 0.6242 | 0.5747 | 0.5354 |
| pill | 0.8222 | 0.8231 | 0.8268 |
| screw | 0.6021 | 0.5772 | 0.4620 |
| tile | 0.8492 | 0.8508 | 0.8665 |
| toothbrush | 0.6903 | 0.6809 | 0.6974 |
| transistor | 0.8484 | 0.8678 | 0.7591 |
| wood | 0.8403 | 0.8443 | 0.8544 |
| zipper | 0.8821 | 0.8740 | 0.8693 |

## 4. Macro quality

Le medie sono medie semplici sulle categorie (peso uguale); le mediane sono mediane delle 15 categorie.

| group | metric | statistic | M2-P | M2-N1 | M2-G |
|---|---|---|---:|---:|---:|
| all | Image AUROC | mean | 0.8718 | 0.8602 | 0.8448 |
| all | Image AUROC | median | 0.9067 | 0.8846 | 0.8249 |
| all | Pixel AUROC | mean | 0.9310 | 0.9291 | 0.9158 |
| all | Pixel AUROC | median | 0.9458 | 0.9413 | 0.9349 |
| all | AUPRO@0.30 | mean | 0.8059 | 0.8055 | 0.7847 |
| all | AUPRO@0.30 | median | 0.8403 | 0.8443 | 0.8268 |
| texture | Image AUROC | mean | 0.9603 | 0.9597 | 0.9208 |
| texture | Image AUROC | median | 0.9616 | 0.9574 | 0.9510 |
| texture | Pixel AUROC | mean | 0.9587 | 0.9583 | 0.9573 |
| texture | Pixel AUROC | median | 0.9458 | 0.9413 | 0.9451 |
| texture | AUPRO@0.30 | mean | 0.8856 | 0.8854 | 0.8818 |
| texture | AUPRO@0.30 | median | 0.8590 | 0.8509 | 0.8665 |
| object | Image AUROC | mean | 0.8276 | 0.8104 | 0.8068 |
| object | Image AUROC | median | 0.8464 | 0.8210 | 0.8044 |
| object | Pixel AUROC | mean | 0.9172 | 0.9145 | 0.8951 |
| object | Pixel AUROC | median | 0.9243 | 0.9254 | 0.8964 |
| object | AUPRO@0.30 | mean | 0.7660 | 0.7655 | 0.7362 |
| object | AUPRO@0.30 | median | 0.7979 | 0.8073 | 0.7810 |

Sintesi delle differenze rispetto a P:

- N1: Image AUROC media `-0.0116`, Pixel AUROC `-0.0019`, AUPRO `-0.0004`. Mediane: Image `-0.0011`, Pixel `+0.0000`, AUPRO `+0.0009`.
- G: Image AUROC media `-0.0270`, Pixel AUROC `-0.0152`, AUPRO `-0.0212`. Mediane: Image `-0.0194`, Pixel `-0.0016`, AUPRO `+0.0045`.

La mediana AUPRO di G è leggermente superiore a P, ma la media e le singole categorie mostrano perdite gravi: la mediana nasconde la coda negativa.

Texture: `carpet, grid, leather, tile, wood`.

## 5. Texture vs object

- N1 lascia la texture praticamente invariata: Image `0.9597` vs `0.9603`, Pixel `0.9583` vs `0.9587`, AUPRO `0.8854` vs `0.8856`. La perdita N1 è concentrata sugli oggetti, soprattutto Image AUROC (`0.8104` vs `0.8276`, `-0.0172`), mentre Pixel (`-0.0027`) e AUPRO (`-0.0005`) restano quasi identici.
- G peggiora la texture in Image (`0.9208` vs `0.9603`) e marginalmente Pixel/AUPRO; sugli oggetti perde `-0.0208` Image, `-0.0221` Pixel e `-0.0298` AUPRO. Le perdite object sono dominate da metal_nut, transistor, grid e screw.
- La distinzione texture/object non cambia l'ordinamento di robustezza: N1 è sempre intermedio tra P e G, ma la qualità object di G è troppo compromessa per un uso generale.

## 6. Translation robustness

Delta = aumento paired dello score native-Z rispetto all'immagine originale (unità Z; più alto = peggiore). I valori sono medie equal-category delle statistiche per categoria. `exceed.` = frazione che supera il riferimento diagnostico (massimo native max-Z dei normal held-out); **non è un FPR di produzione**. `int` = solo pixel interni, esclusi i bordi.

### Translation X

| method | shift (px) | median full | mean full | p90 full | p95 full | exceed. | median int | mean int | p90 int | p95 int |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| M2-P | -8.00 | 29.591 | 30.113 | 38.985 | 41.507 | 0.992 | 20.969 | 21.121 | 27.690 | 29.740 |
| M2-P | -4.00 | 6.229 | 6.240 | 10.975 | 12.675 | 0.762 | 4.930 | 5.177 | 9.035 | 10.366 |
| M2-P | -2.00 | 1.245 | 1.283 | 4.017 | 4.776 | 0.518 | 1.279 | 1.222 | 3.474 | 4.048 |
| M2-P | +2.00 | 1.416 | 1.616 | 4.394 | 5.880 | 0.532 | 1.278 | 1.313 | 3.880 | 5.143 |
| M2-P | +4.00 | 7.665 | 7.440 | 12.308 | 13.508 | 0.757 | 4.957 | 4.850 | 8.329 | 9.724 |
| M2-P | +8.00 | 33.147 | 33.584 | 42.583 | 45.361 | 0.958 | 17.971 | 18.109 | 25.439 | 28.323 |
| M2-N1 | -8.00 | 17.096 | 17.119 | 22.892 | 26.464 | 0.985 | 10.551 | 10.410 | 13.800 | 14.364 |
| M2-N1 | -4.00 | 5.159 | 5.166 | 8.404 | 9.985 | 0.728 | 4.020 | 4.094 | 7.074 | 8.340 |
| M2-N1 | -2.00 | 1.021 | 1.130 | 3.914 | 4.816 | 0.504 | 0.999 | 0.993 | 3.438 | 4.126 |
| M2-N1 | +2.00 | 1.273 | 1.315 | 4.144 | 5.402 | 0.516 | 0.968 | 1.051 | 3.530 | 4.617 |
| M2-N1 | +4.00 | 5.772 | 5.738 | 9.622 | 10.782 | 0.732 | 3.863 | 3.718 | 6.566 | 7.248 |
| M2-N1 | +8.00 | 18.453 | 18.984 | 25.035 | 26.574 | 0.942 | 8.761 | 8.502 | 11.473 | 12.145 |
| M2-G | -8.00 | 10.429 | 10.227 | 13.640 | 14.272 | 0.931 | 6.740 | 6.631 | 9.445 | 10.213 |
| M2-G | -4.00 | 4.181 | 4.046 | 7.021 | 7.835 | 0.648 | 2.837 | 2.905 | 5.266 | 6.116 |
| M2-G | -2.00 | 0.822 | 0.904 | 3.146 | 3.954 | 0.446 | 0.797 | 0.794 | 2.925 | 3.436 |
| M2-G | +2.00 | 0.825 | 1.051 | 3.513 | 4.614 | 0.434 | 0.654 | 0.753 | 2.919 | 3.755 |
| M2-G | +4.00 | 4.401 | 4.415 | 7.483 | 8.397 | 0.648 | 2.600 | 2.499 | 4.876 | 5.417 |
| M2-G | +8.00 | 11.169 | 10.955 | 14.345 | 15.014 | 0.870 | 5.558 | 5.430 | 7.950 | 8.483 |

### Translation Y

| method | shift (px) | median full | mean full | p90 full | p95 full | exceed. | median int | mean int | p90 int | p95 int |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| M2-P | -8.00 | 26.051 | 25.496 | 32.136 | 34.082 | 0.991 | 17.150 | 16.866 | 21.783 | 23.063 |
| M2-P | -4.00 | 6.657 | 6.687 | 11.128 | 12.383 | 0.745 | 3.970 | 4.295 | 7.666 | 9.353 |
| M2-P | -2.00 | 1.450 | 1.395 | 4.243 | 5.365 | 0.523 | 0.953 | 1.063 | 3.607 | 4.778 |
| M2-P | +2.00 | 1.559 | 1.872 | 5.156 | 6.607 | 0.551 | 0.925 | 0.997 | 3.455 | 4.478 |
| M2-P | +4.00 | 8.245 | 8.774 | 14.564 | 16.952 | 0.792 | 4.725 | 4.940 | 8.606 | 10.217 |
| M2-P | +8.00 | 36.767 | 37.050 | 46.030 | 48.996 | 0.935 | 20.453 | 20.282 | 26.106 | 27.834 |
| M2-N1 | -8.00 | 17.188 | 16.748 | 21.019 | 22.188 | 0.975 | 9.069 | 8.753 | 11.560 | 12.370 |
| M2-N1 | -4.00 | 6.500 | 6.121 | 9.926 | 10.719 | 0.710 | 3.198 | 3.120 | 5.588 | 6.517 |
| M2-N1 | -2.00 | 1.230 | 1.258 | 3.886 | 4.953 | 0.488 | 0.767 | 0.786 | 3.107 | 3.964 |
| M2-N1 | +2.00 | 1.624 | 1.799 | 4.798 | 6.113 | 0.529 | 0.843 | 0.823 | 3.168 | 3.852 |
| M2-N1 | +4.00 | 7.566 | 8.063 | 13.009 | 14.729 | 0.758 | 3.841 | 4.075 | 7.115 | 8.283 |
| M2-N1 | +8.00 | 26.046 | 26.182 | 33.453 | 34.635 | 0.929 | 9.847 | 9.867 | 12.932 | 13.812 |
| M2-G | -8.00 | 9.389 | 9.169 | 12.105 | 12.924 | 0.908 | 6.121 | 5.886 | 8.210 | 9.147 |
| M2-G | -4.00 | 5.502 | 5.266 | 7.945 | 8.835 | 0.646 | 2.563 | 2.381 | 4.400 | 5.277 |
| M2-G | -2.00 | 1.294 | 1.283 | 3.694 | 4.632 | 0.467 | 0.697 | 0.725 | 2.929 | 3.477 |
| M2-G | +2.00 | 1.293 | 1.497 | 3.903 | 5.015 | 0.439 | 0.652 | 0.539 | 2.304 | 2.968 |
| M2-G | +4.00 | 4.963 | 4.882 | 8.036 | 9.238 | 0.678 | 2.299 | 2.202 | 4.332 | 4.976 |
| M2-G | +8.00 | 10.782 | 10.624 | 13.705 | 14.566 | 0.866 | 5.683 | 5.513 | 7.841 | 8.465 |

Lettura: a ±2 px i tre metodi sono vicini; a ±8 px N1 dimezza circa il delta mediano rispetto a P, G lo riduce di circa il 60–70%. La traslazione resta la famiglia meno critica rispetto a rotazione e scala.

## 7. Rotation robustness

| method | angle (°) | median full | mean full | p90 full | p95 full | exceed. | median int | mean int | p90 int | p95 int |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| M2-P | -20.00 | 71.949 | 71.961 | 83.576 | 86.676 | 1.000 | 67.050 | 66.547 | 77.459 | 80.910 |
| M2-P | -10.00 | 50.833 | 50.961 | 61.509 | 64.969 | 0.993 | 28.730 | 28.719 | 36.802 | 38.622 |
| M2-P | -5.00 | 14.822 | 14.729 | 20.598 | 22.135 | 0.942 | 7.904 | 8.094 | 13.038 | 14.364 |
| M2-P | +5.00 | 17.780 | 18.354 | 26.239 | 28.670 | 0.958 | 8.574 | 8.721 | 13.457 | 14.642 |
| M2-P | +10.00 | 49.148 | 48.551 | 57.023 | 60.984 | 1.000 | 26.021 | 25.774 | 31.961 | 33.782 |
| M2-P | +20.00 | 82.280 | 83.346 | 96.651 | 102.184 | 1.000 | 68.847 | 69.112 | 80.437 | 84.533 |
| M2-N1 | -20.00 | 22.906 | 22.999 | 27.567 | 29.061 | 0.977 | 14.964 | 14.746 | 18.553 | 19.172 |
| M2-N1 | -10.00 | 14.446 | 14.144 | 18.465 | 19.725 | 0.990 | 9.226 | 9.040 | 12.238 | 13.080 |
| M2-N1 | -5.00 | 9.448 | 9.229 | 13.146 | 14.343 | 0.923 | 4.702 | 4.598 | 7.543 | 8.436 |
| M2-N1 | +5.00 | 11.446 | 11.271 | 15.425 | 16.535 | 0.956 | 4.881 | 4.785 | 8.007 | 8.866 |
| M2-N1 | +10.00 | 13.689 | 13.209 | 17.877 | 18.769 | 1.000 | 8.962 | 8.475 | 11.652 | 12.382 |
| M2-N1 | +20.00 | 30.006 | 30.269 | 36.614 | 38.730 | 1.000 | 16.936 | 16.701 | 20.279 | 21.335 |
| M2-G | -20.00 | 11.265 | 10.957 | 14.009 | 14.552 | 0.914 | 8.102 | 7.824 | 10.344 | 10.965 |
| M2-G | -10.00 | 8.817 | 8.785 | 11.962 | 13.092 | 0.959 | 6.609 | 6.463 | 9.323 | 10.009 |
| M2-G | -5.00 | 6.089 | 5.777 | 8.470 | 9.316 | 0.841 | 3.271 | 3.106 | 5.375 | 6.094 |
| M2-G | +5.00 | 7.414 | 7.182 | 10.015 | 10.823 | 0.858 | 3.528 | 3.336 | 5.867 | 6.563 |
| M2-G | +10.00 | 9.274 | 8.968 | 12.216 | 12.884 | 0.956 | 6.945 | 6.558 | 9.344 | 9.895 |
| M2-G | +20.00 | 10.398 | 10.090 | 13.089 | 13.989 | 0.940 | 9.126 | 8.844 | 11.279 | 12.004 |

Lettura: la rotazione è la famiglia dove P è più fragile. A ±20° N1 scende da `72–82` a `23–30` (mediana), G a `10.4–11.3`; a ±10° N1 è `13.7–14.4`, G `8.8–9.3`, P `49–51`. L'exceedance diagnostica resta alta anche per N1/G alle rotazioni grandi; non va interpretata come FPR.

## 8. Scale robustness

| method | scale | median full | mean full | p90 full | p95 full | exceed. | median int | mean int | p90 int | p95 int |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| M2-P | +0.90 | 62.983 | 64.134 | 75.574 | 79.148 | 1.000 | 36.475 | 36.742 | 45.081 | 47.576 |
| M2-P | +0.95 | 18.463 | 19.168 | 26.258 | 28.211 | 0.988 | 9.590 | 10.278 | 15.400 | 17.070 |
| M2-P | +1.05 | 9.521 | 10.063 | 15.893 | 17.846 | 0.727 | 5.242 | 5.359 | 9.046 | 10.602 |
| M2-P | +1.10 | 45.256 | 46.419 | 56.433 | 65.088 | 0.769 | 17.736 | 18.334 | 24.415 | 27.527 |
| M2-N1 | +0.90 | 44.610 | 45.100 | 52.360 | 55.915 | 1.000 | 15.999 | 16.107 | 20.210 | 21.587 |
| M2-N1 | +0.95 | 15.333 | 15.506 | 20.474 | 22.301 | 0.987 | 7.025 | 7.104 | 10.398 | 11.228 |
| M2-N1 | +1.05 | 8.601 | 9.032 | 14.025 | 16.170 | 0.651 | 4.024 | 4.029 | 6.898 | 7.745 |
| M2-N1 | +1.10 | 22.393 | 22.070 | 27.147 | 28.432 | 0.678 | 5.431 | 5.589 | 9.737 | 10.894 |
| M2-G | +0.90 | 16.620 | 16.156 | 19.270 | 20.082 | 0.998 | 10.934 | 10.918 | 14.060 | 15.273 |
| M2-G | +0.95 | 9.984 | 9.877 | 13.434 | 14.166 | 0.923 | 5.651 | 5.625 | 8.197 | 8.986 |
| M2-G | +1.05 | 6.672 | 6.418 | 9.163 | 10.037 | 0.618 | 3.655 | 3.488 | 5.910 | 6.345 |
| M2-G | +1.10 | 8.819 | 8.470 | 11.851 | 12.908 | 0.631 | 3.792 | 3.794 | 6.881 | 7.740 |

Lettura: a `0.90` N1 migliora P di circa il 29% (`44.6` vs `63.0`), G di circa il 74% (`16.6`); a `1.10` N1 è `22.4` vs `45.3`, G `8.8`. A `1.05` il vantaggio di N1 è piccolo (`8.6` vs `9.5`). La scala rimane difficile anche per N1 a `0.90`.

## 9. Full-grid vs interior

La tabella seguente riassume le medie delle statistiche macro sui livelli di ciascuna famiglia. I valori per livello sono nelle sezioni 6–8 (colonne `int`).

| method | family | median | mean | p90 | p95 | exceedance | interior median | interior mean | interior p90 | interior p95 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| M2-P | TRANSLATION_X | 13.216 | 13.380 | 18.877 | 20.618 | 0.753 | 8.564 | 8.632 | 12.974 | 14.557 |
| M2-P | TRANSLATION_Y | 13.455 | 13.546 | 18.876 | 20.731 | 0.756 | 8.029 | 8.074 | 11.871 | 13.287 |
| M2-P | ROTATION | 47.802 | 47.983 | 57.599 | 60.936 | 0.982 | 34.521 | 34.495 | 42.192 | 44.476 |
| M2-P | SCALE | 34.055 | 34.946 | 43.539 | 47.574 | 0.871 | 17.261 | 17.678 | 23.486 | 25.694 |
| M2-N1 | TRANSLATION_X | 8.129 | 8.242 | 12.335 | 14.004 | 0.734 | 4.861 | 4.795 | 7.647 | 8.473 |
| M2-N1 | TRANSLATION_Y | 10.026 | 10.029 | 14.349 | 15.556 | 0.731 | 4.594 | 4.571 | 7.245 | 8.133 |
| M2-N1 | ROTATION | 16.990 | 16.853 | 21.516 | 22.861 | 0.974 | 9.945 | 9.724 | 13.045 | 13.878 |
| M2-N1 | SCALE | 22.734 | 22.927 | 28.502 | 30.705 | 0.829 | 8.120 | 8.207 | 11.811 | 12.864 |
| M2-G | TRANSLATION_X | 5.305 | 5.266 | 8.191 | 9.014 | 0.663 | 3.198 | 3.169 | 5.564 | 6.237 |
| M2-G | TRANSLATION_Y | 5.537 | 5.453 | 8.231 | 9.202 | 0.667 | 3.003 | 2.874 | 5.003 | 5.718 |
| M2-G | ROTATION | 8.876 | 8.627 | 11.627 | 12.443 | 0.911 | 6.263 | 6.022 | 8.589 | 9.255 |
| M2-G | SCALE | 10.524 | 10.230 | 13.430 | 14.298 | 0.793 | 6.008 | 5.956 | 8.762 | 9.586 |

Il full-grid include i bordi e gonfia i delta; l'interior li riduce sensibilmente (ad esempio rotazione ±20°: P `67–69`, N1 `15–17`, G `8–9`; scala 0.90: P `36.5`, N1 `16.0`, G `10.9`). L'ordinamento P > N1 > G resta invariato: la conclusione non dipende dagli artifact di bordo.

## 10. Displacement diagnostics

Le statistiche sono per-immagine, aggregate su tutte le categorie. Quality test: 1.725 immagini per metodo. Robustness: 10.274 immagini normali trasformate per metodo.

| set | method | n | same-position mean | same median | same p05 | same p95 | disp mean | disp median | disp p90 | disp p95 | disp max |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| quality | M2-P | 1725 | 1.000 | 1.000 | 1.000 | 1.000 | 0.000 | 0.000 | 0.000 | 0.000 | 0.000 |
| quality | M2-N1 | 1725 | 0.544 | 0.531 | 0.214 | 0.934 | 0.511 | 0.487 | 0.891 | 0.922 | 1.017 |
| quality | M2-G | 1725 | 0.452 | 0.429 | 0.102 | 0.903 | 2.112 | 1.755 | 4.105 | 4.522 | 6.627 |
| robustness | M2-P | 10274 | 1.000 | 1.000 | 1.000 | 1.000 | 0.000 | 0.000 | 0.000 | 0.000 | 0.000 |
| robustness | M2-N1 | 10274 | 0.422 | 0.352 | 0.128 | 0.908 | 0.650 | 0.706 | 0.992 | 1.042 | 1.187 |
| robustness | M2-G | 10274 | 0.316 | 0.245 | 0.036 | 0.832 | 2.682 | 2.814 | 4.562 | 4.936 | 6.406 |

- P è per costruzione sempre sulla stessa posizione.
- N1 cambia riferimento per circa metà delle immagini quality (`same = 0.544`) e per il `42%` delle immagini robustness; lo spostamento medio è circa `0.5–0.7` celle.
- G cambia riferimento più spesso (`same = 0.452` quality, `0.316` robustness) e con spostamenti molto maggiori (`2.1–2.7` celle in media, p95 `4.5–4.9`).
- **Concentrazione dei riferimenti per G:** non disponibile. Gli artifact persistono solo la frazione same-position e il displacement medio per immagine; `raw.npy` contiene gli score, non l'indice argmin. Non è stato ricalcolato nulla, per non rilanciare la campagna. Questa è una limitazione del report, non un errore della campagna.
- Queste diagnostiche non hanno influenzato calibrazione o scoring.

## 11. Timing scorer-only

Timing matched, 3 JVM fresche, stessi 8 descrittori held-out di Bottle, 2 s warmup per metodo, 9 round interleaved da 64 mappe per metodo, ordine ruotato. Include generazione della mappa raw (validazione, allocazione, argmin); esclude Z/normalizzazione/CNN/I/O. Unità: µs/mappa. Nessun JMH e nessuna pipeline completa.

| fork | method | n | median | mean | min | max |
|---|---:|---:|---:|---:|---:|---:|
| 0 | M2-P | 9 | 28.916 | 30.888 | 27.394 | 34.991 |
| 0 | M2-N1 | 9 | 135.452 | 137.141 | 130.066 | 148.287 |
| 0 | M2-G | 9 | 2874.912 | 2845.786 | 2673.787 | 2949.761 |
| 1 | M2-P | 9 | 29.422 | 30.393 | 27.830 | 35.958 |
| 1 | M2-N1 | 9 | 135.972 | 141.037 | 130.430 | 154.566 |
| 1 | M2-G | 9 | 2922.238 | 2911.614 | 2787.245 | 2991.669 |
| 2 | M2-P | 9 | 18.456 | 18.899 | 17.791 | 23.481 |
| 2 | M2-N1 | 9 | 85.727 | 85.981 | 84.944 | 87.836 |
| 2 | M2-G | 9 | 1720.614 | 1727.214 | 1715.912 | 1769.188 |
| pooled | M2-P | 27 | 28.403 | 26.727 | 17.791 | 35.958 |
| pooled | M2-N1 | 27 | 131.870 | 121.387 | 84.944 | 154.566 |
| pooled | M2-G | 27 | 2859.731 | 2494.871 | 1715.912 | 2991.669 |

Sui mediani pooled: N1 è circa `4.6×` P; G circa `100.7×` P. La fork 2 è nettamente più veloce (JVM/termica), quindi vanno riportati sia i valori per fork sia il pooled; il rapporto N1/P resta circa `4.6×`, mentre G/P scende a circa `93×` sulla fork 2. Il costo di N1 è reale ma contenuto; G è oltre due ordini di grandezza e non è realistico per produzione.

## 12. Categorie aiutate/peggiorate da N1

Delta = media delle tre metriche quality rispetto a P (unità metriche, non Z).

| method | helped categories | worsened categories |
|---|---|---|
| M2-N1 | cable (+0.0250), hazelnut (+0.0045), wood (+0.0016), tile (+0.0013), carpet (+0.0008), leather (+0.0002) | metal_nut (-0.0575), toothbrush (-0.0158), pill (-0.0069), screw (-0.0061), zipper (-0.0057), grid (-0.0056), capsule (-0.0040), transistor (-0.0010), bottle (-0.0001) |

N1 aiuta 6 categorie e ne peggiora 9. Il caso negativo dominante è `metal_nut` (`-0.0575`), seguito da `toothbrush`, `pill`, `screw`, `zipper`, `grid`. Il caso positivo principale è `cable` (`+0.0250`), con guadagni più piccoli su hazelnut, wood, tile, carpet e leather.

Sintesi per metrica (N1 vs P): Image mean `-0.0116`, Pixel mean `-0.0019`, AUPRO mean `-0.0004`; mediane Image `-0.0011`, Pixel `+0.0000`, AUPRO `+0.0009`.

## 13. Categorie aiutate/peggiorate da G

| method | helped categories | worsened categories |
|---|---|---|
| M2-G | hazelnut (+0.0488), tile (+0.0071), carpet (+0.0039), leather (+0.0015) | metal_nut (-0.0942), transistor (-0.0882), grid (-0.0860), screw (-0.0599), cable (-0.0207), zipper (-0.0124), toothbrush (-0.0096), capsule (-0.0033), bottle (-0.0025), wood (-0.0010), pill (-0.0004) |

G aiuta 4 categorie e ne peggiora 11. Le perdite sono gravi e concentrate su `metal_nut`, `transistor`, `grid`, `screw`; anche `cable` perde `-0.0207` pur essendo una categoria dove N1 guadagna. Il guadagno su hazelnut (`+0.0488`) non compensa la perdita media.

Riferimenti diagnostici e exceedance sui normal originali:

| category | n held-out | ref P | ref N1 | ref G | orig exc P | orig exc N1 | orig exc G |
|---|---:|---:|---:|---:|---:|---:|---:|
| bottle | 20 | 6.1464 | 6.1464 | 6.1423 | 0.4500 | 0.4000 | 0.4000 |
| cable | 58 | 6.0304 | 5.8962 | 5.9091 | 0.3103 | 0.1897 | 0.1379 |
| capsule | 23 | 6.4172 | 6.3403 | 6.2936 | 0.3913 | 0.3913 | 0.3043 |
| carpet | 28 | 6.1375 | 6.1905 | 5.9097 | 0.1071 | 0.1071 | 0.1786 |
| grid | 21 | 5.3840 | 5.3984 | 5.2742 | 0.3810 | 0.3810 | 0.3333 |
| hazelnut | 40 | 7.8583 | 7.8499 | 7.3965 | 0.4500 | 0.3500 | 0.0750 |
| leather | 32 | 6.1756 | 6.0325 | 6.1574 | 0.2812 | 0.3750 | 0.2812 |
| metal_nut | 22 | 6.0552 | 6.0552 | 5.5745 | 0.4545 | 0.3636 | 0.3182 |
| pill | 26 | 6.5206 | 5.8981 | 5.8981 | 0.3846 | 0.3846 | 0.2308 |
| screw | 41 | 5.8236 | 5.7253 | 5.6188 | 0.2927 | 0.2195 | 0.1707 |
| tile | 33 | 5.4483 | 5.4806 | 5.3555 | 0.3636 | 0.3333 | 0.3333 |
| toothbrush | 12 | 3.1518 | 3.0644 | 3.0644 | 0.9167 | 0.9167 | 0.9167 |
| transistor | 60 | 5.6389 | 5.8210 | 5.0193 | 0.2500 | 0.3500 | 0.3167 |
| wood | 19 | 5.8717 | 5.8055 | 5.7973 | 0.4211 | 0.4737 | 0.4737 |
| zipper | 32 | 5.4952 | 5.4047 | 5.2533 | 0.3125 | 0.2812 | 0.3438 |

L'`original_exceedance` è la frazione di normal held-out che supera il massimo di calibrazione; **non è un FPR di produzione**. G abbassa questo valore in diverse categorie (hazelnut `0.450 → 0.075`, cable `0.310 → 0.138`, pill `0.385 → 0.231`, screw `0.293 → 0.171`, metal_nut `0.455 → 0.318`), ma lo alza in altre (carpet, zipper, wood). Il dato va letto insieme alla perdita di quality: la combinazione è compatibile con ridotta sensibilità/mascheramento in alcune categorie, non è una prova isolata. La quality supervisionata (sezione 3) mostra direttamente le perdite.

## 14. Test

Stato precedente all'interruzione: 133 test Java e 21 test Python passavano.

Validazione finale (solo controlli necessari; nessuna campagna rieseguita):

- `mvn -q -Dtest=SpatialCorrespondenceTest test` → **10 test, 0 failure, 0 error** (Surefire).
- `python -m pytest tools -q --basetemp=...` → **30 passed**, 2 warning di deprecazione `click`/`typer` (non failure).
- `git diff --check` → exit `0`; unico messaggio: warning CRLF su `LocalAblationBridge.java`, non un errore.
- Nessuna modifica a M2 production, M2-P, M2-N1, M2-G o allo scorer è stata effettuata in questa sessione.
- Il test `SpatialCorrespondenceTest` copre: identità bit-level P/M2, clipping 3×3 su angoli/bordi/interni, argmin locale senza spostamento dell'output, argmin globale all'angolo opposto, scala anisotropa del riferimento selezionato, calibrazione alla posizione query, tie-break row-major, riferimento singleton, calibrazione solo da held-out e rifiuto di query non finite.

## 15. Durata campagna

`complete.json`: `wall_seconds = 815.8959515999886`, cioè **13m35.90s**. Il timing scorer-only è un esperimento separato e non è incluso in questo wall time. La durata del report non è inclusa.

## 16. Trade-off robustness vs quality

Sintesi (famiglie: medie dei mediani macro; quality: delta medio sulle 15 categorie):

| method | rot median | rot Δ | scale median | scale Δ | TX median | TX Δ | TY median | TY Δ | Image Δ | Pixel Δ | AUPRO Δ |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| M2-P | 47.802 | — | 34.055 | — | 13.216 | — | 13.455 | — | — | — | — |
| M2-N1 | 16.990 | -64.5% | 22.734 | -33.3% | 8.129 | -38.5% | 10.026 | -25.5% | -0.0116 | -0.0019 | -0.0004 |
| M2-G | 8.876 | -81.4% | 10.524 | -69.1% | 5.305 | -59.9% | 5.537 | -58.8% | -0.0270 | -0.0152 | -0.0212 |

- N1 compra una riduzione molto grande dei delta geometrici con una perdita macro quasi nulla su Pixel/AUPRO e modesta su Image. Il costo è però non uniforme: metal_nut perde molto, mentre cable guadagna.
- G compra ulteriore robustezza, ma a un costo quality sostanziale e non distribuito: 11/15 categorie peggiorano, con perdite severe su metal_nut/transistor/grid/screw. Non è un trade-off accettabile per un detector general-purpose.
- Timing: N1 ≈ `4.6×` P; G ≈ `100.7×` P (pooled median). N1 è il solo dei due con un costo compatibile con un candidato successivo.
- I delta di robustezza sono in unità Z e non sono direttamente convertibili in punti di AUROC; il confronto va letto come ordine di grandezza + comportamento per categoria, non come tasso di scambio esatto.

## 17. Risposte Q1–Q4

### Q1 — Rilassare la corrispondenza spaziale migliora davvero la robustezza a rotazione/scala?

**Sì.** Il miglioramento è monotono P → N1 → G ed è grande:

- rotazione: mediana macro `47.80 → 16.99 → 8.88`;
- scala: `34.06 → 22.73 → 10.52`;
- traslazione X: `13.22 → 8.13 → 5.31`; Y: `13.45 → 10.03 → 5.54`.

N1 riduce il delta mediano di rotazione del `64.5%`, scala del `33.3%`, traslazione X del `38.5%` e Y del `25.5%`. G riduce ulteriormente (`81.4%`, `69.1%`, `59.9%`, `58.8%`), ma con il costo quality descritto. La risposta riguarda la robustezza geometrica, non la qualità.

### Q2 — M2-N1 conserva la qualità di anomaly detection/localization?

**Sì a livello macro, con eccezioni di categoria.** Pixel AUROC medio `-0.0019`, AUPRO `-0.0004`, Image AUROC `-0.0116`; mediane Image `-0.0011`, Pixel `+0.0000`, AUPRO `+0.0009`. La localizzazione è quindi essenzialmente preservata; l'Image AUROC perde qualcosa. Non è uniforme: `metal_nut -0.0575` (media delle tre metriche), `toothbrush -0.0158`, `pill -0.0069`, `screw -0.0061`; dall'altro lato `cable +0.0250`, `hazelnut +0.0045`. La risposta corretta è: **sì come media, no come garanzia per ogni categoria**. Servono guardrail per categoria, in primis metal_nut.

### Q3 — M2-G diventa troppo permissivo e nasconde anomalie reali?

**Sì, il rischio è concreto e i dati lo supportano.** G perde in media `-0.0270` Image, `-0.0152` Pixel e `-0.0212` AUPRO; 11/15 categorie peggiorano, con `metal_nut -0.0942`, `transistor -0.0882`, `grid -0.0860`, `screw -0.0599`. Inoltre l'exceedance sui normal originali scende in diverse categorie (hazelnut `0.450 → 0.075`, cable `0.310 → 0.138`, pill `0.385 → 0.231`, screw `0.293 → 0.171`), coerente con una ridotta sensibilità. L'exceedance diagnostica non è un FPR e non prova da sola il mascheramento, ma la perdita quality supervisionata è diretta. **G non va adottato.**

### Q4 — Quale UNA direzione è meglio supportata dai dati?

**Adottare/raffinare la corrispondenza locale (M2-N1).** È l'unica direzione che insieme:

1. riduce in modo sostanziale rotazione/scala/traslazione;
2. mantiene Pixel AUROC e AUPRO macro praticamente invariati;
3. ha un costo di scoring circa `4.6×` P, non `100×`.

Mantenere M2-P lascia una fragilità geometrica troppo grande; adottare M2-G costa troppa qualità e rischia mascheramento; passare subito a canonicalizzazione/allineamento geometrico non è supportato da questi dati, che mostrano un beneficio già ottenibile con il solo vicinato locale. Corrispondenze strutturate/orbite restano un'opzione successiva se il raffinamento di N1 non basta, non la direzione primaria.

## 18. Raccomandazione finale (una sola)

**Promuovere M2-N1 (vicinato locale 3×3) come candidato successivo e raffinarlo con guardrail per categoria — in particolare metal_nut — mantenendo M2-P come riferimento; non adottare M2-G e non passare ora a canonicalizzazione.**

Nessun modello è stato modificato sulla base di questi dati. Nessun commit è stato creato.
