# Sprint 8: convenzioni della valutazione

Il codice e confinato a `src/test/java/.../evaluation`. Jackson viene usato
solo nei test per leggere JSON; nessuna dipendenza runtime aggiunta.

- Ground truth: unione dei bitmap DatasetNinja alla loro origine `[x,y]`,
  a risoluzione originale. Supportati PNG base64 diretti e PNG avvolti in
  zlib. Con alpha, foreground = alpha diverso da zero; senza alpha,
  foreground = RGB diverso da nero. Dimensioni/origini incoerenti causano
  errore. Le immagini good hanno maschera vuota.
- Predizione: raw dello Sprint 7, bilineare con coordinate half-pixel
  `(dest + 0.5) * sourceSize / destSize - 0.5`, bordi replicati
  (`align_corners=false`). Nessuna trasformazione degli score.
- Pixel AUROC: ROC globale su tutti i pixel delle 83 immagini, good inclusi;
  score crescente indica anomalia. Sweep esatto di tutti i valori distinti,
  pareggi processati insieme, integrazione trapezoidale.
- PRO: componenti connesse a 8 vicini nella maschera unita, separate per
  immagine. Media non pesata della frazione di pixel rilevati in ogni
  componente: ogni regione pesa uguale, indipendentemente dalla sua area.
- FPR: falsi positivi divisi per tutti i pixel background del test set,
  comprese le immagini good. Si percorrono tutte le soglie distinte con
  predizione `score >= threshold`, iniziando da nessun pixel positivo.
- AUPRO: area trapezoidale della curva PRO/FPR in `[0, 0.30]`, interpolazione
  lineare all'estremo 0.30 e divisione dell'area per 0.30. I segmenti verticali
  a FPR costante hanno area zero. Nessun sottocampionamento delle soglie nel
  calcolo; la curva CSV e soltanto una rappresentazione interpolata ogni 0.001.
  A FPR con segmenti verticali il CSV riporta l'estremo superiore.

Questa e la convenzione AU-PRO0.30 MVTec; la definizione PRO e descritta nel
[paper MVTec AD](https://openaccess.thecvf.com/content_CVPR_2019/papers/Bergmann_MVTec_AD_--_A_Comprehensive_Real-World_Dataset_for_Unsupervised_Anomaly_Detection_CVPR_2019_paper.pdf),
e il limite 0.30 e esplicitato nella denominazione nel
[paper MVTec AD 2](https://www.mvtec.com/fileadmin/Redaktion/mvtec.com/company/research/datasets/MVTecAD2_arXiv_preprint_2503.21622v1.pdf).
Il risultato puo differire dalle approssimazioni con un numero fisso di soglie.

## Validazione separata

`BottleLocalizationIT` non viene eseguito dal normale `mvn test`. Legge
`target/bottle-raw-heatmaps.csv` gia prodotto dallo Sprint 7; verifica tutti
i nomi, le 196 celle per immagine e la regressione image AUROC a 1e-12.
Il CSV e un artefatto fidato dello Sprint 7, non un modello serializzato con
identita verificabile: per artefatti di altra provenienza va prima riprodotto
lo Sprint 7. Non viene avviato alcun encoder/training/benchmark.

Comando completo da eseguire nella root con gli artefatti Sprint 7 presenti:

```powershell
mvn -B "-Dtest=*Test,BottleLocalizationIT,!BottleTrainingTest,!BottleRealEvaluationTest" "-DargLine=-Xmx2g" test
```

Dataset alternativo: `-Danomalib.dataset=...`; CSV alternativo:
`-Danomalib.rawMaps=...`. Lo sweep esatto conserva double e ID regione per
pixel, circa 807 MB per 83 immagini 900x900; heap suggerito 2 GB.

Output in `target/bottle-localization/`: `metrics.txt`, `pro-curve.csv` e
quattro PNG di confronto (originale, ground truth, overlay raw). Scala colori
comune derivata dalle mappe raw, usata esclusivamente per visualizzazione.
