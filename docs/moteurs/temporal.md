# Fiche — Temporal

## À quoi sert ce document
Documenter Temporal comme moteur de workflows durables pour les opérations qui impliquent plusieurs Control Planes (D-02).

## Ce qu'il ne couvre pas
Les opérations elles-mêmes ([09](../09-operations-distribuees.md)).

## Rôle
Temporal exécute des **workflows durables** : des suites d'étapes dont l'avancement est conservé, qui survivent aux pannes et
reprennent où elles s'étaient arrêtées. Termes :

| Terme | Définition en une phrase |
|---|---|
| **Workflow** | Le déroulé d'une opération, écrit en code, rejoué de façon déterministe après une panne. |
| **Activité** | Une étape qui agit sur le monde extérieur (ici : un appel C3 à un Control Plane). Elle peut échouer et être retentée. |
| **Saga** | Un workflow dont chaque étape a une **compensation** (action qui annule ses effets) exécutée si une étape suivante échoue. |
| **Signal / requête** | Message envoyé à un workflow en cours ; lecture de son état. |
| **File de tâches** | Là où les exécutants (« workers ») viennent chercher le travail. |

## Pourquoi lui
Il apporte **nativement** ce que le Federation Plane devrait sinon coder : reprise après interruption, un seul exécutant actif
par exécution, nouvelles tentatives à attente croissante, délais, annulation, compensation, historique complet. **Rôle strictement
limité** : les opérations qui franchissent plusieurs Control Planes. Il ne sert pas à la réconciliation locale (confiée aux
contrôleurs et aux moteurs).

**Alternatives écartées** : Dapr Workflow (plus léger mais versionnement moins éprouvé ; conservé comme repli si la charge
d'exploitation de Temporal devient un obstacle), Argo Workflows (tâches par conteneurs, pas des opérations métier durables).

## Vérifié
| Fait | Source | Date |
|---|---|---|
| Temporal est sous licence MIT (version auto-hébergée) et utilisé en production par de grandes entreprises. | [S-13](../references.md) (secondaire) | 2026-11-19 |
| Face à Dapr Workflow, Temporal demande un cluster dédié et offre un versionnement de workflows plus robuste ; sa courbe d'apprentissage est plus lourde. | [S-13](../references.md) (secondaire) | 2026-11-19 |

## À vérifier
VR-06 : version et licence sur la page officielle ; kit de développement Java (version) ; bases de données prises en
charge pour la persistance ; déploiement de développement et de production ; mécanisme de versionnement des workflows ;
exigences de haute disponibilité.

## Comment EGEN l'utilise
- Le Federation Plane héberge les exécutants Temporal et les **définitions de workflows** (ex. `provision-application`).
- Chaque étape appelle un Control Plane par C3 avec une clé d'idempotence **dérivée** de l'identifiant du workflow et de
  l'étape (rejouer ne crée jamais deux fois).
- Un état `Unknown` déclenche une **observation** (lecture de l'opération par clé) avant toute nouvelle tentative (P-15).
- Chaque workflow a une **échéance** ; passé ce délai, il compense (P-14).

## Pièges connus
- Le code d'un workflow doit être **déterministe** : pas d'heure système ni d'aléa directs, pas d'appels externes (ils vont
  dans les activités).
- Modifier un workflow en cours d'utilisation exige le **versionnement** : une modification incompatible casse le rejeu.
- Les activités doivent être **idempotentes** : elles peuvent être exécutées plusieurs fois.

## Tests d'intégration attendus
Serveur Temporal de développement : exécution d'une Saga à deux Control Planes factices ; panne d'un exécutant en plein
milieu ; compensation après échec à l'étape C ; opération incertaine ; échéance dépassée.

## Plan de sortie
Les définitions de workflows sont isolées derrière une interface « coordination d'opération » ; un autre moteur durable (Dapr
Workflow) pourrait la réaliser sans changer les contrats.
