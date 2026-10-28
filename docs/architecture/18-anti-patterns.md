# 18 — Anti-patterns à bannir explicitement

Cette liste est citée en revue de code chaque fois qu'un des symptômes
apparaît.

| Anti-pattern | Symptôme observable dans le code | Correction |
|---|---|---|
| **Le Kernel devient un second Nomad** | Une classe `SchedulingEngine` ou `PlacementResolver` apparaît dans `egen-domain` ou `egen-application`. | Supprimer, déléguer entièrement à `DeploymentPort` → Nomad. |
| **Le Registry devient un second Consul** | Le Registry EGEN expose une résolution d'instance en temps réel avec health check, dupliquant `/v1/health/service`. | Le Registry ne stocke QUE le désiré + un cache d'observé pour l'API de statut — jamais la source de vérité de disponibilité en temps réel. |
| **Réconciliation edge-triggered** | Le code réagit au *contenu* d'un événement webhook plutôt que de re-lire l'état complet. | Revenir au modèle « la clé suffit, on relit tout » (voir [04.1](04-moteur-de-reconciliation.md#level-triggered-pas-edge-triggered)). |
| **Appel synchrone bloquant dans l'API publique** | `POST /services` attend la fin du déploiement Nomad avant de répondre. | Retourner `202 Accepted` immédiatement, laisser la boucle converger. |
| **God class d'orchestration** | Une classe unique de plusieurs milliers de lignes qui connaît Nomad, Consul ET Kafka simultanément. | Un port = un besoin, un adapter = une technologie, aucune classe ne doit connaître plus d'un SDK externe. |
| **Fuite de type externe dans le domaine** | Une signature de méthode dans `egen-domain` prend en paramètre un type du SDK Nomad (`com.hashicorp.nomad.javasdk.Job` ou équivalent). | Interdire au niveau du build (voir [17, niveau 4](17-strategie-de-tests.md#niveau-4--tests-darchitecture-fitness-functions)), pas seulement en revue. |
| **Opération non idempotente déléguée** | Un `create()` d'adapter échoue en erreur bloquante si la ressource existe déjà, au lieu de faire un upsert. | Revoir chaque méthode d'adapter selon la checklist de [04.3](04-moteur-de-reconciliation.md#idempotence--condition-de-survie-de-la-boucle). |
| **Secret transporté en clair dans le manifeste** | Un `ServiceManifest` contient une valeur de mot de passe en dur. | Toujours une `SecretReference`, jamais une valeur (voir [06.2](06-service-manifest.md#ce-que-le-manifeste-ne-contient-jamais), [07](07-ports-et-adapters.md#secrets-port)). |
| **Workflow avec logique métier embarquée** | Une `WorkflowDefinition` contient une branche conditionnelle basée sur une règle métier (`if articleCategory == "sport"`). | Cette décision doit être prise par le service, exposée comme une opération distincte ou un événement distinct — le workflow orchestre des appels, il ne décide pas du métier. |
| **Sur-ingénierie anticipée** | Implémentation d'un mécanisme de circuit-breaking maison alors qu'aucun besoin réel ne l'a encore justifié. | Toujours vérifier d'abord si le moteur spécialisé le fait déjà (garde-fou n°3, voir [02](02-principes-fondamentaux.md)). |
| **Une cellule dans le Kernel** | Une classe, un champ ou un paquet `Cell`/`Tenant`/`Organization`/`Assignment`/`Catalog` dans `egen-domain`, `egen-application` ou `egen-api`. | Supprimer ; le concept vit dans `cell-registry-service`/`catalog-service` ([22](22-ecosysteme-multi-solutions-et-frontiere.md)). Règle A4 en CI. |
| **Le Kernel interprète un label réservé** | Une décision, un routage ou un RBAC dépend de `platform.civitas.africa/*`. | Les labels sont informatifs ; la décision appartient au catalogue ([06](06-service-manifest.md#labels-réservés-posés-par-la-plateforme)). |
| **Le catalogue appelle le Kernel** | `catalog-service` ou `config-service` détient un jeton du Kernel. | Seul `platform-operator-service` parle au Kernel (D14). |
| **Droits de cellule dans le jeton** | Des rôles ou appartenances sont embarqués dans un JWT. | Calcul à l'appel ([25](25-identite-et-autorisations.md)). |
| **Deuxième implémentation du contrôle d'usage** | Une gateway ou le portail recalcule « peut utiliser ». | Appeler `check-use` du catalogue (D13). |
| **Synchronisation implicite entre instances** | Deux instances d'un même domaine échangent des données sans règle. | Règle d'échange explicite ([26.7](26-contrats-de-domaine-et-connecteurs.md#267--données--aucune-synchronisation-implicite-d18)). |
| **Remplacement présenté comme transparent** | Une substitution d'application sans rapport de différences de capacités. | Rapport obligatoire, procédure de la phase H ([29.11](29-plan-par-phase-ecosysteme.md#2911--phase-h--échanges-explicites-remplacement-et-migration)). |
| **Secret en valeur dans la configuration** | Une clé `x-egen-secret-ref` contient une valeur. | Références uniquement ([27.3](27-configuration-multi-niveaux.md#273--schéma-des-clés-obligatoire)). |
