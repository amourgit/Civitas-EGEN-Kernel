# 27 — Configuration multi-niveaux

*(Service : `config-service`, hors Kernel — voir
[22](22-ecosysteme-multi-solutions-et-frontiere.md). Décisions : D14, D19,
D22. Normatif pour les phases D (niveaux techniques) et F (niveaux complets)
de [29](29-plan-par-phase-ecosysteme.md).)*

## 27.1 — Principe

> Le code définit les **capacités** et les **règles générales**. La
> configuration détermine **comment** ces capacités sont utilisées dans chaque
> contexte. (vision, section 15)

Les choix d'applications, les affectations, les fonctionnalités activées, les
endpoints, les paramètres d'exploitation sont des **données explicites,
versionnées, tracées**, avec des périmètres et des priorités définis — jamais
des constantes dispersées dans le code.

## 27.2 — Les niveaux

Du moins spécifique au plus spécifique :

| N° | Niveau | Porté par | Exemple |
|---|---|---|---|
| 1 | `PLATFORM` | Toute la plateforme | Politique TLS minimale |
| 2 | `IMPLEMENTATION_VERSION` | Une version d'implémentation | Valeurs par défaut du produit |
| 3 | `INSTANCE` | Une instance | Adresse de connexion, taille des ressources |
| 4 | `CELL` | Une cellule (la racine = niveau « organisation ») | Fuseau horaire de l'organisation, politique de partage |
| 5 | `ASSIGNMENT` | Une affectation `(cellule, domaine, usage)` | Fonctionnalités disponibles pour cet usage |
| 6 | `USER` | Un utilisateur (`egen_uid`) | Préférences d'affichage |

**Le niveau 4 suit la hiérarchie des cellules** : pour une cellule `C`, les
valeurs de `[racine, …, parent(C), C]` s'appliquent dans cet ordre (la cellule
la plus proche l'emporte), sous réserve des verrous (27.4).

**Environnement.** Les niveaux 1 à 3 (« techniques ») sont **par
environnement** (`development`, `staging`, `production`…, le `TargetEnvironment`
du Kernel). Les niveaux 4 à 6 (« fonctionnels ») ne dépendent pas de
l'environnement.

## 27.3 — Schéma des clés (obligatoire)

Aucune valeur n'existe sans clé **déclarée**. Les clés sont déclarées par un
**schéma de configuration** (`configurationSchema`) porté par la version
d'implémentation ([24.3](24-catalogue-instances-affectations.md#243--implémentation-et-version-dimplémentation))
ou par la plateforme. Format : JSON Schema (2020-12) enrichi de mots-clés
`x-egen-*` :

| Mot-clé | Sens |
|---|---|
| `x-egen-levels` | Liste des niveaux où la clé **peut** être définie (obligatoire) |
| `x-egen-lockable` | La clé peut être **verrouillée** à un niveau (27.4) |
| `x-egen-secret-ref` | La valeur est une **référence** `{provider, path}`, jamais un secret |
| `x-egen-apply` | `HOT` (rechargé à chaud) ou `RESTART` (nouvelle génération du service) |
| `x-egen-description` | Description affichée dans les interfaces |

Une valeur est **validée** contre le schéma avant écriture (type, bornes,
niveaux autorisés). Une clé inconnue est **refusée**.

**Aucun secret en valeur (D19).** Une clé `x-egen-secret-ref` ne contient
qu'une référence ; pour une instance MANAGED elle est reportée dans le bloc
`configuration.secretsRefs` du manifeste ([06](06-service-manifest.md)) ; le
Kernel vérifie qu'elle se résout et ne lit jamais la valeur.

## 27.4 — Règles de priorité et de verrouillage

Pour une clé `k` et un contexte donné :

1. Rassembler les valeurs de `k` aux niveaux applicables (dans l'ordre 1 → 6,
   la chaîne de cellules étant parcourue de la racine à la cellule).
2. **Règle générale** : la valeur **la plus spécifique** l'emporte.
3. **Verrou** : une valeur peut être marquée `locked` (seulement si la clé est
   `x-egen-lockable`) par un auteur disposant du droit au niveau considéré.
   Une valeur verrouillée **interdit toute surcharge** par un niveau plus
   spécifique.
4. Si plusieurs verrous coexistent, le **moins spécifique** l'emporte (la
   politique la plus globale prime).
5. Une tentative d'écriture **sous** un verrou est **refusée** avec une erreur
   nommant le verrou et son auteur.

Conséquence voulue : une politique de sécurité imposée au niveau plateforme ou
organisation **ne peut jamais** être contournée par un service, une
affectation ou un utilisateur (vision, section 15).

**Droits d'écriture par niveau** : 1 → administrateur de plateforme ;
2 → administrateur de plateforme ; 3 → `can_configure` de l'instance
([25](25-identite-et-autorisations.md)) ; 4 → `can_configure_tools` de la
cellule (racine : `org_admin`) ; 5 → `can_configure_tools` de la cellule de
l'affectation ; 6 → l'utilisateur lui-même.

## 27.5 — Résolution effective

Deux résolutions, deux usages :

**Résolution technique d'une instance** `(instanceId, environment)` : niveaux
1 → 3. Résultat : un document clé/valeur **par instance et environnement**.
C'est ce document que `platform-operator-service` publie au Kernel (27.6).

**Résolution fonctionnelle** `(cell, domain, usage, user?)` : niveaux 1 → 6.
Consommée par les gateways de domaine et le portail, **à la demande** ; **jamais**
envoyée au Kernel.

Chaque résolution retourne, pour chaque clé, la **valeur** et son **origine**
(`niveau`, `cible`, `verrou ?`, `version`) : « pourquoi cette valeur » est
toujours consultable. API :

```
GET  /api/v1/config/schemas/{scope}                 schéma de clés
PUT  /api/v1/config/values                          écrire (niveau, cible, clé, valeur, locked, reason)
GET  /api/v1/config/values?level=&target=           lire
GET  /api/v1/config/resolve/instance/{id}?environment=
GET  /api/v1/config/resolve/functional?cell=&domain=&usage=&user=
POST /api/v1/config/changes:preview                 prévisualiser un changement
GET  /api/v1/config/history?level=&target=&key=
```

## 27.6 — Lien avec le Kernel (D19)

1. `config-service` **calcule** la résolution technique de chaque instance
   MANAGED. Il **n'appelle pas** le Kernel (D14).
2. `platform-operator-service` lit ce résultat et le **publie** via
   `PUT /api/v1/services/{id}/configuration?environment=` du Kernel
   (évolution A3, [13](13-api-et-contrats.md#publication-de-configuration--put-apiv1servicesidconfiguration)),
   avec `Idempotency-Key` dérivée de `(instanceId, environment, version de
   résolution)`.
3. Seules les clés **déclarées** dans `configuration.required` du manifeste de
   l'instance sont acceptées par le Kernel ; l'opérateur construit ce bloc à
   partir du schéma de la version d'implémentation (clés dont **tous** les
   niveaux autorisés sont techniques).
4. **Application d'un changement** : une clé `HOT` est rechargée par le
   service via son SDK/sidecar (`watch`, [07](07-ports-et-adapters.md#configuration-port)) ;
   une clé `RESTART` fait produire à l'opérateur une **nouvelle génération** du
   manifeste (annotation `platform.civitas.africa/config-version` mise à
   jour), donc une mise à jour gérée par le Kernel ([09](09-cycle-de-vie.md)).
   Le comportement exact de génération sur simple changement d'annotation est
   **vérifié par un test** en phase D, pas présumé.

## 27.7 — Traçabilité et contrôle avant application (D22)

- Chaque écriture crée une **version immuable** : auteur (`egen_uid`),
  horodatage, ancienne et nouvelle valeur, motif obligatoire, corrélation.
  Retour arrière = nouvelle version reprenant l'ancienne valeur (jamais de
  suppression d'historique).
- **Prévisualisation obligatoire** pour tout changement aux niveaux 1 à 3 en
  environnement `production`, ou qui touche une clé `locked` : `changes:preview`
  retourne les instances et cellules **impactées**, la différence de valeur
  effective, les clés `RESTART` concernées ; l'application exige le jeton de
  prévisualisation (anti-application à l'aveugle).
- Politique d'organisation optionnelle : `approval = NONE | ONE_APPROVER` pour
  les niveaux 4 et 5 ; si `ONE_APPROVER`, l'écriture reste `PENDING` jusqu'à
  approbation par un autre `manager`.

## 27.8 — Phases

- **Phase D** : niveaux 1 à 3, par environnement, schémas, historique,
  résolution technique, publication via l'opérateur. Pas de verrous
  hiérarchiques ni de niveaux fonctionnels.
- **Phase F** : niveaux 4 à 6, verrous, prévisualisation, approbation,
  résolution fonctionnelle. **Même service, même API** : aucune migration de
  données ni de contrat ([29](29-plan-par-phase-ecosysteme.md)).

## 27.9 — Ce que `config-service` ne fait jamais

- Stocker un secret en valeur, ou le renvoyer.
- Écrire directement dans le Kernel ou dans Consul.
- Appliquer silencieusement un changement qui contourne un verrou.
- Interpréter la signification métier d'une clé (il valide son type et ses
  niveaux, rien de plus).
