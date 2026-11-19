# Glossaire

## À quoi sert ce document
Définir chaque terme technique en une phrase, une fois pour toutes. Un terme défini ici a le même sens dans tous les documents.

## Ce qu'il ne couvre pas
Les spécifications (documents 03 à 12).

| Terme | Définition |
|---|---|
| **Adhésion** | Procédure par laquelle un Control Plane rejoint une fédération ([06](06-federation-plane.md)). |
| **Audience (d'un jeton)** | Le service auquel un jeton est destiné ; un autre service le refuse. |
| **Autorité (d'une ressource)** | Le seul Control Plane qui administre une ressource. |
| **Capacité** | Ce qu'un Control Plane sait faire, décrit indépendamment de la technologie (`workload-runtime`, `object-storage`…). |
| **Canari** | Fausse valeur reconnaissable (`CANARI-…`) injectée dans un essai pour prouver qu'un secret ne fuit pas. |
| **CloudEvents** | Standard d'enveloppe d'événements (identifiant, source, type, sujet, date, données). |
| **Composition (Crossplane)** | Description de la façon de réaliser une ressource composite ; plusieurs Compositions = plusieurs implémentations. |
| **Compensation** | Action qui annule les effets d'une étape déjà réussie quand une étape suivante échoue. |
| **Condition** | Couple (type, statut, raison) qui décrit un aspect de l'état d'une ressource ; la raison est stable. |
| **Connecteur** | Service séparé, basé sur Apache Camel, qui traduit un contrat vers un logiciel ou un protocole tiers. |
| **Contrat EGEN (C1 à C6)** | Interface commune, indépendante de toute technologie, entre Control Planes ([04](04-contrats-egen.md)). |
| **Control Plane** | Système autonome qui reçoit des intentions, applique des politiques, délègue l'exécution et suit les résultats. |
| **CRD (type personnalisé)** | Mécanisme de Kubernetes pour ajouter ses propres types de ressources à son API. |
| **Délégation** | Demande d'exécution d'une opération adressée à un Control Plane (C3). |
| **Domaine de confiance** | Périmètre d'identités émises par une même autorité (ex. `univ-x.civitas.africa`). |
| **Écosystème** | Ensemble cohérent de services (Métier, Transversal, Intelligence et Agents…). |
| **Environnement de gouvernance** | Espace dans lequel EGEN gouverne les systèmes d'une organisation. |
| **Extension** | Paquet installable qui ajoute une capacité ou un moyen de joindre un moteur, sans reconstruire le Control Plane. |
| **Federation Plane** | Niveau qui relie des Control Planes : registre, découverte, confiance, routage, coordination. |
| **Idempotence** | Propriété d'une demande dont le rejeu n'a pas d'effet double ; assurée par une clé d'idempotence. |
| **Kernel EGEN** | Implémentation de référence d'un Control Plane (ce dépôt). |
| **Moteur spécialisé** | Système qui exécute réellement un travail précis (déploiement, messagerie, stockage, workflow, secrets). |
| **Opération (Operation)** | Demande tracée de modification, avec phase, conditions et ressources suivies. |
| **Pont de fédération** | Composant qui expose les contrats C1 à C6 pour un Control Plane. |
| **Périmètre** | Chemin hiérarchique opaque (`univ-x/rh`) sur lequel s'expriment les politiques. |
| **Provider** | Extension d'un Kernel qui pilote un système externe. |
| **Réconciliation** | Boucle qui compare l'état voulu à l'état réel et agit pour les rapprocher. |
| **Référence (de secret)** | Indication (fournisseur, chemin) d'un secret ; jamais sa valeur. |
| **Saga** | Workflow dont chaque étape a une compensation. |
| **SVID** | Certificat de courte durée qui porte une identité SPIFFE. |
| **Trust bundle** | Ensemble des clés publiques de confiance d'un domaine, échangé lors de la fédération de confiance. |
| **Vue de fédération** | Ce que le Federation Plane connaît : membres, capacités, confiance, autorités ; jamais les ressources. |
