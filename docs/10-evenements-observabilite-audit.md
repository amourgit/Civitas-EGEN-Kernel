# 10 — Événements, observabilité et audit

## À quoi sert ce document
Spécifier comment on suit ce qui se passe : événements (C5), traces et métriques, journaux, audit, supervision de bout en bout.

## Ce qu'il ne couvre pas
Le format des événements ([04](04-contrats-egen.md)), l'outillage d'exploitation ([12](12-resilience-et-exploitation.md)).

## Trois sources, un seul fil

| Source | Sert à | Technologie |
|---|---|---|
| **Événements** | Réagir et se resynchroniser | CloudEvents sur NATS (C5) |
| **Traces et métriques** | Comprendre où le temps passe et ce qui échoue | OpenTelemetry |
| **Audit** | Prouver qui a décidé quoi | Événements d'audit conservés |

Le **fil conducteur** est l'identifiant d'opération et le contexte `traceparent`, présents dans les trois.

## Traces

- Le contexte W3C Trace Context est propagé à **chaque saut** : portail → Federation Plane → Saga → Control Plane → moteur.
- Chaque span d'un Control Plane porte : `egen.operation_id`, `egen.capability`, `egen.scope`, `egen.controlplane`.
- Une opération multi-Control Planes se lit comme **une seule trace**, même si les Control Planes ont leurs propres collecteurs.

## Métriques (noms proposés, figés en R2.7)

| Métrique | Sens |
|---|---|
| `egen_operations_total{capability,phase}` | Opérations par capacité et phase finale |
| `egen_operation_duration_seconds{capability}` | Durée d'une opération |
| `egen_delegation_failures_total{capability,reason}` | Échecs de délégation par cause stable |
| `egen_policy_denials_total{level}` | Refus de politique (local ou fédéré) |
| `egen_registry_staleness_seconds` | Âge de la copie du registre fédéré |
| `egen_reconcile_backlog` | Ressources en attente de réconciliation |

Les étiquettes (labels) n'ont **jamais** de valeur à cardinalité illimitée (pas d'identifiant d'opération en étiquette).

## Journaux

Structurés, un événement par ligne. **Jamais** de secret, de jeton ni de valeur de configuration (G-08). Les essais vérifient
l'absence de fuite avec des **canaris** (fausses valeurs reconnaissables).

## Audit

| Ce qui est audité | Pourquoi |
|---|---|
| Toute décision d'autorisation (acceptée ou refusée), avec le niveau qui a décidé | Prouver qui a décidé quoi |
| Adhésion, suspension, révocation d'un Control Plane | Gouvernance de la fédération |
| Toute opération et sa chaîne « agit pour » (personne, agent) | Tracer les actions d'un agent à travers plusieurs Control Planes |
| Tout changement de politique | Responsabilité |

L'audit est publié comme **événements** (`africa.civitas.egen.audit.*.v1`) puis conservé par un service d'audit consommateur
(hors du cœur). Durée de conservation : point ouvert (PO-13).

## Supervision de bout en bout

Le Federation Plane assemble une **vue globale** à partir des `OperationRecord`, des événements et des traces : quelles opérations
sont en cours, bloquées, incertaines, compensées. Cette vue est **consultative** : elle ne devient jamais la source de vérité d'une
ressource (G-02). Les tableaux de bord sont des **services consommateurs**, hors du cœur.

## Exemple de bout en bout

Marie demande son agenda : la trace unique `4bf92f35…` relie le portail, le Federation Plane, les trois appels C3 et les moteurs.
Les événements `operation.accepted`, `progressed` et `succeeded` de chaque Control Plane portent le même `operationId`. L'audit
enregistre « opérateur de la Plateforme, au nom de Marie, a demandé `object-storage` sur `univ-x/rh` : accepté par SpiceDB
(niveau fédéré) et par la règle locale ».
