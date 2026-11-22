#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Recorta la hoja isometrica del jugador (5 filas de direccion x 8 columnas) al ESTANDAR de POW.

HOJA (fondo transparente, grid limpio):
  filas    = direccion de la camara: S, SE, E, NE, N  (las 3 restantes salen espejando)
  columnas = 1 idle | 2-4 caminar | 5-7 correr | 8 golpe

ESTANDAR (el mismo de tools/slice_npc_standard.py, NO CAMBIAR):
  lienzo fijo 193 x 249, UN solo factor de escala por personaje (alto de caminar = 200 px),
  pies abajo y centrado, WebP calidad 88.

SALIDA: <out_dir>/<Walk|Run|Special|Idle>/<prefix><dir>_<w|r|s|i>_<N>.webp   (dir en s,se,e,ne,n)

USO:
  pip install pillow numpy
  python tools/slice_iso_player.py <hoja.png> <out_dir> <prefix>
  # ej: python tools/slice_iso_player.py ISO_SHEET.png app/src/main/assets/SPRITES/PLAYER/EstudianteIso iso_
"""
import os
import sys

import numpy as np
from PIL import Image

CW, CH = 193, 249
STD_BODY_H = 200.0
PAD = 18
DIRS = ["s", "se", "e", "ne", "n"]
COLS = 8
# (carpeta, letra, columnas de la hoja que le tocan)
ACTIONS = [("Idle", "i", [0]), ("Walk", "w", [1, 2, 3]), ("Run", "r", [4, 5, 6]), ("Special", "s", [7])]


def crop_cell(arr, row, col, rows):
    h, w = arr.shape[:2]
    y0, y1 = int(round(row * h / rows)), int(round((row + 1) * h / rows))
    x0, x1 = int(round(col * w / COLS)), int(round((col + 1) * w / COLS))
    sub = arr[y0:y1, x0:x1]
    mask = sub[:, :, 3] > 25
    ys, xs = np.where(mask.any(1))[0], np.where(mask.any(0))[0]
    if not len(ys) or not len(xs):
        raise SystemExit(f"celda vacia fila={row} col={col}")
    return sub[ys[0]:ys[-1] + 1, xs[0]:xs[-1] + 1]


def place(crop, scale):
    nw = max(1, int(round(crop.shape[1] * scale)))
    nh = max(1, int(round(crop.shape[0] * scale)))
    f = min(1.0, (CW - 2) / nw, (CH - PAD // 2 - 2) / nh)
    nw, nh = max(1, int(nw * f)), max(1, int(nh * f))
    im = Image.fromarray(crop, "RGBA").resize((nw, nh), Image.LANCZOS)
    cv = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
    cv.alpha_composite(im, ((CW - nw) // 2, CH - nh - PAD // 2))
    return cv


def main(sheet, out_dir, prefix):
    arr = np.array(Image.open(sheet).convert("RGBA"))
    rows = len(DIRS)
    crops = {(r, c): crop_cell(arr, r, c, rows) for r in range(rows) for c in range(COLS)}
    walk_heights = [crops[(r, c)].shape[0] for r in range(rows) for c in ACTIONS[1][2]]
    scale = STD_BODY_H / float(np.median(walk_heights))
    for folder, letter, cols in ACTIONS:
        os.makedirs(os.path.join(out_dir, folder), exist_ok=True)
        for r, d in enumerate(DIRS):
            for n, c in enumerate(cols, 1):
                place(crops[(r, c)], scale).save(
                    os.path.join(out_dir, folder, f"{prefix}{d}_{letter}_{n}.webp"), "WEBP", quality=88, method=4)
    print(f"OK {out_dir}  escala={scale:.3f}  idle=1 walk=3 run=3 special=1 por direccion ({', '.join(DIRS)})")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("uso: python slice_iso_player.py <hoja.png> <out_dir> <prefix>")
        sys.exit(1)
    main(*sys.argv[1:])
