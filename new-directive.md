# MISSION : rectifier et réorganiser la documentation d'architecture EGEN (Kernel + Plateforme)

## 0. Contexte et rôle

Tu es l'agent en charge de la documentation d'architecture d'EGEN (CIVITAS Africa).
Tu travailles uniquement sur la documentation. Tu ne modifies aucun code.

État actuel :
- Le dépôt Civitas-EGEN-Kernel contient la documentation du Kernel dans
  docs/architecture/ (documents 01 à 21, glossaire, références, README).
- Une branche non fusionnée, docs/ecosysteme-multi-solutions, ajoute les
  documents 22 à 29 (écosystème multi-solutions : cellules, catalogue,
  identité, contrats de domaine, configuration, portail, plan par phase A à H)
  et modifie plusieurs documents existants.
- Des décisions ont été prises ou corrigées depuis la rédaction de ces
  documents. Cette mission les intègre toutes. Le détail est en partie 3.

Le propriétaire du projet (Samuel) exige :
- que la documentation soit très précise, explicite, sans terme implicite ni
  jargon non défini ;
- que chaque sujet soit documenté dans sa propre documentation : ce qui
  concerne le Kernel dans la documentation du Kernel, ce qui concerne la
  plateforme dans la documentation de la plateforme ;
- que la priorité soit donnée au Kernel et à la Plateforme. Le système
  d'agents, l'Edge Gateway et les applications métier seront traités plus tard.

## 1. Règles de travail (obligatoires)

1. Documentation seulement. Aucune modification de code, aucun test ajouté.
   Les évolutions de code (A3, A4, A5 du Kernel) restent des tâches « à faire ».
2. Écris l'état voulu, pas l'historique. Ne mentionne jamais « avant on faisait
   X » ni la réforme passée. Chaque document décrit le système tel qu'il est
   décidé aujourd'hui.
3. Ne tranche jamais une question que ce prompt classe comme « point ouvert »
   (partie 6). Consigne-la comme décision à prendre, avec les options.
4. N'invente aucun fait technique. Tout fait externe (SpiceDB, Apache Camel,
   Keycloak, etc.) doit être vérifié dans la documentation officielle du projet
   et la version consignée. Si tu ne peux pas vérifier, écris « à vérifier » et
   ne l'affirme pas.
5. Les constats sur l'état du Kernel (doc 22.9 notamment) doivent être
   vérifiés dans le code actuel du dépôt Kernel avant d'être conservés.
6. Définis chaque terme technique à sa première apparition dans un document
   (en une phrase simple). Donne un exemple concret de bout en bout dans les
   documents d'architecture : l'Université X, le Service RH, le Service
   Informatique, Radicale et Nextcloud Calendar, Marie, membre du Service RH.
7. Une seule source de vérité par information. Le statut (fait / en cours / à
   faire / reporté) de chaque phase n'existe qu'à un seul endroit par
   documentation (partie 2).
8. Le vocabulaire doit être cohérent partout (glossaire, partie 5). Évite toute
   ambiguïté de nommage : « passerelle » seul est interdit, précise toujours
   laquelle (partie 3, décision R7).
9. Style : français, phrases courtes, tableaux pour comparer, schémas texte ou
   Mermaid pour les flux. Chaque document commence par « À quoi sert ce
   document » et « Ce qu'il ne couvre pas ».
10. Respecte les conventions existantes : liens relatifs valides, numérotation
    des documents, format des tableaux, garde-fous du Kernel (docs 02).

## 2. Périmètre, priorité et organisation cible

### Priorité de cette mission
1. Documentation du Kernel.
2. Documentation de la Plateforme.
Ne réécris pas le doc 21 (système d'agents) ni les autres sujets en périphérie,
sauf les corrections minimales de cohérence listées en partie 4.

### Où vit quoi

A. Documentation du Kernel (dépôt Civitas-EGEN-Kernel, docs/architecture/)
   Elle garde les documents 01 à 21 (corrigés, partie 4). Les documents 22 à 29
   quittent ce dépôt, sauf un seul document court qui reste :
   - 22-frontiere-avec-la-plateforme.md : ce que le Kernel offre à la
     plateforme et ce qu'il ne fait jamais. Contenu : la règle de frontière,
     la répartition des responsabilités, les labels réservés, l'API de
     publication de configuration (A3), les équipes Kernel « platform » et
     « platform-operator », les limites actuelles du Kernel (22.9 vérifié), la
     règle d'architecture A4, les décisions qui touchent le Kernel (D01, D14,
     D15, D20), et des renvois vers la documentation de la plateforme.
   Le suivi de feuille de route du Kernel (doc 19) ne porte que les phases du
   Kernel, la phase A de l'écosystème, la phase P0 proposée et les points
   ouverts du Kernel.

B. Documentation de la Plateforme (espace documentaire propre, séparé du Kernel)
   Dépôt cible : par défaut Civitas-EGEN-Business (cité en docs 16 et 22 comme
   « Projet Métier »), dossier docs/plateforme/. Si ce dépôt n'existe pas ou si
   tu as un doute, ne devine pas : crée le dossier prêt à être déplacé, dis-le
   clairement dans ton rapport final, et demande confirmation à Samuel.
   Structure proposée :
   - README.md : sommaire, ordre de lecture.
   - 01-vision-et-frontiere.md : vision opérationnelle, trois étages, frontière
     Kernel / Plateforme (renvoie au doc 22 du Kernel).
   - 02-registre-des-decisions.md : décisions D01 à D22 révisées + nouvelles
     décisions de la partie 3.
   - 03-vue-d-ensemble-des-systemes.md : le bilan des systèmes retenus (R6), les
     deux schémas de flux (R9), qui répond à quelle question (R7).
   - 04-cellules.md (ancien 23).
   - 05-catalogue-instances-affectations.md (ancien 24, incluant l'opérateur).
   - 06-identite-et-droits.md (ancien 25, réécrit pour Keycloak + SpiceDB).
   - 07-contrats-de-domaine-et-passerelle.md (ancien 26, première moitié :
     contrats, passerelle de domaine générique, conformité).
   - 08-connecteurs.md (ancien 26, seconde moitié, réécrite : connecteurs en
     services séparés, base Apache Camel).
   - 09-configuration-multi-niveaux.md (ancien 27).
   - 10-portail.md (ancien 28).
   - 11-plan-par-phase.md (ancien 29, révisé) + SUIVI des phases B à H dans ce
     seul fichier (source unique de statut pour la plateforme).
   - 12-anti-patterns.md (ceux propres à la plateforme).
   - glossaire.md et references.md propres à la plateforme.
   Les liens entre les deux documentations se font par « nom du dépôt + chemin
   du document », jamais par lien relatif qui sortirait du dépôt.

## 3. Décisions retenues (à intégrer, verrouillées)

R1. Trois étages et une règle de dépendance.
  - Étage 1 : le Kernel (lance, surveille, relance les services, dit où ils
    sont, transporte les messages). Il ne connaît aucun concept métier.
  - Étage 2 : la Plateforme (organisation, catalogue, droits, passerelle,
    connecteurs, configuration, portail). Ce sont des services ordinaires
    déployés par le Kernel via des ServiceManifest.
  - Étage 3 : les applications métier (Radicale, Nextcloud, civitas-news, etc.).
  - La Plateforme appelle le Kernel par son API publique. Le Kernel n'importe
    jamais un concept de la Plateforme.

R2. Moteur de droits : SpiceDB. Il remplace OpenFGA partout (décision ferme).
  - Remplace toutes les mentions d'OpenFGA (documents 14, 21, 22, 23, 24, 25,
    26, 29, glossaire, références) par SpiceDB.
  - Réécris le modèle de droits du doc 25.4 dans le langage de schéma de
    SpiceDB. Ne recopie pas la syntaxe OpenFGA. Reprends la même sémantique :
    cellule, parent, member, manager (hérité du parent), org_admin (hérité du
    parent), chooser, configurator, instance d'application, affectation à
    audience explicite.
  - Valide le schéma avec l'outil officiel de SpiceDB et consigne la version
    utilisée. N'écris plus de référence à un transformateur de syntaxe OpenFGA.
  - Exigence de sécurité à écrire explicitement : un droit retiré (par exemple
    la sortie d'un membre d'un service) doit être effectif immédiatement, sans
    qu'un cache périmé laisse passer l'utilisateur. Documente comment le
    système l'obtient avec les options de cohérence de SpiceDB (vérifie dans la
    documentation officielle le mécanisme exact : jetons de cohérence, niveaux
    de cohérence par requête).
  - Test de charge bloquant de la phase C (doc 25.5) : à rejouer sur SpiceDB
    avec les mêmes critères de départ (arbre de profondeur 64, 100 000
    cellules, 10 000 utilisateurs, 50 000 appartenances ; p95 ≤ 50 ms, p99 ≤
    150 ms à 200 requêtes par seconde ; aucune erreur de profondeur). Ne
    présume pas la limite de profondeur de résolution de SpiceDB : lis la
    valeur dans la documentation officielle de la version retenue et consigne-la.
    Le repli pré-décidé (PostgreSQL avec ltree, relations effectives
    matérialisées) reste en place.
  - Le stockage de SpiceDB est PostgreSQL (vérifie la compatibilité de version).
  - Vérifie et consigne la licence actuelle de SpiceDB.
  - Le contrôle d'usage reste unique : seul catalog-service répond à « cet
    utilisateur peut-il utiliser cet outil dans ce contexte ? ». Il consulte
    SpiceDB seulement pour les audiences explicites et pour les droits liés à
    la hiérarchie.

R3. Une seule passerelle de domaine, générique (remplace D16 et toute notion
    de passerelle par domaine).
  - Le service s'appelle domain-gateway (un seul service pour tous les
    domaines). Supprime partout le nom domain-gateway-<domaine> et toute phrase
    « un domaine = une passerelle ».
  - Un domaine est une donnée du catalogue : identifiant, libellé, version du
    contrat, liste des capacités (obligatoires ou optionnelles), schémas des
    échanges. Créer un domaine ne demande aucun code et aucun déploiement.
  - La passerelle lit ces données au moment de l'appel. Le travail de
    contrôle est le même pour tous les domaines (les 9 étapes du doc 26.3 :
    authentifier, lire le contexte cellule et usage, check-use du catalogue,
    obtenir l'instance effective, vérifier la capacité, vérifier le droit sur la
    ressource auprès de SpiceDB, résoudre l'adresse, appeler, journaliser).
  - Deux modes, à documenter clairement :
    a) Mode sans traduction : la passerelle contrôle, puis transmet la requête
       telle quelle à l'application (ou l'application est ouverte par son
       interface, mode LINK). Aucun connecteur nécessaire.
    b) Mode uniforme : la passerelle appelle un connecteur qui traduit
       l'API canonique du domaine vers le logiciel. Utile quand on veut appeler
       tous les logiciels d'un domaine de la même façon (par exemple pour un
       agent IA ou une autre application).
  - La coexistence de plusieurs logiciels d'un même domaine peut donc démarrer
    avec le seul mode sans traduction.
  - Contrepartie à documenter : la passerelle est un passage obligé pour tous
    les domaines. Elle doit être robuste et déployable en plusieurs exemplaires.

R4. Connecteurs : services séparés, basés sur Apache Camel (décision ferme).
  - Un connecteur est un petit service déclaré au Kernel par un ServiceManifest
    comme tout autre service. La passerelle le trouve par la découverte du
    Kernel (GET /api/v1/discover/{nom}). Ajouter un connecteur ne modifie ni la
    passerelle ni le Kernel.
  - Un connecteur correspond à un protocole ou à un logiciel (jamais à un
    domaine). Un connecteur peut servir plusieurs logiciels qui parlent le même
    protocole, avec des profils (des données) pour les particularités.
  - Une nouvelle instance d'une version de logiciel déjà connectée ne demande
    aucun code, seulement de la configuration.
  - Interface commune que tout connecteur expose (reprends le doc 26.4) :
    décrire ses capacités, vérifier sa santé, créer et supprimer un compte local
    si nécessaire, une opération par capacité du contrat, traduire les erreurs
    vers les codes normalisés. Écris ce contrat normatif (méthodes, entrées,
    sorties, codes d'erreur). Il doit être indépendant du langage.
  - Base technique : Apache Camel (Java, licence Apache 2.0) avec Quarkus.
    Camel est une bibliothèque, pas un serveur à déployer. Chaque connecteur
    inclut uniquement les composants Camel dont il a besoin, déclarés comme
    dépendances. On ne copie pas le code source de Camel, sauf pour modifier
    un composant en profondeur ; dans ce cas, conserver l'en-tête de licence et
    le fichier de notices. Prévoir un gabarit de connecteur commun pour ne pas
    réécrire la structure à chaque fois.
  - Camel ne fournit pas de composant CalDAV officiel (aucun trouvé lors de
    l'analyse). Le premier connecteur d'agenda sera donc écrit en partie
    (appels HTTP et lecture/écriture du format iCalendar). Avant d'engager
    cette écriture, évaluer les bibliothèques Java CalDAV/iCalendar existantes
    (licence permissive, maintenance active). Consigne ce point comme point
    ouvert (partie 6).
  - Règle de licences : n'utiliser que du code sous licence permissive
    (Apache 2.0, MIT, BSD ou équivalent). Écarter, pour cet usage, n8n (licence
    à usage restreint), Airbyte (la plupart de ses connecteurs actuels sont sous
    Elastic License 2.0 qui interdit l'offre en service managé) et Pipedream
    (non auto-hébergeable, registre « source-available »). Vérifie la licence de
    chaque composant avant réutilisation.
  - Camel apporte des composants pour des protocoles répandus (HTTP, mail,
    LDAP, base de données, fichiers). Ne chiffre pas le nombre de composants
    dans la documentation d'architecture.

R5. Les connecteurs ne sont jamais dans le Kernel.
  - Documente la distinction : les adapters du Kernel parlent à des outils
    d'infrastructure (Nomad, Consul, NATS, Vault), ils sont peu nombreux et
    changent rarement. Les connecteurs parlent à des applications métier, sont
    nombreux et changent souvent.
  - Raisons à écrire : (1) garde-fou n°5 : ajouter un logiciel ne doit pas
    obliger à modifier le Kernel ; (2) le Kernel est le seul point qui maintient
    tout en vie et il tourne en un exemplaire, un défaut dans un connecteur ne
    doit pas l'arrêter ; (3) les secrets des applications ne doivent pas
    cohabiter avec les clés du Kernel ; (4) la règle d'architecture A4 refuse
    tout concept métier dans le Kernel ; (5) poids et conflits de dépendances ;
    (6) rythmes de vie et mise à l'échelle différents.
  - Ajoute « connecteur dans le Kernel » aux anti-patterns du Kernel.

R6. Les systèmes retenus, regroupés (applications métier exclues).
  Groupe 1 — Le Kernel : EGEN Kernel (codé, existant).
  Groupe 2 — Outils open source qui font tourner le Kernel (installés, non codés) :
    Nomad, Consul, NATS JetStream, Vault, PostgreSQL (un seul serveur peut
    héberger une base par service), observabilité (collecteur OpenTelemetry,
    Jaeger, Prometheus, Grafana).
  Groupe 3 — Outils open source de porte d'entrée et d'identité (installés,
    non codés) : Kong (entrée publique), Keycloak (identité unique), SpiceDB
    (droits).
  Groupe 4 — Services de la Plateforme (codés) : cell-registry-service,
    catalog-service, platform-operator-service, config-service, domain-gateway
    (générique), portail, connecteurs (Camel).
  Totaux à écrire : 12 programmes à installer (5 dans le groupe 2 hors
  observabilité, 4 outils d'observabilité, 3 dans le groupe 3) ; 8 systèmes à
  coder au minimum (le Kernel déjà fait, 6 services fixes, 1 connecteur). Le
  nombre de connecteurs grandit avec les logiciels au protocole nouveau.
  Hors bilan, chantier séparé et optionnel pour la coexistence : système
  d'agents (Agent Gateway, Agent Runtime, Capability Gateway), qui réutilise
  Keycloak et SpiceDB.
  Ne chiffre pas les applications métier.

R7. Trois « passerelles » à ne jamais confondre, et qui répond à quelle question.
  - Kong : porte d'entrée publique. Vérifie que le jeton est bien formé, limite
    le débit, route. Ne décide d'aucun droit métier.
  - Passerelle de domaine (domain-gateway) : orchestre l'appel vers
    l'application (contrôle, puis appel direct ou via connecteur).
  - Capability Gateway (système d'agents) : fournit les outils aux agents IA.
  Table « question → pièce qui répond » :
  - Qui es-tu ? → Keycloak (jeton avec egen_uid).
  - Le jeton est-il bien formé ? → Kong.
  - Quelle est ta place dans l'organigramme ? → registre de cellules (et SpiceDB
    pour l'héritage).
  - Quel outil est affecté à cette cellule, et as-tu le droit de l'utiliser ?
    → catalogue (check-use), seule implémentation de cette question.
  - As-tu le droit sur cette ressource précise ? → SpiceDB, appelé par la
    passerelle.
  - Où tourne l'instance ? → Kernel (découverte).
  - Les applications ne décident jamais d'un droit EGEN.

R8. Règles d'interaction (à écrire comme règles normatives).
  1. Seul platform-operator-service parle au Kernel pour installer ou gérer des
     instances. Le catalogue, le portail, la passerelle et les connecteurs n'ont
     aucun jeton de gestion du Kernel.
  2. La passerelle interroge le Kernel seulement pour trouver l'adresse d'une
     instance (découverte).
  3. Le catalogue est le seul à décider « peut-il utiliser cet outil ».
  4. Les données d'une application restent dans cette application. Aucune
     synchronisation implicite entre instances (règle D18 conservée).

R9. Deux flux à documenter, avec un schéma pour chacun, dans le doc « vue
    d'ensemble des systèmes » de la Plateforme, et résumés dans le doc 03 du
    Kernel.
  Flux A — Un appel utilisateur (moment d'usage) :
    Utilisateur ou portail → Kong → domain-gateway → [catalogue (outil +
    droit d'usage) ; SpiceDB (droit sur la ressource) ; Kernel (où est
    l'instance)] → application, directement (mode sans traduction) ou via un
    connecteur (mode uniforme). Keycloak émet le jeton en amont ; Kong le vérifie.
  Flux B — L'installation d'une application (moment de mise en service) :
    catalog-service (état désiré) → platform-operator-service → Kernel
    (déploie, surveille, relance) → application. L'opérateur relit le statut du
    Kernel et le reporte dans le catalogue.
  Précise dans le texte que ce sont deux moments séparés : une application est
  installée une fois, puis appelée de nombreuses fois.

R10. Décisions inchangées à conserver telles quelles : la cellule générique (D03
     à D08, D11), les six notions domaine / implémentation / version
     d'implémentation / instance / activation / affectation (D09), unicité et
     héritage des affectations (D10), identité egen_uid (D12), D14 (opérateur
     seul client du Kernel), D15 (instances MANAGED et EXTERNAL), D17 à D22,
     labels réservés platform.civitas.africa/*, API de publication de
     configuration (A3), règle d'architecture A4, scénario de niveau 5 (A5),
     niveaux de configuration et verrous, portail et modes d'ouverture.

## 4. Corrections document par document

Documentation du Kernel :
- 01, 02, 03 : conserver les sections « écosystème » mais les raccourcir et les
  renvoyer vers le doc 22 (frontière) et la documentation de la plateforme. Dans
  le doc 03, ajoute le schéma global trois étages et les deux flux (version
  courte).
- 05, 06, 07, 08 : conserver (labels réservés, limites de l'adapter Nomad,
  pas de filtre par label). Vérifie les constats dans le code.
- 13 : conserver la spécification de A3 (publication de configuration).
- 14 : remplacer OpenFGA par SpiceDB dans la section « utilisation par
  l'écosystème » ; conserver le RBAC grossier voulu.
- 16 : mettre à jour la section « hors de ce dépôt » : un service
  domain-gateway générique, des connecteurs en services séparés, et les noms
  définitifs des services de la plateforme.
- 17 : conserver A4 et A5 ; déplacer vers la documentation de la plateforme ce
  qui concerne les tests des services de la plateforme.
- 18 : garder dans le Kernel les anti-patterns qui le concernent (une cellule
  dans le Kernel ; le Kernel interprète un label réservé ; le catalogue appelle
  le Kernel ; connecteur dans le Kernel). Déplace les autres vers la plateforme.
  Ajoute côté plateforme : « une passerelle de domaine codée par domaine » et
  « copier le code source de Camel au lieu de déclarer la dépendance ».
- 19 : restructurer comme indiqué en partie 2 A (voir partie 6 pour la phase P0
  et les points ouverts). Les phases B à H quittent ce document et vont dans le
  suivi de la plateforme.
- 21 (corrections minimales seulement) : remplacer OpenFGA par SpiceDB ;
  remplacer toute mention de passerelles de domaine par domaine ; ne rien
  réécrire d'autre.
- 22 : remplacé par le document de frontière décrit en partie 2 A.
- Glossaire et références du Kernel : retirer OpenFGA ; ajouter SpiceDB et Apache
  Camel côté références ; ne garder dans le glossaire du Kernel que les termes qui
  concernent le Kernel et la frontière.
- README : mettre à jour le sommaire et la présentation en parties.

Documentation de la Plateforme :
- 04 (cellules, ancien 23) : remplacer la mention « projection OpenFGA » par
  SpiceDB. Le reste est conservé.
- 05 (catalogue, ancien 24) : audience EXPLICIT et tuples (assignment#can_use)
  réécrits pour SpiceDB ; préciser que les domaines sont des données ; préciser
  que le Kernel ne gère pas les instances EXTERNAL en V1 : les projets déjà
  exploités hors du Kernel (par exemple civitas-news, annuaire, TEMA) sont des
  instances EXTERNAL tant qu'ils ne sont pas conteneurisés avec une image
  publiée ; ils deviennent MANAGED après conteneurisation. Documente cette
  conséquence sans décider quels projets migrent (point ouvert).
- 06 (identité et droits, ancien 25) : réécriture complète du modèle pour
  SpiceDB (voir R2). Conserver Keycloak, egen_uid, niveaux d'autorisation,
  comptes locaux liés, échange de jeton, audit.
- 07 (contrats de domaine et passerelle) : réécrire la gateway en passerelle
  unique générique (R3). Les 9 étapes de contrôle sont conservées et valent pour
  tous les domaines. Retirer la notion de gateway par domaine. Garder le contrat
  de domaine (API canonique, capacités M/O, conformité FULL/PARTIAL, suite de
  tests de contrat). Garder « aucune synchronisation implicite » et les règles
  d'échange.
- 08 (connecteurs) : nouveau document, voir R4 et R5.
- 09 (configuration), 10 (portail) : conserver, avec renvois mis à jour.
- 11 (plan par phase) :
  - Phase C : Keycloak + SpiceDB (au lieu d'OpenFGA) ; test de charge rejoué sur
    SpiceDB.
  - Phase E : remplacer « implémenter domain-gateway-agenda » par : (a) définir
    le domaine agenda comme donnée du catalogue ; (b) implémenter la passerelle
    de domaine générique ; (c) écrire le connecteur CalDAV (service séparé,
    base Camel) avec deux profils, Radicale et Nextcloud Calendar. Ajouter un
    test d'acceptation : créer un second domaine, par données seulement, et
    l'appeler par la passerelle en mode sans traduction, sans écrire de code ni
    redéployer la passerelle. Ajouter un test : la panne d'un connecteur
    n'affecte ni la passerelle ni les autres domaines.
  - Definition of Done : ajouter « un domaine s'ajoute sans code ».
  - Tout le suivi de statut de B à H est dans ce document, et lui seul.
- Glossaire et références de la plateforme : voir partie 5.

## 5. Glossaire et vocabulaire

Ajouter ou corriger dans les glossaires :
- Passerelle de domaine générique (domain-gateway) : service unique qui contrôle
  puis appelle l'application, pour tous les domaines.
- Domaine (donnée du catalogue) : besoin fonctionnel défini par un contrat ; n'est
  pas du code.
- Mode sans traduction / mode uniforme.
- Connecteur : service séparé qui traduit le contrat d'un domaine vers un
  logiciel ou un protocole.
- Profil de connecteur : données décrivant les particularités d'un logiciel.
- Moteur de droits (SpiceDB).
- Kong, Keycloak, SpiceDB, Apache Camel : une phrase chacun.
- Adapter (du Kernel) vs connecteur (de la plateforme) : définition et
  distinction.
- Supprimer : Domain Gateway par domaine, OpenFGA.
- Références à ajouter : documentation officielle de SpiceDB et d'Apache Camel
  (et, côté plateforme, ce qui était déjà cité : Keycloak, ltree, JSON Schema,
  RFC 8693, SCIM, CalDAV). Retirer OpenFGA.

## 6. Points ouverts à consigner SANS les trancher

Pour chacun : énoncé du problème, options, impact, décideur (Samuel).
1. Amorçage circulaire : sans émetteur OIDC configuré, le Kernel refuse toute
   mutation, mais la phase C prévoit que le Kernel déploie Keycloak. Options :
   lancer Keycloak par la pipeline avant le Kernel ; ou fournir une clé publique
   statique temporaire pour émettre le premier jeton egen-admin. À documenter
   dans la feuille de route du Kernel et dans la phase C de la plateforme.
2. Phase P0 « production du Kernel » (proposition de Claude, à valider par
   Samuel) : aucun Dockerfile ni image publiable, pas de job Nomad du Kernel,
   Vault utilisé en mode développement, un seul exemplaire du Kernel (clé
   d'idempotence et liens de traces en mémoire de processus), mTLS désactivé par
   défaut, sidecar sans mTLS ni publication d'événements. Vérifie ces constats
   dans le code puis consigne la phase proposée.
3. Charge d'exploitation : recommandation (non décidée) d'avancer B, C et D comme
   une seule tranche avec le domaine de test « echo » avant d'engager la phase E.
4. Dépôt cible et nom de la documentation de la plateforme (partie 2 B).
5. Choix de la bibliothèque CalDAV/iCalendar pour le premier connecteur d'agenda.
6. Limite de profondeur de résolution de SpiceDB (à lire dans la documentation
   officielle, non présumée).
7. Quels projets existants deviennent des instances MANAGED (conteneurisation).
8. Annonce des capacités à la Capability Gateway : comment la passerelle unique
   expose les capacités de tous les domaines, et comment la Capability Gateway
   l'appelle. Priorité basse (système d'agents plus tard).

## 7. Contrôles de qualité avant de rendre

1. Recherche globale dans les deux documentations : OpenFGA et openfga ne
   doivent plus apparaître nulle part. Même contrôle pour « domain-gateway- »
   suivi d'un nom de domaine, et pour toute phrase décrivant une passerelle par
   domaine.
2. Tous les liens relatifs sont valides dans chaque dépôt. Les références entre
   dépôts utilisent « dépôt + chemin ».
3. Chaque information a une seule source de vérité. Le statut des phases du
   Kernel n'est que dans le doc 19 ; celui des phases B à H n'est que dans le
   suivi de la plateforme.
4. Le vocabulaire est cohérent avec le glossaire. Aucune occurrence ambiguë de
   « passerelle » sans précision.
5. Les documents du Kernel ne contiennent aucun détail de plateforme en dehors
   du document de frontière et des renvois.
6. Chaque fait externe est vérifié ou marqué « à vérifier ». Chaque constat sur
   le code est vérifié dans le code.
7. Les décisions R1 à R10 sont toutes présentes, et chaque point ouvert de la
   partie 6 est consigné sans être tranché.
8. Les deux schémas de flux (R9) et le bilan des systèmes (R6) sont présents,
   lisibles par quelqu'un qui découvre le projet, avec l'exemple concret.

## 8. Git

- Travaille sur la branche docs/ecosysteme-multi-solutions pour le dépôt du
  Kernel. Fais des commits petits et nommés par étape.
- Pousse sur cette branche. Ne fusionne pas dans main sans le feu vert de
  Samuel. Rappelle-lui sa règle habituelle (pousser sur main à la fin de chaque
  étape vérifiée) et demande-lui de confirmer la fusion.
- Pour la documentation de la plateforme, applique la même discipline dans son
  dépôt ou son dossier cible (partie 2 B).

## 9. Livrables et ordre d'exécution

Ordre :
1. Documentation du Kernel (priorité 1) : corrections des docs 01 à 21, nouveau
   doc 22 de frontière, doc 19 restructuré.
2. Documentation de la plateforme (priorité 2) : création de l'espace, migration
   et réécriture des anciens 22 à 29, nouveaux documents (vue d'ensemble des
   systèmes, connecteurs, registre des décisions).
3. Glossaires, références, README des deux documentations.
4. Contrôles de la partie 7.

Rapport final à rendre à Samuel, en français simple :
- la liste des fichiers créés, modifiés, déplacés et supprimés, par
  documentation ;
- les résultats des contrôles de la partie 7 (avec les recherches effectuées) ;
- les points ouverts consignés (partie 6) ;
- tout ce que tu n'as pas pu vérifier, clairement signalé ;
- toute ambiguïté rencontrée et la façon dont tu l'as traitée.
