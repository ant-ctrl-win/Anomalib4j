# Sprint 9 - Risultato validazione SPATIAL_28

Comando eseguito:

```powershell
mvn -B "-Dtest=*Test,BottleSpatial28IT,!BottleTrainingTest,!BottleRealEvaluationTest" "-DargLine=-Xmx2g" test
```

## Build e test

- BUILD SUCCESS
- Tests run: 69, Failures: 0, Errors: 0, Skipped: 0
- `BottleSpatial28IT`: Tests run 1, Failures 0, Errors 0, Skipped 0

## Durata

- Tempo totale Maven: 13:01 min
- `BottleSpatial28IT`: 779.0 s

## Risultati SPATIAL_28

| Metrica | Valore |
| --- | --- |
| Image AUROC | 1.0000000000000000 |
| Pixel AUROC | 0.90140233533244440 |
| AUPRO@0.30 | 0.70555613590835210 |

## Equivalenza explicit-VSA vs compiled-Adjoint

- Confronti: 3136 (784 celle x 4 immagini overlay)
- Errore massimo assoluto: 1.1652900866465643e-12
- Tolleranza asserita: 1e-9

## Confronto con SPATIAL_14

| Metrica | SPATIAL_14 (14x14x96) | SPATIAL_28 (28x28x64) | Delta (28 - 14) |
| --- | --- | --- | --- |
| Image AUROC | 0.9857142857142858 | 1.0000000000000000 | +0.0142857142857142 |
| Pixel AUROC | 0.9569495750935939 | 0.9014023353324444 | -0.0555472397611495 |
| AUPRO@0.30 | 0.8653474657258569 | 0.7055561359083521 | -0.1597913298175048 |

## Artefatti prodotti

In `target/bottle-spatial28/`:

- `image-scores.csv`
- `raw-heatmaps.csv` (28x28, formato lungo)
- `metrics.csv`
- `pro-curve.csv`
- `comparison.md`
- quattro overlay PNG: `bottle_good_000.png`, `bottle_broken_large_000.png`, `bottle_broken_small_000.png`, `bottle_contamination_000.png`

## Problemi osservati

- Nessun errore tecnico, nessun test fallito.
- SPATIAL_28 peggiora la localizzazione rispetto a SPATIAL_14 (Pixel AUROC -0.0555, AUPRO@0.30 -0.1598) pur migliorando Image AUROC (+0.0143).
- SPATIAL_28 e' uno stadio precedente a 64 canali, SPATIAL_14 a 96 canali: la differenza non e' attribuibile alla sola risoluzione spaziale.

## Verifica git diff --stat

```
 TASK.md | 26 ++++++++++++---------------
 1 file changed, 11 insertions(+), 15 deletions(-)
```

Untracked: `SPATIAL28_VALIDATION.md`, `src/test/java/.../BottleSpatial28IT.java`,
`src/test/java/.../Spatial28Evaluation.java`, `src/test/java/.../Spatial28EvaluationTest.java`.

Tutte le modifiche appartengono allo Sprint 9: nessuna modifica fuori scope.
