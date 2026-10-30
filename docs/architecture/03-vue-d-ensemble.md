# 03 — Vue d'ensemble de l'architecture

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                                    CLIENTS                                        │
│        CLI  │  Console Web  │  CI/CD  │  Autres systèmes (via API/gRPC)           │
└───────────────────────────────────┬────────────────────────────────────────────--┘
                                     │  API de contrôle EGEN (REST + gRPC)
┌────────────────────────────────────▼───────────────────────────────────────────---┐
│                              EGEN CORE (hexagone)                                  │
│                                                                                     │
│   ┌────────────────────────────────────────────────────────────────────────────┐  │
│   │  Application Layer (Use Cases / Ports Primaires)                           │  │
│   │  DeployService · RegisterService · ResolveDiscovery · RunWorkflow · ...    │  │
│   └───────────────────────────────┬────────────────────────────────────────────┘  │
│                                    │                                               │
│   ┌────────────────────────────────▼──────────────────────────────────────────┐   │
│   │  Domain Layer (entités, VOs, règles)                                      │   │
│   │  ServiceManifest · DesiredState · ObservedState · DependencyGraph ·       │   │
│   │  LifecycleStateMachine · WorkflowDefinition · ReconciliationEngine        │   │
│   └───────────────────────────────┬────────────────────────────────────────────┘  │
│                                    │  Ports Secondaires (interfaces)                │
│   ┌────────────────────────────────▼──────────────────────────────────────────┐   │
│   │  DeploymentPort │ DiscoveryPort │ MessagingPort │ ConfigPort │             │   │
│   │  SecretsPort    │ ObservabilityPort │ RegistryStorePort │ WorkflowStorePort│   │
│   └──┬───────────────┬───────────────┬──────────────┬─────────────┬───────────┘   │
└──────┼───────────────┼───────────────┼──────────────┼─────────────┼───────────────┘
       │               │               │              │             │
┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐ ┌─────▼──────┐ ┌────▼─────────┐
│  Nomad      │ │  Consul     │ │  Kafka/NATS │ │  Vault /   │ │  OpenTelemetry│
│  Adapter    │ │  Adapter    │ │  Adapter    │ │  Config    │ │  Collector    │
└──────┬──────┘ └──────┬──────┘ └──────┬──────┘ └─────┬──────┘ └────┬──────────┘
       │               │               │              │             │
┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐ ┌─────▼──────┐ ┌────▼──────────┐
│    NOMAD    │ │   CONSUL    │ │ KAFKA/NATS  │ │   VAULT    │ │  PROMETHEUS/  │
│  (exécution)│ │ (discovery) │ │ (messaging) │ │ (secrets)  │ │  GRAFANA/     │
│             │ │             │ │             │ │            │ │  JAEGER       │
└──────┬──────┘ └─────────────┘ └─────────────┘ └────────────┘ └───────────────┘
       │
┌──────▼──────────────────────────────────────────────────────────────────────┐
│                     SERVICES AUTONOMES (polyglottes)                        │
│   news-service (Python)  │  billing-service (Go)  │  ged-service (Java)     │
│   notification-service (Rust) │ ... chacun avec son repo, son runtime,      │
│   sa base, son cycle de release                                            │
└───────────────────────────────────────────────────────────────────────────--┘
```

## Ce qui entoure le Kernel : trois étages, deux flux

Le Kernel est l'étage 1. Autour de lui, la Plateforme (étage 2) et les applications métier (étage 3) sont des services ordinaires qu'il lance et surveille ; la frontière est décrite dans [22](22-frontiere-avec-la-plateforme.md).

```mermaid
flowchart TB
    subgraph E3["Étage 3 — Applications métier"]
        APP["Radicale, Nextcloud Calendar, civitas-news…"]
    end
    subgraph E2["Étage 2 — Plateforme (services ordinaires)"]
        PLAT["registre de cellules · catalogue · opérateur · configuration · passerelle de domaine · connecteurs · portail"]
    end
    subgraph E1["Étage 1 — Kernel"]
        K["lance, surveille, relance, dit où sont les services, transporte les messages"]
    end
    APP --> PLAT
    PLAT -- "API publique" --> K
    K -. "ne connaît aucun concept de la Plateforme" .-> PLAT
```

**Deux moments séparés.** Une application est **installée une fois**, puis **appelée de nombreuses fois**.

*Flux B — installer une application (mise en service).* Exemple : installer Radicale pour le Service RH de l'Université X.

```mermaid
flowchart LR
    CAT["catalog-service (état désiré)"] --> OP["platform-operator-service"]
    OP -- "ServiceManifest" --> K["Kernel (déploie, surveille, relance)"]
    K --> APP["application"]
    OP -. "relit le statut et le reporte" .-> CAT
```

*Flux A — un appel d'utilisateur (moment d'usage).* Exemple : Marie, du Service RH, ouvre son agenda.

```mermaid
flowchart LR
    U["Marie / portail"] --> KONG["Kong (porte d'entrée)"] --> GW["domain-gateway"]
    GW --> CAT["catalog-service : outil + droit d'usage"]
    GW --> SDB["SpiceDB : droit sur la ressource"]
    GW --> K["Kernel : où est l'instance ?"]
    GW --> APP["application (direct ou via un connecteur)"]
```

Dans le flux A, le Kernel répond seulement à « où est l'instance ? » (découverte). Le détail des deux flux et des termes employés est dans le dépôt `Civitas-EGEN-Business`, `docs/plateforme/03-vue-d-ensemble-des-systemes.md`.

## Ce qui doit rester stable dans le temps

Le **cœur hexagonal** (Application + Domain layers) est la partie du
système qui doit changer le moins souvent. Tout le reste — adapters,
moteurs externes, services métier — doit pouvoir être remplacé sans
toucher au cœur. C'est le test décisif de la réussite architecturale : **la
fréquence de modification de `egen-domain` doit tendre vers zéro** une fois
le Kernel stabilisé (métrique à suivre, voir [19 — Feuille de route](19-feuille-de-route.md)).
