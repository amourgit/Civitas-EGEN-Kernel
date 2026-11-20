# Références

## À quoi sert ce document
Lister les sources externes sur lesquelles reposent les faits cités dans la documentation, avec leur nature, leur date de
consultation et ce qu'on en a retenu.

## Ce qu'il ne couvre pas
Les décisions (voir [02](02-principes-et-decisions.md)). Une source ne décide de rien : elle étaye un fait.

## Lecture de ce tableau

Toutes les sources ont été consultées le **2026-11-19**. « Secondaire » = article de presse, blog ou comparatif : à
**recouper sur la page officielle du projet** avant décision finale (RK-08, VR-19). « Primaire » = annonce de la fondation ou
du projet.

| Id | Source | Adresse | Fait retenu | Nature | Cité dans |
|---|---|---|---|---|---|
| S-01 | CNCF — annonce de la diplomation de Crossplane (6 novembre 2025) | https://www.cncf.io/announcements/2025/11/06/cloud-native-computing-foundation-announces-graduation-of-crossplane | Crossplane est « diplômé » (niveau de maturité le plus élevé) ; statut au 28 octobre 2025 ; la version 2.0 introduit une architecture affinée pour des plans de contrôle d'applications complets | Primaire | [moteurs/crossplane.md](moteurs/crossplane.md) |
| S-04 | CNCF — annonce de la diplomation de Karmada (7 septembre 2026) | https://www.cncf.io/news/2026/09/07/cloud-native-computing-foundation-announces-karmada-graduation/ | Karmada (gestion multi-clusters à hub central) est diplômé de la CNCF | Primaire | [16](16-risques-et-verifications.md) (VR-14) |
| S-05 | CNCF — présentation du projet kcp | https://presentations.cncf.io/projects/kcp/ | kcp est au niveau « Sandbox » de la CNCF | Primaire | [16](16-risques-et-verifications.md) (VR-14) |
| S-06 | SPIFFE — spécification de la fédération | https://spiffe.io/docs/latest/spiffe-specs/spiffe_federation/ | La fédération consiste à échanger les *trust bundles* entre domaines de confiance ; elle porte sur la confiance dans l'émetteur, pas sur l'autorisation de l'appelant | Primaire | [07](07-identite-confiance-securite.md), [moteurs/identite-spire-keycloak-spicedb.md](moteurs/identite-spire-keycloak-spicedb.md) |
| S-08 | Dapr — annonce de la version 1.17 ; article Wikipédia de Dapr | https://blog.dapr.io/posts/2026/02/27/dapr-v1.17-is-now-available/ ; https://en.wikipedia.org/wiki/Dapr | Dapr est diplômé de la CNCF (novembre 2024) ; la 1.17 apporte le versionnement de workflows | Primaire et secondaire | [08](08-moteurs-specialises.md) (PO-07) |
| S-09 | CNCF — accord CNCF/Synadia sur NATS (1er mai 2025) ; réponse de Synadia | https://www.cncf.io/announcements/2025/05/01/cncf-and-synadia-align-on-securing-the-future-of-the-nats-io-project/ ; https://www.synadia.com/blog/synadia-response-to-cncf | NATS reste sous Apache 2.0 dans la CNCF ; Synadia affirme avoir financé environ 97 % des contributions au serveur (chiffre de Synadia) | Primaire | [moteurs/messagerie-nats.md](moteurs/messagerie-nats.md) |
| S-10 | Analyses du passage de HashiCorp à la Business Source License | https://dev.to/vivian-voss/the-terms-you-did-not-sign-hashicorps-bsl-opentofu-and-ibm-36g5 ; https://opensourcelicenserisk.pages.dev/hashicorp-terraform/ | Le 10 août 2023, Terraform, Vault, Consul et Nomad passent de MPL-2.0 à la BSL 1.1, licence non reconnue par l'OSI qui restreint l'usage concurrent en production | Secondaire | [moteurs/nomad.md](moteurs/nomad.md), [16](16-risques-et-verifications.md) (RK-03, VR-08) |
| S-11 | Finalisation du rachat de HashiCorp par IBM ; lancement du fork OpenBao | https://www.theregister.com/2025/02/28/ibm_hashicorp_deal_closing/ ; https://openbao.org (pour OpenBao : URL de l'article source à rétablir, VR-19) | IBM a finalisé le rachat le 27 février 2025 ; OpenBao est un fork de Vault sous la Linux Foundation lancé en décembre 2023 | Secondaire | [moteurs/nomad.md](moteurs/nomad.md), [moteurs/secrets-openbao.md](moteurs/secrets-openbao.md) |
| S-13 | Comparatifs Temporal / Dapr Workflow / Argo Workflows | URL des pages secondaires à rétablir (VR-19) ; source officielle : https://temporal.io | Temporal : licence MIT, usage en production dans de grandes entreprises ; demande un cluster dédié ; versionnement de workflows plus robuste que Dapr Workflow ; courbe d'apprentissage plus lourde | Secondaire | [moteurs/temporal.md](moteurs/temporal.md) |
| S-14 | Fiche de Kratix (annuaire d'ingénierie de plateforme) | https://platformengineering.org/tools/kratix | Kratix : licence Apache 2.0, développé par Syntasso, pas (encore) un projet de la CNCF | Secondaire | [moteurs/crossplane.md](moteurs/crossplane.md) |
| S-15 | Articles sur MCP et A2A à l'Agentic AI Foundation | https://www.techzine.eu/news/devops/143659/google-transfers-a2a-to-the-agentic-ai-foundation/ ; https://www.flowhunt.io/blog/agentic-ai-foundation-a2a-mcp-standards/ | MCP a été confié à l'Agentic AI Foundation (Linux Foundation) le 9 décembre 2025 ; A2A est devenu projet hébergé de la même fondation le 17 août 2026 | Secondaire | [11](11-ecosystemes-et-integrations.md) |
| S-16 | Blog de Crossplane — « Announcing Crossplane 2.0 » | https://blog.crossplane.io/announcing-crossplane-2-0 | Compositions pouvant inclure n'importe quelle ressource Kubernetes ; ressources composites et gérées à portée d'espace de noms par défaut ; définitions de ressources gérées et politiques d'activation ; nouveau type `Operation` | Primaire | [moteurs/crossplane.md](moteurs/crossplane.md) |
| S-17 | Documentation de Crossplane — « Operations » | https://docs.crossplane.io/v2.3/operations/operation/ ; https://docs.crossplane.io/v2.0/whats-new | `Operation` (groupe `ops.crossplane.io/v1alpha1`) : exécution d'une chaîne de fonctions jusqu'à son terme ; fonctionnalité alpha ; option `--enable-operations` | Primaire | [moteurs/crossplane.md](moteurs/crossplane.md), [04](04-contrats-egen.md) |
| S-18 | Versions de la spécification CloudEvents | https://www.github.com/cloudevents/spec/releases | Dernière version 1.0.2 ; extension « Distributed Tracing » (`traceparent`) présente depuis la 1.0 | Primaire | [04](04-contrats-egen.md) |
| S-19 | Versions de la spécification AsyncAPI | https://www.github.com/asyncapi/spec/releases ; https://www.asyncapi.com/blog/release-notes-3.0.0 | 3.0.0 publiée le 5 décembre 2023 (la 3.0.1 est marquée invalide) ; 3.1.0 publiée le 31 janvier 2026 | Primaire (3.1.0 : relevé secondaire) | [04](04-contrats-egen.md) |
| S-20 | Versions de la spécification OpenAPI | https://www.github.com/OAI/OpenAPI-Specification/releases ; https://spec.openapis.org/oas/ | 3.1.1 publiée le 24 octobre 2024 (3.1 s'aligne sur JSON Schema 2020-12) ; 3.2.0 publiée en septembre 2025 | Primaire | [04](04-contrats-egen.md) |

## Pages officielles à consulter pour recouper (VR-19)

| Sujet | Page |
|---|---|
| Kubernetes | https://kubernetes.io |
| k3s | https://k3s.io |
| Crossplane | https://www.crossplane.io |
| Nomad | https://developer.hashicorp.com/nomad |
| Temporal | https://temporal.io |
| NATS | https://nats.io |
| OpenBao | https://openbao.org |
| SPIFFE / SPIRE | https://spiffe.io |
| Keycloak | https://www.keycloak.org |
| SpiceDB | https://authzed.com/spicedb |
| Dapr | https://dapr.io |
| CloudEvents | https://cloudevents.io |
| AsyncAPI | https://www.asyncapi.com |
| OpenTelemetry | https://opentelemetry.io |
