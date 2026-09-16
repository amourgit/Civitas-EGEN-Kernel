# 10 — Gestion des dépendances — graphe et ordonnancement

## Construction du graphe

Chaque `ServiceManifest.dependencies.services[]` devient une arête
dirigée `A depends-on B` dans le `DependencyGraph` global (voir
[05](05-modele-de-domaine.md)). EGEN construit ce graphe **au niveau de
tout l'écosystème connu**, pas seulement localement à un manifeste — c'est
ce qui permet de détecter un cycle qui traverserait trois services ou plus
(`A → B → C → A`), invisible si l'on ne regarde qu'un manifeste à la fois.

## Détection de cycle — refus à la validation

Un cycle de dépendance est **toujours** une erreur de conception métier,
jamais un cas à supporter techniquement. Le manifeste introduisant le
cycle est **rejeté** dès l'étape `Declare`, avec un message explicite
listant le chemin du cycle (`news-service → notification-service →
news-service`).

## Ordonnancement du déploiement — tri topologique

L'étape `Compose` (voir [04](04-moteur-de-reconciliation.md)) utilise un
**tri topologique** (algorithme de Kahn ou DFS post-order) du sous-graphe
concerné pour déterminer l'ordre de déploiement lors d'un déploiement
initial de plusieurs services liés : les dépendances **required** sont
déployées et doivent atteindre `RUNNING` avant que le service dépendant ne
quitte `CONFIGURED` pour `DEPLOYING` (voir la table des phases,
[09](09-cycle-de-vie.md)).

Pour les dépendances **non-required** (`required: false` dans le
manifeste), EGEN ne bloque pas l'ordonnancement — le service part en
`DEPLOYING` en parallèle, et la `Condition DependenciesSatisfied` reste
`False` avec `reason: OptionalDependencyUnavailable`, sans empêcher la
convergence vers `RUNNING`. C'est le mécanisme de **dégradation gracieuse**
prévu par construction.

## Ce qu'EGEN ne fait jamais sur les dépendances

- Il ne connaît **jamais** la raison métier de la dépendance (« pourquoi
  news-service a besoin de notification-service ») — uniquement le fait
  technique.
- Il n'empêche **jamais** un déploiement pour une dépendance optionnelle
  manquante — seulement pour une dépendance `required`.
- Il ne résout **jamais** de version automatiquement en cas d'ambiguïté
  (ex. deux versions majeures de `notification-service` coexistantes) — il
  expose l'ambiguïté via une `Condition` et laisse la décision à un
  humain ou à une politique explicite (voir [13](13-api-et-contrats.md)).
