# Censimento del dataset MVTec AD (versione DatasetNinja)

Report di **sola ispezione**. Nessun file del dataset è stato modificato, spostato o rinominato;
il progetto Anomalib4j non è stato toccato. Unico file creato: `MVTEC_DATASET_CENSUS.md`.

- Dataset: `<DATASET_ROOT>/mvtec-ad-DatasetNinja`
- Metodo: enumerazione dei file, lettura dei nomi, parsing dei JSON di annotazione e lettura
  dell'header PNG (IHDR) per le dimensioni reali. Nessuna conversione, nessuna ipotesi sui nomi se
  non confermata dai dati.

---

## 1. Struttura delle directory

Struttura **piatta** (nessuna sottocartella per categoria, nessuna cartella `val`, nessuna cartella
`masks`):

```
mvtec-ad-DatasetNinja/
├── LICENSE.md                 (202 byte)
├── README.md                  (156 byte)
├── meta.json                  (15.552 byte)  → descrizione progetto/classi/tag DatasetNinja
├── train/
│   ├── img/   3629 file .png
│   └── ann/   3629 file .png.json
└── test/
    ├── img/   1725 file .png
    └── ann/   1725 file .png.json
```

- Convenzione nomi immagine: `<categoria>_<difetto>_<indice>.png` (indice a 3 cifre), es.
  `bottle_good_000.png`, `bottle_broken_large_000.png`, `cable_bent_wire_000.png`,
  `metal_nut_bent_000.png`, `grid_bent_000.png` (attenzione: la categoria `metal_nut` contiene un
  underscore nel nome).
- Ogni immagine in `img/` ha esattamente un file di annotazione omonimo in `ann/` con suffisso
  `.json` (nessun file di annotazione mancante: 0 su 5354).
- Estensioni totali presenti nel dataset: `.png` = 5354, `.json` = 5355, `.md` = 2. I 5354 `.png`
  sono **tutti** immagini (3629 train + 1725 test): **non esistono file maschera separati**.

---

## 2. Categorie MVTec disponibili

15 categorie (identiche in `train` e `test`):

`bottle`, `cable`, `capsule`, `carpet`, `grid`, `hazelnut`, `leather`, `metal_nut`, `pill`, `screw`,
`tile`, `toothbrush`, `transistor`, `wood`, `zipper`.

---

## 3. Dettaglio per categoria

Convenzioni del censimento:
- "immagini" = conteggio a livello di immagine (un'immagine è contata una volta per tipo di difetto,
  anche se contiene più oggetti-maschera).
- "oggetti-maschera" = numero di bitmap di annotazione (può essere > numero di immagini).

### bottle — dimensione 900×900
- Training: **209** immagini — sottocategorie: `good` (209).
- Test: **83** immagini; `good` = 20; difettose = 63 (68 oggetti-maschera).
  - `broken_large` = 20, `broken_small` = 22, `contamination` = 21.

### cable — dimensione 1024×1024
- Training: **224** immagini — sottocategorie: `good` (224).
- Test: **150** immagini; `good` = 58; difettose = 92 (151 oggetti-maschera).
  - `bent_wire` = 13, `cable_swap` = 12, `combined` = 11, `cut_inner_insulation` = 14,
    `cut_outer_insulation` = 10, `missing_cable` = 12, `missing_wire` = 10, `poke_insulation` = 10.

### capsule — dimensione 1000×1000
- Training: **219** immagini — sottocategorie: `good` (219).
- Test: **132** immagini; `good` = 23; difettose = 109 (114 oggetti-maschera).
  - `crack` = 23, `faulty_imprint` = 22, `poke` = 21, `scratch` = 23, `squeeze` = 20.

### carpet — dimensione 1024×1024
- Training: **280** immagini — sottocategorie: `good` (280).
- Test: **117** immagini; `good` = 28; difettose = 89 (97 oggetti-maschera).
  - `color` = 19, `cut` = 17, `hole` = 17, `metal_contamination` = 17, `thread` = 19.

### grid — dimensione 1024×1024
- Training: **264** immagini — sottocategorie: `good` (264).
- Test: **78** immagini; `good` = 21; difettose = 57 (170 oggetti-maschera).
  - `bent` = 12, `broken` = 12, `glue` = 11, `metal_contamination` = 11, `thread` = 11.

### hazelnut — dimensione 1024×1024
- Training: **391** immagini — sottocategorie: `good` (391).
- Test: **110** immagini; `good` = 40; difettose = 70 (136 oggetti-maschera).
  - `crack` = 18, `cut` = 17, `hole` = 18, `print` = 17.

### leather — dimensione 1024×1024
- Training: **245** immagini — sottocategorie: `good` (245).
- Test: **124** immagini; `good` = 32; difettose = 92 (99 oggetti-maschera).
  - `color` = 19, `cut` = 19, `fold` = 17, `glue` = 19, `poke` = 18.

### metal_nut — dimensione 700×700
- Training: **220** immagini — sottocategorie: `good` (220).
- Test: **115** immagini; `good` = 22; difettose = 93 (132 oggetti-maschera).
  - `bent` = 25, `color` = 22, `flip` = 23, `scratch` = 23.

### pill — dimensione 800×800
- Training: **267** immagini — sottocategorie: `good` (267).
- Test: **167** immagini; `good` = 26; difettose = 141 (245 oggetti-maschera).
  - `color` = 25, `combined` = 17, `contamination` = 21, `crack` = 26, `faulty_imprint` = 19,
    `pill_type` = 9, `scratch` = 24.

### screw — dimensione 1024×1024
- Training: **320** immagini — sottocategorie: `good` (320).
- Test: **160** immagini; `good` = 41; difettose = 119 (135 oggetti-maschera).
  - `manipulated_front` = 24, `scratch_head` = 24, `scratch_neck` = 25, `thread_side` = 23,
    `thread_top` = 23.

### tile — dimensione 840×840
- Training: **230** immagini — sottocategorie: `good` (230).
- Test: **117** immagini; `good` = 33; difettose = 84 (86 oggetti-maschera).
  - `crack` = 17, `glue_strip` = 18, `gray_stroke` = 16, `oil` = 18, `rough` = 15.

### toothbrush — dimensione 1024×1024
- Training: **60** immagini — sottocategorie: `good` (60).
- Test: **42** immagini; `good` = 12; difettose = 30 (66 oggetti-maschera).
  - `defective` = 30.

### transistor — dimensione 1024×1024
- Training: **213** immagini — sottocategorie: `good` (213).
- Test: **100** immagini; `good` = 60; difettose = 40 (44 oggetti-maschera).
  - `bent_lead` = 10, `cut_lead` = 10, `damaged_case` = 10, `misplaced` = 10.

### wood — dimensione 1024×1024
- Training: **247** immagini — sottocategorie: `good` (247).
- Test: **79** immagini; `good` = 19; difettose = 60 (168 oggetti-maschera).
  - `color` = 8, `combined` = 11, `hole` = 10, `liquid` = 10, `scratch` = 21.

### zipper — dimensione 1024×1024
- Training: **240** immagini — sottocategorie: `good` (240).
- Test: **151** immagini; `good` = 32; difettose = 119 (177 oggetti-maschera).
  - `broken_teeth` = 19, `combined` = 16, `fabric_border` = 17, `fabric_interior` = 16,
    `rough` = 17, `split_teeth` = 18, `squeezed_teeth` = 16.

**Totali complessivi**: training = **3629** immagini (tutte `good`); test = **1725** immagini
(467 `good` + 1258 difettose); totale = **5354**. Oggetti-maschera totali nel test = **1888**.

---

## 4. Maschere / annotazioni pixel-level

- **Posizione**: non ci sono file maschera separati. Le annotazioni pixel-level sono **incorporate
  nei JSON** in `test/ann/<nomeimmagine>.json` (e in `train/ann/...`, che però non ne contengono).
- **Struttura**: nel JSON, l'array `objects` contiene le maschere. Ogni oggetto-maschera ha:
  - `geometryType`: **`"bitmap"`** (confermato su tutti i 1888 oggetti del test; 0 altri tipi);
  - `classId` e `classTitle`: la classe/difetto (es. `broken_large`), con `classId` che combacia con
    l'`id` dell'omonima voce in `meta.json → classes`;
  - `bitmap.data`: PNG codificato in **base64** (maschera pixel-level);
  - `bitmap.origin`: coordinata `[x, y]` di posizionamento della bitmap nel canvas immagine.
- **Distribuzione**: le maschere esistono **solo** su immagini difettose del test. Nel training
  `objects` è sempre vuoto (0 oggetti su 3629 immagini); nel test `objects` è vuoto solo per le
  immagini `good`. Immagini test con almeno una maschera: 1258; oggetti-maschera: 1888. Più oggetti
  per immagine in diverse categorie (es. `grid`: 57 immagini → 170 oggetti; `wood`: 60 → 168;
  `pill`: 141 → 245).
- Le immagini `good` (train e test) **non hanno** maschera.

---

## 5. Dimensioni delle immagini

Dimensioni lette dall'header PNG (larghezza×altezza, senza conversioni). Per **ogni** categoria la
dimensione è **uniforme** (un solo valore sia nel train sia nel test); inoltre `size` dichiarato nel
JSON di annotazione coincide sempre con la dimensione reale del PNG (0 discrepanze su 5354).

| Categoria | Dimensione (W×H) |
|---|---|
| bottle | 900×900 |
| cable | 1024×1024 |
| capsule | 1000×1000 |
| carpet | 1024×1024 |
| grid | 1024×1024 |
| hazelnut | 1024×1024 |
| leather | 1024×1024 |
| metal_nut | 700×700 |
| pill | 800×800 |
| screw | 1024×1024 |
| tile | 840×840 |
| toothbrush | 1024×1024 |
| transistor | 1024×1024 |
| wood | 1024×1024 |
| zipper | 1024×1024 |

Le dimensioni **non** sono uniformi tra categorie diverse (variano da 700×700 a 1024×1024); sono
uniformi solo all'interno di ciascuna categoria.

---

## 6. Interpretazione di `meta.json` (formato DatasetNinja / Supervisely)

`meta.json` descrive il progetto, non le singole immagini. Contiene:

- `projectType`: `"images"`.
- `classes`: elenco delle **classi di annotazione** = i **tipi di difetto** (es. `broken_large`,
  `cable_swap`, `scratch`, …). Ogni classe ha `id`, `title`, `color`, `shape` (tutte `"bitmap"`) e
  `geometry_config`. In totale 47 classi di difetto.
- `tags`: elenco di **tag di progetto**, usati come metadati a livello immagine. Comprende:
  - i nomi delle **categorie MVTec** (`bottle`, `cable`, `capsule`, `carpet`, `grid`, `hazelnut`,
    `leather`, `metal_nut`, `pill`, `screw`, `tile`, `toothbrush`, `transistor`, `wood`, `zipper`);
  - il tag `good` (immagini normali) e il tag `object`;
  - tag descrittivi di proprietà (es. `regular texture`, `random texture`,
    `objects with natural variations`, `rigid object with a fixed appearance`, …).

**Associazione immagine ↔ classe/difetto ↔ annotazione** (verificata su file campione):

- Ogni immagine ha il file `<...>.json` in `ann/`. Nel JSON:
  - `tags`: include il tag della **categoria** (il cui `tagId` combacia con l'`id` in
    `meta.json → tags`, es. `bottle` = 10233) e, per le immagini normali, il tag `good`
    (id 10248); è presente anche un tag descrittivo.
  - `size`: `{ height, width }` dell'immagine.
  - `objects`: le maschere pixel-level. Per ogni oggetto, `classId`/`classTitle` rimandano alla voce
    corrispondente in `meta.json → classes` (es. `broken_large` con `classId` 40834).
- Quindi: la **categoria** proviene dai tag (o dal nome file `categoria_...`), il **tipo di difetto**
  dalla `classTitle` degli oggetti (o assenza di oggetti ⇒ `good`), e la **maschera** dal campo
  `bitmap.data` (+ `bitmap.origin`). I nomi file rispecchiano gli stessi valori
  (`<categoria>_<difetto>_<indice>.png`).

Nota: alcune `classTitle` in `meta.json` hanno `id`/`title` come `cable_swap`, `metal_contamination`,
`pill_type`, ecc., coerenti con i difetti usati nei nomi file.

---

## 7. Tabella sintetica delle categorie

| Categoria | Dim (W×H) | Train (tot) | Train `good` | Test (tot) | Test `good` | Test difettose | N. tipi difetto | N. oggetti-maschera (test) |
|---|---|---|---|---|---|---|---|---|
| bottle | 900×900 | 209 | 209 | 83 | 20 | 63 | 3 | 68 |
| cable | 1024×1024 | 224 | 224 | 150 | 58 | 92 | 8 | 151 |
| capsule | 1000×1000 | 219 | 219 | 132 | 23 | 109 | 5 | 114 |
| carpet | 1024×1024 | 280 | 280 | 117 | 28 | 89 | 5 | 97 |
| grid | 1024×1024 | 264 | 264 | 78 | 21 | 57 | 5 | 170 |
| hazelnut | 1024×1024 | 391 | 391 | 110 | 40 | 70 | 4 | 136 |
| leather | 1024×1024 | 245 | 245 | 124 | 32 | 92 | 5 | 99 |
| metal_nut | 700×700 | 220 | 220 | 115 | 22 | 93 | 4 | 132 |
| pill | 800×800 | 267 | 267 | 167 | 26 | 141 | 7 | 245 |
| screw | 1024×1024 | 320 | 320 | 160 | 41 | 119 | 5 | 135 |
| tile | 840×840 | 230 | 230 | 117 | 33 | 84 | 5 | 86 |
| toothbrush | 1024×1024 | 60 | 60 | 42 | 12 | 30 | 1 | 66 |
| transistor | 1024×1024 | 213 | 213 | 100 | 60 | 40 | 4 | 44 |
| wood | 1024×1024 | 247 | 247 | 79 | 19 | 60 | 5 | 168 |
| zipper | 1024×1024 | 240 | 240 | 151 | 32 | 119 | 7 | 177 |
| **TOTALE** | — | **3629** | **3629** | **1725** | **467** | **1258** | — | **1888** |

Note di fedeltà del censimento:
- Il conteggio dei difetti è per **immagine**; il numero di oggetti-maschera può essere maggiore
  perché alcune immagini contengono più bitmap.
- Nessuna categoria è stata selezionata o giudicata "migliore": il report fotografa soltanto lo
  stato del dataset disponibile.
