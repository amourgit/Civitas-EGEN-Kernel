# Fiche — NATS JetStream (événements)

## À quoi sert ce document
Documenter NATS JetStream comme transport par défaut des événements EGEN (contrat C5).

## Ce qu'il ne couvre pas
Le catalogue des événements ([04](../04-contrats-egen.md), [10](../10-evenements-observabilite-audit.md)).

## Rôle
NATS est un système de messagerie ; **JetStream** en est la couche de persistance (flux conservés, consommateurs durables,
rejeu). Il transporte les événements au format CloudEvents. Il **transporte**, il n'interprète jamais le contenu.

## Pourquoi lui
Léger à exploiter, adapté aux petites et moyennes infrastructures, persistance et rejeu intégrés, bibliothèques dans la plupart
des langages. Il fournit la livraison « au moins une fois » exigée par C5 et la resynchronisation après reconnexion.

**Alternative** : Kafka, admis par une extension (même contrat C5, autre transport) si le volume ou l'écosystème l'exige.

## Vérifié
| Fait | Source | Date |
|---|---|---|
| Après l'accord du 1er mai 2025 entre la CNCF et Synadia, NATS reste un projet sous licence Apache 2.0 au sein de la CNCF. | [S-09](../references.md) | 2026-11-19 |
| Synadia affirme avoir financé environ 97 % des contributions au serveur NATS (chiffre fourni par Synadia elle-même) : risque de concentration de gouvernance. | [S-09](../references.md) | 2026-11-19 |

## À vérifier
VR-09 : version retenue, fonctionnalités JetStream utilisées (flux, consommateurs durables, rejeu), topologie entre sites
(liaisons de flux entre Control Planes), plan de sortie vers Kafka.

## Comment EGEN l'utilise
- Un flux par Control Plane pour ses événements ; sujets nommés selon le type CloudEvents (`africa.civitas.egen.…`).
- Consommateurs **durables** pour le rejeu après coupure.
- Les consommateurs dédoublonnent sur l'identifiant d'événement (livraison au moins une fois).
- Le repli HTTP de C5 existe pour un Control Plane sans bus.

## Pièges connus
- Publier dans un flux **inexistant** perd les premiers événements : un service qui déclare des flux n'est pas déployé tant
  qu'ils ne sont pas garantis ([05](../05-kernel-de-reference.md)).
- L'ordre n'est garanti que **par sujet**.
- Un événement ne contient jamais de secret ni de donnée métier (G-08).

## Tests d'intégration attendus
Contre un NATS réel : publication et rejeu après coupure, dédoublonnage, flux manquant, consommateur lent.

## Plan de sortie
Contrat C5 indépendant du transport : remplacer NATS par Kafka ou HTTP ne change rien pour les consommateurs de C5.
