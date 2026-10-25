#!/usr/bin/env python3
"""Builds a terrarium-encoded WebP MBTiles from Copernicus GLO-30 for a bounding box.

The tiles match what the app expects from the online DEM (512 px, terrarium encoding, pixel
centers at i + 0.5), so hillshade and in-app contours work the same online and offline.

Usage: dem_tiles.py --bbox W,S,E,N --output dem.mbtiles --cache DIR [--minzoom 0] [--maxzoom 12]
"""
import argparse
import io
import math
import os
import sqlite3
import sys
import urllib.error
import urllib.request
from multiprocessing import Pool

import numpy as np
import rasterio
from PIL import Image
from rasterio.transform import from_bounds
from rasterio.warp import Resampling, reproject

TILE = 512
HALF_WORLD = 20037508.342789244
COP_URL = "https://copernicus-dem-30m.s3.amazonaws.com/{name}/{name}.tif"


def cop_name(lat, lon):
    ns = "N" if lat >= 0 else "S"
    ew = "E" if lon >= 0 else "W"
    return f"Copernicus_DSM_COG_10_{ns}{abs(lat):02d}_00_{ew}{abs(lon):03d}_00_DEM"


def fetch_sources(bbox, cache):
    """Downloads the 1x1 degree Copernicus tiles covering bbox. Sea-only tiles do not exist (404)."""
    w, s, e, n = bbox
    os.makedirs(cache, exist_ok=True)
    paths = []
    for lat in range(math.floor(s), math.ceil(n)):
        for lon in range(math.floor(w), math.ceil(e)):
            name = cop_name(lat, lon)
            path = os.path.join(cache, name + ".tif")
            if not os.path.exists(path):
                try:
                    print(f"  downloading {name}", flush=True)
                    urllib.request.urlretrieve(COP_URL.format(name=name), path + ".part")
                    os.replace(path + ".part", path)
                except urllib.error.HTTPError as err:
                    if err.code in (403, 404):
                        print(f"  {name}: no data (sea)", flush=True)
                        continue
                    raise
            paths.append(path)
    return paths


def tile_range(bbox, z):
    w, s, e, n = bbox
    count = 2 ** z

    def x_of(lon):
        return min(count - 1, max(0, int((lon + 180) / 360 * count)))

    def y_of(lat):
        rad = math.radians(max(-85.0511, min(85.0511, lat)))
        return min(count - 1, max(0, int((1 - math.asinh(math.tan(rad)) / math.pi) / 2 * count)))

    return range(x_of(w), x_of(e) + 1), range(y_of(n), y_of(s) + 1)


def tile_bounds_3857(z, x, y):
    size = 2 * HALF_WORLD / 2 ** z
    xmin = -HALF_WORLD + x * size
    ymax = HALF_WORLD - y * size
    return xmin, ymax - size, xmin + size, ymax


# Quarter-meter steps: well below the DEM accuracy, and half the file size of raw floats.
QUANTUM_M = 0.25


def encode_terrarium(elev):
    v = np.round(np.clip(elev, -32768, 32767) / QUANTUM_M) * QUANTUM_M + 32768.0
    r = np.floor(v / 256)
    g = np.floor(v) % 256
    b = np.floor((v - np.floor(v)) * 256)
    rgb = np.stack([r, g, b], axis=-1).astype(np.uint8)
    buf = io.BytesIO()
    Image.fromarray(rgb, "RGB").save(buf, "WEBP", lossless=True, quality=80, method=4)
    return buf.getvalue()


_sources = []


def _init(paths):
    global _sources
    _sources = [rasterio.open(p) for p in paths]


def render(job):
    z, x, y = job
    dst = np.full((TILE, TILE), np.nan, dtype=np.float32)
    transform = from_bounds(*tile_bounds_3857(z, x, y), TILE, TILE)
    for src in _sources:
        reproject(
            source=rasterio.band(src, 1),
            destination=dst,
            dst_transform=transform,
            dst_crs="EPSG:3857",
            dst_nodata=np.nan,
            init_dest_nodata=False,
            resampling=Resampling.bilinear,
        )
    if np.isnan(dst).all():
        return z, x, y, None
    # Copernicus has no data over the open sea: that is sea level.
    return z, x, y, encode_terrarium(np.nan_to_num(dst, nan=0.0))


def create_mbtiles(path, bbox, minzoom, maxzoom):
    if os.path.exists(path):
        os.remove(path)
    db = sqlite3.connect(path)
    db.executescript(
        """
        CREATE TABLE metadata (name TEXT, value TEXT);
        CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB);
        CREATE UNIQUE INDEX tile_index ON tiles (zoom_level, tile_column, tile_row);
        """
    )
    meta = {
        "name": "Copernicus DEM (terrarium)",
        "format": "webp",
        "type": "baselayer",
        "encoding": "terrarium",
        "tileSize": str(TILE),
        "minzoom": str(minzoom),
        "maxzoom": str(maxzoom),
        "bounds": ",".join(f"{v:.5f}" for v in bbox),
        "attribution": "Copernicus DEM GLO-30 © DLR e.V. 2010-2014 and © Airbus Defence and Space GmbH 2014-2018",
    }
    db.executemany("INSERT INTO metadata VALUES (?, ?)", meta.items())
    return db


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bbox", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--cache", required=True)
    parser.add_argument("--minzoom", type=int, default=0)
    parser.add_argument("--maxzoom", type=int, default=12)
    parser.add_argument("--workers", type=int, default=os.cpu_count())
    args = parser.parse_args()
    bbox = [float(v) for v in args.bbox.split(",")]

    sources = fetch_sources(bbox, args.cache)
    if not sources:
        sys.exit("No DEM data in bbox")
    jobs = []
    for z in range(args.minzoom, args.maxzoom + 1):
        xs, ys = tile_range(bbox, z)
        jobs += [(z, x, y) for x in xs for y in ys]
    print(f"  rendering {len(jobs)} DEM tiles z{args.minzoom}-{args.maxzoom}", flush=True)

    db = create_mbtiles(args.output, bbox, args.minzoom, args.maxzoom)
    written = 0
    with Pool(args.workers, initializer=_init, initargs=(sources,)) as pool:
        for i, (z, x, y, data) in enumerate(pool.imap_unordered(render, jobs, chunksize=4), 1):
            if data is not None:
                # MBTiles uses TMS rows (origin bottom-left).
                db.execute("INSERT INTO tiles VALUES (?, ?, ?, ?)", (z, x, 2**z - 1 - y, data))
                written += 1
            if i % 200 == 0:
                print(f"  {i}/{len(jobs)}", flush=True)
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"  wrote {written} tiles to {args.output}", flush=True)


if __name__ == "__main__":
    main()
