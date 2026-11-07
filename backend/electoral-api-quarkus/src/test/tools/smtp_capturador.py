#!/usr/bin/env python3
"""Capturador SMTP minimo para probar el flujo de invitaciones de punta a punta.

Habla SMTP suficiente para que el mailer de Quarkus acepte el mensaje y lo
escriba en un archivo, para que el test pueda leer el link de activacion que
viaja en el email.

No es un servidor de produccion: no autentica, no cifra y no habla con
relay. Solo escucha en localhost y escribe cada mensaje en /tmp/mailbox/.
"""

import os
import socket
import threading
from datetime import datetime, timezone

HOST = "127.0.0.1"
PORT = 1025
MAILBOX = "/tmp/mailbox"


def handle(conn: socket.socket) -> None:
    conn.sendall(b"220 localhost ESMTP capturador\r\n")
    data_lines: list[str] = []
    in_data = False

    while True:
        raw = conn.recv(65536)
        if not raw:
            break
        line = raw.decode("utf-8", errors="replace")
        for cmd in line.splitlines():
            if in_data:
                if cmd == ".":
                    in_data = False
                    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%f")
                    path = os.path.join(MAILBOX, f"mail-{stamp}.eml")
                    with open(path, "w", encoding="utf-8") as fh:
                        fh.write("\n".join(data_lines))
                    print(f"capturado: {path}", flush=True)
                    data_lines = []
                    conn.sendall(b"250 OK queued\r\n")
                else:
                    data_lines.append(cmd)
                continue

            upper = cmd.upper()
            if upper.startswith("EHLO"):
                conn.sendall(b"250-localhost\r\n250 SIZE 10485760\r\n")
            elif upper.startswith("HELO"):
                conn.sendall(b"250 localhost\r\n")
            elif upper.startswith(("MAIL FROM", "RCPT TO")):
                conn.sendall(b"250 OK\r\n")
            elif upper.startswith("DATA"):
                in_data = True
                conn.sendall(b"354 End data with <CR><LF>.<CR><LF>\r\n")
            elif upper.startswith("RSET"):
                conn.sendall(b"250 OK\r\n")
            elif upper.startswith("NOOP"):
                conn.sendall(b"250 OK\r\n")
            elif upper.startswith("QUIT"):
                conn.sendall(b"221 Bye\r\n")
                return
            else:
                conn.sendall(b"250 OK\r\n")

    conn.close()


def main() -> None:
    os.makedirs(MAILBOX, exist_ok=True)
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, PORT))
    server.listen(20)
    print(f"capturador SMTP escuchando en {HOST}:{PORT}, escribiendo en {MAILBOX}", flush=True)

    while True:
        conn, _ = server.accept()
        threading.Thread(target=handle, args=(conn,), daemon=True).start()


if __name__ == "__main__":
    main()
