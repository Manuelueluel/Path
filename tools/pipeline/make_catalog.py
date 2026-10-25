#!/usr/bin/env python3
"""Builds catalog.json from the region packs in an output directory.

Usage: make_catalog.py --dist DIR --base-url URL

Each built region has DIR/<id>/region.json (written by build_region.sh); files are referenced
as <base-url>/<file name>, so the whole DIR can be uploaded flat (e.g. as GitHub release assets).
"""
import argparse
import glob
import json
import os


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dist", required=True)
    parser.add_argument("--base-url", required=True)
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    regions = []
    for meta_path in sorted(glob.glob(os.path.join(args.dist, "*", "region.json"))):
        with open(meta_path) as f:
            region = json.load(f)
        for file in region["files"]:
            file["url"] = f"{base}/{file.pop('name')}"
        regions.append(region)
    catalog = {"formatVersion": 1, "regions": regions}
    out = os.path.join(args.dist, "catalog.json")
    with open(out, "w") as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"catalog with {len(regions)} regions -> {out}")


if __name__ == "__main__":
    main()
