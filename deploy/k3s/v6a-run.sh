#!/usr/bin/env bash
# V6a rejoue sur le comportement corrige (ecart E4, docs/architecture/09 « Quand le dé-enregistrement échoue »).
# Deux modes :
#   bash deploy/k3s/v6a-run.sh absent   (defaut) : Consul reste ARRETE pendant tout l'arret
#   bash deploy/k3s/v6a-run.sh retour            : Consul est relance 7 secondes apres la demande d'arret
# Prerequis : cluster (v45-setup.sh) et Kernel demarres avec le jar le plus recent (redemarrer T1 apres tout mvn verify).
set -uo pipefail
MODE=${1:-absent}; D=${V45_DIR:-/tmp/v45}; LOG=${KERNEL_LOG:-$D/kernel.log}; K=http://localhost:8080
kc() { docker exec egen-k3s kubectl --kubeconfig=/etc/rancher/k3s/k3s.yaml "$@"; }
token() { curl -s -m 10 -d grant_type=client_credentials -d client_id=platform-operator-service -d client_secret=secret-operator-test \
  http://localhost:8180/realms/egen/protocol/openid-connect/token | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])" 2>/dev/null; }
OP=$(token); [ -n "$OP" ] || { echo "ECHEC : pas de jeton Keycloak (Keycloak arrete ?) ; verifier : docker ps"; exit 1; }
status() { curl -s -m 10 $K/api/v1/services/v6-news/status -H "Authorization: Bearer $OP" | python3 -c "
import sys,json
try: d=json.load(sys.stdin)
except Exception: print('(reponse illisible : Kernel arrete ?)'); sys.exit()
cs={c.get('type'):c for c in (d.get('conditions') or [])}
c=cs.get('DiscoveryDeregistered')
print('phase =',d.get('phase'),'|', ('DiscoveryDeregistered = %s / %s / %s' % (c.get('status'),c.get('reason'),(c.get('message') or '')[:130])) if c else 'DiscoveryDeregistered : absente')"; }
dereg_lines() { grep -c "Echec de deregistrement pour v6-news" "$LOG"; }

echo "=== 0. mode : $MODE"
echo "=== 1. Consul arrete (le Kernel ne peut pas dé-enregistrer)"
docker rm -f egen-consul >/dev/null 2>&1 || true
printf "  Consul : "; curl -s -m 3 -o /dev/null -w "HTTP %{http_code} (attendu : 000)\n" http://localhost:8500/v1/status/leader
kc -n egen delete deployment v6-news >/dev/null 2>&1 || true
cat > "$D/v6-news.yaml" <<'EOY'
apiVersion: egen.civitas.africa/v1
kind: ServiceManifest
metadata:
  name: v6-news
  version: 1.26.0
  team: v6-test
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
echo "=== 2. declarer v6-news et attendre qu'un pod tourne (jusqu'a 150 s)"
curl -s -m 15 -w ' [HTTP %{http_code}]\n' -X POST $K/api/v1/services -H "Authorization: Bearer $OP" -H "Content-Type: application/yaml" \
  -H "Idempotency-Key: v6a-decl-$RANDOM" --data-binary @"$D/v6-news.yaml"
for i in $(seq 1 30); do
  kc -n egen get pods --no-headers 2>/dev/null | grep -q Running && break; sleep 5
done
kc -n egen get pods --no-headers 2>&1 | head -3
echo "  etat avant l'arret :"; printf "  "; status
A=$(dereg_lines)
echo "=== 3. demande d'arret (POST actions/stop), puis surveillance toutes les 5 s pendant 110 s"
curl -s -m 15 -w ' [HTTP %{http_code}] (attendu : 202)\n' -X POST $K/api/v1/services/v6-news/actions/stop \
  -H "Authorization: Bearer $OP" -H "Idempotency-Key: v6a-stop-$RANDOM"
T0=$(date +%s); RETURNED=0
for i in $(seq 1 22); do
  sleep 5; NOW=$(( $(date +%s) - T0 ))
  if [ "$MODE" = "retour" ] && [ "$RETURNED" = 0 ] && [ "$NOW" -ge 7 ]; then
    docker run -d --name egen-consul -p 8500:8500 hashicorp/consul:1.19 agent -dev -client=0.0.0.0 >/dev/null && echo "  +${NOW} s : Consul relance"; RETURNED=1
  fi
  printf "  +%3d s : " "$NOW"; P=$(status); echo "$P"
  case "$P" in "phase = STOPPED"*) break;; esac
done
echo "=== 4. controles"
echo "  lignes de journal « Echec de deregistrement » pour cet arret : $(( $(dereg_lines) - A ))  (mode absent : 5 attendues ; mode retour : 1 a 4 si l'arret reussit apres le retour de Consul)"
grep "Echec de deregistrement pour v6-news" "$LOG" | tail -6 | sed 's/^/    /'
printf "  Deployment v6-news (attendu apres STOPPED : NotFound) : "; kc -n egen get deployment v6-news 2>&1 | tail -1
echo "  statut final :"; printf "  "; status
echo "Nettoyage : docker rm -f egen-consul (si lance) ; puis bash deploy/k3s/v45-clean.sh quand tu as fini."
