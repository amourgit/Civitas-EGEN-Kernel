# 07 — Architecture Ports & Adapters — spécification détaillée

Pour chaque domaine (Deployment, Discovery, Messaging, Configuration,
Secrets, Observability), ce document donne : le **port** (interface,
langage-agnostique — pseudo-code de type Java/Kotlin pour lisibilité), la
**table de mapping exacte vers l'API du moteur choisi**, un exemple
d'implémentation, et les **pièges connus**.

## Deployment Port

### Le port (interface secondaire)

```java
public interface DeploymentPort {
    DeploymentHandle create(DeploymentSpec spec);          // idempotent : upsert si existe déjà
    void update(ServiceId id, DeploymentSpec newSpec);     // rolling update selon updateStrategy
    void scale(ServiceId id, int replicas);
    void restart(ServiceId id);
    void stop(ServiceId id);
    void remove(ServiceId id);
    DeploymentObservation getStatus(ServiceId id);         // lecture — jamais mutatif
    List<AllocationInfo> listAllocations(ServiceId id);
    void rollback(ServiceId id, ServiceVersion targetVersion);
}
```

`DeploymentObservation` est un DTO **neutre** (ne contient aucun type
Nomad) : `{ desiredCount, runningCount, healthyCount, failedCount,
lastUpdateTime, allocations[] }`.

### Mapping vers l'API HTTP Nomad (`/v1/*`, port par défaut `4646`)

| Opération du port | Endpoint Nomad | Détails d'implémentation |
|---|---|---|
| `create(spec)` | `PUT /v1/jobs` (ou `POST /v1/job/:job_id`) | Traduire `DeploymentSpec` → job HCL/JSON Nomad. Un `ServiceManifest` EGEN → un `Job` Nomad avec un `TaskGroup` (mapping 1:1 recommandé pour la V1). |
| `update(spec)` | `PUT /v1/jobs` avec le job existant modifié | Nomad gère nativement le rolling update via le bloc `update` du job (`max_parallel`, `health_check`, `auto_revert`) — **ne pas réimplémenter cette logique dans EGEN**, se contenter de la configurer depuis `updateStrategy`. |
| `scale(replicas)` | `POST /v1/job/:job_id/scale` | Endpoint dédié au scaling, distinct d'une mise à jour complète du job — préférer ce endpoint pour les opérations d'autoscaling fréquentes. |
| `restart(id)` | `POST /v1/job/:job_id/evaluate` (avec `ForceReschedule`) | Force une nouvelle évaluation du scheduler. |
| `stop(id)` | `DELETE /v1/job/:job_id` | Nomad conserve par défaut l'historique du job (purge=false). Recommandation : **ne pas purger**, la suppression logique (`remove`) gère la purge. |
| `remove(id)` | `DELETE /v1/job/:job_id?purge=true` | Purge complète, à utiliser uniquement depuis la phase `REMOVING` du cycle de vie (voir [09](09-cycle-de-vie.md)). |
| `getStatus(id)` | `GET /v1/job/:job_id` + `GET /v1/job/:job_id/allocations` | Le statut agrégé se construit en croisant le job (desired count) et les allocations (statut réel de chaque tâche). |
| `listAllocations(id)` | `GET /v1/job/:job_id/allocations?all=true` | Pagination via `X-Nomad-NextToken` / `per_page` pour les jobs à forte volumétrie. |
| `rollback(id, version)` | `POST /v1/job/:job_id/revert` | Nomad conserve un historique de versions de job — s'appuyer dessus plutôt que ré-soumettre un ancien manifeste depuis zéro. |

**Watchers Nomad** : Nomad expose des *blocking queries* (paramètre
`index`) sur la plupart des endpoints `GET` — EGEN les utilise pour un
mode « watch » efficace en long-polling plutôt qu'un polling bête à
intervalle fixe, ce qui réduit la charge sur le cluster Nomad et la
latence de détection de changement.

### Pièges connus

- **Ne jamais construire l'objet Job Nomad en string-concat HTML/JSON** :
  utiliser le SDK officiel, ou construire un DTO fidèle au schéma JSON
  Nomad et sérialiser proprement. Un champ mal formé (ex. `resources.cpu`
  en string au lieu d'un entier de MHz) échoue silencieusement ou produit
  un comportement de scheduling inattendu.
- **`namespace` Nomad** : si le cluster Nomad utilise des namespaces
  (multi-tenant), le `DeploymentPort` doit propager le `TargetEnvironment`
  d'EGEN vers le bon namespace Nomad — ne jamais coder `default` en dur.
- **ACL Nomad** : le token utilisé par l'adapter doit avoir une politique
  ACL scoped (au minimum `submit-job`, `read-job`, `dispatch-job` sur le
  namespace concerné) — ne jamais utiliser le token root de bootstrap en
  dehors du bootstrap initial.

### Limites constatées de l'adapter de déploiement

- L'adapter Nomad **ne gère pas** les namespaces Nomad : `TargetEnvironment` est un nom d'environnement, pas une frontière de tenant. L'isolation par tenant n'est **pas** fournie par le Kernel ; elle est assurée par les données ou par des instances dédiées ([22](22-frontiere-avec-la-plateforme.md#229--limites-actuelles-du-kernel), déclencheur A-X2 dans [19](19-feuille-de-route.md)).
- `runtime.type` n'est pas différencié (déclencheur A-X1).

### Kubernetes Adapter — second adapter de déploiement

**À quoi sert cette section.** Elle fixe le contrat de `KubernetesDeploymentAdapter`, le second adapter du `DeploymentPort` (Phase 6, [19](19-feuille-de-route.md)). Elle est normative : le code s'y conforme, pas l'inverse.

**Ce qu'elle ne couvre pas.** Les namespaces Kubernetes par tenant et la découverte par `Service` Kubernetes. Le choix du moteur par service est décrit dans « Sélection et routage ».

**Pourquoi Kubernetes.** Nomad et Kubernetes sont deux moteurs qui placent, redémarrent et mettent à jour des conteneurs. Kubernetes sait nativement ajuster le nombre de réplicas, faire un rolling update (remplacer les instances une à une), garder l'historique des versions et lister les instances. L'adapter n'a donc **aucune logique de planification à écrire** : il traduit (garde-fou n°3, [02](02-principes-fondamentaux.md)). Une organisation qui exploite déjà un cluster Kubernetes peut ainsi utiliser EGEN sans installer Nomad.

**Vocabulaire** (définis à leur première apparition) :

| Terme | Définition en une phrase |
|---|---|
| Deployment | Objet Kubernetes (`apps/v1`) qui décrit « N copies de ce conteneur, mises à jour progressivement ». |
| Pod | Une instance en cours d'exécution d'un conteneur ; c'est l'équivalent d'une *allocation* Nomad. |
| ReplicaSet | Objet créé par un Deployment pour chaque version du modèle de pod ; l'ensemble forme l'historique. |
| Namespace | Espace de noms Kubernetes dans lequel vivent les objets (voir `egen.kubernetes.namespace`). |
| Server-side apply | Mode d'écriture où l'on envoie l'état voulu complet et où Kubernetes crée l'objet s'il manque ou le met à jour sinon : c'est un *upsert* natif. |

**Exemple de bout en bout.** Le service `news-service` (image `registry.civitas.africa/news-service:2.4.0`, 500m de CPU, 512Mi de mémoire, 2 réplicas minimum) est déclaré via `POST /api/v1/services`. (1) Le moteur de réconciliation appelle `create(news-service, spec)`. (2) L'adapter envoie à Kubernetes **un** Deployment nommé `news-service`. (3) Kubernetes crée un ReplicaSet puis deux pods. (4) Au cycle suivant, `getStatus` compte les pods : 2 voulus, 2 en cours, 2 prêts → le service passe `RUNNING`. (5) `listAllocations` renvoie l'adresse et le port de chaque pod ; le moteur les enregistre dans Discovery (Consul). (6) Pour passer à `2.5.0`, `update` renvoie le même Deployment avec la nouvelle image : Kubernetes remplace les pods un par un. (7) `rollback(news-service, 2.4.0)` retrouve le ReplicaSet de `2.4.0` et remet son modèle de pod dans le Deployment.

#### Sélection et routage : un moteur par service

**Principe.** Un même Kernel peut exécuter **plusieurs** Deployment Adapters en parallèle. Chaque service choisit le sien dans son manifeste, par `deployment.adapter` ([06](06-service-manifest.md)). Le moteur de réconciliation ne change pas : il parle toujours à **un seul** `DeploymentPort`.

**Propriété** `egen.deployment.adapters` : liste, séparée par des virgules, des adapters **activés** dans ce Kernel. Valeurs possibles : `nomad`, `kubernetes`. Défaut : `nomad`. Une valeur inconnue, un doublon ou une liste vide fait **échouer le démarrage**, avec un message qui liste les valeurs permises. Un adapter non activé n'est jamais construit (pas de connexion, pas de configuration exigée).

**Qui fait quoi, dans l'ordre** (exemple : le Kernel active `nomad,kubernetes` ; `news-service` déclare `adapter: kubernetes`) :

1. **Declare.** `DeployServiceUseCase` lit `deployment.adapter` du manifeste. Si cet adapter n'est pas activé, la déclaration est refusée (HTTP 400, message : « adapter "docker" non activé ; adapters activés : nomad, kubernetes »). C'est l'étape 3 de la validation décrite en [06](06-service-manifest.md#validation-du-manifeste-obligatoire-avant-tout-declare).
2. **Adapter immuable.** Si `news-service` existe déjà avec un autre adapter, la déclaration est refusée (HTTP 409). Sans cette règle, les instances de l'ancien moteur resteraient en vie sans personne pour les piloter. Pour changer de moteur : déclarer un **nouveau** service (nouvel identifiant), puis arrêter l'ancien.
3. **Réconciliation.** Le moteur appelle `DeploymentPort`. Cet objet est un `RoutingDeploymentPort` (dans `egen-application`) : pour `create` et `update`, il lit l'adapter dans la `DeploymentSpec` reçue ; pour toutes les autres opérations, il lit l'adapter du manifeste courant du service dans le Registry. Il délègue ensuite à l'adapter correspondant. Il ne contient **aucune** logique de déploiement.
4. **Observabilité.** Chaque adapter est enveloppé séparément dans `ObservedDeploymentPort` avec son propre nom : les métriques distinguent `nomad` de `kubernetes`.

**Cas limites** (comportement fixé, pas de repli silencieux) :

| Situation | Résultat |
|---|---|
| `getStatus` ou `listAllocations` sur un service absent du Registry | `DeploymentObservation.empty()` / liste vide |
| `scale`, `restart`, `stop`, `remove`, `rollback` sur un service absent du Registry | `DeploymentException` (service inconnu) |
| Service enregistré avec un adapter qui n'est plus activé (le Kernel a redémarré avec une autre liste) | `DeploymentException` qui nomme l'adapter ; aucun repli sur un autre moteur |

**Où vit le code.** `DeploymentAdapterBeans` (`egen-bootstrap`) construit les adapters activés et le routeur ; c'est le seul code qui connaît la propriété. `egen-domain` ne change pas. `egen-application` reçoit `RoutingDeploymentPort`, `EnabledDeploymentAdapters` (la liste des adapters activés, lue par le use case de déclaration) et deux exceptions.

#### Configuration

| Propriété | Défaut | Rôle |
|---|---|---|
| `egen.kubernetes.address` | `https://kubernetes.default.svc` | URL de l'API Kubernetes |
| `egen.kubernetes.namespace` | `egen` | Namespace des Deployments gérés (jamais `default` codé en dur) |
| `egen.kubernetes.token` | aucun | Jeton porteur (*bearer token*) d'un compte de service dont les droits sont limités à ce namespace |
| `egen.kubernetes.ca-cert-path` | aucun | Fichier PEM de l'autorité de certification de l'API ; sans lui, le magasin de confiance du JDK est utilisé. La vérification TLS n'est **jamais** désactivable. |
| `egen.kubernetes.container-port` | `8080` | Port que le conteneur écoute (voir « Réseau ») |

Droits minimaux du compte de service, dans le namespace : `deployments` (get, create, patch, delete), `deployments/scale` (get, patch), `replicasets` (list), `pods` (list).

#### Mapping des opérations

Chemins relatifs à `{address}`, `{ns}` = namespace configuré, `{id}` = identifiant du service.

| Opération du port | Appel Kubernetes | Détails |
|---|---|---|
| `create(id, spec)` | `PATCH /apis/apps/v1/namespaces/{ns}/deployments/{id}?fieldManager=egen-kernel&force=true`, `Content-Type: application/apply-patch+yaml` | Server-side apply : idempotent par construction (crée ou met à jour). Un `ServiceManifest` → **un** Deployment, **un** conteneur, nommés `{id}`. |
| `update(id, spec)` | identique à `create` | Le rolling update est fait par Kubernetes ; l'adapter ne le réimplémente pas. |
| `scale(id, n)` | `PATCH …/deployments/{id}/scale`, `application/merge-patch+json`, corps `{"spec":{"replicas":n}}` | Sous-ressource dédiée au changement de réplicas. |
| `restart(id)` | `PATCH …/deployments/{id}`, `application/merge-patch+json`, annotation `kubectl.kubernetes.io/restartedAt` = l'heure courante dans `spec.template.metadata.annotations` | Changer le modèle de pod provoque un rolling update sans changer l'image. *À vérifier* dans la documentation officielle de la version cible (c'est la convention de `kubectl rollout restart`). |
| `stop(id)` | `scale(id, 0)` | Kubernetes n'a pas d'« arrêt qui garde l'historique » : le Deployment reste, à 0 réplica (l'historique est conservé, comme `purge=false` chez Nomad). Un Deployment absent (404) n'est pas une erreur. |
| `remove(id)` | `DELETE …/deployments/{id}` avec `propagationPolicy=Background` | Les ReplicaSets et pods sont supprimés ensuite par Kubernetes. 404 = déjà supprimé, pas une erreur. |
| `getStatus(id)` | `GET …/deployments/{id}` + `GET /api/v1/namespaces/{ns}/pods?labelSelector=…` | Voir « Comptage ». Deployment absent (404) → `DeploymentObservation.empty()`. |
| `listAllocations(id)` | `GET /api/v1/namespaces/{ns}/pods?labelSelector=…` | Un `AllocationInfo` par pod. |
| `rollback(id, v)` | `GET …/replicasets?labelSelector=…` puis `PATCH …/deployments/{id}`, `application/json-patch+json`, remplacement de `/spec/template` | Voir « Retour arrière ». |

#### Objets créés

- Étiquettes (*labels*) de chaque pod et du sélecteur : `app.kubernetes.io/managed-by: egen-kernel` et `egen.civitas.africa/service-id: {id}`. Elles ne sont **pas** dans l'espace réservé `platform.civitas.africa/*` ([06](06-service-manifest.md#labels-réservés-posés-par-la-plateforme)) : cet espace appartient à la Plateforme.
- Réplicas : `spec.replicas.min` (même règle que l'adapter Nomad).
- Ressources : `cpu` et `memory` du `DeploymentSpec` sont déjà des quantités Kubernetes (`500m`, `512Mi`) et sont transmises **telles quelles**, en `requests` **et** en `limits`. Le CPU accepte un entier, avec ou sans le suffixe `m` (`2`, `500m`) ; la mémoire accepte un entier, avec ou sans le suffixe `Ki`, `Mi`, `Gi` ou `Ti` (`512Mi`). Toute autre forme (par exemple `0.5` ou `512MB`) est refusée par `DeploymentException` avant tout appel réseau.
- Aucun objet `Service` Kubernetes n'est créé : la découverte appartient à Discovery ([07](#discovery-port)), pas à Kubernetes.

#### Réseau

Le conteneur déclare un port nommé `http` égal à `egen.kubernetes.container-port`, et reçoit la variable d'environnement `EGEN_PORT_http` portant cette valeur : c'est l'équivalent de `NOMAD_PORT_http` chez Nomad. Comme chez Nomad, c'est une réserve provisoire en attendant `network.endpoints` dans le manifeste ([19](19-feuille-de-route.md)). L'adresse d'une allocation est l'IP du pod. **Limite** : cette IP n'est joignable que depuis le réseau du cluster ; un Discovery (Consul) situé hors du cluster ne peut pas la joindre sans routage adapté.

#### Comptage et statuts

| Champ de `DeploymentObservation` | Valeur |
|---|---|
| `desiredCount` | `spec.replicas` du Deployment |
| `runningCount` | pods en phase `Running` et sans `deletionTimestamp` |
| `healthyCount` | pods ci-dessus dont la condition `Ready` vaut `True` |
| `failedCount` | pods en phase `Failed` ou `Unknown` |

Contrairement à l'adapter Nomad (qui assimile « sain » à « en cours »), Kubernetes fournit l'état « prêt ». Sans sonde de disponibilité (*readiness probe*) déclarée, « prêt » signifie « tous les conteneurs ont démarré ».

Statut d'une allocation : `Running` → `running`, `Pending` → `pending`, `Succeeded` → `complete`, `Failed` → `failed`, `Unknown` → `lost`, autre → `unknown`. Seul `running` avec une IP est enregistré dans Discovery (voir le moteur de réconciliation).

#### Retour arrière (`rollback`)

Kubernetes n'expose **pas** de point d'accès de retour arrière : `kubectl rollout undo` est exécuté côté client (il retrouve le ReplicaSet de la révision voulue, retire l'étiquette `pod-template-hash` de son modèle, et remplace le modèle du Deployment — source : `pkg/kubectl/rollback.go`, version 1.15 ; *à recouper* avec la version courante). L'adapter fait la même chose :

1. lister les ReplicaSets du sélecteur, ne garder que ceux dont le propriétaire est le Deployment `{id}` ;
2. garder ceux dont l'image du conteneur finit par `:{version}` (même règle que l'adapter Nomad) ; en cas de plusieurs, prendre la plus haute révision (annotation `deployment.kubernetes.io/revision`) ;
3. aucun trouvé → `DeploymentException` (« aucune version … ne correspond ») ;
4. remplacer `/spec/template` du Deployment par ce modèle, **sans** l'étiquette `pod-template-hash`.

Le nombre de versions retenues est celui de `revisionHistoryLimit` du Deployment (10 par défaut dans `apps/v1`, *à vérifier*) : un retour vers une version plus ancienne échoue.

#### Faits externes et leur état de vérification

| Fait | État |
|---|---|
| Server-side apply : `PATCH`, `application/apply-patch+yaml`, `fieldManager` obligatoire, `force` pour reprendre les champs en conflit, stable depuis la v1.22 | Vérifié (kubernetes.io, « Server-Side Apply ») |
| Sous-ressource `deployments/{name}/scale` (GET, PATCH, PUT) | Vérifié (référence de l'API `autoscaling/v1`) |
| Retour arrière réalisé côté client | Vérifié sur `rollback.go` v1.15 ; à recouper sur la version cible |
| Annotation `restartedAt`, valeur par défaut de `revisionHistoryLimit` | **À vérifier** |

#### Tests

Niveau 1 : conversion des quantités, construction du Deployment (aucun réseau). Niveau 2 : comptage et statuts avec un faux serveur HTTP. Niveau 3 : `KubernetesDeploymentAdapterIT` contre un vrai cluster Kubernetes (k3s dans Testcontainers), avec de vrais pods. Il s'authentifie par jeton d'un compte de service qui porte **exactement** les droits listés plus haut (donc il prouve qu'ils suffisent), puis suit la vie d'un service : création, convergence, idempotence, `scale`, mise à jour, `rollback`, `restart`, `stop`, `remove`. Il vérifie aussi directement dans le cluster (par `kubectl`) ce que Kubernetes a enregistré : propriétaire `egen-kernel`, image, annotation `restartedAt`. Prérequis : Docker autorisant un conteneur privilégié, et accès à Docker Hub pour `nginx:1.26.0` et `nginx:1.27.0`. Voir [17](17-strategie-de-tests.md).

## Discovery Port

### Le port

```java
public interface DiscoveryPort {
    void register(ServiceRegistration registration);   // idempotent
    void deregister(ServiceId id, String instanceId);
    ResolvedInstances resolve(ServiceId id);            // ne retourne QUE les instances saines
    HealthStatus health(ServiceId id, String instanceId);
    void registerHealthCheck(ServiceId id, HealthCheckSpec spec);
}
```

### Mapping vers l'API HTTP Consul (`/v1/*`, port par défaut `8500`)

| Opération du port | Endpoint Consul | Détails |
|---|---|---|
| `register(reg)` | `PUT /v1/agent/service/register` | Idempotent par construction côté Consul : un `PUT` avec le même `ID` remplace l'enregistrement. Inclure directement le bloc `Check` (HTTP/TCP/gRPC/TTL) dans le payload d'enregistrement plutôt que deux appels séparés. |
| `deregister(id)` | `PUT /v1/agent/service/deregister/:service_id` | À appeler explicitement lors d'un arrêt gracieux (phase `STOPPING`, voir [09.3](09-cycle-de-vie.md#arret-gracieux)) — ne pas compter uniquement sur l'échec des health checks. |
| `resolve(id)` | `GET /v1/health/service/:service?passing=true` | Le paramètre `passing=true` est **la** clé : il filtre déjà côté Consul les instances non saines. |
| `health(id, instanceId)` | `GET /v1/health/checks/:service` ou lecture ciblée dans la réponse `health/service` | Peut aussi être dérivé directement de la réponse de `resolve()`, sans appel séparé. |
| `registerHealthCheck` | Inclus dans `PUT /v1/agent/service/register` (bloc `Check`/`Checks`) | Consul supporte plusieurs checks simultanés par service (ex. HTTP + TTL applicatif) — mapper `HealthSpec.http` et `HealthSpec.readiness` du manifeste vers deux `Check` distincts. |
| Watch catalogue | `GET /v1/catalog/services` avec **blocking query** (`index=<X>`) | Préférer le long-polling par blocking query pour alimenter la boucle de réconciliation plutôt qu'un polling naïf. |

### Modèle DNS complémentaire

Consul expose aussi une interface DNS (`<service>.service.consul`). EGEN
ne s'appuie **pas** dessus pour sa propre logique de résolution interne
(préférer l'API HTTP, plus riche en métadonnées et en filtres), mais
documente aux équipes de services qu'elles peuvent utiliser cette DNS pour
des cas simples sans passer par le SDK EGEN.

### Pièges connus

- **Le TTL des health checks HTTP par défaut est généralement de 10s**
  avec 2 échecs avant retrait — ces valeurs sont **configurables par
  service** via `health.http.interval`/`failuresBeforeUnhealthy` du
  manifeste, jamais figées globalement.
- **Consistency mode** : pour `resolve()`, Consul propose `default` (peut
  lire une réplique légèrement en retard) vs `consistent` (lecture leader,
  plus lent). Pour la résolution de service en hot path applicatif,
  `default` est recommandé.
- **Multi-datacenter** : anticiper `dc` comme paramètre explicite du
  `DiscoveryPort` dès la V1, même si un seul datacenter est utilisé au
  départ — c'est un changement d'interface coûteux à faire après coup.

## Messaging Port

### Le port

```java
public interface MessagingPort {
    void publish(TechnicalEvent event, String subjectOrTopic);
    Subscription subscribe(String subjectOrTopic, String consumerGroup, EventHandler handler);
    void unsubscribe(Subscription subscription);
    BindingStatus getBindingStatus(ServiceId id);
    void createTopicOrStream(TopicSpec spec);          // idempotent — no-op si déjà existant
}
```

### L'enveloppe d'événement : CloudEvents (CNCF), pas un format maison

Le domaine `TechnicalEvent` (voir [05](05-modele-de-domaine.md)) se
sérialise en [**CloudEvents**](https://cloudevents.io) — spécification
CNCF **graduée** (donc stable), déjà adoptée nativement par Dapr. Format
JSON minimal :

```json
{
  "specversion": "1.0",
  "id": "8f3a6e2e-...-uuid",
  "source": "/news-service",
  "type": "africa.civitas.news.article.created.v1",
  "time": "2026-09-16T10:15:00Z",
  "datacontenttype": "application/json",
  "egen_correlation_id": "corr-4471",
  "egen_causation_id": "corr-4470",
  "data": { "articleId": "art-123", "authorId": "usr-77" }
}
```

- `type`, `source`, `id`, `specversion`, `time` : attributs CloudEvents
  standards.
- `egen_correlation_id` / `egen_causation_id` : extensions CloudEvents
  propres à EGEN, utilisées pour la traçabilité de bout en bout (corrélées
  aux traces OpenTelemetry, voir [15](15-observabilite.md)) et pour le
  moteur de Workflow ([11](11-moteur-de-workflow.md)).
- `data` : **opaque pour EGEN** — jamais désérialisé ni interprété par le
  Kernel, uniquement transporté.

### Kafka vs NATS JetStream — quand utiliser lequel

Il n'y a pas de vainqueur universel : la décision dépend du profil de
charge. Le `MessagingPort` reste donc agnostique et permet de choisir
l'implémentation **par environnement**, voire par type d'événement.

| Critère | Kafka | NATS JetStream |
|---|---|---|
| Débit très élevé (> ~500k msg/s), rétention longue, rejouabilité pour analytics/CDC | ✅ Recommandé | Possible mais pas son terrain de prédilection |
| Empreinte opérationnelle réduite, équipe petite | Plus lourd à opérer | ✅ Recommandé |
| Indépendance publisher/consumer forte (consumer groups indépendants) | ✅ Plus mature (partitions + consumer groups natifs) | Possible, avec un peu plus de discipline sur les sujets |
| Request/reply à basse latence en plus du pub/sub | Pas son usage principal | ✅ NATS supporte nativement request-reply |
| Écosystème (Kafka Connect, Schema Registry, Streams/Flink) | ✅ Riche | Plus limité |

**Décision retenue pour la V1** : **NATS JetStream** comme implémentation
par défaut (empreinte opérationnelle plus légère), tout en s'assurant —
via le `MessagingPort` — qu'un `KafkaMessagingAdapter` peut être ajouté
plus tard pour les flux à très haut débit ou à besoin d'écosystème
analytique, sans aucune modification du domaine ni des services qui
utilisent `MessagingPort`.

### Mapping vers l'API — NATS JetStream

| Opération du port | Primitive NATS JetStream | Détails |
|---|---|---|
| `createTopicOrStream(spec)` | `nats stream add` / API JetStream Manager `AddStream` | Un `type` d'événement EGEN (ou une famille) → un `Stream` avec un pattern de sujet hiérarchique (`news.article.>`). Pour le multi-tenant, filtrer par sous-sujet (`orders.<tenant>.*`) plutôt que créer un stream par tenant. |
| `publish(event, subject)` | `js.Publish(subject, data)` | Utiliser la publication **avec accusé de réception synchrone** pour garantir la persistance avant de considérer l'événement « publié » côté EGEN. |
| `subscribe(subject, group, handler)` | Consumer JetStream *pull* ou *push*, avec `DurableName = group` | Un consumer *durable* nommé par `consumerGroup` garantit la reprise de position après redémarrage — équivalent au `group.id` Kafka. |
| `getBindingStatus` | `ConsumerInfo` (`NumPending`, `NumAckPending`, `NumRedelivered`) | Alimente `MessagingObservation` du modèle de domaine — permet de détecter un consommateur bloqué. |

### Mapping vers l'API — Kafka

| Opération du port | Primitive Kafka | Détails |
|---|---|---|
| `createTopicOrStream(spec)` | Admin API `createTopics` | Idempotent : ignorer `TopicExistsException` en cas de course entre deux instances du Kernel. |
| `publish(event, topic)` | Producer Kafka avec **producteur idempotent activé** (`enable.idempotence=true`) | Évite les doublons en cas de retry réseau — condition nécessaire à la propriété d'idempotence globale d'EGEN. |
| `subscribe(topic, group, handler)` | Consumer Kafka avec `group.id = consumerGroup` | Kafka gère nativement le rebalance et la répartition des partitions. |
| `getBindingStatus` | Consumer group lag via Admin API (`listConsumerGroupOffsets` vs `endOffsets`) | Le lag est **la** métrique clé à exposer dans `MessagingObservation`. |

### Dead-letter, retry, ordre

Le manifeste (`events.consumes[].deadLetter`) définit une politique
**indépendante du broker choisi** : nombre de tentatives, stratégie de
backoff, sujet/topic de dead-letter (`<original>.DLQ`). C'est à l'adapter
de traduire cette politique dans les primitives natives (redelivery NATS,
ou topic dédié + consumer applicatif pour Kafka, qui n'a pas de DLQ native
intégrée au broker).

## Configuration Port

```java
public interface ConfigurationPort {
    ResolvedConfig resolve(ServiceId id, Environment env);   // fusionne défauts + overrides d'env
    void publish(ServiceId id, Environment env, ConfigSet values);
    Subscription watch(ServiceId id, Environment env, ConfigChangeHandler handler);
    ConfigVersion currentVersion(ServiceId id, Environment env);
}
```

- EGEN reste **générique** : il transporte des paires clé/valeur typées
  (`ConfigSpec` du manifeste définit *quelles clés* sont attendues et leur
  type), jamais leur signification métier.
- Implémentation V1 : un store clé/valeur simple (Consul KV, déjà présent
  dans la stack, plutôt qu'introduire un système supplémentaire).
  L'abstraction `ConfigurationPort` permet de migrer plus tard vers un
  outil dédié (ex. Vault pour le couplage secrets/config, ou etcd) sans
  impact sur le domaine.
- Le `watch()` permet un rechargement à chaud côté service (le service
  s'abonne, via son SDK client léger ou son sidecar, aux changements de
  configuration sans redémarrage) — fonctionnalité optionnelle en V1, mais
  l'interface l'anticipe dès le départ.

**Exposition par l'API.** `ConfigurationPort.publish` est exposé aux clients par `PUT /api/v1/services/{id}/configuration` (évolution A3, contrat dans [13](13-api-et-contrats.md#publication-de-configuration--put-apiv1servicesidconfiguration)). La fusion de configuration par organisation, cellule ou utilisateur n'est **jamais** faite ici : elle appartient à `config-service`, service de la Plateforme (voir [22](22-frontiere-avec-la-plateforme.md#225--lapi-de-publication-de-configuration-a3)).

## Secrets Port

```java
public interface SecretsPort {
    SecretRef resolve(SecretReference reference);   // retourne une référence à monter, JAMAIS la valeur en clair dans les logs/API
    void rotate(SecretReference reference);
}
```

- **Séparation stricte de `ConfigurationPort`** : un secret n'est jamais
  retourné par l'API de contrôle EGEN, jamais loggé, jamais mis en cache
  par le Kernel au-delà de la durée d'un appel.
- Implémentation : **délégation à HashiCorp Vault** (cohérence
  d'écosystème avec Nomad/Consul). L'adapter EGEN résout une référence
  (`secret/data/news-service/db`) vers l'information nécessaire à
  l'injection (ex. un chemin de fichier monté par Nomad via
  l'intégration native Nomad-Vault, ou un jeton de courte durée) —
  **EGEN ne stocke jamais le secret lui-même**.

## Observability Port

```java
public interface ObservabilityPort {
    void recordEvent(ReconciliationEvent event);
    void recordMetric(MetricSample sample);
    Span startSpan(String operationName, TraceContext parent);
    void reportCondition(ServiceId id, Condition condition);
}
```

- Implémentation : **OpenTelemetry** comme standard de génération
  (traces/metrics/logs). Les conventions sémantiques standard
  (`service.name`, `service.version`, `service.namespace`,
  `deployment.environment`) sont respectées scrupuleusement pour que les
  données EGEN s'agrègent proprement avec la télémétrie émise par les
  services eux-mêmes.
- Le Kernel propage le contexte de trace (`traceparent` W3C) à travers
  toute la chaîne Declare → Resolve → Compose → Delegate, pour permettre
  de suivre, dans un outil comme Jaeger ou Grafana Tempo, le chemin
  complet d'une opération de déploiement à travers Nomad, Consul, et
  jusqu'au premier appel applicatif du service déployé.
- Le backend de visualisation (Prometheus/Grafana pour les métriques,
  Jaeger/Tempo pour les traces) est un choix d'infrastructure, découplé
  par le port — EGEN génère la télémétrie au format OpenTelemetry (OTLP),
  peu importe qui la consomme ensuite. Détail complet :
  [15 — Observabilité](15-observabilite.md).

## Service Invocation Port

**Ajouté en Phase 5** ([19](19-feuille-de-route.md)), absent de la
rédaction initiale de ce document — le Moteur de Workflow
([11](11-moteur-de-workflow.md)) est le premier composant du Kernel à
avoir besoin d'émettre lui-même un appel HTTP sortant vers un service
déployé (une étape `POST /internal/validate`, par exemple) : aucun des
six domaines ci-dessus ne couvrait ce besoin.

```java
public interface ServiceInvocationPort {
    InvocationResult invoke(URI endpoint, String method, Map<String, String> payload, Duration timeout);
}
```

- **Ne fait jamais de découverte lui-même.** `endpoint` est une URI déjà
  résolue — l'appelant (le Moteur de Workflow, en
  `egen-application`) obtient l'adresse d'une instance saine via
  `DiscoveryPort.resolve(ServiceId)` ([Discovery Port](#discovery-port),
  déjà existant) et construit l'URI à partir de
  `ResolvedInstance.address()`/`port()`. Séparer strictement résolution et
  invocation évite de dupliquer dans ce nouveau port une logique de
  sélection d'instance qui existe déjà.
- **`payload`/`InvocationResult.body` restent `Map<String, String>`**,
  jamais un type JSON concret : même garde-fou que `ConfigSet`/
  `ResolvedConfig` ([Configuration Port](#configuration-port)) — EGEN
  reste neutre vis-à-vis du contenu métier échangé. C'est l'**adapter**
  (jamais `egen-application`, voir
  [02](02-principes-fondamentaux.md)) qui sérialise/désérialise le JSON
  réel sur le fil, via la bibliothèque de son choix.
- **Jamais pour une opération `EVENT ...`** d'une étape de workflow — ce
  cas-là passe par `MessagingPort.publish` ([Messaging
  Port](#messaging-port)), déjà existant et déjà conçu pour ça. Ce port-ci
  ne sert que les opérations `operation: "MÉTHODE /chemin"` (voir
  [11](11-moteur-de-workflow.md), "Définition déclarative d'un workflow").
- **Timeout obligatoire, jamais un timeout par défaut implicite caché
  dans l'adapter** — l'appelant connaît le `timeout` propre à l'étape
  (`Step.timeout()`), c'est la seule source de vérité.
- Implémentation V1 : `java.net.http.HttpClient` (JDK, zéro dépendance
  supplémentaire) — pas de bibliothèque HTTP tierce pour un besoin aussi
  simple (garde-fou n3, [02](02-principes-fondamentaux.md)). La
  sérialisation JSON du `payload`/`InvocationResult.body` utilise Jackson,
  déjà présent dans la stack (voir `egen-adapter-postgres-registry`, qui
  l'utilise déjà pour les mêmes raisons).
- `InvocationResult` est un DTO neutre : `{ int statusCode, Map<String,
  String> body }` — un code `4xx`/`5xx` est une réponse valide, jamais une
  exception : c'est au Moteur de Workflow de décider, selon
  `RetryPolicy`, si ce statut doit déclencher une nouvelle tentative ou un
  échec d'étape. Seule une erreur réseau (DNS, connexion refusée, timeout)
  lève une exception.
