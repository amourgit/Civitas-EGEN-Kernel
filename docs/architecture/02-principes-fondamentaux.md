# 02 — Principes architecturaux fondamentaux

## Hexagonal Architecture — deux familles de ports

Dans la littérature (Cockburn, puis la pratique moderne Java/Kotlin), on
distingue précisément **deux familles de ports**, et le Kernel EGEN
respecte cette distinction dans son code :

- **Ports primaires (driving / inbound ports)** : interfaces par
  lesquelles le monde extérieur *pilote* le domaine EGEN. Exemples :
  `DeployServiceUseCase`, `RegisterServiceUseCase`, `TriggerWorkflowUseCase`.
  Ils sont **implémentés** par le domaine (application layer) et
  **appelés** par les adapters primaires (contrôleur REST, gRPC, CLI,
  consommateur de message entrant).
- **Ports secondaires (driven / outbound ports)** : interfaces que le
  domaine **appelle** pour parler au monde extérieur. Exemples :
  `DeploymentPort`, `DiscoveryPort`, `MessagingPort`. Ils sont
  **définis** par le domaine et **implémentés** par les adapters
  secondaires (`NomadDeploymentAdapter`, `ConsulDiscoveryAdapter`…).

```
                     ADAPTERS PRIMAIRES                         ADAPTERS SECONDAIRES
                  (pilotent le domaine)                      (pilotés par le domaine)

  REST Controller ───┐                                    ┌──▶ NomadDeploymentAdapter
  gRPC Service ───────┤                                   ├──▶ ConsulDiscoveryAdapter
  CLI ─────────────────┤     PORTS PRIMAIRES   DOMAINE   PORTS SECONDAIRES
  Event Consumer ───────┼──▶ (interfaces) ──▶  EGEN CORE ──▶ (interfaces) ──┼──▶ KafkaMessagingAdapter
  Scheduler interne ───┘     (Use Cases)     (entités,      (Ports)         ├──▶ PostgresRegistryAdapter
                                              value objects,                └──▶ OtelObservabilityAdapter
                                              services de domaine,
                                              règles métier)
```

**Règle de dépendance stricte** : les flèches de dépendance (imports)
pointent **toujours vers le domaine**. Le domaine ne connaît **jamais** un
type Nomad, Consul, Kafka, ou un framework web. Cette règle est
**vérifiée automatiquement** (tests d'architecture ArchUnit, voir
[17](17-strategie-de-tests.md#niveau-4)), pas seulement documentée : un
rappel dans un README ne survit jamais à six mois de pression de delivery.

## Le cycle canonique : Declare → Resolve → Compose → Delegate → Observe → Reconcile

C'est **le** pattern central d'EGEN — littéralement le nom d'un module du
cœur (`core.reconciliation`), pas juste un concept dans un slide.

1. **Declare** — un client (humain ou système) déclare un **état désiré**
   via l'API de contrôle : « ce service doit exister en version 2.4.0,
   avec ces dépendances, ces endpoints, ces events ». Persisté tel quel,
   versionné, avant toute action.
2. **Resolve** — EGEN résout les références : quelle version des
   dépendances est disponible, quel adapter est responsable de quel
   aspect, quelles valeurs de configuration s'appliquent à cet
   environnement, quelles contraintes de placement s'appliquent.
3. **Compose** — EGEN assemble un **plan d'exécution concret** : la
   séquence d'opérations sur les adapters (créer le job Nomad, enregistrer
   le health check Consul, créer les souscriptions Kafka…), en respectant
   l'ordre imposé par le graphe de dépendances ([10](10-gestion-des-dependances.md)).
4. **Delegate** — EGEN exécute le plan en appelant les adapters. Chaque
   appel est idempotent et journalisé (corrélation par un `operationId`).
5. **Observe** — EGEN interroge en continu (ou écoute les événements de)
   chaque moteur spécialisé pour connaître l'**état observé réel** :
   statut de l'allocation Nomad, statut du health check Consul, lag de
   consommation Kafka.
6. **Reconcile** — EGEN compare l'état désiré à l'état observé et
   déclenche les actions correctrices nécessaires — **sans intervention
   humaine**, sauf politique explicite contraire (ex.
   `manualApprovalRequired: true` pour la prod).

Ce cycle **ne s'arrête jamais**. Détail complet de son implémentation :
[04 — Moteur de réconciliation](04-moteur-de-reconciliation.md).

## Les dix garde-fous non négociables

1. **Aucune logique métier dans le cœur.** Si une PR introduit un concept
   comme `invoiceLimit` ou `schoolYear` dans `egen-domain`, elle est
   rejetée en revue de code, point final.
2. **Aucun type d'un SDK externe (Nomad, Consul, Kafka client…) ne doit
   apparaître dans une signature de méthode du domaine.** Seuls les
   adapters importent ces SDK.
3. **Toute capacité candidate à l'implémentation interne doit d'abord
   répondre « non »** à la question : *« Nomad/Consul/Kafka/NATS le
   fait-il déjà ? »* — avant d'être codée.
4. **Une installation EGEN vide (zéro service métier) doit démarrer et
   répondre à son API de santé.** Test d'intégration exécuté en CI, pas
   une déclaration d'intention.
5. **Ajouter un service ne doit jamais nécessiter de modifier une ligne du
   Kernel.** Vérifié par un test qui déploie un service « fixture » via
   l'API publique uniquement.
6. **Ajouter un adapter (ex. Kubernetes) ne doit jamais nécessiter de
   modifier le domaine.** Seuls de nouveaux fichiers dans `egen-adapters/`
   doivent apparaître dans le diff.
7. **Chaque opération déléguée à un adapter doit être idempotente** —
   rejouable sans effet de bord destructeur (condition nécessaire pour une
   boucle de réconciliation saine, voir [04.3](04-moteur-de-reconciliation.md#idempotence)).
8. **Chaque service peut être dans n'importe quel langage.** Le contrat
   d'intégration (le manifeste + le protocole HTTP/gRPC/événementiel)
   prime toujours sur le langage.
9. **Le cœur est testable sans qu'aucun moteur externe ne tourne** (pas de
   Nomad/Consul/Kafka nécessaires pour lancer la suite de tests du
   domaine — voir [17](17-strategie-de-tests.md)).
10. **Toute divergence entre état désiré et état observé doit être
    visible** (API, métriques, logs structurés) — jamais silencieuse.
