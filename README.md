# EGEN Kernel — plateforme de gouvernance fédérée

**Le Kernel gouverne, délègue et suit. Il n'exécute pas ce que des solutions éprouvées exécutent déjà.**

EGEN (CIVITAS Africa) organise les systèmes d'une organisation (par exemple une université) en
**écosystèmes** (Métier, Transversal, Intelligence et Agents). Chaque écosystème est gouverné par un
**Control Plane** autonome (un système qui reçoit des intentions, applique les politiques, délègue
l'exécution et suit les résultats). Un **Federation Plane** relie plusieurs Control Planes sans les
remplacer. Le **Kernel EGEN** est l'implémentation de référence d'un Control Plane.

## État du dépôt

Le dépôt contient la documentation d'architecture et le plan de développement ; le code arrive phase par phase,
à commencer par les contrats et le kit de conformité (phase R1).
L'état exact de chaque phase est dans [docs/00-etat-et-reprise.md](docs/00-etat-et-reprise.md) :
c'est le seul endroit où figure un statut.

## Par où commencer

1. [docs/README.md](docs/README.md) : sommaire, ordre de lecture, règles de rédaction.
2. [docs/00-etat-et-reprise.md](docs/00-etat-et-reprise.md) : où l'on en est, comment reprendre.
3. [docs/01-vision-et-perimetre.md](docs/01-vision-et-perimetre.md) puis
   [docs/03-architecture-generale.md](docs/03-architecture-generale.md) : le schéma d'ensemble.
4. [docs/14-plan-de-developpement.md](docs/14-plan-de-developpement.md) : les phases et étapes.

Les deux documents fondateurs (vision et inventaire des questions) sont dans
[docs/fondations/](docs/fondations/). Ils se lisent en entier avant toute décision d'architecture.

## Ce que le Kernel n'est pas

- Pas un moteur de déploiement, de messagerie, de workflow ou d'identité : il délègue à des moteurs spécialisés.
- Pas une copie centrale de toutes les ressources : chaque Control Plane garde la maîtrise de ses données.
- Pas dépendant d'une technologie unique pour **participer** à la fédération : seuls les contrats comptent.
- Pas porteur de concepts métier (cellule, tenant, catalogue…) : il les ignore.

## Règles non négociables

- La documentation précède le code : le contrat s'écrit avant l'implémentation.
- Adopter avant de coder : une solution éprouvée existante prime sur du code écrit ici (décision D-07).
- Aucun secret (jeton, mot de passe, clé) dans un fichier, un commit ou un document.
- `main` ne reçoit que des étapes vérifiées par des tests ; il est toujours à jour et vérifié.

Licence : à définir (point ouvert PO-09 dans [docs/16-risques-et-verifications.md](docs/16-risques-et-verifications.md)).

Contribuer : voir [CONTRIBUTING.md](CONTRIBUTING.md).
