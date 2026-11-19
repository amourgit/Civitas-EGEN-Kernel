# 14 — Plan de développement

## À quoi sert ce document
Détailler les phases R0 à R7 : objectif, étapes, livrables, vérifications, dépendances et critères de sortie. **Il ne porte aucun
statut** : l'avancement est dans [00](00-etat-et-reprise.md).

## Ce qu'il ne couvre pas
Le contenu technique des étapes (documents 04 à 12) ni les dates (on ne planifie pas ici de durée).

## Règles de déroulement

1. **Contrat, document, test, code** : dans cet ordre. Aucune étape de code ne commence sans son contrat normatif et ses tests écrits.
2. **Entrée de phase** : les documents cités sont lus ; les points ouverts (PO) et vérifications (VR) cités sont tranchés ou levés,
   ou explicitement repoussés par Samuel dans [00](00-etat-et-reprise.md).
3. **Sortie de phase** : le critère de sortie est vérifié (par un test, pas par une opinion) ; l'état est écrit dans [00](00-etat-et-reprise.md) ;
   Samuel valide.
4. **Adopter avant de coder** (D-07) : toute étape qui écrit du code explique pourquoi une solution éprouvée ne suffit pas.
5. **Un composant n'est installé que lorsqu'une étape en a besoin.**

## R0 — Assainissement

**Objectif.** Partir d'un dépôt sain : rien de périmé, documentation neuve complète.

| Étape | Livrable | Vérification |
|---|---|---|
| R0.1 | Archivage de l'état antérieur (tag git) | Le tag existe sur le dépôt distant |
| R0.2 | Suppression du code et de la documentation périmés | `git ls-files` ne liste que `.gitignore`, `docs/fondations/` puis la documentation neuve |
| R0.3 | Documentation neuve (README, CONTRIBUTING, documents 00 à 16, glossaire, références, fiches de moteurs) | Contrôles automatiques : liens valides, sections d'ouverture, aucun terme périmé |
| R0.4 | Validation par Samuel, puis fusion sur `main` | Lecture par Samuel ; fusion en avance directe |

**Critère de sortie.** `main` ne contient que l'état voulu ; Samuel a validé.

## R1 — Contrats et kit de conformité

**Objectif.** Figer C1 à C6 et disposer d'un moyen automatique de les vérifier. **Entrées** : 04, 13 ; PO-02, PO-04, PO-06 ; VR-15.

| Étape | Livrable | Vérification | Dépend de |
|---|---|---|---|
| R1.1 | Conventions communes figées : identifiants, mots réservés, noms de types, format d'erreur, identifiant d'opération (PO-02) | Relecture ; test d'architecture « mots réservés » | R0 |
| R1.2 | C1 : JSON Schema du descripteur, format de signature, exemples valides et invalides | Validation automatique des exemples | R1.1 |
| R1.3 | C2 : JSON Schema du descripteur de capacité ; registre initial des capacités standard | Validation ; relecture du registre | R1.1 |
| R1.4 | C3/C4 : spécification OpenAPI de l'API d'opérations ; catalogue des phases, conditions et raisons ; durée de garde de la clé d'idempotence | Validation OpenAPI ; exemples | R1.1 |
| R1.5 | C5 : schéma de chaque type d'événement ; document AsyncAPI | Validation ; exemples | R1.1 |
| R1.6 | C6 : règles de version ; outil de détection de rupture en CI (PO-06) | Une rupture volontaire est détectée et refusée | R1.2 à R1.5 |
| R1.7 | Kit de conformité : format des scénarios, exécuteur de référence, Control Plane factice, Control Planes défectueux | Le factice passe `full` ; chaque défectueux échoue sur le contrat visé | R1.2 à R1.5 |
| R1.8 | CI : schémas, liens de documentation, tests d'architecture, kit contre le factice | Pipeline vert ; un échec volontaire est détecté | R1.6, R1.7 |

**Critère de sortie.** Contrats v1.0 figés ; le factice passe `full` ; au moins un défectueux par contrat est refusé ; CI verte.

## R2 — Kernel de référence (un seul Control Plane)

**Objectif.** Un Control Plane complet qui déploie des services de bout en bout. **Entrées** : 05, 08, fiches Kubernetes et
Crossplane ; PO-02, PO-04 ; VR-01, VR-02, VR-11, VR-12, VR-15.

| Étape | Livrable | Vérification | Dépend de |
|---|---|---|---|
| R2.1 | Environnement d'essai reproductible : script k3s, Crossplane épinglé | Script lancé par Samuel ; cluster sain | R1 |
| R2.2 | Types EGEN : ressource composite `Service` ; composition `workload-runtime` pour Kubernetes | Un service d'essai passe à `Running` | R2.1 |
| R2.3 | Opérateur EGEN (squelette) et ressource `Operation` : phases, conditions, tentatives | Une demande crée une `Operation` qui avance | R2.2 |
| R2.4 | Dépendances entre services, ordre de démarrage, flux d'événements garantis | Un service dont la dépendance requise n'est pas prête n'est pas déployé | R2.3 |
| R2.5 | Cycle de vie complet : `Degraded`, `Failed`, mise à jour, retour arrière, arrêt, suppression | Chaque règle de [05](05-kernel-de-reference.md) a un test | R2.4 |
| R2.6 | Politiques locales : RBAC, règles d'admission, normes de sécurité des pods | Une ressource non conforme est refusée | R2.2 |
| R2.7 | Événements (NATS, CloudEvents) et observabilité (OpenTelemetry) ; métriques figées | Événements reçus ; trace complète ; canari absent des sorties | R2.3 |
| R2.8 | Paquet `egen-core` sous forme de Configuration Crossplane | Installation sur un cluster neuf | R2.2 à R2.6 |
| R2.9 | Pont de fédération v0 : C1 à C4 | Le kit niveau `delegation` passe | R2.3, R1.7 |
| R2.10 | Scénario « agenda Radicale » dans un seul Control Plane | Scénario de bout en bout vérifié | R2.8, R2.9 |

**Critère de sortie.** Scénario passé sur un cluster neuf ; kit `delegation` vert ; VR de la phase levées ou reclassées.

## R3 — Identité, confiance et politiques

**Objectif.** Deux Control Planes s'authentifient et s'autorisent mutuellement. **Entrées** : 07, fiche identité, fiche OpenBao ;
PO-10, PO-13, PO-15 ; VR-03, VR-04, VR-05, VR-10.

| Étape | Livrable | Vérification | Dépend de |
|---|---|---|---|
| R3.1 | SPIRE : deux domaines de confiance fédérés ; durées de certificat | Appel mTLS entre domaines ; expiration observée | R2 |
| R3.2 | Keycloak : domaine, échange de jetons, audience | Jeton à portée réduite ; mauvaise audience refusée | R3.1 |
| R3.3 | SpiceDB : schéma de droits fédéré (PO-15) | Refus et accord conformes à la politique | R3.2 |
| R3.4 | OpenBao : références de secrets résolues à l'exécution | Résolution réussie ; canari absent des sorties | R2 |
| R3.5 | Adhésion d'un Control Plane : jeton, vérification, kit | Adhésion complète ; un défectueux est refusé | R3.1, R3.3 |
| R3.6 | Suspension et révocation | Accès refusé après suspension ; refusé après expiration des certificats | R3.5 |
| R3.7 | Audit : événements d'audit et service consommateur minimal (PO-13) | Chaque décision de test est retrouvée dans l'audit | R3.3 |
| R3.8 | Amorçage de l'identité (PO-10) | Un cluster neuf s'amorce sans étape manuelle cachée | R3.1 à R3.4 |

**Critère de sortie.** Authentification mutuelle fédérée ; révocation vérifiée ; refus de droits vérifié ; aucun secret dans les sorties.

## R4 — Federation Plane et opérations distribuées

**Objectif.** Orchestrer une opération sur plusieurs Control Planes. **Entrées** : 06, 09, fiche Temporal ; PO-03 ; VR-06.

| Étape | Livrable | Vérification | Dépend de |
|---|---|---|---|
| R4.1 | Registre fédéré : `ControlPlane`, `TrustRelation`, `FederationPolicy`, `AuthorityMap`, `OperationRecord` | Création, lecture, révocation | R3 |
| R4.2 | Découverte : index signé des capacités, rafraîchissement, resynchronisation | Une copie périmée est signalée ; une capacité retirée disparaît | R4.1 |
| R4.3 | Routage : résoudre puis appeler directement ; mode mandataire optionnel | Appel direct vérifié ; refus politique respecté | R4.2 |
| R4.4 | Temporal : serveur de développement, exécutant, premières activités C3 | Une activité appelle un Control Plane factice | R4.1 |
| R4.5 | Workflow `provision-application` sur deux Control Planes | Scénario de bout en bout (agenda) réussi | R4.3, R4.4 |
| R4.6 | Compensation, incertitude, échéance ; `decommission-application` | Échec injecté à l'étape C : compensation complète | R4.5 |
| R4.7 | Pannes : Federation Plane arrêté, exécutant arrêté en pleine Saga | Reprise sans doublon | R4.6 |
| R4.8 | Hiérarchie : un Federation Plane membre d'un autre (PO-03) | Découverte à travers deux niveaux | R4.3 |

**Critère de sortie.** Scénario de l'agenda avec échec injecté et compensation vérifiée ; reprise après panne vérifiée.

## R5 — Moteurs spécialisés et intégrations

**Objectif.** Ajouter des moteurs et intégrer des systèmes externes sans toucher au cœur. **Entrées** : 08, 11, fiche Nomad ; PO-01,
PO-05, PO-07, PO-11 ; VR-07, VR-08, VR-13, VR-17.

| Étape | Livrable | Vérification | Dépend de |
|---|---|---|---|
| R5.1 | Nomad : essai comparatif des mécanismes (PO-01), puis extension « workload-runtime pour Nomad » | Tests L4 contre un Nomad réel ([moteurs/nomad.md](moteurs/nomad.md)) | R2 |
| R5.2 | Sélection par `engine` et par politique | Deux `Service` identiques, deux moteurs différents | R5.1 |
| R5.3 | Pont pour un Control Plane externe construit sur Crossplane | Il passe le kit de conformité | R2.9 |
| R5.4 | Connecteur de référence (agenda, CalDAV) basé sur Camel | Passe par la garde de politique ; audité ; sans identifiant direct | R3 |
| R5.5 | Dapr, si adopté (PO-07) | Service qui l'utilise sans y être obligé | R2 |
| R5.6 | Capacité `object-storage` : implémentation choisie (PO-11) | Opération `create-bucket` réussie | R2 |

**Critère de sortie.** `workload-runtime` sélectionnable par moteur ; un connecteur et un Control Plane externe validés.

## R6 — Exploitation, résilience, bootstrap

**Objectif.** Rendre le système exploitable. **Entrées** : 12 ; PO-12, PO-14, PO-16 ; VR-18.

| Étape | Livrable | Vérification | Dépend de |
|---|---|---|---|
| R6.1 | Amorçage par livraison continue déclarative (PO-14) | Un cluster neuf s'amorce depuis Git | R3.8 |
| R6.2 | Sauvegardes et restaurations (PO-16) | Restauration complète testée | R4 |
| R6.3 | Mises à jour et compatibilité (Crossplane, Providers, `egen-core`, contrats) | Mise à jour par étapes sans interruption | R6.1 |
| R6.4 | Exercices de panne : une ligne de la matrice de [12](12-resilience-et-exploitation.md) = un test + un runbook | Toutes les lignes testées | R4 |
| R6.5 | `replace-controlplane` : migration d'un Control Plane | Autorité transférée sans double autorité | R4.6 |
| R6.6 | `rotate-trust` | Renouvellement sans coupure | R3.6 |
| R6.7 | Connectivité limitée (PO-12) | Site isolé : autonomie et resynchronisation vérifiées | R4.7 |

**Critère de sortie.** Chaque panne de la matrice est testée et documentée ; migration réussie.

## R7 — Écosystème d'Intelligence et d'Agents

**Objectif.** Faire gouverner les agents comme tout autre acteur. **Entrées** : 07, 11 ; documentation du système d'agents.

| Étape | Livrable | Vérification | Dépend de |
|---|---|---|---|
| R7.1 | Identité d'agent et chaîne « agit pour » | La chaîne est lisible dans la trace et l'audit | R3 |
| R7.2 | Exposition de capacités aux agents (MCP, A2A) | Un agent appelle une capacité autorisée, est refusé sur une autre | R7.1 |
| R7.3 | Traçabilité inter-Control Planes d'une action d'agent | Une action traverse deux Control Planes dans une seule trace | R4, R7.1 |
| R7.4 | Écosystème IA déployé comme paquet | Installation et retrait sans effet sur les autres écosystèmes | R2.8 |

**Critère de sortie.** Les actions d'un agent sont tracées de bout en bout et limitées par ses droits.
