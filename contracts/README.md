# Contrats EGEN (C1 à C6)

## À quoi sert ce dossier
Contenir la **source machine** des contrats décrits dans [docs/04-contrats-egen.md](../docs/04-contrats-egen.md).

## Contenu

| Dossier | Contenu |
|---|---|
| `schemas/` | Schémas JSON (2020-12) : descripteur C1, capacité C2, demande et état d'opération C3/C4, événements C5, erreur |
| `openapi/` | API d'un Control Plane (OpenAPI 3.1.1) : C1 à C4 et repli HTTP de C5 |
| `asyncapi/` | Événements C5 (AsyncAPI 3.0.0), **généré** par `egen-gen-asyncapi` depuis `catalog/` : ne pas modifier à la main |
| `catalog/` | Types d'événements, raisons d'erreur standard, capacités standard |
| `examples/` | Exemples valides et invalides avec leur manifeste ; chaque exemple invalide dit pourquoi il doit être refusé |
| `baseline/` | Instantané de chaque version publiée, pour la détection de rupture (règle C6) |
| `VERSION` | Version courante des contrats (`MAJEUR.MINEUR`) |

## Règles

- Le document 04 et ces fichiers disent la même chose : des tests le vérifient.
- Une rupture de compatibilité exige une version majeure ; un ajout, une version mineure (`egen-check-breaking`).
- Aucun nom ne contient un mot réservé de la Plateforme et aucun contrat ne cite une technologie (`egen-check-arch`).
