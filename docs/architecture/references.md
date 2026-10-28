# Références et sources d'inspiration

À consulter par l'équipe, jamais à intégrer comme dépendances runtime du
cœur (voir la règle d'or dans [le sommaire](README.md)).

- **Nomad** — documentation API HTTP officielle : `https://developer.hashicorp.com/nomad/api-docs`
- **Consul** — catalogue et service discovery : `https://developer.hashicorp.com/consul/docs/concept/catalog`
- **NATS JetStream** — documentation officielle : `https://docs.nats.io/nats-concepts/jetstream`
- **Apache Kafka** — documentation officielle : `https://kafka.apache.org/documentation/`
- **CloudEvents (CNCF, spécification graduée)** — `https://cloudevents.io` et `https://github.com/cloudevents/spec`
- **Dapr (building blocks, workflow, resiliency policies)** — `https://docs.dapr.io`
- **KubeVela / Open Application Model** — `https://kubevela.io`
- **Crossplane (composition, réconciliation continue)** — `https://docs.crossplane.io`
- **Score (spécification de workload portable)** — `https://score.dev` et `https://github.com/score-spec/spec`
- **OpenTelemetry (traces/métriques/logs, conventions sémantiques)** — `https://opentelemetry.io`
- **Kubernetes controller pattern (réconciliation, informers, work queues)** — documentation `controller-runtime` et le pattern Operator en général
- **Saga pattern** — `https://microservices.io/patterns/data/saga.html`
- **Hexagonal Architecture (Alistair Cockburn, Ports & Adapters)** — article original et littérature dérivée sur la distinction ports primaires/secondaires
- **Keycloak (OIDC, identity brokering, protocol mappers)** — `https://www.keycloak.org/documentation`
- **OpenFGA (autorisation relationnelle, modèles, conditions)** — `https://openfga.dev/docs`
- **PostgreSQL `ltree` (chemins hiérarchiques)** — `https://www.postgresql.org/docs/current/ltree.html`
- **JSON Schema 2020-12** — `https://json-schema.org/specification`
- **OAuth 2.0 Token Exchange (RFC 8693)** — `https://www.rfc-editor.org/rfc/rfc8693`
- **SCIM 2.0 (RFC 7644)** — `https://www.rfc-editor.org/rfc/rfc7644`
- **CalDAV (RFC 4791)** — `https://www.rfc-editor.org/rfc/rfc4791`
- **Radicale, Nextcloud Calendar, Odoo** — documentation officielle de chaque projet (à consulter pour établir leurs profils de connecteur, sans présumer de leurs capacités : elles sont mesurées par la suite de tests, [26.6](26-contrats-de-domaine-et-connecteurs.md#266--suite-de-tests-de-contrat-domain-contract-test-kit))
