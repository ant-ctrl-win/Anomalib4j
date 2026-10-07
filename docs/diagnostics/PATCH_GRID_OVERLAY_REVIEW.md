# Patch-grid overlay review — Bottle (SPATIAL_14 vs SPATIAL_28)

Visualizzazione diagnostica delle heatmap **gia' esistenti**, senza training,
senza nuova inferenza e senza modificare il codice di produzione. Confronto
tra la rappresentazione **discreta** reale delle feature map e l'**upsampling
bilineare** attuale (Sprint 8).

## Input utilizzati

| Artefatto | Ruolo |
| --- | --- |
| `target/bottle-raw-heatmaps.csv` | mappe raw SPATIAL_14 (14x14) |
| `target/bottle-spatial28/raw-heatmaps.csv` | mappe raw SPATIAL_28 (28x28) |
| `../Anomalib4j_md/mvtec-ad-DatasetNinja/test/img/bottle_*.png` | immagine originale |
| `../Anomalib4j_md/mvtec-ad-DatasetNinja/test/ann/bottle_*.png.json` | ground truth |
| `target/test-classes` (`LocalizationMaps.upsample`, `DatasetNinjaMasks`) | helper congelati per il bilineare e le maschere |

Esempi: `bottle_good_000`, `bottle_broken_large_000`, `bottle_broken_small_000`,
`bottle_contamination_000`.

## Output generati

Sotto `target/patch-grid-overlays/`, una tavola PNG per esempio (6 pannelli,
2 righe x 3 colonne):

- `bottle_good_000_patch_grid.png`
- `bottle_broken_large_000_patch_grid.png`
- `bottle_broken_small_000_patch_grid.png`
- `bottle_contamination_000_patch_grid.png`

Ordine dei pannelli: (1) originale, (2) ground truth, (3) SPATIAL_14 discreta,
(4) SPATIAL_14 bilineare, (5) SPATIAL_28 discreta, (6) SPATIAL_28 bilineare.

## Metodo

- **Pannello 3/5 (discreto)**: nearest-neighbor sul griglia, ovvero ogni cella
  `14x14` (o `28x28`) diventa un **blocco costante** a piena risoluzione,
  calcolato con la stessa convenzione half-pixel
  `s = (d + 0.5) * side / dest - 0.5` e arrotondamento. Nessuna interpolazione.
- **Pannello 4/6 (bilineare)**: `LocalizationMaps.upsample` gia' congelato
  (coordinate half-pixel, bordi replicati, `align_corners=false`), identico
  allo Sprint 8.
- **Ground truth**: originale con maschera in rosso (blend 0.55/0.45).
- **Heatmap**: colore HSB blu (basso) -> rosso (alto) come nello Sprint 8,
  sovrapposto all'originale con lo stesso blend.
- **Griglia visiva** sovrapposta su tutti i pannelli caldi: `14x14` per la
  variante 14, `28x28` per la variante 28 (linea bianca con bordo scuro).
- Nessuno smoothing, nessuna calibrazione, nessuna normalizzazione per
  immagine.

### Scale cromatiche (differiscono tra varianti)

Le scale sono derivate dall'intero test set della rispettiva variante e sono
**diverse**, quindi i colori **non** sono direttamente confrontabili tra
14 e 28. All'interno di una variante, discreto e bilineare condividono la
stessa scala.

| Variante | scale_low | scale_high |
| --- | --- | --- |
| SPATIAL_14 | -113.36993126538826 | 49.35299664402688 |
| SPATIAL_28 | -152.69985442300913 | 69.07278642044432 |

### Nota geometrica (non e' il receptive field)

- `14x14` su input `224x224` corrisponde a un **passo geometrico di 16x16
  pixel**.
- `28x28` corrisponde a un **passo geometrico di 8x8 pixel**.
- Questi passi **NON** coincidono con il receptive field reale della CNN, che
  e' piu' ampio. La griglia disegnata e' soltanto il passo della griglia di
  celle, non l'estensione spaziale della feature.

## Celle attive per esempio

Due descrittori, entrambi **solo descrittivi della visualizzazione**, non
soglie di scoring:

- `fra >= 0.50` / `>= 0.75`: numero di celle nella meta' alta / nel quarto
  alto della scala cromatica condivisa della variante (descrizione visiva).
- `env posizionale`: celle il cui raw supera il **massimo osservato sulle 20
  good alla stessa cella** (riferimento posizionale; nessuna normalizzazione
  per immagine). Per definizione `good_000` vale 0.

| Esempio | Variante | max cella | pos (r,c) | celle fra>=0.50 | celle fra>=0.75 | comp. hot075 | hot075 max | env posizionale |
| --- | --- | ---: | --- | ---: | ---: | ---: | ---: | ---: |
| good_000 | SPATIAL_14 | -83.42 | (6,6) | 0 | 0 | 0 | 0 | 0 |
| good_000 | SPATIAL_28 | -17.02 | (1,18) | 12 | 0 | 0 | 0 | 0 |
| broken_large_000 | SPATIAL_14 | 10.98 | (11,7) | 5 | 1 | 1 | 1 | 55 |
| broken_large_000 | SPATIAL_28 | 52.48 | (14,22) | 73 | 15 | 5 | 5 | 112 |
| broken_small_000 | SPATIAL_14 | -66.28 | (8,4) | 0 | 0 | 0 | 0 | 12 |
| broken_small_000 | SPATIAL_28 | 22.36 | (18,8) | 7 | 1 | 1 | 1 | 18 |
| contamination_000 | SPATIAL_14 | 30.79 | (6,7) | 3 | 1 | 1 | 1 | 41 |
| contamination_000 | SPATIAL_28 | 36.94 | (11,9) | 52 | 4 | 4 | 1 | 86 |

## Risposte alle domande

**Quante celle risultano chiaramente attive per ciascun difetto.**
- `broken_large`: a 14 una macchia compatta di ~4-5 celle (1 sola chiaramente
  calda); a 28 una banda verticale con ~15 celle chiaramente calde, ripartite
  in 5 componenti.
- `broken_small`: a 14 nessuna cella e' "claramente calda" sulla scala
  condivisa (max -66 e' sotto la meta'), ma l'envelope posizionale segnala 12
  celle; visivamente ~2-3 celle ciano/verdi intorno al difetto. A 28 una sola
  cella chiaramente calda (arancio/giallo, il massimo) piu' ~6 celle verdi
  (7 sopra la meta' della scala, 18 sopra l'envelope).
- `contamination`: a 14 ~3-4 celle (1 chiaramente calda); a 28 ~4 celle
  chiaramente calde piu' una vasta area verde (52 sopra la meta' della scala,
  86 sopra l'envelope).

**`broken_small` e' sostenuto da una singola cella o da piu' celle.**
Da piu' celle, ma con **una cella dominante**. A 28 il massimo (22.36) e' una
singola cella arancio/giallo; e' pero' circondata da celle verdi e
l'envelope posizionale ne conta 18. A 14 non c'e' una cella singola calda: il
segnale e' distribuito su ~2-3 celle adiacenti (envelope 12). Non e' quindi
un singolo pixel di griglia isolato.

**Quanto il bilineare modifica solo la visualizzazione.**
Il bilineare e' una semplice **re-campionatura dei medesimi valori di cella**:
non aggiunge informazione, non cambia i massimi, le scale, ne' i conteggi.
Rende fluide le transizioni tra blocchi e quindi **nasconde** quanti blocchi
sono coinvolti; il pannello discreto e' l'unico che mostra la vera struttura
`14x14`/`28x28`. Visivamente il difetto bilineare appare come una macchia
sfumata, mentre il discreto mostra i singoli blocchi attivi.

**Il confronto 14 vs 28 rende piu' evidente la frammentazione del 28.**
Si. Il pannello discreto `28x28` mostra molti blocchi piccoli con salti
cella-cella marcati (effetto a mosaico/banding), mentre il `14x14` e' molto
piu' uniforme perche' ogni blocco e' 4x piu' grande. La frammentazione del 28
e' visibile pure nella regione normale (`good_000`: 0 celle sopra 0.50 a 14,
12 a 28). Va notato che una parte di questo effetto e' di rendering/scala: il
28 ha 4x celle e usa una scala globale piu' larga che amplifica la variabilita'
normale. Non e' di per se' prova di migliore localizzazione.

## Conclusione

- Il discreto e' l'unica vista fedele alla griglia; il bilineare e' solo una
  resa piu' morbida degli stessi numeri.
- La frammentazione e' chiaramente maggiore a 28, ma in parte dipende dalla
  granularita' 4x e dalla scala condivisa piu' larga.
- Per `broken_small` il supporto e' un piccolo cluster con una cella dominante,
  non una cella isolata.
- Le due varianti usano scale cromatiche diverse: i confronti di colore tra 14
  e 28 non sono diretti. Il passo 16/8 px e' geometrico e non e' il receptive
  field della CNN.
