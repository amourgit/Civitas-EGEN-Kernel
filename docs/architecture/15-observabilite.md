# 15 — Observabilité — standards et signaux attendus

*(Complète le [Port `ObservabilityPort`](07-ports-et-adapters.md#observability-port)
— ce document couvre le « quoi », pas le « comment technique ».)*

## Les trois signaux, standardisés OpenTelemetry

- **Traces** : chaque cycle de réconciliation (voir
  [04](04-moteur-de-reconciliation.md)) génère un span racine
  `reconcile(serviceId)`, avec des spans enfants par appel de port
  (`deployment.create`, `discovery.register`…). Chaque appel entrant sur
  l'API de contrôle propage son `traceparent` jusqu'au premier appel
  réseau vers Nomad/Consul/Kafka.
- **Métriques** — jeu minimal à exposer dès la V1 :
  - `egen_services_total{phase="RUNNING|DEGRADED|FAILED|..."}` — *état réel du code* : un **compteur** incrémenté à chaque enregistrement du statut, donc à chaque cycle d'un service actif ; ce n'est pas le nombre de services actuellement dans la phase. Il n'existe pas de jauge du nombre courant en V1.
  - `egen_reconcile_duration_seconds` — *état réel du code* : la durée de chaque cycle de réconciliation, étiquetée par `service` (et non par type de ressource)
  - `egen_reconcile_failures_total{service, reason}`
  - `egen_dependency_unresolved_total{service, dependency}`
  - `egen_workflow_executions_total{workflow, status}`
  - `egen_adapter_call_duration_seconds{adapter, operation}` et `egen_adapter_call_errors_total{adapter, operation}`
- **Logs** : structurés (JSON), toujours enrichis de `serviceId`,
  `generation`, `operationId`, `traceId` — jamais de log de texte libre
  non corrélable.

## Questions auxquelles l'observabilité EGEN doit répondre en un coup d'œil

Quel service est actif, quelle version tourne, où est-il exécuté, est-il
sain, quelles dépendances sont indisponibles, quel déploiement a échoué,
quelle opération a provoqué l'état actuel. Chacune de ces questions
correspond à **une requête simple** sur l'API de statut ou sur les
métriques exposées — si la réponse nécessite de croiser manuellement les
logs de trois systèmes, l'observabilité est incomplète et doit être
retravaillée avant la mise en production du domaine concerné.

## SLO du Kernel lui-même

EGEN définit ses propres objectifs de niveau de service en tant que
plateforme : temps de convergence médian après un `Declare` (ex. p50 <
30s, p99 < 2min pour un déploiement simple sans dépendances bloquantes),
disponibilité de l'API de contrôle, temps de détection d'une dégradation
(délai entre la panne réelle et le passage en phase `DEGRADED`). Ces SLO
sont suivis dès les premiers environnements de test, pas ajoutés a
posteriori.

## Export vers un collecteur (écart I6)

*Décidé par Samuel le 14 novembre 2026 (décision déléguée à Claude) ; codé et testé le même jour.*

L'adresse du collecteur OpenTelemetry (protocole OTLP sur gRPC) se règle par `egen.otel.endpoint`, que l'on alimente par la variable d'environnement `OTEL_EXPORTER_OTLP_ENDPOINT`. Sa valeur par défaut est **vide**.
- **Adresse renseignée** : le Kernel exporte ses traces et ses métriques vers ce collecteur.
- **Adresse vide** : le Kernel n'exporte **rien** et ne tente aucune connexion. Les spans existent quand même et le `traceparent` continue de se propager entre les appels, mais rien ne part sur le réseau. Une ligne `INFO` au démarrage le dit : « aucun point de collecte configuré ».

*Avant cette règle*, la valeur par défaut était `http://localhost:4317` : sans collecteur, le Kernel écrivait environ 35 lignes d'erreur (`SEVERE`, « Failed to export spans ») toutes les 60 secondes.

**Exemple de bout en bout.** Sur le poste de développement, personne ne lance de collecteur : le journal du Kernel reste silencieux sur ce sujet. En production, le déploiement positionne `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317` et les traces arrivent dans l'outil d'observation.
