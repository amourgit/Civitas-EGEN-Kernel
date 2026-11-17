# 19 — Feuille de route — phases livrables

Chaque phase produit un **livrable démontrable**, pas seulement du code
intermédiaire. L'ordre est pensé pour que chaque phase s'appuie sur un
socle déjà testé, et pour dérisquer en premier les intégrations externes
(Nomad/Consul), qui sont la partie la plus incertaine techniquement.

## Suivi — légende et tableau de bord

Ce document est la **seule source de statut** des phases du Kernel, de la phase A de la Plateforme (côté Kernel), des phases P0 et G, du durcissement des pods (K9) et des points ouverts du Kernel (tous tranchés le 14 novembre 2026). Le statut des phases B à H de la Plateforme est dans le dépôt `Civitas-EGEN-Business`, `docs/plateforme/11-plan-par-phase.md`.

Légende : `[x]` fait · `[~]` en cours · `[ ]` à faire · `[>]` reporté, avec un **déclencheur écrit** (on ne le fait que si le déclencheur se produit) · `[?]` proposé, à valider.

Mise à jour : 10 novembre 2026. **Reprendre par la section « Reprise de session » ci-dessous.**

| Élément | Statut | Reste à faire |
|---|---|---|
| Phases 0 à 5 du Kernel | **Fait** | Compléments liés à la Plateforme, listés dans chaque phase : Phase 1 (A-X1 reporté), Phase 2 (décision D20 actée), Phase 4 (A3 fait, A-X2 reporté), Phase 5 (A5 fait) |
| Phase 6 (continue) | **En cours** | Éléments 1 (adapter Kubernetes, routage par service), 2 (chaos 6a), 3 (dossier de sécurité), 4 (runbooks) et la relance par `Declare` livrés, **validés et sur `main`**. Reste : chaos 6b, relecteur externe, relecture des runbooks par un opérateur |
| Chantier Système d'agents | **Partiel** | 1 élément fait, 4 à faire (voir le chantier) |
| Phase A de la Plateforme (côté Kernel) | **Fait** | A2 à A5 livrés |
| Phase P0 « production du Kernel » | **En cours** | Validée par Samuel le 14 novembre 2026 ; P0-1 et P0-2 écrites (image et job non encore essayés : V15, V17), P0-3 terminée (validé par `mvn verify` (18 modules, code 0, 17 novembre 2026, 03:16)), P0-3b écrite (à valider par `mvn verify`, essai réel V18), P0-4 à P0-6 non commencées |
| Points ouverts du Kernel | **Tous tranchés** | Réponses de Samuel du 14 novembre 2026 ; voir la section « Points ouverts du Kernel — tranchés » |
| Durcissement des pods Kubernetes (K9) | **En cours** | Option (a) ; K9-1 à K9-3 faites (code validé par `mvn verify` (18 modules, code 0, 16 novembre 2026, 03:38)), test de niveau 3 non écrit, K9-4 à faire |
| Phase G « surface gRPC » | **En cours** | Décidée par Samuel le 14 novembre 2026 ; G1 (contrat) faite, G2 à G6 non commencées |
| **Ordre de réalisation** | K9, puis P0, puis G | K9 est le plus court ; G suit P0 (une image et un job Nomad existent avant d'exposer une seconde porte) |
| Nettoyage du code non conforme (N1) | **Fait** | Audit complet du 3 novembre 2026 ; 3 écarts, tranchés le 14 novembre 2026 (points ouverts 3 à 5) |

## Reprise de session — point de situation du 13 novembre 2026

> **À lire en premier par quiconque reprend le développement.** Le reste du document dit *ce qui est prévu* ; cette section dit *où l'on en est exactement*, ce qui est validé, ce qui ne l'est pas, et dans quel ordre continuer.

### 1. Où est le code, et dans quel état est l'environnement

**Dépôt (tout est poussé ; aucun commit local en attente)**

| Branche | Contenu | Statut |
|---|---|---|
| `main` (`8cb89fa`) | tout le code validé (`bc33d0a`, `mvn verify` vert chez Samuel le 10 novembre 2026, 22:41 : K6, K8, K9, K10, autorisation à deux niveaux) + les docs fusionnées le 11 novembre sur ordre de Samuel | à jour |
| `docs/v10-keycloak` | **13 commits de plus que `main`** : docs 07, 14, 19, 23, 24 mis à jour par les vérifications V1 à V6 ; `deploy/keycloak/` (realm d'essai, scripts V10, V3c) ; `deploy/k3s/` (cluster d'essai, scripts V4, V5, V5b, V6, V6c) ; aucun code du Kernel | **non fusionnée** : attend l'accord de Samuel (voir 4) |
| `docs/*`, `feat/*`, `fix/*` (6 autres) | déjà contenues dans `main` | supprimables (`git push origin --delete <branche>`) |

Pour reprendre : `git fetch && git checkout docs/v10-keycloak && git pull`.

**Environnement de Samuel à la fin de la session (13 novembre 2026, ~09:45)** : Docker avec `egen-pg-v2` (PostgreSQL), `egen-kc` (Keycloak 26.8.2, realm `egen`), `egen-k3s` (cluster, service `v6-news` bloqué en `STOPPING`) ; le Kernel tourne dans le terminal T1 (adapter `kubernetes`, journal `/tmp/v45/kernel.log`). **`/tmp` et les variables disparaissent au redémarrage du poste** : tout se recrée par `bash deploy/k3s/v45-setup.sh`, puis `source /tmp/v45/env.sh`, puis `java -jar egen-bootstrap/target/quarkus-app/quarkus-run.jar 2>&1 | tee /tmp/v45/kernel.log` (le jar de `bc33d0a` est dans `egen-bootstrap/target/`). Nettoyage complet : `docker rm -f egen-consul egen-k3s egen-kc egen-pg-v2`.

**Bac à sable de Claude** (ce qu'il peut et ne peut pas faire) : ni Maven ni Docker, aucun accès à Maven Central ; Java 21 **JRE** avec le module `jdk.compiler` (compiler : `java -m jdk.compiler/com.sun.tools.javac.Main` ; contrôle de syntaxe seule d'un fichier Java : petit programme `JavacTask.parse()` lancé par `java --add-modules jdk.compiler Parse.java <fichiers>`, à recréer) ; `python3`, `openssl`, `curl` ; accès à `github.com` (clone, push, téléchargement de releases) mais l'API GitHub non authentifiée est limitée en débit (trouver la dernière version d'un projet par la redirection de `/releases/latest`) ; le shell des outils est **`sh` (dash)** : écrire `bash -c '…'` dès qu'on utilise `${var:0:7}` ou `export -f` ; **les processus de fond sont tués entre deux appels d'outil** : démarrer, tester et arrêter dans le même appel ; `pkill -f keycloak` tue le shell qui contient ce mot.

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

- **Code validé** par `mvn verify` chez Samuel (18 modules) : jusqu'à `bc33d0a`. Rien d'autre n'a été modifié dans le code depuis.
- **Comportements observés sur un vrai Kernel de production** (détails et chiffres dans « Faites », registre ci-dessous) : refus de démarrer avec le mot de passe de développement (V1) ; aucun chemin `/q/…` exposé (V2) ; aucun jeton Nomad, Consul, Vault, Kubernetes ni mot de passe dans le journal ni dans les réponses, et tous les jetons réellement envoyés (V3, V3b, V4/V5, V5b) ; corps d'erreur conservé et borné à 500 caractères (V3c, V5b) ; rôle `egen-admin` lu dans la claim `groups` avec Keycloak (V10) ; `revisionHistoryLimit = 10` (V4) ; vrais corps d'erreur Kubernetes `422` et `403` (V5) ; six promesses sur sept du scénario 1 du runbook (V6).
- **Non vérifié** : tout ce qui est encore dans le tableau « Vérifications en attente » (V6b, V7, V8, V10b, V11) ; l'exécution de **tous les scripts** `deploy/` n'a été rejouée par Claude qu'avec de faux outils (docker, curl) : seul Samuel les a lancés pour de vrai.

### 4. Décisions et accords attendus de Samuel, par priorité

1. **Deux accords** demandés plusieurs fois, sans réponse : (a) **fusionner `docs/v10-keycloak` dans `main`** (docs + fichiers d'essai `deploy/`, aucun code) ; (b) **cloner `Civitas-EGEN-Business` en lecture seule avec son jeton** pour faire V9 et V11 (le jeton a été donné pour le dépôt du Kernel : ne pas toucher un autre dépôt sans cet accord).
2. **Écarts décidés le 14 novembre 2026 (avis de Claude retenu), codés, validés par `mvn verify` (18 modules)** : **E4** arrêt bloqué par Consul (dé-enregistrement « au mieux », **validé en réel en V6a, modes `absent` et `retour`**) ; **E1** `discover` renvoie `503` quand Consul échoue ; **E2** cause d'un `503` de publication écrite dans une ligne `WARN` ; **E3** secret refusé retenté avec un délai croissant ; **I11** jamais « null » dans un message d'adapter ; **I17** réglé par E4. **Reste à décider** : I16 (Conditions d'un statut `FAILED`).
3. **K2** (audience du jeton) : Keycloak n'émet **pas** de claim `aud` pour un compte de service ; vérifier l'audience exigerait un mapper d'audience par client. **K3** (règle « tout refuser hors de `/api/v1/` ») : V2 montre que rien n'est exposé aujourd'hui ; règle = défense en profondeur, non décidée.
4. **Outil d'injection de pannes** pour le chaos 6b (V7) ; **cas de test** d'un retour arrière de plus d'une version (aucune route n'appelle `rollback`) ; **renommage** `OwnerTeam` → nom neutre (non fait : risque sans compilateur).

### 5. Prochaines étapes — **reprendre ici**

**V6a : terminée le 14 novembre 2026** (résultats dans « V6a, second essai » ci-dessous : `absent` et `retour` réussis). **Tâche suivante : V6b** (ci-dessous). Deux points restent ouverts de V6a : le cas d'un échec de `stop()` (voir « Observation, toujours non vérifiée ») et I14.

**Ensuite, dans l'ordre** :
1. **V6b** : relecture des scénarios 2 et 3 du runbook (`COMPENSATION_FAILED`, divergence prolongée) ; méthode : suivre chaque étape du runbook sur un Kernel réel, annoncer ce que le runbook promet, noter tout ce qui diffère ou qu'une personne qui ne l'a pas écrit ne comprendrait pas.
2. **V9 et V11** dès que Samuel autorise le dépôt `Civitas-EGEN-Business` (lecture seule : `docs/plateforme/02-registre-des-decisions.md` pour D14 ; recherche des clients du Kernel qui filtrent par équipe ou attendent un `403` fondé sur l'équipe).
3. **V7** (chaos 6b) après le choix de l'outil ; **V8** (revue externe) : choisir le relecteur, lui remettre le doc 24 ; **V10b** quand un vrai realm Keycloak existe (commandes du doc 23).
4. **Développement**, seulement après décision de Samuel sur E1 à E4, I11, K2, K3 : **contrat dans la doc d'abord** (docs 07, 09, 13, 23 selon le cas), puis le code (adapters, API), puis les tests (niveau 1 : unitaire ; niveau 2 : faux serveur du JDK ; niveau 3 : conteneur), sur une branche dédiée ; Claude ne peut pas compiler : il fait vérifier la syntaxe (analyseur du JDK) et **Samuel valide par `mvn verify`** avant toute fusion de code dans `main`.

4. Écarts relevés, non traités : `egen_services_total` est un compteur par cycle et non une jauge ; `actions/restart`, `actions/rollback`, `/history` et `/adapters` (doc 13) ne sont pas livrés ; la liste « critères de fin » de la fin de ce document contient des cases encore ouvertes.

   **Écarts relevés par les vérifications du 10 et 11 novembre 2026 (V1 à V3), chacun à décider par Samuel, aucun n'est une fuite de secret :**
   - **E1, découverte** : quand Consul répond une erreur, `GET /api/v1/discover/{nom}` renvoie un `500` générique (`Error id …`, aucun détail) : `DiscoveryException` n'est convertie par aucun mappeur. Le message d'adapter est dans le journal (« Consul a rejete resolve(v3-news) : HTTP 500 — … » avec la trace). Le doc 13 ne précise aucune erreur pour cette route ; les autres routes renvoient `503` quand leur store est indisponible. *Question* : renvoyer `503` ? **Décidé le 14 novembre 2026 : oui (avis de Claude retenu). Codé et documenté (doc 13), validé par `mvn verify` (18 modules, code de sortie 0, 14 novembre 2026, 09:33).**
   - **E2, publication de configuration** : quand Consul répond une erreur, `PUT …/configuration` renvoie `503` **sans corps**, conformément au doc 13 (« Store indisponible : `503`, rien n'est publié »), mais la cause (code HTTP et corps de la réponse de Consul) **n'est écrite nulle part** dans le journal : elle n'est enregistrée que dans la trace (`span.recordError`), invisible sans collecteur OpenTelemetry. *Question* : ajouter une ligne de journal `WARN` portant le message d'adapter, comme le contrat K8 le conserve ailleurs pour déboguer ? **Décidé le 14 novembre 2026 : oui (avis de Claude retenu). Codé pour la publication et documenté (doc 13), validé par `mvn verify` (18 modules, code de sortie 0, 14 novembre 2026, 09:33).**
   - **E4, arrêt bloqué par Consul** (relevé en V6, 13 novembre 2026) : `actions/stop` d'un service dont l'instance ne peut pas être dé-enregistrée de Consul laisse le service **`STOPPING` indéfiniment** (au moins 2 min 45, tentatives toutes les 30 à 60 secondes sans plafond, Deployment conservé, pod toujours disponible). Le plafond de 5 échecs qui mène à `FAILED` ne s'applique pas à l'arrêt : contraire à « jamais de retry infini silencieux » (doc 09), seul le journal le dit. *Question* : dé-enregistrement « au mieux » (continuer et supprimer le Deployment après les 5 échecs, avec une Condition qui signale les entrées Consul restantes) ou `FAILED` au 5e échec ? **Décidé le 14 novembre 2026 : au mieux (avis de Claude retenu). Codé (doc 09, « Quand le dé-enregistrement échoue » ; runbook doc 23), validé par `mvn verify` (18 modules, code de sortie 0, 14 novembre 2026, 09:33). Règle aussi I17 et I18. Validé en réel le 14 novembre 2026 (V6a, modes `absent` et `retour`).** *Avis de Claude* : au mieux, car un arrêt qui ne s'exécute pas pendant un incident est plus grave qu'une entrée Consul périmée que ses contrôles de santé retirent.
   - **E3, secret non résolu** : un service dont le secret Vault est refusé est retenté **toutes les 3 secondes, sans fin ni délai croissant** (relevé : environ 45 appels à Vault entre 10:33:29 et 10:35:45, et 3 lignes de journal par tentative, soit près de 3 600 lignes par heure et par service bloqué). Le doc 09 dit « retentée à chaque cycle » sans durée, et pose ailleurs (état `FAILED`) le principe « **jamais de retry infini silencieux**, le compteur d'échecs est plafonné et visible » : ici le compteur n'est pas plafonné (même si chaque tentative est journalisée). *Question* : un délai croissant, comme pour les échecs de déploiement (2, 4, 8, 16 secondes, puis arrêt) ? **Décidé le 14 novembre 2026 : oui (avis de Claude retenu). Codé (délai de 2, 4, 8, 16, 32 s puis 60 s au plus, sans arrêt : le service reste `REGISTERED`, doc 09), validé par `mvn verify` (18 modules, code de sortie 0, 14 novembre 2026, 09:33).**

### 6. Règles de travail à ne pas perdre

- **`main` ne reçoit que du code validé** par un `mvn verify` vert chez Samuel, en avance directe. Samuel peut ordonner un merge explicite (cela a eu lieu le 6 novembre) : dans ce cas, le dire clairement dans la section 1, comme ci-dessus.
- **La documentation d'abord** : le contrat normatif s'écrit avant le code. Tout fait externe est vérifié dans une source (code, dépôt officiel) ou marqué « à vérifier ».
- **Des commits explicites** : un titre qui dit la fonctionnalité touchée, un corps qui liste modules, fichiers, tests et état de vérification. Des commits supplémentaires plutôt qu'une réécriture d'historique.
- **Ne jamais écrire un jeton ou un mot de passe** dans un fichier, un commit ou une doc. Le jeton GitHub qui a servi à pousser a été exposé dans une conversation : **à révoquer**.
- **Relever chaque incohérence ou ambiguïté d'un test** (demande de Samuel, 11 novembre 2026) : à chaque test, comparer ce qu'on obtient à ce qu'on devrait normalement recevoir en sortie ; tout écart, même non critique, s'écrit dans « Incohérences et ambiguïtés relevées dans les tests » (section du registre) avec sa gravité, pour y revenir et ajuster plus tard. Un écart critique (fuite de secret, droit contourné, perte de données) interrompt les tests et se traite tout de suite.
- **Noter toute vérification non faite** : une vérification que Samuel n'a pas faite, ou dont personne n'a de résultat, s'écrit dans « Vérifications en attente » (début du document), avec qui peut la faire et le résultat attendu. Chaque réponse de fin de travail rappelle ce qui est **vérifié ici** et ce qui **ne l'est pas**.
- **Vérifier avant de conclure** : relire le code plutôt que la seule doc, essayer plusieurs motifs de recherche avant d'affirmer qu'une chose « n'existe pas », et prouver qu'un test peut échouer (test de mutation).

### 7. Pièges déjà rencontrés

- **Un script Python qui échoue ne bloque pas la commande suivante** : le 13 novembre, une fonction appelée avec un argument de trop a laissé un commit avec seulement une partie des changements. Toujours `python3 … && git add …`, ou vérifier la sortie du script avant de commiter, et relire `git diff --stat`.
- **Un script lancé avec un chemin relatif** (`bash deploy/…/x.sh`) ne retrouve pas ses fichiers s'il fait un `cd` avant de calculer son propre dossier : calculer `HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)` en premier, et **tester la commande exacte que Samuel tapera** (chemin relatif, depuis la racine du dépôt).
- **Le rôle du Kernel dans Kubernetes n'a pas `list` sur les deployments** (moindre privilège voulu) : un contrôle d'accès ne doit pas lister les deployments (utiliser la liste des pods).
- **Les messages d'erreur de Consul se terminent par `: null`** quand l'exception n'a pas de texte (écart I11) ; ne pas s'en servir pour chercher une cause.

- **Testcontainers** : le BOM de Quarkus, importé en premier dans le `pom.xml` racine, fixe le noyau `testcontainers` à 2.0.4. Le module `k3s` 1.20.4 est incompatible (`NoClassDefFoundError … shaded … IOUtils`) : on utilise un `GenericContainer` configuré comme `K3sContainer`.
- **Règle A4** (`PlatformBoundaryRuleTest`) : des mots réservés à la Plateforme sont interdits dans les identifiants du code du Kernel (relire la liste dans le test ; `catalog` a fait échouer un build, `DeploymentAdapterCatalog` est devenu `EnabledDeploymentAdapters`).
- **`Map.copyOf` et `Set.of`** renvoient des collections dont l'ordre d'itération change à chaque lancement de JVM : ne jamais les utiliser quand l'ordre apparaît dans un message ou un résultat.
- **Le Registry attribue lui-même la génération** à l'enregistrement : `save(new DesiredState(…, 0L, …))` donne la génération 1. Un test ne doit jamais supposer une génération absolue, mais comparer à la valeur observée avant.
- **Le moteur réapplique l'état désiré à chaque cycle** (`create()` rappelé toutes les ~10 s) : toute modification manuelle dans Nomad ou Kubernetes est annulée ; la sûreté repose sur l'idempotence de chaque appel.
- **Un recensement par recherche textuelle peut rater une forme d'appel** : la durée de réconciliation était émise via `recordDuration(new MetricSample("egen_…"))`, que mon premier motif ne couvrait pas, et deux documents ont affirmé à tort qu'elle n'existait pas.
- **Un script de modification qui échoue à mi-chemin** laisse des changements partiels : lire `git status` avant de commiter, et vérifier que le message de commit décrit bien ce qui est dedans.
- **Deux jetons expirent pendant un essai** : le jeton Kubernetes du Kernel créé par `v45-setup.sh` (2 h jusqu'au 14 novembre 2026, 8 h depuis ; au-delà, Kubernetes répond `401` au Kernel) et le jeton Keycloak d'un script (300 s par défaut : un script de plus de 5 minutes doit en redemander un à chaque appel, sinon il lit des réponses vides et croit que le Kernel est arrêté). `v6a-run.sh` contrôle les deux au départ.

### 8. Méthode de test — comment on formule et fait avancer chaque vérification

**Principes (demandés par Samuel ou appris en route)**

1. **Une vérification à la fois, du début à la fin**, avant de passer à la suivante ; Samuel a demandé d'être guidé **pas à pas**, en supposant qu'il n'a **rien de déployé** (tout se monte par script).
2. **Annoncer l'attendu avant de lancer** : chaque étape dit « Attendu : … » ; on compare ligne à ligne la sortie à l'attendu et **tout écart** (même petit, même de la faute de Claude) va dans « Incohérences et ambiguïtés relevées dans les tests » (Samuel : « tant que ce n'est pas critique, on y reviendra »). Un écart **critique** (fuite de secret, droit contourné, perte de données) interrompt les tests.
3. **Un test qui ne peut pas échouer ne prouve rien** : prouver que le chemin de code a été exercé (les erreurs d'adapter sont bien dans le journal), faire un **contrôle positif** (le test voit la fuite quand on la plante), et au besoin un test de mutation. Un faux serveur qui **affiche ce qu'il reçoit** (en-têtes de jeton) prouve que le jeton part réellement.
4. **Tester les scripts avant de les donner** : syntaxe (`bash -n`), puis déroulement complet avec de faux `docker`, `curl` et `sleep` placés en tête du `PATH`, avec des réponses réalistes ; dire clairement ce qui a été rejoué ici et ce qui ne l'a pas été (« NON vérifié : le vrai Docker, le vrai Kernel »).
5. **Distinguer « vérifié ici » et « non vérifié »** à la fin de chaque réponse et dans chaque message de commit ; toute vérification non faite s'écrit dans « Vérifications en attente » avec qui peut la faire et le résultat attendu.
6. **Ne jamais demander ni écrire un jeton ou un mot de passe réel** : lire les claims sans afficher le jeton, chercher les canaris `CANARI-…` plutôt que de vrais secrets ; les secrets des realms d'essai sont des secrets de test.
7. **Documentation d'abord, puis script, puis résultat** : le contrat s'écrit dans la doc ; les observations réelles (chiffres, heures, extraits) y sont ajoutées avec la mention « Observé le <date> » ; ce qui contredit la doc est un écart, pas une correction silencieuse.
8. **Ne rien inventer** : relire le code avant d'affirmer ; les corps d'erreur, versions et noms de champs externes sont observés ou marqués « à vérifier » ; ne pas toucher un autre dépôt sans accord.

**Gabarit d'un message de test à Samuel** : titre (`V<n> : …`) ; *Ce qu'on vérifie* (une phrase, termes définis) ; *Ce que j'ai lu dans le code* ; **étapes numérotées**, chacune avec son bloc de commandes (chemins absolus ou relatifs à la racine du dépôt), **Attendu**, et « si autre chose : ne rien modifier, envoyer la sortie » ; *Ce que tu m'envoies* (liste précise) ; nettoyage. Terminaux nommés **T1** (Kernel, journal enregistré par `tee`), **T2** (commandes), **T3** (faux serveurs).

**Gabarit d'enregistrement** (dans ce document, branche docs, commit explicite) : (a) ligne retirée du tableau « Vérifications en attente » ; (b) entrée « Faites » : `V<n> (date, heure, jar/commit, environnement, script)` + chiffres relevés + **Limites** + **Observations** ; (c) une ligne par écart dans le tableau des incohérences (n°, test, attendu, obtenu, gravité, statut) ; (d) mise à jour des docs concernées avec « Observé le … » (07, 14, 23, 24) ; (e) commit + push, puis résumé à Samuel : résultat, écarts, étape suivante.

**Techniques déjà éprouvées** : faux serveurs Python (`ThreadingHTTPServer`) pour Nomad, Consul, Vault qui répondent une erreur et affichent le jeton reçu (modèle dans `deploy/keycloak/v3c-run.sh` ; réponse d'un corps long pour tester la borne de 500 caractères) ; jeton de test signé RS256 avec `openssl` et clé publique donnée au Kernel par `EGEN_OIDC_JWKS_URL=file://…` ; Keycloak 26.8.2 dans Docker avec import d'un realm (`deploy/keycloak/egen-realm-test.json`) et mapper `groups` ; k3s dans Docker avec les options du test de niveau 3 ; journal du Kernel enregistré par `tee` et interrogé par `grep -c` ; mesure par expression régulière (`HTTP 500 — (X+)( \[tronque\])?`).

**Catalogue des scripts** (`deploy/`, tous lancés depuis la racine du dépôt, après `git checkout docs/v10-keycloak && git pull`)

| Script | Sert à | Prérequis |
|---|---|---|
| `keycloak/v10-setup.sh` | Keycloak d'essai + PostgreSQL + `news.yaml` | Docker |
| `keycloak/v10-claims.sh CLIENT SECRET` | claims d'un jeton, sans l'afficher | Keycloak |
| `keycloak/v10-run.sh` | V10 : codes HTTP par appelant, porteurs du rôle | Kernel + Keycloak |
| `keycloak/v3c-run.sh` | V3c : corps de 1 200 caractères coupé à 500 | Kernel de V10, port 4646 libre |
| `k3s/v45-setup.sh` | cluster k3s, RBAC du Kernel, jeton, `env.sh` (recrée aussi PostgreSQL) | Docker |
| `k3s/v45-run.sh` | V4 et V5 : Deployment, `revisionHistoryLimit`, fuite du jeton, `422` et `403` réels | Kernel adapter `kubernetes` |
| `k3s/v5b-run.sh` | V5b : chemin d'erreur Kubernetes par le Kernel | idem, après `v45-run.sh` |
| `k3s/v6-run.sh` | V6 : scénario 1 du runbook | idem |
| `k3s/v6c-run.sh` | V6a : reprise d'un arrêt bloqué quand Consul revient | Kernel et cluster de V6 en marche |
| `k3s/v6a-run.sh absent` ou `retour` | V6a rejoué sur l'arrêt corrigé (E4) : Consul arrêté pendant tout l'arrêt, ou relancé 7 s après la demande | Kernel redémarré avec le jar de `mvn verify`, cluster de `v45-setup.sh` |
| `k3s/v45-clean.sh` | supprime le cluster | |

**Références** : stratégie de tests par niveaux dans le [document 17](17-strategie-de-tests.md) ; identifiants : V1 à V11 (vérifications), E1 à E4 (écarts décidés et faits le 14 novembre 2026), I1 à I21 (incohérences), K1 à K11 (constats de sécurité, [24](24-revue-de-securite-externe.md)).

## Vérifications en attente — non faites, ou sans résultat

**Règle (demandée par Samuel, 9 novembre 2026).** Toute vérification qui n'a pas été faite, ou dont personne n'a obtenu de résultat, est notée **ici**, et nulle part ailleurs sous forme de liste. Ce n'est pas un oubli : Samuel les fera plus tard. Une ligne n'est retirée que lorsqu'un **résultat daté** est écrit dans la colonne de droite ; elle passe alors dans « Faites ». Un document peut renvoyer à cette section, jamais la dupliquer.

| N° | Vérification | Qui peut la faire | Résultat attendu | Où c'est mentionné |
|---|---|---|---|---|
| V6 | **Reste de V6 : (b) seulement** : relecture des scénarios 2 (`COMPENSATION_FAILED`) et 3 (divergence prolongée) du runbook par une personne qui ne les a pas écrits. *(V6a est terminé le 14 novembre 2026.)* | Samuel | aucune étape incompréhensible | [23](23-runbooks-operateur.md) |
| V7 | Chaos 6b : latence vers Consul, coupure de NATS, redémarrage brutal d'un vrai Kernel | Samuel, après le choix de l'outil d'injection de pannes | Le Kernel converge de nouveau sans intervention | [17](17-strategie-de-tests.md), niveau 6 |
| V8 | Revue de sécurité externe ; le relecteur confirme aussi la configuration NATS (`egen.messaging.*`) | le relecteur, à choisir | Un rapport sur le document 24 | [24](24-revue-de-securite-externe.md) |
| V10b | **Suite de V10** : dans le **vrai** realm Keycloak de la Plateforme (quand il existe : au 11 novembre 2026 aucun Keycloak ne tourne chez Samuel), seuls `platform-operator-service` et la pipeline de déploiement portent `egen-admin`, directement, par un groupe ou par un rôle composite ; le rôle n'est pas dans les rôles par défaut ; le mapper `groups` est présent sur chaque client appelant | Samuel | Les commandes du doc 23 (« Vérifier qui porte le rôle egen-admin ») donnent exactement les deux comptes de service attendus, des groupes vides ou de confiance, et des rôles par défaut sans `egen-admin` | [14](14-securite.md), [23](23-runbooks-operateur.md), [24](24-revue-de-securite-externe.md) K11 |
| V11 | **Suite de V11** : quand le code des clients du Kernel de la Plateforme existera (`platform-operator-service`, passerelle, console, SDK ; au 14 novembre 2026 il n'existe pas), vérifier qu'aucun n'attend un `403` fondé sur l'équipe ni une liste filtrée par équipe. *(Les documents de `Civitas-EGEN-Business` sont corrigés : commit `3c3d3b7`.)* | Samuel | Aucun appelant ne dépend d'un `403` fondé sur l'équipe ni d'une liste filtrée | [14](14-securite.md) | 
| V12 | **Surface gRPC** (phase G) : rejouer la session `grpcurl` du doc 13 (`Declare`, `WatchStatus`, `Resolve`, `Stop`) contre un Kernel local | Samuel | chaque appel répond comme le doc 13 le décrit ; `WatchStatus` reçoit les phases jusqu'à `RUNNING`, puis `STOPPED` | [13](13-api-et-contrats.md) |
| V13 | **Vault hors mode développement** (étape P0-4) : rejouer de zéro la procédure d'initialisation et de descellage, puis lire un secret avec le jeton à politique minimale | Samuel | lecture autorisée sur `secret/data/egen/*`, `403` ailleurs | [14](14-securite.md) |
| V14 | **Installation neuve** (critère de sortie de P0) : Keycloak, puis le Kernel dans son image lancé par le job Nomad, sur un poste vierge | Samuel | `GET /api/v1/health` → `200` ; un service déclaré atteint `RUNNING` | [19](19-feuille-de-route.md) |
| V15 | **Image du Kernel** (étape P0-1) : `docker build -t egen-kernel:dev .`, puis lancer avec la commande de `deploy/README.md`, puis relancer avec `--read-only --tmpfs /tmp` | Samuel | l'image se construit ; `GET /api/v1/health` → `200` dans les deux cas ; `docker run --rm egen-kernel:dev id -u` affiche `10001` ; taille de l'image relevée | [19](19-feuille-de-route.md) |
| V16 | **Règle K3** (tout refuser hors de `/api/v1/`) : sur un Kernel démarré, `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/health` puis le même appel sur `/q/health` et sur `/nimportequoi` | Samuel | `/api/v1/health` → `200` ; les deux autres chemins refusés (`401` ou `403`, jamais `200`) ; un appel authentifié sur `/api/v1/services` reste accepté | [14](14-securite.md) |
| V17 | **Job Nomad du Kernel** (étape P0-2) : `nomad job validate`, `plan`, puis `run` sur le Nomad de `deploy/nomad-dev.sh` avec l'image `egen-kernel:dev` (V15 faite avant) | Samuel | `validate` et `plan` sans erreur ; allocation `running`, contrôle `passing` ; `curl` sur l'adresse donnée par `nomad service info egen-kernel` → `200` | [19](19-feuille-de-route.md) |
| V18 | **Deux Kernels** (étape P0-3b) : lancer deux Kernels sur la même base, déclarer un workflow de plusieurs étapes lentes, tuer le Kernel pilote en pleine exécution | Samuel | l'autre Kernel reprend l'exécution dans les 90 secondes ; chaque étape réussie n'est appelée qu'une fois ; aucune erreur de bail dans les journaux | [11](11-moteur-de-workflow.md) |


### Incohérences et ambiguïtés relevées dans les tests

*Demande de Samuel (11 novembre 2026) : relever à chaque fois ce qui diffère de ce qu'on devrait normalement recevoir, et y revenir pour ajuster tant que ce n'est pas critique. Aucune ligne ci-dessous n'est une fuite de secret ni un contournement de droit.*

| N° | Test | Attendu | Obtenu | Gravité | Statut |
|---|---|---|---|---|---|
| I1 | V1, mot de passe absent | message du Kernel « Démarrage refusé en production … » | message générique de Quarkus (« Failed to load config value … quarkus.datasource.password ») | non critique (le refus est sûr) | docs 14, 23, 24 corrigés ; code inchangé |
| I2 | V1, sans émetteur OIDC | le Kernel démarre et refuse les modifications (doc 19, point ouvert 1) | le Kernel ne démarre pas du tout | non critique (refus sûr) | doc 19 corrigé |
| I3 | V2, chemin `/` | non documenté | `404` | sans gravité | à documenter si besoin |
| I4 | V3, consigne de test (Claude) | « `PUT /v1/job/…` ou `POST /v1/jobs` » (consigne ambiguë) | `PUT /v1/jobs` | sans gravité | la consigne était imprécise |
| I5 | V3, journal | une entrée de journal par événement | un corps d'erreur de 11 lignes est écrit sur 11 lignes du journal | non critique | décidé et codé le 14 novembre 2026 (doc 07) ; validé par `mvn verify` (18 modules, code 0, 16 novembre 2026, 03:38) |
| I6 | V2/V3, OpenTelemetry sans collecteur | non documenté (doc 15 muet) | erreur `SEVERE` d'environ 35 lignes toutes les 60 secondes | non critique | décidé et codé le 14 novembre 2026 (doc 15) ; validé par `mvn verify` (18 modules, code 0, 16 novembre 2026, 03:38) |
| I7 | V3b, `PUT …/configuration` quand Consul échoue | `503` avec la cause dans le journal | `503` sans corps et cause absente du journal | moyenne (débogage) | **E2** |
| I8 | V3b, `GET /discover/{nom}` quand Consul échoue | `503` comme les autres routes | `500` générique | moyenne | **E1** |
| I9 | V3b, secret Vault refusé | délai croissant comme pour le déploiement | nouvelle tentative toutes les 3 secondes, sans fin | non critique | **E3** |
| I10 | V10, essai Keycloak local de Claude | le Kernel lit le rôle `egen-admin` dans la claim `groups` (défaut MicroProfile JWT, non écrit dans la doc 14 avant cet essai ; V3 l'a confirmé avec un jeton de test) | Keycloak met par défaut les rôles dans `realm_access.roles` ; **un client sans mapper porte le rôle mais n'a pas de claim `groups`** (observé) | moyenne : sans mapper, toute modification serait `403` | documenté dans 14 (« Jetons attendus par le Kernel ») ; à confirmer contre le Kernel chez Samuel (V10) |

| I11 | V10, journal du Kernel (Nomad absent) | un message d'échec qui dit pourquoi (par exemple « Connection refused ») | `Appel Nomad impossible (PUT http://localhost:4646/v1/jobs) : null` : le texte de l'exception est vide et on l'écrit tel quel. Même construction dans les adapters Consul (2 endroits), Kubernetes et Vault (5 endroits au total) ; **observé aussi avec Consul** le 12 novembre 2026 (`Appel Consul impossible (GET …/v1/health/service/v45-news?passing=true) : null`) | moyenne (débogage, même esprit que le contrat K8) | décidé le 14 novembre 2026 : écrire le nom de l'exception quand son message est vide ; **codé** (`ErrorText`, doc 07), validé par `mvn verify` (18 modules, code de sortie 0, 14 novembre 2026, 09:33) |
| I12 | V10, deux `Declare` à la suite sur le même service | doc 09 : chaque `Declare` crée une nouvelle génération (conforme : génération 1 puis 2) ; le doc ne dit pas comment les échecs sont comptés | le compteur d'échecs d'adapter est **par service, pas par génération** tant que le service réessaie : `(1/5)` pour la génération 1 puis `(2/5)` pour la génération 2, à 9 ms d'intervalle ; en revanche il **repart à `(1/5)`** quand on relance un service `FAILED` par un nouveau `Declare` (V6, 13 novembre 2026) | non critique | documenté dans le doc 09 (« Comptage des échecs d'adapter »), 14 novembre 2026 |

| I13 | V4/V5, `v45-setup.sh` (mon contrôle) | `200` pour la liste des deployments avec le jeton du Kernel | `403` : le rôle du Kernel n'a pas le droit `list` sur les deployments (moindre privilège voulu) ; c'était une erreur de mon contrôle, pas du Kernel | sans gravité | script corrigé (liste des pods, que le rôle peut lire) |
| I14 | V4/V5, service `v45-news` (adapter Kubernetes, pod prêt en 57 s) | `RUNNING`, ou au moins l'échec d'un déploiement | pods `1/1` prêts **mais** le service passe `FAILED` : `Appel Consul impossible (GET http://localhost:8500/v1/health/service/v45-news?passing=true) : null`, 5 tentatives (2, 4, 8, 16 s), `DeploymentReady=FALSE`. La santé des instances vient de Consul, que mon test n'avait pas lancé, et le pod nginx ne s'y enregistre pas | sans gravité pour le Kernel (cohérent avec le doc 09) ; **omission de mon test** | documenté dans `deploy/k3s/README.md` (« Limite connue ») : pour atteindre `RUNNING`, il faut Consul et un service qui s'enregistre ; le Déploiement reste dans le cluster quand le service est `FAILED` (compensation non testée) |

| I15 | V5b/V6, recoupement du runbook (doc 23, scénario 1) | le runbook couvre les messages qui font tomber un service en `FAILED` | il couvre Kubernetes et Nomad, **pas Consul** : un Consul injoignable fait passer un service `FAILED` même avec des pods prêts (observé deux fois) | moyenne (un opérateur ne trouverait pas la cause) | **corrigé dans le doc 23** (paragraphe « Adapter Consul ») ; **étendu le 13 novembre 2026 (V6)** : arrêter un service passe aussi par Consul (dé-enregistrement des instances), le runbook ne le disait pas |

| I16 | V6, étape 3 : `GET /status` d'un service `FAILED` (génération 1, droit Kubernetes manquant) | la liste des Conditions du doc 09 | seulement trois Conditions (`ManifestValid`, `ConfigResolved`, `DeploymentReady`) alors que le journal avait posé aussi `DependenciesSatisfied` et `MessagingBindingsReady` (`TRUE`) ; la génération 2 `FAILED` (Consul absent) les affiche toutes les cinq | non critique | décidé et codé le 14 novembre 2026 (doc 09) : cause trouvée, les Conditions étaient enregistrées après l'appel au moteur de déploiement ; validé par `mvn verify` (18 modules, code 0, 16 novembre 2026, 03:38) |

| I17 | V6, `GET /status` d'un service `STOPPING` bloqué (Consul absent) | une Condition qui dit pourquoi l'arrêt n'avance pas | la Condition `DeploymentReady = FALSE`, raison `AdapterFailureThresholdExceeded`, **héritée du déploiement `FAILED` précédent** ; aucune Condition ne signale l'échec du dé-enregistrement (la cause n'est que dans le journal) | moyenne (débogage, même esprit que E2) | réglé par E4 : la Condition `DiscoveryDeregistered` dit pourquoi (validé en réel, V6a, 14 novembre 2026) |
| I18 | V6, journal d'un arrêt bloqué | un compteur plafonné à 5 | `(6/5)`, `(7/5)`, `(8/5)` : le compteur dépasse son plafond sans que la phase devienne `FAILED` | non critique (trompeur) | réglé par E4 pour le dé-enregistrement (validé en réel, V6a) ; décidé et codé le 14 novembre 2026 pour `stop()` (doc 09, « Quand l'arrêt lui-même échoue ») ; validé par `mvn verify` (18 modules, code 0, 16 novembre 2026, 03:38) |
| I19 | V6a, `POST /api/v1/services` (Declare) sur un service resté `STOPPING` | le doc 09 ne prévoit la relance par `Declare` que depuis `FAILED` ou `STOPPED` | `202`, génération 3 créée, la phase reste `STOPPING` | à évaluer | décidé et codé le 14 novembre 2026 : `409` (doc 09 et 13) ; validé par `mvn verify` (18 modules, code 0, 16 novembre 2026, 03:38) |
| I20 | V6a, `POST …/actions/stop` sur un service `DEPLOYING` | non documenté | `400` : la table des transitions n'a pas d'arête `DEPLOYING → STOPPING` ; le service devient `FAILED` environ 30 à 50 s plus tard, d'où l'arrêt devient possible | non critique | documenté dans le doc 09 (« Quand une demande d'arrêt est acceptée »), 14 novembre 2026 ; autoriser l'arrêt depuis `DEPLOYING` serait un changement de la machine à états, décidé le 14 novembre 2026 : on garde la règle actuelle |
| I21 | V6a, consigne de test (Claude) : les scripts `v6-run.sh`, `v6c-run.sh` et `v6a-run.sh` attendaient `NotFound` après un arrêt | le doc 07 dit que `stop` = `scale(id, 0)` et que le Deployment **reste**, à 0 réplica | `0/0` présent après `STOPPED` (ton résultat du 14 novembre, 21:15) | sans gravité | scripts corrigés (attendu : présent, `0/0`) |

**Faites** (avec leur résultat) : **V6, scénario 1 du runbook (13 novembre 2026, 09:37 à 09:42, jar de `bc33d0a`, k3s v1.31.2, `deploy/k3s/v6-run.sh`)** : confirmées : `FAILED` au 5e échec (`DeploymentReady = FALSE`, `AdapterFailureThresholdExceeded`) ; un service `FAILED` n'est pas réessayé (5 échecs avant et après 20 s) ; corriger la cause (RoleBinding recréé) ne relance rien ; une clé d'idempotence déjà utilisée rejoue l'ancienne réponse (génération 1) sans rien exécuter ; une nouvelle clé crée la génération 2, repart de `DECLARED`, efface `DeploymentReady` et le Kernel crée le Deployment ; sans Consul le service retombe `FAILED` (I14) avec un compteur remis à `(1/5)`. **Arrêt** : `actions/stop` répond 202, la phase devient `STOPPING`, le Kernel essaie de dé-enregistrer le pod dans Consul (`PUT …/v1/agent/service/deregister/<pod>`), échoue 5 fois (2, 4, 8, 16 s) puis continue (`(6/5)` à 09:40:21, `(7/5)` à 09:41:21, `(8/5)` à 09:41:59) : **au moins 2 min 45 plus tard le service est toujours `STOPPING`, le Deployment toujours `1/1`** (écarts E4, I17, I18). Le texte `: null` (I11) apparaît 10 fois dans ce journal. **V6, scénario 1 du runbook (13 novembre 2026, 09:37 à 09:40, jar de `bc33d0a`, k3s v1.31.2, script `deploy/k3s/v6-run.sh`)** : **promesses confirmées** : (1) `FAILED` au 5e échec consécutif avec `DeploymentReady = FALSE` et `AdapterFailureThresholdExceeded` ; (2) le moteur ne réessaie pas un service `FAILED` (5 échecs journalisés avant et après 20 secondes) ; (3) corriger la cause (recréer le RoleBinding) ne relance rien : toujours `FAILED`, toujours 5 échecs ; (4) une clé d'idempotence déjà utilisée **rejoue l'ancienne réponse** (génération 1) sans rien exécuter ; (5) une **nouvelle** clé crée la génération 2, repart de `DECLARED` (journal : `DECLARED`, `REGISTERED`, `CONFIGURED`, `DEPLOYING`) et **efface** la Condition `DeploymentReady` (absente 4 secondes après) ; le Kernel crée alors le Deployment (la cause corrigée a bien été corrigée) ; (6) sans Consul le service retombe `FAILED` au 5e échec (écart I14 connu) avec un compteur remis à `(1/5)`. **Promesse à confirmer** : `actions/stop` répond **202** et la phase passe à `STOPPING`, mais le Kernel essaie de dé-enregistrer l'instance dans Consul (`PUT http://localhost:8500/v1/agent/service/deregister/v6-news-7f4668697-54x8b`, nom du pod) et échoue 5 fois (2, 4, 8, 16 secondes) : `STOPPED` n'est pas atteint dans les 12 secondes et le Deployment existe encore (voir « Reste de V6 »). Le texte `: null` des messages Consul (écart I11) apparaît encore 10 fois dans ce journal. **V5b (12 novembre 2026, 23:19 à 23:21, jar de `bc33d0a`, k3s v1.31.2, RoleBinding du Kernel supprimé, script `deploy/k3s/v5b-run.sh`)** : `POST /api/v1/services` → 202 ; 5 tentatives ; message d'adapter dans le journal : « Kubernetes a rejete create/update(v5b-news) : HTTP 403 — {…} » avec le **vrai corps Kubernetes de 356 caractères** (entier, sous la borne), qui nomme le verbe refusé (`patch`), le compte de service et le namespace ; la même chose dans la Condition `DeploymentReady` (`AdapterFailureThresholdExceeded`, `lastTransitionTime` 22:20:46 UTC, soit 30 secondes après la première tentative), lue par `GET /status` avec un simple jeton authentifié (K11 confirmé) ; **jeton du compte de service : 0 occurrence** dans le journal et dans la réponse, alors que le chemin d'erreur de l'adapter est exercé. **V4 et V5 sont donc terminées en entier.** Le recoupement avec le runbook a révélé l'écart I15 (Consul absent du scénario 1). **V4 et V5 (12 novembre 2026, ~23:00 à 23:05, jar de `bc33d0a`, k3s v1.31.2 créé par `deploy/k3s/v45-setup.sh`, adapter `kubernetes`, scripts `deploy/k3s/v45-run.sh`)** : le Kernel crée le Deployment `v45-news` (nginx:1.26.0) : 1 pod prêt après environ 57 s. **V4** : `revisionHistoryLimit = 10` dans le cluster (défaut de l'API, non fixé par le Kernel), **valeur supposée confirmée**. **Jeton du compte de service Kubernetes** : 0 occurrence (fin du jeton, jeton complet) dans le journal, dans `GET /status` et dans `GET /services/{id}` ; le jeton est bien envoyé (le Deployment a été créé avec lui). **V5** : deux **vrais** corps d'erreur de Kubernetes, par appel direct avec le jeton du Kernel : `422` (`replicas = -1`) : objet `Status` de 532 caractères, `reason: Invalid`, cause `FieldValueInvalid` sur `spec.replicas` ; `403` (namespace `default`) : 377 caractères, `reason: Forbidden`, le `message` contient le nom du compte de service et du namespace, aucun secret. Les deux exemples sont dans le doc 07. **Limite** : le chemin d'erreur *par le Kernel* n'a pas été exercé avec Kubernetes (V5b). Le retour arrière de plus d'une version reste hors de portée de l'API (aucune route n'appelle `rollback`) : cas de test à ajouter au niveau 3, décision à prendre. **V3c (12 novembre 2026, ~10:14, jar de `bc33d0a`, faux Nomad Python renvoyant `500` avec un corps de 1 200 caractères `X`, script `deploy/keycloak/v3c-run.sh`)** : `POST /api/v1/services` → 202 ; 5 tentatives, puis `DeploymentReady=FALSE` (`AdapterFailureThresholdExceeded`) et phase `FAILED` ; dans le journal (tentative 1/5 et tentative 5/5) **et** dans le message de la Condition : exactement **500 caractères `X` consécutifs suivis de ` [tronque]`**. Le contrat K8 (corps conservé, borné à 500 caractères) est donc confirmé sur un vrai Kernel, avec une réponse plus longue que la borne. **V3 est terminée en entier**, sauf le jeton Kubernetes (reporté à V4/V5). **V10, partie mécanisme (11 novembre 2026, ~22:17 à 22:25, jar de `bc33d0a`, Kernel de production + Keycloak 26.8.2 et PostgreSQL dans Docker, realm d'essai `deploy/keycloak/egen-realm-test.json`, scripts `deploy/keycloak/v10-*.sh`)** : le Kernel démarre avec `EGEN_OIDC_ISSUER_URL=http://localhost:8180/realms/egen` et `EGEN_OIDC_JWKS_URL=…/protocol/openid-connect/certs` ; il accepte les jetons Keycloak. Lecture / déclaration : `platform-operator-service` 200 / **202**, `deploy-pipeline` 200 / **202**, `catalog-service` (sans le rôle) 200 / **403**, `sans-mapper` (porte le rôle mais pas la claim `groups`) 200 / **403**, jeton invalide 401 / 401, sans jeton 401 : **tous conformes à l'attendu**. L'API d'administration liste exactement `service-account-deploy-pipeline`, `service-account-platform-operator-service` et le client temporaire `service-account-sans-mapper`, aucun groupe, et des rôles par défaut sans `egen-admin` (`manage-account`, `offline_access`, `uma_authorization`, `view-profile`). **I10 est donc confirmée sur le vrai Kernel** : sans le mapper `groups`, le rôle n'est pas vu. Le script de départ comportait un défaut de chemin relatif (corrigé, commit `6b9012a`). **V10 reste à faire sur le vrai realm : voir V10b.** **Essai Keycloak par Claude (11 novembre 2026, bac à sable de Claude : Keycloak 26.8.2 en local, realm importé depuis `deploy/keycloak/egen-realm-test.json`)** : trois clients à compte de service ; jetons obtenus par `client_credentials` ; `iss` = `http://localhost:8180/realms/egen` ; `groups` = `['egen-admin']` avec le mapper, absente sans lui ; pas de claim `aud` ; clés publiées : une RS256 de signature et une RSA-OAEP de chiffrement ; l'API d'administration liste bien les porteurs du rôle (`/roles/egen-admin/users`, `/groups`). **Non vérifié contre le Kernel** (aucun jar dans le bac à sable) : c'est l'objet de V10 chez Samuel. **V1 (10 novembre 2026, 23:01, jar de `bc33d0a`, lancé depuis un dossier vide)** : (a) sans `EGEN_REGISTRY_DB_PASSWORD`, démarrage refusé, code de sortie 1, mais par Quarkus avec un message générique « Failed to load config value … quarkus.datasource.password » (et non le message du Kernel ; la branche « absent » de `ProductionConfigurationRules` est donc inatteignable en pratique en production, elle reste une défense) ; (b) avec `egen`, démarrage refusé avec le message prévu, code de sortie 1 ; (c) avec une valeur propre, la garde ne refuse pas (l'échec suivant est la connexion à PostgreSQL, absent). **V3, partie Nomad (10 novembre 2026, 23:22 à 23:27, jar de `bc33d0a`, base PostgreSQL + faux Nomad Python sur le port 4646)** : jeton de test signé avec une clé locale (`EGEN_OIDC_JWKS_URL=file:///tmp/v3/public.pem`, émetteur `https://issuer.test.invalid`, groupe `egen-admin`) : `GET /api/v1/services` → 200 `[]` ; `POST /api/v1/services` → **202 Accepted** (le rôle `egen-admin` est bien lu dans le jeton). Le Kernel a appelé `PUT /v1/jobs` sur le faux Nomad **5 fois** (23:26:34, :36, :40, :48, 23:27:04 : intervalles de 2, 4, 8 et 16 secondes), journalisé « Echec d'adapter pour v3-news (1/5) … (5/5) : Nomad a rejete create/update(v3-news) : HTTP 501 — <corps> », puis posé la Condition `DeploymentReady=FALSE` (raison `AdapterFailureThresholdExceeded`) et la phase `FAILED`. Journal de 454 lignes. Recherche dans ce journal de `CANARI-NOMAD-111`, `CANARI-CONSUL-222`, `CANARI-VAULT-333`, `un-secret-de-test`, `eyJ` (début d'un jeton JWT), `Bearer` et `Authorization` : **0 occurrence pour chacun**. **Limites** : le faux serveur n'affichait pas les en-têtes reçus, donc l'envoi effectif du jeton Nomad n'est pas démontré ; les chemins Consul et Vault n'ont pas été exercés (voir V3b). **Observations** : le corps d'erreur (11 lignes de HTML, environ 330 caractères, donc sous la borne de 500) apparaît intégralement et sur plusieurs lignes dans le journal et dans le message de la Condition ; la borne du K8 est en caractères, pas en lignes. L'erreur OpenTelemetry sur les traces est apparue 3 fois entre 23:26:42 et 23:27:23. **V3b (11 novembre 2026, 10:31 à 10:35, jar de `bc33d0a`, faux Nomad, Consul et Vault Python affichant le jeton reçu)** : le faux serveur a affiché `X-Nomad-Token: CANARI-NOMAD-111` (5 appels `PUT /v1/jobs` à 10:32:09, :11, :15, :23, :39), `X-Vault-Token: CANARI-VAULT-333` (`GET /v1/secret/metadata/v3-sec/db` répété toutes les 3 secondes dès 10:33:29), `X-Consul-Token: CANARI-CONSUL-222` (`PUT /v1/kv/egen/config/development/v3-sec` et `GET /v1/health/service/v3-news?passing=true` à 10:34:15) : **les trois jetons sont bien envoyés**. Recherche dans le journal du Kernel de `CANARI-NOMAD-111`, `CANARI-CONSUL-222`, `CANARI-VAULT-333`, `un-secret-de-test`, `eyJ`, `Bearer`, `Authorization` : **0 occurrence pour chacun**. Les réponses de l'API ne contiennent aucun jeton (`PUT …/configuration` : 503 sans corps ; `GET /discover/v3-news` : 500 générique « Error id … »). Le journal contient bien les messages d'adapter Vault (« Vault a rejete la verification de \"secret/data/v3-sec/db\" : HTTP 403 — {…} ») et Consul (« Consul a rejete resolve(v3-news) : HTTP 500 — {…} »). Le mot de passe PostgreSQL n'apparaît pas non plus dans la trace d'échec de connexion observée en V1. **V3 est terminée** pour Nomad, Consul, Vault et le mot de passe de la base ; le jeton Kubernetes est reporté à V4/V5. **V2 (10 novembre 2026, ~23:15, même jar, base PostgreSQL seule)** : le Kernel démarre en production avec la seule base (profil `prod`, 1,4 s) ; `curl` sans jeton sur `/q/health`, `/q/health/live`, `/q/health/ready`, `/q/openapi`, `/q/metrics`, `/q/dev-ui`, `/q/swagger-ui`, `/q/arc` et `/` : **404 partout** ; `/api/v1/health` : 200 ; `/api/v1/services` : 401. Fonctionnalités installées affichées au démarrage : `agroal, cdi, jdbc-postgresql, narayana-jta, rest, rest-jackson, security, smallrye-context-propagation, smallrye-jwt, vertx` (aucune extension de santé, de métriques, d'OpenAPI ni d'interface de développement). **Observation** : sans collecteur OpenTelemetry (`localhost:4317`), le Kernel démarre et reste en vie ; environ une minute après le démarrage il journalise une erreur `SEVERE` avec une trace complète (`Failed to export metrics`). L'erreur se répète **environ toutes les 60 secondes** (relevé : 23:12:06, 23:13:07, 23:14:05, 23:15:06, 23:16:06, puis 23:23:14 et 23:24:14 sur un second démarrage), avec une trace d'environ 35 lignes à chaque fois. Une erreur analogue « Failed to export spans » a été relevée à 23:23:24 (premier export de traces ; sans doute déclenchée par une requête reçue : non vérifié). **Constats** : sans `EGEN_OIDC_ISSUER_URL` et `EGEN_OIDC_JWKS_URL`, le Kernel ne démarre pas non plus en production ; ces trois variables sont obligatoires (ajoutées au runbook, [23](23-runbooks-operateur.md)).  `mvn verify` complet, 18 modules, vert chez Samuel pour K9 (7 novembre 2026), K10 (9 novembre 2026, 22:26, après correction d'un test), K8 (9 novembre 2026, 22:45) et l'autorisation à deux niveaux (10 novembre 2026, 22:41, vert du premier coup ; ce passage valide aussi les 12 cas de `ServiceResourceAdminOnlyTest` et `ServiceConfigurationAdminOnlyTest`, et la suppression de la propriété `egen.security.team-claim`).

**Faites (suite, 14 novembre 2026)** : **E1, E2, E3, E4 et I11 codés, documentés (docs 07, 09, 13, 23) et validés** par `mvn verify` sur les 18 modules (code de sortie 0, chez Samuel) ; E4 règle aussi I17 et I18 ; `v45-setup.sh` attend désormais le nœud `Ready` (correction d'un faux « ECHEC » du setup). Fusionnés dans `main`. **Non vérifié contre un vrai Consul, Vault ou Kubernetes** : le comportement corrigé de l'arrêt (V6a) reste à rejouer avec `bash deploy/k3s/v6a-run.sh absent` (Consul arrêté pendant tout l'arrêt) puis `bash deploy/k3s/v6a-run.sh retour` (Consul relancé 7 s après la demande d'arrêt), après avoir **redémarré le Kernel** avec le jar issu de `mvn verify`.

**Reste à faire dans le Kernel (point du 14 novembre 2026, après les réponses de Samuel) : plus aucune décision n'est ouverte.** Il reste à **réaliser** ce qui est décidé, dans cet ordre :
1. **Fait** : le code du 14 novembre 2026 (délai `DEGRADED → FAILED`, arrêt qui échoue (I18), `Declare` pendant `STOPPING` (I19), messages sur une ligne (I5), pas d'export OpenTelemetry sans collecteur (I6), Conditions conservées dans un `FAILED` (I16), K9 pour le domaine, le manifeste et les adapters, refus des chemins hors `/api/v1/` (K3)) est validé par `mvn verify` (18 modules, code 0, 16 novembre 2026, 03:38) et fusionné dans `main`. Le seul constat de ce `mvn verify` : le schéma JSON du manifeste ne connaissait pas `deployment.security` (corrigé, commit `e157f6d`). **K3 reste à essayer sur un vrai Kernel** (vérification V16 : `mvn verify` ne prouve pas ce que répond le serveur).
2. **K9** (durcissement des pods), puis **P0**, puis **G** (gRPC), étape par étape, avec les tests prévus à chaque étape.
3. **Reportés, déclencheur non survenu** : A-X1 (`runtime.type`), A-X2 (namespaces Nomad).
4. **Hors de ce dépôt** : le chantier Système d'agents et Edge Gateway (`Civitas-EGEN-Agents`).
5. **Pas du code** : V6b (relecture des scénarios 2 et 3 du runbook), V7 (écrire puis exécuter le chaos avec Toxiproxy), V8 (choix du relecteur externe, puis revue), V10b (vrai realm Keycloak), V11 (quand le code des clients existera), V12 à V14 (vérifications de G et de P0), cases de la Definition of Done.
6. **Corrigé le 14 novembre 2026** : I12 et I14 ne sont **pas** des décisions : le comportement est documenté (document 09, « Comptage des échecs d'adapter » ; `deploy/k3s/README.md`, « Limite connue »). Un « Consul injoignable met le service en `DEGRADED` » proposé auparavant n'était pas dans la documentation et n'est pas retenu.

**V6a rejoué sur l'arrêt corrigé (14 novembre 2026, 21:00, jar de la branche `feat/e3-e4-secret-et-arret`, script `v6a-run.sh` commit `8378ee4`, mode `absent`).** *Environnement : le jeton Kubernetes du Kernel avait expiré (créé à 09:11 pour 2 h) : le dé-enregistrement a donc échoué sur `Kubernetes a rejete listAllocations(v6-news) : HTTP 401`, pas sur Consul ; aucun pod n'a jamais été créé.*
- **Observé conforme à E4 (doc 09)** : 5 échecs consignés à 21:00:26,9 · 28,9 · 32,9 · 40,9 · 56,9 (écarts de 2, 4, 8 et 16 s) ; Condition `DiscoveryDeregistered = FALSE / DeregistrationRetrying`, message `tentative n/5 : <cause>` avec le corps `401` de Kubernetes (borné, sans jeton) ; au cinquième échec `DeregistrationIncomplete` ; la phase n'est jamais passée `FAILED` ; plus de compteur `(6/5)` pour le dé-enregistrement.
- **Observé non conforme à l'attente du script** : après `DeregistrationIncomplete` le service est **resté `STOPPING`** plus de 80 secondes (délai de grâce du manifeste : 30 s), sans atteindre `STOPPED`. Cause **probable, non vérifiée** : `DeploymentPort.stop()` échoue aussi avec ce `401` et suit l'ancien chemin d'échec (hors périmètre d'E4, voir doc 09). À vérifier avec `grep "Echec d'adapter pour v6-news" /tmp/v45/kernel.log | tail`. Si c'est le cas, un échec de `stop()` peut bloquer un arrêt sans fin ; décision à prendre (même règle « au mieux » ?).
- **Essai `retour` invalide** : (a) le service était encore `STOPPING` du premier essai ; (b) le script lisait avec un jeton Keycloak expiré après 300 s et a affiché à tort « Kernel arrêté ? » (rien ne prouve que le Kernel se soit arrêté). Corrigés dans `v6a-run.sh` (contrôles de départ, jeton renouvelé à chaque appel) et `v45-setup.sh` (jeton Kubernetes de 8 h). **À rejouer**, après `v45-setup.sh` et redémarrage du Kernel.
- **Non vérifié** : le comportement avec un vrai Consul absent puis revenu (cas « Deregistered après N échecs ») ; la fin d'un arrêt « au mieux » jusqu'à `STOPPED` contre un vrai cluster. Nouvelles incohérences : I19, I20.

**V6a, second essai (14 novembre 2026, 21:14 à 21:18, jar de `feat/e3-e4-secret-et-arret`, scripts de `main` à `3eec8d9`, Registry vide et cluster neuf).**
- **Mode `absent` : réussi, avec un vrai Consul absent.** 5 échecs consignés à 21:14:50,5 · 52,5 · 56,5 · 04,5 · 20,6 (écarts de 2, 4, 8, 16 s). Condition `DiscoveryDeregistered = FALSE / DeregistrationRetrying` (`tentative n/5 : Appel Consul impossible (PUT http://localhost:8500/v1/agent/service/deregister/<instance>) : java.net.ConnectException (cause : java.net.ConnectException (cause : java.nio.channels.ClosedChannelException))` : **plus aucun « null »**, I11 corrigé) ; à +31 s `DeregistrationIncomplete` ; à +61 s `STOPPED` (délai de grâce de 30 s écoulé) ; la Condition est conservée dans le statut `STOPPED` ; le service n'est jamais passé `FAILED` ; 5 lignes de journal `Echec de deregistrement (n/5)`. **E4 est validé contre un vrai Consul absent.** *Limites :* le service n'a jamais été `RUNNING` (il est devenu `FAILED` avant que son pod soit prêt, écart I14) ; l'arrêt a donc été demandé depuis `FAILED`.
- **Mode `retour` : réussi** (troisième essai, 14 novembre 2026, 23:19 à 23:20, cluster neuf et Kernel redémarré). 3 échecs consignés à 23:19:55,9 · 57,9 · 02,0 (2 s et 4 s d'écart) ; Consul relancé à +11 s ; le 4e essai réussit : Condition `DiscoveryDeregistered = TRUE / Deregistered / Deregistrement reussi apres 3 echec(s)` ; `STOPPED` à +48 s ; le Consul revenu n'a **pas** refusé le dé-enregistrement d'une instance jamais enregistrée (l'inconnue que j'avais signalée ne s'est pas produite). Deployment `0/0` conservé après `STOPPED`, comme le dit le doc 07. **E4 est donc validé dans les deux cas.** Les deux essais précédents du mode `retour` étaient invalides (service resté `STOPPING`, jeton Keycloak expiré, puis Kernel non redémarré avec le jeton du nouveau cluster), voir I21 et le piège des jetons.
- **Observation, toujours non vérifiée** : quand `listAllocations` ou `stop()` échouent avec `401`, le service reste `STOPPING` sans fin après `DeregistrationIncomplete`. Même cause probable : `stop()` suit l'ancien chemin d'échec (hors périmètre d'E4). *La preuve est perdue : `kernel.log` a été écrasé au redémarrage du Kernel (`tee` sans `-a`) ; il faudrait reproduire (jeton Kubernetes périmé volontairement).*

**V9 et V11 : constats (14 novembre 2026, lecture seule de `Civitas-EGEN-Business` au commit `5300ee0`, accord de Samuel).** **Corrigé le même jour, avec l'accord écrit de Samuel : commit `3c3d3b7` sur `main` de `Civitas-EGEN-Business` (4 fichiers de `docs/plateforme/` : 01, 02, 05, 06).** V9 est donc fait ; la correction de V11 est faite pour les documents.
- **V9 : écart confirmé.** `docs/plateforme/02-registre-des-decisions.md` (D14) dit encore « seul `platform-operator-service` détient des identifiants de gestion du Kernel (équipe `platform-operator`) », alors que le doc 22 dit : `platform-operator-service` **et la pipeline de déploiement** portent le **rôle administrateur**, et l'équipe n'autorise plus rien. Même écart dans `06-identite-et-droits.md` (ligne « Identité de gestion du Kernel », champ `team = platform-operator` du jeton), `05-catalogue-instances-affectations.md` (§5.10, « jeton Kernel `team: platform-operator` ») et `01-vision-et-frontiere.md` (tableau des services).
- **V11 : un appelant dépend d'une lecture filtrée par équipe.** `05-catalogue-instances-affectations.md` §5.10 (détection des orphelins) compare « `GET /api/v1/services` (filtrée par équipe) » au catalogue. Le Kernel ne filtre **pas** (aucun paramètre, la liste renvoie tous les services, doc 14). Le correctif fait filtrer l'opérateur lui-même sur `metadata.team` (étiquette de traçabilité). Aucun appelant dans ce dépôt n'attend un `403` fondé sur l'équipe (recherche de `403`, `team=`, `?team` : seules des occurrences sans rapport). Les 218 fichiers Java du dépôt (module `organization`) ne contiennent aucun appel au Kernel.
- **Non vérifié** : les services de la Plateforme (`platform-operator-service`, passerelle, console, SDK) **ne sont pas encore codés** dans ce dépôt (phases du doc 11) : V11 ne porte donc que sur les documents. À refaire quand ce code existera.
- **Hors périmètre, noté** : `01-vision-et-frontiere.md`, ligne `domain-gateway` : le lien `07-contrats-de-domaine-et-passerelle de domaine.md` contient des espaces, il est cassé.


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

Écarts **tranchés le 14 novembre 2026** (points ouverts 3 à 5, tableau « Points ouverts du Kernel — tranchés » ci-dessous). Le nettoyage du code du dépôt `Civitas-EGEN-Business` suit la même règle ; il attend la décision du point ouvert 4 de ce dépôt-là pour le module `organization` déjà présent (voir `docs/plateforme/11-plan-par-phase.md`).

## Phase A de la Plateforme — côté Kernel

La Plateforme (voir [22](22-frontiere-avec-la-plateforme.md)) se construit au-dessus du Kernel. Seule sa **phase A** touche ce dépôt ; ses phases B à H sont suivies dans le dépôt `Civitas-EGEN-Business`, `docs/plateforme/11-plan-par-phase.md`.

- [x] **A1 — Documentation de la frontière** : document [22](22-frontiere-avec-la-plateforme.md) et corrections des documents 01 à 21.
- [x] **A2 — Convention de labels réservés** `platform.civitas.africa/*` ([06](06-service-manifest.md#labels-réservés-posés-par-la-plateforme)) : documentée ; test d'aller-retour de l'API livré (`ReservedLabelsRoundTripTest`), aucun changement de code.
- [x] **A3 — API de publication de configuration** : voir la Phase 4 ci-dessus (même élément, suivi à cet endroit).
- [x] **A4 — Règle d'architecture « Kernel ignorant de la Plateforme »** en CI ([22](22-frontiere-avec-la-plateforme.md#227--la-règle-darchitecture-a4)) : `PlatformBoundaryRuleTest`, avec une violation volontaire qui prouve que la règle échoue.
- [x] **A5 — Scénario de niveau 5** : voir la Phase 5 ci-dessus (même élément, suivi à cet endroit).
- [>] **A-X1** (Phase 1) et **A-X2** (Phase 4) : reportés, avec déclencheur.
- **Critère de sortie** : intégration continue verte ; `egen-domain` sans aucune modification liée à la Plateforme ; A3 testé aux niveaux 1, 2, 3, 4 et 5 ([17](17-strategie-de-tests.md)).

## Phase P0 — Production du Kernel

*Validée par Samuel le 14 novembre 2026 (« OUI »). Elle suit la phase A, déjà terminée, et précède la phase G (gRPC).* Chaque constat a été vérifié dans le code du dépôt le 29 octobre 2026.

| Constat | Vérification |
|---|---|
| Aucun `Dockerfile`, donc aucune image publiable | Aucun fichier `Dockerfile*` dans le dépôt |
| Aucun job Nomad du Kernel | Le dossier `deploy/` ne contient que `nomad-dev.sh`, `otel-collector-config.yaml` et `prometheus.yml` |
| Vault en mode développement | `docker-compose.yml` lance Vault avec `VAULT_DEV_ROOT_TOKEN_ID` |
| Un seul exemplaire du Kernel possible (idempotence) | La clé d'idempotence (`IdempotencyKeyFilter`) était gardée dans une `ConcurrentHashMap` du processus ; les liens de traces (`PendingDeclareTraces`) le sont encore |
| **Un seul exemplaire du Kernel possible (workflows)** *(constat ajouté le 16 novembre 2026)* | Le moteur de workflow (`WorkflowEngine`) garde les exécutions qu'il pilote dans un ensemble en mémoire (`driving`) et `recoverInterruptedExecutions()` reprend, au démarrage, **toutes** les exécutions `RUNNING` du Registry : un second exemplaire reprendrait des exécutions que le premier est en train de piloter, et les appels de leurs étapes seraient faits deux fois |
| mTLS désactivé par défaut | Les deux lignes de configuration sont commentées dans `application.properties` |
| Sidecar sans mTLS ni publication d'événements | Aucune occurrence de « publish », « mtls » ou « tls » dans les sources du sidecar |

**Ordre de démarrage d'une installation neuve (point ouvert 1, option a, décidé le 14 novembre 2026).** La pipeline de déploiement lance **Keycloak d'abord**, attend que son realm réponde (`GET /realms/<realm>` → `200`), puis lance le Kernel avec `EGEN_OIDC_ISSUER_URL` et `EGEN_OIDC_JWKS_URL`. Le Kernel ne démarre pas sans ces deux valeurs (constaté le 10 novembre 2026) : il n'y a pas de clé de secours.

**Étapes, dans cet ordre.** Chaque étape suit la méthode : contrat dans la documentation, code, tests, critère de sortie vérifié.

| Étape | Livrable | Tests et critère de sortie | Dépend de |
|---|---|---|---|
| **P0-1 — Image du Kernel** | `Dockerfile` à plusieurs étages (compilation Maven, puis image d'exécution `eclipse-temurin:21-jre`), format `quarkus-app` ; exécution sous l'utilisateur non-root `10001` ; `.dockerignore` ; instructions de publication dans le registre | `docker build` réussit ; `docker run` avec les variables d'environnement de `v45-setup.sh` : `GET /api/v1/health` → `200` ; `docker run --read-only` démarre (le Kernel n'écrit que dans `/tmp`) ; taille de l'image relevée. **Fait le 14 novembre 2026 : `Dockerfile`, `.dockerignore` et `deploy/README.md` écrits ; jamais construit ni lancé (vérification V15)** | — |
| **P0-2 — Job Nomad** | `deploy/nomad/egen-kernel.nomad.hcl` : variable `count`, port dynamique, contrôle de santé sur `/api/v1/health`, variables d'environnement, ressources, mise à jour progressive avec retour automatique, arrêt gracieux de 30 s ; `deploy/nomad/egen-kernel.vars.example.hcl` ; section « Job Nomad » de `deploy/README.md`. **Le bloc `vault` du job (jeton Vault fourni par Nomad) est déplacé à l'étape P0-4** : il exige un Vault configuré, qui n'existe qu'après cette étape | `nomad job validate` et `nomad job plan` sans erreur ; déploiement sur le Nomad de `deploy/nomad-dev.sh` : le job est `running`, le contrôle de santé est `passing` et `GET /api/v1/health` répond ; ordre Keycloak puis Kernel écrit dans `deploy/README.md`. **Fait le 16 novembre 2026 : fichiers et README écrits ; jamais validés par Nomad (vérification V17)** | P0-1 |
| **P0-3 — Idempotence partagée** | port `IdempotencyStorePort` ; adapter PostgreSQL `PostgresIdempotencyStoreAdapter` (table `egen_idempotency_response`, clé réduite à son empreinte SHA-256, expiration, purge, plafond de 10 000) ; adapter en mémoire pour les tests ; `IdempotencyKeyFilter` lit et écrit par ce port ; contrat dans le doc 13. **Décision de Claude (16 novembre 2026) : les liens de traces (`PendingDeclareTraces`) restent en mémoire** : les perdre coupe seulement le lien visuel entre la trace d'un `Declare` et celle du cycle qui suit, sans effet sur le comportement ; une table pour cela serait de l'évolution anticipée | niveau 1 : règles du stockage en mémoire (7 tests, passés le 16 novembre 2026) ; niveau 2 : les tests `@QuarkusTest` existants de rejeu passent par le nouveau stockage ; niveau 3 (Testcontainers) : `PostgresIdempotencyStoreAdapterIT`, dont le test central fait enregistrer une réponse par un adapter et la fait lire par un **second** adapter sur la même base (deux exemplaires). **Code écrit le 16 novembre 2026, validé par `mvn verify` (18 modules, code 0, 17 novembre 2026, 03:16) (dont `PostgresIdempotencyStoreAdapterIT` : deux adapters sur la même base)** | P0-1 |
| **P0-3b — Un seul pilote par exécution de workflow** *(étape ajoutée le 16 novembre 2026)* | **Contrat** écrit dans le doc 11, « Bail d'exécution » : bail en base (table `egen_workflow_lease`), prise atomique, renouvellement toutes les durées/3, remise en fin de pilotage, **reprise périodique toutes les 30 secondes** (ajoutée le 17 novembre 2026 : sans elle, un Kernel mort laisserait ses exécutions orphelines tant qu'un autre Kernel ne redémarre pas). Nouvelles méthodes de `WorkflowStorePort` : `tryClaim`, `renew`, `release` (par défaut sans coordination, redéfinies par l'adapter PostgreSQL ; le décorateur d'observation les délègue) ; réglages `egen.workflow.lease-duration`, `egen.workflow.recovery-interval`, `egen.kernel.id` | niveau 2 : `WorkflowEngineLeaseTest` (6 tests, passés le 17 novembre 2026) : exécution tenue par un autre Kernel non pilotée, bail expiré repris, bail tenu pendant l'étape puis rendu, **deux Kernels sur la même exécution : chaque étape appelée une seule fois**, règles du faux stockage ; les 52 tests existants du moteur passent toujours ; niveau 3 (Testcontainers) : 5 tests dans `PostgresWorkflowStoreAdapterIT`, dont **deux adapters qui se disputent un même bail**. **Code écrit le 17 novembre 2026 ; à valider par `mvn verify`. Non testés : le renouvellement par la tâche de fond pendant une étape longue (délais réels), la tâche de reprise périodique de bootstrap, et un essai réel à deux Kernels (vérification V18)** | P0-3 |
| **P0-4 — Vault hors mode développement** | `deploy/vault/` : configuration du serveur (stockage Raft, sans jeton racine de développement), politique minimale lecture seule sur `secret/data/egen/*`, procédure d'initialisation et de descellage ; `docker-compose.yml` ne lance plus Vault en mode `-dev` pour la production | niveau 3 : le Kernel lit un secret avec un jeton à politique minimale et reçoit `403` sur un chemin interdit ; la procédure est rejouée de zéro par Samuel (vérification V13) | P0-2 |
| **P0-5 — mTLS de bout en bout** | génération des certificats (script `deploy/tls/`), activation des deux lignes `quarkus.http.ssl.*` du Kernel, TLS mutuel entre le Kernel et ses clients de confiance ; rotation documentée | niveau 3 : un client sans certificat valide est refusé avant tout appel `/api/v1/` ; un client avec certificat valide et jeton valide est accepté ; documentation dans le doc 14 | P0-2 |
| **P0-6 — Sidecar complété** | le sidecar EGEN expose la publication d'événements et parle en mTLS ([12](12-communication-fabric.md)) ; contrat inter-langages mis à jour | tests de contrat inter-langages ([17](17-strategie-de-tests.md)) : publication via le sidecar reçue par un consommateur NATS ; connexion sans certificat refusée | P0-5 |

**Critère de sortie de P0** : les étapes P0-1 à P0-6 (dont P0-3b) sont `[x]`, `mvn verify` est vert sur les 18 modules, et Samuel a rejoué de zéro les vérifications V13 (Vault) et V14 (installation neuve : Keycloak, puis Kernel dans l'image, par le job Nomad). Statut : `[ ]` P0-1 à P0-6, non commencées.

## Points ouverts du Kernel — tranchés le 14 novembre 2026

*Samuel a répondu le 14 novembre 2026. Plus aucun point de cette section n'est ouvert.*

| N° | Question | Décision | Où c'est écrit |
|---|---|---|---|
| 1 | Amorçage circulaire (Keycloak et Kernel) | **(a)** Keycloak est lancé **avant** le Kernel par la pipeline | phase P0 ci-dessus ; `docs/plateforme/11-plan-par-phase.md` (phase C) |
| 2 | Phase P0 | **Oui**, après la phase A (terminée) et avant la phase G | phase P0 ci-dessus |
| 3 | Surface gRPC | **Mise en place complète**, sur le même port et avec la même authentification que le REST | phase G ci-dessus |
| 4 | `DEGRADED → FAILED` | **(a)** délai configurable, 10 minutes par défaut | [09](09-cycle-de-vie.md), « DEGRADED depuis trop longtemps » ; codé |
| 5 | Ports et doc 07 | **(a)** le document 07 décrit les ports livrés | [07](07-ports-et-adapters.md), « Ce que `DiscoveryPort` ne contient pas » ; fait |
| 6 | Durcissement des pods | **(a)** champ facultatif `deployment.security` | section « Durcissement des pods Kubernetes » ci-dessus |

**Décisions prises par Claude le 14 novembre 2026, sur la consigne de Samuel de décider « selon son intelligence et ses compétences »** (chacune est écrite dans le document indiqué ; Samuel peut en renverser une) :

| Écart | Décision | Où |
|---|---|---|
| I5 | une entrée de journal = une ligne : le corps d'erreur est ramené sur une ligne puis borné à 500 caractères ; codé | [07](07-ports-et-adapters.md) |
| I6 | sans point de collecte configuré, le Kernel n'exporte rien (valeur par défaut vide) ; codé | [15](15-observabilite.md) |
| I16 | un statut `FAILED` garde les Conditions posées avant l'échec : elles sont enregistrées avant l'appel au moteur de déploiement ; codé | [09](09-cycle-de-vie.md) |
| I18 | un échec de `stop()` laisse le service `STOPPING`, Condition `DeploymentStopped`, compteur plafonné à 5/5, nouvelle tentative toutes les 60 s ; codé | [09](09-cycle-de-vie.md), « Quand l'arrêt lui-même échoue » |
| I19 | un `Declare` sur un service `STOPPING` est refusé avec `409` ; codé | [09](09-cycle-de-vie.md), « Déclarer pendant un arrêt » ; [13](13-api-et-contrats.md) |
| I20 | on garde la règle actuelle : pas d'arrêt depuis `DEPLOYING` (un arrêt concurrent d'un déploiement en cours créerait un état indéfini) | [09](09-cycle-de-vie.md), « Quand une demande d'arrêt est acceptée » |
| K2 | vérification d'audience **facultative**, désactivée par défaut, activable par `MP_JWT_VERIFY_AUDIENCES` et un mapper Keycloak | [14](14-securite.md), « Audience du jeton » |
| K3 | tout chemin hors de `/api/v1/` est refusé par défaut ; configuré | [14](14-securite.md), « Surface HTTP » |
| V7 | outil d'injection de pannes : Toxiproxy (module Testcontainers) | [17](17-strategie-de-tests.md), niveau 6b |


## Phase G — Surface gRPC du Kernel

*Décidée par Samuel le 14 novembre 2026 (point ouvert 3 : « on le met en place correctement et proprement »). Elle suit la phase P0.*

**Les termes.** *gRPC* : protocole d'appel à distance sur HTTP/2, dont les messages sont des structures binaires décrites dans un fichier `.proto`. *Flux serveur* (*server streaming*) : le client envoie une demande, le serveur lui répond par une suite de messages sur la même connexion.

**Rôle.** gRPC est une **seconde porte d'entrée**, équivalente au REST, pour les clients qui ont besoin de volume ou d'un suivi continu. Il appelle **les mêmes cas d'usage** que le REST (`DeployServiceUseCase`, `StopServiceUseCase`, `ReadServiceManifestUseCase`, `ResolveServiceUseCase`…) : aucune règle métier n'est écrite deux fois.

**Décisions de conception** (prises par Claude le 14 novembre 2026 sur délégation de Samuel, à relire à l'étape G1) :
1. **Même port, même authentification.** gRPC est servi sur le port 8080 par le serveur HTTP de Quarkus (`quarkus.grpc.server.use-separate-server=false`). Le jeton Keycloak se passe dans l'en-tête `authorization: Bearer …`, comme en REST. La règle des rôles est identique : lecture et découverte pour tout appelant authentifié, modifications réservées au rôle `egen-admin` ([14](14-securite.md)).
2. **Le contrat vit dans `egen-contracts/src/main/resources/proto/egen_control.proto`** (une seule source), dans le module `egen-contracts` qui porte déjà le schéma du manifeste et l'OpenAPI et dont `egen-api` dépend. Il est livré aux clients dans le jar de ce module. `egen-api` génère son code à partir de ce fichier (propriété Quarkus `quarkus.generate-code.grpc.scan-for-proto`).
3. **Le contrat gère les versions par son nom de paquet** : `africa.civitas.egen.control.v1`. Une évolution compatible (champ ajouté) reste en `v1` ; une évolution incompatible crée `v2` à côté.
4. **Première version limitée à ce que le REST fait déjà et qui a un client réel** : `Declare`, `Get`, `List`, `Stop`, `Resolve` (découverte) et `WatchStatus` (le flux de statut). Publication de configuration, événements et workflows restent en REST tant qu'aucun client gRPC ne les demande.
5. **`WatchStatus`** : le serveur lit le statut du service chaque seconde et envoie un message **à chaque changement** (phase, Conditions ou génération), puis un dernier message et la fin du flux quand le service atteint `STOPPED`, `FAILED` ou `REMOVED`, ou quand le client annule. Ce n'est pas un système de notifications interne : c'est une boucle de lecture côté serveur, suffisante tant que le Registry n'émet pas de changements (aucun mécanisme n'est ajouté par anticipation).
6. **Correspondance des erreurs** (même sens qu'en REST) : `400` → `INVALID_ARGUMENT` ; `401` → `UNAUTHENTICATED` ; `403` → `PERMISSION_DENIED` ; `404` → `NOT_FOUND` ; `409` → `FAILED_PRECONDITION` ; `503` → `UNAVAILABLE`. *(Un `422` pour une clé rejouée avec un autre corps avait été écrit par erreur le 14 novembre 2026 : ni le doc 13 ni le code ne le prévoient ; retiré le 16 novembre 2026.)* La clé d'idempotence passe dans la métadonnée `idempotency-key`, le contexte de trace dans `traceparent`.
7. **Attention à la règle K3** ([14](14-securite.md)) : les chemins gRPC (`/africa.civitas.egen.control.v1.ServiceControl/Declare`…) ne sont pas sous `/api/v1/`. L'étape G3 ajoute une règle explicite qui les réserve aux appelants authentifiés, sans quoi ils seraient refusés.

**Exemple de bout en bout.** Une pipeline CI déploie 3 000 services. Elle ouvre une connexion gRPC et envoie `Declare` pour chacun avec son jeton `egen-admin`, puis ouvre un `WatchStatus` par service (ou un seul flux par lot, étape G4). Elle reçoit « `news-service` : `DEPLOYING` », puis « `RUNNING` » au moment où cela arrive, au lieu d'interroger `GET …/status` toutes les 5 secondes.

| Étape | Livrable | Tests et critère de sortie |
|---|---|---|
| **G1 — Contrat** | `egen_control.proto` (messages, services, commentaires) ; section normative « Surface gRPC » dans le doc 13 (méthodes, champs, erreurs, métadonnées, exemple `grpcurl`) ; docs 13 et 16 corrigés ; l'emplacement du fichier et le nom du paquet figés | le fichier se compile avec `protoc` ; un descripteur de référence (`egen-contracts/baseline/egen_control.v1.descriptor`) est versionné et le script `egen-contracts/tools/check_proto_compat.py` refuse les évolutions incompatibles ; le contrat est relu par Samuel |
| **G2 — Construction** | dépendance `quarkus-grpc` et génération du code dans `egen-api` (greffon Quarkus `generate-code`) ; règles d'architecture (ArchUnit) : le code gRPC ne dépend que des cas d'usage, jamais du domaine directement ni des adapters | `mvn verify` vert sur les 18 modules ; le Kernel démarre toujours et `GET /api/v1/health` répond ; règle ArchUnit qui échoue sur une violation volontaire |
| **G3 — Services unitaires** | `ServiceControlGrpc` : `Declare`, `Get`, `List`, `Stop`, `Resolve`, avec la correspondance des erreurs, l'intercepteur d'authentification et de rôle, la clé d'idempotence ; règle de chemin pour K3 | niveau 1 : table de correspondance des erreurs testée ligne à ligne ; niveau 2 : appels avec de faux cas d'usage ; niveau 3 (`@QuarkusTest`, canal gRPC en mémoire) : sans jeton → `UNAUTHENTICATED`, jeton non admin sur `Declare` → `PERMISSION_DENIED`, `Declare` rejoué avec la même clé → même réponse ; parité : le même `Declare` en REST et en gRPC crée la même génération |
| **G4 — `WatchStatus`** | flux serveur décrit au point 5 ; limites : durée maximale d'un flux et nombre de flux simultanés par appelant, configurables | un changement de phase est reçu en moins de 2 secondes ; l'annulation par le client libère le flux (compteur de flux actifs revenu à zéro) ; un service qui atteint `FAILED` termine le flux avec un dernier message ; une limite dépassée → `RESOURCE_EXHAUSTED` |
| **G5 — Scénario de bout en bout** | scénario de niveau 5 : déclarer par gRPC, suivre par `WatchStatus` jusqu'à `RUNNING`, découvrir par `Resolve`, arrêter par `Stop`, constater `STOPPED` | le scénario passe sur l'infrastructure de test (Nomad, Consul, PostgreSQL en conteneurs) ; la sortie est conservée dans `docs/` comme les autres scénarios |
| **G6 — Documentation et vérification** | doc 14 (authentification gRPC), runbook doc 23 (diagnostic d'un appel gRPC refusé), doc 17 (niveaux de test gRPC) | Samuel rejoue la session `grpcurl` du doc 13 contre son Kernel local (vérification V12) |

**Critère de sortie de G** : G1 à G6 `[x]`, `mvn verify` vert, le test de parité REST/gRPC vert, V12 faite. Statut : **G1 `[x]`** (contrat écrit, compilé avec `protoc`, script de compatibilité vérifié sur 4 cas ; **relecture par Samuel : en attente**) ; G2 à G6 `[ ]`, non commencées (la phase G suit P0).

## Durcissement des pods Kubernetes (constat K9, [24](24-revue-de-securite-externe.md))

*Décidé par Samuel le 14 novembre 2026 (point ouvert 6, option a).*

**Les termes.** Un *contexte de sécurité* (`securityContext`) est l'ensemble des restrictions que Kubernetes impose à un conteneur. Trois d'entre elles ne sont pas posées aujourd'hui parce qu'elles font échouer beaucoup d'images : `runAsNonRoot` (refuser de démarrer si le programme tourne en administrateur), `readOnlyRootFilesystem` (interdire d'écrire sur le disque du conteneur) et `capabilities.drop: [ALL]` (retirer les privilèges Linux).

**Le contrat (à inscrire dans le [document 06](06-service-manifest.md) à l'étape K9-1).** Un champ **facultatif** `deployment.security` du manifeste :
```yaml
deployment:
  security:
    runAsNonRoot: true            # défaut : non posé
    runAsUser: 10001              # facultatif ; entier de 1 à 2147483647
    readOnlyRootFilesystem: true  # défaut : non posé
    dropAllCapabilities: true     # défaut : non posé
```
Sans ce bloc, l'adapter pose le socle actuel (rien de plus strict). Avec ce bloc, il pose exactement ce que le propriétaire demande. Chaque champ absent n'est pas posé. **Règles de validation** (refus du `Declare`, `400`) : `runAsUser` doit être compris entre 1 et 2147483647 ; un champ inconnu ou d'un mauvais type est refusé ; le Kernel n'ajoute aucun volume. **Adapter qui ne sait pas appliquer un champ** (Nomad) : il refuse à la création ou à la mise à jour du service, avec un message qui nomme le champ et l'adapter, jamais en l'ignorant en silence ; le service suit le chemin d'échec d'adapter habituel et la Condition `DeploymentReady` porte ce message.

**Exemple de bout en bout.** L'équipe `content-platform` déclare `news-service` avec une image dont le programme tourne sous l'utilisateur 10001 et écrit seulement dans `/tmp` : elle ajoute `runAsNonRoot: true`, `readOnlyRootFilesystem: true`, `dropAllCapabilities: true`. Kubernetes lance le pod durci. Une autre équipe garde l'image `nginx` d'origine sans bloc `security` : son pod continue de démarrer comme avant.

| Étape | Livrable | Tests et critère de sortie |
|---|---|---|
| **K9-1 — Contrat** | section « `deployment.security` » du doc 06 (champs, défauts, validation, exemple) ; doc 07 (adapter Kubernetes : correspondance des champs ; adapter Nomad : champs non supportés) | relecture : aucun champ sans défaut ni règle de validation |
| **K9-2 — Domaine et lecture du manifeste** | type `SecuritySpec` facultatif dans `DeploymentSpec` ; lecture du YAML et du JSON ; validation | niveau 1 : toutes les règles de validation, un test par règle ; aller-retour YAML ↔ objet ↔ réponse `GET /api/v1/services/{id}` sans perte |
| **K9-3 — Adapter Kubernetes** | le `securityContext` du pod et du conteneur reflète `deployment.security` ; l'adapter Nomad refuse (à la création ou à la mise à jour) un manifeste qui porte `deployment.security` | niveau 2 (faux serveur Kubernetes) : le JSON envoyé contient exactement les champs demandés, et le socle actuel quand le bloc est absent ; niveau 3 (k3s réel) : une image non-root avec `runAsNonRoot` démarre ; l'image `nginx` d'origine avec `runAsNonRoot` échoue avec un message lisible dans la Condition |
| **K9-4 — Clôture du constat K9** | doc 24 mis à jour | `mvn verify` vert |

Statut : **K9-1 `[x]`** (contrat écrit dans les docs 06 et 07) ; **K9-2 `[x]`** et **K9-3 `[x]`** pour le code et les tests de niveaux 1 et 2 (domaine `SecuritySpec`, lecture et écriture du manifeste, stockage PostgreSQL, adapter Kubernetes, refus par l'adapter Nomad), **validé par `mvn verify` (18 modules, code 0, 16 novembre 2026, 03:38)** ; **le test de niveau 3 sur un vrai k3s (image non-root qui démarre, image `nginx` avec `runAsNonRoot` qui échoue) n'est pas écrit** ; K9-4 `[ ]` (clôture du constat K9 dans le doc 24, après la validation).


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
