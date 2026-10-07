# SPATIAL_14 positional raw-score calibration

Esperimento **in-sample positional calibration**: dopo il normale training,
gli stessi 209 training-good vengono ricodificati e valutati con i filtri
compilati congelati. Welford calcola media e deviazione standard campionaria
(N-1) separatamente sulle 196 posizioni. I test non contribuiscono alle stime.

Il floor di sigma e **1e-6 nelle unita del raw score**, applicato alla deviazione
standard, non alla varianza. E un parametro distinto dal floor delle statistiche
VSA (che resta 1e-8). Vengono conservate anche le sigma prima del floor e il
numero di posizioni interessate, senza cambiare alcun parametro del modello.

`z[p] = (raw[p] - mu[p]) / sigma[p]`, senza clamp o trasformazioni successive.
Image score = max(z). Per localizzazione si interpola bilinearmente la mappa z
14x14 alla dimensione originale, seguendo `LOCALIZATION_CONVENTIONS.md`.
La calibrazione posizionale precede l'interpolazione. La precedente calibrazione
globale a percentili con clamp [0,1] non viene utilizzata o modificata.

## Esecuzione reale per OpenCode

```powershell
mvn -B "-Dtest=*Test,BottlePositionalCalibrationIT,!BottleTrainingTest,!BottleRealEvaluationTest" "-DargLine=-Xmx2g" test
```

Dataset predefinito: `../Anomalib4j_md/mvtec-ad-DatasetNinja`; per cambiarlo,
aggiungere `-Danomalib.dataset=...`. Il test IT non parte con il normale `mvn test`.
L'esperimento richiede il training completo e un terzo passaggio sui training-good
per stimare la calibrazione; non viene eseguito durante l'implementazione.

Output dedicati in `target/bottle-spatial14-positional-calibration/`:

- `parameters.csv`: 196 righe, mu, sigma campionaria, sigma effettiva e flag floor;
- `parameter-summary.csv`: min, quartili, media, max e deviazione tra posizioni;
- `test-scores.csv`: raw e z delle 196 celle di ogni immagine;
- `image-scores.csv`, `metrics.csv`, `pro-curve.csv`, `comparison.md`;
- quattro overlay good/broken_large/broken_small/contamination `_000`, con etichetta z.

La scala cromatica e comune agli overlay di questo esperimento e non modifica
gli score. Gli artefatti baseline non sono sovrascritti. Il test verifica la
regressione dell'Image AUROC raw; Pixel AUROC/AUPRO raw nel confronto sono i
valori congelati dello Sprint 8. Il report dichiara esplicitamente l'uso in-sample;
non sono previste cross-validation o leave-one-out.
