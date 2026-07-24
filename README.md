# EGEN Kernel

EGEN est une plateforme de gouvernance d'organisations souveraines et de services
modulaires, developpee par **CIVITAS Africa**. Ce depot contient le **Kernel** et
les **modules Niveau 2** (providers systeme + modules business) qui gravitent autour
de lui — voir la Charte d'Architecture ci-dessous pour la distinction exacte entre
ces trois notions.

**Logiciel proprietaire — tous droits reserves.** Ce depot est prive et son contenu
n'est distribue sous aucune licence open source. Toute reproduction, modification ou
distribution en dehors de CIVITAS Africa est interdite sauf autorisation explicite.

## Reference d'architecture

**[`docs/architecture/charte-v3.md`](docs/architecture/charte-v3.md)** est le
document qui fait foi pour toute decision d'architecture dans ce depot. Il fixe le
modele a trois niveaux (Niveau 0 irreductible / Niveau 1 primitif / Niveau 2
pluggable) qui remplace le modele "systemes plats A1-E3" utilise jusqu'au 22 juillet
2026 — lire ce document avant de contribuer, surtout si vous arrivez avec le
vocabulaire de l'ancienne classification (A1, A2, B4...) en tete : les numeros
restent valables comme reference historique, mais le **Niveau** (0/1/2) de chaque
systeme a change de sens.

## Principe directeur

Le Kernel possede la verite organisationnelle (identite, souverainete, hierarchie,
politique, ressources, audit) et delegue toujours la logique d'execution
(autorisation, evenements, orchestration de processus, notification) a des services
externes qu'il invoque et journalise, sans jamais la reimplementer. Le Kernel
lui-meme (Niveau 0 + Niveau 1) ignore tout de ce qui est charge par-dessus lui — voir
la Charte v3, Partie A.

## Arborescence (etat reel au 23 juillet 2026, apres refactoring vers la Charte v3 et livraison du primitif Niveau 1)

```
egen-platform/                                     (racine du reacteur Maven)
├── egen-kernel/                                    ← Niveau 0 + Niveau 1 uniquement
│   ├── kernel-sdk/                                 ← contrat public, JPMS pur
│   │                                                  (extension, event, contexte,
│   │                                                  manifest, tracabilite,
│   │                                                  permission/{identity,
│   │                                                  authorization,policy})
│   ├── kernel-jpa-support/                         ← TracabiliteEmbeddable (mapping
│   │                                                  JPA partage entre tous les
│   │                                                  modules -impl, kernel comme
│   │                                                  egen-modules)
│   ├── kernel-domain/                              ← reserve (module-domain, B2 —
│   │                                                  a venir)
│   ├── kernel-systems/                             ← primitifs Niveau 1 (point 3, livre)
│   │   ├── identity/                                 KernelSubjectService — sujet
│   │   │                                             minimal, sans persistance
│   │   ├── authorization/                            KernelPermissionCheckImpl —
│   │   │                                             octrois de capacites, fail-closed
│   │   └── policy/                                   PolitiqueNoyauImpl — refuse
│   │                                                  systematiquement, sans exception
│   ├── kernel-eventbus/                            ← a venir
│   ├── kernel-plugin-engine/                       ← a venir
│   ├── kernel-bootstrap/                           ← a venir
│   └── kernel-test-support/                        ← a venir
└── egen-modules/                                   ← Niveau 2 (pluggable)
    ├── system/                                      ← les providers (ponts vers
    │   └── identity/                                  l'exterieur : Keycloak,
    │       ├── identity-provider-api/                 SpiceDB, un futur fournisseur
    │       └── identity-provider-keycloak/             de communication...)
    └── business/                                    ← les modules metier
        ├── organization/                              (fusion Organisation +
        │   ├── organization-api/                      Rattachements + Politique
        │   │   └── .../api/{affiliation,politique}/    organisationnelle)
        │   └── organization-impl/
        │       └── .../impl/{affiliation,politique}/
        └── reference-data/
            ├── reference-data-api/
            └── reference-data-impl/
```

### Pourquoi ce depot n'est plus un empilement plat de "systemes A1-E3"

Avant le 22 juillet 2026, `kernel-systems/` contenait `identity`, `reference-data`,
`organization`, `affiliation` et `policy`, tous traites comme des systemes pairs du
Kernel. Une analyse rigoureuse a la lumiere de la Charte v3 a etabli qu'aucun des
cinq n'etait, en verite, du Niveau 0 ou 1 :

- `identity` et `organization`/`affiliation`/`policy` etaient deja des implementations
  riches, couplees a une technologie concrete (Keycloak) ou a un concept
  intrinsequement Niveau 2 (Organisation/Cellule) — jamais le "minimum vital avant
  qu'aucun module ne soit charge" que Niveau 0/1 designe.
- `policy` en particulier portait a tort l'etiquette "Systeme B1" : son contenu reel
  (Politique + Derogation sur un Contexte Organisation/Cellule, resolution "le plus
  proche l'emporte") est la Politique **organisationnelle** (§B.12 de la Charte v3),
  pas la Politique-**noyau** (le vrai B1). Les deux portent le meme mot dans le
  langage courant ; ce n'est pas le meme systeme.

Consequence : ce contenu a ete deplace tel quel (repackage, pas reecrit) vers
`egen-modules/`, avec `organization` + `affiliation` + `policy` fusionnes en un seul
module business (`organization`), conformement a la Charte v3 (§C.1). `identity` est
devenu un provider (`egen-modules/system/identity/`), avec un contrat generique
(`identity-provider-api`) separe de son implementation Keycloak
(`identity-provider-keycloak`) — pour que d'autres providers de la meme capacite
puissent le rejoindre au fil du temps sans jamais casser ce qui en depend, exactement
le modele de connecteurs pluggables d'ActivePieces ou n8n.

`kernel-systems/` porte desormais les trois primitifs Niveau 1 sous leur forme
correcte : `identity` (`KernelSubjectService`, sujet minimal), `authorization`
(`KernelPermissionCheckImpl`, octrois de capacites fail-closed) et `policy`
(`PolitiqueNoyauImpl`, la vraie Politique-noyau — voir `docs/architecture/
charte-v3.md`, §A.5, pour la conception complete). Livre le 23 juillet 2026, en
reponse au point 3 de la Charte v3 — une premiere proposition rigoureuse, pas une
verite gravee : elle pourra evoluer une fois eprouvee par un consommateur reel
(module-registry, kernel-plugin-engine, kernel-bootstrap).

## Le DAG de dependances, desormais impose mecaniquement

Le reacteur Maven calcule l'ordre de construction et refuse tout cycle — ca a
toujours ete vrai. Ce qui ne l'etait pas jusqu'au 22 juillet 2026 : rien n'empechait
mecaniquement une dependance croisee **non cyclique** mais interdite (par exemple un
module qui importerait directement le `-impl` d'un autre plutot que son `-api`). Le
`pom.xml` racine porte desormais une execution `maven-enforcer-plugin` (regle
`bannedDependencies`), heritee par tous les modules du reacteur, qui bloque
explicitement toute dependance de scope compile/runtime vers un artefact `-impl` ou
un provider concret — seul un scope `test` reste tolere (integration reelle avec une
vraie implementation CDI, ex. `organization-impl` -> `identity-provider-keycloak`).
Voir les commentaires dans le `pom.xml` racine pour le detail, y compris l'exception
documentee que `kernel-bootstrap` devra assumer explicitement le jour de sa creation.

## Etat d'avancement

| Module | Niveau / Categorie | Statut |
|---|---|---|
| `kernel-sdk` | 0 | ✅ Livre — extension, event, Contexte, Manifeste d'Extension, Socle de Traçabilite |
| `kernel-jpa-support` | 0 (partage) | ✅ Livre — TracabiliteEmbeddable |
| `kernel-systems/identity` | 1 (primitif) | ✅ Livre — `KernelSubject` (kernel-sdk) + `KernelSubjectService`, sans persistance |
| `kernel-systems/authorization` | 1 (primitif) | ✅ Livre — `KernelCapability` (kernel-sdk) + `KernelPermissionCheckImpl`, octrois/revocations avec Traçabilite complete |
| `kernel-systems/policy` | 1 (primitif) | ✅ Livre — `PolitiqueNoyau` (kernel-sdk) + `PolitiqueNoyauImpl`, refuse systematiquement |
| `kernel-domain`, `module-registry`, `kernel-plugin-engine`, `kernel-eventbus`, `kernel-bootstrap`, `kernel-test-support` | 0 | À venir |
| `egen-modules/system/identity` (`identity-provider-api` + `identity-provider-keycloak`) | 2, system | ✅ Livre — Personne, Compte, Historique d'Identite (provider Keycloak) |
| `egen-modules/business/organization` | 2, business | ✅ Livre — Organisation, Cellule (+ Fermeture Transitive), Lexique, Tutelle, Succession ; sous-domaine `.affiliation` (Affectation, Mandat, Delegation) ; sous-domaine `.politique` (Politique organisationnelle, Derogation) |
| `egen-modules/business/reference-data` | 2, business | ✅ Livre — Pays, Langue, Devise, Fuseau Horaire, Unite de Mesure, Modele Sectoriel, Type de Cellule Modele, Mandat Modele |
| `egen-modules/system/authorization` (SpiceDB), `egen-modules/system/communication` | 2, system | À venir |
| `egen-modules/business/resource` | 2, business | À venir |

## Convention de versionnement Flyway

Chaque module gere sa propre sequence Flyway et sa propre table
`flyway_schema_history_*` — plus de numerotation globale unique sur l'ensemble de la
plateforme comme avant le 22 juillet 2026. **Nuance importante, corrigee apres un
premier echec CI sur ce refactoring** : "propre sequence" ne veut pas dire "peut
toujours reprendre a V1 sans regarder les autres modules". Flyway valide l'unicite
des numeros de version au sein de l'**ensemble de migrations resolu pour une
execution donnee** (les locations combinees), jamais au sein d'une seule table
d'historique. Deux modules qui ne se combinent jamais dans une meme execution (ex.
`reference-data-impl`, qui n'a besoin des migrations d'aucun autre module) peuvent
chacun reprendre a V1 sans risque. Mais des qu'un module ajoute la location d'un
autre a la sienne pour ses tests d'integration reels — ce qui est exactement le cas
de `organization-impl`, qui a besoin des tables `identity` pour verifier une
reference Personne — leurs numeros doivent rester mutuellement uniques dans cet
ensemble combine, meme si leurs tables d'historique restent bien distinctes en
production.

| Module | Sequence | Combine avec (tests) |
|---|---|---|
| `identity-provider-keycloak` | V1 (identity) | — (mais reserve V1 pour tout module qui le combinera) |
| `reference-data-impl` | V1 (referencedata) | aucun — independance reelle |
| `organization-impl` | V2 (organization), V3 (affiliation), V4 (politique organisationnelle) | `identity` (V1) — d'ou le decalage a partir de V2 |
| `kernel-systems/authorization` | V1 (authorization) | aucun — independance reelle |

Quand un module a legitimement besoin des tables d'un autre pour ses tests
d'integration reels (ex. `organization-impl` a besoin d'`identity` pour verifier une
reference Personne), il ajoute la location Flyway de l'autre module en plus de la
sienne dans son `application.properties`, et declare l'artefact correspondant en
dependance de scope **test** uniquement (jamais compile/runtime — voir la regle
Enforcer ci-dessus).

## Construire le projet

Prerequis : JDK 21, Maven 3.9+, Docker (requis par Quarkus Dev Services pour les
tests d'integration des modules `-impl`, qui provisionnent un PostgreSQL ephemere
automatiquement).

```bash
mvn -B verify
```

La CI GitHub Actions (`.github/workflows/ci.yml`) reconstruit et teste l'integralite
du reacteur a chaque push sur `main` et sur chaque pull request — c'est la porte de
validation faisant foi du projet, Docker etant disponible nativement sur les runners
GitHub-hosted.
