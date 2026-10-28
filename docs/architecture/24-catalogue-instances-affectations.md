# 24 — Catalogue, instances, activations et affectations

*(Services : `catalog-service` et `platform-operator-service`, hors Kernel —
voir [22](22-ecosysteme-multi-solutions-et-frontiere.md). Décisions : D09,
D10, D14, D15, D17, D22. Normatif pour la phase D de
[29](29-plan-par-phase-ecosysteme.md).)*

## 24.1 — Les entités et leurs relations

```
Domaine ──< Implémentation ──< Version d'implémentation ──< Instance
                                                              │
                                      Activation (instance × organisation)
                                                              │
                                   Affectation (instance × cellule × domaine × usage)
```

Lecture : un **domaine** compte plusieurs **implémentations** ; une
implémentation a plusieurs **versions** ; une version peut être déployée en
plusieurs **instances** ; une instance est **activée** pour une ou plusieurs
organisations (cellules racines) ; une instance activée est **affectée** à des
cellules de ces organisations.

## 24.2 — Domaine (`domain`)

| Champ | Règle |
|---|---|
| `id` | `[a-z][a-z0-9-]{0,62}` (ex. `agenda`, `vente`) ; immuable |
| `label`, `description` | Affichage |
| `contract` | `{ id, version }` du contrat de domaine ([26](26-contrats-de-domaine-et-connecteurs.md)) ; obligatoire |
| `status` | `ACTIVE` ou `RETIRED` |

Un domaine sans contrat n'existe pas : le catalogue refuse de l'enregistrer.

## 24.3 — Implémentation et version d'implémentation

### Implémentation (`implementation`)
`id` (ex. `radicale`), `domainId`, `label`, `vendor`, `license`, `homepage`,
`status` (`ACTIVE`/`RETIRED`).

### Version d'implémentation (`implementation_version`)

| Champ | Règle |
|---|---|
| `version` | SemVer ou version native du produit, textuelle, unique par implémentation |
| `artifact` | Référence d'image (avec **digest** obligatoire) pour une instance MANAGED ; absent pour un produit uniquement EXTERNAL |
| `deployment` | Ressources par défaut, ports, `healthCheck` (alimente le manifeste Kernel) |
| `requirements` | Prérequis : mécanisme d'identité (`oidc`, `saml`, `ldap`, `local-accounts`), stockage, base de données, dépendances applicatives (`dependsOn` : services et contraintes de version, traduits en `dependencies.services` du manifeste Kernel ; compatibilité vérifiée **avant** toute mise à jour) |
| `tenancyModel` | `SHARED_ISOLATED` (une instance peut servir plusieurs organisations en isolant leurs données) ou `SINGLE_TENANT` (une organisation par instance) |
| `capabilities` | Map `capabilityId → { supported: true/false, limits }` pour le contrat du domaine |
| `conformance` | `FULL` ou `PARTIAL` (D17), calculé par la suite de tests de contrat ([26](26-contrats-de-domaine-et-connecteurs.md)) |
| `waivers` | Pour `PARTIAL` : liste des capacités **obligatoires** manquantes, avec justification et approbateur |
| `connector` | `{ id, version }` du connecteur de la gateway du domaine |
| `ui` | Mode d'ouverture (`LINK`, `EMBED`, `API_ONLY`), point d'entrée (URL de base, chemins), contraintes d'intégration ([28](28-portail-et-integration-des-interfaces.md)) |
| `configurationSchema` | Schéma des clés de configuration ([27](27-configuration-multi-niveaux.md)) |
| `knownLimits` | Liste structurée de limites connues, affichée aux décideurs |
| `status` | `DRAFT` → `APPROVED` → `DEPRECATED` → `WITHDRAWN` |

**Cycle d'approbation.** `DRAFT` (enregistrée, non sélectionnable) →
`APPROVED` (réservé à l'administrateur de plateforme, après conformité
vérifiée) → `DEPRECATED` (plus de nouvelles instances ; les existantes
continuent) → `WITHDRAWN` (plus de mises à jour proposées ; migration
recommandée). **Seules les versions `APPROVED` sont sélectionnables** par un
responsable de cellule (D17).

**Le catalogue ne présente jamais deux implémentations comme
interchangeables** du seul fait qu'elles partagent un domaine : la sélection
affiche la conformité, les capacités optionnelles non supportées et les
limites connues.

## 24.4 — Instance (`instance`)

| Champ | Règle |
|---|---|
| `id` | UUID ; `serviceId` Kernel dérivé de façon **déterministe** : `app-<implémentation>-<8 premiers hex de l'id>` (jamais réutilisé) |
| `implementationVersionId` | Version **APPROVED** au moment de la création |
| `ownerCellId` | Cellule qui **administre** l'instance (périmètre de configuration et d'administration) |
| `mode` | `MANAGED` ou `EXTERNAL` (D15) |
| `name` | Libellé |
| `endpoints` | Pour `EXTERNAL` : URL(s) et point de contrôle de santé ; pour `MANAGED` : alimentés depuis la découverte du Kernel |
| `desired` | État désiré : `RUNNING` ou `STOPPED` ; version désirée ; réplicas min/max |
| `lifecycle` | État observé (24.5) |
| `health` | `OK`, `DEGRADED`, `UNKNOWN` |
| `kernelRef` | Pour `MANAGED` : `{ serviceId, generation }` |

### Mode MANAGED
Déployée par le Kernel. `platform-operator-service` traduit l'instance en
`ServiceManifest` : `metadata.team: platform-operator`, labels réservés
([06](06-service-manifest.md#labels-réservés-posés-par-la-plateforme)),
`deployment.image` (avec digest), ressources, `health`, `dependencies`,
références de secrets, bloc `events` si l'implémentation en publie.

### Mode EXTERNAL
Logiciel déjà exploité ailleurs (autre serveur, fournisseur, organisation
partenaire). **Le Kernel ne le gère pas en V1** (limite documentée en
[22.9](22-ecosysteme-multi-solutions-et-frontiere.md#229--limites-actuelles-du-kernel-à-connaître)).
Le catalogue en porte l'endpoint ; la santé est vérifiée périodiquement par
le **connecteur** (opération `healthCheck` du contrat). Une instance EXTERNAL
passe à `INSTALLED` quand cette vérification réussit. Elle **ne** produit
**aucun** `ServiceManifest`.

### Tenance
- `SINGLE_TENANT` : l'instance ne peut avoir **qu'une** activation (une seule
  organisation). Une activation supplémentaire est refusée.
- `SHARED_ISOLATED` : plusieurs activations autorisées ; l'isolation des
  données entre organisations est de la responsabilité du **logiciel** et
  validée par le connecteur (jamais présumée). Une version qui ne peut pas
  l'attester ne déclare pas ce modèle.

## 24.5 — Cycle de vie d'une instance et correspondance avec le Kernel

États du catalogue : `PLANNED`, `INSTALLING`, `INSTALLED`, `FAILED`,
`STOPPED`, `DECOMMISSIONING`, `DECOMMISSIONED`.

| Phase du Kernel (`ServiceStatus.phase`, [09](09-cycle-de-vie.md)) | État du catalogue | `health` |
|---|---|---|
| (aucune — pas encore déclaré) | `PLANNED` | `UNKNOWN` |
| `DECLARED`, `REGISTERED`, `CONFIGURED`, `DEPLOYING` | `INSTALLING` | `UNKNOWN` |
| `RUNNING` | `INSTALLED` | `OK` |
| `DEGRADED` | `INSTALLED` | `DEGRADED` |
| `UPDATING`, `ROLLED_BACK` | `INSTALLED` | selon la `Condition` `DeploymentReady` |
| `FAILED` | `FAILED` | `DEGRADED` |
| `STOPPING`, `STOPPED` | `STOPPED` | `UNKNOWN` |
| `REMOVING` | `DECOMMISSIONING` | `UNKNOWN` |
| `REMOVED` | `DECOMMISSIONED` | `UNKNOWN` |

`platform-operator-service` applique cette table **mécaniquement** et reporte
dans le catalogue les `Condition` du Kernel (type, raison, message) pour
diagnostic. Il **ne devine rien** : un état du Kernel inconnu de la table est
reporté tel quel avec `health: UNKNOWN` et déclenche une alerte.

### Opérations de retrait : quatre opérations distinctes, jamais confondues

| Opération | Effet | Ce qu'elle ne fait pas |
|---|---|---|
| **Arrêt** (`desired: STOPPED`) | Le Kernel arrête le service (arrêt gracieux, [09](09-cycle-de-vie.md)) | Ne supprime ni la configuration ni les affectations ni les données |
| **Retrait d'affectation** | L'outil disparaît pour la cellule concernée (et ses descendantes qui héritaient) | N'arrête rien |
| **Désactivation** (pour une organisation) | Plus aucune affectation de cette organisation n'est résolue | N'affecte pas les autres organisations |
| **Décommissionnement** | Après arrêt : suppression de la déclaration Kernel et de la configuration | Ne **détruit pas** les données : la disposition des données est une décision explicite (conserver / migrer / purger), journalisée |

Un décommissionnement exige que **toutes** les affectations et activations
aient été retirées au préalable ; sinon la requête est refusée avec la liste
des éléments bloquants.

## 24.6 — Activation (`activation`)

| Champ | Règle |
|---|---|
| `instanceId`, `rootCellId` | Une activation relie une instance à **une organisation** |
| `status` | `ACTIVE`, `SUSPENDED`, `REVOKED` |
| `activatedBy`, `activatedAt` | |
| `limits` | Optionnel : quotas propres à l'organisation (ex. nombre d'utilisateurs) |

Règles :
- Seul un `org_admin` de la racine peut activer, parmi les instances dont la
  version est `APPROVED` ou `DEPRECATED` (les `WITHDRAWN` ne s'activent plus).
- `SINGLE_TENANT` : au plus **une** activation non révoquée.
- Une instance **non activée** pour la racine R n'est **jamais** résolue pour
  aucune cellule de R, même si une affectation existait.

## 24.7 — Affectation (`assignment`)

| Champ | Règle |
|---|---|
| `id` | UUID |
| `cellId` | Cellule bénéficiaire |
| `domainId` | Domaine concerné |
| `usage` | Clé libre `[a-z][a-z0-9-]{0,30}`, défaut `default`. Permet **plusieurs outils d'un même domaine dans une même cellule** pour des usages différents |
| `instanceId` | Instance choisie, **ou nul** pour une **coupure d'héritage** (24.8) |
| `inherit` | Booléen (défaut `true`) : l'affectation s'applique aux cellules descendantes |
| `audience` | `SCOPE_MEMBERS` (défaut) ou `EXPLICIT` (24.9) |
| `status` | `ACTIVE` ou `SUSPENDED` |
| `chosenBy`, `chosenAt` | |

**Unicité (D10)** : une seule affectation par `(cellId, domainId, usage)`.

**Validité à la création** : l'instance est activée pour la racine de la
cellule ; son domaine égale `domainId` ; sa version est `APPROVED` ; l'auteur
détient `can_choose_tools` sur la cellule.

## 24.8 — Résolution de l'affectation effective

Pour une cellule `C`, un domaine `D`, un usage `U` :

1. Prendre la liste ordonnée des cellules `[C, parent(C), …, racine]` (une
   requête `ltree`).
2. Parcourir cette liste **de `C` vers la racine** ; retenir la **première**
   affectation `ACTIVE` pour `(D, U)` qui satisfait : appartient à la cellule
   courante et (c'est `C` elle-même **ou** `inherit = true`).
3. Si l'affectation retenue a `instanceId` nul : **coupure** → résultat vide
   (aucun outil pour `(C, D, U)`).
4. Sinon, vérifier : cellule `C` `ACTIVE` ; instance `INSTALLED` ; activation
   `ACTIVE` pour la racine ; sinon résultat vide **avec raison** (jamais un
   résultat silencieusement absent).
5. Le résultat porte l'**origine** (cellule qui porte l'affectation), pour
   l'explicabilité (« hérité de Faculté Sciences »).

Propriétés : le résultat est **calculé à la demande**, jamais matérialisé ;
déplacer une cellule ou changer une affectation parente change donc le
résultat sans migration. Une affectation d'un descendant **remplace**
l'héritée pour le même `(D, U)`.

## 24.9 — Qui peut utiliser (audience)

La décision « l'utilisateur `u` peut-il utiliser l'instance `I` pour `(C, D, U)` ? » est
rendue **uniquement** par le contrôle d'usage de `catalog-service` (D13) :

1. Résoudre l'affectation effective (24.8). Absente → refus (avec raison).
2. Selon l'`audience` de l'affectation retenue :
   - **`SCOPE_MEMBERS`** : `u` doit être **membre** (rôle `MEMBER`, `MANAGER`
     ou `ORG_ADMIN`) de `C` — ou, si l'affectation est héritée, de la
     cellule d'origine **ou** de toute cellule de son sous-arbre concernée par
     l'héritage. Calculé dans PostgreSQL (jointure appartenances × `ltree`),
     **sans** dépendre de la profondeur de résolution d'OpenFGA.
   - **`EXPLICIT`** : seuls les utilisateurs et les groupes (`cell#member`)
     listés dans l'affectation sont autorisés ; contrôlé par OpenFGA
     (`assignment#can_use`, [25](25-identite-et-autorisations.md)).
3. Les droits sur les **ressources métier** de l'application (lire tel
   calendrier) ne sont **pas** décidés ici : ils relèvent de la gateway du
   domaine et du moteur de politiques ([26](26-contrats-de-domaine-et-connecteurs.md)).

## 24.10 — `platform-operator-service`

**Rôle unique** : réconcilier l'état désiré des instances MANAGED du
catalogue avec le Kernel. Même esprit que [04](04-moteur-de-reconciliation.md) :

- **Level-triggered** : à chaque cycle, relire l'instance désirée et l'état
  Kernel, puis agir sur la différence. Jamais de décision basée sur le contenu
  d'un événement.
- **Idempotent** : chaque `Declare` avec `Idempotency-Key` stable dérivée de
  `(instanceId, generation désirée)` ; un `Declare` identique ne change rien.
- **Seul** détenteur du jeton Kernel (`team: platform-operator`), moindre
  privilège ([14](14-securite.md)).
- **Pas de blocage** : il n'attend jamais la fin d'un déploiement ; il
  interroge `GET /api/v1/services/{id}/status` périodiquement (intervalle
  configurable) et reporte.
- **Divergence visible** : tout écart persistant entre désiré et observé est
  exposé (métrique, condition de l'instance, alerte).
- **Orphelins** : à chaque cycle de resynchronisation, il compare la liste
  `GET /api/v1/services` (filtrée par équipe) aux instances du catalogue et
  signale — sans les supprimer — tout service Kernel `platform-operator`
  inconnu du catalogue.

## 24.11 — API (ressources normatives de `catalog-service`)

```
POST/GET/PUT /api/v1/domains                       (admin plateforme pour écrire)
POST/GET     /api/v1/implementations
POST/GET     /api/v1/implementations/{id}/versions
POST         /api/v1/implementations/{id}/versions/{v}/actions/approve|deprecate|withdraw
POST/GET     /api/v1/instances                      (création : owner de cellule)
POST         /api/v1/instances/{id}/actions/stop|start|update|decommission
GET          /api/v1/instances/{id}/impact       cellules, affectations et utilisateurs touchés par stop/update/decommission
POST/GET     /api/v1/instances/{id}/activations     (org_admin de la racine)
POST/GET/DELETE /api/v1/cells/{cellId}/assignments
GET    /api/v1/resolution/effective-assignment?cell=&domain=&usage=
GET    /api/v1/resolution/effective-assignments?cell=         (tous domaines/usages)
POST   /api/v1/resolution/check-use                   { user, cell, domain, usage } → allow/deny + raison
GET    /api/v1/me/applications                         (voir 28)
GET    /api/v1/audit/catalog?…
```

Mutations : `Idempotency-Key` obligatoire. Événements publiés :
`africa.civitas.platform.catalog.implementation-version-changed.v1`,
`…instance.lifecycle-changed.v1`, `…activation.changed.v1`,
`…assignment.changed.v1`.

## 24.12 — Ce que le catalogue ne fait jamais

- Il ne déploie rien et n'appelle jamais le Kernel.
- Il ne connaît pas le protocole d'une application (c'est le connecteur,
  [26](26-contrats-de-domaine-et-connecteurs.md)).
- Il ne duplique pas l'état observé du Kernel comme vérité : c'est une copie de
  lecture, réconciliée en permanence.
- Il ne présente pas un remplacement d'application comme transparent
  ([29](29-plan-par-phase-ecosysteme.md), phase H).
