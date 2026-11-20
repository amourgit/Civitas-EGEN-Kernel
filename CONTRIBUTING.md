# Contribuer à EGEN Kernel

## À quoi sert ce document
Dire comment travailler dans ce dépôt : avant d'écrire, comment écrire, comment valider, comment commiter.

## Ce qu'il ne couvre pas
Les décisions d'architecture (voir [docs/02-principes-et-decisions.md](docs/02-principes-et-decisions.md)) et
la méthode de test (voir [docs/13-tests-et-conformite.md](docs/13-tests-et-conformite.md)).

## Avant d'écrire quoi que ce soit

1. Lire [docs/00-etat-et-reprise.md](docs/00-etat-et-reprise.md) : phase en cours, gel, point de reprise.
2. Lire le document d'architecture qui concerne le sujet. Si le sujet n'y figure pas, **la documentation
   s'écrit d'abord** (contrat normatif), puis le code.
3. Appliquer la règle d'adoption (D-07) : une solution éprouvée couvre-t-elle le besoin ? Si oui, on l'adopte.
   Si on code quand même, la raison s'écrit dans le document concerné.
4. Tout fait externe (version, licence, comportement d'un outil) est vérifié dans la source officielle ou
   marqué « à vérifier » avec une entrée dans le registre
   [docs/16-risques-et-verifications.md](docs/16-risques-et-verifications.md).

## Règles de dépendance

- Le Kernel ne contient aucun code propre à un moteur spécialisé : chaque moteur s'intègre par une extension
  (voir [docs/05-kernel-de-reference.md](docs/05-kernel-de-reference.md)).
- Les contrats (C1 à C6) ne dépendent d'aucune technologie : ni Kubernetes, ni Crossplane, ni Nomad.
- Aucun identifiant du Kernel ne contient un mot réservé à la Plateforme (liste dans
  [docs/11-ecosystemes-et-integrations.md](docs/11-ecosystemes-et-integrations.md)).

## Environnement de développement et commandes

Python 3.12 ou plus ; Node 20 ou plus (validation AsyncAPI).

```bash
python3 -m venv .venv && . .venv/bin/activate
pip install -e ".[dev]"
(cd tools/asyncapi && npm install)      # analyseur AsyncAPI officiel

pytest                                  # toute la suite de tests
egen-check-docs                         # liens, sections, identifiants, tournures interdites, secrets
egen-check-arch                         # mots réservés, technologies dans les contrats, code de moteur, secrets
egen-check-breaking                     # rupture de compatibilité des contrats (règle C6)
egen-gen-asyncapi                       # régénère contracts/asyncapi/ depuis contracts/catalog/
```

## Commits

- Un commit est atomique : une intention, un titre qui dit ce qui change.
- Préfixes : `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`.
- Le corps du commit liste les fichiers touchés et l'état de vérification : ce qui est **vérifié ici**
  et ce qui **ne l'est pas**.
- On ajoute des commits plutôt que de réécrire l'historique.
- Avant de commiter un fichier produit par un script : relire `git status` et `git diff --stat`
  (un script qui échoue à mi-chemin laisse des changements partiels).

## Secrets

Jamais de jeton, de mot de passe ou de clé privée dans un fichier, un commit ou un document. Les secrets
d'essai sont des secrets d'essai, nommés comme tels. Un jeton exposé dans une conversation se révoque.

## Fusion sur main

Le développement se fait sur `main`. Chaque étape dont les vérifications sont passées (tests automatiques, plus les
commandes locales demandées à Samuel quand l'étape en exige) y est poussée en avance directe (fast-forward).
`main` est toujours à jour **et** vérifié.
