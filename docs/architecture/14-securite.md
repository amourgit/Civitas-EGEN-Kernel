# 14 — Sécurité — modèle transverse

## Identité de service

Chaque service enregistré obtient une **identité cryptographique**
(certificat court-terme, idéalement au format proche de SPIFFE/SPIFFE-ID
— `spiffe://civitas.africa/ns/content-platform/sa/news-service` — même si
l'implémentation V1 délègue la PKI à Vault plutôt que de déployer un
système SPIFFE/SPIRE complet, pour rester pragmatique). Cette identité
sert de base au **mTLS** entre services (porté par la Communication
Fabric, voir [12](12-communication-fabric.md)) et à l'authentification des
adapters vers Nomad/Consul (tokens ACL scoped, jamais le token root).

## Principe du moindre privilège, partout

- **Token Nomad de l'adapter** : politique ACL limitée aux capacités
  strictement nécessaires (`submit-job`, `read-job`, `dispatch-job`),
  scoped par namespace.
- **Token Consul de l'adapter** : `service:write` limité aux préfixes de
  noms de service gérés par EGEN.
- **Accès aux secrets (Vault)** : chaque service n'a accès qu'à son propre
  chemin (`secret/data/<service-name>/*`), jamais un accès transverse.
- **RBAC de l'API de contrôle EGEN : une règle unique, volontairement pauvre** (décision de Samuel, 10 novembre 2026). Elle tient en quatre phrases :
  1. Tout appel exige un jeton valide.
  2. **Lire** est ouvert à tout appelant authentifié : l'état et le manifeste d'un service, la liste des services, la configuration, la découverte, le graphe de dépendances, les workflows.
  3. **Modifier** est réservé au **rôle administrateur** (`egen.security.admin-role`, `egen-admin` par défaut) : déclarer ou redéclarer un service (y compris pour le relancer), l'arrêter, publier sa configuration, publier un événement, créer ou conduire un workflow. Un appelant sans ce rôle reçoit `403`.
  4. **`metadata.team` est purement informatif** : le Kernel le conserve et le restitue pour la **traçabilité** (savoir qui est responsable d'un service) et ne le lit **jamais** pour décider. Aucun claim d'équipe du jeton n'est lu non plus : il n'y a ni autorisation par équipe, ni filtre par équipe, ni notion de propriété.

  Le droit est vérifié **avant** toute lecture du corps de la requête et avant de chercher le service : un appelant non administrateur reçoit `403`, y compris pour un identifiant inconnu (jamais `404`, qui apprendrait à un appelant non autorisé quels services existent). **Ce que cette règle ne protège pas** : le Kernel ne sépare pas une équipe d'une autre. Tout appelant qui porte le rôle administrateur peut modifier n'importe quel service. Ces appelants doivent donc être des appelants de confiance (la pipeline de déploiement, l'opérateur de la Plateforme) ; les droits fins (qui, dans une organisation, peut demander quoi) relèvent d'un système **extérieur** au Kernel ([21](21-systeme-agents-et-gateway.md), 21.5, et SpiceDB pour la Plateforme).
- **Workflows.** Une `WorkflowDefinition` orchestre des appels vers des services de *plusieurs* équipes et ne porte aucune équipe dans son modèle ([05](05-modele-de-domaine.md)) ; leur lecture est ouverte à tout appelant authentifié, leurs mutations suivent la règle ci-dessus (administrateur). À revoir si les workflows reçoivent un jour un propriétaire explicite.
- **Publication d'événements : un appelant ne publie que ce qu'un manifeste annonce.** `POST /api/v1/events/publish` ([13](13-api-et-contrats.md)) exige que l'appelant soit administrateur **et** que le `type` figure dans `events.publishes` d'au moins un service déclaré. Sinon `403`.

## Utilisation par la Plateforme

- Deux valeurs de `metadata.team` sont réservées à la Plateforme **par convention de traçabilité** : `platform` (ses services, déclarés par la pipeline) et `platform-operator` (**toutes** les instances d'applications, déclarées par `platform-operator-service`). Le Kernel les traite comme n'importe quelle autre valeur : il ne les lit pas pour décider. Voir [22](22-frontiere-avec-la-plateforme.md#226--les-deux-équipes-kernel-de-la-plateforme).
- Comme **modifier** exige le rôle administrateur, `platform-operator-service` et la pipeline de déploiement de la Plateforme portent ce rôle (décision D14, [22](22-frontiere-avec-la-plateforme.md#228--décisions-qui-touchent-le-kernel)). Le catalogue, le portail, la passerelle de domaine et les connecteurs n'ont **aucun** jeton de gestion du Kernel. *Conséquence de la décision du 10 novembre 2026* : le Kernel ne distingue plus les droits de `platform-operator-service` de ceux d'un autre administrateur (le « moindre privilège » par équipe n'existe plus au niveau du Kernel) ; il revient à l'émetteur des jetons de n'en donner le rôle administrateur qu'à ces deux appelants.
- Les équipes humaines **ne voient pas** les instances d'applications dans le Kernel : elles passent par la Plateforme.
- Le RBAC du Kernel (administrateur ou non) reste volontairement **grossier** : il n'exprime ni équipe, ni hiérarchie de cellules, ni droits métier. Ceux-ci relèvent de **SpiceDB** (moteur de droits de la Plateforme) et du contrôle d'usage du catalogue, documentés dans le dépôt `Civitas-EGEN-Business`, `docs/plateforme/06-identite-et-droits.md`.

## Ce que le Kernel ne fait jamais en matière de sécurité

- Il ne stocke jamais un secret **de service** en clair (ni en base, ni en log, ni
  en cache mémoire persistant au-delà de la durée d'un appel). Un secret de
  service est une valeur que les services déployés lisent (un mot de passe de
  base de leur application, par exemple) : le Kernel ne la lit jamais, il
  vérifie seulement qu'elle existe dans Vault. **Une exception, écrite
  en fin de document** ([constat K7](#exception--les-identifiants-du-kernel-lui-même-constat-k7)).
- Il ne rejoue jamais la réponse d'un appelant à un autre : la clé de cache
  d'idempotence inclut l'appelant ([13](13-api-et-contrats.md#sécurité-de-lapi)).
- Il ne démarre jamais en production avec un mot de passe de base de données absent
  ou égal à la valeur par défaut du développement : le démarrage échoue
  ([24](24-revue-de-securite-externe.md), constat K6). *Observé sur le jar de
  production le 10 novembre 2026* : si `EGEN_REGISTRY_DB_PASSWORD` vaut `egen`, le
  Kernel affiche « Démarrage refusé en production … Définir EGEN_REGISTRY_DB_PASSWORD
  … » ; si elle est **absente**, c'est Quarkus qui refuse avant le Kernel, avec un
  message générique qui nomme la propriété `quarkus.datasource.password` (« Failed to
  load config value … ») et non la variable d'environnement.
- Il ne lit jamais un document YAML venu de l'extérieur sans limites explicites de
  taille, d'imbrication, d'alias et de clés dupliquées, ni un corps de requête de
  plus de 1 Mio ([13](13-api-et-contrats.md), « Limites d'entrée » ; constat K10
  de [24](24-revue-de-securite-externe.md)).
- Il ne recopie jamais sans borne le corps d'une réponse d'erreur d'un système
  externe (Nomad, Kubernetes, Consul, Vault) dans un message d'erreur : le corps
  est conservé, mais limité à 500 caractères
  ([07](07-ports-et-adapters.md#messages-derreur-des-adapters-http), constat K8
  de [24](24-revue-de-securite-externe.md)).
- Il ne délivre jamais un token de portée plus large que celle
  strictement requise par l'opération demandée.
- Il ne fait jamais confiance implicitement à un appel interne juste parce
  qu'il vient du réseau interne (« zero trust » par défaut, même en
  interne — cohérent avec les cas d'usage mixtes VM/conteneurs/edge
  possibles pour Consul).

### Exception : les identifiants du Kernel lui-même (constat K7)

*À quoi sert cette sous-section* : dire exactement quels identifiants le Kernel garde en mémoire, combien de temps, et ce qui limite le risque. *Ce qu'elle ne couvre pas* : les secrets des services déployés (voir ci-dessus).

Pour parler à ses propres systèmes, le Kernel a besoin de cinq identifiants. Il les lit dans sa configuration **une seule fois**, à la création de l'adapter concerné au démarrage, puis les **garde en mémoire du processus pendant toute sa vie** :

| Identifiant | Propriété de configuration | Où il est gardé |
|---|---|---|
| Jeton Nomad | `egen.nomad.token` | `NomadDeploymentAdapter` |
| Jeton Kubernetes | `egen.kubernetes.token` | `KubernetesConfig`, utilisé par `KubernetesDeploymentAdapter` |
| Jeton Consul | `egen.consul.token` | `ConsulDiscoveryAdapter` et `ConsulConfigurationAdapter` (le même jeton, deux adapters) |
| Jeton Vault | `egen.vault.token` | `VaultSecretsAdapter` |
| Mot de passe PostgreSQL | `quarkus.datasource.password` | le pool de connexions du Kernel |

Ce qui limite le risque, vérifié dans le code :

- Une recherche textuelle dans le code des adapters (9 novembre 2026) ne trouve aucune ligne de journal qui mentionne un jeton ou un mot de passe (une recherche textuelle peut rater une forme d'appel : ce n'est pas une preuve). `KubernetesConfig.toString()` masque le jeton (`***`). Pour l'adapter Kubernetes, le test `apiRejectionBecomesADeploymentExceptionWithTheStatus` vérifie que le jeton n'apparaît pas dans un message d'erreur ; les autres adapters n'ont pas ce test.
- En production, le mot de passe PostgreSQL n'a pas de valeur par défaut et le démarrage échoue s'il manque ([24](24-revue-de-securite-externe.md), constat K6).

Ce qui **n'est pas** couvert : un identifiant n'est pas relu après le démarrage. Changer un jeton (rotation) exige donc de **redémarrer** le Kernel. Un accès à la mémoire du processus (par exemple une copie de la mémoire) révèle ces cinq identifiants. Leur portée dépend des droits que l'opérateur a donnés à chacun côté Nomad, Kubernetes, Consul, Vault et PostgreSQL : le Kernel ne la vérifie pas ([24](24-revue-de-securite-externe.md), tableau 3).

## Jetons attendus par le Kernel (Keycloak)

*À quoi sert cette section* : dire ce que le Kernel lit dans un jeton, et comment Keycloak doit être configuré pour que le rôle `egen-admin` soit reconnu. *Ce qu'elle ne couvre pas* : la création d'un realm de production (utilisateurs, politique de mots de passe, durée de vie des jetons).

**Ce que le Kernel vérifie** à chaque appel : la signature (algorithme RS256) avec les clés publiées à l'adresse `EGEN_OIDC_JWKS_URL` ; que l'émetteur `iss` est exactement la valeur de `EGEN_OIDC_ISSUER_URL` ; que le jeton n'est pas expiré (`exp`). Il **ne vérifie pas l'audience** `aud` ([24](24-revue-de-securite-externe.md), constat K2).

**Où il lit le rôle** : dans la claim **`groups`**, une liste de textes (valeur par défaut de MicroProfile JWT ; `egen-bootstrap` n'en définit pas d'autre). Un appelant est administrateur si cette liste contient la valeur de `egen.security.admin-role` (`egen-admin` par défaut).

**Ce que Keycloak met par défaut** : le rôle d'un realm figure dans `realm_access.roles`, **pas** dans `groups`. Sans réglage, un client qui porte le rôle `egen-admin` serait vu par le Kernel comme un simple appelant : `403` sur toute modification. Il faut donc, sur chaque client qui appelle le Kernel (ou dans un *client scope* partagé), un *mapper* de type **User Realm Role** (`oidc-usermodel-realm-role-mapper`) avec : *Token Claim Name* = `groups`, *Multivalued* = activé, *Add to access token* = activé, *Claim JSON Type* = `String`.

**Observé le 11 novembre 2026** (Keycloak 26.8.2 lancé dans le bac à sable de Claude, **pas contre le Kernel** : le jar n'y existe pas) :

| Client (compte de service) | Rôle `egen-admin` | `groups` dans le jeton | `realm_access.roles` |
|---|---|---|---|
| `platform-operator-service`, avec le mapper | oui | `['egen-admin']` | `['egen-admin']` |
| `deploy-pipeline`, avec le mapper | oui | `['egen-admin']` | `['egen-admin']` |
| `catalog-service`, avec le mapper | non | absente | absente |
| client sans le mapper | oui | **absente** | `['default-roles-egen', 'offline_access', 'egen-admin', 'uma_authorization']` |

Le jeton porte aussi `iss`, `azp`, `sub`, `jti`, `iat`, `exp`, `preferred_username`, `typ` et `scope` ; il ne porte **pas** `aud` (conséquence pour K2 : une vérification d'audience exigerait un autre mapper). Les clés publiées contiennent une clé de signature RS256 et une clé de chiffrement RSA-OAEP ; seule la première signe les jetons. Le fichier [`deploy/keycloak/egen-realm-test.json`](../../deploy/keycloak/egen-realm-test.json) crée ce realm d'essai (secrets de test : jamais en production).

**Qui porte le rôle** se vérifie dans Keycloak : voir [23](23-runbooks-operateur.md), « Vérifier qui porte le rôle egen-admin ».

