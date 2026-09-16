# 20 — Scénario de bout en bout — walkthrough complet

Ce scénario canonique — déployer `news-service` en 2.4.0, avec ses
dépendances, le rendre découvrable, connecter ses événements, maintenir
son état désiré — détaille **exactement** ce que fait le Kernel, appel par
appel. Rejoué **littéralement** comme test de niveau 5 (voir
[17](17-strategie-de-tests.md#niveau-5--tests-de-bout-en-bout-et-de-non-regression-architecturale))
avec de vraies commandes `curl` contre un environnement Testcontainers, il
constitue le test d'acceptation le plus important du projet — celui qui
doit figurer en premier dans la CI et dans toute démonstration à des
parties prenantes non techniques.

```
1. DECLARE
   Client ──POST /api/v1/services (ServiceManifest news-service:2.4.0)──▶ EGEN API
   EGEN valide le schéma + les invariants (voir 06.3), persiste un DesiredState
   generation=1, répond 202 Accepted + Location: /api/v1/services/news-service/status

2. RESOLVE (premier cycle de réconciliation, déclenché par l'enqueue post-Declare)
   ReconciliationEngine charge DesiredState(news-service, gen=1)
   → résout dependencies.services: notification-service (>=1.2.0), storage-service (>=3.0.0)
   → interroge RegistryStorePort : les deux sont RUNNING en versions compatibles ✅
   → résout dependencies.resources: primary-db, cache (référencées, pas gérées en V1 — hors périmètre Deployment Adapter)
   → résout configuration.secretsRefs via SecretsPort (Vault) → obtient une référence de montage, pas la valeur

3. COMPOSE
   Le planificateur construit le plan d'exécution :
   a) DeploymentPort.create(spec) — car dependencies required sont déjà RUNNING, pas de blocage d'ordonnancement
   b) DiscoveryPort.registerHealthCheck (sera appelé après confirmation du déploiement)
   c) MessagingPort.createTopicOrStream pour "africa.civitas.news.article.created.v1" (si absent)

4. DELEGATE (avec operationId=op-8841, propagé dans tous les appels et logs)
   → PUT /v1/jobs (Nomad) : job "news-service" créé, resources 500m CPU / 512Mi RAM, replicas min=2
   → statut : DEPLOYING

5. OBSERVE (cycles suivants de la boucle, toutes les few secondes tant que non convergé)
   → GET /v1/job/news-service/allocations (Nomad) : 2 allocations "running", tâches saines
   → EGEN appelle alors DiscoveryPort.register() pour chaque instance saine détectée
   → PUT /v1/agent/service/register (Consul) avec Check HTTP sur /health, interval 10s
   → GET /v1/health/service/news-service?passing=true (Consul) : 2 instances "passing"

6. RECONCILE
   DeploymentObservation.healthyCount (2) >= desiredCount (2) ET DiscoveryObservation confirme 2 instances saines
   → Phase passe de DEPLOYING à RUNNING
   → Condition DeploymentReady=True, DiscoveryReady=True, DependenciesSatisfied=True
   → observedGeneration=1 (= generation du DesiredState traité)
   → Événement de réconciliation émis (trace OpenTelemetry complète, du POST initial jusqu'à ce statut)

7. RÉGIME PERMANENT
   Toutes les 10s (health check) et à chaque resync périodique (5-10 min), le cycle se répète.
   Si une allocation Nomad meurt : le cycle suivant observe healthyCount=1 < desiredCount=2
   → Phase passe à DEGRADED, Condition DeploymentReady=False (reason: AllocationLost)
   → Nomad lui-même retente le scheduling (c'est SON travail) ; EGEN observe la reconvergence
   → dès que healthyCount revient à 2 : Phase repasse à RUNNING automatiquement, sans intervention humaine

8. CLIENT — À TOUT MOMENT
   GET /api/v1/services/news-service/status
   → { phase: "RUNNING", observedGeneration: 1, conditions: [...], instances: 2 }
```
