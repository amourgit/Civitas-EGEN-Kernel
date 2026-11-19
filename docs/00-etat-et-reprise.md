# 00 — État et reprise

## À quoi sert ce document
Dire où en est le projet, ce qui est gelé, ce qui vient ensuite, et comment reprendre une session de travail.
**C'est le seul document qui porte un statut** (fait / en cours / à faire / en attente de validation).

## Ce qu'il ne couvre pas
Le détail des phases (voir [14](14-plan-de-developpement.md)), les vérifications en attente et les points
ouverts (voir [16](16-risques-et-verifications.md)).

## Point de situation

| Élément | Valeur |
|---|---|
| Dernière mise à jour | 19 novembre 2026 |
| Code dans le dépôt | **Aucun.** Le premier code est la phase R1. |
| Développement antérieur | **Gelé et retiré de l'arbre** (décision D-04). Rien ne se développe hors de ce plan. |
| Phase en cours | R0 — Assainissement (reste la validation par Samuel) |
| Prochaine phase | R1 — Contrats et kit de conformité |

## Statut des phases

| Phase | Intitulé | Statut |
|---|---|---|
| R0 | Assainissement | **En attente de validation par Samuel** |
| R0.1 | Archiver l'état antérieur | Fait |
| R0.2 | Supprimer le code et la documentation périmés | Fait |
| R0.3 | Écrire la documentation neuve (documents 00 à 16, fiches moteurs) | Fait |
| R0.4 | Valider avec Samuel, puis fusionner sur `main` | **À faire (attend Samuel)** |
| R1 | Contrats C1 à C6 et kit de conformité | À faire |
| R2 | Kernel de référence : un Control Plane complet | À faire |
| R3 | Identité, confiance et politiques | À faire |
| R4 | Federation Plane et opérations distribuées | À faire |
| R5 | Moteurs spécialisés et intégrations (dont Nomad) | À faire |
| R6 | Exploitation, résilience, bootstrap | À faire |
| R7 | Écosystème d'Intelligence et d'Agents | À faire |

Une phase ne démarre que lorsque la précédente a atteint son critère de sortie (voir [14](14-plan-de-developpement.md)),
sauf accord explicite de Samuel inscrit ici.

## Pour reprendre une session

1. Lire ce document, puis [14](14-plan-de-developpement.md) pour la phase en cours.
2. Lire [16](16-risques-et-verifications.md) : vérifications en attente et points ouverts qui touchent la phase.
3. Lire en entier les deux documents de [fondations/](fondations/) si une décision d'architecture est en jeu.
4. Cloner avec un jeton à portée limitée ; ne jamais écrire le jeton dans un fichier.

## Règles de travail

- **`main` ne reçoit que de l'état validé par Samuel**, en avance directe. Une branche de travail se pousse sans fusion.
- **La documentation d'abord** : le contrat normatif s'écrit avant le code.
- **Commits explicites** : titre = fonctionnalité touchée ; corps = fichiers, tests, état de vérification.
- **Relever chaque incohérence** : à chaque test, comparer le résultat obtenu à l'attendu ; tout écart, même
  non critique, s'écrit dans le registre (document 16) avec sa gravité. Un écart **critique** (fuite de secret,
  droit contourné, perte de données) interrompt les tests et se traite immédiatement.
- **Noter toute vérification non faite** dans le document 16 (qui peut la faire, quel résultat attendu).
  Chaque fin de travail rappelle ce qui est **vérifié ici** et ce qui **ne l'est pas**.
- **Vérifier avant de conclure** : lire le code plutôt que la seule documentation ; essayer plusieurs motifs de
  recherche avant d'affirmer qu'une chose n'existe pas ; prouver qu'un test peut échouer.
- **Ne jamais écrire ni demander un jeton ou un mot de passe réel** dans un document ou un message.
- **Ne pas toucher un autre dépôt sans accord de Samuel.**

## Pièges connus (valables pour tout script ou commande)

- Un script qui échoue ne bloque pas la commande suivante : enchaîner avec `&&`, relire `git status` et
  `git diff --stat` avant de commiter.
- Un script lancé avec un chemin relatif doit calculer son propre dossier avant tout `cd`
  (`HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)`). Tester la commande exacte que Samuel tapera.
- Les jetons expirent pendant un essai (jeton Kubernetes de service, jeton Keycloak de courte durée) : un script
  long redemande un jeton à chaque appel et contrôle sa validité au départ.
- Une recherche textuelle peut manquer une forme d'appel : essayer plusieurs motifs avant d'affirmer l'absence.
- Un ordre d'itération non déterministe (ensembles, tables de hachage) ne doit jamais apparaître dans un
  message ou un résultat comparé par un test.
- Ne jamais supposer une valeur absolue générée par un système (numéro de génération, identifiant) : comparer
  à la valeur observée juste avant.

## Environnement de travail de Claude

Claude peut lire et écrire le dépôt, vérifier la syntaxe, valider des schémas et des liens, et consulter le web.
Il **ne peut pas** tirer d'images de conteneurs ni lancer Docker, Kubernetes ou k3s : l'accès réseau est limité
(GitHub, PyPI, npm, dépôts Ubuntu). Tout essai qui exige un cluster réel est donc exécuté par Samuel.
Claude fournit alors le script, l'**attendu** ligne à ligne, et la liste de ce qu'il doit renvoyer.

## Archive

Le tag git `archive/kernel-natif-2026-11-18` conserve l'arbre complet antérieur à R0. Ce n'est pas une source de
vérité et on n'y décide rien : il sert uniquement à retrouver un fichier précis si un besoin réel l'exige.

## Dépendances externes à ce dépôt

- La documentation de la Plateforme (dépôt Civitas-EGEN-Business, `docs/plateforme/`) devra être réalignée sur
  cette architecture. Point ouvert PO-08 dans [16](16-risques-et-verifications.md).
