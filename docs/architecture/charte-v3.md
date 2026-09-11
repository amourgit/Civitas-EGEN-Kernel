# Charte d'Architecture — EGEN Kernel

## 1. Objet de ce document

Cette charte est la référence unique et faisant foi pour toute décision
d'architecture concernant le Kernel EGEN. Elle décrit ce que le Kernel est,
ce qu'il n'est pas, et détaille chacun des mécanismes qui le composent — ce
qu'il gère, ce qu'il ignore délibérément, ses points d'extension. Toute
implémentation, tout module, tout service qui s'appuie sur ce Kernel doit
s'y conformer.

C'est un document vivant : il évolue avec le Kernel, mais toute évolution
doit rester fidèle au principe directeur énoncé au chapitre 3. Ce principe
ne se négocie pas ; les mécanismes qui le servent peuvent, eux, s'affiner
avec l'expérience.

## 2. Vision

CIVITAS Africa construit, sur ce Kernel, un écosystème de services
indépendants — éducation, actualité civique, communication, identité, et
tout autre domaine que l'avenir apportera. Chacun de ces services est
autonome : il possède sa propre logique, ses propres données, sa propre
technologie. Le Kernel n'est pas l'un de ces services et ne cherche jamais
à en devenir un. Il est la **plateforme d'accueil** — le substrat commun qui
permet à des services écrits par des équipes différentes, dans des langages
différents, avec des cycles de vie différents, de coexister, de se
découvrir et de collaborer comme s'ils appartenaient à un seul et même
écosystème cohérent.

Un Kernel réussi est un Kernel dont on peut retirer n'importe quel service
sans que les autres cessent de fonctionner, et auquel on peut ajouter
n'importe quel nouveau service métier — même dans un domaine encore
inconnu aujourd'hui — sans devoir modifier une seule ligne du Kernel
lui-même.

## 3. Principe directeur

> **Le Kernel ne possède rien du métier. Il fournit les mécanismes
> permettant au métier d'exister sous forme de services autonomes.**

Concrètement, cela veut dire :

- Le Kernel ne connaît pas les utilisateurs, les organisations, les rôles
  ou les permissions métier d'un service — c'est au service de les définir
  et de les faire respecter.
- Le Kernel ne connaît pas le sens d'un événement, d'une configuration ou
  d'un contrat d'API — il en connaît seulement la forme technique (type,
  version, schéma, destination).
- Le Kernel ne privilégie aucune technologie, aucun langage, aucun
  framework applicatif. Il orchestre la présence des services dans
  l'environnement, jamais leur implémentation interne.
- Le Kernel reste utilisable tel quel comme socle de n'importe quel futur
  écosystème de services, sans lien avec le premier cas d'usage qu'on lui
  aura fait porter.

## 4. Ce que le Kernel n'est pas

Pour que le principe directeur reste vérifiable dans le code et pas
seulement dans l'intention, voici ce qui est explicitement exclu du Kernel,
sans exception :

- **Identity** — pas de notion d'utilisateur, de compte, de session.
- **Authentication** — pas de vérification d'identité, pas de jeton
  applicatif interprété par le Kernel.
- **Authorization métier** — pas de rôle, de permission ou de règle
  d'accès propre à un domaine métier.
- **Users / Organizations / Tenants / Members** — aucune de ces notions
  n'existe dans le vocabulaire du Kernel.
- **Roles métier / Permissions métier / Resources métier** — ces concepts
  vivent entièrement dans les services qui en ont besoin.
- **Domaines métier eux-mêmes** — GED, Éducation, Finance, RH, Santé,
  CRM, actualité civique, etc. Chacun est un service parmi d'autres, jamais
  une brique du Kernel.

Plus largement : **le Kernel ne doit jamais devenir un IAM central
déguisé, un ERP technique, ou un backend métier partagé.** Il reste un
substrat technique — rien de plus, rien de moins. Le jour où une capacité
« pratique à avoir au même endroit pour tout le monde » se présente
(identité, autorisation, notification...), la question à se poser n'est
jamais « est-ce plus simple de la mettre dans le Kernel ? » mais toujours
« quel service indépendant va porter cette capacité, et comment le Kernel
va-t-il l'aider à l'exposer au reste de l'écosystème ? ».

## 5. Anatomie du Kernel — les douze mécanismes

Chaque mécanisme ci-dessous répond au même principe : il fournit
l'infrastructure générique, jamais le contenu métier qui la traverse.

### 5.1 Service Runtime

**Rôle.** Définir comment une instance de service doit être exécutée,
sans fournir le runtime applicatif lui-même.

**Ce qu'il gère** : instance de service, processus, container,
environnement d'exécution, ressources CPU/RAM, ports, volumes, variables
de configuration, dépendances techniques, démarrage, arrêt, redémarrage,
healthcheck, version, état d'exécution.

**Ce qu'il ignore.** Un service Python utilise Python, un service Java
utilise la JVM, un service Go utilise son runtime Go, un service Node
utilise Node. Le Kernel orchestre leur présence dans l'environnement sans
jamais chercher à uniformiser leur technologie.

### 5.2 Service Registry

**Rôle.** Savoir quels services sont présents dans l'écosystème.

**Ce qu'il gère** : identifiant, nom, version, endpoint, protocole,
capacités techniques déclarées, état, instance, dépendances, configuration
d'intégration.

**Ce qu'il ignore.** Le Registry ne contient jamais une phrase du type
« ce service gère les étudiants ». Il contient uniquement : « ce service
expose telle interface et accepte telles interactions ».

### 5.3 Service Discovery

**Rôle.** Permettre aux services de se découvrir mutuellement.

Un service n'a pas besoin d'avoir une URL codée en dur. Il peut demander
« où se trouve le service X ? » et le Kernel lui fournit l'information
nécessaire à partir du Registry. Cela permet de déplacer, répliquer,
mettre à jour ou remplacer un service sans modifier tous ceux qui en
dépendent.

### 5.4 Communication Fabric

**Rôle.** L'une des fonctions centrales du Kernel : fournir les
mécanismes permettant aux services de communiquer entre eux.

**Ce qu'elle englobe** : communication HTTP, gRPC, messaging, événements,
queues, topics, routing, retry, timeout, circuit breaking, correlation ID,
communication service-à-service.

**Ce qu'elle ignore.** La Communication Fabric transporte et route ; elle
ne comprend pas le contenu métier. Un événement `student.created`, produit
par un service Éducation, n'est pour le Kernel qu'un message possédant un
type, une source, une destination éventuelle, une version, un payload et
des métadonnées — jamais la notion d'« étudiant ».

### 5.5 Workflow Engine

**Rôle.** Fournir le mécanisme d'orchestration, sans contenir aucun
workflow métier prédéfini.

**Ce qu'il sait exécuter** : étapes, transitions, conditions, appels de
services, événements, timers, retries, compensation, états, erreurs.

Le contrat est simple : le Kernel dit « je sais exécuter un workflow », le
service dit « voici mon workflow ». Le Kernel ne connaît jamais sa
signification métier.

### 5.6 Configuration Engine

**Rôle.** Fournir le mécanisme permettant aux services de recevoir leur
configuration.

**Ce qu'il gère** : configuration déclarative, variables, paramètres,
configuration dynamique, configuration par environnement, versionnement,
validation, propagation, reload.

**Ce qu'il ignore.** Il ne définit jamais les paramètres métier eux-mêmes.
Il ne sait pas ce que signifie `school.year` ou `invoice.tax.rate` — il
sait seulement gérer des configurations déclarées par les services qui les
possèdent.

### 5.7 Dependency Management

**Rôle.** Comprendre les dépendances techniques entre services — par
exemple, que le Service A dépend du Service B.

**Ce qu'il sait** : si B est disponible, où il se trouve, quelle version
est installée, si cette version est compatible, dans quel ordre certaines
briques doivent démarrer.

**Ce qu'il ignore.** Il ne sait jamais **pourquoi** A dépend de B — cette
raison appartient aux deux services concernés.

### 5.8 Lifecycle Management

**Rôle.** Gérer le cycle de vie technique des services de bout en bout.

**Ce qu'il couvre** : install, register, configure, start, stop, restart,
upgrade, downgrade, rollback, disable, remove — ainsi que la gestion des
versions et des états associés. Ce mécanisme devient central si EGEN doit
fonctionner comme une plateforme où l'on peut installer ou retirer des
briques indépendamment les unes des autres.

### 5.9 Observability

**Rôle.** Fournir les mécanismes techniques communs permettant de savoir
si l'écosystème fonctionne.

**Ce qu'il standardise** : logs, métriques, traces, healthchecks,
diagnostics, événements système, monitoring, correlation IDs.

**Ce qu'il ignore.** Aucune analyse métier. Le Kernel sait dire
« service X → erreur 503 », jamais « le processus d'inscription d'un
étudiant a échoué » — cette seconde information appartient entièrement au
service concerné.

### 5.10 Event Infrastructure

**Rôle.** Fournir l'infrastructure événementielle de l'écosystème.

**Ce qu'elle fournit** : event bus, brokers, topics, subscriptions,
routing, delivery, retry, dead-letter, persistance éventuelle, ordering,
consumer groups.

**Ce qu'elle ignore.** Les événements appartiennent aux services qui les
émettent. Le Kernel ne définit jamais leur vocabulaire métier — il fournit
uniquement l'infrastructure nécessaire pour les transporter.

### 5.11 API / Contract Infrastructure

**Rôle.** Fournir les mécanismes permettant aux services de déclarer et
d'exposer leurs interfaces.

**Ce qu'elle gère** : découverte d'API, versionnement, validation de
contrats, routing, compatibilité, documentation technique, génération
éventuelle de clients, politiques techniques d'appel.

**Ce qu'elle ignore.** Elle ne définit jamais les API métier elles-mêmes.
Le service Éducation définit son API Éducation, le service GED définit son
API GED ; le Kernel fournit uniquement le cadre dans lequel ces API
existent.

### 5.12 Deployment Adapter

**Rôle.** Faire le pont entre le Kernel et l'infrastructure de
déploiement réelle, sans que le Kernel ne soit lui-même responsable
directement d'une technologie de déploiement donnée.

Cette responsabilité passe par une couche d'abstraction — un
**Deployment Provider / Runtime Adapter** — qui permet de brancher Docker,
Podman, Kubernetes, systemd, une VM, un runtime cloud, ou tout autre
orchestrateur. Le Kernel exprime une intention (« je veux une instance de
ce service avec cette configuration ») ; l'adaptateur la traduit vers
l'environnement réel. Ainsi, EGEN n'est jamais prisonnier d'une
technologie de déploiement particulière.

## 6. Le mode d'extension embarqué — modules et plugins

### 6.1 Pourquoi ce second mode existe

Tout besoin ne justifie pas de concevoir, déployer et opérer un service
séparé. Pour les fonctionnalités personnelles ou les extensions de portée
plus modeste, le Kernel conserve un second mode d'extension : un système
de modules/plugins chargés directement dans le process du Kernel (ou dans
un process isolé qui lui est rattaché), à travers un mécanisme de
manifeste, de points d'extension et de cycle de vie dédié. Ce mode reste
disponible sans être le mode par défaut de l'écosystème.

### 6.2 Service vs Module — la frontière exacte

| | **Service indépendant** | **Module/plugin embarqué** |
|---|---|---|
| Déploiement | Séparé, autonome | Chargé dans (ou à côté de) le process du Kernel |
| Découverte | Via Service Registry / Discovery | Via manifeste local, chargé au démarrage ou à la demande |
| Communication | Via la Communication Fabric | Appel direct, en mémoire, à un point d'extension |
| Portée typique | Tout domaine métier, toute équipe, tout cycle de vie propre | Fonctionnalité ciblée, usage personnel ou interne |
| Isolation | Totale (process, mémoire, échec) | Par classloader ou par process séparé, selon le besoin |

Le choix entre les deux n'est jamais une question de facilité, mais de
portée réelle du besoin : un domaine métier durable et partagé devient un
service ; une extension ponctuelle ou personnelle reste un module.

### 6.3 La règle de neutralité s'applique aussi aux modules

Le mode d'extension embarqué ne dispense jamais de la règle du chapitre 3.
Un module chargé dans le Kernel ne fait pas exception : il peut porter sa
propre logique métier, mais le Kernel qui l'accueille continue d'ignorer
tout de cette logique. Le mécanisme de chargement (manifeste, point
d'extension, cycle de vie) reste générique, quel que soit le contenu du
module qui l'utilise.

## 7. Responsabilités exactes du Kernel — résumé

En dernière analyse, le Kernel EGEN se résume à ces responsabilités, et à
elles seules :

1. Monter et exécuter les services.
2. Enregistrer et découvrir les services.
3. Gérer leur cycle de vie.
4. Gérer leur configuration technique.
5. Gérer leurs dépendances techniques.
6. Permettre leur communication.
7. Transporter leurs événements et messages.
8. Exécuter/orchestrer les workflows qui lui sont déclarés.
9. Gérer les contrats et interfaces techniques.
10. Fournir l'observabilité et le diagnostic de la plateforme.
11. S'interfacer avec l'infrastructure de déploiement.

Chacune de ces responsabilités reste générique par construction. C'est
cette généricité, tenue sans exception, qui rend crédible l'objectif de la
Vision (chapitre 2) : que ce Kernel serve un jour de socle à un tout autre
écosystème métier sans qu'une seule ligne de sa propre implémentation
n'ait à changer.

## 8. Statut de ce document

Cette charte fait foi pour l'architecture du Kernel EGEN. Elle est amenée à
s'affiner à mesure que l'implémentation avance, mais toute évolution
future doit être évaluée à l'aune du principe directeur du chapitre 3 —
jamais l'inverse.
