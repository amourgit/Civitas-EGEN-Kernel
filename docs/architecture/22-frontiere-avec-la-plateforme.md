# 22 — Frontière entre le Kernel et la Plateforme

## À quoi sert ce document

Il fixe **ce que le Kernel offre à la Plateforme** et **ce qu'il ne fait jamais**. Il est la seule porte d'entrée du Kernel vers la documentation de la Plateforme.

Trois mots à connaître d'emblée :

- **Kernel** : le programme documenté dans les documents 01 à 21. Il lance les services, surveille leur santé, les relance, dit où ils sont et transporte leurs messages. Il ne connaît aucun concept métier.
- **Plateforme** : l'ensemble des services qui apportent le sens (organisation, catalogue d'outils, droits, passerelle, connecteurs, configuration, portail). Ce sont des services ordinaires, déclarés au Kernel comme n'importe quel autre.
- **Service** : un programme que le Kernel sait lancer, décrit par un fichier appelé `ServiceManifest` (voir [06](06-service-manifest.md)).

## Ce qu'il ne couvre pas

- Le fonctionnement interne de la Plateforme (cellules, catalogue, droits, passerelle, connecteurs, configuration, portail, plan par phase) : il est documenté dans le dépôt `Civitas-EGEN-Business`, dossier `docs/plateforme/` (voir 22.9).
- Le système d'agents et l'Edge Gateway : voir [21](21-systeme-agents-et-gateway.md).
- Les applications métier.

## 22.1 — Les trois étages et la règle de dépendance

| Étage | Contenu | Ce qu'il sait faire |
|---|---|---|
| **1. Le Kernel** | EGEN Kernel | Lancer, surveiller, relancer des services ; dire où ils sont ; transporter des messages |
| **2. La Plateforme** | `cell-registry-service`, `catalog-service`, `platform-operator-service`, `config-service`, `domain-gateway`, portail, connecteurs | Donner du sens : qui appartient à quelle organisation, quel outil est affecté à qui, qui a le droit de quoi |
| **3. Les applications métier** | Radicale, Nextcloud Calendar, `civitas-news`, … | Rendre le service final à l'utilisateur |

**Règle de dépendance.** La Plateforme appelle le Kernel par son **API publique** ([13](13-api-et-contrats.md)). Le Kernel n'importe **jamais** un concept de la Plateforme. Aucun type interne du Kernel n'est utilisé par la Plateforme.

**Exemple.** L'Université X veut donner Radicale (un logiciel d'agenda) à son Service RH. Marie, membre du Service RH, ouvrira cet agenda. Du point de vue du Kernel, il se passe une seule chose : un service appelé `app-radicale-<identifiant>` est déclaré, lancé et surveillé. Le Kernel ignore l'Université X, le Service RH et Marie.

## 22.2 — Répartition des responsabilités

| Responsabilité | Kernel | Plateforme |
|---|---|---|
| Déclarer, déployer, mettre à jour, arrêter un conteneur ou un binaire | **Oui** (Nomad, par un adapter) | Non : elle le demande au Kernel |
| Dire où se trouvent les instances saines d'un service | **Oui** (Consul, par un adapter) | Consulte `GET /api/v1/discover/{nom}` |
| Ordonner les démarrages selon les dépendances techniques | **Oui** | Non |
| Transporter des événements techniques | **Oui** (NATS JetStream, par un adapter) | Publie et consomme des événements ordinaires |
| Orchestrer une suite d'étapes avec compensation | **Oui** (moteur de workflow) | Définit ses workflows, ne réimplémente pas le moteur |
| Publier la configuration technique clé/valeur d'un service | **Oui** (`ConfigurationPort`, API de publication, 22.5) | Calcule les valeurs ; ne les écrit jamais ailleurs que par cette API |
| Référencer un secret | **Oui** (référence seulement, Vault) | Fournit les chemins ; ne lit jamais la valeur à travers le Kernel |
| Savoir ce qu'est une cellule, une organisation, un tenant, un catalogue, une affectation, un utilisateur, un domaine fonctionnel | **Jamais** | **Oui** |

Un **tenant** est une organisation cliente isolée des autres (par exemple l'Université X). Une **cellule** est un élément de la structure d'une organisation (voir la documentation de la Plateforme).

## 22.3 — Adapter du Kernel et connecteur de la Plateforme

Ces deux mots désignent des choses différentes et ne se remplacent jamais.

| | **Adapter** (Kernel) | **Connecteur** (Plateforme) |
|---|---|---|
| Parle à | Un outil d'infrastructure (Nomad, Consul, NATS, Vault) | Une application métier (Radicale, Nextcloud Calendar, …) |
| Nombre | Peu | Nombreux |
| Fréquence de changement | Rare | Fréquente |
| Où il vit | Dans le Kernel (modules `egen-adapters/*`) | Hors du Kernel : un petit service séparé déclaré par un `ServiceManifest` |

**Les connecteurs ne sont jamais dans le Kernel.** Six raisons :

1. **Garde-fou n°5** ([02](02-principes-fondamentaux.md)) : ajouter un logiciel ne doit pas obliger à modifier le Kernel.
2. Le Kernel est le seul point qui maintient tout en vie et il tourne en **un exemplaire** : un défaut dans un connecteur ne doit pas l'arrêter.
3. Les **secrets des applications** ne doivent pas cohabiter avec les clés du Kernel.
4. La **règle d'architecture A4** (22.7) refuse tout concept métier dans le Kernel.
5. **Poids et conflits de dépendances** : chaque connecteur embarque ses propres bibliothèques.
6. **Rythmes de vie et mise à l'échelle différents** : un connecteur évolue et se multiplie bien plus vite que le Kernel.

## 22.4 — Les labels réservés

Un **label** est une paire clé/valeur de texte attachée à un service dans son `ServiceManifest`. Le Kernel les conserve et les restitue sans jamais les interpréter. Le préfixe `platform.civitas.africa/` est réservé à la Plateforme. Les clés et leur sens sont définis dans [06](06-service-manifest.md#labels-réservés-posés-par-la-plateforme). Seul `platform-operator-service` les pose.

## 22.5 — L'API de publication de configuration (A3)

Le Kernel dispose d'un port de configuration (`ConfigurationPort`) mais d'aucune API qui le rende utilisable par ses clients. L'évolution **A3** ajoute `PUT /api/v1/services/{id}/configuration`. Le contrat complet (requête, validation, réponse, sécurité) est la **source unique** de [13](13-api-et-contrats.md#publication-de-configuration--put-apiv1servicesidconfiguration). Son état d'avancement est suivi dans [19](19-feuille-de-route.md).

Le Kernel transporte des paires clé/valeur typées par le manifeste ; il ne fusionne **aucune** configuration par organisation, cellule ou utilisateur : cette fusion appartient à `config-service` (Plateforme).

## 22.6 — Les deux équipes Kernel de la Plateforme

L'équipe d'un service est le champ `metadata.team` de son manifeste ([14](14-securite.md)). Deux valeurs sont réservées :

| Équipe | Services concernés | Qui les déclare |
|---|---|---|
| `platform` | Les services de la Plateforme : `cell-registry-service`, `catalog-service`, `platform-operator-service`, `config-service`, `domain-gateway`, connecteurs | La pipeline de déploiement |
| `platform-operator` | **Toutes** les instances d'applications (Radicale, Nextcloud Calendar, …) | `platform-operator-service`, et lui seul |

`platform-operator-service` est le **seul** service de la Plateforme à détenir un jeton de gestion du Kernel (décision D14, 22.8). Les équipes humaines ne voient pas les instances d'applications dans le Kernel : elles passent par le portail et le catalogue de la Plateforme.

## 22.7 — La règle d'architecture A4

**Règle A4** : aucun type, paquet ni champ de `egen-domain`, `egen-application`, `egen-api` ni `egen-bootstrap` dont le nom correspond (sans tenir compte de la casse) à `cell`, `cellule`, `tenant`, `organization`, `organisation`, `assignment`, `affectation`, `catalog`, `catalogue` ou `domainContract` n'est accepté. Les modules `egen-adapters/*` sont exclus (l'API « catalog » de Consul y est légitime). Le test vérifie aussi qu'une violation volontaire échoue ([17](17-strategie-de-tests.md#niveau-4--tests-darchitecture-fitness-functions)).

**Scénario de niveau 5 (A5).** Dans le test de bout en bout, un service « fixture de plateforme » est déclaré **uniquement par l'API publique** avec `team: platform-operator`, les labels réservés et un bloc `configuration` ; sa configuration est publiée par A3 puis relue ; il converge vers `RUNNING` ; ses labels sont restitués intacts par `GET /api/v1/services/{id}`. Cela vérifie le garde-fou n°5 pour la Plateforme.

## 22.8 — Décisions qui touchent le Kernel

| N° | Décision |
|---|---|
| **D01** | Le Kernel ne contient aucun concept de cellule, organisation, tenant, catalogue, affectation, utilisateur ou domaine fonctionnel. |
| **D14** | Seul `platform-operator-service` détient des identifiants de gestion du Kernel (équipe `platform-operator`). Le catalogue, le portail, la passerelle et les connecteurs n'en ont aucun. |
| **D15** | Une instance d'application est soit **MANAGED** (déployée par le Kernel), soit **EXTERNAL** (déjà exploitée hors du Kernel ; le Kernel ne la gère pas en V1, voir 22.9). |
| **D20** | Aucune évolution anticipée du Kernel : seules les évolutions de la phase A de la Plateforme (A2 à A5, suivies dans [19](19-feuille-de-route.md)) sont planifiées ; les autres ont un déclencheur écrit. |

Le registre complet des décisions de la Plateforme est dans le dépôt `Civitas-EGEN-Business`, `docs/plateforme/02-registre-des-decisions.md`.

## 22.9 — Limites actuelles du Kernel

Chaque constat a été vérifié dans le code du dépôt (branche `main`, 29 octobre 2026).

| Constat | Conséquence pour la Plateforme | Traitement |
|---|---|---|
| Les `labels` et `annotations` sont stockés et restitués, sans filtre de liste | Aucune : l'opérateur connaît ses `ServiceId` et filtre côté client la liste déjà restreinte à son équipe | Aucune évolution (D20) |
| Le port `ConfigurationPort` n'est exposé par **aucune** API | La configuration d'une instance ne peut pas être publiée | Évolution **A3** (22.5) |
| `runtime.type` (`container`, `binary`, `external`, `vm`) est conservé mais **non différencié** : tout déploiement passe par le Deployment Adapter | Une application déjà exploitée hors du Kernel n'est pas gérée par lui | D15 ; déclencheur **A-X1** ([19](19-feuille-de-route.md)) |
| L'adapter Nomad **ne gère pas** les namespaces Nomad ; `TargetEnvironment` est un nom d'environnement, pas une frontière de tenant | Aucune isolation d'infrastructure par tenant via le Kernel | Isolation par les données ou par instance dédiée ; déclencheur **A-X2** |
| Le RBAC de l'API est `metadata.team` (une chaîne) plus le rôle administrateur | Il ne peut pas représenter une hiérarchie de cellules | Volontaire : l'autorisation métier n'est pas dans le Kernel |
| La clé d'idempotence et les liens de traces sont gardés en mémoire du processus | Un seul exemplaire du Kernel | Voir la phase proposée P0 dans [19](19-feuille-de-route.md) |

## 22.10 — Où lire la suite

Pour tout ce qui concerne la Plateforme, lire dans le dépôt `Civitas-EGEN-Business` :

| Sujet | Chemin |
|---|---|
| Sommaire et ordre de lecture | `docs/plateforme/README.md` |
| Vision, trois étages, frontière | `docs/plateforme/01-vision-et-frontiere.md` |
| Registre des décisions | `docs/plateforme/02-registre-des-decisions.md` |
| Vue d'ensemble des systèmes et des flux | `docs/plateforme/03-vue-d-ensemble-des-systemes.md` |
| Cellules, catalogue, identité et droits | `docs/plateforme/04-cellules.md`, `05-catalogue-instances-affectations.md`, `06-identite-et-droits.md` |
| Passerelle de domaine, connecteurs | `docs/plateforme/07-contrats-de-domaine-et-passerelle.md`, `08-connecteurs.md` |
| Configuration, portail | `docs/plateforme/09-configuration-multi-niveaux.md`, `10-portail.md` |
| Plan par phase et suivi des phases B à H | `docs/plateforme/11-plan-par-phase.md` |
