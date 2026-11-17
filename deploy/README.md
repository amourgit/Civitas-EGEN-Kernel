# Déployer le Kernel EGEN

Ce document décrit comment construire et lancer le Kernel comme image de conteneur (phase P0 du
[document 19](../docs/architecture/19-feuille-de-route.md)). Les étapes P0-2 (job Nomad) à P0-6 y seront ajoutées au fur et à mesure.

## Ordre de démarrage d'une installation neuve

Décidé par Samuel le 14 novembre 2026 (point ouvert 1, option a). **Keycloak est lancé avant le Kernel**, parce que le Kernel
ne démarre pas sans l'adresse de son fournisseur d'identité.

1. Lancer PostgreSQL (le Registry) et Keycloak.
2. Attendre que le realm réponde : `curl -s -o /dev/null -w '%{http_code}\n' http://<keycloak>/realms/<realm>` doit afficher `200`.
3. Lancer le Kernel avec `EGEN_OIDC_ISSUER_URL` et `EGEN_OIDC_JWKS_URL` renseignées.
4. Vérifier : `curl -s -o /dev/null -w '%{http_code}\n' http://<kernel>:8080/api/v1/health` doit afficher `200`.

## Image du Kernel (étape P0-1)

**Construire** (depuis la racine du dépôt ; la compilation se fait dans l'image, tests exclus) :

```
docker build -t egen-kernel:dev .
```

L'image tourne sous l'utilisateur non-root `10001`, écoute sur le port `8080` et ne contient que l'environnement d'exécution Java et l'application.

**Variables d'environnement.** Celles dont le Kernel ne peut pas se passer (il ne démarre pas sans elles) :

| Variable | Rôle |
|---|---|
| `EGEN_OIDC_ISSUER_URL` | adresse du realm Keycloak qui émet les jetons (par exemple `http://localhost:8180/realms/egen`) |
| `EGEN_OIDC_JWKS_URL` | adresse des clés publiques de ce realm (par exemple `…/protocol/openid-connect/certs`) |
| `EGEN_REGISTRY_DB_PASSWORD` | mot de passe PostgreSQL du Registry |

Les autres ont une valeur par défaut, donnée entre parenthèses : `EGEN_REGISTRY_DB_URL` (`jdbc:postgresql://localhost:5432/egen_registry`), `EGEN_REGISTRY_DB_USER` (`egen`), `EGEN_DEPLOYMENT_ADAPTERS` (`nomad` ; `kubernetes` ou une liste), `NOMAD_ADDR` (`http://localhost:4646`), `NOMAD_TOKEN`, `EGEN_KUBERNETES_ADDRESS` (`https://kubernetes.default.svc`), `EGEN_KUBERNETES_NAMESPACE` (`egen`), `EGEN_KUBERNETES_TOKEN`, `EGEN_KUBERNETES_CA_CERT_PATH`, `CONSUL_HTTP_ADDR` (`http://localhost:8500`), `CONSUL_HTTP_TOKEN`, `VAULT_ADDR` (`http://localhost:8200`), `VAULT_TOKEN`, `NATS_URL` (`nats://localhost:4222`), `OTEL_EXPORTER_OTLP_ENDPOINT` (vide : aucun export de traces, [15](../docs/architecture/15-observabilite.md)).

**Lancer, avec l'environnement de test de `deploy/k3s/v45-setup.sh`** (le cluster, Keycloak et PostgreSQL sont déjà démarrés par ce script ; `--network host` permet au conteneur d'atteindre `localhost`) :

```
set -a; source /tmp/v45/env.sh; set +a
docker run --rm --network host \
  -e EGEN_OIDC_ISSUER_URL -e EGEN_OIDC_JWKS_URL -e EGEN_REGISTRY_DB_PASSWORD \
  -e EGEN_DEPLOYMENT_ADAPTERS -e EGEN_KUBERNETES_ADDRESS -e EGEN_KUBERNETES_NAMESPACE -e EGEN_KUBERNETES_TOKEN \
  -v /tmp/v45/k3s-ca.pem:/tmp/k3s-ca.pem:ro -e EGEN_KUBERNETES_CA_CERT_PATH=/tmp/k3s-ca.pem \
  egen-kernel:dev
```

**Lancer avec un système de fichiers en lecture seule** (le Kernel doit pouvoir écrire dans `/tmp`) : ajouter `--read-only --tmpfs /tmp` à la commande ci-dessus.

**Statut au 14 novembre 2026** : le `Dockerfile` est écrit mais **jamais construit ni lancé** (vérification V15 du [document 19](../docs/architecture/19-feuille-de-route.md)).

## Job Nomad du Kernel (étape P0-2)

Le fichier `deploy/nomad/egen-kernel.nomad.hcl` décrit le Kernel comme un job Nomad : un groupe `kernel` (nombre d'exemplaires réglé par `count`), un port HTTP choisi par Nomad, un contrôle de santé sur `GET /api/v1/health` toutes les 10 secondes, une mise à jour progressive (un exemplaire à la fois, retour automatique à la version précédente si le nouvel exemplaire ne devient pas sain) et un arrêt gracieux de 30 secondes.

**Les variables du job** (voir les `variable` du fichier) : les trois obligatoires sont `oidc_issuer_url`, `oidc_jwks_url` et `registry_db_password` ; toutes les autres ont une valeur par défaut. Copiez `deploy/nomad/egen-kernel.vars.example.hcl` en `deploy/nomad/mon-env.vars.hcl`, remplacez les valeurs, et ne versionnez jamais ce fichier réel (le dépôt ignore `deploy/nomad/*.vars.hcl`).

**Essai sur le Nomad de développement** (Keycloak, PostgreSQL et le reste déjà démarrés ; l'image `egen-kernel:dev` construite sur la même machine) :

```
bash deploy/nomad-dev.sh                    # dans un terminal à part : Nomad en mode développement
nomad job validate -var-file=deploy/nomad/mon-env.vars.hcl deploy/nomad/egen-kernel.nomad.hcl
nomad job plan     -var-file=deploy/nomad/mon-env.vars.hcl deploy/nomad/egen-kernel.nomad.hcl
nomad job run      -var-file=deploy/nomad/mon-env.vars.hcl deploy/nomad/egen-kernel.nomad.hcl
nomad job status egen-kernel                # attendu : une allocation « running », un contrôle « passing »
nomad service info egen-kernel              # donne l'adresse et le port : curl http://<adresse>:<port>/api/v1/health → 200
```

**Limites connues de cette étape**
- Les mots de passe passent par les variables du job et sont **visibles dans sa spécification** : acceptable pour un essai ; l'étape P0-4 les remplace par Vault et ajoute le bloc `vault` du job.
- Le conteneur utilise le réseau de l'hôte (`network_mode = "host"`) pour joindre PostgreSQL, Keycloak, Consul et NATS par `localhost`, comme dans l'environnement d'essai. Une installation avec réseau isolé et adresses réelles est à faire avec l'étape P0-5.
- `count` doit rester à `1`. L'étape P0-3 (écrite le 16 novembre 2026, à valider par `mvn verify`) partage les clés d'idempotence entre exemplaires, mais **deux exemplaires ne sont pas encore supportés** : le moteur de workflow ne coordonne pas ses exécutions entre exemplaires (étape P0-3b du document 19).
- Le fichier n'a **jamais été validé par Nomad** (vérification V17 du [document 19](../docs/architecture/19-feuille-de-route.md)).
