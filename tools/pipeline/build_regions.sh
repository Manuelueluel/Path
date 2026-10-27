#!/usr/bin/env bash
# Builds several region packs in a row, logging the outcome and duration of each one.
#
#   tools/pipeline/build_regions.sh <region-id>...
#
# The OSM extracts the regions need are downloaded in parallel up front, so the first regions
# build while the other extracts are still downloading. A failed region does not stop the batch.
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
CACHE="${TRACCIA_PIPELINE_CACHE:-$HOME/.cache/traccia-pipeline}"

if [[ $# -eq 0 ]]; then
  echo "usage: build_regions.sh <region-id>..." >&2
  exit 2
fi

declare -A EXTRACT_OF PREFETCH_PID
while read -r ID EXTRACT; do
  EXTRACT_OF[$ID]="$EXTRACT"
done < <(python3 - "$HERE/regions.json" "$@" <<'PY'
import json, sys
regions = {r["id"]: r for r in json.load(open(sys.argv[1]))["regions"]}
for rid in sys.argv[2:]:
    if rid not in regions:
        sys.exit(f"unknown region {rid}; known: {', '.join(regions)}")
    print(rid, regions[rid]["extract"])
PY
) || exit 2

mkdir -p "$CACHE/osm"
for EXTRACT in $(printf '%s\n' "${EXTRACT_OF[@]}" | sort -u); do
  PBF="$CACHE/osm/$(basename "$EXTRACT")-latest.osm.pbf"
  # Same freshness rule as build_region.sh, which then finds the file ready.
  if [[ ! -f "$PBF" ]] || [[ -n "$(find "$PBF" -mtime +7)" ]]; then
    echo "### prefetch $EXTRACT"
    (curl -fsSL --retry 5 -o "$PBF.part" "https://download.geofabrik.de/$EXTRACT-latest.osm.pbf" \
      && mv "$PBF.part" "$PBF" && echo "### prefetch $EXTRACT done $(date +%H:%M:%S)") &
    PREFETCH_PID[$EXTRACT]=$!
  fi
done

BATCH_START=$SECONDS
FAILED=()
for ID in "$@"; do
  EXTRACT="${EXTRACT_OF[$ID]}"
  if [[ -n "${PREFETCH_PID[$EXTRACT]:-}" ]]; then
    echo "### $ID waiting for $EXTRACT"
    # A failed prefetch is not fatal: build_region.sh retries the download itself.
    wait "${PREFETCH_PID[$EXTRACT]}" || echo "### prefetch $EXTRACT failed"
    unset "PREFETCH_PID[$EXTRACT]"
  fi
  echo "### $ID start $(date +%H:%M:%S)"
  START=$SECONDS
  if "$HERE/build_region.sh" "$ID"; then
    echo "### $ID OK in $(((SECONDS - START) / 60)) min"
  else
    echo "### $ID FAILED after $(((SECONDS - START) / 60)) min"
    FAILED+=("$ID")
  fi
done

echo "### batch done in $(((SECONDS - BATCH_START) / 60)) min, failed: ${FAILED[*]:-none}"
[[ ${#FAILED[@]} -eq 0 ]]
