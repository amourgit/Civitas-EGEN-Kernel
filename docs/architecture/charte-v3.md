# EGEN — Charte d'Architecture & Modele Organisationnel (v3, consolidee)

> Ce document est la **reference faisant foi** pour toute decision d'architecture
> dans ce depot, depuis le refactoring engage le 22 juillet 2026. Il remplace et
> fusionne les deux documents precedents (la charte d'architecture noyau "paradigme
> noyau Linux", et le plan organisationnel Organisation/Cellule/Lexique/Tutelle...).
> Toute incoherence entre ce document et le code doit etre traitee comme un bug a
> corriger dans le code, jamais comme une raison de modifier silencieusement ce
> document.

## Statut de ce document

Il integre les decisions actees le 22 juillet 2026 :

- **Point 1 (mono-tenant par instance)** — confirme.
- **Point 2 (classification Policy/Resource/Reference-data)** — confirme.
- **Point 3 (contenu exact du primitif Niveau 1)** — **toujours ouvert**, non traite ici.
- **Point 4 (structure `egen-modules/`)** — confirme, option structuree retenue.

Il signale aussi une tension nee de la combinaison des deux documents sources : la
Tutelle multi-organisation (§ Partie D) supposait une visibilite entre Organisations
que le modele mono-tenant par instance rend non triviale. **Cette tension a ete
tranchee le 22 juillet 2026, lors du refactoring** : la Tutelle reste strictement
intra-instance (voir la note ajoutee a la fin de la Partie D).

---

# PARTIE A — Le Noyau : philosophie et classification

## A.1 Le paradigme, en une phrase

Le Kernel EGEN est un noyau strictement neutre, facon noyau Linux : il ne connait ni
l'identite, ni l'organisation, ni l'autorisation metier, ni aucun domaine. Il fournit
un mecanisme pour charger, decharger et faire cohabiter des modules, plus le strict
minimum vital pour que ce mecanisme soit protege des le tout premier demarrage.

## A.2 Le modele a trois niveaux

| Niveau | Role | Analogie Linux | Remplacable ? |
|---|---|---|---|
| **Niveau 0** | Irreductible — le mecanisme de chargement et ce qui le protege | Scheduler, VFS, `modprobe`, `CAP_SYS_MODULE` | Jamais |
| **Niveau 1** | Primitif minimal, compile en dur, jamais retire — complete par le Niveau 2 | Permissions POSIX (rwx, UID/GID) | Non retire, seulement empile |
| **Niveau 2** | Pluggable, choisi a l'installation | `pam_ldap.so`, drivers, filesystems | Oui |

> Note de vocabulaire pour toute la suite du document : pour eviter toute confusion,
> **"Niveau"** designe exclusivement ce classement architectural (0/1/2) du noyau. La
> profondeur de la hierarchie organisationnelle (Organisation -> Cellule -> Cellule
> fille...) est designee par **"Palier"** dans ce document — les deux mots existaient
> sous "Niveau" dans les documents sources, ce qui aurait cree une collision reelle
> dans le code et dans les discussions d'equipe.

## A.3 Decisions actees dans cette version

### Point 1 — Modele mono-tenant par instance : confirme

Chaque entreprise cliente recoit une instance deployee separee et complete du projet.
Consequences concretes :

- **SpiceDB** : une instance par deploiement. Plus besoin de prefixage tenant dans les
  relations.
- **Keycloak** : plus besoin de realms multiples pour isoler des Organisations entre
  elles — un deploiement, une Organisation. Un realm peut toujours structurer
  l'interne (par Cellule, par exemple), mais l'isolation inter-tenant au sens SaaS
  mutualise disparait du probleme.
- **Le schema reste generique malgre tout** : `Organisation` reste une entite a part
  entiere dans le modele de donnees (pas une hypothese codee en dur qu'il n'y en a
  qu'une), pour ne pas fermer la porte a un scenario futur ou un deploiement voudrait
  heberger plusieurs Organisations (un groupe avec filiales, par exemple).
  Operationnellement, chaque deploiement en a une seule au demarrage.
- **Consequence en cascade non anticipee** : la Tutelle multi-organisation (CHU
  rattache a deux Organisations) devient une vraie question d'architecture
  inter-instances, pas juste inter-tables. Traite en Partie D — et desormais tranche :
  intra-instance uniquement (voir la note de fin de Partie D).

### Point 2 — Policy, Resource, Reference-data : confirme

Decision : Policy (B1) rejoint le meme niveau qu'Identity — **Niveau 1**. Resource
(B3) et Reference-data (B4) vont directement en **Niveau 2**, parce qu'ils sont
rattaches a des modules Niveau 2 (Organisation, Cellule).

Le raisonnement se generalise en une regle utile pour classer tout futur systeme :

> **Un systeme appartient au Niveau 2 des que son modele de donnees est
> intrinsequement dependant d'un concept Niveau 2 (Organisation, Cellule). Un
> systeme appartient au Niveau 1 quand le noyau a besoin d'une reponse par defaut
> avant meme qu'un module Niveau 2 ne soit charge.**

C'est exactement pourquoi Resource (une salle, un document — toujours rattache a une
Cellule ou une Organisation) et Reference-data (les Modeles Sectoriels, qui n'ont de
sens que pour peupler le Lexique d'une Organisation, voir § B.5) suivent Organisation
en Niveau 2, alors que Policy suit Identity en Niveau 1 : le noyau a besoin d'une
resolution de politique par defaut (fail-closed, comportement de demarrage) avant
meme qu'un module de gouvernance metier ne soit charge — exactement comme il a
besoin d'un sujet minimal (Identity) et d'une verification de permission minimale
(Authorization) avant que Keycloak ou SpiceDB ne soient disponibles.

**Point de vigilance terminologique** — ne pas confondre :
- **"Politique-noyau"** (B1, Niveau 1) : la resolution de politique par defaut du
  Kernel lui-meme — comportement de secours, validation minimale de Manifeste,
  decisions fail-closed avant chargement d'un module de gouvernance.
- **"Politique organisationnelle"** (§ B.12, Niveau 2) : les regles de configuration
  qu'une Organisation ou une Cellule definit pour elle-meme (politique de mot de
  passe, etc.), avec Derogation en cascade. Ce concept vit entierement dans le
  module Organisation — ce n'est pas B1.

Ce sont deux systemes differents qui portent le meme mot dans le langage courant. Il
faut les nommer differemment dans le code (`kernel.policy` vs `organization.politique`
par exemple) pour ne jamais les confondre en revue de code.

> **Note de refactoring (22 juillet 2026)** : le code livre initialement sous
> `kernel-systems/policy` implementait en realite integralement la Politique
> organisationnelle (Contexte + Derogation en cascade), jamais la Politique-noyau.
> Il a ete deplace vers `egen-modules/business/organization` (sous-package
> `.politique`). La vraie Politique-noyau (Niveau 1) reste entierement a concevoir —
> voir le Point 3.

### Point 4 — Structure `egen-modules/` : confirme, option structuree

```
egen-modules/
├── system/       (providers — equivalent PAM/NSS : identity-keycloak-provider, authorization-spicedb-provider, communication-*-provider)
└── business/      (modules metier — equivalent applications utilisateur : organization, resource, reference-data, academie, RH, finance...)
```

> **Note de refactoring (22 juillet 2026)** : suivant cette structure, le provider
> Identite livre a ce jour vit desormais sous `egen-modules/system/identity/` et se
> nomme `identity-provider-keycloak` (avec un contrat generique
> `identity-provider-api` a cote, pour accueillir d'autres providers au fil du temps
> — Auth0, LDAP... — sans jamais casser ce qui en depend). C'est une precision de
> nommage par rapport a la denomination `identity-keycloak-provider` employee plus
> haut ; les deux designent le meme composant.

## A.4 Classification complete — table de reference mise a jour

| Systeme | Niveau | Categorie | Statut |
|---|---|---|---|
| `kernel-sdk` | **0** | — | Confirme |
| `kernel-plugin-engine` (PF4J) | **0** | — | Confirme |
| `module-registry` (B2) | **0** | — | Confirme |
| `kernel-bootstrap` | **0** | — | Confirme |
| `kernel-eventbus` (API) | **0** | — | Confirme |
| `eventbus-kafka-adapter` | **2** | system | Propose |
| Identity — primitif | **1** | — | Niveau confirme, contenu a concevoir (point 3 ouvert) |
| Authorization — primitif | **1** | — | Niveau confirme, contenu a concevoir (point 3 ouvert) |
| **Policy-noyau (B1)** | **1** | — | **Niveau confirme**, contenu a concevoir (point 3 ouvert) |
| Identity riche (Keycloak) | **2** | system | Propose — **livre** sous `identity-provider-keycloak` |
| Authorization riche (SpiceDB) | **2** | system | Confirme |
| Communication (E1) | **2** | system | Propose |
| Audit (E2) — contrat d'emission | **1** | — | Propose |
| Audit (E2) — moteur de stockage/chainage | **2** | system | Propose |
| **Organization (A2) + Affiliation (A3)** — Cellule, Lexique, Tutelle, Affectation, Mandat, Delegation, Politique organisationnelle | **2** | business | **Confirme** (niveau) ; fusion des deux en un seul module **actee et realisee** le 22 juillet 2026, voir § C.2 |
| **Resource (B3)** | **2** | business | **Confirme** |
| **Reference-data (B4)** — porte les Modeles Sectoriels | **2** | business | **Confirme** (niveau et categorie — voir § C.3) |

## A.5 Le primitif Niveau 1 — etat actualise, toujours ouvert (point 3)

Trois composants, a concevoir ensemble, jamais dependants d'un systeme Niveau 2 :

- **Identity** : `KernelSubject` — sujet minimal, l'equivalent UID/GID, avec un
  indicateur bootstrap pour le tout premier demarrage.
- **Authorization** : `KernelCapability` + `KernelPermissionCheck` — capacites
  noyau fermees (charger/decharger un module, enregistrer une extension),
  verification fail-closed.
- **Policy-noyau** *(nouveau, ajoute suite au point 2)* : une resolution de
  politique par defaut pour le Kernel — a minima, le comportement de secours quand
  aucun module de gouvernance Niveau 2 n'est encore charge (Manifeste incomplet,
  Activation non resolue, etc.). Portee exacte non detaillee — a concevoir en meme
  temps que les deux composants ci-dessus, pas separement, puisque les trois
  repondent a la meme question de fond : *que fait le noyau avant qu'aucun module ne
  soit charge ?*

**Ce point reste ouvert au 22 juillet 2026**, y compris apres le refactoring de
repositionnement. Rien n'a ete invente unilateralement pour le combler ; voir le plan
de suite de programmation pour la proposition de conception a valider avant tout
premier code.

## A.6 Arborescence noyau — mise a jour (etat reel au 22 juillet 2026)

```
egen-kernel/
├── kernel-sdk/                          (contrat, JPMS pur — inchange)
├── kernel-jpa-support/                  (Socle de Tracabilite partage — inchange)
├── kernel-domain/                       (a venir — module-domain seul, B2, Niveau 0)
├── kernel-systems/                      (VIDE a ce jour — voir kernel-systems/pom.xml)
│   ├── identity/                        (a concevoir — primitif Niveau 1, point 3)
│   ├── authorization/                   (a concevoir — primitif Niveau 1, point 3)
│   └── policy/                          (a concevoir — Politique-noyau, Niveau 1, point 3)
├── kernel-plugin-engine/                (a venir)
├── kernel-eventbus/                     (a venir)
├── kernel-bootstrap/                    (a venir)
└── kernel-test-support/                 (a venir)

egen-modules/
├── system/
│   └── identity/
│       ├── identity-provider-api/       (contrat generique — LIVRE)
│       └── identity-provider-keycloak/  (implementation Keycloak — LIVRE)
│   └── (authorization/, communication/ — a venir)
└── business/
    ├── organization/                    (fusion Organisation + Rattachements + Politique
    │                                      organisationnelle — LIVRE)
    │   ├── organization-api/            (sous-packages .affiliation, .politique inclus)
    │   └── organization-impl/
    └── reference-data/                  (LIVRE)
        ├── reference-data-api/
        └── reference-data-impl/
```

---

# PARTIE B — Le Modele Organisationnel

*(Contenu consolide du plan recu, avec la terminologie « Palier » a la place de
« Niveau » pour eviter la collision avec la Partie A.)*

## B.1 Principes directeurs

1. **Souverainete organisationnelle** — une Organisation ne depend d'aucune autre
   pour definir sa structure, son vocabulaire ou ses politiques internes.
2. **Genericite totale** — aucun concept metier sectoriel n'est code en dur dans le
   schema. Tout est une instance typee d'un concept generique.
3. **Separation identite / appartenance** — une Personne existe independamment
   d'ou elle est rattachee. L'appartenance est toujours une relation, jamais un
   conteneur.
4. **Tracabilite temporelle** — rien n'est jamais ecrase.
5. **Autonomie en cascade** — chaque Palier de la hierarchie peut, s'il en a le
   droit, deroger aux regles du Palier superieur.

## B.2 Le Lexique EGEN

| Terme | Definition |
|---|---|
| **Organisation** | L'entite souveraine — ministere, universite, entreprise, ONG, hopital. Racine de tout. |
| **Cellule** | Le noeud generique et recursif de la hierarchie interne (Faculte, Departement, Classe, Agence, Service...). |
| **Etablissement** | Convention de nommage : une Cellule de Palier 1, directement rattachee a l'Organisation. Pas une table distincte. |
| **Type de Cellule** | La nomenclature qui donne son vocabulaire metier a une Cellule. Definie **par Organisation**. |
| **Lexique Organisationnel** | L'ensemble des Types de Cellule qu'une Organisation a choisi ou cree pour elle-meme. |
| **Modele Sectoriel** | Un Lexique pre-rempli fourni par EGEN, que l'Organisation adopte puis modifie librement. |
| **Tutelle** | La relation de rattachement d'un Etablissement a une ou plusieurs Organisations. |
| **Affectation** | La relation entre une Personne et une Cellule, avec un role et une periode de validite. |
| **Mandat** | Les droits et responsabilites portes par une Affectation active. |
| **Delegation** | Le transfert temporaire d'un Mandat, sans rompre l'Affectation d'origine. |
| **Ressource Locale** | Une ressource rattachee a une Cellule precise. |
| **Ressource Souveraine** | Une ressource rattachee directement a l'Organisation, partagee par relations explicites. |
| **Catalogue** | L'ensemble des modules que la plateforme EGEN sait proposer. |
| **Souscription** | Ce qu'une Organisation a choisi d'acquerir dans le Catalogue. |
| **Activation** | Ce qu'une Cellule precise a choisi d'allumer, parmi ce que l'Organisation a souscrit. |
| **Politique organisationnelle** | Regles de configuration definies a un Palier donne, heritees par defaut en dessous. |
| **Derogation** | Le droit d'une Cellule de remplacer une Politique organisationnelle heritee. |
| **Succession Organisationnelle** | La trace reliant une Cellule disparue a celle(s) qui lui succede. |

## B.3 a B.15

*(Contenu integral inchange par rapport a la version transmise le 22 juillet 2026 —
Organisation racine souveraine, Cellule recursive, Lexique et Modeles Sectoriels,
Tutelle, Personnes & Comptes, Affectation & Mandat, Delegation, Ressources
Locales/Souveraines, Catalogue/Souscription/Activation, Politique organisationnelle &
Derogation, Cycle de vie & Succession, schema de synthese, recommandations
techniques. Voir l'historique de conversation ou une version anterieure de ce fichier
pour le detail section par section si besoin de le re-derouler integralement —
aucune de ces sections n'a ete remise en cause par le refactoring du 22 juillet.)*

---

# PARTIE C — La Jointure : ou chaque concept vit dans l'architecture noyau

| Concept (Partie B) | Vit dans | Niveau | Depend de |
|---|---|---|---|
| Organisation, Cellule, Lexique, Tutelle, Affectation, Mandat, Delegation, Politique organisationnelle | `egen-modules/business/organization` | 2 | `identity-provider-api` (pour referencer une Personne sans la posseder) |
| Personnes / Comptes | `egen-modules/system/identity/identity-provider-keycloak` | 2 | `identity-provider-api` |
| Ressource (Locale/Souveraine) | `egen-modules/business/resource` (a venir) | 2 | `organization-api` |
| Modeles Sectoriels | `egen-modules/business/reference-data` | 2 | — (seed data consommee par `organization` a la creation) |
| Catalogue / mecanisme Souscription-Activation | `kernel-systems/module-registry` (B2, a venir) | 0 | — |
| Enregistrements Souscription/Activation d'une Organisation donnee | Base du module `organization`, valides par B2 au chargement | 0 (mecanisme) + 2 (donnees) | — |
| Synchronisation Affectation/Delegation/Tutelle -> SpiceDB | Evenements via `kernel-eventbus` (0), consommes par `authorization-provider-spicedb` (2, a venir) | 0 + 2 | — |

**C.1 — Sur la fusion Organization (A2) + Affiliation (A3)**
**Actee et realisee** le 22 juillet 2026 : un seul module `organization`
(`organization-api` / `organization-impl`), avec Affectation/Mandat/Delegation dans
un sous-package `.affiliation`, et Politique organisationnelle/Derogation dans un
sous-package `.politique`. La granularite de deploiement plus fine (mettre a jour
Affectation sans redeployer tout Organization) est abandonnee au profit de la
simplicite du graphe de dependances — c'etait le compromis assume dans la
proposition initiale.

**C.2 — Sur la categorie de Reference-data**
**Actee** : `business`, pas `system` — ce n'est pas un fournisseur d'infrastructure
interchangeable (a la difference de Keycloak/SpiceDB), c'est du contenu metier
fourni en usine.

---

# PARTIE D — Tutelle : intra-instance, decision finale

L'ancienne tension (§ B.6 : le CHU rattache a deux Organisations, avec SpiceDB
arbitrant les droits « sans que les deux Organisations ne se voient mutuellement »)
supposait implicitement une base ou un SpiceDB partages entre Organisations — une
hypothese rendue fausse par le Point 1 (mono-tenant strict par instance).

**Decision actee le 22 juillet 2026, qui clot definitivement cette tension** : la
Tutelle est et reste **strictement intra-instance**. Une instance EGEN deployee
represente **une seule entreprise** ; la Tutelle sert uniquement a hierarchiser et
regrouper des Organisations **au sein de cette meme instance** (typiquement : une
maison-mere et ses filiales, ou un ministere et les etablissements sous sa tutelle,
tant qu'ils partagent le meme deploiement). Aucune federation inter-instances n'est
prevue ni necessaire — le mono-tenant par instance reste total, "chacun avec son
environnement".

Des trois options envisagees dans une version anterieure de ce document (Tutelle
restreinte a l'intra-instance / federation inter-instances dediee / zone mutualisee
limitee), c'est la **premiere** qui est retenue, sans reserve ni scenario de
contournement a batir : le cas d'un CHU sous tutelle de deux Organisations
veritablement heberges sur deux instances EGEN distinctes sort du modele de donnees
EGEN et se traiterait, si le besoin se presentait un jour, par une integration API
ponctuelle hors plateforme — pas par une extension du modele de Tutelle lui-meme.

Consequence concrete sur le code : aucune refonte du modele de donnees Tutelle
n'etait necessaire pour appliquer cette decision — `Tutelle.organisationId` et
`Tutelle.celluleRacineId` ont toujours ete des references locales a l'instance. Seuls
le deplacement vers `egen-modules/business/organization` et la clarification
documentaire (voir la migration `V1__init_organization.sql`) etaient requis.

---

# PARTIE E — Points ouverts restants (etat au 22 juillet 2026, apres refactoring)

1. **Contenu exact du primitif Niveau 1** (Identity + Authorization + Policy-noyau,
   § A.5) — **toujours ouvert**. Voir le plan de suite de programmation pour une
   proposition de conception a valider avant tout premier code.
2. ~~Fusion ou separation Organization/Affiliation~~ — **tranche et realise** :
   fusion en un seul module.
3. ~~Categorie `system` vs `business` pour Reference-data~~ — **tranche et
   realise** : `business`.
4. ~~Resolution de la tension Tutelle inter-organisation~~ — **tranche
   definitivement** : intra-instance uniquement, aucune federation (Partie D).
