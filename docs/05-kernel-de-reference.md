# 05 — Le Kernel de référence

## À quoi sert ce document
Décrire de quoi est fait un Control Plane de référence (le Kernel EGEN) : ses fonctions, ses types de ressources,
le modèle de service, le cycle de vie, les capacités, les extensions, l'opérateur et les politiques locales.

## Ce qu'il ne couvre pas
Les contrats vus de l'extérieur ([04](04-contrats-egen.md)), la fédération ([06](06-federation-plane.md)), la sécurité
en détail ([07](07-identite-confiance-securite.md)), les moteurs ([08](08-moteurs-specialises.md)).

## Principe

Un Kernel de référence est un **cluster Kubernetes** (la distribution légère k3s convient aux petits sites) sur
lequel on installe **Crossplane** (cadre pour construire des plans de contrôle déclaratifs, voir
[moteurs/crossplane.md](moteurs/crossplane.md)), le **paquet « egen-core »** (types et compositions EGEN),
l'**opérateur EGEN** et le **pont de fédération**. Kubernetes est donc nécessaire **pour ce Kernel-là** ; il ne
l'est pas pour participer à la fédération (G-04) : un Control Plane d'une autre technologie qui respecte les
contrats est un membre à part entière.

## Les neuf fonctions d'un Kernel et leur réalisation

| Fonction | Rôle | Réalisation dans le Kernel de référence |
|---|---|---|
| **Control API** | Point d'entrée des intentions et des demandes | API Kubernetes (types EGEN) pour l'intérieur ; pont de fédération (C1 à C6) pour l'extérieur |
| **Registre local** | Connaître les ressources, leurs versions et leur état | Stockage de l'API Kubernetes, avec numéro de génération et conditions |
| **Capability Management** | Publier les capacités, gérer leurs implémentations | Définitions de ressources composites Crossplane (l'interface) + Compositions (les implémentations) |
| **Extension Management** | Installer, mettre à jour, désinstaller des extensions | Paquets Crossplane (Providers, Functions, Configurations) |
| **Delegation Management** | Choisir le moteur compétent et lui déléguer | Compositions et Providers ; choix par sélecteurs et politique |
| **Reconciliation Management** | Rapprocher l'état réel de l'état voulu | **Délégué** : contrôleurs Kubernetes, Crossplane, moteurs |
| **Policy Management** | Appliquer les politiques locales et fédérées | RBAC et règles d'admission Kubernetes ; SpiceDB consulté par le pont |
| **Operation Tracking et Events** | Suivre chaque opération, publier des événements | Ressource `EgenOperation` + opérateur EGEN + NATS (CloudEvents) |
| **Administration** | Vues et outils d'exploitation | Services **séparés** qui consomment la Control API (jamais dans le cœur) |

## Types de ressources EGEN

Groupe d'API : `egen.civitas.africa`. Les noms sont figés ([04](04-contrats-egen.md), D-16) ; la version d'API
(`v1alpha1`) évolue jusqu'à R2.2.

| Type | Nature | Rôle |
|---|---|---|
| `EgenService` | Ressource composite Crossplane | Déclarer un service à exécuter (modèle ci-dessous). |
| `EgenOperation` | Ressource gérée par l'opérateur | Représenter une opération (C3/C4) : phase, conditions, ressources suivies. |
| `ControlPlaneConfig` | Ressource simple | Configuration d'identité du Control Plane, source du document C1. |

Les préfixes évitent les collisions : Kubernetes possède déjà un type `Service`, et Crossplane 2.x un type `Operation`.

Une **capacité** n'a pas de type propre : c'est une définition Crossplane portant l'étiquette
`egen.civitas.africa/capability: <nom>`. Le pont de fédération les lit et produit C2.

## Le modèle `EgenService`

Un `EgenService` décrit **ce que le service est, ce dont il a besoin et ce qu'il publie**, sans dire quel moteur
l'exécute (le moteur est un choix du Control Plane, G-06).

| Bloc | Contenu | Remarque |
|---|---|---|
| Identité | `name`, `version`, étiquettes, annotations (dont `team`, `repo`, `contact`) | Informatif : ne décide de rien (P-11). |
| `runtime` | `type` : `container`, `binary`, `external` ou `vm` ; `language` | `language` informatif. `external` = déjà exécuté ailleurs, simplement **référencé**. |
| `deployment` | image, ressources (cpu, mémoire), réplicas min/max et autoscaling, stratégie de mise à jour (`rolling`, `blue-green`, `canary`), durcissement (`runAsNonRoot`, `readOnlyRootFilesystem`, capacités retirées), contraintes de placement | Neutre vis-à-vis du moteur. Un sélecteur facultatif `engine` oriente l'implémentation. |
| `network` | points d'accès (nom, protocole, port, chemin), santé (`health`, `readiness`) | |
| `dependencies` | `services` (nom, contrainte de version, `required`) ; `resources` nommées non-service (type, paramètres), à la manière de Score | Une dépendance `required` doit être prête avant le déploiement. |
| `events` | `publishes` (type, référence de schéma), `consumes` (type, groupe, file des échecs) | Le Kernel **transporte** les événements, il n'en interprète jamais le contenu. |
| `configuration` | clés requises (type, valeur par défaut) ; `secretsRefs` (clé, fournisseur, chemin) | **Aucune valeur de secret** (P-09). |
| `resiliency` | délai, nouvelles tentatives, disjoncteur demandés à ceux qui appellent ce service (facultatif) | Appliqué par l'infrastructure de communication ([08](08-moteurs-specialises.md)). |
| `interfaces` | interfaces annoncées à la couche agents (type : `openapi`, `mcp`…, référence, niveau de risque par défaut) | Opaque pour le Kernel. |
| `lifecycle` | ordre de démarrage, arrêt (délai de grâce, vidange des connexions), approbation manuelle | |

**Le modèle ne contient jamais** : une valeur de secret ; une règle métier ; une adresse IP ou un point d'accès codé
en dur d'un autre service (on **résout** par nom) ; une logique de traitement des événements.

Exemple (forme provisoire) : l'agenda du Service RH.

```yaml
apiVersion: egen.civitas.africa/v1alpha1
kind: EgenService
metadata:
  name: agenda-rh
  namespace: univ-x-rh
  labels: { tier: backend }
  annotations: { team: service-informatique }
spec:
  runtime: { type: container }
  deployment:
    image: registry.example.org/radicale:3.2.0
    resources: { cpu: 250m, memory: 256Mi }
    replicas: { min: 1, max: 2 }
    updateStrategy: { type: rolling }
    security: { runAsNonRoot: true, readOnlyRootFilesystem: true, dropAllCapabilities: true }
  network:
    endpoints: [ { name: http, protocol: http, port: 5232, path: /.web/ } ]
    health: { http: { endpoint: /.web/, interval: 10s, timeout: 2s, failuresBeforeUnhealthy: 3 } }
  dependencies:
    resources:
      - { name: stockage, type: object-storage, params: { quotaGb: 50 } }
      - { name: notifications, type: messaging }
  configuration:
    secretsRefs: [ { key: STORAGE_KEY, provider: openbao, path: secret/data/agenda-rh/stockage } ]
```

## Cycle de vie d'un service

Le champ `status.phase` résume l'état ; les **Conditions** (type, `status`, `reason` stable, `message` lisible
ne citant que des noms) en donnent la cause.

```
Declared → Resolving → Deploying → Running ⇄ Degraded
                          │            │
                          ▼            ├→ Updating → Running (ou retour arrière → Running)
                        Failed         └→ Stopping → Stopped → Removing → Removed
Failed / Stopped → Declared   (une nouvelle déclaration du même service le relance)
```

| Phase | Sens |
|---|---|
| `Declared` | Intention validée et enregistrée ; rien n'est encore délégué. |
| `Resolving` | Résolution de la configuration, des secrets (par référence), des dépendances et des flux d'événements. |
| `Deploying` | Délégation faite, attente de la convergence. |
| `Running` | État nominal : voulu et observé convergent. |
| `Degraded` | Convergence partielle ; retour à `Running` si elle se rétablit, `Failed` après un délai (10 minutes par défaut, configurable). |
| `Updating` | Nouvelle génération en cours de déploiement sur une version `Running` ou `Degraded` ; retour arrière automatique possible. |
| `Stopping`, `Stopped` | Arrêt gracieux, puis arrêté (le manifeste reste, le service peut être relancé). |
| `Removing`, `Removed` | Suppression, puis suppression terminée. |
| `Failed` | Le Kernel ne converge pas malgré les tentatives. Ne se corrige pas seul. |

Règles à respecter (reprises comme exigences) :

- **Pas de nouvelle tentative infinie et silencieuse** : le compteur d'échecs est plafonné et visible (P-14).
- Une cause **externe** (secret absent, bus de messages injoignable) maintient le service dans sa phase, avec la
  Condition à `False` et une nouvelle tentative à attente croissante (2, 4, 8, 16, 32 secondes, puis 60 secondes au
  plus entre deux essais) ; elle ne le fait **pas** passer en `Failed`.
- Un service dont une dépendance `required` n'est pas prête **n'est pas déployé**.
- Un service dont les flux d'événements ne sont pas garantis **n'est pas déployé** (publier dans un flux absent
  perdrait les premiers événements).
- **Arrêt** : le service est d'abord retiré de la découverte, puis arrêté (sinon des appels partent vers un service
  qui s'éteint).
- Un `Failed` ou `Stopped` est relancé par une nouvelle déclaration du même service, depuis `Declared`, pour que les
  contrôles de configuration, de secrets, de dépendances et de messagerie soient refaits.
- Le moteur **réapplique l'état voulu** à chaque cycle : toute modification manuelle chez le moteur est annulée ; la
  sûreté repose sur l'idempotence de chaque appel.

## Capacités et implémentations

Une capacité est une **interface** (ressource composite) avec **une ou plusieurs implémentations** (Compositions).
Exemple : `workload-runtime` a une implémentation « kubernetes » et, si l'extension est installée, une
implémentation « nomad » ([moteurs/nomad.md](moteurs/nomad.md)). Le Control Plane choisit l'implémentation par des
**sélecteurs** (étiquettes), la politique locale et le contexte ; l'appelant ne voit que la capacité (C2).

## Extensions

Une extension est un **paquet installable** (image de type OCI) qui ajoute des implémentations de capacités, des
fonctions de composition ou un moyen de joindre un moteur. Elle s'installe, se met à jour et se désinstalle **sans
reconstruire** le Control Plane ; ses dépendances sont déclarées dans le paquet. Un **paquet d'écosystème** regroupe
ce qu'il faut pour déployer un écosystème (types, compositions, politiques, services initiaux). La signature et la
vérification des paquets sont à définir (VR-16).

## L'opérateur EGEN

Seule pièce de code applicatif du Kernel (avec le pont). Il :

1. crée et fait vivre les ressources `EgenOperation` (phases, conditions, tentatives, incertitude) ;
2. traduit une demande C3 en ressources Crossplane et suit leur état ;
3. publie les événements C5 (CloudEvents sur NATS) ;
4. applique la **garde de politique** avant toute délégation (consultation de SpiceDB) ;
5. calcule le résumé `status.phase` d'un `EgenService` à partir des Conditions.

Il **ne** réconcilie **pas** les ressources, **n'**exécute **aucun** déploiement et **ne** contient **aucun** code propre à un
moteur. Il est idempotent, redémarrable, et élit un chef parmi ses réplicas (mécanisme standard de Kubernetes).
Son langage est un point ouvert (PO-04).

## Politiques locales

| Mécanisme | Sert à |
|---|---|
| RBAC Kubernetes | Qui peut lire ou modifier quels types de ressources dans quel espace de noms. |
| Règles d'admission (ValidatingAdmissionPolicy, écrites en CEL) | Refuser une ressource non conforme : pas de conteneur privilégié, durcissement obligatoire, étiquettes réservées respectées. |
| Normes de sécurité des pods (niveau `restricted`) | Durcissement par défaut des charges. |
| SpiceDB, consulté par le pont et l'opérateur | Droits fins : cet acteur peut-il demander cette capacité sur ce périmètre ? |

Toute opération non déclarée ou non autorisée est refusée (G-09). Détail : [07](07-identite-confiance-securite.md).

## Autorité sur les ressources

Un Control Plane est **l'autorité unique** des ressources qu'il administre (P-04). Une ressource peut être
**administrée** (le Control Plane la crée et la réconcilie) ou **référencée** (elle existe déjà ailleurs ; le Control
Plane l'observe sans la modifier). Une même ressource n'est jamais administrée par deux Control Planes. L'autorité
est lisible sur la ressource (étiquette `egen.civitas.africa/authority`).

## Exemple de bout en bout

Étape C de [03](03-architecture-generale.md) : le pont de fédération du Control Plane Métier reçoit la demande
`workload-runtime` (C3). Il vérifie le certificat de l'appelant et le jeton, consulte SpiceDB, puis l'opérateur crée
l'`EgenOperation` (`Accepted`) et le `EgenService` `agenda-rh` ci-dessus. Crossplane compose les ressources du moteur
choisi ; Kubernetes déploie ; les Conditions passent à `Ready` ; la phase du `EgenService` devient `Running` ; l'opérateur
fait passer l'`EgenOperation` à `Succeeded` et publie `operation.succeeded.v1`.

## Ce que ce Kernel ne fait pas

- Il ne déploie pas lui-même, ne transporte pas de messages, ne stocke pas de secrets, ne gère pas de workflows.
- Il ne connaît aucun concept de Plateforme ni de métier (P-10).
- Il n'embarque aucun adaptateur propre à un moteur dans son cœur (G-05) : tout passe par des extensions.
