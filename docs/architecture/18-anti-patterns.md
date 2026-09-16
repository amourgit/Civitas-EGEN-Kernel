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
