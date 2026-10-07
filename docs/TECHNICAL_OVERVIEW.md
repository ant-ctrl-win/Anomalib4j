# Anomalib4j — panoramica tecnica

Aggiornamento documentale: **2026-10-07**. La fase scientifica è chiusa.
Questo documento spiega il sistema dall'immagine allo score e collega le evidenze
conservate. Il benchmark originale resta `final-comparison-20261003`.

## 1. Orientamento: rappresentazione, memoria e detector

L'encoder produce descrittori locali; la memoria apprende la normalità; lo scorer
misura lo scostamento; la calibrazione rende confrontabili le posizioni; l'evaluator
misura detection e localizzazione. Questi ruoli sono separati.

| Modello | Memoria e score | Ruolo |
|---|---|---|
| M0 | Statistiche VSA globali, archetipo per posizione, readout direzionale compilato | Baseline Java v1 del benchmark esterno |
| M1 | Direzione del centroide CNN locale, distanza angolare | Controllo nell'ablazione |
| M2 | Media CNN locale e deviazione standard per posizione/coordinata | Miglior detector attuale per localizzazione nelle due categorie misurate |
| M3 | Media e dispersione locali dopo la proiezione VSA | Controllo dell'effetto della metrica dopo la proiezione |

Il core M0 usa Java 21 e ONNX Runtime. La [procedura di ablazione](../tools/prerun/local_memory_ablation.py)
valuta M0–M3 in Python su feature estratte dal medesimo encoder Java.
M2 non è stato inserito nel run esterno con PaDiM/PatchCore.

### Legenda comune

| Simbolo | Significato |
|---|---|
| $i$ | Indice dell'immagine |
| $p$ | Posizione nella griglia, fra le $P=196$ celle |
| $j$ | Coordinata CNN, fra le $C=96$ componenti |
| $d$ | Coordinata VSA, fra le $D=10000$ componenti |
| $N$ | Numero di immagini normali del fit: 167 Bottle, 176 Metal Nut |
| $x_{i,p}$ | Descrittore CNN prima della normalizzazione L2 |
| $u_{i,p}$ | Descrittore CNN dopo la normalizzazione L2 |
| $R$ | Matrice fissa di proiezione, dimensione $D\times C$ |
| $\odot,\oslash$ | Prodotto e divisione componente per componente |
| $\|v\|$ | Norma Euclidea del vettore $v$ |

Le medie e le deviazioni standard appartengono a tre popolazioni diverse:
statistiche VSA globali del fit, statistiche locali del fit, statistiche degli
score sul subset di calibrazione. Non sono intercambiabili.

## 2. Dall'immagine al contratto MobileNetV4

[ImageNetPreprocessor](../src/main/java/io/github/antctrlwin/anomalib4j/onnx/ImageNetPreprocessor.java)
porta l'immagine RGB a 224×224 mediante resize bicubico. Per ogni canale divide
il pixel per 255, sottrae la media ImageNet e divide per la deviazione standard:
medie (0.485, 0.456, 0.406), deviazioni (0.229, 0.224, 0.225).
Questi sono parametri fissi del preprocessing, non statistiche apprese su MVTec.

[OnnxMobileNetV4Encoder](../src/main/java/io/github/antctrlwin/anomalib4j/onnx/OnnxMobileNetV4Encoder.java)
usa il modello SPATIAL_14 congelato:
input float32 NCHW $[1,3,224,224]$, output float32 NHWC
$[1,14,14,96]$. Il grafo restituisce feature, non uno score di anomalia.

Il descrittore che entra nella memoria è:

$
u_{i,p}=\frac{x_{i,p}}{\max(\|x_{i,p}\|,\epsilon)},\qquad \epsilon=10^{-6}.
$

**Normalizzazione L2** significa portare il vettore sulla sfera unitaria quando
la norma supera epsilon. **Standardizzazione** significa sottrarre una media e
dividere per una deviazione standard apprese. Se epsilon si attiva, il vettore
non è necessariamente unitario: le identità che richiedono norma uno hanno
questa condizione esplicita.

L'encoder non viene aggiornato nel fitting della memoria. Layout, resize, ordine
dei canali e normalizzazione devono coincidere fra fit e inferenza.
Shape, hash degli asset e limiti della provenienza dei pesi sono nel
[contratto ONNX](architecture/ONNX_MODEL_CONTRACT.md).

## 3. Costruzione della memoria M0

[DenseRademacherProjection](../src/main/java/io/github/antctrlwin/anomalib4j/projection/DenseRademacherProjection.java)
costruisce $R$ con coefficienti ±1, seed 42 e ordine deterministico
canale CNN esterno/coordinata VSA interna. La proiezione è:

$
y_{i,p}=Ru_{i,p}/\sqrt D.
$

Sul solo fit, [ProjectionStatisticsAccumulator](../src/main/java/io/github/antctrlwin/anomalib4j/memory/ProjectionStatisticsAccumulator.java)
stima media globale $g$ e deviazione standard campionaria globale $s_g$
aggregando immagini e posizioni. La deviazione effettiva è
$\tilde s_{g,d}=\max(s_{g,d},10^{-8})$.
Il vettore standardizzato è $z_{i,p}=(y_{i,p}-g)\oslash\tilde s_g$.

[PositionalMemoryBuilder](../src/main/java/io/github/antctrlwin/anomalib4j/memory/PositionalMemoryBuilder.java)
accumula $B_p=\sum_i z_{i,p}$ e produce l'archetipo direzionale
$A_p=B_p/\|B_p\|$, nel caso non degenere.

Definendo $\bar y_p$ come media VSA della posizione sul fit:

$
B_p=N(\bar y_p-g)\oslash\tilde s_g.
$

L'archetipo è quindi la direzione dell'offset posizionale rispetto alla
popolazione globale standardizzata. M0 non conserva una deviazione standard
specifica di ogni posizione: non descrive esplicitamente la dispersione nominale
locale. La standardizzazione globale resta una scelta valida del modello.

## 4. Derivazione della compilazione Adjoint

Per una nuova osservazione $u$ alla posizione $p$, lo score esplicito è:

$
s_p(u)=A_p^T\big((Ru/\sqrt D-g)\oslash\tilde s_g\big).
$

Definiamo $q_p=A_p\oslash\tilde s_g$. Poiché i parametri sono fissati dopo
il fit, la divisione diagonale può essere trasferita al readout:

$
\begin{aligned}
s_p(u)&=q_p^T(Ru/\sqrt D-g)\\
      &=(R^Tq_p/\sqrt D)^Tu-g^Tq_p\\
      &=W_p^Tu+b_p,\\
W_p&=R^Tq_p/\sqrt D,\qquad b_p=-g^Tq_p.
\end{aligned}
$

Qui $q_p$ ha D componenti, $W_p$ ha C componenti e $b_p$ è uno scalare.
[AdjointCompiler](../src/main/java/io/github/antctrlwin/anomalib4j/adjoint/AdjointCompiler.java)
costruisce pesi e bias; [PatchScoreKernel](../src/main/java/io/github/antctrlwin/anomalib4j/inference/PatchScoreKernel.java)
valuta direttamente:

$
s_p(x)=\frac{W_p^Tx}{\max(\|x\|,\epsilon)}+b_p.
$

Scompaiono la proiezione VSA runtime e la materializzazione del vettore
standardizzato a D dimensioni. Restano encoder, norma CNN, prodotto scalare e bias,
calibrazione e generazione della mappa. L'identità è affine in $u$, non in
$x$ prima della normalizzazione.

Non serve invertire R. Non si afferma una biiezione fra spazi né la compilabilità
di qualsiasi operazione VSA non lineare. L'equivalenza richiede gli stessi
parametri, ordine dei canali e policy di normalizzazione.

Il [confronto reale Sprint 7](development/SPRINT7_RESULT.md) verifica 83 immagini,
16,268 celle e massimo errore assoluto 1.31×10⁻¹². In floating point si confrontano
percorsi con ordini diversi di accumulo, non si promette identità bit per bit.

## 5. Calibrazione e localizzazione

Per M0 lo score anomalo RAW è $r_p=1-s_p$.
Sulle sole immagini di calibrazione si stimano la media $a_p$ e la deviazione
standard campionaria $t_p$ di questo RAW:

$
Z_p=\frac{r_p-a_p}{\max(t_p,10^{-6})}.
$

[PositionalRawCalibration](../src/test/java/io/github/antctrlwin/anomalib4j/evaluation/PositionalRawCalibration.java)
implementa questa calibrazione nel percorso di valutazione. Le immagini di
calibrazione non partecipano al fitting di statistiche, archetipi e filtri.

Lo score immagine è il massimo delle 196 Z, prima dell'upsampling. La mappa
14×14 viene interpolata con convenzione bilineare half-pixel e bordi replicati,
fino alla risoluzione originale. La valutazione conserva valori numerici senza
clamp; colori e scale grafiche sono presentazione. Le API visuali non vanno
confuse con il percorso numerico del benchmark.
Dettagli nelle [convenzioni di localizzazione](benchmark/LOCALIZATION_CONVENTIONS.md).

## 6. M1 e M2: centroide e tolleranza locale

Per ogni posizione, $\mu_p=N^{-1}\sum_i u_{i,p}$ è la media dei descrittori
normalizzati del fit. Non è in generale unitaria.
Poniamo $\rho_p=\|\mu_p\|$ e $c_p=\mu_p/\rho_p$ se $\rho_p>0$.

M1 usa $r_{1,p}(u)=1-c_p^Tu$. È un confronto angolare isotropo intorno
alla direzione del centroide. Alla stessa posizione, per $\|u\|=1$:

$
\|u-\mu_p\|^2
=1-2\rho_pc_p^Tu+\rho_p^2
=(1-\rho_p)^2+2\rho_p r_{1,p}(u).
$

La distanza isotropa dalla media è una trasformazione affine positiva di M1.
Per qualsiasi score $r$, offset costante $\alpha$ e scala $\beta>0$,
le statistiche sullo stesso subset di calibrazione sono
$\mathrm{mean}(\alpha+\beta r)=\alpha+\beta\mathrm{mean}(r)$ e
$\mathrm{std}(\alpha+\beta r)=\beta\mathrm{std}(r)$.
Pertanto le Z coincidono se entrambi i floor di calibrazione sono inattivi.

M2 stima inoltre la deviazione standard campionaria $\sigma_{p,j}$ dei
descrittori del fit, separatamente per posizione e coordinata. Usa
$\tilde\sigma_{p,j}=\max(\sigma_{p,j},10^{-6})$ e:

$
r_{2,p}(u)=\frac1C\sum_{j=1}^C
\left(\frac{u_j-\mu_{p,j}}{\tilde\sigma_{p,j}}\right)^2.
$

Se tutte le deviazioni sono uguali a $s_p>0$, M2 è la distanza isotropa
moltiplicata per $1/(Cs_p^2)$: dopo Z coincide con M1 nelle stesse condizioni.
Il confronto M1-Z/M2-Z sostiene quindi l'utilità della pesatura anisotropa
complessiva, non il solo passaggio da direzione a centro.

Le superfici a score M2 costante sono ellissoidi nelle coordinate CNN ambiente,
valutati sui descrittori unitari. Non è una distribuzione intrinseca sullo spazio
tangente della sfera. Le varianze sono stime da campioni finiti e la diagonale
ignora le correlazioni: non si dimostra ottimalità di ciascun peso o del modello.

Nell'audit dei dati usati, epsilon non si attiva, i centroidi sono non degeneri
e i floor effettivi M2/calibrazione sono inattivi. L'identità algebrica rimane
condizionata ai floor inattivi anche per lo score isotropo ipotetico.
L'uguaglianza Z posizione per posizione conserva anche il successivo massimo
e l'upsampling. I RAW non sono invece equivalenti fra posizioni: offset e scala
dipendono da $p$. Evidenze e derivazione nell'[ablazione](diagnostics/LOCAL_MEMORY_ABLATION_RESULT.md).

## 7. M3: la metrica dopo la proiezione

M3 applica il residuo diagonale alle coordinate $y=Lu$, dove
$L=R/\sqrt D$. Indichiamo con $\nu_p=L\mu_p$ la media VSA locale e
con $\tau_p$ la sua deviazione standard per coordinata, con floor $10^{-8}$.

$
r_{3,p}(u)=\frac1D\sum_d
\left(\frac{(Lu)_d-\nu_{p,d}}{\tilde\tau_{p,d}}\right)^2.
$

Definendo $T_p=\mathrm{diag}(\tilde\tau_{p,d}^{-2})$, nelle coordinate CNN
la metrica è $L^TT_pL/D$. È diversa dalla diagonale CNN di M2:
fare una stima diagonale dopo una combinazione delle coordinate cambia il modello.

La matrice congelata 10000×96 ha rango di colonna 96: la proiezione conserva
l'informazione del descrittore normalizzato sulla propria immagine. Non recupera
l'ampiezza rimossa dalla precedente L2 e non crea informazione nuova.
Il risultato M3 non prova una perdita informativa della VSA.

Lo stesso score M3 ammette una forma quadratica CNN
$u^TQ_pu+w_p^Tu+b_p$, con
$Q_p=L^TT_pL/D$, $w_p=-2L^TT_p\nu_p/D$,
$b_p=\nu_p^TT_p\nu_p/D$.
La verifica numerica è documentata nell'ablazione; non è un filtro affine M0
né un nuovo detector separato.

## 8. Diagnostica posizionale e globale

Per una coordinata $d$, ora usata genericamente nello spazio analizzato,
$s_{g,d}$ è la deviazione globale osservata, $s_{p,d}$ quella locale,
$\mu_{p,d}$ la media locale e $\mu_{g,d}$ quella globale.
Con $n=NP$, le varianze campionarie non floored soddisfano:

$
(n-1)s_{g,d}^2
=(N-1)\sum_p s_{p,d}^2
+N\sum_p(\mu_{p,d}-\mu_{g,d})^2.
$

Il primo termine a destra è la variabilità entro posizione; il secondo quella
fra medie posizionali. $H_d$ è il secondo termine diviso per il totale
$(n-1)s_{g,d}^2$, quando positivo.

| Mediana H | Bottle | Metal Nut |
|---|---:|---:|
| CNN normalizzata | 0.9299 | 0.5056 |
| VSA | 0.9684 | 0.4912 |

Il confronto corretto usa le stesse feature CNN normalizzate prima e dopo la
proiezione. La differenza qualitativa fra categorie è già presente nelle CNN.
H descrive la distribuzione della variabilità, non predice causalmente AUPRO e
non certifica l'affidabilità dell'archetipo. Norme degli offset piccole o grandi
restano quantità osservate. La precedente analisi CNN pre-L2 è conservata con
etichetta legacy, non come confronto equivalente.
Fonte: [diagnostico posizionale/globale](diagnostics/POSITIONAL_GLOBAL_VARIANCE_DIAGNOSTIC.md).

## 9. Metodo del benchmark congelato

| Categoria | Fit M0 | Calibrazione M0 | Test |
|---|---:|---:|---:|
| Bottle | 167 | 42 | 83: 20 normali, 63 anomalie |
| Metal Nut | 176 | 44 | 115: 22 normali, 93 anomalie |

PaDiM e PatchCore usano tutti i 209/220 normali nel rispettivo fitting nativo.
Il run usa seed 42, SPATIAL_14 per M0, CPU AMD Ryzen AI 9 HX 370 e Windows 11.
I competitor sono Anomalib 2.6.2, con ResNet18 per PaDiM e Wide ResNet50-2 per
PatchCore; M0 usa MobileNetV4. Backbone, preprocessing, risoluzioni e runtime
non sono identici: è un confronto Native/System sullo stesso hardware.

Image AUROC usa lo score immagine nativo dichiarato; non lo ricava dalla mappa
interpolata. Pixel AUROC è globale sui pixel. AUPRO usa regioni 8-connesse con
uguale peso per regione e area normalizzata fino a FPR 0.30.
I risultati completi restano nel [benchmark finale](benchmark/BENCHMARK_FINAL_RESULT.md);
il [contratto](benchmark/BENCHMARK_CONTRACT.md) ne definisce il perimetro.

## 10. Ablazione successiva al benchmark

M0–M3 usano le stesse feature, il medesimo fit/calibrazione/test e la stessa
procedura di valutazione. Ogni modello stima la propria Z sul subset riservato.
M0 ricostruito è confrontato con lo stato e le mappe congelate.

| Categoria | M0 AUPRO Z | M1 AUPRO Z | M2 AUPRO Z | M3 AUPRO Z |
|---|---:|---:|---:|---:|
| Bottle | 0.877447 | 0.890405 | 0.915738 | 0.891606 |
| Metal Nut | 0.379794 | 0.372387 | 0.624208 | 0.504555 |

M2 migliora la localizzazione nelle due categorie; non domina ogni metrica di
detection. Gli ordinamenti M0/M1 e M1/M3 cambiano secondo metrica e RAW/Z.
L'ablazione è interna, post-hoc e su categorie già osservate. Non sostituisce
il benchmark esterno e non dimostra generalizzazione indipendente.
Tutte le metriche sono nel [report M0–M3](diagnostics/LOCAL_MEMORY_ABLATION_RESULT.md).

## 11. Runtime e stato conservato

| Misura M0 | Bottle | Metal Nut |
|---|---:|---:|
| Detection p50, ms | 3.723 | 3.695 |
| Localizzazione p50, ms | 9.486 | 5.775 |

La misura storica del solo kernel è circa 12 µs per 196 posizioni.
Detection include preprocessing, encoder e score immagine; localizzazione
include anche la mappa finale. L'immagine è già decodificata, batch 1.
JMH e campionamento Python dei competitor hanno protocolli diversi; la latenza
non è una misura di throughput sostenuto né di SLA.

Lo stato minimo double di scoring M0 comprende $P(C+1)$ valori:
152,096 byte per W e bias. La Z aggiunge 3,136 byte. Lo stato di apprendimento
con archetipi e media/std globale conta 15,840,000 byte per questi array.
Sono conteggi di payload, non memoria del processo; escludono encoder, R,
overhead e buffer. Il benchmark conserva anche stato serializzato e verifica
il reload mediante [ComparisonModel](../src/test/java/io/github/antctrlwin/anomalib4j/evaluation/ComparisonModel.java).

Il timing Python M0/M3 dell'ablazione cronometra un unico loop con proiezione
condivisa e calcolo di entrambi gli score. I due campi riportano quel medesimo
tempo: non sono latenze individuali e non rappresentano M0 compilato.
Nessuna latenza M0 viene attribuita a M2.
Vedere [misure JMH](development/PERFORMANCE_BASELINE_RESULT.md) e benchmark finale.

## 12. Riproducibilità, artefatti e limiti

### Percorso minimo e sorgenti

JDK 21 e Maven servono per il core; ONNX e test sono nel repository.
Il comando mirato del [README](../README.md) esegue i test algebrici senza
training sui dati reali. Il normale `mvn test` comprende anche test reali lunghi.

Gli script post-hoc leggono il run locale congelato
`target/comparison/final-comparison-20261003/`. Non sono comandi autonomi
che ricreano tutto da una copia appena clonata.

Prima di usarli occorrono:

1. **Dataset esterno** nel layout DatasetNinja atteso: gli script usano
   `../Anomalib4j_md/mvtec-ad-DatasetNinja`, con `train/img`, `test/img`
   e `test/ann`. Non esiste un argomento CLI per cambiarlo nei due script.
2. **Artefatti originali disponibili localmente**: manifest in
   `data/<categoria>/`, `anomalib4j/<categoria>/learning-state.bin`;
   per l'ablazione anche `predictions.csv` e mappe RAW/native in `maps/`.
   Non basta creare una directory vuota con il nome del run.
3. **Classi Java e classpath** generati dal bootstrap, dalla root del repository:

   ```powershell
   powershell -NoProfile -File tools/comparison/bootstrap.ps1
   ```

   [bootstrap.ps1](../tools/comparison/bootstrap.ps1) esegue test-compile e
   dependency:build-classpath, scrivendo `target/comparison-classpath.txt`.
   Non esegue benchmark e non ripristina gli artefatti congelati.
4. **Python e librerie**: Python 3.11+ con NumPy per il diagnostico di varianza.
   L'ablazione importa anche `metal_nut_pose_diagnostic.py`, che richiede
   SciPy, Pillow, scikit-image e Matplotlib. L'ambiente usato dal progetto è
   descritto nella [preparazione del benchmark](benchmark/BENCHMARK_PRERUN_VERIFICATION.md).
   Il percorso `target/prerun/anomalib-2.6.2/.venv/Scripts/python.exe` nei
   report indica quell'ambiente locale, non un eseguibile distribuito nel repo.

Con questi prerequisiti già soddisfatti, usando l'interprete preparato:

```shell
python tools/prerun/positional_global_variance_diagnostic.py
python tools/prerun/local_memory_ablation.py
```

Questi comandi eseguono analisi e scrivono risultati derivati; non sono smoke
test e non sono stati eseguiti per la ristrutturazione documentale.
Il [feature dump Java](../src/test/java/io/github/antctrlwin/anomalib4j/evaluation/PositionalVarianceFeatureDump.java)
può riestrarre le feature quando la cache non è disponibile.
Gli script riusano cache esistenti: conservarle insieme ai manifest coerenti.

### Cosa è pubblicato e cosa resta locale

Sono sorgenti pubblicabili i file Java/Python, i Markdown, gli asset ONNX del
progetto e le immagini selezionate in `docs/images/`. Il dataset resta esterno.
`target/` è ignorata: contiene classi, ambienti, modelli appresi, mappe,
feature dump, CSV, JSON e campioni di latenza. Questi output sono evidenze
generate, non sorgenti da aggiungere al commit. `mvn clean` li cancella.

La copia pubblica documenta risultati e procedure; da sola non include l'archivio
completo necessario a ripetere i controlli sugli artefatti del run storico.
Per il percorso completo dei runner consultare le
[istruzioni del confronto](benchmark/BENCHMARK_PHASE2_IMPLEMENTATION.md).
Una nuova esecuzione sarebbe distinta dal run congelato; non serve a chiudere
questa fase documentale.

Due categorie e diagnosi sul test già osservato limitano la generalizzazione.
L'encoder è congelato, la provenienza completa dell'export mantiene i limiti del
contratto ONNX e non si dichiara maturità produttiva. La
[riconciliazione finale](development/FINAL_POSTHOC_RECONCILIATION.md) registra la
chiusura della fase e la distinzione fra compilazione, baseline M0 e risultato M2.
