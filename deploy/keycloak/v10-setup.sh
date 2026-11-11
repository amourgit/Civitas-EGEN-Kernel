#!/usr/bin/env bash
# V10 : monte un Keycloak de test (realm "egen", fichier egen-realm-test.json) et une base PostgreSQL, et ecrit news.yaml.
set -euo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)   # dossier du script, calcule AVANT tout cd
D=${V10_DIR:-/tmp/v10}; mkdir -p "$D"; cd "$D"

cp "$HERE/egen-realm-test.json" "$D/egen-realm.json"

cat > news.yaml <<'EOF1'
apiVersion: egen.civitas.africa/v1
kind: ServiceManifest
metadata:
  name: v10-news
  version: 1.0.0
  team: v10-test
runtime:
  type: container
  artifact: registry.test.invalid/v10-news:1.0.0
deployment:
  adapter: nomad
  image: registry.test.invalid/v10-news:1.0.0
  resources:
    cpu: "100m"
    memory: "64Mi"
  replicas:
    min: 1
    max: 1
health:
  http:
    endpoint: /health
    interval: 10s
    timeout: 2s
    failuresBeforeUnhealthy: 3
EOF1

docker rm -f egen-kc egen-pg-v2 >/dev/null 2>&1 || true
docker run -d --name egen-pg-v2 -e POSTGRES_DB=egen_registry -e POSTGRES_USER=egen \
  -e POSTGRES_PASSWORD=un-secret-de-test -p 5432:5432 postgres:16-alpine >/dev/null
docker run -d --name egen-kc -p 8180:8080 -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin-test \
  -v "$D/egen-realm.json:/opt/keycloak/data/import/egen-realm.json:ro" \
  quay.io/keycloak/keycloak:26.8.2 start-dev --import-realm >/dev/null
echo "attente de Keycloak (jusqu'a 3 minutes)..."
for i in $(seq 1 45); do
  code=$(curl -s -m 3 -o /dev/null -w "%{http_code}" http://localhost:8180/realms/egen || true)
  [ "$code" = "200" ] && break; sleep 4
done
echo "realm egen : HTTP $code (attendu : 200)"
