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
egen-platform/                  (racine du reacteur Maven)
├── egen-kernel/
│   ├── kernel-sdk/              ← contrat public (extension, event, contexte, manifest)
│   ├── kernel-domain/            (a venir)
│   ├── kernel-systems/           (a venir — A1, A2, A3, B1-B4, E1-E3)
│   ├── kernel-eventbus/          (a venir)
│   ├── kernel-plugin-engine/     (a venir)
│   └── kernel-bootstrap/         (a venir)
└── egen-modules/                 (reserve — modules metier, hors scope du Kernel)
```

## Etat d'avancement

| Module | Statut |
|---|---|
| `kernel-sdk` | ✅ Livre — contrat d'extension, evenementiel, Contexte, Manifeste d'Extension |
| `identity` (A1) + `reference-data` (B4) | À venir |
| `organization` (A2) | À venir |
| `affiliation` (A3) | À venir |
| `policy`, `module-registry`, `resource` (B1-B3) | À venir |
| `communication`, `audit`, `authorization` (E1-E3) | À venir |
| `kernel-eventbus`, `kernel-plugin-engine`, `kernel-bootstrap` | À venir |

## Construire le projet

Prerequis : JDK 21, Maven 3.9+.

```bash
mvn -B verify
```

La CI GitHub Actions (`.github/workflows/ci.yml`) reconstruit et teste l'integralite
du reacteur a chaque push sur `main` et sur chaque pull request — c'est la porte de
validation faisant foi du projet.
