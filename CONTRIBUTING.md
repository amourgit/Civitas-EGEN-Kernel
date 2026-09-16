# Contribuer à EGEN Kernel

Ce document résume les règles à respecter avant d'ouvrir une Pull Request.
Il ne remplace pas la documentation d'architecture complète dans
[`docs/architecture/`](docs/architecture/README.md) — il en extrait ce qui
doit être vérifié à chaque contribution.

## Avant d'écrire du code

1. **La capacité existe-t-elle déjà dans Nomad, Consul, Kafka ou NATS ?**
   Si oui, EGEN la compose — il ne la réimplémente jamais. Voir
   [`docs/architecture/01-vision-et-positionnement.md`](docs/architecture/01-vision-et-positionnement.md).
2. **Où vit ce code ?** `egen-domain` (règle métier pure, zéro dépendance
   externe), `egen-application` (orchestration de use case et moteur de
   réconciliation), `egen-adapters/*` (une intégration technique
   concrète, un module par technologie), ou `egen-api` (exposition REST/
   gRPC). Voir
   [`docs/architecture/16-packages-et-stack-technique.md`](docs/architecture/16-packages-et-stack-technique.md).
3. **Cette opération est-elle idempotente ?** Toute méthode d'un adapter
   secondaire appelée par la boucle de réconciliation doit pouvoir être
   rejouée sans effet de bord destructeur.

## Règles de dépendance strictes

- `egen-domain` n'importe **jamais** un type d'un SDK externe (Nomad,
  Consul, Kafka…), ni un framework web, ni un adapter.
- `egen-application` ne connaît que les **interfaces** de ports qu'il
  définit lui-même — jamais un module `egen-adapters/*` concret.
- Un module `egen-adapters/*` n'importe **jamais** un autre module
  `egen-adapters/*` (pas de couplage caché entre deux technologies).

Ces règles sont vérifiées mécaniquement en CI par des tests d'architecture
(ArchUnit ou équivalent) — une violation fait échouer le build, pas
seulement la revue de code.

## Anti-patterns à ne jamais introduire

Liste complète et détaillée dans
[`docs/architecture/18-anti-patterns.md`](docs/architecture/18-anti-patterns.md).
En bref :

| Anti-pattern | Correction |
|---|---|
| Le Kernel réimplémente du scheduling/placement | Déléguer entièrement à `DeploymentPort` → Nomad. |
| Le Registry duplique la résolution temps réel de Consul | Le Registry ne stocke que le désiré + un cache d'observé pour le statut. |
| Réconciliation qui réagit au contenu d'un événement | Revenir au modèle « la clé suffit, on relit tout ». |
| Appel synchrone bloquant dans l'API publique | Retourner `202 Accepted`, laisser la boucle converger. |
| Une classe connaît plus d'un SDK externe à la fois | Un port = un besoin, un adapter = une technologie. |
| Type externe qui fuite dans une signature du domaine | Interdit au build, pas seulement en revue. |
| Opération d'adapter non idempotente | Toujours un upsert, jamais une erreur bloquante sur ressource déjà existante. |
| Secret en clair dans un manifeste | Toujours une `SecretReference`, jamais une valeur. |
| Logique métier dans une définition de workflow | La décision métier reste dans le service, jamais dans l'orchestration. |
| Sur-ingénierie anticipée (ex. circuit-breaker maison sans besoin prouvé) | Toujours vérifier d'abord si le moteur spécialisé le fait déjà. |

## Tests attendus sur une Pull Request

- **Niveau 1 — Domaine** : aucun conteneur, aucun accès réseau, exécution
  en quelques secondes.
- **Niveau 2 — Use case** : doubles de test en mémoire (`egen-test-kit`),
  jamais un vrai Nomad/Consul/Kafka.
- **Niveau 3 — Adapter** : Testcontainers, contre le moteur réel concerné.
- **Niveau 4 — Architecture** : suite ArchUnit, doit rester verte.
- **Niveau 5 — Bout en bout** (si la PR touche un scénario existant) :
  vérifié en environnement éphémère complet.

Détail complet de la stratégie de tests :
[`docs/architecture/17-strategie-de-tests.md`](docs/architecture/17-strategie-de-tests.md).

## Convention de commit

Un commit doit rester atomique et pousser à chaque étape discrète plutôt
que d'accumuler du travail non commité. Les préfixes `feat:`, `fix:`,
`docs:`, `refactor:`, `test:` sont utilisés pour qualifier l'intention du
commit.
