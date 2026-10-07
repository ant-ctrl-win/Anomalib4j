# Contratto dell'encoder MobileNetV4 — Anomalib4j

Documento di riferimento per l'integrazione ONNX Runtime in Java.
Generato per **sola ispezione**: nessun file di codice, `pom.xml`, modello o risorsa è stato modificato.
Unico file creato/sovrascritto: `ONNX_MODEL_CONTRACT.md`.

---

## 0. Metodologia e strumenti usati

Ambiente: Windows, repository (radice del repository Anomalib4j).

- **ONNX (grafo)**: letto con la libreria Python `onnx` **già presente** nell'ambiente conda locale
  (`onnxAMD`, `onnx 1.19.0`), in sola lettura. Usati `onnx.load`,
  `onnx.shape_inference.infer_shapes` e confronto hash dei pesi. Nessuna installazione.
- **`timm`**: **NON installato** in nessun ambiente Python locale (verificato su `base`, `onnxAMD`,
  `chatterbox`: `ModuleNotFoundError: No module named 'timm'`). La ricerca sul filesystem non ha
  trovato alcun pacchetto `timm` importabile.
  Di conseguenza la configurazione pretrained del tag non è stata letta da una `timm` locale, ma dalla
  **configurazione ufficiale pubblicata del tag** su Hugging Face (`huggingface.co/timm/...`, repo
  ufficiale `timm`), **fonte esterna**. Le voci derivate da questa fonte sono etichettate come tali.
- **Nessun software installato, nessun pacchetto aggiunto, nessuna modifica all'ambiente.**

Riferimenti esterni consultati (solo lettura web):
- `https://huggingface.co/timm/mobilenetv4_conv_small.e2400_r224_in1k/raw/main/config.json`
- `https://huggingface.co/timm/mobilenetv4_conv_small.e2400_r224_in1k/raw/main/README.md`

---

## 1. File e identificatori

| Modello | Percorso | Dimensione (byte) | SHA-256 |
|---|---|---|---|
| 14×14 | `src/main/resources/models/mobilenetv4_spatial_14x14.onnx` | 1.269.313 | `48da88a8da5f1f1f1ce2308d19128c0f302498aa1889882263620cad21bdd26e` |
| 28×28 | `src/main/resources/models/mobilenetv4_spatial_28x28.onnx` | 183.163 | `23ee797ff10396f5eab8b4f00bec3520a573246448262f3c07c0a88f44e9aaf3` |

Proprietà comuni del modello ONNX (lette dal file):

- `ir_version`: **8**
- `opset_import`: dominio `''` (ai.onnx), **versione 17**
- `producer_name`: **`pytorch`**, `producer_version`: **`2.14.0`**
- `domain`: `''` (vuoto) — `model_version`: `0` — `doc_string`: vuoto
- `metadata_props`: **nessuna**
- `graph.name`: `main_graph` — nessun `value_info` salvato

---

## 2. Interfaccia I/O ONNX (verificata direttamente dal file)

### 2.1 `mobilenetv4_spatial_14x14.onnx`
- **Input (1)**
  - nome `input_image`
  - tipo **FLOAT** (float32)
  - shape **`[1, 3, 224, 224]`** (NCHW), nessuna dimensione dinamica
- **Output (1)**
  - nome `spatial_features`
  - tipo **FLOAT** (float32)
  - shape **`[1, 14, 14, 96]`** (NHWC), nessuna dimensione dinamica
- Grafo: 46 nodi, 48 initializer
- Op type usati: `Conv`, `Relu`, `Add`, `Transpose`
- Il tensore `spatial_features` è prodotto dal nodo `Transpose` con **`perm = [0, 2, 3, 1]`**
  applicato a `/backbone/blocks.2/blocks.2.5/Add_output_0` → layout di uscita **NHWC**.

### 2.2 `mobilenetv4_spatial_28x28.onnx`
- **Input (1)**
  - nome `input_image`
  - tipo **FLOAT** (float32)
  - shape **`[1, 3, 224, 224]`** (NCHW), nessuna dimensione dinamica
- **Output (1)**
  - nome `spatial_features`
  - tipo **FLOAT** (float32)
  - shape **`[1, 28, 28, 64]`** (NHWC), nessuna dimensione dinamica
- Grafo: 11 nodi, 10 initializer
- Op type usati: `Conv`, `Relu`, `Transpose`
- Il tensore `spatial_features` è prodotto dal nodo `Transpose` con **`perm = [0, 2, 3, 1]`**
  applicato a `/backbone/blocks.1/blocks.1.1/bn1/act/Relu_output_0` → layout di uscita **NHWC**.

### 2.3 Assenza di preprocessing nel grafo (verificata)
In entrambi i grafi **non esistono** nodi di normalizzazione (`Sub`/`Mul`/`Div`/`Normalize`), né di
resize/crop (`Resize`, `Crop`), né `BatchNormalization`. Le op presenti sono solo `Conv`, `Relu`,
`Add`, `Transpose`. Conseguenza: **resize, crop e normalizzazione devono essere eseguiti esternamente**
dal runtime Java; l'input atteso è già un tensore FLOAT NCHW normalizzato.

### 2.4 BatchNorm fusa (evidenza dagli initializer)
Ogni `Conv` ha esattamente 2 initializer (pesi + bias); non esiste alcun initializer di tipo
`BatchNormalization` (scale/mean/var). La normalizzazione batch risulta quindi **fusa nei pesi delle
convoluzioni** in fase di export.

---

## 3. Profondità e provenienza degli output

### 3.1 Evidenze direttamente osservabili nel grafo ONNX

I nomi dei tensori intermedi codificano i **path dei moduli PyTorch** (`/backbone/...`). Shape
intermedie ottenute con `onnx.shape_inference`:

Catena di downsampling (identica nei due modelli nel tratto condiviso):

| Stadio (path nel grafo) | Shape interna (NCHW) |
|---|---|
| `/backbone/conv_stem/Conv_output_0` | `[1, 32, 112, 112]` |
| `/backbone/blocks.0/blocks.0.1/bn1/act/Relu_output_0` | `[1, 32, 56, 56]` |
| `/backbone/blocks.1/blocks.1.0/bn1/act/Relu_output_0` | `[1, 96, 28, 28]` |
| `/backbone/blocks.1/blocks.1.1/bn1/act/Relu_output_0` | `[1, 64, 28, 28]` |
| `/backbone/blocks.2/blocks.2.0/dw_mid/bn/act/Relu_output_0` | `[1, 192, 14, 14]` |
| `/backbone/blocks.2/blocks.2.5/Add_output_0` | `[1, 96, 14, 14]` |

- Modello **28×28**: termina al nodo `Relu` sotto `/backbone/blocks.1/blocks.1.1/bn1/act/` →
  ultimo blocco elaborato = **`blocks[1]`** (sotto-blocco `1.1`).
- Modello **14×14**: prosegue attraversando i sotto-blocchi `blocks[2].0 … blocks[2].5` e termina al
  `Add` di `/backbone/blocks.2/blocks.2.5/Add_output_0` → ultimo blocco elaborato = **`blocks[2]`**.
  In `blocks[2].0` avviene il downsampling spaziale 28→14 (`dw_mid` con stride 2), quindi tutti i
  sotto-blocchi `2.1…2.5` operano a 14×14.

Sequenza dei blocchi osservata:
- 28×28: `conv_stem → act1 → blocks.0.0.0 → blocks.0.0.1 → blocks.1.0.0 → blocks.1.1.1 → Transpose`
- 14×14: quanto sopra **più** `blocks.2.0 (dw_start,pw_exp,dw_mid,pw_proj) → blocks.2.1…2.4
  (pw_exp,dw_mid,pw_proj + Add residuo) → blocks.2.5 (dw_start,pw_exp,pw_proj + Add) → Transpose`

### 3.2 I due modelli condividono gli stessi pesi (verificato)
Dei **10 initializer** del modello 28×28, **tutti e 10** risultano **byte-identici** (stesse dimensioni
e stesso contenuto `raw_data`) a initializer presenti nel modello 14×14 (10 su 48). I due modelli
condividono quindi lo stesso tronco con gli stessi pesi: **non sono due modelli indipendenti, ma due
"tagli" a profondità diverse della stessa backbone**.

### 3.3 Corrispondenza con gli stage timm

`NON VERIFICABILE DAL SOLO GRAFO ONNX` — la corrispondenza con un indice di stage PyTorch/timm
*denominato* non è ricostruibile con certezza dal solo file ONNX (il grafo espone solo i path dei
moduli, non gli indici di `features_only`).

Evidenza esterna di supporto (README ufficiale timm del tag, fonte esterna): l'estrazione
`features_only` di `mobilenetv4_conv_small.e2400_r224_in1k` restituisce, in ordine:

| Indice feature | Shape |
|---|---|
| 0 | `[1, 32, 112, 112]` |
| 1 | `[1, 32, 56, 56]` |
| 2 | `[1, 64, 28, 28]` |
| 3 | `[1, 96, 14, 14]` |
| 4 | `[1, 960, 7, 7]` |

Questo **coincide esattamente** con gli output ONNX:

| Modello ONNX | Output (NHWC) | Feature timm corrispondente |
|---|---|---|
| 28×28 | `[1, 28, 28, 64]` | indice 2 (`64×28×28`) |
| 14×14 | `[1, 14, 14, 96]` | indice 3 (`96×14×14`) |

Conclusione: i due modelli rappresentano **due livelli di profondità diversi** (non due mere
risoluzioni della stessa feature map): il 28×28 è il taglio a fine `blocks[1]`, il 14×14 è il taglio
a fine `blocks[2]`. La risoluzione diversa è una conseguenza del diverso stadio.

---

## 4. Preprocessing

### 4.1 Configurazione pretrained del tag `mobilenetv4_conv_small.e2400_r224_in1k`

`timm` **non è disponibile localmente**, quindi i valori seguenti provengono dalla **configurazione
ufficiale pubblicata del tag** (fonte esterna Hugging Face, repo `timm`), non da un'istanza `timm`
locale:

| Parametro | Valore |
|---|---|
| `architecture` | `mobilenetv4_conv_small` |
| `input_size` (train) | `[3, 224, 224]` |
| `test_input_size` | `[3, 256, 256]` |
| `fixed_input_size` | `false` |
| `interpolation` | `bicubic` |
| `crop_pct` | `0.875` |
| `test_crop_pct` | `0.95` |
| `crop_mode` | `center` |
| `mean` (RGB) | `[0.485, 0.456, 0.406]` |
| `std` (RGB) | `[0.229, 0.224, 0.225]` |
| `num_classes` | `1000` |
| `num_features` | `960` |
| `pool_size` | `[7, 7]` |
| `first_conv` | `conv_stem` |
| `classifier` | `classifier` |

Trasformazione di inferenza standard timm per input 224 (dedotta dalla config): resize del lato corto
a `round(224 / 0.875) = 256` con interpolazione **bicubic**, **center crop** a 224×224, conversione a
tensore FLOAT, normalizzazione per canale con la `mean`/`std` indicate, poi layout NCHW.

### 4.2 Verifica dell'affermazione `pixel / 127.5 - 1.0`

- **Dal file ONNX**: l'affermazione **non è verificabile** (nel grafo non c'è alcuna op di
  normalizzazione, vedi §2.3). Il grafo non contiene alcuna informazione su mean/std.
- **Dalla configurazione timm del tag**: la normalizzazione **NON equivale** a `pixel/127.5 - 1.0`.
  `pixel/127.5 - 1.0` corrisponde a `mean = 0.5`, `std = 0.5`. La config ufficiale del tag indica
  invece `mean = [0.485, 0.456, 0.406]`, `std = [0.229, 0.224, 0.225]` (normalizzazione ImageNet
  classica). I due valori **sono diversi**.

**Esito**: l'informazione proveniente dall'export originale ("normalizzazione = `pixel/127.5 - 1.0`")
**non è confermata né dal file ONNX né dalla configurazione timm del tag**; risulta anzi in
**conflitto** con la config timm pubblicata. Va **confermata con l'autore dell'export** prima di
implementare il preprocessing in Java (una scelta errata di mean/std degrada le feature e quindi il
punteggio di anomalia).

> Nota: la config timm è la fonte corretta *se* i pesi dell'export sono quelli timm
> `mobilenetv4_conv_small.e2400_r224_in1k`. Poiché i due modelli condividono pesi coerenti tra loro e
> con la struttura della variante `conv_small`, questa ipotesi è plausibile ma **non dimostrata dal
> solo file ONNX** (i pesi non sono confrontabili con il checkpoint timm in questo ambiente, perché
> `timm`/`safetensors`/`torch` del tag non sono stati usati).

---

## 5. Contratto canonico

### 5.A VERIFICATO DIRETTAMENTE DAL FILE ONNX

| Voce | Valore |
|---|---|
| Input name / tipo / shape | `input_image`, FLOAT, `[1,3,224,224]` (entrambi) |
| Output name / tipo | `spatial_features`, FLOAT (entrambi) |
| Output 28×28 shape | `[1,28,28,64]` |
| Output 14×14 shape | `[1,14,14,96]` |
| Layout output | NHWC, tramite `Transpose perm=[0,2,3,1]` (entrambi) |
| Layout input | NCHW |
| Opset | 17 — IR version 8 |
| Dimensioni dinamiche | Nessuna (batch e spaziali fisse) |
| Producer | `pytorch` 2.14.0 |
| Metadata modello | Nessuna |
| Normalizzazione nel grafo | Assente |
| Resize/crop nel grafo | Assenti |
| BatchNormalization | Assente (fusa nelle Conv) |
| Path moduli (ultimo stadio) | 28×28 → `blocks.1.1`; 14×14 → `blocks.2.5` |
| Condivisione pesi | 10/10 initializer del 28×28 identici byte-per-byte a initializer del 14×14 |
| Dimensione / SHA-256 | §1 |

### 5.B VERIFICATO DALLA CONFIGURAZIONE TIMM
*(fonte esterna: config ufficiale del tag su Hugging Face, repo `timm`; `timm` locale assente)*

| Voce | Valore |
|---|---|
| Tag | `mobilenetv4_conv_small.e2400_r224_in1k` |
| `input_size` | `[3,224,224]` |
| `mean` RGB | `[0.485, 0.456, 0.406]` |
| `std` RGB | `[0.229, 0.224, 0.225]` |
| `interpolation` | `bicubic` |
| `crop_pct` / `crop_mode` | `0.875` / `center` |
| `test_input_size` / `test_crop_pct` | `[3,256,256]` / `0.95` |
| `num_features` / `pool_size` | `960` / `[7,7]` |
| Feature map `features_only` | `32×112`, `32×56`, `64×28`, `96×14`, `960×7` |
| Corrispondenza feature | 28×28 → indice 2; 14×14 → indice 3 |

### 5.C NON VERIFICATO / DA CONFERMARE

| Voce | Stato |
|---|---|
| Normalizzazione `pixel/127.5 - 1.0` | **Non confermata / in conflitto con la config timm**; da confermare con l'autore dell'export |
| Quale preprocessing sia stato realmente applicato in export | Da confermare (non desumibile dal file ONNX) |
| Corrispondenza con indice/stage PyTorch-timm *denominato* | `NON VERIFICABILE DAL SOLO GRAFO ONNX` |
| Verifica dei pesi contro il checkpoint timm | Non eseguita (`timm`/checkpoint non disponibili localmente) |
| Uso previsto dei due modelli (combinati o alternativi) | Da confermare dal design |
| Tolleranza numerica ONNX Runtime ↔ feature attese | Da definire |
| Supporto input a risoluzioni diverse da 224×224 | Escluso dai grafi (dimensioni fisse) |

---

## 6. Sintesi operativa per l'integrazione Java

1. Input a ONNX Runtime: tensore FLOAT **NCHW** `[1,3,224,224]`, **già preprocessato** (resize/crop/
   normalizzazione esterni al grafo).
2. Output: tensore FLOAT **NHWC** — 28×28 `[1,28,28,64]`, 14×14 `[1,14,14,96]` (si veda il contratto
   HWC già atteso dal core Anomalib4j).
3. Preprocessing da implementare in Java **dipende dalla decisione su mean/std**: il valore
   `pixel/127.5 - 1.0` dichiarato dall'export è **in conflitto** con la config timm
   (`mean=[0.485,0.456,0.406]`, `std=[0.229,0.224,0.225]`). **Blocco da chiarire prima di scrivere il
   codice.**
4. I due modelli sono due **tagli di profondità** della stessa backbone (fine `blocks[1]` → 28×28×64;
   fine `blocks[2]` → 14×14×96), con pesi condivisi.
