# 13 — API & Contract Infrastructure — l'API de contrôle publique d'EGEN

## Double surface : REST + gRPC

- **REST/JSON** (OpenAPI 3.1) : pour les intégrations humaines/outillage
  (console web, CLI, CI/CD, scripts d'exploitation). Toutes les mutations
  (`POST`, `PUT`, `PATCH`) sur des ressources de premier niveau retournent
  `202 Accepted` (voir [04.5](04-moteur-de-reconciliation.md#anti-pattern-a-bannir--la-reconciliation-synchrone-dans-lapi))
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

### Découverte — `GET /api/v1/discover/{serviceName}`

Répond `200` avec les instances saines **et**, lorsque le service est
déclaré, la politique `resiliency` de sa cible (valeurs effectives, défauts
appliqués — voir [06](06-service-manifest.md#resilience-declaree-par-la-cible-resiliency)) :

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
