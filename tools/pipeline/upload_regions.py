#!/usr/bin/env python3
"""Publishes region packs to the GitHub release "maps" a few regions at a time.

Usage: upload_regions.py <region-id>... [--dry-run]

Uploads the files of the given regions that the release does not have yet (shared routing
segments already there are skipped), then replaces catalog.json with one listing these regions
plus every region already fully published. The catalog goes up last, so the app never sees a
region whose files are still missing. Interrupted runs can simply be repeated.

Requires GitHub CLI (gh) logged in with write access to the repository.
"""
import argparse
import json
import os
import subprocess
import sys

from make_catalog import build_catalog

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = "Manuelueluel/Path"
TAG = "maps"
BASE_URL = f"https://github.com/{REPO}/releases/download/{TAG}"
EXCLUDE = ["test-lecco"]


def gh(*args, capture=False):
    result = subprocess.run(["gh", *args], check=False, text=True, capture_output=capture)
    if result.returncode != 0:
        detail = result.stderr.strip() if capture else ""
        sys.exit(f"gh {' '.join(args[:2])} failed {detail}")
    return result.stdout


def release_assets():
    """Name -> size of the uploaded assets, or None when the release does not exist yet."""
    view = subprocess.run(
        ["gh", "release", "view", TAG, "-R", REPO, "--json", "assets"],
        check=False, text=True, capture_output=True,
    )
    if view.returncode != 0:
        if "release not found" in view.stderr:
            return None
        sys.exit(f"gh release view failed: {view.stderr.strip()}")
    return {a["name"]: a["size"] for a in json.loads(view.stdout)["assets"]}


def built_regions(dist):
    regions = {}
    for rid in sorted(os.listdir(dist)):
        meta = os.path.join(dist, rid, "region.json")
        if rid not in EXCLUDE and os.path.isfile(meta):
            with open(meta) as f:
                regions[rid] = json.load(f)
    return regions


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("regions", nargs="+", help="region ids to publish, e.g. veneto")
    parser.add_argument("--dist", default=os.environ.get("TRACCIA_PIPELINE_DIST", os.path.join(HERE, "dist")))
    parser.add_argument("--dry-run", action="store_true", help="only show what would be uploaded")
    args = parser.parse_args()

    regions = built_regions(args.dist)
    unknown = [r for r in args.regions if r not in regions]
    if unknown:
        sys.exit(f"not built: {', '.join(unknown)}; built: {', '.join(regions)}")

    assets = release_assets()
    uploaded = assets or {}

    def is_uploaded(file):
        return uploaded.get(file["name"]) == file["size"]

    published = [rid for rid, r in regions.items() if all(is_uploaded(f) for f in r["files"])]
    listed = sorted(set(published) | set(args.regions))

    to_upload = []
    for rid in args.regions:
        for file in regions[rid]["files"]:
            if not is_uploaded(file) and file["name"] not in (f["name"] for f in to_upload):
                to_upload.append(file)
    total_mb = sum(f["size"] for f in to_upload) / 1e6

    print(f"already published: {', '.join(published) or 'none'}")
    print(f"to upload: {len(to_upload)} files, {total_mb:.0f} MB")
    for file in to_upload:
        print(f"  {file['name']}  {file['size'] / 1e6:.0f} MB")
    print(f"catalog after upload: {', '.join(listed)}")
    if args.dry_run:
        return

    if assets is None:
        print(f"creating release {TAG}")
        gh("release", "create", TAG, "-R", REPO, "--title", "Mappe offline",
           "--notes", "Pacchetti regione per le mappe offline dell'app (OSM, Copernicus GLO-30, BRouter).",
           "--latest=false")

    build_catalog(args.dist, BASE_URL, EXCLUDE, listed)
    upload_dir = os.path.join(args.dist, "upload")
    for i, file in enumerate(to_upload, 1):
        print(f"[{i}/{len(to_upload)}] {file['name']} ({file['size'] / 1e6:.0f} MB)", flush=True)
        # --clobber replaces a broken asset left by an interrupted upload.
        gh("release", "upload", TAG, "-R", REPO, "--clobber", os.path.join(upload_dir, file["name"]))
    print("catalog.json")
    gh("release", "upload", TAG, "-R", REPO, "--clobber", os.path.join(upload_dir, "catalog.json"))
    print(f"done: published {', '.join(listed)}")


if __name__ == "__main__":
    main()
