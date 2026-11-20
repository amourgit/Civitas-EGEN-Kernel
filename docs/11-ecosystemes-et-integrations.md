# 11 — Écosystèmes et intégrations

## À quoi sert ce document
Décrire les trois écosystèmes fondamentaux et leurs relations, la frontière entre le Kernel et la Plateforme, les trois modes
d'intégration d'un système externe, les connecteurs, et les exigences du système d'agents vis-à-vis du Kernel.

## Ce qu'il ne couvre pas
La documentation de la Plateforme elle-même (dépôt Civitas-EGEN-Business, `docs/plateforme/`) et celle du système d'agents
(dépôt Civitas-EGEN-Agents).

## Les écosystèmes

Un **écosystème** est un ensemble cohérent de services qui répondent à une famille de besoins. Il est gouverné par un
Control Plane et **déployé comme un paquet** dans un espace de noms dédié ([05](05-kernel-de-reference.md)).

| Écosystème | Contenu | Exemples (Université X) |
|---|---|---|
| **Métier** | Les applications qui servent les activités de l'organisation | Agenda Radicale du Service RH, scolarité |
| **Transversal** | Les capacités partagées par plusieurs écosystèmes | Stockage, messagerie, identité, observabilité |
| **Intelligence et Agents** | Agents, modèles, services d'IA | Assistant vocal du Service RH |
| **Spécialisés** (ajoutables) | Tout autre ensemble cohérent, sans refonte | Recherche, formation |

**Relations.** Métier **consomme** le Transversal (par capacités) ; l'IA **consomme** le Métier et le Transversal ; **aucune
relation n'est implicite** : chacune est une demande C3 soumise aux politiques. L'autorité d'une ressource reste à un seul
Control Plane (P-04).

**Cycle de vie d'un écosystème.** Créer (installer le paquet, créer l'espace de noms, appliquer les politiques) ; étendre
(ajouter des services ou capacités) ; mettre à jour (nouvelle version du paquet) ; retirer (supprimer le paquet et l'espace de
noms **sans toucher** aux autres écosystèmes). Les dépendances entre écosystèmes sont des dépendances de paquets et de
capacités consommées, toutes déclarées.

## Frontière Kernel / Plateforme

La **Plateforme** (cellules, catalogue, instances, affectations, portail, configuration multi-niveaux, passerelle de domaine,
connecteurs) est un ensemble de **services** qui s'exécutent dans les écosystèmes et **utilisent** le Kernel. Règles :

| Règle | Contenu |
|---|---|
| Le Kernel ignore la Plateforme (P-10) | Aucun type, champ, identifiant ou message du Kernel ne contient un concept de la Plateforme. |
| Mots réservés | Les identifiants du Kernel ne contiennent pas : `cellule`, `tenant`, `catalogue`, `affectation`, `portail`. Liste figée en R1.1 (D-16). Un test d'architecture la vérifie. |
| Étiquettes | Le préfixe `egen.civitas.africa/` est réservé au Kernel ; le préfixe `platform.civitas.africa/` est réservé à la Plateforme. Le Kernel **transporte** les étiquettes de la Plateforme **sans les interpréter**. |
| Ce que le Plateforme demande au Kernel | Des capacités (C3), via l'opérateur de la Plateforme, avec un jeton à portée limitée. |
| Ce que le Kernel ne fait jamais pour la Plateforme | Décider d'un tenant, d'une affectation, d'un droit métier ; stocker une configuration métier. |

La documentation de la Plateforme se réaligne sur ce document (PO-08). Entre dépôts, on cite « dépôt + chemin ».

## Trois modes d'intégration d'un système externe

| Mode | Quand | Comment | Exemple |
|---|---|---|---|
| **Native** | Le système parle déjà les contrats EGEN | Il expose C1 à C6 et rejoint la fédération | Un Control Plane développé par CIVITAS dans une autre technologie |
| **Extension (Provider)** | Le système a une API exploitable et on veut le piloter **depuis un Kernel** | Une extension ajoute une implémentation de capacité | Nomad pour `workload-runtime` |
| **Connecteur ou pont** | Le système ne peut pas parler les contrats | Un service séparé traduit | Un agenda Nextcloud joint par CalDAV |

**Vocabulaire (à ne pas confondre).**

| Terme | Définition |
|---|---|
| **Provider** | Extension d'un Kernel qui pilote un système externe depuis l'intérieur du Kernel. |
| **Connecteur** | Service **séparé**, basé sur Apache Camel, qui traduit un contrat de domaine vers un logiciel ou un protocole (D-10). |
| **Pont de fédération** | Composant qui expose les contrats C1 à C6 pour un Control Plane qui ne les parle pas nativement. |
| **Passerelle de domaine** | Service de la **Plateforme** qui contrôle puis appelle une application pour tous les domaines. Hors Kernel. |

## Préserver la gouvernance (G-07)

Un connecteur ou un pont **ne contourne jamais** les politiques :

1. il a sa propre identité de charge de travail (SPIFFE) et n'est joignable que par des appelants autorisés ;
2. les politiques réseau sont en **refus par défaut** : il ne peut joindre que ce qui est déclaré ;
3. il **n'a aucun identifiant direct** de la cible : les secrets lui sont délivrés par le coffre, à courte durée ;
4. chaque appel passe par la **même garde de politique** que n'importe quelle demande, et est audité ;
5. il est **remplaçable** sans changer le contrat qu'il sert.

## Intégrer une plateforme externe : Crossplane natif comme Control Plane

Une plateforme construite directement sur Crossplane peut rejoindre la fédération : un **pont** lit ses définitions de ressources
composites et les publie comme capacités C2, reçoit les demandes C3 et les traduit en ressources Crossplane. Un Kernel EGEN peut
aussi **déléguer** certaines opérations à un Crossplane externe : ce sont deux rôles à ne pas confondre (Control Plane fédéré,
ou moteur sollicité).

## Exigences du système d'agents vis-à-vis du Kernel

| Exigence | Contenu |
|---|---|
| Identité d'agent | Chaque agent a une identité de charge de travail (SPIFFE) distincte de celle de la personne qu'il sert. |
| Chaîne « agit pour » | Jeton délégué à portée réduite ; la chaîne est conservée dans les traces et l'audit ([07](07-identite-confiance-securite.md)). |
| Capacités exposées | Le bloc `interfaces` d'un `EgenService` annonce ce qu'un agent peut appeler (opaque pour le Kernel). |
| Normes d'interface | MCP (outils) et A2A (agent à agent) sont aujourd'hui sous la gouvernance de l'Agentic AI Foundation (Linux Foundation) ([S-15](references.md)). Transparence : MCP a été créé par Anthropic ; la recommandation s'appuie sur sa gouvernance neutre actuelle. |
| Garde de politique | Un agent passe par la même garde que toute demande ; ses droits sont des droits fins (SpiceDB). |

Le détail (garde d'accès aux capacités, jetons, risque de chaque action) relève de la phase R7 et de la documentation du système d'agents.

## Exemple de bout en bout

Le Service RH utilise aussi un agenda Nextcloud déjà installé. Un **connecteur** (service séparé, basé sur Camel) traduit la
capacité « agenda » en appels CalDAV. Il a sa propre identité, ne détient aucun mot de passe : le coffre lui délivre un jeton court.
Quand Marie crée un événement, la demande passe par la garde de politique, est auditée, puis le connecteur appelle Nextcloud.
Si l'on retire le connecteur, la capacité « agenda » reste décrite par le même contrat.
