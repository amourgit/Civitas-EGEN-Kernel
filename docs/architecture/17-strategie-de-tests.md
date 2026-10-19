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
`DeploymentPort.stop()` (voir [09](09-cycle-de-vie.md#ordre-darrêt-gracieux--un-piège-classique)),
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
via docker-compose ou un cluster de test dédié — ici Testcontainers et un
agent Nomad de l'hôte, voir plus bas) sur lequel tournent des scénarios
représentatifs :

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
  [15](15-observabilite.md#slo-du-kernel-lui-même)).
- **Scénario de workflow avec compensation** : forcer l'échec d'une étape
  intermédiaire, vérifier que les compensations s'exécutent dans le bon
  ordre.

Le scénario complet de bout en bout, rejoué littéralement comme test de ce
niveau, est détaillé en [20](20-scenario-bout-en-bout.md) — c'est le test
d'acceptation le plus important du projet.

### Le test de niveau 5 du dépôt

`EgenAcceptanceE2ETest` (module `egen-bootstrap`, étiqueté `e2e`) démarre le
**vrai Kernel**, avec ses vrais adapters, contre un environnement éphémère
complet — aucun double de port — et le pilote par HTTP comme le ferait un
`curl`. Chaque scénario ci-dessus y a son test :

| Scénario | Test | Ce qui est vérifié, pour de vrai |
| --- | --- | --- |
| Kernel vide | `emptyKernelIsHealthy…` | `/api/v1/health`, aucun service, rien de résolu |
| Ajout sans modifier le Kernel | `aMissingVaultSecret…` | un service déclaré par l'API seule converge jusqu'à `RUNNING` ; un secret Vault absent le retient en `REGISTERED` (`ConfigResolved`), sans job Nomad, puis il converge dès que le secret existe |
| Dépendances | `aServiceWaitsForItsRequiredDependencies…` | `news-service`, déclaré **avant** ses deux dépendances `required`, n'est soumis à Nomad qu'**après** elles (comparaison des `SubmitTime`) |
| Liaisons | `aRunningServiceIsFullyBound…` | les six Conditions V1 à `TRUE` ; deux instances saines dans **Consul** ; un flux **NATS** par type d'événement publié ou consommé ; le bloc `interfaces` restitué tel que déclaré |
| Panne et reconvergence | `aRealCrashDegrades…` | un vrai processus s'arrête : `DEGRADED`, puis `RUNNING`, puis deux instances saines |
| Workflow avec compensation | `theReferenceWorkflowRuns…`, `aFailingStepCompensates…` | le workflow du doc 11 sur de vrais services : événement dans le flux NATS ; échec voulu de `publish` → `release-slot` seul, dans l'ordre, jamais `unpublish` |

**Environnement.** PostgreSQL, NATS (JetStream), Consul et Vault sont des
conteneurs Testcontainers. **Nomad est un processus de l'hôte**, pas un
conteneur : l'adapter Nomad génère un job de driver `docker`, donc Nomad doit
être un *client* avec accès au démon Docker, et HashiCorp ne supporte pas un
client Nomad dans un conteneur. Il doit en outre annoncer l'**interface
réseau réelle** de l'hôte — en mode `-dev` il annonce le loopback, que Consul
(dans un conteneur) ne peut pas sonder : aucun service n'atteindrait jamais
`RUNNING`. Le test vérifie ces deux points (nœud prêt, driver `docker` sain,
adresse routable) et échoue, avec la commande à lancer, s'ils ne sont pas
remplis. Le service de fixture est un vrai processus Python déployé par Nomad
(`egen-bootstrap/src/test/resources/e2e/fixture/server.py`) qui se laisse
piloter : journal d'appels, échec forcé, crash réel.

**Exécution.**

```bash
sudo nomad agent -dev -bind=0.0.0.0 \
     -network-interface="$(ip route show default | awk '/default/ {print $5; exit}')" &
mvn -B install -DskipTests            # une fois
mvn -B -pl egen-bootstrap -Pe2e verify
```

Il est **exclu du build par défaut** (il exige Docker et plusieurs
minutes) : le profil `e2e` ne lance que les tests étiquetés `e2e`. En CI, il
a son propre job (« Acceptation de bout en bout (niveau 5) »), qui installe et
démarre l'agent, et **publie son diagnostic en commentaire du commit** en cas
d'échec : tests en échec, Conditions distinctes observées par le Kernel pour
chaque service, avertissements du Kernel.

**Ce qu'il a déjà révélé** — ce pour quoi il existe : l'application assemblée
**n'exposait aucun endpoint REST** (le plugin Jandex n'était activé dans aucun
module, donc Quarkus ne découvrait pas `egen-api` consommé comme dépendance ;
les tests de l'API, qui s'exécutent *dans* ce module, passaient) ; et le défaut
d'adresse annoncée par Nomad décrit plus haut.

## Niveau 6 — Chaos engineering (à introduire progressivement)

Une fois les niveaux 1 à 5 stabilisés : injection de latence réseau vers
Consul, coupure temporaire de NATS pendant une rafale de publications,
redémarrage brutal du Kernel en plein cycle de réconciliation — pour
valider concrètement la propriété d'idempotence et de reprise sur
laquelle repose toute l'architecture (voir
[04.3](04-moteur-de-reconciliation.md#idempotence--condition-de-survie-de-la-boucle)).

## Tests de contrat inter-langages (Communication Fabric)

La résilience ([12](12-communication-fabric.md#sémantique-normative-de-la-résilience))
est implémentée trois fois (Kernel, SDK Java, SDK Python). Pour qu'elles ne
divergent jamais, la sémantique est **exécutable** :
`sdk/contract/resiliency-contract.json` liste des scénarios (réponses
scriptées → résultat, nombre de tentatives, attentes, état du circuit) que
chaque implémentation rejoue dans sa suite habituelle :

| Implémentation | Scénarios rejoués | Où |
| --- | --- | --- |
| Kernel (`CircuitBreaker`) | `scope: breaker` | `CircuitBreakerContractTest` (`egen-application`) |
| SDK Java | tous | `ResiliencyContractTest` (`egen-sdk-java`) |
| SDK Python | tous | `sdk/python/tests/test_contract.py` |

Règle : **toute évolution de la sémantique commence par ce fichier** ; un
scénario ajouté doit faire échouer les trois suites avant que l'on corrige
les trois implémentations. Les SDK sont par ailleurs testés contre de vrais
serveurs HTTP locaux (`com.sun.net.httpserver`, `http.server`) — jamais contre
un mock du client HTTP, qui ne vérifierait ni les délais ni les refus de
connexion.
