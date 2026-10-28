# 23 — Cellules : la structure organisationnelle générique

*(Service : `cell-registry-service`, hors Kernel — voir
[22](22-ecosysteme-multi-solutions-et-frontiere.md). Décisions : D03 à D08,
D11, D22. Ce document est normatif pour la phase B de
[29](29-plan-par-phase-ecosysteme.md).)*

## 23.1 — Principe

Une organisation, une faculté, un service informatique, une cellule projet :
pour EGEN, **tout est une cellule**. Il n'existe **aucun** autre concept
structurel. Ce qui distingue un « service RH » d'une « faculté » n'est pas une
classe de code, mais :

- son **type de cellule** (une donnée, définie par la plateforme ou par
  l'organisation) ;
- ses **attributs**, validés par le schéma de ce type ;
- sa **place** dans l'arbre ;
- ses **affectations** d'outils ([24](24-catalogue-instances-affectations.md)).

Ajouter un type de cellule, un niveau hiérarchique ou un attribut **ne
nécessite aucune modification de code** : c'est de la configuration de
données, validée.

## 23.2 — Modèle logique

### Cellule (`cell`)

| Champ | Type | Règle |
|---|---|---|
| `id` | UUID (v7) | Immuable. |
| `parentId` | UUID ou nul | Nul **uniquement** pour une racine (organisation). Après création, ne peut être modifié que par l'opération `move` (voir 23.5). |
| `rootId` | UUID | Identifiant de la racine de l'arbre ; égal à `id` pour une racine. Immuable : une cellule ne change jamais de racine (D04). |
| `typeId` | texte | Référence à un type de cellule visible dans la portée de `rootId` (23.3). |
| `code` | texte | Court identifiant humain, `[a-z0-9][a-z0-9-]{0,62}`. **Unique parmi les frères** (même `parentId`). Pour une racine : unique globalement. |
| `name` | texte | Libellé affiché, modifiable. |
| `status` | énumération | `ACTIVE`, `SUSPENDED`, `ARCHIVED` (23.6). |
| `attributes` | document JSON | Validé par le schéma du type (23.3). |
| `labels` | map texte→texte | Libres, non interprétées. |
| `path` | `ltree` | **Dérivé** : suite des identifiants (`c` + hexadécimal sans tirets) de la racine à la cellule. Jamais saisi, toujours recalculé. |
| `depth` | entier | Dérivé de `path`. |
| `version` | entier | Verrouillage optimiste (incrémenté à chaque écriture). |
| `createdAt`, `updatedAt`, `createdBy`, `updatedBy` | | `*By` = `egen_uid`. |

**Racine = organisation = tenant.** La frontière d'isolation des données et
des activations ([24](24-catalogue-instances-affectations.md)) est la racine :
aucune requête ne traverse deux racines, sauf opération explicite de
plateforme réservée aux administrateurs de plateforme.

### Type de cellule (`cell_type`)

| Champ | Type | Règle |
|---|---|---|
| `id` | texte | `[a-z][a-z0-9-]{0,62}`. Unique **dans sa portée**. |
| `scope` | `PLATFORM` ou `ROOT:<rootId>` | Un type `ROOT:R` n'est utilisable que dans l'arbre de R. Un type `PLATFORM` l'est partout. |
| `label` | texte | Affichage. |
| `canBeRoot` | booléen | Le type peut-il être celui d'une racine ? |
| `allowedParentTypes` | ensemble d'identifiants de types | Types autorisés pour le parent. Valeur spéciale `*` = n'importe quel type. Ignoré si le type n'est utilisable qu'en racine. |
| `attributeSchema` | JSON Schema (draft 2020-12) | Valide `attributes`. Objet, `additionalProperties` explicite. |
| `version` | entier | Incrémenté à chaque changement de schéma. |
| `status` | `ACTIVE` ou `RETIRED` | Un type `RETIRED` n'accepte plus de nouvelles cellules ; les existantes restent valides. |

**Unicité et collisions (D05).** La création d'un type `ROOT:R` dont
l'identifiant existe déjà en portée `PLATFORM` est **refusée**, et
inversement. Cela supprime toute ambiguïté de résolution : un identifiant de
type désigne un seul type dans un arbre donné.

**Qui définit quoi.** Un administrateur de plateforme définit les types
`PLATFORM`. Un `org_admin` de la racine R définit les types `ROOT:R`
([25](25-identite-et-autorisations.md)).

### Appartenance (`membership`)

| Champ | Type | Règle |
|---|---|---|
| `cellId` | UUID | |
| `egenUid` | UUID | Identité permanente ([25](25-identite-et-autorisations.md)). |
| `role` | `MEMBER`, `MANAGER`, `ORG_ADMIN` | `ORG_ADMIN` n'est accordé que sur une **racine**. |
| `since`, `until` | dates | `until` optionnel (appartenance temporaire). |
| `addedBy` | UUID | |

Un utilisateur peut avoir plusieurs appartenances dans plusieurs cellules,
éventuellement de racines différentes (intervenant dans plusieurs
organisations). **Aucun nouveau compte** n'est créé pour cela (D12).

**Héritage (D11).**
- `MEMBER` **ne s'hérite pas** : être membre d'une faculté ne rend pas membre
  de chacun de ses départements.
- `MANAGER` et `ORG_ADMIN` **s'héritent vers le bas** : un manager d'une
  cellule gère toutes ses descendantes ; un `ORG_ADMIN` gère tout l'arbre de
  sa racine.

## 23.3 — Exemple (illustratif)

Types : `universite` (`canBeRoot: true`), `faculte` (parent : `universite`),
`departement` (parent : `faculte`), `service` (parent : `*`),
`cellule-projet` (parent : `*`). Aucun n'est codé : ce sont des lignes de
données.

```
universite « Université X »                         ← racine = tenant
├── faculte « Sciences »
│   ├── departement « Informatique »
│   └── departement « Physique »
├── service « Ressources humaines »
│   └── cellule-projet « Recrutement 2027 »
└── service « Informatique et réseaux »
```

Schéma d'attributs du type `service` (extrait) :

```json
{
  "type": "object",
  "properties": {
    "responsableEmail": { "type": "string", "format": "email" },
    "campus": { "type": "string" }
  },
  "required": ["responsableEmail"],
  "additionalProperties": false
}
```

Une autre organisation peut définir `direction`, `bureau`, `unite` sans que
l'Université X ne le voie, ni qu'aucun code ne change.

## 23.4 — Stockage

- **PostgreSQL**, extension `ltree` (D07). Une seule technique, pas de table de
  fermeture, pas de base de graphe.
- Index GiST sur `path` ; index unique `(parentId, code)` ; index sur
  `rootId`.
- Requêtes de référence, toutes indexées : ancêtres (`path @> x`),
  descendants (`path <@ x`), enfants directs, sous-arbre d'une racine.
- **Garde-fou de profondeur (D06)** : `maxDepth`, configurable, défaut **64**.
  Une création ou un déplacement qui dépasserait la limite est refusé avec une
  erreur nommée. Ce n'est **pas** une limite du modèle : c'est une protection
  d'exploitation, relevable par configuration.
- Le **verrouillage optimiste** (`version`) protège toute écriture concurrente.
  Un `move` verrouille la cellule déplacée **et** l'ancien et le nouveau
  parent dans une seule transaction.

## 23.5 — Opérations et règles

| Opération | Règles de validation (toutes obligatoires) | Droit requis |
|---|---|---|
| Créer une **racine** | Type avec `canBeRoot`; `code` globalement unique | Administrateur de plateforme |
| Créer une cellule enfant | Type visible dans la portée de la racine ; type du parent ∈ `allowedParentTypes` ; `attributes` valides ; `code` unique parmi les frères ; `depth ≤ maxDepth` ; parent `ACTIVE` | `manager` ou `org_admin` du parent |
| Modifier nom, attributs, labels | `attributes` valides pour la version courante du schéma ; `version` fournie | `manager` de la cellule |
| **Déplacer** (`move`) | Même `rootId` ; pas de cycle (le nouveau parent n'est pas un descendant) ; type compatible avec le nouveau parent ; `depth` résultante (cellule + sous-arbre) ≤ `maxDepth` ; nouveau parent `ACTIVE` ; `code` libre chez les nouveaux frères | `manager` **de l'ancien et du nouveau parent** |
| Suspendre / réactiver | Une cellule suspendue conserve ses données ; ses affectations ne sont plus résolues ([24](24-catalogue-instances-affectations.md)) | `manager` |
| Archiver | Aucun descendant `ACTIVE` (sinon refus, ou archivage en cascade **explicitement** demandé) ; affectations actives listées dans la réponse | `manager` |
| Purger | Cellule `ARCHIVED` depuis plus que la rétention (configurable) ; aucune affectation ; **jamais** automatique | Administrateur de plateforme |
| Gérer les appartenances | `ORG_ADMIN` uniquement sur racine ; l'ajouteur détient un rôle ≥ au rôle accordé sur la cellule | `manager` ou `org_admin` |
| Définir / modifier un type | Un changement de schéma incompatible avec des cellules existantes est **refusé** tant qu'une migration n'est pas fournie (23.7) | `org_admin` (ROOT) ou administrateur de plateforme |

**Effet d'un `move` sur le reste.** Les affectations héritées sont
recalculées par résolution à la demande ([24](24-catalogue-instances-affectations.md)),
jamais stockées matérialisées : un déplacement ne réécrit donc aucune
affectation. La projection OpenFGA (`parent`) est mise à jour par l'outbox.

## 23.6 — États d'une cellule

| État | Signification | Effets |
|---|---|---|
| `ACTIVE` | Nominal | Tout autorisé |
| `SUSPENDED` | Gel temporaire | Lecture seule ; plus de création d'enfants ; affectations non résolues (les utilisateurs ne voient plus ses outils) ; descendants **non** affectés par défaut (option `cascade` explicite) |
| `ARCHIVED` | Retirée de la vie courante | Invisible des vues par défaut ; conservée pour audit ; reste dans les chemins des descendants archivés |

## 23.7 — Évolution d'un schéma de type

1. Un changement **compatible** (ajout d'une propriété optionnelle, assouplissement)
   incrémente `version` ; les cellules existantes restent valides.
2. Un changement **incompatible** (propriété obligatoire ajoutée, type
   modifié) est refusé tant que **toutes** les cellules du type ne valident pas
   le nouveau schéma. Une migration d'attributs (opération dédiée, journalisée)
   permet d'y parvenir par lots.
3. Chaque cellule enregistre la `version` du schéma avec laquelle elle a été
   validée la dernière fois.

## 23.8 — API (ressources normatives)

Les chemins et la sémantique sont fixés ici ; les schémas JSON exacts sont un
**livrable de début de phase B** (fichier OpenAPI relu avant tout code).

```
POST   /api/v1/cell-types                       définir un type
GET    /api/v1/cell-types?rootId=               types visibles dans une racine
PUT    /api/v1/cell-types/{id}                  modifier (règles 23.7)
POST   /api/v1/cells                            créer (racine ou enfant)
GET    /api/v1/cells/{id}                       lire
PATCH  /api/v1/cells/{id}                       nom, attributs, labels (If-Match: version)
GET    /api/v1/cells/{id}/children              enfants directs
GET    /api/v1/cells/{id}/ancestors             chaîne jusqu'à la racine
GET    /api/v1/cells/{id}/subtree               descendants (pagination obligatoire)
POST   /api/v1/cells/{id}/actions/move          { newParentId }
POST   /api/v1/cells/{id}/actions/suspend|reactivate|archive
POST   /api/v1/cells/{id}/memberships           ajouter
DELETE /api/v1/cells/{id}/memberships/{membershipId}
GET    /api/v1/cells/{id}/memberships           lister
GET    /api/v1/users/{egenUid}/memberships      cellules d'un utilisateur
GET    /api/v1/audit/cells?cellId=&from=&to=    journal d'audit (lecture)
```

Mutations : `Idempotency-Key` obligatoire, comme au Kernel
([13](13-api-et-contrats.md)). Erreurs : `400` règle de validation violée
(message nommant la règle), `403` droit insuffisant, `404` inconnu,
`409` conflit de version ou d'unicité.

## 23.9 — Événements publiés (via la messagerie du Kernel)

Types déclarés dans `events.publishes` du manifeste de `cell-registry-service`
(donc un flux par type, [06](06-service-manifest.md)) :

`africa.civitas.platform.cell.created.v1`, `…cell.updated.v1`,
`…cell.moved.v1`, `…cell.status-changed.v1`,
`…cell.membership-changed.v1`, `…cell-type.changed.v1`.

Le contenu (`data`) porte les identifiants et la version ; les consommateurs
**relisent l'état** (principe level-triggered, [04](04-moteur-de-reconciliation.md)),
ils ne reconstruisent jamais l'état à partir du contenu d'un événement.

## 23.10 — Audit (D22)

Chaque écriture ajoute une ligne **append-only** : `qui` (`egen_uid`), `quand`,
`quoi` (opération), `cible`, `avant`/`après` (documents), `corrélation`
(`traceparent`). Aucune ligne n'est jamais modifiée ni supprimée par l'application.

## 23.11 — Ce que `cell-registry-service` ne fait jamais

- Il ne sait pas quelles applications existent ni lesquelles sont affectées.
- Il ne décide d'aucun droit **sur une application** ou une ressource métier.
- Il ne parle jamais au Kernel.
- Il ne limite pas la structure à un modèle universitaire : aucun type n'est
  prédéfini par le code (les types d'exemple sont de la donnée d'amorçage
  optionnelle).
