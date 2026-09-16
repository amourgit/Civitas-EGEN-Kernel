# Architecture EGEN Kernel — Documentation de référence

Cette documentation est **la** référence qui fait foi pour toute décision
d'architecture dans ce dépôt. Toute implémentation, tout module, tout
service qui s'appuie sur le Kernel doit s'y conformer.

## Comment lire cette documentation

Elle est organisée en quatre parties, dans l'ordre où il est recommandé de
les lire pour une première prise en main :

1. **Vision & Positionnement** ([01](01-vision-et-positionnement.md),
   [02](02-principes-fondamentaux.md)) — pourquoi EGEN existe, en quoi il
   diffère de Dapr/KubeVela/Crossplane/Score, et la règle d'or qui tranche
   tout débat de conception.
2. **Architecture détaillée** ([03](03-vue-d-ensemble.md) à
   [15](15-observabilite.md)) — le cœur technique : moteur de
   réconciliation, modèle de domaine, contrat de service, chaque
   port/adapter en détail, cycle de vie, dépendances, workflow,
   communication, API, sécurité, observabilité.
3. **Mise en œuvre** ([16](16-packages-et-stack-technique.md) à
   [18](18-anti-patterns.md)) — structure de packages, stack technique,
   stratégie de tests, anti-patterns.
4. **Exécution** ([19](19-feuille-de-route.md) à
   [20](20-scenario-bout-en-bout.md), [glossaire](glossaire.md),
   [références](references.md)) — feuille de route en phases livrables,
   scénario de bout en bout, glossaire, sources d'inspiration.

Chaque document technique majeur contient, quand c'est pertinent : le
**contrat** (interface/port), le **mapping** vers le moteur spécialisé
concerné, des **exemples concrets**, et les **pièges connus**.

## Table des matières

| # | Document | Contenu |
|---|---|---|
| 01 | [Vision et positionnement](01-vision-et-positionnement.md) | Pourquoi EGEN existe, positionnement face à Dapr/KubeVela/Crossplane/Score, ce qu'EGEN n'est pas |
| 02 | [Principes fondamentaux](02-principes-fondamentaux.md) | Hexagonal architecture, ports primaires/secondaires, cycle canonique, dix garde-fous |
| 03 | [Vue d'ensemble](03-vue-d-ensemble.md) | Schéma global du système, ce qui doit rester stable |
| 04 | [Moteur de réconciliation](04-moteur-de-reconciliation.md) | Boucle level-triggered, work queue, idempotence, état désiré vs observé |
| 05 | [Modèle de domaine](05-modele-de-domaine.md) | Entités et value objects du cœur |
| 06 | [Service Manifest](06-service-manifest.md) | Le contrat déclaratif entre un service et EGEN |
| 07 | [Ports & Adapters](07-ports-et-adapters.md) | Deployment, Discovery, Messaging, Configuration, Secrets, Observability |
| 08 | [Registry EGEN](08-registry.md) | Catalogue déclaratif, distinction avec Consul |
| 09 | [Cycle de vie](09-cycle-de-vie.md) | Machine à états complète, conditions, arrêt gracieux |
| 10 | [Gestion des dépendances](10-gestion-des-dependances.md) | Graphe, détection de cycle, ordonnancement |
| 11 | [Moteur de Workflow](11-moteur-de-workflow.md) | Saga orchestrée, compensation, durabilité |
| 12 | [Communication Fabric](12-communication-fabric.md) | SDK léger vs sidecar, résilience, propagation de contexte |
| 13 | [API & Contract Infrastructure](13-api-et-contrats.md) | Surface REST + gRPC, versionnage, sécurité |
| 14 | [Sécurité](14-securite.md) | Identité de service, moindre privilège, zero trust |
| 15 | [Observabilité](15-observabilite.md) | Traces, métriques, logs, SLO du Kernel |
| 16 | [Packages & Stack technique](16-packages-et-stack-technique.md) | Structure multi-module, choix technologiques |
| 17 | [Stratégie de tests](17-strategie-de-tests.md) | Pyramide de tests adaptée à un control plane |
| 18 | [Anti-patterns](18-anti-patterns.md) | Ce qu'il ne faut jamais faire, et pourquoi |
| 19 | [Feuille de route](19-feuille-de-route.md) | Phases livrables + Definition of Done |
| 20 | [Scénario de bout en bout](20-scenario-bout-en-bout.md) | Walkthrough complet, appel par appel |
| — | [Glossaire](glossaire.md) | Vocabulaire EGEN |
| — | [Références](references.md) | Sources d'inspiration externes |

## La règle d'or

> **EGEN possède l'intelligence de composition. Les moteurs spécialisés
> possèdent l'intelligence d'exécution.**

EGEN ne réimplémente jamais ce qu'un moteur spécialisé (Nomad, Consul,
Kafka/NATS) sait déjà faire. Avant d'écrire une ligne de code sur une
capacité candidate, l'équipe vérifie toujours si l'un de ces projets — ou
les sources d'inspiration listées dans [références](references.md)
(Dapr, KubeVela, Crossplane, Score, CloudEvents, OpenTelemetry) — a déjà
résolu le problème. Ce sont des sources d'inspiration de conception,
jamais des dépendances runtime du cœur.
