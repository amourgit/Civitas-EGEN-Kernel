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
