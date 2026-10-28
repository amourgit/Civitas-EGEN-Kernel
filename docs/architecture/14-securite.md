# 14 — Sécurité — modèle transverse

## Identité de service

Chaque service enregistré obtient une **identité cryptographique**
(certificat court-terme, idéalement au format proche de SPIFFE/SPIFFE-ID
— `spiffe://civitas.africa/ns/content-platform/sa/news-service` — même si
l'implémentation V1 délègue la PKI à Vault plutôt que de déployer un
système SPIFFE/SPIRE complet, pour rester pragmatique). Cette identité
sert de base au **mTLS** entre services (porté par la Communication
Fabric, voir [12](12-communication-fabric.md)) et à l'authentification des
adapters vers Nomad/Consul (tokens ACL scoped, jamais le token root).

## Principe du moindre privilège, partout

- **Token Nomad de l'adapter** : politique ACL limitée aux capacités
  strictement nécessaires (`submit-job`, `read-job`, `dispatch-job`),
  scoped par namespace.
- **Token Consul de l'adapter** : `service:write` limité aux préfixes de
  noms de service gérés par EGEN.
- **Accès aux secrets (Vault)** : chaque service n'a accès qu'à son propre
  chemin (`secret/data/<service-name>/*`), jamais un accès transverse.
- **RBAC de l'API de contrôle EGEN** : scoping par équipe/domaine (voir
  [13](13-api-et-contrats.md)).
- **Workflows : mutations réservées aux administrateurs.** Une
  `WorkflowDefinition` orchestre des appels vers des services de
  *plusieurs* équipes et ne porte aucune équipe propriétaire dans son modèle
  ([05](05-modele-de-domaine.md)) : il n'y a donc rien à comparer au claim
  d'équipe du jeton. Tant qu'un modèle de propriété n'existe pas, la seule
  politique sûre par défaut est de refuser toute mutation (`POST` de
  définition, démarrage, `pause`/`resume`/`terminate`) aux non-administrateurs ;
  la lecture reste ouverte à tout appelant authentifié. À revoir si les
  workflows reçoivent un jour un propriétaire explicite.
- **Publication d'événements : un appelant ne publie que ce qu'un manifeste
  annonce.** `POST /api/v1/events/publish` ([13](13-api-et-contrats.md))
  exige que le `type` figure dans `events.publishes` d'au moins un service
  déclaré, et que l'appelant soit administrateur ou porte l'équipe
  propriétaire de l'un de ces services (même règle que pour toute action sur
  un service déjà enregistré ; un service sans équipe assignée reste ouvert à
  tout appelant authentifié, note de migration ci-dessus). Sinon `403`.

## Utilisation par l'écosystème multi-solutions

- Deux équipes `metadata.team` sont réservées à la plateforme : `platform` (services de l'écosystème, déclarés par la pipeline) et `platform-operator` (**toutes** les instances d'applications, déclarées par `platform-operator-service`).
- **Seul** `platform-operator-service` détient un jeton du Kernel (client *client credentials*, claim `team = platform-operator`) : moindre privilège, aucun rôle administrateur ([22.8](22-ecosysteme-multi-solutions-et-frontiere.md#228--registre-des-décisions), D14, [25.7](25-identite-et-autorisations.md#257--échange-de-jeton-et-moindre-privilège)).
- Les équipes humaines **ne voient pas** les instances d'applications dans le Kernel ; elles passent par le catalogue et le portail.
- Le RBAC du Kernel (équipe + administrateur) reste volontairement **grossier** : il n'exprime ni hiérarchie de cellules ni droits métier, qui relèvent d'OpenFGA et du contrôle d'usage du catalogue ([25](25-identite-et-autorisations.md)).

## Ce que le Kernel ne fait jamais en matière de sécurité

- Il ne stocke jamais un secret en clair (ni en base, ni en log, ni en
  cache mémoire persistant au-delà de la durée d'un appel).
- Il ne délivre jamais un token de portée plus large que celle
  strictement requise par l'opération demandée.
- Il ne fait jamais confiance implicitement à un appel interne juste parce
  qu'il vient du réseau interne (« zero trust » par défaut, même en
  interne — cohérent avec les cas d'usage mixtes VM/conteneurs/edge
  possibles pour Consul).
