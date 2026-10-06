# Sprint 9: validazione SPATIAL_28

`BottleSpatial28IT` ricostruisce da zero il modello con l'encoder esistente
SPATIAL_28 (28x28x64) sulle 209 immagini normali. Non legge modelli o mappe
SPATIAL_14. Mantiene D=10000, seed=42, epsilon=1e-6, std floor=1e-8 e tutte
le formule del core. Valuta le stesse 83 immagini con max della discrepanza
raw, Pixel AUROC e AUPRO secondo `LOCALIZATION_CONVENTIONS.md`.

L'equivalenza explicit/compiled viene verificata sulle 784 celle di ciascuna
delle quattro immagini `_000` (good e tre difetti): 3136 confronti, tolleranza
assoluta 1e-9. Nessun benchmark ripetuto. Le stesse immagini hanno overlay.

## Comando completo per OpenCode

Dalla root del repository, con il dataset nella posizione consueta:

```powershell
mvn -B "-Dtest=*Test,BottleSpatial28IT,!BottleTrainingTest,!BottleRealEvaluationTest" "-DargLine=-Xmx2g" test
```

Per un dataset altrove aggiungere `-Danomalib.dataset=...`. Il test IT e escluso
dal normale `mvn test`; i due test reali precedenti sono esclusi dal comando.
Questa esecuzione comprende nuovo training e valutazione a risoluzione originale
e va eseguita da OpenCode, non durante la sola implementazione.

Output esclusivamente in `target/bottle-spatial28/`:

- `image-scores.csv`, `raw-heatmaps.csv` (28x28, formato lungo);
- `metrics.csv`, `pro-curve.csv`;
- quattro PNG di confronto con scala colori condivisa nella nuova valutazione;
- `comparison.md`, con misure SPATIAL_28, baseline congelata Sprint 8 e differenze.

Il report e generato soltanto dall'esperimento. Nessun risultato SPATIAL_28 e
presunto dall'implementazione. Il confronto riguarda anche lo stadio della CNN:
SPATIAL_28 usa uno stadio precedente a 64 canali, SPATIAL_14 uno a 96 canali.
