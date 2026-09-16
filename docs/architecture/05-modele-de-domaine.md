# 05 — Modèle de domaine (entités & value objects)

Ces types vivent **exclusivement** dans `egen-domain`, sans aucune
dépendance externe (pas de JPA, pas de client Nomad, pas d'annotation de
framework web).

```
ServiceManifest (aggregate root)
├── ServiceId            (VO — identifiant stable, ex. "news-service")
├── ServiceVersion        (VO — semver, ex. "2.4.0")
├── Runtime               (VO — { type: container|binary|external, artifact })
├── DeploymentSpec         (VO — image, ressources requises, contraintes, stratégie de mise à jour)
├── NetworkSpec           (VO — endpoints exposés, protocoles)
├── List<Dependency>      (VO — sur quels autres ServiceId ce service compte)
├── EventsSpec            (VO — événements publiés / consommés, avec leur schéma)
├── HealthSpec            (VO — endpoint(s) de health check, seuils)
├── ConfigSpec            (VO — clés de configuration attendues, sans leurs valeurs)
├── LifecyclePolicy       (VO — stratégie de redémarrage, ordre d'arrêt, etc.)
└── Metadata              (VO — labels, annotations, propriétaire, équipe)

DesiredState
├── ServiceManifest
├── Generation (long)
└── TargetEnvironment (VO)

ObservedState (reconstruit, non persisté comme vérité)
├── DeploymentObservation  (statut Nomad : allocations, santé des tâches)
├── DiscoveryObservation   (instances Consul, statut de santé)
├── MessagingObservation   (statut des bindings, lag éventuel)
└── ObservedGeneration (long)

ServiceStatus
├── Phase (enum — voir 09)
├── List<Condition>
└── ObservedGeneration

DependencyGraph
├── Nodes: Set<ServiceId>
├── Edges: Set<Dependency>   (avec détection de cycle obligatoire — un cycle est un manifeste invalide, rejeté à la validation)
└── topologicalOrder(): List<ServiceId>   (utilisé par le planificateur de déploiement, voir 10)

WorkflowDefinition (aggregate root — voir 11)
├── WorkflowId
├── List<Step>            (chaque Step référence un ServiceId + une opération + des conditions)
├── CompensationMap       (Step → CompensatingStep)
└── ExecutionPolicy       (timeout, retry, parallélisme autorisé)

WorkflowExecution
├── WorkflowId, ExecutionId
├── CurrentStepIndex
├── List<StepResult>
└── Status (RUNNING | COMPLETED | COMPENSATING | FAILED | COMPENSATED)

TechnicalEvent (VO immuable — enveloppe CloudEvents, voir 07.3)
├── id, source, type, time, specversion
├── correlationId, causationId
└── data (payload opaque pour EGEN — jamais interprété)
```

## Règle de conception

Chaque Value Object est immuable et se valide à la construction (invariants
métier appliqués dans le constructeur/factory, jamais dans une couche de
validation séparée qui pourrait être contournée). Exemple :
`ServiceVersion` refuse de se construire si la chaîne ne respecte pas
SemVer ; `DependencyGraph` refuse de se construire si un cycle est
détecté.
