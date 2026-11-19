# 01 — Vision et périmètre

## À quoi sert ce document
Dire ce qu'est EGEN, ce qu'il garantit, ce qu'il refuse de devenir, et définir les niveaux de l'architecture
(organisation, environnement, écosystème, Control Plane, Federation Plane, moteur spécialisé).

## Ce qu'il ne couvre pas
Le fonctionnement détaillé de chaque niveau (documents [03](03-architecture-generale.md) à
[12](12-resilience-et-exploitation.md)) et le plan de développement ([14](14-plan-de-developpement.md)).

## L'idée en une phrase

Une organisation (par exemple l'Université X) fait tourner beaucoup de systèmes : applications métier, services
partagés (identité, stockage, messagerie), agents d'intelligence artificielle. EGEN est la **plateforme de
gouvernance** qui permet de les déployer, les relier, les autoriser et les suivre, **sans réécrire** les outils
spécialisés qui les exécutent (Kubernetes, Nomad, NATS, Temporal…) et **sans dépendre d'un seul d'entre eux**.

## Les niveaux, définis un à un

| Niveau | Définition en une phrase | Exemple |
|---|---|---|
| **Contexte organisationnel** | L'organisation réelle, avec ses services, ses règles et ses responsables. | L'Université X, son Service RH, son Service Informatique. |
| **Environnement de gouvernance EGEN** | L'espace dans lequel EGEN gouverne les systèmes d'une organisation. | L'environnement « univ-x ». |
| **Écosystème** | Un ensemble cohérent de services qui répondent à une famille de besoins. Trois sont fondamentaux : **Métier**, **Transversal**, **Intelligence et Agents**. | Métier : agenda, scolarité. Transversal : identité, stockage, messagerie. |
| **Control Plane** | Un système autonome qui reçoit des intentions (« je veux ce service », « je veux cette ressource »), applique les politiques, choisit à qui déléguer l'exécution et suit le résultat. | Le Control Plane de l'écosystème Métier. |
| **Kernel EGEN** | L'implémentation de référence d'un Control Plane, fournie par ce dépôt. Un Control Plane peut aussi être développé autrement (autre technologie) s'il respecte les contrats. | Un Kernel par écosystème. |
| **Federation Plane** | Le niveau qui connaît les Control Planes participants, découvre leurs capacités, établit la confiance, oriente les demandes et coordonne les opérations qui en impliquent plusieurs. Il ne remplace aucun Control Plane. | Le Federation Plane de l'environnement « univ-x ». |
| **Moteur spécialisé** | Un système qui exécute réellement un travail précis et que le Control Plane sollicite : déploiement, messagerie, stockage, workflow, secrets, identité. | Kubernetes, Nomad, NATS, Temporal, OpenBao. |
| **Extension** | Un paquet installable qui ajoute à un Control Plane une capacité ou un moyen de joindre un moteur, sans reconstruire le Control Plane. | Le paquet « workload-runtime pour Nomad ». |
| **Contrat EGEN** | Une interface commune, indépendante de toute technologie, par laquelle des Control Planes s'échangent identité, capacités, demandes, états et événements (C1 à C6, voir [04](04-contrats-egen.md)). | La demande « déploie Radicale » sous forme d'Operation. |

## Le rôle du Kernel

Le Kernel est un **plan de contrôle** : il décrit, gouverne, délègue et suit. Concrètement :

- il reçoit des **intentions déclaratives** (l'état voulu, pas la suite de commandes) ;
- il **vérifie** qui demande et si c'est autorisé, selon des politiques locales et fédérées ;
- il **choisit** le moteur compétent parmi ceux dont il dispose et lui **délègue** l'exécution ;
- il **suit** chaque opération et publie des événements ;
- il **ne réimplémente pas** ce qu'un moteur éprouvé fait déjà (décision D-07).

## Ce qu'EGEN garantit

| Garantie | Signification | Où elle est traitée |
|---|---|---|
| Indépendance des Control Planes | Chacun fonctionne, évolue et s'administre seul. | [05](05-kernel-de-reference.md), [12](12-resilience-et-exploitation.md) |
| Indépendance des moteurs | Aucun moteur n'est imposé à tous les environnements. | [08](08-moteurs-specialises.md) |
| Délégation des responsabilités spécialisées | Ce qu'un moteur sait faire lui est confié. | [05](05-kernel-de-reference.md), [08](08-moteurs-specialises.md) |
| Interopérabilité | Des Control Planes de technologies différentes se parlent par les contrats communs. | [04](04-contrats-egen.md), [13](13-tests-et-conformite.md) |
| Extensibilité | Nouvelles capacités, moteurs, connecteurs sans refonte. | [05](05-kernel-de-reference.md), [11](11-ecosystemes-et-integrations.md) |
| Autonomie locale | Un Control Plane continue sans la fédération. | [12](12-resilience-et-exploitation.md) |
| Gouvernance | Politiques globales et locales ; une autorité claire par ressource. | [07](07-identite-confiance-securite.md), [06](06-federation-plane.md) |
| Traçabilité | Demandes, délégations, événements suivis à travers la fédération. | [10](10-evenements-observabilite-audit.md) |
| Évolutivité | Nouveaux Control Planes et technologies sans refonte des fondations. | [04](04-contrats-egen.md) (C6) |
| Compatibilité externe | Intégration de systèmes non CIVITAS, y compris propriétaires. | [11](11-ecosystemes-et-integrations.md) |

## Ce qu'EGEN ne doit jamais devenir

Ces sept dérives sont des garde-fous (G-01 à G-07, voir [02](02-principes-et-decisions.md)) :

1. Un Kernel central qui connaît et contrôle tout.
2. Une copie globale des registres locaux (le Federation Plane garde une **vue de fédération**, pas les ressources).
3. Un moteur universel qui remplace les moteurs spécialisés.
4. Un système dépendant d'une technologie unique pour participer à la fédération.
5. Un catalogue d'adaptateurs propriétaires dans le cœur du Kernel.
6. Une fédération qui impose le même fonctionnement à tous.
7. Une communication sans gouvernance : pouvoir joindre un système ne donne pas le droit de lui demander n'importe quoi.

## Périmètre de ce dépôt

| Dans ce dépôt | Hors de ce dépôt |
|---|---|
| Contrats C1 à C6 et kit de conformité | Cellules, catalogue, portail, passerelle de domaine, configuration multi-niveaux (Plateforme, dépôt Civitas-EGEN-Business) |
| Kernel de référence (types EGEN, opérateur, pont de fédération, paquets d'extensions) | Applications métier |
| Federation Plane | Système d'agents (dépôt Civitas-EGEN-Agents) ; ses exigences vis-à-vis du Kernel sont en [11](11-ecosystemes-et-integrations.md) |
| Documentation d'architecture, plan, vérifications | Interfaces d'administration (services consommateurs de la fédération) |
