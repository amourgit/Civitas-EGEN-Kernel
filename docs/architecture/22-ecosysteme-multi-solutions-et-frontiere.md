# 22 — Écosystème EGEN multi-solutions : vision, frontière Kernel / Projet Métier, registre des décisions

*(Document normatif. Il fixe **ce qui est décidé** pour l'écosystème construit
**au-dessus** du Kernel. Il ne modifie aucun des dix garde-fous de
[02](02-principes-fondamentaux.md) : il montre où chaque exigence de la
vision EGEN vient se brancher sur eux. Les documents [23](23-cellules.md) à
[29](29-plan-par-phase-ecosysteme.md) en détaillent chaque partie.)*

## 22.1 — Objet et statut

La vision EGEN décrit un écosystème où :

- plusieurs applications d'un **même domaine** (agenda, vente, documents…)
  coexistent, y compris au sein d'une même organisation ;
- chaque service d'une organisation **choisit** ses outils, sans imposer ce
  choix aux autres ;
- l'**identité** de l'utilisateur est unique à l'échelle de l'écosystème ;
- la personnalisation passe par la **configuration**, pas par des variantes
  du code source ;
- on peut **ajouter, remplacer, migrer** une application sans reconstruire
  l'ensemble.

Cette vision est entièrement réalisable, **mais pas dans le Kernel**. Le
Kernel est un plan de contrôle technique (Control/Composition Plane) : il
sait déclarer, déployer, découvrir, observer et réconcilier des **services**.
Il ne sait rien — et ne doit jamais savoir — ce qu'est une organisation, un
service d'organisation, un utilisateur, un droit métier ou un domaine
fonctionnel ([01](01-vision-et-positionnement.md), garde-fou n°1).

Conséquence structurante, décision **D01** (voir [22.8](#228--registre-des-décisions)) :

> **Tout ce que la vision EGEN ajoute au Kernel vit dans des services
> indépendants, déclarés au Kernel par des `ServiceManifest` ordinaires et
> gouvernés par lui comme n'importe quel autre service. Ces services
> consomment l'API publique du Kernel ([13](13-api-et-contrats.md)) ; jamais
> l'inverse.**

C'est exactement le modèle déjà retenu pour le système d'agents
([21](21-systeme-agents-et-gateway.md)), dont l'écosystème partage l'IAM et le
moteur de politiques (décision **D21**).

## 22.2 — Résumé de la vision, en termes opérationnels

| Exigence de la vision | Traduction opérationnelle | Où |
|---|---|---|
| Identité commune, unique | Un fournisseur d'identité (Keycloak) ; identifiant interne immuable `egen_uid` ; aucun service n'utilise le `sub` du jeton | [25](25-identite-et-autorisations.md) |
| Organisations, services, hiérarchies flexibles | **Cellule générique** : un seul concept, types définis en données, profondeur sans limite de modèle | [23](23-cellules.md) |
| Plusieurs applications d'un même domaine | Distinction **domaine / implémentation / instance** ; catalogue central | [24](24-catalogue-instances-affectations.md) |
| Chaque service choisit ses outils | **Affectation** instance ↔ cellule, avec héritage descendant et coupure explicite | [24](24-catalogue-instances-affectations.md) |
| Choisir ≠ configurer ≠ administrer ≠ utiliser | Quatre niveaux d'autorisation distincts + droits de plateforme | [25](25-identite-et-autorisations.md) |
| Coexistence sans changer le reste | **Contrats de domaine** + connecteurs isolés, niveaux de conformité | [26](26-contrats-de-domaine-et-connecteurs.md) |
| Personnalisation par configuration | **Configuration multi-niveaux** avec priorités, verrous, historique | [27](27-configuration-multi-niveaux.md) |
| Portail adapté à chaque utilisateur | Portail alimenté par la **résolution** des affectations effectives | [28](28-portail-et-integration-des-interfaces.md) |
| Pas de partage automatique des données | Aucune synchronisation implicite ; **règles d'échange explicites** | [26](26-contrats-de-domaine-et-connecteurs.md), [29](29-plan-par-phase-ecosysteme.md) (phase H) |
| Remplacement, migration | Procédure de remplacement outillée, jamais « transparente » par défaut | [29](29-plan-par-phase-ecosysteme.md) (phase H) |
| Multitenant, isolation | La **cellule racine** est la frontière de tenant | [23](23-cellules.md), [24](24-catalogue-instances-affectations.md) |

## 22.3 — La frontière Kernel / Projet Métier

### Règle

> **Le Kernel gère des *services* (technique). Le Projet Métier gère des
> *organisations, des outils et des gens* (sens). Le Kernel n'importe jamais
> un concept du Projet Métier ; le Projet Métier n'importe jamais un type
> interne du Kernel, il n'en consomme que l'API REST publique.**

### Répartition complète

| Responsabilité | Kernel | Projet Métier |
|---|---|---|
| Déclarer, déployer, mettre à jour, arrêter un conteneur/binaire | **Oui** (Nomad via adapter) | Non — demande au Kernel |
| Découvrir les instances saines d'un service | **Oui** (Consul via adapter) | Consomme `GET /api/v1/discover/{name}` |
| Ordonnancer par dépendances techniques | **Oui** | Non |
| Transporter des événements techniques | **Oui** (NATS via adapter) | Publie / consomme des événements ordinaires |
| Orchestrer une saga technique avec compensation | **Oui** (moteur de workflow) | Définit des workflows, ne réimplémente pas le moteur |
| Publier une configuration technique clé/valeur d'un service | **Oui** (`ConfigurationPort`, API de publication — [13](13-api-et-contrats.md), évolution A3) | Calcule les valeurs ; ne les stocke pas ailleurs que chez lui |
| Référencer un secret | **Oui** (référence seulement, Vault) | Fournit les chemins ; ne lit jamais les valeurs dans le Kernel |
| Savoir ce qu'est une cellule, une organisation, un tenant | **Jamais** | **Oui** ([23](23-cellules.md)) |
| Catalogue de domaines, d'implémentations, d'instances, d'affectations | **Jamais** | **Oui** ([24](24-catalogue-instances-affectations.md)) |
| Identité, authentification, appartenances, droits métier | **Jamais** | **Oui** ([25](25-identite-et-autorisations.md)) |
| Contrat d'un domaine (agenda, vente…) et connecteurs | **Jamais** | **Oui** ([26](26-contrats-de-domaine-et-connecteurs.md)) |
| Fusion de configuration par organisation/cellule/utilisateur | **Jamais** | **Oui** ([27](27-configuration-multi-niveaux.md)) |
| Portail, présentation des applications | **Jamais** | **Oui** ([28](28-portail-et-integration-des-interfaces.md)) |

### Ce que le Kernel fait pour l'écosystème, sans le connaître

1. Il **stocke et restitue sans les interpréter** les `labels` et `annotations`
   du manifeste. L'écosystème y dépose des références de traçabilité (clés
   réservées, [06](06-service-manifest.md#labels-réservés-posés-par-la-plateforme)).
2. Il **authentifie et autorise** ses appelants selon `metadata.team` et le
   rôle administrateur ([14](14-securite.md)). L'écosystème s'y présente avec
   **une seule identité technique** (décision **D14**).
3. Il **expose la configuration technique** d'un service via une API de
   publication (évolution A3, [19](19-feuille-de-route.md)).
4. Il **reste inchangé** quand une cellule est créée, une affectation modifiée
   ou un domaine ajouté : ces opérations n'atteignent le Kernel que sous la
   forme d'un `Declare` ou d'un `publish` de configuration d'**instance**.

## 22.4 — Les composants de l'écosystème

Tous sont des services EGEN ordinaires (`ServiceManifest`), indépendants,
polyglottes en droit. Les **identifiants de service** ci-dessous sont
normatifs : ce sont les `ServiceId` déclarés au Kernel.

| `ServiceId` | Rôle (une seule responsabilité) | Ne fait jamais | Détail | Phase |
|---|---|---|---|---|
| `cell-registry-service` | Arbre des cellules, types de cellules, appartenances, journal d'audit de structure | N'interprète pas les applications ; ne décide pas des droits métier d'une application | [23](23-cellules.md) | B |
| `catalog-service` | Domaines, implémentations, versions, instances, activations, affectations ; **résolution** des affectations effectives et contrôle d'usage | Ne parle jamais au Kernel (aucun jeton Kernel) ; ne déploie rien | [24](24-catalogue-instances-affectations.md) | D |
| `platform-operator-service` | **Seul** détenteur de l'identité Kernel : traduit les instances désirées du catalogue en `ServiceManifest`, lit leur statut, le reporte | Ne décide d'aucune affectation ; n'a aucune logique de domaine | [24](24-catalogue-instances-affectations.md) | D |
| `config-service` | Configuration multi-niveaux : schémas de clés, valeurs par niveau, verrous, historique, résolution effective | Ne stocke aucun secret (références seulement) ; n'appelle **jamais** le Kernel (c'est l'opérateur qui publie) | [27](27-configuration-multi-niveaux.md) | D (niveaux techniques), F (niveaux complets) |
| `domain-gateway-<domaine>` (ex. `domain-gateway-agenda`) | API canonique d'un domaine ; route chaque appel vers l'instance effective via un connecteur | Ne mélange pas les domaines ; ne synchronise pas deux instances sans règle explicite | [26](26-contrats-de-domaine-et-connecteurs.md) | E (agenda), puis un domaine à la fois |
| *Keycloak* (outil existant) | Authentification, fédération, émission des jetons, `egen_uid` | Ne connaît ni cellules ni capacités techniques | [25](25-identite-et-autorisations.md) | C |
| *OpenFGA* (outil existant) | Relations et droits non hiérarchiques calculés ; partagé avec [21](21-systeme-agents-et-gateway.md) | Ne stocke aucun secret ni donnée métier | [25](25-identite-et-autorisations.md) | C |
| Portail (frontend, monorepo micro-frontend) | Affiche les applications de l'utilisateur ; n'embarque aucune règle d'accès | Ne décide jamais d'un droit | [28](28-portail-et-integration-des-interfaces.md) | G |

**Dépôt.** L'ensemble vit dans le **Projet Métier** (`Civitas-EGEN-Business`,
déjà cité en [19](19-feuille-de-route.md)). Le découpage interne en dépôts ou
en modules de ce projet n'est **pas** une décision du Kernel ; la seule
contrainte du Kernel est que chaque composant soit déclaré par un
`ServiceManifest` valide.

**Équipes Kernel (`metadata.team`).** Deux valeurs distinctes, normatives :

- `platform` : services de l'écosystème eux-mêmes (`cell-registry-service`,
  `catalog-service`, `platform-operator-service`, `config-service`, gateways),
  déclarés par la pipeline de déploiement ([21.8](21-systeme-agents-et-gateway.md#218--pipeline-de-déploiement-générale)) ;
- `platform-operator` : **toutes les instances d'applications** déclarées par
  `platform-operator-service`. Les équipes humaines ne les voient pas dans le
  Kernel ; elles passent par le portail ([14](14-securite.md)).

## 22.5 — Les six notions à ne jamais confondre

| Notion | Définition | Exemple |
|---|---|---|
| **Domaine** | Besoin fonctionnel, défini par un contrat | `agenda` |
| **Implémentation** | Logiciel capable de remplir (en tout ou partie) un domaine | `radicale`, `nextcloud-calendar` |
| **Version d'implémentation** | Une version précise, avec son artefact, ses prérequis, ses capacités | `radicale 3.x` |
| **Instance** | Un déploiement concret d'une version, avec sa configuration | « Radicale de l'Université X, prod » |
| **Activation** | Autorisation d'utiliser une instance **dans une organisation** (cellule racine) | « Instance I activée pour l'Université X » |
| **Affectation** | Outil de travail d'une **cellule** pour un domaine et un usage | « Service RH → instance I, domaine agenda, usage `default` » |

Une application peut donc être **dans le catalogue sans être installée**,
**installée sans être activée**, **activée sans être affectée** : trois états
distincts, jamais fusionnés ([24](24-catalogue-instances-affectations.md)).

## 22.6 — Sources de vérité

Chaque donnée a **une seule** source de vérité. Tout le reste est dérivé et
reconstructible.

| Donnée | Source de vérité unique | Dérivés (reconstructibles) |
|---|---|---|
| Arbre des cellules, types, attributs | PostgreSQL de `cell-registry-service` | Projection OpenFGA (`parent`), chemins `ltree` |
| Appartenances (membre, manager, org_admin) | PostgreSQL de `cell-registry-service` | Tuples OpenFGA |
| Catalogue, instances désirées, activations, affectations | PostgreSQL de `catalog-service` | Tuples OpenFGA d'audience explicite |
| État **observé** d'une instance managée | **Kernel** (statut du service) | Copie en lecture dans `catalog-service` |
| Valeurs de configuration par niveau | PostgreSQL de `config-service` | Valeurs publiées dans le Kernel (niveaux techniques) |
| Identité de l'utilisateur | Keycloak | `egen_uid` dans les jetons |
| Secrets | Vault | Références dans les manifestes et schémas |
| Données métier d'une application | L'application elle-même | Aucune copie implicite ([26](26-contrats-de-domaine-et-connecteurs.md)) |

Règle de cohérence : toute projection est alimentée par **outbox
transactionnelle**, rejouable et vérifiée par une **réconciliation
périodique** (level-triggered, même esprit que [04](04-moteur-de-reconciliation.md)).

## 22.7 — Trois flux de référence

**Flux 1 — Créer une cellule.**
1. Un utilisateur autorisé (`manager`/`org_admin` du parent) appelle
   `cell-registry-service`.
2. Le service valide le type, le schéma d'attributs, la compatibilité de
   parenté, la profondeur ; écrit en base ; écrit l'événement dans l'outbox ;
   journalise l'audit.
3. Le projecteur écrit le tuple `parent` dans OpenFGA ; un événement
   `africa.civitas.platform.cell.created.v1` est publié (transport : Kernel,
   [12](12-communication-fabric.md)).
4. **Le Kernel n'est pas appelé.**

**Flux 2 — Installer une instance managée.**
1. Un administrateur de plateforme enregistre l'implémentation (approuvée),
   puis un `owner` de cellule demande une instance : `catalog-service` crée
   l'instance au statut `PLANNED` avec l'état désiré.
2. `platform-operator-service` voit l'instance désirée, construit le
   `ServiceManifest` (image de la version, ressources, santé, dépendances,
   références de secrets, labels réservés), appelle `POST /api/v1/services` du
   Kernel (avec `Idempotency-Key`), publie la configuration d'instance.
3. Il interroge périodiquement `GET /api/v1/services/{id}/status` et reporte
   l'état dans le catalogue (`INSTALLING` → `INSTALLED` ou `FAILED`).
4. L'activation puis l'affectation sont des opérations **du catalogue** ; le
   Kernel n'en est jamais informé.

**Flux 3 — Un utilisateur ouvre son portail.**
1. Le jeton OIDC porte `egen_uid`.
2. `catalog-service` calcule les affectations effectives pour les cellules de
   l'utilisateur, filtre par état (instance `INSTALLED`, activation
   `ACTIVE`, affectation `ACTIVE`) et par droit d'usage ([25](25-identite-et-autorisations.md)).
3. Le portail affiche le résultat. **Le Kernel n'est pas appelé** dans ce flux.

## 22.8 — Registre des décisions

Chaque décision est numérotée, **définitive** pour cette version du plan, et
référencée par les autres documents. Revenir sur l'une d'elles demande une
revue d'architecture explicite (trace dans ce registre).

| N° | Décision |
|---|---|
| **D01** | Le Kernel ne contient aucun concept de cellule, organisation, tenant, catalogue, affectation, utilisateur ou domaine fonctionnel. Tout cela vit dans des services déclarés par `ServiceManifest`. |
| **D02** | Les composants de l'écosystème vivent dans le Projet Métier ; le Kernel n'impose que la validité de leurs manifestes. |
| **D03** | Un **seul** concept structurel : la cellule générique. Les « types » (faculté, service, projet…) sont des **données**, jamais des classes. |
| **D04** | Une cellule **racine** (sans parent) = une organisation = la **frontière de tenant**. Une cellule ne change jamais de racine (pas de déplacement inter-racines). |
| **D05** | Un type de cellule a une **portée** : plateforme (partagé) ou racine (propre à une organisation). Les identifiants de types sont uniques dans leur portée et ne peuvent pas entrer en collision entre une portée racine et la portée plateforme. |
| **D06** | Le modèle n'impose **aucune limite de profondeur**. Un garde-fou technique configurable (défaut **64**) protège contre les arbres pathologiques. |
| **D07** | PostgreSQL + `ltree` pour l'arbre. PostgreSQL est la vérité ; OpenFGA reçoit une projection par outbox + réconciliation périodique. |
| **D08** | Aucune suppression physique en V1 : **archivage** logique, puis purge explicite par un administrateur de plateforme après rétention. |
| **D09** | Six notions distinctes : domaine, implémentation, version d'implémentation, instance, activation, affectation ([22.5](#225--les-six-notions-à-ne-jamais-confondre)). |
| **D10** | Unicité d'une affectation sur (cellule, domaine, usage). Héritage **descendant** par défaut ; coupure explicite possible ; plusieurs usages d'un même domaine dans une cellule autorisés. |
| **D11** | L'**appartenance** (`member`) ne s'hérite **pas** vers le bas ; les rôles `manager` et `org_admin` **s'héritent** vers le bas. |
| **D12** | Identité : Keycloak ; identifiant permanent `egen_uid` (UUID immuable, claim du jeton). Aucun service n'utilise `sub` comme clé. |
| **D13** | Autorisation : OpenFGA pour les relations ; les jetons **ne portent pas** les droits de cellule. Les décisions dépendant de la hiérarchie sont validées par un test de charge bloquant en phase C avec repli pré-décidé ([25](25-identite-et-autorisations.md)). La **seule** implémentation de « cet utilisateur peut-il utiliser cette instance dans ce contexte » est le contrôle d'usage de `catalog-service` ; Domain Gateways et Capability Gateway l'appellent. |
| **D14** | **Seul** `platform-operator-service` détient des identifiants du Kernel (équipe `platform-operator`). Aucun humain n'appelle le Kernel pour des actions métier. |
| **D15** | Deux modes d'instance : **MANAGED** (déployée par le Kernel) et **EXTERNAL** (existante, hors Kernel en V1 : le catalogue en porte l'endpoint et la vérification de santé). |
| **D16** | Un domaine = un contrat + une gateway. Un **connecteur** = un module isolé par protocole ou produit ; un connecteur peut servir plusieurs implémentations (ex. CalDAV pour Radicale et Nextcloud). |
| **D17** | Conformité : `FULL` ou `PARTIAL` ; seules les versions d'implémentation `APPROVED` sont sélectionnables ; `PARTIAL` exige une dérogation explicite, visible. |
| **D18** | Aucune synchronisation implicite entre instances. Tout échange passe par une **règle d'échange explicite** (phase H). |
| **D19** | `config-service` calcule la configuration effective ; seuls les niveaux techniques (plateforme, version d'implémentation, instance) sont publiés au Kernel, par `platform-operator-service` (seul client du Kernel, D14), via l'API de publication de configuration ; les niveaux fonctionnels (organisation, cellule, affectation, utilisateur) sont lus à la demande par gateways et portail. Aucun secret en valeur. |
| **D20** | Aucune évolution anticipée du Kernel : seules les évolutions de la phase A sont planifiées ; les autres ont un **déclencheur** écrit ([22.9](#229--limites-actuelles-du-kernel-à-connaître)). |
| **D21** | Une seule infrastructure IAM et moteur de politiques, partagée avec le système d'agents ([21](21-systeme-agents-et-gateway.md)). |
| **D22** | Journal d'audit **append-only** pour : structure des cellules, appartenances, affectations, activations, configuration, changements de conformité/approbation. |

## 22.9 — Limites actuelles du Kernel à connaître

Vérifiées dans le code (branche `main`, 27 octobre 2026). Elles sont la raison
d'être de la phase A et des déclencheurs écrits.

| Constat | Conséquence pour l'écosystème | Traitement |
|---|---|---|
| `labels`/`annotations` stockés et restitués, **sans filtre** de liste | Rien de bloquant : l'opérateur connaît ses `ServiceId` ; le filtrage par label se fait côté client sur la liste (déjà filtrée par équipe) | Aucune évolution (**D20**) |
| **Aucune API** pour publier la configuration d'un service (le port existe, l'API non) | Bloque la configuration d'instance | **Évolution A3** (planifiée) |
| `runtime.type` (`container`/`binary`/`external`/`vm`) est **conservé mais non différencié** : tout passe par le Deployment Adapter | Les instances **EXTERNAL** ne sont pas gérées par le Kernel | **D15** ; déclencheur A-X1 ([19](19-feuille-de-route.md)) |
| L'adapter Nomad ne gère **pas** les namespaces ; `TargetEnvironment` est un nom d'environnement | Pas d'isolation d'infrastructure par tenant via le Kernel | Isolation par données ou par instance dédiée ; déclencheur A-X2 |
| RBAC de l'API = `metadata.team` (chaîne unique) + rôle admin | Ne peut pas représenter une hiérarchie de cellules | Volontaire : l'autorisation métier n'est pas dans le Kernel (**D14**) |
| Idempotency-Key en mémoire de processus | Une seule instance du Kernel en V1 | Déjà documenté ([19](19-feuille-de-route.md), phase 4) |

## 22.10 — Hors périmètre

- L'implémentation du code des services de l'écosystème (Projet Métier).
- Le choix d'un fournisseur d'identité autre que Keycloak (le contrat est
  l'OIDC standard ; un changement est une évolution d'infrastructure,
  jamais de modèle d'identité).
- Les applications métier elles-mêmes (RH, GED, finance…), qui suivent
  [21.10](21-systeme-agents-et-gateway.md#2110--ordre-de-construction-recommandé)
  et le rythme du Projet Métier.
- L'isolation d'infrastructure par tenant (voir A-X2).
