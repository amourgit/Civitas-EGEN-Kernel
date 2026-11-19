# Fiche — OpenBao (secrets)

## À quoi sert ce document
Documenter OpenBao comme moteur de la capacité `secrets`.

## Ce qu'il ne couvre pas
L'identité des charges de travail ([identite-spire-keycloak-spicedb.md](identite-spire-keycloak-spicedb.md)).

## Rôle
Stocker et distribuer les secrets (mots de passe, clés, jetons de service) et les délivrer à l'exécution. Aucune valeur de
secret ne figure jamais dans une ressource EGEN : seulement une **référence** (fournisseur, chemin), résolue à l'exécution (P-09).

## Pourquoi lui
OpenBao est le fork open source de Vault sous gouvernance de la Linux Foundation. Vault est passé sous Business Source
License (voir [nomad.md](nomad.md) pour le contexte).

**Alternative** : Vault, admis par une extension si l'avis juridique le permet (PO-05, VR-08).

## Vérifié
| Fait | Source | Date |
|---|---|---|
| OpenBao est un fork de Vault lancé en décembre 2023, sous la Linux Foundation, né de la décision de passer Vault en Business Source License. | [S-11](../references.md) | 2026-11-19 |

## À vérifier
VR-03 : compatibilité d'API avec Vault (la version de référence de compatibilité annoncée, les moteurs de secrets utilisés,
les méthodes d'authentification Kubernetes) ; absence de réplication entre clusters (acceptable si chaque Control Plane a
son propre coffre) ; licence et version exactes sur la page officielle.

## Comment EGEN l'utilise
- **Un coffre par Control Plane** (autonomie, P-02) ; pas de partage de secrets entre Control Planes : on échange des
  références et des jetons à courte durée.
- Les services s'authentifient auprès du coffre par leur identité de charge de travail.
- Le Kernel ne lit **jamais** une valeur de secret pour la stocker ailleurs ; il vérifie seulement qu'une référence est résoluble.

## Pièges connus
- Mode de développement du coffre : jamais en production (refus de démarrer avec les identifiants de développement).
- Aucun secret dans les journaux, erreurs, événements : les essais utilisent des canaris (G-08).
- Un jeton racine sert à l'amorçage seulement.

## Tests d'intégration attendus
Contre un OpenBao réel : résolution d'une référence, absence de la valeur dans les sorties (canari), perte de connexion
(`Resolving` retenté avec attente croissante, jamais `Failed`).

## Plan de sortie
Les ressources ne contiennent que des références : changer de moteur de secrets revient à changer le fournisseur de référence.
