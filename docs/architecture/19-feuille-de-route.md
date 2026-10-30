# 19 — Feuille de route — phases livrables

Chaque phase produit un **livrable démontrable**, pas seulement du code
intermédiaire. L'ordre est pensé pour que chaque phase s'appuie sur un
socle déjà testé, et pour dérisquer en premier les intégrations externes
(Nomad/Consul), qui sont la partie la plus incertaine techniquement.

## Suivi — légende et tableau de bord

Ce document est la **seule source de statut** des phases du Kernel, de la phase A de la Plateforme (côté Kernel), de la phase P0 proposée et des points ouverts du Kernel. Le statut des phases B à H de la Plateforme est dans le dépôt `Civitas-EGEN-Business`, `docs/plateforme/11-plan-par-phase.md`.

Légende : `[x]` fait · `[~]` en cours · `[ ]` à faire · `[>]` reporté, avec un **déclencheur écrit** (on ne le fait que si le déclencheur se produit) · `[?]` proposé, à valider.

Mise à jour : 30 octobre 2026.

| Élément | Statut | Reste à faire |
|---|---|---|
| Phases 0 à 5 du Kernel | **Fait** | Compléments liés à la Plateforme, listés dans chaque phase : Phase 1 (A-X1 reporté), Phase 2 (décision D20 actée), Phase 4 (A3 à faire, A-X2 reporté), Phase 5 (A5 à faire) |
| Phase 6 (continue) | **À faire** | 4 éléments (voir la phase) |
| Chantier Système d'agents | **Partiel** | 1 élément fait, 4 à faire (voir le chantier) |
| Phase A de la Plateforme (côté Kernel) | **En cours** | A2 à A5 à faire |
| Phase P0 « production du Kernel » | **Proposée** | À valider par Samuel |
| Points ouverts du Kernel | **Ouverts** | 2 points, à décider par Samuel |
| Nettoyage du code non conforme (N1) | **À faire** | Voir « Référence unique et nettoyage du code » ci-dessous |

## Phase 0 — Fondations et décisions structurantes

**Objectif** : poser les décisions et les garde-fous avant d'écrire la
première ligne de logique métier du Kernel.

- [x] Décisions actées et documentées : langage définitif du Kernel (voir
      [16](16-packages-et-stack-technique.md)), NATS vs Kafka en V1 (voir
      [07](07-ports-et-adapters.md#kafka-vs-nats-jetstream--quand-utiliser-lequel)),
      Vault pour les secrets.
- [x] Squelette multi-module qui compile : `egen-domain`,
      `egen-application`, `egen-adapters/*`, `egen-api`,
      `egen-contracts`, `egen-bootstrap`, `egen-test-kit`.
- [x] Mise en place immédiate des règles de dépendance de build (voir
      [16.2](16-packages-et-stack-technique.md#règle-de-build-à-faire-respecter-par-loutillage-pas-seulement-la-revue-de-code) /
      [17, niveau 4](17-strategie-de-tests.md#niveau-4--tests-darchitecture-fitness-functions))
      — **avant** d'écrire le premier port, pour qu'aucune violation ne
      s'accumule dès le départ.
- **Livrable** : squelette multi-module vide qui compile, avec les règles
  ArchUnit déjà actives (rouges tant qu'aucun code n'existe, c'est normal
  — elles doivent juste être prêtes).

## Phase 1 — Cœur hexagonal minimal + premier adapter (Deployment/Nomad)

**Objectif** : prouver le cycle Declare → Resolve → Compose → Delegate →
Observe → Reconcile de bout en bout sur **un seul** port.

- [x] `egen-domain` : `ServiceManifest` (version minimale : id, version,
      runtime, deployment), `DesiredState`, `ObservedState`,
      `LifecycleStateMachine` réduite aux phases `DECLARED → REGISTERED →
      DEPLOYING → RUNNING → FAILED/STOPPED`.
- [x] `egen-application` : `DeployServiceUseCase`, `ReconciliationEngine`
      v0 (work queue simple, un seul worker, resync périodique basique —
      pas encore de concurrence bornée avancée).
- [x] `DeploymentPort` + `NomadDeploymentAdapter` complet selon le mapping
      de [07](07-ports-et-adapters.md#mapping-vers-lapi-http-nomad-v1-port-par-défaut-4646)
      (create/update/scale/stop/remove/getStatus/rollback).
- [x] `egen-api` minimal : `POST /api/v1/services`, `GET
      /api/v1/services/{id}/status`.
- [x] Tests niveaux 1, 2, 3 (Testcontainers Nomad), et un premier test de
      niveau 5 : déployer un service fixture réel, observer sa
      convergence jusqu'à `RUNNING`.
- **Livrable démontrable** : `curl -X POST /api/v1/services -d
  @manifest.yaml` déploie réellement un conteneur via Nomad et son statut
  converge vers `RUNNING` dans l'API.

**Compléments liés à la Plateforme** (voir [22](22-frontiere-avec-la-plateforme.md#229--limites-actuelles-du-kernel)) :

- [>] **A-X1 — Différencier `runtime.type`** (`external`, `vm`, `binary`) : aujourd'hui conservé mais non différencié par le Deployment Adapter. *Déclencheur* : besoin avéré que le Kernel **gère** (découverte, santé) une application hébergée hors de lui ; sinon les instances `EXTERNAL` restent portées par le catalogue (D15, [22](22-frontiere-avec-la-plateforme.md#228--décisions-qui-touchent-le-kernel)).

## Phase 2 — Discovery (Consul) + Registry EGEN + cycle de vie complet

- [x] `DiscoveryPort` + `ConsulDiscoveryAdapter` (voir
      [07](07-ports-et-adapters.md#discovery-port)).
- [x] `RegistryStorePort` + adapter PostgreSQL (voir
      [08](08-registry.md)), avec verrouillage optimiste sur
      `generation`.
- [x] `LifecycleStateMachine` complète (toutes les phases de
      [09](09-cycle-de-vie.md)), avec `Conditions`.
- [x] Ordre d'arrêt gracieux implémenté et testé explicitement (voir
      [09](09-cycle-de-vie.md#ordre-darrêt-gracieux--un-piège-classique)).
- [x] `GET /api/v1/discover/{serviceName}` exposé.
- **Livrable démontrable** : deux services fixtures se découvrent
  mutuellement via l'API EGEN (sans IP codée en dur), et un arrêt gracieux
  ne produit aucune erreur côté appelant pendant la fenêtre de
  propagation.

**Complément lié à la Plateforme** :

- [x] **Décision D20 : pas de filtre de liste par label** (`GET /api/v1/services`). Le filtrage se fait côté client sur la liste déjà restreinte à l'équipe ([08](08-registry.md)). Aucune évolution.

## Phase 3 — Dépendances, ordonnancement, Messaging (NATS)

- [x] `DependencyGraph` complet : détection de cycle, tri topologique,
      dégradation gracieuse pour dépendances optionnelles (voir
      [10](10-gestion-des-dependances.md)).
- [x] `MessagingPort` + `NatsMessagingAdapter` (voir
      [07](07-ports-et-adapters.md#mapping-vers-lapi--nats-jetstream)),
      enveloppe CloudEvents.
- [x] Ordonnancement du déploiement initial multi-services respectant le
      graphe de dépendances.
- **Livrable démontrable** : trois services fixtures liés par dépendances
  `required` se déploient dans le bon ordre ; l'un publie un événement
  CloudEvents que l'autre consomme via `MessagingPort`.

## Phase 4 — Configuration, Secrets, Observabilité de production

- [x] `ConfigurationPort` (voir [07](07-ports-et-adapters.md#configuration-port)),
      `SecretsPort` + `VaultAdapter` (voir
      [07](07-ports-et-adapters.md#secrets-port)).
- [x] `ObservabilityPort` OpenTelemetry complet : métriques de
      [15.1](15-observabilite.md#les-trois-signaux-standardisés-opentelemetry)
      au complet (dont `egen_dependency_unresolved_total` et les métriques
      génériques `egen_adapter_call_duration_seconds`/`_errors_total`,
      ajoutées via des décorateurs `Observed*Port` — voir
      `egen-application/observability/`), logs structurés enrichis de
      serviceId/generation/operationId/traceId, attributs de ressource
      OTel complets (`service.name/version/namespace`,
      `deployment.environment`). La trace HTTP entrante est liée au
      premier cycle de réconciliation qui en résulte, jusqu'à son premier
      appel réseau réel (voir `PendingDeclareTraces` et le livrable
      ci-dessous — complété après coup, voir l'historique Git).
- [x] Sécurité de base de l'API de contrôle : authentification (jeton
      MicroProfile JWT — voir [14](14-securite.md)) + RBAC scoped par
      équipe (`metadata.team` du manifeste, comparé au claim du jeton —
      voir `egen-api/security/EgenSecurityContext.java`), politique HTTP
      par défaut refusant tout sauf `/api/v1/health`. `Idempotency-Key`
      obligatoire sur les mutations, également livré à cette étape (voir
      [13](13-api-et-contrats.md)) bien que documenté hors de la liste
      initiale de cette phase — implémentation V1 en mémoire de processus,
      à remplacer par un stockage partagé le jour où plusieurs instances
      du Kernel tournent derrière un équilibreur de charge.
- **Livrable démontrable** : une trace unique dans Jaeger/Tempo montre le
  chemin complet d'un déploiement, de l'appel API jusqu'au premier appel
  applicatif du service déployé ; un tableau de bord Grafana répond en un
  coup d'œil aux questions de [15.2](15-observabilite.md#questions-auxquelles-lobservabilite-egen-doit-repondre-en-un-coup-doeil).
  **Livré en entier.** Le span `http.declare`/`http.stop` et les cycles de
  réconciliation qu'il déclenche (`reconcile`, puis ses enfants
  `deployment.create`/`discovery.deregister`) apparaissent désormais comme
  un seul arbre de trace continu dans Jaeger, grâce a
  `PendingDeclareTraces` (`egen-application.reconciliation`) : un petit
  registre en mémoire, jamais persisté ni transitant par la `WorkQueue`
  (qui reste strictement des clés, voir
  [04](04-moteur-de-reconciliation.md)), qui relie le contexte de trace
  HTTP au premier cycle de réconciliation concerné, le temps que ce cycle
  atteigne son premier appel réseau réel — au-delà, les cycles de
  convergence et de resync redeviennent des racines indépendantes, exactement
  la portée que demande [15](15-observabilite.md) ("jusqu'au premier appel
  réseau", jamais au-delà). Resync périodique et convergence continuent de
  produire des racines de span indépendantes, conformément à
  [15](15-observabilite.md).

**Compléments liés à la Plateforme** :

- [ ] **A3 — API de publication de configuration** `PUT /api/v1/services/{id}/configuration` (expose `ConfigurationPort.publish`, aujourd'hui **sans API**) : contrat normatif en [13](13-api-et-contrats.md#publication-de-configuration--put-apiv1servicesidconfiguration). Nécessaire à la configuration des instances d'applications par la Plateforme.
- [>] **A-X2 — Namespaces Nomad par tenant** : l'adapter ne gère pas les namespaces ([07](07-ports-et-adapters.md)). *Déclencheur* : une organisation exige une isolation d'**infrastructure** (et non de données) ; sinon isolation par données ou par instance dédiée.
- [x] Limite documentée : le RBAC (équipe + administrateur) reste grossier **par choix** ; l'autorisation métier n'est pas dans le Kernel ([14](14-securite.md), D14).

## Chantier parallèle — Système d'agents & Edge Gateway (hors de ce dépôt)

**Ajouté après coup (voir [21](21-systeme-agents-et-gateway.md)) : ce
chantier ne figurait pas dans les phases initiales ci-dessus.** Il est
inséré ici, entre les Phases 4 et 5, parce que c'est exactement le moment
où il devient possible de commencer — pas avant (il a besoin d'une API de
contrôle authentifiée et RBAC, livrée en Phase 4), pas forcément après (il
n'a pas besoin, sauf sur un point précis signalé plus bas, d'attendre la
Phase 5).

**Ceci n'est pas une phase du Kernel.** Elle ne produit aucun livrable
dans `Civitas-EGEN-Kernel` (à une seule exception explicite ci-dessous) —
le code correspondant vit dans deux dépôts séparés,
`Civitas-EGEN-Edge-Gateway` et `Civitas-EGEN-Agents`, chacun avec son
propre cycle de release (voir [21.0](21-systeme-agents-et-gateway.md#210--où-vit-ce-système-et-où-il-ne-vit-pas)).
Le Kernel reste ce qu'il a toujours été : un control plane qui ne connaît
ni utilisateurs, ni agents, ni le sens des capacités qu'un service expose
(garde-fou n°1, [02](02-principes-fondamentaux.md)).

Le détail complet (services, responsabilités, sécurité, ordre de
construction) est entièrement dans [21](21-systeme-agents-et-gateway.md) —
cette section ne le duplique pas, elle situe seulement ce chantier par
rapport aux phases du Kernel ci-dessus et ci-dessous.

- [x] **Seul point qui touche réellement ce dépôt** : le bloc optionnel
      `interfaces` dans `ServiceManifest`, additif et rétro-compatible,
      décrit en [21.3](21-systeme-agents-et-gateway.md#213--le-service-manifest-sétend-jamais-ne-se-casse)
      (`interfaces: [{type: mcp|openapi, ref, riskDefault}]`). Sans lui,
      aucun service ne peut annoncer ses capacités au Capability Gateway.
      Prérequis explicite à l'étape 1 de
      [21.10](21-systeme-agents-et-gateway.md#2110--ordre-de-construction-recommandé) —
      rien de plus, jamais de logique d'interprétation de ce bloc côté
      Kernel (il reste une chaîne opaque, comme `deployment.image`).
      *Livré : bloc `interfaces` du manifeste, relu par
      `GET /api/v1/services/{id}` ([06](06-service-manifest.md),
      [13](13-api-et-contrats.md)) et vérifié par le test de niveau 5.*
- [ ] Étapes 1 à 6 de [21.10](21-systeme-agents-et-gateway.md#2110--ordre-de-construction-recommandé)
      (service fixture → Capability Gateway → IAM/Policy Engine → Agent
      Gateway/Runtime à un seul agent → HITL/audit/quotas → supervisor et
      swarm) : peuvent démarrer dès aujourd'hui, en parallèle de la Phase
      5 ci-dessous — aucune des deux ne bloque l'autre.
- [ ] Étape 7 de [21.10](21-systeme-agents-et-gateway.md#2110--ordre-de-construction-recommandé)
      (brancher le Workflow Engine du Kernel comme outil de l'agent) : la
      **seule** dépendance stricte envers ce dépôt — ne peut pas commencer
      avant que `WorkflowEnginePort` (Phase 5 ci-dessous) soit livré.
- [ ] Étape 8 (services métier réels, un par un, chacun avec son bloc
      `interfaces`) : en continu, au rythme de `Civitas-EGEN-Business`.
- [ ] Edge Gateway (Kong ou équivalent, voir
      [21.1](21-systeme-agents-et-gateway.md#211--edge-gateway-kong--la-porte-dentrée-unique)) :
      projet de déploiement à part entière, jamais un service EGEN déclaré
      *auprès* du Kernel (il se trouve topologiquement devant lui). Sa
      mise en place peut commencer indépendamment de tout le reste de ce
      chantier, dès que la pipeline de déploiement générale
      ([21.8](21-systeme-agents-et-gateway.md#218--pipeline-de-déploiement-générale))
      est prête à l'accueillir.
- **Livrable démontrable** : le test d'acceptation de
  [21.9](21-systeme-agents-et-gateway.md#219--test-dacceptation-de-bout-en-bout-du-système-dagents)
  passe en entier — deux utilisateurs de deux équipes différentes
  obtiennent deux catalogues d'outils différents, une action `risk: write`
  passe par une confirmation HITL et produit un enregistrement d'audit, et
  une tentative de contournement du catalogue filtré est refusée au niveau
  du Capability Gateway, pas seulement masquée côté Agent Runtime.

## Phase 5 — Moteur de Workflow et communication fabric avancée

- [x] `WorkflowEnginePort` natif (voir [11](11-moteur-de-workflow.md)),
      avec compensation Saga orchestrée, durabilité de l'exécution.
      *Livré : modèle de domaine, ports (`ServiceInvocationPort`,
      `WorkflowStorePort`), `WorkflowEngine`, use cases, API REST
      `/api/v1/workflows` ; validé par le build Maven complet de la CI
      (tests unitaires, tests d'adapters PostgreSQL, API Quarkus, ArchUnit)
      et par `PublishArticleWorkflowScenarioIT` (voir ci-dessous).*
- [x] Communication Fabric : SDK léger pour au moins deux langages (Java et
      Python) et un mode sidecar de référence (voir
      [12](12-communication-fabric.md)).
      *Livré : `egen-sdk-java`, `sdk/python`, `egen-sidecar`.
      `POST /api/v1/events/publish` est livré côté Kernel
      ([13](13-api-et-contrats.md)), ainsi que `publish` dans les SDK Java et
      Python, après sa sémantique de résilience normative
      ([12](12-communication-fabric.md)).*
- [x] Résilience configurable (retry/timeout/circuit breaking) appliquée
      uniformément.
      *Livré : bloc `resiliency` du manifeste ([06](06-service-manifest.md)),
      sémantique normative et contrat exécutable partagé par le Kernel, le
      SDK Java et le SDK Python ([12](12-communication-fabric.md),
      [17](17-strategie-de-tests.md)), appliquée par le Moteur de Workflow
      et servie par `discover`.*
- [x] **Livrable démontrable** : le scénario complet de
  [20 — Scénario de bout en bout](20-scenario-bout-en-bout.md) s'exécute
  de bout en bout, y compris un cas de compensation déclenché
  volontairement.
      *Livré : le test de niveau 5 (`EgenAcceptanceE2ETest`, module
      `egen-bootstrap`, voir [17](17-strategie-de-tests.md#le-test-de-niveau-5-du-dépôt))
      rejoue la partie 1 et la partie 2 contre de vrais Nomad, Consul, NATS,
      PostgreSQL et Vault, et il est vert en CI. Il a révélé, en plus de
      l'absence d'index Jandex et du défaut d'adresse annoncée par Nomad, que
      le Kernel ne ré-observait un service convergé qu'au resync de 5 minutes :
      la perte d'une allocation n'était vue qu'au bout de plusieurs minutes.
      Corrigé par le régime permanent de ~10 s ([04](04-moteur-de-reconciliation.md)).*

**Complément lié à la Plateforme** :

- [ ] **A5 — Scénario de niveau 5 « service fixture de plateforme »** : déclaré par l'API publique avec `team: platform-operator`, labels réservés et configuration publiée via A3 ; convergence vers `RUNNING`, labels restitués ([22](22-frontiere-avec-la-plateforme.md#227--la-règle-darchitecture-a4)).

---

## Référence unique et nettoyage du code

**Règle.** La documentation d'architecture publiée sur `main` est la **seule référence** de tout le projet, sans exception : pour le Kernel, `docs/architecture/` de ce dépôt ; pour la Plateforme, `docs/plateforme/` du dépôt `Civitas-EGEN-Business`. Tout élément de code, de configuration, de test ou de document qui ne concorde pas avec elle est **abandonné** : il est supprimé ou aligné, jamais conservé « au cas où ». En cas de conflit, la documentation l'emporte.

- [ ] **N1 — Nettoyage du code non conforme à la documentation.** Marche à suivre : (1) audit complet du code contre la documentation ; (2) suppression ou alignement par petits commits nommés ; (3) construction et tests verts ; (4) cocher cet élément. Ne touche jamais une évolution déjà décrite comme « à faire » (A2 à A5, phase 6, P0) : ce sont des ajouts, pas du nettoyage.

**Audit préliminaire du 30 octobre 2026** (partiel : vocabulaire et structure des modules seulement) :

| Constat | Vérification | Suite |
|---|---|---|
| Aucun vocabulaire de l'ancienne architecture dans le code (PF4J, `kernel-sdk`, `ManifesteExtension`, `kernel-service-runtime`, EventBus) | Recherche dans les sources Java, XML, YAML, propriétés et Python hors `docs/` : aucune occurrence | Rien à nettoyer |
| Les dossiers racine correspondent au document 16 ; `egen-adapter-kafka` est documenté comme optionnel et futur, donc son absence est conforme | Comparaison des dossiers du dépôt avec le document 16 | Rien à nettoyer |
| Le document 16 cite `egen-contracts/openapi/egen-control-api.yaml`, absent du code (seul le schéma `service-manifest.schema.json` existe) | `find egen-contracts` | À aligner dans N1 : fournir le fichier OpenAPI ou retirer la mention |

L'audit complet (comportements, tests, configuration, scripts) reste à faire dans N1. Le nettoyage du code du dépôt `Civitas-EGEN-Business` suit la même règle ; il attend la décision du point ouvert 4 pour le module `organization` déjà présent (voir `docs/plateforme/11-plan-par-phase.md`).

## Phase A de la Plateforme — côté Kernel

La Plateforme (voir [22](22-frontiere-avec-la-plateforme.md)) se construit au-dessus du Kernel. Seule sa **phase A** touche ce dépôt ; ses phases B à H sont suivies dans le dépôt `Civitas-EGEN-Business`, `docs/plateforme/11-plan-par-phase.md`.

- [x] **A1 — Documentation de la frontière** : document [22](22-frontiere-avec-la-plateforme.md) et corrections des documents 01 à 21.
- [ ] **A2 — Convention de labels réservés** `platform.civitas.africa/*` ([06](06-service-manifest.md#labels-réservés-posés-par-la-plateforme)) : documentée ; reste un test d'aller-retour de l'API (aucun changement de code attendu).
- [ ] **A3 — API de publication de configuration** : voir la Phase 4 ci-dessus (même élément, suivi à cet endroit).
- [ ] **A4 — Règle d'architecture « Kernel ignorant de la Plateforme »** en CI ([22](22-frontiere-avec-la-plateforme.md#227--la-règle-darchitecture-a4)).
- [ ] **A5 — Scénario de niveau 5** : voir la Phase 5 ci-dessus (même élément, suivi à cet endroit).
- [>] **A-X1** (Phase 1) et **A-X2** (Phase 4) : reportés, avec déclencheur.
- **Critère de sortie** : intégration continue verte ; `egen-domain` sans aucune modification liée à la Plateforme ; A3 testé aux niveaux 1, 2, 3, 4 et 5 ([17](17-strategie-de-tests.md)).

## Phase P0 proposée — Production du Kernel

*Proposition à valider par Samuel.* Chaque constat a été vérifié dans le code du dépôt le 29 octobre 2026.

| Constat | Vérification |
|---|---|
| Aucun `Dockerfile`, donc aucune image publiable | Aucun fichier `Dockerfile*` dans le dépôt |
| Aucun job Nomad du Kernel | Le dossier `deploy/` ne contient que `nomad-dev.sh`, `otel-collector-config.yaml` et `prometheus.yml` |
| Vault en mode développement | `docker-compose.yml` lance Vault avec `VAULT_DEV_ROOT_TOKEN_ID` |
| Un seul exemplaire du Kernel possible | La clé d'idempotence (`IdempotencyKeyFilter`) et les liens de traces (`PendingDeclareTraces`) sont gardés dans une `ConcurrentHashMap` du processus |
| mTLS désactivé par défaut | Les deux lignes de configuration sont commentées dans `application.properties` |
| Sidecar sans mTLS ni publication d'événements | Aucune occurrence de « publish », « mtls » ou « tls » dans les sources du sidecar |

Livrables proposés : un `Dockerfile` et une image publiée ; un job Nomad du Kernel ; Vault hors mode développement ; idempotence et liens de traces partagés entre exemplaires ; mTLS activé de bout en bout ; sidecar complété. Statut : `[?]` proposée.

## Points ouverts du Kernel

Aucun n'est tranché dans cette documentation. Décideur : Samuel.

**1. Amorçage circulaire.**
- *Problème* : sans émetteur de jetons OIDC configuré (`EGEN_OIDC_ISSUER_URL`, `EGEN_OIDC_JWKS_URL`), le Kernel refuse toute opération qui modifie quelque chose ; or la Plateforme prévoit que le Kernel déploie Keycloak, l'émetteur de jetons.
- *Options* : (a) lancer Keycloak par la pipeline de déploiement **avant** le Kernel ; (b) fournir au Kernel une clé publique statique temporaire pour émettre le premier jeton `egen-admin`.
- *Impact* : détermine l'ordre de démarrage de toute installation neuve.
- Ce point est aussi consigné dans la phase C de la Plateforme (`docs/plateforme/11-plan-par-phase.md`).

**2. Validation de la phase P0.** Voir ci-dessus : faut-il la lancer, et avant ou après la phase A ?

## Phase 6 (continue) — Extensibilité prouvée et durcissement

- [ ] Ajouter un **second** adapter de déploiement (ex.
      `DockerDeploymentAdapter` pour les environnements de développement
      local, ou `KubernetesDeploymentAdapter`) **sans modifier
      `egen-domain` ni `egen-application`** — c'est le test ultime de la
      promesse architecturale.
- [ ] Chaos engineering (voir [17, niveau 6](17-strategie-de-tests.md#niveau-6--chaos-engineering-à-introduire-progressivement)).
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
- [ ] Un service a été ajouté sans qu'aucun commit ne
      touche `egen-domain`, `egen-application`, ni `egen-adapters/*`.
- [ ] **Plateforme** : `egen-domain` n'a reçu aucune notion de la Plateforme (règle A4 verte).

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
      [15.3](15-observabilite.md#slo-du-kernel-lui-même) respecté).

**Qualité**

- [ ] La suite de tests de niveau 1 (domaine) s'exécute sans aucun
      conteneur ni accès réseau.
- [ ] Les règles ArchUnit sont actives en CI et bloquent effectivement une
      violation volontaire de test.
- [ ] La fréquence de modification de `egen-domain` diminue dans le temps
      (métrique suivie via l'historique Git — un cœur qui bouge encore
      beaucoup après la Phase 3 est un signal d'alerte architecturale, pas
      de vélocité).
