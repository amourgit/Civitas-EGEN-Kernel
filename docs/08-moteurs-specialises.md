# 08 — Les moteurs spécialisés

## À quoi sert ce document
Dire quel moteur réalise quelle capacité, où se place chaque moteur dans l'architecture (dont **Nomad**), comment un
moteur est choisi, ajouté ou remplacé, et comment plusieurs Control Planes peuvent en partager un.

## Ce qu'il ne couvre pas
Le détail d'exploitation de chaque moteur : voir les fiches dans [moteurs/](moteurs/README.md).

## Ce qu'est un moteur spécialisé

Un **moteur spécialisé** est un système qui exécute réellement un travail précis. Le Control Plane le **sollicite** ; il ne
le remplace pas (G-03). Un moteur **n'est pas nécessairement un Control Plane** : Kubernetes ou NATS ne parlent pas les
contrats EGEN et n'ont pas à le faire. Seul un système qui expose C1 à C6 est un Control Plane.

## Catalogue : capacités et moteurs

| Capacité (C2) | Moteur par défaut | Autres moteurs possibles | Fiche |
|---|---|---|---|
| `workload-runtime` (exécuter des charges) | **Kubernetes** | **Nomad** (conservé, voir ci-dessous) | [kubernetes](moteurs/kubernetes.md), [nomad](moteurs/nomad.md) |
| Composition et extensions (interne au Kernel) | **Crossplane** | — | [crossplane](moteurs/crossplane.md) |
| Workflows durables et Sagas (Federation Plane) | **Temporal** | Dapr Workflow (repli, non retenu) | [temporal](moteurs/temporal.md) |
| `messaging` et événements | **NATS JetStream** (CloudEvents) | Kafka (par une extension) | [messagerie-nats](moteurs/messagerie-nats.md) |
| `secrets` | **OpenBao** | Vault (licence à source disponible, PO-05) | [secrets-openbao](moteurs/secrets-openbao.md) |
| Identité des charges, des personnes, droits | **SPIFFE/SPIRE, Keycloak, SpiceDB** | — | [identite-spire-keycloak-spicedb](moteurs/identite-spire-keycloak-spicedb.md) |
| `service-discovery` | Services Kubernetes (DNS interne) | Enregistrement natif de Nomad ; Consul (PO-05) | [kubernetes](moteurs/kubernetes.md), [nomad](moteurs/nomad.md) |
| `object-storage` | À choisir (stockage compatible S3) | — | PO-11 |
| `observability` | **OpenTelemetry** + outils de collecte | — | [10](10-evenements-observabilite-audit.md) |
| Communication entre services (options) | **Dapr**, capacité optionnelle offerte aux services | Maillage de services (non étudié) | PO-07 |

## Où se place Nomad (décision D-03)

| Question | Réponse |
|---|---|
| **Quel niveau ?** | Niveau 3 : **moteur spécialisé**. Jamais un Control Plane, jamais le socle du Kernel. |
| **Quelle capacité ?** | `workload-runtime` : exécution de charges de travail. Il est une **implémentation** de cette capacité, à côté de celle de Kubernetes. |
| **Comment est-il choisi ?** | Par un sélecteur (`engine: nomad`) dans l'`EgenService`, ou par une politique du Control Plane (ex. charges qui ne sont pas des conteneurs, sites légers). Sans sélecteur ni politique, c'est Kubernetes. |
| **Où est-il installé ?** | Dans une **extension** « workload-runtime pour Nomad » ajoutée au Control Plane. Aucun code propre à Nomad dans le cœur (G-05). |
| **Qui pilote le Control Plane ?** | Toujours un Kernel de référence (Kubernetes + Crossplane). Nomad exécute ; il ne gouverne pas. |
| **Peut-il cohabiter avec Kubernetes ?** | Oui : plusieurs moteurs dans un même Control Plane sont un cas prévu (scénario A des fondations). |
| **Et un service déjà exécuté dans Nomad ?** | Il est **référencé** (`runtime.type: external`) sans être administré, ou **administré** si on le déclare. |
| **Que reste-t-il à décider ?** | Le **mécanisme d'intégration** (PO-01), la **licence** (PO-05, VR-08) et le sort de **Consul** (PO-05). |

Un site sans Kubernetes mais avec Nomad : le Control Plane qui le pilote doit pouvoir joindre l'API Nomad. Si le site
est isolé du réseau, il lui faut un Control Plane local (donc un petit cluster k3s) : on le dit ici pour que ce coût
soit connu (RK-05).

## Choisir, ajouter ou remplacer un moteur

**Critères d'adoption (tous obligatoires).**

1. Le besoin est une capacité de C2 ou une brique interne listée ici.
2. Le moteur est un projet open source reconnu, ou à source disponible avec **avis juridique documenté** (PO-05).
3. Il est maintenu activement ; sa gouvernance est connue (fondation neutre, société unique : on le note dans le registre des risques de [16](16-risques-et-verifications.md)).
4. Il peut être joint par une interface stable (API documentée).
5. Une **fiche** est écrite dans [moteurs/](moteurs/README.md) : rôle, ce qui est vérifié (avec date), ce qui reste à
   vérifier, pièges, plan de sortie.
6. Un test d'intégration contre le moteur **réel** existe avant toute mise en service.

**Ajouter** un moteur = installer une extension qui ajoute une implémentation de capacité. **Remplacer** = installer
la nouvelle implémentation, basculer la sélection, retirer l'ancienne ; l'appelant (C2/C3) ne voit aucun changement.
**Compatibilité de version** : chaque extension déclare les versions du moteur qu'elle supporte ; le contrôle de
compatibilité est un test de l'extension, rejoué à chaque nouvelle version mineure du moteur.

## Mutualisation d'un moteur

Plusieurs Control Planes peuvent s'appuyer sur le **même moteur** (par exemple un même cluster Nomad ou un même
service de messagerie). Règles :

- le moteur partagé est présenté par une **extension partagée**, installée et versionnée explicitement dans chaque
  Control Plane qui l'utilise ;
- chaque Control Plane garde **sa propre autorité** sur ses ressources dans le moteur (espaces de noms, quotas, comptes
  distincts) ;
- un Control Plane ne peut pas agir sur les ressources d'un autre dans le moteur partagé.

## Exemple de bout en bout

Étape C de [03](03-architecture-generale.md) : le Control Plane Métier a deux implémentations de `workload-runtime`. L'agenda
Radicale est un conteneur ordinaire : sans sélecteur, **Kubernetes** l'exécute. Un service de rapports fonctionnant comme
un binaire Java qui ne tourne pas en conteneur porte `engine: nomad` : la même demande C3 aboutit au moteur **Nomad**,
sans que l'appelant ne le sache.
