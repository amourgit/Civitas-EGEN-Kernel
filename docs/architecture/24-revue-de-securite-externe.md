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

L'authentification est un jeton JWT vérifié par `quarkus-smallrye-jwt` : l'émetteur et la clé publique viennent de `EGEN_OIDC_ISSUER_URL` et `EGEN_OIDC_JWKS_URL`. Le rôle administrateur se règle par `egen.security.admin-role` (défaut `egen-admin`). Aucun claim d'équipe n'est lu : `metadata.team` est informatif (décision de Samuel, 10 novembre 2026, [14](14-securite.md)).

---

## 2. La surface à examiner

### 2.1 Ce qui écoute (API de contrôle)

Une politique HTTP par défaut exige un jeton valide sur **tout** `/api/v1/*`, sauf `/api/v1/health`. Voici chaque point d'accès et le contrôle **supplémentaire** que le code applique.

| Méthode et chemin | Contrôle supplémentaire |
|---|---|
| `POST /api/v1/services` | **administrateur**, vérifié avant de lire le corps |
| `GET /api/v1/services`, `GET /api/v1/services/{id}` et `…/status` | tout appelant authentifié |
| `POST /api/v1/services/{id}/actions/stop` | **administrateur** (`403` aussi pour un identifiant inconnu) |
| `GET /api/v1/services/{id}/configuration` | tout appelant authentifié (`404` si le service est inconnu) |
| `PUT /api/v1/services/{id}/configuration` | **administrateur**, vérifié **avant** de lire le corps (`403` aussi pour un identifiant inconnu) |
| `POST /api/v1/events/publish` | **administrateur** ; et le `type` doit être annoncé par un manifeste déclaré (sinon `403`) |
| `POST /api/v1/workflows` et `…/executions` | administrateur |
| `POST …/executions/{id}/actions/{pause,resume,terminate}` | administrateur (par une méthode commune) |
| `GET /api/v1/workflows/{id}` et `…/executions/{executionId}` | tout appelant authentifié (voir constat K5) |
| `GET /api/v1/dependencies/graph` | tout appelant authentifié (voir constat K4) |
| `GET /api/v1/discover/{serviceName}` | tout appelant authentifié (voir constat K4) |
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
| RBAC à deux niveaux : lire (tout appelant authentifié), modifier (administrateur) ; `metadata.team` informatif | Appliqué par `EgenSecurityContext` (voir le tableau 2.1). Depuis le 10 novembre 2026 le Kernel ne sépare plus une équipe d'une autre (constat K11). Avant cette date, une faille de l'autorisation par équipe avait été trouvée et corrigée (C1). |
| Un appelant ne publie que ce qu'un manifeste annonce | Appliqué dans `PublishEventUseCaseImpl` (le type doit être annoncé), renvoyé en `403` ; la publication est en plus réservée aux administrateurs. |
| Jamais de secret en clair (base, journal, cache) | Les **valeurs** de secrets ne sont jamais lues. **Mais** les jetons des adapters (Nomad, Kubernetes, Consul, Vault) et le mot de passe PostgreSQL sont lus depuis la configuration et gardés en mémoire pendant toute la vie du processus : une exception de fait à la formule « au-delà de la durée d'un appel » (constat K7). |
| Zéro confiance implicite, même en interne | La politique `/api/v1/*` authentifié est une défense en profondeur. Mais elle ne couvre que ce préfixe (constat K3). |

---

## 4. Ce que nous avons déjà trouvé

### Corrigés, validés et sur `main`

**C1 — Redéclarer le service d'une autre équipe (gravité élevée).** `Declare` ne comparait le jeton qu'à l'équipe écrite dans le **nouveau** manifeste, pas au propriétaire actuel. Une équipe B non administratrice pouvait envoyer le manifeste de `news-service` (équipe A) avec `team: B`, réécrire son image et ses réplicas, et s'en attribuer la propriété. Aucun test ne couvrait ce cas, car les tests de sécurité existants ne fabriquaient pas de jeton portant un claim d'équipe. *Correction* : `Declare` applique maintenant le contrôle de propriétaire déjà utilisé pour `stop`, `status` et la lecture. *Régression* : `ServiceResourceTeamOwnershipTest` (4 cas, avec un vrai claim `team`). **Historique** : depuis la décision du 10 novembre 2026 l'autorisation par équipe n'existe plus ; cette faille ne peut plus se produire (modifier exige le rôle administrateur) et ce test est remplacé par `ServiceResourceAdminOnlyTest`.

**C2 — Cache d'idempotence partagé entre appelants (gravité moyenne à élevée).** La clé de cache était `méthode + chemin + valeur de l'en-tête`, sans l'appelant. Deux appelants authentifiés qui envoyaient la même valeur partageaient une entrée : le second recevait la **réponse du premier**, et sa propre requête n'était **jamais exécutée**. Le cache n'avait en plus aucune borne de taille. *Correction* : la clé inclut l'appelant (chaque composant préfixé par sa longueur, pour qu'aucun séparateur ne puisse être forgé) et le cache est borné à 10 000 réponses. *Régression* : `IdempotencyCacheKeyTest` (règle pure) et `ServiceResourceIdempotencyScopeTest` (deux appelants, même clé).

### Connus et ouverts

| N° | Constat | À décider ou à examiner |
|---|---|---|
| K1 | ~~Un service sans équipe est ouvert à tout appelant authentifié.~~ **Sans objet** depuis le 10 novembre 2026 : `metadata.team` est informatif, il n'existe plus de service « sans équipe » au sens du contrôle d'accès, et modifier exige le rôle administrateur pour tout service. La décision du 9 novembre (date de fin de tolérance) est **annulée**. | — |
| K2 | **Aucune validation de l'audience** du jeton : `mp.jwt.verify.audiences` n'est pas configuré. Un jeton émis pour un autre service du même émetteur est accepté. | À examiner : le fournisseur d'identité émet-il des audiences distinctes ? **9 novembre 2026** : pas de décision de conception possible tant qu'on ne sait pas si le fournisseur d'identité émet des audiences distinctes ; sans cela, activer la vérification bloquerait tous les appels. *Reste à fournir* : la réponse sur le fournisseur d'identité. |
| K3 | La politique HTTP par défaut ne couvre que `/api/v1/*`. Les chemins que d'éventuelles extensions exposent ailleurs (par exemple `/q/…`) ne sont pas couverts par cette règle. | **Examiné dans le code (9 novembre 2026) puis observé sur un Kernel de production démarré (10 novembre 2026, V2) : `404` sur `/q/health`, `/q/health/live`, `/q/health/ready`, `/q/openapi`, `/q/metrics`, `/q/dev-ui`, `/q/swagger-ui`, `/q/arc` et `/`, sans jeton.** Le Kernel n'embarque que les extensions Quarkus `arc`, `agroal`, `jdbc-postgresql`, `rest-jackson` et `smallrye-jwt` (aucune d'elles ne publie de chemin `/q/…` en production : ni santé, ni métriques, ni OpenAPI, ni interface de développement — **confirmé** sur un Kernel démarré, V2) ; toutes les ressources REST du Kernel (`@Path`) sont sous `/api/v1/`. *Constat de la vérification* : aucun chemin `/q/…` n'est exposé aujourd'hui. Une extension ajoutée plus tard pourrait en publier ; la règle « tout refuser par défaut » ci-dessous protégerait contre ce risque futur. *Décision ouverte* : ajouter, ou non, une règle « tout refuser par défaut hors de `/api/v1/` » ; elle changerait le comportement et n'est pas écrite aujourd'hui. |
| K4 | `GET /api/v1/dependencies/graph` et `GET /api/v1/discover/{nom}` : tout appelant authentifié voit **tous** les services et leurs dépendances, et les **adresses et ports** de n'importe quel service. | **Décidé le 10 novembre 2026 par Samuel (option A)** : la lecture est ouverte à tout appelant authentifié, y compris la découverte et le graphe ; cela est **écrit** dans [13](13-api-et-contrats.md) et [14](14-securite.md). La décision du 9 novembre (graphe filtré par équipe) est **annulée** : le filtre par équipe n'existe plus. |
| K5 | La lecture des workflows est ouverte à tout appelant authentifié (écrit dans le document 14). | À confirmer. **Décidé le 9 novembre 2026 par Samuel** : confirmé, la lecture des workflows reste ouverte à tout appelant authentifié tant que les workflows n'ont pas de propriétaire ([14](14-securite.md)) ; à revoir le jour où ils en reçoivent un. |
| K6 | **Le mot de passe PostgreSQL a une valeur par défaut** (`egen`) **sans garde `%prod`** : un oubli de variable d'environnement en production laisserait les identifiants par défaut. De même pour le jeton Vault de développement (`%dev` seulement, donc moins grave). | **Corrigé, validé et sur `main`** : surcharge `%prod.` sans valeur par défaut, et garde `ProductionConfigGuard` qui fait échouer le démarrage si la variable manque ou vaut `egen` ; testé par `ProductionConfigurationRulesTest`. **Observé le 10 novembre 2026 sur le jar de production (V1)** : valeur `egen` refusée avec le message prévu (code de sortie 1) ; variable absente refusée par Quarkus lui-même, avec un message générique qui nomme `quarkus.datasource.password` mais pas la variable d'environnement ; une valeur propre n'est pas refusée. |
| K7 | Les jetons d'adapters vivent en mémoire pendant tout le processus (voir le tableau 3). | **Rédigé** : l'exception est écrite dans [14](14-securite.md#exception--les-identifiants-du-kernel-lui-même-constat-k7) (les cinq identifiants, où ils sont gardés, ce qui limite le risque, ce qui n'est pas couvert). Aucune décision de conception : reste à décider seulement si une relecture sans redémarrage est un jour nécessaire. |
| K8 | **Messages d'erreur d'adapter exposés** : un échec d'adapter est enregistré dans une Condition lisible par tout appelant authentifié (la lecture est ouverte), et le message **recopie le corps de la réponse** de l'API Kubernetes, Nomad, Consul ou Vault. Cela peut révéler des détails d'infrastructure (noms de namespaces, droits manquants) à un développeur d'équipe. | **Décidé le 9 novembre 2026 par Samuel : le corps est conservé** (le code d'état seul est moins explicite pour le débogage) **mais borné à 500 caractères**, pour les quatre adapters ; le risque restant est accepté et écrit en [07](07-ports-et-adapters.md#messages-derreur-des-adapters-http). Corrigé sur `fix/k8-messages-adapter-bornes`, en attente de validation. |
| K9 | **Le Deployment Kubernetes généré n'a aucun `securityContext`** : pas de `runAsNonRoot`, pas de `readOnlyRootFilesystem`, pas de profil `seccomp`, pas de capacités retirées. La sécurité des pods repose sur les règles du cluster (Pod Security Admission). L'image vient du manifeste, sans autre contrôle. | **Corrigé en partie, validé et sur `main`** : `seccompProfile: RuntimeDefault`, `automountServiceAccountToken: false`, `allowPrivilegeEscalation: false`. **Reste** `runAsNonRoot`, `readOnlyRootFilesystem` et le retrait des capacités : décision ouverte ([19](19-feuille-de-route.md), point ouvert 6). |
| K10 | **L'analyse YAML n'a aucune limite explicite** : `new YAMLMapper()`, sans plafond d'alias ni de taille. Elle dépend des valeurs par défaut de la bibliothèque et de Quarkus. | **Corrigé, validé et sur `main`** (9 novembre 2026) : une seule classe `YamlMappers` avec limites explicites (256 Ki caractères, 32 niveaux, 10 alias, clés dupliquées refusées) et corps de requête limité à 1 Mio (`413`). Tests : `YamlMappersTest`, `ServiceResourceInputLimitsTest`. |
| K11 | **Le Kernel ne sépare plus une équipe d'une autre** (décision de Samuel, 10 novembre 2026, option A). Tout appelant qui porte le rôle administrateur peut modifier n'importe quel service ; tout appelant authentifié peut lire n'importe quel manifeste (y compris `labels` et `annotations`), n'importe quelle configuration et les Conditions, dont les messages d'erreur d'adapter (K8). | **Décidé et accepté** : les droits fins relèvent d'un système extérieur au Kernel (SpiceDB, [14](14-securite.md)). *À examiner par le relecteur* : l'émetteur des jetons ne donne-t-il le rôle administrateur qu'à `platform-operator-service` et à la pipeline de déploiement ? (V10, [19](19-feuille-de-route.md)) |
| K11 | Le cache d'idempotence est en mémoire du processus : il n'est pas partagé entre plusieurs instances du Kernel (déjà documenté comme limite V1). | À traiter avec la haute disponibilité. |

---

## 5. Hors périmètre

La terminaison TLS publique (faite par l'Edge Gateway, en amont du Kernel, [21](21-systeme-agents-et-gateway.md)), le fournisseur d'identité, SpiceDB et les droits métier de la Plateforme, les clusters Nomad et Kubernetes eux-mêmes, et les services déployés par le Kernel.
