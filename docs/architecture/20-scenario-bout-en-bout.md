# 20 — Scénario de bout en bout — walkthrough complet

Ce scénario canonique — déployer `news-service` en 2.4.0, avec ses
dépendances, le rendre découvrable, connecter ses événements, maintenir
son état désiré — détaille **exactement** ce que fait le Kernel, appel par
appel. Rejoué **littéralement** comme test de niveau 5 (voir
[17](17-strategie-de-tests.md#niveau-5--tests-de-bout-en-bout-et-de-non-régression-architecturale))
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
   → vérifie configuration.required : chaque clé a une valeur d'environnement ou un défaut, du type déclaré
   → Condition ConfigResolved=True ; sinon le service reste REGISTERED et la Condition dit pourquoi (voir 09)

3. COMPOSE
   Le planificateur construit le plan d'exécution :
   a) DeploymentPort.create(spec) — car dependencies required sont déjà RUNNING, pas de blocage d'ordonnancement
   b) DiscoveryPort.registerHealthCheck (sera appelé après confirmation du déploiement)
   c) MessagingPort.createTopicOrStream : un flux par type d'événement publié OU consommé, dont
      "africa.civitas.news.article.created.v1" (si absent) — Condition MessagingBindingsReady=True ;
      le déploiement (a) n'est demandé qu'une fois les flux garantis (voir 12, « Flux d'événements »)

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
   → Conditions ManifestValid, ConfigResolved, DependenciesSatisfied, MessagingBindingsReady,
     DeploymentReady, DiscoveryReady toutes à True (sémantique et `reason` : voir 09)
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

## Partie 2 — Workflow : publier un article, avec compensation volontaire

La partie 1 montre le Kernel *maintenir un état désiré*. Cette partie montre
le Moteur de Workflow *orchestrer plusieurs services à travers un échec*
([11](11-moteur-de-workflow.md)). Le workflow est celui de référence,
`publish-article-workflow`, déclaré **tel quel** en YAML (voir le doc 11).
`news-service` et `notification-service` sont supposés `RUNNING` (partie 1).

```
9. DEFINE
   Client ──POST /api/v1/workflows  (Content-Type: application/yaml, Idempotency-Key: …)──▶ EGEN API
   rôle administrateur requis (voir 14) ; WorkflowMapper valide la forme (durées s/m/h, kind),
   WorkflowDefinition valide le graphe (étapes inconnues, cycles) ; persistée ;
   répond 202 Accepted + Location: /api/v1/workflows/publish-article-workflow

10. RUN
   Client ──POST /api/v1/workflows/publish-article-workflow/executions
              {"input": {"articleId": "42"}}──▶ EGEN API
   RunWorkflowUseCase charge la définition, WorkflowEngine crée l'exécution (RUNNING, entrée
   persistée) et la pilote sur un thread virtuel ; répond 202 + Location: …/executions/{id}

11. EXECUTE (cas nominal)
   Dans l'ordre topologique, pour chaque étape HTTP :
   DiscoveryPort.resolve(service) → circuit breaker de la cible (voir 12) → ServiceInvocationPort.invoke
   → un StepResult final par tentative est journalisé (clé unique exécution/étape/tentative) :
     validate-content        ✔  POST news-service /internal/validate
     reserve-notification-slot ✔ POST notification-service /internal/reserve-slot
     publish                 ✔  POST news-service /internal/publish
     notify-subscribers      ✔  EVENT africa.civitas.news.article.published.v1
                                 (CloudEvent corrélé à l'exécution, data = l'entrée en JSON)
   → exécution COMPLETED

12. COMPENSATE (échec volontaire de l'étape intermédiaire `publish`)
   POST news-service /internal/publish répond 500
   → l'étape n'a pas de retry (maxAttempts 1) : échec DÉFINITIF, journalisé dans la MÊME sauvegarde
     que le passage en COMPENSATING (jamais un échec visible sans son effet sur le statut)
   → compensations des SEULES étapes réussies qui en déclarent une, en ordre INVERSE :
       reserve-notification-slot  →  POST notification-service /internal/release-slot  ✔
       validate-content           →  (aucune compensation déclarée : ignorée)
     `publish`, qui a échoué, n'est PAS compensée (son effet partiel est l'affaire du service) ;
     `notify-subscribers` n'a jamais été lancée : aucun événement ne part.
   → exécution COMPENSATED

13. CRASH DU KERNEL PENDANT L'EXÉCUTION (durabilité)
   Le Kernel s'arrête après `validate-content` (RUNNING, un résultat journalisé, l'entrée persistée).
   Au redémarrage, recoverInterruptedExecutions() relit l'exécution depuis le store :
   `validate-content` n'est PAS rejouée, les étapes restantes s'exécutent avec l'entrée d'origine.

14. CLIENT — À TOUT MOMENT
   GET /api/v1/workflows/publish-article-workflow/executions/{id}
   → { status: "COMPENSATED", input: {...}, stepResults: [ …un résultat final par tentative… ] }
```

Commandes de l'environnement complet (à exécuter contre un Kernel démarré
avec Nomad, Consul, NATS, PostgreSQL et Vault ; `$TOKEN` = jeton du rôle
administrateur) :

```bash
curl -si -X POST localhost:8080/api/v1/workflows \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/yaml" --data-binary @publish-article-workflow.yaml
curl -si -X POST localhost:8080/api/v1/workflows/publish-article-workflow/executions \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $(uuidgen)" \
  -d '{"input": {"articleId": "42"}}'
curl -s localhost:8080/api/v1/workflows/publish-article-workflow/executions/$EXECUTION_ID \
  -H "Authorization: Bearer $TOKEN"
```

**Test automatisé de cette partie** : `PublishArticleWorkflowScenarioIT`
(`egen-bootstrap`). Vrai moteur, vrai adapter HTTP, vrai PostgreSQL
(Testcontainers) et de vrais serveurs HTTP de fixtures ; le YAML du doc 11
y est parsé tel quel. Il rejoue les étapes 11, 12 et 13 et vérifie l'ordre
exact des appels. Il n'inclut pas Nomad, Consul ni NATS : la découverte est
en mémoire et le messaging est un enregistreur — le rejeu complet, avec
`curl`, reste le test de niveau 5 de l'environnement complet
([17](17-strategie-de-tests.md#niveau-5--tests-de-bout-en-bout-et-de-non-régression-architecturale)).
