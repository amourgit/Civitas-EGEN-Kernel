# 21 — Système d'agents EGEN, Capability Gateway et Edge Gateway

*(Étend [01](01-vision-et-positionnement.md) et [02](02-principes-fondamentaux.md)
à un cas d'usage précis : des agents conversationnels qui consomment, pour
le compte d'un utilisateur habilité, les capacités exposées par les
services que le Kernel héberite. Ce document ne modifie aucun garde-fou
existant — il montre où un système d'agents vient se brancher sur eux.)*

## 21.0 — Où vit ce système, et où il ne vit pas

Trois repositories distincts, trois cycles de vie de déploiement distincts,
réunis seulement au moment du déploiement par une pipeline commune (voir
[21.8](#218--pipeline-de-déploiement-générale)) :

```
Civitas-EGEN-Kernel/          ce dépôt — control plane pur, ne sait rien
                               des agents, des utilisateurs, ni du sens
                               métier d'un service (garde-fou n1, voir 02).

Civitas-EGEN-Edge-Gateway/     projet à part entière — Kong (ou équivalent),
                               DÉPLOYÉ EN DEHORS du Kernel comme service
                               EGEN (voir 21.1). Unique porte d'entrée
                               publique de toute la plateforme.

Civitas-EGEN-Agents/           projet à part entière — Agent Gateway,
                               Agent Runtime (LangGraph), Capability
                               Gateway (voir 21.2 à 21.6). DÉCLARÉ comme un
                               ensemble de services EGEN ordinaires
                               (`ServiceManifest`), déployé et gouverné par
                               le Kernel comme n'importe quel autre service
                               métier — jamais un citoyen privilégié.
```

Le Kernel ne connaît ni utilisateurs, ni rôles, ni permissions métier, ni
le sens de ce que fait un service ([02](02-principes-fondamentaux.md),
garde-fou n1). Une conséquence directe et non négociable : **tout ce
document décrit des services qui consomment l'API publique du Kernel
([13](13-api-et-contrats.md)) de l'extérieur** — aucune des classes
décrites ici n'entre jamais dans `egen-domain`, `egen-application`,
`egen-adapters/*`, `egen-api` ou `egen-bootstrap`.

## 21.1 — Edge Gateway (Kong) — la porte d'entrée unique

Rôle : terminaison TLS publique, routage vers les projets de la
plateforme (frontend, Agent Gateway, API du Kernel si exposée
directement à des clients machine), rate-limiting global, WAF,
et — point important pour la sécurité décrite en
[14](14-securite.md) — **le point où un jeton OIDC client est d'abord
vérifié syntaxiquement** avant d'atteindre un service interne. Kong ne
fait *pas* le travail de RBAC métier (qui appartient à chaque service,
voir [21.5](#215--autorisation--le-policy-enforcement-point)) : il fait
de la gestion d'API (clés, quotas, routage, observabilité de bord).

Décision actée par le commanditaire : Kong est un projet de déploiement à
part entière, pas un service interne du Kernel. Il est déclaré dans la
pipeline générale de déploiement de l'entreprise (voir
[21.8](#218--pipeline-de-déploiement-générale)), au même titre que le
frontend et le Kernel lui-même — **pas** via un `ServiceManifest` déployé
*par* le Kernel, puisqu'il se trouve topologiquement *devant* lui et doit
pouvoir démarrer indépendamment de la santé du Kernel (sinon une panne du
Kernel empêcherait même de servir une page d'erreur propre).

```
Internet
   │
   ▼
┌─────────────────────────┐
│   Edge Gateway (Kong)    │  TLS, routage, quotas, WAF
└───────────┬─────────────┘
            │
   ┌────────┼─────────────────┬───────────────────────┐
   ▼        ▼                 ▼                        ▼
Frontend  Agent Gateway   API Kernel (rare,        (autres projets
                          consoles/CI/CD)           de la plateforme)
```

## 21.2 — Vue d'ensemble du système d'agents

```
Utilisateur
   │ HTTPS (jeton OIDC)
   ▼
Edge Gateway (Kong)
   │
   ▼
Agent Gateway ───────────────► IAM (Keycloak) + Policy Engine (OpenFGA/OPA/Cedar)
   │  session + streaming            (qui est l'utilisateur, quels droits)
   ▼
Agent Runtime (LangGraph)
   │  outils = catalogue FILTRÉ pour CET utilisateur
   ▼
Capability Gateway (MCP)  ◄──── Policy Engine (revérification à l'exécution)
   │  jeton échangé, scope = un seul outil, une seule action
   ▼
Services métier (RH, GED, Finance, ...) — chacun déclaré comme
ServiceManifest, déployé et gouverné par le Kernel EGEN
```

Cinq services applicatifs composent le système d'agents ; chacun a une
responsabilité et une seule :

| Service | Rôle | Ce qu'il NE fait jamais |
|---|---|---|
| Agent Gateway | Session utilisateur, streaming SSE/WebSocket vers le frontend, orchestration de haut niveau | N'appelle jamais un service métier directement |
| Agent Runtime | Raisonnement LLM, graphes LangGraph (supervisor/swarm/HITL), mémoire de conversation | Ne décide jamais seul des permissions — il ne voit que le catalogue déjà filtré |
| Capability Gateway | Registre des capacités (agrégé depuis les manifestes des services), Policy Enforcement Point, échange de jeton, audit | N'interprète jamais le sens métier d'une capacité — il vérifie une autorisation, il ne juge pas une donnée |
| IAM (Keycloak ou équivalent) | Authentification, émission de jetons, appartenance aux équipes/rôles | Ne connaît jamais les capacités techniques d'un service |
| Policy Engine (OpenFGA, OPA ou Cedar) | Évaluation des règles d'autorisation (qui peut faire quoi, sur quelle ressource) | Ne stocke jamais de secret ni de donnée métier |

## 21.3 — Le Service Manifest s'étend, jamais ne se casse

Pour qu'un service métier devienne consommable par un agent, il déclare
dans son propre `ServiceManifest` — géré par lui, jamais par le Kernel
— un bloc optionnel `interfaces`, exactement dans l'esprit de
[13](13-api-et-contrats.md) (« rétro-compatible en lecture, champs
optionnels seulement ») :

```yaml
apiVersion: egen.civitas.africa/v1
kind: ServiceManifest
metadata:
  name: rh-conges-service
  version: 1.3.0
  team: ressources-humaines
# ... champs Phase 1-4 inchangés (runtime, deployment, health, lifecycle,
#     dependencies) ...

interfaces:                          # NOUVEAU, optionnel — ignoré par tout
                                      # consommateur qui ne le comprend pas
  - type: mcp                        # mcp | openapi
    ref: https://rh-conges-service.internal/mcp
    riskDefault: read                # read | write | sensitive — valeur
                                      # par défaut si une opération ne
                                      # précise pas la sienne
```

Le Kernel ne lit **jamais** le contenu de `ref` et n'interprète jamais
`riskDefault` — il stocke ce bloc comme une chaîne opaque au même titre
que le reste du manifeste ([08](08-registry.md)), exactement comme il
traite déjà `deployment.image` sans savoir ce qu'il y a dans l'image. Seul
le Capability Gateway sait lire un descripteur MCP ou OpenAPI.

### Le descripteur de capacités lui-même (côté service métier, pas Kernel)

Chaque service publie, à l'URL indiquée par `ref`, une liste d'opérations
au format MCP (Model Context Protocol) — choisi plutôt qu'un OpenAPI brut
parce qu'il porte nativement les descriptions en langage naturel dont un
LLM a besoin pour choisir un outil à bon escient :

```json
{
  "tools": [
    {
      "name": "poser_conge",
      "description": "Enregistre une demande de congé pour l'utilisateur courant, sur une plage de dates donnée.",
      "inputSchema": { "type": "object", "properties": { "dateDebut": {"type": "string", "format": "date"}, "dateFin": {"type": "string", "format": "date"} }, "required": ["dateDebut", "dateFin"] },
      "risk": "write",
      "requiredPermission": "rh.conges.creer"
    },
    {
      "name": "consulter_solde_conges",
      "description": "Retourne le solde de jours de congé restants de l'utilisateur courant.",
      "inputSchema": { "type": "object", "properties": {} },
      "risk": "read",
      "requiredPermission": "rh.conges.lire"
    }
  ]
}
```

`requiredPermission` est un identifiant métier que **seul le Policy
Engine** interprète — le Capability Gateway ne fait que le transporter.

## 21.4 — Capability Gateway : le cœur du système

C'est la pièce qui n'existe dans aucun starter LangGraph et qui doit être
construite avec le plus grand soin, car c'est elle qui transforme « un LLM
qui peut appeler des outils » en « un système que l'entreprise peut auditer
et faire confiance ».

Quatre responsabilités, dans cet ordre à chaque appel d'outil :

1. **Résolution** — retrouve, dans son registre agrégé (rafraîchi
   périodiquement depuis les `interfaces.ref` de tous les services
   `RUNNING` connus via `GET /api/v1/discover/{serviceName}` du Kernel),
   l'opération demandée et le service qui la porte.
2. **Autorisation (PEP)** — interroge le Policy Engine avec
   `(utilisateur, requiredPermission, ressource)` — voir
   [21.5](#215--autorisation--le-policy-enforcement-point). Refus par
   défaut.
3. **Échange de jeton** — obtient, via OAuth2 Token Exchange
   ([RFC 8693](https://www.rfc-editor.org/rfc/rfc8693)), un jeton
   court-terme qui dit explicitement *« l'agent X agit pour le compte de
   l'utilisateur Y, uniquement pour l'opération Z »* — jamais un compte de
   service générique à privilèges larges (même principe de moindre
   privilège que [14](14-securite.md) applique déjà à l'adapter Nomad et
   à l'adapter Consul).
4. **Exécution et audit** — appelle le service métier avec ce jeton,
   journalise systématiquement qui a demandé quoi, sur quelle ressource,
   avec quel résultat (succès/refus), horodaté et corrélé par un
   identifiant de trace unique (même discipline que
   [15](15-observabilite.md) impose déjà à la boucle de réconciliation).

### Catalogue filtré en amont — pas seulement contrôle a posteriori

Avant même de démarrer une session agent, le Capability Gateway construit,
pour l'utilisateur authentifié, la liste des outils que le Policy Engine
autorise déjà *a priori*. Cette liste-là, et seulement elle, est transmise
à l'Agent Runtime comme catalogue d'outils disponibles. Deux bénéfices :

- Le modèle ne voit jamais un outil qu'il n'a pas le droit d'utiliser — il
  ne peut donc ni en halluciner un usage, ni révéler son existence à
  l'utilisateur par erreur.
- La vérification à l'exécution (étape 2 ci-dessus) reste néanmoins
  **obligatoire et jamais contournée** : un catalogue filtré en amont
  réduit la surface d'erreur, il ne remplace jamais le contrôle
  d'autorisation à l'appel (principe de défense en profondeur — zero
  trust même en interne, [14](14-securite.md)).

## 21.5 — Autorisation : le Policy Enforcement Point

Le Capability Gateway est le PEP (Policy Enforcement Point) ; le Policy
Engine (OpenFGA, OPA, ou Cedar — choix d'infrastructure découplé, à
l'image de chaque port du Kernel en [07](07-ports-et-adapters.md)) est le
PDP (Policy Decision Point). Séparation stricte : le PEP ne décide jamais
lui-même d'une règle, il l'applique.

Exemple de modèle de relations (style OpenFGA/ReBAC), volontairement
simple pour amorcer :

```
utilisateur:alice   member    équipe:ressources-humaines
équipe:ressources-humaines  peut  rh.conges.creer
utilisateur:alice   manager  utilisateur:bob        # pour les approbations
```

La question posée à chaque appel d'outil est toujours de la même forme :
*« alice a-t-elle `rh.conges.creer` sur la ressource concernée, maintenant,
dans ce contexte ? »* — jamais *« alice a-t-elle un rôle admin ? »* pris
isolément, pour éviter la dérive classique du RBAC plat qui devient
ingérable à l'échelle d'une entreprise avec des dizaines de services
métier.

## 21.6 — Agent Runtime : ce qui reste du starter LangGraph, et ce qui change

Reprend la structure déjà validée dans l'échange précédent :

**Conservé tel quel** : le moteur LangGraph, la gestion des graphs, les
checkpoints (persistance de l'état de conversation), les interruptions
(`interrupt()` pour le Human-in-the-Loop), les patrons supervisor et
swarm/handoff, la sortie structurée, l'intégration LLM, le serveur HTTP
de streaming.

**Supprimé** : toute la logique de démonstration (Supervisor Demo, Swarm
Demo, RAG Demo, Research Demo, Support Demo) et tout ce qui code en dur un
métier particulier.

**Ajouté, spécifique à EGEN** :

- **Aucun appel direct à un service métier.** Le seul outil que
  l'Agent Runtime connaît nativement est *« appeler le Capability
  Gateway »* — jamais un client HTTP vers RH, GED ou Finance directement.
  Le catalogue d'outils réel est injecté dynamiquement au démarrage de
  session (voir [21.4](#214--capability-gateway--le-cœur-du-système)),
  pas codé dans le graphe.
- **Isolation stricte de la mémoire par utilisateur** (et par organisation
  si le système sert plusieurs entreprises) : checkpoints et mémoire
  long-terme cloisonnés — jamais un `thread_id` ou une clé de mémoire
  partagée entre deux utilisateurs.
- **HITL obligatoire sur les opérations `risk: write` ou `risk:
  sensitive`** déclarées dans le descripteur MCP du service
  ([21.3](#213--le-service-manifest-sétend-jamais-ne-se-casse)) : le
  graphe s'arrête sur un `interrupt()`, l'utilisateur (ou un valideur
  désigné par la politique) confirme avant que le Capability Gateway
  n'exécute réellement l'appel.
- **Quotas par agent** : budget de tokens, nombre d'appels d'outils par
  session, profondeur maximale de délégation entre agents (pour le mode
  swarm), timeout global — pour qu'un agent qui boucle ne consomme jamais
  des ressources ou des quotas d'API illimités.
- **Traitement des sorties d'outils comme des données non fiables** :
  le contenu renvoyé par un service métier (ex. un document de la GED)
  peut contenir une instruction cachée destinée à manipuler l'agent
  (injection de prompt indirecte). Aucune sortie d'outil n'élargit jamais,
  par elle-même, le catalogue de capacités ou les droits de la session en
  cours.

## 21.7 — Deux moteurs, deux rôles — ne jamais les confondre

Le Kernel porte déjà son propre [Moteur de Workflow](11-moteur-de-workflow.md)
(Phase 5, saga avec compensation, déterministe). L'Agent Runtime porte
LangGraph (raisonnement non déterministe, conversationnel). Ce sont deux
outils différents pour deux problèmes différents, jamais l'un à la place
de l'autre :

| | Workflow Engine (Kernel) | Agent Runtime (LangGraph) |
|---|---|---|
| Déterminisme | Oui — même entrée, même déroulé | Non — dépend du raisonnement du LLM |
| Compensation en cas d'échec | Native (voir [11](11-moteur-de-workflow.md)) | Aucune — HITL et audit seulement |
| Déclenché par | Un événement technique ou une API | Une intention utilisateur en langage naturel |
| Exemple | « Provisionner un nouvel employé : créer le compte AD, l'accès badge, le poste de travail » | « Aide-moi à comprendre pourquoi mon solde de congés a baissé » |

Un agent peut **déclencher** un workflow du Kernel comme l'un de ses
outils (via le Capability Gateway, comme n'importe quelle autre capacité)
— il ne le remplace jamais, et le Workflow Engine ne devient jamais un
moteur de raisonnement.

## 21.8 — Pipeline de déploiement générale

Une pipeline d'entreprise unique orchestre le déploiement ordonné des
projets indépendants, chacun avec son propre cycle de build/release :

```
1. Edge Gateway (Kong)         — doit être joignable avant tout le reste
2. EGEN Kernel                 — control plane, doit converger avant que
                                  des services métier ne soient déclarés
3. Services de plateforme      — IAM (Keycloak), Policy Engine, Capability
                                  Gateway, Agent Gateway, Agent Runtime
                                  (déclarés comme ServiceManifest auprès
                                  du Kernel, sauf Edge Gateway — voir 21.1)
4. Services métier              — RH, GED, Finance, ... (chacun avec son
                                  bloc interfaces s'il expose des
                                  capacités aux agents)
5. Frontend                     — routé par l'Edge Gateway
```

L'ordre 1 → 2 → 3 → 4 → 5 n'est pas arbitraire : un service de l'étape N
peut dépendre d'un service de l'étape N-1 pour démarrer sainement (ex. le
Capability Gateway a besoin du Kernel pour découvrir les services), mais
jamais l'inverse — cohérent avec le tri topologique déjà appliqué par le
Kernel à l'intérieur de sa propre étape 4 ([10](10-gestion-des-dependances.md)).

## 21.9 — Test d'acceptation de bout en bout du système d'agents

À l'image du scénario de [20](20-scenario-bout-en-bout.md) pour le Kernel
seul, le système d'agents a son propre test d'acceptation minimal, qui ne
doit jamais être réputé fonctionnel sans lui :

1. Deux utilisateurs, deux équipes différentes, sont authentifiés auprès
   de l'IAM.
2. Chacun ouvre une session sur l'Agent Gateway. Le catalogue d'outils
   reçu par leurs deux Agent Runtime respectifs **diffère** — c'est la
   preuve que le filtrage en amont ([21.4](#214--capability-gateway--le-cœur-du-système))
   fonctionne.
3. Le premier utilisateur demande une action `risk: write` qu'il a le
   droit d'effectuer : l'agent s'arrête sur une confirmation HITL, il
   confirme, l'action s'exécute, un enregistrement d'audit complet existe
   (qui, quoi, quand, résultat).
4. Le second utilisateur tente, via une formulation détournée dans son
   message, de faire exécuter par son agent une capacité qui n'est pas
   dans son catalogue filtré : le Capability Gateway refuse à l'étape
   d'autorisation (pas seulement parce que l'outil est absent du
   catalogue — même si un bug exposait l'outil, le refus doit tenir).
5. Un document injecté avec une instruction cachée est lu par un outil de
   lecture (`risk: read`) : l'agent ne doit exécuter aucune action
   supplémentaire non demandée explicitement par l'utilisateur à la suite
   de cette lecture.

## 21.10 — Ordre de construction recommandé

1. Un service fixture minimal avec un descripteur MCP de deux ou trois
   outils factices, déclaré au Kernel avec un bloc `interfaces`.
2. Capability Gateway (résolution + audit), sans encore de Policy Engine
   réel (une politique « tout autorisé » temporaire, explicitement
   marquée comme non définitive).
3. IAM (Keycloak) et Policy Engine réel (OpenFGA en premier choix pour sa
   simplicité de modèle ReBAC) ; le test d'acceptation
   [21.9](#219--test-dacceptation-de-bout-en-bout-du-système-dagents)
   étape 2 devient possible.
4. Agent Gateway + Agent Runtime avec un seul agent, sans supervisor ni
   swarm encore.
5. HITL, audit complet, quotas — étapes 3 à 5 du test d'acceptation.
6. Supervisor et swarm multi-agents.
7. Branchement du Workflow Engine du Kernel (dès sa Phase 5) comme outil
   long de l'agent.
8. Ajout des services métier réels, un par un, chacun avec son propre
   bloc `interfaces` — jamais plusieurs à la fois, pour garder à chaque
   étape un système testable de bout en bout.
