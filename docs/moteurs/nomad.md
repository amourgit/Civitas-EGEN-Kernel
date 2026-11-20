# Fiche — Nomad

## À quoi sert ce document
Documenter Nomad (orchestrateur de charges de travail) comme moteur spécialisé de la capacité `workload-runtime` :
son rôle, son placement, la correspondance des opérations, les pièges et les tests attendus.

## Ce qu'il ne couvre pas
La décision de placement (voir [08](../08-moteurs-specialises.md), D-03) et le mécanisme d'intégration, encore ouvert (PO-01).

## Rôle
Exécuter des charges de travail : conteneurs, mais aussi binaires, applications Java ou machines virtuelles qui ne
sont pas conteneurisés. Il est une **implémentation** de `workload-runtime` (niveau 3), choisie par `engine: nomad` ou
par politique. Il ne gouverne jamais.

## Pourquoi lui
- Il exécute des charges **non conteneurisées**, ce que le Kernel de référence ne ferait pas sans lui.
- Il est léger à exploiter (un binaire) : utile pour des sites modestes.
- Il gère nativement la mise à jour progressive, le retour arrière et l'historique des versions de jobs : EGEN **configure**
  ces mécanismes, il ne les réimplémente pas (D-07).
- Écarté comme **socle du Kernel** : pas d'API de ressources déclaratives comparable à celle de Kubernetes pour porter
  les types EGEN, et licence à source disponible (voir ci-dessous).

## Vérifié
| Fait | Source | Date |
|---|---|---|
| Nomad, comme Vault et Consul, est passé de MPL-2.0 à la Business Source License 1.1 le 10 août 2023 ; la BSL n'est pas une licence reconnue par l'OSI et restreint l'usage concurrent en production. | [S-10](../references.md) | 2026-11-19 |
| IBM a finalisé le rachat de HashiCorp le 27 février 2025 ; ce rachat ne change pas la licence. | [S-11](../references.md) | 2026-11-19 |

## À vérifier
- VR-07 : mécanisme d'intégration depuis Crossplane (existence d'un Provider communautaire, génération d'un Provider,
  ou HTTP générique), et correspondance ci-dessous contre la documentation de la version retenue.
- VR-08 : avis juridique sur la BSL au regard de l'offre d'EGEN à des tiers.

## Comment EGEN l'utilise

Extension « workload-runtime pour Nomad » : une implémentation de `workload-runtime` qui traduit un `EgenService` en job
Nomad et observe son état. Correspondance des opérations (**à revérifier** contre la documentation de la version retenue, VR-07) :

| Opération EGEN | Point d'accès Nomad (`/v1/…`, port 4646 par défaut) | Détails |
|---|---|---|
| Créer / mettre à jour | `POST /v1/job/:job_id` (ou `PUT /v1/jobs`) | Un `EgenService` donne un job à un groupe de tâches. La mise à jour progressive se configure par le bloc `update` du job (`max_parallel`, `health_check`, `auto_revert`) : **ne pas la réimplémenter**. |
| Changer le nombre d'instances | `POST /v1/job/:job_id/scale` | Point d'accès dédié, à préférer pour l'autoscaling fréquent. |
| Redémarrer | `POST /v1/job/:job_id/evaluate` | Force une nouvelle évaluation par l'ordonnanceur. |
| Arrêter | `DELETE /v1/job/:job_id` | Conserve l'historique du job (pas de purge). |
| Supprimer | `DELETE /v1/job/:job_id?purge=true` | Uniquement depuis la phase `Removing`. |
| Lire l'état | `GET /v1/job/:job_id` + `GET /v1/job/:job_id/allocations` | L'état agrégé croise le job (instances voulues) et les allocations (état réel de chaque tâche). |
| Lister les allocations | `GET /v1/job/:job_id/allocations?all=true` | Pagination par `X-Nomad-NextToken` et `per_page`. |
| Retour arrière | `POST /v1/job/:job_id/revert` | S'appuyer sur l'historique des versions de Nomad. |
| Observer les changements | requêtes bloquantes (paramètre `index`) | Plutôt qu'une interrogation à intervalle fixe. |

L'état observé est traduit en Conditions de l'`EgenService` (`Ready`, `Degraded`) ; il ne contient aucun type propre à Nomad.

## Pièges connus
- **Ne jamais construire le job par concaténation de texte** : utiliser le SDK officiel ou une structure typée
  sérialisée. Un champ mal typé (ex. `resources.cpu` en texte au lieu d'un entier en MHz) échoue en silence ou
  produit un ordonnancement inattendu.
- **Espaces de noms Nomad** : propager l'environnement vers le bon espace de noms ; ne jamais écrire `default` en dur.
  Un espace de noms n'est **pas** une frontière de tenant fournie par EGEN.
- **ACL** : le jeton du Control Plane n'a que les droits nécessaires (au minimum `submit-job`, `read-job`,
  `dispatch-job` sur l'espace concerné) ; **jamais** le jeton racine d'amorçage hors de l'amorçage initial (G-10).
- **Réapplication** : le Control Plane réapplique l'état voulu à chaque cycle ; toute modification manuelle dans Nomad
  est annulée. La sûreté repose sur l'idempotence de chaque appel.
- **Arrêt gracieux** : retirer le service de la découverte **avant** d'arrêter le job.
- **Agent de développement** : un client Nomad ne se lance pas correctement dans un conteneur pour les essais ;
  lancer l'agent en processus de l'hôte (`nomad agent -dev`). Constat à revérifier (VR-07).
- **Découverte** : Nomad peut enregistrer lui-même les services ; l'usage de Consul est optionnel (PO-05). Un point
  d'accès Consul peut répondre avec un message d'erreur se terminant par `: null` quand l'exception n'a pas de texte :
  ne pas s'en servir pour déterminer une cause.

## Mécanismes d'intégration possibles (PO-01)

| Option | Principe | Pour | Contre |
|---|---|---|---|
| A. Provider HTTP générique de Crossplane | Une Composition appelle l'API Nomad | Aucun code à écrire | Observation d'état limitée, gestion d'erreurs rudimentaire |
| B. Provider dédié | Provider Crossplane propre à Nomad (existant à identifier, ou généré) | Observation fine, cycle de vie complet | À trouver ou à produire, à maintenir |
| C. Fonction de composition + A | Une fonction produit le job, le Provider HTTP l'applique | Séparation nette entre traduction et application | Deux pièces à maintenir |

Un essai comparatif est prévu en R5.1 ; la décision revient à Samuel.

## Tests d'intégration attendus
- Contre un Nomad **réel** (agent de développement), jamais un faux : idempotence de la création (rejouer ne duplique rien),
  mise à jour progressive, retour arrière, arrêt gracieux, suppression, observation d'état.
- Un faux serveur Nomad qui **affiche le jeton reçu** prouve que le jeton part réellement et qu'il n'apparaît dans aucun journal.
- Rejouer les tests à chaque version mineure de Nomad (dérive de compatibilité).

## Plan de sortie
Retirer l'extension et basculer les services concernés vers l'implémentation Kubernetes ; l'appelant ne voit aucun
changement (C2/C3 inchangés).
