# 09 — Gestion du cycle de vie — machine à états complète

## États (phases)

```
DECLARED ──▶ REGISTERED ──▶ CONFIGURED ──▶ DEPLOYING ──▶ RUNNING ──┬──▶ DEGRADED ──▶ RUNNING
                                               │                    │        │
                                               │                    └──▶ FAILED
                                               ▼
                                            FAILED
RUNNING/DEGRADED ──▶ UPDATING ──▶ RUNNING (ou ROLLED_BACK ──▶ RUNNING si échec)
RUNNING/DEGRADED/FAILED ──▶ STOPPING ──▶ STOPPED
STOPPED ──▶ REMOVING ──▶ REMOVED
FAILED / STOPPED ──▶ DECLARED       (un nouveau Declare du même service le relance, voir « Relance par Declare »)
```

| Phase | Signification | Condition de sortie (calculée par la réconciliation) |
|---|---|---|
| `DECLARED` | Le manifeste a été validé et persisté. Aucune action déléguée encore. | Passe à `REGISTERED` dès que le premier cycle de réconciliation le prend en charge. |
| `REGISTERED` | Le service existe dans le Registry EGEN, ses dépendances sont résolues. | Passe à `CONFIGURED` une fois la configuration/les secrets résolus avec succès. |
| `CONFIGURED` | Configuration et secrets prêts à être injectés. | Passe à `DEPLOYING` dès que le plan d'exécution (voir [04](04-moteur-de-reconciliation.md), étape Compose) est prêt et que les dépendances **required** sont elles-mêmes `RUNNING`. |
| `DEPLOYING` | `DeploymentPort.create/update` a été appelé, en attente de convergence. | `RUNNING` si `ObservedState.deployment.healthyCount >= desiredCount` **et** `DiscoveryObservation` confirme des instances saines. `FAILED` si le déploiement échoue au-delà du seuil de retry. |
| `RUNNING` | État nominal — désiré et observé convergent. | `DEGRADED` si une partie des instances devient malsaine ; `UPDATING` sur nouvelle génération du manifeste ; `STOPPING` sur demande d'arrêt. |
| `DEGRADED` | Convergence partielle (ex. 3 instances saines sur 5 désirées). | Retour `RUNNING` si la convergence se rétablit ; `FAILED` après `egen.reconciliation.degraded-timeout` (10 minutes par défaut, « DEGRADED depuis trop longtemps »). |
| `FAILED` | Le Kernel ne parvient pas à converger malgré les retries. | Ne se corrige pas seul : **jamais de retry infini silencieux**, le compteur d'échecs est plafonné et visible. Un nouveau `Declare` du même identifiant (manifeste identique ou corrigé) le relance depuis `DECLARED` ([Relance par Declare](#relance-par-declare)) ; sinon il reste en `FAILED` avec alerting actif, ou passe en `STOPPING`. |
| `UPDATING` | Nouvelle génération en cours de déploiement par-dessus une version `RUNNING`/`DEGRADED`. | `RUNNING` sur succès ; `ROLLED_BACK` puis `RUNNING` si `updateStrategy.autoRevert` déclenche un retour arrière automatique (mappé sur le mécanisme natif de rollback Nomad, voir [07.1](07-ports-et-adapters.md#deployment-port)). |
| `STOPPING` | Arrêt gracieux en cours (deregister Consul avant arrêt Nomad — ordre important, voir plus bas). | `STOPPED` une fois toutes les instances arrêtées. |
| `STOPPED` | Aucune instance active, mais le manifeste reste dans le Registry (peut être redéployé). | `DECLARED` sur un nouveau `Declare` du même identifiant (relance, [Relance par Declare](#relance-par-declare)), ou `REMOVING` sur suppression définitive. |
| `REMOVING` | Suppression en cours (purge Nomad, désenregistrement Consul, nettoyage des topics/streams si demandé). | `REMOVED`. |
| `REMOVED` | Suppression terminée. Conservé en historique pour audit, filtré des vues actives par défaut. | Terminal. |

## Relance par `Declare`

**Règle.** Un `Declare` accepté sur un service dont la phase est `FAILED` ou `STOPPED` remet ce service en `DECLARED`. Il repart alors comme un service neuf, par le chemin normal : `DECLARED → REGISTERED → CONFIGURED → DEPLOYING → RUNNING`. L'identifiant et l'historique des générations sont conservés. Le Kernel ne relance **jamais** un service tout seul (garde-fou n°10, [02](02-principes-fondamentaux.md)) : c'est toujours une déclaration de l'opérateur qui le fait.

**Pourquoi `DECLARED` et non `DEPLOYING`.** La machine à états autorise aussi `FAILED → DEPLOYING` et `STOPPED → DEPLOYING`, mais ce chemin sauterait les contrôles de `REGISTERED` (configuration et secrets résolus) et de `CONFIGURED` (dépendances `required` démarrées, flux d'événements garantis). Or le nouveau manifeste peut changer exactement ces choses, et un service `FAILED` a pu tomber avant l'un de ces contrôles. Repartir de `DECLARED` rejoue donc tout, sans règle spéciale. Les deux arêtes vers `DEPLOYING` restent autorisées mais **aucun code ne les emprunte** : elles sont réservées à une éventuelle action explicite `restart`.

**Qui fait quoi, dans l'ordre.** Exemple : `news-service` est `FAILED` parce que le namespace Kubernetes n'existait pas.

1. L'opérateur crée le namespace.
2. Il envoie `POST /api/v1/services` avec le manifeste (le même, ou corrigé) et une **nouvelle** valeur d'`Idempotency-Key`. Avec une valeur déjà utilisée, l'API rejoue la réponse mémorisée **sans rien exécuter** ([13](13-api-et-contrats.md)).
3. Le use case de déclaration valide la requête (adapter activé et inchangé, pas de cycle, appelant administrateur) et enregistre une nouvelle génération.
4. Il constate la phase `FAILED` et écrit le statut `DECLARED`, avec une liste de Conditions **vide** et l'`observedGeneration` de la nouvelle génération. Il met ensuite le service dans la file de travail.
5. Le moteur de réconciliation prend le relais : `ManifestValid`, puis `ConfigResolved`, puis `DependenciesSatisfied` et `MessagingBindingsReady`, puis le déploiement. L'opérateur suit la progression par `GET /api/v1/services/news-service/status`.

**Effet selon la phase au moment du `Declare`.**

| Phase | Effet |
|---|---|
| `FAILED`, `STOPPED` | Remise en `DECLARED`, Conditions effacées. |
| `DECLARED`, `REGISTERED`, `CONFIGURED`, `DEPLOYING`, `RUNNING`, `DEGRADED`, `UPDATING`, `ROLLED_BACK` | **Inchangé** : une nouvelle génération est enregistrée et le moteur la prend en compte comme avant. |
| `STOPPING` | **Inchangé** : l'arrêt va à son terme (il n'est pas interrompu). Un second `Declare` relance ensuite le service arrêté. |
| `REMOVING`, `REMOVED` | **Inchangé** : phases terminales. |
| Service inconnu | Création normale. |

**Pourquoi effacer les Conditions.** Sans cela, la Condition `DeploymentReady = False` (raison `AdapterFailureThresholdExceeded`) de l'ancien échec resterait lisible pendant que le service est de nouveau `DECLARED`, ce qui induirait l'opérateur en erreur. Chaque Condition est réécrite par le cycle qui la concerne.

**Ce que la relance ne fait pas.** Un `Declare` **refusé** (adapter non activé, adapter changé, cycle, droits insuffisants) ne touche à rien : toutes les validations précèdent l'écriture. La relance n'ajoute aucun droit : seul un administrateur peut déclarer (ou redéclarer) un service ([14](14-securite.md)).

### Comptage des échecs d'adapter

*Écart I12 du [document 19](19-feuille-de-route.md), documenté d'après les observations du 12 au 14 novembre 2026 (V6, V10).*

Le Moteur de Réconciliation compte les échecs consécutifs d'un adapter (Nomad, Kubernetes, Consul, Vault) **par service**, et non par génération. Concrètement :
1. Tant qu'un service réessaie, deux `Declare` successifs ne remettent pas le compteur à zéro : `(1/5)` pour la génération 1, puis `(2/5)` pour la génération 2 (observé en V10).
2. Quand le compteur atteint 5, le service passe `FAILED` (sauf pendant un arrêt, voir plus bas).
3. Relancer un service `FAILED` par un nouveau `Declare` **remet le compteur à zéro** : la tentative suivante s'écrit `(1/5)` (observé en V6).
4. Un passage de réconciliation qui réussit remet aussi le compteur à zéro.
5. Le dé-enregistrement d'un arrêt a son propre compteur (voir « Quand le dé-enregistrement échoue »).

### DEGRADED depuis trop longtemps

*Décidé par Samuel le 14 novembre 2026 (point ouvert 4, option a) ; codé et testé le même jour.*

**Les termes.** `DEGRADED` : le service tourne mais n'a plus toutes ses instances saines (par exemple 3 sur 5). Le **délai de dégradation** est la durée maximale pendant laquelle le Kernel tolère cet état avant de conclure que le service ne se rétablira pas seul.

**Le contrat.**
1. La propriété `egen.reconciliation.degraded-timeout` (durée ISO-8601) fixe ce délai. Valeur par défaut : **10 minutes** (`PT10M`). `PT0S` désactive la règle : un service peut alors rester `DEGRADED` sans fin.
2. Le délai se compte depuis le moment où le Moteur de Réconciliation voit le service passer `DEGRADED`. Si le service redevient `RUNNING` entre-temps, le délai repart de zéro à la dégradation suivante.
3. Le délai est tenu **en mémoire** : un redémarrage du Kernel le fait repartir de zéro (aucune date n'est enregistrée dans le Registry).
4. Quand le délai est écoulé, le service passe `FAILED` avec la Condition `DeploymentReady = False`, `reason = DegradedTimeout`, `message = « DEGRADED depuis plus de PT10M (egen.reconciliation.degraded-timeout) ; desired=… healthy=… »`. Comme tout service `FAILED`, il n'est plus réconcilié : un nouveau `Declare` le relance (« Relance par Declare »).

**Pourquoi 10 minutes.** Nomad ou Kubernetes replanifient normalement une instance perdue en quelques secondes à quelques minutes. Un délai trop court déclarerait en échec un service que l'orchestrateur est en train de réparer ; l'absence de délai laisserait un service amputé sans que personne ne soit alerté.

**Exemple de bout en bout.** `news` demande 5 instances, 3 sont saines à 10 h 00 : `RUNNING → DEGRADED`, Condition `AllocationLost`. À 10 h 04 Nomad a replanifié, 5 instances saines : `RUNNING`. À 11 h 00 une panne de disque en perd 2 et Nomad ne peut pas les replacer : `DEGRADED`. À 11 h 10 le délai est écoulé : `FAILED`, Condition `DegradedTimeout`. L'opérateur lit la cause, corrige le cluster, puis relance `news` par `Declare`.

### Déclarer pendant un arrêt (`STOPPING`)

*Écart I19 du [document 19](19-feuille-de-route.md), décidé par Samuel le 14 novembre 2026 (décision déléguée à Claude) ; codé et testé le même jour.*

Un `Declare` sur un service qui est en `STOPPING` est **refusé avec `409`** (« en cours d'arrêt : attendre `STOPPED`, puis le déclarer à nouveau »), avant toute écriture : la génération n'est pas créée et le statut n'est pas touché. Raison : une nouvelle génération enregistrée pendant l'arrêt n'aurait aucun effet défini, puisque l'arrêt va se terminer en `STOPPED`, phase d'où seul un `Declare` relance. Un `Declare` sur un service `STOPPED` ou `FAILED` reste accepté et le relance (« Relance par `Declare` »).

## Conditions

En complément de la phase (état macro), chaque `ServiceStatus` porte une
liste de `Condition` — plus fine, multi-dimensionnelle, et qui ne perd pas
d'information au fil des transitions (modèle inspiré des
`status.conditions` Kubernetes) :

```json
{
  "type": "DependenciesSatisfied",
  "status": "False",
  "reason": "VersionConstraintUnmet",
  "message": "notification-service is at 1.1.0, but >=1.2.0 is required",
  "lastTransitionTime": "2026-09-16T10:00:00Z"
}
```

Types de `Condition` en V1 : `ManifestValid`, `DependenciesSatisfied`,
`DeploymentReady`, `DiscoveryReady`, `MessagingBindingsReady`,
`ConfigResolved`. Cette granularité permet à un opérateur humain de
diagnostiquer *en un coup d'œil* pourquoi un service reste bloqué en
`CONFIGURED` au lieu de `DEPLOYING`, sans devoir fouiller les logs bruts.

### Sémantique des Conditions V1

Chaque Condition porte un `status` (`True`/`False`), un `reason` **stable**
(lisible par une machine, jamais reformulé) et un `message` lisible. Un
`message` ne cite que des **noms** — clés, chemins de référence, types
d'événements — jamais une valeur de configuration ni de secret.

| Condition | Évaluée | `True` | `False` — et ce que cela provoque |
| --- | --- | --- | --- |
| `ManifestValid` | à `DECLARED` → `REGISTERED` | `Valid` | jamais `False` en pratique : un manifeste invalide est refusé en `400` avant toute persistance |
| `ConfigResolved` | à `REGISTERED`, avant `CONFIGURED` | `NothingToResolve` (aucune configuration ni secret déclarés), `Resolved` | le service **reste `REGISTERED`** et la résolution est retentée — avec un **délai croissant** (2, 4, 8, 16, 32 secondes, puis 60 secondes au plus entre deux tentatives ; écart E3, décidé le 14 novembre 2026) — ce n'est pas un échec du service : un secret ou une clé manquants peuvent être fournis plus tard sans toucher au manifeste. `reason` : `SecretUnresolved`, `RequiredKeyMissing`, `InvalidConfigurationValue`, `ConfigurationUnavailable`, `PortNotConfigured` |
| `DiscoveryDeregistered` | à `STOPPING`, au dé-enregistrement | `Deregistered` (réussi ; le message dit après combien d'échecs s'il y en a eu) | `DeregistrationRetrying` (nouvelle tentative prévue, le message donne `tentative n/5` et la cause), `DeregistrationIncomplete` (cinq échecs : l'arrêt continue, voir « Quand le dé-enregistrement échoue ») |
| `DeploymentStopped` | à `STOPPING`, à l'arrêt du déploiement | — | `StopFailed` (l'arrêt a échoué et est retenté, le message donne `tentative n/5` et la cause, voir « Quand l'arrêt lui-même échoue ») |
| `DependenciesSatisfied` | à `CONFIGURED` | `AllSatisfied`, `OptionalDependencyUnavailable` | le service reste `CONFIGURED` (voir [10](10-gestion-des-dependances.md)) |
| `MessagingBindingsReady` | à `CONFIGURED` avant le déploiement, puis à **chaque cycle** | `NoEventsDeclared`, `StreamsEnsured` | à `CONFIGURED` : le service **n'est pas déployé** tant que ses flux ne sont pas garantis (publier dans un flux inexistant perdrait les premiers événements). Une fois `RUNNING` : la Condition passe à `False` mais **le service n'est jamais dégradé** pour autant. `reason` : `MessagingUnavailable`, `PortNotConfigured` |
| `DeploymentReady`, `DiscoveryReady` | de `DEPLOYING` à `RUNNING`/`DEGRADED` | convergence atteinte | voir ci-dessus ; `DeploymentReady` peut aussi porter `DegradedTimeout` (« DEGRADED depuis trop longtemps ») |

Une Condition qui passe à `False` pour une raison **externe** (secret absent,
broker injoignable) ne fait jamais passer le service en `FAILED` : seul un
échec répété du Deployment Adapter le fait (voir [04](04-moteur-de-reconciliation.md)).

## Ordre d'arrêt gracieux — un piège classique

Lors du passage `RUNNING → STOPPING`, l'ordre des opérations déléguées
compte :

1. **D'abord** `DiscoveryPort.deregister()` — retire l'instance de la
   liste des instances saines de Consul, pour que les clients cessent de
   lui envoyer du nouveau trafic.
2. **Attendre** `lifecycle.shutdown.gracePeriod` (défini dans le
   manifeste) — laisse le temps aux requêtes en vol de se terminer
   (`drainConnections`).
3. **Ensuite** `DeploymentPort.stop()` — arrête réellement le
   processus/conteneur via Nomad.

**Si le Kernel meurt pendant l'arrêt.** Le marqueur « désinscrit à tel instant » que le moteur utilise pour compter le délai de grâce est gardé **en mémoire**, pas persisté. Un Kernel redémarré le retrouve vide : il refait la désinscription (idempotente), puis attend de nouveau tout le délai de grâce avant d'arrêter. L'arrêt est donc plus long que prévu, jamais incomplet ; c'est vérifié par le test de chaos de niveau 6a ([17](17-strategie-de-tests.md#niveau-6a--mort-brutale-du-processus-simulée-de-façon-déterministe-livré)).

### Quand une demande d'arrêt est acceptée

*Écart I20 du [document 19](19-feuille-de-route.md), documenté d'après le code (`StopServiceUseCaseImpl`, `LifecycleStateMachine`) et l'essai V6a du 14 novembre 2026.*

`POST /api/v1/services/{id}/actions/stop` répond selon la phase du service au moment de la demande :

| Phase du service | Réponse | Effet |
|---|---|---|
| `RUNNING`, `DEGRADED`, `FAILED` | `202` | le service passe `STOPPING` |
| `STOPPING`, `STOPPED` | `202` | aucun : la demande est idempotente, rien ne change |
| toute autre phase (`DECLARED`, `REGISTERED`, `CONFIGURED`, `DEPLOYING`, `UPDATING`, `ROLLED_BACK`, `REMOVING`) | `400` | refusée : la machine à états n'a pas de transition vers `STOPPING` depuis ces phases |
| service inconnu | `404` | — |

**Conséquence pratique.** Un service en `DEPLOYING` ne peut pas être arrêté. Il le peut dès qu'il quitte cette phase : `RUNNING` s'il converge, ou `FAILED` après ses cinq échecs (de 30 à 50 secondes environ, observé en V6a). Autoriser l'arrêt depuis `DEPLOYING` serait un changement de la machine à états, non décidé à ce jour.

### Quand le dé-enregistrement échoue (Consul injoignable)

*Écart E4 du [document 19](19-feuille-de-route.md). Décidé par Samuel le 14 novembre 2026 (avis de Claude retenu).*

**Les termes.** *Dé-enregistrer* : retirer du Discovery (Consul) chaque instance du service, pour que les clients cessent de lui envoyer du trafic (étape 1 ci-dessus). *Au mieux* : on essaie, et si cela échoue de façon répétée, on continue quand même l'arrêt au lieu de le bloquer.

**Le problème.** Avant cette règle, si Consul ne répondait pas au moment d'un `actions/stop`, le service restait `STOPPING` sans fin : le dé-enregistrement était retenté toutes les 30 à 60 secondes sans plafond, l'arrêt n'était jamais exécuté dans le moteur de déploiement (le Deployment gardait ses réplicas), et aucune Condition n'expliquait le blocage (la cause n'était que dans le journal).

**Le contrat.**

1. Chaque échec du dé-enregistrement est compté, **à part** du compteur d'échecs de déploiement. Un échec écrit une ligne `WARN` (`Echec de deregistrement pour <id> (n/5) : <cause>`).
2. Aux échecs 1 à 4, le service **reste `STOPPING`**. La Condition `DiscoveryDeregistered = False`, `reason = DeregistrationRetrying`, dit `tentative n/5 : <cause>`. La nouvelle tentative a lieu après **2, 4, 8 puis 16 secondes**.
3. Au **cinquième échec**, l'arrêt **continue** : la Condition devient `DeregistrationIncomplete` (« après 5 échecs, l'arrêt continue ; des entrées Discovery peuvent rester jusqu'à l'expiration de leurs contrôles de santé »), puis le délai de grâce s'applique comme d'habitude, puis l'arrêt est exécuté dans le moteur de déploiement (`DeploymentPort.stop()` : Kubernetes met le Deployment à 0 réplica et le conserve, Nomad arrête le job sans le purger, document [07](07-ports-et-adapters.md)), et le service passe `STOPPED`. La Condition est conservée dans le statut `STOPPED`.
4. Si une tentative réussit avant le cinquième échec, la Condition devient `Deregistered` (`True`) et le message dit après combien d'échecs. Le compteur repart de zéro.
5. Le service ne passe **jamais** `FAILED` à cause du dé-enregistrement : `STOPPING → FAILED` reste interdit par la machine à états.

**Pourquoi « au mieux ».** Un arrêt qui ne s'exécute pas pendant un incident est plus grave qu'une entrée Consul périmée : ses contrôles de santé la retirent d'eux-mêmes. Le prix : pendant ce délai, des clients peuvent encore recevoir l'adresse d'une instance en cours d'arrêt.

**Ce que cette règle ne couvre pas.** Un échec de `DeploymentPort.stop()` (Nomad ou Kubernetes refuse l'arrêt) a sa propre règle : « Quand l'arrêt lui-même échoue ».

**Exemple de bout en bout.** L'opérateur demande l'arrêt de `news`. Consul est arrêté. T+0 : échec 1, Condition `tentative 1/5 : …`, nouvelle tentative dans 2 s. T+2 s, T+6 s, T+14 s : échecs 2, 3, 4. T+30 s : échec 5, la Condition passe à `DeregistrationIncomplete`. Après le délai de grâce du manifeste, l'arrêt est exécuté dans le moteur de déploiement et `news` est `STOPPED`. L'opérateur lit `GET …/status` : phase `STOPPED`, Condition `DiscoveryDeregistered = False` avec la cause, et sait qu'une entrée de `news` peut rester dans Consul.

### Quand l'arrêt lui-même échoue

*Écart I18 du [document 19](19-feuille-de-route.md), décidé par Samuel le 14 novembre 2026 (décision déléguée à Claude) ; codé et testé le même jour.*

**Le problème.** `DeploymentPort.stop()` (Kubernetes : mettre le Deployment à 0 réplica ; Nomad : arrêter le job) peut échouer, par exemple avec un `401` quand le jeton du Kernel a expiré. Un service `STOPPING` ne peut pas passer `FAILED` (transition interdite), et il ne peut pas non plus passer `STOPPED` tant que l'arrêt n'a pas eu lieu.

**Le contrat.**
1. Le service **reste `STOPPING`**. L'arrêt est retenté : après 2, 4, 8 puis 16 secondes pour les quatre premiers échecs, puis **toutes les 60 secondes** sans fin.
2. Le compteur d'échecs est **plafonné à 5/5** : le journal n'écrit jamais `(6/5)`.
3. La Condition `DeploymentStopped = False`, `reason = StopFailed`, `message = « tentative n/5 : <cause> »` dit pourquoi l'arrêt n'avance pas.
4. Dès qu'une tentative réussit, le service passe `STOPPED`.

**Exemple de bout en bout.** Le jeton Kubernetes du Kernel a expiré. L'opérateur demande l'arrêt de `news` : le dé-enregistrement réussit, puis `stop()` répond `401`. `GET …/status` : phase `STOPPING`, Condition `DeploymentStopped = False / StopFailed / « tentative 5/5 : Kubernetes a rejete scale(news) : HTTP 401 … »`. L'opérateur renouvelle le jeton et redémarre le Kernel ; la tentative suivante réussit et `news` passe `STOPPED`.

Inverser cet ordre (arrêter Nomad avant de désenregistrer Consul) provoque
des erreurs 502/connexions refusées côté clients pendant la fenêtre de
propagation du health check — c'est l'erreur la plus fréquente en
production sur ce genre de plateforme et elle est testée explicitement
(voir [17 — Stratégie de tests, niveau 3](17-strategie-de-tests.md)).
