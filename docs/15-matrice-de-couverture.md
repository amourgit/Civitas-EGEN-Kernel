# 15 — Matrice de couverture

## À quoi sert ce document
Montrer, pour chaque question posée dans les deux documents fondateurs, **où se trouve la réponse** dans la documentation et
**dans quelle phase** elle se réalise. C'est l'outil pour vérifier qu'aucune problématique n'est oubliée.

## Ce qu'il ne couvre pas
Les réponses elles-mêmes (elles sont dans les documents cités) et le statut d'avancement ([00](00-etat-et-reprise.md)).

## 1. Les 31 catégories d'inventaire (fondations, `REFONTE-MAJEUR.md` §1 à §31)

| § | Catégorie | Réponse | Phase |
|---|---|---|---|
| 1 | Périmètre de communication du Kernel | [01](01-vision-et-perimetre.md), [03](03-architecture-generale.md) (responsabilités) | R1, R2 |
| 2 | Nature des communications | [04](04-contrats-egen.md) (C3 synchrone, C5 asynchrone), [10](10-evenements-observabilite-audit.md) | R1 |
| 3 | Protocoles et mécanismes de communication | [04](04-contrats-egen.md) (conventions : HTTPS/JSON, CloudEvents) | R1 |
| 4 | Découverte et identification des composants | [06](06-federation-plane.md) (découverte), [04](04-contrats-egen.md) (C1, C2), [07](07-identite-confiance-securite.md) | R4 |
| 5 | Contrats, interfaces et compatibilité | [04](04-contrats-egen.md) (C1 à C6, C6 versionnement) | R1 |
| 6 | Intégration des services développés ou personnalisés | [05](05-kernel-de-reference.md) (modèle `EgenService`) | R2 |
| 7 | Intégration des solutions propriétaires et fermées | [11](11-ecosystemes-et-integrations.md) (trois modes) | R5 |
| 8 | Connecteurs, bridges et gateways | [11](11-ecosystemes-et-integrations.md) (vocabulaire, gouvernance) | R5 |
| 9 | Authentification et identité entre composants | [07](07-identite-confiance-securite.md) | R3 |
| 10 | Autorisations, politiques et gouvernance | [07](07-identite-confiance-securite.md), [05](05-kernel-de-reference.md) (politiques locales) | R3 |
| 11 | Communication interservices | [08](08-moteurs-specialises.md) (Dapr optionnel, PO-07), [05](05-kernel-de-reference.md) (`resiliency`) | R5 |
| 12 | Messagerie, événements et diffusion | [04](04-contrats-egen.md) (C5), [10](10-evenements-observabilite-audit.md), [moteurs/messagerie-nats.md](moteurs/messagerie-nats.md) | R2 |
| 13 | Gestion des données et des échanges | [02](02-principes-et-decisions.md) (P-04, P-09, G-02) : le Kernel ne détient ni ne transporte de données métier ; il manipule des **références** | R2 |
| 14 | Déploiement, installation et cycle de vie des services intégrés | [05](05-kernel-de-reference.md) (cycle de vie), [08](08-moteurs-specialises.md) | R2 |
| 15 | Gestion des environnements hétérogènes | [08](08-moteurs-specialises.md) (plusieurs moteurs), [03](03-architecture-generale.md) (topologies) | R5 |
| 16 | Communication entre plusieurs Kernels et écosystèmes | [06](06-federation-plane.md), [09](09-operations-distribuees.md), [11](11-ecosystemes-et-integrations.md) | R4 |
| 17 | Services d'administration, de supervision et d'exploitation | [12](12-resilience-et-exploitation.md) (administration), [10](10-evenements-observabilite-audit.md) | R6 |
| 18 | Interopérabilité avec les systèmes d'identité et de sécurité | [07](07-identite-confiance-securite.md) (intégration externe), [moteurs/identite-spire-keycloak-spicedb.md](moteurs/identite-spire-keycloak-spicedb.md) | R3 |
| 19 | Fiabilité, résilience et tolérance aux pannes | [12](12-resilience-et-exploitation.md) (matrice des pannes), [09](09-operations-distribuees.md) | R4, R6 |
| 20 | Cohérence, concurrence et orchestration | [09](09-operations-distribuees.md), [05](05-kernel-de-reference.md) (autorité) | R4 |
| 21 | Sécurité des communications et confiance | [07](07-identite-confiance-securite.md) | R3 |
| 22 | Observabilité, traçabilité et audit | [10](10-evenements-observabilite-audit.md) | R2, R3 |
| 23 | Configuration et adaptation dynamique | [05](05-kernel-de-reference.md) (réapplication, références) ; configuration multi-niveaux = Plateforme ([11](11-ecosystemes-et-integrations.md)) | R2 |
| 24 | Gestion des ressources et des capacités techniques | [05](05-kernel-de-reference.md) (ressources), [04](04-contrats-egen.md) (`limits` de C2) | R2 |
| 25 | Extensibilité et évolution du Kernel | [05](05-kernel-de-reference.md) (extensions), [04](04-contrats-egen.md) (C6) | R2, R5 |
| 26 | Décentralisation et autonomie des composants | [02](02-principes-et-decisions.md) (P-02), [06](06-federation-plane.md), [12](12-resilience-et-exploitation.md) | R4, R6 |
| 27 | Compatibilité avec les solutions et standards du marché | [08](08-moteurs-specialises.md), [moteurs/](moteurs/README.md), [references.md](references.md) | R1 à R5 |
| 28 | Intégration dans les environnements d'entreprise | [07](07-identite-confiance-securite.md), [11](11-ecosystemes-et-integrations.md), [12](12-resilience-et-exploitation.md) | R5, R6 |
| 29 | Installation, bootstrap et auto-administration | [12](12-resilience-et-exploitation.md) (amorçage, livraison continue) | R3.8, R6 |
| 30 | Tests, validation et certification de l'interopérabilité | [13](13-tests-et-conformite.md) (kit de conformité), [06](06-federation-plane.md) (admission) | R1.7 |
| 31 | Questions de conception fondamentale | [01](01-vision-et-perimetre.md), [02](02-principes-et-decisions.md) | R0 |

## 2. Les 24 sections d'architecture cible (fondations, `REFONTE-MAJEUR.md`, seconde partie)

| § | Thème | Réponse |
|---|---|---|
| 1, 2 | Contexte, principes fondamentaux | [01](01-vision-et-perimetre.md), [02](02-principes-et-decisions.md) |
| 3, 4 | Niveaux, architecture cible | [03](03-architecture-generale.md) |
| 5 | Federation Plane | [06](06-federation-plane.md) |
| 6 | Control Plane | [05](05-kernel-de-reference.md) |
| 7 | Moteurs spécialisés et mutualisation | [08](08-moteurs-specialises.md) |
| 8 | Crossplane dans l'architecture | [moteurs/crossplane.md](moteurs/crossplane.md), [11](11-ecosystemes-et-integrations.md) |
| 9 | Contrats et interfaces communs | [04](04-contrats-egen.md) |
| 10 | Control Planes externes et solutions propriétaires | [11](11-ecosystemes-et-integrations.md) |
| 11 | Communication entre niveaux | [03](03-architecture-generale.md), [04](04-contrats-egen.md), [06](06-federation-plane.md) |
| 12 | Sécurité, identité, confiance | [07](07-identite-confiance-securite.md) |
| 13 | Opérations distribuées et dépendances | [09](09-operations-distribuees.md) |
| 14 | Résilience en environnement distribué | [12](12-resilience-et-exploitation.md) |
| 15 | Observabilité, supervision, audit | [10](10-evenements-observabilite-audit.md) |
| 16 | Organisation distribuée et hiérarchique | [03](03-architecture-generale.md) (topologies), [06](06-federation-plane.md) |
| 17 | Cycle de vie et auto-administration | [12](12-resilience-et-exploitation.md) |
| 18 | Interfaces d'administration et agents autonomes | [12](12-resilience-et-exploitation.md), [11](11-ecosystemes-et-integrations.md) |
| 19 | Garanties architecturales | [01](01-vision-et-perimetre.md) (tableau des garanties) |
| 20 | Scénarios représentatifs | [03](03-architecture-generale.md) (exemple), [13](13-tests-et-conformite.md) (scénarios de test) |
| 21 | Ce que l'architecture ne doit pas devenir | [02](02-principes-et-decisions.md) (G-01 à G-07) |
| 22 | Grandes responsabilités | [03](03-architecture-generale.md) |
| 23 | Résultat recherché | [01](01-vision-et-perimetre.md) |
| 24 | Questions structurantes | Section 3 ci-dessous |

## 3. Les 40 questions structurantes (fondations, `REFONTE-MAJEUR.md` §24, groupes A à H)

| Groupe | Questions | Thème | Réponse |
|---|---|---|---|
| A | 1 à 5 | Topologie de la fédération | [03](03-architecture-generale.md) (topologies), [06](06-federation-plane.md) |
| B | 6 à 10 | Adhésion, identité, confiance | [06](06-federation-plane.md) (adhésion, révocation), [07](07-identite-confiance-securite.md) |
| C | 11 à 15 | Contrats et interopérabilité | [04](04-contrats-egen.md), [11](11-ecosystemes-et-integrations.md) |
| D | 16 à 20 | Délégation et autorité | [05](05-kernel-de-reference.md) (autorité, capacités), [06](06-federation-plane.md) |
| E | 21 à 25 | Extensions et moteurs | [05](05-kernel-de-reference.md) (extensions), [08](08-moteurs-specialises.md) |
| F | 26 à 30 | Opérations distribuées | [09](09-operations-distribuees.md) |
| G | 31 à 35 | Gouvernance et sécurité | [07](07-identite-confiance-securite.md), [11](11-ecosystemes-et-integrations.md) (gouvernance des connecteurs) |
| H | 36 à 40 | Résilience, supervision, exploitation | [12](12-resilience-et-exploitation.md), [10](10-evenements-observabilite-audit.md) |

## 4. Les questions de la seconde fondation (`EXPLICATION-ECOSYSTEME.md` §24, groupes A à G)

| Groupe | Thème | Réponse |
|---|---|---|
| A | Organisation des environnements et des écosystèmes | [01](01-vision-et-perimetre.md), [11](11-ecosystemes-et-integrations.md) |
| B | Architecture interne du Kernel | [05](05-kernel-de-reference.md) |
| C | Délégation et moteurs spécialisés | [05](05-kernel-de-reference.md), [08](08-moteurs-specialises.md) |
| D | Fédération des Control Planes | [06](06-federation-plane.md) |
| E | Contrats et interopérabilité | [04](04-contrats-egen.md) |
| F | Sécurité et gouvernance | [07](07-identite-confiance-securite.md) |
| G | Résilience et exploitation | [12](12-resilience-et-exploitation.md) |

## Comment utiliser et tenir cette matrice

- À chaque nouveau document ou changement important, vérifier qu'aucune ligne ne pointe vers un document supprimé.
- Une question des fondations **sans réponse** devient un point ouvert dans [16](16-risques-et-verifications.md).
- Les réponses sont des **propositions argumentées** : celles qui portent sur un choix non tranché renvoient à un PO-nn.
