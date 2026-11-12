#!/usr/bin/env bash
# V3c : un corps de reponse de 1200 caracteres doit etre coupe a 500 caracteres + " [tronque]" (contrat K8, doc 07).
# Prerequis : le Kernel de V10 tourne (journal enregistre dans /tmp/v10/kernel.log) et le port 4646 est libre.
set -uo pipefail
LOG=${KERNEL_LOG:-/tmp/v10/kernel.log}; K=http://localhost:8080
NAME=v3c-$RANDOM

python3 - <<'PY' &
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
class H(BaseHTTPRequestHandler):
    def _any(self):
        n = int(self.headers.get("Content-Length") or 0)
        if n: self.rfile.read(n)
        body = ("X" * 1200).encode()
        self.send_response(500); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)
    do_GET = do_PUT = do_POST = do_DELETE = _any
    def log_message(self, *a): pass
ThreadingHTTPServer(("0.0.0.0", 4646), H).serve_forever()
PY
FAKE=$!; sleep 1
echo "faux Nomad sur le port 4646 : repond 500 avec un corps de 1200 caracteres"

TOKEN=$(curl -s -m 10 -d grant_type=client_credentials -d client_id=platform-operator-service -d client_secret=secret-operator-test \
  http://localhost:8180/realms/egen/protocol/openid-connect/token | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
sed "s/v10-news/$NAME/g" /tmp/v10/news.yaml > /tmp/v10/$NAME.yaml
code=$(curl -s -m 15 -o /dev/null -w "%{http_code}" -X POST $K/api/v1/services -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/yaml" -H "Idempotency-Key: v3c-$RANDOM$RANDOM" --data-binary @/tmp/v10/$NAME.yaml)
echo "declaration de $NAME : HTTP $code (attendu : 202)"
echo "attente de 45 secondes (5 tentatives : 2 + 4 + 8 + 16 secondes)..."; sleep 45
kill $FAKE 2>/dev/null

measure() { python3 -c "
import sys,re
l=sys.stdin.read()
if not l.strip(): print('  (aucune ligne trouvee dans le journal)'); sys.exit()
m=re.search(r'HTTP 500 — (X+)( \[tronque\])?', l)
print('  X consecutifs :', len(m.group(1)) if m else '(motif absent)', '| suffixe [tronque] :', bool(m and m.group(2)))"; }
echo "--- ligne du journal, tentative 1/5 (attendu : 500 X consecutifs, suffixe [tronque] : True) :"
grep "Echec d'adapter pour $NAME (1/5)" "$LOG" | tail -1 | measure
echo "--- Condition DeploymentReady apres la 5e tentative (meme attendu) :"
grep "serviceId=$NAME .*AdapterFailureThresholdExceeded" "$LOG" | tail -1 | measure
