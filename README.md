# EGEN Kernel

EGEN Kernel est le socle technique développé par **CIVITAS Africa** sur lequel
s'assemble et s'exécute un écosystème de services indépendants. Le Kernel ne
connaît aucun métier : il fournit les mécanismes qui permettent à des services
autonomes — éducation, actualité civique, communication, identité, ou tout
autre domaine futur — d'exister, de tourner, de se trouver, de communiquer,
de s'orchestrer et d'évoluer, sans jamais posséder lui-même la moindre
logique métier.

**Logiciel propriétaire — tous droits réservés.** Ce dépôt est privé et son
contenu n'est distribué sous aucune licence open source. Toute reproduction,
modification ou distribution en dehors de CIVITAS Africa est interdite sauf
autorisation explicite.

## Référence d'architecture

[`docs/architecture/charte-v3.md`](docs/architecture/charte-v3.md) est le
document qui fait foi pour toute décision d'architecture dans ce dépôt. Il
détaille, mécanisme par mécanisme, chacun des piliers résumés ci-dessous —
ce qu'il gère, ce qu'il ignore délibérément, ses points d'extension. Toute
contribution doit s'y conformer.

## Principe directeur

> Le Kernel ne possède rien du métier. Il fournit les mécanismes permettant
> au métier d'exister sous forme de services autonomes.

Il ne connaît ni l'identité, ni les organisations, ni les utilisateurs, ni
aucun domaine métier — pas plus qu'il ne cherche à uniformiser la technologie
des services qu'il héberge. Un service Python reste un service Python, un
service Java reste un service Java ; le Kernel les monte, les enregistre, les
connecte et les orchestre, sans jamais entrer dans leur logique interne. Son
rôle s'arrête au **substrat d'exécution de l'écosystème**.

## Les douze piliers du Kernel

| Pilier | Rôle en une phrase |
|---|---|
| **Service Runtime** | Définit comment une instance de service démarre, tourne et s'arrête — sans fournir le runtime applicatif lui-même. |
| **Service Registry** | Sait quels services existent et quelles capacités techniques ils exposent — jamais ce qu'ils font au sens métier. |
| **Service Discovery** | Permet à un service d'en trouver un autre par son identité déclarée, jamais par une URL codée en dur. |
| **Communication Fabric** | Transporte et route les échanges entre services (HTTP, gRPC, messaging, événements) sans en comprendre le contenu. |
| **Workflow Engine** | Sait exécuter étapes, transitions, conditions et compensations — jamais un workflow métier prédéfini. |
| **Configuration Engine** | Distribue, versionne et valide la configuration déclarée par les services, sans connaître le sens de leurs paramètres. |
| **Dependency Management** | Sait qu'un service dépend techniquement d'un autre, jamais pourquoi. |
| **Lifecycle Management** | Installe, enregistre, configure, démarre, arrête, met à jour et retire un service — le cycle de vie technique de bout en bout. |
| **Observability** | Standardise logs, métriques, traces et healthchecks — sans jamais analyser ce qu'ils signifient pour le métier. |
| **Event Infrastructure** | Fournit bus, topics, abonnements et livraison — les événements eux-mêmes appartiennent aux services. |
| **API / Contract Infrastructure** | Fournit le cadre de déclaration, de versionnement et de validation des interfaces — jamais les API métier elles-mêmes. |
| **Deployment Adapter** | Traduit une intention de déploiement vers l'environnement réel (Docker, Kubernetes, systemd, VM, cloud...), sans y enfermer le Kernel. |

## Ce que le Kernel n'est pas

Le Kernel EGEN n'est, et ne doit jamais devenir :

- un IAM central déguisé (identité, authentification, autorisation métier,
  utilisateurs, organisations, tenants, membres, rôles ou permissions
  métier) ;
- un ERP technique ou un backend métier partagé (GED, éducation, finance,
  RH, santé, CRM...) ;
- un cadre qui impose sa propre stack technologique aux services qu'il
  héberge.

Toute logique de cette nature est portée par des **services indépendants**,
au même titre que n'importe quel autre service de l'écosystème — jamais
codée en dur dans le Kernel.

## Deux façons d'étendre l'écosystème

Le Kernel propose deux modes d'extension, qui coexistent sans se substituer
l'un à l'autre :

1. **Services indépendants** — le mode principal, décrit ci-dessus : un
   service autonome, déployé séparément, qui s'enregistre auprès du Kernel
   et communique avec le reste de l'écosystème via la Communication Fabric.
   C'est la voie par défaut pour tout domaine métier.
2. **Modules/plugins embarqués** — un mécanisme d'extension plus léger,
   conservé pour les besoins où coder et déployer un service séparé serait
   disproportionné : une fonctionnalité ou un module personnel chargé
   directement dans le process du Kernel (ou dans un process isolé), sans
   pour autant faire porter au Kernel lui-même la moindre logique métier
   codée en dur. Voir la Charte d'Architecture, chapitre « Le mode
   d'extension embarqué », pour la frontière exacte entre les deux modes.

## Construire le projet

Prérequis : JDK 21, Maven 3.9+, Docker.

```bash
mvn -B verify
```

La CI GitHub Actions reconstruit et teste l'intégralité du réacteur à chaque
push.
