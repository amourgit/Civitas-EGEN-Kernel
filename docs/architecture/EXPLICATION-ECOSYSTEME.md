# EGEN — Architecture globale des environnements, des écosystèmes et de la fédération des Control Planes

**Document d’orientation architecturale — Vision cible**

---

# 1. Introduction : la nouvelle vision architecturale d’EGEN

## 1.1. L’ambition générale

EGEN a pour ambition de fournir une architecture permettant de déployer, gouverner, administrer et faire coopérer des environnements numériques composés de services autonomes. Ces services peuvent être développés par CIVITAS, provenir de projets open source ou être fournis par des éditeurs tiers. Ils peuvent également reposer sur des technologies, des modèles d’exécution, des infrastructures et des cycles de vie différents.

L’objectif n’est pas de développer un système monolithique qui réimplémente toutes les fonctions nécessaires au fonctionnement d’un environnement numérique. Il s’agit plutôt de disposer d’une architecture capable d’intégrer des composants indépendants, de leur attribuer des responsabilités précises, de gérer leurs relations et de permettre leur évolution sans remettre systématiquement en cause l’ensemble de l’environnement.

Cette orientation impose une séparation claire entre les responsabilités organisationnelles, les périmètres fonctionnels, les systèmes de contrôle et les technologies d’exécution. Une application métier ne doit pas devenir responsable de l’infrastructure qui l’héberge. Un moteur de déploiement ne doit pas devenir le propriétaire de la gouvernance globale d’un écosystème. Un système de fédération ne doit pas se substituer aux systèmes locaux qu’il met en relation.

C’est dans cette perspective que la conception d’EGEN évolue vers une architecture composée de plusieurs environnements autonomes, organisés en écosystèmes, gouvernés par des Control Planes indépendants et capables de collaborer au moyen d’un niveau de fédération.

## 1.2. Une évolution importante du rôle du Kernel

Dans les premières représentations de l’architecture, le Kernel était principalement présenté comme un Control / Composition Plane chargé de décrire les services, de composer leurs dépendances et de déléguer certaines opérations à des moteurs spécialisés à travers des adaptateurs.

Cette représentation exprimait une partie importante de son rôle, mais elle ne suffit plus à décrire l'architecture cible.

Le Kernel doit désormais être considéré comme le **socle d'un système de contrôle autonome**, c'est-à-dire un Control Plane capable de gouverner un périmètre déterminé. Il ne se limite donc pas à faire correspondre une demande à un moteur technique. Il possède un environnement de contrôle, une connaissance structurée de son périmètre, des mécanismes d'extension, des politiques, des interfaces, des capacités et des moyens de suivre les opérations qu'il déclenche.

Il peut ainsi administrer un écosystème déployé dans son périmètre, intégrer plusieurs moteurs spécialisés, utiliser des services externes et participer à une fédération avec d'autres systèmes de contrôle.

Cette évolution ne signifie pas que le Kernel doit devenir un moteur universel. Au contraire, il doit conserver une séparation stricte entre la gouvernance des ressources et l'exécution des opérations spécialisées. Il détermine ce qui doit être réalisé, identifie le système compétent, lui délègue l'opération et suit son résultat. Le moteur spécialisé demeure responsable de son propre fonctionnement technique.

Le Kernel n'est donc ni un simple adaptateur, ni un orchestrateur universel qui remplace les autres technologies. Il constitue un **système autonome de contrôle, de gouvernance et de composition**, capable de s'appuyer sur des moteurs indépendants.

## 1.3. La fédération comme principe d'organisation

Une autre évolution importante concerne la possibilité de faire fonctionner plusieurs systèmes de contrôle indépendants au sein d'un même environnement global.

Il ne faut plus supposer qu'un seul Kernel doit administrer l'ensemble des ressources, des applications et des infrastructures d'une organisation. Certains périmètres peuvent avoir des responsabilités distinctes, des contraintes de sécurité différentes, des technologies propres ou des cycles de vie indépendants.

L'architecture doit donc permettre de déployer plusieurs EGEN Kernels, chacun responsable d'un écosystème ou d'un périmètre de contrôle déterminé. Ces Kernels peuvent fonctionner indépendamment, tout en participant à une même fédération.

Cette fédération peut également accueillir des Control Planes qui ne sont pas développés avec EGEN. Une plateforme externe comme Crossplane peut ainsi participer à l'environnement fédéré sans devoir être transformée artificiellement en composant interne du Kernel.

La direction architecturale retenue est donc la suivante :

**EGEN fournit un socle de contrôle autonome, permet de déployer plusieurs Control Planes indépendants et met à disposition les mécanismes nécessaires pour les fédérer.**

---

# 2. Comprendre les niveaux fondamentaux de l'architecture

Avant de présenter les composants techniques, il est indispensable de distinguer les notions qui structurent l'architecture. Elles ne désignent pas des éléments interchangeables.

## 2.1. Le contexte organisationnel

Le contexte organisationnel désigne l'organisation ou l'entité pour laquelle un environnement EGEN est déployé.

Il peut s'agir, par exemple, d'une banque, d'une université, d'un ministère, d'une mairie, d'une entreprise, d'un établissement public ou d'une autre organisation.

Ce contexte détermine les besoins, les contraintes, les responsabilités, les règles de gouvernance et les applications qui devront être prises en charge.

Il ne constitue pas un écosystème. Il représente le cadre dans lequel l'ensemble de l'environnement EGEN sera installé et exploité.

Une université et une banque peuvent donc disposer chacune de leur propre environnement EGEN, même si elles utilisent des technologies et des composants architecturaux similaires.

Le fait de partager le même modèle architectural ne signifie pas qu'elles partagent leurs données, leurs services, leurs configurations, leurs registres ou leurs autorités de contrôle.

## 2.2. L'environnement de gouvernance EGEN

L'environnement de gouvernance EGEN représente le déploiement global destiné à une organisation.

Il rassemble les infrastructures, les écosystèmes, les Kernels, les Control Planes externes éventuellement intégrés, les services, les registres et les mécanismes de fédération nécessaires à son fonctionnement.

Il constitue donc le périmètre global dans lequel les différentes responsabilités numériques de l'organisation sont organisées et administrées.

Par exemple, une université peut disposer d'un environnement EGEN comprenant :

- Un écosystème Métier pour ses applications et services fonctionnels.
- Un écosystème Transversal pour les capacités techniques communes.
- Un écosystème d'Intelligence et d'Agents pour l'administration de ses agents autonomes.
- D'autres écosystèmes spécialisés si des responsabilités distinctes justifient leur création.
- Un niveau de fédération permettant aux différents systèmes de contrôle de collaborer.

Cet environnement peut être déployé sur une infrastructure physique, virtuelle, cloud privé ou hybride. Sa structure logique ne dépend pas nécessairement de son organisation matérielle.

Il faut également préciser que l'environnement EGEN n'est pas nécessairement un système centralisé. Il peut être distribué sur plusieurs infrastructures, plusieurs sites ou plusieurs réseaux, à condition de disposer des mécanismes de communication et de gouvernance appropriés.

## 2.3. L'écosystème

Un écosystème représente un regroupement cohérent de responsabilités, de services, de composants et de ressources qui participent à une même finalité fonctionnelle ou technique.

Il ne représente pas un domaine d'activité comme l'éducation, la santé, la banque ou l'administration.

La distinction est importante : une université ne constitue pas un écosystème, pas plus qu'une banque ou un ministère. Ce sont des contextes organisationnels qui disposent de besoins différents. À l'intérieur de chacun de ces contextes, les responsabilités numériques peuvent être organisées en plusieurs écosystèmes.

Un écosystème peut regrouper plusieurs applications, plusieurs microservices, des composants open source, des services développés spécifiquement et des ressources techniques qui contribuent à sa responsabilité.

Il possède un périmètre clairement défini. Les services qui lui appartiennent sont administrés selon les règles de ce périmètre, tandis que les services extérieurs sont consommés à travers des interfaces et des contrats.

Un écosystème n'est donc pas simplement un dossier dans lequel on range des applications. Il représente un périmètre de responsabilité qui doit pouvoir être gouverné, déployé, observé et faire évoluer ses composants de manière cohérente.

## 2.4. L'EGEN Kernel

L'EGEN Kernel est le socle logiciel d'un Control Plane autonome.

Il fournit les mécanismes nécessaires pour administrer un périmètre de ressources et de services, déclarer les intentions de contrôle, gérer les extensions, connaître les capacités disponibles, appliquer les politiques locales, déléguer les opérations spécialisées et suivre les résultats.

Un écosystème peut être déployé dans un EGEN Kernel. Le Kernel constitue alors son environnement de contrôle : il gouverne les composants relevant de son périmètre et coordonne les opérations nécessaires à leur fonctionnement.

Il faut bien distinguer le Kernel de l'écosystème qu'il gouverne. Le Kernel n'est pas l'écosystème lui-même et ne contient pas nécessairement les applications métier de celui-ci. Il fournit les mécanismes de contrôle qui permettent de les administrer.

Il faut également distinguer le Kernel de son instance déployée. Le Kernel désigne le socle logiciel et architectural ; son instance correspond à un déploiement concret, configuré pour un périmètre de responsabilité donné.

Deux environnements EGEN peuvent donc utiliser le même socle Kernel tout en possédant des instances, des registres, des extensions, des configurations et des ressources indépendants.

## 2.5. Le Control Plane

Un Control Plane est un système autonome chargé du contrôle et de la gouvernance d'un périmètre technique.

Un EGEN Kernel déployé constitue une implémentation de ce modèle de Control Plane.

Toutefois, tous les Control Planes présents dans l'environnement EGEN ne sont pas nécessairement des EGEN Kernels. Une plateforme externe peut posséder son propre système de contrôle et participer à la fédération.

Le Control Plane conserve la responsabilité de son périmètre. Il connaît les ressources qu'il administre, les capacités dont il dispose, les politiques qu'il applique et les opérations qu'il a confiées à d'autres systèmes.

Cette autonomie est une propriété essentielle de l'architecture.

## 2.6. Le Federation Plane

Le Federation Plane représente le niveau de coordination et de coopération entre plusieurs Control Planes.

Il permet de connaître les systèmes de contrôle participants, de découvrir leurs capacités, d'établir des relations de confiance, de router les demandes et de coordonner les opérations qui nécessitent plusieurs périmètres.

Il ne remplace pas les Kernels. Il ne devient pas propriétaire de leurs registres locaux et ne prend pas en charge leurs opérations internes.

Son rôle est de permettre à plusieurs systèmes autonomes de travailler ensemble sans fusionner leurs responsabilités.

---

# 3. Organisation des écosystèmes dans un environnement EGEN

L'organisation des écosystèmes doit répondre à une logique de responsabilités. Elle ne doit pas être construite à partir d'une classification des secteurs d'activité.

La structure fondamentale repose sur deux écosystèmes obligatoires : Métier et Transversal. Un troisième écosystème, dédié à l'intelligence artificielle et aux agents autonomes, peut être ajouté lorsque l'environnement doit disposer de capacités avancées d'administration et de gouvernance des agents.

D'autres écosystèmes peuvent être créés lorsque des responsabilités particulières justifient réellement leur séparation.

## 3.1. L'écosystème Métier

L'écosystème Métier regroupe les applications, les systèmes et les services qui réalisent les fonctions propres à l'organisation.

Il constitue le périmètre dans lequel se trouvent les logiciels utilisés pour accomplir les activités opérationnelles de l'organisation.

Dans une entreprise, il peut notamment accueillir des applications de gestion des ressources humaines, de facturation, de gestion financière, de gestion commerciale ou de gestion documentaire.

Dans une université, il peut accueillir les systèmes de gestion des étudiants, des inscriptions, des formations, des emplois du temps, des évaluations ou des services administratifs.

Dans une banque, il peut accueillir les systèmes de gestion des comptes, des opérations bancaires, des clients ou des produits financiers.

Ces différences ne créent pas des catégories d'écosystèmes supplémentaires. Elles représentent simplement les variations des services métier hébergés dans l'écosystème Métier de chaque organisation.

L'écosystème Métier peut accueillir des applications complètes, des microservices, des moteurs spécialisés métier, des projets open source ou des composants développés sur mesure.

Il ne doit pas être obligé d'utiliser une technologie de développement ou d'exécution unique. Une application Java peut coexister avec un service Python, une application Go, un système PHP ou un logiciel open source disposant de sa propre architecture.

L'objectif est de pouvoir intégrer ces composants dans un environnement gouverné sans leur imposer une réécriture systématique.

L'écosystème Métier est responsable de ses applications et de ses services fonctionnels. Il ne doit cependant pas devenir le propriétaire des capacités techniques communes qui sont administrées dans l'écosystème Transversal.

Une application métier peut utiliser un stockage, une messagerie ou un service d'observabilité sans devoir administrer elle-même ces infrastructures.

## 3.2. L'écosystème Transversal

L'écosystème Transversal regroupe les capacités techniques communes nécessaires au fonctionnement de plusieurs services ou écosystèmes.

Il existe pour éviter que chaque application réimplémente ou administre indépendamment les mêmes fonctions techniques.

Il peut notamment regrouper :

- Les infrastructures de stockage objet et de fichiers.
- Les systèmes de messagerie et de diffusion d'événements.
- Les mécanismes de gestion des secrets.
- Les services de certificats et de PKI.
- Les infrastructures d'observabilité.
- Les services de journalisation, de métriques et de traces.
- Les mécanismes de sauvegarde et de restauration.
- Les services DNS et de découverte, selon leur périmètre.
- Les registres d'images, de paquets et d'artefacts.
- Les services de proxy et de connectivité.
- Les mécanismes de configuration partagée.
- D'autres capacités techniques destinées à être consommées par plusieurs services.

La responsabilité de cet écosystème est d'administrer et de rendre accessibles ces capacités.

Il ne signifie pas que toutes les ressources transversales doivent être installées sur une seule machine, dans un seul cluster ou dans une seule instance technique. Un même écosystème peut contenir plusieurs services indépendants et plusieurs infrastructures spécialisées.

Il peut également disposer de plusieurs instances d'un même service lorsqu'une séparation de sécurité, de performance, de disponibilité ou de localisation est nécessaire.

Par exemple, l'écosystème Transversal peut administrer plusieurs infrastructures de stockage, chacune réservée à un périmètre particulier, tout en exposant une capacité de stockage commune à travers des contrats adaptés.

La notion de transversalité décrit donc la responsabilité du service, pas nécessairement sa centralisation physique.

### Relation entre Métier et Transversal

L'écosystème Métier consomme les capacités mises à disposition par l'écosystème Transversal.

Prenons le cas d'une application de gestion des ressources humaines. Elle peut avoir besoin de stocker des documents, de publier des événements, de récupérer des secrets et de transmettre ses journaux.

Elle utilise alors les interfaces et les références des capacités disponibles dans l'environnement.

Elle n'a pas besoin de connaître les détails internes du déploiement de MinIO, de Vault, de Kafka ou d'un autre moteur. Elle n'a pas non plus besoin de solliciter le Kernel Transversal à chaque opération fonctionnelle.

Le Kernel Métier connaît les capacités qu'il est autorisé à consommer et les références nécessaires pour y accéder. Le Kernel Transversal reste responsable de leur administration.

Cette distinction évite de mélanger la responsabilité de fournir une capacité avec celle de l'utiliser.

## 3.3. L'écosystème d'Intelligence et d'Agents

Un troisième écosystème peut être déployé pour prendre en charge l'administration, la gouvernance et l'exploitation des capacités d'intelligence artificielle et des agents autonomes.

L'appellation proposée est **Écosystème d'Intelligence et d'Agents**. Dans les documents techniques, il pourra être désigné plus simplement comme l'écosystème IA.

Il ne doit pas être considéré comme un simple environnement d'hébergement de modèles de langage. Sa responsabilité est plus large : il doit fournir les mécanismes permettant de créer, configurer, administrer, déployer, superviser et faire évoluer les agents autonomes de l'ensemble de l'environnement EGEN.

Il peut notamment regrouper :

- Les registres et catalogues d'agents.
- Les mécanismes de création et de configuration des agents.
- Les environnements d'exécution des agents.
- Les systèmes d'orchestration et de coordination.
- Les fournisseurs de modèles et les passerelles vers ces fournisseurs.
- Les outils et connecteurs utilisables par les agents.
- Les mécanismes de gestion des instructions et des comportements.
- Les ressources de connaissance et de mémoire.
- Les mécanismes de supervision, d'évaluation et de traçabilité.
- Les politiques d'accès aux capacités et aux outils.
- Les mécanismes de versionnement et de gestion du cycle de vie des agents.

Cet écosystème doit permettre de gérer des agents qui interviennent dans différents services et différents périmètres.

Par exemple, une organisation peut disposer d'un agent chargé d'assister les utilisateurs, d'un autre chargé de l'analyse documentaire, d'un autre dédié à l'exploitation technique et d'un autre destiné à automatiser certains processus métier.

Ces agents peuvent être administrés dans l'écosystème d'Intelligence et d'Agents, mais ils ne deviennent pas pour autant propriétaires des applications ou des données métier auxquelles ils accèdent.

Un agent doit utiliser les interfaces et les autorisations prévues par les services concernés. Il ne doit pas contourner les politiques du Kernel responsable d'une ressource.

L'écosystème IA constitue ainsi un environnement spécialisé de gouvernance des agents, capable de coopérer avec les écosystèmes Métier et Transversal.

## 3.4. Les écosystèmes spécialisés supplémentaires

L'architecture ne doit pas être limitée définitivement à trois écosystèmes.

Il peut être pertinent de créer d'autres écosystèmes lorsqu'une responsabilité particulière nécessite une séparation claire, notamment en matière de sécurité, d'isolation, de gouvernance, de cycle de vie ou d'évolution.

Cependant, la création d'un nouvel écosystème ne doit pas devenir un moyen de multiplier artificiellement les périmètres.

Avant d'en créer un, il faut déterminer :

1. Quelle responsabilité précise doit-il prendre en charge ?
2. Cette responsabilité est-elle déjà couverte par un écosystème existant ?
3. Pourquoi ne peut-elle pas être correctement intégrée à un écosystème existant ?
4. La séparation apporte-t-elle un avantage réel en matière d'isolation, de sécurité, de gouvernance ou d'évolution ?
5. Quelles interfaces et quels contrats permettront aux autres écosystèmes de consommer ses capacités ?
6. Quel Control Plane sera responsable de son administration ?

Un écosystème supplémentaire doit donc répondre à un besoin architectural démontré.

Il ne doit pas dupliquer les responsabilités de Métier, de Transversal ou d'Intelligence et d'Agents.

---

# 4. Relation entre environnement, écosystèmes et Kernels

Cette partie constitue l'un des fondements de la nouvelle architecture.

Un environnement EGEN peut contenir plusieurs écosystèmes. Un écosystème peut être déployé dans un EGEN Kernel, qui constitue son système de contrôle local.

Le Kernel est donc associé à un périmètre de gouvernance et non à une catégorie de technologie ou à un secteur d'activité.

## 4.1. Le déploiement d'un écosystème dans un Kernel

Lorsqu'un écosystème est déployé dans un EGEN Kernel, le Kernel fournit les mécanismes nécessaires à son administration.

Il connaît les composants qui relèvent de son périmètre, les capacités qu'il peut utiliser, les extensions installées, les moteurs disponibles, les politiques applicables et les opérations en cours.

L'écosystème conserve néanmoins sa propre organisation fonctionnelle. Ses applications et ses services restent des composants autonomes, avec leurs propres technologies, leurs propres données et leurs propres cycles de vie.

Le Kernel ne devient donc pas le code applicatif de l'écosystème. Il constitue le système de contrôle qui permet de gouverner son déploiement et son fonctionnement.

Cette distinction permet de faire évoluer indépendamment le socle de contrôle et les services qu'il administre.

## 4.2. Le Kernel n'est pas une simple couche d'adaptateurs

Le Kernel ne doit plus être représenté comme un composant central qui se contente de recevoir une demande, de choisir un adaptateur et d'appeler un moteur.

Cette vision est trop limitée.

Il doit disposer d'une structure interne capable de prendre en charge les responsabilités suivantes :

**Gouvernance du périmètre.** Le Kernel connaît son identité, son périmètre de responsabilité, ses politiques et les ressources qu'il est autorisé à administrer.

**Registre local.** Il conserve les informations structurées nécessaires à la connaissance de son environnement : ressources, services, composants, relations, dépendances, extensions, capacités et opérations suivies.

**Modèle déclaratif.** Il permet d'exprimer les intentions et les états attendus des ressources relevant de son périmètre.

**Gestion des capacités.** Il connaît les fonctions techniques disponibles, les extensions qui les fournissent, les contraintes associées et les systèmes auxquels il peut déléguer les opérations.

**Gestion des extensions.** Il permet d'ajouter, d'activer, de configurer, de mettre à jour et de retirer des intégrations ou des capacités.

**Délégation.** Il transmet les opérations aux moteurs spécialisés ou à d'autres systèmes compétents.

**Suivi et observation.** Il conserve la visibilité sur les demandes, les opérations, les résultats, les erreurs et les états connus.

**Administration locale.** Il expose les interfaces nécessaires à la gestion de son propre périmètre.

**Participation à la fédération.** Il peut annoncer son identité, ses interfaces et ses capacités aux autres systèmes de contrôle, selon les contrats prévus.

Le Kernel doit donc être un système de contrôle complet dans son périmètre, tout en déléguant les responsabilités techniques spécialisées.

## 4.3. Le Kernel ne devient pas propriétaire de tous les moteurs

Un Kernel peut utiliser plusieurs moteurs pour administrer son écosystème.

Par exemple, il peut utiliser Nomad pour certaines charges de travail, Kubernetes pour d'autres, Consul pour la découverte, NATS pour la messagerie et Vault pour la gestion des secrets.

Il peut également utiliser une plateforme externe comme Crossplane pour certaines opérations de provisioning.

Le Kernel connaît ces systèmes à travers des intégrations et des contrats adaptés. Il ne doit pas nécessairement réimplémenter leurs mécanismes internes.

Le fait qu'un moteur soit utilisé par un Kernel ne signifie pas que ce moteur fait partie du Kernel lui-même.

Il faut donc distinguer :

- Le socle de contrôle : EGEN Kernel.
- Les extensions et intégrations : les mécanismes qui rendent les capacités techniques accessibles au Kernel.
- Les moteurs spécialisés : les systèmes qui réalisent effectivement les opérations.
- Les services administrés : les applications et ressources relevant du périmètre du Kernel.

Cette séparation doit être conservée dans l'architecture logicielle et dans l'organisation des déploiements.

---

# 5. Le rôle du Federation Plane dans l'environnement EGEN

Lorsque plusieurs écosystèmes disposent de leurs propres Kernels, il devient nécessaire de mettre en place un mécanisme de coopération.

C'est le rôle du Federation Plane.

## 5.1. Pourquoi un niveau de fédération est nécessaire

Chaque Kernel doit rester autonome. Il doit pouvoir administrer son écosystème sans dépendre en permanence des autres Kernels.

Cependant, les écosystèmes ne peuvent pas fonctionner comme des environnements totalement isolés.

L'écosystème Métier doit pouvoir consommer des capacités de l'écosystème Transversal. L'écosystème d'Intelligence et d'Agents doit pouvoir accéder aux interfaces des services métier et aux capacités techniques nécessaires à ses agents.

Un écosystème supplémentaire peut également dépendre de ressources administrées par plusieurs autres écosystèmes.

Il faut donc disposer d'un niveau capable de connaître les différents Control Planes, de découvrir leurs capacités et de faciliter leur coopération.

Le Federation Plane remplit cette fonction.

## 5.2. Le Federation Plane ne remplace pas les Kernels

Le Federation Plane ne doit pas devenir un Kernel central qui administre directement tous les écosystèmes.

Il ne doit pas posséder une copie complète des registres locaux, ni prendre en charge les opérations internes des Kernels.

Chaque Kernel demeure l'autorité responsable de son périmètre.

Le Federation Plane connaît notamment :

- L'identité des Control Planes participants.
- Leur propriétaire et leur périmètre de responsabilité.
- Leurs interfaces et versions de contrats.
- Les capacités qu'ils déclarent.
- Les relations de confiance établies.
- Les informations nécessaires à leur découverte et à leur communication.
- Les opérations globales nécessitant plusieurs Control Planes.
- Les événements et informations synthétiques utiles à la coordination.

Il ne devient pas pour autant la source de vérité de toutes les ressources locales.

Par exemple, le Federation Plane peut savoir que le Kernel Transversal fournit une capacité de stockage objet. Il n'a pas besoin de connaître tous les objets stockés, les détails des volumes ou les opérations internes de MinIO.

Ces informations relèvent du Kernel responsable et du moteur spécialisé concerné.

## 5.3. Le Federation Plane peut fédérer des Kernels EGEN et des Control Planes externes

La fédération ne doit pas être réservée aux seules instances d'EGEN Kernel.

Un environnement peut déjà disposer de systèmes de contrôle qui remplissent certaines fonctions et qu'il serait inutile de remplacer.

Crossplane constitue un exemple important. Il peut être intégré comme Control Plane externe, en conservant son propre modèle déclaratif, ses Providers, ses contrôleurs et ses mécanismes de réconciliation.

D'autres plateformes peuvent également participer à la fédération si elles disposent d'interfaces suffisamment exploitables.

Le Federation Plane doit donc être conçu autour de contrats communs et non autour d'une dépendance à l'implémentation interne du Kernel.

---

# 6. Architecture générale cible

L'architecture peut être représentée à travers trois niveaux principaux : l'environnement de gouvernance, les systèmes de contrôle et les moteurs spécialisés.

```text
                  ENVIRONNEMENT DE GOUVERNANCE EGEN
┌───────────────────────────────────────────────────────────────────┐
│                                                                   │
│                         FEDERATION PLANE                          │
│                                                                   │
│   Registre des Control Planes     Découverte des capacités        │
│   Routage et coordination         Confiance et politiques globales│
│   Suivi des opérations globales   Observabilité de la fédération  │
│                                                                   │
│          │                    │                    │               │
│          ▼                    ▼                    ▼               │
│                                                                   │
│   ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐    │
│   │ KERNEL MÉTIER  │  │ KERNEL         │  │ KERNEL           │    │
│   │                │  │ TRANSVERSAL    │  │ INTELLIGENCE     │    │
│   │ Control Plane  │  │ Control Plane  │  │ ET AGENTS        │    │
│   │ autonome       │  │ autonome       │  │ Control Plane    │    │
│   └───────┬────────┘  └───────┬────────┘  └────────┬─────────┘    │
│           │                  │                    │              │
│           ▼                  ▼                    ▼              │
│   Applications et     Capacités techniques  Agents, modèles,     │
│   services métier     communes              outils et mémoire    │
│                                                                   │
│   ┌───────────────────────────────────────────────────────────┐   │
│   │          CONTROL PLANES EXTERNES ÉVENTUELS                 │   │
│   │                                                           │   │
│   │          Crossplane / autres plateformes                  │   │
│   └───────────────────────────────────────────────────────────┘   │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

Ce schéma représente une organisation logique. Il ne signifie pas que tous les composants doivent être installés sur le même cluster, ni que le Federation Plane doit être un serveur unique.

Chaque Kernel possède son propre périmètre de contrôle. Les moteurs spécialisés peuvent être installés dans des infrastructures différentes et certains peuvent être mutualisés lorsque les conditions d'isolation le permettent.

Le Federation Plane assure la coopération entre les systèmes de contrôle, mais ne supprime pas leurs frontières.

---

# 7. Architecture interne d'un EGEN Kernel

La nouvelle place du Kernel nécessite de préciser ses grandes responsabilités internes.

Il ne s'agit pas encore de figer les packages, les classes ou les modules logiciels. L'objectif est d'identifier les capacités architecturales que le socle doit fournir.

## 7.1. Control API

Le Kernel doit exposer une interface de contrôle permettant aux autres composants de lui transmettre des demandes et de consulter les informations relevant de son périmètre.

Cette interface doit permettre de déclarer des ressources, de consulter leur état, de demander des opérations, de connaître les capacités disponibles et de suivre les traitements.

Elle constitue le point d'entrée principal des consommateurs autorisés.

Le Kernel ne doit pas obliger ses consommateurs à connaître les API internes de chaque moteur spécialisé. Il doit exposer des contrats adaptés à ses responsabilités.

## 7.2. Registre local et modèle de ressources

Le Kernel doit posséder un registre local qui constitue sa référence pour les ressources et les éléments qu'il administre.

Ce registre peut contenir les descriptions des services, des applications, des ressources techniques, des dépendances, des configurations, des extensions, des opérations et des relations entre composants.

Il doit également permettre d'identifier clairement les ressources dont le Kernel est responsable et celles qu'il ne fait que référencer.

Une ressource externe consommée par un Kernel ne devient pas automatiquement une ressource qu'il administre.

Cette distinction doit être présente dans le modèle de données.

Le registre local ne doit pas être confondu avec le registre de fédération. Le premier contient les informations nécessaires à l'administration locale ; le second contient les informations nécessaires à la connaissance et à la coordination des Control Planes.

## 7.3. Capability Management

Le Kernel doit être capable de connaître les capacités techniques qu'il peut utiliser.

Une capacité représente une fonction disponible, indépendamment de la technologie qui l'implémente.

Par exemple, une capacité de déploiement peut être fournie par Nomad, Kubernetes ou un autre moteur. Une capacité de stockage objet peut être fournie par MinIO ou une solution compatible S3.

Le Kernel doit pouvoir identifier le fournisseur de la capacité, son contrat, ses opérations, ses contraintes, ses versions et les conditions d'utilisation.

Cette approche permet d'éviter de coder directement les noms des technologies dans toutes les parties du Kernel.

Elle permet également d'installer plusieurs implémentations d'une même capacité et de choisir celle qui convient à une opération donnée.

## 7.4. Extension Management

Le système d'extensions doit permettre au Kernel d'intégrer de nouvelles capacités sans devoir modifier systématiquement son socle.

Une extension peut connecter le Kernel à un moteur, à un protocole, à une plateforme externe ou à un service spécialisé.

Elle doit disposer de métadonnées permettant de connaître sa version, ses dépendances, ses capacités, sa compatibilité et son état.

Le Kernel doit pouvoir gérer le cycle de vie des extensions : installation, activation, configuration, mise à jour, désactivation et retrait.

Les extensions ne doivent pas être confondues avec les moteurs qu'elles connectent. Une extension fournit le mécanisme d'intégration ; le moteur réalise l'opération technique.

Le format et le modèle d'exécution des extensions devront être définis dans une phase de conception technique spécifique.

## 7.5. Delegation Management

La délégation constitue une responsabilité centrale du Kernel.

Lorsqu'une opération doit être réalisée, le Kernel identifie le système compétent et lui transmet une demande conforme au contrat attendu.

Il peut s'agir d'un moteur spécialisé, d'un service externe ou d'un autre Control Plane.

La délégation doit préserver les informations nécessaires à la traçabilité : identité du demandeur, ressource concernée, opération demandée, autorisations, identifiant de suivi et résultat attendu.

Le Kernel doit également pouvoir gérer les refus, les erreurs, les délais, les opérations asynchrones et les interruptions de communication.

Déléguer une opération ne signifie pas abandonner sa responsabilité de gouvernance. Le Kernel reste responsable du suivi de la demande et de la connaissance de son résultat dans son propre périmètre.

## 7.6. Reconciliation Management

La réconciliation doit être comprise comme une responsabilité de contrôle dont l'exécution peut être confiée à un moteur spécialisé.

Le Kernel conserve la connaissance de l'état attendu, les informations de suivi et la responsabilité de gouverner les opérations.

Il peut ensuite déléguer le rapprochement entre l'état réel et l'état attendu au système approprié.

Il ne faut donc pas imposer que chaque EGEN Kernel développe un moteur de réconciliation universel.

Certains moteurs possèdent déjà leurs propres mécanismes de réconciliation. Kubernetes, Crossplane ou d'autres plateformes déclaratives peuvent prendre en charge cette fonction dans leur périmètre.

Le Kernel doit être capable de travailler avec ces mécanismes, de suivre les résultats et de maintenir une représentation cohérente de ses ressources.

Il peut néanmoins exister des mécanismes internes de suivi, de vérification ou de coordination dans le Kernel. Ceux-ci ne doivent pas être confondus avec un moteur spécialisé de réconciliation des ressources.

## 7.7. Policy Management

Chaque Kernel doit pouvoir appliquer ses propres politiques locales.

Ces politiques déterminent notamment les opérations autorisées, les moteurs utilisables, les ressources administrables, les limites d'utilisation et les conditions dans lesquelles une demande peut être exécutée.

Les politiques locales doivent s'articuler avec les éventuelles politiques globales de la fédération.

Une demande acceptée au niveau global peut être refusée par le Kernel responsable si elle ne respecte pas ses règles locales.

Inversement, un Kernel ne doit pas pouvoir ignorer les contraintes globales qui lui sont applicables.

## 7.8. Operation Tracking et Event Management

Le Kernel doit conserver les informations relatives aux opérations qu'il déclenche ou reçoit.

Il doit être capable de distinguer une demande reçue, une demande acceptée, une opération en cours, une opération terminée, une opération échouée et une opération partiellement réalisée.

Il doit également pouvoir publier les événements utiles aux autres composants et à la fédération.

Cette capacité est particulièrement importante pour les opérations asynchrones et les environnements distribués.

## 7.9. Administration et exploitation

Le Kernel doit exposer les interfaces nécessaires à son administration.

Ces interfaces peuvent être utilisées par des applications Web, des outils CLI, des SDK, des systèmes d'automatisation ou des agents IA.

Les interfaces d'administration ne doivent pas être intégrées obligatoirement dans le cœur du Kernel. Elles peuvent être développées comme des services autonomes qui utilisent les API de contrôle.

Cette séparation permet de remplacer ou de faire évoluer les outils d'administration sans reconstruire le socle de contrôle.

Le fonctionnement du Kernel ne doit cependant pas dépendre exclusivement d'une interface d'administration externe. Il doit toujours exister des mécanismes de récupération et d'accès technique indépendants.

---

# 8. Les moteurs spécialisés et les systèmes externes

L'architecture doit permettre aux Kernels de travailler avec des technologies différentes, sans dépendre d'un moteur unique.

## 8.1. Les moteurs spécialisés ne sont pas nécessairement des Control Planes

Il faut distinguer un moteur spécialisé d'un système de contrôle.

Un moteur spécialisé assure une fonction technique particulière. Il peut être utilisé par un Kernel sans constituer lui-même un Control Plane autonome.

Quelques exemples :

| Technologie | Responsabilité principale |
|---|---|
| Nomad | Orchestration et placement de charges de travail |
| Kubernetes | Gestion déclarative de ressources et d'applications |
| Consul | Découverte et connectivité de services |
| NATS | Messagerie et communication événementielle |
| Kafka | Diffusion et conservation de flux d'événements |
| Vault | Gestion des secrets |
| MinIO | Stockage objet compatible S3 |
| Harbor | Registre d'images et d'artefacts |
| Prometheus | Collecte et stockage de métriques |
| OpenTelemetry | Instrumentation et transmission des données d'observabilité |

Cette liste est indicative. Elle ne signifie pas que toutes ces technologies doivent être utilisées dans chaque environnement.

## 8.2. La possibilité de mutualiser les moteurs

Plusieurs Kernels peuvent utiliser un même moteur spécialisé lorsque son modèle de sécurité et ses capacités d'isolation le permettent.

Par exemple, plusieurs Kernels peuvent utiliser un même cluster Kubernetes ou une même infrastructure de messagerie.

Cette mutualisation ne doit pas entraîner une fusion des registres locaux ni une confusion des responsabilités.

Chaque Kernel doit pouvoir identifier les ressources qu'il est autorisé à administrer, les opérations qu'il peut réaliser et les limites de son périmètre.

Il faut distinguer le partage d'une infrastructure technique du partage d'un système de contrôle.

Dans le premier cas, plusieurs Control Planes utilisent un moteur commun tout en conservant leur autonomie. Dans le second, plusieurs périmètres dépendent du même système de contrôle, ce qui constitue un modèle différent et nécessite des garanties d'isolation particulières.

## 8.3. L'indépendance vis-à-vis des moteurs de déploiement

Un Kernel ne doit pas imposer un moteur de déploiement unique.

Il doit pouvoir travailler avec Nomad, Kubernetes ou d'autres technologies, selon les capacités disponibles et les contraintes du périmètre qu'il administre.

Un même environnement EGEN peut donc utiliser plusieurs moteurs de déploiement simultanément.

La sélection doit prendre en compte les capacités, les contraintes techniques, les politiques, les ressources disponibles et les caractéristiques du service concerné.

Cette indépendance doit également s'appliquer à la découverte, à la messagerie, au stockage, à la configuration, aux secrets et à l'observabilité.

---

# 9. Crossplane et les Control Planes externes

Crossplane occupe une place particulière dans cette architecture, car il ne doit pas être confondu avec un simple moteur spécialisé.

Crossplane est un framework permettant de construire des Control Planes déclaratifs sur Kubernetes. Il fournit notamment des mécanismes de définition de ressources, de composition et de réconciliation à travers des Providers et des contrôleurs.

Il peut donc intervenir de plusieurs manières dans l'environnement EGEN.

## 9.1. Crossplane comme Control Plane fédéré

Dans ce scénario, Crossplane est intégré comme un système de contrôle autonome.

Il conserve son environnement Kubernetes, ses ressources déclaratives, ses Providers et ses mécanismes de réconciliation.

Le Federation Plane connaît son identité, ses interfaces et les capacités qu'il expose.

Les autres Control Planes peuvent lui transmettre des demandes à travers une intégration appropriée, sans devoir connaître tous les détails de son fonctionnement interne.

Crossplane reste responsable des ressources qu'il administre.

## 9.2. Crossplane comme système auquel un Kernel délègue certaines opérations

Dans un autre scénario, un EGEN Kernel peut utiliser Crossplane pour réaliser certaines opérations de provisioning et de gestion déclarative des ressources.

Le Kernel conserve son propre périmètre de gouvernance et confie à Crossplane une responsabilité spécialisée.

Cette intégration nécessite de définir les ressources concernées, les contrats d'échange, les opérations autorisées et les mécanismes de suivi.

Dans ce cas, Crossplane intervient comme système délégué au service du Kernel, et non comme un Control Plane participant nécessairement à la fédération au même niveau.

## 9.3. Une distinction à conserver

Les deux scénarios sont différents.

- Dans le premier, Crossplane est un Control Plane autonome intégré à la fédération.
- Dans le second, Crossplane est un système spécialisé utilisé par un autre Control Plane pour certaines opérations.

Il ne faut pas imposer un seul positionnement à Crossplane dans tous les environnements.

Le choix dépend du périmètre de responsabilité, des besoins et de l'organisation technique retenue.

L'architecture EGEN doit pouvoir prendre en charge les deux modèles sans dépendre obligatoirement de Kubernetes ou de Crossplane.

---

# 10. La fédération : responsabilités et mécanismes

Le Federation Plane doit disposer de mécanismes suffisamment génériques pour fédérer plusieurs Control Planes indépendants.

## 10.1. Registre des Control Planes

Le registre de fédération doit permettre de connaître les systèmes de contrôle qui participent à l'environnement.

Pour chaque système, il doit être possible de connaître son identifiant, son nom, sa nature, son propriétaire, son périmètre, ses interfaces, ses versions de contrats, ses capacités déclarées et les informations nécessaires à son accès.

Il doit également conserver les informations utiles à la gestion de la confiance et de la disponibilité.

Ce registre ne doit pas devenir un inventaire détaillé de toutes les ressources administrées par les Kernels.

Il représente les systèmes de contrôle et les informations nécessaires à leur coopération.

## 10.2. Découverte des capacités

Le Federation Plane doit pouvoir découvrir les capacités proposées par les différents Control Planes.

Cette découverte ne doit pas se limiter aux technologies utilisées.

Deux Kernels peuvent utiliser Kubernetes tout en exposant des capacités et des autorisations différentes. Deux systèmes peuvent également exposer une même capacité en s'appuyant sur des technologies complètement différentes.

La fédération doit donc connaître les fonctions réellement disponibles, leurs contrats, leurs versions, leurs contraintes et leurs conditions d'utilisation.

## 10.3. Routage des demandes

Lorsqu'une demande concerne un périmètre particulier, la fédération doit être capable de l'orienter vers le Control Plane responsable.

Le routage doit prendre en compte le propriétaire de la ressource, le périmètre de responsabilité, les capacités disponibles, les politiques applicables et les relations de confiance.

Il ne s'agit pas simplement de choisir une adresse réseau.

Il s'agit d'identifier l'autorité compétente pour traiter une demande.

## 10.4. Coordination des opérations multi-Control Planes

Certaines opérations nécessitent plusieurs systèmes de contrôle.

Une application peut dépendre d'un environnement d'exécution géré par un Kernel, d'une base de données administrée par un autre système et d'un service de stockage fourni par un troisième.

Le Federation Plane doit pouvoir coordonner les demandes nécessaires et suivre leur avancement.

Il ne doit toutefois pas imposer une transaction distribuée unique à tous les systèmes.

Les Control Planes peuvent avoir des temporalités, des modèles de réconciliation et des mécanismes de fonctionnement différents.

La coordination doit donc gérer les opérations asynchrones, les dépendances, les échecs partiels, les reprises et les compensations lorsque cela est nécessaire.

Chaque Control Plane reste responsable de ses opérations locales. La fédération suit le processus global.

## 10.5. Fédération centralisée, distribuée ou hiérarchique

Le Federation Plane ne doit pas nécessairement être un serveur central unique.

Plusieurs modèles doivent pouvoir être étudiés :

- Une fédération centralisée, avec un niveau de coordination commun.
- Une fédération distribuée, dans laquelle plusieurs composants de fédération coopèrent.
- Une fédération hiérarchique, dans laquelle des niveaux de fédération peuvent eux-mêmes coordonner plusieurs sous-fédérations.

Le choix dépendra du nombre de Control Planes, des contraintes d'exploitation, des frontières organisationnelles, des besoins de disponibilité et de la distribution géographique.

Cette question devra être résolue lors de la conception détaillée du Federation Plane.

---

# 11. Les contrats et interfaces d'interopérabilité

La fédération ne peut fonctionner correctement que si les systèmes participants disposent d'un langage commun.

Il ne suffit pas que chaque Control Plane expose une API. Il faut que les autres systèmes puissent comprendre ce qu'il annonce, les opérations qu'il accepte, les résultats qu'il retourne et les garanties qu'il fournit.

Les contrats doivent donc constituer une partie centrale de l'architecture.

## 11.1. Contrat d'identité et de description

Chaque Control Plane doit pouvoir présenter une identité stable.

Cette identité doit permettre de connaître son type, son propriétaire, son périmètre, son environnement, ses interfaces et les versions des contrats qu'il prend en charge.

## 11.2. Contrat de capacités

Le contrat de capacités décrit les fonctions que le Control Plane met à disposition.

Il doit préciser les opérations disponibles, leurs paramètres, leurs contraintes, les versions prises en charge et les conditions d'accès.

Il doit permettre de distinguer une capacité annoncée d'une capacité effectivement accessible à un demandeur donné.

## 11.3. Contrat de délégation

Le contrat de délégation définit la manière dont un système transmet une opération à un autre.

Il doit préciser les informations nécessaires à l'exécution, les conditions d'acceptation, les identifiants de suivi, les états possibles et les modalités de récupération du résultat.

Il doit également permettre de gérer les demandes refusées, les erreurs et les opérations asynchrones.

## 11.4. Contrat d'état et de résultat

Il faut distinguer l'état d'une demande, l'état d'une opération et l'état d'une ressource.

Une demande peut être acceptée alors que son exécution n'a pas commencé. Une opération peut être terminée alors que la ressource n'est pas encore dans l'état attendu.

Les contrats doivent permettre de représenter ces situations sans ambiguïté.

## 11.5. Contrat d'événements

Les Control Planes doivent pouvoir publier les événements importants concernant leur fonctionnement, les capacités disponibles et les opérations réalisées.

Le transport peut être assuré par différents systèmes de messagerie. Le contrat doit rester indépendant de la technologie utilisée.

## 11.6. Versionnement

Les contrats doivent être versionnés.

Un changement dans un Kernel ne doit pas provoquer automatiquement une rupture avec les autres Control Planes.

Il faudra donc prévoir la compatibilité, la négociation des versions, les périodes de transition et les règles de dépréciation.

---

# 12. Intégration des solutions externes

L'architecture doit permettre d'intégrer des technologies qui ne sont pas développées par CIVITAS.

Il faut notamment pouvoir travailler avec des projets open source, des plateformes propriétaires et des systèmes qui ne peuvent pas être modifiés.

Plusieurs modes d'intégration sont possibles.

## 12.1. Intégration native

Lorsqu'un système peut implémenter directement les contrats EGEN, il peut participer à la fédération sans couche de traduction particulière.

C'est le mode le plus direct.

## 12.2. Intégration par extension ou Provider

Certaines plateformes possèdent leur propre système d'extensions.

Il est possible de s'appuyer sur ces mécanismes pour connecter le système à EGEN, à condition de respecter ses interfaces et ses garanties.

Le terme Provider peut notamment désigner une extension spécifique à l'écosystème Crossplane. Il ne faut pas imposer ce modèle à tous les autres systèmes.

## 12.3. Intégration par Connector ou Bridge

Lorsqu'une plateforme ne peut pas implémenter directement les contrats EGEN, un composant intermédiaire peut assurer la traduction.

Il peut utiliser les API REST, GraphQL, les webhooks, LDAP, OIDC, SAML, une CLI ou d'autres interfaces documentées.

Le Connector doit traduire les modèles, les formats, les mécanismes d'authentification et les opérations nécessaires.

Il ne doit cependant pas inventer des capacités que le système externe ne possède pas.

Si une solution ne propose aucun point d'intégration exploitable, une intégration complète et fiable ne peut pas être garantie.

## 12.4. Préservation de la gouvernance

Une intégration externe ne doit pas créer un chemin permettant de contourner les politiques de sécurité.

Les opérations doivent conserver l'identité de leur demandeur, les autorisations, les limites d'utilisation et les informations de traçabilité.

Le Connector ne doit pas devenir une porte d'entrée privilégiée qui contourne le contrôle du Kernel ou les règles de la fédération.

---

# 13. Relations entre les écosystèmes

La fédération ne doit pas être comprise comme une hiérarchie dans laquelle un écosystème administre les autres.

Les écosystèmes possèdent des responsabilités différentes et coopèrent à travers des contrats.

## 13.1. Relation Métier → Transversal

L'écosystème Métier consomme les capacités techniques mises à disposition par l'écosystème Transversal.

Le Kernel Métier connaît les références des capacités dont ses applications ont besoin et applique les règles d'accès correspondantes.

Le Kernel Transversal demeure responsable de leur administration.

## 13.2. Relation IA → Métier

L'écosystème d'Intelligence et d'Agents peut administrer des agents qui utilisent les services métier.

Ces agents peuvent consulter des informations, déclencher des opérations ou assister les utilisateurs, mais uniquement à travers les interfaces et les autorisations prévues.

L'écosystème IA ne devient pas propriétaire des applications métier ni de leurs données.

## 13.3. Relation IA → Transversal

Les agents peuvent avoir besoin de stockage, de messagerie, de secrets, d'observabilité ou d'autres capacités techniques.

Ces ressources sont consommées selon les contrats et les autorisations fournis par l'écosystème Transversal.

Le Kernel IA ne doit pas prendre automatiquement en charge l'administration de ces infrastructures.

## 13.4. Relations avec des écosystèmes supplémentaires

Un écosystème spécialisé peut consommer les capacités des autres écosystèmes selon ses besoins.

Il doit annoncer ses propres capacités et respecter les contrats des systèmes auxquels il fait appel.

Les relations ne doivent pas créer de dépendances circulaires incontrôlées ou plusieurs autorités de contrôle sur une même ressource.

## 13.5. Autorité sur les ressources

Chaque ressource administrée doit avoir une autorité clairement identifiée.

Le fait qu'un Kernel puisse consulter une ressource ou demander une opération ne signifie pas qu'il en devient le propriétaire.

Cette règle est fondamentale pour éviter que plusieurs Control Planes cherchent à administrer indépendamment la même ressource.

---

# 14. Communication et fonctionnement distribué

Les différents niveaux de l'architecture doivent pouvoir communiquer selon plusieurs modèles.

## 14.1. Communication synchrone

Elle permet à un composant de soumettre une demande et de recevoir une réponse immédiate concernant sa réception ou son traitement initial.

Elle convient notamment à la consultation, à la découverte et aux opérations rapides.

Il faut cependant distinguer l'acceptation d'une demande de la réalisation complète de l'opération.

## 14.2. Communication asynchrone

Certaines opérations peuvent durer plusieurs secondes, plusieurs minutes ou davantage.

Le système doit pouvoir accepter la demande, retourner un identifiant de suivi et communiquer les changements d'état au fur et à mesure.

La messagerie et les événements permettent de réaliser ces échanges sans maintenir une connexion permanente.

## 14.3. Communication directe entre Control Planes

La fédération ne doit pas nécessairement être un intermédiaire obligatoire pour tous les échanges.

Deux Control Planes peuvent communiquer directement lorsque les politiques et les relations de confiance le permettent.

Le Federation Plane peut alors intervenir pour la découverte, l'établissement des relations et l'orientation initiale.

## 14.4. Communication indirecte

Dans certains environnements, les échanges peuvent passer par un système de messagerie commun.

Ce modèle est utile lorsque les systèmes sont séparés par des réseaux, lorsqu'ils fonctionnent de manière asynchrone ou lorsqu'ils ne peuvent pas maintenir des connexions directes.

Le contrat d'échange doit rester indépendant du mécanisme de transport.

---

# 15. Sécurité, identité et confiance

La fédération introduit des relations entre plusieurs systèmes autonomes. Ces relations doivent être explicitement gouvernées.

Il faut distinguer quatre notions :

- **Identification :** connaître le système qui communique.
- **Authentification :** vérifier que le système est bien celui qu'il prétend être.
- **Autorisation :** déterminer les opérations qu'il est autorisé à effectuer.
- **Confiance :** définir les conditions dans lesquelles ses demandes et ses déclarations sont acceptées.

Un Control Plane ne doit pas accepter automatiquement toutes les demandes provenant d'un système connu du registre de fédération.

Chaque Kernel doit pouvoir conserver des politiques locales tout en respectant les contraintes globales applicables.

La sécurité doit notamment couvrir l'identité des Control Planes, les communications, les certificats, les secrets, les autorisations par capacité, la délégation des privilèges, la révocation des relations de confiance et la traçabilité des actions.

Il faudra également prévoir les situations dans lesquelles un Control Plane est compromis ou ne respecte plus les conditions de confiance. La fédération doit pouvoir suspendre ou révoquer sa participation sans arrêter l'ensemble de l'environnement.

---

# 16. Résilience et continuité de fonctionnement

L'architecture doit être conçue pour résister aux indisponibilités partielles.

## 16.1. Indisponibilité d'un Kernel

La panne d'un Kernel ne doit pas automatiquement arrêter les autres.

Les écosystèmes qui ne dépendent pas du Kernel indisponible doivent continuer à fonctionner selon leurs propres conditions d'exploitation.

Les services déjà déployés peuvent continuer à fonctionner grâce à leurs moteurs d'exécution, même si leur système de contrôle est temporairement inaccessible.

Il faut cependant distinguer la continuité des charges de travail et la capacité à administrer de nouvelles opérations.

## 16.2. Indisponibilité du Federation Plane

La perte temporaire du Federation Plane ne doit pas empêcher les Kernels de continuer à administrer leurs propres périmètres.

Les opérations locales peuvent rester disponibles.

En revanche, les opérations qui nécessitent une coordination globale peuvent être mises en attente ou refusées selon les garanties disponibles.

Les Kernels doivent pouvoir conserver les événements et les informations nécessaires à une synchronisation ultérieure.

## 16.3. Gestion des opérations incertaines

Une absence de réponse ne signifie pas nécessairement qu'une opération a échoué.

Un système distant peut avoir exécuté une opération sans que la réponse ait pu revenir.

Il faut donc disposer d'identifiants de suivi, de mécanismes d'idempotence et de moyens de récupérer l'état d'une opération sans la relancer aveuglément.

## 16.4. Reconnexion et synchronisation

Lorsqu'un système revient après une interruption, il doit pouvoir transmettre les changements intervenus, publier les événements conservés et actualiser les informations de fédération.

La synchronisation ne doit pas entraîner une perte de l'autorité locale sur les ressources.

---

# 17. Observabilité et audit

L'architecture doit permettre de superviser chaque Kernel indépendamment et de disposer d'une vue globale sur la fédération.

La supervision locale doit permettre de connaître l'état du Kernel, de ses extensions, de ses moteurs spécialisés, de ses ressources et des opérations qu'il suit.

La supervision globale doit permettre de connaître les Control Planes participants, leurs disponibilités, leurs capacités et les opérations qui traversent plusieurs périmètres.

Il doit être possible de suivre une opération depuis sa demande initiale jusqu'à son résultat, même lorsqu'elle implique plusieurs systèmes.

Pour cela, l'architecture doit conserver des identifiants de corrélation et des informations de provenance.

Il faut pouvoir déterminer quel système a initié une opération, quel Kernel en était responsable, quels autres systèmes ont participé, quel moteur a été sollicité, quelles étapes ont été réalisées et où une erreur s'est produite.

La fédération ne doit pas nécessairement centraliser toutes les données d'observabilité. Elle doit permettre leur corrélation et leur consultation selon les droits disponibles.

L'audit doit également distinguer les événements produits par un Kernel, ceux transmis par un Connector et ceux calculés par le Federation Plane.

---

# 18. Cycle de vie des écosystèmes et des Kernels

Les écosystèmes et les Kernels doivent pouvoir évoluer indépendamment, dans les limites de leurs contrats et de leurs dépendances.

## 18.1. Cycle de vie d'un écosystème

Un écosystème peut être créé, configuré, déployé, mis à jour, étendu, suspendu ou retiré.

Son cycle de vie doit permettre de gérer les applications, les services, les capacités consommées, les dépendances et les règles propres à son périmètre.

Le retrait d'un écosystème ne doit pas entraîner automatiquement la suppression de ressources qui lui sont seulement accessibles ou partagées.

## 18.2. Cycle de vie d'un Kernel

Un Kernel doit pouvoir être installé, configuré, démarré, mis à jour, étendu, suspendu et retiré indépendamment des autres Kernels.

Il doit conserver les informations nécessaires à son fonctionnement local.

La fédération doit être informée des changements qui affectent son identité, ses interfaces, ses capacités ou sa disponibilité.

## 18.3. Remplacement d'un Kernel

Il faut prévoir le remplacement d'une instance de Kernel sans perdre automatiquement les ressources qu'elle administrait.

Cela nécessite de définir comment les registres, les références, les responsabilités et les opérations en cours sont transférés.

Le transfert doit également éviter que l'ancien et le nouveau Kernel deviennent simultanément des autorités concurrentes sur les mêmes ressources.

## 18.4. Administration par des services externes

Des services d'administration peuvent être déployés dans un environnement EGEN et utiliser les interfaces des Kernels pour administrer leurs périmètres.

Ils peuvent fournir des interfaces Web, des outils CLI, des services d'automatisation ou des assistants IA.

Ces services restent indépendants du socle Kernel et peuvent être remplacés ou faire évoluer leurs interfaces sans modifier les mécanismes fondamentaux de contrôle.

---

# 19. Scénario complet : déploiement d'un environnement EGEN pour une université

Prenons l'exemple d'une université qui souhaite disposer de son propre environnement de gouvernance EGEN.

L'objectif est de comprendre comment les responsabilités sont organisées et comment les différents niveaux coopèrent.

## Étape 1 — Définition du contexte organisationnel

L'université constitue le contexte organisationnel.

Ses besoins sont identifiés : gestion des étudiants, inscriptions, formations, emplois du temps, ressources humaines, finances, communication et services numériques.

Ces besoins déterminent les applications et les capacités nécessaires.

Ils ne déterminent pas la création d'un écosystème par domaine d'activité.

## Étape 2 — Création de l'environnement EGEN

Un environnement de gouvernance EGEN est déployé pour cette université.

Il possède ses propres infrastructures, configurations, règles de gouvernance et périmètres de responsabilité.

Il ne partage pas automatiquement ses registres ou ses données avec les environnements EGEN d'autres organisations.

## Étape 3 — Déploiement des écosystèmes fondamentaux

L'environnement comprend au minimum :

- Un écosystème Métier.
- Un écosystème Transversal.

L'université peut également déployer l'écosystème d'Intelligence et d'Agents si elle souhaite administrer des agents autonomes.

## Étape 4 — Déploiement des EGEN Kernels

Un EGEN Kernel est déployé pour chaque périmètre de contrôle retenu.

Le Kernel Métier gouverne les applications et les services fonctionnels de l'université.

Le Kernel Transversal gouverne les capacités techniques communes.

Le Kernel IA, s'il est déployé, gouverne les agents, leurs outils, leurs modèles et leur cycle de vie.

Chaque Kernel dispose de son propre registre, de ses politiques et de ses extensions.

## Étape 5 — Mise en place des moteurs spécialisés

Les Kernels sont configurés pour utiliser les moteurs appropriés.

Le Kernel Métier peut utiliser Nomad ou Kubernetes pour les applications, selon les besoins.

Le Kernel Transversal peut administrer les infrastructures de stockage, de messagerie, de secrets, de sauvegarde et d'observabilité.

Le Kernel IA peut utiliser différents environnements d'exécution et fournisseurs de modèles.

Les moteurs ne doivent pas nécessairement être identiques d'un Kernel à l'autre.

## Étape 6 — Mise en place de la fédération

Le Federation Plane est configuré pour connaître les Kernels participants.

Il découvre leurs capacités, leurs interfaces, leurs versions et leurs relations de confiance.

Il permet aux différents périmètres de se reconnaître et de coopérer.

## Étape 7 — Déploiement des applications métier

Les applications de gestion des étudiants, des inscriptions, des formations et des ressources humaines sont déployées dans le périmètre Métier.

Le Kernel Métier connaît leurs descriptions, leurs dépendances et les opérations nécessaires à leur administration.

Il délègue les opérations d'exécution aux moteurs appropriés.

## Étape 8 — Consommation des capacités transversales

Les applications métier ont besoin de stockage, de messagerie, de secrets et d'observabilité.

Le Kernel Métier découvre les capacités mises à disposition par l'écosystème Transversal.

Il configure les références nécessaires et permet aux applications autorisées de les consommer.

Le Kernel Transversal demeure responsable de l'administration des infrastructures concernées.

## Étape 9 — Déploiement des agents autonomes

L'écosystème IA peut accueillir des agents destinés à assister les étudiants, les enseignants ou les équipes administratives.

Ces agents sont administrés dans le périmètre IA.

Lorsqu'un agent doit consulter un dossier étudiant, il utilise les interfaces du service métier concerné, avec les autorisations correspondantes.

Lorsqu'il doit stocker une ressource ou publier un événement, il utilise les capacités transversales prévues.

Il ne devient pas propriétaire des applications ni des infrastructures qu'il utilise.

## Étape 10 — Exploitation et évolution

Les applications, les moteurs et les Kernels peuvent évoluer indépendamment dans les limites des contrats.

Une nouvelle application peut être ajoutée au Métier sans modifier le Kernel Transversal.

Un nouveau moteur de stockage peut être intégré au Transversal sans imposer une réécriture des applications qui consomment son contrat.

Un nouvel agent peut être ajouté à l'écosystème IA sans modifier les mécanismes de contrôle des applications métier.

C'est cette indépendance qui doit permettre à l'environnement EGEN d'évoluer durablement.

---

# 20. Scénarios architecturaux complémentaires

## Scénario A — Plusieurs moteurs dans un même Kernel

Un Kernel doit déployer plusieurs services.

Certains sont exécutés sur Nomad, d'autres sur Kubernetes.

Le Kernel connaît les capacités de déploiement disponibles et sélectionne le moteur approprié en fonction des contraintes de chaque service.

Il conserve la gouvernance de son périmètre sans imposer un moteur unique.

## Scénario B — Un Kernel utilise un Control Plane externe

Un Kernel doit réaliser une opération de provisioning que Crossplane sait gérer.

Il transmet une demande à l'interface prévue et suit son traitement.

Crossplane conserve son propre fonctionnement et ses propres mécanismes de réconciliation.

Le Kernel ne doit pas réimplémenter les contrôleurs de Crossplane.

## Scénario C — Une application consomme plusieurs capacités transversales

Une application métier a besoin de stockage objet, de messagerie et de gestion des secrets.

Le Kernel Métier connaît les références des capacités disponibles et configure les accès autorisés.

Le Kernel Transversal reste responsable des services qui fournissent ces capacités.

## Scénario D — Une opération nécessite plusieurs Kernels

Une opération nécessite la création d'une ressource dans le périmètre Transversal, puis le déploiement d'une application dans le périmètre Métier.

Le Federation Plane identifie les deux Kernels concernés et coordonne les demandes.

Chaque Kernel exécute et suit ses propres opérations.

Le Federation Plane suit le résultat global.

## Scénario E — Intégration d'un système propriétaire

Une organisation possède déjà une plateforme de gestion qui ne peut pas être modifiée.

Un Connector utilise ses API documentées et expose les informations nécessaires à l'intégration.

Le système propriétaire conserve son fonctionnement interne et sa responsabilité sur ses ressources.

Il peut participer à la fédération sans devenir un composant interne du Kernel.

## Scénario F — Ajout d'un nouvel écosystème

Un environnement nécessite une responsabilité spécialisée qui ne relève ni du Métier, ni du Transversal, ni de l'IA.

Après analyse, un nouveau périmètre est créé.

Il reçoit son propre système de contrôle et ses propres contrats d'intégration.

Il ne doit pas dupliquer les capacités déjà administrées par les autres écosystèmes.

## Scénario G — Indisponibilité d'un Kernel

Le Kernel Transversal devient temporairement indisponible.

Les services déjà déployés peuvent continuer à fonctionner selon les capacités de leurs moteurs.

Le Kernel Métier peut poursuivre les opérations locales qui ne nécessitent pas d'intervention du Transversal.

Les nouvelles opérations qui nécessitent une capacité indisponible peuvent être mises en attente ou refusées.

Lorsque le Kernel Transversal revient, il reprend le suivi et actualise les informations nécessaires.

---

# 21. Ce que l'architecture EGEN ne doit pas devenir

Plusieurs dérives doivent être évitées dès la conception.

**Un Kernel universel qui contrôle tout.** Chaque Kernel doit conserver un périmètre de responsabilité précis. L'ensemble de l'environnement est organisé par coopération entre les systèmes de contrôle.

**Un Federation Plane qui remplace les Kernels.** La fédération coordonne les systèmes autonomes ; elle ne prend pas en charge leur administration locale.

**Un moteur universel qui réimplémente toutes les fonctions.** Le Kernel doit déléguer les opérations spécialisées aux moteurs appropriés.

**Une dépendance obligatoire à une technologie.** Ni Nomad, ni Kubernetes, ni Crossplane, ni une autre technologie ne doit devenir une condition universelle de fonctionnement.

**Un écosystème par secteur d'activité.** Les écosystèmes doivent être définis par leurs responsabilités et non par les domaines organisationnels.

**Une duplication des responsabilités.** Un service ne doit pas être administré simultanément par plusieurs Kernels sans mécanisme explicite d'autorité et de coordination.

**Une centralisation systématique des infrastructures.** La transversalité ne signifie pas que toutes les ressources doivent être physiquement regroupées.

**Une fédération qui devient un registre global de toutes les ressources.** Chaque Kernel conserve son registre local ; la fédération connaît les systèmes, leurs capacités et les informations nécessaires à leur coopération.

**Une intégration externe sans gouvernance.** Les Connectors et les Bridges doivent respecter les politiques, les autorisations et la traçabilité.

**Une dépendance permanente à la fédération.** Chaque Kernel doit pouvoir continuer à administrer son périmètre lorsque la fédération est temporairement indisponible.

---

# 22. Répartition synthétique des responsabilités

| Élément | Responsabilité principale |
|---|---|
| Environnement EGEN | Regrouper les écosystèmes et les systèmes de contrôle d'une organisation |
| Écosystème Métier | Organiser les applications et services fonctionnels |
| Écosystème Transversal | Fournir et administrer les capacités techniques communes |
| Écosystème d'Intelligence et d'Agents | Administrer les agents, modèles, outils et mécanismes IA |
| EGEN Kernel | Contrôler et gouverner un périmètre de ressources et de services |
| Control Plane externe | Administrer son propre périmètre avec son fonctionnement indépendant |
| Federation Plane | Découvrir, relier et coordonner les Control Planes |
| Registre local | Conserver les informations nécessaires au contrôle local |
| Registre de fédération | Connaître les Control Planes, leurs capacités et leurs interfaces |
| Extension | Ajouter une intégration ou une capacité au Kernel |
| Provider | Fournir une intégration selon le modèle d'une plateforme comme Crossplane |
| Connector / Bridge | Traduire les interfaces entre systèmes incompatibles |
| Moteur spécialisé | Réaliser une opération technique particulière |
| Service métier | Réaliser une fonction opérationnelle de l'organisation |
| Service d'administration | Fournir des interfaces et des outils de gestion des systèmes de contrôle |

---

# 23. Garanties architecturales attendues

L'architecture cible doit garantir plusieurs propriétés.

**Autonomie locale.** Chaque Kernel doit pouvoir fonctionner et administrer son périmètre indépendamment de la disponibilité permanente de la fédération.

**Séparation des responsabilités.** Chaque écosystème et chaque Kernel doit disposer d'un périmètre clairement défini.

**Indépendance technologique.** Les Kernels doivent pouvoir travailler avec plusieurs moteurs et plusieurs plateformes.

**Délégation.** Les opérations spécialisées doivent pouvoir être confiées aux systèmes compétents sans obliger le Kernel à réimplémenter leurs mécanismes internes.

**Interopérabilité.** Les Kernels EGEN et les Control Planes externes doivent pouvoir collaborer à travers des contrats communs.

**Extensibilité.** Il doit être possible d'ajouter des moteurs, des extensions, des Connectors et de nouvelles capacités sans reconstruire l'ensemble du système.

**Fédération.** Plusieurs systèmes de contrôle doivent pouvoir être fédérés sans fusionner leurs registres, leurs politiques ou leurs responsabilités.

**Traçabilité.** Les demandes, les délégations, les opérations et les événements doivent pouvoir être suivis.

**Résilience.** Les indisponibilités partielles ne doivent pas provoquer automatiquement l'arrêt de tous les écosystèmes.

**Évolutivité.** Les écosystèmes, les Kernels et les moteurs doivent pouvoir évoluer selon des cycles de vie indépendants.

---

# 24. Questions à résoudre avant la conception technique détaillée

Ce document fixe la direction architecturale et les responsabilités générales. Il ne prétend pas encore figer les modèles de données, les interfaces, les protocoles ou les mécanismes internes à implémenter.

Plusieurs sujets doivent être étudiés précisément.

## A. Organisation des environnements et des écosystèmes

1. Quel modèle de déploiement doit être retenu pour un écosystème dans un EGEN Kernel ?
2. Quelles sont les frontières exactes entre un écosystème et son Kernel ?
3. Quelles informations doivent être propres à chaque écosystème ?
4. Comment gérer les dépendances entre écosystèmes ?
5. Comment créer et retirer un écosystème sans perturber les autres ?
6. Comment représenter les relations entre plusieurs environnements EGEN appartenant à des organisations différentes ?

## B. Architecture interne du Kernel

7. Quel est le modèle interne du registre local ?
8. Comment représenter les ressources, les services, les extensions et les capacités ?
9. Comment définir le modèle déclaratif des intentions ?
10. Comment gérer les opérations et leur suivi ?
11. Quelle doit être la structure du système d'extensions ?
12. Comment isoler les extensions du socle ?
13. Comment gérer les politiques locales ?
14. Quelles responsabilités doivent être assurées directement par le Kernel et lesquelles doivent être déléguées ?

## C. Délégation et moteurs spécialisés

15. Comment un Kernel découvre-t-il les moteurs disponibles ?
16. Comment décrit-on une capacité indépendamment de sa technologie ?
17. Comment sélectionner un moteur pour une opération ?
18. Comment déléguer une opération et suivre son résultat ?
19. Comment gérer plusieurs moteurs fournissant une même capacité ?
20. Comment éviter que plusieurs systèmes réconcilient la même ressource ?
21. Comment intégrer un moteur qui possède déjà son propre système déclaratif ?

## D. Fédération des Control Planes

22. Le Federation Plane doit-il être centralisé, distribué ou hiérarchique ?
23. Un Control Plane peut-il participer à plusieurs fédérations ?
24. Une fédération peut-elle elle-même participer à une autre fédération ?
25. Comment un Kernel rejoint-il une fédération ?
26. Comment identifier et authentifier un Control Plane externe ?
27. Comment découvrir et versionner ses capacités ?
28. Comment router une demande vers le système responsable ?
29. Comment coordonner les opérations qui traversent plusieurs Kernels ?
30. Comment gérer les échecs partiels et les reprises ?

## E. Contrats et interopérabilité

31. Quel format doit décrire un Control Plane ?
32. Quel modèle doit représenter les capacités ?
33. Comment représenter les opérations, leurs paramètres et leurs résultats ?
34. Comment gérer les versions et la compatibilité ?
35. Quels contrats doivent être obligatoires pour rejoindre une fédération ?
36. Comment intégrer des plateformes propriétaires qui ne peuvent pas implémenter nativement les contrats EGEN ?

## F. Sécurité et gouvernance

37. Comment articuler les politiques globales et locales ?
38. Comment gérer l'identité des Kernels et des Control Planes externes ?
39. Comment déléguer les autorisations sans transmettre de privilèges excessifs ?
40. Comment empêcher un Connector de contourner les politiques ?
41. Comment tracer les opérations initiées par un agent IA ?
42. Comment suspendre ou révoquer un Control Plane compromis ?

## G. Résilience et exploitation

43. Que doit-il se passer lorsqu'un Kernel est indisponible ?
44. Que doit-il se passer lorsque le Federation Plane est indisponible ?
45. Comment reprendre les opérations après une interruption ?
46. Comment synchroniser les informations de fédération ?
47. Comment migrer les ressources lorsqu'un Kernel est remplacé ?
48. Comment superviser une opération de bout en bout ?

Ces questions doivent être étudiées avant de figer les modules techniques, les interfaces et les choix d'implémentation.

---

# 25. Conclusion : la véritable architecture cible

L'architecture EGEN doit être comprise comme un ensemble d'environnements de gouvernance autonomes, organisés en écosystèmes et capables de collaborer à travers une fédération de systèmes de contrôle.

Chaque organisation dispose de son propre environnement EGEN. À l'intérieur de celui-ci, les responsabilités sont réparties entre des écosystèmes distincts, notamment Métier et Transversal, auxquels peut s'ajouter l'écosystème d'Intelligence et d'Agents.

Un écosystème peut être déployé dans un EGEN Kernel. Ce Kernel constitue son système de contrôle autonome : il possède son registre, ses politiques, ses extensions, ses interfaces et les mécanismes nécessaires pour gouverner les ressources de son périmètre.

Il ne cherche pas à remplacer les moteurs spécialisés. Il connaît les capacités disponibles, détermine les opérations nécessaires, les délègue aux systèmes compétents et suit leurs résultats.

Plusieurs Kernels peuvent ainsi fonctionner dans un même environnement EGEN sans partager obligatoirement leurs registres, leurs extensions ou leurs mécanismes internes.

Le Federation Plane fournit le niveau de coopération nécessaire pour les découvrir, les relier, leur transmettre des demandes et coordonner les opérations qui impliquent plusieurs périmètres. Il peut également intégrer des Control Planes externes, notamment des plateformes comme Crossplane, lorsque leurs interfaces permettent une intégration fiable.

Cette architecture permet de conserver trois propriétés essentielles :

1. **L'autonomie :** chaque écosystème et son système de contrôle conservent leurs responsabilités et leur cycle de vie.
2. **La délégation :** les opérations techniques spécialisées sont réalisées par les moteurs compétents, sans imposer leur réimplémentation dans le Kernel.
3. **La fédération :** plusieurs systèmes de contrôle indépendants peuvent collaborer sans devenir un système monolithique.

Le rôle d'EGEN n'est donc pas de construire un unique moteur qui administrerait directement toutes les ressources de toutes les organisations. Il est de fournir les fondations permettant de déployer des systèmes de contrôle autonomes, de gouverner des écosystèmes indépendants et de les faire coopérer au sein d'environnements numériques cohérents.

**C'est cette articulation entre environnement, écosystème, Kernel, Control Plane, Federation Plane et moteurs spécialisés qui doit désormais constituer la référence de l'architecture EGEN.**
