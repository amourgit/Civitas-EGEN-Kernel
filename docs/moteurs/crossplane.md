# Fiche — Crossplane

## À quoi sert ce document
Documenter Crossplane, le cadre sur lequel le Kernel de référence compose ses capacités et gère ses extensions.

## Ce qu'il ne couvre pas
Les types EGEN eux-mêmes ([05](../05-kernel-de-reference.md)).

## Rôle
Crossplane est un **cadre pour construire des plans de contrôle déclaratifs** sur Kubernetes. Termes utilisés :

| Terme | Définition en une phrase |
|---|---|
| **Définition de ressource composite (XRD)** | Décrit une nouvelle ressource de haut niveau (ici : une capacité ou un `Service`) : son schéma, sans dire comment elle est réalisée. |
| **Composition** | Décrit **comment** une ressource composite est réalisée : quelles ressources plus basses créer. Plusieurs Compositions peuvent réaliser la même XRD : ce sont les implémentations. |
| **Fonction de composition** | Un programme qui calcule le résultat d'une Composition (plus souple qu'un modèle statique). |
| **Provider** | Une extension qui sait parler à un système externe (nuage, base, API) et le piloter. |
| **Configuration** | Un paquet qui regroupe XRD, Compositions et dépendances ; installable et versionné. |

## Pourquoi lui
Il correspond point par point aux fonctions du Kernel : capacité = XRD, implémentations = Compositions, extensions =
paquets, délégation = Providers. Il est **adopté**, pas recodé (D-07).

**Alternatives écartées**

| Solution | Raison |
|---|---|
| Kratix | Cadre de plateforme sous licence Apache 2.0 mais pas un projet de la fondation CNCF à ce jour ; porté par une société ([S-14](../references.md)) ; recouvre Crossplane. À reconsidérer seulement si Crossplane échoue à un besoin précis. |
| Terraform / OpenTofu seuls | Outils d'application ponctuelle, sans réconciliation continue ; ne forment pas un plan de contrôle. Utilisables **derrière** un Provider si nécessaire. |
| Plan de contrôle écrit sur mesure | Contraire à D-07. |

## Vérifié
| Fait | Source | Date |
|---|---|---|
| Crossplane est un projet **diplômé** du CNCF (niveau de maturité le plus élevé) : statut au 28 octobre 2025, annonce du 6 novembre 2025. | [S-01](../references.md) | 2026-11-19 |
| La version 2.0 a introduit une architecture affinée pour des plans de contrôle d'applications complets. | [S-01](../references.md) | 2026-11-19 |

## À vérifier
- VR-01 : version retenue ; composition de ressources quelconques et ressources à portée d'espace de noms en 2.x ; modes
  de gestion des ressources (dont un mode « observation seule » pour des ressources référencées) ; limitation du
  nombre de types installés (définitions de ressources gérées et politiques d'activation).
- VR-02 : état (alpha, bêta, stable) des « fonctions d'opération » de la 2.x et risque de collision de nom avec le type
  `Operation` d'EGEN (PO-02).
- VR-16 : signature et vérification des paquets.

## Comment EGEN l'utilise
- Chaque capacité = une XRD étiquetée `egen.civitas.africa/capability` ; ses implémentations = des Compositions étiquetées par moteur.
- Le Kernel s'installe comme une **Configuration** « egen-core » (types, compositions, politiques).
- Chaque moteur s'ajoute par un Provider (ou une fonction) sans toucher au cœur (G-05).

## Pièges connus
- **Nombre de types** : installer tous les types d'un Provider alourdit l'API ; n'activer que ceux utilisés.
- **Épinglage de version** : épingler Crossplane, les Providers et les fonctions ; mettre à jour par étapes, jamais « dernière version ».
- **Collision de noms** : éviter d'appeler `Operation` un type qui existe déjà dans l'écosystème (PO-02).

## Tests d'intégration attendus
`crossplane render` (ou équivalent, à vérifier) pour tester une Composition sans cluster ; un cluster k3s réel pour la
convergence ; test de mise à jour d'un paquet sans interruption.

## Plan de sortie
Les XRD sont des schémas ; les contrats externes (C2) n'exposent pas Crossplane. Remplacer Crossplane revient à réécrire la
couche de composition du Kernel de référence, sans changer les contrats.
