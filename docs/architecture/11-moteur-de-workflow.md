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

## Compensation — le cœur de la robustesse Saga

Si l'étape `publish` échoue après que `reserve-notification-slot` a
réussi, le moteur exécute automatiquement, dans l'ordre inverse, les
`compensation` déclarées : `release-slot`. **Chaque compensation doit
elle-même être idempotente et doit pouvoir échouer** — auquel cas
l'exécution passe en statut `COMPENSATION_FAILED`, visible et alertable,
jamais masquée.
