# 19 — Feuille de route — phases livrables

Chaque phase produit un **livrable démontrable**, pas seulement du code
intermédiaire. L'ordre est pensé pour que chaque phase s'appuie sur un
socle déjà testé, et pour dérisquer en premier les intégrations externes
(Nomad/Consul), qui sont la partie la plus incertaine techniquement.

## Suivi — légende et tableau de bord

Ce document est la **seule source de statut** des phases du Kernel, de la phase A de la Plateforme (côté Kernel), de la phase P0 proposée et des points ouverts du Kernel. Le statut des phases B à H de la Plateforme est dans le dépôt `Civitas-EGEN-Business`, `docs/plateforme/11-plan-par-phase.md`.

Légende : `[x]` fait · `[~]` en cours · `[ ]` à faire · `[>]` reporté, avec un **déclencheur écrit** (on ne le fait que si le déclencheur se produit) · `[?]` proposé, à valider.

Mise à jour : 10 novembre 2026. **Reprendre par la section « Reprise de session » ci-dessous.**

| Élément | Statut | Reste à faire |
|---|---|---|
| Phases 0 à 5 du Kernel | **Fait** | Compléments liés à la Plateforme, listés dans chaque phase : Phase 1 (A-X1 reporté), Phase 2 (décision D20 actée), Phase 4 (A3 fait, A-X2 reporté), Phase 5 (A5 fait) |
| Phase 6 (continue) | **En cours** | Éléments 1 (adapter Kubernetes, routage par service), 2 (chaos 6a), 3 (dossier de sécurité), 4 (runbooks) et la relance par `Declare` livrés, **validés et sur `main`**. Reste : chaos 6b, relecteur externe, relecture des runbooks par un opérateur |
| Chantier Système d'agents | **Partiel** | 1 élément fait, 4 à faire (voir le chantier) |
| Phase A de la Plateforme (côté Kernel) | **Fait** | A2 à A5 livrés |
| Phase P0 « production du Kernel » | **Proposée** | À valider par Samuel |
| Points ouverts du Kernel | **Ouverts** | 6 points, à décider par Samuel (le point 6 est le durcissement strict des pods, issu du constat K9) |
| Nettoyage du code non conforme (N1) | **Fait** | Audit complet du 3 novembre 2026 ; 3 écarts non tranchés → points ouverts 3 à 5 |

## Reprise de session — point de situation du 10 novembre 2026

> **À lire en premier par quiconque reprend le développement.** Le reste du document dit *ce qui est prévu* ; cette section dit *où l'on en est exactement*, ce qui est validé, ce qui ne l'est pas, et dans quel ordre continuer.

### 1. Où est le code

```
main (bc33d0a)   = TOUT est dessus, VALIDÉ par un `mvn verify` complet chez Samuel (10 novembre 2026, 22:41)
```

`main` contient : l'adapter Kubernetes et son test de niveau 3, le routage du Deployment Adapter par service, les correctifs de sécurité C1, C2, **K6**, **K8**, **K9**, **K10** (voir le document 24), **l'autorisation à deux niveaux** (lire : tout appelant authentifié ; modifier : rôle administrateur ; `metadata.team` purement informatif, décision de Samuel du 10 novembre 2026, option A, documents 13, 14 et 22), l'exception K7 et l'examen de K3 (document 14 et 24), le registre des vérifications en attente, le chaos 6a, les runbooks, le dossier de revue de sécurité (documents 23 et 24) et la relance par `Declare`.

Aucune branche de **code** n'est en attente de validation. **Fusion explicite ordonnée par Samuel le 11 novembre 2026** : toutes les branches de documentation (dernière : `docs/v1-resultats`, résultats de V1, V2, V3 et V3b) ont été fusionnées dans `main` en avance directe, sans `mvn verify` (documentation seulement, aucun fichier hors `docs/`). Toutes les branches du dépôt distant sont maintenant contenues dans `main` : elles peuvent être supprimées (`git push origin --delete <branche>`).

### 2. Dernière fonctionnalité livrée : la relance par `Declare`

**Point ouvert 6, option (a), décidée par Samuel le 6 novembre 2026 : un `Declare` accepté sur un service `FAILED` ou `STOPPED` le relance depuis `DECLARED`.** Contrat normatif : [09](09-cycle-de-vie.md#relance-par-declare).

| Couche | Fichier | Changement |
|---|---|---|
| Domaine | `LifecycleStateMachine` | deux arêtes ajoutées : `FAILED → DECLARED` et `STOPPED → DECLARED` |
| Application | `DeployServiceUseCaseImpl` | après l'enregistrement de la nouvelle génération, un service `FAILED` ou `STOPPED` est remis en `DECLARED`, Conditions effacées ; Javadoc remis à sa place |
| Tests | `LifecycleStateMachineTest` | les deux arêtes autorisées, quatre transitions voisines refusées |
| Tests | `DeployServiceRelaunchTest` (5 cas) | relance depuis `FAILED` et `STOPPED` ; **toutes les autres phases intactes** ; un `Declare` refusé ne touche à rien ; service neuf |
| Tests | `FailedServiceRelaunchTest` (2 cas) | **de bout en bout avec le vrai moteur** : un service `FAILED` que le moteur ignorait revient en `RUNNING` avec exactement 2 instances ; idem pour `STOPPED` |
| Docs | 09, 13, 15, 23, 19 | contrat de relance ; routes non livrées marquées dans le 13 ; runbook réécrit ; métrique corrigée |

*Pourquoi `DECLARED` et pas `DEPLOYING`* (arête déjà autorisée) : `DEPLOYING` sauterait les contrôles de configuration, de secrets, de dépendances et de messagerie, alors que le nouveau manifeste peut changer exactement cela.

### 3. Ce qui a été vérifié, et ce qui ne l'a pas été

L'environnement où ce code a été écrit **n'atteint pas Maven Central** : pas de Maven, pas de Quarkus, pas de Testcontainers. On y compile et teste avec `javac` et les JAR JUnit et Jackson installés par `apt` ; la validation complète se fait **chez Samuel** par `mvn verify`.

- **Validé chez Samuel** : `mvn verify` complet, 18 modules, dont le test de niveau 3 contre un vrai cluster k3s, tous les tests Quarkus et les règles ArchUnit, le 6 novembre 2026 ; puis de nouveau le 9 novembre 2026 pour K10 (premier essai rouge sur un seul test, corrigé : la limite de 1 Mio n'était pas sur le classpath d'`egen-api`) et pour K8 (vert du premier coup).
- **Vérifié en plus, ici** : test de mutation de la relance (sans l'appel, les 4 tests qui la vérifient échouent) et du chaos 6a.

### 4. Décisions attendues de Samuel, par priorité

1. **Constats du document 24** : **K2** (audience du jeton : attend la réponse sur le fournisseur d'identité) et **K3** (règle « tout refuser hors de `/api/v1/` » : non décidée). K1 et K4 sont **sans objet / décidés** (10 novembre 2026), K5 confirmé, K6, K7, K8, K10 traités, K11 accepté.
2. **Outil d'injection de pannes réseau** pour le niveau 6b du chaos.

### 5. Prochaines étapes de développement, dans l'ordre

1. **Vérifications V1 à V8** : voir la section « Vérifications en attente » ci-dessous.
2. **K2 et K3** : décisions de Samuel (voir 4).
4. Écarts relevés, non traités : `egen_services_total` est un compteur par cycle et non une jauge ; `actions/restart`, `actions/rollback`, `/history` et `/adapters` (doc 13) ne sont pas livrés ; la liste « critères de fin » de la fin de ce document contient des cases encore ouvertes.

   **Écarts relevés par les vérifications du 10 et 11 novembre 2026 (V1 à V3), chacun à décider par Samuel, aucun n'est une fuite de secret :**
   - **E1, découverte** : quand Consul répond une erreur, `GET /api/v1/discover/{nom}` renvoie un `500` générique (`Error id …`, aucun détail) : `DiscoveryException` n'est convertie par aucun mappeur. Le message d'adapter est dans le journal (« Consul a rejete resolve(v3-news) : HTTP 500 — … » avec la trace). Le doc 13 ne précise aucune erreur pour cette route ; les autres routes renvoient `503` quand leur store est indisponible. *Question* : renvoyer `503` ?
   - **E2, publication de configuration** : quand Consul répond une erreur, `PUT …/configuration` renvoie `503` **sans corps**, conformément au doc 13 (« Store indisponible : `503`, rien n'est publié »), mais la cause (code HTTP et corps de la réponse de Consul) **n'est écrite nulle part** dans le journal : elle n'est enregistrée que dans la trace (`span.recordError`), invisible sans collecteur OpenTelemetry. *Question* : ajouter une ligne de journal `WARN` portant le message d'adapter, comme le contrat K8 le conserve ailleurs pour déboguer ?
   - **E3, secret non résolu** : un service dont le secret Vault est refusé est retenté **toutes les 3 secondes, sans fin ni délai croissant** (relevé : environ 45 appels à Vault entre 10:33:29 et 10:35:45, et 3 lignes de journal par tentative, soit près de 3 600 lignes par heure et par service bloqué). Le doc 09 dit « retentée à chaque cycle » sans durée. *Question* : un délai croissant, comme pour les échecs de déploiement (2, 4, 8, 16 secondes, puis arrêt) ?

### 6. Règles de travail à ne pas perdre

- **`main` ne reçoit que du code validé** par un `mvn verify` vert chez Samuel, en avance directe. Samuel peut ordonner un merge explicite (cela a eu lieu le 6 novembre) : dans ce cas, le dire clairement dans la section 1, comme ci-dessus.
- **La documentation d'abord** : le contrat normatif s'écrit avant le code. Tout fait externe est vérifié dans une source (code, dépôt officiel) ou marqué « à vérifier ».
- **Des commits explicites** : un titre qui dit la fonctionnalité touchée, un corps qui liste modules, fichiers, tests et état de vérification. Des commits supplémentaires plutôt qu'une réécriture d'historique.
- **Ne jamais écrire un jeton ou un mot de passe** dans un fichier, un commit ou une doc. Le jeton GitHub qui a servi à pousser a été exposé dans une conversation : **à révoquer**.
- **Relever chaque incohérence ou ambiguïté d'un test** (demande de Samuel, 11 novembre 2026) : à chaque test, comparer ce qu'on obtient à ce qu'on devrait normalement recevoir en sortie ; tout écart, même non critique, s'écrit dans « Incohérences et ambiguïtés relevées dans les tests » (section du registre) avec sa gravité, pour y revenir et ajuster plus tard. Un écart critique (fuite de secret, droit contourné, perte de données) interrompt les tests et se traite tout de suite.
- **Noter toute vérification non faite** : une vérification que Samuel n'a pas faite, ou dont personne n'a de résultat, s'écrit dans « Vérifications en attente » (début du document), avec qui peut la faire et le résultat attendu. Chaque réponse de fin de travail rappelle ce qui est **vérifié ici** et ce qui **ne l'est pas**.
- **Vérifier avant de conclure** : relire le code plutôt que la seule doc, essayer plusieurs motifs de recherche avant d'affirmer qu'une chose « n'existe pas », et prouver qu'un test peut échouer (test de mutation).

### 7. Pièges déjà rencontrés

- **Testcontainers** : le BOM de Quarkus, importé en premier dans le `pom.xml` racine, fixe le noyau `testcontainers` à 2.0.4. Le module `k3s` 1.20.4 est incompatible (`NoClassDefFoundError … shaded … IOUtils`) : on utilise un `GenericContainer` configuré comme `K3sContainer`.
- **Règle A4** (`PlatformBoundaryRuleTest`) : des mots réservés à la Plateforme sont interdits dans les identifiants du code du Kernel (relire la liste dans le test ; `catalog` a fait échouer un build, `DeploymentAdapterCatalog` est devenu `EnabledDeploymentAdapters`).
- **`Map.copyOf` et `Set.of`** renvoient des collections dont l'ordre d'itération change à chaque lancement de JVM : ne jamais les utiliser quand l'ordre apparaît dans un message ou un résultat.
- **Le Registry attribue lui-même la génération** à l'enregistrement : `save(new DesiredState(…, 0L, …))` donne la génération 1. Un test ne doit jamais supposer une génération absolue, mais comparer à la valeur observée avant.
- **Le moteur réapplique l'état désiré à chaque cycle** (`create()` rappelé toutes les ~10 s) : toute modification manuelle dans Nomad ou Kubernetes est annulée ; la sûreté repose sur l'idempotence de chaque appel.
- **Un recensement par recherche textuelle peut rater une forme d'appel** : la durée de réconciliation était émise via `recordDuration(new MetricSample("egen_…"))`, que mon premier motif ne couvrait pas, et deux documents ont affirmé à tort qu'elle n'existait pas.
- **Un script de modification qui échoue à mi-chemin** laisse des changements partiels : lire `git status` avant de commiter, et vérifier que le message de commit décrit bien ce qui est dedans.

## Vérifications en attente — non faites, ou sans résultat

**Règle (demandée par Samuel, 9 novembre 2026).** Toute vérification qui n'a pas été faite, ou dont personne n'a obtenu de résultat, est notée **ici**, et nulle part ailleurs sous forme de liste. Ce n'est pas un oubli : Samuel les fera plus tard. Une ligne n'est retirée que lorsqu'un **résultat daté** est écrit dans la colonne de droite ; elle passe alors dans « Faites ». Un document peut renvoyer à cette section, jamais la dupliquer.

| N° | Vérification | Qui peut la faire | Résultat attendu | Où c'est mentionné |
|---|---|---|---|---|
| V4 | Valeur par défaut de `revisionHistoryLimit` d'un Deployment Kubernetes (10 supposé), et retour arrière de plus d'une version | Samuel (cluster k3s) ou le relecteur | La valeur confirmée ; un `rollback` vers une version plus ancienne que la limite échoue comme décrit. Le test de niveau 3 ne remonte aujourd'hui que d'une version | [07](07-ports-et-adapters.md), « Retour arrière » ; **en plus** (reporté de V3) : avec l'adapter Kubernetes actif, vérifier que `EGEN_KUBERNETES_TOKEN` n'apparaît ni dans le journal ni dans une réponse de l'API |
| V5 | Texte exact d'un corps d'erreur Kubernetes (par exemple un `422`) : il dépend de la version de Kubernetes | Samuel (cluster k3s) | Un exemple réel, à mettre dans l'exemple de bout en bout du 07 | [07](07-ports-et-adapters.md), « Messages d'erreur des adapters HTTP » |
| V6 | Relecture des runbooks par un opérateur | un opérateur | Les scénarios `FAILED`, `COMPENSATION_FAILED` et divergence prolongée se suivent sans aide | [23](23-runbooks-operateur.md) |
| V7 | Chaos 6b : latence vers Consul, coupure de NATS, redémarrage brutal d'un vrai Kernel | Samuel, après le choix de l'outil d'injection de pannes | Le Kernel converge de nouveau sans intervention | [17](17-strategie-de-tests.md), niveau 6 |
| V8 | Revue de sécurité externe ; le relecteur confirme aussi la configuration NATS (`egen.messaging.*`) | le relecteur, à choisir | Un rapport sur le document 24 | [24](24-revue-de-securite-externe.md) |
| V9 | Aligner la décision **D14** dans le dépôt `Civitas-EGEN-Business` (`docs/plateforme/02-registre-des-decisions.md`) : plus d'équipe `platform-operator` pour l'autorisation ; `platform-operator-service` et la pipeline portent le rôle administrateur | Samuel (autre dépôt) | Le registre de la Plateforme dit la même chose que le doc 22 | [22](22-frontiere-avec-la-plateforme.md), D14 |
| V10 | L'émetteur des jetons (fournisseur d'identité) ne donne le rôle administrateur (`egen-admin`) qu'à `platform-operator-service` et à la pipeline de déploiement | Samuel (configuration du fournisseur d'identité) | La liste des clients qui portent ce rôle ne contient que ces deux appelants | [24](24-revue-de-securite-externe.md), K11 |
| V11 | Le dépôt `Civitas-EGEN-Business` et les clients du Kernel (SDK, console, passerelle) ne comptent pas sur l'autorisation par équipe (par exemple une lecture filtrée par équipe) | Samuel | Aucun appelant ne dépend d'un `403` fondé sur l'équipe ni d'une liste filtrée | [14](14-securite.md) |

| V3c | **Reste de V3b** : un corps de réponse **de plus de 500 caractères** dans un vrai journal : le message d'adapter doit être coupé à 500 caractères et se terminer par ` [tronque]` (les tests unitaires le prouvent, aucun test réel ne l'a encore observé : le corps de V3 faisait environ 330 caractères) | Samuel (faux serveur avec un corps long) | Message d'adapter de 500 caractères de corps maximum, suffixe ` [tronque]`, dans le journal **et** dans la Condition | [07](07-ports-et-adapters.md), « Messages d'erreur des adapters HTTP » |

### Incohérences et ambiguïtés relevées dans les tests

*Demande de Samuel (11 novembre 2026) : relever à chaque fois ce qui diffère de ce qu'on devrait normalement recevoir, et y revenir pour ajuster tant que ce n'est pas critique. Aucune ligne ci-dessous n'est une fuite de secret ni un contournement de droit.*

| N° | Test | Attendu | Obtenu | Gravité | Statut |
|---|---|---|---|---|---|
| I1 | V1, mot de passe absent | message du Kernel « Démarrage refusé en production … » | message générique de Quarkus (« Failed to load config value … quarkus.datasource.password ») | non critique (le refus est sûr) | docs 14, 23, 24 corrigés ; code inchangé |
| I2 | V1, sans émetteur OIDC | le Kernel démarre et refuse les modifications (doc 19, point ouvert 1) | le Kernel ne démarre pas du tout | non critique (refus sûr) | doc 19 corrigé |
| I3 | V2, chemin `/` | non documenté | `404` | sans gravité | à documenter si besoin |
| I4 | V3, consigne de test (Claude) | « `PUT /v1/job/…` ou `POST /v1/jobs` » (consigne ambiguë) | `PUT /v1/jobs` | sans gravité | la consigne était imprécise |
| I5 | V3, journal | une entrée de journal par événement | un corps d'erreur de 11 lignes est écrit sur 11 lignes du journal | non critique | à décider (couper aux retours à la ligne ?) |
| I6 | V2/V3, OpenTelemetry sans collecteur | non documenté (doc 15 muet) | erreur `SEVERE` d'environ 35 lignes toutes les 60 secondes | non critique | à décider (désactiver l'export sans collecteur ?) |
| I7 | V3b, `PUT …/configuration` quand Consul échoue | `503` avec la cause dans le journal | `503` sans corps et cause absente du journal | moyenne (débogage) | **E2** |
| I8 | V3b, `GET /discover/{nom}` quand Consul échoue | `503` comme les autres routes | `500` générique | moyenne | **E1** |
| I9 | V3b, secret Vault refusé | délai croissant comme pour le déploiement | nouvelle tentative toutes les 3 secondes, sans fin | non critique | **E3** |
| I10 | V10 (à faire) | le Kernel lit le rôle `egen-admin` dans la claim `groups` (défaut MicroProfile JWT, non écrit dans la doc 14) ; V3 l'a confirmé avec un jeton de test | Keycloak met par défaut les rôles dans `realm_access.roles`, pas dans `groups` | à confirmer par V10 | **ambiguïté de documentation** : la claim n'est écrite ni dans 14 ni dans 23 |

**Faites** (avec leur résultat) : **V1 (10 novembre 2026, 23:01, jar de `bc33d0a`, lancé depuis un dossier vide)** : (a) sans `EGEN_REGISTRY_DB_PASSWORD`, démarrage refusé, code de sortie 1, mais par Quarkus avec un message générique « Failed to load config value … quarkus.datasource.password » (et non le message du Kernel ; la branche « absent » de `ProductionConfigurationRules` est donc inatteignable en pratique en production, elle reste une défense) ; (b) avec `egen`, démarrage refusé avec le message prévu, code de sortie 1 ; (c) avec une valeur propre, la garde ne refuse pas (l'échec suivant est la connexion à PostgreSQL, absent). **V3, partie Nomad (10 novembre 2026, 23:22 à 23:27, jar de `bc33d0a`, base PostgreSQL + faux Nomad Python sur le port 4646)** : jeton de test signé avec une clé locale (`EGEN_OIDC_JWKS_URL=file:///tmp/v3/public.pem`, émetteur `https://issuer.test.invalid`, groupe `egen-admin`) : `GET /api/v1/services` → 200 `[]` ; `POST /api/v1/services` → **202 Accepted** (le rôle `egen-admin` est bien lu dans le jeton). Le Kernel a appelé `PUT /v1/jobs` sur le faux Nomad **5 fois** (23:26:34, :36, :40, :48, 23:27:04 : intervalles de 2, 4, 8 et 16 secondes), journalisé « Echec d'adapter pour v3-news (1/5) … (5/5) : Nomad a rejete create/update(v3-news) : HTTP 501 — <corps> », puis posé la Condition `DeploymentReady=FALSE` (raison `AdapterFailureThresholdExceeded`) et la phase `FAILED`. Journal de 454 lignes. Recherche dans ce journal de `CANARI-NOMAD-111`, `CANARI-CONSUL-222`, `CANARI-VAULT-333`, `un-secret-de-test`, `eyJ` (début d'un jeton JWT), `Bearer` et `Authorization` : **0 occurrence pour chacun**. **Limites** : le faux serveur n'affichait pas les en-têtes reçus, donc l'envoi effectif du jeton Nomad n'est pas démontré ; les chemins Consul et Vault n'ont pas été exercés (voir V3b). **Observations** : le corps d'erreur (11 lignes de HTML, environ 330 caractères, donc sous la borne de 500) apparaît intégralement et sur plusieurs lignes dans le journal et dans le message de la Condition ; la borne du K8 est en caractères, pas en lignes. L'erreur OpenTelemetry sur les traces est apparue 3 fois entre 23:26:42 et 23:27:23. **V3b (11 novembre 2026, 10:31 à 10:35, jar de `bc33d0a`, faux Nomad, Consul et Vault Python affichant le jeton reçu)** : le faux serveur a affiché `X-Nomad-Token: CANARI-NOMAD-111` (5 appels `PUT /v1/jobs` à 10:32:09, :11, :15, :23, :39), `X-Vault-Token: CANARI-VAULT-333` (`GET /v1/secret/metadata/v3-sec/db` répété toutes les 3 secondes dès 10:33:29), `X-Consul-Token: CANARI-CONSUL-222` (`PUT /v1/kv/egen/config/development/v3-sec` et `GET /v1/health/service/v3-news?passing=true` à 10:34:15) : **les trois jetons sont bien envoyés**. Recherche dans le journal du Kernel de `CANARI-NOMAD-111`, `CANARI-CONSUL-222`, `CANARI-VAULT-333`, `un-secret-de-test`, `eyJ`, `Bearer`, `Authorization` : **0 occurrence pour chacun**. Les réponses de l'API ne contiennent aucun jeton (`PUT …/configuration` : 503 sans corps ; `GET /discover/v3-news` : 500 générique « Error id … »). Le journal contient bien les messages d'adapter Vault (« Vault a rejete la verification de \"secret/data/v3-sec/db\" : HTTP 403 — {…} ») et Consul (« Consul a rejete resolve(v3-news) : HTTP 500 — {…} »). Le mot de passe PostgreSQL n'apparaît pas non plus dans la trace d'échec de connexion observée en V1. **V3 est terminée** pour Nomad, Consul, Vault et le mot de passe de la base ; le jeton Kubernetes est reporté à V4/V5. **V2 (10 novembre 2026, ~23:15, même jar, base PostgreSQL seule)** : le Kernel démarre en production avec la seule base (profil `prod`, 1,4 s) ; `curl` sans jeton sur `/q/health`, `/q/health/live`, `/q/health/ready`, `/q/openapi`, `/q/metrics`, `/q/dev-ui`, `/q/swagger-ui`, `/q/arc` et `/` : **404 partout** ; `/api/v1/health` : 200 ; `/api/v1/services` : 401. Fonctionnalités installées affichées au démarrage : `agroal, cdi, jdbc-postgresql, narayana-jta, rest, rest-jackson, security, smallrye-context-propagation, smallrye-jwt, vertx` (aucune extension de santé, de métriques, d'OpenAPI ni d'interface de développement). **Observation** : sans collecteur OpenTelemetry (`localhost:4317`), le Kernel démarre et reste en vie ; environ une minute après le démarrage il journalise une erreur `SEVERE` avec une trace complète (`Failed to export metrics`). L'erreur se répète **environ toutes les 60 secondes** (relevé : 23:12:06, 23:13:07, 23:14:05, 23:15:06, 23:16:06, puis 23:23:14 et 23:24:14 sur un second démarrage), avec une trace d'environ 35 lignes à chaque fois. Une erreur analogue « Failed to export spans » a été relevée à 23:23:24 (premier export de traces ; sans doute déclenchée par une requête reçue : non vérifié). **Constats** : sans `EGEN_OIDC_ISSUER_URL` et `EGEN_OIDC_JWKS_URL`, le Kernel ne démarre pas non plus en production ; ces trois variables sont obligatoires (ajoutées au runbook, [23](23-runbooks-operateur.md)).  `mvn verify` complet, 18 modules, vert chez Samuel pour K9 (7 novembre 2026), K10 (9 novembre 2026, 22:26, après correction d'un test), K8 (9 novembre 2026, 22:45) et l'autorisation à deux niveaux (10 novembre 2026, 22:41, vert du premier coup ; ce passage valide aussi les 12 cas de `ServiceResourceAdminOnlyTest` et `ServiceConfigurationAdminOnlyTest`, et la suppression de la propriété `egen.security.team-claim`).

## Phase 0 — Fondations et décisions structurantes

**Objectif** : poser les décisions et les garde-fous avant d'écrire la
première ligne de logique métier du Kernel.

- [x] Décisions actées et documentées : langage définitif du Kernel (voir
      [16](16-packages-et-stack-technique.md)), NATS vs Kafka en V1 (voir
      [07](07-ports-et-adapters.md#kafka-vs-nats-jetstream--quand-utiliser-lequel)),
      Vault pour les secrets.
- [x] Squelette multi-module qui compile : `egen-domain`,
      `egen-application`, `egen-adapters/*`, `egen-api`,
      `egen-contracts`, `egen-bootstrap`, `egen-test-kit`.
- [x] Mise en place immédiate des règles de dépendance de build (voir
      [16.2](16-packages-et-stack-technique.md#règle-de-build-à-faire-respecter-par-loutillage-pas-seulement-la-revue-de-code) /
      [17, niveau 4](17-strategie-de-tests.md#niveau-4--tests-darchitecture-fitness-functions))
      — **avant** d'écrire le premier port, pour qu'aucune violation ne
      s'accumule dès le départ.
- **Livrable** : squelette multi-module vide qui compile, avec les règles
  ArchUnit déjà actives (rouges tant qu'aucun code n'existe, c'est normal
  — elles doivent juste être prêtes).

## Phase 1 — Cœur hexagonal minimal + premier adapter (Deployment/Nomad)

**Objectif** : prouver le cycle Declare → Resolve → Compose → Delegate →
Observe → Reconcile de bout en bout sur **un seul** port.

- [x] `egen-domain` : `ServiceManifest` (version minimale : id, version,
      runtime, deployment), `DesiredState`, `ObservedState`,
      `LifecycleStateMachine` réduite aux phases `DECLARED → REGISTERED →
      DEPLOYING → RUNNING → FAILED/STOPPED`.
- [x] `egen-application` : `DeployServiceUseCase`, `ReconciliationEngine`
      v0 (work queue simple, un seul worker, resync périodique basique —
      pas encore de concurrence bornée avancée ; livrée ensuite par N1 :
      `maxConcurrentReconciles`, voir « Référence unique et nettoyage du code »).
- [x] `DeploymentPort` + `NomadDeploymentAdapter` complet selon le mapping
      de [07](07-ports-et-adapters.md#mapping-vers-lapi-http-nomad-v1-port-par-défaut-4646)
      (create/update/scale/stop/remove/getStatus/rollback).
- [x] `egen-api` minimal : `POST /api/v1/services`, `GET
      /api/v1/services/{id}/status`.
- [x] Tests niveaux 1, 2, 3 (Testcontainers Nomad), et un premier test de
      niveau 5 : déployer un service fixture réel, observer sa
      convergence jusqu'à `RUNNING`.
- **Livrable démontrable** : `curl -X POST /api/v1/services -d
  @manifest.yaml` déploie réellement un conteneur via Nomad et son statut
  converge vers `RUNNING` dans l'API.

**Compléments liés à la Plateforme** (voir [22](22-frontiere-avec-la-plateforme.md#229--limites-actuelles-du-kernel)) :

- [>] **A-X1 — Différencier `runtime.type`** (`external`, `vm`, `binary`) : aujourd'hui conservé mais non différencié par le Deployment Adapter. *Déclencheur* : besoin avéré que le Kernel **gère** (découverte, santé) une application hébergée hors de lui ; sinon les instances `EXTERNAL` restent portées par le catalogue (D15, [22](22-frontiere-avec-la-plateforme.md#228--décisions-qui-touchent-le-kernel)).

## Phase 2 — Discovery (Consul) + Registry EGEN + cycle de vie complet

- [x] `DiscoveryPort` + `ConsulDiscoveryAdapter` (voir
      [07](07-ports-et-adapters.md#discovery-port)).
- [x] `RegistryStorePort` + adapter PostgreSQL (voir
      [08](08-registry.md)), avec verrouillage optimiste sur
      `generation`.
- [x] `LifecycleStateMachine` complète (toutes les phases de
      [09](09-cycle-de-vie.md)), avec `Conditions`.
- [x] Ordre d'arrêt gracieux implémenté et testé explicitement (voir
      [09](09-cycle-de-vie.md#ordre-darrêt-gracieux--un-piège-classique)).
- [x] `GET /api/v1/discover/{serviceName}` exposé.
- **Livrable démontrable** : deux services fixtures se découvrent
  mutuellement via l'API EGEN (sans IP codée en dur), et un arrêt gracieux
  ne produit aucune erreur côté appelant pendant la fenêtre de
  propagation.

**Complément lié à la Plateforme** :

- [x] **Décision D20 : pas de filtre de liste par label** (`GET /api/v1/services`). Le filtrage se fait côté client sur la liste complète ([08](08-registry.md)). Aucune évolution.

## Phase 3 — Dépendances, ordonnancement, Messaging (NATS)

- [x] `DependencyGraph` complet : détection de cycle, tri topologique,
      dégradation gracieuse pour dépendances optionnelles (voir
      [10](10-gestion-des-dependances.md)).
- [x] `MessagingPort` + `NatsMessagingAdapter` (voir
      [07](07-ports-et-adapters.md#mapping-vers-lapi--nats-jetstream)),
      enveloppe CloudEvents.
- [x] Ordonnancement du déploiement initial multi-services respectant le
      graphe de dépendances.
- **Livrable démontrable** : trois services fixtures liés par dépendances
  `required` se déploient dans le bon ordre ; l'un publie un événement
  CloudEvents que l'autre consomme via `MessagingPort`.

## Phase 4 — Configuration, Secrets, Observabilité de production

- [x] `ConfigurationPort` (voir [07](07-ports-et-adapters.md#configuration-port)),
      `SecretsPort` + `VaultAdapter` (voir
      [07](07-ports-et-adapters.md#secrets-port)).
- [x] `ObservabilityPort` OpenTelemetry complet : métriques de
      [15.1](15-observabilite.md#les-trois-signaux-standardisés-opentelemetry)
      au complet (dont `egen_dependency_unresolved_total` et les métriques
      génériques `egen_adapter_call_duration_seconds`/`_errors_total`,
      ajoutées via des décorateurs `Observed*Port` — voir
      `egen-application/observability/`), logs structurés enrichis de
      serviceId/generation/operationId/traceId, attributs de ressource
      OTel complets (`service.name/version/namespace`,
      `deployment.environment`). La trace HTTP entrante est liée au
      premier cycle de réconciliation qui en résulte, jusqu'à son premier
      appel réseau réel (voir `PendingDeclareTraces` et le livrable
      ci-dessous — complété après coup, voir l'historique Git).
- [x] Sécurité de base de l'API de contrôle : authentification (jeton
      MicroProfile JWT — voir [14](14-securite.md)) + RBAC scoped par
      équipe (`metadata.team` du manifeste, comparé au claim du jeton —
      voir `egen-api/security/EgenSecurityContext.java`) **[remplacé le 10
      novembre 2026 : plus d'autorisation par équipe, lire = tout appelant
      authentifié, modifier = rôle administrateur ; `metadata.team` est
      informatif — voir [14](14-securite.md)]**, politique HTTP
      par défaut refusant tout sauf `/api/v1/health`. `Idempotency-Key`
      obligatoire sur les mutations, également livré à cette étape (voir
      [13](13-api-et-contrats.md)) bien que documenté hors de la liste
      initiale de cette phase — implémentation V1 en mémoire de processus,
      à remplacer par un stockage partagé le jour où plusieurs instances
      du Kernel tournent derrière un équilibreur de charge.
- **Livrable démontrable** : une trace unique dans Jaeger/Tempo montre le
  chemin complet d'un déploiement, de l'appel API jusqu'au premier appel
  applicatif du service déployé ; un tableau de bord Grafana répond en un
  coup d'œil aux questions de [15.2](15-observabilite.md#questions-auxquelles-lobservabilite-egen-doit-repondre-en-un-coup-doeil).
  **Livré en entier.** Le span `http.declare`/`http.stop` et les cycles de
  réconciliation qu'il déclenche (`reconcile`, puis ses enfants
  `deployment.create`/`discovery.deregister`) apparaissent désormais comme
  un seul arbre de trace continu dans Jaeger, grâce a
  `PendingDeclareTraces` (`egen-application.reconciliation`) : un petit
  registre en mémoire, jamais persisté ni transitant par la `WorkQueue`
  (qui reste strictement des clés, voir
  [04](04-moteur-de-reconciliation.md)), qui relie le contexte de trace
  HTTP au premier cycle de réconciliation concerné, le temps que ce cycle
  atteigne son premier appel réseau réel — au-delà, les cycles de
  convergence et de resync redeviennent des racines indépendantes, exactement
  la portée que demande [15](15-observabilite.md) ("jusqu'au premier appel
  réseau", jamais au-delà). Resync périodique et convergence continuent de
  produire des racines de span indépendantes, conformément à
  [15](15-observabilite.md).

**Compléments liés à la Plateforme** :

- [x] **A3 — API de publication de configuration** `PUT` et `GET /api/v1/services/{id}/configuration` (expose `ConfigurationPort.publish`) : contrat normatif en [13](13-api-et-contrats.md#publication-de-configuration--put-apiv1servicesidconfiguration). Nécessaire à la configuration des instances d'applications par la Plateforme. **Livré le 3 novembre 2026**, testé aux niveaux 1 (`ConfigurationSpec.validatePublication`), 2 (cas d'usage), 3 (adapter Consul contre un vrai agent : remplacement complet, version monotone), 4 (règle A4, contrat OpenAPI) et 5 (scénario A5).
- [>] **A-X2 — Namespaces Nomad par tenant** : l'adapter ne gère pas les namespaces ([07](07-ports-et-adapters.md)). *Déclencheur* : une organisation exige une isolation d'**infrastructure** (et non de données) ; sinon isolation par données ou par instance dédiée.
- [x] Limite documentée : le RBAC (à l'origine équipe + administrateur ; depuis le 10 novembre 2026 lire / modifier) reste grossier **par choix** ; l'autorisation métier n'est pas dans le Kernel ([14](14-securite.md), D14).

## Chantier parallèle — Système d'agents & Edge Gateway (hors de ce dépôt)

**Ajouté après coup (voir [21](21-systeme-agents-et-gateway.md)) : ce
chantier ne figurait pas dans les phases initiales ci-dessus.** Il est
inséré ici, entre les Phases 4 et 5, parce que c'est exactement le moment
où il devient possible de commencer — pas avant (il a besoin d'une API de
contrôle authentifiée et RBAC, livrée en Phase 4), pas forcément après (il
n'a pas besoin, sauf sur un point précis signalé plus bas, d'attendre la
Phase 5).

**Ceci n'est pas une phase du Kernel.** Elle ne produit aucun livrable
dans `Civitas-EGEN-Kernel` (à une seule exception explicite ci-dessous) —
le code correspondant vit dans deux dépôts séparés,
`Civitas-EGEN-Edge-Gateway` et `Civitas-EGEN-Agents`, chacun avec son
propre cycle de release (voir [21.0](21-systeme-agents-et-gateway.md#210--où-vit-ce-système-et-où-il-ne-vit-pas)).
Le Kernel reste ce qu'il a toujours été : un control plane qui ne connaît
ni utilisateurs, ni agents, ni le sens des capacités qu'un service expose
(garde-fou n°1, [02](02-principes-fondamentaux.md)).

Le détail complet (services, responsabilités, sécurité, ordre de
construction) est entièrement dans [21](21-systeme-agents-et-gateway.md) —
cette section ne le duplique pas, elle situe seulement ce chantier par
rapport aux phases du Kernel ci-dessus et ci-dessous.

- [x] **Seul point qui touche réellement ce dépôt** : le bloc optionnel
      `interfaces` dans `ServiceManifest`, additif et rétro-compatible,
      décrit en [21.3](21-systeme-agents-et-gateway.md#213--le-service-manifest-sétend-jamais-ne-se-casse)
      (`interfaces: [{type: mcp|openapi, ref, riskDefault}]`). Sans lui,
      aucun service ne peut annoncer ses capacités au Capability Gateway.
      Prérequis explicite à l'étape 1 de
      [21.10](21-systeme-agents-et-gateway.md#2110--ordre-de-construction-recommandé) —
      rien de plus, jamais de logique d'interprétation de ce bloc côté
      Kernel (il reste une chaîne opaque, comme `deployment.image`).
      *Livré : bloc `interfaces` du manifeste, relu par
      `GET /api/v1/services/{id}` ([06](06-service-manifest.md),
      [13](13-api-et-contrats.md)) et vérifié par le test de niveau 5.*
- [ ] Étapes 1 à 6 de [21.10](21-systeme-agents-et-gateway.md#2110--ordre-de-construction-recommandé)
      (service fixture → Capability Gateway → IAM/Policy Engine → Agent
      Gateway/Runtime à un seul agent → HITL/audit/quotas → supervisor et
      swarm) : peuvent démarrer dès aujourd'hui, en parallèle de la Phase
      5 ci-dessous — aucune des deux ne bloque l'autre.
- [ ] Étape 7 de [21.10](21-systeme-agents-et-gateway.md#2110--ordre-de-construction-recommandé)
      (brancher le Workflow Engine du Kernel comme outil de l'agent) : la
      **seule** dépendance stricte envers ce dépôt — ne peut pas commencer
      avant que `WorkflowEnginePort` (Phase 5 ci-dessous) soit livré.
- [ ] Étape 8 (services métier réels, un par un, chacun avec son bloc
      `interfaces`) : en continu, au rythme de `Civitas-EGEN-Business`.
- [ ] Edge Gateway (Kong ou équivalent, voir
      [21.1](21-systeme-agents-et-gateway.md#211--edge-gateway-kong--la-porte-dentrée-unique)) :
      projet de déploiement à part entière, jamais un service EGEN déclaré
      *auprès* du Kernel (il se trouve topologiquement devant lui). Sa
      mise en place peut commencer indépendamment de tout le reste de ce
      chantier, dès que la pipeline de déploiement générale
      ([21.8](21-systeme-agents-et-gateway.md#218--pipeline-de-déploiement-générale))
      est prête à l'accueillir.
- **Livrable démontrable** : le test d'acceptation de
  [21.9](21-systeme-agents-et-gateway.md#219--test-dacceptation-de-bout-en-bout-du-système-dagents)
  passe en entier — deux utilisateurs de deux équipes différentes
  obtiennent deux catalogues d'outils différents, une action `risk: write`
  passe par une confirmation HITL et produit un enregistrement d'audit, et
  une tentative de contournement du catalogue filtré est refusée au niveau
  du Capability Gateway, pas seulement masquée côté Agent Runtime.

## Phase 5 — Moteur de Workflow et communication fabric avancée

- [x] `WorkflowEnginePort` natif (voir [11](11-moteur-de-workflow.md)),
      avec compensation Saga orchestrée, durabilité de l'exécution.
      *Livré : modèle de domaine, ports (`ServiceInvocationPort`,
      `WorkflowStorePort`), `WorkflowEngine`, use cases, API REST
      `/api/v1/workflows` ; validé par le build Maven complet de la CI
      (tests unitaires, tests d'adapters PostgreSQL, API Quarkus, ArchUnit)
      et par `PublishArticleWorkflowScenarioIT` (voir ci-dessous).*
- [x] Communication Fabric : SDK léger pour au moins deux langages (Java et
      Python) et un mode sidecar de référence (voir
      [12](12-communication-fabric.md)).
      *Livré : `egen-sdk-java`, `sdk/python`, `egen-sidecar`.
      `POST /api/v1/events/publish` est livré côté Kernel
      ([13](13-api-et-contrats.md)), ainsi que `publish` dans les SDK Java et
      Python, après sa sémantique de résilience normative
      ([12](12-communication-fabric.md)).*
- [x] Résilience configurable (retry/timeout/circuit breaking) appliquée
      uniformément.
      *Livré : bloc `resiliency` du manifeste ([06](06-service-manifest.md)),
      sémantique normative et contrat exécutable partagé par le Kernel, le
      SDK Java et le SDK Python ([12](12-communication-fabric.md),
      [17](17-strategie-de-tests.md)), appliquée par le Moteur de Workflow
      et servie par `discover`.*
- [x] **Livrable démontrable** : le scénario complet de
  [20 — Scénario de bout en bout](20-scenario-bout-en-bout.md) s'exécute
  de bout en bout, y compris un cas de compensation déclenché
  volontairement.
      *Livré : le test de niveau 5 (`EgenAcceptanceE2ETest`, module
      `egen-bootstrap`, voir [17](17-strategie-de-tests.md#le-test-de-niveau-5-du-dépôt))
      rejoue la partie 1 et la partie 2 contre de vrais Nomad, Consul, NATS,
      PostgreSQL et Vault, et il est vert en CI. Il a révélé, en plus de
      l'absence d'index Jandex et du défaut d'adresse annoncée par Nomad, que
      le Kernel ne ré-observait un service convergé qu'au resync de 5 minutes :
      la perte d'une allocation n'était vue qu'au bout de plusieurs minutes.
      Corrigé par le régime permanent de ~10 s ([04](04-moteur-de-reconciliation.md)).*

**Complément lié à la Plateforme** :

- [x] **A5 — Scénario de niveau 5 « service fixture de plateforme »** : déclaré par l'API publique avec `team: platform-operator`, labels réservés et configuration publiée via A3 ; convergence vers `RUNNING`, labels restitués ([22](22-frontiere-avec-la-plateforme.md#227--la-règle-darchitecture-a4)).

---

## Référence unique et nettoyage du code

**Règle.** La documentation d'architecture publiée sur `main` est la **seule référence** de tout le projet, sans exception : pour le Kernel, `docs/architecture/` de ce dépôt ; pour la Plateforme, `docs/plateforme/` du dépôt `Civitas-EGEN-Business`. Tout élément de code, de configuration, de test ou de document qui ne concorde pas avec elle est **abandonné** : il est supprimé ou aligné, jamais conservé « au cas où ». En cas de conflit, la documentation l'emporte.

- [x] **N1 — Nettoyage du code non conforme à la documentation.** Marche à suivre : (1) audit complet du code contre la documentation ; (2) suppression ou alignement par petits commits nommés ; (3) construction et tests verts ; (4) cocher cet élément. Ne touche jamais une évolution déjà décrite comme « à faire » (A2 à A5, phase 6, P0) : ce sont des ajouts, pas du nettoyage.

**Audit complet du 3 novembre 2026** (comportements, tests, configuration, contrats ; les documents 02, 04 à 20 et 22 relus contre le code ; domaine, application, SDK Java, SDK Python et sidecar construits et testés séparément, le reste par la CI) :

| Constat | Traitement |
|---|---|
| Deux messages de journal du moteur (`ReconciliationEngine`, `WorkflowEngine`) contenaient une apostrophe non doublée : leurs paramètres `{0}`…`{3}` n'étaient **jamais** remplacés, donc le journal d'un échec d'adapter ne disait ni quel service ni quelle erreur | Corrigé |
| `versionConstraint` des dépendances était stockée et restituée mais **jamais évaluée** (06 « Validation du manifeste » point 4, 09, 20 étape 2) | Corrigé : `VersionConstraint` (domaine) ; une dépendance dont la version déclarée ne satisfait pas la contrainte retient le service en `CONFIGURED`, Condition `DependenciesSatisfied=False`, raison `VersionConstraintUnmet` ; une contrainte illisible refuse le manifeste. Grammaire dans [06](06-service-manifest.md) |
| Quand une allocation était perdue, la Condition `DeploymentReady` disait `AwaitingConvergence` au lieu de `AllocationLost` (20 étape 7) | Corrigé, avec les premiers tests de niveau 2 de la dégradation `RUNNING → DEGRADED → RUNNING` |
| `egen_workflow_executions_total{workflow, status}` (jeu minimal V1 de [15](15-observabilite.md)) n'était pas émise | Corrigé |
| Un seul worker de réconciliation, sans `maxConcurrentReconciles` (04 « Composants de la boucle ») ; en plus, chaque re-planification différée créait un thread et s'**ajoutait** à celles déjà en attente (une chaîne de plus par déclaration ou resync, jamais éteinte) ; un même service pouvait être pris par deux workers | Corrigé : `WorkQueue` à exclusion par clé et une seule échéance par clé ; `egen.reconciliation.max-concurrent-reconciles` (défaut 4) |
| L'arrêt du Kernel, qui interrompt un appel d'adapter, était compté comme un échec d'adapter (journal d'erreur, backoff, marche vers `FAILED`) | Corrigé |
| `egen-contracts/openapi/egen-control-api.yaml`, cité par [16](16-packages-et-stack-technique.md), était absent ; le schéma JSON du manifeste n'était confronté à aucun test | Corrigé : le contrat OpenAPI de la surface REST livrée existe ; `OpenApiContractTest` et `ManifestSchemaConsistencyTest` font échouer la CI si le contrat et le code divergent |
| La règle « un adapter ne dépend d'aucun autre » ne couvrait pas `egen-adapter-http-invocation` (liste en dur) | Corrigé : tranches ArchUnit, une par adapter, sans liste |
| `POST /api/v1/services/{id}/actions/stop`, livré et testé, ne figurait pas dans la liste des ressources de [13](13-api-et-contrats.md) | Documenté |
| Un principal non-JWT faisait répondre l'API par une erreur 500 au lieu de 403 (le code lisait un claim sur un principal qui n'en a pas) | Corrigé |

Écarts **non tranchés**, qui demandent une décision : points ouverts 3 à 5 ci-dessous. Le nettoyage du code du dépôt `Civitas-EGEN-Business` suit la même règle ; il attend la décision du point ouvert 4 de ce dépôt-là pour le module `organization` déjà présent (voir `docs/plateforme/11-plan-par-phase.md`).

## Phase A de la Plateforme — côté Kernel

La Plateforme (voir [22](22-frontiere-avec-la-plateforme.md)) se construit au-dessus du Kernel. Seule sa **phase A** touche ce dépôt ; ses phases B à H sont suivies dans le dépôt `Civitas-EGEN-Business`, `docs/plateforme/11-plan-par-phase.md`.

- [x] **A1 — Documentation de la frontière** : document [22](22-frontiere-avec-la-plateforme.md) et corrections des documents 01 à 21.
- [x] **A2 — Convention de labels réservés** `platform.civitas.africa/*` ([06](06-service-manifest.md#labels-réservés-posés-par-la-plateforme)) : documentée ; test d'aller-retour de l'API livré (`ReservedLabelsRoundTripTest`), aucun changement de code.
- [x] **A3 — API de publication de configuration** : voir la Phase 4 ci-dessus (même élément, suivi à cet endroit).
- [x] **A4 — Règle d'architecture « Kernel ignorant de la Plateforme »** en CI ([22](22-frontiere-avec-la-plateforme.md#227--la-règle-darchitecture-a4)) : `PlatformBoundaryRuleTest`, avec une violation volontaire qui prouve que la règle échoue.
- [x] **A5 — Scénario de niveau 5** : voir la Phase 5 ci-dessus (même élément, suivi à cet endroit).
- [>] **A-X1** (Phase 1) et **A-X2** (Phase 4) : reportés, avec déclencheur.
- **Critère de sortie** : intégration continue verte ; `egen-domain` sans aucune modification liée à la Plateforme ; A3 testé aux niveaux 1, 2, 3, 4 et 5 ([17](17-strategie-de-tests.md)).

## Phase P0 proposée — Production du Kernel

*Proposition à valider par Samuel.* Chaque constat a été vérifié dans le code du dépôt le 29 octobre 2026.

| Constat | Vérification |
|---|---|
| Aucun `Dockerfile`, donc aucune image publiable | Aucun fichier `Dockerfile*` dans le dépôt |
| Aucun job Nomad du Kernel | Le dossier `deploy/` ne contient que `nomad-dev.sh`, `otel-collector-config.yaml` et `prometheus.yml` |
| Vault en mode développement | `docker-compose.yml` lance Vault avec `VAULT_DEV_ROOT_TOKEN_ID` |
| Un seul exemplaire du Kernel possible | La clé d'idempotence (`IdempotencyKeyFilter`) et les liens de traces (`PendingDeclareTraces`) sont gardés dans une `ConcurrentHashMap` du processus |
| mTLS désactivé par défaut | Les deux lignes de configuration sont commentées dans `application.properties` |
| Sidecar sans mTLS ni publication d'événements | Aucune occurrence de « publish », « mtls » ou « tls » dans les sources du sidecar |

Livrables proposés : un `Dockerfile` et une image publiée ; un job Nomad du Kernel ; Vault hors mode développement ; idempotence et liens de traces partagés entre exemplaires ; mTLS activé de bout en bout ; sidecar complété. Statut : `[?]` proposée.

## Points ouverts du Kernel

Aucun n'est tranché dans cette documentation. Décideur : Samuel.

**1. Amorçage circulaire.**
- *Problème* : sans émetteur de jetons OIDC configuré (`EGEN_OIDC_ISSUER_URL`, `EGEN_OIDC_JWKS_URL`), le Kernel **ne démarre pas du tout en production** (constaté le 10 novembre 2026, V1 : Quarkus traite ces valeurs vides comme absentes) ; or la Plateforme prévoit que le Kernel déploie Keycloak, l'émetteur de jetons.
- *Options* : (a) lancer Keycloak par la pipeline de déploiement **avant** le Kernel ; (b) fournir au Kernel une clé publique statique temporaire pour émettre le premier jeton `egen-admin`.
- *Impact* : détermine l'ordre de démarrage de toute installation neuve.
- Ce point est aussi consigné dans la phase C de la Plateforme (`docs/plateforme/11-plan-par-phase.md`).

**2. Validation de la phase P0.** Voir ci-dessus : faut-il la lancer, et avant ou après la phase A ?

**3. Surface gRPC ([13](13-api-et-contrats.md), [16](16-packages-et-stack-technique.md)).**
- *Problème* : les documents 13 et 16 décrivent une double surface REST + gRPC (`proto/egen_control.proto`, `egen-api/grpc`). Aucune phase ne la livre et le code n'a que REST.
- *Options* : (a) planifier une phase gRPC, avec son déclencheur ; (b) retirer gRPC des documents 13 et 16 jusqu'à ce qu'un client le demande.
- *Impact* : le contrat publié aux clients du Kernel.

**4. `DEGRADED → FAILED` ([09](09-cycle-de-vie.md)).**
- *Problème* : le document 09 dit que `DEGRADED` devient `FAILED` « si aucune amélioration après un délai configurable ». Aucun délai n'est appliqué ni documenté : un service reste `DEGRADED` indéfiniment, avec ses Conditions visibles. Rien n'a été inventé : passer automatiquement en `FAILED` interromprait une reprise normale par Nomad.
- *Options* : (a) fixer un délai configurable et sa valeur par défaut ; (b) retirer la phrase et assumer que seule une action humaine fait passer un service `DEGRADED` en `FAILED`.
- *Impact* : la sémantique opérationnelle de `FAILED` pour les alertes.

**5. Ports : écarts avec le pseudo-code de [07](07-ports-et-adapters.md).**
- *Problème* : `DiscoveryPort` n'a ni `health` ni `registerHealthCheck` ni le paramètre `dc` ; `MessagingPort.getBindingStatus` prend un sujet et un groupe de consommateurs et non un `ServiceId` ; l'état observé n'a pas de `MessagingObservation` ; l'exemple du document 20 montre `instances: 2` dans le statut, absent du contrat de [13](13-api-et-contrats.md). Le code est volontairement minimal (anti-pattern « évolution anticipée », [18](18-anti-patterns.md)).
- *Options* : (a) aligner le document 07 sur les ports livrés ; (b) ajouter ces méthodes quand un appelant en a besoin.
- *Impact* : le document 07 reste, en attendant, plus riche que le code.

**6. Durcissement strict des pods Kubernetes (constat K9, [24](24-revue-de-securite-externe.md)).**
- *Problème* : l'adapter pose maintenant les valeurs sûres qui ne cassent pas les images courantes (`seccompProfile: RuntimeDefault`, `automountServiceAccountToken: false`, `allowPrivilegeEscalation: false`). Il ne pose pas `runAsNonRoot`, `readOnlyRootFilesystem` ni `capabilities.drop: [ALL]`, parce qu'ils font échouer beaucoup d'images : l'image officielle `nginx` de notre test de niveau 3 démarre en root, écrit dans `/var/cache/nginx` et change d'utilisateur pour ses processus de travail.
- *Options* : (a) un champ optionnel du manifeste (par exemple `deployment.security`) par lequel le propriétaire du service déclare ce que son image supporte, l'adapter appliquant le plus strict possible par défaut ; (b) des valeurs strictes par défaut pour tous, avec une dérogation explicite par service ; (c) laisser ces trois points aux règles du cluster (Pod Security Admission), l'adapter ne posant que le socle actuel.
- *Impact* : le contrat du manifeste ([06](06-service-manifest.md)) si (a) ou (b) ; les images des services existants.

## Phase 6 (continue) — Extensibilité prouvée et durcissement

- [x] Ajouter un **second** adapter de déploiement **sans modifier
      `egen-domain` ni `egen-application`** — c'est le test ultime de la
      promesse architecturale. *Livré et validé (5 novembre 2026).*
      *Choix : `KubernetesDeploymentAdapter`* (Kubernetes a nativement
      réplicas, rolling update, historique et liste des pods : l'adapter
      traduit sans écrire de logique de planification ; un adapter Docker
      aurait dû réimplémenter ces mécanismes, contre le garde-fou n°3).
      Contrat normatif : [07](07-ports-et-adapters.md#kubernetes-adapter--second-adapter-de-déploiement).
      *Preuve que le cœur n'a pas changé :*
      `git diff 0216cd0 b1e5165 -- egen-domain egen-application` est vide :
      l'adapter n'a ajouté que le module `egen-adapters/egen-adapter-kubernetes`,
      des lignes d'interdiction dans les poms des autres adapters, et le
      câblage de `egen-bootstrap`.
      *Tests, verts chez Samuel :* niveaux 1 et 2 (faux serveur) ; niveau 3
      `KubernetesDeploymentAdapterIT` contre un vrai cluster k3s v1.31.2, avec
      de vrais pods, qui prouve que les droits minimaux du document 07
      suffisent et exerce création, convergence, idempotence, `scale`, mise à
      jour, `rollback`, `restart`, `stop` et `remove` ; build Maven complet des
      18 modules (dont la règle ArchUnit « un adapter ne dépend d'aucun autre »).
      Le module `k3s` de Testcontainers est volontairement écarté : il est
      incompatible avec le noyau 2.0.4 que le BOM de Quarkus impose (voir le
      pom du module).
      *Ajout décidé ensuite, par délégation de Samuel (4 novembre 2026) :*
      routage par service. Le champ `deployment.adapter` du manifeste est
      utilisé : un Kernel active une liste d'adapters
      (`egen.deployment.adapters`), chaque service choisit le sien, l'adapter
      d'un service est immuable, un `RoutingDeploymentPort` délègue. Ce
      changement touche `egen-application`, **séparément** de la preuve
      ci-dessus. Validé et mergé, avec un test HTTP de `POST /api/v1/services`
      (202 adapter activé, 400 adapter non activé, 409 adapter changé).
      Contrat : [07](07-ports-et-adapters.md#sélection-et-routage--un-moteur-par-service).
- [~] Chaos engineering (voir [17, niveau 6](17-strategie-de-tests.md#niveau-6--chaos-engineering-à-introduire-progressivement)).
      *Écrit, validé chez Samuel et mergé sur `main` :* niveau 6a,
      `ReconciliationEngineCrashRecoveryTest` — mort brutale du processus
      simulée à chaque point d'appel de port, pour un déploiement complet et
      pour un arrêt gracieux ; il a aussi établi que le moteur réapplique
      l'état désiré à chaque cycle. *Reste à faire :* niveau 6b (infrastructure
      réelle : latence vers Consul, coupure de NATS, redémarrage brutal d'un
      vrai Kernel), après choix de l'outil d'injection de pannes.
- [~] Revue de sécurité externe (pentest ciblé sur l'API de contrôle et la
      gestion des secrets).
      *Écrit :* le dossier de cadrage à remettre au relecteur,
      [24](24-revue-de-securite-externe.md) (surface, promesses du document 14
      face au code, constats déjà connus). Établi par relecture du code, **sans
      test d'intrusion**. *Deux failles trouvées et corrigées pendant sa
      rédaction :* C1 (une équipe pouvait redéclarer le service d'une autre) et
      C2 (cache d'idempotence partagé entre appelants). *Reste à faire :*
      trancher les constats K1 à K11 du document 24, puis choisir le relecteur
      externe et lui remettre le dossier.
- [~] Documentation opérateur complète (runbooks pour les scénarios
      `FAILED`, `COMPENSATION_FAILED`, divergence prolongée).
      *Écrit, validé et mergé :* [23](23-runbooks-operateur.md), d'après le
      comportement réel du code (pas la seule documentation) ; il a mis au
      jour le point ouvert 6, depuis traité (voir l'élément suivant) et
      reporté dans la section 1 du runbook. *Reste à faire :* relecture par
      un opérateur.
- [x] **Relance par `Declare`** (ancien point ouvert 6, option (a) décidée
      par Samuel le 6 novembre 2026) : un `Declare` accepté sur un service
      `FAILED` ou `STOPPED` le remet en `DECLARED`, Conditions effacées, et il
      repart comme un service neuf. Contrat :
      [09](09-cycle-de-vie.md#relance-par-declare). *Livré, validé chez Samuel
      et mergé sur `main` (6 novembre 2026) :* deux arêtes dans
      `LifecycleStateMachine`, la relance dans `DeployServiceUseCaseImpl`, sept
      tests dont deux de bout en bout avec le vrai moteur (vérifiés par test de
      mutation).

Sur la durée de vie du projet, chaque phase suivante ne doit jamais casser
les garanties déjà prouvées par la précédente — chaque livrable de phase
devient un test de non-régression permanent dans la CI.

---

## Definition of Done du projet EGEN

Une checklist vérifiable, à cocher lors de chaque revue d'architecture
trimestrielle.

**Architecture**

- [ ] EGEN est démontrablement un Control/Composition Plane (cycle de
      [04](04-moteur-de-reconciliation.md) implémenté et testé).
- [ ] Nomad exécute, Consul découvre, NATS/Kafka transporte — vérifié par
      le fait qu'aucune de ces responsabilités n'est dupliquée dans
      `egen-domain`/`egen-application` (voir [18](18-anti-patterns.md)).
- [ ] Toutes les intégrations passent par des adapters isolés (vérifié
      mécaniquement en CI).
- [x] Un second adapter de déploiement a été ajouté sans modification du
      cœur (Phase 6) — preuve empirique, pas déclaration d'intention : le
      diff de `egen-domain` et `egen-application` entre `0216cd0` et `b1e5165`
      est vide (voir la Phase 6).

**Services**

- [ ] Au moins deux services fixtures dans des langages différents ont
      été déployés avec succès via EGEN.
- [ ] Un service a été ajouté sans qu'aucun commit ne
      touche `egen-domain`, `egen-application`, ni `egen-adapters/*`.
- [ ] **Plateforme** : `egen-domain` n'a reçu aucune notion de la Plateforme (règle A4 verte).

**Infrastructure**

- [ ] Aucune capacité déjà fournie nativement par Nomad/Consul/NATS n'est
      réimplémentée dans le Kernel (audit de code périodique, garde-fou
      n°3 posé systématiquement en revue).

**Orchestration**

- [ ] Dépendances déclarées, ordonnancement topologique vérifié par test
      automatisé.
- [ ] Un workflow avec compensation a été exécuté en production ou en
      environnement de pré-production, avec un cas d'échec réel observé
      et correctement compensé.
- [ ] La divergence entre désiré et observé est mesurée en continu
      (métrique `egen_reconcile_failures_total` suivie, SLO de
      [15.3](15-observabilite.md#slo-du-kernel-lui-même) respecté).

**Qualité**

- [ ] La suite de tests de niveau 1 (domaine) s'exécute sans aucun
      conteneur ni accès réseau.
- [ ] Les règles ArchUnit sont actives en CI et bloquent effectivement une
      violation volontaire de test.
- [ ] La fréquence de modification de `egen-domain` diminue dans le temps
      (métrique suivie via l'historique Git — un cœur qui bouge encore
      beaucoup après la Phase 3 est un signal d'alerte architecturale, pas
      de vélocité).
