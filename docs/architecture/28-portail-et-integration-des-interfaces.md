# 28 — Portail et intégration des interfaces

*(Frontend : monorepo micro-frontend du Projet Métier — voir
[22](22-ecosysteme-multi-solutions-et-frontiere.md). Normatif pour la phase G
de [29](29-plan-par-phase-ecosysteme.md).)*

## 28.1 — Principe

Le portail **affiche** les applications auxquelles un utilisateur a accès. Il
**ne décide jamais** d'un droit : il présente le résultat de la résolution du
catalogue ([24.8](24-catalogue-instances-affectations.md#248--résolution-de-laffectation-effective),
[24.9](24-catalogue-instances-affectations.md#249--qui-peut-utiliser-audience)).

**Intégrer l'interface ≠ intégrer le métier** (vision, section 8) : afficher
une application dans le portail ne signifie pas que ses données, permissions
ou API sont intégrées à EGEN. Ce sont deux intégrations indépendantes : celle
de l'interface (ce document) et celle du métier (gateways,
[26](26-contrats-de-domaine-et-connecteurs.md)).

## 28.2 — La liste « mes applications »

`GET /api/v1/me/applications` (`catalog-service`) calcule, pour l'utilisateur
authentifié (`egen_uid`) :

1. Ses cellules d'appartenance (toutes racines confondues) — depuis
   `cell-registry-service`.
2. Pour chaque cellule `C` et chaque `(domaine, usage)` : l'affectation
   effective ([24.8](24-catalogue-instances-affectations.md#248--résolution-de-laffectation-effective)).
3. Le **filtre d'état** : cellule `ACTIVE`, affectation `ACTIVE`, activation
   `ACTIVE`, instance `INSTALLED` ; sinon l'entrée est omise **ou** marquée
   indisponible avec la raison, selon le paramètre `includeUnavailable`.
4. Le **filtre d'audience** ([24.9](24-catalogue-instances-affectations.md#249--qui-peut-utiliser-audience)).

Réponse (structure normative, schéma exact figé en début de phase G) :
une liste d'entrées `{ cell, domain, usage, instance, origin, entryPoint,
availability, conformance, limits }`, regroupées par cellule.

**Utilisateur dans plusieurs services (vision, section 8/9)** : l'ensemble des
applications est l'**union** de ses contextes ; **deux entrées** d'un même
domaine peuvent coexister (un agenda par service) ; elles sont présentées
séparément avec leur cellule d'origine. Aucune identité supplémentaire.

**Performance** : le calcul s'appuie sur PostgreSQL (`ltree`) ; résultat mis en
cache côté serveur par `egen_uid`, invalidé par les événements
`membership-changed`, `assignment.changed`, `activation.changed`,
`instance.lifecycle-changed` ([23.9](23-cellules.md#239--événements-publiés-via-la-messagerie-du-kernel),
[24.11](24-catalogue-instances-affectations.md#2411--api-ressources-normatives-de-catalog-service)).
L'invalidation déclenche une **relecture complète** (jamais une mise à jour
déduite du contenu de l'événement).

## 28.3 — Modes d'ouverture d'une application

Déclaré par la version d'implémentation (`ui`), jamais deviné :

| Mode | Fonctionnement | Quand |
|---|---|---|
| `LINK` | Le portail ouvre l'application dans un nouvel onglet ou la même fenêtre ; SSO si l'application supporte OIDC/SAML | Application complète conservant son interface (cas général) |
| `EMBED` | L'interface est un **micro-frontend** chargé dans l'environnement de travail EGEN (single-spa) | Application dont l'architecture et la licence le permettent |
| `API_ONLY` | Aucun point d'entrée visuel ; consommée via une gateway | Services sans interface propre |

Une même cellule peut avoir des applications de modes différents. Le portail
gère : les **points d'entrée** (URL de base, chemins), les **contextes de
navigation** (cellule et usage transmis), les **contraintes techniques**
(cadres interdits, en-têtes `X-Frame-Options`/CSP de l'application : un mode
`EMBED` n'est proposé que si l'application l'autorise).

**Remplacement d'une application (vision, section 8)** : changer l'instance
effective d'une affectation change l'`entryPoint` de l'entrée ; **le portail
n'est pas reconstruit** et aucune autre entrée n'est touchée.

## 28.4 — Intégration SSO

Priorité, dans l'ordre : OIDC > SAML > SCIM seulement pour le provisionnement >
comptes locaux liés ([25.2](25-identite-et-autorisations.md#252--identité-d12)).
Si l'application ne supporte aucun mécanisme, l'entrée affiche le badge
« compte séparé » : la limite est **visible**, jamais masquée.

## 28.5 — Personnalisation de l'expérience

Pilotée par la configuration ([27](27-configuration-multi-niveaux.md)) :
ordre d'affichage, regroupement, libellés, mode par défaut (niveaux cellule,
affectation, utilisateur). Aucune règle d'accès n'est portée par ces
paramètres.

## 28.6 — Ce que le portail ne fait jamais

- Décider d'un droit, ou le recalculer côté client.
- Appeler le Kernel.
- Appeler directement une application métier pour ses données (il passe par
  les gateways).
- Masquer à l'utilisateur la conformité partielle ou un compte séparé.
