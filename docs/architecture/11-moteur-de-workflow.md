# 11 — Moteur de Workflow — orchestration de processus techniques inter-services

## Pourquoi un moteur natif, et comment le dimensionner

Le pattern **Saga** (la référence académique/industrielle pour la
cohérence de transactions distribuées entre microservices) donne le cadre
à reprendre :

- **Saga orchestrée** (un coordinateur central pilote chaque étape et
  déclenche les compensations) — **recommandée par défaut pour EGEN**,
  car elle offre une visibilité centralisée du déroulé, ce qui correspond
  exactement au rôle de « cerveau de composition » qu'EGEN joue. La saga
  choreographiée (chaque service réagit aux événements des autres sans
  coordinateur) est plus adaptée à des écosystèmes très découplés mais
  devient vite difficile à observer globalement — **à réserver aux cas où
  le nombre d'étapes est très faible** (2-3 services).
- EGEN implémente le **modèle orchestré** comme moteur natif (inspiré très
  directement de **Dapr Workflow** et de la structure d'**Argo
  Workflows** : étapes, activités, durabilité de l'état d'exécution,
  reprise après crash).

## Le port

```java
public interface WorkflowEnginePort {
    WorkflowExecutionHandle start(WorkflowDefinition def, WorkflowInput input);
    void raiseEvent(ExecutionId id, String eventName, Object payload);   // ex: réponse asynchrone d'une étape
    void pause(ExecutionId id);
    void resume(ExecutionId id);
    void terminate(ExecutionId id, String reason);
    WorkflowExecutionStatus status(ExecutionId id);
}
```

> **Précisions d'implémentation (Phase 5, incrément 3 — `WorkflowEngine`,
> `egen-application.workflow`).** Le port ci-dessus est livré avec trois
> écarts assumés, documentés ici plutôt que découverts dans le code :
>
> - `start` renvoie un **`ExecutionId`** — la « poignée » (`WorkflowExecutionHandle`)
>   n'a aucun contenu de plus à porter : le statut et les résultats se
>   relisent via `status(id)` / l'API.
> - `WorkflowInput` est une `Map<String, String>` neutre (même garde-fou que
>   `ConfigSet`) et **est persisté avec l'exécution** (`WorkflowExecution.input()`),
>   faute de quoi une reprise après redémarrage rejouerait les étapes avec un
>   payload vide.
> - **`raiseEvent` n'est pas livré.** Une étape `EVENT …` est *fire-and-forget*
>   (voir ci-dessous) : aucune étape du modèle actuel n'attend un callback, donc
>   `raiseEvent` n'aurait aucun consommateur. Il sera ajouté avec la première
>   étape asynchrone qui en a réellement besoin, jamais par anticipation.

## Définition déclarative d'un workflow

```yaml
apiVersion: egen.civitas.africa/v1
kind: WorkflowDefinition
metadata:
  name: publish-article-workflow
spec:
  steps:
    - id: validate-content
      service: news-service
      operation: POST /internal/validate
      timeout: 10s
      retry: { maxAttempts: 3, backoff: exponential }
    - id: reserve-notification-slot
      service: notification-service
      operation: POST /internal/reserve-slot
      dependsOn: [validate-content]
      compensation:
        operation: POST /internal/release-slot
    - id: publish
      service: news-service
      operation: POST /internal/publish
      dependsOn: [reserve-notification-slot]
      compensation:
        operation: POST /internal/unpublish
    - id: notify-subscribers
      service: notification-service
      operation: EVENT africa.civitas.news.article.published.v1   # étape asynchrone : publie un événement, n'attend pas de réponse synchrone
      dependsOn: [publish]
  compensationStrategy: backward   # backward = compenser dans l'ordre inverse d'exécution (comportement Saga standard)
  executionPolicy:
    globalTimeout: 5m
    manualApprovalSteps: []       # possibilité de marquer une étape comme nécessitant une validation humaine
```

**Formats et valeurs par défaut du YAML** (appliqués par
`WorkflowMapper`, `egen-api`) :

- Durées : un entier suivi de `s`, `m` ou `h` (`10s`, `5m`, `1h`) — rien
  d'autre (`1.5s`, `PT5S`, `5d` sont refusés avec un message explicite).
- `timeout` d'étape omis → **30 s** (l'exemple ci-dessus en omet plusieurs :
  le domaine exige une valeur strictement positive).
- `retry` omis → aucune nouvelle tentative (`maxAttempts: 1`) ; `backoff`
  omis → `fixed`.
- `compensationStrategy` omis → `backward` (la seule valeur existante).
- `executionPolicy.globalTimeout` est **obligatoire** (aucune valeur par
  défaut raisonnable n'existe pour une borne de sécurité) ;
  `maxParallelSteps` omis → `1`.
- `kind`, s'il est présent, doit valoir `WorkflowDefinition`.

**Point de conception clé** : chaque étape référence une opération exposée
par le service (HTTP interne, gRPC, ou publication d'événement) — **jamais**
une logique embarquée dans le workflow lui-même. EGEN orchestre
l'enchaînement, le timing, le retry, et la compensation ; le service reste
seul responsable de ce que signifie « valider », « réserver » ou
« publier ».

## Durabilité de l'exécution

Chaque `WorkflowExecution` (voir [05](05-modele-de-domaine.md)) est
**persisté à chaque transition d'étape** (event-sourcing léger : liste
append-only de `StepResult`). Cela permet au moteur de reprendre une
exécution exactement où elle s'était arrêtée après un redémarrage du
Kernel — propriété identique à celle offerte par Dapr Workflow ou Temporal,
indispensable dès qu'un workflow dépasse quelques secondes ou implique un
appel asynchrone en attente de callback.

```java
public interface WorkflowStorePort {
    WorkflowExecution save(WorkflowExecution execution);         // upsert — persiste l'etat COURANT, append-only via WorkflowExecution.withStepResult
    Optional<WorkflowExecution> findById(ExecutionId id);
    List<ExecutionId> findRunningExecutions();                   // reprise au demarrage du Kernel (voir ci-dessus)
    Optional<WorkflowDefinition> findDefinition(WorkflowId id);
    WorkflowDefinition saveDefinition(WorkflowDefinition definition);
}
```

- Implémentation V1 : **PostgreSQL**, le même store que `RegistryStorePort`
  ([08](08-registry.md)) — pas une base dédiée au Workflow Engine, pour la
  même raison que `ConfigurationPort` réutilise l'agent Consul déjà présent
  plutôt que d'introduire un système supplémentaire (garde-fou n3,
  [02](02-principes-fondamentaux.md)). `WorkflowDefinition`/
  `WorkflowExecution` sont deux tables distinctes de `desired_state`/
  `observed_status`, dans la même base.
- `findRunningExecutions()` est ce qui permet au Moteur de Workflow de
  reconstruire, après un redémarrage du Kernel, la liste des exécutions
  à reprendre — exactement le même besoin que
  `RegistryStorePort.findAllIds()` sert déjà à la boucle de
  réconciliation des services au démarrage.

## Moteur d'exécution — règles exactes (`WorkflowEngine`)

`WorkflowEngine` implémente `WorkflowEnginePort`. Il n'appelle que des ports
(`WorkflowStorePort`, `DiscoveryPort`, `ServiceInvocationPort`,
`MessagingPort`) — jamais un SDK, jamais Nomad/Consul/NATS directement.

**Journal d'exécution : un fait final par tentative.** Le store est
insert-only avec une clé d'unicité `(execution_id, step_id, attempt)` ; le
moteur n'écrit donc **aucune ligne intermédiaire `RUNNING`** (elle masquerait
le résultat final de la même tentative). Le numéro de tentative est
`max(attempt déjà journalisé pour l'étape) + 1`. Une tentative interrompue
par un crash n'a laissé aucune trace : elle est simplement rejouée à la
reprise (**au moins une fois** — les opérations de service doivent donc être
idempotentes, comme leurs compensations).

**Avancement.** Séquentiel, dans l'ordre topologique de la définition ; une
étape n'est lancée que lorsque toutes ses dépendances ont réussi. Le champ
`maxParallelSteps` est accepté et validé mais **le parallélisme n'est pas
encore exploité** : exécuter séquentiellement respecte toute valeur
(≥ 1) de façon conservatrice.

**Opérations.**

| Forme | Exécution |
| --- | --- |
| `METHODE /chemin` | `DiscoveryPort.resolve(service)` → première instance → `ServiceInvocationPort.invoke` avec `Step.timeout()`. Corps = entrée du workflow pour POST/PUT/PATCH, vide pour GET/DELETE. La sortie de l'étape est le corps de la réponse, journalisé dans `StepResult.output` (le **chaînage des sorties vers les étapes suivantes n'existe pas** : hors périmètre tant qu'il n'est pas spécifié). |
| `EVENT type` | `MessagingPort.publish` d'un `TechnicalEvent` (`correlationId` = `ExecutionId`, `data` = entrée du workflow en JSON, sujet = `type`). N'attend aucune réponse. |

**Retry.** Une tentative échoue sur : erreur réseau/exception d'adapter,
aucune instance résolue, ou statut HTTP `5xx`/`408`/`429` → **réessayable**
tant que le nombre d'échecs journalisés est `< RetryPolicy.maxAttempts`.
Tout autre `4xx` est un échec **définitif** immédiat (rejouer la même requête
ne peut pas la rendre valide). Délai entre tentatives : `FIXED` = base,
`EXPONENTIAL` = base × 2^(échecs−1), plafonné (`WorkflowEngineSettings`).

**Échec définitif d'une étape.** L'étape échouée n'est jamais compensée
(son effet partiel est de la responsabilité du service) ; seules le sont les
étapes **réussies** déclarant une `compensation`, en **ordre inverse
d'exécution**. S'il n'y en a aucune, l'exécution passe directement en
`FAILED` ; sinon `COMPENSATING`. Chaque compensation est tentée **une seule
fois** ; si l'une échoue, les suivantes sont **quand même tentées**
(meilleur effort — laisser plus d'effets non annulés serait pire) et
l'exécution finit en `COMPENSATION_FAILED`, sinon en `COMPENSATED`.

**`globalTimeout`.** Vérifié avant chaque étape : s'il est dépassé, la
prochaine étape est journalisée en échec et la compensation démarre. Il
n'interrompt pas un appel en cours (celui-ci est borné par `Step.timeout()`).

**Approbation manuelle** (`manualApprovalSteps`). Avant la première tentative
d'une telle étape, le moteur journalise un `StepResult` `PENDING` et passe
l'exécution en `PAUSED` ; `resume(id)` vaut approbation et l'étape est lancée.

**Pause / reprise / arrêt.** `pause` (RUNNING → PAUSED) prend effet entre
deux étapes (un appel en cours va à son terme et son résultat est journalisé).
`terminate` lance la compensation des étapes réussies (statut `COMPENSATING`,
y compris si un appel est en cours, pour qu'un effet qui aboutirait après
coup ne reste pas orphelin) ou `FAILED` si rien n'est à annuler. La raison est
journalisée dans les logs du Kernel, pas persistée (aucun champ dédié dans le
modèle).

**Reprise au démarrage.** `recoverInterruptedExecutions()` relance toute
exécution `RUNNING`/`COMPENSATING` renvoyée par
`WorkflowStorePort.findRunningExecutions()` ; une exécution `PAUSED` attend un
`resume` explicite.

**Limites connues de cet incrément** (à lever explicitement, jamais en
silence) : définition non versionnée (une exécution en cours utilise la
définition stockée la plus récente) ; un seul Kernel pilote une exécution à la
fois (aucun verrou distribué — cohérent avec l'`Idempotency-Key` en mémoire de
[13](13-api-et-contrats.md)) ; pas encore de métriques propres au moteur
(les appels de ports sont déjà instrumentés par les décorateurs `Observed*Port`).

## Compensation — le cœur de la robustesse Saga

Si l'étape `publish` échoue après que `reserve-notification-slot` a
réussi, le moteur exécute automatiquement, dans l'ordre inverse, les
`compensation` déclarées : `release-slot`. **Chaque compensation doit
elle-même être idempotente et doit pouvoir échouer** — auquel cas
l'exécution passe en statut `COMPENSATION_FAILED`, visible et alertable,
jamais masquée.
