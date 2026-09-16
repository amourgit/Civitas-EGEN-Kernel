# EGEN Kernel

**EGEN Kernel** est le Control / Composition Plane développé par **CIVITAS
Africa** pour faire tourner, découvrir, connecter et orchestrer un
écosystème de services autonomes — éducation, actualité civique,
communication, identité, ou tout autre domaine à venir. Le Kernel ne
possède aucune logique métier : il compose des moteurs spécialisés déjà
éprouvés (Nomad, Consul, Kafka/NATS) pour donner à cet écosystème un socle
technique cohérent, sans jamais dupliquer ce que ces moteurs savent déjà
faire.

**Logiciel propriétaire — tous droits réservés.** Ce dépôt est privé et son
contenu n'est distribué sous aucune licence open source. Toute
reproduction, modification ou distribution en dehors de CIVITAS Africa est
interdite sauf autorisation explicite.

## La phrase qui guide chaque décision

> **EGEN possède l'intelligence de composition. Les moteurs spécialisés
> possèdent l'intelligence d'exécution.**

| EGEN sait dire… | … le moteur spécialisé sait répondre |
|---|---|
| « Ce service doit tourner, avec ces ressources. » | **Nomad** : sur quel nœud, avec quel scheduling, quelle allocation. |
| « Ce service doit être découvrable et sain. » | **Consul** : où sont ses instances, sont-elles en bonne santé. |
| « Ces services échangent tel type d'événement. » | **Kafka/NATS** : comment transporter, répliquer, garantir la livraison. |
| « Cette suite d'étapes métier doit s'exécuter dans cet ordre, avec compensation si ça échoue. » | Le **moteur de Workflow EGEN**, natif, orchestre — mais délègue chaque étape au service propriétaire. |

Avant d'écrire une ligne de code sur une capacité, la question à se poser
est toujours : *« Nomad/Consul/Kafka/NATS le fait-il déjà ? »* Si la
réponse est oui, EGEN se contente de le composer — il ne le réimplémente
jamais.

## Le cycle canonique

Le cœur d'EGEN est une boucle de contrôle permanente, jamais un script
d'installation :

```
Declare → Resolve → Compose → Delegate → Observe → Reconcile
```

Un client déclare un **état désiré** ; EGEN résout les références, compose
un plan d'exécution, délègue aux moteurs spécialisés, observe l'état réel,
et corrige tout écart — sans intervention humaine, en continu. Le détail
complet de ce cycle est dans
[`docs/architecture/04-moteur-de-reconciliation.md`](docs/architecture/04-moteur-de-reconciliation.md).

## Documentation d'architecture

La documentation complète — vision, principes, moteur de réconciliation,
modèle de domaine, contrat de service, ports & adapters, cycle de vie,
dépendances, workflow, communication, API, sécurité, observabilité, stack,
tests, feuille de route — vit dans
[`docs/architecture/`](docs/architecture/README.md). C'est la référence qui
fait foi pour toute décision de conception ou de revue de code dans ce
dépôt.

Point d'entrée recommandé : [`docs/architecture/README.md`](docs/architecture/README.md).

## Les dix garde-fous non négociables

1. Aucune logique métier dans le cœur (`egen-domain`, `egen-application`).
2. Aucun type d'un SDK externe (Nomad, Consul, Kafka…) dans une signature de méthode du domaine.
3. Toute capacité candidate à l'implémentation interne doit d'abord répondre « non » à : *Nomad/Consul/Kafka/NATS le fait-il déjà ?*
4. Une installation EGEN vide (zéro service métier) doit démarrer et répondre à son API de santé.
5. Ajouter un service ne doit jamais nécessiter de modifier une ligne du Kernel.
6. Ajouter un adapter ne doit jamais nécessiter de modifier le domaine.
7. Chaque opération déléguée à un adapter doit être idempotente.
8. Chaque service peut être dans n'importe quel langage.
9. Le cœur est testable sans qu'aucun moteur externe ne tourne.
10. Toute divergence entre état désiré et état observé doit être visible — jamais silencieuse.

Détail et justification de chacun : [`docs/architecture/02-principes-fondamentaux.md`](docs/architecture/02-principes-fondamentaux.md).

## Ce que le Kernel n'est pas

- Un PaaS métier (pas de modèle `Invoice`, `Patient`, `Article`).
- Un remplaçant de Kubernetes/Nomad — c'est une couche au-dessus.
- Un ESB qui comprend la sémantique métier des messages.
- Un framework applicatif imposé aux services (pas de SDK obligatoire couplé à un langage).
- Un IAM central déguisé, un ERP technique ou un backend métier partagé.

Toute logique de cette nature est portée par des **services indépendants**,
jamais codée en dur dans le Kernel. Détail complet :
[`docs/architecture/01-vision-et-positionnement.md`](docs/architecture/01-vision-et-positionnement.md).

## Construire le projet

Prérequis : JDK 21, Maven 3.9+, Docker.

```bash
mvn -B verify
```

La CI GitHub Actions reconstruit et teste l'intégralité du réacteur à
chaque push.

## Contribuer

Toute contribution doit respecter les dix garde-fous ci-dessus et éviter
les anti-patterns listés dans
[`CONTRIBUTING.md`](CONTRIBUTING.md) et
[`docs/architecture/18-anti-patterns.md`](docs/architecture/18-anti-patterns.md).
