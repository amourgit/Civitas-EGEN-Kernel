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

## Sémantique normative de la résilience

Trois implémentations doivent se comporter **à l'identique** : le Moteur de
Workflow du Kernel (`egen-application.resilience`), le SDK Java et le SDK
Python. Elles partagent un jeu de scénarios exécutable,
`sdk/contract/resiliency-contract.json` (le Kernel rejoue les scénarios de
`scope: breaker` — il ne porte que le circuit breaker, voir plus bas ; les
deux SDK rejouent tous les scénarios), que chacune rejoue dans ses propres
tests : modifier la sémantique sans modifier ce fichier — ou l'inverse — fait
échouer la CI.

**Classification d'une tentative.**

| Résultat | Classe | Effet sur le circuit breaker |
| --- | --- | --- |
| `2xx`, `3xx` | succès | remet le compteur d'échecs à zéro |
| `4xx` autre que `408`/`429` | **réponse définitive** (le service a répondu : la requête est invalide) | compte comme un *succès* du circuit, **jamais réessayée** |
| `5xx`, `408`, `429`, délai dépassé, erreur réseau | **échec réessayable** | +1 échec consécutif |

**Retry.** Au plus `maxAttempts` tentatives (la première incluse). Attente
entre deux tentatives : `fixed` = `baseDelay` ; `exponential` =
`baseDelay × 2^(n−1)` après la n-ième tentative échouée, plafonnée à 30 s.
Le `timeout` s'applique à **chaque** tentative. Si le circuit s'ouvre
pendant la série, les tentatives restantes sont abandonnées (échec rapide).
Quand plusieurs instances sont résolues, chaque tentative vise l'instance
suivante (tourniquet) — un retry sur la même instance défaillante n'a
guère de sens.

**Circuit breaker** (un par service cible et par processus appelant) :

```
CLOSED ──(failureThreshold échecs consécutifs)──▶ OPEN
OPEN ──(openDuration écoulée)──▶ HALF_OPEN  (UNE seule tentative d'essai admise)
HALF_OPEN ──succès──▶ CLOSED     HALF_OPEN ──échec──▶ OPEN (nouvelle fenêtre)
```

Pendant `OPEN` (et pour toute tentative autre que l'essai en `HALF_OPEN`),
l'appel est **rejeté sans aucun accès réseau** (`CircuitOpen`) ; un rejet ne
compte pas comme un échec et ne prolonge pas la fenêtre d'ouverture.

**Propagation de contexte.** Chaque appel sortant porte `traceparent` (W3C
Trace Context ; généré s'il est absent) et l'en-tête HTTP
**`Egen-Correlation-Id`** (UUID généré s'il est absent) — même valeur
logique que l'attribut d'événement `egen_correlation_id` de
[07](07-ports-et-adapters.md) : un en-tête HTTP ne doit pas contenir de
tiret bas (les proxies courants, nginx en tête, les suppriment).

**mTLS.** Le SDK accepte un `SSLContext` (Java) / `ssl.SSLContext` (Python)
et le transmet tel quel à son client HTTP ; **lorsqu'un contexte est fourni,
les services cibles sont adressés en `https`** (sans contexte, en `http`). Le
sidecar n'expose pas encore cette option : il parle `http` aux cibles tant que
leur terminaison TLS est assurée par le maillage (Consul Connect) hors du
processus. Aucun des deux ne gère de certificats lui-même
(voir [14](14-securite.md)).

**Politique appliquée par le Moteur de Workflow.** L'étape d'un workflow
déclare déjà son propre retry (voir [11](11-moteur-de-workflow.md)) : le
Moteur **n'applique pas** en plus le retry de la cible (les retries se
multiplieraient). Il applique celle de la cible pour ce que le workflow ne
peut pas connaître : le **circuit breaker**, partagé par tous les workflows
qui appellent cette cible, et le **timeout** (le plus strict des deux
l'emporte). Les **compensations** ne sont jamais rejetées par un circuit
ouvert — c'est le mécanisme de sécurité : elles sont toujours tentées, et
leur résultat alimente quand même le circuit — sauf s'il est `OPEN` : un
résultat enregistré par un appel contourné ne change alors pas l'état (il ne
referme pas le circuit et ne prolonge pas sa fenêtre).

## SDK léger

Bibliothèques **sans aucune dépendance** hors la bibliothèque standard du
langage, et sans dépendance vers `egen-domain` (elles parlent uniquement
l'API de contrôle, voir [13](13-api-et-contrats.md)).

| Langage | Emplacement | Tests |
| --- | --- | --- |
| Java 21 | module Maven `egen-sdk-java` | JUnit, serveurs `com.sun.net.httpserver` |
| Python 3.10+ | `sdk/python/` (paquet `egen_sdk`) | `unittest`, serveurs `http.server` |

Surface commune (noms adaptés à chaque langage) :

- `resolve(service)` → instances saines **et** politique de résilience de la
  cible (`GET /api/v1/discover/{service}`, voir [13](13-api-et-contrats.md)) ;
  résultat mis en cache brièvement (5 s par défaut) pour ne pas faire du
  Kernel un intermédiaire de chaque appel.
- `call(service, method, path, body, …)` → résolution, choix d'instance,
  application de la politique ci-dessus, propagation de contexte.
- Jamais : transformer, enrichir ou router selon le contenu du corps (voir
  « Ce que la Communication Fabric ne fait jamais »).

*Pas encore livré* : la publication d'événements par le SDK. Elle
passerait par `POST /api/v1/events/publish` (voir [13](13-api-et-contrats.md)),
qui n'est **pas encore implémenté côté Kernel** ; l'ajouter aux SDK avant lui
n'aurait aucun sens.

## Sidecar de référence

Module Maven `egen-sidecar` : un seul processus, sans dépendance, **construit
sur le SDK Java** (il n'a donc aucune logique de résilience propre — il
l'hérite). Il écoute sur `127.0.0.1` uniquement (jamais sur une interface
publique) et expose :

| Route locale | Effet |
| --- | --- |
| `GET /discover/{service}` | résolution (instances + politique de la cible) |
| `ANY /call/{service}/{chemin…}` | appel avec résilience et propagation de contexte ; statut et corps de la cible renvoyés tels quels ; circuit ouvert → `503` + en-tête `Egen-Circuit: open` ; aucune instance → `503` + `Egen-Resolution: none` |
| `GET /health` | `200` si le processus répond |

Configuration par variables d'environnement : `EGEN_CONTROL_PLANE_URL`
(obligatoire), `EGEN_TOKEN` (jeton de l'API de contrôle, optionnel),
`EGEN_SIDECAR_PORT` (défaut `3500`). Déployé par Nomad dans le même
`TaskGroup` que la tâche applicative.
