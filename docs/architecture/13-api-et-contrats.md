# 13 — API & Contract Infrastructure — l'API de contrôle publique d'EGEN

## Double surface : REST + gRPC

- **REST/JSON** (OpenAPI 3.1) : pour les intégrations humaines/outillage
  (console web, CLI, CI/CD, scripts d'exploitation). Toutes les mutations
  (`POST`, `PUT`, `PATCH`) sur des ressources de premier niveau retournent
  `202 Accepted` (voir [04.5](04-moteur-de-reconciliation.md#anti-pattern-à-bannir--la-réconciliation-synchrone-dans-lapi))
  avec un `Location` pointant vers la ressource de statut.
- **gRPC/Protobuf** : pour les intégrations machine-à-machine à fort
  volume ou basse latence (ex. un système CI qui déclenche des milliers de
  déploiements), et pour le mode « streaming de statut » (`watch` en flux
  continu plutôt qu'en polling REST).

## Ressources de premier niveau de l'API

```
/api/v1/services                          # CRUD sur ServiceManifest (Declare)
/api/v1/services/{id}/status              # lecture du ServiceStatus (Observe)
/api/v1/services/{id}/history             # historique des générations
/api/v1/services/{id}/configuration      # publication/lecture de la configuration technique d'un service (évolution A3)
/api/v1/services/{id}/actions/restart     # actions impératives explicites (rares — la norme reste déclarative)
/api/v1/services/{id}/actions/rollback
/api/v1/dependencies/graph                # lecture du DependencyGraph global
/api/v1/workflows                         # CRUD sur WorkflowDefinition
/api/v1/workflows/{id}/executions         # déclenchement et suivi d'exécutions
/api/v1/discover/{serviceName}            # proxy pratique vers DiscoveryPort.resolve (lecture seule)
/api/v1/events/publish                    # point d'entrée générique de publication d'événement technique (pour systèmes externes hors écosystème EGEN)
/api/v1/health                            # santé du Kernel lui-même
/api/v1/adapters                          # introspection : quels adapters sont enregistrés et opérationnels
```

### Lecture des manifestes — `GET /api/v1/services` et `GET /api/v1/services/{id}`

Le CRUD de `/api/v1/services` ([ci-dessus](#ressources-de-premier-niveau-de-lapi)) comprend la
**lecture** du manifeste déclaré. Réponse de `GET /api/v1/services/{id}` :

```json
{ "serviceId": "news-service", "generation": 3,
  "manifest": { "apiVersion": "egen.civitas.africa/v1", "kind": "ServiceManifest", "metadata": { … },
                "runtime": { … }, "deployment": { … }, "interfaces": [ { "type": "openapi", "ref": "/internal/openapi.json", "riskDefault": "read" } ], … } }
```

- Le `manifest` est **relisible par `Declare`** : mêmes champs que la déclaration,
  valeurs *effectives* (défauts inclus), un bloc absent à la déclaration reste
  absent à la lecture. Le Kernel ne restitue que ce qu'il a persisté, et
  n'interprète rien — c'est ainsi que le bloc `interfaces`
  ([06](06-service-manifest.md#interfaces-annoncées-interfaces),
  [21](21-systeme-agents-et-gateway.md), 21.3) parvient à la couche agentique.
- `GET /api/v1/services` renvoie la liste de ces objets, par identifiant
  croissant.
- **Autorisation** : comme `…/status`, le manifeste d'un service est lisible par
  son équipe propriétaire ou par un administrateur ; la liste ne contient que
  les services que l'appelant a le droit de lire. Un service inconnu est un `404`.

### Découverte — `GET /api/v1/discover/{serviceName}`

Répond `200` avec les instances saines **et**, lorsque le service est
déclaré, la politique `resiliency` de sa cible (valeurs effectives, défauts
appliqués — voir [06](06-service-manifest.md#résilience-déclarée-par-la-cible-resiliency)) :

```json
{ "serviceId": "notification-service",
  "instances": [ { "instanceId": "…", "address": "10.0.0.7", "port": 8080 } ],
  "resiliency": { "timeoutMillis": 10000,
                  "retry": { "maxAttempts": 3, "backoff": "exponential", "baseDelayMillis": 200 },
                  "circuitBreaker": { "failureThreshold": 5, "openDurationMillis": 30000 } } }
```

Un service inconnu du Registry (mais présent dans le Discovery) reçoit les
valeurs par défaut. C'est ce que consomment les SDK et le sidecar
([12](12-communication-fabric.md)).

### Workflows — contrat détaillé (Phase 5)

| Méthode & chemin | Rôle | Réponse |
| --- | --- | --- |
| `POST /api/v1/workflows` (`application/yaml`, voir [11](11-moteur-de-workflow.md)) | déclare ou remplace une `WorkflowDefinition` | `202` + `Location: /api/v1/workflows/{id}` |
| `GET /api/v1/workflows/{id}` | relit la définition (étapes en ordre topologique) | `200` / `404` |
| `POST /api/v1/workflows/{id}/executions` (corps JSON optionnel `{"input": {"clé": "valeur"}}`) | démarre une exécution | `202` + `Location: …/executions/{executionId}` |
| `GET /api/v1/workflows/{id}/executions/{executionId}` | statut, entrée et `StepResult` journalisés | `200` / `404` |
| `POST …/executions/{executionId}/actions/pause` · `…/resume` · `…/terminate` (corps JSON optionnel `{"reason": "…"}`) | `pause`, `resume`, `terminate` du moteur | `202` / `404` / `409` |

- **Erreurs** : contenu YAML/JSON illisible, workflow invalide (cycle, étape
  inconnue, durée mal formée…) ou identifiant mal formé → `400` ; définition
  ou exécution inconnue — *y compris une exécution demandée sous un autre
  workflow que le sien* → `404` ; transition d'état impossible (ex. `resume`
  d'une exécution terminée) → `409`.
- **Autorisation** : lecture pour tout appelant authentifié ; **toute
  mutation est réservée au rôle administrateur** (`egen.security.admin-role`),
  voir [14](14-securite.md).
- **Pas encore livré** : liste des définitions (`GET /api/v1/workflows`) et
  liste des exécutions d'un workflow — `WorkflowStorePort` n'expose pas de
  requête de listing ; elle sera ajoutée avec le premier besoin réel (console
  d'exploitation), pas par anticipation.

### Publication d'événement — `POST /api/v1/events/publish`

Point d'entrée générique pour qu'un processus **sans accès direct au broker**
publie un événement technique ([12](12-communication-fabric.md#flux-dévénements)).

- **Corps** (`application/json`) : un événement CloudEvents —
  `{ "specversion": "1.0", "type": "…", "source": "…", "egen_correlation_id": "…", "data": { … } }`.
  `type`, `source` et `data` sont obligatoires ; `data` est n'importe quelle
  valeur JSON et reste **opaque** : le Kernel la relaie sans la lire.
  `specversion`, `egen_correlation_id` et `egen_causation_id` sont optionnels
  (`specversion`, s'il est donné, doit valoir `1.0`) ; un identifiant de
  corrélation est généré s'il est absent. L'`id` et l'horodatage de l'événement
  sont attribués par le Kernel. Tout autre champ est refusé (`400`).
- **Relais** : vers le Messaging Port, sur le sujet `type`
  ([12](12-communication-fabric.md#flux-dévénements)). Le Kernel **ne crée pas**
  le flux : il existe parce qu'un service qui publie ce type a été réconcilié.
- **Réponse** : `202 Accepted`, corps
  `{ "id": "…", "type": "…", "egen_correlation_id": "…" }`. Pas d'en-tête
  `Location` : un événement n'a pas de ressource de suivi.
- **`Idempotency-Key` obligatoire** (mutation, voir [Sécurité de l'API](#sécurité-de-lapi)) ;
  sa répétition rejoue la première réponse sans republier.
- **Autorisation** : un appelant ne publie que ce qu'un manifeste annonce. Le
  `type` doit figurer dans `events.publishes` d'au moins un service déclaré,
  **et** l'appelant doit être administrateur ou porter l'équipe propriétaire de
  l'un de ces services ([14](14-securite.md)) ; sinon `403`.
- **Codes d'erreur** : corps illisible, champ obligatoire absent, `specversion`
  non supportée ou champ inconnu → `400` ; type non annoncé ou appelant non
  autorisé → `403` ; messagerie indisponible → `503` (rien n'a été publié).

### Publication de configuration — `PUT /api/v1/services/{id}/configuration`

*Contrat de l'évolution A3. L'état d'avancement est suivi dans [19](19-feuille-de-route.md).*

Cette API expose `ConfigurationPort.publish` ([07](07-ports-et-adapters.md#configuration-port)) aux clients de l'API. Elle publie la **configuration technique** d'un service : des paires clé/valeur que le service lit au démarrage ou à chaud. Le Kernel les transporte sans leur donner de sens métier.

**Écriture — `PUT /api/v1/services/{id}/configuration?environment=<env>`**

- **Corps** : `{ "values": { "<clé>": <valeur> } }`. Chaque valeur est un scalaire JSON (texte, booléen ou nombre), converti en texte. Un objet, un tableau, `null` ou un champ de premier niveau autre que `values` est refusé (`400`).
- **En-tête** : `Idempotency-Key` obligatoire. Un rejeu de la même clé renvoie la première réponse.
- **Sémantique** : **remplacement complet** du jeu de valeurs publié pour le couple `(service, environnement)`. Les valeurs par défaut déclarées dans le manifeste (`configuration.required[].default`) restent applicables aux clés absentes.
- **Validation** (toutes les violations sont renvoyées ensemble, chacune nomme une clé et une règle, **jamais une valeur**) :
  1. le service est déclaré (sinon `404`) ;
  2. chaque clé figure dans `configuration.required` du manifeste (sinon `400`) ;
  3. aucune clé n'est une clé de `configuration.secretsRefs` : un secret n'est jamais publié en valeur (sinon `400`) ;
  4. chaque valeur respecte le type déclaré (`string`, `boolean`, `integer`, `number`) (sinon `400`) ;
  5. toute clé **sans valeur par défaut** est présente (sinon `400` qui liste les clés manquantes).
- **Réponse** : `202 Accepted`, en-tête `Location` vers la lecture, corps `{ serviceId, environment, version, configurationUrl }`.
- **Effet** : si le service n'est ni arrêté ni en retrait, il est enfilé dans la work queue ; la réconciliation relit tout (level-triggered) et met à jour la `Condition` `ConfigResolved` ([09](09-cycle-de-vie.md)). Un service arrêté garde sa configuration, qui s'appliquera à un redéploiement.
- **Store indisponible** : `503`, rien n'est publié.

**Lecture — `GET /api/v1/services/{id}/configuration?environment=<env>`**

- `200 { serviceId, environment, version, values }`. Une configuration jamais publiée est vide, version `0`. `404` seulement si le service n'est pas déclaré. Une configuration ne contient **jamais** de secret.

**Sécurité** : même règle que les autres opérations sur un service : l'équipe propriétaire ou un administrateur ([14](14-securite.md)). Le droit est vérifié **avant** toute lecture du corps de la requête.

**Environnement** : `environment` absent = environnement par défaut du Kernel (`egen.environment`).

## Versionnage et compatibilité

- **Versionnage sémantique de l'API elle-même** (`/api/v1/`, `/api/v2/`…)
  — indépendant du versionnage des `ServiceManifest` individuels.
- **Idempotency-Key** (en-tête HTTP) obligatoire sur toutes les mutations
  pour permettre un retry client sûr côté API — complémentaire de
  l'idempotence interne des adapters (voir
  [04.3](04-moteur-de-reconciliation.md#idempotence--condition-de-survie-de-la-boucle)),
  cette fois côté contrat public.
- Toute évolution du schéma `ServiceManifest` est **rétro-compatible en
  lecture** (un vieux client doit pouvoir lire un manifeste plus récent en
  ignorant les nouveaux champs) — règles classiques d'évolution de schéma
  (champs optionnels seulement, jamais de suppression sans période de
  dépréciation documentée).

## Sécurité de l'API

Authentification obligatoire (mTLS pour machine-à-machine, ou jeton OIDC
pour les humains via la console), et autorisation par **RBAC** scoped par
équipe/domaine (`content-platform` ne doit pas pouvoir modifier le
manifeste de `billing-service` sans droit explicite) — l'API elle-même est
un citoyen de première classe du modèle de sécurité, jamais un point
d'accès de confiance implicite. Détail complet : [14 — Sécurité](14-securite.md).
