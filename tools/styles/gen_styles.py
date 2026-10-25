#!/usr/bin/env python3
"""Generates the MapLibre styles in feature/map/src/main/assets/styles (OpenMapTiles schema).

Usage: python3 tools/styles/gen_styles.py feature/map/src/main/assets/styles

The {{...}} placeholders are filled at runtime from core/terrain/src/main/assets/map_sources.json.
Contour layers are not here: they are computed in-app and added by MapLibreController.
"""
import json, sys
OUT = sys.argv[1]

REG = ["Noto Sans Regular"]; BOLD = ["Noto Sans Bold"]; ITAL = ["Noto Sans Italic"]
NAME = ["coalesce", ["get", "name"], ["get", "name:latin"]]

def zi(*stops):  # zoom interpolation
    e = ["interpolate", ["exponential", 1.4], ["zoom"]]
    for s in stops: e += list(s)
    return e

def cls(*v): return ["match", ["get", "class"], list(v), True, False]
def sub(*v): return ["match", ["get", "subclass"], list(v), True, False]
def sac(*v): return ["match", ["coalesce", ["get", "sac_scale"], ""], list(v), True, False]
def notcls(*v): return ["match", ["get", "class"], list(v), False, True]

def sources(topo):
    # All tile URLs are virtual: TileRouter serves them from offline packs or online sources.
    s = {
        "openmaptiles": {"type": "vector", "tiles": ["{{BASEMAP_TILES}}"], "minzoom": 0, "maxzoom": "{{BASEMAP_MAXZOOM}}",
                         "attribution": "{{BASEMAP_ATTRIBUTION}}"},
        "dem": {"type": "raster-dem", "tiles": ["{{DEM_TILES}}"], "tileSize": "{{DEM_TILESIZE}}",
                "maxzoom": "{{DEM_MAXZOOM}}", "encoding": "terrarium", "attribution": "{{DEM_ATTRIBUTION}}"},
    }
    if topo:
        # Trail difficulty overlay, only present in offline region packs (OpenMapTiles has no sac_scale).
        s["trails"] = {"type": "vector", "tiles": ["{{TRAILS_TILES}}"], "minzoom": 12, "maxzoom": "{{TRAILS_MAXZOOM}}"}
    return s

def fill(id, layer, flt, color, minzoom=0, opacity=1, outline=None):
    p = {"fill-color": color, "fill-opacity": opacity, "fill-antialias": True}
    if outline: p["fill-outline-color"] = outline
    l = {"id": id, "type": "fill", "source": "openmaptiles", "source-layer": layer, "minzoom": minzoom, "paint": p}
    if flt is not None: l["filter"] = flt
    return l

def line(id, layer, flt, color, width, minzoom=0, dash=None, opacity=1, offset=None, cap="round", join="round"):
    p = {"line-color": color, "line-width": width, "line-opacity": opacity}
    if dash: p["line-dasharray"] = dash
    if offset is not None: p["line-offset"] = offset
    lay = {"line-join": join}
    if not dash: lay["line-cap"] = cap
    l = {"id": id, "type": "line", "source": "openmaptiles", "source-layer": layer, "minzoom": minzoom, "layout": lay, "paint": p}
    if flt is not None: l["filter"] = flt
    return l

def hillshade(c):
    return {"id": "hillshade", "type": "hillshade", "source": "dem", "minzoom": 0,
            "paint": {"hillshade-exaggeration": ["interpolate", ["linear"], ["zoom"], 9, c["hs_exag"], 13, c["hs_exag"] * 0.8, 16, c["hs_exag"] * 0.5], "hillshade-shadow-color": c["hs_shadow"],
                      "hillshade-highlight-color": c["hs_highlight"], "hillshade-accent-color": c["hs_accent"],
                      "hillshade-illumination-anchor": "viewport"}}

def trails(c):
    """Difficulty overlay from the "trails" source: a halo hides the generic path, then the difficulty dash."""
    def tline(id, flt, color, width, dash=None):
        l = line(id, "trails", flt, color, width, 13, dash=dash)
        l["source"] = "trails"
        return l
    width = zi((13, 1.0), (14, 1.6), (18, 3.5))
    known = sac("hiking", "mountain_hiking", "demanding_mountain_hiking", "alpine_hiking", "demanding_alpine_hiking",
                "difficult_alpine_hiking")
    return [
        tline("trail-halo", known, c["bg"], zi((13, 1.8), (14, 2.6), (18, 5.5))),
        tline("trail-hiking", sac("hiking"), c["path_easy"], width, dash=[4, 2]),
        tline("trail-mountain", sac("mountain_hiking", "demanding_mountain_hiking"), c["path_mountain"], width, dash=[2, 1.5]),
        tline("trail-alpine", sac("alpine_hiking", "demanding_alpine_hiking", "difficult_alpine_hiking"), c["path_alpine"], width,
              dash=[1, 1.5]),
    ]


def roads(c, topo):
    L = []
    T = "transportation"
    path = ["all", cls("path"), ["!", sub("steps")]]
    track = cls("track")
    minor = cls("minor", "service")
    sec = cls("secondary", "tertiary")
    pri = cls("primary")
    mot = cls("motorway", "trunk")
    # casings
    L += [line("road-minor-casing", T, minor, c["minor_case"], zi((12, 0.8), (14, 3.5), (18, 16)), 12),
          line("road-secondary-casing", T, sec, c["sec_case"], zi((8, 1), (12, 3), (14, 6), (18, 22)), 8),
          line("road-primary-casing", T, pri, c["pri_case"], zi((7, 1), (12, 4), (14, 7.5), (18, 26)), 7),
          line("road-motorway-casing", T, mot, c["mot_case"], zi((5, 1), (12, 4.5), (14, 8.5), (18, 28)), 5)]
    # aerialways (useful for ski touring)
    L.append(line("aerialway", T, cls("aerialway"), c["aerialway"], zi((12, 0.8), (16, 1.6)), 12))
    # tracks (strade bianche): unpaved dashed, paved solid
    unpaved = ["match", ["coalesce", ["get", "surface"], "unpaved"], ["paved"], False, True]
    L.append(line("track-paved", T, ["all", track, ["!", unpaved]], c["track"], zi((12, 0.8), (14, 1.6), (18, 4)), 12))
    L.append(line("track-unpaved", T, ["all", track, unpaved], c["track"], zi((12, 0.8), (14, 1.6), (18, 4)), 12, dash=[3, 1.5]))
    # paths
    if topo:
        L.append(line("path", T, path, c["path_unknown"], zi((12, 0.6), (14, 1.3), (18, 3)), 12, dash=[3, 2]))
        L += trails(c)
        mtb_color = ["match", ["to-string", ["get", "mtb_scale"]], ["0", "0+", "0-", "1", "1+", "1-"], "#2e9e44", ["2", "2+", "2-"], "#2a6fdb",
                     ["3", "3+", "3-"], "#e07b00", "#222222"]
        L.append(line("path-mtb-scale", T, ["all", cls("path", "track"), ["has", "mtb_scale"]], mtb_color,
                      zi((13, 0.8), (16, 1.6)), 13, opacity=0.85, offset=zi((13, 1.5), (16, 3), (18, 5))))
        L.append(line("path-steps", T, ["all", cls("path"), sub("steps")], c["path_mountain"], zi((14, 1.5), (18, 4)), 14, dash=[0.4, 0.6]))
    else:
        L.append(line("path", T, path, c["path_unknown"], zi((12, 0.5), (14, 1), (18, 2.5)), 12, dash=[3, 2]))
    # road fills
    L += [line("road-minor", T, minor, c["minor"], zi((12, 0.5), (14, 2.2), (18, 13)), 12),
          line("road-secondary", T, sec, c["sec"], zi((8, 0.5), (12, 2), (14, 4.5), (18, 19)), 8),
          line("road-primary", T, pri, c["pri"], zi((7, 0.6), (12, 2.8), (14, 5.5), (18, 22)), 7),
          line("road-motorway", T, mot, c["mot"], zi((5, 0.6), (12, 3), (14, 6.5), (18, 24)), 5)]
    L.append(line("railway", T, cls("rail"), c["rail"], zi((10, 0.6), (16, 1.6)), 10, dash=[4, 2]))
    return L

def labels(c, topo):
    L = []
    def sym(id, layer, flt, minzoom, layout, paint):
        l = {"id": id, "type": "symbol", "source": "openmaptiles", "source-layer": layer, "minzoom": minzoom, "layout": layout, "paint": paint}
        if flt is not None: l["filter"] = flt
        return l
    halo = {"text-halo-color": c["halo"], "text-halo-width": 1.4, "text-halo-blur": 0.5}
    L.append(sym("waterway-label", "waterway", cls("river", "stream"), 13,
                 {"text-field": NAME, "text-font": ITAL, "text-size": 11, "symbol-placement": "line", "text-letter-spacing": 0.1},
                 {"text-color": c["water_text"], **halo}))
    L.append(sym("water-label", "water_name", None, 10,
                 {"text-field": NAME, "text-font": ITAL, "text-size": 12},
                 {"text-color": c["water_text"], **halo}))
    L.append(sym("road-label", "transportation_name", notcls("path", "track"), 14,
                 {"text-field": NAME, "text-font": REG, "text-size": 10.5, "symbol-placement": "line"},
                 {"text-color": c["road_text"], **halo}))
    if topo:
        L.append(sym("path-label", "transportation_name", cls("path", "track"), 15,
                     {"text-field": NAME, "text-font": ITAL, "text-size": 10, "symbol-placement": "line"},
                     {"text-color": c["path_text"], **halo}))
        L.append(sym("poi-water", "poi", sub("drinking_water", "spring", "water_point"), 14,
                     {"icon-image": "water", "icon-size": 0.9, "text-field": "", "icon-allow-overlap": False},
                     {}))
        L.append(sym("poi-hut", "poi", ["any", sub("alpine_hut", "wilderness_hut"), cls("shelter")], 12,
                     {"icon-image": ["case", sub("alpine_hut"), "hut", "shelter"], "icon-size": 1,
                      "text-field": ["step", ["zoom"], "", 13, NAME], "text-font": BOLD, "text-size": 11,
                      "text-anchor": "top", "text-offset": [0, 0.9], "text-optional": True, "text-max-width": 8},
                     {"text-color": c["hut_text"], **halo}))
    peak_text = ["case", ["has", "ele"], ["concat", NAME, "\n", ["to-string", ["get", "ele"]], " m"], NAME]
    if topo:
        L.append(sym("peak", "mountain_peak", cls("peak", "volcano"), 11,
                     {"icon-image": "peak", "icon-size": 0.9, "text-field": ["step", ["zoom"], "", 12, peak_text],
                      "text-font": REG, "text-size": 11, "text-anchor": "top", "text-offset": [0, 0.7], "text-optional": True,
                      "symbol-sort-key": ["get", "rank"], "text-max-width": 9},
                     {"text-color": c["peak_text"], **halo}))
        L.append(sym("saddle", "mountain_peak", cls("saddle"), 13,
                     {"icon-image": "saddle", "icon-size": 0.8, "text-field": ["step", ["zoom"], "", 14, peak_text],
                      "text-font": ITAL, "text-size": 10, "text-anchor": "top", "text-offset": [0, 0.6], "text-optional": True},
                     {"text-color": c["peak_text"], **halo}))
    else:
        L.append(sym("peak", "mountain_peak", cls("peak", "volcano"), 12,
                     {"text-field": peak_text, "text-font": REG, "text-size": 10, "symbol-sort-key": ["get", "rank"], "text-max-width": 9},
                     {"text-color": c["peak_text"], **halo}))
    place_size = ["match", ["get", "class"], "city", 16, "town", 14, "village", 12.5, 11]
    L.append(sym("place-small", "place", cls("hamlet", "isolated_dwelling", "locality", "neighbourhood", "suburb"), 13,
                 {"text-field": NAME, "text-font": REG, "text-size": 10.5, "text-max-width": 8},
                 {"text-color": c["place_text"], **halo}))
    L.append(sym("place-village", "place", cls("village"), 10,
                 {"text-field": NAME, "text-font": REG, "text-size": place_size, "text-max-width": 8},
                 {"text-color": c["place_text"], **halo}))
    L.append(sym("place-town", "place", cls("town", "city"), 5,
                 {"text-field": NAME, "text-font": BOLD, "text-size": place_size, "text-max-width": 8, "symbol-sort-key": ["get", "rank"]},
                 {"text-color": c["place_text"], **halo}))
    L.append(sym("place-country", "place", cls("country", "state"), 2,
                 {"text-field": NAME, "text-font": BOLD, "text-size": 13, "text-transform": "uppercase", "text-letter-spacing": 0.1, "text-max-width": 8},
                 {"text-color": c["country_text"], **halo}))
    return L

def style(name, c, topo):
    L = [{"id": "background", "type": "background", "paint": {"background-color": c["bg"]}}]
    L.append(fill("landuse-residential", "landuse", cls("residential", "suburb", "neighbourhood"), c["residential"], 9))
    L.append(fill("landcover-farmland", "landcover", cls("farmland"), c["farmland"], 9))
    L.append(fill("landcover-grass", "landcover", cls("grass"), c["grass"], 8))
    L.append(fill("landcover-wood", "landcover", cls("wood"), c["wood"], 6))
    L.append(fill("landcover-wetland", "landcover", cls("wetland"), c["wetland"], 10))
    L.append(fill("landcover-rock", "landcover", cls("rock"), c["rock"], 9))
    L.append(fill("landcover-sand", "landcover", cls("sand"), c["sand"], 9))
    L.append(fill("landcover-ice", "landcover", cls("ice"), c["ice"], 6, outline=c["ice_line"]))
    if topo:
        L.append(fill("park", "park", None, c["park"], 7, opacity=0.12))
        L.append(line("park-outline", "park", None, c["park_line"], zi((8, 0.8), (14, 2)), 8, dash=[3, 2], opacity=0.6))
    L.append(fill("water", "water", ["!=", ["get", "brunnel"], "tunnel"], c["water"], 0))
    L.append(line("waterway-river", "waterway", cls("river", "canal"), c["water_line"], zi((8, 0.6), (14, 2.5), (18, 8)), 8))
    L.append(line("waterway-stream", "waterway", notcls("river", "canal"), c["water_line"], zi((12, 0.4), (14, 1), (18, 3)), 12))
    L.append(hillshade(c))
    # Contour layers are inserted here at runtime (above "hillshade").
    L.append(fill("building", "building", None, c["building"], 14, outline=c["building_line"]))
    L += roads(c, topo)
    L.append(line("boundary-country", "boundary", ["all", ["==", ["get", "admin_level"], 2], ["!=", ["get", "maritime"], 1]],
                  c["boundary"], zi((3, 0.8), (10, 2)), 0, dash=[4, 2], opacity=0.8))
    L.append(line("boundary-region", "boundary", ["all", ["==", ["get", "admin_level"], 4], ["!=", ["get", "maritime"], 1]],
                  c["boundary"], zi((5, 0.5), (12, 1.2)), 5, dash=[3, 2], opacity=0.5))
    L += labels(c, topo)
    return {"version": 8, "name": name, "glyphs": "{{GLYPHS}}", "sources": sources(topo), "layers": L}

TOPO = dict(bg="#f6f3ea", residential="#ebe5dc", farmland="#f1edd8", grass="#e4ecc9", wood="#cfe3b6", wetland="#d8e8dc",
            rock="#e4dfd6", sand="#f3e9c8", ice="#eef6fb", ice_line="#9ec4dd", park="#4f9a4c", park_line="#3f8a3c",
            water="#a7cfe9", water_line="#7fb6dc", building="#d9cfc3", building_line="#c6b9aa",
            hs_exag=0.45, hs_shadow="#4a3f30", hs_highlight="#ffffff", hs_accent="#6b5c45",
            minor="#ffffff", minor_case="#b9b0a2", sec="#fbe9a6", sec_case="#b8a46c", pri="#f8c97a", pri_case="#b48a40",
            mot="#f0a35e", mot_case="#a8642a", track="#8a6a3a", aerialway="#4a4a4a", rail="#7a7a7a",
            path_unknown="#a0522d", path_easy="#d1452f", path_mountain="#c0281b", path_alpine="#1f1f1f",
            boundary="#8d6b9a", halo="#fbf9f3", water_text="#2f6f9f", road_text="#4a4238", path_text="#7a3a1f",
            hut_text="#9b1c12", peak_text="#5b3f22", place_text="#2c2a26", country_text="#5e4a6a")
MINIMAL = dict(bg="#f3f3f1", residential="#ebebe8", farmland="#f1f1ee", grass="#ecece8", wood="#e6e9e2", wetland="#e8ecea",
               rock="#ebe9e6", sand="#f1efe8", ice="#f9fbfc", ice_line="#d5dde3", park="#000000", park_line="#000000",
               water="#d3dde4", water_line="#c2d0da", building="#e4e2de", building_line="#d6d3ce",
               hs_exag=0.25, hs_shadow="#6b6b6b", hs_highlight="#ffffff", hs_accent="#8a8a8a",
               minor="#ffffff", minor_case="#d6d6d3", sec="#ffffff", sec_case="#cfcfcb", pri="#fafafa", pri_case="#c4c4c0",
               mot="#f4f4f2", mot_case="#b9b9b5", track="#b5aea3", aerialway="#9a9a9a", rail="#b0b0b0",
               path_unknown="#9a948a", path_easy="#9a948a", path_mountain="#9a948a", path_alpine="#9a948a",
               boundary="#b3a6ba", halo="#f6f6f4", water_text="#7d95a8", road_text="#7a7a76", path_text="#8a8a86",
               hut_text="#6a6a6a", peak_text="#7a7570", place_text="#55534f", country_text="#8a8090")

for fname, c, topo, n in (("topo.json", TOPO, True, "Path Topo"), ("minimal.json", MINIMAL, False, "Path Minimal")):
    with open(f"{OUT}/{fname}", "w") as f:
        json.dump(style(n, c, topo), f, indent=1, ensure_ascii=False)
        f.write("\n")
    print(fname, len(style(n, c, topo)["layers"]), "layers")
