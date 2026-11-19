# Fiches des moteurs spécialisés

## À quoi sert ce document
Donner l'index des fiches de moteurs et le gabarit que toute fiche doit respecter.

## Ce qu'il ne couvre pas
Le choix du moteur par capacité ([08](../08-moteurs-specialises.md)).

## Index

| Fiche | Moteur | Rôle |
|---|---|---|
| [kubernetes.md](kubernetes.md) | Kubernetes / k3s | Socle du Kernel de référence ; moteur par défaut de `workload-runtime` |
| [crossplane.md](crossplane.md) | Crossplane | Capacités, compositions, extensions du Kernel |
| [nomad.md](nomad.md) | Nomad | Moteur optionnel de `workload-runtime` |
| [temporal.md](temporal.md) | Temporal | Workflows durables et Sagas du Federation Plane |
| [messagerie-nats.md](messagerie-nats.md) | NATS JetStream | Événements (CloudEvents) |
| [secrets-openbao.md](secrets-openbao.md) | OpenBao | Secrets |
| [identite-spire-keycloak-spicedb.md](identite-spire-keycloak-spicedb.md) | SPIRE, Keycloak, SpiceDB | Identités et droits |

## Gabarit obligatoire d'une fiche

1. **Rôle** : une phrase ; la capacité réalisée.
2. **Pourquoi lui** : critères d'adoption satisfaits ; alternatives écartées et pourquoi.
3. **Vérifié** : faits avec date et source (renvoi à [references.md](../references.md)).
4. **À vérifier** : renvoi aux entrées VR-nn de [16](../16-risques-et-verifications.md).
5. **Comment EGEN l'utilise** : interface, extension, correspondance des opérations.
6. **Pièges connus.**
7. **Tests d'intégration attendus.**
8. **Plan de sortie** : comment le remplacer si besoin.
