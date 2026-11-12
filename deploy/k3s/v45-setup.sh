#!/usr/bin/env bash
# V4/V5 : cree un cluster Kubernetes de test (k3s dans Docker), le compte de service du Kernel avec ses droits minimaux,
# son jeton (2 h) et le certificat de l'autorite, puis ecrit /tmp/v45/env.sh (variables du Kernel).
# Reprend exactement la configuration du test de niveau 3 (KubernetesDeploymentAdapterIT, valide chez Samuel).
set -euo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
D=${V45_DIR:-/tmp/v45}; mkdir -p "$D"
KC="docker exec egen-k3s kubectl --kubeconfig=/etc/rancher/k3s/k3s.yaml"

# 1) Keycloak (jetons) : relance seulement s'il ne repond pas ; PostgreSQL (Registry) : toujours recree, pour repartir d'un Registry vide
code=$(curl -s -m 3 -o /dev/null -w "%{http_code}" http://localhost:8180/realms/egen || true)
if [ "$code" != "200" ]; then
  echo "Keycloak absent : lancement de deploy/keycloak/v10-setup.sh (qui recree aussi PostgreSQL)"
  bash "$HERE/../keycloak/v10-setup.sh"
else
  echo "Keycloak deja en marche ; PostgreSQL recree (Registry vide)"
  docker rm -f egen-pg-v2 >/dev/null 2>&1 || true
  docker run -d --name egen-pg-v2 -e POSTGRES_DB=egen_registry -e POSTGRES_USER=egen \
    -e POSTGRES_PASSWORD=un-secret-de-test -p 5432:5432 postgres:16-alpine >/dev/null
fi

# 2) Cluster k3s (conteneur privilegie, memes options que le test de niveau 3)
docker rm -f egen-k3s >/dev/null 2>&1 || true
docker run -d --name egen-k3s --privileged --cgroupns=host -v /sys/fs/cgroup:/sys/fs/cgroup:rw \
  --tmpfs /run --tmpfs /var/run -p 6443:6443 rancher/k3s:v1.31.2-k3s1 \
  server --disable=traefik --tls-san=localhost >/dev/null
echo "attente du cluster k3s (jusqu'a 3 minutes)..."
for i in $(seq 1 45); do
  docker logs egen-k3s 2>&1 | grep -q "Node controller sync successful" && break; sleep 4
done
docker logs egen-k3s 2>&1 | grep -q "Node controller sync successful" || { echo "ECHEC : le cluster n'a pas demarre. Fin des journaux :"; docker logs egen-k3s 2>&1 | tail -15; exit 1; }
for i in $(seq 1 30); do $KC get nodes >/dev/null 2>&1 && break; sleep 2; done
$KC get nodes

# 3) Namespace egen, compte de service egen-kernel et droits minimaux (documents 07 et 14)
docker exec -i egen-k3s kubectl --kubeconfig=/etc/rancher/k3s/k3s.yaml apply -f - <<'RBAC'
apiVersion: v1
kind: Namespace
metadata:
  name: egen
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: egen-kernel
  namespace: egen
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: egen-kernel
  namespace: egen
rules:
  - apiGroups: ["apps"]
    resources: ["deployments"]
    verbs: ["get", "create", "patch", "delete"]
  - apiGroups: ["apps"]
    resources: ["deployments/scale"]
    verbs: ["get", "patch"]
  - apiGroups: ["apps"]
    resources: ["replicasets"]
    verbs: ["list"]
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: egen-kernel
  namespace: egen
subjects:
  - kind: ServiceAccount
    name: egen-kernel
    namespace: egen
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: egen-kernel
RBAC

# 4) Jeton du compte de service (2 heures) et certificat de l'autorite de l'API
$KC -n egen create token egen-kernel --duration=2h > "$D/k8s-token.txt"
$KC config view --raw --minify -o 'jsonpath={.clusters[0].cluster.certificate-authority-data}' | base64 -d > "$D/k3s-ca.pem"
echo "jeton : $(wc -c < "$D/k8s-token.txt") caracteres ; certificat : $(grep -c 'BEGIN CERTIFICATE' "$D/k3s-ca.pem") certificat(s)"

# 5) Contrôle : l'API repond-elle avec ce jeton et ce certificat ?
# (le role du Kernel n'a pas le droit « list » sur les deployments : on teste donc la liste des pods, qu'il a le droit de lire)
printf "API Kubernetes avec le jeton du Kernel, liste des pods (attendu : 200) : "
curl -s -o /dev/null -w "%{http_code}\n" --cacert "$D/k3s-ca.pem" -H "Authorization: Bearer $(cat "$D/k8s-token.txt")" \
  https://localhost:6443/api/v1/namespaces/egen/pods

# 6) Variables du Kernel
cat > "$D/env.sh" <<ENV
export EGEN_OIDC_ISSUER_URL=http://localhost:8180/realms/egen
export EGEN_OIDC_JWKS_URL=http://localhost:8180/realms/egen/protocol/openid-connect/certs
export EGEN_REGISTRY_DB_PASSWORD=un-secret-de-test
export EGEN_DEPLOYMENT_ADAPTERS=kubernetes
export EGEN_KUBERNETES_ADDRESS=https://localhost:6443
export EGEN_KUBERNETES_NAMESPACE=egen
export EGEN_KUBERNETES_CA_CERT_PATH=$D/k3s-ca.pem
export EGEN_KUBERNETES_TOKEN=\$(cat $D/k8s-token.txt)
ENV
echo "variables du Kernel ecrites dans $D/env.sh"
