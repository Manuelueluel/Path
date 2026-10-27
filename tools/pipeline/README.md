# Pipeline delle mappe offline

Genera i pacchetti regione scaricati dall'app (M2.1). Ogni regione è composta da tre file MBTiles:

| File | Contenuto | Fonte |
|---|---|---|
| `<id>-basemap-<data>.mbtiles` | Mappa base vettoriale, schema OpenMapTiles, z0–14 | OSM (Geofabrik) con Planetiler |
| `<id>-trails-<data>.mbtiles` | Difficoltà dei sentieri (`sac_scale`, `mtb:scale`, `trail_visibility`), z12–14 | OSM con lo schema `trails.yml` |
| `<id>-dem-<data>.mbtiles` | DEM terrarium WebP 512 px, z0–12: hillshade, curve di livello e quote nell'app | Copernicus GLO-30 |
| `<tile>-<data>.rd5` | Segmenti di routing BRouter 5°×5° (es. `E5_N45`), condivisi tra regioni vicine | brouter.de |

## Requisiti

- Java 21+ (Planetiler viene scaricato in automatico)
- Python 3 con `rasterio`, `numpy`, `pillow`:
  ```bash
  python3 -m venv ~/.cache/traccia-pipeline/venv
  ~/.cache/traccia-pipeline/venv/bin/pip install rasterio numpy pillow
  ```
- Spazio disco: ~3 GB di sorgenti globali (oceani, Natural Earth) al primo avvio, più l'estratto OSM e qualche GB temporaneo.

Cache predefinita: `~/.cache/traccia-pipeline` (variabile `TRACCIA_PIPELINE_CACHE`). Output: `tools/pipeline/dist/` (variabile `TRACCIA_PIPELINE_DIST`, ignorata da git).

## Generare una regione

```bash
tools/pipeline/build_region.sh lombardia
```

Le regioni sono definite in `regions.json` (id, nome, estratto Geofabrik, bounding box). La versione dei dati è la data di generazione. La regione `test-lecco` è piccola (~20 MB) e si genera in pochi minuti: serve per provare l'app.

## Pubblicare

1. Generare il catalogo con l'URL base della release `maps`, escludendo la regione di test:
   ```bash
   python3 tools/pipeline/make_catalog.py --dist tools/pipeline/dist --exclude test-lecco \
     --base-url https://github.com/Manuelueluel/Path/releases/download/maps
   ```
   Lo script prepara `tools/pipeline/dist/upload/` con `catalog.json` e tutti i file da caricare, una sola volta ciascuno.
2. Caricare il contenuto di `dist/upload/` come asset della release con tag `maps` (limite 2 GB per file; GitHub supporta le richieste `Range` per la ripresa dei download):
   - **dal sito**: Releases → crea o modifica la release `maps` → trascina i file di `dist/upload/`. Per aggiornare un file già presente, prima va cancellato quello vecchio;
   - **con GitHub CLI**: `gh release upload maps tools/pipeline/dist/upload/* --clobber`.
   - **a lotti** (connessione lenta): `tools/pipeline/upload_regions.py veneto [altre regioni...]` carica solo i file mancanti delle regioni indicate (crea la release se non c'è), poi sostituisce `catalog.json` con queste regioni più quelle già pubblicate per intero. Il catalogo sale per ultimo, quindi l'app non vede mai regioni incomplete; se l'upload si interrompe basta rilanciare. `--dry-run` mostra cosa caricherebbe.
3. `catalogUrl` in `core/terrain/src/main/assets/map_sources.json` punta già a `.../releases/download/maps/catalog.json`.

Per aggiornare una regione basta rigenerarla, rifare il catalogo e ricaricare: l'app confronta le versioni e propone l'aggiornamento. I segmenti `.rd5` prendono la data del download da brouter.de (rinnovato ogni settimana), non quella della regione: le regioni generate nella stessa settimana condividono lo stesso file, che l'app installa una volta sola.

## Provare senza pubblicare

```bash
python3 tools/pipeline/make_catalog.py --dist tools/pipeline/dist --base-url http://localhost:8000
mkdir -p /tmp/traccia-serve && ln -sf "$PWD"/tools/pipeline/dist/upload/* /tmp/traccia-serve/
(cd /tmp/traccia-serve && python3 -m http.server 8000)

# telefono collegato via USB: la porta 8000 del telefono punta al computer
adb reverse tcp:8000 tcp:8000
./gradlew installDebug -Ptraccia.catalogUrl=http://localhost:8000/catalog.json
```

Con l'emulatore usare `http://10.0.2.2:8000` sia in `--base-url` sia in `traccia.catalogUrl`. Le build debug accettano HTTP in chiaro solo verso `localhost` e `10.0.2.2`.

## Note

- I bounding box sono volutamente abbondanti: i sentieri a cavallo dei confini restano utilizzabili. I dati OSM però arrivano solo fin dove copre l'estratto Geofabrik (es. nessun dato svizzero nell'estratto nord-ovest).
- Le tile della heatmap Strava non passano mai di qui e non vanno mai pubblicate (vedi `CLAUDE.md`).
