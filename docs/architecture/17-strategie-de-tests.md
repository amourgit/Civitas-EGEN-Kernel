# 17 — Stratégie de tests — pyramide adaptée à une architecture hexagonale + control plane

## Niveau 1 — Tests de domaine (rapides, majoritaires, zéro I/O)

Testent `egen-domain` en isolation totale : construction d'un
`ServiceManifest` invalide (doit lever une exception de validation),
détection de cycle dans `DependencyGraph`, transitions de la
`LifecycleStateMachine` (toute transition non explicitement autorisée par
la table de [09](09-cycle-de-vie.md) doit être rejetée par du code, pas
seulement par convention), calcul du tri topologique. Ces tests ne
démarrent **aucun** conteneur, ne font **aucun** appel réseau — ils
s'exécutent en quelques secondes pour l'ensemble de la suite.

## Niveau 2 — Tests de use case (application layer) avec doubles de test

Testent `egen-application` en injectant les implémentations **en
mémoire** des ports secondaires fournies par `egen-test-kit`
(`InMemoryDeploymentPort`, `InMemoryDiscoveryPort`…). Objectif : vérifier
la logique d'orchestration du cycle Declare → Resolve → Compose → Delegate
(voir [04](04-moteur-de-reconciliation.md)) — par exemple, vérifier qu'un
`DeployServiceUseCase` appelle bien `DiscoveryPort.deregister()` avant
`DeploymentPort.stop()` (voir [09](09-cycle-de-vie.md#ordre-darret-gracieux--un-piege-classique)),
sans dépendre d'un vrai Consul.

## Niveau 3 — Tests d'adapters avec Testcontainers

Chaque module `egen-adapters/*` a sa **propre** suite de tests
d'intégration qui démarre le moteur réel concerné dans un conteneur
éphémère (image officielle Nomad en mode dev, image officielle Consul en
mode dev, NATS JetStream) via Testcontainers, et vérifie que le mapping
décrit en [07](07-ports-et-adapters.md) fonctionne réellement contre l'API
réelle — pas seulement contre une documentation. C'est **le filet de
sécurité** contre la dérive de compatibilité quand HashiCorp ou le projet
NATS publient une nouvelle version mineure de leur API.

Tests spécifiques à ne pas oublier à ce niveau :

- Idempotence réelle : appeler `create()` deux fois de suite avec le même
  manifeste et vérifier qu'aucune erreur ni duplication n'apparaît côté
  Nomad/Consul/NATS.
- Comportement sous panne : couper le conteneur Nomad en plein milieu
  d'un appel et vérifier que l'adapter remonte une erreur exploitable par
  la boucle de réconciliation (pas une exception non catégorisée).
- Ordre d'arrêt gracieux (voir [09](09-cycle-de-vie.md)) reproduit avec un
  vrai health check Consul et une vraie fenêtre de propagation.

## Niveau 4 — Tests d'architecture (fitness functions)

Suite dédiée (ArchUnit ou équivalent) exécutée à chaque build, qui
**échoue la CI** si :

- `egen-domain` importe un package d'un module `egen-adapters/*` ou d'un
  SDK externe.
- Une classe de `egen-domain` porte une annotation de framework
  (`@Entity`, `@RestController`, etc.).
- Un module `egen-adapters/*` importe un autre module `egen-adapters/*`.

Ce niveau transforme les garde-fous de [02](02-principes-fondamentaux.md)
en **faits vérifiés automatiquement**, pas en promesses.

## Niveau 5 — Tests de bout en bout et de non-régression architecturale

Environnement éphémère complet (Nomad + Consul + NATS + Postgres + Vault,
via docker-compose ou un cluster de test dédié) sur lequel tournent des
scénarios représentatifs :

- **Test « Kernel vide »** (garde-fou n°4, voir [02](02-principes-fondamentaux.md)) :
  démarrer EGEN avec zéro service déclaré, vérifier que `/api/v1/health`
  répond correctement.
- **Test « ajout de service sans modification du Kernel »** (garde-fou
  n°5) : déployer un service fixture *uniquement* via l'API publique, sans
  toucher au code du Kernel, vérifier la convergence complète jusqu'à
  `RUNNING`.
- **Scénario complet de dépendances** : déployer trois services liés par
  des dépendances `required`, vérifier l'ordonnancement topologique réel
  du déploiement.
- **Scénario de panne et reconvergence** : tuer une allocation Nomad
  manuellement, vérifier que la boucle de réconciliation détecte la
  divergence et la corrige dans le délai du SLO (voir
  [15](15-observabilite.md#slo-du-kernel-lui-meme)).
- **Scénario de workflow avec compensation** : forcer l'échec d'une étape
  intermédiaire, vérifier que les compensations s'exécutent dans le bon
  ordre.

Le scénario complet de bout en bout, rejoué littéralement comme test de ce
niveau, est détaillé en [20](20-scenario-bout-en-bout.md) — c'est le test
d'acceptation le plus important du projet.

## Niveau 6 — Chaos engineering (à introduire progressivement)

Une fois les niveaux 1 à 5 stabilisés : injection de latence réseau vers
Consul, coupure temporaire de NATS pendant une rafale de publications,
redémarrage brutal du Kernel en plein cycle de réconciliation — pour
valider concrètement la propriété d'idempotence et de reprise sur
laquelle repose toute l'architecture (voir
[04.3](04-moteur-de-reconciliation.md#idempotence--condition-de-survie-de-la-boucle)).
