# EGEN Kernel

EGEN est une plateforme de gouvernance d'organisations souveraines et de services
modulaires, developpee par **CIVITAS Africa**. Ce depot contient le **Kernel** et
les **modules Niveau 2** (providers systeme + modules business) qui gravitent autour
de lui — voir la Charte d'Architecture ci-dessous pour la distinction exacte entre
ces trois notions.

**Logiciel proprietaire — tous droits reserves.** Ce depot est prive et son contenu
n'est distribue sous aucune licence open source. Toute reproduction, modification ou
distribution en dehors de CIVITAS Africa est interdite sauf autorisation explicite.

## Reference d'architecture

**[`docs/architecture/charte-v3.md`](docs/architecture/charte-v3.md)** est le
document qui fait foi pour toute decision d'architecture dans ce depot. Il fixe le
modele a trois niveaux (Niveau 0 irreductible / Niveau 1 primitif / Niveau 2
pluggable) qui remplace le modele "systemes plats A1-E3" utilise jusqu'au 22 juillet
2026 — lire ce document avant de contribuer, surtout si vous arrivez avec le
vocabulaire de l'ancienne classification (A1, A2, B4...) en tete : les numeros
restent valables comme reference historique, mais le **Niveau** (0/1/2) de chaque
systeme a change de sens.

## Principe directeur

Le Kernel possede la verite organisationnelle (identite, souverainete, hierarchie,
politique, ressources, audit) et delegue toujours la logique d'execution
(autorisation, evenements, orchestration de processus, notification) a des services
externes qu'il invoque et journalise, sans jamais la reimplementer. Le Kernel
lui-meme (Niveau 0 + Niveau 1) ignore tout de ce qui est charge par-dessus lui — voir
la Charte v3, Partie A.

## Arborescence (etat reel au 23 juillet 2026, apres refactoring vers la Charte v3 et livraison du primitif Niveau 1)

```
egen-platform/                                     (racine du reacteur Maven)
├── egen-kernel/                                    ← Niveau 0 + Niveau 1 uniquement
│   ├── kernel-sdk/                                 ← contrat public, JPMS pur
│   │                                                  (extension, event, contexte,
│   │                                                  manifest, tracabilite,
│   │                                                  permission/{identity,
│   │                                                  authorization,policy})
│   ├── kernel-jpa-support/                         ← TracabiliteEmbeddable (mapping
│   │                                                  JPA partage entre tous les
│   │                                                  modules -impl, kernel comme
│   │                                                  egen-modules)
│   ├── kernel-domain/                              ← module-domain (B2) — ModuleId,
│   │                                                  CatalogueEntree, Souscription,
│   │                                                  Activation : vocabulaire pur
│   ├── kernel-systems/                             ← primitifs Niveau 1 (point 3, livre)
│   │   ├── identity/                                 KernelSubjectService — sujet
│   │   │                                             minimal, sans persistance
│   │   ├── authorization/                            KernelPermissionCheckImpl —
│   │   │                                             octrois de capacites, fail-closed
│   │   ├── policy/                                   PolitiqueNoyauImpl — refuse
│   │   │                                             systematiquement, sans exception
│   │   └── module-registry/                          Catalogue -> Souscription ->
│   │                                                  Activation (B2, Niveau 0) —
│   │                                                  cascade stricte, fail-closed
│   ├── kernel-eventbus/                            ← EventBus + InMemoryEventBus
│   │                                                  (Niveau 0), KafkaEventBusAdapter
│   │                                                  (Niveau 2, meme module)
│   ├── kernel-plugin-engine/                       ← ManifestReader, ExtensionRegistry,
│   │                                                  PluginLifecycleManager (orchestrateur),
│   │                                                  PluginLoader + Pf4jPluginLoader
│   ├── kernel-bootstrap/                           ← EgenKernelApplication,
│   │                                                  KernelBootSequence,
│   │                                                  PluginDirectoryScanner — l'app
│   │                                                  Quarkus reelle
│   └── kernel-test-support/                        ← TracabiliteFixtures,
│                                                       FakeKernelPermissionCheck,
│                                                       FakeModuleActivationResolver,
│                                                       PostgresTestResource
└── egen-modules/                                   ← Niveau 2 (pluggable)
    ├── system/                                      ← les providers (ponts vers
    │   └── identity/                                  l'exterieur : Keycloak,
    │       ├── identity-provider-api/                 SpiceDB, un futur fournisseur
    │       └── identity-provider-keycloak/             de communication...)
    └── business/                                    ← les modules metier
        ├── organization/                              (fusion Organisation +
        │   ├── organization-api/                      Rattachements + Politique
        │   │   └── .../api/{affiliation,politique}/    organisationnelle)
        │   └── organization-impl/
        │       └── .../impl/{affiliation,politique}/
        └── reference-data/
            ├── reference-data-api/
            └── reference-data-impl/
```

### Pourquoi ce depot n'est plus un empilement plat de "systemes A1-E3"

Avant le 22 juillet 2026, `kernel-systems/` contenait `identity`, `reference-data`,
`organization`, `affiliation` et `policy`, tous traites comme des systemes pairs du
Kernel. Une analyse rigoureuse a la lumiere de la Charte v3 a etabli qu'aucun des
cinq n'etait, en verite, du Niveau 0 ou 1 :

- `identity` et `organization`/`affiliation`/`policy` etaient deja des implementations
  riches, couplees a une technologie concrete (Keycloak) ou a un concept
  intrinsequement Niveau 2 (Organisation/Cellule) — jamais le "minimum vital avant
  qu'aucun module ne soit charge" que Niveau 0/1 designe.
- `policy` en particulier portait a tort l'etiquette "Systeme B1" : son contenu reel
  (Politique + Derogation sur un Contexte Organisation/Cellule, resolution "le plus
  proche l'emporte") est la Politique **organisationnelle** (§B.12 de la Charte v3),
  pas la Politique-**noyau** (le vrai B1). Les deux portent le meme mot dans le
  langage courant ; ce n'est pas le meme systeme.

Consequence : ce contenu a ete deplace tel quel (repackage, pas reecrit) vers
`egen-modules/`, avec `organization` + `affiliation` + `policy` fusionnes en un seul
module business (`organization`), conformement a la Charte v3 (§C.1). `identity` est
devenu un provider (`egen-modules/system/identity/`), avec un contrat generique
(`identity-provider-api`) separe de son implementation Keycloak
(`identity-provider-keycloak`) — pour que d'autres providers de la meme capacite
puissent le rejoindre au fil du temps sans jamais casser ce qui en depend, exactement
le modele de connecteurs pluggables d'ActivePieces ou n8n.

`kernel-systems/` porte desormais les trois primitifs Niveau 1 sous leur forme
correcte : `identity` (`KernelSubjectService`, sujet minimal), `authorization`
(`KernelPermissionCheckImpl`, octrois de capacites fail-closed) et `policy`
(`PolitiqueNoyauImpl`, la vraie Politique-noyau — voir `docs/architecture/
charte-v3.md`, §A.5, pour la conception complete). Livre le 23 juillet 2026, en
reponse au point 3 de la Charte v3 — une premiere proposition rigoureuse, pas une
verite gravee. Deux consommateurs reels confirment desormais la conception :
`module-registry` (B2, 24 juillet) consulte `PolitiqueNoyau`, et
`kernel-plugin-engine` (25 juillet) consulte a la fois `KernelPermissionCheck` ET
`ModuleActivationResolver` avant tout chargement — le test le plus exigeant, reussi.
`kernel-bootstrap`, livre le 27 juillet 2026, est la composition finale de tout ce qui
precede.

`kernel-domain/module-domain` et `kernel-systems/module-registry` implementent la
cascade Catalogue -> Souscription -> Activation (§B.11) : un module doit etre au
Catalogue avant qu'une Organisation ne puisse y Souscrire, elle-meme prealable a
toute Activation par une de ses Cellules. Chaque palier est verifie explicitement
au niveau service, jamais suppose. `ModuleActivationResolver` est la question
fail-closed que `kernel-plugin-engine` consulte desormais reellement avant de
charger un module : sans Activation active, la Politique-noyau tranche, toujours un
refus. Organisation et Cellule y sont systematiquement des UUID nus, jamais un
import du module business Organization (Niveau 2) — module-registry est Niveau 0, il
ignore tout de la hierarchie des Cellules ; c'est a l'appelant de la resoudre avant
d'appeler ce
service (voir le pom.xml de module-registry pour cette decision assumee).

## kernel-plugin-engine — l'orchestrateur, livre le 25 juillet 2026

Le mecanisme d'accueil des modules (§1 de l'anatomie du Kernel), avec trois points
d'extensibilite deliberes :

- **`ManifestSource`** (`manifest/`) — d'ou viennent les donnees brutes d'un
  Manifeste. `PropertiesFileManifestSource` lit un fichier `.properties` (meme
  format de base que le descripteur natif de PF4J) ; `ManifestReader` construit un
  `ManifesteExtension` (kernel-sdk) a partir de n'importe quelle source respectant
  ce contrat.
- **`PluginLoader`** (`loader/`) — comment un plugin est physiquement charge et
  decharge. `Pf4jPluginLoader` est adossee a PF4J (`org.pf4j`, Apache 2.0, le choix
  technologique acte pour EGEN en remplacement d'OSGi) pour l'isolation de
  classloader et le cycle de vie physique. PF4J recherchant sa **propre** annotation
  d'extension par defaut, jamais celle d'EGEN, `Pf4jPluginLoader` fait elle-meme,
  apres chargement, le balayage du JAR a la recherche des classes annotees
  `@Extension` (kernel-sdk) et les verifie contre le point qu'elles declarent
  servir.
- **`ExtensionRegistry`** (`registry/`) — le registre vivant des extensions
  chargees, thread-safe, independant du mecanisme physique qui les a decouvertes.

**`PluginLifecycleManager`** (`lifecycle/`) est le seul point d'entree, avec un
ordre de verification strict et jamais permute pour `charger(...)` :
`KernelPermissionCheck` (le sujet a-t-il le droit administratif de declencher un
chargement ?) → lecture et validation du Manifeste (echec → `PolitiqueNoyau`,
toujours un refus) → `ModuleActivationResolver` (ce module doit-il tourner dans
cette Cellule ?) → verification des dependances declarees → alors seulement,
chargement physique. `decharger(...)` refuse tant qu'un autre module charge declare
encore en dependre — jamais de cascade implicite.

Cette classe est un bean CDI a injection **par constructeur**, deliberement
instanciable a la main dans les tests, sans conteneur ni Docker : toute la logique
de decision — l'essentiel de la valeur de ce module — est couverte par des tests
unitaires purs, avec un `PluginLoader` de test entierement en memoire
(`FakePluginLoader`) qui prouve, par sa seule existence, que
`PluginLifecycleManager` ne se comporte pas differemment selon le mecanisme physique
qui le sert. Seule `Pf4jPluginLoader`, qui touche reellement PF4J, n'est pas encore
couverte par un test d'integration reel, faute d'un premier plugin JAR dans ce
depot — voir son javadoc pour le detail de cette limite assumee.

## kernel-eventbus — le systeme nerveux, livre le 26 juillet 2026

Le Bus d'Evenements (anatomie du Kernel, §4) : porte l'annonce qu'un fait s'est
produit, sans jamais dicter ce qu'il faut en faire, ni jamais porter la verite
lui-meme (la verite est deja en base avant toute publication).

- **`eventbus-api`** (Niveau 0) — le contrat neutre (`EventBus`, `EventHandler`,
  `Abonnement`) et **`InMemoryEventBus`**, son unique implementation Niveau 0 :
  thread-safe, zero dependance externe, isolation stricte des gestionnaires en echec
  (jamais propage a l'emetteur ni aux autres souscripteurs). C'est le repli toujours
  disponible, avant meme qu'un courtier externe ne soit joignable.
- **`eventbus-kafka-adapter`** (Niveau 2, "system") — **`KafkaEventBusAdapter`**,
  adossee a Kafka (le choix technologique acte pour EGEN). Rattachee physiquement a
  `kernel-eventbus/` plutot qu'a `egen-modules/system/`, par decision explicite de
  la Charte v3 (§A.6) : le Bus est une infrastructure coeur que le Kernel demarre
  lui-meme, pas un plugin metier optionnel.

**Convention retenue** : un topic Kafka par systeme d'origine (`egen.<systeme>`,
ex. `egen.organisation` porte a la fois `organisation.affectation.terminee` et
`organisation.tutelle.etablie`) plutot qu'un topic par type exact — evite une
proliferation de topics et rend la souscription par prefixe directe. Cle de
partition : `contexteId`, pour un ordre de livraison preserve par Contexte.

**Decision de conception assumee** : la charge utile generique d'un evenement
traverse Kafka comme une structure JSON (`EnvelopeJson`, un DTO non generique —
un record generique introduirait une ambiguite de type a la deserialisation que
Jackson ne resout qu'avec une information explicite). Un gestionnaire recevra
typiquement une `java.util.Map` pour une charge utile structuree, pas l'instance
Java d'origine — une limite reelle de cette premiere livraison, documentee dans le
javadoc de `KafkaEventBusAdapter`, pas cachee.

**Contrainte respectee** : `KafkaConsumer` n'est pas thread-safe — `subscribe` et
`poll` doivent toujours provenir du meme thread. Les methodes de
souscription/desabonnement, appelables depuis n'importe quel thread, ne font donc
que mettre a jour un ensemble partage (`volatile`) ; le thread unique de
consommation relit et applique cet ensemble lui-meme, a chaque iteration, avant de
scruter.

**Limite assumee** : comme `Pf4jPluginLoader`, `KafkaEventBusAdapter` n'est pas
couverte par un test d'integration reel dans ce depot, faute d'un courtier Kafka
disponible dans ce sandbox. Sa logique de correspondance et d'isolation des
gestionnaires en echec est neanmoins identique, dans son intention, a celle
d'`InMemoryEventBus` — entierement testee, elle, avec 20 tests couvrant chaque
combinaison (type exact, prefixe, gestionnaires multiples, gestionnaire en echec,
desabonnement cible et par module).

## Le DAG de dependances, desormais impose mecaniquement

Le reacteur Maven calcule l'ordre de construction et refuse tout cycle — ca a
toujours ete vrai. Ce qui ne l'etait pas jusqu'au 22 juillet 2026 : rien n'empechait
mecaniquement une dependance croisee **non cyclique** mais interdite (par exemple un
module qui importerait directement le `-impl` d'un autre plutot que son `-api`). Le
`pom.xml` racine porte desormais une execution `maven-enforcer-plugin` (regle
`bannedDependencies`), heritee par tous les modules du reacteur, qui bloque
explicitement toute dependance de scope compile/runtime vers un artefact `-impl` ou
un provider concret — seul un scope `test` reste tolere (integration reelle avec une
vraie implementation CDI, ex. `organization-impl` -> `identity-provider-keycloak`).
Voir les commentaires dans le `pom.xml` racine pour le detail. `kernel-bootstrap`
exerce desormais reellement l'unique exception assumee : son propre `pom.xml`
desactive explicitement cette regle (execution `enforce-niveau2-impl-isolation`,
`<skip>true</skip>`), avec le meme commentaire de justification que celui deja
annonce dans le `pom.xml` racine — c'est la seule dependance compile-scope de tout
le depot vers `identity-provider-keycloak`.

## kernel-bootstrap — la composition finale, livree le 27 juillet 2026

L'app Quarkus reelle. `EgenKernelApplication` (`@QuarkusMain`) ne fait que
declencher `KernelBootSequence` et journaliser son bilan — aucune logique metier,
conformement au principe pose des le premier jour pour ce module.

Assemble, en scope compile : tous les systemes Niveau 0/1 (kernel-sdk,
kernel-jpa-support, module-domain, identity, authorization, policy,
module-registry), kernel-plugin-engine, kernel-eventbus (les deux modules), et
**`identity-provider-keycloak`** — le seul provider Niveau 2 disponible a ce jour,
traite comme une infrastructure coeur (au meme titre que
`eventbus-kafka-adapter`) plutot que comme un plugin metier charge dynamiquement.
N'assemble jamais de module business : ceux-la restent des candidats au
chargement dynamique via `kernel-plugin-engine`, jamais des dependances Maven de ce
module.

**`PluginDirectoryScanner`** decouvre les candidats dans un repertoire de plugins
(convention : `<moduleId>.jar` + `<moduleId>.properties`, en paire, directement dans
le repertoire) — configurable (`egen.kernel.plugins-directory`, defaut `plugins`).
**`KernelBootSequence`** scanne puis tente de charger chaque candidat trouve, pour
le compte du sujet bootstrap.

**Decision de conception assumee** : `ModuleActivationResolver` verifie l'Activation
d'un module pour *une* Cellule precise. Au tout premier demarrage, avant qu'aucune
hierarchie organisationnelle ne soit necessairement consultable, cette sequence
utilise une unique Cellule racine, configuree (`egen.kernel.cellule-racine`,
obligatoire, sans valeur par defaut — un echec de demarrage franc plutot qu'une
valeur inventee silencieusement). Charger un module pour d'autres Cellules, au fil
de l'exploitation reelle, reste une operation administrative posterieure au
demarrage, hors scope de cette premiere livraison.

**Consequence de ce large assemblage sur Flyway** : identity (V1), authorization
(V2) et module-registry (V3) partagent desormais une seule execution Flyway de
production (une seule base partagee, un seul jeu de migrations resolu, table
`flyway_schema_history_kernel`) — chaque module garde neanmoins sa propre table pour
SES PROPRES tests autonomes, ou cette renumerotation n'a aucune consequence.

**Trois producteurs CDI necessaires** (`KernelBootConfig`) : `ManifestReader`,
`ExtensionRegistry` et `PluginLoader` (kernel-plugin-engine) sont des classes
volontairement simples, sans annotation CDI propre, pour rester instanciables a la
main dans leurs propres tests — c'est kernel-bootstrap, la racine de composition,
qui leur donne une portee CDI, jamais kernel-plugin-engine lui-meme.

**Tests** : `KernelBootSequenceTest` verifie la sequence complete contre de
**vraies** implementations (`KernelPermissionCheckImpl`, `ModuleActivationResolverImpl`
— Testcontainers — et `PolitiqueNoyauImpl`), avec un `FakePluginLoader` local pour le
seul maillon qui necessiterait un plugin JAR physique — la premiere verification de
bout en bout de toute la chaine de gouvernance avec de vraies donnees en base.
`PluginDirectoryScannerTest` couvre la decouverte de fichiers en isolation.

## Etat d'avancement

| Module | Niveau / Categorie | Statut |
|---|---|---|
| `kernel-sdk` | 0 | ✅ Livre — extension, event, Contexte, Manifeste d'Extension, Socle de Traçabilite |
| `kernel-jpa-support` | 0 (partage) | ✅ Livre — TracabiliteEmbeddable |
| `kernel-systems/identity` | 1 (primitif) | ✅ Livre — `KernelSubject` (kernel-sdk) + `KernelSubjectService`, sans persistance |
| `kernel-systems/authorization` | 1 (primitif) | ✅ Livre — `KernelCapability` (kernel-sdk) + `KernelPermissionCheckImpl`, octrois/revocations avec Traçabilite complete |
| `kernel-systems/policy` | 1 (primitif) | ✅ Livre — `PolitiqueNoyau` (kernel-sdk) + `PolitiqueNoyauImpl`, refuse systematiquement |
| `kernel-domain/module-domain` | 0 | ✅ Livre — `ModuleId`, `CatalogueEntree`, `Souscription`, `Activation` : vocabulaire pur, zero framework |
| `kernel-systems/module-registry` | 0 | ✅ Livre — cascade Catalogue → Souscription → Activation, `ModuleActivationResolver` fail-closed |
| `kernel-plugin-engine` | 0 | ✅ Livre — `ManifestReader`, `ExtensionRegistry`, `PluginLifecycleManager` (orchestrateur), `PluginLoader` + `Pf4jPluginLoader` |
| `kernel-eventbus` | 0 | ✅ Livre — `EventBus`/`InMemoryEventBus` (`eventbus-api`), `KafkaEventBusAdapter` (`eventbus-kafka-adapter`) |
| `kernel-bootstrap` | 0 | ✅ Livre — `EgenKernelApplication`, `KernelBootSequence`, `PluginDirectoryScanner` |
| `kernel-test-support` | 0 | ✅ Livre — `TracabiliteFixtures`, `FakeKernelPermissionCheck`, `FakeModuleActivationResolver`, `PostgresTestResource` |
| `egen-modules/system/identity` (`identity-provider-api` + `identity-provider-keycloak`) | 2, system | ✅ Livre — Personne, Compte, Historique d'Identite (provider Keycloak) |
| `egen-modules/business/organization` | 2, business | ✅ Livre — Organisation, Cellule (+ Fermeture Transitive), Lexique, Tutelle, Succession ; sous-domaine `.affiliation` (Affectation, Mandat, Delegation) ; sous-domaine `.politique` (Politique organisationnelle, Derogation) |
| `egen-modules/business/reference-data` | 2, business | ✅ Livre — Pays, Langue, Devise, Fuseau Horaire, Unite de Mesure, Modele Sectoriel, Type de Cellule Modele, Mandat Modele |
| `egen-modules/system/authorization` (SpiceDB), `egen-modules/system/communication` | 2, system | À venir |
| `egen-modules/business/resource` | 2, business | À venir |

## Convention de versionnement Flyway

Chaque module gere sa propre sequence Flyway et sa propre table
`flyway_schema_history_*` — plus de numerotation globale unique sur l'ensemble de la
plateforme comme avant le 22 juillet 2026. **Nuance importante, corrigee apres un
premier echec CI sur ce refactoring** : "propre sequence" ne veut pas dire "peut
toujours reprendre a V1 sans regarder les autres modules". Flyway valide l'unicite
des numeros de version au sein de l'**ensemble de migrations resolu pour une
execution donnee** (les locations combinees), jamais au sein d'une seule table
d'historique. Deux modules qui ne se combinent jamais dans une meme execution (ex.
`reference-data-impl`, qui n'a besoin des migrations d'aucun autre module) peuvent
chacun reprendre a V1 sans risque. Mais des qu'un module ajoute la location d'un
autre a la sienne pour ses tests d'integration reels — ce qui est exactement le cas
de `organization-impl`, qui a besoin des tables `identity` pour verifier une
reference Personne — leurs numeros doivent rester mutuellement uniques dans cet
ensemble combine, meme si leurs tables d'historique restent bien distinctes en
production.

| Module | Sequence | Combine avec (tests) |
|---|---|---|
| `identity-provider-keycloak` | V1 (identity) | — (mais reserve V1 pour tout module qui le combinera) |
| `reference-data-impl` | V1 (referencedata) | aucun — independance reelle |
| `organization-impl` | V2 (organization), V3 (affiliation), V4 (politique organisationnelle) | `identity` (V1) — d'ou le decalage a partir de V2 |
| `kernel-systems/authorization` | V1 (authorization) | aucun — independance reelle |
| `kernel-systems/module-registry` | V1 (moduleregistry) | aucun — independance reelle |

Quand un module a legitimement besoin des tables d'un autre pour ses tests
d'integration reels (ex. `organization-impl` a besoin d'`identity` pour verifier une
reference Personne), il ajoute la location Flyway de l'autre module en plus de la
sienne dans son `application.properties`, et declare l'artefact correspondant en
dependance de scope **test** uniquement (jamais compile/runtime — voir la regle
Enforcer ci-dessus).

## kernel-test-support — fixtures et doublures communes, livre le 28 juillet 2026

Reduit la duplication accumulee au fil des modules precedents, sans jamais toucher
aux tests deja livres et verts :

- **`TracabiliteFixtures`** — `Tracabilite.initiale(Acteur.systeme("test"),
  OrigineDonnee.SAISIE_MANUELLE)` etait deja duplique, verbatim, dans des dizaines
  de fichiers de test. Disponible pour tout code de test ecrit desormais.
- **`FakeKernelPermissionCheck`**, **`FakeModuleActivationResolver`** — versions
  canoniques, partagees entre `kernel-plugin-engine` et `kernel-bootstrap`.
- **`PostgresTestResource`** — ressource Testcontainers explicite (`postgres:16`,
  meme version que `docker-compose.yml`), pour les besoins qu'un Dev Services
  automatique ne couvre pas encore. Testee (verifie une vraie connexion, pas
  seulement que le conteneur demarre).

**Contrainte de conception respectee** : `FakePluginLoader` n'a volontairement pas
sa place ici. Le partager aurait exige que `kernel-test-support` depende de
`kernel-plugin-engine` — et les tests de `kernel-plugin-engine` consommant a leur
tour `kernel-test-support` auraient ferme un cycle de reacteur Maven
(`kernel-plugin-engine` test → `kernel-test-support` compile → `kernel-plugin-engine`
compile), que Maven refuse categoriquement. `kernel-plugin-engine` et
`kernel-bootstrap` gardent donc chacun leur propre copie locale de
`FakePluginLoader` — un petit cout de duplication assume plutot qu'une
"solution" qui casserait le build.

**Deliberement absents, pour la meme raison que `Pf4jPluginLoader` et
`KafkaEventBusAdapter` n'ont pas de test d'integration reel** : `KeycloakTestResource`
et `SpiceDbTestResource`. Aucun code de ce depot n'integre encore reellement OIDC ou
SpiceDB — les construire maintenant serait de la speculation non verifiable, pas de
la rigueur. A ajouter avec le meme soin des qu'un premier consommateur reel existera
(veritable integration OIDC dans `identity-provider-keycloak`,
`authorization-provider-spicedb`).

## Construire le projet

Prerequis : JDK 21, Maven 3.9+, Docker (requis par Quarkus Dev Services pour les
tests d'integration des modules `-impl`, qui provisionnent un PostgreSQL ephemere
automatiquement).

```bash
mvn -B verify
```

La CI GitHub Actions (`.github/workflows/ci.yml`) reconstruit et teste l'integralite
du reacteur a chaque push sur `main` et sur chaque pull request — c'est la porte de
validation faisant foi du projet, Docker etant disponible nativement sur les runners
GitHub-hosted.

### Lancer le Kernel localement

`docker-compose.yml`, a la racine du depot, fournit Postgres, Kafka (mode KRaft),
Keycloak et SpiceDB (ce dernier provisionne par avance ; aucun provider ne le
consomme encore) :

```bash
docker compose up -d postgres kafka keycloak
cp .env.example .env
mvn -pl egen-kernel/kernel-bootstrap -am quarkus:dev
```

En mode dev, Quarkus Dev Services prend le relais pour Postgres si aucune URL n'est
explicitement fournie — `docker-compose.yml` reste utile pour une base persistante
entre deux redemarrages, ou pour Kafka/Keycloak/SpiceDB, qu'aucun Dev Service ne
provisionne encore automatiquement dans ce depot.
