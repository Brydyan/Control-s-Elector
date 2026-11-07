#!/usr/bin/env python3
"""Extrae el token de activación del email de un destinatario concreto.

No sirve "el email más reciente": durante el flujo llegan al mismo buzón la
invitación, el reenvío y la confirmación de activación, y la confirmación no
tiene token. Por eso se busca por destinatario y, si hay varios emails para
ese destinatario, se toma el último (el reenvío es siempre posterior a la
invitación original).

Uso:  extraer_token.py <destinatario> [carpeta-del-buzon]
"""

import glob
import os
import quopri
import re
import sys

DESTINATARIO = sys.argv[1]
BUZON = sys.argv[2] if len(sys.argv) > 2 else "/tmp/mailbox"

patron_destinatario = re.compile(r"^To:\s*(.*)$", re.IGNORECASE | re.MULTILINE)
patron_token = re.compile(r"token=([A-Za-z0-9._%-]+)")

ultimo = None
for ruta in sorted(glob.glob(os.path.join(BUZON, "*.eml"))):
    crudo = open(ruta, "rb").read()
    if b"\r\n\r\n" in crudo:
        cuerpo = crudo.split(b"\r\n\r\n", 1)[-1]
    else:
        cuerpo = crudo.split(b"\n\n", 1)[-1]
    texto = quopri.decodestring(cuerpo).decode("utf-8", errors="replace")

    cabeceras = patron_destinatario.search(crudo.decode("utf-8", errors="replace"))
    if not cabeceras:
        continue
    if DESTINATARIO.strip().lower() not in cabeceras.group(1).strip().lower():
        continue

    encontrado = patron_token.search(texto)
    if encontrado:
        ultimo = encontrado.group(1)

print(ultimo or "")
