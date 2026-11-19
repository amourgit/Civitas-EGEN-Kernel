# 13 — Tests et conformité

## À quoi sert ce document
Fixer la méthode de vérification : niveaux de tests, règles pour les formuler et les exécuter, kit de conformité des
contrats, tests d'architecture automatiques, et enregistrement des incohérences.

## Ce qu'il ne couvre pas
Le plan des phases ([14](14-plan-de-developpement.md)) ni la liste des vérifications externes en attente
([16](16-risques-et-verifications.md)).

## Niveaux de tests

| Niveau | Ce qu'on vérifie | Moyen | Où il s'exécute |
|---|---|---|---|
| **L1 Contrats** | Schémas valides ; exemples valides acceptés et invalides **refusés** ; pas de rupture entre versions | Validation JSON Schema, OpenAPI, AsyncAPI ; comparaison avec la version précédente | CI |
| **L2 Compositions** | Une composition produit bien les ressources attendues, sans cluster | Rendu local des Compositions et validation des ressources | CI |
| **L3 Opérateur et pont** | Logique de l'opérateur et du pont contre une API Kubernetes de test | Tests unitaires et d'intégration légers | CI |
| **L4 Intégration réelle** | Le comportement contre un cluster et des moteurs **réels** | Scripts lancés par Samuel (k3s, Nomad, NATS, OpenBao…) | Poste de Samuel |
| **L5 Fédération** | Plusieurs Control Planes et un Federation Plane : adhésion, délégation, Sagas | Environnement multi-clusters d'essai | Poste de Samuel |
| **L6 Chaos et pannes** | Chaque ligne de la matrice de pannes ([12](12-resilience-et-exploitation.md)) | Coupures réseau, arrêt de composants, exécutant arrêté en pleine Saga | Poste de Samuel |
| **L7 Conformité** | Un Control Plane respecte C1 à C6 | Kit de conformité | CI et admission |

Un faux (simulateur) ne **remplace pas** L4 : tout moteur a au moins un test contre le moteur **réel** avant mise en service.

## Méthode (valable pour toute vérification)

1. **Un test peut échouer.** Avant de faire confiance à un test, prouver qu'il échoue quand il le doit (un cas négatif).
2. **Attendu écrit d'avance**, ligne à ligne ; comparer le résultat obtenu à l'attendu et relever **tout écart**, même non critique.
3. **Pas de suppositions** : lire le code ou la configuration plutôt que se fier à la seule documentation ; essayer plusieurs
   motifs de recherche avant d'affirmer qu'une chose n'existe pas.
4. **Écart critique = arrêt** : fuite de secret, droit contourné, perte de données. On s'arrête et on traite avant de continuer.
5. **Tout écart se consigne** dans le registre ([16](16-risques-et-verifications.md), section Incohérences) : id, où, attendu,
   obtenu, gravité, statut.
6. **Rien n'est déclaré « vérifié » sans l'avoir été** : chaque fin de travail rappelle ce qui est vérifié ici et ce qui ne l'est pas.
7. **Un jeton ou un mot de passe réel n'est jamais écrit ni demandé** : on utilise des secrets d'essai nommés comme tels.
8. **Les canaris** : pour prouver qu'un secret ne fuit pas, injecter une fausse valeur reconnaissable (`CANARI-…`) et vérifier
   qu'elle n'apparaît dans aucune sortie (journaux, erreurs, événements, réponses).

### Gabarit d'un test demandé à Samuel

```
Test <id> — <ce qu'on vérifie>
Pré-requis   : <ce qui doit tourner>
Commande     : <commande exacte, copiable>
Attendu      : <ligne à ligne>
Cas négatif  : <ce qui doit échouer, et comment>
À me renvoyer: <sortie complète / extraits précis>
Si écart     : <ce qu'on fait>
```

### Techniques éprouvées pour les essais

- **Faux serveur qui affiche ce qu'il reçoit** (en-têtes, jeton) : prouve qu'un jeton part réellement et n'est pas journalisé.
- **Jeton d'essai signé localement** : pour tester le contrôle d'audience sans service d'identité complet.
- **Keycloak d'essai avec import de domaine** et **k3s lancé par script** : environnements reproductibles ; la commande
  exacte est toujours testée telle que Samuel la tapera.
- **Scripts** : enchaînés par `&&`, chemin calculé par le script lui-même, validés par `bash -n` avant remise ; `git status`
  et `git diff --stat` relus avant tout commit.

## Le kit de conformité (niveau L7)

**But.** Qu'un Control Plane, quelle que soit sa technologie, puisse **prouver** qu'il respecte les contrats.

| Élément | Contenu |
|---|---|
| **Scénarios** | Fichiers déclaratifs (étapes, requêtes, réponses attendues, états attendus), indépendants du langage. |
| **Exécuteur de référence** | Programme qui joue les scénarios contre l'adresse d'un Control Plane (langage : PO-04). |
| **Familles de tests** | `base` (C1, C2, C6), `delegation` (C3, C4 : idempotence, états, erreurs), `events` (C5 : format, dédoublonnage), `full`. |
| **Cas négatifs** | Pour chaque contrat, des Control Planes **défectueux** que le kit doit refuser. |
| **Sortie** | Rapport horodaté, niveau atteint, liste des écarts. |
| **Control Plane factice** | Réalisation minimale qui **passe** le niveau `full` : preuve que les contrats sont implémentables et que le kit n'exige rien d'une technologie. |
| **Usage** | Contrôle d'admission dans la fédération ([06](06-federation-plane.md)) ; contrôle de non-régression en CI. |

## Tests d'architecture (automatiques, en CI)

| Règle vérifiée | Garde-fou |
|---|---|
| Aucun mot réservé de la Plateforme dans un identifiant du Kernel | P-10 |
| Aucun code propre à un moteur dans le cœur (structure du dépôt) | G-05 |
| Les contrats ne référencent aucune technologie | G-04 |
| Aucune valeur de secret dans une ressource d'exemple ni un document | P-09 |
| Aucun lien de documentation cassé ; chaque document a ses deux sections d'ouverture | Règles de rédaction |
| Aucune rupture de compatibilité de contrat sans version majeure | C6 |

## Enregistrement des incohérences

Tout écart constaté pendant un test s'inscrit dans [16](16-risques-et-verifications.md), section « Incohérences relevées ».

## Exemple de bout en bout

Le test L5 de l'agenda : lancer la Saga de [03](03-architecture-generale.md) ; **attendu** : trois opérations `Succeeded`, un seul
stockage créé ; **cas négatif** : arrêter le Control Plane Métier à l'étape C ; **attendu** : compensation, état final `Failed`
(`Compensated`), aucun stockage ni sujet orphelin ; canari présent dans le secret de l'agenda, **absent** de tous les journaux
et événements.
