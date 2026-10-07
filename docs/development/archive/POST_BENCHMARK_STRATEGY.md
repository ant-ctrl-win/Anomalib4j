# Anomalib4j dopo i benchmark: decisione strategica

Data: 2 ottobre 2026. Perimetro: ispezione del repository e delle fonti, nessuna modifica al codice e nessuna nuova esecuzione di training o benchmark.

## 1. Decisione e significato delle etichette

**INFERENCE — Decisione:** sospendere l'ottimizzazione prestazionale come attivita principale. Il prossimo investimento dovrebbe chiarire **quanto del risultato dipende dalla memoria VSA**, attraverso un confronto controllato con teste semplici sulle stesse feature, esteso a una seconda categoria. Subito dopo, misurare il compromesso accuratezza/costo contro implementazioni esistenti sullo stesso hardware. Riservare il lavoro incrementale a un esperimento delimitato, con confronti che possano smentire un vantaggio specifico VSA.

**INFERENCE:** questa sequenza non assegna un punteggio arbitrario alle opzioni. Prima risolve l'incertezza che cambia il posizionamento scientifico; poi quella competitiva; infine valuta una possibile capacita distintiva. Ridurre il preprocessing risolverebbe un problema reale, ma oggi non distinguerebbe un nuovo metodo da una buona implementazione di una testa statistica su feature frozen.

- **VERIFIED:** riscontro nel codice, negli artefatti conservati o in una fonte primaria specificata. Per risultati storici significa verifica documentale, non nuova riproduzione dell'esperimento.
- **INFERENCE:** conseguenza ragionata delle evidenze, inclusa una derivazione algebrica; non una nuova misura.
- **HYPOTHESIS:** spiegazione o vantaggio da verificare mediante un esperimento falsificabile.
- **OPEN:** dato mancante o conclusione che le evidenze attuali non consentono.

## 2. Stato scientifico

### Risultati da tenere separati

**VERIFIED:** Bottle contiene 209 training-good e 83 immagini test, 20 good e 63 anomale. SPATIAL_14 restituisce 14x14x96 feature; D=10000, seed di proiezione 42. Il test non entra nella stima delle statistiche o degli archetipi. Fonti: [Sprint 6](SPRINT6_RESULT.md), [Sprint 7](../SPRINT7_RESULT.md), [censimento](../../benchmark/MVTEC_DATASET_CENSUS.md).

**VERIFIED — Metriche storiche:**

| Protocollo | Image AUROC raw / Z | Pixel AUROC raw / Z | AUPRO@0.30 raw / Z |
| --- | --- | --- | --- |
| 209 training; calibrazione sugli stessi 209 | 0.985714 / 1.000000 | 0.956950 / 0.961102 | 0.865347 / 0.876670 |
| 167 training + 42 calibration, seed split 42 | 0.985714 / 1.000000 | 0.956927 / 0.961664 | 0.865098 / 0.877447 |
| 167+42, media split seed 1..5 | 0.985714 / 1.000000 | 0.956977 / 0.960423 | 0.865513 / 0.876453 |

Fonti: [in-sample](../../benchmark/POSITIONAL_CALIBRATION_RESULT.md), [held-out](../../benchmark/HELDOUT_CALIBRATION_RESULT.md), [multi-seed](../../benchmark/HELDOUT_MULTI_SEED_RESULT.md). I valori held-out e multi-seed sono stati anche confrontati con i rispettivi CSV sotto target. Nel report in-sample le metriche pixel raw sono riferimenti congelati dello Sprint 8; nel confronto held-out entrambi i rami sono misurati sullo stesso modello.

**VERIFIED:** nei cinque split il delta calibrated-minus-raw e positivo per tutte e tre le metriche, cinque volte su cinque. Per Pixel AUROC il delta medio e 0.003446, deviazione standard campionaria 0.001660; per AUPRO e 0.010940, deviazione 0.004279, intervallo osservato 0.005244..0.016052. La deviazione tra split della AUPRO calibrata e 0.004285. Nessuna posizione raggiunge il floor sigma=1e-6.

**INFERENCE:** la calibrazione corregge differenze posizionali effettive, non soltanto un artefatto della stima in-sample. Il risultato held-out rende questa conclusione piu convincente.

**OPEN:** i cinque esperimenti condividono lo stesso test e subset di training sovrapposti. Non sono cinque dataset indipendenti, non forniscono una prova generale di significativita e non misurano variabilita del seed di proiezione. AUROC=1 su 20 good/63 anomalie non equivale ad assenza di errori in produzione. La calibrazione Z non produce probabilita di anomalia; non implica gaussianita delle code ne una soglia operativa validata.

**VERIFIED:** [Sprint 9](SPRINT9_RESULT.md) ottiene con SPATIAL_28 Image AUROC=1, ma Pixel AUROC=0.901402 e AUPRO=0.705556. Cambiano profondita CNN e canali oltre alla risoluzione; non e un esperimento sulla sola densita della griglia.

**INFERENCE:** il buon ranking image-level non basta a giudicare la localizzazione. Il risultato SPATIAL_28 rende poco giustificato inseguire subito piu patch o un backbone diverso senza un'ipotesi specifica.

### Che cosa dimostra l'Adjoint

**VERIFIED:** lo Sprint 7 confronta 16268 celle test, con errore massimo explicit/compiled di 1.3073986337985843e-12, contro tolleranza 1e-9. Il codice [AdjointCompiler](../../../src/main/java/io/github/antctrlwin/anomalib4j/adjoint/AdjointCompiler.java) compila il prodotto scalare VSA in pesi e bias CNN; [PositionalMemoryBuilder](../../../src/main/java/io/github/antctrlwin/anomalib4j/memory/PositionalMemoryBuilder.java) somma vettori standardizzati e normalizza la somma.

**INFERENCE — Conclusione forte:** e dimostrata una compilazione numericamente fedele dello **score definito dal modello**, non una ricostruzione generale dei vettori CNN o un'inversione della proiezione. L'identita del prodotto scalare con l'aggiunto non necessita di un'ipotesi Johnson-Lindenstrauss; preserva la formula implementata. Non certifica automaticamente l'equivalenza con cosine similarity VSA dotata di una diversa normalizzazione.

**INFERENCE — Il controllo scientifico mancante:** poniamo u=x/max(||x||,epsilon), T=diag(1/sigma_VSA) R/sqrt(D) e c=-mu_VSA/sigma_VSA. A statistiche congelate, il vettore standardizzato e Tu+c. Per N osservazioni alla posizione p:

```text
S_p = sum_n (T u_np + c) = T sum_n u_np + N c
A_p = S_p / ||S_p||
score_p(u) = A_p^T(Tu+c) = (T^T A_p)^T u + A_p^T c
```

**INFERENCE:** la memoria posizionale corrente dipende dalla somma delle feature normalizzate, oltre alle statistiche globali della proiezione. La somma non conserva separatamente modalita, pose o casi individuali. La dimensione 10000 di uno spazio raggiunto attraverso una mappa affine da 96 canali non dimostra, da sola, un aumento della capacita informativa. La standardizzazione VSA puo indurre una geometria utile; questo e il possibile contributo da isolare, non una ricchezza automaticamente garantita dal numero di dimensioni.

**HYPOTHESIS:** questa geometria e il bundling possono dare un compromesso migliore di accuratezza, dati richiesti e aggiornabilita rispetto a una memoria CNN piu semplice.

**OPEN:** manca il confronto che lo dimostri. Anche un centroide CNN puo essere aggiornato senza backpropagation e compilato in uno score economico. Una baseline CNN che riproducesse la stessa trasformazione e le stesse statistiche sarebbe un controllo di equivalenza della rappresentazione, non un metodo indipendente da battere. Non si propone alcuna forma quadratica nella hot path.

## 3. Stato prestazionale

**VERIFIED:** i p50 sotto coincidono con i JSON conservati in target/benchmark, letti durante questa ispezione. CPU Ryzen AI 9 HX 370, Windows 11, Java 21.0.9, ORT 1.30.0, JMH 1.37; un caller JMH, batch 1. Fixture: una stessa Bottle 900x900 precaricata; preparazione del modello esclusa.

| Percorso | p50 | Perimetro |
| --- | --- | --- |
| compiled Adjoint | 12.0 us | 196 celle, feature gia pronte, raw=1-score |
| positional Z | 0.2 us | 196 raw score gia pronti; misura sub-microsecondo |
| detection completa | 19.824640 ms | immagine RAM, encoder, raw, Z, summary/max |
| localization completa | 24.444928 ms | detection piu upsampling a 900x900 |
| upsampling bilineare isolato | 2.965504 ms | mappa 14x14 gia pronta |
| preprocessing baseline / encoder profile / ultimo profile | 12.615680 / 12.632064 / 12.271616 ms | stesso comportamento; run diversi |
| sessionRunAndClose | 0.549888 ms | tensore pronto, run e chiusura Result |
| lettura RGB sorgente / materializzazione RGB | 6.782976 / 4.055040 ms | fasi isolate |
| resize / lettura RGB ridotta / ciclo numerico NCHW | 1.067008 / 0.204800 / 0.085248 ms | fasi isolate |

Fonti: [baseline](../PERFORMANCE_BASELINE_RESULT.md), [encoder](ENCODER_MICROPROFILE_RESULT.md), [preprocessing](PREPROCESSING_MICROPROFILE_RESULT.md), e i tre documenti VALIDATION omonimi per i confini delle misure.

**VERIFIED:** i p95 end-to-end sono 23.035904 ms detection e 27.885568 ms localization; i p99 24.739840 e 29.544940 ms. Sono percentili sul workload ripetuto, non SLA di un impianto. Il benchmark usa il modello held-out 167+42 seed 42, non il modello full-209 associato alla prima riga della tabella scientifica.

**VERIFIED:** il vecchio confronto appaiato dello Sprint 7 misura 651.0842 ms explicit contro 13.0121 us compiled, circa 50036.8x sul solo scoring. Il successivo JMH compiled misura 12 us; dividere il vecchio tempo explicit per il nuovo valore JMH non produce un nuovo speedup sperimentalmente appaiato.

**INFERENCE:** il vantaggio ingegneristico interno e enorme e concreto: toglie dal runtime un'elaborazione inutile per calcolare quello score. Non e uno speedup di 50000x dell'intero sistema, ne un confronto contro PatchCore o PaDiM. Un prodotto scalare per posizione e una baseline runtime gia economica: i 12 us diventano competitivamente distintivi solo insieme a qualita, memoria e costo di apprendimento/aggiornamento.

**VERIFIED:** il report JFR descrive campioni concentrati in getRGB/setRGB, raster e ColorModel, con allocazioni dominate da int[]. L'ultimo micro-profile concorda nell'indicare l'accesso/materializzazione RGB come fasi isolate costose. Qui non e stata ripetuta l'analisi dell'intera registrazione JFR.

**INFERENCE:** esiste una candidata area di miglioramento Java concreta. Non segue che eliminarla conservi automaticamente tutti i comportamenti di color model/alpha, ne che il risparmio isolato si trasferisca integralmente all'end-to-end.

### Incongruenze e limiti della documentazione

- **VERIFIED:** PROJECT_STATE.md e storico: elenca pixel metrics e SPATIAL_28 tra le cose non stabilite, ma report successivi le misurano. Per questa decisione prevalgono esperimenti specifici e codice corrente.
- **VERIFIED:** ENCODER_MICROPROFILE_RESULT.md scrive in un punto che sessionRunAndClose include la chiusura della sessione. Il codice chiude il Result a ogni operazione; la sessione solo in teardown.
- **OPEN:** l'affermazione del report baseline che 12 us e 13 us siano "statisticamente indistinguibili" non e supportata da un test riportato. Sono dello stesso ordine; non e la stessa affermazione.
- **OPEN:** la differenza tra end-to-end e tempi isolati non e spiegata causalmente dai report. Attribuirla soltanto a varianza e troppo forte: cache, gestione delle risorse, GC e interazioni del runtime possono contribuire. Non ricavare un tempo CNN per sottrazione e non sommare mediane.
- **VERIFIED:** JMH impone un caller; l'encoder usa SessionOptions predefinite. **OPEN:** numero effettivo di worker nativi, consumo CPU complessivo ed energia. Non chiamare il risultato "CPU single-native-thread".
- **OPEN:** il vecchio ONNX_MODEL_CONTRACT.md documenta limiti sulla provenienza dei pesi rispetto al checkpoint timm. La pipeline Java corrente e identificata e riproducibile; una catena completa di provenienza/export resta da documentare prima di una pubblicazione esterna. Non e una ragione per cambiare oggi il preprocessing congelato.

## 4. Risposte dirette alle domande strategiche

**INFERENCE — 20 ms sono gia abbastanza?** Sono sufficienti per proseguire la valutazione scientifica e per molte demo interattive. Sono compatibili, sul workload misurato, con un budget inferenza da 30 frame/s; non certificano 30 frame/s per acquisizione, decode, code, UI e controllo macchina. Non soddisfano invece una scadenza inferenza da 10 ms. **OPEN:** target applicativo, p99 richiesto, hardware di destinazione e carico concorrente. Senza questi requisiti, un altro sprint di ottimizzazione e prematuro come priorita strategica, non tecnicamente inutile.

**INFERENCE — I 12 ms di preprocessing?** Sono un costo implementativo rilevante, non un difetto dimostrato del paradigma. Diventano strategici quando impediscono uno SLA o alterano un confronto di deployment. Per giudicare il metodo, separare il confronto sulle medesime feature dal confronto completo. Se una futura applicazione richiede bassa latenza o una CPU piu debole, questa e una candidata area da affrontare; oggi non e la domanda a maggiore incertezza.

**INFERENCE — I 12 us dimostrano originalita?** Dimostrano che il modello appreso puo essere eseguito come testa compatta e che l'implementazione realizza bene questa compilazione. L'identita lineare dell'aggiunto e standard; senza controlli non dimostra un nuovo algoritmo di anomaly detection o un vantaggio esclusivo VSA. Il valore professionale della verifica numerica e del profiling resta reale anche se la novita scientifica risultasse limitata.

**INFERENCE — Confrontarsi prima di ottimizzare?** Si, prima di inseguire la competitivita generale servono misure locali contro baseline esistenti, dopo o insieme alla piccola ablation controllata. Non serve implementare subito tutti gli algoritmi. Due confronti rispondono a domande diverse: stessa CNN/testa diversa per attribuzione scientifica; configurazioni native dei metodi per competitivita di sistema.

**INFERENCE — Esperimento di massimo valore informativo?** Un confronto appaiato fra VSA compilato, centroide CNN e modello diagonale posizionale sulle identiche feature frozen, con e senza la stessa calibrazione held-out, su Bottle e una categoria preregistrata. Potrebbe dimostrare un contributo del VSA oppure mostrare che la maggior parte del risultato viene dalla CNN e dalla calibrazione. Entrambi gli esiti impediscono sviluppo inutile.

**OPEN — Che cosa manca all'originalita credibile?** Un vantaggio attribuito al meccanismo, risultati oltre Bottle, confronti riproducibili con metodi semplici e consolidati, costo totale di apprendimento/aggiornamento e memoria, studio di lavori precedenti specifico sulla combinazione proposta. Non sono ancora dimostrate proprieta simboliche quali binding/unbinding o ragionamento composizionale: il codice corrente verifica proiezione, bundling e score compilato. La formula "neuro-simbolico" da sola non costituisce un risultato sperimentale.

## 5. Opzioni strategiche e rischi

**INFERENCE — Stime di impegno:** intervalli indicativi per una persona che conosce il repository, in giornate di lavoro, non preventivi o nuovi tempi misurati. Dipendenze, export e disponibilita di GPU possono allungarli. Si basano sull'estensione richiesta, non su una classifica di preferenza.

### 1. Ottimizzazione end-to-end Java/ONNX

- **VERIFIED:** profiling disponibile; RGB e una parte consistente del preprocessing, testa Adjoint gia economica.
- **HYPOTHESIS:** ridurre copie/conversioni conservando esattamente il contratto migliora p50/p95 reali.
- **INFERENCE:** 2–5 giorni per un intervento delimitato con regressioni; piu tempo per compatibilita generale dei formati. Informazione ottenuta: margine di deployment, non valore statistico della memoria.
- **INFERENCE:** rischio marginale alto finche manca uno SLA: una demo piu veloce non risolve l'assenza di confronti. Posizionamento: engineering Java/edge piu forte, novita del metodo invariata.
- **INFERENCE — Arresto:** fissare prima hardware, budget p95/p99 e regressioni ammesse. Fermarsi quando il budget e soddisfatto o se il guadagno non si ripete in end-to-end in run indipendenti. Non sostituire il preprocessing se cambia le feature senza un esperimento distinto.

### 2. Confronti con Anomalib, PatchCore, PaDiM, EfficientAD

- **VERIFIED:** [Anomalib](https://github.com/open-edge-platform/anomalib) e una libreria che implementa piu metodi, non un singolo concorrente. [PatchCore](https://openaccess.thecvf.com/content/CVPR2022/html/Roth_Towards_Total_Recall_in_Industrial_Anomaly_Detection_CVPR_2022_paper.html) usa una memoria rappresentativa di patch nominali e studia anche pochi esempi; [PaDiM](https://arxiv.org/abs/2011.08785) modella distribuzioni gaussiane multivariate di embedding per patch. [EfficientAD](https://arxiv.org/abs/2303.14535) affronta esplicitamente il compromesso qualita/latenza con un estrattore leggero; le latenze pubblicate su GPU non sono confrontabili direttamente con questi tempi Java CPU.
- **HYPOTHESIS:** Anomalib4j occupa un punto utile nel compromesso qualita, latenza e memoria rispetto a questi metodi.
- **INFERENCE:** 4–8 giorni per un confronto accurato iniziale con PatchCore e PaDiM; altri 2–5 per EfficientAD, variabili con training e runtime. Informazione ottenuta: competitivita esterna. Rischio: spendere settimane in conversioni/export o scambiare differenze di backbone/runtime per superiorita algoritmica.
- **INFERENCE — Arresto:** prima riprodurre input, numero immagini e metriche di almeno una baseline. Se non sono conciliabili, sospendere le affermazioni comparative invece di aumentare il numero dei metodi. Se Anomalib4j e dominato in qualita, latenza e memoria sul medesimo scenario, abbandonare la pretesa di superiorita generale e verificare solo eventuali nicchie di aggiornamento/deployment.

### 3. Seconda categoria piu impegnativa

- **VERIFIED:** il censimento offre Cable: 224 training-good, 150 test, 58 good/92 anomalie, otto tipi di difetto. Non ci sono risultati Anomalib4j per questa categoria nei documenti esaminati.
- **HYPOTHESIS:** la memoria di un archetipo per posizione resiste a maggiore variabilita strutturale e a piu tipi di difetto. Cable e una scelta motivata per questo stress; che sia piu difficile per questo modello resta da misurare.
- **INFERENCE:** 2–4 giorni per adattamento minimo dell'evaluation e controlli delle maschere; evitare un framework multicategoria. Informazione ottenuta: confine di generalizzazione; posizionamento meno dipendente da un solo caso favorevole.
- **INFERENCE — Arresto:** scegliere Cable prima di vedere i risultati e non sostituirla se va male. Dopo la verifica di integrita dei dati, un fallimento va riportato e diagnosticato; niente tuning ripetuto sul suo test. Una seconda categoria positiva giustifica estensione, non affermazioni su tutte le 15 categorie.

### 4. Few-shot e aggiornamento senza backpropagation

- **VERIFIED:** la pipeline ha CNN frozen, Welford e bundling per somma. La versione reale costruisce statistiche e archetipi in due passaggi; non esiste ancora nei risultati una validazione di apprendimento incrementale.
- **HYPOTHESIS:** aggiornamenti compatti consentono qualita utile con pochi dati e minor costo/memoria rispetto a riaddestramento o memoria di patch.
- **INFERENCE:** 4–8 giorni per un protocollo iniziale. E la direzione potenzialmente piu legata alla proposta VSA, ma anche la piu facile da sovrainterpretare: un centroide o una Gaussiana diagonale sono anch'essi aggiornabili, e PatchCore non richiede necessariamente backpropagation sul backbone.
- **INFERENCE:** se mu/sigma VSA restano congelati, la somma puo essere estesa; se cambiano, cambiano anche i vettori standardizzati gia accumulati. Dopo cambiamenti del filtro anche mu/sigma dei raw score devono essere rivalutati. Un archetipo unitario e il conteggio da soli non conservano la norma della somma originale. Misurare tutto questo costo, non soltanto l'aggiunta di un vettore.
- **INFERENCE — Arresto:** se a memoria/qualita comparabili un centroide aggiornabile offre gli stessi benefici, cessare di rivendicarli come esclusivi VSA. Se l'aggiornamento non coincide col batch entro tolleranza a statistiche fisse, correggere il protocollo prima di studiare drift. Nessuna promessa di robustezza alla contaminazione senza test dedicato.

### 5. Alignment e invarianza

- **VERIFIED:** archetipi e calibrazione dipendono dalla posizione. **HYPOTHESIS:** una quota degli errori fuori Bottle dipende dalla posa, non dall'assenza di feature discriminanti.
- **INFERENCE:** 1–3 giorni per un test di sensibilita, 1–3 settimane per un intervento di alignment verificato. Informazione: se il problema sia davvero geometrico. Rischio alto di introdurre preprocessing specifico, cancellare difetti geometrici o migliorare solo una categoria.
- **INFERENCE — Arresto:** non implementare alignment prima di misurare errori legati alla posa. Ruotare/traslare immagini e maschere insieme, controllando artefatti di bordo e interpolazione; non dichiarare invarianti trasformazioni che cambiano l'etichetta, ad esempio difetti di posizione/orientamento. Fermare la direzione se gli errori restano con posa controllata o se si nascondono anomalie semanticamente rilevanti.

### 6. Demo/reference implementation Java

- **VERIFIED:** core, test di equivalenza e benchmark separati esistono; parte di calibrazione/evaluation vive nei test e i modelli sono costruiti in memoria.
- **HYPOTHESIS:** una dimostrazione riproducibile della separazione apprendimento/runtime ha valore professionale anche senza un primato accademico.
- **INFERENCE:** 3–7 giorni per una demo ristretta con istruzioni, artefatti identificati e limiti visibili; distribuzione robusta del modello richiedera lavoro ulteriore. Informazione scientifica bassa, valore portfolio alto per qualita dell'ingegneria e chiarezza dei compromessi.
- **INFERENCE — Arresto:** limitarsi a caricamento immagine, score/heatmap e riproduzione del risultato dichiarato. Niente nuova piattaforma MLOps. Congelare la ricerca e ragionevole se la priorita personale e il portfolio o se i controlli negano un contributo VSA; per sostenere originalita scientifica e ancora presto.

## 6. Sequenza proposta: esperimenti che cambiano la decisione

### Esperimento 1 — Attribuzione del vantaggio, su Bottle e Cable

**INFERENCE — Protocollo proposto:** congelare encoder, preprocessing, feature map, test e metriche. Confrontare tre teste: modello VSA corrente, centroide posizionale CNN con distanza angolare, Gaussiana diagonale posizionale sulle medesime feature normalizzate. Le ultime due sono baseline semplici, non un'implementazione nominale di PaDiM. Per ogni testa misurare raw e la stessa procedura Z held-out, usando gli stessi subset. Fissare formule e regolarizzazioni prima del test, senza selezionarle in base al test.

**INFERENCE:** conservare i cinque split Bottle gia definiti. Per Cable preregistrare split deterministici 179/45 sui 224 good e seed 1..5, con la stessa regola sort/shuffle. Calcolare le tre metriche sul suo test completo; riportare per difetto senza scegliere dopo quelli favorevoli. Usare differenze appaiate e intervalli di incertezza per immagini, non bootstrap dei pixel come osservazioni indipendenti. Per AUPRO preservare immagini e regioni durante il ricampionamento. Le ripetizioni degli split misurano un'altra fonte di variabilita.

**OPEN:** Bottle e gia stato usato per scegliere varianti: anche senza leakage nel fitting, esiste adattamento del progetto al suo test. Cable deve fungere da verifica preregistrata. Il seed di proiezione resta 42 in questa prima ablation per non confondere le cause; una successiva verifica dei seed di proiezione e necessaria se emerge un vantaggio attribuito al VSA.

**INFERENCE — Decisione:** se il VSA non migliora qualita o costo rispetto alle teste dirette in nessuno dei due casi, interrompere l'espansione VSA per sola accuratezza e considerare demo/compilazione come contributo principale. Se migliora solo Bottle, limitare il claim. Se il vantaggio compare anche su Cable, passare al confronto esterno. Stabilire prima un margine di utilita applicativa; senza un requisito, non inventare una soglia universale di un punto AUROC.

### Esperimento 2 — Competitivita sullo stesso hardware

**INFERENCE — Protocollo proposto:** iniziare da PatchCore e PaDiM in una versione/commit fissata di Anomalib, poi EfficientAD se il confronto velocita/qualita resta decisivo. Due tabelle distinte:

1. **INFERENCE — Controllo sulle feature:** dove possibile, stesso tensore CNN e stesso budget di dati; misura del solo rilevatore, RAM della memoria e costo di fitting. Le varianti a backbone comune vanno nominate come adattamenti.
2. **INFERENCE — Sistema nativo:** configurazioni documentate di ciascun metodo, preprocessing richiesto e backbone dichiarati; immagine RAM in ingresso, score o mappa alla medesima risoluzione in uscita. Misurare anche il costo completo dell'acquisizione solo in una tabella applicativa distinta.

**INFERENCE:** usare gli stessi file e maschere DatasetNinja verificati. Esportare gli score senza normalizzazioni sul test; ricomputare Pixel AUROC e AUPRO con LOCALIZATION_CONVENTIONS.md, senza smoothing aggiunto unilateralmente. Se un metodo ha postprocessing nativo, dichiararlo e misurarlo; non chiamare replica ufficiale una variante modificata. Distinguere budget comune 167+42 dal regime nativo full-training. EfficientAD puo richiedere dati/pesi ausiliari: riportarli, non fingere identita dei budget.

**INFERENCE:** fissare batch, warmup, thread applicativi e limiti nativi dove esposti, provider, precisione, versioni, risoluzione, numero di core disponibili; misurare p50/p95/p99, RAM residente, dimensione modello/memoria, training e tempo aggiornamento. Se i limiti nativi non sono controllabili in tutti i sistemi, dichiarare il confronto come configurazione CPU di default, non parita single-thread. Ripetere in processi separati; non derivare throughput sostenuto dal reciproco del p50.

**INFERENCE — Decisione:** cercare un compromesso non dominato rispetto a un requisito concreto, non un vincitore unico. Se la penalita di deployment e concentrata nel preprocessing e cambia questa decisione, allora aprire uno sprint di ottimizzazione con controllo end-to-end e regressioni. Se manca qualita, qualche millisecondo in meno non colma quel divario.

### Esperimento 3 — Sample efficiency e aggiornamento, solo con confronto diretto

**INFERENCE — Protocollo proposto:** curve con 5, 10, 20, 50 esempi nominali per costruire gli archetipi, subset annidati e ripetuti; stesso budget per le baseline. Tenere un calibration set disgiunto e dichiarare che "5-shot + 42 calibration" usa 47 immagini normali, non cinque. Per studiare un vero budget totale di cinque, occorrera un protocollo diverso: con sigma campionaria e pochi esempi questa e una domanda separata, non un'estensione implicita.

**INFERENCE:** nella prima prova incrementale congelare CNN, R e statistiche VSA su un insieme iniziale dichiarato. Aggiungere blocchi nominali, confrontare aggiornamento e ricostruzione batch sugli stessi dati con le stesse statistiche. Includere ricompilazione e ricalibrazione nel tempo di aggiornamento; misurare RAM trattenuta e peggioramento sui casi precedenti. Confrontare con aggiornamento del centroide/diagonale e costo di manutenzione di PatchCore. Il test resta escluso da fitting e scelta dei parametri.

**HYPOTHESIS:** il VSA offre un vantaggio di memoria/aggiornamento a qualita simile. **INFERENCE — Decisione:** proseguire solo se questo vantaggio sopravvive al costo completo e ai controlli CNN semplici; altrimenti chiudere il claim specifico, mantenendo l'implementazione come risultato tecnico utile. Drift, forgetting e contaminazione sono esperimenti successivi, non proprieta gia acquisite.

## 7. Posizionamento credibile e condizioni di chiusura

**INFERENCE — Presentabile oggi:** "Reference implementation Java di anomaly detection posizionale con encoder frozen, memoria VSA e compilazione affine degli score; equivalenza numerica verificata su immagini reali, calibrazione held-out stabile su Bottle, profiling completo e limiti documentati". E una descrizione tecnica sostenuta dalle evidenze.

**OPEN — Non ancora presentabile come fatto:** superiorita rispetto allo stato dell'arte, novita universale del principio Adjoint, vantaggio few-shot esclusivo VSA, invarianza alla posa, robustezza al drift, probabilita calibrate, latenza garantita in produzione o trasferibilita alle 15 categorie.

**INFERENCE:** per un risultato originale credibile servono: ablation negativa o positiva pubblicabile, seconda categoria non scelta a posteriori, confronto a budget dichiarato, provenienza di modello/dataset e versione esatta degli artefatti, risultati riproducibili da terzi, descrizione della novita rispetto ai lavori precedenti. Un semplice centroide compilabile deve essere il primo avversario sperimentale. La validazione numerica e una condizione di correttezza; il vantaggio rispetto a quel controllo sarebbe l'evidenza del contributo aggiuntivo.

**INFERENCE — Regola finale di investimento:** non aprire una nuova linea solo perche e implementabile. Aprirla se un esito puo cambiare una decisione: usare VSA invece di memoria diretta, usare Anomalib4j invece di un baseline esistente, aggiornare invece di ricostruire, o soddisfare uno SLA reale. Se i primi due esperimenti non mostrano una nicchia utile, una demo Java onesta e ben documentata e una conclusione professionale valida, non un fallimento da nascondere con altro tuning.

## 8. Tracciabilita dell'ispezione

**VERIFIED:** letti AGENTS.md, PROJECT_STATE.md e i sei documenti prestazionali VALIDATION/RESULT richiesti; letti Sprint 6–9, risultati delle calibrazioni in-sample/held-out/multi-seed, LOCALIZATION_CONVENTIONS.md, ONNX_MODEL_CONTRACT.md e censimento. Ispezionati builder, compiler, filter bank, calibrazione e perimetri JMH; confrontati i p50 dei tre JSON benchmark e i CSV held-out/multi-seed. Le fonti primarie esterne sono collegate nella sezione 5. Nessuna nuova metrica di accuracy o latenza e stata generata in questo lavoro.

**OPEN:** questa ispezione non e una replica indipendente delle annotazioni, dei test lunghi o dei conteggi JFR, ne una revisione sistematica esaustiva della letteratura. Tutti i file preesistenti rimangono invariati; viene creato soltanto questo documento.
