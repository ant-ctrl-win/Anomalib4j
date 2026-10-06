# Riconciliazione del contract benchmark

Data: 2026-10-03. Attivita esclusivamente documentale e di progettazione.

## File modificati/creati

- Aggiornato [PROJECT_STATE.md](../PROJECT_STATE.md): perimetro due categorie, budget, confronto Native/System, chiusura fase, stato effettivo dell'attivita e future work.
- Aggiornato [DECISION_LOG.md](../DECISION_LOG.md): D10-D14 aggiunte dopo D09.
- Creato [BENCHMARK_CONTRACT.md](BENCHMARK_CONTRACT.md): protocollo, verifiche preliminari, confini di misura, schema artefatti e criteri di completamento.
- Creato questo resoconto.

## Decisioni registrate

| ID | Decisione |
| --- | --- |
| D10 | Confronto, sintesi/limiti, README/repository e chiusura di questa fase |
| D11 | Bottle e Metal Nut, configurazioni fissate prima dei risultati, budget 167+42 e 176+44 |
| D12 | Native/System primario; PaDiM/PatchCore candidati Anomalib 2.6.2; controllato rinviato |
| D13 | Score/map raw nativi, evaluator comune e risultati per categoria; niente score-only artificiale |
| D14 | Costi initialization/fit/ready/inference e memoria espliciti; completamento su sei casi |

L'ordine comparativo prima dell'ablazione resta DECIDED. La questione VSA/direct latent-space memory resta OPEN / DEFERRED, senza nuova interpretazione scientifica.

## Verifiche documentali e locali

Letti AGENTS, stato e decisioni canonici, censimento, convenzioni metriche, documenti benchmark/microprofile/ottimizzazione e protocollo held-out pertinenti. Ispezionati `PerformanceBaseline` e `HeldOutBottleCalibration` per fixture, regioni e shuffle. Consultati i report di performance come misure storiche, non rieseguite.

Enumerazione locale e header PNG: Bottle 209 train-good, 83 test (20 good/63 anomaly), 900x900; Metal Nut 220 train-good, 115 test (22 good/93 anomaly), 700x700. Annotazioni omonime presenti per tutte queste immagini. Conteggi Metal Nut per difetto: bent 25, color 22, flip 23, scratch 23. Nessuna nuova decodifica/valutazione delle maschere: le convenzioni restano quelle gia consolidate.

Split Metal Nut 176+44 ora DECIDED grazie alla conferma dei 220 file; non e ancora stato costruito un modello Metal Nut. Lo shuffle futuro deve essere quello Java con seed 42, non una permutazione Python con lo stesso seed.

Baseline performance preservata: p50 preprocessing 1.380352 ms, fullExtract 2.752512 ms, detection 2.789376 ms, localization 7.462912 ms; Adjoint 12 us/196 celle dalla precedente run JMH. Numeri Bottle su HX 370, non predizioni della latenza Metal Nut o dei competitor.

## OPEN prima dell'esecuzione

1. Fonti/tag/commit esatto e configurazioni native di Anomalib 2.6.2; nessuna conferma inventata dei default. Le fonti esterne non sono incluse nel materiale locale ispezionato; nessuna installazione/download eseguita.
2. Versioni risolte Python/PyTorch/dipendenze, backbone/pesi/hash, thread effettivi, parametri PaDiM/PatchCore e percorso di export raw prima delle trasformazioni visuali.
3. Geometria di crop/padding/mappe e allineamento alla GT originale. Nessuno stretching arbitrario di mappe cropped; confine native/full-resolution distinto.
4. Harness comparativo e adattatori sperimentali Metal Nut ancora da implementare in un task successivo. I runner attuali sono Bottle-specifici.
5. Strumenti di memoria Windows, confini initialization/ready, dettagli del campionamento e archivio durevole degli artefatti.

Questi OPEN riguardano l'esecuzione del contract, non riaprono categorie, ordine della roadmap o allocazioni gia decise.

## Discrepanze e limiti rilevati

- Il precedente 'Bottle inizialmente / seconda categoria candidata' e superato dalla decisione esplicita Bottle + Metal Nut. Documenti storici lasciati invariati.
- La precedente generica sospensione di packaging non esclude ora README e preparazione repository: questi sono passi di chiusura. Nuova CLI/persistenza non necessaria resta future work.
- `HeldOutBottleCalibration` controlla 209 filename Bottle e `PerformanceBaseline` impone fixture 900x900: il benchmark Metal Nut non e gia eseguibile senza futuri adattatori; non dichiarato IMPLEMENTED.
- 132 oggetti-bitmap Metal Nut nel censimento non certificano 132 regioni PRO dopo unione/connessione.
- La latenza Java full-resolution non va presentata come identica perimetralmente a una mappa competitor piu piccola. Il contract richiede shape, confine nativo e, se necessario, misura separata fino alla mappa originale.
- Il budget totale e comparabile ma il fitting usa allocazioni diverse, esplicitamente accettate. Niente equivalenza causale fra metodi o confronto equal-fit-budget implicito.
- Root PROJECT_STATE/TASK e rimandi AGENTS restano storici/obsoleti come gia segnalato; nessun file ulteriore modificato.

## Controlli e perimetro effettivo

Sola lettura di documenti/sorgenti, enumerazione filesystem, lettura header PNG e controllo dei riferimenti Markdown. Nessun test, benchmark, training, installazione, commit o modifica sorgenti Java. Le modifiche preesistenti in `pom.xml`, `ImageNetPreprocessor.java`, `LocalizationMaps.java` e altri file sono state lasciate intatte. Nessun artefatto di risultati rigenerato.

Il contract e pronto per guidare il prossimo task di verifica delle configurazioni e implementazione minima. Il benchmark comparativo non e ancora completato e non produce nuovi claim di superiorita.
