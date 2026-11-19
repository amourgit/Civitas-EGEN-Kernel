# Fiche — Kubernetes (et k3s)

## À quoi sert ce document
Documenter Kubernetes comme socle du Kernel de référence et moteur par défaut de `workload-runtime`.

## Ce qu'il ne couvre pas
L'installation d'un cluster en production ([12](../12-resilience-et-exploitation.md)) ni Crossplane ([crossplane.md](crossplane.md)).

## Rôle
Deux rôles distincts :
1. **Socle** du Kernel de référence : son API sert de Control API et de registre local (types personnalisés, numéro de
   génération, conditions, observation des changements, droits, admission).
2. **Moteur** de `workload-runtime` : il exécute les charges de travail.

## Pourquoi lui
Il fournit **déjà** ce qu'il serait long et risqué de coder soi-même : stockage versionné des ressources, observation des changements,
contrôle d'accès, validation à l'admission, élection de chef pour les opérateurs, cadre de contrôleurs. Le choisir, c'est
appliquer D-07. **k3s** (distribution légère) convient aux petits sites.

## Vérifié
Aucun fait nouveau vérifié dans cette documentation : les affirmations ci-dessous sont de connaissance générale et sont à
confirmer sur les sources officielles (VR-11, VR-12).

## À vérifier
- VR-11 : version de Kubernetes et de k3s retenues, support à long terme, installation à haute disponibilité.
- VR-12 : disponibilité stable des règles d'admission déclaratives (ValidatingAdmissionPolicy) dans la version retenue.

## Comment EGEN l'utilise
- Types EGEN installés comme **types personnalisés** (CRD), groupe `egen.civitas.africa` ([05](../05-kernel-de-reference.md)).
- Un **espace de noms** par écosystème ou par périmètre ; RBAC par espace de noms.
- Règles d'admission écrites en CEL pour refuser les ressources non conformes.
- Normes de sécurité des pods au niveau `restricted` par défaut.
- Mécanisme de baux (leases) pour l'élection de chef de l'opérateur EGEN.

## Pièges connus
- **Moindre privilège** : un contrôle d'accès ne doit pas exiger un droit plus large que sa fonction. Exemple : le rôle du
  Kernel n'a pas `list` sur les déploiements ; pour contrôler l'accès, lister les **pods**.
- **Historique de déploiement** : fixer consciemment `revisionHistoryLimit`, car le retour arrière s'appuie sur l'historique.
- **Jetons de comptes de service d'essai** : ils expirent ; un script d'essai doit contrôler leur validité au départ.
- **Réapplication** : un contrôleur réapplique l'état voulu ; une modification manuelle est écrasée.
- **Nombre de types** : chaque type personnalisé alourdit l'API ; n'installer que les types dont on a besoin (voir [crossplane.md](crossplane.md)).

## Tests d'intégration attendus
Sur un cluster k3s réel lancé par script (par Samuel) : création, mise à jour progressive, retour arrière, refus à l'admission,
élection de chef (arrêt du chef, reprise par un autre réplica), perte de jeton.

## Plan de sortie
Les contrats C1 à C6 sont indépendants de Kubernetes (G-04) : le Kernel de référence peut être réécrit sur un autre substrat
sans changer ce que voient les autres Control Planes.
