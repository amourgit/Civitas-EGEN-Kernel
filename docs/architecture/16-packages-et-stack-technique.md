# 16 — Architecture des packages & stack technique

## Structure des packages

Structure multi-module JVM (Maven), organisée strictement selon les
frontières hexagonales définies en [02](02-principes-fondamentaux.md) et
[07](07-ports-et-adapters.md) :

```
egen-kernel/                              (repo racine — build multi-module Maven)
│
├── egen-domain/                          # AUCUNE dépendance externe (pas de framework, pas de SDK Nomad/Consul/Kafka)
│   └── src/main/java/africa/civitas/egen/domain/
│       ├── model/                        # ServiceManifest, DesiredState, ObservedState, ServiceStatus...
│       ├── dependency/                   # DependencyGraph, cycle detection, topological sort
│       ├── lifecycle/                    # LifecycleStateMachine, Phase, Condition
│       ├── workflow/                     # WorkflowDefinition, WorkflowExecution, Step, Compensation
│       ├── event/                        # TechnicalEvent (enveloppe CloudEvents interne)
│       └── validation/                   # règles d'invariants du manifeste (SemVer, unicité d'endpoint...)
│
├── egen-application/                     # Use cases (ports primaires) + moteur de réconciliation
│   └── src/main/java/africa/civitas/egen/application/
│       ├── usecase/                      # DeployServiceUseCase, RegisterServiceUseCase, RunWorkflowUseCase...
│       ├── reconciliation/               # ReconciliationEngine, WorkQueue, Reconciler<T>, ResyncScheduler
│       └── port/                         # DÉFINITION des ports secondaires (interfaces uniquement)
│           ├── DeploymentPort.java
│           ├── DiscoveryPort.java
│           ├── MessagingPort.java
│           ├── ConfigurationPort.java
│           ├── SecretsPort.java
│           ├── ObservabilityPort.java
│           ├── RegistryStorePort.java
│           └── WorkflowStorePort.java
│
├── egen-adapters/                        # UN sous-module PAR intégration externe — dépendances isolées
│   ├── egen-adapter-nomad/               # dépend du SDK/API Nomad UNIQUEMENT ici
│   ├── egen-adapter-consul/              # dépend du SDK/API Consul UNIQUEMENT ici
│   ├── egen-adapter-nats/                # dépend du client NATS UNIQUEMENT ici
│   ├── egen-adapter-kafka/               # dépend du client Kafka UNIQUEMENT ici (activable en V2)
│   ├── egen-adapter-vault/               # secrets
│   ├── egen-adapter-postgres-registry/   # RegistryStorePort
│   └── egen-adapter-otel/                # ObservabilityPort
│
├── egen-api/                             # Adapters PRIMAIRES : contrôleurs REST + services gRPC
│   └── src/main/java/africa/civitas/egen/api/
│       ├── rest/                         # contrôleurs REST, DTOs de transport (JAMAIS les entités de domaine directement)
│       ├── grpc/                         # implémentations des services Protobuf
│       └── mapper/                       # DTO <-> Domain (aucune fuite de type de domaine dans les DTO ni l'inverse)
│
├── egen-contracts/                       # schémas partagés : JSON Schema du ServiceManifest, .proto, OpenAPI
│   ├── schema/service-manifest.schema.json
│   ├── proto/egen_control.proto
│   └── openapi/egen-control-api.yaml
│
├── egen-bootstrap/                       # point d'entrée exécutable : câblage (wiring) de TOUS les ports vers leurs adapters
│   └── src/main/java/africa/civitas/egen/bootstrap/
│       └── EgenKernelApplication.java    # seul module autorisé à connaître domaine ET adapters simultanément
│
└── egen-test-kit/                        # doubles de test partagés : InMemoryDeploymentPort, InMemoryDiscoveryPort...
    └── src/main/java/africa/civitas/egen/testkit/
```

## Règle de build à faire respecter par l'outillage, pas seulement la revue de code

Configurer les règles de dépendances **au niveau du build** (Maven
Enforcer, ou un plugin ArchUnit exécuté en CI — voir
[17](17-strategie-de-tests.md#niveau-4--tests-darchitecture-fitness-functions))
pour qu'il soit **techniquement impossible** que :

- `egen-domain` déclare une dépendance vers `egen-adapters/*`, `egen-api`,
  ou tout SDK externe (Nomad/Consul/Kafka/Spring/Quarkus).
- `egen-application` déclare une dépendance vers un module
  `egen-adapters/*` spécifique (il ne connaît que les interfaces qu'il
  définit lui-même).
- un module `egen-adapters/*` déclare une dépendance vers un **autre**
  module `egen-adapters/*` (chaque adapter est isolé — sinon on recrée du
  couplage caché entre Nomad et Consul par exemple).

## Stack technique retenue

| Composant | Choix | Justification |
|---|---|---|
| Langage du Kernel | Java 21+ (LTS), build Maven multi-module | Écosystème mature pour hexagonal architecture, SDK HTTP robustes pour Nomad/Consul, bon outillage de test (ArchUnit, Testcontainers). |
| Framework d'API | Un framework léger, sans imposer son modèle au domaine (ex. Quarkus, **uniquement dans `egen-api` et `egen-bootstrap`**, jamais dans `egen-domain`/`egen-application`) | Le choix du framework web est un détail d'adapter primaire, pas une décision architecturale centrale. |
| Communication inter-adapters/services | gRPC + Protobuf pour le machine-à-machine interne, REST/JSON pour l'API de contrôle publique | Cohérent avec le choix fait par Dapr et la majorité des control planes modernes. |
| Format d'échange de manifeste | YAML (lisibilité humaine) validé contre un JSON Schema strict | Cohérent avec Score, Kubernetes. |
| Persistance du Registry | PostgreSQL | Transactionnel, verrouillage optimiste natif via numéro de version, écosystème d'outillage mature. |
| Messaging par défaut | NATS JetStream (V1), Kafka en option via adapter additionnel (V2+) | Voir analyse complète [07](07-ports-et-adapters.md#kafka-vs-nats-jetstream--quand-utiliser-lequel). |
| Secrets | HashiCorp Vault | Cohérence d'écosystème avec Nomad/Consul (intégration native Nomad-Vault pour l'injection de secrets dans les allocations). |
| Observabilité | OpenTelemetry SDK + Collector, Prometheus (métriques), Grafana Tempo ou Jaeger (traces) | Standard ouvert, écosystème le plus large. |
| Tests d'architecture | ArchUnit (JVM) | Fait respecter mécaniquement les règles de dépendance ci-dessus. |
| CI/CD | Pipeline multi-étapes : tests domaine (rapides, sans I/O) → tests d'adapters (Testcontainers avec Nomad/Consul/NATS en conteneurs) → tests d'intégration bout-en-bout (environnement éphémère complet) | Voir détail [17](17-strategie-de-tests.md). |

Le point non négociable : quel que soit le langage du Kernel, il ne doit
**jamais** en imposer un aux services de l'écosystème.
