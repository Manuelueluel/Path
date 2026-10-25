#!/usr/bin/env bash
# Bundles the map label glyphs (SDF fonts) in the app, so labels render offline.
# Only the Unicode ranges used by European place names are included; others fall back to online.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../core/terrain/src/main/assets/fonts"
BASE="https://tiles.openfreemap.org/fonts"
STACKS=("Noto Sans Regular" "Noto Sans Bold" "Noto Sans Italic")
# Basic Latin + Latin-1, Latin Extended A/B, IPA/spacing, combining marks, Greek, Latin Extended Additional, punctuation, letterlike.
RANGES=(0-255 256-511 512-767 768-1023 7680-7935 8192-8447 8448-8703)
for stack in "${STACKS[@]}"; do
  mkdir -p "$OUT/$stack"
  for range in "${RANGES[@]}"; do
    curl -fsS -o "$OUT/$stack/$range.pbf" "$BASE/${stack// /%20}/$range.pbf"
  done
done
du -sh "$OUT"
