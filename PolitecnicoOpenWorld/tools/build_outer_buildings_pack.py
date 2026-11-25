#!/usr/bin/env python3
"""Genera el paquete opcional de edificios exteriores (t51) para que la vista isométrica no dependa de Overpass.

Uso:
    python tools/build_outer_buildings_pack.py --south 19.495 --west -99.160 --north 19.515 --east -99.135 \
        --out app/src/main/assets/CONFIG/outer_buildings_pack.json

Descarga los edificios de OpenStreetMap (ODbL) por teselas pequeñas (para no saturar los servidores públicos), quita
duplicados por id y guarda solo las etiquetas que usa el dibujo, con el mismo formato que `escom_osm_footprints.json`.
La app lo lee con `cargarPaqueteEdificios()`; si el archivo no existe, se ignora. Revisa el tamaño antes de subirlo
(presupuesto de assets en `README for IAS/00_INDEX.md`).
"""
import argparse
import json
import sys
import time
import urllib.parse
import urllib.request

SERVERS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.openstreetmap.fr/api/interpreter",
]
TAGS = ("building", "height", "building:levels", "amenity", "name")
USER_AGENT = "PolitecnicoOpenWorld-pack-builder/1.0"


def fetch_tile(south, west, north, east):
    query = f'[out:json][timeout:60];way["building"]({south},{west},{north},{east});out geom;'
    data = urllib.parse.urlencode({"data": query}).encode()
    last = None
    # Los servidores públicos fallan seguido (504): 3 rondas con espera creciente antes de rendirse con la tesela.
    for attempt in range(3):
        for server in SERVERS:
            try:
                request = urllib.request.Request(server, data=data, headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(request, timeout=90) as response:
                    return json.load(response)["elements"]
            except Exception as error:  # noqa: BLE001 - se prueba el siguiente servidor
                last = error
                time.sleep(2)
        time.sleep(15 * (attempt + 1))
    raise RuntimeError(f"ningún servidor respondió para la tesela: {last}")


def to_way(element):
    geometry = element.get("geometry") or []
    pts = [[p["lat"], p["lon"]] for p in geometry if "lat" in p and "lon" in p]
    tags = {k: v for k, v in (element.get("tags") or {}).items() if k in TAGS and v}
    if len(pts) < 4 or "building" not in tags:
        return None
    return {"id": element["id"], "tags": tags, "pts": pts}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--south", type=float, required=True)
    parser.add_argument("--west", type=float, required=True)
    parser.add_argument("--north", type=float, required=True)
    parser.add_argument("--east", type=float, required=True)
    parser.add_argument("--tile", type=float, default=0.005, help="lado de la tesela en grados (~550 m)")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    ways = {}
    failed = []
    lat = args.south
    while lat < args.north - 1e-9:
        lon = args.west
        while lon < args.east - 1e-9:
            tile = (round(lat, 6), round(lon, 6), round(min(lat + args.tile, args.north), 6), round(min(lon + args.tile, args.east), 6))
            try:
                for element in fetch_tile(*tile):
                    way = to_way(element)
                    if way:
                        ways[way["id"]] = way
                print(f"tesela {tile}: {len(ways)} edificios", file=sys.stderr)
            except RuntimeError as error:
                # Una tesela caída no tira todo el trabajo: se anota y se sigue (se puede repetir solo esa zona).
                failed.append(tile)
                print(f"tesela {tile}: FALLÓ ({error})", file=sys.stderr)
            lon += args.tile
            time.sleep(1.5)
        lat += args.tile

    payload = {"source": "OpenStreetMap contributors (ODbL)", "ways": list(ways.values())}
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(ways)} edificios -> {args.out}", file=sys.stderr)
    if failed:
        print(f"ATENCIÓN: {len(failed)} teselas sin datos (paquete incompleto): {failed}", file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()