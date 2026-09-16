# 01 — Vision et positionnement

## Pourquoi EGEN existe

EGEN n'est pas un orchestrateur de plus. Le paysage des « control planes »
est déjà dense — Kubernetes, KubeVela, Crossplane, Dapr, Score, Humanitec —
et EGEN se positionne **très précisément** par rapport à eux : le projet ne
doit jamais réinventer ce qui existe déjà.

> **EGEN possède l'intelligence de composition. Les moteurs spécialisés
> possèdent l'intelligence d'exécution.**

| EGEN sait dire… | … le moteur spécialisé sait répondre |
|---|---|
| « Ce service doit tourner, avec ces ressources. » | Nomad : sur quel nœud, avec quel scheduling, quelle allocation. |
| « Ce service doit être découvrable et sain. » | Consul : où sont ses instances, sont-elles en bonne santé. |
| « Ces services échangent tel type d'événement. » | Kafka/NATS : comment transporter, répliquer, garantir la livraison. |
| « Cette suite d'étapes métier doit s'exécuter dans cet ordre, avec compensation si ça échoue. » | Le moteur de Workflow EGEN (natif — voir [11](11-moteur-de-workflow.md)) orchestre, mais délègue chaque étape au service propriétaire. |

## Positionnement face à l'existant

| Dimension | Dapr | KubeVela (OAM) | Crossplane | Score (CNCF) | **EGEN** |
|---|---|---|---|---|---|
| Nature | Runtime de primitives distribuées (sidecar) | Control plane de *delivery* d'applications | Control plane universel d'infrastructure (Kubernetes-natif) | Spécification de workload portable (pas un runtime) | Control/Composition plane d'écosystème de services |
| Infra sous-jacente imposée | Aucune (composants pluggables) | **Kubernetes obligatoire** | **Kubernetes obligatoire** | Aucune — dépend de l'implémentation | Aucune — Nomad/Consul/Kafka en V1, adapters remplaçables |
| Unité de description | Composants Dapr (state store, pubsub, binding…) | `Application` (OAM) = composants + traits + policies + workflow | `CompositeResourceDefinition` (XRD) + `Composition` | `score.yaml` (workload + resources + service ports) | `EGEN Service Manifest` (voir [06](06-service-manifest.md)) |
| Boucle de contrôle | Non (sidecar réactif) | Oui (contrôleurs Kubernetes) | **Oui, cœur du produit** | Non (transformation statique par le CLI) | **Oui — Declare → Resolve → Compose → Delegate → Observe → Reconcile** |
| Scheduling / placement | Non traité | Délégué à Kubernetes | Délégué au(x) provider(s) cloud | Non traité | **Délégué à Nomad** via Deployment Adapter |
| Service discovery | Oui (résolution de nom d'app) | Indirect (Services K8s) | Non | Non | **Délégué à Consul** via Discovery Adapter |
| Messaging | Oui (building block pub/sub) | Via composants | Non | Non | **Délégué à Kafka/NATS** via Messaging Adapter |
| Workflow | Oui (Dapr Workflow) | Oui (`WorkflowStepDefinition`) | Non | Non | Natif, inspiré de Dapr Workflow / Argo Workflows |
| Polyglotte | Oui, via sidecar HTTP/gRPC | Oui | Sans objet | Oui | Oui — **exigence de conception n°1** |
| Verrouillage technologique | Faible | **Fort (Kubernetes)** | **Fort (Kubernetes)** | Faible | Faible, par construction (adapters) |

**Conclusion opérationnelle** : EGEN s'inspire de Dapr pour la
Communication Fabric ([12](12-communication-fabric.md)), de Crossplane
pour le moteur de réconciliation ([04](04-moteur-de-reconciliation.md)) et
le concept desired/observed state, de Score pour le format déclaratif de
manifeste de service ([06](06-service-manifest.md)), et de KubeVela/Argo
pour le moteur de Workflow ([11](11-moteur-de-workflow.md)). Mais EGEN ne
dépend **d'aucun** d'entre eux : ce sont des sources d'inspiration de
conception, jamais des dépendances runtime.

## Ce qu'EGEN n'est pas

- **Ce n'est pas** un PaaS métier (pas de modèle `Invoice`, `Patient`, `Article`).
- **Ce n'est pas** un remplaçant de Kubernetes/Nomad — c'est une couche au-dessus.
- **Ce n'est pas** un ESB (Enterprise Service Bus) qui comprend la sémantique métier des messages.
- **Ce n'est pas** un framework applicatif imposé aux services (pas de SDK obligatoire couplé à un langage — voir [12.2](12-communication-fabric.md)).
- **Ce n'est pas** un IAM central déguisé (identité, authentification, autorisation métier, utilisateurs, organisations, tenants, membres, rôles ou permissions métier).
- **Ce n'est pas** un ERP technique ou un backend métier partagé (GED, éducation, finance, RH, santé, CRM…).

Toute logique de cette nature est portée par des **services indépendants**,
au même titre que n'importe quel autre service de l'écosystème — jamais
codée en dur dans le Kernel. Le jour où une capacité « pratique à avoir au
même endroit pour tout le monde » se présente (identité, autorisation,
notification…), la question n'est jamais « est-ce plus simple de la mettre
dans le Kernel ? » mais toujours « quel service indépendant va porter cette
capacité, et comment le Kernel va-t-il l'aider à l'exposer au reste de
l'écosystème ? ».

## Ce qui doit rester stable dans le temps

Le cœur hexagonal (`egen-domain` + `egen-application`) est la partie du
système qui doit changer le moins souvent. Tout le reste — adapters,
moteurs externes, services métier — doit pouvoir être remplacé sans
toucher au cœur. C'est le test décisif de la réussite architecturale : **la
fréquence de modification de `egen-domain` doit tendre vers zéro** une fois
le Kernel stabilisé (métrique suivie, voir [19](19-feuille-de-route.md)).
