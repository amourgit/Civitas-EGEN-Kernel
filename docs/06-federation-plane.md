# 06 — Le Federation Plane

## À quoi sert ce document
Spécifier le Federation Plane : ce qu'il sait, ce qu'il ne sait pas, comment un Control Plane le rejoint ou le
quitte, comment les capacités sont découvertes, comment une demande est orientée, comment les opérations
multi-Control Planes sont coordonnées, et ce qui se passe quand il est indisponible.

## Ce qu'il ne couvre pas
Le détail des contrats ([04](04-contrats-egen.md)), des mécanismes de confiance ([07](07-identite-confiance-securite.md))
et des Sagas ([09](09-operations-distribuees.md)).

## Ce qu'il est, et ce qu'il n'est pas

Le Federation Plane est le niveau qui relie des Control Planes **autonomes**. Il garde une **vue de fédération**
(qui participe, quelles capacités sont offertes, quelles relations de confiance existent, qui administre quoi). Il
ne garde **jamais** les ressources elles-mêmes (G-02), ne parle **jamais** à un moteur, et ne remplace aucun
Control Plane (G-01).

Il expose **les mêmes contrats C1 à C6** qu'un Control Plane : c'est ce qui permet qu'une fédération soit membre
d'une autre (hiérarchie) sans mécanisme supplémentaire.

## Ce qu'il détient

| Objet | Contenu | Source de vérité |
|---|---|---|
| `ControlPlane` | Document C1 du membre, état d'admission (`Pending`, `Admitted`, `Suspended`, `Revoked`), niveau de conformité vérifié | Le Federation Plane (admission) ; le Control Plane (description) |
| `CapabilityIndex` | Copie signée des descripteurs C2 de chaque membre, avec date de fraîcheur | Le Control Plane concerné |
| `TrustRelation` | Relation de confiance entre le Federation Plane et un membre : domaine de confiance, empreinte des clés publiques, dates | Le Federation Plane |
| `FederationPolicy` | Règles fédérées : qui peut demander quelle capacité sur quel périmètre | Le Federation Plane, appliquées via SpiceDB |
| `AuthorityMap` | Pour chaque ressource désignée : quel Control Plane en est l'autorité (références, jamais le contenu) | Le Control Plane concerné |
| `OperationRecord` | Trace d'une opération multi-Control Planes : étapes, états, clé d'idempotence, identifiant du workflow Temporal | Le Federation Plane |

Réalisation : ces objets sont des ressources de l'API Kubernetes du Federation Plane (même mécanique que le Kernel
de référence) ; la **technologie du Federation Plane est un détail d'implémentation** derrière les contrats.

## Adhésion d'un Control Plane

| # | Étape | Qui | Détail |
|---|---|---|---|
| 1 | Invitation | Administrateur de la fédération | Émet un **jeton d'adhésion à usage unique** et à courte durée, lié à un nom et à un périmètre. |
| 2 | Présentation | Control Plane candidat | Présente son document C1 signé et le jeton. |
| 3 | Vérification | Federation Plane | Contrôle signature, validité, versions de contrats communes ; **exécute le kit de conformité** sur les capacités annoncées (état `Pending` → vérifié). |
| 4 | Décision | Politique + administrateur | Admission automatique si la politique le permet, sinon approbation humaine. |
| 5 | Échange de confiance | Les deux parties | Échange des clés publiques de confiance de leurs domaines (fédération SPIFFE, [07](07-identite-confiance-securite.md)). |
| 6 | Publication | Federation Plane | Enregistre `ControlPlane` en `Admitted`, indexe les capacités, émet `controlplane.registered.v1`. |

Un Control Plane peut appartenir à **plusieurs fédérations** : chaque adhésion est une `TrustRelation` distincte,
avec ses propres politiques ; son identité reste la même, la confiance est accordée **par fédération**.

## Découverte des capacités

- Chaque Control Plane publie C2 ; le Federation Plane en conserve une **copie signée** avec sa date de fraîcheur.
- Une recherche porte sur une capacité, un périmètre et une version ; elle renvoie les Control Planes compétents
  **que l'appelant a le droit de solliciter** (la politique filtre avant de répondre).
- La copie est rafraîchie sur événement (`capability.changed.v1`) et par resynchronisation périodique complète.
  Une copie périmée est signalée comme telle ; elle ne sert **jamais** à contourner la vérification faite par le
  Control Plane cible au moment de l'appel.

## Routage : résoudre, puis laisser les Control Planes se parler

Mode par défaut : le Federation Plane **résout** (« pour cette capacité et ce périmètre, voici le Control Plane et son
adresse ») ; l'appelant s'adresse ensuite **directement** au Control Plane (C3). Cela évite un goulot d'étranglement
central et préserve l'autonomie. Un mode « mandataire » (le Federation Plane relaie) est possible pour les
environnements où les Control Planes ne peuvent pas se joindre directement. Dans les deux cas, **la décision
d'autoriser reste prise par le Control Plane cible**.

## Coordination des opérations multi-Control Planes

Quand une demande exige plusieurs Control Planes, le Federation Plane lance un **workflow Temporal** (une Saga) dont
chaque étape est une délégation C3. Il y ajoute dépendances entre étapes, compensations, reprise après interruption
et suivi de bout en bout. Détail : [09](09-operations-distribuees.md).

## Suspension et révocation

| Action | Effet | Rapidité |
|---|---|---|
| **Suspension** | Le routeur refuse toute nouvelle demande vers ce membre et depuis lui ; ses opérations en cours ne sont pas poursuivies ; réversible. | Immédiate (décision de politique) |
| **Révocation** | Retrait de la confiance : suppression de ses clés de confiance, retrait de l'index, `controlplane.revoked.v1`. | Immédiate pour les nouvelles connexions ; les certificats courts déjà émis expirent d'eux-mêmes. |

## Indisponibilité du Federation Plane

| Situation | Comportement |
|---|---|
| Un Control Plane en a besoin pour une **nouvelle** découverte | Il utilise sa copie signée locale du registre (fraîcheur indiquée). |
| Une opération multi-Control Planes était en cours | Temporal conserve l'avancement ; elle reprend au retour. |
| Un Control Plane administre son périmètre | **Rien ne change** : l'autonomie locale est entière (P-02). |
| Au retour | Les Control Planes se resynchronisent (rejeu des événements depuis le dernier point connu, puis comparaison complète périodique). |

Le Federation Plane lui-même est **répliqué** (au moins trois instances, voir [12](12-resilience-et-exploitation.md)).

## Topologies

Voir [03](03-architecture-generale.md). Par défaut, un Federation Plane par environnement de gouvernance (PO-03). Pour
les relations entre Control Planes non directement connectés, le chemin passe par un Federation Plane commun ou par
la hiérarchie ; la confiance n'est **pas transitive** : A fait confiance à B et B à C ne donne à A aucune confiance en C.

## Frontières organisationnelles

Un **périmètre** (ex. `univ-x/rh`) est un chemin hiérarchique : organisation, puis sous-ensemble. Les politiques
fédérées s'expriment sur des périmètres. Le Federation Plane ne connaît ni les cellules ni les tenants de la
Plateforme : un périmètre est une chaîne opaque pour lui (P-10).

## Exemple de bout en bout

Étapes 3 à 5 de [03](03-architecture-generale.md) : l'opérateur de la Plateforme interroge le Federation Plane. Celui-ci
consulte SpiceDB (« cet opérateur peut-il demander `object-storage` sur `univ-x/rh` ? »), cherche dans `CapabilityIndex` le
Control Plane Transversal, inscrit un `OperationRecord` et démarre la Saga. Les appels C3 partent ensuite
**directement** vers les Control Planes Transversal et Métier.
