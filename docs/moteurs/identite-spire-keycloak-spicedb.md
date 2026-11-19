# Fiche — SPIFFE/SPIRE, Keycloak, SpiceDB (identités et droits)

## À quoi sert ce document
Documenter les trois briques d'identité et de droits : charges de travail (SPIFFE/SPIRE), personnes (Keycloak), droits fins (SpiceDB).

## Ce qu'il ne couvre pas
Les règles d'ensemble ([07](../07-identite-confiance-securite.md)).

## Rôle

| Brique | Rôle |
|---|---|
| **SPIFFE** (norme) et **SPIRE** (son implémentation) | Donner à chaque charge de travail une identité vérifiable (`spiffe://…`) et des certificats de courte durée ; fédérer des domaines de confiance. |
| **Keycloak** | Identité des personnes : connexion, jetons, échange de jetons, fédération d'annuaires. |
| **SpiceDB** | Droits fins par relations : « cet acteur peut-il faire cela sur cet objet ? » |

## Pourquoi eux
- SPIFFE/SPIRE : identité **par domaine**, sans autorité centrale ; fédération par échange de clés publiques.
- Keycloak et SpiceDB : décisions maintenues de Samuel (D-09), à reconfirmer en R3.

## Vérifié
| Fait | Source | Date |
|---|---|---|
| La fédération SPIFFE consiste à échanger les *trust bundles* entre domaines de confiance. | [S-06](../references.md) | 2026-11-19 |
| La fédération dit « je fais confiance à l'émetteur », non « je fais confiance à cet appelant » : l'autorisation reste explicite. | [S-06](../references.md) | 2026-11-19 |

## À vérifier
- VR-05 : projet SPIFFE/SPIRE diplômé de la CNCF (statut et date sur la page officielle) ; fédération entre deux clusters ;
  durée des certificats ; mécanisme de révocation effectif.
- VR-04 : échange de jetons (RFC 8693) dans la version de Keycloak retenue ; porte-t-il la chaîne « agit pour » ?
- VR-10 : SpiceDB — licence, version, **limite de profondeur de résolution** des relations, performance.

## Comment EGEN l'utilise
Voir [07](../07-identite-confiance-securite.md) : domaine de confiance par Control Plane, fédération par adhésion, échange
de jetons à chaque saut, schéma de droits fédéré (esquisse à valider en R3.3).

## Pièges connus
- **Durée de vie des jetons Keycloak** : courte par défaut (de l'ordre de cinq minutes) ; un script long doit redemander un
  jeton à chaque appel, sinon il lit des réponses vides et croit le service arrêté.
- **Audience** : un jeton n'est accepté que s'il est destiné au service qui le reçoit.
- **Ne jamais afficher un jeton** : lire ses claims sans l'imprimer ; utiliser des secrets d'essai nommés comme tels.
- **Amorçage** : émettre le premier jeton d'administration exige un émetteur déjà configuré (PO-10).

## Tests d'intégration attendus
Deux domaines SPIRE fédérés ; adhésion puis révocation d'un membre (accès refusé après expiration) ; échange de jetons à
portée réduite ; refus SpiceDB ; jeton d'audience incorrecte refusé ; canari de secret absent des sorties.

## Plan de sortie
Identité des charges et des personnes sont séparées des contrats ; remplacer une brique ne change ni C1 à C6 ni les ressources.
