# Cluster Kubernetes d'essai pour le Kernel EGEN (V4 et V5)

Scripts de la vérification V4/V5 ([docs/architecture/19](../../docs/architecture/19-feuille-de-route.md)). Ils reprennent la
configuration de `KubernetesDeploymentAdapterIT` (k3s `rancher/k3s:v1.31.2-k3s1` dans un conteneur privilégié).

- `v45-setup.sh` : lance (si besoin) Keycloak et PostgreSQL, crée le cluster, le namespace `egen`, le compte de service `egen-kernel` avec ses droits minimaux, son jeton de 2 heures et le certificat de l'autorité, puis écrit `/tmp/v45/env.sh` (variables du Kernel).
- `v45-run.sh` : avec le Kernel démarré, déclare un service sur le cluster, lit `revisionHistoryLimit`, cherche le jeton dans le journal, et capture deux vrais corps d'erreur de Kubernetes (422 et 403).
- `v45-clean.sh` : supprime le cluster.
- `v5b-run.sh` : avec le cluster et le Kernel démarrés, retire ses droits au Kernel et lit le message d'erreur Kubernetes dans le journal et dans la Condition (chemin d'erreur par le Kernel, V5b).

**Limite connue** : pour qu'un service atteigne `RUNNING`, il faut Consul et une instance qui s'y enregistre ; sans eux le service passe `FAILED` même si les pods sont prêts (écart I14).
