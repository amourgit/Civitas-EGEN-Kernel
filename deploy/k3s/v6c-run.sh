#!/usr/bin/env bash
# V6c : suite de V6. Le service v6-news est STOPPING parce que Consul est absent. On lance Consul (comme dans docker-compose.yml)
# et on regarde si l'arret se termine tout seul (STOPPED, Deployment supprime). Prerequis : Kernel et cluster de V6 toujours en marche.
set -uo pipefail
kc() { docker exec egen-k3s kubectl --kubeconfig=/etc/rancher/k3s/k3s.yaml "$@"; }
phase() {
  OP=$(curl -s -m 10 -d grant_type=client_credentials -d client_id=platform-operator-service -d client_secret=secret-operator-test \
    http://localhost:8180/realms/egen/protocol/openid-connect/token | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
  curl -s -m 10 http://localhost:8080/api/v1/services/v6-news/status -H "Authorization: Bearer $OP" | python3 -c "
import sys,json
try: d=json.load(sys.stdin); print(d.get('phase'))
except Exception: print('(illisible)')"
}
echo "avant Consul : phase = $(phase)"
docker rm -f egen-consul >/dev/null 2>&1 || true
docker run -d --name egen-consul -p 8500:8500 hashicorp/consul:1.19 agent -dev -client=0.0.0.0 >/dev/null && echo "Consul lance (port 8500)"
for i in $(seq 1 6); do
  printf "attente de Consul : "; curl -s -m 3 -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8500/v1/status/leader && break; sleep 3
done
echo "le Kernel retente l'arret environ toutes les 30 a 60 secondes : surveillance pendant 2 minutes 30"
for i in $(seq 1 15); do
  sleep 10; P=$(phase); echo "  +$((i*10)) s : phase = $P"
  [ "$P" = "STOPPED" ] && break
done
printf "Deployment v6-news dans le cluster (attendu apres un arret reussi : NotFound) : "
kc -n egen get deployment v6-news 2>&1 | tail -1
