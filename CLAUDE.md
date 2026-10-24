# CLAUDE.md

## Progetto

**Traccia** (nome provvisorio) è un'app Android nativa per **pianificare percorsi outdoor e generare file GPX** direttamente dalla mappa, per qualsiasi sport: escursionismo, trail running, corsa, bici da strada, gravel, MTB, scialpinismo e altri.

L'ispirazione è il route planner dell'app Suunto: disegni il percorso a tocchi su una mappa topografica semplice ma completa, e il tracciato si aggancia ai sentieri e alle strade. In più l'app aggiunge due cose che altrove non si trovano insieme:

1. **Overlay della Strava Global Heatmap**, filtrabile per sport, per vedere quali sentieri e strade vengono davvero frequentati.
2. **Funzionamento completamente offline**: mappe topografiche scaricate per regione e heatmap salvata in cache mentre la si visualizza online.

Il risultato finale è un GPX pulito da esportare verso l'app Suunto (o qualsiasi altro dispositivo e app).

L'app è **open source, senza introiti, per uso personale**.

## Obiettivi

- Pianificazione rapida e intuitiva: pochi tocchi, nessun menu superfluo.
- Mappa topografica leggibile: curve di livello, ombreggiatura del rilievo, sentieri, rifugi, sorgenti.
- Routing offline con profili per sport diversi.
- Heatmap Strava come layer opzionale: l'app deve funzionare benissimo anche senza.
- Tutto deve funzionare senza rete, una volta scaricati i dati.

## Interazione di pianificazione (requisito fondamentale)

La pianificazione deve funzionare **esattamente come il Suunto Routeplanner**: semplice, minimale, senza domande. L'utente disegna a mano un percorso lineare, un punto dopo l'altro.

Le uniche interazioni ammesse sono queste:

| Gesto | Effetto |
|---|---|
| Primo tap sulla mappa | Crea il primo punto: la **partenza** |
| Ogni tap successivo sulla mappa | Aggiunge un punto e collega il percorso **dal punto precedente** a quello nuovo |
| Tap su un punto esistente | Compare **una sola opzione: "Cancella"** |
| Pressione prolungata su un punto | Il punto si **trascina** (drag and drop) per spostarlo; i tratti adiacenti si ricalcolano |
| Pulsante **Undo** | Rimuove **l'ultimo punto inserito** |
| Pulsante **Chiudi anello** | Aggiunge in coda un tratto dall'ultimo punto alla partenza, chiudendo il percorso ad anello |
| Pulsante **Cancella tutto** | Svuota il percorso per ricominciare da zero (nessuna conferma, snackbar "Ripristina" per qualche secondo) |

Regole di design da rispettare sempre:

- **Nessun popup, dialog o menu durante il disegno.** Niente richieste su che tipo di punto sia (checkpoint, tappa, meta, waypoint): ogni punto è semplicemente il punto successivo del percorso.
- Il percorso è **sempre una sequenza lineare**: i punti si aggiungono solo in coda. Non si inseriscono punti intermedi trascinando un tratto.
- L'ultimo punto è implicitamente l'arrivo; non esiste un concetto separato di "meta".
- Il tap su un punto esistente non deve mai aggiungere un nuovo punto.
- Cancellando un punto intermedio, i due punti vicini vengono ricollegati automaticamente.
- Se un tratto non è calcolabile, viene collegato in linea retta tratteggiata, senza chiedere nulla.
- I controlli a schermo sono solo tre pulsanti (Undo, Chiudi anello, Cancella tutto) più le statistiche del percorso. Le impostazioni (sport, profilo di routing) stanno fuori dalla modalità di disegno.
- In caso di dubbio su una nuova funzione di pianificazione: **se aggiunge un passaggio o una domanda all'utente, non va fatta.**

## Non obiettivi (per ora)

- Registrazione delle attività e statistiche di allenamento.
- Social, community, condivisione online.
- Versione iOS o web.
- Sincronizzazione diretta con gli orologi: si passa sempre da export GPX.

## Stack tecnico

| Ambito | Scelta |
|---|---|
| Linguaggio | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Mappa | MapLibre Native Android |
| Mappa base offline | MBTiles vettoriali (schema OpenMapTiles) generati da OSM con Planetiler |
| Rilievo | Curve di livello e hillshade da DEM Copernicus (GLO-30) |
| Routing | BRouter (motore offline, file segmenti `.rd5`) |
| Rete | OkHttp |
| Persistenza | Room (percorsi, metadati), file MBTiles (mappe e cache heatmap) |
| DI | Hilt |
| Async | Coroutines + Flow |
| Min SDK | 26 |

## Architettura

Architettura a strati con moduli Gradle separati:

```
app/                 # entry point, navigazione, DI
core/
  common/            # utilità, modelli condivisi, Result/errori
  geo/               # coordinate, distanze, semplificazione tracce, quote
  storage/           # Room, accesso ai file MBTiles
feature/
  map/               # schermata mappa, gestione layer
  offlinemaps/       # catalogo regioni, download, aggiornamento
  heatmap/           # login Strava, cookie, tile source, cache
  planner/           # disegno percorso, punti, undo/redo
  routing/           # wrapper BRouter, profili per sport
  elevation/         # profilo altimetrico, dislivelli
  gpx/               # parser/writer GPX, import/export, share
  library/           # elenco percorsi salvati
  settings/          # preferenze
```

Pattern: **MVVM + Unidirectional Data Flow**. Ogni feature espone uno `UiState` immutabile, un ViewModel e una sola schermata (o più) Compose. La logica di dominio sta in use case puri e testabili, senza dipendenze Android quando possibile.

### Stack dei layer sulla mappa (dal basso)

1. Mappa base vettoriale offline (OSM)
2. Hillshade
3. Curve di livello
4. **Heatmap Strava** (raster, opacità regolabile)
5. Percorso in pianificazione + punti di controllo
6. Posizione utente

## Heatmap Strava: regole

Questa parte vive in una zona grigia rispetto ai termini di Strava. Va implementata **solo** così:

- L'accesso avviene **con l'account dell'utente**: login su strava.com dentro una WebView, poi lettura dei cookie di sessione con `CookieManager`. Mai credenziali salvate dall'app.
- Le tile vengono richieste **solo quando servono alla visualizzazione** (area e zoom a schermo).
- **Vietato il pre-download massivo** di aree della heatmap, anche se tecnicamente semplice.
- La cache contiene solo tile già visualizzate. Ogni tile registra la data di download.
- Le tile in cache **non vengono mai esportate, condivise o incluse** nel repository o nei rilasci.
- Se i cookie scadono, l'app degrada con grazia: usa la cache e propone un nuovo login.
- Identificatori di sport e URL delle tile vanno in configurazione, non nel codice, perché Strava può cambiarli.

## Convenzioni

- Codice e identificatori in inglese; stringhe UI in italiano con supporto alla localizzazione (`strings.xml`).
- Commit in stile Conventional Commits (`feat:`, `fix:`, `refactor:`…).
- Ogni use case di dominio ha test unitari. La parte GPX e geo ha test con file di esempio in `src/test/resources`.
- Niente logica nei Composable: solo rendering dello stato ed eventi verso il ViewModel.
- Le operazioni pesanti (download, routing, generazione GPX) girano fuori dal main thread e sono annullabili.
- Gestione errori esplicita: niente eccezioni silenziate, niente `!!`.

## Comandi

```bash
./gradlew assembleDebug        # build debug
./gradlew test                 # test unitari
./gradlew connectedAndroidTest # test strumentali
./gradlew ktlintCheck detekt   # lint e analisi statica
```

## Documentazione correlata

- `features.md`: elenco dettagliato delle funzionalità, organizzate in milestone. È il riferimento per sapere cosa implementare e in che ordine.
