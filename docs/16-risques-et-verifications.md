# 16 — Risques, vérifications en attente, points ouverts

## À quoi sert ce document
Tenir en un seul endroit : les risques connus, les **vérifications** qui n'ont pas pu être faites (et comment les faire), les
**points ouverts** (décisions à prendre) et le registre des incohérences relevées pendant les tests.

## Ce qu'il ne couvre pas
Les décisions prises ([02](02-principes-et-decisions.md)) et l'état d'avancement ([00](00-etat-et-reprise.md)).

## Risques

| Id | Risque | Gravité | Réponse |
|---|---|---|---|
| RK-01 | Charge d'exploitation de la pile (Kubernetes, Crossplane, SPIRE, Temporal, NATS, OpenBao, Keycloak, SpiceDB). | Élevée | Chaque composant n'est installé que lorsqu'une étape en a besoin ([14](14-plan-de-developpement.md)). Accepté par Samuel : on le fait « proprement du début à la fin ». |
| RK-02 | Jeunesse de Crossplane 2.x ; multiplication des types dans l'API. | Moyenne | Versions épinglées ; n'activer que les types utilisés (VR-01). |
| RK-03 | Licence Business Source License de Nomad, Consul, Vault ; propriété IBM. | Élevée si EGEN est proposé à des tiers | Avis juridique (VR-08) ; OpenBao pour les secrets ; Nomad borné à une extension remplaçable. |
| RK-04 | Concentration de la gouvernance de NATS autour d'une société. | Moyenne | Contrat C5 indépendant du transport ; Kafka possible par extension. |
| RK-05 | Un site avec Nomad mais sans Kubernetes exige un petit Control Plane local (k3s) s'il est isolé du réseau. | Moyenne | Coût connu ([08](08-moteurs-specialises.md)) ; décision au cas par cas. |
| RK-06 | Courbe d'apprentissage de Temporal (déterminisme, versionnement). | Moyenne | Définitions de workflows isolées ; Dapr Workflow en repli. |
| RK-07 | **L'architecture n'a été validée par aucun essai** : l'environnement de travail de Claude ne peut pas lancer de conteneurs. | Élevée | R2 est la première validation réelle ; les VR sont à lever en tête de phase. |
| RK-08 | Plusieurs faits externes reposent sur des sources secondaires (blogs, comparatifs). | Moyenne | Recoupement sur les pages officielles avant décision finale (VR-19). |
| RK-09 | La documentation de la Plateforme (autre dépôt) reste alignée sur un état antérieur. | Moyenne | PO-08. |
| RK-10 | La fédération de confiance entre sites à connectivité limitée peut échouer au renouvellement. | Moyenne | Dimensionner la durée des certificats (R3.1) ; PO-12. |

## Vérifications en attente

Chaque ligne dit **quoi vérifier**, **où**, **le résultat attendu**, **qui** et **quand**.

| Id | Sujet | À vérifier et où | Résultat attendu | Qui | Phase |
|---|---|---|---|---|---|
| VR-01 | Crossplane 2.x | Documentation officielle (crossplane.io) : version retenue ; composition de ressources quelconques ; ressources à portée d'espace de noms ; modes de gestion (dont observation seule) ; définitions de ressources gérées et politiques d'activation | Chaque capacité utilisée en [05](05-kernel-de-reference.md) existe dans la version retenue | Claude (documentation) puis Samuel (essai) | R2.1 |
| VR-02 | Fonctions d'opération de Crossplane ; collision de nom `Operation` | Documentation officielle | Décision de nom de type (PO-02) | Claude | R1.1 |
| VR-03 | OpenBao | Page officielle : licence, version, compatibilité d'API avec Vault, absence de réplication ; essai avec les méthodes d'authentification prévues | Résolution d'une référence de secret réussie | Claude puis Samuel | R3.4 |
| VR-04 | Keycloak : échange de jetons (RFC 8693) | Documentation officielle ; essai | Jeton à portée réduite, chaîne « agit pour » lisible, audience contrôlée | Claude puis Samuel | R3.2 |
| VR-05 | SPIFFE/SPIRE : statut CNCF, fédération entre deux clusters, durées de certificat, révocation effective | Pages officielles ; essai à deux clusters | Appel mTLS fédéré ; accès refusé après retrait du bundle et expiration | Claude puis Samuel | R3.1 |
| VR-06 | Temporal : licence et version, kit Java, bases prises en charge, déploiement, versionnement, haute disponibilité | Pages officielles ; essai | Saga à deux Control Planes factices reprise après panne | Claude puis Samuel | R4.4 |
| VR-07 | Nomad : mécanisme d'intégration depuis Crossplane ; correspondance des opérations ; agent de développement en conteneur | Documentation officielle ; essai comparatif | Les trois options de [moteurs/nomad.md](moteurs/nomad.md) sont évaluées | Claude puis Samuel | R5.1 |
| VR-08 | Licence BSL au regard de l'offre d'EGEN à des tiers | **Juriste** | Avis écrit | Samuel | Avant R5.1 |
| VR-09 | NATS JetStream : version, fonctions utilisées, liaisons entre sites, plan de sortie Kafka | Pages officielles ; essai | Rejeu après coupure vérifié | Claude puis Samuel | R2.7 |
| VR-10 | SpiceDB : licence, version, limite de profondeur de résolution, performance | Pages officielles ; essai | Schéma fédéré évalué sans dépasser la limite | Claude puis Samuel | R3.3 |
| VR-11 | Kubernetes et k3s : versions, support, haute disponibilité | Pages officielles | Versions épinglées | Claude | R2.1 |
| VR-12 | Règles d'admission déclaratives (ValidatingAdmissionPolicy) stables dans la version retenue | Documentation Kubernetes | Disponibles sans option expérimentale | Claude | R2.6 |
| VR-13 | Dapr : décision d'adoption (PO-07) ; version ; kit Java ; fonctionnement sans Kubernetes ; politiques de résilience | Pages officielles ; essai | Un service l'utilise sans y être obligé | Claude puis Samuel | R5.5 |
| VR-14 | Déclencheur d'un outil multi-clusters (Karmada, Open Cluster Management) | — | Non requis tant qu'un Control Plane ne couvre qu'un cluster | Claude | À déclencher |
| VR-15 | Versions de CloudEvents (extension de trace), AsyncAPI, OpenAPI, JSON Schema | Spécifications officielles | Versions épinglées dans [04](04-contrats-egen.md) | Claude | R1.1 |
| VR-16 | Signature et vérification des paquets Crossplane et des images | Documentation officielle | Politique de chaîne d'approvisionnement écrite | Claude | R2.8 |
| VR-17 | Stockage objet compatible S3 : implémentations et licences | Pages officielles | Choix argumenté (PO-11) | Claude | R5.6 |
| VR-18 | Contraintes d'infrastructure du terrain : connectivité, hébergement, ressources des sites | **Samuel** | Description écrite par site | Samuel | Avant R6 |
| VR-19 | Rétablir et recouper les sources : certaines adresses de sources secondaires ne sont pas conservées dans [references.md](references.md) | Pages officielles de chaque projet | Chaque fait de [references.md](references.md) a une source officielle | Claude | En continu |

## Points ouverts (décisions à prendre)

| Id | Question | Options | Impact | Décideur | Échéance |
|---|---|---|---|---|---|
| PO-01 | Mécanisme d'intégration de Nomad | A : Provider HTTP générique ; B : Provider dédié ; C : fonction de composition + A ([moteurs/nomad.md](moteurs/nomad.md)) | Qualité de l'observation d'état, charge de maintenance | Samuel | R5.1 |
| PO-02 | Noms définitifs des types EGEN (`Service`, `Operation`, `ControlPlaneConfig`), liste des mots réservés, format de l'identifiant d'opération, collision possible avec un type Crossplane | À proposer en R1.1 | Stabilité des contrats | Samuel | R1.1 |
| PO-03 | Topologie par défaut du Federation Plane | Par environnement et répliqué (proposé) ; centralisé ; distribué | Autonomie, simplicité | Samuel | R4.1 |
| PO-04 | Langage de l'opérateur EGEN, du pont, du service Federation Plane et du kit de conformité | Java (cohérence avec l'existant) ; Go (écosystème Kubernetes) ; autre | Compétences, outillage disponible | Samuel | R1.7 |
| PO-05 | Politique de licences des dépendances : acceptation ou non d'un composant sous licence à source disponible (Nomad, Consul, Vault) ; sort de Consul | Refuser ; accepter avec avis juridique | Risque juridique et technique | Samuel | Avant R5.1 |
| PO-06 | Durée minimale de dépréciation d'un contrat | À proposer | Compatibilité entre Control Planes | Samuel | R1.6 |
| PO-07 | Dapr : adopter comme capacité optionnelle offerte aux services ? | Oui ; non | Communication entre services | Samuel | R5.5 |
| PO-08 | Réaligner la documentation de la Plateforme (dépôt Civitas-EGEN-Business) | Oui, en session dédiée ; plus tard | Cohérence entre dépôts | Samuel | Avant R2 |
| PO-09 | Licence du dépôt (aucun fichier de licence) | À choisir | Diffusion | Samuel | R1 |
| PO-10 | Amorçage de l'identité (circularité Keycloak / Kernel) | Identité amorcée avant le Kernel par un jeton d'installation à usage unique ; clé de confiance publique initiale | Sécurité de l'installation | Samuel | R3.8 |
| PO-11 | Implémentation du stockage objet | À proposer après VR-17 | Capacité `object-storage` | Samuel | R5.6 |
| PO-12 | Fonctionnement en connectivité limitée : exigences | À fournir (VR-18) | Durée des certificats, copies locales | Samuel | R6.7 |
| PO-13 | Durée de conservation de l'audit | À fixer | Conformité, stockage | Samuel | R3.7 |
| PO-14 | Livraison continue déclarative : Argo CD ou Flux | Comparaison en R6.1 | Amorçage et mises à jour | Samuel | R6.1 |
| PO-15 | Comportement si SpiceDB est indisponible | Refus par défaut strict ; cache de décisions borné | Disponibilité contre sécurité | Samuel | R3.3 |
| PO-16 | Objectifs de reprise après sinistre (perte de données tolérée, durée de coupure tolérée) | À fixer | Sauvegardes | Samuel | R6.2 |

## Incohérences relevées dans les tests

Format : identifiant, où, attendu, obtenu, gravité (critique / majeure / mineure), statut. Registre vide à ce jour : aucun test n'a encore eu lieu.

| Id | Où | Attendu | Obtenu | Gravité | Statut |
|---|---|---|---|---|---|
| — | — | — | — | — | — |
