#!/usr/bin/env bash
# V4/V5 : avec le Kernel demarre (adapter kubernetes, journal /tmp/v45/kernel.log) :
#  - declare un service sur le cluster ; - V4 : revisionHistoryLimit ; - jeton Kubernetes absent du journal et des reponses ;
#  - V5 : vrais corps d'erreur de Kubernetes (422 et 403).
set -uo pipefail
D=${V45_DIR:-/tmp/v45}; LOG=${KERNEL_LOG:-$D/kernel.log}; K=http://localhost:8080; API=https://localhost:6443
kc() { docker exec egen-k3s kubectl --kubeconfig=/etc/rancher/k3s/k3s.yaml "$@"; }
TOK=$(cat "$D/k8s-token.txt")
OP=$(curl -s -m 10 -d grant_type=client_credentials -d client_id=platform-operator-service -d client_secret=secret-operator-test \
  http://localhost:8180/realms/egen/protocol/openid-connect/token | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")

cat > "$D/k8s-news.yaml" <<'EOY'
apiVersion: egen.civitas.africa/v1
kind: ServiceManifest
metadata:
  name: v45-news
  version: 1.26.0
  team: v45-test
runtime:
  type: container
  artifact: nginx:1.26.0
deployment:
  adapter: kubernetes
  image: nginx:1.26.0
  resources:
    cpu: "100m"
    memory: "64Mi"
  replicas:
    min: 1
    max: 3
health:
  http:
    endpoint: /
    interval: 10s
    timeout: 2s
    failuresBeforeUnhealthy: 3
EOY

echo "=== 1. declarer v45-news sur le cluster (adapter kubernetes, image nginx:1.26.0)"
curl -s -m 15 -o /dev/null -w "HTTP %{http_code} (attendu : 202)\n" -X POST $K/api/v1/services -H "Authorization: Bearer $OP" \
  -H "Content-Type: application/yaml" -H "Idempotency-Key: v45-$RANDOM$RANDOM" --data-binary @"$D/k8s-news.yaml"
echo "attente du pod (jusqu'a 4 minutes ; l'image nginx est telechargee)..."
r=""; for i in $(seq 1 48); do r=$(kc -n egen get deployment v45-news -o 'jsonpath={.status.readyReplicas}' 2>/dev/null); [ "$r" = "1" ] && break; sleep 5; done
echo "pods prets : ${r:-0} (attendu : 1), apres environ $((i*5)) s"
kc -n egen get deployment v45-news 2>&1 | head -3

echo; echo "=== 2. V4 : revisionHistoryLimit lu dans le cluster (attendu : 10)"
kc -n egen get deployment v45-news -o 'jsonpath=revisionHistoryLimit = {.spec.revisionHistoryLimit}{"\n"}'

echo; echo "=== 3. le jeton Kubernetes n'apparait ni dans le journal ni dans une reponse (attendu : 0 partout)"
SIG=${TOK: -40}
printf "fin du jeton (40 car.) dans le journal : "; grep -c -F -- "$SIG" "$LOG"
printf "jeton complet dans le journal          : "; grep -c -F -- "$TOK" "$LOG"
printf "fin du jeton dans GET /status          : "; curl -s -m 10 $K/api/v1/services/v45-news/status -H "Authorization: Bearer $OP" | grep -c -F -- "$SIG"
printf "fin du jeton dans GET /services/...    : "; curl -s -m 10 $K/api/v1/services/v45-news -H "Authorization: Bearer $OP" | grep -c -F -- "$SIG"

show() { python3 -c "
import sys,json
code,raw=sys.argv[1],sys.stdin.read()
print('  HTTP', code)
try:
    d=json.loads(raw)
    print('  kind    :', d.get('kind'), '| reason :', d.get('reason'), '| code :', d.get('code'))
    print('  message :', d.get('message'))
    for c in (d.get('details') or {}).get('causes') or []:
        print('  cause   :', c.get('reason'), '|', c.get('field'), '|', c.get('message'))
except Exception as e:
    print('  corps non JSON :', raw[:300])
print('  longueur du corps :', len(raw), 'caracteres')" "$1"; }
DEP() { printf '{"apiVersion":"apps/v1","kind":"Deployment","metadata":{"name":"v5-invalide"},"spec":{"replicas":%s,"selector":{"matchLabels":{"app":"x"}},"template":{"metadata":{"labels":{"app":"x"}},"spec":{"containers":[{"name":"c","image":"nginx"}]}}}}' "$1"; }
call() { curl -s -m 15 -w '\n%{http_code}' --cacert "$D/k3s-ca.pem" -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" -X POST "$API/apis/apps/v1/namespaces/$1/deployments" -d "$2"; }

echo; echo "=== 4. V5 : vrai corps d'erreur Kubernetes, cas 1 : champ invalide (replicas = -1), attendu HTTP 422"
out=$(call egen "$(DEP -1)"); printf '%s' "${out%$'\n'*}" | show "${out##*$'\n'}"
echo; echo "=== 5. V5 : vrai corps d'erreur Kubernetes, cas 2 : namespace interdit au Kernel (default), attendu HTTP 403"
out=$(call default "$(DEP 1)"); printf '%s' "${out%$'\n'*}" | show "${out##*$'\n'}"
