# 09 — Opérations distribuées

## À quoi sert ce document
Spécifier comment une opération qui implique plusieurs Control Planes est coordonnée : Sagas, dépendances entre étapes,
échecs partiels, compensations, idempotence, reprise, opérations incertaines.

## Ce qu'il ne couvre pas
Une opération locale à un seul Control Plane (elle relève de la réconciliation, [05](05-kernel-de-reference.md)) et la fiche de
l'outil ([moteurs/temporal.md](moteurs/temporal.md)).

## Principe

Une **opération multi-Control Planes** est un **workflow Temporal** (une Saga) hébergé par le Federation Plane (D-02). Chaque
étape est une **activité** qui appelle un Control Plane par C3. Le workflow porte la logique de coordination ; **chaque Control
Plane reste responsable de l'exécution locale** de ce qu'on lui délègue (P-02, G-01).

## Anatomie d'un workflow

| Élément | Règle |
|---|---|
| Identité | `workflowId` = identifiant de l'`OperationRecord` ([06](06-federation-plane.md)). Deux demandes avec la même clé d'idempotence donnent **le même** workflow. |
| Étapes | Liste ordonnée ou graphe de dépendances ; une étape peut s'exécuter en parallèle si elle ne dépend d'aucune autre. |
| Clé d'idempotence d'une étape | `<workflowId>:<nom-de-l'étape>` : rejouer l'étape n'a pas d'effet double côté Control Plane (C3). |
| Compensation | Chaque étape qui a un effet possède une compensation : l'**opération inverse** déclarée par la capacité (`inverse`, voir [04](04-contrats-egen.md)) ou, à défaut, définie par le workflow. |
| Échéance | Chaque workflow a une échéance absolue. Passée, il compense ; il ne poursuit jamais indéfiniment (P-14). |
| Trace | Le contexte `traceparent` du workflow est transmis à chaque appel ; l'identifiant d'opération est un attribut de chaque span. |

## États et transitions

L'état d'un workflow se projette sur les phases de C4 :

| Situation du workflow | Phase C4 de l'opération |
|---|---|
| Étapes en cours | `Running` |
| Toutes les étapes réussies | `Succeeded` |
| Une étape échoue définitivement, compensation en cours | `PartiallySucceeded` |
| Compensation terminée | `Failed` (raison `Compensated`) |
| Une étape a un état inconnu | `Unknown` (jusqu'à observation) |
| Annulation demandée et autorisée | `Cancelled` (après compensation des effets déjà produits) |

## Échecs partiels et compensation

1. Une étape échoue après un **nombre plafonné** de tentatives (attente croissante entre deux essais).
2. Le workflow lance, **dans l'ordre inverse**, la compensation des étapes déjà réussies.
3. Une compensation qui échoue est retentée ; si elle échoue définitivement, l'opération reste `PartiallySucceeded` et une
   **alerte** est émise : une intervention humaine est requise. Rien n'est caché ni abandonné en silence.
4. L'état final et ses causes sont consignés (audit, [10](10-evenements-observabilite-audit.md)).

## Opération incertaine (P-15)

Une étape est **incertaine** quand on ignore si elle a eu lieu (délai dépassé, réponse perdue, Control Plane injoignable). Règle :

1. Ne **jamais** conclure à l'échec ni au succès.
2. **Observer** : relire l'opération du Control Plane par sa clé d'idempotence (C3/C4).
3. Si elle existe : adopter son état réel. Si elle n'existe pas : **rejouer** l'étape (la clé garantit l'absence de doublon).
4. Si le Control Plane reste injoignable jusqu'à l'échéance : compenser ce qui peut l'être et signaler l'incertitude restante.

## Reprise après interruption

Temporal conserve l'avancement et reprend l'exécution après la panne d'un exécutant ou du Federation Plane. Les activités étant
idempotentes, une reprise ne produit pas de doublon. Les Control Planes poursuivent leur travail local pendant l'interruption.

## Dépendances entre étapes

Une dépendance se déclare par ordre dans le workflow : l'étape C n'a lieu qu'après A et B. Les **références** (jamais des valeurs)
produites par une étape sont transmises aux suivantes (identifiant du stockage créé, adresse du sujet de messagerie).

## Concurrence et autorité

Deux workflows ne doivent pas agir sur la **même ressource** en même temps : la ressource a une seule autorité (P-04) ; le
Control Plane autorité sérialise ses opérations par ressource et refuse la seconde avec `409` (raison `ResourceBusy`).

## Évolution des workflows

Modifier la définition d'un workflow qui a des exécutions en cours exige le **versionnement** (mécanisme de Temporal, VR-06) :
les exécutions en cours se terminent selon l'ancienne version, les nouvelles utilisent la nouvelle. Un workflow modifié sans
versionnement est refusé en revue.

## Catalogue initial des workflows

| Workflow | Rôle | Phase |
|---|---|---|
| `provision-application` | Exemple de [03](03-architecture-generale.md) : capacités puis exécution | R4.5 |
| `decommission-application` | Retrait ordonné : arrêt, libération des capacités, nettoyage | R4.6 |
| `rotate-trust` | Renouvellement des relations de confiance d'un membre | R6 |
| `replace-controlplane` | Migration lors du remplacement d'un Control Plane ([12](12-resilience-et-exploitation.md)) | R6 |

## Exemple de bout en bout

Reprise de [03](03-architecture-generale.md) : A (stockage) et B (messagerie) réussissent ; C (exécution) échoue après ses
tentatives. Le workflow demande au Control Plane Transversal `delete-bucket` puis `delete-topic` (les `inverse` déclarés). Pendant
ce temps l'opération est `PartiallySucceeded`. Quand les deux suppressions sont confirmées, elle devient `Failed` (`Compensated`)
et le portail l'indique à Marie avec la cause. Si, à l'étape C, le Control Plane Métier ne répond plus du tout : l'étape est
`Unknown`, le workflow observe, rejoue si l'opération n'existe pas, sinon adopte son état.
