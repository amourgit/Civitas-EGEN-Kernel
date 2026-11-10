# 24 — Dossier de cadrage de la revue de sécurité externe

**À quoi sert ce document.** Il est remis au relecteur externe de la Phase 6 ([19](19-feuille-de-route.md)). Il dit **ce qu'il faut examiner** (l'API de contrôle et la gestion des secrets), **où se trouve chaque chose** dans le code, **ce que le Kernel promet** ([14](14-securite.md)) et **ce que nous avons déjà trouvé nous-mêmes**.

**Comment ce dossier a été établi.** Par relecture du code au 5 novembre 2026, point par point. **Aucun test d'intrusion n'a été mené.** Les constats ci-dessous sont des lectures de code, certains confirmés par un test de régression, aucun par une attaque réelle : c'est précisément le travail demandé au relecteur.

**Ce que nous demandons.** Une revue ciblée « API de contrôle et gestion des secrets ». En retour : une liste de constats classés par gravité, avec une preuve reproductible pour chacun et une recommandation.

---

## 1. Pour reproduire

| Besoin | Où |
|---|---|
| Construire et lancer tous les tests | `mvn verify` à la racine (Docker requis : les tests de niveau 3 démarrent PostgreSQL, Consul, Vault, NATS, un cluster k3s) |
| Contrat de l'API | `egen-contracts/src/main/resources/openapi/egen-control-api.yaml` |
| Schéma des manifestes | `egen-contracts/src/main/resources/schema/service-manifest.schema.json` |
| Configuration | `egen-bootstrap/src/main/resources/application.properties` (les valeurs `%prod.` sont celles de la production) |
| Règles d'autorisation | `egen-api/.../security/EgenSecurityContext.java` |
| Cache d'idempotence | `egen-api/.../security/IdempotencyKeyFilter.java` |

L'authentification est un jeton JWT vérifié par `quarkus-smallrye-jwt` : l'émetteur et la clé publique viennent de `EGEN_OIDC_ISSUER_URL` et `EGEN_OIDC_JWKS_URL`. Le rôle administrateur et le claim d'équipe se règlent par `egen.security.admin-role` (défaut `egen-admin`) et `egen.security.team-claim` (défaut `team`).

---

## 2. La surface à examiner

### 2.1 Ce qui écoute (API de contrôle)

Une politique HTTP par défaut exige un jeton valide sur **tout** `/api/v1/*`, sauf `/api/v1/health`. Voici chaque point d'accès et le contrôle **supplémentaire** que le code applique.

| Méthode et chemin | Contrôle supplémentaire |
|---|---|
| `POST /api/v1/services` | l'équipe du jeton doit égaler l'équipe du manifeste (ou administrateur) ; **et** propriétaire du service existant (voir constat C1) |
| `GET /api/v1/services` | filtré : un non-administrateur ne voit que les services de son équipe |
| `GET /api/v1/services/{id}` et `…/status` | propriétaire ou administrateur |
| `POST /api/v1/services/{id}/actions/stop` | propriétaire ou administrateur |
| `GET` et `PUT /api/v1/services/{id}/configuration` | propriétaire ou administrateur, vérifié **avant** de lire le corps |
| `POST /api/v1/events/publish` | le `type` doit être annoncé par un manifeste déclaré, et l'appelant propriétaire de l'un d'eux (sinon `403`) |
| `POST /api/v1/workflows` et `…/executions` | administrateur |
| `POST …/executions/{id}/actions/{pause,resume,terminate}` | administrateur (par une méthode commune) |
| `GET /api/v1/workflows/{id}` et `…/executions/{executionId}` | tout appelant authentifié (voir constat K5) |
| `GET /api/v1/dependencies/graph` | **aucun contrôle d'équipe** (voir constat K4) |
| `GET /api/v1/discover/{serviceName}` | **aucun contrôle d'équipe** (voir constat K4) |
| `GET /api/v1/health` | ouvert |

Toutes les mutations exigent aussi un en-tête `Idempotency-Key`.

**Entrée non fiable principale** : un manifeste YAML (`POST /api/v1/services`) et une définition de workflow YAML, lus par `new YAMLMapper()` de Jackson.

### 2.2 Ce que le Kernel appelle (sorties)

| Cible | Identifiant détenu | Propriété de configuration |
|---|---|---|
| Nomad | jeton ACL | `egen.nomad.token` |
| Kubernetes | jeton porteur d'un compte de service, certificat de l'autorité | `egen.kubernetes.token`, `egen.kubernetes.ca-cert-path` |
| Consul | jeton ACL | `egen.consul.token` |
| Vault | jeton | `egen.vault.token` |
| PostgreSQL (Registry) | utilisateur et mot de passe | `quarkus.datasource.username`, `quarkus.datasource.password` |
| NATS | (à confirmer par le relecteur) | `egen.messaging.*` |

---

## 3. Les promesses du document 14, face au code

| Promesse | Ce que le code fait réellement |
|---|---|
| Identité cryptographique de service, mTLS (PKI via Vault) | **Non implémentée.** Le mTLS machine-à-machine est **désactivé par défaut** (deux lignes commentées dans la configuration). L'adapter Vault ne gère que des secrets, aucune PKI. |
| Jeton Nomad au moindre privilège (`submit-job`, `read-job`, `dispatch-job`) | Le Kernel **reçoit** un jeton par configuration. Sa portée dépend de l'ACL créée par l'opérateur : **rien dans le code ne la vérifie ni ne la limite.** |
| Jeton Consul limité aux préfixes gérés | Même remarque. |
| Chaque service n'accède qu'à son chemin Vault | Le Kernel **ne lit jamais une valeur de secret** : il vérifie l'existence d'une référence (`ServiceBindings`). L'isolation des chemins dépend de la politique Vault, à examiner. |
| RBAC par équipe, mutations de workflows réservées aux administrateurs | Appliqué par `EgenSecurityContext` (voir le tableau 2.1). Une faille a été trouvée et corrigée (C1). |
| Un appelant ne publie que ce qu'un manifeste annonce | Appliqué dans `PublishEventUseCaseImpl`, renvoyé en `403`. |
| Jamais de secret en clair (base, journal, cache) | Les **valeurs** de secrets ne sont jamais lues. **Mais** les jetons des adapters (Nomad, Kubernetes, Consul, Vault) et le mot de passe PostgreSQL sont lus depuis la configuration et gardés en mémoire pendant toute la vie du processus : une exception de fait à la formule « au-delà de la durée d'un appel » (constat K7). |
| Zéro confiance implicite, même en interne | La politique `/api/v1/*` authentifié est une défense en profondeur. Mais elle ne couvre que ce préfixe (constat K3). |

---

## 4. Ce que nous avons déjà trouvé

### Corrigés, validés et sur `main`

**C1 — Redéclarer le service d'une autre équipe (gravité élevée).** `Declare` ne comparait le jeton qu'à l'équipe écrite dans le **nouveau** manifeste, pas au propriétaire actuel. Une équipe B non administratrice pouvait envoyer le manifeste de `news-service` (équipe A) avec `team: B`, réécrire son image et ses réplicas, et s'en attribuer la propriété. Aucun test ne couvrait ce cas, car les tests de sécurité existants ne fabriquaient pas de jeton portant un claim d'équipe. *Correction* : `Declare` applique maintenant le contrôle de propriétaire déjà utilisé pour `stop`, `status` et la lecture. *Régression* : `ServiceResourceTeamOwnershipTest` (4 cas, avec un vrai claim `team`).

**C2 — Cache d'idempotence partagé entre appelants (gravité moyenne à élevée).** La clé de cache était `méthode + chemin + valeur de l'en-tête`, sans l'appelant. Deux appelants authentifiés qui envoyaient la même valeur partageaient une entrée : le second recevait la **réponse du premier**, et sa propre requête n'était **jamais exécutée**. Le cache n'avait en plus aucune borne de taille. *Correction* : la clé inclut l'appelant (chaque composant préfixé par sa longueur, pour qu'aucun séparateur ne puisse être forgé) et le cache est borné à 10 000 réponses. *Régression* : `IdempotencyCacheKeyTest` (règle pure) et `ServiceResourceIdempotencyScopeTest` (deux appelants, même clé).

### Connus et ouverts

| N° | Constat | À décider ou à examiner |
|---|---|---|
| K1 | **Un service sans équipe est ouvert à tout appelant authentifié** (note de migration du document 14). Lire, arrêter ou redéclarer un tel service est permis à tous. | Fixer une date de fin de cette tolérance. **Décidé le 9 novembre 2026 par Samuel** : fixer une date après laquelle un `Declare` sans équipe est refusé, **après** avoir contrôlé les services déjà enregistrés. *Reste à fournir* : la date, et la liste des services sans équipe. |
| K2 | **Aucune validation de l'audience** du jeton : `mp.jwt.verify.audiences` n'est pas configuré. Un jeton émis pour un autre service du même émetteur est accepté. | À examiner : le fournisseur d'identité émet-il des audiences distinctes ? **9 novembre 2026** : pas de décision de conception possible tant qu'on ne sait pas si le fournisseur d'identité émet des audiences distinctes ; sans cela, activer la vérification bloquerait tous les appels. *Reste à fournir* : la réponse sur le fournisseur d'identité. |
| K3 | La politique HTTP par défaut ne couvre que `/api/v1/*`. Les chemins que d'éventuelles extensions exposent ailleurs (par exemple `/q/…`) ne sont pas couverts par cette règle. | **Examiné dans le code (9 novembre 2026), pas encore sur un Kernel démarré.** Le Kernel n'embarque que les extensions Quarkus `arc`, `agroal`, `jdbc-postgresql`, `rest-jackson` et `smallrye-jwt` (d'après la documentation de Quarkus, aucune d'elles ne publie de chemin `/q/…` en production : ni santé, ni métriques, ni OpenAPI, ni interface de développement — **à vérifier** sur un Kernel démarré) ; toutes les ressources REST du Kernel (`@Path`) sont sous `/api/v1/`. *À faire par l'opérateur sur le jar de production, avec un jeton valide* : `curl -i http://<kernel>/q/health`, `/q/openapi`, `/q/metrics`, `/q/dev-ui` doivent répondre `404`. Une autre réponse est un constat. *Décision ouverte* : ajouter, ou non, une règle « tout refuser par défaut hors de `/api/v1/` » ; elle changerait le comportement et n'est pas écrite aujourd'hui. |
| K4 | `GET /api/v1/dependencies/graph` et `GET /api/v1/discover/{nom}` ne filtrent pas par équipe : tout appelant authentifié voit **tous** les services et leurs dépendances, et les **adresses et ports** de n'importe quel service. C'est peut-être voulu (la résolution entre services est transverse), mais ce n'est écrit nulle part. | Décision de conception, à consigner. **Décidé le 9 novembre 2026 par Samuel** : `GET /api/v1/discover/{nom}` reste ouvert à tout appelant authentifié (la résolution entre services est transverse) et cela est **écrit** ; `GET /api/v1/dependencies/graph` est **filtré par équipe**, l'administrateur voyant tout. *À faire* : contrat dans [13](13-api-et-contrats.md), puis code et tests. |
| K5 | La lecture des workflows est ouverte à tout appelant authentifié (écrit dans le document 14). | À confirmer. **Décidé le 9 novembre 2026 par Samuel** : confirmé, la lecture des workflows reste ouverte à tout appelant authentifié tant que les workflows n'ont pas de propriétaire ([14](14-securite.md)) ; à revoir le jour où ils en reçoivent un. |
| K6 | **Le mot de passe PostgreSQL a une valeur par défaut** (`egen`) **sans garde `%prod`** : un oubli de variable d'environnement en production laisserait les identifiants par défaut. De même pour le jeton Vault de développement (`%dev` seulement, donc moins grave). | **Corrigé, validé et sur `main`** : surcharge `%prod.` sans valeur par défaut, et garde `ProductionConfigGuard` qui fait échouer le démarrage si la variable manque ou vaut `egen` ; testé par `ProductionConfigurationRulesTest`. Reste à observer sur un vrai jar de production. |
| K7 | Les jetons d'adapters vivent en mémoire pendant tout le processus (voir le tableau 3). | **Rédigé** : l'exception est écrite dans [14](14-securite.md#exception--les-identifiants-du-kernel-lui-même-constat-k7) (les cinq identifiants, où ils sont gardés, ce qui limite le risque, ce qui n'est pas couvert). Aucune décision de conception : reste à décider seulement si une relecture sans redémarrage est un jour nécessaire. |
| K8 | **Messages d'erreur d'adapter exposés** : un échec d'adapter est enregistré dans une Condition lisible par le propriétaire du service, et le message **recopie le corps de la réponse** de l'API Kubernetes, Nomad, Consul ou Vault. Cela peut révéler des détails d'infrastructure (noms de namespaces, droits manquants) à un développeur d'équipe. | **Décidé le 9 novembre 2026 par Samuel : le corps est conservé** (le code d'état seul est moins explicite pour le débogage) **mais borné à 500 caractères**, pour les quatre adapters ; le risque restant est accepté et écrit en [07](07-ports-et-adapters.md#messages-derreur-des-adapters-http). Corrigé sur `fix/k8-messages-adapter-bornes`, en attente de validation. |
| K9 | **Le Deployment Kubernetes généré n'a aucun `securityContext`** : pas de `runAsNonRoot`, pas de `readOnlyRootFilesystem`, pas de profil `seccomp`, pas de capacités retirées. La sécurité des pods repose sur les règles du cluster (Pod Security Admission). L'image vient du manifeste, sans autre contrôle. | **Corrigé en partie, validé et sur `main`** : `seccompProfile: RuntimeDefault`, `automountServiceAccountToken: false`, `allowPrivilegeEscalation: false`. **Reste** `runAsNonRoot`, `readOnlyRootFilesystem` et le retrait des capacités : décision ouverte ([19](19-feuille-de-route.md), point ouvert 6). |
| K10 | **L'analyse YAML n'a aucune limite explicite** : `new YAMLMapper()`, sans plafond d'alias ni de taille. Elle dépend des valeurs par défaut de la bibliothèque et de Quarkus. | **Corrigé, validé et sur `main`** (9 novembre 2026) : une seule classe `YamlMappers` avec limites explicites (256 Ki caractères, 32 niveaux, 10 alias, clés dupliquées refusées) et corps de requête limité à 1 Mio (`413`). Tests : `YamlMappersTest`, `ServiceResourceInputLimitsTest`. |
| K11 | Le cache d'idempotence est en mémoire du processus : il n'est pas partagé entre plusieurs instances du Kernel (déjà documenté comme limite V1). | À traiter avec la haute disponibilité. |

---

## 5. Hors périmètre

La terminaison TLS publique (faite par l'Edge Gateway, en amont du Kernel, [21](21-systeme-agents-et-gateway.md)), le fournisseur d'identité, SpiceDB et les droits métier de la Plateforme, les clusters Nomad et Kubernetes eux-mêmes, et les services déployés par le Kernel.
