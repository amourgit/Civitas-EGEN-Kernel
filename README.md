# EGEN Kernel

EGEN est une plateforme de gouvernance d'organisations souveraines et de services
modulaires, developpee par **CIVITAS Africa**. Ce depot contient le **Kernel** :
le minimum vital sans lequel aucun module metier (Academie, RH, Finance, Sante...)
ne peut fonctionner.

**Logiciel proprietaire — tous droits reserves.** Ce depot est prive et son contenu
n'est distribue sous aucune licence open source. Toute reproduction, modification ou
distribution en dehors de CIVITAS Africa est interdite sauf autorisation explicite.

## Principe directeur

Le Kernel possede la verite organisationnelle (identite, souverainete, hierarchie,
politique, ressources, audit) et delegue toujours la logique d'execution (autorisation,
evenements, orchestration de processus, notification) a des services externes qu'il
invoque et journalise, sans jamais la reimplementer.

## Arborescence

```
egen-platform/                          (racine du reacteur Maven)
├── egen-kernel/
│   ├── kernel-sdk/                      ← contrat public (extension, event, contexte,
│   │                                        manifest, tracabilite)
│   ├── kernel-jpa-support/              ← TracabiliteEmbeddable (mapping JPA partage
│   │                                        entre tous les systemes -impl)
│   ├── kernel-systems/
│   │   ├── identity/                    ← Systeme A1
│   │   │   ├── identity-api/
│   │   │   └── identity-impl/
│   │   ├── reference-data/              ← Systeme B4
│   │   │   ├── reference-data-api/
│   │   │   └── reference-data-impl/
│   │   └── organization/                ← Systeme A2
│   │       ├── organization-api/
│   │       └── organization-impl/
│   ├── kernel-eventbus/                  (a venir)
│   ├── kernel-plugin-engine/             (a venir)
│   └── kernel-bootstrap/                 (a venir)
└── egen-modules/                         (reserve — modules metier, hors scope du Kernel)
```

Note d'architecture : le "kernel-domain" evoque initialement comme couche separee a
ete replie dans chaque `-api` (modele de lecture + commandes) — c'est le pattern
hexagonal standard (domaine + ports dans un seul module), qui evite de dupliquer les
memes objets metier dans trois couches distinctes. `kernel-jpa-support` a ete
introduit en cours de route : deux systemes `-impl` ne doivent jamais dependre l'un
de l'autre, mais ils ont tous deux besoin de la meme projection JPA du Socle de
Traçabilite — ce module la centralise sans violer le DAG de dependances.

## Etat d'avancement

| Module | Statut |
|---|---|
| `kernel-sdk` | ✅ Livre — extension, event, Contexte, Manifeste d'Extension, Socle de Traçabilite |
| `kernel-jpa-support` | ✅ Livre — TracabiliteEmbeddable, partage entre tous les `-impl` |
| `identity` (A1) | ✅ Livre — Personne, Compte, Historique d'Identite |
| `reference-data` (B4) | ✅ Livre — Pays, Langue, Devise, Fuseau Horaire, Unite de Mesure, Modele Sectoriel, Type de Cellule Modele, Mandat Modele |
| `organization` (A2) | ✅ Livre — Organisation, Lexique Organisationnel, Type de Cellule, Cellule (arbre recursif + Fermeture Transitive), Tutelle, Succession Organisationnelle |
| `affiliation` (A3) | À venir |
| `policy`, `module-registry`, `resource` (B1-B3) | À venir |
| `communication`, `audit`, `authorization` (E1-E3) | À venir |
| `kernel-eventbus`, `kernel-plugin-engine`, `kernel-bootstrap` | À venir |

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
