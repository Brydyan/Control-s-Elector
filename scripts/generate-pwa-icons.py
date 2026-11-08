#!/usr/bin/env python3
"""Genera los iconos de la PWA a partir de la geometria de la marca.

El favicon que habia en el repo era un PNG de 64x64 que el manifest declaraba
como 192x192 y 512x512. Eso es falso: el navegador recien instalado ve una
imagen de 64px y avisa, y la instalabilidad de la PWA queda en duda.

Este script redibuja la marca con la misma geometria (medida sobre el PNG
original, no inventada) y la exporta a los tamanos que el manifest declara:

  - icon-192.png          proposito "any"       -> 192x192, la marca llena el lienzo
  - icon-512.png          proposito "any"       -> 512x512, la marca llena el lienzo
  - icon-512-maskable.png proposito "maskable" -> 512x512, fondo de marca y glifo
                                                   blanco dentro de la zona segura
                                                   (80% central), que es lo que
                                                   exige el recorte del sistema
  - apple-touch-icon.png  180x180               -> icono de iOS: opaco, porque iOS
                                                   dibuja sobre negro lo que
                                                   tiene transparencia
  - favicon.png           64x64                 -> el favicon existente, redibujado
                                                   desde la misma fuente para que
                                                   no haya dos marcas distintas

Se dibuja a 4x y se reduce con LANCZOS: el resultado tiene el mismo aspecto que
el original pero con el borde limpio a cualquier resolucion.

Uso:  python3 scripts/generate-pwa-icons.py
Requiere: Pillow (PIL).
"""

from __future__ import annotations

import os

from PIL import Image, ImageDraw

# Geometria de la marca, medida sobre el favicon.png original de 64x64.
# Todo en unidades de un lienzo de 64, para poder escalar a cualquier tamano.
CANVAS = 64.0
BRAND = (71, 138, 255, 255)  # #478AFF
WHITE = (255, 255, 255, 255)
TRANSPARENT = (0, 0, 0, 0)

RING_CENTER = (31.5, 31.5)
RING_OUTER_R = 31.5
RING_INNER_R = 25.5

DISC_CENTER = (31.5, 31.5)
DISC_R = 14.6

DOT_CENTER = (52.4, 11.6)
DOT_R = 6.6
# Radio del halo que separa el punto satelite del anillo: el anillo esta cortado
# en un arco de ~36 grados alrededor del punto (angulo 299..335, medido sobre el
# PNG original).
DOT_KNOCKOUT_R = 9.0

SUPERSAMPLE = 4

ASSETS = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "frontend",
    "app",
    "src",
    "assets",
    "icon",
)


def _mark(draw: ImageDraw.ImageDraw, k: float, offset: float, ink, hole) -> None:
    """Dibuja la marca (anillo + disco interior + punto satelite).

    ``k`` es la escala del lienzo de referencia (64) al lienzo real y ``offset``
    el desplazamiento para centrar una marca encogida. ``hole`` es el color con
    el que se perfora el anillo: transparente para el icono "any", el color de
    fondo para el maskable.
    """

    def pt(x: float, y: float) -> tuple[float, float]:
        return (x * k + offset, y * k + offset)

    def r(value: float) -> float:
        return value * k

    def circle(center, radius, color) -> None:
        cx, cy = pt(*center)
        draw.ellipse([cx - r(radius), cy - r(radius), cx + r(radius), cy + r(radius)], fill=color)

    # Anillo: circulo exterior del color de la tinta, luego se perfora el hueco.
    circle(RING_CENTER, RING_OUTER_R, ink)
    circle(RING_CENTER, RING_INNER_R, hole)
    # El punto satelite va separado del anillo: se perfora un halo a su alrededor,
    # que es lo que abre el corte del anillo (~36 grados) en el PNG original.
    circle(DOT_CENTER, DOT_KNOCKOUT_R, hole)
    # Disco interior y punto satelite.
    circle(DISC_CENTER, DISC_R, ink)
    circle(DOT_CENTER, DOT_R, ink)


def render_any(size: int, ink=BRAND) -> Image.Image:
    """Icono 'any': la marca en color de marca sobre fondo transparente."""
    big = size * SUPERSAMPLE
    img = Image.new("RGBA", (big, big), TRANSPARENT)
    _mark(ImageDraw.Draw(img), big / CANVAS, 0.0, ink, TRANSPARENT)
    return img.resize((size, size), Image.LANCZOS)


def render_maskable(size: int) -> Image.Image:
    """Icono 'maskable': fondo opaco de marca y glifo blanco en la zona segura.

    La zona segura del recorte del sistema es el 80% central del lienzo; el
    glifo se dibuja al 60% para que ningun recorte lo corte.
    """
    big = size * SUPERSAMPLE
    img = Image.new("RGBA", (big, big), BRAND)
    inner = big * 0.60
    _mark(ImageDraw.Draw(img), inner / CANVAS, (big - inner) / 2, WHITE, BRAND)
    return img.resize((size, size), Image.LANCZOS)


def main() -> None:
    os.makedirs(ASSETS, exist_ok=True)
    salidas = [
        ("favicon.png", render_any(64)),
        ("icon-192.png", render_any(192)),
        ("icon-512.png", render_any(512)),
        ("icon-512-maskable.png", render_maskable(512)),
        # apple-touch-icon: iOS exige 180x180 y NO soporta transparencia — con
        # fondo transparente la dibuja sobre negro. Se genera opaca, con el fondo
        # de marca y el glifo blanco.
        ("apple-touch-icon.png", render_maskable(180)),
    ]
    for nombre, img in salidas:
        ruta = os.path.join(ASSETS, nombre)
        img.save(ruta, "PNG", optimize=True)
        print(f"{ruta}  {img.size[0]}x{img.size[1]}")


if __name__ == "__main__":
    main()
