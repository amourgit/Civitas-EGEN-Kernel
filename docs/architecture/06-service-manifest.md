# 06 — Le contrat de service — EGEN Service Manifest

C'est **le contrat technique central** entre un service autonome et EGEN.
Il s'inspire directement de la structure de [Score](https://score.dev)
(workload-centric, resources nommées, séparation stricte dev/plateforme)
tout en l'étendant pour couvrir événements, dépendances de service (pas
seulement de ressources d'infra), et cycle de vie — ce que Score ne couvre
pas.

## Schéma complet annoté

```yaml
apiVersion: egen.civitas.africa/v1
kind: ServiceManifest
metadata:
  name: news-service
  version: 2.4.0
  team: content-platform
  labels:
    domain: actualite
    tier: backend
  annotations:
    repo: https://git.civitas.africa/news-service
    contact: content-platform@civitas.africa

# --- CE QUE LE SERVICE EST -------------------------------------------------
runtime:
  type: container                 # container | binary | external | vm
  language: python                # informatif uniquement — EGEN ne l'interprète jamais

deployment:
  adapter: nomad                  # quel Deployment Adapter cible ce service (extensible)
  image: registry.civitas.africa/news-service:2.4.0
  resources:
    cpu: "500m"
    memory: "512Mi"
  replicas:
    min: 2
    max: 6
    autoscaling:
      metric: cpu
      target: 70
  updateStrategy:
    type: rolling                 # rolling | blue-green | canary
    maxUnavailable: 1
    healthyDeadline: 120s
  placement:
    constraints:
      - attribute: "${node.datacenter}"
        operator: "="
        value: "dc1"

# --- COMMENT LE SERVICE EST JOIGNABLE ---------------------------------------
network:
  endpoints:
    - name: http-api
      protocol: http
      port: 8080
      path: /health          # utilisé par HealthSpec ci-dessous
    - name: grpc-internal
      protocol: grpc
      port: 9090

health:
  http:
    endpoint: /health
    interval: 10s
    timeout: 2s
    failuresBeforeUnhealthy: 3
  readiness:
    endpoint: /ready
    interval: 5s

# --- CE DONT LE SERVICE A BESOIN --------------------------------------------
dependencies:
  services:
    - name: notification-service
      versionConstraint: ">=1.2.0"
      required: true             # si false, dégradation gracieuse acceptée
    - name: storage-service
      versionConstraint: ">=3.0.0"
      required: true
  resources:                      # inspiré de Score : ressources externes nommées, non-service
    - name: primary-db
      type: postgres
      params:
        sizeGb: 20
    - name: cache
      type: redis

# --- CE QUE LE SERVICE PUBLIE / CONSOMME COMME ÉVÉNEMENTS -------------------
events:
  publishes:
    - type: africa.civitas.news.article.created.v1
      schemaRef: schemas/article-created.v1.json
    - type: africa.civitas.news.article.updated.v1
      schemaRef: schemas/article-updated.v1.json
  consumes:
    - type: africa.civitas.notification.delivery-failed.v1
      subscriptionGroup: news-service-consumers
      deadLetter:
        maxRetries: 5
        strategy: exponential-backoff

# --- CONFIGURATION (SANS VALEURS) -------------------------------------------
configuration:
  required:
    - key: FEATURE_FLAG_COMMENTS
      type: boolean
      default: "false"
  secretsRefs:                     # EGEN ne stocke JAMAIS un secret — uniquement une référence
    - key: DB_PASSWORD
      provider: vault
      path: secret/data/news-service/db

# --- POLITIQUE DE CYCLE DE VIE ----------------------------------------------
lifecycle:
  startupOrder: after-dependencies   # after-dependencies | independent
  shutdown:
    gracePeriod: 30s
    drainConnections: true
  manualApprovalRequired: false      # true en prod pour certains domaines sensibles
```

## Ce que le manifeste ne contient jamais

- Une valeur de secret (uniquement une **référence**, résolue à
  l'exécution par le Configuration/Secrets Adapter — voir
  [07 — Ports & Adapters](07-ports-et-adapters.md#configuration-port)).
- Une règle métier (« un article ne peut être publié que si… ») — cela
  reste dans le code du service.
- Une adresse IP ou un endpoint codé en dur d'un autre service — toujours
  une résolution par `discover(name)` (voir [07.2](07-ports-et-adapters.md#discovery-port)).
- Une logique de traitement des événements — EGEN transporte, il
  n'interprète pas `data`.

## Validation du manifeste (obligatoire avant tout `Declare`)

1. Schéma JSON/YAML strict (rejette les champs inconnus — évite la dérive
   silencieuse).
2. Cohérence sémantique : SemVer valide, pas de cycle dans
   `dependencies.services` **au niveau de l'écosystème entier** (le graphe
   global, pas seulement local à ce manifeste), noms d'endpoints uniques.
3. Vérification que l'`adapter` déclaré dans `deployment.adapter` est bien
   enregistré dans le Kernel (voir [07.1](07-ports-et-adapters.md#deployment-port)).
4. Compatibilité de version : si `notification-service` existe déjà en
   version `1.1.0` et que ce manifeste exige `>=1.2.0`, le manifeste est
   accepté (déclaré) mais la réconciliation place le service en phase
   `DEGRADED`/`BLOCKED` avec une `Condition` explicite (voir
   [09](09-cycle-de-vie.md#conditions)) — jamais un rejet silencieux ni un
   crash.
