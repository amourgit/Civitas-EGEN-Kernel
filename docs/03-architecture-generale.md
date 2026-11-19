# 03 — Architecture générale : qui fait quoi, dans quel ordre

## À quoi sert ce document
Donner le schéma mental d'ensemble : les niveaux, la répartition des responsabilités, ce qui est du code EGEN et
ce qui est adopté, et suivre **une demande de bout en bout** (l'agenda du Service RH de l'Université X).

## Ce qu'il ne couvre pas
Les spécifications détaillées : contrats ([04](04-contrats-egen.md)), Kernel ([05](05-kernel-de-reference.md)),
Federation Plane ([06](06-federation-plane.md)), sécurité ([07](07-identite-confiance-securite.md)).

## Les quatre niveaux

```
NIVEAU 0  CONTRATS EGEN : C1 identité · C2 capacités · C3 délégation · C4 état
          · C5 événements · C6 versions
          (la seule chose obligatoire pour participer à la fédération)
              │
NIVEAU 1  FEDERATION PLANE
          registre des Control Planes · découverte des capacités · confiance
          · politiques fédérées · coordination des opérations multi-Control Planes
          (il répond à « qui est compétent ? » puis laisse les Control Planes se parler)
              │
NIVEAU 2  CONTROL PLANES (chacun autonome)
          Métier          Transversal          Intelligence et Agents      Externe (ex. Crossplane natif)
          = Kernel EGEN   = Kernel EGEN        = Kernel EGEN               = pont de fédération (C1 à C6)
              │
NIVEAU 3  MOTEURS SPÉCIALISÉS (ils exécutent et réconcilient)
          Kubernetes · Nomad · NATS · OpenBao · stockage objet · Temporal · …
```

Règle de lecture : **chaque niveau ne connaît que le niveau en dessous par un contrat**. Le Federation Plane ne
parle jamais à un moteur ; un Control Plane ne dépend jamais du fonctionnement interne d'un autre.

## Qui est responsable de quoi

| Responsabilité | Responsable |
|---|---|
| Connaître les Control Planes participants ; découvrir leurs capacités | Federation Plane |
| Établir la confiance entre Control Planes | Federation Plane et Control Planes |
| Orienter une demande vers le bon Control Plane | Federation Plane |
| Coordonner une opération qui implique plusieurs Control Planes | Federation Plane (avec Temporal) |
| Connaître et administrer les ressources locales | Control Plane concerné |
| Définir les politiques locales | Control Plane concerné |
| Choisir et solliciter les moteurs ; installer les extensions locales | Control Plane concerné |
| Réconcilier les ressources (rapprocher l'état réel de l'état voulu) | Moteur délégué |
| Exécuter les déploiements et les tâches | Moteur spécialisé |
| Traduire des interfaces incompatibles | Connecteur ou pont |
| Fournir les vues et outils d'administration | Services consommateurs de la fédération |

## Ce qui est du code EGEN et ce qui est adopté

| Fonction | Adopté (solution éprouvée) | Code EGEN |
|---|---|---|
| API de contrôle, registre local, versionnement des ressources, observation des changements | API Kubernetes (types personnalisés « CRD ») | Les schémas des types EGEN |
| Capacités et implémentations interchangeables, extensions | Crossplane | Le paquet « egen-core » (types et compositions) |
| Réconciliation locale | Contrôleurs Kubernetes et Crossplane, moteurs | — |
| Suivi des opérations, publication d'événements, garde des politiques | — | **L'opérateur EGEN** |
| Exposition des contrats C1 à C6 hors du cluster | — | **Le pont de fédération** |
| Registre fédéré, découverte, routage | — | **Le service Federation Plane** (petit) |
| Opérations multi-Control Planes, Sagas | Temporal | Les définitions de workflows |
| Identité des charges de travail | SPIFFE/SPIRE | Configuration |
| Identité des personnes, droits fins | Keycloak, SpiceDB | Schéma de droits fédéré |
| Secrets | OpenBao | Configuration |
| Événements | NATS JetStream, format CloudEvents | Catalogue des types d'événements |
| Observabilité | OpenTelemetry | Conventions de nommage |
| Vérification d'interopérabilité | — | **Le kit de conformité** |

Le code EGEN tient en cinq pièces : **schémas**, **opérateur**, **pont de fédération**, **Federation Plane**,
**kit de conformité**. Tout le reste est adopté (décision D-07).

## Exemple de bout en bout : l'agenda du Service RH

**Situation.** Marie, du Service RH de l'Université X, demande un agenda partagé pour son service. L'application
choisie est Radicale (un serveur d'agenda). Elle a besoin de **stockage** et d'un **sujet de messagerie** (fournis
par le Control Plane Transversal) et d'être **exécutée** (par le Control Plane Métier).

| # | Qui | Quoi | Technologie |
|---|---|---|---|
| 1 | Marie | S'identifie sur le portail de la Plateforme et demande « agenda pour le Service RH ». | Keycloak (identité des personnes) |
| 2 | Opérateur de la Plateforme | Transmet au Federation Plane une demande de trois capacités (`workload-runtime`, `object-storage`, `messaging`) pour le périmètre `univ-x/rh`, avec un jeton à portée limitée au nom de Marie. | Échange de jeton |
| 3 | Federation Plane | Vérifie que cet opérateur peut demander ces capacités sur ce périmètre. | SpiceDB |
| 4 | Federation Plane | Cherche dans sa vue de fédération quel Control Plane offre chaque capacité : Transversal pour les deux premières, Métier pour l'exécution. | Registre fédéré |
| 5 | Federation Plane | Lance une Saga (suite d'étapes avec annulation en cas d'échec) avec une clé d'idempotence. | Temporal |
| 6 | Saga → Control Plane Transversal | Étape A : délègue `object-storage` (C3). Le Control Plane vérifie l'identité de l'appelant et sa politique locale, crée une Operation, délègue au moteur de stockage. États : `Accepted` → `Progressing` → `Ready`. Événement émis (C5). | mTLS SPIFFE, Crossplane, moteur de stockage, NATS |
| 7 | Saga → Control Plane Transversal | Étape B : délègue `messaging` de la même façon. | idem |
| 8 | Saga → Control Plane Métier | Étape C : délègue `workload-runtime` pour Radicale, avec des **références** (jamais des valeurs) au stockage et au sujet créés. Le moteur d'exécution (Kubernetes, ou Nomad si le Control Plane l'a choisi) déploie. | Crossplane, Kubernetes ou Nomad |
| 9 | Federation Plane | L'opération passe à `Succeeded`. Il ne conserve que l'enregistrement de l'opération et **qui administre quoi**, pas le contenu des ressources. | — |
| 10 | Portail | Lit l'état de l'opération et affiche « agenda prêt » à Marie. | C4 |

**Si l'étape C échoue** après A et B : la Saga **compense** en demandant au Control Plane Transversal de supprimer le
stockage et le sujet (opérations de suppression déléguées). L'opération reste `PartiallySucceeded` tant que la
compensation n'est pas terminée.

**Si le Control Plane Métier est injoignable** : l'état de l'étape C est `Unknown` (incertain). La Saga ne conclut
pas à l'échec : elle **observe** (relit l'opération par sa clé d'idempotence) puis réessaie avec attente
croissante jusqu'à l'échéance, et seulement alors compense (P-15).

**Si le Federation Plane tombe en cours de route** : chaque Control Plane continue localement ; la Saga reprend
là où elle s'était arrêtée à son retour (Temporal conserve son avancement).

## Topologie de la fédération

| Topologie | Description | Retenue |
|---|---|---|
| Centralisée | Un seul Federation Plane pour tous. | Possible, pas imposée. |
| Par environnement | Un Federation Plane par environnement de gouvernance (une organisation), répliqué. | **Proposition par défaut** (PO-03). |
| Hiérarchique | Un Federation Plane peut être membre d'un autre : il expose les **mêmes contrats** qu'un Control Plane. | Rendue possible par construction. |
| Distribuée | Pas d'organe central ; les Control Planes se connaissent par paires. | Possible : la confiance et la découverte restent portées par les contrats. |

Un Control Plane peut appartenir à plusieurs fédérations : chaque appartenance est une relation de confiance
distincte (voir [06](06-federation-plane.md)).

## Schéma d'un Kernel de référence (vue rapide)

```
Control API ............... API Kubernetes (types EGEN)
Registre local ............ stockage de l'API Kubernetes
Capacités ................. Crossplane (interface + implémentations)
Extensions ................ paquets Crossplane
Délégation/réconciliation . contrôleurs + Providers Crossplane → moteurs
Politiques ................ RBAC + règles d'admission + SpiceDB
Suivi des opérations ...... ressource Operation + événements   ← code EGEN
Pont de fédération ........ expose C1 à C6                      ← code EGEN
```

Détail dans [05](05-kernel-de-reference.md).
