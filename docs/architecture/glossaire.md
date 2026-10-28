# Glossaire

| Terme | Définition dans le contexte EGEN |
|---|---|
| **Control Plane** | Composant qui décide *quoi* doit se passer, sans exécuter directement l'action (par opposition au *data plane* qui exécute). |
| **Composition Plane** | Extension du concept de control plane : EGEN ne gère pas une seule ressource technique mais compose plusieurs moteurs pour former un écosystème cohérent. |
| **Port (primaire / secondaire)** | Interface définie par le domaine ; primaire = pilote le domaine (inbound), secondaire = piloté par le domaine (outbound). Voir [02](02-principes-fondamentaux.md). |
| **Adapter** | Implémentation concrète d'un port, liée à une technologie précise (Nomad, Consul, NATS…). |
| **Desired State / Observed State** | État voulu (déclaré par un client) vs état réel (mesuré par observation des moteurs spécialisés). Voir [04.4](04-moteur-de-reconciliation.md#état-désiré-vs-état-observé--modèle-explicite). |
| **Reconciliation (réconciliation)** | Processus continu de rapprochement entre état désiré et état observé. Voir [04](04-moteur-de-reconciliation.md). |
| **Level-triggered** | Modèle de contrôle où une notification ne déclenche qu'une relecture complète de l'état, jamais un traitement basé sur le contenu de la notification elle-même. Voir [04.1](04-moteur-de-reconciliation.md#level-triggered-pas-edge-triggered). |
| **Idempotence** | Propriété d'une opération : l'exécuter plusieurs fois produit le même résultat que l'exécuter une fois. Condition nécessaire à une boucle de réconciliation sûre. |
| **CloudEvents** | Spécification CNCF graduée pour l'enveloppe standard des événements. Voir [07](07-ports-et-adapters.md#lenveloppe-dévénement--cloudevents-cncf-pas-un-format-maison). |
| **Saga (orchestrée / choreographiée)** | Pattern de transaction distribuée par étapes locales + compensations, sans verrou global. Voir [11.1](11-moteur-de-workflow.md#pourquoi-un-moteur-natif-et-comment-le-dimensionner). |
| **SemVer** | Semantic Versioning — schéma `MAJOR.MINOR.PATCH` utilisé pour `ServiceVersion`. |
| **Condition** | Élément de statut fin-grained (`type`, `status`, `reason`, `message`), inspiré du modèle Kubernetes, complémentaire à la `Phase` macro. Voir [09](09-cycle-de-vie.md#conditions). |
| **DependencyGraph** | Graphe orienté des dépendances techniques déclarées entre services, utilisé pour l'ordonnancement du déploiement. Voir [10](10-gestion-des-dependances.md). |
| **Manifest (ServiceManifest)** | Le contrat technique déclaratif d'un service, remis à EGEN. Voir [06](06-service-manifest.md). |
| **Work Queue** | File contenant des *clés* de ressources à réconcilier, avec déduplication et retry. Voir [04.2](04-moteur-de-reconciliation.md#composants-de-la-boucle). |
| **Sidecar** | Processus déployé aux côtés d'un service pour lui offrir des capacités transverses sans dépendance de code (inspiré de Dapr/Envoy). Voir [12.2](12-communication-fabric.md#deux-modes-dintégration-au-choix-du-service). |
| **Écosystème multi-solutions** | Ensemble de services construits **au-dessus** du Kernel permettant à plusieurs applications d'un même domaine de coexister et à chaque service d'une organisation de choisir ses outils. Voir [22](22-ecosysteme-multi-solutions-et-frontiere.md). |
| **Projet Métier** | Projet (`Civitas-EGEN-Business`) qui héberge les services de l'écosystème et les applications métier ; consomme l'API du Kernel, n'est jamais importé par lui. |
| **Cellule** | Unique concept structurel générique d'une organisation (organisation, faculté, service, projet…) ; son « type » est une donnée. Voir [23](23-cellules.md). |
| **Cellule racine** | Cellule sans parent : une organisation, frontière de tenant. |
| **Type de cellule** | Donnée définissant un genre de cellule (parents admis, schéma d'attributs, portée plateforme ou racine). |
| **Domaine (fonctionnel)** | Besoin fonctionnel défini par un contrat (agenda, vente…). Voir [24](24-catalogue-instances-affectations.md). |
| **Implémentation / Version d'implémentation** | Logiciel capable de remplir un domaine / sa version précise, avec capacités, prérequis et conformité. |
| **Instance (applicative)** | Déploiement concret d'une version d'implémentation ; `MANAGED` (déployée par le Kernel) ou `EXTERNAL`. |
| **Activation** | Autorisation d'utiliser une instance dans une organisation (cellule racine). |
| **Affectation** | Outil de travail d'une cellule pour un domaine et un usage ; héritage descendant, coupure possible. |
| **Résolution (affectation effective)** | Calcul à la demande de l'instance applicable à `(cellule, domaine, usage)`. |
| **Audience** | Qui peut utiliser une affectation : `SCOPE_MEMBERS` ou `EXPLICIT`. |
| **Contrat de domaine** | API canonique et liste de capacités (obligatoires/optionnelles) d'un domaine. Voir [26](26-contrats-de-domaine-et-connecteurs.md). |
| **Domain Gateway / Connecteur** | Service qui expose le contrat d'un domaine / module qui traduit le contrat vers un logiciel réel. |
| **Conformité (`FULL`/`PARTIAL`)** | Niveau de respect du contrat par une version d'implémentation, calculé par la suite de tests. |
| **Règle d'échange** | Déclaration explicite d'un transfert de données entre deux instances ; aucune synchronisation n'est implicite. |
| **`egen_uid`** | Identifiant permanent et immuable d'un utilisateur EGEN, claim des jetons. Voir [25](25-identite-et-autorisations.md). |
| **Compte local lié** | Compte technique propre à une application, rattaché à l'`egen_uid`. |
| **Contrôle d'usage (`check-use`)** | Unique implémentation de « cet utilisateur peut-il utiliser cet outil dans ce contexte ». |
| **Platform Operator** | `platform-operator-service`, seul client du Kernel pour l'écosystème. |
| **Labels réservés** | Clés `platform.civitas.africa/*` posées par la plateforme, stockées sans être interprétées par le Kernel. |
| **Niveaux de configuration** | PLATFORM, IMPLEMENTATION_VERSION, INSTANCE, CELL, ASSIGNMENT, USER, avec verrous. Voir [27](27-configuration-multi-niveaux.md). |
