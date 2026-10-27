#!/usr/bin/env bash
# Builds the offline pack of one region: OpenMapTiles basemap, trail difficulty overlay and DEM.
#
#   tools/pipeline/build_region.sh <region-id>
#
# Requirements: Java 21+, Python 3 with rasterio, numpy, pillow (see README.md), ~10 GB free disk.
# Output: $DIST/<region-id>/ with the .mbtiles files and region.json (sizes, SHA-256, version).
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
CACHE="${TRACCIA_PIPELINE_CACHE:-$HOME/.cache/traccia-pipeline}"
DIST="${TRACCIA_PIPELINE_DIST:-$HERE/dist}"
PYTHON="${PYTHON:-$CACHE/venv/bin/python}"
JAVA="${JAVA:-java}"
JAVA_MEM="${JAVA_MEM:-8g}"
PLANETILER_VERSION="0.10.2"
DEM_MAXZOOM=12

REGION="${1:?usage: build_region.sh <region-id>}"
VERSION="$(date -u +%Y-%m-%d)"

read -r NAME EXTRACT BBOX < <(python3 - "$HERE/regions.json" "$REGION" <<'PY'
import json, sys
regions = {r["id"]: r for r in json.load(open(sys.argv[1]))["regions"]}
r = regions.get(sys.argv[2]) or sys.exit(f"unknown region {sys.argv[2]}; known: {', '.join(regions)}")
print(r["name"].replace(" ", "_"), r["extract"], ",".join(str(v) for v in r["bbox"]))
PY
)
NAME="${NAME//_/ }"
OUT="$DIST/$REGION"
mkdir -p "$CACHE/osm" "$CACHE/dem" "$CACHE/bin" "$CACHE/planetiler" "$OUT"

echo "==> $NAME ($REGION) bbox=$BBOX version=$VERSION"

JAR="$CACHE/bin/planetiler.jar"
if [[ ! -f "$JAR" ]]; then
  echo "==> downloading Planetiler $PLANETILER_VERSION"
  curl -fL -o "$JAR.part" "https://github.com/onthegomap/planetiler/releases/download/v$PLANETILER_VERSION/planetiler.jar"
  mv "$JAR.part" "$JAR"
fi

PBF="$CACHE/osm/$(basename "$EXTRACT")-latest.osm.pbf"
# Refresh the extract when older than a week (Geofabrik updates daily).
if [[ ! -f "$PBF" ]] || [[ -n "$(find "$PBF" -mtime +7)" ]]; then
  echo "==> downloading OSM extract $EXTRACT"
  curl -fL -o "$PBF.part" "https://download.geofabrik.de/$EXTRACT-latest.osm.pbf"
  mv "$PBF.part" "$PBF"
fi

BASEMAP="$OUT/$REGION-basemap-$VERSION.mbtiles"
TRAILS="$OUT/$REGION-trails-$VERSION.mbtiles"
DEM="$OUT/$REGION-dem-$VERSION.mbtiles"

echo "==> basemap (OpenMapTiles)"
# Run from the cache so Planetiler keeps its global sources (water polygons, Natural Earth) there.
(cd "$CACHE/planetiler" && "$JAVA" -Xmx"$JAVA_MEM" -jar "$JAR" \
  --osm-path="$PBF" --bounds="$BBOX" --download --force \
  --languages=it,en,de,fr --transportation-z13-paths=true \
  --output="$BASEMAP")

echo "==> trails overlay"
(cd "$CACHE/planetiler" && "$JAVA" -Xmx"$JAVA_MEM" -jar "$JAR" generate-custom \
  --schema="$HERE/trails.yml" --osm_path="$PBF" --bounds="$BBOX" --force \
  --output="$TRAILS")

echo "==> DEM (Copernicus GLO-30)"
"$PYTHON" "$HERE/dem_tiles.py" --bbox="$BBOX" --maxzoom="$DEM_MAXZOOM" --cache="$CACHE/dem" --output="$DEM"

echo "==> routing segments (BRouter)"
# 5x5 degree BRouter tiles covering the bbox; shared by neighbouring regions.
# A tile the bbox only grazes (less than MARGIN degrees) is skipped: it would add ~100-250 MB
# for a strip with no useful routing.
SEGMENTS=()
for TILE in $(python3 - "$BBOX" <<'PY'
import math, sys
MARGIN = 0.05
w, s, e, n = (float(v) for v in sys.argv[1].split(","))
w, s, e, n = w + MARGIN, s + MARGIN, e - MARGIN, n - MARGIN
def name(lon, lat):
    return ("W%d" % -lon if lon < 0 else "E%d" % lon) + "_" + ("S%d" % -lat if lat < 0 else "N%d" % lat)
for lat in range(math.floor(s / 5) * 5, math.ceil(n / 5) * 5, 5):
    for lon in range(math.floor(w / 5) * 5, math.ceil(e / 5) * 5, 5):
        print(name(lon, lat))
PY
); do
  SRC="$CACHE/segments/$TILE.rd5"
  mkdir -p "$CACHE/segments"
  if [[ ! -f "$SRC" ]] || [[ -n "$(find "$SRC" -mtime +7)" ]]; then
    echo "  downloading $TILE.rd5"
    curl -fL -o "$SRC.part" "https://brouter.de/brouter/segments4/$TILE.rd5"
    mv "$SRC.part" "$SRC"
  fi
  # Named after the segment's own date, not the region's: regions built from the same download
  # share one identical file, which the app installs only once.
  DST="$OUT/$TILE-$(date -u -r "$SRC" +%Y-%m-%d).rd5"
  rm -f "$OUT/$TILE"-*.rd5
  ln -f "$SRC" "$DST" 2>/dev/null || cp "$SRC" "$DST"
  SEGMENTS+=("$DST")
done

echo "==> metadata"
python3 - "$OUT/region.json" "$REGION" "$NAME" "$VERSION" "$BBOX" "$BASEMAP" "$TRAILS" "$DEM" "${SEGMENTS[@]}" <<'PY'
import hashlib, json, os, sys
out, rid, name, version, bbox, basemap, trails, dem = sys.argv[1:9]
segments = sys.argv[9:]

def entry(kind, path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return {"type": kind, "name": os.path.basename(path), "size": os.path.getsize(path), "sha256": h.hexdigest()}

region = {
    "id": rid,
    "name": name,
    "version": version,
    "bbox": [float(v) for v in bbox.split(",")],
    "files": [entry("basemap", basemap), entry("trails", trails), entry("dem", dem)],
}
for path in segments:
    e = entry("routing", path)
    # BRouter looks segment files up by tile name: the app saves them under "target".
    e["target"] = os.path.basename(path).split("-")[0] + ".rd5"
    region["files"].append(e)
json.dump(region, open(out, "w"), indent=2, ensure_ascii=False)
total = sum(f["size"] for f in region["files"]) / 1e6
print(f"  {name}: {total:.1f} MB")
PY
echo "==> done: $OUT"
