# 23 — Runbooks opérateur

**À quoi sert ce document.** Il dit à une personne d'astreinte quoi regarder et quoi faire quand le Kernel signale un problème. Il couvre les trois scénarios de la Phase 6 ([19](19-feuille-de-route.md)) : un service en `FAILED`, une exécution de workflow en `COMPENSATION_FAILED`, une divergence prolongée entre l'état désiré et l'état observé.

**Règle d'écriture.** Chaque affirmation ci-dessous a été relue dans le code du Kernel (état au 5 novembre 2026), pas seulement dans la documentation. Quand le code ne fait pas ce que la documentation promet, ce document décrit le code, et l'écart est consigné dans [19](19-feuille-de-route.md) (points ouverts 4 et 6). Un geste qui n'existe pas n'est jamais présenté comme disponible.

**Vocabulaire.**

| Terme | Définition en une phrase |
|---|---|
| Phase | L'état macro d'un service : `RUNNING`, `FAILED`, etc. ([09](09-cycle-de-vie.md)). |
| Condition | Une ligne de diagnostic fine attachée au statut : un `type`, un `status` (`True`/`False`), une `reason` stable lisible par une machine, un `message` lisible par un humain. |
| Génération | Le numéro de version du manifeste désiré ; il augmente à chaque `Declare`. |
| Adapter | Le module qui parle à un moteur d'exécution (Nomad ou Kubernetes, [07](07-ports-et-adapters.md)). |
| Cycle de réconciliation | Une passe du moteur qui compare l'état désiré à l'état observé et agit pour les rapprocher ([04](04-moteur-de-reconciliation.md)). |

---

## 0. Lire l'état avant d'agir

Tous les appels sont sur l'API de contrôle. Tout appel exige un jeton valide. **Lire** (état, manifeste, configuration, découverte, graphe de dépendances, workflows) est ouvert à tout appelant authentifié. **Modifier** (déclarer, relancer, arrêter un service, publier une configuration ou un événement, conduire un workflow) est réservé aux **administrateurs** (`egen.security.admin-role`, valeur `egen-admin` par défaut) ; `metadata.team` est informatif et ne donne aucun droit (voir [13](13-api-et-contrats.md), [14](14-securite.md) et [24](24-revue-de-securite-externe.md)).

| Question | Appel | Ce que vous lisez |
|---|---|---|
| Où en est ce service ? | `GET /api/v1/services/{id}/status` | `phase`, la liste des `conditions`, `observedGeneration` |
| Quel manifeste est désiré ? | `GET /api/v1/services/{id}` | le manifeste enregistré, dont `deployment.adapter` |
| Qui dépend de qui ? | `GET /api/v1/dependencies/graph` | le graphe de dépendances de tout l'écosystème |
| Où en est cette exécution de workflow ? | `GET /api/v1/workflows/{id}/executions/{executionId}` | `status`, et un `stepResults` avec, par étape : `status`, `attempt`, `error` |

**Les métriques que le Kernel émet aujourd'hui** (relues dans le code) :

| Métrique | Ce qu'elle compte |
|---|---|
| `egen_services_total{phase}` | Un **compteur** : il augmente de 1 **à chaque enregistrement du statut**, donc à chaque cycle d'un service actif (environ toutes les 10 secondes pour un service `RUNNING`). Ce n'est **pas** le nombre de services actuellement dans cette phase, et son rythme ne dit rien du nombre de changements de phase. Pour `FAILED`, le statut n'est enregistré qu'une fois : le moteur ignore ensuite le service. |
| `egen_reconcile_duration_seconds{service}` | La durée de chaque cycle de réconciliation, par service. |
| `egen_reconcile_failures_total{service, reason}` | Les échecs d'adapter pendant un cycle ; `reason` est le nom de la classe de l'erreur (par exemple `DeploymentException`). |
| `egen_adapter_call_errors_total{adapter, operation}` et `egen_adapter_call_duration_seconds{adapter, operation}` | Les appels aux adapters, par adapter (`nomad`, `kubernetes`) et par opération. |
| `egen_dependency_unresolved_total{service, dependency}` | Les dépendances non résolues. |
| `egen_workflow_executions_total{workflow, status}` | Compté **une fois**, quand une exécution entre dans un statut terminal. |

**Alertes à poser** (conditions exprimées sans syntaxe de requête, car le nom exact côté exportateur dépend de la configuration d'OpenTelemetry) :

| Alerte | Condition |
|---|---|
| Service tombé en échec | toute augmentation de `egen_services_total` avec `phase = FAILED` |
| Échecs d'adapter répétés | `egen_reconcile_failures_total` qui augmente pendant plusieurs minutes pour un même `service` |
| Compensation en échec | toute augmentation de `egen_workflow_executions_total` avec `status = COMPENSATION_FAILED` |
| Adapter malade | `egen_adapter_call_errors_total` qui augmente pour un `adapter` |

---

## 1. Un service est en `FAILED`

### Ce qui s'est passé, dans l'ordre

Exemple : `news-service`, déclaré avec `adapter: kubernetes`.

1. À chaque cycle, le moteur appelle l'adapter (créer, observer, etc.). Une erreur d'adapter lève une exception.
2. Le moteur compte l'échec (`egen_reconcile_failures_total`) et replanifie un nouvel essai après **2, 4, 8 puis 16 secondes** (la durée double à chaque échec, plafonnée à 60 secondes).
3. Au **cinquième échec consécutif**, le moteur passe le service en `FAILED` et pose la Condition `DeploymentReady = False`, `reason = AdapterFailureThresholdExceeded`. Le `message` de cette Condition est le message de **la dernière erreur** de l'adapter.
4. Un cycle réussi remet le compteur à zéro : cinq échecs doivent être **consécutifs**.

**Ce qui ne doit pas mener à `FAILED`** : d'après [09](09-cycle-de-vie.md#sémantique-des-conditions-v1), une condition externe (secret absent, courtier de messages injoignable, dépendance non satisfaite) laisse le service à sa phase, avec une Condition `False` explicite ; seul l'échec répété de l'adapter mène à `FAILED`. Côté code, ce sont les erreurs levées pendant un cycle qui sont comptées ; ce chemin précis n'a pas de test dédié.

### Diagnostic

1. `GET /api/v1/services/news-service/status` : lisez la Condition `DeploymentReady`. Son `message` contient l'erreur de l'adapter.
2. Retrouvez le message dans le tableau ci-dessous.

**Adapter Kubernetes** (messages produits par `KubernetesDeploymentAdapter`, reproduits tels quels, sans accent) :

| Début du message | Cause probable | Que faire |
|---|---|---|
| `Kubernetes a rejete … : HTTP 401` | Le jeton du compte de service est invalide ou expiré. | Renouveler le jeton, le mettre dans `egen.kubernetes.token`, redémarrer le Kernel. |
| `Kubernetes a rejete … : HTTP 403` | Un droit manque au compte de service. | Comparer ses droits à la liste du document [07](07-ports-et-adapters.md#configuration) (Deployments, `deployments/scale`, ReplicaSets, pods). |
| `Kubernetes a rejete … : HTTP 404` sur un chemin contenant `/namespaces/` | Le namespace configuré n'existe généralement pas. | Créer le namespace ou corriger `egen.kubernetes.namespace`. |
| `Kubernetes a rejete … : HTTP 422` | Kubernetes refuse le Deployment demandé. | Lire le corps de l'erreur, joint au message. |
| `Appel Kubernetes impossible …` | Réseau, DNS ou certificat TLS. | Vérifier `egen.kubernetes.address` et `egen.kubernetes.ca-cert-path` depuis la machine du Kernel. |
| `DeploymentSpec.cpu invalide …` ou `… memory invalide …` | Le manifeste donne une quantité que Kubernetes n'accepte pas dans ce format. | Corriger `deployment.resources` (exemples valides : `500m`, `512Mi`). |
| `Le service "…" demande l'adapter "…", qui n'est pas activé dans ce Kernel` | Le Kernel a redémarré avec une liste `egen.deployment.adapters` qui n'inclut plus l'adapter du service. | Remettre l'adapter dans la liste. Il n'y a aucun repli sur un autre moteur ([07](07-ports-et-adapters.md#sélection-et-routage--un-moteur-par-service)). |

**Adapter Nomad.** Le message est celui de l'API Nomad. Vérifiez `egen.nomad.address` et `egen.nomad.token`. Le job Nomad d'un service porte l'identifiant du service.

3. Pour comprendre **quand** les échecs ont commencé : la métrique `egen_reconcile_failures_total{service="news-service"}`, ventilée par `reason`.

### Ce que vous pouvez faire

1. **Corrigez la cause** (tableau ci-dessus). Le moteur **ne réessaie pas** un service en `FAILED` : il l'ignore tant que sa phase est `FAILED`, `STOPPED` ou `REMOVED`. Corriger la cause ne relance donc rien tout seul.
2. **Relancez le service** : envoyez de nouveau `POST /api/v1/services` avec son manifeste (le même, ou corrigé) **sous le même identifiant**, et une **nouvelle** valeur d'`Idempotency-Key`. Avec une valeur déjà utilisée, l'API rejoue l'ancienne réponse sans rien exécuter. Le service repart de `DECLARED` avec ses Conditions effacées, puis suit le chemin normal jusqu'à `RUNNING` ([09](09-cycle-de-vie.md#relance-par-declare)). Suivez-le par `GET /api/v1/services/{id}/status`. Seul un administrateur peut le faire.
3. **Si vous préférez libérer les ressources** sans relancer : `POST /api/v1/services/news-service/actions/stop` (rôle administrateur). La machine à états autorise `FAILED → STOPPING → STOPPED`. Un service `STOPPED` se relance de la même façon, par un nouveau `Declare`.

**Si le service retombe en `FAILED` après la relance**, la cause n'est pas corrigée : relisez la Condition `DeploymentReady`. Il faut de nouveau cinq échecs consécutifs d'adapter pour qu'il y retombe.

**Ce qui n'existe pas.** `actions/restart` et `actions/rollback`, cités dans [13](13-api-et-contrats.md), ne sont pas livrés : la relance passe uniquement par `Declare`.

---

## 2. Une exécution de workflow est en `COMPENSATION_FAILED`

### Ce qui s'est passé, dans l'ordre

Exemple : le workflow `publish-article` réserve un créneau (`reserve-notification-slot`, compensation `release-slot`) puis publie (`publish`).

1. L'étape `publish` échoue définitivement. Elle n'est **jamais** compensée : son effet partiel est de la responsabilité du service qu'elle appelle.
2. Le moteur lance, dans l'**ordre inverse**, la compensation des étapes **déjà réussies** qui en déclarent une (`release-slot`).
3. Chaque compensation est tentée **une seule fois**. Si l'une échoue, les suivantes sont **quand même tentées** (meilleur effort).
4. Si au moins une compensation a échoué, l'exécution finit en `COMPENSATION_FAILED`. Sinon : `COMPENSATED`.

`COMPENSATION_FAILED` est un état **terminal** : aucune transition n'en sort ([11](11-moteur-de-workflow.md)). L'exécution n'est jamais relancée automatiquement.

### Diagnostic

1. `GET /api/v1/workflows/publish-article/executions/{executionId}`.
2. Dans `stepResults`, repérez les étapes dont le `status` est `COMPENSATION_FAILED` : leur champ `error` dit pourquoi la compensation a échoué. **Chaque étape de ce statut a laissé un effet non annulé.**
3. Repérez aussi les étapes en `FAILED` (l'étape qui a fait échouer l'exécution) : son effet partiel est à vérifier dans le service concerné.
4. `GET /api/v1/workflows/publish-article` pour relire la définition : une étape `SUCCEEDED` **sans** compensation déclarée n'est pas annulée par conception.
5. La raison d'un arrêt demandé par un opérateur (`terminate`) est dans les **journaux** du Kernel, pas dans l'exécution (elle n'est pas conservée).

### Ce que vous pouvez faire aujourd'hui

1. **Annulez l'effet à la main** dans le service concerné. Une compensation **doit** être idempotente : c'est une exigence imposée à l'auteur de la définition ([11](11-moteur-de-workflow.md#compensation--le-cœur-de-la-robustesse-saga)), que le Kernel ne vérifie pas. Relisez la définition du workflow avant de rejouer à la main l'appel que le moteur n'a pas pu faire.
2. **Ne tentez ni `resume` ni `terminate`** sur cette exécution : l'état est terminal, l'API répond `409`.
3. Si le traitement doit être refait : lancez une **nouvelle** exécution (`POST /api/v1/workflows/publish-article/executions`).
4. Une exécution `RUNNING` ou `COMPENSATING` est reprise automatiquement au redémarrage du Kernel ; une exécution `PAUSED` attend un `resume` explicite.

---

## 3. Divergence prolongée entre désiré et observé

**Ce que cela veut dire.** Le service devrait être `RUNNING` (c'est ce qu'a demandé le manifeste) mais ne l'est pas, ou plus, depuis plus longtemps que prévu. Le Kernel ne mesure pas cette durée aujourd'hui (le critère de fin « la divergence est mesurée en continu » de [19](19-feuille-de-route.md) n'est pas atteint) : il faut lire la phase.

**Le moteur réapplique l'état désiré en continu** : à chaque cycle il rappelle `create()` et ré-enregistre les instances (constaté en traçant un déploiement sans panne). Toute modification faite à la main directement dans Nomad ou Kubernetes (un `kubectl scale`, un changement d'image) est donc **annulée au cycle suivant**, environ 10 secondes plus tard. Pour changer un service, changez son manifeste.

**Rythmes du moteur** à connaître : un service convergé est ré-observé toutes les **10 secondes**, et toutes les ressources sont ré-examinées toutes les **5 minutes**, même sans changement ([04](04-moteur-de-reconciliation.md)). Une panne est donc vue en quelques secondes ; une phase qui reste bloquée plus longtemps n'est pas un retard d'observation.

### Diagnostic, par phase

`GET /api/v1/services/{id}/status`, puis :

| Phase vue | Condition à lire | Ce que cela veut dire | Que faire |
|---|---|---|---|
| `DECLARED` ou `REGISTERED` | `ConfigResolved = False`, `reason` : `SecretUnresolved`, `RequiredKeyMissing`, `InvalidConfigurationValue`, `ConfigurationUnavailable`, `PortNotConfigured` | La configuration ou un secret manque. Le service **attend**, ce n'est pas un échec. | Fournir la clé ou le secret ; la résolution est retentée à **chaque cycle**, sans toucher au manifeste. |
| `CONFIGURED` | `DependenciesSatisfied = False` | Une dépendance obligatoire n'est pas `RUNNING` ou sa version ne convient pas ([10](10-gestion-des-dependances.md)). | Voir l'état de la dépendance (`GET …/status` sur elle) et `GET /api/v1/dependencies/graph`. |
| `CONFIGURED` | `MessagingBindingsReady = False` (`MessagingUnavailable`, `PortNotConfigured`) | Les flux d'événements ne sont pas garantis : le service n'est **pas déployé** tant qu'ils ne le sont pas. | Rétablir le courtier NATS ; les flux sont re-garantis à chaque cycle. |
| `DEPLOYING` depuis longtemps | `DeploymentReady` | L'adapter a créé le déploiement mais les instances ne deviennent pas saines. | Voir ci-dessous « côté moteur ». |
| `DEGRADED` | `DeploymentReady` | Une partie seulement des instances est saine. | Voir ci-dessous « côté moteur ». |

**`DEGRADED` ne devient jamais `FAILED` tout seul.** Le document 09 prévoit « un délai configurable » ; aucun délai n'est appliqué (point ouvert 4 de [19](19-feuille-de-route.md)). Un service peut donc rester `DEGRADED` indéfiniment : c'est à l'alerte et à l'opérateur d'agir. Il repasse en `RUNNING` tout seul dès que les instances redeviennent saines.

### Côté moteur d'exécution

**Kubernetes.** Les pods d'un service portent l'étiquette `egen.civitas.africa/service-id`, et le Deployment porte le nom du service :

```
kubectl -n <namespace> get pods -l egen.civitas.africa/service-id=news-service
kubectl -n <namespace> describe pod <pod>      # image non téléchargée, sonde en échec, mémoire…
kubectl -n <namespace> rollout status deployment/news-service
```

**Nomad.**

```
nomad job status news-service
```

## Démarrer le Kernel en production : variables obligatoires

*À quoi sert cette section* : savoir pourquoi un Kernel de production ne démarre pas. *Ce qu'elle ne couvre pas* : la configuration complète d'un environnement.

**Observé le 10 novembre 2026** (jar produit par `mvn verify`, lancé sans infrastructure) : le Kernel **ne démarre pas** si l'une de ces trois variables d'environnement manque ou est vide. Quarkus l'arrête avant le Kernel, avec le message `Failed to load config value of type class java.lang.String for: <propriété>` :

| Variable d'environnement | Propriété nommée dans le message | Rôle |
|---|---|---|
| `EGEN_REGISTRY_DB_PASSWORD` | `quarkus.datasource.password` | mot de passe de la base PostgreSQL du Registry |
| `EGEN_OIDC_ISSUER_URL` | `mp.jwt.verify.issuer` | émetteur des jetons OIDC |
| `EGEN_OIDC_JWKS_URL` | `mp.jwt.verify.publickey.location` | adresse des clés publiques de l'émetteur |

De plus, si `EGEN_REGISTRY_DB_PASSWORD` vaut `egen` (la valeur de développement), le Kernel refuse de démarrer avec le message « Démarrage refusé en production : le mot de passe de la base du Registry est la valeur par défaut du développement ». Le code de sortie est `1` dans tous ces cas.

*Non éprouvé* : le comportement des autres variables de production (`NOMAD_TOKEN`, `CONSUL_HTTP_TOKEN`, `VAULT_TOKEN`, `EGEN_KUBERNETES_TOKEN`, les adresses de Nomad, Consul, NATS et Vault) n'a pas encore été observé sur un Kernel de production complet.

*Observé le 10 novembre 2026* (Kernel de production avec la seule base PostgreSQL, V2) : sans collecteur OpenTelemetry à l'adresse `http://localhost:4317`, le Kernel démarre normalement (profil `prod`, environ 1,4 seconde) et reste en vie. Environ une minute après le démarrage il écrit une erreur de niveau `SEVERE`, « Failed to export metrics », avec une trace complète : c'est l'exportateur de métriques qui échoue à se connecter. Le Kernel n'est pas arrêté. Le service est décrit dans `docker-compose.yml` (`otel-collector`).
