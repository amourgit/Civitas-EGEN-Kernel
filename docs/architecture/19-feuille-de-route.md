# 19 — Feuille de route — phases livrables

Chaque phase produit un **livrable démontrable**, pas seulement du code
intermédiaire. L'ordre est pensé pour que chaque phase s'appuie sur un
socle déjà testé, et pour dérisquer en premier les intégrations externes
(Nomad/Consul), qui sont la partie la plus incertaine techniquement.

## Phase 0 — Fondations et décisions structurantes

**Objectif** : poser les décisions et les garde-fous avant d'écrire la
première ligne de logique métier du Kernel.

- [ ] Décisions actées et documentées : langage définitif du Kernel (voir
      [16](16-packages-et-stack-technique.md)), NATS vs Kafka en V1 (voir
      [07](07-ports-et-adapters.md#kafka-vs-nats-jetstream--quand-utiliser-lequel)),
      Vault pour les secrets.
- [ ] Squelette multi-module qui compile : `egen-domain`,
      `egen-application`, `egen-adapters/*`, `egen-api`,
      `egen-contracts`, `egen-bootstrap`, `egen-test-kit`.
- [ ] Mise en place immédiate des règles de dépendance de build (voir
      [16.2](16-packages-et-stack-technique.md#regle-de-build-a-faire-respecter-par-loutillage-pas-seulement-la-revue-de-code) /
      [17, niveau 4](17-strategie-de-tests.md#niveau-4--tests-darchitecture-fitness-functions))
      — **avant** d'écrire le premier port, pour qu'aucune violation ne
      s'accumule dès le départ.
- **Livrable** : squelette multi-module vide qui compile, avec les règles
  ArchUnit déjà actives (rouges tant qu'aucun code n'existe, c'est normal
  — elles doivent juste être prêtes).

## Phase 1 — Cœur hexagonal minimal + premier adapter (Deployment/Nomad)

**Objectif** : prouver le cycle Declare → Resolve → Compose → Delegate →
Observe → Reconcile de bout en bout sur **un seul** port.

- [ ] `egen-domain` : `ServiceManifest` (version minimale : id, version,
      runtime, deployment), `DesiredState`, `ObservedState`,
      `LifecycleStateMachine` réduite aux phases `DECLARED → REGISTERED →
      DEPLOYING → RUNNING → FAILED/STOPPED`.
- [ ] `egen-application` : `DeployServiceUseCase`, `ReconciliationEngine`
      v0 (work queue simple, un seul worker, resync périodique basique —
      pas encore de concurrence bornée avancée).
- [ ] `DeploymentPort` + `NomadDeploymentAdapter` complet selon le mapping
      de [07](07-ports-et-adapters.md#mapping-vers-lapi-http-nomad-v1-port-par-defaut-4646)
      (create/update/scale/stop/remove/getStatus/rollback).
- [ ] `egen-api` minimal : `POST /api/v1/services`, `GET
      /api/v1/services/{id}/status`.
- [ ] Tests niveaux 1, 2, 3 (Testcontainers Nomad), et un premier test de
      niveau 5 : déployer un service fixture réel, observer sa
      convergence jusqu'à `RUNNING`.
- **Livrable démontrable** : `curl -X POST /api/v1/services -d
  @manifest.yaml` déploie réellement un conteneur via Nomad et son statut
  converge vers `RUNNING` dans l'API.

## Phase 2 — Discovery (Consul) + Registry EGEN + cycle de vie complet

- [ ] `DiscoveryPort` + `ConsulDiscoveryAdapter` (voir
      [07](07-ports-et-adapters.md#discovery-port)).
- [ ] `RegistryStorePort` + adapter PostgreSQL (voir
      [08](08-registry.md)), avec verrouillage optimiste sur
      `generation`.
- [ ] `LifecycleStateMachine` complète (toutes les phases de
      [09](09-cycle-de-vie.md)), avec `Conditions`.
- [ ] Ordre d'arrêt gracieux implémenté et testé explicitement (voir
      [09](09-cycle-de-vie.md#ordre-darret-gracieux--un-piege-classique)).
- [ ] `GET /api/v1/discover/{serviceName}` exposé.
- **Livrable démontrable** : deux services fixtures se découvrent
  mutuellement via l'API EGEN (sans IP codée en dur), et un arrêt gracieux
  ne produit aucune erreur côté appelant pendant la fenêtre de
  propagation.

## Phase 3 — Dépendances, ordonnancement, Messaging (NATS)

- [ ] `DependencyGraph` complet : détection de cycle, tri topologique,
      dégradation gracieuse pour dépendances optionnelles (voir
      [10](10-gestion-des-dependances.md)).
- [ ] `MessagingPort` + `NatsMessagingAdapter` (voir
      [07](07-ports-et-adapters.md#mapping-vers-lapi--nats-jetstream)),
      enveloppe CloudEvents.
- [ ] Ordonnancement du déploiement initial multi-services respectant le
      graphe de dépendances.
- **Livrable démontrable** : trois services fixtures liés par dépendances
  `required` se déploient dans le bon ordre ; l'un publie un événement
  CloudEvents que l'autre consomme via `MessagingPort`.

## Phase 4 — Configuration, Secrets, Observabilité de production

- [ ] `ConfigurationPort` (voir [07](07-ports-et-adapters.md#configuration-port)),
      `SecretsPort` + `VaultAdapter` (voir
      [07](07-ports-et-adapters.md#secrets-port)).
- [ ] `ObservabilityPort` OpenTelemetry complet (traces propagées de bout
      en bout, métriques de [15.1](15-observabilite.md#les-trois-signaux-standardises-opentelemetry),
      logs structurés).
- [ ] Sécurité de base de l'API de contrôle : authentification + RBAC
      scoped par équipe (voir [13](13-api-et-contrats.md),
      [14](14-securite.md)).
- **Livrable démontrable** : une trace unique dans Jaeger/Tempo montre le
  chemin complet d'un déploiement, de l'appel API jusqu'au premier appel
  applicatif du service déployé ; un tableau de bord Grafana répond en un
  coup d'œil aux questions de [15.2](15-observabilite.md#questions-auxquelles-lobservabilite-egen-doit-repondre-en-un-coup-doeil).

## Phase 5 — Moteur de Workflow et communication fabric avancée

- [ ] `WorkflowEnginePort` natif (voir [11](11-moteur-de-workflow.md)),
      avec compensation Saga orchestrée, durabilité de l'exécution.
- [ ] Communication Fabric : SDK léger pour au moins deux langages (ex.
      Python + Java) et un mode sidecar de référence (voir
      [12](12-communication-fabric.md)).
- [ ] Résilience configurable (retry/timeout/circuit breaking) appliquée
      uniformément.
- **Livrable démontrable** : le scénario complet de
  [20 — Scénario de bout en bout](20-scenario-bout-en-bout.md) s'exécute
  de bout en bout, y compris un cas de compensation déclenché
  volontairement.

## Phase 6 (continue) — Extensibilité prouvée et durcissement

- [ ] Ajouter un **second** adapter de déploiement (ex.
      `DockerDeploymentAdapter` pour les environnements de développement
      local, ou `KubernetesDeploymentAdapter`) **sans modifier
      `egen-domain` ni `egen-application`** — c'est le test ultime de la
      promesse architecturale.
- [ ] Chaos engineering (voir [17, niveau 6](17-strategie-de-tests.md#niveau-6--chaos-engineering-a-introduire-progressivement)).
- [ ] Revue de sécurité externe (pentest ciblé sur l'API de contrôle et la
      gestion des secrets).
- [ ] Documentation opérateur complète (runbooks pour les scénarios
      `FAILED`, `COMPENSATION_FAILED`, divergence prolongée).

Sur la durée de vie du projet, chaque phase suivante ne doit jamais casser
les garanties déjà prouvées par la précédente — chaque livrable de phase
devient un test de non-régression permanent dans la CI.

---

## Definition of Done du projet EGEN

Une checklist vérifiable, à cocher lors de chaque revue d'architecture
trimestrielle.

**Architecture**

- [ ] EGEN est démontrablement un Control/Composition Plane (cycle de
      [04](04-moteur-de-reconciliation.md) implémenté et testé).
- [ ] Nomad exécute, Consul découvre, NATS/Kafka transporte — vérifié par
      le fait qu'aucune de ces responsabilités n'est dupliquée dans
      `egen-domain`/`egen-application` (voir [18](18-anti-patterns.md)).
- [ ] Toutes les intégrations passent par des adapters isolés (vérifié
      mécaniquement en CI).
- [ ] Un second adapter de déploiement a été ajouté sans modification du
      cœur (Phase 6) — preuve empirique, pas déclaration d'intention.

**Services**

- [ ] Au moins deux services fixtures dans des langages différents ont
      été déployés avec succès via EGEN.
- [ ] Un service a été ajouté à l'écosystème sans qu'aucun commit ne
      touche `egen-domain`, `egen-application`, ni `egen-adapters/*`.

**Infrastructure**

- [ ] Aucune capacité déjà fournie nativement par Nomad/Consul/NATS n'est
      réimplémentée dans le Kernel (audit de code périodique, garde-fou
      n°3 posé systématiquement en revue).

**Orchestration**

- [ ] Dépendances déclarées, ordonnancement topologique vérifié par test
      automatisé.
- [ ] Un workflow avec compensation a été exécuté en production ou en
      environnement de pré-production, avec un cas d'échec réel observé
      et correctement compensé.
- [ ] La divergence entre désiré et observé est mesurée en continu
      (métrique `egen_reconcile_failures_total` suivie, SLO de
      [15.3](15-observabilite.md#slo-du-kernel-lui-meme) respecté).

**Qualité**

- [ ] La suite de tests de niveau 1 (domaine) s'exécute sans aucun
      conteneur ni accès réseau.
- [ ] Les règles ArchUnit sont actives en CI et bloquent effectivement une
      violation volontaire de test.
- [ ] La fréquence de modification de `egen-domain` diminue dans le temps
      (métrique suivie via l'historique Git — un cœur qui bouge encore
      beaucoup après la Phase 3 est un signal d'alerte architecturale, pas
      de vélocité).
