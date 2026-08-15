# EGEN — Charte d'Architecture & Modele Organisationnel (v3, consolidee)

> Ce document est la **reference faisant foi** pour toute decision d'architecture
> dans ce depot, depuis le refactoring engage le 22 juillet 2026. Il remplace et
> fusionne les deux documents precedents (la charte d'architecture noyau "paradigme
> noyau Linux", et le plan organisationnel Organisation/Cellule/Lexique/Tutelle...).
> Toute incoherence entre ce document et le code doit etre traitee comme un bug a
> corriger dans le code, jamais comme une raison de modifier silencieusement ce
> document.

## Statut de ce document

Il integre les decisions actees le 22 juillet 2026 :

- **Point 1 (mono-tenant par instance)** — confirme.
- **Point 2 (classification Policy/Resource/Reference-data)** — confirme.
- **Point 3 (contenu exact du primitif Niveau 1)** — **une premiere proposition
  concrete a ete conçue et implementee le 23 juillet 2026** (voir §A.5) ; toujours
  susceptible d'evoluer a l'usage.
- **Point 4 (structure `egen-modules/`)** — confirme, option structuree retenue.

Il signale aussi une tension nee de la combinaison des deux documents sources : la
Tutelle multi-organisation (§ Partie D) supposait une visibilite entre Organisations
que le modele mono-tenant par instance rend non triviale. **Cette tension a ete
tranchee le 22 juillet 2026, lors du refactoring** : la Tutelle reste strictement
intra-instance (voir la note ajoutee a la fin de la Partie D).

**Le 26 juillet 2026**, la Partie F ajoute le **Modele Reseau** : la federation
inter-instances entre Organisations souveraines, via Passerelle et Reseau —
strictement business (Niveau 2), sans detail d'implementation, close sans point
ouvert restant.

---

# PARTIE A — Le Noyau : philosophie et classification

## A.1 Le paradigme, en une phrase

Le Kernel EGEN est un noyau strictement neutre, facon noyau Linux : il ne connait ni
l'identite, ni l'organisation, ni l'autorisation metier, ni aucun domaine. Il fournit
un mecanisme pour charger, decharger et faire cohabiter des modules, plus le strict
minimum vital pour que ce mecanisme soit protege des le tout premier demarrage.

## A.2 Le modele a trois niveaux

| Niveau | Role | Analogie Linux | Remplacable ? |
|---|---|---|---|
| **Niveau 0** | Irreductible — le mecanisme de chargement et ce qui le protege | Scheduler, VFS, `modprobe`, `CAP_SYS_MODULE` | Jamais |
| **Niveau 1** | Primitif minimal, compile en dur, jamais retire — complete par le Niveau 2 | Permissions POSIX (rwx, UID/GID) | Non retire, seulement empile |
| **Niveau 2** | Pluggable, choisi a l'installation | `pam_ldap.so`, drivers, filesystems | Oui |

> Note de vocabulaire pour toute la suite du document : pour eviter toute confusion,
> **"Niveau"** designe exclusivement ce classement architectural (0/1/2) du noyau. La
> profondeur de la hierarchie organisationnelle (Organisation -> Cellule -> Cellule
> fille...) est designee par **"Palier"** dans ce document — les deux mots existaient
> sous "Niveau" dans les documents sources, ce qui aurait cree une collision reelle
> dans le code et dans les discussions d'equipe.

## A.3 Decisions actees dans cette version

### Point 1 — Modele mono-tenant par instance : confirme

Chaque entreprise cliente recoit une instance deployee separee et complete du projet.
Consequences concretes :

- **SpiceDB** : une instance par deploiement. Plus besoin de prefixage tenant dans les
  relations.
- **Keycloak** : plus besoin de realms multiples pour isoler des Organisations entre
  elles — un deploiement, une Organisation. Un realm peut toujours structurer
  l'interne (par Cellule, par exemple), mais l'isolation inter-tenant au sens SaaS
  mutualise disparait du probleme.
- **Le schema reste generique malgre tout** : `Organisation` reste une entite a part
  entiere dans le modele de donnees (pas une hypothese codee en dur qu'il n'y en a
  qu'une), pour ne pas fermer la porte a un scenario futur ou un deploiement voudrait
  heberger plusieurs Organisations (un groupe avec filiales, par exemple).
  Operationnellement, chaque deploiement en a une seule au demarrage.
- **Consequence en cascade non anticipee** : la Tutelle multi-organisation (CHU
  rattache a deux Organisations) devient une vraie question d'architecture
  inter-instances, pas juste inter-tables. Traite en Partie D — et desormais tranche :
  intra-instance uniquement (voir la note de fin de Partie D).

### Point 2 — Policy, Resource, Reference-data : confirme

Decision : Policy (B1) rejoint le meme niveau qu'Identity — **Niveau 1**. Resource
(B3) et Reference-data (B4) vont directement en **Niveau 2**, parce qu'ils sont
rattaches a des modules Niveau 2 (Organisation, Cellule).

Le raisonnement se generalise en une regle utile pour classer tout futur systeme :

> **Un systeme appartient au Niveau 2 des que son modele de donnees est
> intrinsequement dependant d'un concept Niveau 2 (Organisation, Cellule). Un
> systeme appartient au Niveau 1 quand le noyau a besoin d'une reponse par defaut
> avant meme qu'un module Niveau 2 ne soit charge.**

C'est exactement pourquoi Resource (une salle, un document — toujours rattache a une
Cellule ou une Organisation) et Reference-data (les Modeles Sectoriels, qui n'ont de
sens que pour peupler le Lexique d'une Organisation, voir § B.5) suivent Organisation
en Niveau 2, alors que Policy suit Identity en Niveau 1 : le noyau a besoin d'une
resolution de politique par defaut (fail-closed, comportement de demarrage) avant
meme qu'un module de gouvernance metier ne soit charge — exactement comme il a
besoin d'un sujet minimal (Identity) et d'une verification de permission minimale
(Authorization) avant que Keycloak ou SpiceDB ne soient disponibles.

**Point de vigilance terminologique** — ne pas confondre :
- **"Politique-noyau"** (B1, Niveau 1) : la resolution de politique par defaut du
  Kernel lui-meme — comportement de secours, validation minimale de Manifeste,
  decisions fail-closed avant chargement d'un module de gouvernance.
- **"Politique organisationnelle"** (§ B.12, Niveau 2) : les regles de configuration
  qu'une Organisation ou une Cellule definit pour elle-meme (politique de mot de
  passe, etc.), avec Derogation en cascade. Ce concept vit entierement dans le
  module Organisation — ce n'est pas B1.

Ce sont deux systemes differents qui portent le meme mot dans le langage courant. Il
faut les nommer differemment dans le code (`kernel.policy` vs `organization.politique`
par exemple) pour ne jamais les confondre en revue de code.

> **Note de refactoring (22 juillet 2026)** : le code livre initialement sous
> `kernel-systems/policy` implementait en realite integralement la Politique
> organisationnelle (Contexte + Derogation en cascade), jamais la Politique-noyau.
> Il a ete deplace vers `egen-modules/business/organization` (sous-package
> `.politique`). La vraie Politique-noyau (Niveau 1) reste entierement a concevoir —
> voir le Point 3.

### Point 4 — Structure `egen-modules/` : confirme, option structuree

```
egen-modules/
├── system/       (providers — equivalent PAM/NSS : identity-keycloak-provider, authorization-spicedb-provider, communication-*-provider)
└── business/      (modules metier — equivalent applications utilisateur : organization, resource, reference-data, academie, RH, finance...)
```

> **Note de refactoring (22 juillet 2026)** : suivant cette structure, le provider
> Identite livre a ce jour vit desormais sous `egen-modules/system/identity/` et se
> nomme `identity-provider-keycloak` (avec un contrat generique
> `identity-provider-api` a cote, pour accueillir d'autres providers au fil du temps
> — Auth0, LDAP... — sans jamais casser ce qui en depend). C'est une precision de
> nommage par rapport a la denomination `identity-keycloak-provider` employee plus
> haut ; les deux designent le meme composant.

## A.4 Classification complete — table de reference mise a jour

| Systeme | Niveau | Categorie | Statut |
|---|---|---|---|
| `kernel-sdk` | **0** | — | Confirme |
| `kernel-plugin-engine` (PF4J) | **0** | — | **Livre** le 25 juillet 2026 |
| `module-registry` (B2) | **0** | — | **Livre** le 24 juillet 2026 |
| `kernel-bootstrap` | **0** | — | **Livre** le 27 juillet 2026 |
| `kernel-eventbus` (API) | **0** | — | **Livre** le 26 juillet 2026 |
| `eventbus-kafka-adapter` | **2** | system | **Livre** le 26 juillet 2026 |
| Identity — primitif | **1** | — | **Livre** le 23 juillet 2026 (`KernelSubject` + `KernelSubjectService`) |
| Authorization — primitif | **1** | — | **Livre** le 23 juillet 2026 (`KernelCapability` + `KernelPermissionCheckImpl`) |
| **Policy-noyau (B1)** | **1** | — | **Livre** le 23 juillet 2026 (`PolitiqueNoyauImpl`) |
| Identity riche (Keycloak) | **2** | system | Propose — **livre** sous `identity-provider-keycloak` |
| Authorization riche (SpiceDB) | **2** | system | Confirme |
| Communication (E1) | **2** | system | Propose |
| Audit (E2) — contrat d'emission | **1** | — | Propose |
| Audit (E2) — moteur de stockage/chainage | **2** | system | Propose |
| **Organization (A2) + Affiliation (A3)** — Cellule, Lexique, Tutelle, Affectation, Mandat, Delegation, Politique organisationnelle | **2** | business | **Confirme** (niveau) ; fusion des deux en un seul module **actee et realisee** le 22 juillet 2026, voir § C.2 |
| **Resource (B3)** | **2** | business | **Confirme** |
| **Reference-data (B4)** — porte les Modeles Sectoriels | **2** | business | **Confirme** (niveau et categorie — voir § C.3) |

## A.5 Le primitif Niveau 1 — proposition concrete implementee le 23 juillet 2026 (point 3)

Trois composants, concus ensemble, aucun dependant d'un systeme Niveau 2 :

- **Identity** (`kernel-sdk/permission/identity` + `kernel-systems/identity`) :
  `KernelSubject` — sujet minimal, l'equivalent UID/GID, un simple identifiant
  opaque (UUID) + un indicateur bootstrap. Le sujet bootstrap porte un identifiant
  fixe et reserve (`KernelSubject.BOOTSTRAP_ID`, jamais genere) — l'equivalent
  d'UID 0. `KernelSubjectService` ne persiste rien : reconnaitre le bootstrap est
  une comparaison a une constante, jamais une consultation de base de donnees. Il
  fournit aussi le seul pont assume vers le Socle de Traçabilite existant
  (`versActeur`), toujours via `Acteur.systeme(...)` — jamais `Acteur.personne(...)` :
  ce primitif ignore delibrement s'il correspond a une Personne reelle, cette
  connaissance restant entierement du ressort du provider Identite riche Niveau 2.
  *Decision de conception assumee* : `Acteur` (kernel-sdk, tracabilite) n'a pas ete
  etendu d'une troisieme variante pour ce cas — cela aurait exige une nouvelle
  colonne sur *toutes* les tables de toute la plateforme (chaque entite porte
  integralement le Socle de Traçabilite), un impact disproportionne pour un besoin
  couvert correctement par la variante SYSTEME existante avec une convention de
  nommage claire (`kernel-bootstrap` / `kernel-sujet:<id>`).

- **Authorization** (`kernel-sdk/permission/authorization` + `kernel-systems/authorization`) :
  `KernelCapability` — ensemble ferme de quatre capacites noyau (`CHARGER_MODULE`,
  `DECHARGER_MODULE`, `ENREGISTRER_EXTENSION`, `ADMINISTRER_CAPACITES_NOYAU`).
  `KernelPermissionCheck` (contrat) + `KernelPermissionCheckImpl`, fail-closed par
  construction : seul le sujet bootstrap est autorise sans preuve explicite ; tout
  autre sujet doit disposer d'un `KernelCapabiliteOctroi` actif, sans quoi la
  Politique-noyau tranche (toujours refus). Administrer les capacites d'autrui
  exige soi-meme `ADMINISTRER_CAPACITES_NOYAU` (ou le sujet bootstrap) — verifie a
  chaque octroi/revocation par le service lui-meme, via son propre
  `KernelPermissionCheck`. Une revocation est toujours une suppression logique
  (Traçabilite complete), jamais une suppression physique ; un index PostgreSQL
  unique partiel (`WHERE supprime_le IS NULL`) empeche mecaniquement deux octrois
  actifs simultanes pour le meme couple (sujet, capacite).

- **Policy-noyau** (`kernel-sdk/permission/policy` + `kernel-systems/policy`) :
  `PolitiqueNoyau` (contrat) + `PolitiqueNoyauImpl`. Repond a quatre questions
  fermees (`PolitiqueNoyauQuestion`) — echec de construction d'un Manifeste,
  Activation non resolue, capacite noyau non accordee, gouvernance Niveau 2
  indisponible — et refuse systematiquement, sans aucune exception ni
  configuration possible. Aucune persistance : chaque reponse est pure et
  deterministe, ce qui lui permet de fonctionner avant que PostgreSQL, Keycloak ou
  SpiceDB ne soient joignables. La Politique-noyau ne "s'assouplit" jamais ; elle
  est simplement court-circuitee, question par question, des qu'un module de
  gouvernance Niveau 2 competent prend le relais — c'est deja le cas pour
  `ACTIVATION_NON_RESOLUE` : `module-registry` (§A.6bis) consulte `PolitiqueNoyau`
  uniquement quand aucune Activation active n'est trouvee.

**Ordre de dependance entre les trois** (aucun cycle) : identity et policy ne
dependent que de kernel-sdk ; authorization depend des deux autres pour repondre
completement a sa propre question. Aucun des trois n'est scinde en `-api`/`-impl` a
la maniere des systemes Niveau 2 : le contrat vit deja dans kernel-sdk, et il
n'existe qu'une seule implementation possible de chaque primitif, jamais
substituable — la separation api/impl n'aurait ici aucune fonction protectrice.

**Statut** : implemente, pousse sur `main`, CI verte. Ce n'est pas presente comme
la seule conception possible — c'est une proposition rigoureuse, coherente avec le
reste de la plateforme, ouverte a revision si l'usage reel revele un besoin non
anticipe ici. Deux confirmations obtenues : `module-registry` (§A.6bis, 24 juillet)
consomme `PolitiqueNoyau` sans friction ; `kernel-plugin-engine` (§A.6ter, 25
juillet) consulte a la fois `KernelPermissionCheck` ET `ModuleActivationResolver`
avant tout chargement — le test le plus exigeant envisage pour cette conception,
reussi sans ajustement necessaire.

## A.6bis Module-registry (B2) — livre le 24 juillet 2026, neutralise le 15 aout 2026

Premiere brique Niveau 0 construite au-dela du primitif Niveau 1 : Catalogue,
Souscription, Activation (§B.2, §B.11), avec la cascade strictement imposee au
niveau service — un module doit etre au Catalogue avant qu'un Contexte ne puisse
y Souscrire ; ce Contexte doit avoir une Souscription active avant qu'un Contexte
qui en depend ne puisse Activer le module.

`kernel-domain/module-domain` porte le vocabulaire pur (`ModuleId`,
`CatalogueEntree`, `Souscription`, `Activation`), sans aucun framework — reserve
explicitement a part du reste de l'implementation par la Charte (§A.6), a la
difference des primitifs Niveau 1 qui gardent leur domaine directement dans
`kernel-systems/`.

**Decision de conception assumee** : `Souscription.contexteId` et
`Activation.contexteId` sont des UUID nus, jamais une reference typee vers un
module Niveau 2. Consequence directe : module-registry ne verifie jamais
lui-meme qu'un Contexte appartient bien au Contexte dont on affirme la
Souscription — cette resolution reste la responsabilite de l'appelant
(`ActiverModuleCommand` recoit les deux identifiants explicitement, sous les
noms `contexteSouscripteurId` et `contexteCibleId`). Egalement assume : aucune
verification `KernelPermissionCheck` ne gate les ecritures de
Souscription/Activation dans cette premiere livraison — aucune des quatre
`KernelCapability` fermees ne couvre cette question business, qui reste, pour
l'instant, la responsabilite de l'appelant.

**Note de neutralisation (15 aout 2026)** : cette section decrivait jusqu'ici
`Souscription.organisationId` et `Activation.celluleId`, avec un renvoi explicite
au duo Organisation/Cellule du module business Organization. Passe de
neutralisation complete du Kernel : `ContexteNature` (kernel-sdk, l'enumeration
fermee `{ORGANISATION, CELLULE}`) a ete supprimee, `Contexte` reduit a son seul
identifiant (`UUID id()`), et tout champ ou variable nommant explicitement
Organisation/Cellule dans `egen-kernel/` renomme vers le vocabulaire neutre
`contexteId`/`contexteSouscripteurId`/`contexteCibleId`/`contexteRacine`. Meme
traitement pour `ManifesteExtension` (kernel-sdk), qui exposait deux champs
(`cellTypesProvided`, `mandatesProvided`) directement calques sur le Lexique du
module business Organization — retires, sans equivalent generique invente a la
place : un module Niveau 2 qui a besoin d'etendre son propre vocabulaire le fait
entierement dans son propre module, jamais via un champ hardcode dans le SDK.
Migration SQL V3 (module-registry) editee en place (`organisation_id`/
`cellule_id` -> `contexte_id`) plutot que versionnee a nouveau : projet encore en
0.1.0-SNAPSHOT, aucune base de production concernee. Toute base de developpement
locale deja migree doit etre recreee (`flyway clean` puis remigration, ou drop
direct) avant la prochaine execution.

`ModuleActivationResolver` est la question que `kernel-plugin-engine` (§A.6ter, livre)
posera avant de charger un module dans un Contexte : fail-closed, elle retombe sur
`PolitiqueNoyau` (`ACTIVATION_NON_RESOLUE`) des qu'aucune Activation active n'est
trouvee — la premiere consultation reelle de la Politique-noyau par un module autre
que ses propres tests.

## A.6ter kernel-plugin-engine — livre le 25 juillet 2026

Le mecanisme d'accueil (§1 de l'anatomie du Kernel), avec trois points
d'extensibilite deliberes : `ManifestSource` (d'ou viennent les donnees brutes d'un
Manifeste — `PropertiesFileManifestSource` livree), `PluginLoader` (comment un
plugin est physiquement charge — `Pf4jPluginLoader`, adossee a PF4J, livree), et
`ExtensionRegistry` (le registre vivant des extensions chargees, thread-safe).

`PluginLifecycleManager` est le seul point d'entree, avec un ordre de verification
strict pour `charger(...)` : `KernelPermissionCheck` (capacite administrative du
sujet) → lecture/validation du Manifeste (echec → `PolitiqueNoyau`, toujours un
refus) → `ModuleActivationResolver` (le module doit-il tourner ici ?) →
verification des dependances declarees → chargement physique. `decharger(...)`
refuse tant qu'un module charge en depend encore.

**Decision de conception assumee** : PF4J recherche sa propre annotation
d'extension par defaut (`org.pf4j.Extension`), jamais celle d'EGEN
(`africa.civitas.egen.kernel.sdk.extension.Extension`). `Pf4jPluginLoader` ne
detourne donc jamais le mecanisme natif de PF4J vers l'annotation d'EGEN : apres que
PF4J a charge et isole le plugin (classloader dedie), `Pf4jPluginLoader` balaie
elle-meme le JAR a la recherche des classes annotees `@Extension` d'EGEN et les
verifie contre le point qu'elles declarent servir.

**Limite assumee et documentee** : `Pf4jPluginLoader` — la seule classe de ce
module qui touche reellement PF4J — n'est pas couverte par un test d'integration
reel dans ce depot, faute d'un premier plugin JAR physique a charger. Toute la
logique de decision (`PluginLifecycleManager`, `ManifestReader`,
`ExtensionRegistry`) est en revanche couverte a 100% par des tests unitaires purs,
sans Quarkus ni Docker : `PluginLifecycleManager` est un bean CDI a injection par
constructeur, instanciable a la main avec un `PluginLoader` de test entierement en
memoire — la preuve, par construction, que la logique de decision est independante
du mecanisme physique concret qui la sert.

## A.6quater kernel-eventbus — livre le 26 juillet 2026

Le systeme nerveux (anatomie du Kernel, §4) : `eventbus-api` (Niveau 0) porte le
contrat neutre (`EventBus`, `EventHandler`, `Abonnement`) et `InMemoryEventBus`, son
unique implementation Niveau 0 sans dependance externe. `eventbus-kafka-adapter`
(Niveau 2, "system", mais physiquement rattache ici — voir §A.6) porte
`KafkaEventBusAdapter`, adossee a Kafka.

Convention retenue : un topic Kafka par systeme d'origine (`egen.<systeme>`) plutot
qu'un topic par type exact d'evenement — evite une proliferation de topics et rend
la souscription par prefixe directe. Cle de partition : `contexteId`, pour un ordre
de livraison preserve par Contexte (jamais garanti globalement, ce que Kafka ne
garantit d'ailleurs jamais au-dela d'une partition).

**Decision de conception assumee** : la charge utile generique d'un evenement
traverse Kafka comme une structure JSON (`EnvelopeJson`, DTO non generique — un
record generique introduirait une ambiguite de type a la deserialisation que
Jackson ne resout qu'avec une information explicite). Un gestionnaire recevra
typiquement une `java.util.Map` pour une charge utile structuree, pas l'instance
Java d'origine. Limite reelle de cette premiere livraison, qu'un mecanisme
d'enregistrement avec classe cible explicite pourrait lever dans une iteration
future, sans que le contrat `EventBus` lui-meme n'ait besoin de changer.

**Limite assumee et documentee** : `KafkaEventBusAdapter`, comme `Pf4jPluginLoader`
dans kernel-plugin-engine, n'est pas couverte par un test d'integration reel dans ce
depot, faute d'un courtier Kafka disponible dans ce sandbox. Sa logique de dispatch
est neanmoins identique, dans son intention, a celle d'`InMemoryEventBus` — qui,
elle, est entierement testee (20 tests couvrant chaque combinaison de
correspondance, d'isolation des gestionnaires en echec et de desabonnement).

## A.6quinquies kernel-bootstrap — livre le 27 juillet 2026

La composition finale (Charte v3, §5) : `EgenKernelApplication` (`@QuarkusMain`) ne
declenche que `KernelBootSequence` et journalise son bilan, aucune logique metier —
principe respecte a la lettre.

Assemble en scope compile : tous les systemes Niveau 0/1, `kernel-plugin-engine`,
`kernel-eventbus` (les deux modules), et **`identity-provider-keycloak`** — seule
dependance compile-scope de tout le depot vers un provider Niveau 2 concret,
exerçant reellement l'unique exception a la regle d'isolation `-impl`/providers deja
annoncee au pom.xml racine (execution `enforce-niveau2-impl-isolation`, desactivee
explicitement dans le pom.xml de ce module). N'assemble jamais de module business.

**Decision de conception assumee** : `ModuleActivationResolver` verifie l'Activation
d'un module pour un Contexte precis ; au tout premier demarrage, `KernelBootSequence`
utilise donc un unique Contexte racine configure (`egen.kernel.contexte-racine`,
obligatoire, sans valeur par defaut). Le chargement pour d'autres Contextes, au fil
de l'exploitation reelle, reste une operation administrative posterieure au
demarrage — hors scope de cette premiere livraison, une simplification assumee et
documentee plutot que silencieuse.

**Consequence sur Flyway** : identity (V1), authorization (renumerotee V2) et
module-registry (renumerotee V3) partagent desormais une seule execution de
production (une seule base, un seul jeu de migrations resolu, table
`flyway_schema_history_kernel`) — chaque module garde sa propre table pour ses
propres tests autonomes, ou la renumerotation n'a aucune consequence. Meme
discipline de coordination que celle deja etablie pour organization/identity.

**Cablage CDI** : `ManifestReader`, `ExtensionRegistry` et `PluginLoader`
(kernel-plugin-engine) sont des classes volontairement simples, sans annotation CDI
propre, pour rester instanciables a la main dans leurs propres tests — trois
producteurs `@Produces` dans `KernelBootConfig` leur donnent une portee CDI,
jamais kernel-plugin-engine lui-meme.

**Premiere verification de bout en bout** : `KernelBootSequenceTest` exerce la
sequence complete contre de vraies implementations (`KernelPermissionCheckImpl`,
`ModuleActivationResolverImpl` — Testcontainers — et `PolitiqueNoyauImpl`), avec un
`FakePluginLoader` local pour le seul maillon qui necessiterait un plugin JAR
physique. C'est la premiere fois que toute la chaine de gouvernance (capacite
noyau, Activation, Politique-noyau) est verifiee ensemble, avec de vraies donnees
en base plutot qu'avec des doublures de chaque cote.

## A.6sexies kernel-test-support — livre le 28 juillet 2026

Fixtures et doublures communes, ajoutees sans jamais retoucher un test deja livre
et vert : `TracabiliteFixtures` (reduit une construction dupliquee dans des
dizaines de fichiers), `FakeKernelPermissionCheck` et `FakeModuleActivationResolver`
(versions canoniques, partagees entre `kernel-plugin-engine` et `kernel-bootstrap`),
`PostgresTestResource` (Testcontainers explicite, `postgres:16`).

**Contrainte de conception respectee** : `FakePluginLoader` reste volontairement
hors de ce module — le partager exigerait une dependance de
`kernel-test-support` vers `kernel-plugin-engine`, et les tests de
`kernel-plugin-engine` consommant a leur tour `kernel-test-support` fermeraient un
cycle de reacteur Maven. `kernel-plugin-engine` et `kernel-bootstrap` gardent donc
chacun leur propre copie locale — un cout de duplication mineur assume plutot
qu'une solution qui casserait le build.

**Deliberement differe, pour la meme raison que `Pf4jPluginLoader` et
`KafkaEventBusAdapter` n'ont pas de test d'integration reel** : `KeycloakTestResource`
et `SpiceDbTestResource`. Aucun code de ce depot n'integre encore reellement OIDC ou
SpiceDB — les construire maintenant serait de la speculation non verifiable. A
ajouter des qu'un premier consommateur reel existera.

## A.6 Arborescence noyau — mise a jour (etat reel au 28 juillet 2026)

```
egen-kernel/
├── kernel-sdk/                          (contrat, JPMS pur — + permission/{identity,authorization,policy})
├── kernel-jpa-support/                  (Socle de Tracabilite partage — inchange)
├── kernel-domain/
│   └── module-domain/                   (ModuleId, CatalogueEntree, Souscription,
│                                          Activation — LIVRE, B2, zero framework)
├── kernel-systems/
│   ├── identity/                        (KernelSubjectService — LIVRE, point 3)
│   ├── authorization/                   (KernelPermissionCheckImpl, octrois — LIVRE, point 3)
│   ├── policy/                          (PolitiqueNoyauImpl — LIVRE, point 3)
│   └── module-registry/                 (Catalogue/Souscription/Activation — LIVRE, B2)
├── kernel-plugin-engine/                (ManifestReader, ExtensionRegistry,
│                                          PluginLifecycleManager, PluginLoader +
│                                          Pf4jPluginLoader — LIVRE)
├── kernel-eventbus/
│   ├── eventbus-api/                    (EventBus, InMemoryEventBus — LIVRE, Niveau 0)
│   └── eventbus-kafka-adapter/          (KafkaEventBusAdapter — LIVRE, Niveau 2)
├── kernel-bootstrap/                    (EgenKernelApplication, KernelBootSequence,
│                                          PluginDirectoryScanner — LIVRE)
└── kernel-test-support/                 (TracabiliteFixtures, fakes canoniques,
                                           PostgresTestResource — LIVRE)

egen-modules/
├── system/
│   └── identity/
│       ├── identity-provider-api/       (contrat generique — LIVRE)
│       └── identity-provider-keycloak/  (implementation Keycloak — LIVRE)
│   └── (authorization/, communication/ — a venir)
└── business/
    ├── organization/                    (fusion Organisation + Rattachements + Politique
    │                                      organisationnelle — LIVRE)
    │   ├── organization-api/            (sous-packages .affiliation, .politique inclus)
    │   └── organization-impl/
    └── reference-data/                  (LIVRE)
        ├── reference-data-api/
        └── reference-data-impl/
```

---

# PARTIE B — Le Modele Organisationnel

*(Contenu consolide du plan recu, avec la terminologie « Palier » a la place de
« Niveau » pour eviter la collision avec la Partie A.)*

## B.1 Principes directeurs

1. **Souverainete organisationnelle** — une Organisation ne depend d'aucune autre
   pour definir sa structure, son vocabulaire ou ses politiques internes.
2. **Genericite totale** — aucun concept metier sectoriel n'est code en dur dans le
   schema. Tout est une instance typee d'un concept generique.
3. **Separation identite / appartenance** — une Personne existe independamment
   d'ou elle est rattachee. L'appartenance est toujours une relation, jamais un
   conteneur.
4. **Tracabilite temporelle** — rien n'est jamais ecrase.
5. **Autonomie en cascade** — chaque Palier de la hierarchie peut, s'il en a le
   droit, deroger aux regles du Palier superieur.

## B.2 Le Lexique EGEN

| Terme | Definition |
|---|---|
| **Organisation** | L'entite souveraine — ministere, universite, entreprise, ONG, hopital. Racine de tout. |
| **Cellule** | Le noeud generique et recursif de la hierarchie interne (Faculte, Departement, Classe, Agence, Service...). |
| **Etablissement** | Convention de nommage : une Cellule de Palier 1, directement rattachee a l'Organisation. Pas une table distincte. |
| **Type de Cellule** | La nomenclature qui donne son vocabulaire metier a une Cellule. Definie **par Organisation**. |
| **Lexique Organisationnel** | L'ensemble des Types de Cellule qu'une Organisation a choisi ou cree pour elle-meme. |
| **Modele Sectoriel** | Un Lexique pre-rempli fourni par EGEN, que l'Organisation adopte puis modifie librement. |
| **Tutelle** | La relation de rattachement d'un Etablissement a une ou plusieurs Organisations. |
| **Affectation** | La relation entre une Personne et une Cellule, avec un role et une periode de validite. |
| **Mandat** | Les droits et responsabilites portes par une Affectation active. |
| **Delegation** | Le transfert temporaire d'un Mandat, sans rompre l'Affectation d'origine. |
| **Ressource Locale** | Une ressource rattachee a une Cellule precise. |
| **Ressource Souveraine** | Une ressource rattachee directement a l'Organisation, partagee par relations explicites. |
| **Catalogue** | L'ensemble des modules que la plateforme EGEN sait proposer. |
| **Souscription** | Ce qu'une Organisation a choisi d'acquerir dans le Catalogue. |
| **Activation** | Ce qu'une Cellule precise a choisi d'allumer, parmi ce que l'Organisation a souscrit. |
| **Politique organisationnelle** | Regles de configuration definies a un Palier donne, heritees par defaut en dessous. |
| **Derogation** | Le droit d'une Cellule de remplacer une Politique organisationnelle heritee. |
| **Succession Organisationnelle** | La trace reliant une Cellule disparue a celle(s) qui lui succede. |

## B.3 a B.15

*(Contenu integral inchange par rapport a la version transmise le 22 juillet 2026 —
Organisation racine souveraine, Cellule recursive, Lexique et Modeles Sectoriels,
Tutelle, Personnes & Comptes, Affectation & Mandat, Delegation, Ressources
Locales/Souveraines, Catalogue/Souscription/Activation, Politique organisationnelle &
Derogation, Cycle de vie & Succession, schema de synthese, recommandations
techniques. Voir l'historique de conversation ou une version anterieure de ce fichier
pour le detail section par section si besoin de le re-derouler integralement —
aucune de ces sections n'a ete remise en cause par le refactoring du 22 juillet.)*

---

# PARTIE C — La Jointure : ou chaque concept vit dans l'architecture noyau

| Concept (Partie B) | Vit dans | Niveau | Depend de |
|---|---|---|---|
| Organisation, Cellule, Lexique, Tutelle, Affectation, Mandat, Delegation, Politique organisationnelle | `egen-modules/business/organization` | 2 | `identity-provider-api` (pour referencer une Personne sans la posseder) |
| Personnes / Comptes | `egen-modules/system/identity/identity-provider-keycloak` | 2 | `identity-provider-api` |
| Ressource (Locale/Souveraine) | `egen-modules/business/resource` (a venir) | 2 | `organization-api` |
| Modeles Sectoriels | `egen-modules/business/reference-data` | 2 | — (seed data consommee par `organization` a la creation) |
| Catalogue / mecanisme Souscription-Activation | `kernel-systems/module-registry` (B2) — **livre** | 0 | `kernel-domain/module-domain` |
| Enregistrements Souscription/Activation d'une Organisation donnee | Base de module-registry, valides par B2 au chargement | 0 (mecanisme) + 2 (donnees) | — |
| Synchronisation Affectation/Delegation/Tutelle -> SpiceDB | Evenements via `kernel-eventbus` (0) — **livre** — consommes par `authorization-provider-spicedb` (2, a venir) | 0 + 2 | — |

**C.1 — Sur la fusion Organization (A2) + Affiliation (A3)**
**Actee et realisee** le 22 juillet 2026 : un seul module `organization`
(`organization-api` / `organization-impl`), avec Affectation/Mandat/Delegation dans
un sous-package `.affiliation`, et Politique organisationnelle/Derogation dans un
sous-package `.politique`. La granularite de deploiement plus fine (mettre a jour
Affectation sans redeployer tout Organization) est abandonnee au profit de la
simplicite du graphe de dependances — c'etait le compromis assume dans la
proposition initiale.

**C.2 — Sur la categorie de Reference-data**
**Actee** : `business`, pas `system` — ce n'est pas un fournisseur d'infrastructure
interchangeable (a la difference de Keycloak/SpiceDB), c'est du contenu metier
fourni en usine.

---

# PARTIE D — Tutelle : intra-instance, decision finale

L'ancienne tension (§ B.6 : le CHU rattache a deux Organisations, avec SpiceDB
arbitrant les droits « sans que les deux Organisations ne se voient mutuellement »)
supposait implicitement une base ou un SpiceDB partages entre Organisations — une
hypothese rendue fausse par le Point 1 (mono-tenant strict par instance).

**Decision actee le 22 juillet 2026, qui clot definitivement cette tension** : la
Tutelle est et reste **strictement intra-instance**. Une instance EGEN deployee
represente **une seule entreprise** ; la Tutelle sert uniquement a hierarchiser et
regrouper des Organisations **au sein de cette meme instance** (typiquement : une
maison-mere et ses filiales, ou un ministere et les etablissements sous sa tutelle,
tant qu'ils partagent le meme deploiement). Aucune federation inter-instances n'est
prevue ni necessaire — le mono-tenant par instance reste total, "chacun avec son
environnement".

Des trois options envisagees dans une version anterieure de ce document (Tutelle
restreinte a l'intra-instance / federation inter-instances dediee / zone mutualisee
limitee), c'est la **premiere** qui est retenue, sans reserve ni scenario de
contournement a batir : le cas d'un CHU sous tutelle de deux Organisations
veritablement heberges sur deux instances EGEN distinctes sort du modele de donnees
EGEN et se traiterait, si le besoin se presentait un jour, par une integration API
ponctuelle hors plateforme — pas par une extension du modele de Tutelle lui-meme.

Consequence concrete sur le code : aucune refonte du modele de donnees Tutelle
n'etait necessaire pour appliquer cette decision — `Tutelle.organisationId` et
`Tutelle.celluleRacineId` ont toujours ete des references locales a l'instance. Seuls
le deplacement vers `egen-modules/business/organization` et la clarification
documentaire (voir la migration `V1__init_organization.sql`) etaient requis.

---

# PARTIE E — Points ouverts restants (etat au 28 juillet 2026, apres refactoring, conception du primitif Niveau 1, livraison de module-registry, de kernel-plugin-engine, de kernel-eventbus, de kernel-bootstrap et de kernel-test-support)

1. ~~Contenu exact du primitif Niveau 1~~ (Identity + Authorization + Policy-noyau,
   § A.5) — **une premiere proposition concrete est implementee et poussee sur
   `main`** (KernelSubject, KernelCapability/KernelPermissionCheck,
   PolitiqueNoyau), **et desormais eprouvee de bout en bout**. `module-registry`
   consomme `PolitiqueNoyau` sans friction (§A.6bis) ; `kernel-plugin-engine`
   consulte a la fois `KernelPermissionCheck` et `ModuleActivationResolver` avant
   tout chargement (§A.6ter) ; `kernel-bootstrap` (§A.6quinquies) assemble tout ce
   qui precede et le verifie ensemble, avec de vraies donnees en base
   (Testcontainers), dans `KernelBootSequenceTest`. Ce point n'est plus ouvert au
   sens ou aucun consommateur restant ne pourrait encore reveler un besoin non
   anticipe — il reste neanmoins une proposition, pas un dogme : toute evolution
   future restera documentee et datee, comme le reste de cette Charte.
2. ~~Fusion ou separation Organization/Affiliation~~ — **tranche et realise** :
   fusion en un seul module.
3. ~~Categorie `system` vs `business` pour Reference-data~~ — **tranche et
   realise** : `business`.
4. ~~Resolution de la tension Tutelle inter-organisation~~ — **tranche
   definitivement** : intra-instance uniquement, aucune federation (Partie D).


# PARTIE F — Le Modèle Réseau : philosophie, gouvernance et articulation avec le Kernel

> Suite logique de la Charte v3 (Parties A à E). Ce document résout une tension nouvelle,
> distincte de celle déjà tranchée en Partie D, et pose le paradigme complet du Réseau —
> strictement métier, sans détail d'implémentation.

## F.0 — Résoudre la tension avec la Partie D avant toute chose

La Partie D a tranché que la **Tutelle** reste strictement intra-instance : aucune fédération
inter-instances n'est prévue, et un cas de rattachement multi-organisations réel se traiterait
« par une intégration API ponctuelle hors plateforme — pas par une extension du modèle de
Tutelle lui-même ».

Le Réseau n'est **pas** une extension du modèle de Tutelle. C'est cette « intégration hors
plateforme » que la Partie D avait anticipée — mais formalisée, généralisée, et faite citoyenne
de première classe de la plateforme plutôt que traitée au cas par cas. Il faut donc bien
distinguer deux mécanismes qui ne se ressemblent qu'en apparence :

| | Tutelle | Réseau |
|---|---|---|
| Portée | Intra-instance (une seule Organisation, ses filiales/établissements) | Inter-instance (plusieurs Organisations, potentiellement sur des déploiements distincts) |
| Nature du lien | Hiérarchique (rattachement, subordination) | Horizontal (fédération entre pairs souverains) |
| Ce qui est partagé | Structure organisationnelle interne | Ressources explicitement publiées |
| Moteur d'autorisation | Un seul, unifié, pour toute l'instance | Un par Organisation, jamais fusionné |

Une Organisation reste donc, comme la Partie A.3 (Point 1) l'a acté, **un déploiement complet
et autonome**. Le Réseau n'y change rien : il ne fusionne jamais deux Organisations, il les
met en relation depuis l'extérieur.

*Ne pas confondre avec la Succession Organisationnelle (§B.2), un tout autre concept du
Lexique : celle-ci trace le lien entre une Cellule disparue et celle(s) qui lui succède dans
le temps (une réorganisation interne), alors que la Tutelle est une relation d'autorité entre
entités qui existent simultanément.*

## F.1 — Le paradigme repris tel quel : l'Organisation est un réseau, le Réseau est le tissu qui les relie

Le principe directeur de cette partie : **ne rien réinventer**. Le vocabulaire des réseaux
informatiques décrit déjà exactement ce dont on a besoin.

| Concept EGEN | Équivalent réseau informatique |
|---|---|
| Organisation | Réseau local privé (LAN), système autonome |
| Cellule | Segment / sous-réseau interne |
| Ressource Locale / Souveraine | Service hébergé en interne |
| **Passerelle** (nouveau concept, voir F.3) | Routeur de bordure / pare-feu |
| **Réseau** (le nouveau système) | Tissu d'interconnexion (WAN, extranet, peering) — jamais un LAN de plus |
| Publication d'une ressource | Exposition d'un service via la Passerelle |
| Consommation | Requête sortante, toujours résolue chez l'Organisation propriétaire |
| Ressource héritée | Route apprise, intégrée à la table locale |
| Identifiant composé (F.5) | Adresse hiérarchique, à la manière d'un nom pleinement qualifié |
| Révocation en cascade (F.9) | Retrait de route, propagé jusqu'aux sessions dépendantes |
| Capacité Réseau (F.7) | Habilitation d'exploitation, adossée à un abonnement |

Une seule règle gouverne tout le reste : **rien ne traverse la frontière d'une Organisation
sans passer explicitement par sa Passerelle**. Tout le paradigme découle de cette phrase.

## F.2 — L'Organisation : un système autonome souverain, complet en lui-même

Rien ne change au modèle déjà établi (Charte v3, Partie B) : Organisation racine
souveraine, Cellules récursives, Personnes affectées via Affectation/Mandat/Delegation,
Ressources Locales et Souveraines, Politique organisationnelle avec Dérogation en cascade.
Ce modèle reste entièrement intact — c'est précisément parce qu'il est déjà complet et
cohérent en vase clos qu'il peut devenir un nœud d'un réseau plus large sans être modifié.

Une seule nuance à ajouter, qui préexistait implicitement mais qu'il faut maintenant rendre
explicite : **seule l'Organisation publie, jamais une Cellule directement.** Que la ressource
candidate à la publication soit une Ressource Locale (rattachée à une Cellule précise) ou une
Ressource Souveraine (rattachée directement à l'Organisation), l'acte de publication est
toujours un acte organisationnel — exercé via la Passerelle, jamais initié par la Cellule
elle-même. C'est la continuité directe du principe déjà posé : « l'organisation reste
toujours souveraine ».

À l'intérieur de la frontière organisationnelle, rien n'est visible depuis l'extérieur par
défaut — exactement comme un réseau privé n'expose rien tant qu'aucune règle de routage ne
le décide explicitement.

## F.3 — La Passerelle : le seul point de passage légitime

C'est la pièce manquante qu'il faut introduire formellement. La Passerelle est le point de
bordure unique d'une Organisation — l'équivalent fonctionnel du routeur de périphérie d'un
réseau local. Aucune ressource ne sort, aucune ressource n'entre, sans passer par elle.

Ses responsabilités, et rien d'autre :

- **Vers l'extérieur** — exposer les ressources que l'Organisation a explicitement choisi de
  publier, jamais plus ; appliquer les Capacités Réseau (F.7) avant d'autoriser toute
  publication.
- **Vers l'intérieur** — recevoir les ressources consommées depuis un Réseau, les convertir
  en ressources héritées, et les remettre à la Politique de redistribution interne (F.10)
  avant qu'aucune Cellule n'en ait connaissance.
- **Traçabilité** — porter à elle seule la responsabilité de la chaîne de provenance (F.11) :
  c'est le point unique où l'identifiant composé d'une ressource distante est résolu et
  conservé.
- **Ce qu'elle ne fait jamais** — elle n'exécute rien, ne stocke aucune donnée métier, et ne
  laisse jamais une Cellule dialoguer directement avec un Réseau. Une Cellule ne sait même
  pas qu'un Réseau existe ; elle ne voit que des ressources, locales ou héritées, déjà
  unifiées par le moteur d'autorisation interne — exactement le principe déjà posé dans la
  philosophie d'origine.

## F.4 — Le Réseau : tissu d'interconnexion, jamais un troisième silo

Le Réseau reste fidèle à la philosophie initiale, avec une précision supplémentaire rendue
nécessaire par le modèle mono-tenant : **un Réseau relie des Organisations qui peuvent être
sur des déploiements entièrement distincts.** Ce n'est donc jamais un module qui vivrait « à
l'intérieur » d'une instance parmi d'autres — c'est un système à part, positionné entre les
Passerelles de plusieurs Organisations.

Ce qu'un Réseau possède réellement, et rien de plus :

- un **registre des membres** (quelles Organisations en font partie, avec quel rôle) ;
- un **annuaire des publications** (quelles ressources ont été publiées, par qui, sous quel
  identifiant composé) ;
- des **règles de fonctionnement du réseau lui-même** (qui peut y publier, qui peut
  administrer les adhésions, la republication y est-elle autorisée par défaut — voir F.8).

Ce qu'un Réseau ne possède jamais :

- aucune donnée métier — consommer une ressource se traduit toujours par un aller-retour vers
  la Passerelle de l'Organisation propriétaire, jamais par une lecture dans le Réseau
  lui-même ;
- aucun utilisateur, aucune Cellule, aucun moteur d'autorisation propre — l'autorisation reste
  entièrement du ressort de chaque Organisation, à chaque bout de la chaîne.


## F.5 — L'adressage : l'identifiant composé comme clé de voûte (répond au point 4)

Toute ressource circulant dans un Réseau porte une adresse à trois niveaux, jamais une
seule :

```text
Réseau  ›  Organisation source  ›  Ressource
```

Par exemple :

```text
Réseau-Ministère › Université-Libreville › Catalogue-Formations
```

Cette adresse composée n'est pas un identifiant technique parmi d'autres : c'est la **clé de
voûte de tout le modèle de traçabilité**. Elle rend la provenance intrinsèque à l'adresse
elle-même plutôt que dépendante d'une métadonnée séparée qu'on pourrait perdre ou désynchroniser.
Elle évite aussi tout conflit de nommage : deux Réseaux différents peuvent chacun exposer un
« Annuaire » sans jamais se percuter, puisque l'adresse complète les distingue par
construction — exactement comme un nom hiérarchique évite les collisions dans un espace de
noms partagé.

Chaque ressource héritée, une fois entrée chez l'Organisation réceptrice, conserve cette
adresse complète comme étiquette d'origine — invisible pour les Cellules, mais toujours
disponible pour la Passerelle et pour l'audit.

## F.6 — Le cycle de vie complet d'une ressource en réseau

En reprenant la chaîne déjà posée dans la philosophie initiale, enrichie de la Passerelle,
des Capacités et de l'adressage :

1. **Souveraineté par défaut** — la ressource naît chez son Organisation, invisible de
   l'extérieur.
2. **Vérification de Capacité** — l'Organisation doit détenir la Capacité Réseau adéquate
   (F.7) avant que sa Passerelle n'accepte d'agir.
3. **Publication volontaire, ressource par ressource** — jamais un lot, jamais une catégorie
   entière ; un acte, une ressource.
4. **Adressage** — la ressource publiée reçoit son identifiant composé (F.5) dans le registre
   du Réseau.
5. **Consommation conditionnée** — une autre Organisation, via sa propre Passerelle, consomme
   la ressource si elle est publiée, si elle dispose des autorisations nécessaires, et dans le
   respect des politiques du propriétaire.
6. **Héritage** — la ressource entre, côté Organisation réceptrice, dans le même moteur
   d'autorisation que ses ressources locales — aucune différence de traitement, seule
   l'étiquette d'origine change.
7. **Redistribution interne, ressource par ressource** (F.10) — l'Organisation réceptrice
   décide, ressource héritée par ressource héritée, quelles Cellules puis quels utilisateurs y
   accèdent.
8. **Révocation en cascade** (F.9) — à tout moment, si la ressource est retirée à la source,
   tout ce qui en dépendait, jusqu'à l'utilisateur final, perd l'accès.

## F.7 — Les Capacités Réseau, adossées à l'abonnement (répond au point 1)

Ceci répond aussi à une question restée ouverte précédemment sur la nature du grade
« Organisation Fédératrice » : ce n'est ni un simple indicateur binaire ni une table de rôles
ad hoc — c'est une **Capacité Réseau** parmi un ensemble fermé d'autres, gérée par un mécanisme
d'octroi explicite et adossée à la Souscription commerciale de l'Organisation. Même
philosophie que celle déjà posée ailleurs dans le Kernel : refus par défaut, seule une
habilitation active autorise l'action.

Ensemble fermé proposé :

| Capacité Réseau | Autorise |
|---|---|
| **Fédératrice** | Créer un nouveau Réseau et en devenir l'Organisation administratrice |
| **Membre** | Rejoindre un Réseau existant (sous réserve d'acceptation, voir F.12) |
| **Publication** | Publier une ressource dans un Réseau dont l'Organisation est membre |
| **Consommation** | Consommer une ressource publiée par une autre Organisation membre |
| **Administration** | Gérer un Réseau qu'on a fondé : accepter/exclure des membres, dissoudre |

**Point de vigilance terminologique**, dans le même esprit que celui déjà posé pour la
Politique (Politique-noyau vs Politique organisationnelle) : cette Capacité Réseau n'a rien
à voir avec la Capacité-noyau existante (Niveau 1, ensemble fermé des quatre capacités
techniques gouvernant le chargement de modules). Les deux portent le même mot dans le langage
courant ; ce ne sont pas le même système, et il faudra les nommer distinctement le jour de la
conception détaillée, exactement comme cela a déjà été fait pour Policy.

**Distinction importante à ne pas confondre** : la Capacité Réseau est un plafond commercial —
ce que l'abonnement de l'Organisation autorise en général. Le droit de republication (F.8),
lui, n'est jamais accordé par un abonnement : c'est une licence accordée ressource par
ressource, par le propriétaire d'origine, à une Organisation précise. Une Organisation peut
très bien détenir la Capacité Consommation sans jamais recevoir le moindre droit de
republication — ce sont deux niveaux de gouvernance différents, l'un vertical (plateforme →
Organisation), l'autre horizontal (Organisation → Organisation).

## F.8 — La republication : une licence explicite, jamais un héritage automatique (répond au point 2)

Par défaut, une ressource héritée ne peut jamais être republiée par l'Organisation qui l'a
reçue. C'est le principe de neutralité par défaut : sans autorisation explicite du
propriétaire d'origine, une ressource consommée s'arrête là où elle a été consommée.

Le propriétaire d'origine peut accorder un **droit de republication**, comparable à une
licence :

- il est toujours accordé pour une ressource précise, jamais globalement ;
- il peut être révoqué à tout moment, indépendamment du droit de consommation initial ;
- son exercice ne rompt jamais la chaîne de provenance : si l'Organisation B republie dans un
  second Réseau une ressource reçue de l'Organisation A, l'adresse complète conservée reste
  celle d'origine (Réseau A › Organisation A › Ressource), jamais réécrite comme si B en était
  la source.

Ce mécanisme protège exactement ce que la philosophie initiale visait : le propriétaire garde
la visibilité sur qui consomme réellement sa ressource, même après plusieurs sauts.

## F.9 — Révocation en cascade, jusqu'à l'utilisateur final (répond au point 3)

Décision actée sans réserve : la perte d'accès à une ressource se propage intégralement,
jamais partiellement. Le déclencheur peut être :

- le retrait de publication par l'Organisation propriétaire ;
- le départ (ou l'exclusion) d'une Organisation du Réseau ;
- la révocation d'un droit de republication.

Dans tous les cas, la chaîne de propagation est la même et ne s'arrête jamais en cours de
route : registre du Réseau → Passerelle de chaque Organisation consommatrice → moteur
d'autorisation interne (la ressource héritée disparaît) → politiques de redistribution
appliquées aux Cellules concernées → accès effectif de chaque utilisateur. Aucune étape
intermédiaire n'est un point d'arrêt légitime — c'est la même posture fail-closed déjà retenue
ailleurs dans le Kernel : l'absence de droit actif signifie absence d'accès, immédiatement,
sans période de grâce implicite.

## F.10 — La politique de redistribution interne, ressource par ressource (répond au point 5)

Décision actée : la granularité est **ressource par ressource**, jamais par catégorie ni par
Réseau source — plus sûre, plus explicite, même si plus lourde à administrer à grande échelle.

Rien de nouveau à inventer ici : ce choix se branche directement sur le mécanisme de Politique
organisationnelle et Dérogation déjà établi dans le modèle organisationnel. Une ressource
héritée, une fois entrée chez l'Organisation réceptrice, devient simplement un nouvel objet
gouverné par ce même moteur — au même titre qu'une politique de mot de passe l'est aujourd'hui.
L'Organisation fixe la visibilité par défaut d'une ressource héritée donnée ; une Cellule peut,
si elle en a le droit, y déroger — exactement le même principe d'autonomie en cascade déjà
posé pour toute autre politique organisationnelle.

## F.11 — Traçabilité de bout en bout (répond au point 6)

Chaque accès effectif à une ressource, à n'importe quel niveau, doit pouvoir remonter
intégralement sa chaîne de provenance :

```text
Organisation propriétaire
   → Réseau (identifiant composé)
      → Organisation réceptrice
         → droit de republication éventuel
            → politique de redistribution appliquée
               → Cellule autorisée
                  → utilisateur final
```

Là encore, rien de nouveau à concevoir : c'est la même discipline que le Socle de Traçabilité
déjà en place partout ailleurs dans le Kernel (rien n'est jamais écrasé, tout est journalisé) —
simplement étendue à un objet de plus, la ressource réseau. Un administrateur doit pouvoir
répondre à « pourquoi cet utilisateur voit cette ressource ? » en remontant cette chaîne sans
jamais avoir à la reconstituer à la main.

## F.12 — Gouvernance d'un Réseau : qui décide quoi

Deux rôles distincts au sein d'un Réseau, jamais confondus :

- **Organisation Fédératrice** — celle qui a créé le Réseau (Capacité Fédératrice) et qui, par
  défaut, en détient l'Administration : elle accepte ou refuse les adhésions, elle peut
  dissoudre le Réseau.
- **Organisation Membre** — elle a rejoint le Réseau (Capacité Membre) mais n'a aucune autorité
  sur son fonctionnement global ; elle ne gouverne que ce qu'elle publie elle-même.

L'adhésion à un Réseau est toujours un **accord à deux sens**, jamais unilatéral : l'Organisation
candidate doit vouloir rejoindre (elle en fait la demande, ou y est invitée) et l'Organisation
Fédératrice doit accepter. Aucune adhésion automatique, même si l'Organisation candidate
détient la Capacité Membre — la Capacité ouvre la possibilité, elle ne force jamais l'entrée.

## F.13 — Où cela se situe dans l'architecture, sans entrer dans le détail technique

Deux natures d'objets, à ne pas mélanger :

- **La Passerelle** est un concept qui vit à l'intérieur d'une Organisation, au même niveau que
  Ressource dans la classification déjà établie (Charte v3, Partie A) : dépendant d'un concept
  intrinsèquement organisationnel, donc du même niveau que le reste du modèle organisationnel.
  Chaque instance déployée porte la sienne.
- **Le Réseau** lui-même — le registre, l'adressage, les règles de fonctionnement — ne vit à
  l'intérieur d'aucune instance en particulier. Il se situe entre les Passerelles de plusieurs
  Organisations, potentiellement déployées séparément, exactement comme le mécanisme déjà
  décrit en Partie D pour tout besoin réellement inter-instances : une intégration explicite,
  pas une extension du modèle interne d'une seule instance.

*Voir aussi F.4.1 pour la distinction entre Administration (portée par l'Organisation
Fédératrice, un rôle métier) et Opération (portée exclusivement par la plateforme EGEN,
jamais par une Organisation, quel que soit son abonnement).*

## F.14 — Schéma de synthèse

```text
Organisation A (système autonome complet)          Organisation B (système autonome complet)
├── Cellules, Ressources, Politiques                ├── Cellules, Ressources, Politiques
└── Passerelle A ──────────┐          ┌────────── Passerelle B
                            │          │
                            ▼          ▼
                        ┌─────────────────┐
                        │      RÉSEAU      │
                        │  (registre +      │
                        │   adressage +     │
                        │   règles)         │
                        │  — aucune donnée  │
                        │    métier —       │
                        └─────────────────┘
                            ▲          ▲
                            │          │
                    Passerelle C ──────┘
Organisation C (système autonome complet)
├── Cellules, Ressources, Politiques
```

## F.15 — Lexique Réseau (extension du B.2)

| Terme | Définition |
|---|---|
| **Passerelle** | Le point de bordure unique d'une Organisation ; seul canal légitime vers un Réseau. |
| **Réseau** | Le tissu d'interconnexion entre Organisations membres ; ne possède ni ne stocke de données métier. |
| **Organisation Fédératrice** | L'Organisation qui a créé un Réseau et en détient l'Administration par défaut. |
| **Organisation Membre** | Une Organisation qui a rejoint un Réseau existant. |
| **Capacité Réseau** | Habilitation d'exploitation réseau (Fédératrice, Membre, Publication, Consommation, Administration), adossée à la Souscription. |
| **Identifiant composé** | Adresse hiérarchique à trois niveaux (Réseau › Organisation source › Ressource) garantissant l'unicité et la provenance. |
| **Droit de republication** | Licence explicite, accordée ressource par ressource par le propriétaire d'origine, autorisant une republication en aval. |
| **Ressource héritée** | Une ressource consommée depuis un Réseau, unifiée dans le moteur d'autorisation local de l'Organisation réceptrice. |
| **Opérateur du Réseau** | La plateforme EGEN elle-même — héberge et exploite le Réseau. Jamais une Organisation, jamais une Capacité Réseau parmi celles de F.7. |
| **Continuité d'Administration** | Le principe selon lequel un Réseau ne reste jamais sans Administration : transfert obligatoire vers une Organisation successrice déjà Fédératrice, ou dissolution explicite. |
| **Priorité de Réseau** | Le principe selon lequel une règle imposée par le Réseau prévaut toujours sur une politique organisationnelle locale contradictoire. |

## F.4.1 — Qui opère le Réseau *(répond au point ouvert 1, tranché le 26 juillet 2026)*

Décision actée : un Réseau n'est jamais hébergé ni exploité par l'une des Organisations qui le composent — pas même par l'Organisation Fédératrice. C'est un **service neutre, fourni et opéré directement par la plateforme EGEN (CIVITAS Africa)**, exactement comme le Catalogue de modules (§B.11) est un service de la plateforme et non la propriété d'une Organisation cliente.

Cela impose une distinction à garder nette, dans le même esprit que la vigilance déjà exercée pour Policy (Politique-noyau / Politique organisationnelle) :

- **Administration du Réseau** — un rôle métier, porté par l'Organisation Fédératrice : accepter ou refuser des membres, dissoudre, transférer (voir F.12.1). C'est un rôle de gouvernance, jamais de propriété technique.
- **Opérateur du Réseau** — la plateforme EGEN elle-même. Invariable, jamais transférable, jamais une Capacité Réseau parmi celles du tableau F.7. Aucune Organisation, quel que soit son abonnement, ne peut devenir Opérateur — cette fonction n'est tout simplement pas de nature organisationnelle.

Cette séparation garantit qu'aucune Organisation Membre ne puisse jamais retenir en otage l'infrastructure partagée du Réseau : quoi qu'il arrive à l'Administration (transfert, vacance temporaire à régler — voir F.12.1), le Réseau continue de fonctionner, parce que son existence technique ne dépend d'aucune des Organisations qu'il relie.

---

## F.9.1 — Portée de la cascade : tous les sauts, tous les Réseaux *(répond au point ouvert 3, tranché le 26 juillet 2026)*

Décision actée sans réserve : la révocation en cascade ne s'arrête jamais à la première frontière organisationnelle ni au premier Réseau traversé. Que la ressource ait été republiée une fois ou dix, dans un seul Réseau ou successivement dans plusieurs Réseaux distincts (via des droits de republication accordés en chaîne, F.8), le retrait à la source se propage **intégralement jusqu'au dernier utilisateur final concerné**, quel que soit le nombre de sauts.

Concrètement, la chaîne de propagation déjà décrite en F.9 doit être rejouée récursivement à chaque saut de republication : si une ressource republiée par l'Organisation B (via un droit accordé par l'Organisation A) est elle-même consommée par l'Organisation D dans un second Réseau, la révocation initiale par A déclenche la même cascade complète chez B *et* chez D, sans exception ni raccourci. Aucune republication, aussi lointaine soit-elle dans la chaîne, ne crée d'îlot d'accès protégé de la révocation d'origine.

---

## F.10.1 — Priorité entre règle de Réseau et politique organisationnelle *(répond au point ouvert 4, tranché le 26 juillet 2026)*

Décision actée : en cas de conflit entre une règle imposée par le Réseau lui-même (F.4 — par exemple, republication interdite par défaut) et une politique organisationnelle locale qui la contredirait, **la règle du Réseau l'emporte toujours**.

C'est une inversion assumée du principe « le plus proche l'emporte » qui régit par défaut la Politique organisationnelle en interne (Charte v3, §B.12) — et ce n'est pas une incohérence : ce principe de proximité arbitre entre Paliers d'une même hiérarchie souveraine (Organisation → Cellule → sous-Cellule), où chaque Palier appartient à la même autorité. La relation entre un Réseau et une Organisation Membre n'est pas hiérarchique de cette façon — c'est un accord d'adhésion entre pairs (F.12), où le Réseau fixe le cadre commun que chaque membre a accepté en le rejoignant.

Le partage de compétence reste donc strictement borné :

- **Ce que le Réseau gouverne** — les règles qui touchent la circulation de la ressource *entre* Organisations : republication autorisée ou non par défaut, conditions d'adhésion, règles de fonctionnement communes. Aucune politique organisationnelle locale ne peut assouplir une de ces règles.
- **Ce que l'Organisation réceptrice gouverne seule** — tout ce qui se passe *après* réception, à l'intérieur de sa propre frontière : la politique de redistribution interne ressource par ressource (F.10), qui reste entièrement de son ressort, et sur laquelle le Réseau n'a, à l'inverse, aucune autorité.

Le Réseau fixe le plafond de ce qui est possible entre Organisations ; l'Organisation reste seule maîtresse de ce qui est possible en dessous d'elle-même.

---

## F.12.1 — Continuité obligatoire de l'Administration *(répond au point ouvert 2, tranché le 26 juillet 2026)*

Décision actée : une Organisation Fédératrice ne peut jamais quitter un Réseau, ni renoncer à son rôle d'Administration, sans avoir **au préalable transféré cette Administration** à une autre Organisation Membre. Un Réseau sans Administration n'est jamais un état transitoire acceptable — il n'existe que deux issues légitimes, jamais une troisième :

1. **Transférer l'Administration** à une autre Organisation Membre du même Réseau — à la condition stricte que cette Organisation successrice détienne *déjà*, avant le transfert, sa propre Capacité Fédératrice (F.7). Il ne suffit donc pas d'être Membre pour hériter de l'Administration : l'aptitude à fédérer doit préexister au transfert, jamais être accordée en urgence à cette occasion. Le transfert reste un acte à deux sens, symétrique à l'adhésion (F.12) : l'ancienne Fédératrice le déclenche, la nouvelle doit l'accepter.
2. **Dissoudre explicitement le Réseau** — un acte assumé, qui déclenche la révocation en cascade complète (F.9, F.9.1) pour toutes les ressources publiées, jusqu'au dernier utilisateur final, dans chacune des Organisations Membres.

Ce que ce principe exclut formellement, c'est une troisième voie : une Organisation Fédératrice qui se retirerait simplement — perte de sa Capacité Fédératrice (changement d'abonnement), cessation d'activité, retrait unilatéral — sans avoir accompli l'une des deux issues ci-dessus. Un Réseau reste donc toujours administré par une Organisation qui en a explicitement la charge, ou il cesse formellement d'exister ; il n'est jamais laissé à l'abandon.

**Nuance non résolue par cette décision, à garder en tête** : que se passe-t-il si, au moment où l'Organisation Fédératrice souhaite se retirer, *aucune* autre Organisation Membre ne détient encore la Capacité Fédératrice ? La décision actée ferme alors la voie du transfert — il ne resterait que la dissolution. C'est cohérent avec le principe posé, mais ça mérite d'être tranché explicitement plutôt que déduit (voir F.16, point 5).

---

## F.12.2 — Absence de successeur éligible : dissolution imminente, sans délai de grâce *(répond au point ouvert 5, tranché le 26 juillet 2026)*

Décision actée : si, au moment où une Organisation Fédératrice souhaite se retirer, **aucune** autre Organisation Membre du Réseau ne détient déjà la Capacité Fédératrice, la voie du transfert (F.12.1, issue 1) est purement et simplement fermée — il ne reste que la dissolution explicite (F.12.1, issue 2), engagée **immédiatement**.

Ce qui est explicitement exclu, pour que cette décision ne laisse aucune zone grise :

- **Pas de délai de grâce** pour permettre à une Organisation Membre d'acquérir entretemps la Capacité Fédératrice (changement d'abonnement, par exemple) dans l'espoir de sauver le Réseau. La vérification de l'éligibilité d'un successeur se fait à l'instant où la Fédératrice engage son retrait — jamais après.
- **Pas d'état intermédiaire** de Réseau "en sursis" ou "sans Administration active" en attendant une hypothétique relève. C'est la continuité même du principe déjà posé en F.12.1 : un Réseau n'est jamais laissé à l'abandon, ni pour un instant.
- **Pas de rétrogradation automatique** d'une Organisation Membre ordinaire vers un rôle d'Administration temporaire, même par défaut ou par ancienneté d'adhésion. L'aptitude à fédérer doit avoir toujours préexisté au moment du retrait — jamais être improvisée pour l'occasion, exactement la même exigence que celle déjà posée pour un transfert réussi.

La dissolution ainsi engagée déclenche, sans aucune variante par rapport au cas général, la révocation en cascade complète décrite en F.9 et étendue en F.9.1 : tous sauts confondus, tous Réseaux traversés en aval par republication, jusqu'au dernier utilisateur final, dans chacune des Organisations Membres concernées.

**Conséquence de conception à noter pour la suite** : ce mécanisme crée une incitation métier directe — une Organisation Fédératrice a intérêt à s'assurer, tout au long de la vie du Réseau, qu'au moins une autre Organisation Membre maintient activement sa Capacité Fédératrice, sans quoi elle se retrouve mécaniquement prisonnière de l'Administration jusqu'à accepter la dissolution. C'est un effet de bord assumé, pas un défaut à corriger — il pousse naturellement vers des Réseaux gouvernés par plusieurs Organisations capables, jamais par une seule en position de dépendance unique.

---

## F.16 — Points ouverts *(clos le 26 juillet 2026)*

1. ~~Qui opère techniquement un Réseau ?~~ — **tranché** (§F.4.1).
2. ~~Survie d'un Réseau si l'Organisation Fédératrice disparaît ou se retire.~~ — **tranché** (§F.12.1).
3. ~~Réversibilité d'un droit de republication déjà exercé.~~ — **tranché** (§F.9.1).
4. ~~Litige de politique de publication.~~ — **tranché** (§F.10.1).
5. ~~Absence de successeur éligible au moment du retrait.~~ — **tranché** : dissolution immédiate, sans délai de grâce ni état intermédiaire (§F.12.2).

La Partie F est désormais entièrement actée, sans point ouvert restant — au même titre que la Partie D l'était déjà. Toute remise en cause future de l'un de ces points devra être traitée comme une décision d'architecture nouvelle, documentée et datée, jamais comme une modification silencieuse de ce qui précède — exactement la règle déjà posée en tête de la Charte v3.
