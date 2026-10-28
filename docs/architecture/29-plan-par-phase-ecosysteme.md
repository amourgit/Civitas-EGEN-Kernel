# 29 — Plan détaillé par phase de l'écosystème multi-solutions

*(Ce document dit **quoi faire, dans quel ordre, avec quels critères de
sortie**, pour tout ce qui est décidé en [22](22-ecosysteme-multi-solutions-et-frontiere.md)
à [28](28-portail-et-integration-des-interfaces.md). Le **statut** de chaque
phase et de chaque tâche (fait / en cours / à faire / reporté) est tenu dans
**un seul endroit** : [19 — Feuille de route](19-feuille-de-route.md). Ce
document n'en porte aucun, pour éviter deux sources de vérité.)*

## 29.1 — Conventions

- **Phases d'écosystème A à H** : distinctes des **phases du Kernel 0 à 6**
  ([19](19-feuille-de-route.md)). La phase **A** est la seule qui modifie le
  dépôt du Kernel ; **B à H** se déroulent dans le Projet Métier
  (`Civitas-EGEN-Business`).
- **Règle de portée** : chaque phase implémente **uniquement** ce qu'elle
  décrit, en briques modulaires bien posées ; rien n'est anticipé pour une
  phase suivante (consigne du projet).
- **Contrats d'API** : les chemins et la sémantique sont fixés dans les
  documents 23 à 28 ; les schémas JSON exacts (OpenAPI) sont un **livrable de
  début de phase**, relu **avant** le premier code de la phase.
- **Gabarit** de chaque phase : Objectif · Prérequis · Livrables · Tâches ·
  Tests d'acceptation · Critères de sortie · Risques · Hors périmètre.
- Les valeurs chiffrées de performance sont des **objectifs initiaux** mesurés
  dans la phase concernée ; un écart mesuré est consigné et traité, jamais
  ignoré.

## 29.2 — Exigences communes à tout service de l'écosystème

Applicables dès sa première livraison (elles ne sont pas répétées dans chaque
phase) :

1. **Manifeste** `ServiceManifest` valide, `team` conforme à
   [22.4](22-ecosysteme-multi-solutions-et-frontiere.md#224--les-composants-de-lécosystème), dépendances `required` déclarées, bloc `health` (`/health`)
   et `lifecycle.shutdown.gracePeriod`.
2. **Santé** : `/health` (vivant) et `/ready` (dépendances prêtes).
3. **Mutations** : en-tête `Idempotency-Key` obligatoire ; réponses
   idempotentes rejouées.
4. **Observabilité** : traces OpenTelemetry avec propagation de `traceparent`
   et `egen_correlation_id`, logs structurés (`serviceId`, `traceId`,
   `egen_uid`), métriques RED (débit, erreurs, durée) ([15](15-observabilite.md)).
5. **Base de données** : migrations **uniquement vers l'avant**, versionnées ;
   sauvegarde et **restauration testée** documentées.
6. **Outbox transactionnelle** pour tout événement publié ou toute projection ;
   consommateurs **level-triggered** (relecture d'état).
7. **Audit append-only** des opérations listées en [25.8](25-identite-et-autorisations.md#258--audit-d22).
8. **API** : OpenAPI publié ; SemVer ; compatibilité ascendante en lecture
   ([13](13-api-et-contrats.md)).
9. **Tests** : niveaux 1 à 3 de [17](17-strategie-de-tests.md) ; règles
   d'architecture exécutées en CI (aucune dépendance interdite) ; **un
   scénario de niveau 5** par phase ([29.12](#2912--tests-de-niveau-5-de-lécosystème)).
10. **Runbook** : procédure d'exploitation pour chaque état d'échec décrit
    dans la phase.
11. **Licences** : Apache 2.0/MIT/BSD ou équivalent permissif pour tout
    composant embarqué (règle du projet).

## 29.3 — Vue d'ensemble de l'ordre et des dépendances

| Phase | Contenu | Dépend de | Peut avancer en parallèle de |
|---|---|---|---|
| **A** | Préparation du Kernel (documentation, conventions, API de publication de configuration, garde-fou d'architecture) | — | — |
| **B** | `cell-registry-service` | A (conventions) | Début de C (infrastructure) |
| **C** | Keycloak, OpenFGA, projecteur, test de charge | B (données d'appartenance) | — |
| **D** | `catalog-service`, `platform-operator-service`, `config-service` (niveaux techniques) | A (API configuration), B, C | — |
| **E** | Premier domaine complet : `agenda` | D | F |
| **F** | Configuration complète (niveaux 4 à 6, verrous, aperçu, approbation) | D (et B, C) | E |
| **G** | Portail | D (liste), E (une vraie application) | Début possible dès D avec le domaine fixture |
| **H** | Échanges explicites, remplacement et migration | E, F | — |

Les phases se font **dans l'ordre** pour ce qui est de leurs critères de
sortie ; le parallélisme ne dispense jamais d'un critère.

---

## 29.4 — Phase A — Préparation du Kernel

**Objectif.** Faire en sorte que le Kernel puisse porter l'écosystème **sans
rien savoir de lui**, avec la plus petite modification possible, et verrouiller
cette ignorance par un test.

**Prérequis.** Branche `main` du Kernel verte en CI.

**Livrables.**

| N° | Livrable | Détail normatif |
|---|---|---|
| A1 | Documentation | Documents 22 à 29 et mises à jour des documents existants (01, 02, 03, 05, 06, 07, 08, 13, 14, 16, 17, 18, 19, 21, glossaire, références, README) |
| A2 | Convention de labels réservés | Préfixe `platform.civitas.africa/` ; clés définies en [06](06-service-manifest.md#labels-réservés-posés-par-la-plateforme) ; **aucun** changement de code : le Kernel conserve et restitue déjà `labels` |
| A3 | API de publication de configuration | Spécification complète ci-dessous |
| A4 | Garde-fou d'ignorance | Règle ArchUnit ci-dessous |
| A5 | Scénario de niveau 5 | Ci-dessous |

**A3 — API de publication de configuration.** Le contrat normatif complet (requête, sémantique de remplacement, validation, réponse, sécurité) est dans [13](13-api-et-contrats.md#publication-de-configuration--put-apiv1servicesidconfiguration) — source unique. Modules touchés : `egen-api` (ressource), `egen-application` (cas d'usage), `egen-contracts` (OpenAPI). **`egen-domain` : aucune modification** (critère de sortie).

**A4 — Garde-fou d'ignorance.** Règle d'architecture exécutée en CI : aucun
type, paquet ni champ de `egen-domain`, `egen-application`, `egen-api` ni
`egen-bootstrap` dont le nom correspond (insensible à la casse) à
`cell`, `cellule`, `tenant`, `organization`, `organisation`, `assignment`,
`affectation`, `catalog`, `catalogue`, `domainContract`. Les **adapters** sont
exclus de la règle (l'API « catalog » de Consul y est légitime). Le test
vérifie qu'une violation volontaire échoue effectivement (comme
[17, niveau 4](17-strategie-de-tests.md#niveau-4--tests-darchitecture-fitness-functions)).

**A5 — Scénario de niveau 5.** Dans `EgenAcceptanceE2ETest` (ou un test
voisin) : un service « fixture de plateforme » est déclaré **uniquement par
l'API publique** avec `team: platform-operator`, les labels réservés et un
bloc `configuration` ; la configuration est publiée via A3 puis relue ;
le service converge vers `RUNNING` ; les labels sont restitués intacts par
`GET /api/v1/services/{id}`. Cela prouve le garde-fou n°5
([02](02-principes-fondamentaux.md)) pour la plateforme.

**Critères de sortie.**
1. CI verte (build, tests, ArchUnit, Qodana).
2. `git diff --stat main…<branche> -- egen-domain/` est **vide** pour tout le
   code applicatif de la phase A.
3. A3 couvert par tests niveaux 1 (validation), 2 (use case), 3 (Consul réel),
   4 (A4), 5 (A5).
4. Documentation relue ; liens vérifiés ; statuts de [19](19-feuille-de-route.md) à jour.

**Risques.** Tentation d'ajouter un filtre de labels ou un champ « cellule » au
Kernel : **refusé** (D01, D20). Un besoin réel doit d'abord être exprimé à
l'équipe plateforme et passer en revue d'architecture.

**Hors périmètre.** Namespaces Nomad par tenant (A-X2), différenciation des
`runtime.type` (A-X1), filtres de liste, tout code de l'écosystème.

---

## 29.5 — Phase B — `cell-registry-service`

**Objectif.** Un registre de cellules complet : types, arbre, appartenances,
audit, projection — conforme à [23](23-cellules.md).

**Prérequis.** A1, A2 terminés. Base PostgreSQL disponible avec `ltree`.

**Livrables.**
1. Schéma OpenAPI (début de phase, relu).
2. Service `cell-registry-service` + manifeste + migrations.
3. Tables : `cell_type`, `cell`, `membership`, `audit_log`, `outbox`.
4. Toutes les opérations de [23.5](23-cellules.md#235--opérations-et-règles) avec leurs règles.
5. Événements de [23.9](23-cellules.md#239--événements-publiés-via-la-messagerie-du-kernel) publiés via l'outbox.
6. Données d'amorçage **optionnelles** : types d'exemple (université) clairement
   marqués « exemple », chargés par script, jamais dans le code.
7. Runbook : sauvegarde/restauration, rejouer l'outbox, corriger un arbre
   incohérent.

**Tâches.**
1. Modélisation SQL : `ltree`, index GiST sur `path`, unicité `(parent_id, code)`.
2. Validation des attributs par schéma JSON (bibliothèque standard JSON Schema 2020-12).
3. Verrouillage optimiste et transaction multi-cellules pour `move`.
4. Garde-fou `maxDepth` configurable (défaut 64).
5. Archivage, suspension, purge (rétention configurable).
6. Appartenances et règle d'héritage de rôle ([23.2](23-cellules.md#232--modèle-logique)).
7. Journal d'audit append-only ; interdiction d'`UPDATE`/`DELETE` sur la table
   (droits SQL + test).
8. Outbox + publication ; déclaration des types d'événements dans le
   manifeste (`events.publishes`).
9. Déploiement via le Kernel (`team: platform`).

**Tests d'acceptation.**
1. Arbre de 5 niveaux avec un **type inventé à chaud** par un `org_admin` :
   création sans redéploiement ni modification de code.
2. Refus : type incompatible avec le parent ; attribut invalide ; `code` en
   double chez les frères ; création sous parent `SUSPENDED`/`ARCHIVED` ;
   dépassement de `maxDepth`.
3. `move` : refusé si cycle, si autre racine, si type incompatible, si
   profondeur dépassée ; accepté sinon ; **aucune** donnée d'affectation à
   réécrire.
4. Collision d'identifiant de type entre portées `PLATFORM` et `ROOT` : refusée (D05).
5. Concurrence : deux `move` simultanés sur la même cellule → un seul réussit,
   l'autre reçoit `409`.
6. Archivage avec descendants actifs : refusé ; en cascade explicite : accepté.
7. Audit : toute écriture produit une ligne ; tentative de modification d'une
   ligne d'audit : impossible.
8. Isolation : un utilisateur de la racine R1 ne peut lire ni écrire aucune
   cellule de R2.
9. Perf (objectif) : lecture d'ancêtres d'une cellule de profondeur 64 et
   liste d'enfants dans un arbre de 100 000 cellules : p95 ≤ 50 ms.
10. Reprise : arrêt brutal pendant une écriture : aucun état partiel ; outbox
    rejouée sans doublon d'effet.

**Critères de sortie.** Tests 1 à 10 verts ; service `RUNNING` sous Kernel ;
runbook écrit ; aucune dépendance vers le Kernel autre que le déploiement et
la messagerie.

**Risques.** Déplacement massif d'un grand sous-arbre (verrous longs) → opération
par lots avec limite configurable ; ambiguïté de modélisation des types →
D03/D05 ; croissance de `audit_log` → partitionnement documenté, rétention
configurable.

**Hors périmètre.** Droits via OpenFGA (phase C), toute notion d'application.

---

## 29.6 — Phase C — Identité et autorisations

**Objectif.** Une identité unique, des droits calculés, et la **preuve mesurée**
que le modèle de droits tient sur de grandes hiérarchies ([25](25-identite-et-autorisations.md)).

**Prérequis.** B (appartenances et arbre disponibles).

**Livrables.**
1. Keycloak déployé **par le Kernel** (`team: platform`), realm défini **en
   code** (export versionné), persistance PostgreSQL.
2. *Protocol mapper* `egen_uid`, attribut immuable (D12).
3. Clients : portail (public, PKCE), `platform-operator-service`
   (*client credentials*, claim `team = platform-operator`), un client par
   service de plateforme, échange de jeton (RFC 8693) activé.
4. OpenFGA déployé par le Kernel ; *store* et **modèle** de
   [25.4](25-identite-et-autorisations.md#254--modèle-openfga) chargés par script
   reproductible.
5. **Projecteur** (dans `cell-registry-service`) : outbox → tuples
   ([25.4](25-identite-et-autorisations.md#254--modèle-openfga)) + **réconciliation périodique** (recalcule les tuples
   attendus depuis PostgreSQL et corrige les écarts).
6. Rapport du **test de charge** ([25.5](25-identite-et-autorisations.md#255--test-de-charge-bloquant-et-repli-pré-décidé-phase-c)) et décision consignée
   (modèle récursif conservé **ou** repli appliqué).
7. Runbook : rotation des clés, perte d'OpenFGA (reconstruction depuis
   PostgreSQL), import/export des utilisateurs.

**Tâches.** Configuration du realm ; mapper ; script de projection initiale ;
projecteur incrémental ; endpoint interne de **vérification** pour le contrôle
d'usage ; jeu de données de charge ; harnais de mesure (p95/p99) ; décision
écrite.

**Tests d'acceptation.**
1. Création d'un utilisateur : `egen_uid` généré, présent dans tous ses jetons ;
   toute tentative de modification de l'attribut échoue.
2. Un utilisateur appartenant à **deux** cellules de **deux** racines n'a
   qu'**un** compte et un seul `egen_uid`.
3. Héritage : `MANAGER` de la racine a les droits sur la cellule la plus
   profonde ; un `MEMBER` de la faculté **n'est pas** membre du département (D11).
4. `ORG_ADMIN` de R1 n'a aucun droit dans R2.
5. Le **test de charge** de [25.5](25-identite-et-autorisations.md#255--test-de-charge-bloquant-et-repli-pré-décidé-phase-c) est passé (ou le repli appliqué et re-testé).
6. Reconstruction : suppression totale du *store* OpenFGA puis reprojection :
   tous les droits retrouvés à l'identique (comparaison automatisée).
7. Changement de fournisseur simulé : export/import Keycloak vers un second
   realm : les droits d'un utilisateur de test sont **inchangés** (indexés par
   `egen_uid`).
8. Aucun jeton ne contient de droit de cellule (inspection automatisée des claims).
9. Fédération : un utilisateur d'un IdP de test est lié, sans doublon, à un
   utilisateur existant.

**Critères de sortie.** Tests 1 à 9 verts ; décision de modèle consignée dans
[25](25-identite-et-autorisations.md) ; Keycloak et OpenFGA `RUNNING` sous
Kernel ; runbooks écrits.

**Risques.** Limite de profondeur OpenFGA → test bloquant + repli pré-décidé ;
dérive entre PostgreSQL et OpenFGA → réconciliation périodique + métrique
d'écart ; mauvaise configuration du realm → realm en code, revue obligatoire.

**Hors périmètre.** Affectations, audiences, droits sur ressources métier.

---

## 29.7 — Phase D — Catalogue, instances, opérateur, configuration technique

**Objectif.** Faire coexister **plusieurs implémentations d'un même domaine**
dans une organisation, déployées par le Kernel, activées et affectées par
cellule — **avec un domaine fixture** (le domaine métier réel vient en phase E).

**Prérequis.** A3 livré ; B ; C. Domaine fixture `echo` : un contrat trivial
(une capacité `echo.message.send`) et **deux** implémentations conteneurisées
minimales (`echo-a`, `echo-b`) qui répondent différemment, pour démontrer la
coexistence sans dépendre d'un logiciel réel.

**Livrables.**
1. `catalog-service` : entités et règles de [24](24-catalogue-instances-affectations.md) (domaines, implémentations, versions, instances, activations, affectations, audience, résolution, contrôle d'usage, impact).
2. `platform-operator-service` : réconciliation de [24.10](24-catalogue-instances-affectations.md#2410--platform-operator-service).
3. `config-service` **niveau 1 à 3** : [27.8](27-configuration-multi-niveaux.md#278--phases).
4. Génération du `ServiceManifest` d'une instance (labels réservés, config, santé, dépendances, secrets par référence).
5. `GET /api/v1/instances/{id}/impact` : liste des cellules, affectations et utilisateurs touchés par un arrêt, une mise à jour ou un décommissionnement (« signaler les conséquences »).
6. Contrôle de compatibilité à la mise à jour : versions de dépendances (`requirements.dependsOn` → `dependencies.services` du manifeste) vérifiées **avant** la demande au Kernel.
7. Événements de [24.11](24-catalogue-instances-affectations.md#2411--api-ressources-normatives-de-catalog-service).
8. Runbooks : instance `FAILED`, divergence prolongée, orphelin Kernel, opérateur bloqué.

**Tâches (ordre).**
1. OpenAPI relu.
2. Modèle de données + migrations ; machine à états d'instance ([24.5](24-catalogue-instances-affectations.md#245--cycle-de-vie-dune-instance-et-correspondance-avec-le-kernel)).
3. Cycle d'approbation des versions ; règles de conformité (valeurs saisies manuellement en D ; calculées par la suite de tests en E).
4. Activation ; affectation ; unicité ; héritage ; coupure ; audience `SCOPE_MEMBERS`/`EXPLICIT` ; projection des tuples `EXPLICIT`.
5. Résolution et contrôle d'usage ([24.8–24.9](24-catalogue-instances-affectations.md#248--résolution-de-laffectation-effective)).
6. `config-service` niveaux 1–3 + schémas + historique + résolution technique.
7. Opérateur : construction du manifeste, `Declare` idempotent, publication de configuration (A3), lecture du statut, table de correspondance, orphelins.
8. Instances EXTERNAL : vérification de santé par un **connecteur factice** du domaine fixture.
9. Actions `stop`, `start`, `update`, `decommission` avec règles de [24.5](24-catalogue-instances-affectations.md#245--cycle-de-vie-dune-instance-et-correspondance-avec-le-kernel).
10. Isolation des jetons : `catalog-service` et `config-service` **sans** accès Kernel.

**Tests d'acceptation.**
1. Enregistrer le domaine `echo` ; deux implémentations ; une version chacune ;
   `DRAFT` → non sélectionnable ; `APPROVED` → sélectionnable.
2. Créer une instance MANAGED : passe `PLANNED` → `INSTALLING` → `INSTALLED`
   par la table de [24.5](24-catalogue-instances-affectations.md#245--cycle-de-vie-dune-instance-et-correspondance-avec-le-kernel) ; le service est visible dans le Kernel avec `team: platform-operator` et les labels réservés ; le service répond.
3. **Coexistence** : dans l'organisation R, instance `echo-a` affectée au
   service RH, instance `echo-b` affectée au service informatique : chaque
   cellule voit **son** outil ; l'autre ne voit pas le sien ; aucune donnée
   partagée.
4. **Héritage** : affectation sur la faculté avec `inherit = true` → un
   département la reçoit ; une affectation du département la **remplace** ;
   une coupure (`instanceId` nul) supprime l'outil pour le département et ses
   descendants.
5. **Trois états distincts** : implémentation `APPROVED` non installée → non
   affectable ; instance installée **non activée** pour R → affectation refusée ;
   instance activée **non affectée** → absente de toute résolution.
6. `SINGLE_TENANT` : seconde activation refusée ; `SHARED_ISOLATED` : deux
   organisations activées.
7. **Usages multiples** : deux affectations du même domaine dans une cellule
   avec `usage` différents : acceptées ; même `usage` : refusée (`409`).
8. Audience `EXPLICIT` : seuls les utilisateurs listés passent
   `check-use` ; `SCOPE_MEMBERS` : tout membre de la portée, selon héritage.
9. **Quatre opérations distinctes** : arrêt n'efface ni config ni affectation ;
   retrait d'affectation n'arrête rien ; désactivation n'affecte pas une autre
   organisation ; décommissionnement refusé tant qu'il reste des affectations
   (liste des bloquants).
10. Impact : `instances/{id}/impact` liste exactement les cellules et
    affectations touchées.
11. **Idempotence de l'opérateur** : arrêt brutal pendant un `Declare` puis
    redémarrage → **un seul** service dans le Kernel, état correct.
12. **Kernel indisponible** : l'opérateur réessaie avec délai croissant ; le
    catalogue reste cohérent ; retour du Kernel → convergence sans intervention.
13. Configuration : une clé `RESTART` modifiée produit une nouvelle génération
    et un retour à `INSTALLED` ; une clé `HOT` ne redémarre pas ; le
    comportement exact est **vérifié** (hypothèse de [27.6](27-configuration-multi-niveaux.md#276--lien-avec-le-kernel-d19)).
14. Orphelin : un service `platform-operator` créé à la main dans le Kernel est
    **signalé**, jamais supprimé.
15. Instance EXTERNAL : `INSTALLED` seulement quand la vérification de santé
    réussit ; aucun `ServiceManifest` produit.
16. Isolation : un appel de `catalog-service` vers l'API du Kernel est refusé
    (absence de jeton) ; seul l'opérateur réussit.
17. Perf (objectif) : `check-use` p95 ≤ 100 ms à 200 requêtes/s sur un jeu de
    100 000 cellules et 10 000 affectations.

**Critères de sortie.** Tests 1 à 17 verts ; trois services `RUNNING` sous
Kernel ; runbooks écrits ; hypothèse de [27.6](27-configuration-multi-niveaux.md#276--lien-avec-le-kernel-d19) tranchée par test et documentée.

**Risques.** Divergence catalogue/Kernel → réconciliation + métrique ;
explosion du nombre de services → runbooks et alertes dès cette phase ;
confusion des trois états → vocabulaire strict et tests 5 ; mise à jour qui
casse une dépendance → contrôle de compatibilité (livrable 6).

**Hors périmètre.** Contrat d'un vrai domaine (E), niveaux 4 à 6 de
configuration (F), portail (G), remplacement/migration (H).

---

## 29.8 — Phase E — Premier domaine complet : `agenda`

**Objectif.** Prouver la promesse centrale sur un vrai domaine : deux logiciels
d'agenda coexistent, chaque service a le sien, sans partage de données.

**Prérequis.** D.

**Livrables.**
1. Contrat `agenda` v1 ([26.2](26-contrats-de-domaine-et-connecteurs.md#262--le-contrat-de-domaine)) : API canonique OpenAPI, capacités M/O figées, limites.
2. `domain-gateway-agenda` conforme à [26.3](26-contrats-de-domaine-et-connecteurs.md#263--la-gateway-de-domaine).
3. Connecteur **CalDAV** avec deux **profils** : `radicale`, `nextcloud-calendar`.
4. *Domain Contract Test Kit* `agenda` ([26.6](26-contrats-de-domaine-et-connecteurs.md#266--suite-de-tests-de-contrat-domain-contract-test-kit)), exécuté en CI.
5. Deux versions d'implémentation enregistrées avec **conformité et capacités calculées** par la suite.
6. Provisionnement des comptes locaux ([25.2](25-identite-et-autorisations.md#252--identité-d12)).
7. Publication `interfaces` (MCP/OpenAPI) pour le système d'agents, **optionnelle** et seulement si le chantier agents l'a atteint.
8. Runbook : instance d'agenda inaccessible, compte local en échec.

**Tâches.** Rédiger le contrat ; implémenter la gateway (étapes de 26.3) ;
connecteur et profils ; harnais de test avec conteneurs éphémères ; calcul
automatique de conformité ; remplacer la conformité saisie manuellement (D).

**Tests d'acceptation.**
1. **Test décisif** : dans l'organisation R, le service RH utilise Radicale et
   le service administratif Nextcloud Calendar ; un événement créé par un
   utilisateur RH **n'apparaît pas** chez l'administratif.
2. Un utilisateur membre des deux services voit **deux** agendas distincts
   dans deux contextes, avec **un seul** compte EGEN.
3. Capacité optionnelle non supportée par une implémentation : `501`
   `CAPABILITY_NOT_SUPPORTED` nommant la capacité ; jamais une erreur obscure.
4. Conformité **calculée** : une régression volontaire du connecteur fait
   passer la version à `DEPRECATED` avec alerte.
5. `SHARED_ISOLATED` : suite de non-fuite — deux organisations sur une même
   instance ne voient jamais les données de l'autre.
6. Compte local : créé à la première utilisation autorisée ; supprimé/désactivé
   selon la politique à la fin d'appartenance.
7. Ajout d'une **nouvelle instance** d'une version déjà connectée : **zéro
   code**, configuration seule.
8. Instance EXTERNAL d'agenda (CalDAV hors Kernel) : santé vérifiée, utilisable
   par la gateway.
9. Résilience : instance injoignable → réponses d'erreur normalisées,
   circuit breaker conforme à [12](12-communication-fabric.md#sémantique-normative-de-la-résilience).
10. Aucune requête ne passe par la gateway sans `check-use` (test négatif :
    appel direct refusé).

**Critères de sortie.** Tests 1 à 10 verts ; contrat v1 figé ; conformité
automatique en CI ; runbooks.

**Risques.** Écarts de comportement CalDAV entre serveurs → profils (données)
et suite de tests ; compte local impossible à fédérer → limites affichées
(D17), pas masquées ; contrat trop ambitieux → capacités optionnelles.

**Hors périmètre.** Autres domaines (un par un, hors de ce plan), échanges
entre instances (H).

---

## 29.9 — Phase F — Configuration complète

**Objectif.** Toute la personnalisation par configuration, avec priorités,
verrous, traçabilité et contrôle avant application ([27](27-configuration-multi-niveaux.md)).

**Prérequis.** D (config V0), B, C. Peut avancer pendant E.

**Livrables.** Niveaux 4 à 6 ; verrous ; résolution fonctionnelle ; aperçu
(`changes:preview`) ; approbation ; interface d'administration de la
configuration ; runbooks.

**Tâches.** Modèle de données des niveaux 4–6 ; chaîne de cellules par `ltree` ;
règles de priorité et verrous (27.4) ; schéma `x-egen-*` complet ;
`preview` et jeton d'aperçu ; approbation `ONE_APPROVER` ; export de
l'« explication » de chaque valeur.

**Tests d'acceptation.**
1. Valeur à la racine, surcharge au département : le département obtient sa
   valeur ; son parent garde la sienne.
2. Verrou posé à la racine sur une clé `x-egen-lockable` : une écriture
   à un niveau plus spécifique est **refusée** avec l'auteur du verrou ;
   verrou sur clé non verrouillable : refusé.
3. Deux verrous (organisation et cellule) : celui de l'organisation prime.
4. **Politique de sécurité** imposée au niveau plateforme : **impossible** à
   contourner par une cellule, une affectation ou un utilisateur.
5. Clé inconnue ou valeur invalide : refusée.
6. Aucune valeur de secret n'est acceptée ; une référence invalide est refusée.
7. `changes:preview` en production : impacts exacts ; application sans jeton
   d'aperçu refusée.
8. Approbation : l'écriture reste `PENDING` jusqu'à validation par un autre
   `manager`.
9. **Explication** : chaque valeur effective retourne son niveau, sa cible, son
   verrou, sa version.
10. Retour arrière : nouvelle version reprenant l'ancienne valeur ; historique
    complet conservé.
11. **Aucune migration** : les données et contrats de D restent valides sans
    transformation.

**Critères de sortie.** Tests 1 à 11 verts ; runbooks.

**Risques.** Complexité de résolution → toujours expliquer l'origine ;
verrous mal compris → messages explicites et documentation d'usage.

**Hors périmètre.** Interface de personnalisation du portail (G).

---

## 29.10 — Phase G — Portail

**Objectif.** Un utilisateur voit ses applications, de manière cohérente avec
tous les droits, sans que le portail ne décide de rien ([28](28-portail-et-integration-des-interfaces.md)).

**Prérequis.** D (liste), E (au moins une vraie application) ; peut démarrer dès D avec le domaine fixture.

**Livrables.** `GET /api/v1/me/applications` ; micro-frontend « mes applications » ;
modes `LINK`, `EMBED`, `API_ONLY` ; SSO OIDC ; badges (conformité partielle,
compte séparé) ; cache invalidé par événements.

**Tâches.** Endpoint et cache ; micro-frontend (single-spa, système de tokens
du monorepo) ; gestion des modes et des contraintes d'intégration ; affichage
des limites.

**Tests d'acceptation.**
1. Utilisateur du seul service administratif : voit ses outils, pas ceux des RH.
2. Utilisateur dans deux services : **union** des outils, deux agendas séparés.
3. Remplacement de l'instance d'une affectation : l'`entryPoint` change ;
   **aucun** déploiement du portail ; les autres entrées sont inchangées.
4. Instance `FAILED` ou `STOPPED` : entrée marquée indisponible avec raison.
5. Application dont les en-têtes interdisent l'intégration : `EMBED` non proposé.
6. Application sans SSO : badge « compte séparé » visible.
7. Retrait d'une appartenance : l'application disparaît après invalidation
   (délai mesuré ≤ 5 s sur événement).
8. Aucun droit n'est recalculé côté client (revue + test).
9. Perf (objectif) : réponse `me/applications` p95 ≤ 300 ms à froid, ≤ 50 ms en cache.

**Critères de sortie.** Tests 1 à 9 verts ; runbook.

**Risques.** Cache périmé → invalidation par événements et relecture complète ;
intégration d'interface instable → `LINK` par défaut.

**Hors périmètre.** Éditeur d'organisation (peut utiliser directement l'API de
[23](23-cellules.md)).

---

## 29.11 — Phase H — Échanges explicites, remplacement et migration

**Objectif.** Permettre le **remplacement** d'une application pour un seul
service et les **échanges** explicites, sans jamais prétendre à une
substitution transparente (vision, sections 12 et 14).

**Prérequis.** E, F.

**Livrables.**
1. `exchange_rule` ([26.7](26-contrats-de-domaine-et-connecteurs.md#267--données--aucune-synchronisation-implicite-d18)) + exécution `ONE_SHOT` (workflow du Kernel avec compensation) et `CONTINUOUS`.
2. Table `id_map` conservée.
3. **Rapport de différences de capacités** entre deux versions d'implémentation d'un même domaine (capacités perdues, limites nouvelles), **obligatoire** avant tout remplacement.
4. **Procédure de remplacement** outillée (ci-dessous) et son runbook.
5. Piloter par **audience `EXPLICIT`** un test limité avant bascule.

**Procédure de remplacement (par cellule, jamais globale).**
1. Installer la nouvelle instance (`INSTALLED`), **sans** affectation.
2. Générer le **rapport de différences** ; le responsable doit l'accepter
   explicitement (une capacité obligatoire perdue bloque, sauf dérogation).
3. Affecter la nouvelle instance en audience `EXPLICIT` à un groupe pilote.
4. Migrer les données (`ONE_SHOT`, workflow avec compensation) ; `id_map` créé.
5. Basculer l'affectation (ou la coupure d'héritage) pour la cellule.
6. Surveiller ; retour arrière possible (réaffectation de l'ancienne instance)
   tant que l'ancienne n'est pas décommissionnée.
7. Décommissionner l'ancienne avec **disposition explicite des données**
   (conserver / migrer / purger), journalisée.

**Tests d'acceptation.**
1. Remplacer Radicale par Nextcloud Calendar pour **un** service : les autres
   services ne sont **pas** touchés ; aucun changement global.
2. Le rapport de différences liste exactement les capacités perdues ;
   l'acceptation est tracée.
3. Échec au milieu d'une migration : compensation exécutée, ancienne instance
   intacte, état cohérent.
4. `id_map` : une référence externe à l'ancien identifiant se résout vers le nouveau.
5. Règle d'échange `ONE_WAY` en `CONTINUOUS` : propagation correcte ;
   conflit traité selon `conflictPolicy` ; `MANUAL` produit une tâche, jamais
   un écrasement silencieux.
6. Règle d'échange entre **deux organisations** sans accord des deux
   `org_admin` : refusée.
7. Retour arrière après bascule : l'ancienne instance reprend son rôle sans perte.
8. Aucun échange n'existe sans `exchange_rule` (test négatif : aucune
   synchronisation implicite).

**Critères de sortie.** Tests 1 à 8 verts ; runbook de remplacement.

**Risques.** Migration lourde → par lots et reprise ; différences fonctionnelles
sous-estimées → rapport obligatoire ; conflits → politique explicite.

**Hors périmètre.** Migration entre produits de domaines différents.

---

## 29.12 — Tests de niveau 5 de l'écosystème

Un scénario de bout en bout par phase, rejoué **en CI** contre l'environnement
éphémère complet (Kernel + Keycloak + OpenFGA + services de l'écosystème), et
conservé comme test de non-régression :

| Phase | Scénario |
|---|---|
| A | Service fixture de plateforme déclaré, configuré, observé, labels restitués |
| B | Création d'un arbre, type inventé, déplacement, audit |
| C | Utilisateur multi-cellules, droits hérités, reconstruction OpenFGA |
| D | Deux implémentations `echo` coexistent dans une organisation, affectées à deux cellules |
| E | Radicale et Nextcloud Calendar côte à côte, aucun partage |
| F | Verrou de sécurité non contournable |
| G | Un utilisateur, deux services, deux agendas dans le portail |
| H | Remplacement pour un service seulement, avec compensation d'un échec volontaire |

Chaque phase ultérieure **ne doit jamais casser** les scénarios des
précédentes.

## 29.13 — Registre des risques transverses

| N° | Risque | Parade | Phase |
|---|---|---|---|
| R1 | Un concept d'écosystème s'infiltre dans le Kernel | Règle A4 en CI ; revue ; D01/D20 | A, continu |
| R2 | Limite de profondeur de résolution d'OpenFGA | Test bloquant, repli pré-décidé | C |
| R3 | Dérive PostgreSQL ↔ OpenFGA | Outbox, réconciliation, métrique d'écart | B, C |
| R4 | Dérive catalogue ↔ Kernel | Opérateur level-triggered, orphelins signalés | D |
| R5 | Nombre de services à exploiter | Runbooks par phase, alertes, SLO | B à H |
| R6 | Comptes locaux impossibles à fédérer | Limites déclarées et visibles | E, G |
| R7 | Migration de données entre implémentations | Rapport de différences, compensation, `id_map` | H |
| R8 | Fuite entre organisations sur instance partagée | Suite de non-fuite, déclaration `tenancyModel` vérifiée | E |
| R9 | Besoin d'isolation d'infrastructure par tenant | Déclencheur A-X2 ([19](19-feuille-de-route.md)) | hors plan |
| R10 | Secret en clair dans la configuration | Schémas `x-egen-secret-ref`, refus à l'écriture, tests F6 | D, F |
| R11 | Jeton Kernel détenu par un autre service que l'opérateur | Test D16 ; revue de sécurité | D |

## 29.14 — Definition of Done de l'écosystème

- [ ] Deux logiciels d'un même domaine coexistent dans une organisation, chacun
      affecté à un service, sans partage de données (E1).
- [ ] Une nouvelle instance d'une version déjà connectée s'ajoute **sans code** (E7).
- [ ] Un type de cellule et un niveau hiérarchique sont ajoutés **sans code** (B1).
- [ ] Un utilisateur dans plusieurs services n'a **qu'une** identité (C2, G2).
- [ ] Aucune règle de sécurité globale n'est contournable par un service (F4).
- [ ] Le remplacement d'une application ne touche que le service concerné (H1).
- [ ] Le Kernel n'a **reçu** aucune notion d'écosystème : `egen-domain`
      inchangé par ce chantier, règle A4 verte.
- [ ] Les scénarios de niveau 5 A à H passent en CI.
