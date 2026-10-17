"""Service de fixture du test de niveau 5 (docs/architecture/17, niveau 5 ; 20, parties 1 et 2).

Un VRAI processus HTTP, deploye par Nomad comme n'importe quel service : il ecoute sur le port dynamique que
Nomad lui reserve ($NOMAD_PORT_http) et repond 200 sur /health. Il n'a aucune logique metier ; il se laisse
seulement observer et piloter par le test :

  GET  /health                              -> 200 (sonde Nomad/Consul)
  *    <tout autre chemin>                  -> journalise "METHODE chemin", repond {"ok":"true"} (ou l'echec force)
  GET  /__calls                             -> {"service": ..., "calls": ["POST /internal/validate", ...]}
  POST /__fail?path=/internal/publish&status=500   -> force ce statut pour ce chemin
  POST /__reset                             -> vide le journal et les echecs forces
  POST /__crash                             -> le processus s'arrete brutalement (panne reelle)
"""
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

NAME = os.environ.get("NOMAD_JOB_NAME", "fixture")
PORT = int(os.environ["NOMAD_PORT_http"])

lock = threading.Lock()
calls = []
failures = {}


class Handler(BaseHTTPRequestHandler):
    def _reply(self, status, body):
        data = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _handle(self):
        url = urlparse(self.path)
        length = int(self.headers.get("Content-Length") or 0)
        if length:
            self.rfile.read(length)
        if url.path == "/health":
            return self._reply(200, {"status": "UP"})
        if url.path == "/__calls":
            with lock:
                return self._reply(200, {"service": NAME, "calls": list(calls)})
        if url.path == "/__fail":
            query = parse_qs(url.query)
            with lock:
                failures[query["path"][0]] = int(query.get("status", ["500"])[0])
            return self._reply(200, {"ok": "true"})
        if url.path == "/__reset":
            with lock:
                calls.clear()
                failures.clear()
            return self._reply(200, {"ok": "true"})
        if url.path == "/__crash":
            self._reply(200, {"ok": "true"})
            threading.Thread(target=lambda: (time.sleep(0.2), os._exit(1)), daemon=True).start()
            return None
        with lock:
            calls.append(self.command + " " + url.path)
            status = failures.get(url.path, 200)
        return self._reply(status, {"service": NAME, "ok": "true"})

    do_GET = do_POST = do_PUT = do_DELETE = _handle

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
