#!/usr/bin/env bash
# V5b : chemin d'ERREUR de l'adapter Kubernetes, avec le vrai cluster : on retire ses droits au Kernel, on declare un service,
# et on regarde le message d'erreur (journal et Condition) et l'absence du jeton. Prerequis : cluster (v45-setup.sh) et Kernel
# demarres (adapter kubernetes, journal /tmp/v45/kernel.log).
set -uo pipefail
D=${V45_DIR:-/tmp/v45}; LOG=${KERNEL_LOG:-$D/kernel.log}; K=http://localhost:8080
kc() { docker exec egen-k3s kubectl --kubeconfig=/etc/rancher/k3s/k3s.yaml "$@"; }
TOK=$(cat "$D/k8s-token.txt"); SIG=${TOK: -40}
OP=$(curl -s -m 10 -d grant_type=client_credentials -d client_id=platform-operator-service -d client_secret=secret-operator-test \
  http://localhost:8180/realms/egen/protocol/openid-connect/token | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")

echo "=== 1. on retire ses droits au Kernel (suppression du RoleBinding)"
kc -n egen delete rolebinding egen-kernel
sed 's/v45-news/v5b-news/g' "$D/k8s-news.yaml" > "$D/v5b-news.yaml"
echo "=== 2. declarer v5b-news"
curl -s -m 15 -o /dev/null -w "HTTP %{http_code} (attendu : 202)\n" -X POST $K/api/v1/services -H "Authorization: Bearer $OP" \
  -H "Content-Type: application/yaml" -H "Idempotency-Key: v5b-$RANDOM$RANDOM" --data-binary @"$D/v5b-news.yaml"
echo "attente de 40 secondes (5 tentatives : 2 + 4 + 8 + 16 secondes)..."; sleep 40

echo; echo "=== 3. message d'erreur dans le journal, tentative 1/5 (attendu : « Kubernetes a rejete ... HTTP 403 — {...forbidden...} »)"
grep "Echec d'adapter pour v5b-news (1/5)" "$LOG" | tail -1 | cut -c1-700
echo; echo "=== 4. longueur du message apres « HTTP 403 — » dans le journal (borne : 500 caracteres)"
grep "Echec d'adapter pour v5b-news (1/5)" "$LOG" | tail -1 | python3 -c "
import sys
l=sys.stdin.read(); i=l.find('HTTP 403 — ')
print('  corps recopie :', len(l[i+len('HTTP 403 — '):].rstrip('\n')) if i>=0 else '(motif HTTP 403 absent)', 'caracteres')"
echo; echo "=== 5. ce que voit n'importe quel appelant authentifie : le message de la Condition, via GET /status (K11)"
curl -s -m 10 $K/api/v1/services/v5b-news/status -H "Authorization: Bearer $OP" | python3 -c "
import sys,json
d=json.load(sys.stdin)
def walk(x):
    if isinstance(x,dict):
        if x.get('type')=='DeploymentReady' or x.get('reason')=='AdapterFailureThresholdExceeded': print('  ',{k:(str(v)[:300]) for k,v in x.items()})
        for v in x.values(): walk(v)
    elif isinstance(x,list):
        for v in x: walk(v)
walk(d)" 2>&1 | head -6

echo; echo "=== 6. le jeton Kubernetes dans le journal et la reponse (attendu : 0 partout)"
printf "fin du jeton (40 car.) dans le journal : "; grep -c -F -- "$SIG" "$LOG"
printf "jeton complet dans le journal          : "; grep -c -F -- "$TOK" "$LOG"
printf "fin du jeton dans GET /status          : "; curl -s -m 10 $K/api/v1/services/v5b-news/status -H "Authorization: Bearer $OP" | grep -c -F -- "$SIG"
