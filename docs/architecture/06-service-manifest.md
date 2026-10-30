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

# --- RÉSILIENCE DEMANDÉE À TOUS CEUX QUI APPELLENT CE SERVICE (optionnel) ----
resiliency:
  timeout: 10s                       # par tentative ; défaut 30s
  retry:
    maxAttempts: 3                   # tentatives TOTALES (la première incluse) ; défaut 1 = aucun retry
    backoff: exponential             # fixed | exponential ; défaut fixed
    baseDelay: 200ms                 # défaut 200ms
  circuitBreaker:
    failureThreshold: 5              # échecs consécutifs qui ouvrent le circuit ; défaut 5
    openDuration: 30s                # avant l'essai semi-ouvert ; défaut 30s

# --- INTERFACES ANNONCÉES À LA COUCHE AGENTIQUE (optionnel) -----------------
interfaces:                          # opaque pour le Kernel — voir 21.3
  - type: openapi                    # mcp | openapi | … (liste ouverte)
    ref: /internal/openapi.json
    riskDefault: read                # read | write | sensitive | … (optionnel, liste ouverte)

# --- POLITIQUE DE CYCLE DE VIE ----------------------------------------------
lifecycle:
  startupOrder: after-dependencies   # after-dependencies | independent
  shutdown:
    gracePeriod: 30s
    drainConnections: true
  manualApprovalRequired: false      # true en prod pour certains domaines sensibles
```

## Labels réservés posés par la plateforme

Le préfixe `platform.civitas.africa/` est **réservé** à la Plateforme ([22](22-frontiere-avec-la-plateforme.md#224--les-labels-réservés)). Seul `platform-operator-service` pose ces clés ; le Kernel les stocke et les restitue comme n'importe quel label, **sans jamais s'en servir** pour décider, router ou autoriser (garde-fou n°1). Ils servent à la traçabilité et au diagnostic ; la source de vérité reste le catalogue.

| Clé | Type | Sens |
|---|---|---|
| `platform.civitas.africa/instance-id` | label | Identifiant de l'instance dans le catalogue |
| `platform.civitas.africa/domain` | label | Domaine fonctionnel de l'instance |
| `platform.civitas.africa/implementation` | label | Implémentation (ex. `radicale`) |
| `platform.civitas.africa/implementation-version` | label | Version d'implémentation |
| `platform.civitas.africa/owner-cell` | label | Identifiant **opaque** de la cellule propriétaire |
| `platform.civitas.africa/config-version` | annotation | Version de la configuration résolue (sert à produire une nouvelle génération pour les clés `RESTART`, voir `docs/plateforme/09-configuration-multi-niveaux.md`, dépôt `Civitas-EGEN-Business`) |

**À savoir.** `runtime.type` (`container`, `binary`, `external`, `vm`) est aujourd'hui **conservé mais non différencié** : tout déploiement passe par le Deployment Adapter. Une application déjà exploitée hors du Kernel n'est donc **pas** gérée par lui en V1 ([22](22-frontiere-avec-la-plateforme.md#229--limites-actuelles-du-kernel)).

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

## Événements et configuration — ce que fait le Kernel

**`events`.** Le Kernel ne lit jamais le contenu d'un événement ; il ne
s'intéresse qu'au `type`, qui sert de **sujet de messagerie**. Un `type` ne
contient donc ni espace ni caractère générique (`*`, `>`) — sans quoi un
manifeste pourrait capter ou polluer d'autres sujets — et n'est déclaré
qu'une fois dans `publishes` et une fois dans `consumes`. À la réconciliation
(voir [04](04-moteur-de-reconciliation.md) et la Condition
`MessagingBindingsReady`, [09](09-cycle-de-vie.md)), le Kernel garantit
**un flux de messagerie par type d'événement**, publié *ou* consommé, nommé
de façon déterministe à partir du type
([12](12-communication-fabric.md#flux-dévénements)) : l'ordre de déclaration
entre publieur et consommateur n'a ainsi aucune importance. `schemaRef`,
`subscriptionGroup` et `deadLetter` sont conservés tels quels ;
`deadLetter` n'est **pas encore appliqué** par le Kernel (il décrit la
consommation, que le service réalise via son SDK).

**`configuration`.** Aucune valeur n'y figure jamais, et EGEN ne stocke jamais
un secret — uniquement une référence.

- `required[]` : `key` (une lettre ou `_`, puis lettres, chiffres, `_`, `.`
  ou `-`), `type`, `default`. Le doc ne fixe pas le vocabulaire des types ; il
  est **fermé** : `string` (défaut si omis), `boolean` (`true`/`false`),
  `integer`, `number`. Un `default` doit être du type déclaré. Une clé sans
  `default` **doit** être fournie par la configuration d'environnement.
- `secretsRefs[]` : `key`, `provider`, `path` obligatoires. Le Kernel
  vérifie à la réconciliation que la référence se résout (Condition
  `ConfigResolved`) ; il ne lit jamais la valeur.
- Une clé n'est déclarée qu'une fois, **configuration et secrets confondus**.

**`dependencies.resources`.** Ressources externes nommées, non-service
(`name`, `type`, `params` à valeurs scalaires). Elles sont **référencées, pas
gérées en V1** ([20](20-scenario-bout-en-bout.md), étape 2) : le Kernel les
stocke et les restitue, n'interprète ni `type` ni `params`, et ne provisionne
rien — c'est hors du périmètre du Deployment Adapter. Un nom n'est déclaré
qu'une fois.

## Interfaces annoncées (`interfaces`)

Bloc **optionnel et additif**, ajouté pour la couche agentique
([21](21-systeme-agents-et-gateway.md), 21.3) : un manifeste qui l'ignore
reste valide et inchangé, et tout consommateur qui ne le comprend pas
l'ignore. Chaque entrée porte `type`, `ref` et, facultativement,
`riskDefault`.

**Opaque pour le Kernel, comme `deployment.image`** : il stocke et restitue
ces chaînes, n'ouvre jamais `ref`, n'interprète jamais `type` ni
`riskDefault`, et **ne valide aucune valeur contre une liste fermée**. C'est
ce qui permet d'ajouter `grpc`, ou un nouveau niveau de risque, sans migrer
le Kernel. Seule la forme est contrôlée (`type` et `ref` non vides).

## Blocs pris en charge par le Kernel

Le schéma est **strict** : tout champ inconnu est refusé (`400`, champ
nommé). Le tableau dit, pour chaque bloc du schéma annoté ci-dessus, ce que
cette version du Kernel accepte ; un champ du schéma annoté qui n'y figure
pas est refusé explicitement plutôt qu'accepté puis ignoré en silence
(configurer un autoscaling qui n'aurait aucun effet serait pire qu'un refus).

| Bloc | Pris en charge | Refusé aujourd'hui |
| --- | --- | --- |
| `metadata` | `name`, `version`, `team`, `labels`, `annotations` (conservés et restitués, informatifs : le Kernel n'en tire aucune décision) | — |
| `runtime` | `type`, `language`, `artifact` (optionnel : défaut `deployment.image`) | — |
| `deployment` | `adapter`, `image`, `resources`, `replicas.min/max` | `replicas.autoscaling`, `updateStrategy`, `placement` |
| `network` | — | le bloc entier |
| `health` | `http` | `readiness` |
| `dependencies` | `services`, `resources` (référencées, jamais gérées en V1) | — |
| `events` | `publishes`, `consumes` (dont `schemaRef`, `subscriptionGroup`, `deadLetter`) | — |
| `configuration` | `required`, `secretsRefs` | — |
| `resiliency` | le bloc entier | — |
| `interfaces` | le bloc entier | — |
| `lifecycle` | `shutdown.gracePeriod` | `startupOrder`, `shutdown.drainConnections`, `manualApprovalRequired` |

Chaque ligne « Refusé » rejoindra la colonne « Pris en charge » avec la phase
qui en implémente le comportement, jamais avant.

## Résilience déclarée par la cible (`resiliency`)

Bloc **optionnel et additif** (un manifeste qui l'ignore reste valide et
inchangé). Il suit le modèle « target-based » de Dapr : la politique est
déclarée **une seule fois, par le service appelé**, et appliquée
uniformément par tous ses appelants (SDK, sidecar, Moteur de Workflow) — pas
recodée dans chaque appelant. La sémantique exacte (ce qui compte comme un
échec, la machine à états du circuit breaker, les règles de retry) est
normative dans [12](12-communication-fabric.md#sémantique-normative-de-la-résilience).

- Chaque champ omis prend sa valeur par défaut (indiquée ci-dessus) ; un
  service **sans** bloc `resiliency` reçoit les mêmes valeurs par défaut —
  il n'existe pas d'appel inter-services « non protégé ».
- Durées : un entier suivi de `ms`, `s`, `m` ou `h`.
- Bornes (validation à `Declare`) : `timeout` > 0 ; `maxAttempts` entre 1 et
  10 ; `baseDelay` > 0 ; `failureThreshold` ≥ 1 ; `openDuration` > 0.
- Ce bloc ne dit **jamais** *quoi* appeler ni *comment interpréter* une
  réponse métier : il ne porte que des seuils techniques.

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
5. Bornes du bloc `resiliency` (voir ci-dessus), refusées avec un message
   nommant le champ fautif.
