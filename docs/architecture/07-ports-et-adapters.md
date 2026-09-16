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
