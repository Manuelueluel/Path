#!/usr/bin/env python3
"""Builds catalog.json from the region packs in an output directory.

Usage: make_catalog.py --dist DIR --base-url URL [--exclude ID...] [--include ID...]

Each built region has DIR/<id>/region.json (written by build_region.sh); files are referenced
as <base-url>/<file name>. DIR/upload/ receives catalog.json and every referenced file once
(hard links), ready to be uploaded flat, e.g. as GitHub release assets. With --include only the
given regions are listed, so the packs can be published a few at a time (see upload_regions.py).
"""
import argparse
import glob
import json
import os
import shutil


def build_catalog(dist, base_url, exclude=(), include=None):
    """Writes DIR/catalog.json and fills DIR/upload/; returns the catalog."""
    base = base_url.rstrip("/")
    upload = os.path.join(dist, "upload")
    shutil.rmtree(upload, ignore_errors=True)
    os.makedirs(upload)
    regions = []
    for meta_path in sorted(glob.glob(os.path.join(dist, "*", "region.json"))):
        with open(meta_path) as f:
            region = json.load(f)
        if region["id"] in exclude or (include is not None and region["id"] not in include):
            continue
        for file in region["files"]:
            name = file.pop("name")
            file["url"] = f"{base}/{name}"
            dst = os.path.join(upload, name)
            # Shared routing segments appear in several regions: link them once.
            if not os.path.exists(dst):
                os.link(os.path.join(os.path.dirname(meta_path), name), dst)
        regions.append(region)
    catalog = {"formatVersion": 1, "regions": regions}
    out = os.path.join(dist, "catalog.json")
    with open(out, "w") as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False)
        f.write("\n")
    shutil.copy(out, upload)
    total = sum(os.path.getsize(os.path.join(upload, n)) for n in os.listdir(upload)) / 1e9
    print(f"catalog with {len(regions)} regions -> {out}")
    print(f"{len(os.listdir(upload))} files to upload ({total:.1f} GB) -> {upload}")
    return catalog


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dist", required=True)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--exclude", nargs="*", default=[], help="region ids to leave out, e.g. test-lecco")
    parser.add_argument("--include", nargs="*", help="only these region ids (default: all built regions)")
    args = parser.parse_args()
    build_catalog(args.dist, args.base_url, args.exclude, args.include)


if __name__ == "__main__":
    main()
