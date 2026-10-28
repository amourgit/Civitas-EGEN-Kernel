# 25 — Identité unique et autorisations à plusieurs niveaux

*(Outils existants : Keycloak, OpenFGA — hors Kernel, voir
[22](22-ecosysteme-multi-solutions-et-frontiere.md). Décisions : D11 à D14,
D21, D22. Normatif pour la phase C de [29](29-plan-par-phase-ecosysteme.md).
Prolonge [14](14-securite.md) et [21.5](21-systeme-agents-et-gateway.md#215--autorisation--le-policy-enforcement-point).)*

## 25.1 — Principe : une identité, des appartenances, des droits calculés

La vision EGEN pose une exception fondamentale : contrairement aux
applications, l'**identité est une référence commune**. Un utilisateur :

- possède **une** identité EGEN permanente, quel que soit le nombre
  d'applications, de services ou d'organisations qu'il fréquente ;
- a **plusieurs appartenances** (cellules) et **plusieurs rôles** ;
- n'obtient **aucun** nouveau compte EGEN en changeant de service, ni quand une
  application est remplacée ;
- conserve la même identité si le fournisseur d'authentification est changé
  (évolution d'infrastructure, jamais de modèle).

## 25.2 — Identité (D12)

- **Fournisseur** : Keycloak (OIDC), partagé avec le système d'agents
  ([21](21-systeme-agents-et-gateway.md), D21).
- **Identifiant permanent** : `egen_uid`, un UUID généré **une fois** à la
  création de l'utilisateur, stocké comme attribut utilisateur Keycloak
  **immuable** (écriture interdite après création, par politique et test) et
  exposé dans chaque jeton par un *protocol mapper* sous le claim `egen_uid`.
- **Règle** : **aucun** service de l'écosystème n'utilise `sub` comme clé.
  Toutes les bases, tous les tuples OpenFGA, tous les journaux d'audit
  référencent `egen_uid`.
- **Remplacement du fournisseur** : export des utilisateurs avec leur
  attribut `egen_uid`, import dans le nouveau fournisseur, mapper identique.
  Les appartenances, affectations et droits **ne changent pas** : ils sont
  indexés par `egen_uid`.
- **Fédération** (annuaire d'une université, IdP d'un partenaire) : par
  *identity brokering* Keycloak ; l'identité fédérée est **liée** à
  l'utilisateur Keycloak existant, jamais dupliquée.

### Comptes locaux des applications

Certaines applications exigent un compte local (Radicale sans SSO,
Odoo…). Règles :

1. Le compte local est un **compte technique lié** à l'identité EGEN : la
   table `local_account(egen_uid, instance_id, local_id, created_at)` est tenue
   par la **gateway du domaine** ([26](26-contrats-de-domaine-et-connecteurs.md)).
2. Il est créé à la première utilisation autorisée (provisionnement paresseux)
   ou par SCIM si l'application le supporte (RFC 7644).
3. Sa suppression suit la fin d'appartenance ou le retrait d'affectation, selon
   la politique de l'instance ; la disposition des données reste une décision
   explicite ([24.5](24-catalogue-instances-affectations.md#245--cycle-de-vie-dune-instance-et-correspondance-avec-le-kernel)).
4. Le niveau d'intégration d'identité de chaque version d'implémentation est
   **déclaré** (`requirements.identity`, [24](24-catalogue-instances-affectations.md)) : une application qui ne
   supporte ni OIDC ni SAML ni SCIM est marquée `local-accounts` et ses limites
   sont visibles. La plateforme **ne prétend jamais** à une fédération qu'une
   application ne prend pas en charge.

## 25.3 — Les niveaux d'autorisation (vision, section 10)

Quatre catégories **distinctes**, jamais fusionnées :

| Catégorie | Question | Décidée par |
|---|---|---|
| **Plateforme** | Qui peut enregistrer/approuver une implémentation, installer un logiciel, créer une organisation ? | OpenFGA (`platform#admin`) |
| **Application** | Qui peut **utiliser** une application ? **Choisir** une solution pour une cellule ? | Contrôle d'usage de `catalog-service` (D13) + OpenFGA pour les audiences explicites ; `can_choose_tools` dans OpenFGA |
| **Configuration** | Qui peut **configurer** une instance ou une affectation ? **Administrer** (déployer, mettre à jour, arrêter) ? | OpenFGA (`application_instance#can_configure`, `#can_administer`) |
| **Ressource métier** | Qui peut lire/écrire tel calendrier, telle commande ? | Gateway du domaine + moteur de politiques, via `requiredPermission` ([26](26-contrats-de-domaine-et-connecteurs.md), [21.5](21-systeme-agents-et-gateway.md#215--autorisation--le-policy-enforcement-point)) |

Tableau des **rôles** et de leur portée :

| Rôle | Portée | Peut | Ne peut pas |
|---|---|---|---|
| `platform#admin` | Toute la plateforme | Enregistrer/approuver implémentations, créer des racines, purger, administrer toute instance | — |
| `ORG_ADMIN` | Une racine et tout son arbre | Définir les types de cellules de sa racine, **activer** des instances pour son organisation, déléguer | Installer un nouveau logiciel ; agir hors de sa racine |
| `MANAGER` | Une cellule et ses descendantes | Gérer la structure, **choisir** les outils parmi les solutions approuvées et activées, configurer les affectations de ses cellules | Installer ; modifier la sécurité globale ; toucher une autre unité |
| `MEMBER` | Une cellule **seulement** (D11) | Utiliser les outils qui lui sont affectés | Gérer, choisir |
| `chooser` / `configurator` délégués | Une cellule | Droits de choix / de configuration accordés **sans** faire de la personne un manager | Gérer la structure |
| `administrator` d'instance | Une instance | Déployer, mettre à jour, arrêter | Accéder aux données métier de l'instance |

**Séparation des droits** (vision) : un administrateur technique d'une
instance **n'a pas** de droit sur ses données métier ; un utilisateur peut
consulter sans configurer ; un responsable gère les ressources de son service
sans accéder à celles d'une autre unité.

## 25.4 — Modèle OpenFGA

Syntaxe **validée** avec le transformateur officiel `@openfga/syntax-transformer`
(le 28 octobre 2026). La **sémantique** (résolution effective, performances)
est validée par le test de charge de la phase C (25.5) : tant qu'il n'a pas
été passé, ce modèle est **le modèle cible**, non un modèle éprouvé.

```
model
  schema 1.1

type user

type platform
  relations
    define admin: [user]

type cell
  relations
    define parent: [cell]
    define member: [user]
    define manager: [user] or manager from parent
    define org_admin: [user] or org_admin from parent
    define chooser: [user] or manager or org_admin
    define configurator: [user] or manager or org_admin
    define can_view: member or manager or org_admin
    define can_manage_structure: manager or org_admin
    define can_choose_tools: chooser
    define can_configure_tools: configurator

type application_instance
  relations
    define platform: [platform]
    define owner: [cell]
    define activated_for: [cell]
    define administrator: [user] or manager from owner or admin from platform
    define can_administer: administrator
    define can_configure: administrator or configurator from owner

type assignment
  relations
    define cell: [cell]
    define allowed: [user, cell#member]
    define can_use: allowed
```

Tuples projetés (par l'outbox, [22.6](22-ecosysteme-multi-solutions-et-frontiere.md#226--sources-de-vérité)) :

| Tuple | Origine |
|---|---|
| `cell:C#parent@cell:P` (le parent de `C` est `P`) | Arbre ([23](23-cellules.md)) |
| `cell:C#member@user:U`, `#manager`, `#org_admin` | Appartenances ([23](23-cellules.md)) |
| `application_instance:I#owner@cell:C`, `#activated_for@cell:R`, `#platform@platform:egen` | Instances et activations ([24](24-catalogue-instances-affectations.md)) |
| `assignment:A#cell@cell:C`, `#allowed@user:U` / `cell:G#member` | Affectations d'audience `EXPLICIT` uniquement |

`SCOPE_MEMBERS` n'écrit **aucun** tuple `allowed` : cette audience se calcule
dans PostgreSQL ([24.9](24-catalogue-instances-affectations.md#249--qui-peut-utiliser-audience)).

**Ce que le jeton ne porte pas.** Aucun droit de cellule, aucun rôle : l'arbre
peut être profond et les appartenances nombreuses ; les décisions sont
calculées **à l'appel**, pas figées dans un jeton.

## 25.5 — Test de charge bloquant et repli pré-décidé (phase C)

Risque connu (D13) : les relations récursives (`manager from parent`,
`org_admin from parent`) sont soumises à la limite de profondeur de résolution
du serveur OpenFGA (paramètre serveur, valeur à lire sur la **version
retenue** — elle n'est pas présumée ici).

**Test d'acceptation de phase C** (condition de sortie, non négociable) :

1. Jeu de données : arbre de **profondeur 64** (le défaut de `maxDepth`),
   au moins **100 000** cellules au total, 10 000 utilisateurs, 50 000
   appartenances.
2. Vérifications : `manager` hérité sur la cellule la plus profonde depuis un
   `manager` de la racine ; `org_admin` hérité ; absence de droit pour un
   utilisateur d'une autre racine.
3. **Critères** : toutes les réponses sont **correctes** ; latence de `check`
   **p95 ≤ 50 ms** et **p99 ≤ 150 ms** sous 200 requêtes/s ; aucune erreur de
   « profondeur dépassée ».

**Repli pré-décidé** si un critère échoue (il est mis en œuvre **sans**
nouvelle revue de conception) : le *registre de cellules* calcule la chaîne
d'ancêtres via `ltree` et le projecteur matérialise des relations directes
`effective_manager` / `effective_org_admin` sur chaque cellule concernée,
mises à jour de façon incrémentale par l'outbox lors d'un ajout/retrait de
rôle ou d'un `move`. Le coût d'écriture est alors proportionnel à la taille du
sous-arbre concerné ; il est borné par la configuration (opération par lots,
journalisée). Le modèle OpenFGA de 25.4 est alors amendé de façon
mécanique (relations directes à la place des relations récursives), et ce
document est mis à jour avec la décision mesurée.

## 25.6 — Contrôle d'usage unique (D13)

La seule implémentation de « `u` peut-il utiliser `I` pour `(C, D, U)` ? » est
`POST /api/v1/resolution/check-use` de `catalog-service`
([24.9](24-catalogue-instances-affectations.md#249--qui-peut-utiliser-audience)).
Les gateways de domaine, le portail et le *Capability Gateway* du système
d'agents l'appellent : **personne ne la réimplémente**. Conséquence : un
utilisateur ne peut pas obtenir via un agent ce que le portail lui refuse.

## 25.7 — Échange de jeton et moindre privilège

- Appels de service à service : **échange de jeton** OAuth2 (RFC 8693) pour
  porter « le service S agit pour l'utilisateur `egen_uid` pour l'opération O »,
  jamais un compte de service à privilèges larges
  ([21.4](21-systeme-agents-et-gateway.md#214--capability-gateway--le-cœur-du-système)).
- Une application choisie par un service **ne peut pas** contourner les
  règles de sécurité, accéder aux données d'un autre tenant ou obtenir des
  privilèges non accordés (vision) : les gateways n'appellent une instance
  qu'avec les droits de l'utilisateur et du contexte (cellule) concernés ;
  les instances `SHARED_ISOLATED` ne sont jamais jointes avec un compte
  d'administration pour une requête utilisateur.
- **Identité du Kernel** (D14) : `platform-operator-service` est le **seul**
  à posséder un jeton Kernel (client Keycloak dédié, *client credentials*,
  claim `team = platform-operator`). Les services de plateforme sont déclarés
  par la pipeline avec l'équipe `platform` ([14](14-securite.md)). Aucun
  humain n'agit sur les instances d'applications **via** le Kernel : il passe
  par `catalog-service`.

## 25.8 — Audit (D22)

Journalisés en **append-only**, avec `egen_uid` de l'acteur et corrélation :
ajout/retrait d'appartenance et de rôle ; création/modification/retrait
d'affectation et d'activation ; approbation d'une version d'implémentation ;
changements de configuration ; toute **décision de refus** du contrôle
d'usage (avec la raison).

## 25.9 — Ce que l'identité et les droits ne font jamais

- Créer une identité différente par application ou par service.
- Fonder un droit sur un rôle « admin » pris isolément : toujours une
  question relationnelle `(utilisateur, droit, ressource, contexte)`
  ([21.5](21-systeme-agents-et-gateway.md#215--autorisation--le-policy-enforcement-point)).
- Placer un droit de cellule dans un jeton.
- Donner au Kernel la moindre connaissance d'un utilisateur ([01](01-vision-et-positionnement.md)).
