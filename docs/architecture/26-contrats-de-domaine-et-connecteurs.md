# 26 — Contrats de domaine, gateways et connecteurs

*(Services : `domain-gateway-<domaine>`, hors Kernel — voir
[22](22-ecosysteme-multi-solutions-et-frontiere.md). Décisions : D15 à D18.
Normatif pour les phases E et H de [29](29-plan-par-phase-ecosysteme.md).)*

## 26.1 — Pourquoi des contrats

Pour que plusieurs applications d'un même domaine **coexistent** sans
imposer leurs détails internes à tout l'écosystème, chaque domaine publie un
**contrat** : ce que toute implémentation doit (ou peut) savoir faire. Les
contrats **ne sont pas identiques** d'un domaine à l'autre : un agenda, un
système de vente et un fournisseur d'identité ont des responsabilités
différentes (vision, section 7).

## 26.2 — Le contrat de domaine

Un contrat est un artefact **versionné** (SemVer), contenant :

1. **L'API canonique** du domaine (OpenAPI 3.1) : ressources, opérations,
   schémas de données, codes d'erreur normalisés.
2. **La liste des capacités**, chacune identifiée par `<domaine>.<ressource>.<action>`
   (ex. `agenda.event.create`), marquée **obligatoire** (`M`) ou **optionnelle**
   (`O`), avec sa sémantique et ses limites attendues.
3. **Les conditions de compatibilité** : fonctionnalités minimales pour être
   `FULL`, limites connues acceptées pour être `PARTIAL`.
4. **Les identifiants de permission** (`requiredPermission`) : égaux aux
   identifiants de capacités ; seuls le moteur de politiques les interprète
   ([21.3](21-systeme-agents-et-gateway.md#213--le-service-manifest-sétend-jamais-ne-se-casse)).
5. **La suite de tests de contrat** (26.6).

**Évolution** : changement compatible (capacité optionnelle ajoutée) = version
mineure. Changement incompatible = **nouvelle version majeure** ; la gateway
sert **simultanément** la majeure courante et la précédente pendant une
période de dépréciation écrite dans le contrat. Jamais de suppression sans
dépréciation (même règle que [13](13-api-et-contrats.md)).

### Exemple : le domaine `agenda` (illustratif, à figer en phase E)

| Capacité | M/O |
|---|---|
| `agenda.calendar.list`, `agenda.event.list`, `agenda.event.create`, `agenda.event.update`, `agenda.event.delete` | **M** |
| `agenda.calendar.create`, `agenda.calendar.delete` | O |
| `agenda.availability.query` (disponibilités) | O |
| `agenda.invitation.send` (invitations) | O |
| `agenda.event.recurrence`, `agenda.event.attachment` | O |

Les capacités réellement supportées par `radicale` ou `nextcloud-calendar`
sont **mesurées** par la suite de tests, jamais déclarées de mémoire.

## 26.3 — La gateway de domaine

`domain-gateway-<domaine>` est un service EGEN ordinaire (un par domaine,
**jamais** un service qui mélange plusieurs domaines — D16). Pour chaque
requête :

1. Authentifie l'appelant (jeton OIDC, `egen_uid`).
2. Reçoit le **contexte** : la cellule `C` et l'usage `U` (en-têtes
   `X-EGEN-Cell`, `X-EGEN-Usage` ; `usage` défaut `default`).
3. Appelle `check-use` du catalogue ([25.6](25-identite-et-autorisations.md#256--contrôle-dusage-unique-d13)) ;
   refus → `403` avec raison.
4. Obtient l'**instance effective** ([24.8](24-catalogue-instances-affectations.md#248--résolution-de-laffectation-effective)).
5. Vérifie la **capacité** : si l'implémentation ne supporte pas la capacité
   demandée → `501` avec le code normalisé `CAPABILITY_NOT_SUPPORTED` et le nom
   de la capacité (jamais une erreur obscure).
6. Vérifie la **permission métier** sur la ressource via le moteur de
   politiques ([25.3](25-identite-et-autorisations.md#253--les-niveaux-dautorisation-vision-section-10)).
7. Résout l'adresse : instance MANAGED par `discover` du Kernel
   ([13](13-api-et-contrats.md)), instance EXTERNAL par l'endpoint du
   catalogue ; applique la résilience
   ([12](12-communication-fabric.md#sémantique-normative-de-la-résilience)) ;
   obtient/crée le compte local si nécessaire ([25.2](25-identite-et-autorisations.md#252--identité-d12)).
8. Appelle le **connecteur**, normalise la réponse et les erreurs.
9. Journalise (audit) et propage la trace (`traceparent`).

Une gateway **ne mélange jamais** les données de deux instances : la réponse à
une requête vient d'**une** instance. Agréger deux instances est une fonction
explicite d'une règle d'échange (26.7), jamais un effet de bord.

**Publication agentique (optionnelle).** La gateway peut annoncer ses
capacités au système d'agents par le bloc `interfaces` de son manifeste
(`type: mcp` ou `openapi`, [06](06-service-manifest.md#interfaces-annoncées-interfaces),
[21](21-systeme-agents-et-gateway.md)). Le risque (`risk`) de chaque capacité
est défini dans le contrat.

## 26.4 — Les connecteurs

Un **connecteur** traduit le contrat vers une implémentation réelle.

- **Un module isolé par protocole ou produit**, sans dépendance vers un autre
  connecteur (même règle que les adapters du Kernel,
  [16](16-packages-et-stack-technique.md)).
- Un connecteur peut servir **plusieurs implémentations** si elles parlent le
  même protocole : un connecteur **CalDAV** (RFC 4791) peut servir à la fois
  `radicale` et `nextcloud-calendar`, les particularités de chacun étant
  portées par des **profils** (données), pas par des copies de code.
- Interface minimale (langage libre, contrat identique) :
  `describeCapabilities()`, `healthCheck()`, `provisionAccount()` /
  `deprovisionAccount()` (si applicable), une opération par capacité,
  `mapError()` vers les codes normalisés.
- **Un nouveau logiciel au protocole propriétaire ou au modèle fonctionnel
  différent exige un connecteur** (vision, section 5). Une **nouvelle
  instance** d'une version déjà connectée n'exige **aucun** développement : une
  configuration suffit.
- **Réutiliser avant d'écrire** (règle d'or, [02](02-principes-fondamentaux.md)) :
  on utilise un protocole standard quand il existe (CalDAV, OIDC, SAML, SCIM),
  le SDK/API officiel du produit sinon.

## 26.5 — Conformité et approbation (D17)

| Niveau | Définition | Conséquence |
|---|---|---|
| `FULL` | **Toutes** les capacités obligatoires supportées **et** passent la suite de tests | Éligible à `APPROVED` |
| `PARTIAL` | Au moins une capacité obligatoire manquante ou limitée | `APPROVED` seulement avec **dérogation** écrite (capacités manquantes, justification, approbateur) ; le portail et la sélection affichent les limites |

- La conformité est **calculée** par la suite de tests contre une instance de
  test, **jamais déclarée à la main**.
- Les capacités **optionnelles** supportées sont listées ; la sélection les
  montre côte à côte pour chaque candidat.
- Une montée de version du contrat ou de l'implémentation **recalcule** la
  conformité ; une régression place la version en `DEPRECATED` automatiquement,
  avec alerte.

## 26.6 — Suite de tests de contrat (*Domain Contract Test Kit*)

- Livrée avec chaque contrat ; exécutée en **CI** contre chaque connecteur et
  contre une instance réelle (conteneur éphémère, même approche que
  [17, niveau 3](17-strategie-de-tests.md#niveau-3--tests-dadapters-avec-testcontainers)).
- Contient : tests par capacité, tests de **non-fuite entre organisations**
  pour les versions `SHARED_ISOLATED`, tests de normalisation d'erreurs, tests
  de résilience (timeout, indisponibilité).
- Sa sortie **alimente** le champ `conformance` et `capabilities` de la
  version d'implémentation ([24.3](24-catalogue-instances-affectations.md#243--implémentation-et-version-dimplémentation)).

## 26.7 — Données : aucune synchronisation implicite (D18)

- Deux instances d'un même domaine **ne partagent rien** par défaut : ni
  événements, ni catalogues, ni clients. Les données restent rattachées à leur
  système propriétaire.
- Tout échange nécessaire est une **règle d'échange** explicite
  (`exchange_rule`), créée en phase H :

| Champ | Règle |
|---|---|
| `sourceInstanceId`, `targetInstanceId` | Deux instances **du même domaine** ou d'un domaine lié par contrat |
| `entityKind` | Type d'entité du contrat (ex. `calendar`) |
| `direction` | `ONE_WAY` ou `TWO_WAY` |
| `owner` | Qui est responsable de la donnée (cellule) |
| `conflictPolicy` | `SOURCE_WINS`, `TARGET_WINS` ou `MANUAL` (jamais implicite) |
| `scope` | Filtre explicite (cellule, ressources) |
| `mode` | `ONE_SHOT` (workflow du Kernel, avec compensation, [11](11-moteur-de-workflow.md)) ou `CONTINUOUS` (consommation d'événements, relecture d'état) |
| `idMap` | Table de correspondance des identifiants source ↔ cible, **conservée** tant que des références externes existent |

- **Isolation entre organisations** : une règle d'échange entre deux
  organisations exige l'accord explicite des deux `org_admin`.
- La plateforme **ne suppose jamais** que deux applications d'une même
  catégorie ont des modèles de données identiques : toute transformation est
  déclarée dans le connecteur ou la règle.

## 26.8 — Ce que les gateways ne font jamais

- Décider d'une affectation ou d'une activation (c'est le catalogue).
- Stocker les données métier de l'application (elles restent dans
  l'application).
- Contourner le contrôle d'usage ou le moteur de politiques.
- Présenter comme « équivalentes » deux implémentations aux capacités
  différentes.
