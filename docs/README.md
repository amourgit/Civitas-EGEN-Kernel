# Documentation EGEN Kernel — sommaire et règles

## À quoi sert ce document
Donner le sommaire de la documentation, l'ordre de lecture selon ce que l'on veut faire, les règles de
rédaction à respecter et les conventions d'identifiants.

## Ce qu'il ne couvre pas
Le contenu technique : il est dans les documents numérotés ci-dessous.

## Sommaire

| N° | Document | Réponse à la question |
|---|---|---|
| 00 | [État et reprise](00-etat-et-reprise.md) | Où en est-on ? Que fait-on ensuite ? **Seul endroit où figure un statut.** |
| 01 | [Vision et périmètre](01-vision-et-perimetre.md) | Qu'est-ce qu'EGEN, qu'est-ce qu'il n'est pas ? |
| 02 | [Principes et décisions](02-principes-et-decisions.md) | Quelles règles et quelles décisions ? |
| 03 | [Architecture générale](03-architecture-generale.md) | Qui fait quoi, dans quel ordre ? Exemple de bout en bout. |
| 04 | [Contrats EGEN (C1 à C6)](04-contrats-egen.md) | Comment deux Control Planes se parlent-ils ? |
| 05 | [Kernel de référence](05-kernel-de-reference.md) | De quoi est fait un Control Plane de référence ? |
| 06 | [Federation Plane](06-federation-plane.md) | Comment relier des Control Planes ? |
| 07 | [Identité, confiance, sécurité](07-identite-confiance-securite.md) | Qui est qui, qui peut quoi, comment révoquer ? |
| 08 | [Moteurs spécialisés](08-moteurs-specialises.md) | Quel moteur fait quoi ? Où est Nomad ? |
| 09 | [Opérations distribuées](09-operations-distribuees.md) | Comment coordonner plusieurs Control Planes ? |
| 10 | [Événements, observabilité, audit](10-evenements-observabilite-audit.md) | Comment suivre ce qui se passe ? |
| 11 | [Écosystèmes et intégrations](11-ecosystemes-et-integrations.md) | Écosystèmes, frontière Plateforme, connecteurs, agents IA. |
| 12 | [Résilience et exploitation](12-resilience-et-exploitation.md) | Pannes, bootstrap, cycle de vie, remplacement. |
| 13 | [Tests et conformité](13-tests-et-conformite.md) | Comment vérifie-t-on ? Kit de conformité. |
| 14 | [Plan de développement](14-plan-de-developpement.md) | Phases R0 à R7, étapes, critères de sortie. |
| 15 | [Matrice de couverture](15-matrice-de-couverture.md) | Chaque question des documents fondateurs : où est la réponse ? |
| 16 | [Risques, vérifications, points ouverts](16-risques-et-verifications.md) | Ce qui n'est pas vérifié ou pas décidé. |
| — | [Glossaire](glossaire.md) · [Références](references.md) | Définitions ; sources consultées et dates. |
| — | [Moteurs : fiches](moteurs/README.md) | Une fiche par moteur spécialisé. |
| — | [Fondations](fondations/) | Les deux documents fondateurs (vision, inventaire des questions). |

## Ordre de lecture

- **Je découvre le projet** : README racine, 01, 03, glossaire.
- **Je reprends une session de travail** : 00, puis la phase en cours dans 14, puis 16.
- **Je conçois une interface entre systèmes** : 04, 06, 07, 09.
- **Je prépare une phase de développement** : 14 (la phase), les documents qu'elle cite, 13 (comment vérifier).
- **Je dois choisir ou remplacer un moteur** : 08 puis la fiche du moteur dans `moteurs/`.

## Règles de rédaction (obligatoires)

1. **L'état voulu, pas l'historique.** Chaque document décrit le système tel qu'il est décidé aujourd'hui.
   Ni « avant on faisait X », ni récit de réforme. L'historique est dans git.
2. **Une seule source de vérité par information.** Le statut d'une phase n'existe que dans le document 00.
   Une vérification en attente n'existe que dans le document 16. Ailleurs, on renvoie, on ne recopie pas.
3. **Rien d'inventé.** Un fait externe (version, licence, comportement d'un outil) est vérifié dans la source
   officielle et daté dans [references.md](references.md), ou marqué « **à vérifier** » avec une entrée VR-nn
   dans le document 16. Un constat sur du code se vérifie dans le code.
4. **Aucun point ouvert tranché en silence.** Une question non décidée devient un point ouvert PO-nn
   (énoncé, options, impact, décideur) dans le document 16.
5. **Chaque terme technique est défini à sa première apparition** dans un document, en une phrase simple.
6. **Chaque document d'architecture contient un exemple concret de bout en bout** : l'Université X, le Service RH,
   Marie (membre du Service RH), l'agenda Radicale.
7. **Vocabulaire cohérent** avec le [glossaire](glossaire.md). Le mot « passerelle » seul est interdit : on écrit
   « pont de fédération », « passerelle de domaine » (Plateforme), « Provider », « connecteur » selon le cas.
8. **Style** : français, phrases courtes, tableaux pour comparer, schémas en texte pour les flux.
9. **Chaque document commence par** « À quoi sert ce document » et « Ce qu'il ne couvre pas ».
10. **Liens relatifs valides** ; numérotation des documents conservée ; entre dépôts, on cite « dépôt + chemin ».

## Conventions d'identifiants

| Préfixe | Sens | Où |
|---|---|---|
| `D-nn` | Décision verrouillée | 02 |
| `P-nn`, `G-nn` | Principe, garde-fou | 02 |
| `Rn`, `Rn.m` | Phase, étape de développement | 14 |
| `C1` à `C6` | Contrat d'interopérabilité | 04 |
| `PO-nn` | Point ouvert (décision à prendre) | 16 |
| `VR-nn` | Vérification en attente | 16 |
| `RK-nn` | Risque | 16 |

## Où vit quoi (deux documentations séparées)

- **Ce dépôt (Civitas-EGEN-Kernel)** : le Kernel, la fédération, les contrats. Rien de métier.
- **La documentation de la Plateforme** (cellules, catalogue, portail, connecteurs…) vit dans son propre dépôt
  (par défaut Civitas-EGEN-Business, dossier `docs/plateforme/`). La frontière est décrite dans
  [11](11-ecosystemes-et-integrations.md). Les liens entre dépôts se font par « nom du dépôt + chemin ».
