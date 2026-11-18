# **EGEN Kernel — Inventaire des problématiques d'intégration, de communication et d'interopérabilité**

Je te propose de reprendre le sujet à la base, comme une véritable réflexion d'architecture.

L'objectif n'est pas encore de décider quelles interfaces, quels adaptateurs ou quels protocoles nous devons développer. Il est d'identifier **toutes les situations auxquelles EGEN Kernel pourrait être confronté dans un environnement informatique réel**, afin de déterminer ensuite s'il sait les gérer, s'il doit évoluer ou si certaines responsabilités doivent être confiées à des composants externes.

Je vais volontairement poser des questions très simples, parfois évidentes, puis aller vers des problématiques plus complexes.

Je ne donne volontairement aucune réponse ni proposition de solution dans cet inventaire.

## **1. Périmètre de communication du Kernel**

- Avec quels types de composants le Kernel doit-il pouvoir communiquer ?
- Doit-il communiquer uniquement avec les services qu'il déploie ?
- Doit-il pouvoir communiquer avec des services déjà installés avant son propre déploiement ?
- Doit-il pouvoir communiquer avec des services installés indépendamment de lui ?
- Doit-il pouvoir communiquer avec des services qu'il ne connaît pas encore ?
- Doit-il pouvoir communiquer avec des applications complètes ?
- Doit-il pouvoir communiquer avec des infrastructures, des plateformes et des moteurs techniques ?
- Doit-il pouvoir communiquer avec des outils d'administration et de supervision ?
- Doit-il pouvoir communiquer avec des solutions propriétaires ?
- Doit-il pouvoir communiquer avec des solutions open source non modifiées ?
- Doit-il pouvoir communiquer avec des composants qui n'ont aucune connaissance de l'existence d'EGEN ?
- Doit-il pouvoir communiquer avec des systèmes qui ne sont pas conçus pour fonctionner dans une architecture distribuée ?
- Doit-il pouvoir communiquer avec des composants qui ne sont pas exécutés dans le même environnement ?
- Doit-il pouvoir communiquer avec des systèmes extérieurs à l'organisation ?
- Doit-il pouvoir communiquer avec des infrastructures appartenant à des tiers ?
- Quelles communications doivent être obligatoirement possibles pour considérer qu'un composant est intégrable à EGEN ?

## **2. Nature des communications**

- Quels types d'échanges le Kernel doit-il prendre en charge ?
- Doit-il pouvoir envoyer des commandes à un service ?
- Doit-il pouvoir recevoir des commandes d'un service ?
- Doit-il pouvoir demander des informations à un service ?
- Doit-il pouvoir recevoir spontanément des informations ?
- Doit-il pouvoir notifier un service d'un changement d'état ?
- Doit-il pouvoir recevoir des notifications provenant d'un service ?
- Doit-il pouvoir déclencher une opération à distance ?
- Doit-il pouvoir exécuter une opération de manière synchrone ?
- Doit-il pouvoir exécuter une opération de manière asynchrone ?
- Doit-il pouvoir maintenir des communications persistantes ?
- Doit-il pouvoir établir des communications ponctuelles ?
- Doit-il pouvoir gérer des communications bidirectionnelles ?
- Doit-il pouvoir gérer des communications unidirectionnelles ?
- Doit-il pouvoir transmettre des fichiers ?
- Doit-il pouvoir transmettre des flux de données continus ?
- Doit-il pouvoir transmettre des événements ?
- Doit-il pouvoir transmettre des commandes complexes contenant plusieurs opérations ?
- Doit-il pouvoir gérer des échanges nécessitant une confirmation de réception ?
- Doit-il pouvoir garantir qu'une opération a réellement été exécutée et pas seulement transmise ?
- Comment distinguer une commande acceptée, une commande en cours, une commande terminée et une commande échouée ?

## **3. Protocoles et mécanismes de communication**

- Quels protocoles de communication le Kernel doit-il connaître ?
- Doit-il prendre en charge HTTP et HTTPS ?
- Doit-il prendre en charge REST ?
- Doit-il prendre en charge GraphQL ?
- Doit-il prendre en charge gRPC ?
- Doit-il prendre en charge WebSocket ?
- Doit-il prendre en charge Server-Sent Events ?
- Doit-il prendre en charge les Webhooks ?
- Doit-il prendre en charge les systèmes de messagerie ?
- Doit-il pouvoir fonctionner avec NATS ?
- Doit-il pouvoir fonctionner avec Kafka ?
- Doit-il pouvoir fonctionner avec RabbitMQ ?
- Doit-il pouvoir communiquer par AMQP ou MQTT ?
- Doit-il pouvoir communiquer avec des protocoles propres à certaines infrastructures ?
- Doit-il pouvoir intégrer des systèmes qui communiquent uniquement par fichiers ?
- Doit-il pouvoir intégrer des systèmes qui exposent uniquement une CLI ?
- Doit-il pouvoir intégrer des systèmes qui utilisent des sockets locaux ?
- Doit-il pouvoir communiquer avec des bases de données directement ?
- Doit-il pouvoir utiliser des protocoles différents selon le composant destinataire ?
- Peut-il faire coexister plusieurs protocoles pour un même service ?
- Comment gérer les protocoles propriétaires ou les implémentations non standard ?
- Comment ajouter un nouveau protocole sans modifier les fonctionnalités fondamentales du Kernel ?

## **4. Découverte et identification des composants**

- Comment le Kernel découvre-t-il les services disponibles ?
- Comment un service découvre-t-il le Kernel ?
- Comment un service découvre-t-il les autres services ?
- Comment un composant peut-il annoncer son existence ?
- Comment un composant peut-il annoncer les fonctionnalités qu'il expose ?
- Comment distinguer un service simplement installé d'un service réellement opérationnel ?
- Comment identifier un composant de manière unique ?
- Comment gérer plusieurs instances d'un même service ?
- Comment gérer plusieurs services portant le même nom ?
- Comment identifier les points d'entrée d'un service ?
- Comment connaître les protocoles qu'un service accepte ?
- Comment connaître les versions de ses interfaces ?
- Comment connaître les dépendances d'un service ?
- Comment connaître les capacités qu'un service peut réellement exécuter ?
- Comment gérer un service qui change d'adresse IP ou de port ?
- Comment gérer un service qui change d'environnement d'exécution ?
- Comment gérer un service temporairement indisponible ?
- Comment gérer un service qui disparaît sans prévenir ?
- Comment éviter qu'un service continue d'être considéré comme disponible alors qu'il ne l'est plus ?
- Comment découvrir des services qui ne sont pas enregistrés dans le registre du Kernel ?

## **5. Contrats, interfaces et compatibilité**

- Qu'est-ce qui constitue une interface officielle du Kernel ?
- Quels contrats le Kernel doit-il exposer aux services externes ?
- Ces contrats couvrent-ils toutes les opérations fondamentales du Kernel ?
- Comment décrire formellement les fonctionnalités exposées par un service ?
- Comment décrire les entrées et les sorties de chaque opération ?
- Comment décrire les erreurs possibles ?
- Comment décrire les événements émis et reçus ?
- Comment décrire les préconditions et les effets d'une opération ?
- Comment un service peut-il connaître les capacités disponibles sans connaître l'implémentation interne du Kernel ?
- Comment un service peut-il découvrir dynamiquement les interfaces disponibles ?
- Comment gérer les contrats obligatoires et les fonctionnalités facultatives ?
- Comment gérer les interfaces qui évoluent ?
- Comment gérer plusieurs versions d'un même contrat ?
- Comment maintenir la compatibilité avec les anciennes versions ?
- Comment détecter qu'un service utilise un contrat devenu obsolète ?
- Comment éviter qu'une modification du Kernel oblige à modifier tous les services intégrés ?
- Comment éviter qu'une évolution d'un service externe casse les intégrations existantes ?
- Comment vérifier automatiquement qu'un composant respecte réellement le contrat qu'il déclare ?
- Comment gérer les fonctionnalités qui n'ont pas d'équivalent dans les contrats du Kernel ?

## **6. Intégration des services développés ou personnalisés**

- Comment un développeur peut-il rendre son service compatible avec EGEN ?
- Quelles informations doit-il connaître avant de commencer l'intégration ?
- Doit-il connaître toute l'architecture interne du Kernel ?
- Peut-il intégrer EGEN sans dépendre directement de son code source ?
- Peut-il utiliser un SDK dans un langage différent de celui du Kernel ?
- Quels langages doivent pouvoir intégrer les interfaces d'EGEN ?
- Faut-il proposer des SDK Java, Python, Go, Rust, JavaScript et autres ?
- Comment intégrer un service qui utilise déjà son propre système de communication ?
- Comment intégrer un service qui possède déjà une API ?
- Comment intégrer un service qui possède déjà un système d'authentification ?
- Comment intégrer un service qui possède déjà son propre registre de composants ?
- Comment intégrer un service qui possède déjà son propre système d'événements ?
- Comment éviter de devoir réécrire une partie du service pour le rendre compatible ?
- Comment permettre à un service d'implémenter uniquement les capacités dont il a besoin ?
- Comment tester sa compatibilité avant son installation ?
- Comment certifier qu'un service respecte les contrats d'EGEN ?
- Comment gérer les services qui implémentent correctement les contrats, mais ne respectent pas les politiques d'exploitation ?
- Comment permettre à un service de mettre à jour son intégration sans devoir être entièrement redéployé ?

## **7. Intégration des solutions propriétaires et fermées**

- Comment intégrer une solution dont nous ne possédons pas le code source ?
- Comment intégrer une solution qui ne connaît pas EGEN ?
- Comment intégrer une solution qui ne permet aucune personnalisation ?
- Comment intégrer une solution qui possède uniquement une API REST ?
- Comment intégrer une solution qui possède uniquement une API GraphQL ?
- Comment intégrer une solution qui expose des Webhooks, mais ne sait pas consommer les événements d'EGEN ?
- Comment intégrer une solution qui consomme des événements, mais ne sait pas en produire ?
- Comment intégrer une solution qui possède un système d'authentification incompatible avec celui d'EGEN ?
- Comment intégrer une solution qui impose son propre système de gestion des utilisateurs ?
- Comment intégrer une solution qui impose son propre modèle de permissions ?
- Comment intégrer une solution qui ne permet pas de modifier ses URL de callback ?
- Comment intégrer une solution qui ne permet pas de modifier ses paramètres réseau ?
- Comment intégrer une solution qui exige une configuration manuelle ?
- Comment intégrer une solution qui fonctionne uniquement avec un serveur central distant ?
- Comment intégrer une solution SaaS hébergée chez un fournisseur externe ?
- Comment intégrer une solution qui n'expose qu'une partie de ses fonctionnalités ?
- Comment intégrer une solution qui change régulièrement son API ?
- Comment intégrer une solution dont la documentation est incomplète ?
- Comment intégrer une solution qui ne fournit aucune API officielle, mais dispose d'une interface Web ?
- Comment distinguer une intégration officiellement supportée d'une intégration reposant sur des mécanismes non garantis ?
- Jusqu'où EGEN doit-il accepter de s'adapter aux contraintes d'un logiciel propriétaire ?

## **8. Connecteurs, bridges et gateways**

- Quels types de connecteurs EGEN doit-il pouvoir accueillir ?
- Un connecteur doit-il être considéré comme un service autonome ?
- Un connecteur peut-il être développé indépendamment du Kernel ?
- Un connecteur peut-il être installé après le démarrage du Kernel ?
- Peut-on remplacer un connecteur sans interrompre le service qu'il connecte ?
- Un même connecteur peut-il communiquer avec plusieurs instances d'une même solution ?
- Un connecteur peut-il servir plusieurs Kernels ?
- Un connecteur peut-il exposer plusieurs protocoles ?
- Un connecteur peut-il traduire des API différentes vers un contrat commun ?
- Comment gérer les différences de modèles de données entre deux systèmes ?
- Comment gérer les opérations qui n'ont pas d'équivalent exact ?
- Comment gérer les erreurs spécifiques d'une technologie externe ?
- Comment gérer les limites de débit imposées par un fournisseur ?
- Comment gérer les quotas et les limitations d'API ?
- Comment gérer les tokens et les clés nécessaires aux connecteurs ?
- Comment éviter qu'un connecteur devienne un point de défaillance unique ?
- Comment isoler un connecteur défectueux ?
- Comment surveiller la disponibilité et les performances des connecteurs ?
- Comment tester un connecteur indépendamment du Kernel ?
- Comment permettre à plusieurs connecteurs de collaborer sur une même intégration ?
- Comment déterminer si une fonctionnalité doit être implémentée dans le Kernel, dans un connecteur ou dans un service externe ?

## **9. Authentification et identité entre composants**

- Comment un service s'identifie-t-il auprès du Kernel ?
- Comment le Kernel s'identifie-t-il auprès d'un service ?
- Comment authentifier deux services qui communiquent directement ?
- Comment gérer les identités techniques distinctes des identités humaines ?
- Comment gérer les identités des connecteurs ?
- Comment gérer les identités des agents d'administration ?
- Comment gérer les certificats des composants ?
- Comment gérer les secrets et les clés d'accès ?
- Comment renouveler les credentials sans interrompre les communications ?
- Comment révoquer immédiatement l'accès d'un composant ?
- Comment gérer les certificats expirés ?
- Comment gérer les communications entre plusieurs organisations ?
- Comment gérer les composants qui utilisent OIDC ?
- Comment gérer les composants qui utilisent OAuth 2.0 ?
- Comment gérer les composants qui utilisent SAML ?
- Comment gérer les composants qui utilisent LDAP ?
- Comment gérer les composants qui imposent une authentification propriétaire ?
- Comment déléguer une identité ou une autorisation à un autre service ?
- Comment éviter qu'un service puisse usurper l'identité d'un autre ?
- Comment savoir quel composant a réellement initié une opération ?
- Comment gérer les identités lors du déplacement d'un service vers un autre environnement ?

## **10. Autorisations, politiques et gouvernance**

- Qui a le droit de communiquer avec le Kernel ?
- Quels services peuvent appeler quelles interfaces ?
- Quels services peuvent déclencher quelles opérations ?
- Comment attribuer des permissions différentes selon les capacités d'un service ?
- Comment gérer les permissions au niveau d'une opération précise ?
- Comment gérer les permissions sur une ressource particulière ?
- Comment gérer les permissions temporaires ?
- Comment déléguer une autorisation à un service ?
- Comment limiter les actions d'un service même lorsqu'il est authentifié ?
- Comment empêcher un service d'appeler directement une API qu'il ne devrait pas utiliser ?
- Comment faire respecter les politiques lorsque deux services communiquent directement sans passer par le Kernel ?
- Comment gérer les politiques d'un écosystème ?
- Comment gérer les politiques communes à plusieurs écosystèmes ?
- Comment gérer les politiques propres à un service ?
- Comment gérer les politiques imposées par l'organisation ?
- Comment gérer les politiques imposées par l'infrastructure d'hébergement ?
- Comment gérer les conflits entre plusieurs politiques ?
- Comment modifier une politique sans redéployer les services ?
- Comment appliquer immédiatement une révocation ?
- Comment tracer les décisions d'autorisation ?
- Comment prouver qu'une opération a été exécutée conformément aux politiques applicables ?

## **11. Communication interservices**

- Comment deux services peuvent-ils communiquer sans dépendre directement l'un de l'autre ?
- Comment un service peut-il découvrir les capacités d'un autre ?
- Comment un service peut-il demander une opération à un autre ?
- Comment gérer les dépendances entre services ?
- Comment éviter les dépendances circulaires ?
- Comment gérer les communications directes entre services ?
- Comment gérer les communications passant par le Kernel ?
- Dans quels cas le Kernel doit-il intervenir dans une communication ?
- Dans quels cas doit-il laisser les services communiquer directement ?
- Comment garantir que les politiques restent applicables dans les deux situations ?
- Comment gérer les appels entre services appartenant à des écosystèmes différents ?
- Comment gérer les appels entre services appartenant à des organisations différentes ?
- Comment gérer les services qui utilisent des protocoles différents ?
- Comment gérer les échanges de données entre services qui n'ont pas le même modèle de données ?
- Comment éviter qu'un service dépende de l'adresse physique d'un autre ?
- Comment gérer les appels simultanés vers plusieurs services ?
- Comment gérer les opérations nécessitant plusieurs services ?
- Comment gérer les communications lorsque l'un des services devient indisponible ?
- Comment éviter qu'une panne locale se propage à tout l'écosystème ?
- Comment gérer les limites de temps d'exécution des appels interservices ?

## **12. Messagerie, événements et diffusion**

- Comment un service publie-t-il un événement ?
- Comment un service s'abonne-t-il aux événements d'autres services ?
- Comment découvrir les événements disponibles ?
- Comment décrire le contenu d'un événement ?
- Comment gérer les versions des événements ?
- Comment gérer plusieurs brokers de messagerie ?
- Comment permettre à un service d'utiliser NATS et à un autre d'utiliser Kafka ?
- Comment gérer les événements qui doivent être transmis à plusieurs destinations ?
- Comment gérer les événements qui ne sont pas consommés ?
- Comment gérer les doublons ?
- Comment gérer l'ordre des événements ?
- Comment garantir qu'un événement important ne soit pas perdu ?
- Comment gérer les événements reçus plusieurs fois ?
- Comment gérer les événements qui arrivent en retard ?
- Comment rejouer les événements ?
- Comment gérer les événements qui ne peuvent plus être traités ?
- Comment isoler les files de messages défectueux ?
- Comment gérer les abonnements dynamiques ?
- Comment gérer les abonnements temporaires ?
- Comment gérer les événements entre plusieurs écosystèmes ?
- Comment gérer la diffusion d'événements vers des systèmes externes ?
- Comment garantir que les événements transmis respectent les autorisations de leurs destinataires ?

## **13. Gestion des données et des échanges**

- Quels formats de données le Kernel doit-il accepter ?
- Doit-il accepter JSON, XML, YAML, Protobuf, Avro et d'autres formats ?
- Comment gérer les données binaires ?
- Comment gérer les fichiers volumineux ?
- Comment transmettre des fichiers entre deux services ?
- Comment gérer les transferts interrompus ?
- Comment gérer les transferts reprenables ?
- Comment gérer les données sensibles ?
- Comment déterminer quelles données un service est autorisé à consulter ?
- Comment éviter qu'un service reçoive des données dont il n'a pas besoin ?
- Comment gérer les transformations de données entre systèmes ?
- Comment gérer les différences de représentation des dates, nombres, devises et identifiants ?
- Comment gérer les encodages et les langues ?
- Comment gérer les données structurées et non structurées ?
- Comment gérer les données qui transitent par plusieurs services ?
- Comment tracer l'origine d'une donnée ?
- Comment savoir quel service a modifié une donnée ?
- Comment gérer la cohérence des données entre plusieurs systèmes ?
- Comment gérer les conflits de données ?
- Comment gérer les synchronisations entre deux systèmes ?
- Comment gérer les suppressions et les modifications répercutées sur plusieurs systèmes ?
- Comment gérer les données qui doivent rester dans une infrastructure particulière ?
- Comment gérer les échanges de données entre des environnements soumis à des règles de confidentialité différentes ?

## **14. Déploiement, installation et cycle de vie des services intégrés**

- Comment le Kernel installe-t-il un service qui doit ensuite communiquer avec lui ?
- Comment un service connaît-il les informations nécessaires à sa connexion dès son premier démarrage ?
- Comment lui transmettre sa configuration initiale ?
- Comment lui transmettre ses credentials ?
- Comment lui transmettre les politiques qui le concernent ?
- Comment gérer un service qui nécessite une configuration avant de démarrer ?
- Comment gérer un service qui doit découvrir le Kernel après son démarrage ?
- Comment gérer un service qui doit être enregistré avant son démarrage ?
- Comment gérer un service qui doit être démarré avant un autre ?
- Comment gérer les dépendances de démarrage ?
- Comment gérer les services qui doivent être installés dans un ordre précis ?
- Comment gérer les services qui nécessitent plusieurs phases d'initialisation ?
- Comment gérer les services qui doivent effectuer une migration avant d'être opérationnels ?
- Comment gérer un service qui démarre, mais ne parvient pas à se connecter au Kernel ?
- Comment gérer un service qui se connecte, puis perd sa connexion ?
- Comment gérer un service qui redémarre automatiquement ?
- Comment gérer les changements d'adresse, de certificat ou de configuration après un redémarrage ?
- Comment gérer les mises à jour d'un service sans interrompre les communications ?
- Comment gérer le remplacement d'une instance par une autre ?
- Comment gérer la désinstallation d'un service qui possède encore des dépendances ?
- Comment éviter de laisser des identités, des credentials ou des enregistrements inutilisés après sa suppression ?
- Comment gérer le retour à une version précédente ?
- Comment gérer un service dont le cycle de vie est entièrement contrôlé par une plateforme externe ?

## **15. Gestion des environnements hétérogènes**

- Comment EGEN communique-t-il avec des services exécutés dans Docker ?
- Comment communique-t-il avec des services exécutés dans Kubernetes ?
- Comment communique-t-il avec des services exécutés dans Nomad ?
- Comment communique-t-il avec des services exécutés directement sur une machine ?
- Comment communique-t-il avec des applications exécutées sur des machines virtuelles ?
- Comment communique-t-il avec des services serverless ?
- Comment communique-t-il avec des services hébergés dans le cloud ?
- Comment communique-t-il avec des services hébergés sur site (on-premises) ?
- Comment communique-t-il avec des services installés sur des serveurs distants ?
- Comment communique-t-il avec des services installés sur des équipements physiques ?
- Comment communiquer avec des systèmes Windows et Linux ?
- Comment gérer des environnements macOS ?
- Comment gérer des architectures ARM et x86 ?
- Comment intégrer des équipements réseau ?
- Comment intégrer des équipements industriels ou IoT ?
- Comment intégrer des systèmes qui ne disposent pas d'une connectivité permanente ?
- Comment intégrer des systèmes derrière des NAT ou des pare-feu ?
- Comment gérer des environnements isolés du réseau Internet ?
- Comment gérer des réseaux privés et des réseaux publics ?
- Comment gérer les communications entre plusieurs clouds ?
- Comment gérer des environnements dont les configurations réseau sont différentes ?
- Comment éviter de supposer que tous les services peuvent communiquer directement entre eux ?

## **16. Communication entre plusieurs Kernels et plusieurs écosystèmes**

- Un Kernel peut-il découvrir un autre Kernel ?
- Comment deux Kernels s'identifient-ils mutuellement ?
- Comment établir une relation de confiance entre eux ?
- Comment un Kernel peut-il exposer certaines de ses capacités à un autre ?
- Comment un Kernel peut-il consommer les capacités d'un autre ?
- Comment gérer des Kernels qui appartiennent à des organisations différentes ?
- Comment gérer des Kernels qui ne dépendent pas de la même autorité administrative ?
- Comment gérer plusieurs Kernels dans une même organisation ?
- Comment gérer plusieurs écosystèmes qui possèdent chacun leur Kernel ?
- Comment un service peut-il être découvert depuis plusieurs écosystèmes ?
- Comment gérer les services transversaux partagés entre plusieurs écosystèmes ?
- Comment éviter de déployer plusieurs fois un service qui pourrait être mutualisé ?
- Comment gérer les services qui sont mutualisés, mais dont les politiques diffèrent selon l'écosystème consommateur ?
- Comment gérer les identités entre écosystèmes ?
- Comment gérer les autorisations entre écosystèmes ?
- Comment gérer les événements qui traversent plusieurs Kernels ?
- Comment gérer les communications entre un Kernel local et un Kernel distant ?
- Comment gérer les connexions intermittentes entre deux Kernels ?
- Comment gérer les différences de versions entre deux Kernels ?
- Comment gérer une organisation qui possède plusieurs infrastructures indépendantes ?
- Comment gérer une fédération de Kernels sans créer un point central obligatoire ?

## **17. Services d'administration, de supervision et d'exploitation**

- Comment un service d'administration peut-il découvrir les capacités administratives du Kernel ?
- Comment peut-il découvrir les services installés ?
- Comment peut-il découvrir les moteurs utilisés ?
- Comment peut-il découvrir les infrastructures disponibles ?
- Comment peut-il consulter les états des services ?
- Comment peut-il consulter les états des déploiements ?
- Comment peut-il demander le démarrage d'un service ?
- Comment peut-il demander l'arrêt d'un service ?
- Comment peut-il demander le redémarrage d'un service ?
- Comment peut-il modifier une configuration ?
- Comment peut-il consulter les journaux ?
- Comment peut-il consulter les métriques ?
- Comment peut-il recevoir les alertes ?
- Comment peut-il déclencher des opérations de maintenance ?
- Comment peut-il gérer les opérations qui nécessitent une validation humaine ?
- Comment peut-il gérer les opérations qui nécessitent plusieurs validations ?
- Comment peut-il administrer plusieurs Kernels depuis une interface unique ?
- Comment peut-il administrer des environnements qui utilisent des moteurs différents ?
- Comment intégrer une solution d'administration open source sans modifier son cœur ?
- Comment intégrer une solution propriétaire qui ne connaît pas EGEN ?
- Comment gérer les outils d'administration qui possèdent déjà leur propre modèle de permissions ?
- Comment éviter que plusieurs outils d'administration effectuent des opérations contradictoires ?
- Comment garantir que l'interface d'administration reflète l'état réel du Kernel et non un état mis en cache devenu obsolète ?
- Comment gérer les services d'administration eux-mêmes ?
- Comment administrer le service qui administre le Kernel ?
- Comment récupérer l'accès à l'administration si tous les services d'administration sont indisponibles ?

## **18. Interopérabilité avec les systèmes d'identité et de sécurité**

- Comment intégrer un Keycloak déjà existant ?
- Comment intégrer plusieurs fournisseurs d'identité ?
- Comment gérer un système d'identité centralisé pour plusieurs écosystèmes ?
- Comment gérer des systèmes d'identité qui restent indépendants ?
- Comment intégrer Microsoft Active Directory ?
- Comment intégrer un annuaire LDAP ?
- Comment intégrer des fournisseurs d'identité cloud ?
- Comment gérer les utilisateurs, groupes et rôles provenant de systèmes externes ?
- Comment synchroniser les changements d'identité ?
- Comment gérer les comptes désactivés ?
- Comment gérer les sessions et leur expiration ?
- Comment gérer les déconnexions globales ?
- Comment gérer la délégation des accès ?
- Comment gérer les identités techniques utilisées par les services ?
- Comment gérer les certificats TLS et mTLS ?
- Comment gérer les autorités de certification différentes ?
- Comment gérer la rotation des clés ?
- Comment gérer les politiques de sécurité imposées par un fournisseur externe ?
- Comment intégrer des systèmes qui ne prennent pas en charge les mécanismes d'identité retenus par EGEN ?
- Comment garantir la traçabilité des accès à travers plusieurs systèmes ?

## **19. Fiabilité, résilience et tolérance aux pannes**

- Que se passe-t-il lorsqu'un service ne répond plus ?
- Que se passe-t-il lorsque le Kernel ne répond plus ?
- Que se passe-t-il lorsque le système de messagerie tombe en panne ?
- Que se passe-t-il lorsque le registre de découverte devient indisponible ?
- Que se passe-t-il lorsqu'un connecteur perd sa connexion avec le système externe ?
- Comment distinguer une panne d'une simple lenteur ?
- Comment gérer les délais d'attente ?
- Comment gérer les tentatives de reconnexion ?
- Comment éviter les tentatives de reconnexion infinies ?
- Comment éviter qu'un service indisponible surcharge le Kernel avec des requêtes répétées ?
- Comment détecter les communications dégradées ?
- Comment gérer les opérations qui ont été acceptées, mais dont le résultat est inconnu après une panne ?
- Comment reprendre une opération interrompue ?
- Comment gérer les opérations qui ne peuvent pas être répétées sans risque ?
- Comment gérer les opérations partiellement exécutées ?
- Comment annuler une opération déjà commencée ?
- Comment gérer les opérations qui nécessitent une compensation ?
- Comment gérer les communications pendant une migration d'infrastructure ?
- Comment maintenir les communications pendant une mise à jour du Kernel ?
- Comment éviter qu'une panne d'un service d'administration entraîne une panne du Kernel ?
- Comment garantir un fonctionnement dégradé lorsque certains composants sont indisponibles ?
- Comment reprendre les communications après une longue interruption réseau ?
- Comment réconcilier les états après une période pendant laquelle deux systèmes n'ont pas pu communiquer ?

## **20. Cohérence, concurrence et orchestration**

- Comment gérer deux services qui demandent simultanément la même opération ?
- Comment empêcher deux opérations incompatibles de s'exécuter simultanément ?
- Comment gérer les verrous distribués ?
- Comment gérer les conflits entre plusieurs administrateurs ?
- Comment gérer les modifications concurrentes d'une même configuration ?
- Comment déterminer quelle source détient l'état de référence ?
- Comment éviter que le Kernel et un outil externe maintiennent deux états contradictoires ?
- Comment gérer les opérations qui traversent plusieurs systèmes ?
- Comment coordonner plusieurs opérations indépendantes ?
- Comment gérer les dépendances entre les étapes d'un workflow ?
- Comment gérer les opérations qui nécessitent une validation intermédiaire ?
- Comment gérer les workflows interrompus ?
- Comment reprendre un workflow après le redémarrage du Kernel ?
- Comment gérer les opérations qui doivent être exécutées exactement une fois, ou dont les effets doivent être uniques ?
- Comment gérer les opérations qui peuvent être exécutées plusieurs fois sans effet indésirable ?
- Comment gérer les transactions entre plusieurs services qui ne partagent pas la même base de données ?
- Comment gérer les opérations longues ?
- Comment suivre une opération depuis sa demande initiale jusqu'à son résultat final ?
- Comment gérer les opérations lancées depuis plusieurs canaux d'administration ?
- Comment éviter qu'une commande provenant de la CLI entre en conflit avec une commande provenant de l'interface Web ?

## **21. Sécurité des communications et confiance**

- Comment sécuriser toutes les communications entre composants ?
- Comment protéger les communications internes ?
- Comment protéger les communications externes ?
- Comment gérer le chiffrement des données en transit ?
- Comment gérer le chiffrement des données au repos ?
- Comment authentifier les interlocuteurs ?
- Comment vérifier l'intégrité des messages ?
- Comment empêcher la modification d'une commande pendant son transport ?
- Comment empêcher la répétition malveillante d'une ancienne commande ?
- Comment limiter les interfaces accessibles depuis l'extérieur ?
- Comment gérer les réseaux de confiance et les réseaux non fiables ?
- Comment isoler les composants qui ne sont pas totalement fiables ?
- Comment intégrer un service tiers sans lui donner accès à tout l'écosystème ?
- Comment gérer les secrets transmis lors du déploiement ?
- Comment empêcher les secrets d'apparaître dans les journaux ?
- Comment détecter une communication anormale ?
- Comment détecter un service compromis ?
- Comment révoquer la confiance accordée à un composant ?
- Comment gérer les certificats compromis ?
- Comment assurer une séparation entre les droits de consultation, d'exploitation et d'administration ?
- Comment vérifier la provenance et l'intégrité des connecteurs installés ?
- Comment éviter qu'un connecteur malveillant utilise les autorisations du Kernel à son propre avantage ?

## **22. Observabilité, traçabilité et audit**

- Comment savoir quels composants communiquent entre eux ?
- Comment visualiser les dépendances de communication ?
- Comment identifier les appels qui échouent ?
- Comment mesurer les temps de réponse ?
- Comment mesurer les volumes de données échangées ?
- Comment suivre une requête lorsqu'elle traverse plusieurs services ?
- Comment corréler les journaux provenant de plusieurs composants ?
- Comment corréler les événements, les commandes et les opérations ?
- Comment identifier le composant à l'origine d'une erreur ?
- Comment identifier le composant responsable d'une modification ?
- Comment consulter l'historique des communications ?
- Comment conserver les traces des opérations administratives ?
- Comment garantir que les traces d'audit ne puissent pas être modifiées par un service ordinaire ?
- Comment gérer la durée de conservation des traces ?
- Comment exporter les métriques vers des outils externes ?
- Comment intégrer des solutions d'observabilité déjà installées ?
- Comment intégrer des outils propriétaires de supervision ?
- Comment permettre à un outil externe de surveiller le Kernel sans accéder à ses données internes ?
- Comment visualiser les communications entre plusieurs écosystèmes ?
- Comment identifier les goulots d'étranglement ?
- Comment détecter une dégradation progressive des communications ?

## **23. Configuration et adaptation dynamique**

- Comment un service reçoit-il sa configuration initiale ?
- Comment un service découvre-t-il les paramètres de configuration qui le concernent ?
- Comment modifier sa configuration sans le redéployer ?
- Comment notifier un service d'une modification de configuration ?
- Comment gérer les configurations propres à chaque environnement ?
- Comment gérer les configurations propres à chaque écosystème ?
- Comment gérer les configurations communes à plusieurs services ?
- Comment gérer les configurations secrètes ?
- Comment gérer les configurations versionnées ?
- Comment revenir à une configuration précédente ?
- Comment éviter qu'une modification invalide rende un service inutilisable ?
- Comment valider une configuration avant son application ?
- Comment gérer les configurations imposées par une solution propriétaire ?
- Comment gérer les paramètres que le Kernel ne connaît pas ?
- Comment gérer les changements de configuration qui nécessitent un redémarrage ?
- Comment gérer les changements qui nécessitent une migration ?
- Comment permettre à un service de déclarer les paramètres qu'il accepte ?
- Comment permettre à un service de signaler les paramètres qu'il ne comprend plus ?
- Comment gérer les changements de configuration lorsque le service est momentanément inaccessible ?

## **24. Gestion des ressources et des capacités techniques**

- Comment un service déclare-t-il les ressources dont il a besoin ?
- Comment un service peut-il annoncer les ressources qu'il consomme réellement ?
- Comment découvrir les capacités disponibles dans une infrastructure ?
- Comment découvrir les capacités d'un moteur de déploiement ?
- Comment gérer les différences entre les capacités de Docker, Nomad et Kubernetes ?
- Comment savoir si un service est compatible avec un environnement donné ?
- Comment gérer les contraintes matérielles ?
- Comment gérer les GPU, les accélérateurs et les ressources spécialisées ?
- Comment gérer les volumes persistants ?
- Comment gérer les réseaux et les interfaces réseau ?
- Comment gérer les ports exposés ?
- Comment gérer les contraintes de stockage ?
- Comment gérer les limites de ressources ?
- Comment gérer les ressources partagées entre plusieurs services ?
- Comment détecter une saturation ?
- Comment adapter le déploiement lorsqu'une capacité technique n'est plus disponible ?
- Comment gérer les différences de capacités entre deux infrastructures ?
- Comment éviter qu'un service dépende d'une ressource qui n'existe pas dans son environnement cible ?

## **25. Extensibilité et évolution du Kernel**

- Comment ajouter une nouvelle interface au Kernel ?
- Comment ajouter un nouveau protocole de communication ?
- Comment ajouter un nouveau mécanisme d'authentification ?
- Comment ajouter un nouveau type de connecteur ?
- Comment ajouter un nouveau moteur de messagerie ?
- Comment ajouter une nouvelle capacité administrative ?
- Comment ajouter une nouvelle catégorie de service intégrable ?
- Comment permettre à des développeurs tiers de créer des extensions ?
- Comment permettre à une extension d'enregistrer de nouvelles capacités ?
- Comment permettre à une extension d'exposer ses propres interfaces ?
- Comment éviter que les extensions puissent modifier les fonctions fondamentales du Kernel ?
- Comment isoler les extensions les unes des autres ?
- Comment gérer les dépendances entre extensions ?
- Comment gérer les extensions incompatibles entre elles ?
- Comment installer une extension à chaud ?
- Comment désinstaller une extension sans affecter les autres composants ?
- Comment mettre à jour une extension indépendamment du Kernel ?
- Comment garantir la compatibilité des extensions avec plusieurs versions du Kernel ?
- Comment tester une extension avant de l'autoriser dans un environnement de production ?
- Comment gérer un registre de connecteurs et d'extensions ?
- Comment gérer la distribution, la signature et la provenance des extensions ?
- Comment permettre à plusieurs fournisseurs de développer leurs propres extensions sans dépendre de CIVITAS Africa ?

## **26. Décentralisation et autonomie des composants**

- Le Kernel doit-il être le passage obligatoire de toutes les communications ?
- Quels composants doivent pouvoir communiquer directement entre eux ?
- Quels composants doivent obligatoirement passer par un point de contrôle ?
- Comment éviter de transformer le Kernel en goulot d'étranglement ?
- Comment éviter de transformer le Kernel en point de défaillance unique ?
- Comment permettre aux services de continuer à fonctionner lorsque le Kernel est indisponible ?
- Comment gérer les communications entre services sans registre central disponible ?
- Comment gérer les décisions qui doivent être prises localement ?
- Comment gérer les services qui disposent de leur propre logique d'orchestration ?
- Comment gérer les services qui possèdent leur propre système de découverte ?
- Comment gérer les infrastructures qui possèdent déjà leurs propres mécanismes de gouvernance ?
- Comment répartir les responsabilités entre le Kernel et les services autonomes ?
- Comment gérer plusieurs composants capables d'effectuer la même fonction ?
- Comment éviter les duplications de responsabilités ?
- Comment préserver l'autonomie des services tout en conservant une gouvernance commune ?
- Comment permettre à un composant de changer de Kernel de rattachement ?
- Comment permettre à un composant de fonctionner temporairement sans son Kernel de référence ?

## **27. Compatibilité avec les solutions et standards du marché**

- Comment intégrer les solutions qui utilisent déjà OpenTelemetry ?
- Comment intégrer Prometheus et Grafana ?
- Comment intégrer les systèmes de logs existants ?
- Comment intégrer les outils de gestion des secrets ?
- Comment intégrer les outils de gestion des certificats ?
- Comment intégrer les outils de gestion des identités ?
- Comment intégrer les outils de gestion des API ?
- Comment intégrer les outils de gestion des infrastructures ?
- Comment intégrer les outils de sauvegarde et de restauration ?
- Comment intégrer les outils de déploiement continu ?
- Comment intégrer les outils de gestion de configuration ?
- Comment intégrer les systèmes de gestion des incidents ?
- Comment intégrer les outils de ticketing ?
- Comment intégrer les systèmes de notification ?
- Comment intégrer les outils de gestion des coûts ?
- Comment intégrer les plateformes de gestion de données ?
- Comment intégrer les systèmes de stockage objet ?
- Comment intégrer les outils de sécurité et de détection des menaces ?
- Comment intégrer les solutions qui suivent des standards ouverts ?
- Comment intégrer les solutions qui utilisent des conventions propres à leur éditeur ?
- Comment éviter de réimplémenter une fonctionnalité déjà parfaitement prise en charge par une solution existante ?

## **28. Intégration dans les environnements d'entreprise**

- Comment intégrer EGEN dans une entreprise qui possède déjà une infrastructure informatique ?
- Comment intégrer EGEN sans remplacer les outils déjà en place ?
- Comment intégrer EGEN progressivement ?
- Comment intégrer EGEN dans une infrastructure fortement centralisée ?
- Comment l'intégrer dans une infrastructure décentralisée ?
- Comment l'intégrer dans un environnement soumis à des contraintes réglementaires ?
- Comment l'intégrer dans un environnement bancaire ?
- Comment l'intégrer dans une administration publique ?
- Comment l'intégrer dans un environnement industriel ?
- Comment l'intégrer dans un environnement universitaire ?
- Comment l'intégrer dans une entreprise qui possède plusieurs filiales ?
- Comment intégrer des systèmes anciens (legacy) ?
- Comment intégrer des systèmes qui ne peuvent pas être mis à jour ?
- Comment gérer les systèmes dont les interfaces sont mal documentées ?
- Comment gérer les infrastructures exploitées par plusieurs prestataires ?
- Comment gérer les responsabilités entre l'entreprise, CIVITAS et les fournisseurs externes ?
- Comment intégrer des composants qui appartiennent à des périmètres de confiance différents ?
- Comment gérer les contraintes de résidence des données ?
- Comment gérer les environnements sans accès Internet ?
- Comment assurer la continuité des opérations lorsque les fournisseurs externes sont indisponibles ?

## **29. Installation, bootstrap et auto-administration**

- Comment installer le Kernel dans un environnement vierge ?
- De quelles capacités initiales le Kernel dispose-t-il ?
- Comment découvre-t-il les ressources disponibles au premier démarrage ?
- Comment installe-t-il les composants nécessaires à son propre fonctionnement ?
- Comment déploie-t-il les services qui permettront de l'administrer ?
- Comment ces services découvrent-ils le Kernel ?
- Comment leur fournit-il les informations nécessaires à leur connexion ?
- Comment gère-t-il les services d'administration avant que ceux-ci soient opérationnels ?
- Comment administrer le Kernel pendant la phase d'installation ?
- Comment récupérer un Kernel qui n'arrive plus à démarrer ses propres services d'administration ?
- Comment mettre à jour les composants qui participent à l'administration du Kernel ?
- Comment éviter qu'une mise à jour de ces composants bloque toute administration ?
- Comment gérer la perte du registre, du broker ou du système d'identité nécessaire à l'administration ?
- Comment restaurer l'administration après une panne majeure ?
- Comment garantir l'existence d'un canal de secours indépendant ?
- Comment transférer l'administration d'un Kernel vers un autre ?
- Comment administrer un Kernel qui n'a plus accès au réseau ?
- Comment éviter qu'un Kernel puisse accidentellement détruire les moyens nécessaires à sa propre récupération ?

## **30. Tests, validation et certification de l'interopérabilité**

- Comment vérifier qu'un service est réellement compatible avec EGEN ?
- Comment tester ses interfaces sans déployer le service ?
- Comment simuler un Kernel pour tester un service indépendamment ?
- Comment simuler un service externe pour tester le Kernel ?
- Comment tester les échanges entre plusieurs langages ?
- Comment tester les échanges entre plusieurs protocoles ?
- Comment tester les changements de versions ?
- Comment tester les erreurs de communication ?
- Comment tester les interruptions réseau ?
- Comment tester les expirations de certificats ?
- Comment tester les changements de permissions ?
- Comment tester les scénarios de panne ?
- Comment tester les communications entre plusieurs écosystèmes ?
- Comment vérifier qu'un connecteur ne contourne pas les politiques de sécurité ?
- Comment vérifier qu'une solution externe respecte les limites imposées ?
- Comment effectuer des tests de charge sur les communications ?
- Comment mesurer les performances d'une intégration ?
- Comment vérifier qu'une nouvelle version du Kernel ne casse pas les intégrations existantes ?
- Comment certifier une extension développée par un tiers ?
- Comment maintenir une matrice de compatibilité entre les versions du Kernel, des SDK et des connecteurs ?

## **31. Questions de conception fondamentale**

Ces dernières questions sont particulièrement importantes, parce qu'elles touchent à la philosophie même d'EGEN.

- EGEN Kernel est-il un orchestrateur de services ou également un orchestrateur de communications ?
- Est-il un registre de capacités ou doit-il aussi fournir un système de négociation des capacités ?
- Est-il un point de passage obligatoire ou un plan de contrôle qui laisse les services communiquer librement ?
- Doit-il connaître les technologies utilisées par chaque service ou uniquement leurs contrats ?
- Doit-il connaître les modèles de données des services ou uniquement les formats d'échange ?
- Doit-il imposer un protocole commun ou accepter plusieurs protocoles simultanément ?
- Doit-il imposer un SDK ou proposer des contrats indépendants des langages ?
- Doit-il connaître les solutions externes intégrées ou seulement leurs connecteurs ?
- Doit-il gérer les identités de tous les composants ou déléguer cette responsabilité ?
- Doit-il centraliser les politiques ou permettre leur distribution ?
- Doit-il gérer lui-même les événements ou simplement fédérer plusieurs systèmes de messagerie ?
- Doit-il gérer lui-même les communications interservices ou uniquement les rendre possibles ?
- Doit-il fournir des capacités d'administration ou permettre à des services spécialisés de les fournir ?
- Jusqu'où doit-il connaître les infrastructures qu'il orchestre ?
- Quelles responsabilités doivent rester exclusivement dans le Kernel ?
- Quelles responsabilités doivent être externalisées ?
- Quelles responsabilités doivent être partagées entre le Kernel et ses extensions ?
- Quelles fonctions doivent être obligatoires pour tous les services ?
- Quelles fonctions doivent être facultatives ?
- Quelles fonctions doivent être totalement indépendantes de la technologie ?
- Quelles fonctions peuvent dépendre d'une technologie particulière ?
- Comment éviter que le Kernel devienne progressivement un framework monolithique d'intégration ?
- Comment garantir que le Kernel reste utilisable même si toutes ses extensions sont supprimées ?
- Comment garantir que son architecture reste valable avec des technologies qui n'existent pas encore ?

# **32. Les questions transversales à ne surtout pas oublier**

Il existe des questions qui traversent plusieurs catégories. Elles méritent d'être examinées séparément.

| Dimension        | Question fondamentale                                                      |
| ---------------- | -------------------------------------------------------------------------- |
| Découverte       | Comment les composants se trouvent-ils mutuellement ?                      |
| Identification   | Comment savent-ils précisément avec qui ils communiquent ?                 |
| Contrats         | Comment savent-ils ce qu'ils peuvent demander et recevoir ?                |
| Communication    | Comment échangent-ils concrètement ?                                       |
| Compatibilité    | Comment savent-ils qu'ils peuvent travailler ensemble ?                    |
| Sécurité         | Comment savent-ils qu'ils peuvent se faire confiance ?                     |
| Autorisation     | Comment savent-ils ce qu'ils ont le droit de faire ?                       |
| Gouvernance      | Comment les règles sont-elles appliquées ?                                 |
| Configuration    | Comment reçoivent-ils les informations nécessaires à leur fonctionnement ? |
| Évolution        | Comment changent-ils sans casser les autres ?                              |
| Résilience       | Comment continuent-ils à fonctionner lorsqu'un composant tombe ?           |
| Observabilité    | Comment sait-on ce qui se passe réellement ?                               |
| Interopérabilité | Comment travaillent-ils avec des technologies différentes ?                |
| Extensibilité    | Comment ajouter de nouvelles capacités sans modifier le Kernel ?           |
| Autonomie        | Comment fonctionnent-ils lorsque le Kernel est indisponible ?              |
| Exploitation     | Comment les administrer et les maintenir dans le temps ?                   |

# **33. Comment nous allons exploiter cet inventaire**

Je ne veux pas que nous répondions à ces 31 catégories dans le désordre. Il faut transformer ces questions en un véritable audit architectural du Kernel.

Je propose de conserver trois niveaux de qualification pour chaque question :

- **Couvert** : le Kernel actuel possède déjà un mécanisme permettant de satisfaire le besoin.
- **Partiellement couvert** : le mécanisme existe, mais il présente des limites ou ne couvre pas tous les cas.
- **Non couvert** : le besoin n'est pas actuellement satisfait.

Et surtout, il faudra distinguer quatre situations :

1. Le besoin relève directement du Kernel.
2. Le besoin relève d'un contrat ou d'une interface que le Kernel doit exposer.
3. Le besoin relève d'un composant externe ou d'un connecteur.
4. Le besoin ne relève pas d'EGEN et doit rester à la responsabilité du système concerné.

**Un point essentiel : ne cherchons pas à faire entrer toutes ces responsabilités dans le Kernel.** L'objectif est de déterminer ce qu'il doit garantir, ce qu'il doit rendre possible et ce qu'il doit simplement savoir intégrer.

Je commencerais l'audit par les catégories 1 à 8 : périmètre, nature des communications, protocoles, découverte, contrats, intégration native, intégration propriétaire et connecteurs. Elles permettront de définir les fondations de l'interopérabilité avant d'examiner la sécurité, la gouvernance, l'orchestration et l'exploitation.

C'est sur cette base que nous pourrons ensuite confronter chaque question au code réel d'EGEN Kernel, sans confondre ce qui est déjà implémenté, ce qui est seulement prévu dans la documentation et ce qui reste à concevoir.


















# EGEN — Architecture cible de fédération des Control Planes et d'orchestration des environnements autonomes

**Document d'orientation architecturale**

## 1. Contexte et évolution de la vision d'EGEN

EGEN a pour ambition de fournir un environnement capable de faire fonctionner, découvrir, connecter, administrer et orchestrer des services autonomes, en s'appuyant sur des technologies spécialisées plutôt qu'en cherchant à réimplémenter leurs fonctionnalités.

Cette ambition suppose de pouvoir travailler avec des environnements techniques très différents. Certains peuvent utiliser Nomad pour le déploiement des charges de travail, d'autres Kubernetes, tandis que d'autres encore peuvent s'appuyer sur des technologies différentes pour la découverte de services, la messagerie, la gestion des secrets, la configuration, l'observabilité ou le provisioning des ressources.

Il ne serait donc pas souhaitable de construire EGEN autour d'une technologie particulière, ni de faire dépendre son fonctionnement d'un ensemble fixe de moteurs. Les technologies évoluent, les infrastructures sont hétérogènes et les organisations n'ont pas nécessairement les mêmes besoins. EGEN doit pouvoir s'adapter à ces environnements sans remettre en cause ses fondations architecturales.

La réflexion sur Crossplane a permis de préciser davantage cette orientation. Il ne s'agit pas simplement d'ajouter un moteur supplémentaire au Kernel, ni de remplacer automatiquement EGEN par une autre plateforme. La direction retenue est plus générale : **EGEN doit pouvoir fédérer plusieurs Control Planes indépendants, chacun disposant de son propre fonctionnement interne, de son propre registre, de ses propres extensions et de son propre mécanisme de réconciliation.**

Cette évolution introduit une séparation fondamentale entre deux responsabilités :

- La fédération, qui permet de faire coopérer plusieurs Control Planes indépendants.
- Le contrôle local, qui permet à chaque Control Plane d'administrer son propre environnement et les ressources dont il est responsable.

Chaque Control Plane conserve ainsi son autonomie technique et opérationnelle. La fédération ne doit pas absorber leurs responsabilités internes, ni chercher à uniformiser leurs mécanismes de fonctionnement. Elle doit plutôt leur permettre de collaborer à travers des interfaces et des contrats communs.

Cette architecture doit également permettre de travailler avec des Control Planes qui ne sont pas nécessairement développés avec EGEN. Une plateforme externe telle que Crossplane doit pouvoir être intégrée à l'environnement fédéré sans devoir être transformée artificiellement en composant interne du Kernel.

L'objectif n'est donc plus de construire un Kernel unique qui contrôle directement l'ensemble des technologies et des services. Il est de construire une architecture dans laquelle plusieurs systèmes de contrôle autonomes peuvent être fédérés, tout en conservant leurs responsabilités, leurs technologies et leurs cycles de vie respectifs.

## 2. Les principes fondamentaux retenus

Les décisions prises pour cette architecture reposent sur trois principes structurants.

### 2.1. La réconciliation est une responsabilité déléguée

EGEN ne doit pas chercher à posséder systématiquement son propre moteur de réconciliation pour toutes les catégories de ressources qu'il peut administrer.

La réconciliation consiste notamment à observer l'état réel d'une ressource, à le comparer à l'état attendu, à identifier les écarts et à prendre les mesures nécessaires pour rétablir la situation souhaitée. Cette responsabilité est déjà assurée par des moteurs spécialisés, chacun ayant ses propres modèles de fonctionnement, ses propres contrôleurs et ses propres mécanismes de suivi.

EGEN doit donc privilégier la délégation à ces moteurs.

Cela signifie que le Kernel doit être capable d'exprimer une intention, de la transmettre au système compétent, de suivre son traitement et d'exploiter le résultat obtenu. Il ne doit pas nécessairement connaître toutes les opérations internes que le moteur exécute pour parvenir au résultat attendu.

Par exemple, lorsqu'un Control Plane demande le déploiement d'un service, il peut déléguer cette opération à Nomad. Le moteur prend alors en charge la programmation des tâches, leur exécution et le suivi de leur état selon ses propres mécanismes.

Dans un autre environnement, la même intention peut être prise en charge par Kubernetes ou par un système de provisioning utilisant Crossplane.

Le rôle d'EGEN est de rendre cette délégation possible, compréhensible et gouvernable, sans devenir lui-même un substitut aux moteurs spécialisés.

Il reste néanmoins nécessaire que chaque Control Plane puisse suivre les opérations qui lui sont confiées. Déléguer ne signifie pas perdre la visibilité sur les opérations, les résultats, les erreurs ou les responsabilités. Le Control Plane doit conserver une vision suffisante de ce qu'il a demandé, de ce qui a été exécuté et de l'état connu des ressources concernées.

### 2.2. L'indépendance vis-à-vis des technologies de déploiement

EGEN doit pouvoir travailler avec les moteurs de déploiement disponibles dans l'environnement qu'il administre.

Il ne doit pas imposer un moteur unique, ni supposer que tous les services sont exécutés de la même manière. Un environnement peut utiliser plusieurs technologies simultanément, chacune répondant à des besoins particuliers.

Un service peut être déployé dans un cluster Kubernetes, un autre dans un environnement Nomad, un troisième sur une infrastructure virtuelle et un quatrième être déjà hébergé sur une plateforme externe.

L'objectif est de permettre à un Control Plane de sélectionner ou de solliciter le moteur approprié en fonction des capacités disponibles, des contraintes d'exécution, des politiques applicables et des caractéristiques du service.

Cette indépendance ne concerne pas uniquement le déploiement. Elle doit également s'étendre aux autres fonctions techniques : découverte, communication, messagerie, gestion des secrets, configuration, observabilité et provisioning.

Les technologies utilisées doivent pouvoir évoluer sans imposer une réécriture de l'ensemble du système de contrôle.

Il faut donc distinguer les interfaces fonctionnelles exposées par EGEN des implémentations techniques qui les satisfont. Le contrat exprime ce qu'un système doit être capable de faire ; l'extension ou le fournisseur technique permet de réaliser effectivement cette opération avec une technologie donnée.

### 2.3. La fédération de Control Planes indépendants

Plusieurs Control Planes doivent pouvoir fonctionner simultanément, sans dépendre d'un registre central unique ni partager obligatoirement le même moteur de réconciliation.

Chaque Control Plane possède son propre périmètre de responsabilité et conserve la maîtrise de ses ressources.

Il peut avoir son propre registre, son propre catalogue d'extensions, ses propres politiques locales, son propre mécanisme de réconciliation et ses propres moteurs spécialisés.

La fédération permet de les faire collaborer sans fusionner leurs fonctionnements internes.

Un Control Plane peut donc être développé à partir du socle EGEN, tandis qu'un autre peut être construit avec Crossplane ou une autre technologie. La fédération doit pouvoir intégrer ces systèmes à condition qu'ils exposent les interfaces et les garanties nécessaires à leur interopérabilité.

Il ne s'agit pas de rendre tous les Control Planes identiques. Il s'agit de leur donner un langage commun et des mécanismes de coopération.

---

## 3. Comprendre les différents niveaux de l'architecture

Pour éviter les ambiguïtés, il faut distinguer plusieurs notions qui peuvent sembler proches, mais qui ne représentent pas la même responsabilité.

### 3.1. Le Federation Plane

Le Federation Plane constitue le niveau de coordination entre les différents Control Planes.

Il connaît les Control Planes qui participent à l'environnement fédéré, leurs identités, leurs capacités déclarées, leurs interfaces, leurs versions et les périmètres qu'ils administrent.

Il permet de les découvrir, de les contacter, de leur transmettre des demandes et de suivre les opérations qui nécessitent leur intervention.

Il ne doit cependant pas se substituer à leur administration locale.

Par exemple, si un Control Plane administre un ensemble de services sur Nomad, le Federation Plane n'a pas besoin de connaître les détails de chaque Job Nomad pour demander à ce Control Plane de déployer un service. Il doit connaître son identité, son interface, les capacités qu'il expose et les conditions dans lesquelles il accepte les demandes.

Le Federation Plane intervient donc à un niveau supérieur. Il organise la coopération entre les systèmes de contrôle, tandis que chaque système conserve la responsabilité de son propre environnement.

Ses responsabilités principales sont notamment :

- La connaissance et l'enregistrement des Control Planes fédérés.
- La découverte de leurs interfaces et de leurs capacités.
- La gestion de la confiance entre les différents Control Planes.
- Le routage des demandes vers le Control Plane compétent.
- La coordination des opérations impliquant plusieurs Control Planes.
- La gestion des politiques qui s'appliquent à l'échelle de la fédération.
- Le suivi global des opérations et des événements.
- La visibilité sur les relations et les dépendances entre les environnements fédérés.

Le Federation Plane ne doit pas devenir un registre global contenant l'intégralité des ressources de tous les Control Planes. Il doit plutôt disposer des informations nécessaires pour savoir qui administre quoi, quelles capacités sont disponibles et comment joindre le système responsable.

Cette distinction est essentielle pour préserver l'autonomie des environnements.

### 3.2. Le Control Plane

Un Control Plane est une unité autonome de contrôle et d'administration d'un environnement technique.

Il possède son propre périmètre de responsabilité. Il peut administrer des services, des applications, des infrastructures, des ressources techniques ou d'autres éléments selon les capacités qu'il met à disposition.

Il doit être capable de fonctionner indépendamment de la fédération. Sa capacité à administrer ses propres ressources ne doit pas dépendre de la disponibilité permanente du Federation Plane.

Un Control Plane doit donc posséder les mécanismes nécessaires pour :

- Exposer une interface de contrôle.
- Déclarer son identité et son périmètre.
- Connaître les ressources dont il est responsable.
- Maintenir son propre registre.
- Déterminer les capacités techniques disponibles.
- Utiliser ses extensions et moteurs spécialisés.
- Déléguer les opérations aux systèmes compétents.
- Suivre les états désirés et observés.
- Appliquer ses politiques locales.
- Publier les informations et événements utiles à la fédération.

Le Control Plane est ainsi responsable de la gestion locale. Il constitue le point de référence pour les opérations qui concernent son périmètre.

Il ne doit pas être obligé d'utiliser les mêmes technologies qu'un autre Control Plane. Deux Control Planes peuvent exposer des capacités comparables tout en ayant des architectures internes très différentes.

### 3.3. Le moteur de réconciliation

Le moteur de réconciliation est un composant spécialisé chargé de rapprocher l'état réel d'une ressource de son état attendu.

Il peut fonctionner à l'intérieur d'un Control Plane ou être fourni par une plateforme externe.

Dans l'architecture cible, le Control Plane doit pouvoir déléguer cette responsabilité à un moteur adapté à son environnement.

Il ne faut donc pas confondre le Control Plane avec son moteur de réconciliation. Le premier porte la responsabilité de contrôle et de gouvernance de son périmètre ; le second exécute une fonction spécialisée dans la gestion des états et des écarts.

Un Control Plane peut utiliser plusieurs mécanismes de réconciliation selon les types de ressources qu'il administre. Il peut également déléguer une partie de ses opérations à un autre Control Plane.

Crossplane représente ici un exemple important : il fournit un framework permettant de construire des Control Planes déclaratifs sur Kubernetes, avec des mécanismes de gestion et de réconciliation des ressources. Il peut donc être considéré comme un système de contrôle autonome dans un environnement fédéré, mais aussi comme une technologie à laquelle un autre système pourrait déléguer certaines responsabilités, si l'intégration prévue le permet.

### 3.4. Le moteur spécialisé

Un moteur spécialisé est une technologie qui assure une fonction technique particulière.

Il ne constitue pas nécessairement un Control Plane complet.

Par exemple :

- Nomad assure l'orchestration et le placement des charges de travail.
- Kubernetes fournit un environnement de gestion déclarative des ressources et des applications.
- Consul apporte des mécanismes de découverte et de connectivité des services.
- NATS et Kafka assurent des fonctions de messagerie et de diffusion d'événements.
- Vault fournit des mécanismes de gestion des secrets.
- OpenTelemetry participe à la collecte et à la transmission des données d'observabilité.

Ces moteurs peuvent être utilisés par un Control Plane pour réaliser certaines de ses responsabilités.

Un moteur peut être partagé par plusieurs Control Planes, lorsque son modèle de sécurité, son isolation et son organisation opérationnelle le permettent. Cette mutualisation doit toutefois être explicite : elle ne doit pas conduire à mélanger les registres, les politiques ou les responsabilités des Control Planes qui l'utilisent.

### 3.5. L'extension, le Provider et le Connector

L'extension représente le mécanisme permettant à un Control Plane d'acquérir une capacité technique supplémentaire.

Elle peut fournir une intégration avec un moteur, un service externe, un protocole ou une plateforme.

Le terme Provider est notamment utilisé dans l'écosystème Crossplane pour désigner des extensions qui exposent des ressources externes à travers des API Kubernetes et mettent en œuvre leur gestion.

Le Connector, ou Bridge, désigne plutôt un composant chargé d'assurer la communication et la traduction entre deux systèmes qui ne partagent pas nativement les mêmes interfaces.

Ces notions ne doivent pas être confondues. Une extension peut fournir une capacité native à un Control Plane ; un Provider peut mettre en œuvre une intégration dans le modèle de Crossplane ; un Connector peut rendre interopérables deux systèmes qui ne savent pas communiquer directement.

L'architecture EGEN doit pouvoir accueillir ces différents mécanismes sans imposer une seule forme d'intégration.

---

## 4. Architecture générale cible

L'architecture cible s'organise autour de trois niveaux logiques : la fédération, les Control Planes autonomes et les moteurs ou services spécialisés.

```text
                         ENVIRONNEMENT FÉDÉRÉ EGEN
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│                       FEDERATION PLANE                           │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐  │
│  │ Control Plane    │  │ Capability       │  │ Federation     │  │
│  │ Registry         │  │ Discovery        │  │ Router         │  │
│  └──────────────────┘  └──────────────────┘  └────────────────┘  │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐  │
│  │ Trust & Identity │  │ Federation       │  │ Global         │  │
│  │                  │  │ Policies         │  │ Observability  │  │
│  └──────────────────┘  └──────────────────┘  └────────────────┘  │
│                                                                  │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                 Interfaces et contrats de fédération
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
          ▼                    ▼                    ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ CONTROL PLANE A  │  │ CONTROL PLANE B  │  │ CONTROL PLANE C  │
│                  │  │                  │  │                  │
│ API locale       │  │ API locale       │  │ API locale       │
│ Registre local   │  │ Registre local   │  │ Registre local   │
│ Politiques       │  │ Politiques       │  │ Politiques       │
│ Extensions       │  │ Extensions       │  │ Extensions       │
│ Réconciliation  │  │ Réconciliation  │  │ Réconciliation  │
│                  │  │                  │  │                  │
└────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘
         │                     │                     │
         ▼                     ▼                     ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ Moteurs          │  │ Moteurs          │  │ Control Plane   │
│ spécialisés      │  │ spécialisés      │  │ externe          │
│                  │  │                  │  │                  │
│ Nomad            │  │ Kubernetes       │  │ Crossplane       │
│ Consul           │  │ NATS             │  │ Providers        │
│ Vault            │  │ Vault            │  │ Kubernetes       │
│ Autres           │  │ Autres           │  │ Autres           │
└──────────────────┘  └──────────────────┘  └──────────────────┘
```

Ce schéma présente une organisation logique et non une obligation de déploiement physique. Le Federation Plane peut lui-même être distribué, répliqué ou organisé hiérarchiquement. Les Control Planes peuvent se trouver sur des machines, des réseaux, des infrastructures ou des sites géographiques différents.

La fédération ne doit pas imposer que tous les composants soient installés sur le même cluster, dans le même réseau ou dans le même environnement d'exécution.

Il faut également remarquer que le Control Plane C représente ici une plateforme externe intégrée à la fédération. Il n'est pas nécessaire qu'elle utilise le runtime EGEN pour participer à l'environnement fédéré.

---

## 5. Le Federation Plane : fonctionnement et responsabilités

### 5.1. Le registre des Control Planes

Le Federation Plane doit disposer d'un mécanisme lui permettant de connaître les Control Planes qui participent à l'environnement fédéré.

Ce registre ne constitue pas un inventaire détaillé de toutes les ressources administrées. Il contient avant tout les informations nécessaires à l'identification, à la découverte et à la communication avec chaque Control Plane.

Pour chaque Control Plane, il doit notamment être possible de connaître :

- Son identifiant unique et son nom.
- Son type et sa nature.
- Son propriétaire ou son organisation responsable.
- Son périmètre de responsabilité.
- Son environnement d'exécution.
- Ses points d'accès et interfaces disponibles.
- Les versions des contrats qu'il prend en charge.
- Les capacités qu'il déclare.
- Son état de disponibilité connu.
- Ses relations avec les autres Control Planes.
- Les informations nécessaires à l'établissement d'une relation de confiance.

Un Control Plane doit pouvoir rejoindre la fédération, déclarer ses informations, mettre à jour ses capacités et signaler son retrait.

Le registre doit également pouvoir gérer les situations dans lesquelles un Control Plane devient temporairement inaccessible. Il faut distinguer son absence de réponse, son indisponibilité réelle, son retrait volontaire et une éventuelle perte de confiance.

Une information de disponibilité ne doit jamais être considérée comme une garantie absolue. Le Federation Plane peut connaître le dernier état communiqué par un Control Plane, mais seul ce dernier peut confirmer précisément l'état de ses ressources locales.

### 5.2. La découverte des capacités

Connaître l'existence d'un Control Plane ne suffit pas. Il faut également savoir ce qu'il est capable de faire.

La découverte des capacités doit permettre d'identifier les fonctions réellement exposées par chaque Control Plane.

Par exemple, un Control Plane peut déclarer qu'il est capable de :

- Déployer des applications.
- Gérer des charges de travail.
- Provisionner des bases de données.
- Administrer des ressources Kubernetes.
- Gérer des configurations.
- Exposer des services.
- Administrer des ressources réseau.
- Superviser des composants.
- Gérer des workflows.
- Fournir des mécanismes de découverte.

Cette déclaration doit être structurée et suffisamment précise pour permettre à un autre composant de comprendre ce qui est réellement disponible.

Il ne faut pas se limiter à une liste de noms de technologies. Deux Control Planes qui utilisent Kubernetes ne proposent pas nécessairement les mêmes capacités, les mêmes versions, les mêmes autorisations ou les mêmes garanties.

Il faut donc distinguer la technologie utilisée, la capacité fonctionnelle exposée, les opérations acceptées et les contraintes associées.

La découverte doit aussi prendre en compte les versions et les éventuelles limitations. Une capacité peut être disponible, mais uniquement dans certaines conditions ou avec certaines options.

### 5.3. Le routage des demandes

Le Federation Plane doit pouvoir déterminer à quel Control Plane adresser une demande.

Cette décision ne doit pas être fondée uniquement sur un nom de technologie ou sur la proximité réseau. Elle doit prendre en compte le périmètre de responsabilité, les capacités annoncées, les politiques applicables et les relations établies entre les Control Planes.

Lorsqu'une demande concerne une ressource déjà administrée par un Control Plane, elle doit normalement être dirigée vers ce dernier.

Lorsqu'une demande nécessite une capacité particulière, la fédération doit pouvoir identifier les Control Planes qui la proposent et vérifier lequel est autorisé à intervenir.

La fédération doit également éviter les situations dans lesquelles plusieurs Control Planes pensent être responsables de la même ressource.

Le routage ne signifie donc pas simplement transmettre une requête HTTP à une autre adresse. Il s'agit de diriger une intention vers l'autorité compétente, en conservant les informations nécessaires à sa traçabilité et à son suivi.

### 5.4. La coordination des opérations globales

Certaines opérations peuvent nécessiter l'intervention de plusieurs Control Planes.

Par exemple, une application peut dépendre d'une base de données administrée par un premier Control Plane, d'un environnement d'exécution géré par un deuxième et d'un service de communication administré par un troisième.

La fédération doit pouvoir coordonner ces différentes interventions.

Elle ne doit cependant pas imposer une transaction distribuée unique à tous les systèmes. Les Control Planes peuvent avoir des modèles de transaction, des mécanismes de réconciliation et des temporalités très différents.

La coordination doit donc être conçue pour gérer des opérations asynchrones, des étapes successives, des dépendances, des délais, des échecs partiels et des compensations lorsque cela est nécessaire.

Il faut notamment pouvoir distinguer :

- Une opération acceptée par un Control Plane.
- Une opération en cours d'exécution.
- Une opération terminée avec succès.
- Une opération échouée.
- Une opération partiellement réalisée.
- Une opération en attente d'une dépendance.
- Une opération annulée ou compensée.

Le Federation Plane suit le processus global, tandis que chaque Control Plane reste responsable de l'exécution et de l'état local de ses propres opérations.

---

## 6. Le Control Plane : autonomie et fonctionnement interne

### 6.1. Un environnement de contrôle indépendant

Chaque Control Plane doit être considéré comme un système autonome, et non comme une simple extension du Federation Plane.

Il doit pouvoir être installé, configuré, démarré, arrêté, mis à jour et exploité indépendamment des autres Control Planes.

Son fonctionnement local ne doit pas nécessiter une connexion permanente à la fédération.

Cette indépendance est importante pour plusieurs raisons. Un environnement peut être isolé pour des raisons de sécurité, se trouver sur un réseau privé, être soumis à des contraintes de disponibilité ou simplement fonctionner dans une infrastructure éloignée.

La fédération doit donc pouvoir disparaître temporairement sans empêcher les Control Planes de continuer à administrer leurs ressources locales.

Pendant une interruption, un Control Plane doit conserver son registre local, poursuivre les opérations qu'il est capable de réaliser et appliquer ses politiques locales. Il doit également pouvoir conserver les événements et les informations nécessaires à leur transmission ultérieure.

Lorsque la connexion est rétablie, il peut reprendre les échanges avec la fédération, communiquer les changements intervenus et réconcilier les informations globales qui doivent être actualisées.

### 6.2. Le registre local

Chaque Control Plane doit posséder son propre registre, qui constitue sa référence locale pour les ressources qu'il administre.

Ce registre peut contenir les informations sur les services, applications, ressources, configurations, relations, dépendances, opérations et états suivis dans son périmètre.

Le registre local doit rester sous la responsabilité du Control Plane concerné.

Il ne doit pas être remplacé par un registre central de fédération. Il ne doit pas non plus être nécessairement partagé avec les autres Control Planes.

Cette séparation permet d'éviter qu'une indisponibilité du Federation Plane bloque l'accès aux informations locales ou que plusieurs systèmes modifient simultanément le même état sans autorité clairement définie.

La fédération peut recevoir des informations synthétiques sur les ressources et les capacités disponibles. Elle ne devient pas pour autant propriétaire des données détaillées.

Il faut donc établir une distinction entre la connaissance globale d'une ressource et l'autorité qui en administre effectivement le cycle de vie.

### 6.3. Le système d'extensions local

Chaque Control Plane doit disposer de son propre système d'extensions.

Les extensions permettent d'ajouter ou de remplacer des capacités sans obliger à modifier l'ensemble du système de contrôle.

Un Control Plane peut ainsi installer une extension de déploiement pour Nomad, une autre pour Kubernetes, une extension de découverte pour Consul, une intégration de messagerie pour NATS ou Kafka, ou encore une extension de gestion des secrets.

Un autre Control Plane peut utiliser un ensemble complètement différent.

Le système d'extensions doit permettre de connaître les extensions installées, leurs versions, leurs capacités, leurs dépendances, leur état et leur compatibilité avec la version du Control Plane.

Il doit également permettre de gérer leur installation, leur activation, leur désactivation, leur mise à jour et leur retrait.

La fédération peut connaître les capacités rendues disponibles par ces extensions, mais elle ne doit pas nécessairement administrer directement leur installation.

Cette responsabilité appartient au Control Plane local, selon les politiques et les mécanismes prévus pour son environnement.

### 6.4. Le contrôle local et les politiques

Chaque Control Plane doit conserver ses propres règles de fonctionnement.

Il peut définir les ressources qu'il accepte d'administrer, les opérations autorisées, les moteurs disponibles, les limites d'utilisation, les conditions de déploiement et les restrictions applicables à son environnement.

La fédération peut imposer certaines règles globales, mais elle ne doit pas supprimer la capacité des Control Planes à appliquer leurs propres politiques.

Une demande autorisée à l'échelle globale peut être refusée localement si elle ne respecte pas les règles du Control Plane responsable.

Inversement, un Control Plane ne doit pas pouvoir ignorer les contraintes globales qui lui sont applicables.

Il faut donc prévoir une articulation claire entre les politiques de fédération et les politiques locales.

---

## 7. Les moteurs spécialisés et leur mutualisation

L'indépendance des Control Planes ne signifie pas qu'il faut installer un ensemble complet de moteurs dans chacun d'eux.

Plusieurs Control Planes peuvent utiliser les mêmes moteurs spécialisés, lorsque cela est pertinent et techniquement possible.

Par exemple, plusieurs Control Planes peuvent utiliser un même cluster Kubernetes, une même infrastructure de messagerie ou un même service de gestion des secrets.

Cette mutualisation peut réduire les coûts d'exploitation, éviter la multiplication des infrastructures et faciliter la gestion de certaines ressources communes.

Elle doit toutefois respecter les frontières de responsabilité.

Le fait que deux Control Planes utilisent le même moteur ne signifie pas qu'ils doivent partager le même registre local ou administrer les mêmes ressources.

Il faut donc prévoir des mécanismes permettant de distinguer les périmètres, les identités, les autorisations et les ressources de chaque utilisateur du moteur.

La mutualisation doit être un choix d'exploitation, et non une conséquence automatique de l'architecture.

### 7.1. Différence entre moteur partagé et Control Plane partagé

Il faut particulièrement éviter de confondre deux situations.

Dans la première, plusieurs Control Planes utilisent un même moteur spécialisé. Chacun conserve son registre, ses politiques et ses responsabilités, mais délègue certaines opérations à une infrastructure commune.

Dans la deuxième, plusieurs utilisateurs travaillent sur un même Control Plane. Ils partagent alors le système de contrôle lui-même, selon les règles d'isolation et de gouvernance qu'il propose.

Ce ne sont pas les mêmes modèles.

La fédération doit pouvoir fonctionner avec plusieurs Control Planes indépendants, tandis que la mutualisation des moteurs doit être traitée séparément.

### 7.2. La sélection des moteurs

Un Control Plane doit pouvoir déterminer quel moteur utiliser pour une opération.

Cette décision peut dépendre des capacités disponibles, du type de ressource, des contraintes techniques, des politiques locales, des ressources accessibles et des conditions d'exploitation.

Il faut également prévoir la possibilité de disposer de plusieurs moteurs capables d'assurer une même fonction.

Par exemple, si plusieurs moteurs de déploiement sont disponibles, le Control Plane doit pouvoir choisir celui qui répond aux contraintes de l'opération.

Cette sélection ne doit pas être codée de manière à rendre le système dépendant d'une technologie unique.

L'objectif est de pouvoir ajouter de nouvelles implémentations sans remettre en cause les contrats fonctionnels qui décrivent la capacité de déploiement.

---

## 8. Crossplane dans l'architecture cible

Crossplane doit être considéré comme une technologie importante dans cette architecture, mais il ne doit pas devenir une dépendance obligatoire de l'ensemble d'EGEN.

Il faut comprendre son positionnement.

Crossplane est un framework de construction de Control Planes basé sur Kubernetes. Il permet notamment de définir des API déclaratives, de composer des ressources et de confier leur réconciliation à des Providers et à des contrôleurs spécialisés.

Il est donc possible d'envisager plusieurs formes de coopération avec EGEN.

### 8.1. Crossplane comme Control Plane fédéré

Dans ce scénario, Crossplane constitue un système de contrôle indépendant.

Il possède son propre environnement Kubernetes, ses propres ressources déclaratives, ses propres Providers et ses propres mécanismes de réconciliation.

EGEN ne cherche pas à remplacer ces éléments. Il fédère Crossplane à travers une interface d'intégration.

La fédération doit alors être capable de connaître les capacités exposées par Crossplane, de lui transmettre les demandes appropriées et de suivre les opérations qui en résultent.

Crossplane conserve la responsabilité de la réconciliation de ses propres ressources.

### 8.2. Crossplane comme moteur utilisé par un Control Plane

Une autre possibilité consiste à utiliser Crossplane comme technologie spécialisée au service d'un Control Plane.

Dans ce cas, le Control Plane conserve son propre périmètre de responsabilité et utilise les interfaces de Crossplane pour certaines opérations de provisioning et de réconciliation.

Il faut alors définir précisément les ressources confiées à Crossplane, les opérations qu'il doit exécuter et les informations que le Control Plane doit récupérer.

Cette possibilité ne doit pas être confondue avec la précédente. Dans le premier scénario, Crossplane est un Control Plane participant à la fédération. Dans le deuxième, il intervient comme moteur spécialisé utilisé par un autre système de contrôle.

### 8.3. Préserver l'indépendance technologique

L'intégration de Crossplane ne doit pas conduire à imposer Kubernetes comme environnement obligatoire pour tous les Control Planes.

Un Control Plane doit pouvoir utiliser Crossplane lorsque cela est pertinent, mais un autre doit pouvoir continuer à fonctionner avec Nomad ou d'autres technologies.

Les contrats de fédération EGEN doivent donc rester indépendants des modèles internes de Crossplane et des API spécifiques de Kubernetes.

Cette indépendance est indispensable pour conserver la possibilité d'intégrer d'autres plateformes à l'avenir.

---

## 9. Les contrats et interfaces communs

La fédération ne peut fonctionner proprement que si les Control Planes disposent d'un langage commun pour échanger.

Il ne suffit pas que chaque système expose une API. Les systèmes doivent être capables de comprendre la nature des demandes, les informations échangées, les états retournés et les garanties associées.

Les contrats constituent donc un élément central de l'architecture.

Ils doivent définir ce qu'un Control Plane doit pouvoir annoncer, recevoir et communiquer pour participer à la fédération.

### 9.1. Contrat d'identité et de description

Chaque Control Plane doit pouvoir présenter une identité stable.

Cette identité doit permettre de le distinguer des autres systèmes, de connaître son propriétaire, son périmètre, son environnement et les informations nécessaires à l'établissement de la confiance.

Elle doit également permettre d'identifier la version de ses interfaces et de ses contrats.

### 9.2. Contrat de découverte et de capacités

Un Control Plane doit pouvoir déclarer les capacités qu'il expose.

Cette déclaration doit être suffisamment structurée pour permettre à la fédération de déterminer si une opération peut lui être confiée.

Elle doit inclure les versions, les contraintes, les options et les éventuelles limitations des capacités annoncées.

### 9.3. Contrat de délégation

Le contrat de délégation décrit la manière dont un Control Plane reçoit une demande provenant d'un autre système.

Il doit préciser les informations nécessaires à l'opération, les conditions d'acceptation, les identifiants de suivi, les états possibles et les modalités de récupération du résultat.

Il doit également permettre de gérer les demandes refusées, les erreurs, les délais et les opérations qui nécessitent plusieurs étapes.

### 9.4. Contrat d'état et de résultat

Un Control Plane doit pouvoir communiquer les informations nécessaires au suivi d'une opération.

Il faut distinguer l'état de la demande, l'état de l'opération et l'état de la ressource concernée.

Une demande peut être acceptée alors que l'opération n'est pas encore terminée. Une opération peut être terminée alors que la ressource n'est pas dans l'état attendu. Ces situations doivent pouvoir être représentées sans ambiguïté.

### 9.5. Contrat d'événements

Les Control Planes doivent pouvoir communiquer les événements importants concernant leur fonctionnement et les opérations réalisées.

Ces événements peuvent signaler une nouvelle capacité, une modification d'état, une opération terminée, un changement de disponibilité, une erreur ou un incident.

Le mécanisme de transport peut varier selon l'environnement. L'important est de définir un format et une sémantique d'échange suffisamment communs pour permettre l'interopérabilité.

### 9.6. Versionnement et compatibilité

Les contrats doivent être versionnés.

Un Control Plane ne doit pas perdre automatiquement sa capacité à participer à la fédération parce qu'un autre Control Plane a évolué.

Il faut donc prévoir la compatibilité entre versions, les mécanismes de négociation, les périodes de transition et les règles de dépréciation.

L'objectif est que l'évolution d'un composant ne provoque pas une rupture générale de l'environnement fédéré.

---

## 10. Intégrer des Control Planes externes et des solutions propriétaires

L'architecture ne doit pas se limiter aux systèmes développés par CIVITAS ou aux technologies dont le code source est librement accessible.

Dans un environnement d'entreprise, il est courant d'utiliser des solutions propriétaires qui répondent très bien à certains besoins, mais dont le code source ne peut pas être modifié.

Il faut donc prévoir plusieurs modes d'intégration.

### 10.1. Intégration native

Lorsqu'un système est développé par CIVITAS ou qu'il est possible d'en modifier le code source, il peut implémenter directement les contrats de fédération EGEN.

Il peut alors annoncer ses capacités, établir une relation de confiance et communiquer avec les autres composants sans passer par une couche de traduction spécifique.

C'est le mode d'intégration le plus direct.

### 10.2. Intégration par extension ou Provider

Lorsqu'une technologie dispose d'un mécanisme d'extension, d'un système de Providers ou d'une API adaptée, il est possible d'utiliser ce mécanisme pour la connecter à EGEN.

Cette intégration doit respecter le modèle technique de la solution concernée, tout en exposant les capacités nécessaires à la fédération.

Il n'est pas nécessaire que toutes les technologies adoptent le même système d'extensions.

### 10.3. Intégration par Connector ou Bridge

Lorsqu'une solution ne peut pas implémenter directement les contrats EGEN, un composant intermédiaire peut assurer la traduction.

Ce composant communique avec la solution à travers les interfaces qu'elle propose, puis expose les informations et les opérations attendues par EGEN.

Il peut s'agir d'une API REST, d'une API GraphQL, de webhooks, d'un protocole de messagerie, d'une interface LDAP, d'un mécanisme OIDC ou SAML, d'une CLI ou d'un autre point d'intégration documenté.

Le Connector doit prendre en charge les différences de modèle, de format, d'authentification et de communication.

Il ne doit cependant pas inventer des capacités que le système externe ne possède pas.

Si une solution ne propose aucun point d'intégration exploitable, une intégration complète et fiable ne peut pas être garantie. L'automatisation de son interface graphique peut parfois fournir une solution limitée, mais elle ne remplace pas une véritable interface d'intégration.

### 10.4. Préserver les politiques de gouvernance

L'intégration d'une solution externe ne doit pas créer un chemin permettant de contourner les politiques de la fédération ou du Control Plane responsable.

Lorsqu'une opération passe par un Connector, les contrôles nécessaires doivent être appliqués avant son exécution.

Il faut notamment préserver l'identité de l'appelant, les autorisations, la traçabilité, les limites d'utilisation et les règles de sécurité.

La solution externe peut conserver ses propres mécanismes d'autorisation. EGEN doit toutefois pouvoir garantir que les opérations réalisées à travers son environnement respectent les règles applicables.

---

## 11. La communication entre les différents niveaux

La communication est une responsabilité transversale de l'architecture. Elle ne doit pas être réduite à un seul protocole ou à un seul système de messagerie.

Les échanges peuvent être synchrones ou asynchrones, directs ou indirects, locaux ou fédérés.

### 11.1. Communication synchrone

Elle permet à un composant de soumettre une demande et d'obtenir immédiatement une réponse sur sa réception ou son traitement initial.

Elle est adaptée aux opérations de consultation, à la découverte, à la récupération d'informations et aux demandes qui peuvent être traitées rapidement.

Il faut toutefois éviter de considérer qu'une réponse positive signifie nécessairement que toute l'opération demandée est terminée.

### 11.2. Communication asynchrone

Certaines opérations peuvent durer plusieurs secondes, plusieurs minutes ou davantage.

Dans ce cas, le système doit pouvoir accepter la demande, retourner un identifiant de suivi et communiquer les changements d'état au fur et à mesure.

Les événements et la messagerie permettent alors de transmettre les résultats sans maintenir une connexion permanente entre les deux systèmes.

Cette approche est particulièrement importante pour les opérations de provisioning, de déploiement et de réconciliation.

### 11.3. Communication directe entre Control Planes

La fédération ne doit pas nécessairement imposer que toutes les communications transitent par un serveur central.

Lorsque les politiques et les relations de confiance le permettent, deux Control Planes peuvent communiquer directement.

Le Federation Plane peut intervenir pour découvrir les systèmes, établir les relations nécessaires et orienter les demandes, sans devenir un intermédiaire obligatoire pour chaque échange.

Cette possibilité permet de réduire les dépendances et d'améliorer la résilience.

### 11.4. Communication indirecte et événements

Dans certains environnements, les Control Planes peuvent échanger à travers un système de messagerie commun ou des mécanismes de diffusion d'événements.

Cela peut être utile lorsque les systèmes sont séparés par des réseaux, lorsqu'ils doivent fonctionner de manière asynchrone ou lorsqu'ils ne peuvent pas maintenir de connexions directes.

Le mécanisme de transport doit rester indépendant du contrat d'échange.

Ainsi, une même catégorie d'événement doit pouvoir être transmise par différents systèmes de messagerie, selon les capacités de l'environnement.

---

## 12. Sécurité, identité et confiance entre Control Planes

La fédération introduit une relation de confiance entre plusieurs systèmes autonomes. Cette relation doit être explicitement gérée.

Un Control Plane ne doit pas accepter automatiquement les demandes d'un autre simplement parce que celui-ci est connu du registre de fédération.

Il faut distinguer l'identification, l'authentification, l'autorisation et la confiance.

L'identification permet de savoir quel système communique. L'authentification permet de vérifier qu'il est bien celui qu'il prétend être. L'autorisation détermine les opérations qu'il peut effectuer. La confiance définit les conditions dans lesquelles les échanges avec ce système sont acceptés.

Chaque Control Plane doit conserver la possibilité de définir ses propres règles locales, tout en respectant les exigences globales applicables.

La sécurité doit couvrir notamment :

- L'identité des Control Planes.
- L'identité des services et des utilisateurs.
- L'authentification des communications.
- La protection des échanges réseau.
- La gestion des certificats et des secrets.
- Les autorisations par capacité et par opération.
- La délégation contrôlée des privilèges.
- La révocation d'une relation de confiance.
- La traçabilité des actions.
- L'isolation entre les périmètres administrés.

Il faut également prévoir les cas dans lesquels un Control Plane est compromis ou ne respecte plus les conditions de confiance. La fédération doit pouvoir suspendre ou révoquer sa participation sans devoir arrêter l'ensemble des autres Control Planes.

---

## 13. Gestion des opérations distribuées et des dépendances

L'un des enjeux les plus importants de cette architecture est la gestion des opérations qui traversent plusieurs périmètres autonomes.

Prenons l'exemple d'une application qui nécessite :

- Une infrastructure d'exécution.
- Une base de données.
- Un stockage.
- Un service de découverte.
- Un système de communication.
- Des configurations et des secrets.

Ces ressources peuvent être administrées par plusieurs Control Planes.

Le Federation Plane doit pouvoir organiser les différentes demandes dans un ordre cohérent, en tenant compte des dépendances.

Il doit également pouvoir suivre l'opération dans son ensemble, même si chaque étape est exécutée par un système différent.

Il ne faut toutefois pas supposer que tous les Control Planes sont capables de participer à une transaction distribuée commune.

Une opération globale peut réussir partiellement. Une ressource peut être créée alors qu'une autre étape échoue. Une dépendance peut devenir indisponible après la validation d'une première étape.

L'architecture doit donc prévoir des mécanismes de compensation, de reprise, d'idempotence et de suivi des opérations.

La responsabilité doit rester claire : chaque Control Plane est responsable de ses opérations locales ; la fédération est responsable de la coordination globale lorsqu'une demande traverse plusieurs périmètres.

Il faut également empêcher que deux systèmes essaient de réconcilier indépendamment la même ressource sans accord explicite.

Chaque ressource administrée doit avoir une autorité clairement identifiée. Les autres systèmes peuvent la consulter ou demander des opérations à son propriétaire, mais ne doivent pas devenir des contrôleurs concurrents.

---

## 14. Résilience et fonctionnement en environnement distribué

Une fédération composée de plusieurs Control Planes doit être conçue pour résister aux indisponibilités partielles.

La panne d'un Control Plane ne doit pas empêcher les autres de fonctionner. La perte temporaire de la connexion au Federation Plane ne doit pas automatiquement arrêter les opérations locales.

Il faut donc prévoir des mécanismes adaptés aux environnements distribués :

- Files d'attente et reprise des demandes.
- Gestion des délais et des tentatives.
- Identifiants uniques des opérations.
- Détection des doublons.
- Idempotence des commandes.
- Conservation temporaire des événements.
- Reprise après interruption.
- Gestion des états inconnus.
- Détection des relations de confiance invalides.
- Réconciliation des informations après reconnexion.

Il faut surtout éviter de considérer qu'une absence de réponse signifie qu'une opération a échoué.

Une opération peut avoir été exécutée par le système distant sans que la réponse ait pu revenir. Le système appelant doit alors être capable de retrouver son état à partir de son identifiant de suivi, plutôt que de relancer aveuglément la même opération.

Cette distinction est particulièrement importante lorsque les opérations ont des conséquences sur des ressources réelles ou coûteuses.

---

## 15. Observabilité, supervision et audit

L'architecture doit permettre d'observer les Control Planes individuellement et la fédération dans son ensemble.

La supervision locale doit permettre à chaque Control Plane de connaître l'état de ses composants, de ses extensions, de ses moteurs spécialisés et des opérations qu'il administre.

La supervision globale doit permettre d'avoir une vue sur les différents Control Planes fédérés, leurs disponibilités, leurs capacités, leurs relations et les opérations distribuées.

Il faut pouvoir suivre une opération depuis sa demande initiale jusqu'à son exécution finale, même lorsqu'elle traverse plusieurs systèmes.

Cela nécessite de conserver des identifiants de corrélation et des informations de traçabilité communes.

Les journaux, les métriques, les traces distribuées et les événements doivent permettre de comprendre :

- Quel système a initié une opération.
- Quel Control Plane en est responsable.
- Quels autres systèmes ont participé.
- Quel moteur spécialisé a été sollicité.
- Quelles étapes ont été réalisées.
- Où une erreur s'est produite.
- Quel résultat a été obtenu.
- Quelles politiques ont été appliquées.

La fédération ne doit pas nécessairement centraliser toutes les données d'observabilité. Elle doit plutôt permettre de les corréler et de les consulter selon les droits et les mécanismes disponibles.

L'audit doit également préserver la provenance des informations. Il doit être possible de distinguer un événement produit par un Control Plane, une information transmise par un Connector et une donnée calculée par le Federation Plane.

---

## 16. Organisation distribuée et hiérarchique de la fédération

Il ne faut pas considérer que le Federation Plane doit obligatoirement prendre la forme d'un serveur central unique.

La fédération peut être organisée selon plusieurs modèles.

Dans un modèle centralisé, un niveau de fédération connaît et coordonne l'ensemble des Control Planes.

Dans un modèle distribué, plusieurs composants de fédération peuvent coopérer, chacun ayant une connaissance partielle ou complète de l'environnement.

Dans un modèle hiérarchique, un Federation Plane peut fédérer plusieurs sous-fédérations, qui fédèrent elles-mêmes leurs propres Control Planes.

Cette dernière possibilité est importante pour les environnements de grande taille.

Elle permet notamment de représenter des organisations qui possèdent plusieurs infrastructures, plusieurs régions, plusieurs entités ou plusieurs périmètres d'administration.

La fédération doit donc pouvoir fonctionner à plusieurs échelles, sans supposer que tous les Control Planes appartiennent à une seule infrastructure ou à une seule organisation.

Il faut également distinguer les relations de fédération des relations de dépendance. Deux Control Planes peuvent participer à la même fédération sans dépendre l'un de l'autre. À l'inverse, un Control Plane peut avoir besoin d'un service administré par un autre sans que cela signifie qu'ils partagent la même autorité de contrôle.

---

## 17. Cycle de vie des Control Planes et auto-administration

Chaque Control Plane doit disposer d'un cycle de vie indépendant.

Il doit être possible de l'installer, de le configurer, de l'enregistrer auprès de la fédération, de mettre à jour ses extensions, de modifier ses capacités, de le suspendre et de le retirer.

La fédération doit connaître les changements importants qui affectent les systèmes participants.

Il faut aussi prévoir les opérations de remplacement ou de migration d'un Control Plane. Le retrait d'un système ne doit pas entraîner automatiquement la perte des ressources qu'il administrait.

Il faut notamment déterminer comment les ressources sont transférées, comment les responsabilités sont réattribuées et comment les nouvelles autorités de contrôle reprennent les opérations.

L'auto-administration doit également être prévue.

Un service d'administration peut être déployé et administré par un Control Plane, puis utiliser les interfaces de ce même environnement pour gérer son fonctionnement. Il peut également être utilisé pour administrer plusieurs Control Planes à travers la fédération.

Cela permet de construire des interfaces Web, des outils CLI, des services d'administration, des agents autonomes ou des assistants d'exploitation sans les intégrer directement au cœur du système de contrôle.

Cependant, le fonctionnement du Control Plane ne doit jamais dépendre exclusivement du service qui l'administre. Il doit toujours exister un mécanisme minimal d'accès et de récupération indépendant, afin de pouvoir intervenir lorsque le service d'administration est indisponible.

---

## 18. Les interfaces d'administration et les agents autonomes

L'architecture doit permettre à différents types de consommateurs d'utiliser les capacités des Control Planes.

Il peut s'agir d'interfaces Web, d'applications mobiles, d'outils CLI, de SDK, de systèmes d'automatisation, de plateformes externes ou d'agents d'intelligence artificielle.

Ces consommateurs ne doivent pas avoir besoin de connaître l'implémentation interne de chaque Control Plane.

Ils doivent pouvoir utiliser les interfaces exposées et les contrats prévus à cet effet.

Un outil d'administration peut ainsi présenter une vue globale de la fédération, afficher les Control Planes disponibles, consulter leurs capacités et permettre de soumettre des opérations.

Un agent IA peut, de son côté, découvrir les capacités accessibles, comprendre les opérations disponibles et solliciter les systèmes compétents selon les permissions qui lui sont accordées.

Il ne doit toutefois pas disposer d'un accès direct et incontrôlé aux moteurs spécialisés.

Les demandes doivent passer par les interfaces et les mécanismes de gouvernance appropriés. Cela permet de conserver la traçabilité, les autorisations et les politiques applicables, quel que soit le type de consommateur.

Cette approche évite également de rendre les interfaces d'administration dépendantes d'une technologie particulière.

---

## 19. Les garanties architecturales attendues

À terme, l'architecture doit garantir les propriétés suivantes.

### Indépendance des Control Planes

Chaque Control Plane peut fonctionner, évoluer et être administré indépendamment des autres.

### Indépendance des moteurs

Aucun moteur de déploiement, de réconciliation, de découverte ou de communication ne doit être imposé à tous les environnements.

### Délégation des responsabilités spécialisées

Les fonctions déjà assurées par des moteurs spécialisés doivent pouvoir leur être confiées à travers des interfaces appropriées.

### Interopérabilité

Des Control Planes développés avec des technologies différentes doivent pouvoir échanger à travers des contrats communs.

### Extensibilité

Il doit être possible d'ajouter de nouvelles capacités, de nouveaux moteurs, de nouveaux Connectors et de nouvelles plateformes sans remettre en cause l'ensemble de l'architecture.

### Autonomie locale

Un Control Plane doit continuer à administrer son périmètre même en cas d'indisponibilité temporaire de la fédération.

### Gouvernance

Les opérations doivent respecter les politiques globales et locales, avec une autorité clairement définie pour chaque ressource.

### Traçabilité

Les demandes, les délégations, les événements et les opérations doivent pouvoir être suivis à travers l'ensemble de la fédération.

### Évolutivité

L'architecture doit pouvoir accueillir de nouveaux Control Planes et de nouvelles technologies sans exiger une refonte de ses fondations.

### Compatibilité externe

Il doit être possible d'intégrer des systèmes qui ne sont pas développés par CIVITAS, y compris des plateformes propriétaires, lorsque celles-ci offrent des mécanismes d'intégration exploitables.

---

## 20. Scénarios de fonctionnement représentatifs

Les scénarios suivants permettent de vérifier concrètement que l'architecture répond aux besoins attendus.

### Scénario A — Plusieurs Control Planes avec des moteurs différents

Un environnement fédéré possède trois Control Planes.

Le premier utilise Nomad pour les déploiements et Consul pour la découverte des services. Le deuxième utilise Kubernetes et Crossplane. Le troisième administre une infrastructure externe avec ses propres outils.

Un consommateur soumet une demande de déploiement.

La fédération identifie le Control Plane compétent à partir de son périmètre et de ses capacités. Elle lui transmet la demande. Le Control Plane sollicité utilise son propre mécanisme de réconciliation et ses moteurs spécialisés. Il communique ensuite l'évolution de l'opération et son résultat.

Le consommateur n'a pas besoin de connaître les détails techniques du moteur utilisé.

### Scénario B — Une application dépend de plusieurs environnements

Une application nécessite une base de données administrée par le Control Plane A, un environnement d'exécution administré par le Control Plane B et un service de communication géré par le Control Plane C.

La fédération identifie les différents responsables et coordonne les demandes dans l'ordre nécessaire.

Chaque Control Plane reste responsable de ses propres ressources. La fédération suit l'ensemble du processus et gère les dépendances entre les opérations.

### Scénario C — Ajout d'une nouvelle technologie de déploiement

Un nouveau moteur de déploiement devient disponible dans un environnement.

Il doit être possible de l'intégrer à un Control Plane à travers une extension compatible avec ses contrats.

Cette intégration ne doit pas obliger à modifier les autres Control Planes ni les contrats globaux de fédération, tant que la capacité exposée reste compatible.

### Scénario D — Intégration d'une plateforme externe

Une organisation utilise une plateforme de contrôle qui n'a pas été développée avec EGEN.

Cette plateforme dispose de ses propres API et de ses propres mécanismes d'administration.

Un Connector ou une intégration appropriée expose les informations et les opérations nécessaires à la fédération. La plateforme conserve son fonctionnement interne et sa responsabilité sur ses ressources.

Elle peut ainsi participer à l'environnement fédéré sans être réécrite.

### Scénario E — Indisponibilité du Federation Plane

Le Federation Plane devient temporairement inaccessible.

Les Control Planes continuent à administrer leurs propres environnements. Ils conservent les informations et les événements nécessaires au suivi des opérations.

Les nouvelles opérations locales restent possibles selon les politiques de chaque Control Plane. Les opérations nécessitant une coordination globale peuvent être mises en attente ou refusées selon les garanties disponibles.

Lorsque la fédération revient, les échanges reprennent et les informations globales sont actualisées.

### Scénario F — Administration d'un Control Plane par un service autonome

Un Control Plane démarre et expose ses interfaces de contrôle.

Il déploie ensuite un service d'administration qui découvre les interfaces disponibles et se connecte au Control Plane.

Ce service peut fournir une interface Web, une CLI, des API ou des capacités d'administration assistées par IA.

Le service reste indépendant du Control Plane. Il peut être remplacé ou mis à jour sans modifier le cœur du système de contrôle.

### Scénario G — Mutualisation d'un moteur

Deux Control Planes utilisent le même moteur spécialisé.

Ils conservent chacun leur registre et leurs politiques. Le moteur partagé exécute les opérations qui lui sont confiées dans les périmètres autorisés.

Les ressources, les identités et les responsabilités doivent rester distinctes. La mutualisation du moteur ne doit pas créer une confusion sur l'autorité qui administre chaque ressource.

---

## 21. Ce que cette architecture ne doit pas devenir

Certaines dérives doivent être évitées dès la conception.

**Un Kernel central qui connaît et contrôle tout.** La fédération ne doit pas devenir un système monolithique qui concentre tous les registres, tous les états et toutes les opérations.

**Une copie globale des registres locaux.** Le Federation Plane ne doit pas devenir la source de vérité de toutes les ressources. Il conserve une vue de fédération, tandis que les Control Planes gardent la maîtrise de leurs données locales.

**Un moteur universel qui remplace tous les moteurs spécialisés.** EGEN doit déléguer les responsabilités spécialisées plutôt que chercher à tout réimplémenter.

**Un système dépendant d'une technologie unique.** Ni Kubernetes, ni Nomad, ni Crossplane, ni une autre technologie ne doit devenir une condition obligatoire de participation à l'architecture fédérée.

**Un catalogue d'adaptateurs propriétaires dans le cœur du Kernel.** Les intégrations doivent être extensibles et isolées du socle. Le cœur doit s'appuyer sur des contrats stables et des mécanismes génériques.

**Une fédération qui impose le même fonctionnement à tous.** Les Control Planes doivent pouvoir conserver leurs modèles de contrôle, leurs moteurs et leurs règles locales, tant qu'ils respectent les contrats et les garanties nécessaires à leur participation.

**Une communication sans gouvernance.** Le fait qu'un système puisse techniquement contacter un autre ne signifie pas qu'il est autorisé à lui demander n'importe quelle opération.

---

## 22. Les grandes responsabilités à distinguer

Pour résumer l'organisation fonctionnelle, voici la répartition attendue.

| Responsabilité | Niveau principalement responsable |
|---|---|
| Connaître les Control Planes participants | Federation Plane |
| Découvrir les capacités globalement disponibles | Federation Plane |
| Établir les relations de confiance fédérées | Federation Plane et Control Planes |
| Orienter une demande vers le bon responsable | Federation Plane |
| Coordonner une opération multi-Control Planes | Federation Plane |
| Connaître les ressources locales | Control Plane concerné |
| Administrer les ressources locales | Control Plane concerné |
| Définir les politiques locales | Control Plane concerné |
| Choisir et solliciter les moteurs spécialisés | Control Plane concerné |
| Installer et gérer les extensions locales | Control Plane concerné |
| Réconcilier les ressources | Moteur délégué par le Control Plane |
| Exécuter les tâches et déploiements | Moteur spécialisé concerné |
| Fournir les API et protocoles propres à un moteur | Technologie concernée |
| Traduire les interfaces incompatibles | Connector ou Bridge |
| Fournir les vues et outils d'administration | Services consommateurs de la fédération |

Cette répartition doit servir de référence pour éviter les chevauchements de responsabilités lors de la conception des composants.

---

## 23. Résultat architectural recherché

L'objectif final est de disposer d'un environnement dans lequel plusieurs systèmes de contrôle autonomes peuvent fonctionner ensemble, sans être obligés de partager la même architecture interne ou les mêmes technologies.

EGEN doit fournir les fondations nécessaires à cette coopération : les contrats, la découverte, la confiance, la communication, la délégation, la coordination et la gouvernance de la fédération.

Chaque Control Plane conserve ensuite sa propre autonomie. Il possède son registre, ses extensions, ses politiques et son mécanisme de réconciliation. Il choisit les moteurs spécialisés adaptés à son environnement et reste responsable des ressources qu'il administre.

Certains Control Planes pourront être construits avec le socle EGEN. D'autres pourront utiliser Crossplane ou des plateformes externes. Les environnements pourront utiliser des moteurs différents, partager certaines infrastructures ou fonctionner sur des réseaux totalement indépendants.

La fédération doit rendre ces différences compatibles sans chercher à les effacer.

Il faut donc concevoir EGEN comme une **architecture de fédération de systèmes de contrôle autonomes, fondée sur des contrats communs et sur la délégation des responsabilités spécialisées**.

Le véritable enjeu n'est pas de faire d'EGEN un moteur capable de tout administrer lui-même. Il est de lui permettre de comprendre les capacités disponibles, de déterminer quel système est compétent, de lui confier les opérations appropriées et de coordonner les résultats à l'échelle de l'environnement fédéré.

C'est cette séparation entre autonomie locale et coopération globale qui doit guider toutes les décisions architecturales à venir.

---

## 24. Questions structurantes à résoudre avant la conception détaillée

Ce document fixe la direction et les résultats recherchés. Il ne prétend pas encore définir tous les mécanismes techniques nécessaires à leur réalisation. Plusieurs sujets devront être étudiés précisément avant de figer les contrats et la structure des composants.

### A. Fédération et topologie

1. Le Federation Plane doit-il être centralisé, distribué ou capable de fonctionner selon plusieurs topologies ?
2. Un Control Plane peut-il participer à plusieurs fédérations simultanément ?
3. Une fédération peut-elle elle-même être membre d'une autre fédération ?
4. Comment gérer les relations entre Control Planes qui ne sont pas directement connectés ?
5. Comment représenter les frontières organisationnelles et administratives ?

### B. Identité et confiance

6. Comment un Control Plane rejoint-il une fédération ?
7. Qui autorise son inscription ?
8. Comment vérifier son identité et l'authenticité de ses déclarations ?
9. Comment révoquer sa participation ?
10. Comment gérer les certificats, les clés et les identités entre environnements autonomes ?

### C. Contrats et interopérabilité

11. Quel est le modèle commun de description d'un Control Plane ?
12. Comment décrire une capacité de manière indépendante de sa technologie ?
13. Comment représenter les opérations, leurs paramètres, leurs contraintes et leurs résultats ?
14. Comment négocier les versions des contrats ?
15. Comment intégrer un système qui ne prend pas nativement en charge les contrats EGEN ?

### D. Délégation et réconciliation

16. Comment un Control Plane détermine-t-il le moteur compétent pour une opération ?
17. Peut-il déléguer une même catégorie d'opérations à plusieurs moteurs ?
18. Comment suivre une opération après sa délégation ?
19. Comment détecter les pertes de synchronisation entre les états locaux et ceux des moteurs ?
20. Comment éviter que plusieurs Control Planes réconcilient la même ressource ?

### E. Extensions et moteurs

21. Quel format commun doivent respecter les extensions ?
22. Comment installer et mettre à jour une extension sans reconstruire le Control Plane ?
23. Comment gérer les dépendances entre extensions ?
24. Comment permettre à plusieurs Control Planes de mutualiser un moteur ?
25. Comment gérer les versions, la compatibilité et les changements d'API des moteurs ?

### F. Opérations distribuées

26. Comment coordonner une opération qui implique plusieurs Control Planes ?
27. Comment gérer les échecs partiels ?
28. Comment reprendre une opération après une interruption ?
29. Comment assurer l'idempotence des demandes ?
30. Comment gérer les dépendances et les compensations ?

### G. Gouvernance et sécurité

31. Comment articuler les politiques globales et locales ?
32. Comment déléguer des autorisations sans donner des privilèges excessifs ?
33. Comment garantir qu'un Connector ne contourne pas les politiques ?
34. Comment tracer les actions d'un agent IA à travers plusieurs Control Planes ?
35. Comment suspendre rapidement un système compromis ?

### H. Résilience et exploitation

36. Que se passe-t-il lorsqu'un Control Plane est inaccessible ?
37. Que se passe-t-il lorsque le Federation Plane est indisponible ?
38. Comment synchroniser les informations après une reconnexion ?
39. Comment superviser les opérations de bout en bout ?
40. Comment migrer les ressources lorsqu'un Control Plane est remplacé ?

Ces questions constituent le point de départ de la conception détaillée. Elles devront être étudiées avant de décider précisément des modules, des interfaces, des protocoles et des mécanismes internes à implémenter.
