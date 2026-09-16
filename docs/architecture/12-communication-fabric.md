# 12 — Communication Fabric — abstraction d'appel inter-services

## Le problème à résoudre

Un service ne doit jamais avoir à gérer lui-même : résolution du nom du
service cible, retry, timeout, circuit breaking, propagation de trace,
mTLS. Dapr résout cela avec un **sidecar** ; EGEN offre le même bénéfice,
mais **sans imposer un sidecar comme unique mode d'intégration** (cohérent
avec le principe de non-couplage à un framework/runtime).

## Deux modes d'intégration, au choix du service

| Mode | Fonctionnement | Quand l'utiliser |
|---|---|---|
| **SDK léger** (bibliothèque cliente par langage) | Le service importe une petite bibliothèque EGEN (Python/Go/Java/Rust/Node) qui appelle directement `DiscoveryPort`/`MessagingPort` via l'API de contrôle EGEN, avec retry/circuit-breaker intégrés côté client. | Équipes qui acceptent une dépendance de bibliothèque minime, cas le plus courant. |
| **Sidecar / proxy local** (inspiré du modèle Dapr / Envoy) | Un processus local (déployé par Nomad à côté de la tâche principale, dans le même `TaskGroup`) expose une API HTTP locale (`localhost:xxxx/discover/...`) — le service applicatif ne dépend d'aucune bibliothèque EGEN, seulement d'appels HTTP locaux. | Services legacy, langages sans SDK officiel, ou volonté explicite de zéro dépendance de code à EGEN. |

Les deux modes s'appuient sur les **mêmes ports secondaires**
(`DiscoveryPort`, `MessagingPort`) côté Kernel — le choix est une question
de **façade cliente**, pas d'architecture cœur.

## Préoccupations transverses gérées par la Communication Fabric

- **Résolution** : traduction `discover("notification-service")` → appel
  `DiscoveryPort.resolve()`.
- **Retry / Timeout / Circuit breaking** : politiques déclarées dans le
  manifeste (`lifecycle` ou une section dédiée `resiliency`), appliquées
  uniformément — inspiré du modèle de policies « target-based » de Dapr
  (policies appliquées par app/service cible, pas codées en dur dans
  chaque appelant).
- **Propagation de contexte** : `traceparent` (W3C Trace Context) et
  `egen_correlation_id` injectés automatiquement dans chaque appel
  sortant.
- **mTLS** (voir [14 — Sécurité](14-securite.md)) : terminaison
  transparente pour le service appelant, gérée par le SDK/sidecar.

## Ce que la Communication Fabric ne fait jamais

Elle ne transforme, n'enrichit, ni ne route sur la base du **contenu
métier** du message ou de la requête (pas de routage par
`articleCategory` par exemple) — seulement sur des critères techniques
(nom de service, version, environnement, en-têtes de corrélation).
