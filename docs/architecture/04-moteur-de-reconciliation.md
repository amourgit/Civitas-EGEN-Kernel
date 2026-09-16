# 04 — Le moteur de réconciliation

C'est la pièce la plus critique — et la plus souvent sous-estimée — du
Kernel. Elle s'inspire directement du pattern de contrôleur
Kubernetes/Crossplane (watch → diff → act), qui a fait ses preuves à très
grande échelle. Il faut en reprendre les leçons **précisément**, y compris
les pièges.

## Level-triggered, pas edge-triggered

Une erreur de conception fréquente est de faire réagir le Kernel à des
**événements** (« le déploiement vient de changer, donc je fais X »).
C'est fragile : un événement peut être perdu, dupliqué, ou traité dans le
désordre.

Le bon principe, **level-triggered reconciliation** : un événement
(webhook Nomad, création d'un manifeste, tick périodique) ne sert qu'à
dire *« va revérifier l'état complet de CETTE ressource »* — il ne
transporte aucune information sur *ce qui a changé*. Le réconciliateur
relit ensuite l'état désiré ET l'état observé dans leur intégralité au
moment de l'exécution, calcule l'écart, et agit. Un `Reconcile(serviceId)`
doit toujours donner un résultat correct **quel que soit le nombre de fois
où il est appelé et dans quel ordre**, tant qu'il est appelé *au moins une
fois* après chaque changement réel.

## Composants de la boucle

```
┌────────────┐    enqueue(key)     ┌──────────────┐   pop(key)   ┌───────────────────┐
│  Watchers   │───────────────────▶│  Work Queue   │─────────────▶│  Reconcile Worker  │
│ (webhooks,  │                    │ (dédupliquée, │              │  Loop (N goroutines│
│  polling,   │◀───────────────────│  avec retry & │◀─────────────│  /threads en       │
│  API events)│   requeue(key,     │  backoff)     │  requeue on   │  parallèle borné)  │
└────────────┘   delay)            └──────────────┘  error/delta  └─────────┬──────────┘
                                                                             │
                                          ┌──────────────────────────────────┤
                                          │                                  │
                                 ┌────────▼────────┐              ┌─────────▼─────────┐
                                 │  Load DesiredState│              │ Fetch ObservedState│
                                 │  (Registry Store) │              │ (via Ports:         │
                                 │                    │              │ DeploymentPort,     │
                                 │                    │              │ DiscoveryPort...)   │
                                 └────────┬───────────┘              └─────────┬──────────┘
                                          └───────────────┬──────────────────--┘
                                                           ▼
                                              ┌────────────────────────┐
                                              │   Diff & Plan           │
                                              │  (quelles opérations    │
                                              │   ramènent l'observé    │
                                              │   vers le désiré ?)     │
                                              └────────────┬────────────┘
                                                            ▼
                                              ┌────────────────────────┐
                                              │  Delegate (appels aux   │
                                              │  ports secondaires,     │
                                              │  idempotents)           │
                                              └────────────┬────────────┘
                                                            ▼
                                              ┌────────────────────────┐
                                              │  Update Status +        │
                                              │  Emit Reconciliation    │
                                              │  Event + Metrics        │
                                              └────────────────────────┘
```

**Éléments clés à répliquer explicitement dans l'implémentation** :

- **La work queue contient des *clés* (ex. `serviceId`), jamais des
  événements complets.** C'est ce qui permet la déduplication : si trois
  changements arrivent coup sur coup pour le même service, une seule
  réconciliation suffit — la dernière lecture de l'état voit tout.
- **Un `resync` périodique inconditionnel** (ex. toutes les 5–10 minutes)
  doit re-enqueuer *toutes* les ressources connues, même sans changement
  détecté. Cela corrige les divergences causées par des événements manqués
  (ex. webhook Nomad perdu à cause d'une coupure réseau).
- **Retry avec backoff exponentiel et plafond**, et compteur d'échecs
  consécutifs exposé en métrique (`egen_reconcile_failures_total{service=...}`)
  pour permettre l'alerting.
- **Concurrence bornée** (`maxConcurrentReconciles`) par type de ressource,
  pour éviter de saturer Nomad/Consul lors d'un resync massif (ex.
  redémarrage du Kernel avec 500 services enregistrés).
- **Un filtre de type « GenerationChanged »** : ne réagir aux modifications
  du `DesiredState.spec` que lorsqu'il change réellement (comparaison de
  hash ou de numéro de génération), pas à chaque mise à jour de `status` —
  sinon on crée une boucle infinie où observer l'état déclenche une
  nouvelle réconciliation qui remet à jour le statut, etc.

## Idempotence — condition de survie de la boucle

Chaque opération de `Delegate` doit être conçue pour être **rejouée sans
dommage** :

- « Créer le job Nomad `news-service` » → si le job existe déjà avec la
  même spec, ne rien faire (comparaison de version/hash de spec avant
  `PUT`).
- « Enregistrer le service dans Consul » → l'enregistrement Consul est
  déjà nativement idempotent (`PUT /v1/agent/service/register` avec le
  même `ID` remplace l'enregistrement existant).
- « Créer le topic Kafka » → vérifier l'existence avant création, ignorer
  l'erreur « topic already exists » en cas de course.

Un `operationId` (UUID) est généré à chaque cycle de réconciliation et
propagé dans les logs/traces de tous les appels aux ports, pour permettre
de reconstituer *a posteriori* la séquence exacte d'un incident.

## État désiré vs état observé — modèle explicite

```
DesiredState (persisté par EGEN, source de vérité déclarative)
├── spec              → le contenu du EGEN Service Manifest (voir 06), versionné
├── generation        → entier incrémenté à chaque modification du spec
└── metadata          → créateur, date, environnement cible

ObservedState (reconstruit à CHAQUE cycle, jamais persisté comme vérité — c'est un cache de lecture)
├── deployment.status     → lu depuis DeploymentPort.getStatus(serviceId)
├── discovery.instances   → lu depuis DiscoveryPort.resolve(serviceId)
├── discovery.health      → lu depuis DiscoveryPort.health(serviceId)
├── messaging.bindings    → lu depuis MessagingPort.getBindingStatus(serviceId)
└── observedGeneration    → la 'generation' du DesiredState qui a été effectivement traitée

Status (exposé via API, écrit par le réconciliateur, PAS par le client)
├── phase                 → voir la state machine, 09
├── conditions[]          → { type, status, reason, message, lastTransitionTime } (modèle Kubernetes Conditions)
└── observedGeneration    → permet à un client de savoir si son dernier "declare" a déjà été traité
```

Le pattern `observedGeneration` (repris tel quel de Kubernetes) est
essentiel : il permet à un appelant de savoir, sans ambiguïté, si le
`status` qu'il lit correspond bien à la dernière version qu'il a déclarée,
ou si une réconciliation est encore en cours.

## Anti-pattern à bannir : la réconciliation synchrone dans l'API

L'API de contrôle (`POST /services`) ne doit **jamais** attendre que le
déploiement Nomad soit terminé pour répondre. Elle doit :

1. Valider et persister le `DesiredState` (rapide, < 100 ms).
2. Répondre `202 Accepted` avec l'`observedGeneration` courant et un lien
   vers le statut.
3. Laisser la boucle de réconciliation faire le travail de manière
   asynchrone.

C'est la différence fondamentale entre un control plane et un simple
script de déploiement synchrone.
