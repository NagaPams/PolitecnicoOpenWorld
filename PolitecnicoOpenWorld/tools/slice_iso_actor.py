#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Recorta hojas isometricas (personajes y vehiculos) al ESTANDAR de POW. Detecta cada figura por
componentes conectados (no divide la hoja en celdas iguales), asi que tolera figuras desplazadas
y descarta las motas sueltas.

PERSONAJE (hoja 5 filas x 8 columnas, fondo transparente):
  filas    = direccion de la camara: S, SE, E, NE, N  (SW, W, NW salen espejando)
  columnas = 1 idle | 2-4 caminar | 5-7 correr | 8 accion
  Estandar (el mismo de slice_iso_player.py / slice_npc_standard.py): lienzo 193x249, UN factor de
  escala por hoja (alto medio de caminar = 200 px), pies abajo y centrados, WebP calidad 88.
  Salida: <out>/<Idle|Walk|Run|Special>/iso_<dir>_<w|r|s|i>_<N>.webp

VEHICULO (hoja de 1 fila x 5 vistas: N, NE, E, SE, S):
  Lienzo comun a las 5 vistas (ancho fijo 384 px), centrado; WebP calidad 88.
  Salida: <out>/<n|ne|e|se|s>.webp

USO:
  pip install pillow numpy scipy
  python tools/slice_iso_actor.py character <hoja.png> <out_dir>
  python tools/slice_iso_actor.py vehicle   <hoja.png> <out_dir>
"""
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage as ndi

CW, CH = 193, 249
STD_BODY_H = 200.0
PAD = 18
DIRS = ["s", "se", "e", "ne", "n"]
VEHICLE_DIRS = ["n", "ne", "e", "se", "s"]
COLS = 8
ACTIONS = [("Idle", "i", [0]), ("Walk", "w", [1, 2, 3]), ("Run", "r", [4, 5, 6]), ("Special", "s", [7])]
MIN_BLOB = 150      # componentes menores son motas del generador
MIN_GAP = 4         # hueco minimo (px) entre figuras
VEHICLE_W = 384
ALPHA_MIN = 25


def clean_mask(alpha):
    mask = alpha > ALPHA_MIN
    lab, n = ndi.label(mask)
    if n == 0:
        raise SystemExit("hoja vacia")
    sizes = ndi.sum(mask, lab, range(1, n + 1))
    keep = np.isin(lab, [i + 1 for i, s in enumerate(sizes) if s >= MIN_BLOB])
    return keep


def runs(flags, min_gap):
    idx = np.where(flags)[0]
    if not len(idx):
        return []
    segs = [[idx[0], idx[0]]]
    for i in idx[1:]:
        if i - segs[-1][1] > min_gap:
            segs.append([i, i])
        else:
            segs[-1][1] = i
    return [(int(a), int(b)) for a, b in segs]


def figures(arr, rows, cols):
    """Devuelve rows x cols recortes RGBA (solo la figura, sin motas)."""
    keep = clean_mask(arr[:, :, 3])
    rsegs = runs(keep.any(1), MIN_GAP)
    if len(rsegs) != rows:
        raise SystemExit(f"se esperaban {rows} filas y hay {len(rsegs)}")
    out = {}
    for r, (y0, y1) in enumerate(rsegs):
        csegs = runs(keep[y0:y1 + 1].any(0), MIN_GAP)
        if len(csegs) != cols:
            raise SystemExit(f"fila {r}: se esperaban {cols} figuras y hay {len(csegs)}")
        for c, (x0, x1) in enumerate(csegs):
            sub = arr[y0:y1 + 1, x0:x1 + 1].copy()
            sub[~keep[y0:y1 + 1, x0:x1 + 1]] = 0
            ys = np.where(sub[:, :, 3] > ALPHA_MIN)[0]
            xs = np.where((sub[:, :, 3] > ALPHA_MIN).any(0))[0]
            out[(r, c)] = sub[ys[0]:ys[-1] + 1, xs[0]:xs[-1] + 1]
    return out


def place(crop, scale):
    nw = max(1, int(round(crop.shape[1] * scale)))
    nh = max(1, int(round(crop.shape[0] * scale)))
    f = min(1.0, (CW - 2) / nw, (CH - PAD // 2 - 2) / nh)
    nw, nh = max(1, int(nw * f)), max(1, int(nh * f))
    im = Image.fromarray(crop, "RGBA").resize((nw, nh), Image.LANCZOS)
    cv = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
    cv.alpha_composite(im, ((CW - nw) // 2, CH - nh - PAD // 2))
    return cv


def character(sheet, out_dir):
    arr = np.array(Image.open(sheet).convert("RGBA"))
    crops = figures(arr, len(DIRS), COLS)
    walk_heights = [crops[(r, c)].shape[0] for r in range(len(DIRS)) for c in ACTIONS[1][2]]
    scale = STD_BODY_H / float(np.median(walk_heights))
    for folder, letter, cols in ACTIONS:
        os.makedirs(os.path.join(out_dir, folder), exist_ok=True)
        for r, d in enumerate(DIRS):
            for n, c in enumerate(cols, 1):
                place(crops[(r, c)], scale).save(
                    os.path.join(out_dir, folder, f"iso_{d}_{letter}_{n}.webp"), "WEBP", quality=88, method=4)
    print(f"OK personaje {os.path.basename(sheet)} -> {out_dir} (escala {scale:.3f})")


def vehicle(sheet, out_dir):
    arr = np.array(Image.open(sheet).convert("RGBA"))
    crops = figures(arr, 1, len(VEHICLE_DIRS))
    cs = [crops[(0, c)] for c in range(len(VEHICLE_DIRS))]
    scale = (VEHICLE_W - 8) / float(max(c.shape[1] for c in cs))
    ch = int(round(max(c.shape[0] for c in cs) * scale)) + 8
    os.makedirs(out_dir, exist_ok=True)
    for d, crop in zip(VEHICLE_DIRS, cs):
        nw = max(1, int(round(crop.shape[1] * scale)))
        nh = max(1, int(round(crop.shape[0] * scale)))
        im = Image.fromarray(crop, "RGBA").resize((nw, nh), Image.LANCZOS)
        cv = Image.new("RGBA", (VEHICLE_W, ch), (0, 0, 0, 0))
        cv.alpha_composite(im, ((VEHICLE_W - nw) // 2, (ch - nh) // 2))
        cv.save(os.path.join(out_dir, f"{d}.webp"), "WEBP", quality=88, method=4)
    print(f"OK vehiculo {os.path.basename(sheet)} -> {out_dir} ({VEHICLE_W}x{ch})")


if __name__ == "__main__":
    if len(sys.argv) != 4 or sys.argv[1] not in ("character", "vehicle"):
        print(__doc__)
        sys.exit(1)
    (character if sys.argv[1] == "character" else vehicle)(sys.argv[2], sys.argv[3])
