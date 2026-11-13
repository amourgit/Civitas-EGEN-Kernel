#!/usr/bin/env bash
# V6 : essai du runbook « Un service est en FAILED » (docs/architecture/23, scenario 1), avec le vrai cluster.
# On casse (RoleBinding supprime), on constate FAILED, on repare, on relance par Declare (cle d'idempotence rejouee puis nouvelle),
# puis on arrete le service. Chaque etape annonce ce que le runbook promet. Prerequis : cluster (v45-setup.sh) et Kernel demarres.
set -uo pipefail
D=${V45_DIR:-/tmp/v45}; LOG=${KERNEL_LOG:-$D/kernel.log}; K=http://localhost:8080
kc() { docker exec egen-k3s kubectl --kubeconfig=/etc/rancher/k3s/k3s.yaml "$@"; }
OP=$(curl -s -m 10 -d grant_type=client_credentials -d client_id=platform-operator-service -d client_secret=secret-operator-test \
  http://localhost:8180/realms/egen/protocol/openid-connect/token | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
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
post() { curl -s -m 15 -w ' [HTTP %{http_code}]' -X POST "$1" -H "Authorization: Bearer $OP" -H "Content-Type: application/yaml" -H "Idempotency-Key: $2" "${@:3}"; echo; }
echecs() { grep -c "Echec d'adapter pour v6-news" "$LOG"; }
status() { curl -s -m 10 $K/api/v1/services/v6-news/status -H "Authorization: Bearer $OP" | python3 -c "
import sys,json
try: d=json.load(sys.stdin)
except Exception as e: print('  (reponse illisible)'); sys.exit()
print('  phase =', d.get('phase'), '| observedGeneration =', d.get('observedGeneration'))
for c in d.get('conditions') or []:
    print('  condition', c.get('type'), '=', c.get('status'), '| reason =', c.get('reason'))"; }
K1=v6-a-$RANDOM; K2=v6-b-$RANDOM; K3=v6-c-$RANDOM

echo "=== 1. on casse : suppression du RoleBinding du Kernel"; kc -n egen delete rolebinding egen-kernel
echo "=== 2. declarer v6-news (cle $K1)";  post $K/api/v1/services $K1 --data-binary @"$D/v6-news.yaml"
echo "attente de 40 secondes..."; sleep 40
echo "=== 3. RUNBOOK : « au cinquieme echec consecutif, le service passe FAILED, Condition DeploymentReady = False, AdapterFailureThresholdExceeded »"; status
echo "=== 4. RUNBOOK : « le moteur ne reessaie pas un service FAILED » : echecs d'adapter journalises avant / apres 20 s"
A=$(echecs); sleep 20; B=$(echecs); echo "  avant : $A | apres : $B (attendu : 5 | 5)"
echo "=== 5. on repare : recreation du RoleBinding"; kc -n egen create rolebinding egen-kernel --role=egen-kernel --serviceaccount=egen:egen-kernel
echo "=== 6. RUNBOOK : « corriger la cause ne relance rien tout seul » (attendu : toujours FAILED, echecs inchanges)"
sleep 12; status; echo "  echecs journalises : $(echecs) (attendu : 5)"
echo "=== 7. RUNBOOK : « avec une cle deja utilisee, l'API rejoue l'ancienne reponse sans rien executer » (cle $K1 reutilisee)"
post $K/api/v1/services $K1 --data-binary @"$D/v6-news.yaml"; sleep 5; status
echo "=== 8. RUNBOOK : « nouvelle cle : le service repart de DECLARED, Conditions effacees, puis chemin normal » (cle $K2)"
post $K/api/v1/services $K2 --data-binary @"$D/v6-news.yaml"
sleep 4; echo "  apres 4 s :"; status
printf "  Deployment dans le cluster : "; kc -n egen get deployment v6-news -o 'jsonpath={.metadata.name} pods prets={.status.readyReplicas}{"\n"}' 2>&1
echo "attente de 45 secondes (sans Consul, le service retombera FAILED : ecart I14 connu)..."; sleep 45; echo "  apres 49 s :"; status
echo "=== 9. RUNBOOK : « FAILED -> STOPPING -> STOPPED » par actions/stop (cle $K3)"
post $K/api/v1/services/v6-news/actions/stop $K3; sleep 12; status
printf "  Deployment dans le cluster apres l'arret (attendu : NotFound) : "; kc -n egen get deployment v6-news 2>&1 | head -1
