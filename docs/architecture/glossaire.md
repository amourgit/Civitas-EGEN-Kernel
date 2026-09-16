# Glossaire

| Terme | Définition dans le contexte EGEN |
|---|---|
| **Control Plane** | Composant qui décide *quoi* doit se passer, sans exécuter directement l'action (par opposition au *data plane* qui exécute). |
| **Composition Plane** | Extension du concept de control plane : EGEN ne gère pas une seule ressource technique mais compose plusieurs moteurs pour former un écosystème cohérent. |
| **Port (primaire / secondaire)** | Interface définie par le domaine ; primaire = pilote le domaine (inbound), secondaire = piloté par le domaine (outbound). Voir [02](02-principes-fondamentaux.md). |
| **Adapter** | Implémentation concrète d'un port, liée à une technologie précise (Nomad, Consul, NATS…). |
| **Desired State / Observed State** | État voulu (déclaré par un client) vs état réel (mesuré par observation des moteurs spécialisés). Voir [04.4](04-moteur-de-reconciliation.md#etat-desire-vs-etat-observe--modele-explicite). |
| **Reconciliation (réconciliation)** | Processus continu de rapprochement entre état désiré et état observé. Voir [04](04-moteur-de-reconciliation.md). |
| **Level-triggered** | Modèle de contrôle où une notification ne déclenche qu'une relecture complète de l'état, jamais un traitement basé sur le contenu de la notification elle-même. Voir [04.1](04-moteur-de-reconciliation.md#level-triggered-pas-edge-triggered). |
| **Idempotence** | Propriété d'une opération : l'exécuter plusieurs fois produit le même résultat que l'exécuter une fois. Condition nécessaire à une boucle de réconciliation sûre. |
| **CloudEvents** | Spécification CNCF graduée pour l'enveloppe standard des événements. Voir [07](07-ports-et-adapters.md#lenveloppe-devenement--cloudevents-cncf-pas-un-format-maison). |
| **Saga (orchestrée / choreographiée)** | Pattern de transaction distribuée par étapes locales + compensations, sans verrou global. Voir [11.1](11-moteur-de-workflow.md#pourquoi-un-moteur-natif-et-comment-le-dimensionner). |
| **SemVer** | Semantic Versioning — schéma `MAJOR.MINOR.PATCH` utilisé pour `ServiceVersion`. |
| **Condition** | Élément de statut fin-grained (`type`, `status`, `reason`, `message`), inspiré du modèle Kubernetes, complémentaire à la `Phase` macro. Voir [09](09-cycle-de-vie.md#conditions). |
| **DependencyGraph** | Graphe orienté des dépendances techniques déclarées entre services, utilisé pour l'ordonnancement du déploiement. Voir [10](10-gestion-des-dependances.md). |
| **Manifest (ServiceManifest)** | Le contrat technique déclaratif d'un service, remis à EGEN. Voir [06](06-service-manifest.md). |
| **Work Queue** | File contenant des *clés* de ressources à réconcilier, avec déduplication et retry. Voir [04.2](04-moteur-de-reconciliation.md#composants-de-la-boucle). |
| **Sidecar** | Processus déployé aux côtés d'un service pour lui offrir des capacités transverses sans dépendance de code (inspiré de Dapr/Envoy). Voir [12.2](12-communication-fabric.md#deux-modes-dintegration-au-choix-du-service). |
