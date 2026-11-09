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
- **RBAC de l'API de contrôle EGEN** : scoping par équipe/domaine (voir
  [13](13-api-et-contrats.md)).
- **Workflows : mutations réservées aux administrateurs.** Une
  `WorkflowDefinition` orchestre des appels vers des services de
  *plusieurs* équipes et ne porte aucune équipe propriétaire dans son modèle
  ([05](05-modele-de-domaine.md)) : il n'y a donc rien à comparer au claim
  d'équipe du jeton. Tant qu'un modèle de propriété n'existe pas, la seule
  politique sûre par défaut est de refuser toute mutation (`POST` de
  définition, démarrage, `pause`/`resume`/`terminate`) aux non-administrateurs ;
  la lecture reste ouverte à tout appelant authentifié. À revoir si les
  workflows reçoivent un jour un propriétaire explicite.
- **Publication d'événements : un appelant ne publie que ce qu'un manifeste
  annonce.** `POST /api/v1/events/publish` ([13](13-api-et-contrats.md))
  exige que le `type` figure dans `events.publishes` d'au moins un service
  déclaré, et que l'appelant soit administrateur ou porte l'équipe
  propriétaire de l'un de ces services (même règle que pour toute action sur
  un service déjà enregistré ; un service sans équipe assignée reste ouvert à
  tout appelant authentifié, note de migration ci-dessus). Sinon `403`.

## Utilisation par la Plateforme

- Deux équipes `metadata.team` sont réservées à la Plateforme : `platform` (ses services, déclarés par la pipeline) et `platform-operator` (**toutes** les instances d'applications, déclarées par `platform-operator-service`). Voir [22](22-frontiere-avec-la-plateforme.md#226--les-deux-équipes-kernel-de-la-plateforme).
- **Seul** `platform-operator-service` détient un jeton de gestion du Kernel (client *client credentials* d'un fournisseur d'identité OIDC, claim `team = platform-operator`) : moindre privilège, aucun rôle administrateur (décision D14, [22](22-frontiere-avec-la-plateforme.md#228--décisions-qui-touchent-le-kernel)). Le catalogue, le portail, la passerelle de domaine et les connecteurs n'en ont aucun.
- Les équipes humaines **ne voient pas** les instances d'applications dans le Kernel : elles passent par la Plateforme.
- Le RBAC du Kernel (équipe + administrateur) reste volontairement **grossier** : il n'exprime ni hiérarchie de cellules ni droits métier. Ceux-ci relèvent de **SpiceDB** (moteur de droits de la Plateforme) et du contrôle d'usage du catalogue, documentés dans le dépôt `Civitas-EGEN-Business`, `docs/plateforme/06-identite-et-droits.md`.
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
  ou égal à la valeur par défaut du développement : le démarrage échoue, avec un
  message qui nomme la variable d'environnement à définir
  ([24](24-revue-de-securite-externe.md), constat K6).
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
