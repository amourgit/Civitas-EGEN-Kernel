# 02 — Principes, garde-fous et registre des décisions

## À quoi sert ce document
Fixer les règles qui s'appliquent à toute conception (principes, garde-fous) et tenir le registre des décisions
verrouillées. Toute décision d'architecture s'écrit ici ; une décision non prise s'écrit en
[16](16-risques-et-verifications.md) comme point ouvert.

## Ce qu'il ne couvre pas
Le détail technique des décisions (documents 04 à 12) et leur statut d'avancement (document 00).

## Principes

| Id | Principe | Conséquence concrète |
|---|---|---|
| P-01 | **Gouverner, déléguer, suivre.** | Le Kernel ne réimplémente ni déploiement, ni messagerie, ni workflow, ni identité. |
| P-02 | **Autonomie locale d'abord.** | Un Control Plane administre son périmètre même sans la fédération. |
| P-03 | **Le contrat avant la technologie.** | Participer à la fédération = respecter C1 à C6, pas utiliser tel outil. |
| P-04 | **Une seule autorité par ressource.** | Un seul Control Plane administre une ressource donnée ; les autres la consultent ou lui font une demande. |
| P-05 | **Déclaratif.** | On décrit l'état voulu ; un moteur de réconciliation (boucle qui compare l'état voulu à l'état réel et agit pour les rapprocher) converge. |
| P-06 | **La gouvernance précède la communication.** | Pouvoir joindre un système n'autorise rien. |
| P-07 | **Identifier, authentifier, autoriser, faire confiance : quatre choses distinctes.** | Identité vérifiable ≠ droit d'agir ; la confiance entre domaines est explicite. |
| P-08 | **Toute opération est tracée, idempotente et reprenable.** | Une clé d'idempotence (la même demande rejouée n'a pas d'effet double) accompagne chaque demande. |
| P-09 | **Jamais de valeur de secret dans une ressource.** | Uniquement des références, résolues à l'exécution par le système de secrets. |
| P-10 | **Le Kernel ignore le métier.** | Aucun concept de la Plateforme ou du métier dans ses types ni ses identifiants. |
| P-11 | **Les métadonnées informatives ne décident jamais.** | Des champs comme l'équipe ou le langage servent à tracer, jamais à autoriser ou router. |
| P-12 | **Adopter avant de coder.** | Voir D-07. |
| P-13 | **Tout se vérifie.** | Chaque contrat a des tests de conformité ; chaque fait externe est daté ; chaque vérification manquante est inscrite. |
| P-14 | **Pas de nouvelle tentative infinie et silencieuse.** | Tout échec répété est plafonné, compté et visible. |
| P-15 | **Un état incertain n'est jamais converti en silence.** | Avant de réessayer ou de compenser, on observe l'état réel du système concerné. |

## Garde-fous (ce qu'EGEN ne doit pas devenir)

| Id | Garde-fou |
|---|---|
| G-01 | Pas de Kernel central qui connaît et contrôle tout. |
| G-02 | Pas de copie globale des registres locaux : le Federation Plane garde une vue de fédération, pas les ressources. |
| G-03 | Pas de moteur universel qui remplace les moteurs spécialisés. |
| G-04 | Aucune technologie n'est une condition obligatoire de **participation** à la fédération. |
| G-05 | Pas de catalogue d'adaptateurs propriétaires dans le cœur : les intégrations sont des extensions isolées. |
| G-06 | La fédération n'impose pas le même fonctionnement interne à tous les Control Planes. |
| G-07 | Pas de communication sans gouvernance. |
| G-08 | Aucun secret, jeton ou valeur de configuration dans un journal, un message d'erreur, un événement ou une ressource. Un message d'erreur est borné (500 caractères au plus) et ne cite que des **noms**. |
| G-09 | Tout ce qui n'est pas explicitement déclaré est refusé (refus par défaut). |
| G-10 | Moindre privilège : jetons à portée minimale et à courte durée, comptes de service sans droit superflu. |

## Registre des décisions

Statuts : **Verrouillée** (décidée par Samuel) · **Maintenue** (décision de Samuel antérieure à ce plan, compatible avec
lui, à reconfirmer à l'étape où elle s'applique) · **Retenue avec l'option C** (acceptée en bloc, à confirmer
individuellement à l'étape d'introduction).

| Id | Date | Décision | Statut |
|---|---|---|---|
| D-01 | 2026-11-19 | Le Kernel de référence s'appuie sur **Kubernetes** (dans sa version légère k3s si besoin) et **Crossplane**. La fédération, elle, ne dépend d'aucune technologie (G-04). | Verrouillée |
| D-02 | 2026-11-19 | **Temporal** est le moteur de workflows durables pour les opérations qui impliquent plusieurs Control Planes (Sagas). Il n'est pas utilisé pour la réconciliation locale. | Verrouillée |
| D-03 | 2026-11-19 | **Nomad est conservé** comme moteur spécialisé de la capacité `workload-runtime`, à côté de Kubernetes. Il n'est jamais un Control Plane (voir [08](08-moteurs-specialises.md) et [moteurs/nomad.md](moteurs/nomad.md)). Le placement a été confié à l'architecte ; le mécanisme d'intégration reste ouvert (PO-01). | Verrouillée (placement) |
| D-04 | 2026-11-19 | Tout développement hors du plan R0–R7 est gelé : on ne code que ce que [14](14-plan-de-developpement.md) prévoit, dans l'ordre. | Verrouillée |
| D-05 | 2026-11-19 | L'arbre du dépôt ne contient que l'état voulu : aucun document ou code périmé. | Verrouillée |
| D-06 | 2026-11-19 | Documentation d'abord : le contrat normatif s'écrit avant le code. | Verrouillée |
| D-07 | 2026-11-19 | **Adopter avant de coder.** Un besoin couvert par une solution éprouvée (projet open source mature, idéalement diplômé du CNCF) est adopté. Coder ce besoin exige une justification écrite dans le document concerné. | Verrouillée |
| D-08 | antérieure | Le Kernel ignore tout concept métier ou de Plateforme (voir P-10 et [11](11-ecosystemes-et-integrations.md)). | Maintenue |
| D-09 | 2026-10-28 | **Keycloak** pour l'identité des personnes ; **SpiceDB** pour les droits fins. | Maintenue |
| D-10 | 2026-10-28 | Les connecteurs vers les logiciels tiers sont des **services séparés** basés sur **Apache Camel**, hors du Kernel. | Maintenue |
| D-11 | 2026-11-19 | Les contrats C1 à C6 sont indépendants de toute technologie (P-03, G-04). | Verrouillée |
| D-12 | 2026-11-19 | Briques acceptées avec l'option C, à confirmer à leur étape : **SPIFFE/SPIRE** (identité des charges de travail, R3), **NATS JetStream + CloudEvents** (événements, R2.7), **OpenBao** (secrets, R3.4), **OpenTelemetry** (traces et métriques, R2.7), **Dapr** en capacité optionnelle offerte aux services (R5). | Retenue avec l'option C |
| D-13 | 2026-11-19 | Aucune valeur de secret dans une ressource : références uniquement (P-09). | Maintenue |

## Comment ajouter ou changer une décision

1. L'énoncer dans [16](16-risques-et-verifications.md) comme point ouvert, avec options, impact et décideur.
2. Après décision de Samuel, ajouter la ligne ici (date, énoncé, statut) et mettre à jour les documents touchés.
3. Ne jamais supprimer une décision : la marquer « remplacée par D-nn » avec la date.
