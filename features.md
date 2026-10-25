# features.md

Funzionalità di **Path**, organizzate in milestone. Ogni milestone è un blocco logico che, una volta completato, lascia l'app in uno stato usabile e testabile. L'ordine delle milestone è l'ordine di sviluppo consigliato.

Legenda: ogni punto ha un identificativo (es. `M2.3`) da usare in commit, issue e branch.

---

## M0 — Fondamenta del progetto

Obiettivo: progetto compilabile, struttura a moduli, strumenti di qualità attivi.

- **M0.1** Progetto Android con moduli Gradle come da `CLAUDE.md` e version catalog (`libs.versions.toml`).
- **M0.2** Setup Hilt, Compose, Material 3, tema chiaro/scuro.
- **M0.3** Navigazione tra schermate (Navigation Compose) con schermata mappa come home.
- **M0.4** Room configurato con prima migrazione vuota.
- **M0.5** ktlint, detekt e test unitari funzionanti da riga di comando.
- **M0.6** CI (GitHub Actions): build, lint e test su ogni push.
- **M0.7** Gestione permessi (posizione, notifiche per i download) con UI di spiegazione.

**Fatto quando:** l'app si avvia su una schermata vuota e la CI è verde.

---

## M1 — Mappa topografica

Obiettivo: una mappa topografica leggibile e fluida, prima online e poi offline.

- **M1.1** Integrazione MapLibre Native in una schermata Compose (pan, zoom, rotazione, bussola).
- **M1.2** Stile topografico custom (JSON) basato sullo schema OpenMapTiles: sentieri evidenziati per difficoltà (`sac_scale`, `mtb:scale`), strade bianche, rifugi, sorgenti, cime con quota.
- **M1.3** Layer curve di livello con etichette di quota.
- **M1.4** Layer hillshade.
- **M1.5** Posizione utente con pulsante "centra su di me".
- **M1.6** Scala grafica e indicatore di quota/coordinate del punto al centro.
- **M1.7** Selettore layer: attiva o disattiva curve, hillshade e (in seguito) heatmap.
- **M1.8** Stile di mappa alternativo minimale per leggere meglio la heatmap sopra.

**Fatto quando:** si naviga una mappa topo completa di una zona di test.

---

## M2 — Mappe offline

Obiettivo: scaricare regioni e usarle senza rete.

- **M2.1** Pipeline (script fuori dall'app) per generare MBTiles vettoriali per regione con Planetiler, più curve e hillshade da DEM Copernicus.
- **M2.2** Catalogo regioni (es. regioni italiane, poi altri paesi) con dimensione e data dei dati.
- **M2.3** Download in background (WorkManager) con avanzamento, pausa, ripresa e annullamento.
- **M2.4** Verifica integrità dei file scaricati (checksum).
- **M2.5** Gestione spazio: elenco mappe installate, dimensione occupata, eliminazione.
- **M2.6** Aggiornamento mappe: avviso quando esiste una versione più recente.
- **M2.7** Fallback automatico: usa le mappe offline quando presenti, le tile online altrimenti.
- **M2.8** Scelta della posizione di salvataggio (memoria interna o scheda SD).

**Fatto quando:** in modalità aereo la mappa di una regione scaricata è completa.

---

## M3 — Heatmap Strava

Obiettivo: overlay heatmap per sport, con cache per l'uso offline. Rispettare le regole in `CLAUDE.md`.

- **M3.1** Schermata di login: WebView su strava.com, rilevamento del login riuscito.
- **M3.2** Lettura e salvataggio sicuro dei cookie di sessione (EncryptedSharedPreferences).
- **M3.3** Interceptor OkHttp che aggiunge i cookie alle sole richieste verso il dominio delle tile heatmap, impostato come client HTTP di MapLibre.
- **M3.4** Sorgente raster heatmap con URL e identificatori di sport presi da configurazione.
- **M3.5** Selettore sport: tutti, bici, corsa, escursionismo, MTB, gravel, sport invernali, sport acquatici (in base a quelli che Strava rende disponibili).
- **M3.6** Selettore colore della heatmap e opacità regolabile.
- **M3.7** Cache locale: ogni tile visualizzata viene salvata in un MBTiles dedicato per sport, con data di download.
- **M3.8** Lettura dalla cache quando offline o quando la tile è recente; scaricamento quando è più vecchia della soglia configurata.
- **M3.9** Pulsante "aggiorna heatmap in quest'area" (solo le tile a schermo).
- **M3.10** Indicatore dell'età dei dati heatmap nell'area visualizzata.
- **M3.11** Gestione scadenza dei cookie: avviso non bloccante, uso della cache, invito al nuovo login.
- **M3.12** Gestione cache: dimensione occupata per sport, svuotamento.
- **M3.13** Logout con eliminazione dei cookie.

**Fatto quando:** la heatmap si vede online sopra la topo e, dopo averla guardata, resta visibile anche offline.

---

## M4 — Routing

Obiettivo: calcolare tratti di percorso che seguono sentieri e strade, offline.

- **M4.1** Integrazione del motore BRouter come libreria.
- **M4.2** Download dei file segmenti `.rd5` per l'area delle mappe offline (riusa la logica di M2).
- **M4.3** Profili per sport: escursionismo, trail running, corsa, bici da strada, gravel, MTB, scialpinismo, più un profilo "linea retta" (nessun routing).
- **M4.4** Opzioni di profilo esposte nella UI: evitare strade trafficate, preferire sterrato, evitare scalinate e simili.
- **M4.5** Calcolo per singolo tratto (tra due punti consecutivi) con annullamento se l'utente modifica il percorso nel frattempo.
- **M4.6** Gestione errori: se un tratto non è calcolabile (punto non raggiungibile, dati mancanti), viene collegato **automaticamente in linea retta** e mostrato con uno stile diverso (es. tratteggiato). Nessun dialog.
- **M4.7** Cache dei tratti già calcolati per evitare ricalcoli.

**Fatto quando:** dati due punti offline, si ottiene un tratto che segue i sentieri secondo il profilo scelto.

---

## M5 — Pianificazione percorso

Obiettivo: il cuore dell'app, disegnare percorsi **esattamente come nel Suunto Routeplanner**. Vedi la sezione "Interazione di pianificazione" in `CLAUDE.md`: niente popup, niente domande, percorso lineare disegnato a mano.

- **M5.1** Primo tap sulla mappa: crea il primo punto, la **partenza**.
- **M5.2** Ogni tap successivo sulla mappa: aggiunge un punto in coda e calcola il tratto **dal punto precedente** a quello nuovo.
- **M5.3** Tap su un punto esistente: mostra **solo l'opzione "Cancella"**. Cancellando un punto intermedio, i suoi due vicini vengono ricollegati con un nuovo tratto. Il tap su un punto non aggiunge mai un nuovo punto.
- **M5.4** Pressione prolungata su un punto: avvia il **drag and drop**; al rilascio si ricalcolano solo i tratti adiacenti.
- **M5.5** Pulsante **Undo**: rimuove l'ultimo punto inserito e il tratto che lo collegava.
- **M5.6** Pulsante **Cancella tutto**: svuota il percorso e si riparte dal primo tap. Nessun dialog di conferma; al suo posto una snackbar "Ripristina" di pochi secondi per recuperare il percorso in caso di errore.
- **M5.6b** Pulsante **Chiudi anello**: aggiunge in coda un ultimo punto coincidente con la partenza e calcola il tratto dall'ultimo punto alla partenza. Attivo solo con almeno 2 punti e quando il percorso non è già chiuso. Undo lo annulla come qualsiasi altro punto; dopo la chiusura, un nuovo tap sulla mappa continua il percorso dalla partenza.
- **M5.7** Hit area dei punti abbastanza ampia da distinguere con affidabilità il tap su un punto dal tap sulla mappa.
- **M5.8** Statistiche live sempre visibili e discrete: distanza, dislivello positivo e negativo, tempo stimato per sport.
- **M5.9** Sport e profilo di routing scelti **fuori** dalla modalità di disegno (impostazioni del percorso); cambiandoli, l'intero percorso si ricalcola.
- **M5.10** Salvataggio del percorso con nome e sport (unico momento in cui si chiede qualcosa all'utente, a disegno finito); ripresa della modifica in seguito.
- **M5.11** Salvataggio automatico della bozza per non perdere il lavoro.

Esplicitamente **esclusi** dalla pianificazione: inserimento di punti intermedi trascinando un tratto, tipi di punto (checkpoint, tappa, meta), menu di scelta su come inserire un punto, altre funzioni automatiche come "andata e ritorno" o "inverti percorso".

**Fatto quando:** si disegna, corregge, chiude ad anello e salva un percorso usando solo tap, pressione prolungata e i tre pulsanti, offline.

---

## M6 — Profilo altimetrico

Obiettivo: capire subito com'è il percorso in salita e discesa.

- **M6.1** Quote dei punti del percorso dal DEM offline.
- **M6.2** Smussamento del profilo per un dislivello realistico (evitare il rumore del DEM).
- **M6.3** Grafico del profilo sotto la mappa, a scomparsa.
- **M6.4** Interazione: scorrendo sul grafico, un indicatore si muove sulla mappa e viceversa.
- **M6.5** Colorazione dei tratti per pendenza, sul grafico e sulla mappa.
- **M6.6** Dati dettaglio: quota minima e massima, pendenza massima, tratti più ripidi.

**Fatto quando:** ogni percorso mostra un profilo altimetrico interattivo coerente con la mappa.

---

## M7 — GPX e libreria percorsi

Obiettivo: far uscire ed entrare i percorsi dall'app.

- **M7.1** Writer GPX 1.1 (traccia e/o rotta), con nome, descrizione, quote e waypoint.
- **M7.2** Opzione di semplificazione della traccia (numero di punti ridotto) per dispositivi con limiti.
- **M7.3** Export tramite share intent (verso l'app Suunto, Drive, email…) e salvataggio su file.
- **M7.4** Import GPX da file e da share intent di altre app.
- **M7.5** Import come percorso modificabile: conversione della traccia in punti di controllo.
- **M7.6** Libreria percorsi: elenco con anteprima, sport, distanza, dislivello, data.
- **M7.7** Ricerca, filtro per sport, ordinamento.
- **M7.8** Duplicazione, rinomina, eliminazione.
- **M7.9** Visualizzazione di più percorsi insieme sulla mappa.

**Fatto quando:** un percorso creato si esporta, si importa nell'app Suunto e funziona sull'orologio.

---

## M8 — Rifinitura e impostazioni

Obiettivo: app stabile, piacevole e configurabile.

- **M8.1** Schermata impostazioni: unità di misura, sport predefinito, soglia di aggiornamento heatmap, stile mappa.
- **M8.2** Velocità di riferimento per sport, per stimare i tempi.
- **M8.3** Onboarding breve: download prima regione, login Strava facoltativo.
- **M8.4** Accessibilità: contrasto, dimensioni testo, descrizioni per TalkBack.
- **M8.5** Ottimizzazione prestazioni: avvio, rendering dei percorsi lunghi, memoria.
- **M8.6** Backup ed esportazione completa della libreria percorsi (archivio GPX).
- **M8.7** Pagina "Informazioni" con licenze (OSM, Copernicus, BRouter, MapLibre) e nota sulla heatmap.

**Fatto quando:** l'app è pronta per un primo rilascio pubblico (F-Droid / APK).

---

## M9 — Futuro (da valutare)

Idee per dopo la prima versione, non pianificate.

- **M9.1** Navigazione lungo il percorso con avviso di uscita dalla traccia.
- **M9.2** Waypoint personalizzati con icone (acqua, pericolo, punto panoramico).
- **M9.3** Ricerca luoghi offline (cime, rifugi, paesi).
- **M9.4** Vista 3D del terreno.
- **M9.5** Overlay aggiuntivi: piste da sci, vie ferrate, zone protette.
- **M9.6** Registrazione semplice della traccia percorsa.
- **M9.7** Versione tablet con layout dedicato alla pianificazione.
