#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Compara el ancho/alto OPACO de cada vista de los sprites de vehiculos isometricos con la caja de colision proyectada a la
pantalla, por rumbo de mundo (cada 45 grados). Sirve para calibrar CarModel.collisionDimensions y CarVariantRules:
el cuerpo solido debe quedar ~95 % del ancho de la vista lateral (vista "e"), un poco DENTRO del dibujo.

USO (desde PolitecnicoOpenWorld/):  python tools/audit_vehicle_boxes.py
"""
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'assets', 'SPRITES', 'VEHICLES', 'Iso')
# nombre: (largo del lienzo en m = IsoVehicle.drawMeters, largo del cuerpo solido, ancho del cuerpo solido)
VEHICLES = {
    'sedan': (5.2, 4.8, 2.0), 'pickup': (5.6, 5.2, 2.2), 'van': (5.8, 5.4, 2.25),
    'box_truck': (7.8, 7.2, 2.4), 'semi_tractor': (6.8, 6.3, 2.5), 'bus': (11.5, 10.7, 2.6),
}
VIEW = {0: 'n', 1: 'ne', 2: 'e', 3: 'se', 4: 's', 5: 'se', 6: 'e', 7: 'ne'}
R2 = math.sqrt(2)


def sector(h):
    e, n = math.sin(h), math.cos(h)
    phi = (math.degrees(math.atan2(e - n, (e + n) / 2)) + 360) % 360
    return int((phi + 22.5) / 45) % 8


def proj(e, n):
    return (e - n) / R2, (e + n) / (2 * R2)


def main():
    print('vehiculo view rumbo  sprite(m) ancho/alto  cuerpo(m) ancho/alto   dif.ancho  cuerpo/sprite')
    for name, (draw, length, width) in VEHICLES.items():
        for h in range(0, 360, 45):
            hr = math.radians(h)
            fe, fn = math.sin(hr), math.cos(hr)
            pe, pn = -fn, fe
            pts = [proj(fe * sx * length / 2 + pe * sy * width / 2, fn * sx * length / 2 + pn * sy * width / 2) for sx in (-1, 1) for sy in (-1, 1)]
            cw = max(p[0] for p in pts) - min(p[0] for p in pts)
            ch = max(p[1] for p in pts) - min(p[1] for p in pts)
            view = VIEW[sector(hr)]
            im = Image.open(os.path.join(ROOT, name, view + '.webp')).convert('RGBA')
            ys, xs = np.where(np.asarray(im)[..., 3] > 40)
            sw = (xs.max() - xs.min() + 1) / im.width * draw
            sh = (ys.max() - ys.min() + 1) / im.width * draw
            print(f'{name:12s} {view:2s} {h:3d}   {sw:5.2f}/{sh:4.2f}        {cw:5.2f}/{ch:4.2f}        {sw - cw:+5.2f}      {cw / sw:4.2f}')


if __name__ == '__main__':
    main()
