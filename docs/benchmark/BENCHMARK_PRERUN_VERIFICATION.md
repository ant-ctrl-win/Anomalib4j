# Phase 1: verifica pre-run Anomalib4j / Anomalib

2026-10-03. **VERIFIED:** completata la verifica tecnica pre-run richiesta. Nessun benchmark comparativo finale, nessuna accuracy o latenza dei sei casi, nessun tuning. Le misure Bottle storiche restano invariate. Il piccolo fit descritto sotto e una sonda tecnica, non uno dei modelli finali per categoria.

## 1. Codice esatto e ambiente

Repository: `https://github.com/open-edge-platform/anomalib.git`; tag locale `lib/v2.6.2`; HEAD e tag dereferenziato coincidono con **`cc5f400a4a4b1b14b5a3ee5078063c250a8f522e`**. Prefisso cc5f400 confermato, non espanso per supposizione. Checkout `target/prerun/anomalib-2.6.2`, detached HEAD pulito anche dopo setup/smoke.

Ambiente `.venv` isolato nel checkout, creato con il `uv.lock` upstream senza modificarlo. SHA-256 lock: `f33ff8dff6f60cd3e6f7fd23c854d661f474804f465bcaacbefcde612309a103`. uv 0.11.16. `uv pip check`: 78 pacchetti compatibili. Nessuna installazione nell'ambiente Java o Python globale.

| Componente | Versione effettiva |
| --- | --- |
| Python | CPython 3.11.15, Windows x64 |
| anomalib | 2.6.2, editable dal checkout identificato |
| torch / torchvision | 2.13.0+cpu / 0.28.0+cpu |
| timm | 1.0.28 |
| lightning / pytorch-lightning | 2.6.5 / 2.6.5 |
| numpy / scipy / scikit-learn | 2.4.6 / 1.17.1 / 1.9.0 |
| Pillow | 12.3.0 |
| kornia / torchmetrics | 0.8.3 / 1.9.0 |
| huggingface-hub / safetensors | 1.27.0 / 0.8.0 |
| OpenCV headless | 5.0.0.93 |

Freeze completo: [environment.freeze.txt](benchmark-prerun/environment.freeze.txt); ambiente/backend: [environment.json](benchmark-prerun/environment.json); tag e lock: [source.json](benchmark-prerun/source.json). Il freeze contiene un percorso editable assoluto: per riprodurre, prima ricreare il checkout al commit indicato e usare il suo lock, non puntare a una cartella arbitraria. Il Python base trovato da uv e gia installato in un altro percorso locale; la nuova `.venv` e isolata, versione/eseguibile registrati.

## 2. Configurazioni native verificate

Un'unica configurazione per entrambe le categorie, dettagli macchina in [configurations.json](benchmark-prerun/configurations.json). Nessun test finale consultato per scegliere parametri.

### PaDiM

`Padim`: backbone resnet18, layer1/2/3, pre_trained=True, n_features=None -> **100**, estratti fra **448** canali con torch.randperm e seed 42. Feature grid 64x64 per input 256. Fit gaussiano per posizione; sorgente aggiunge 0.01 I alla covarianza e poi 1e-5 I prima dell'inversione. Nessuna modifica a queste costanti.

Score patch: distanza Mahalanobis; mappa bilineare align_corners=False alla dimensione input, blur sigma 4/kernel 33, padding reflect/same. Score immagine = `anomaly_map.amax(dim=(-2,-1))`, verificato anche con uguaglianza esatta nello smoke. Output score [1,1], map [1,1,256,256], float32 CPU. Piu alto = maggiore distanza/anomalia, non probabilita.

Fonti del checkout: [Padim constructor](https://github.com/open-edge-platform/anomalib/blob/cc5f400a4a4b1b14b5a3ee5078063c250a8f522e/src/anomalib/models/image/padim/lightning_model.py#L109), [modello e score](https://github.com/open-edge-platform/anomalib/blob/cc5f400a4a4b1b14b5a3ee5078063c250a8f522e/src/anomalib/models/image/padim/torch_model.py#L109), [mappa](https://github.com/open-edge-platform/anomalib/blob/cc5f400a4a4b1b14b5a3ee5078063c250a8f522e/src/anomalib/models/image/padim/anomaly_map.py), [Gaussiana](https://github.com/open-edge-platform/anomalib/blob/cc5f400a4a4b1b14b5a3ee5078063c250a8f522e/src/anomalib/models/components/stats/multi_variate_gaussian.py).

### PatchCore

`Patchcore`: wide_resnet50_2, layer2/3, pre_trained=True, coreset_sampling_ratio=0.1, num_neighbors=9, precision float32. AvgPool2d(3,1,1), fusione feature 1536x32x32. Fit: KCenterGreedy con SparseRandomProjection eps=0.9, selezione nativa e seed 42.

Nearest-neighbor: **brute-force PyTorch, nessun FAISS**, matrice Euclidea via matmul/norme, clamp_min(0)/sqrt, query chunk 1024. Patch score usa k=1. Image score: massimo patch score pesato con supporto fino a 9 vicini del nearest neighbor (`1-softmax(...)[0]`); non e il massimo della mappa finale. Mappa dei patch score -> upsampling **nearest** (default F.interpolate senza mode) -> blur sigma 4/kernel 33 reflect/same. Score [1], map [1,1,256,256], float32 CPU. Maggiore score = maggiore evidenza di anomalia secondo questa distanza pesata.

Fonti: [constructor/preprocessing/fit](https://github.com/open-edge-platform/anomalib/blob/cc5f400a4a4b1b14b5a3ee5078063c250a8f522e/src/anomalib/models/image/patchcore/lightning_model.py#L141), [forward/NN/score](https://github.com/open-edge-platform/anomalib/blob/cc5f400a4a4b1b14b5a3ee5078063c250a8f522e/src/anomalib/models/image/patchcore/torch_model.py#L321), [map generator](https://github.com/open-edge-platform/anomalib/blob/cc5f400a4a4b1b14b5a3ee5078063c250a8f522e/src/anomalib/models/image/patchcore/anomaly_map.py#L82), [coreset](https://github.com/open-edge-platform/anomalib/blob/cc5f400a4a4b1b14b5a3ee5078063c250a8f522e/src/anomalib/models/components/sampling/k_center_greedy.py). Nessuna sostituzione con Amazon Science/FAISS.

## 3. Pesi pretrained effettivi

Download iniziale solo in setup tramite timm/Hugging Face; file locali safetensors. Gli URL .pth presenti nei metadati timm sono fallback/origine dichiarata, non il formato scaricato in questa esecuzione.

| Backbone / HF repo | Snapshot effettivo | Byte | SHA-256 model.safetensors |
| --- | --- | ---: | --- |
| timm/resnet18.a1_in1k | 491b427b45c94c7fb0e78b5474cc919aff584bbf | 46807446 | 80c49dee3da4822c009c5a7fe591e9223c5a2cfcf95a4067ca4dfb5a7b89c612 |
| timm/wide_resnet50_2.racm_in1k | 30f73aceaaa1911830a9795b83ab1908dba18719 | 275835296 | 03b71d65fb2c73bb0de079a1781009f27a782ec481d2f64ab3bde9b1cdec3000 |

Percorsi completi relativi al repository e pretrained_cfg in [weights.json](benchmark-prerun/weights.json). Cache dedicata `target/prerun/weights/huggingface`; TORCH_HOME dedicato nella stessa directory. Cache Windows senza symlink: warning benigno di duplicazione/spazio, non modifica dei pesi. hf_xet assente, download HTTP ordinario della libreria. Nel run finale usare cache gia verificata/offline e controllare SHA prima di iniziare; non lasciare che un nuovo `main` HF cambi i byte silenziosamente.

## 4. Preprocessing, raw export e geometria

Percorso nativo verificato per entrambi:

```text
PIL RGB originale gia decodificata in RAM
 -> torchvision v2 to_image -> to_dtype(float32, scale=True) [0,1], CHW
 -> module.pre_processor.transform:
      Resize((256,256), bilinear, antialias=True)
      Normalize(ImageNet mean/std)
 -> batch [1,3,256,256]
 -> module.model.eval()(batch), torch.no_grad()
 -> InferenceBatch.pred_score / anomaly_map prima del PostProcessor
```

Conversione RGB e scaling seguono [read_image](https://github.com/open-edge-platform/anomalib/blob/cc5f400a4a4b1b14b5a3ee5078063c250a8f522e/src/anomalib/data/utils/image.py#L300); decode sara escluso dal timer, conversione/scaling e preprocessing inclusi. Sorgente base PaDiM: [configure_pre_processor](https://github.com/open-edge-platform/anomalib/blob/cc5f400a4a4b1b14b5a3ee5078063c250a8f522e/src/anomalib/models/components/base/anomalib_module.py#L309); PatchCore usa la propria implementazione con center_crop_size=None.

**Distinzione necessaria:** [PreProcessor.forward](https://github.com/open-edge-platform/anomalib/blob/cc5f400a4a4b1b14b5a3ee5078063c250a8f522e/src/anomalib/pre_processing/pre_processor.py) applica export_transform, che disattiva antialias. Il percorso nativo callback Lightning usa `.transform`. Si usa quest'ultimo senza cambiare l'algoritmo. Non applicare `module.forward` se fa anche preprocessing export/PostProcessor; il punto raw e il torch model interno, identico a quello delle validation_step native.

Nessun crop/padding geometrico nell'input; resize a tupla non conserva aspect ratio per rettangoli (smoke 300x500 -> 256x256), ma Bottle e Metal Nut sono quadrate. I metadati pretrained timm parlano di bicubic/crop e input 224: non sono le trasformazioni Anomalib applicate. Il blur usa padding reflect interno alla mappa, non cancella parte dell'immagine.

**Gate geometrico VERIFIED:** native map 256x256 copre l'intera immagine; evaluation-space map = bilineare half-pixel, align_corners=False, bordi replicati, senza antialias/normalizzazione aggiuntivi, a Bottle 900x900 o Metal Nut 700x700. Lo smoke converte la mappa a double prima del resize e verifica shape e finitezza. Nessun tentativo di invertire il downsampling: si riproietta una mappa di score sul dominio osservato. L'evaluator futuro deve preservare esattamente le [convenzioni](LOCALIZATION_CONVENTIONS.md), con un controllo mirato dell'adattatore; nessuna metrica finale calcolata qui.

## 5. Data/split gate

Generati con Java 21 reale tramite [splits.jsh](../../tools/prerun/splits.jsh), non con Python Random:

- sort filename Java, shuffle Collections.shuffle(new Random(42)), ordine conservato;
- Bottle 167 fit + 42 calibration, uguaglianza dei due manifest con `target/bottle-spatial14-heldout-calibration` verificata;
- Metal Nut 176 fit + 44 calibration;
- cardinalita, nomi unici, unione completa e disgiunzione verificate;
- competitor-fit ordinati: tutte le 209/220 training-good;
- test manifest 83/115 nomi, enumerati senza decodificarli o usarli nel fit.

File in [benchmark-prerun/data/](benchmark-prerun/data/), hash in [data-sha256.json](benchmark-prerun/data-sha256.json). Un filename puo esistere sia in train che in test: il manifest ha ruolo esplicito e i percorsi si risolvono rispettivamente sotto train/img o test/img. Il futuro runner non deve unirli per basename.

Default MVTec datamodule sorgente: train_batch_size=32, num_workers=8, val_split_mode=SAME_AS_TEST. Per prevenire apprendimento del PostProcessor sul test, il runner usera i manifest e API native di fit senza avviare tale validazione automatica. Fitting batch 32 resta il default nativo pianificato; smoke batch 1 solo per controllo tecnico. Non si cambia il budget ne si usa calibrazione test-good.

## 6. Thread/runtime/hardware manifest

[hardware.json](benchmark-prerun/hardware.json): AMD Ryzen AI 9 HX 370, 12 core fisici/24 logici, RAM 67.772.403.712 byte, Windows 11 Pro 10.0.26200, profilo Bilanciato GUID 381b4222-f694-41f0-9685-ff5bb260df2e. Affinity dell'osservatore 16777215 (0xFFFFFF, 24 bit). Affinity dei futuri processi benchmark **OPEN al run**, non assunta dall'osservatore.

Python: CPU-only (torch.version.cuda=None, cuda.is_available=False), 12 intra-op e 12 inter-op, MKL/oneDNN/OpenMP abilitati dal build; nessun override OMP_NUM_THREADS, MKL_NUM_THREADS, OPENBLAS_NUM_THREADS, OMP_DYNAMIC, MKL_DYNAMIC, KMP_AFFINITY, OMP_PROC_BIND. Nessuna chiamata set_num_threads. Dettagli compiler/backend in environment.json.

Java: encoder SessionOptions default, nessun add-provider o set-intra/inter; runtime 1.30.0 disponibile con provider CPU e AZURE, nessuna GPU. La sessione encoder usa il percorso CPU predefinito. Numero effettivo di worker ORT **unknown: API di produzione non lo espone**, non inferito da JMH. JMH @Threads(1), fork -Xmx2g. Sonda runtime: Java 21.0.9, max heap 2147483648, G1; [java-runtime.txt](benchmark-prerun/java-runtime.txt). Flag JDWP/console nella sonda appartengono a JShell, non al futuro JMH. Il comando diretto JShell e terminato con exit 0.

Nessuna modifica delle impostazioni thread/power. Manifest al run finale obbligatorio: power, affinity, carico di fondo e worker effettivi possono cambiare.

## 7. Memory tooling gate

Implementato un monitor esterno minimo [Measure-ProcessMemory.ps1](../../tools/prerun/Measure-ProcessMemory.ps1): Get-Process -> WorkingSet64, PrivateMemorySize64, PeakWorkingSet64; Win32_Process CIM -> discendenti parent/child. CSV con UTC, tempo trascorso, PID/start-time/root PID e byte. Applicabile senza modificare Java o Python.

Verifiche: sonda Python alloca 32.000.000 byte e il contatore private bytes supera tale valore; 21 campioni nella prima prova. Sonda Java/JShell produce 68 campioni su quattro PID, incluso il processo Java esecutore. File [memory-python-smoke.csv](benchmark-prerun/memory-python-smoke.csv), [memory-java-smoke.csv](benchmark-prerun/memory-java-smoke.csv), [memory-monitor-self-smoke.csv](benchmark-prerun/memory-monitor-self-smoke.csv). Sono prove dello strumento, non stime della memoria PaDiM/PatchCore/Anomalib4j.

Per Phase 2: monitor prima di initialization/fit e fino a ready, con marcatori di fase e successiva finestra steady-state. Working set/RSS-equivalent != private bytes != heap Java != payload tensori. Per il process tree, sommare contatori simultanei per timestamp e prendere il massimo campionato nella finestra; non sommare lifetime peaks di processi diversi. PeakWorkingSet64 e massimo dalla nascita del singolo processo, separato dalla misura del solo fit. Private peak e campionato. I processi terminati fra campioni, PID riusati o figli orfani richiedono attenzione; start-time aiuta a distinguere identita, ma il monitor non e un tracciatore di ogni evento di processo.

100 ms e intervallo richiesto, non garantito: CIM e scheduling aggiungono costo, verificare timestamp. Misurare memoria separatamente dalla latenza per evitare perturbazioni. Nel runner finale seguire i workload PID, non sommare arbitrariamente launcher/JMH driver. I dettagli di lifecycle e marcatori sono implementazione Phase 2, non una nuova ipotesi scientifica.

## 8. Smoke tecnico e risultati

[verify_competitors.py](../../tools/prerun/verify_competitors.py), evidenza [smoke.json](benchmark-prerun/smoke.json).

- Setup: costruttori pre-trained default di entrambi i modelli, pesi scaricati una volta, seed 42, nessun fit finale.
- Offline: nuovo processo, HF_HUB_OFFLINE/TRANSFORMERS_OFFLINE, audit Python che rifiuta socket.connect, DNS e sendto; verificato che un tentativo DNS venga bloccato prima di procedere. I costruttori pretrained caricano la cache e lo smoke termina senza tentativi di rete rilevati. Non e una firewall policy di sistema: il controllo vale per questo processo/percorso Python senza hf_xet.
- Fit nativo su due immagini TRAIN-good: bottle_good_000 e metal_nut_good_000. Piccolo insieme misto solo per provare la completabilita delle API; non e un protocollo di fitting del benchmark finale. Nessun modello smoke salvato come modello scientifico.
- Inferenza su altre due immagini TRAIN-good: bottle_good_001 e metal_nut_good_001; entrambe le shape originali verificate, raw finiti CPU float32 per i due modelli.
- PaDiM effective n_features=100, score uguale al massimo mappa; PatchCore memory bank smoke 204x1536, coreset ratio/9 vicini invariati.
- Nessuna immagine anomaly, maschera o immagine test decodificata; nessuna metrica, soglia, selezione backbone o confronto di performance.

Controlli aggiuntivi: uv pip check passato; checker degli helper Python senza violazioni; split Java passato; checkout upstream pulito. Nessun mvn test/training o benchmark Java rieseguito.

## 9. Comandi effettivi e riproduzione

Da repository root, PowerShell; checkout/cache devono essere preservati per la modalita offline. Usare directory di output nuove per i CSV del monitor (rifiuta overwrite).

```powershell
git clone --depth 1 --branch lib/v2.6.2 https://github.com/open-edge-platform/anomalib.git target/prerun/anomalib-2.6.2
git -C target/prerun/anomalib-2.6.2 rev-parse HEAD
git -C target/prerun/anomalib-2.6.2 rev-parse 'lib/v2.6.2^{commit}'
Push-Location target/prerun/anomalib-2.6.2
uv sync --frozen --extra cpu --no-dev --python 3.11
Pop-Location
$python = 'target/prerun/anomalib-2.6.2/.venv/Scripts/python.exe'
& $python tools/prerun/verify_competitors.py setup
& $python tools/prerun/verify_competitors.py offline
uv pip check --python $python
jshell tools/prerun/splits.jsh
$cp = (Get-Content -Raw target/benchmark-classpath.txt).Trim()
jshell -R-Xmx2g --class-path $cp tools/prerun/runtime.jsh
powershell -NoProfile -File tools/prerun/Verify-MemoryTool.ps1
# Per un workload gia avviato: sostituire 12345 con il suo PID reale.
powershell -NoProfile -File tools/prerun/Measure-ProcessMemory.ps1 -RootPid 12345 -OutputPath target/prerun/memory-new-run.csv -Seconds 60 -IntervalMs 100
```

L'helper genera freeze via `uv pip freeze --python <sys.executable>` e i JSON da ambiente/pesi reali. Setup puo usare rete; offline no. Non eseguire clone sopra un checkout esistente, non usare sync senza --frozen e non cancellare target prima di salvare le evidenze. Il lock e nel checkout, identificato dal commit e SHA; il freeze effettivo e conservato sotto docs. `target/benchmark-classpath.txt` e artefatto Java gia presente e contiene ORT, usato senza ricompilazione.

Hardware acquisito con Get-CimInstance Win32_Processor/Win32_ComputerSystem/Win32_OperatingSystem, affinity via Get-Process, `powercfg /getactivescheme`; comando Java diagnostico `java -XX:+PrintCommandLineFlags -Xmx2g -version`.

Incidenti della verifica, risolti: la prima installazione e stata respinta dalla revisione automatica per il divieto del task precedente; riletta l'autorizzazione corrente, la stessa installazione e passata. CIM nel terminale sandbox aveva accesso negato; la lettura tramite il tool Git Bash/PowerShell e riuscita. Un wrapper Start-Process della sonda Java non ha fornito uno stato di uscita utilizzabile e ha segnalato errore, pur producendo log e campioni; la successiva esecuzione diretta JShell ha confermato exit 0. Nessun errore e stato nascosto come benchmark riuscito.

## 10. Phase 2: implementazione minima progettata, non eseguita

Gate sorgenti/ambiente/pesi/raw/geometria/split/strategia memoria chiusi nei limiti dichiarati. Il prossimo runner deve:

1. Leggere configurazioni/manifests congelati e validarne hash/cardinalita; impedire risoluzione train/test per basename ambiguo.
2. Preparare immagini originali decodificate; applicare la trasformazione nativa dentro la regione cronometrata. Fit per categoria, non l'insieme misto dello smoke; full training budget per competitor, held-out Java invariato.
3. Chiamare API native model/fit, senza Engine validation/test-fitting del PostProcessor; default fit batch 32 per competitor e registrazione della politica data-loader. Preservare lo score immagine nativo e i blur interni.
4. Esportare score/map lossless, mapping bilineare double controllato contro convenzioni Java e confronti su fixture sintetiche prima di metriche reali. Nessuna riscrittura delle metriche se e riutilizzabile l'evaluator corrente.
5. Misurare native full e confine full-resolution quando distinto; sessioni riusate, initialization/fit/ready separati; campioni, fasi memoria e PID espliciti. Una replica del timer non e una nuova API detector.
6. Produrre il layout `target/comparison/<run-id>/` gia previsto nel [contract](BENCHMARK_CONTRACT.md), senza sovrascrivere evidenze storiche; i dettagli del timer e delle fasi vanno verificati prima del run finale.

Nessun implementazione di tale runner in questa Phase 1. Nessuna ottimizzazione, FAISS, ablation o nuova categoria.

## 11. OPEN e blocker residui

- Nessun blocker attualmente osservato per iniziare Phase 2. Lo smoke non prova tempo/memoria o completabilita dei fitting su 209/220 immagini: rimangono da misurare, non stimati come risultati.
- Runner, conversione/export lossless verso evaluator e lifecycle di monitoraggio ancora da implementare/verificare. Finche assenti, il benchmark finale non e eseguibile secondo contract.
- Numero worker ORT effettivo unknown con motivo; non blocca Native/System, ma impedisce claim single-native-thread. Affinity dei workload, power/carico, data-loader e intervallo monitor effettivi vanno registrati nelle future run.
- Pretrained cache offline e hash verificati localmente; archiviazione durevole dei byte e dipendenze prima di `mvn clean` resta necessaria. Provenienza export dell'encoder Java conserva i limiti gia documentati.
- Memoria picco campionata puo perdere transienti; figli brevi/orfani non garantiti. Esporre questi limiti e non equiparare contatori.
- Nessuna conclusione su accuratezza, ranking fra metodi o superiorita Anomalib4j deriva da questa verifica.

## 12. File e resoconto finale

Modificati: `docs/benchmark/BENCHMARK_CONTRACT.md`, `docs/PROJECT_STATE.md`. `docs/DECISION_LOG.md` invariato: si verificano dettagli tecnici di decisioni esistenti, nessuna nuova scelta scientifica.

Creati: questo report; `tools/prerun/verify_competitors.py`, `splits.jsh`, `runtime.jsh`, `Measure-ProcessMemory.ps1`, `Verify-MemoryTool.ps1`; evidenze `docs/benchmark/benchmark-prerun/{source,environment,weights,smoke,hardware,configurations,data-sha256}.json`, `environment.freeze.txt`, manifest sotto `data/bottle` e `data/metal_nut`, log runtime e CSV sonde memoria. Checkout, venv e cache sotto `target/prerun/` (ignorati da Git).

Controlli degli helper: responsabilita separate (smoke competitor, split Java, sonda runtime, campionamento memoria, sonda allocazione); nessun helper Python oltre 200 righe di codice, nessun escape hatch di typing introdotto. Le asserzioni dello smoke verificano comportamento reale e falliscono su output non finiti/geometria/dtype errati. Il monitor e osservato su processi reali. I controlli non sono test di accuracy o benchmark finali.

Nessuna modifica a sorgenti Java/Maven esistenti, dataset o iperparametri dopo risultati; nessun commit. Stato raggiunto: Phase 1 verificata, Phase 2 pronta alla sola implementazione minima prevista dal contract.
