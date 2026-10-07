# Bottle SPATIAL_14: held-out positional calibration

## Split esatto

1. Scoprire i 209 `train/img/bottle_good_*.png`, rifiutando filename duplicati.
2. Ordinare i filename lessicograficamente con `String.compareTo`.
3. Usare `Collections.shuffle(list, new java.util.Random(42L))` (Java 21).
4. Prime 167 immagini per il training; ultime 42 per la calibrazione.
5. Conservare l'ordine shuffled nei due subset e nei passaggi successivi.

I due manifest `archetype-training.txt` e `calibration.txt` riportano i filename
nell'ordine effettivo. Il test unitario verifica anche elementi fissi della
permutazione, disgiunzione, completezza e indipendenza dall'ordine di discovery.

## Modello e confronto

Un solo `NormalImageTrainer.train` riceve esclusivamente i 167 percorsi.
Le statistiche VSA contengono 32732 osservazioni, gli archetipi 167 campioni per
cella. Le 42 immagini vengono elaborate solo dopo la compilazione del modello
congelato e contribuiscono esclusivamente alle 196 mu/sigma dei raw score.
Sigma campionaria N-1, floor 1e-6 nelle unita raw; nessun'altra trasformazione.

Ogni immagine di test viene codificata una sola volta: il suo vettore raw viene
conservato e usato per calcolare z. Entrambi i rami usano max sulla griglia 14x14
per Image AUROC. Le due valutazioni di localizzazione vengono eseguite in
sequenza con le stesse maschere e `LOCALIZATION_CONVENTIONS.md` invariato.
Le matrici grandi del primo sweep diventano liberabili prima del secondo.

Il delta principale e esclusivamente `CALIBRATED_167+42 - RAW_167`, con tutte
e tre le metriche misurate nella stessa esecuzione. Le due baseline full-209
compaiono solo in una sezione contestuale separata, mai nel calcolo del delta.
Nessuna cross-validation o leave-one-out.

## Comando per OpenCode

```powershell
mvn -B "-Dtest=*Test,BottleHeldOutCalibrationIT,!BottleTrainingTest,!BottleRealEvaluationTest" "-DargLine=-Xmx2g" test
```

Dataset di default: `../Anomalib4j_md/mvtec-ad-DatasetNinja`; override:
`-Danomalib.dataset=...`. Il test IT non parte col normale `mvn test`.
L'esperimento reale non viene eseguito durante l'implementazione.

Output separati in `target/bottle-spatial14-heldout-calibration/`:

- due manifest dei subset;
- `parameters.csv` (mu, sigma campionaria/effettiva, flag floor per posizione),
  `parameter-summary.csv` (min, quartili, media, max, deviazione tra posizioni);
- `test-scores.csv` con raw/z appaiati e `image-scores.csv`;
- `metrics.csv`, `comparison.md`, `raw-pro-curve.csv`, `z-pro-curve.csv`;
- quattro overlay calibrati `_000` (good, broken_large, broken_small,
  contamination), con scala cromatica comune usata solo per visualizzazione.

Il report indica anche il numero di posizioni colpite dal floor. Gli esperimenti
precedenti, il modello core, la definizione raw e i loro artefatti restano invariati.
