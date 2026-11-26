#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Genera por codigo hojas isometricas PROVISIONALES (granadero h/m, paramedico, Prankedy, gato) a partir de
las hojas existentes de tools/iso_art_sources (misma rejilla 5 filas x 8 columnas que slice_iso_actor.py).
No toca las hojas actuales: escribe archivos nuevos (<nombre>_sheet.png). Cuando haya arte dibujado a mano,
se reemplazan por las hojas del PROMPT_iso_personajes_faltantes.md y se vuelve a cortar.

USO: python tools/make_iso_derived_sheets.py
"""
import os
import random

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "iso_art_sources")
ROWS, COLS = 5, 8


def row_bands(im):
    """Bandas verticales reales de cada fila (las figuras no caen justo en celdas iguales)."""
    rows = (np.asarray(im)[..., 3] > 25).any(1)
    segs, st = [], None
    for y, v in enumerate(rows):
        if v and st is None:
            st = y
        if not v and st is not None:
            segs.append((st, y)); st = None
    if st is not None:
        segs.append((st, len(rows)))
    if len(segs) != ROWS:
        raise SystemExit("la hoja base no tiene %d filas separadas (%d)" % (ROWS, len(segs)))
    return segs


def cells(im):
    w, h = im.size
    cw = w / COLS
    bands = row_bands(im)
    for r in range(ROWS):
        for c in range(COLS):
            yield r, c, (int(c * cw), bands[r][0], int((c + 1) * cw), bands[r][1])


def bbox_of(arr):
    ys, xs = np.where(arr[..., 3] > 40)
    if len(ys) == 0:
        return None
    return xs.min(), ys.min(), xs.max(), ys.max()


def hsv(arr):
    rgb = Image.fromarray(arr[..., :3].astype(np.uint8), "RGB").convert("HSV")
    h = np.asarray(rgb).astype(np.float32)
    return h[..., 0] / 255.0 * 360.0, h[..., 1] / 255.0, h[..., 2] / 255.0


def from_hsv(h, s, v, a):
    hh = np.stack([(h % 360.0) / 360.0 * 255.0, np.clip(s, 0, 1) * 255.0, np.clip(v, 0, 1) * 255.0], -1)
    rgb = Image.fromarray(hh.astype(np.uint8), "HSV").convert("RGB")
    out = np.dstack([np.asarray(rgb), a.astype(np.uint8)])
    return out


def masks(arr, bb):
    h, s, v = hsv(arr)
    a = arr[..., 3]
    solid = a > 40
    x0, y0, x1, y1 = bb
    height = y1 - y0 + 1
    yy = np.arange(arr.shape[0])[:, None] * np.ones((1, arr.shape[1]))
    top = (yy - y0) < height * 0.17
    skin = solid & (h > 8) & (h < 45) & (s > 0.28) & (v > 0.35)
    blue = solid & ~skin & (h > 190) & (h < 270) & (s > 0.16)
    torso = ((yy - y0) > height * 0.20) & ((yy - y0) < height * 0.58)
    vest = solid & ~skin & ~blue & (s < 0.18) & (v > 0.10) & (v < 0.55) & torso
    cap = solid & ~skin & top & ~vest
    shoes = solid & ~skin & (v < 0.16) & ((yy - y0) > height * 0.90)
    uniform = solid & ~skin & ~vest & ~cap & ~shoes
    return h, s, v, a, skin, uniform, vest, cap, shoes


def put(arr, mask, hue, sat, val_fn, h, s, v, a):
    out_h, out_s, out_v = h.copy(), s.copy(), v.copy()
    out_h[mask] = hue
    out_s[mask] = sat
    out_v[mask] = val_fn(v[mask])
    return from_hsv(out_h, out_s, out_v, a)


def recolor(arr, mode, row):
    bb = bbox_of(arr)
    if bb is None:
        return arr
    h, s, v, a, skin, uniform, vest, cap, shoes = masks(arr, bb)
    oh, os_, ov = h.copy(), s.copy(), v.copy()
    if mode == "paramedico":
        oh[uniform], os_[uniform], ov[uniform] = 2, 0.72, np.clip(ov[uniform] * 1.9 + 0.12, 0, 0.9)
        oh[vest], os_[vest], ov[vest] = 0, 0.03, np.clip(ov[vest] * 1.4 + 0.52, 0, 0.98)
        oh[cap], os_[cap], ov[cap] = 0, 0.0, 0.10
    elif mode == "prankedy":
        for m in (uniform, vest):
            os_[m], ov[m] = 0.05, np.clip(ov[m] * 0.55, 0, 0.3)
        oh[cap], os_[cap], ov[cap] = 0, 0.0, 0.06
        oh[shoes], os_[shoes], ov[shoes] = 0, 0.0, 0.88
    elif mode == "granadero":
        oh[uniform], os_[uniform], ov[uniform] = 222, 0.55, np.clip(ov[uniform] * 0.9, 0, 0.45)
        oh[cap], os_[cap], ov[cap] = 0, 0.0, np.clip(ov[cap] * 0.35, 0.03, 0.18)
    out = from_hsv(oh, os_, ov, a)
    return out


def overlay(im, bb, mode, row, female):
    """Detalles dibujados encima (cruz, sombrero, camara, escudo, chongo). bb esta en coordenadas de la celda."""
    x0, y0, x1, y1 = bb
    w, h = x1 - x0 + 1, y1 - y0 + 1
    cx = (x0 + x1) // 2
    d = ImageDraw.Draw(im, "RGBA")
    front = row in (0, 1)
    side = row == 2
    flip = {0: 0, 1: 1, 2: 2, 3: 1, 4: 0}[row]
    if mode == "paramedico" and row != 2:
        px = cx + (4 if row in (1, 3) else 0)
        py = y0 + int(h * 0.36)
        k = max(3, int(h * 0.06))
        d.rectangle([px - k, py - k // 3, px + k, py + k // 3], fill=(200, 20, 20, 255))
        d.rectangle([px - k // 3, py - k, px + k // 3, py + k], fill=(200, 20, 20, 255))
        # botiquin rojo al hombro derecho (izquierda de la pantalla de frente)
        bx = cx - int(w * 0.36) if front else cx + int(w * 0.30)
        d.rectangle([bx - 6, y0 + int(h * 0.50), bx + 6, y0 + int(h * 0.50) + 10], fill=(190, 25, 25, 255), outline=(60, 0, 0, 255))
        d.rectangle([bx - 1, y0 + int(h * 0.50) + 2, bx + 1, y0 + int(h * 0.50) + 8], fill=(250, 250, 250, 255))
    if mode == "prankedy":
        hx0, hx1 = cx - int(w * 0.30), cx + int(w * 0.30)
        hy = y0 + int(h * 0.12)
        d.ellipse([hx0 - 6, hy - 2, hx1 + 6, hy + 9], fill=(8, 8, 8, 255), outline=(0, 0, 0, 255))
        d.rectangle([cx - int(w * 0.17), y0, cx + int(w * 0.17), hy], fill=(14, 14, 14, 255))
        if front or side:
            d.rectangle([cx - int(w * 0.17), y0 + int(h * 0.17), cx + int(w * 0.17), y0 + int(h * 0.22)], fill=(10, 10, 10, 255))
        for sx in (-1, 1):
            d.rectangle([cx + sx * int(w * 0.20) - 2, hy + 4, cx + sx * int(w * 0.20) + 2, hy + int(h * 0.16)], fill=(20, 14, 10, 255))
        cam_x = cx + int(w * 0.30) if row in (0, 1, 2) else cx - int(w * 0.30)
        d.rectangle([cam_x - 9, y0 + int(h * 0.30), cam_x + 9, y0 + int(h * 0.30) + 12], fill=(30, 30, 34, 255), outline=(0, 0, 0, 255))
        d.rectangle([cam_x + 9, y0 + int(h * 0.30) + 3, cam_x + 14, y0 + int(h * 0.30) + 9], fill=(60, 60, 70, 255))
    if mode == "granadero":
        sx = cx - int(w * 0.34) if row in (0, 1, 2) else cx + int(w * 0.30)
        top = y0 + int(h * 0.26)
        sw = int(w * 0.26) if front else int(w * 0.16)
        if row in (3, 4):
            sw = int(w * 0.22)
        d.rounded_rectangle([sx - sw // 2, top, sx + sw // 2, top + int(h * 0.52)], radius=5,
                            fill=(150, 205, 230, 55), outline=(200, 230, 245, 170), width=1)
        if front:
            d.rectangle([sx - sw // 4, top + int(h * 0.20), sx + sw // 4, top + int(h * 0.26)], fill=(20, 30, 60, 160))
        bxs = cx + int(w * 0.34) if row in (0, 1, 2) else cx - int(w * 0.34)
        d.line([bxs, y0 + int(h * 0.52), bxs + 2, y0 + int(h * 0.80)], fill=(10, 10, 10, 255), width=3)
        if female:
            d.ellipse([cx - 7, y0, cx + 7, y0 + 11], fill=(30, 22, 18, 255))


def derive(src_name, out_name, mode, female=False):
    im = Image.open(os.path.join(SRC, src_name)).convert("RGBA")
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    for r, c, box in cells(im):
        cell = im.crop(box)
        arr = np.asarray(cell).copy()
        if c == 7:  # la accion del original es disparar: se reutiliza el reposo
            src_box = [b for rr, cc, b in cells(im) if rr == r and cc == 0][0]
            arr = np.asarray(im.crop(src_box)).copy()
        bb = bbox_of(arr)
        if bb is None:
            continue
        rec = Image.fromarray(recolor(arr, mode, r), "RGBA")
        overlay(rec, bb, mode, r, female)
        out.paste(rec, (box[0], box[1]))
    path = os.path.join(SRC, out_name)
    out.save(path)
    print("escrito", path)


def draw_cat(path):
    cw = ch = 160
    sheet = Image.new("RGBA", (cw * COLS, ch * ROWS), (0, 0, 0, 0))
    S = 4  # supermuestreo
    fur, dark, white, eye = (110, 112, 118), (70, 72, 78), (235, 235, 238), (230, 200, 40)
    for r in range(ROWS):
        for c in range(COLS):
            big = Image.new("RGBA", (cw * S, ch * S), (0, 0, 0, 0))
            d = ImageDraw.Draw(big)
            phase = {0: 0, 1: 1, 2: 2, 3: 1, 4: 0}
            gait = [0, 1, 2, 3, 1, 2, 3, 0][c]
            run = c in (4, 5, 6)
            stretch = 1.18 if run else 1.0
            sit = c == 7
            bob = (2 if gait in (1, 3) else 0) * (2 if run else 1)
            base = 118 - bob
            # direccion: 0 frente, 1 3/4 frente, 2 perfil, 3 3/4 espalda, 4 espalda
            side = [0.0, 0.5, 1.0, 0.5, 0.0][r]
            facing_cam = r in (0, 1, 2)
            bw, bh = 46 * stretch, 24 if not sit else 32
            bx = 80 + side * 4
            body = [bx - bw * (0.5 + side * 0.35), base - bh, bx + bw * (0.5 + side * 0.35), base]
            # cola
            tail_dx = -(20 + side * 22) if r != 0 else 14
            tail = [(bx, base - 12), (bx + tail_dx, base - 26 - (6 if gait % 2 else 0)), (bx + tail_dx * 1.2, base - 40)]
            if r == 4:
                tail = [(bx, base - 12), (bx + 4, base - 30), (bx + 8, base - 48)]
            d.line([(x * S, y * S) for x, y in tail], fill=dark + (255,), width=9 * S)
            # patas (4, ciclo por gait)
            for i, off in enumerate((-0.35, -0.12, 0.12, 0.35)):
                lift = 0 if sit else (4 if (gait + i) % 2 == 0 and gait else 0)
                lx = bx + off * bw * (0.6 + side * 0.5)
                d.rectangle([(lx - 3.5) * S, (base - 8 - lift) * S, (lx + 3.5) * S, (base + 3 - lift) * S], fill=white + (255,) if r < 3 else dark + (255,), outline=(30, 30, 30, 255))
            d.ellipse([v * S for v in body], fill=fur + (255,), outline=(30, 30, 34, 255), width=S)
            # rayas atigradas
            for k in range(3):
                sx = body[0] + (k + 1) * (body[2] - body[0]) / 4
                d.line([(sx * S, (body[1] + 3) * S), ((sx + 2) * S, (body[1] + 11) * S)], fill=dark + (255,), width=2 * S)
            # pecho blanco al frente
            if facing_cam and side < 0.8:
                d.ellipse([(bx - 9) * S, (base - 15) * S, (bx + 9) * S, (base - 2) * S], fill=white + (255,))
            # cabeza
            hx = bx + (side * 24 if facing_cam else side * 20) + (0 if r != 4 else 0)
            if not facing_cam:
                hx = bx + side * 18
            hy = base - bh - (8 if not sit else 14)
            d.ellipse([(hx - 12) * S, (hy - 11) * S, (hx + 12) * S, (hy + 11) * S], fill=fur + (255,), outline=(30, 30, 34, 255), width=S)
            for sx in (-1, 1):
                d.polygon([((hx + sx * 11) * S, (hy - 6) * S), ((hx + sx * 7) * S, (hy - 18) * S), ((hx + sx * 2) * S, (hy - 9) * S)], fill=fur + (255,), outline=(30, 30, 34, 255))
            if facing_cam:
                ox = side * 5
                for sx in (-1, 1):
                    d.ellipse([(hx + ox + sx * 5 - 2) * S, (hy - 2) * S, (hx + ox + sx * 5 + 2) * S, (hy + 2) * S], fill=eye + (255,))
                d.ellipse([(hx + ox - 1.5) * S, (hy + 3) * S, (hx + ox + 1.5) * S, (hy + 5) * S], fill=(200, 120, 130, 255))
            if sit and r < 3:  # lame una pata
                d.rectangle([(hx - 8) * S, (hy + 6) * S, (hx - 2) * S, (hy + 16) * S], fill=white + (255,), outline=(30, 30, 30, 255))
            small = big.resize((cw, ch), Image.LANCZOS)
            sheet.paste(small, (c * cw, r * ch))
    sheet.save(path)
    print("escrito", path)


if __name__ == "__main__":
    random.seed(7)
    derive("police_officer_sheet_v1.png", "granadero_hombre_sheet.png", "granadero")
    derive("police_officer_sheet_v1.png", "granadero_mujer_sheet.png", "granadero", female=True)
    derive("police_officer_sheet_v1.png", "paramedico_sheet.png", "paramedico")
    derive("police_officer_sheet_v1.png", "prankedy_sheet.png", "prankedy")
    draw_cat(os.path.join(SRC, "cat_sheet.png"))
