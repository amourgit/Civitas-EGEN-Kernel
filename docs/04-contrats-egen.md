# 04 — Les contrats EGEN (C1 à C6)

**Version du document : 0.2 (conventions figées en R1.1). Les contrats C1 à C6 sont figés à la fin de la phase R1.**

## À quoi sert ce document
Spécifier les six contrats par lesquels deux Control Planes (ou un Control Plane et le Federation Plane)
s'échangent identité, capacités, demandes, états et événements, **sans dépendre d'aucune technologie**.
Un système qui respecte ces contrats peut participer à la fédération, quelle que soit sa technologie interne.

## Ce qu'il ne couvre pas
La façon dont le Kernel de référence les réalise ([05](05-kernel-de-reference.md)), la sécurité du transport et
des jetons ([07](07-identite-confiance-securite.md)), l'orchestration d'une opération multi-Control Planes
([09](09-operations-distribuees.md)).

## Conventions communes

| Sujet | Règle |
|---|---|
| Transport | HTTPS, corps JSON en UTF-8. Un transport gRPC peut être ajouté plus tard sans changer le modèle. La sécurité du transport (mTLS) est vérifiée par la famille de tests `security` du kit de conformité (phase R3) ; les familles `base`, `delegation` et `events` s'exécutent en HTTP simple. |
| Authentification | Chaque appel porte un certificat de charge de travail (mTLS, identité de type SPIFFE) **et**, quand l'appel agit au nom d'une personne ou d'un agent, un jeton délégué à portée limitée ([07](07-identite-confiance-securite.md)). L'identité décisive vient du certificat et du jeton, **jamais** d'un champ du corps. |
| Préfixe | `/egen/v1/…` ; le document C1 est à `/.well-known/egen/controlplane`. |
| Dates | RFC 3339, UTC, suffixe `Z`. |
| Trace | En-tête `traceparent` (W3C Trace Context) propagé de bout en bout. |
| Clé d'idempotence | En-tête `Idempotency-Key` obligatoire sur toute demande qui modifie. |
| Version de contrat | En-tête facultatif `Egen-Contract-Version` (ex. `1.0`) : version du contrat utilisée pour l'appel. Une version non supportée donne `406` `NoCommonContractVersion`. |
| Pagination | `pageSize`, `pageToken`. |
| Évolution | Un lecteur **ignore** les champs inconnus (corps, paramètres). |
| Standards épinglés | JSON Schema **2020-12** ; OpenAPI **3.1.1** ; AsyncAPI **3.0.0** ; CloudEvents **1.0** (version de spécification 1.0.2, extension « Distributed Tracing » pour `traceparent`) ; W3C Trace Context. |

### Motifs normatifs des identifiants

| Identifiant | Motif (expression régulière) | Exemple |
|---|---|---|
| Identifiant de Control Plane | `^spiffe://[a-z0-9]([a-z0-9.-]*[a-z0-9])?/egen/cp/[a-z][a-z0-9-]{1,62}$` | `spiffe://univ-x.civitas.africa/egen/cp/metier` |
| Nom de capacité, d'opération | `^[a-z][a-z0-9-]{1,62}$` | `object-storage`, `create-bucket` |
| Identifiant d'opération | ULID : `^[0-9A-HJKMNP-TV-Z]{26}$` (trié par date de création) | `01JD6K3X8Q2M5N7P9R1S3T5V7W` |
| Clé d'idempotence | `^[A-Za-z0-9._:-]{8,128}$` | `7c1e9a52-4d3b-4f10-9a6e-2b8d5c1f0e33` |
| Périmètre | segments séparés par `/` : `^[a-z0-9][a-z0-9-]*(/[a-z0-9][a-z0-9-]*)*$` ; dans le champ `scopes` d'une capacité, le dernier segment peut être `*` | `univ-x/rh` ; `univ-x/*` |
| Raison (`reason`) | PascalCase : `^[A-Z][A-Za-z0-9]{2,63}$` | `PolicyDenied` |
| Type d'événement | `^africa\.civitas\.egen\.[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)*\.v[0-9]+$` | `africa.civitas.egen.operation.succeeded.v1` |
| Version de contrat | `^[0-9]+\.[0-9]+$` | `1.0` |

### Mots réservés (P-10)

Aucun nom défini par EGEN (types, capacités, opérations, raisons, types d'événements, schémas, champs) ne contient, en
minuscules, l'un des mots : `cellule`, `tenant`, `catalogue`, `affectation`, `portail`. Un test d'architecture le vérifie
([13](13-tests-et-conformite.md)). Le Kernel **transporte** des valeurs appartenant à la Plateforme sans les interpréter.

### Noms des types dans le Kernel de référence

Pour éviter toute collision dans l'API Kubernetes (le type `Service` existe dans Kubernetes ; le type `Operation` existe
dans Crossplane 2.x, groupe `ops.crossplane.io`), les types du Kernel de référence s'appellent **`EgenService`**,
**`EgenOperation`** et **`ControlPlaneConfig`** (groupe `egen.civitas.africa`). Les contrats, eux, parlent d'« Operation » :
c'est le nom de la ressource REST (`/egen/v1/operations`).

### Forme d'une erreur

```json
{
  "code": 403,
  "reason": "PolicyDenied",
  "message": "capacité object-storage refusée pour le périmètre univ-x/rh",
  "retryable": false,
  "operationId": null
}
```

- `reason` est **stable** et lisible par une machine : on ne le reformule jamais.
- `message` est lisible par une personne, **borné à 500 caractères**, et ne cite que des **noms** (clés, chemins,
  types d'événements), jamais une valeur de configuration ni de secret (G-08).
- `retryable` dit si rejouer la même demande a un sens.

### Raisons d'erreur standard

Un Control Plane peut définir d'autres raisons (PascalCase) ; celles-ci sont **obligatoires** dans les situations décrites.

| Raison | HTTP | Situation |
|---|---|---|
| `InvalidParameters` | 400 | Corps ou paramètres non conformes au schéma |
| `IdempotencyKeyRequired` | 400 | `Idempotency-Key` absent sur une demande qui modifie |
| `DeadlineInPast` | 400 | `deadline` déjà dépassée |
| `PolicyDenied` | 403 | Refus par une politique (locale ou fédérée) |
| `UnknownCapability` | 404 | Capacité ou opération de capacité inconnue |
| `UnknownOperation` | 404 | Identifiant d'opération inconnu |
| `IdempotencyKeyConflict` | 409 | Même clé, contenu différent |
| `ResourceBusy` | 409 | Une autre opération agit sur la même ressource |
| `OperationTerminal` | 409 | Annulation d'une opération déjà terminée |
| `NoCommonContractVersion` | 406 | Aucune version de contrat commune |
| `TooManyRequests` | 429 | Charge ; `retryable: true` |
| `Unavailable` | 503 | Indisponibilité ; `retryable: true` |

## C1 — Identité et description d'un Control Plane

**But.** Dire qui est ce Control Plane, ce qu'il parle et où le joindre.
**Où.** `GET /.well-known/egen/controlplane` — document signé.

| Champ | Sens |
|---|---|
| `id` | Identifiant du Control Plane (voir conventions). |
| `name` | Nom lisible. |
| `owner` | Organisation responsable. |
| `environment` | Environnement de gouvernance auquel il appartient. |
| `kind` | `kernel`, `external` ou `federation`. |
| `implementation` | Nom et version de l'implémentation. **Informatif : ne décide de rien** (P-11). |
| `contracts` | Versions de chaque contrat supportées, ex. `{"C1":["1.0"],"C3":["1.0"]}`. |
| `endpoints` | Adresses de l'API (C2, C3, C4), du flux d'événements (C5) et du point de publication des clés de confiance. |
| `conformance` | Niveau de conformité déclaré et version de la suite passée (voir ci-dessous). |
| `issuedAt`, `expiresAt` | Validité courte du document, renouvelée régulièrement. |

**Format.** Le corps de la réponse est un objet `{ "descriptor": {…}, "jws": "<signature>" }`. `jws` est une signature
JWS en sérialisation compacte (RFC 7515) dont la charge utile est le JSON du `descriptor` encodé en base64url ;
algorithmes admis : `ES256` et `EdDSA`. Un lecteur décode la charge utile, vérifie la signature avec la clé publique du
domaine de confiance du Control Plane, et vérifie que la charge utile **est identique** au `descriptor` fourni.

**Règles.** Un Federation Plane refuse un document expiré ou mal signé. Un changement de C1 publie un événement (C5).

## C2 — Capacités

**But.** Décrire ce que le Control Plane sait faire, indépendamment de la technologie qui le réalise.
**Où.** `GET /egen/v1/capabilities` (liste), `GET /egen/v1/capabilities/{nom}`.

| Champ | Sens |
|---|---|
| `name`, `version` | Nom de la capacité et sa version. |
| `description` | Phrase simple. |
| `operations[]` | Pour chaque opération : `name`, `inputSchema` (JSON Schema), `outputSchema`, `errors[]` (liste de `reason`), `idempotent` (booléen), `async` (booléen), `limits` (quotas, taille, durée), `inverse` (facultatif : l'opération qui annule celle-ci, utilisée pour compenser). |
| `scopes` | Périmètres où la capacité est offerte (ex. `univ-x/*`). |
| `stability` | `alpha`, `beta` ou `stable`. |

**Règles.** Une capacité **ne révèle pas** le moteur qui la réalise (c'est un détail local, G-06). Plusieurs
implémentations d'une même capacité sont possibles dans un Control Plane : il choisit selon l'opération, la
politique et le contexte ([05](05-kernel-de-reference.md)). Noms de capacités standard (liste figée en R1.3) :
`workload-runtime`, `service-discovery`, `messaging`, `object-storage`, `secrets`, `workflow`,
`identity-provider`, `observability`.

## C3 — Délégation

**But.** Demander à un Control Plane d'exécuter une opération.
**Où.** `POST /egen/v1/operations` ; `GET /egen/v1/operations/{id}` ; `GET /egen/v1/operations` (filtres :
`scope`, `phase`, `capability`) ; `POST /egen/v1/operations/{id}:cancel`.

Demande :

```json
POST /egen/v1/operations
Idempotency-Key: 7c1e9a52-…
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
Authorization: Bearer <jeton délégué à portée limitée>

{
  "capability": "object-storage",
  "operation": "create-bucket",
  "parameters": { "name": "rh-agenda", "quotaGb": 50 },
  "scope": "univ-x/rh",
  "deadline": "2026-11-19T15:00:00Z",
  "parentOperationId": null
}
```

| Résultat | Quand |
|---|---|
| `202 Accepted` + représentation de l'Operation + en-tête `Location` | Demande acceptée (acceptée ≠ exécutée). |
| `200 OK` + l'Operation existante | Même clé d'idempotence, **même** contenu : on renvoie l'existante. |
| `409` `IdempotencyKeyConflict` | Même clé, contenu **différent**. |
| `400` `InvalidParameters` | Paramètres non conformes au `inputSchema`. |
| `403` `PolicyDenied` | Refus par une politique (locale ou fédérée). |
| `404` `UnknownCapability` | Capacité ou opération inconnue de ce Control Plane. |
| `429` / `503` | Charge ou indisponibilité ; `retryable: true`. |

**Règles.** L'identité du demandeur n'est **jamais** lue dans le corps. La clé d'idempotence est conservée au moins
aussi longtemps que l'opération et un délai de garde après sa fin (durée fixée en R1.4). `deadline` est une borne
absolue : passé ce délai, l'opération est abandonnée ou compensée, jamais poursuivie indéfiniment (P-14).

## C4 — État et résultat

Trois niveaux d'état, **à ne jamais confondre** :

| Niveau | Question | Où se lit |
|---|---|---|
| **Demande** | Ma demande a-t-elle été reçue et acceptée ? | Code de réponse de C3 (`202`, `400`, `403`…). |
| **Opération** | Où en est le travail demandé ? | `status.phase` et `status.conditions` de l'Operation. |
| **Ressource** | Dans quel état est ce qui a été créé ou modifié ? | `status.resources[].conditions` (`Ready`, `Degraded`, `observedGeneration`). |

Représentation d'une Operation :

```json
{
  "id": "01JD…",
  "controlPlaneId": "spiffe://univ-x.civitas.africa/egen/cp/transversal",
  "capability": "object-storage",
  "operation": "create-bucket",
  "scope": "univ-x/rh",
  "generation": 1,
  "status": {
    "phase": "Running",
    "conditions": [
      { "type": "Accepted",    "status": "True",  "reason": "PolicyAllowed",     "lastTransitionTime": "2026-11-19T14:02:11Z" },
      { "type": "Progressing", "status": "True",  "reason": "DelegatedToEngine", "message": "moteur object-storage sollicité" },
      { "type": "Completed",   "status": "False", "reason": "InProgress" }
    ],
    "resources": [ { "ref": { "kind": "Bucket", "name": "rh-agenda" }, "conditions": [ { "type": "Ready", "status": "False", "reason": "Provisioning" } ] } ],
    "attempts": 1,
    "uncertain": false
  },
  "createdAt": "2026-11-19T14:02:10Z",
  "deadline": "2026-11-19T15:00:00Z"
}
```

### Phases d'une opération

| Phase | Sens | Peut devenir |
|---|---|---|
| `Pending` | Acceptée, pas encore prise en charge. | `Running`, `Failed`, `Cancelled` |
| `Running` | Délégation faite, travail en cours. | `Succeeded`, `Failed`, `PartiallySucceeded`, `Cancelled`, `Unknown` |
| `Succeeded` | Terminée avec succès. **Terminale.** | — |
| `Failed` | Terminée en échec (après compensation éventuelle). **Terminale.** | — |
| `PartiallySucceeded` | Une partie des effets existe ; l'ensemble n'est pas atteint. | `Succeeded`, `Failed` |
| `Cancelled` | Annulée sur demande autorisée. **Terminale.** | — |
| `Unknown` | L'état réel n'est pas connu (système injoignable, réponse perdue). | `Running`, `Succeeded`, `Failed`, `PartiallySucceeded` — **seulement après observation** (P-15) |

**Règles.** Un `reason` est stable (jamais reformulé). Une cause **externe** (secret absent, courtier de messages
injoignable) ne fait pas passer l'opération en `Failed` : seul un échec répété de la délégation le fait (P-14).
Le compteur `attempts` est plafonné et visible.

## C5 — Événements

**Format.** CloudEvents 1.0 (enveloppe standard d'événements). **Transport.** NATS JetStream par défaut ;
repli par HTTP (liaison HTTP de CloudEvents) pour un Control Plane qui n'a pas de bus. **Description.** Un
document AsyncAPI par Control Plane liste les types publiés.

```json
{
  "specversion": "1.0",
  "id": "5b1c…",
  "source": "spiffe://univ-x.civitas.africa/egen/cp/transversal",
  "type": "africa.civitas.egen.operation.succeeded.v1",
  "subject": "operation/01JD…",
  "time": "2026-11-19T14:05:40Z",
  "datacontenttype": "application/json",
  "data": { "operationId": "01JD…", "capability": "object-storage", "scope": "univ-x/rh", "phase": "Succeeded" }
}
```

| Type (préfixe `africa.civitas.egen.`) | Émis quand |
|---|---|
| `controlplane.registered.v1`, `.suspended.v1`, `.revoked.v1`, `.descriptor-changed.v1` | Cycle de vie d'un Control Plane dans la fédération |
| `capability.published.v1`, `.withdrawn.v1`, `.changed.v1` | Cycle de vie d'une capacité |
| `operation.accepted.v1`, `.progressed.v1`, `.succeeded.v1`, `.failed.v1`, `.compensated.v1` | Cycle de vie d'une opération |
| `resource.ready.v1`, `.degraded.v1` | État d'une ressource suivie |

**Règles.** Livraison **au moins une fois** : les consommateurs sont idempotents (ils dédoublonnent sur `id`).
L'ordre est garanti **par `subject`**, pas globalement. Un événement ne contient jamais de valeur de secret ni de
donnée métier : il désigne, il ne transporte pas (G-08). Les types sont versionnés dans leur nom (`.v1`).

### Repli HTTP : souscriptions

Un Control Plane sans bus expose `POST /egen/v1/subscriptions` (corps : `endpoint` — adresse qui reçoit les événements —
et `types` — liste de préfixes de types, vide = tous). Réponse `201` avec l'identifiant de la souscription ;
`GET /egen/v1/subscriptions/{id}` ; `DELETE /egen/v1/subscriptions/{id}` (`204`). Les événements sont livrés en
`POST` au format **structuré** de la liaison HTTP de CloudEvents (`Content-Type: application/cloudevents+json`).
Une livraison non acquittée (réponse non `2xx`) est rejouée : la livraison reste « au moins une fois ».

## C6 — Versionnement

| Règle | Contenu |
|---|---|
| Forme | `MAJEUR.MINEUR` par contrat, déclaré dans C1 (`contracts`). |
| Compatible | Ajouter un champ optionnel, un type d'événement, une valeur d'énumération **ignorée** par défaut → version MINEURE. |
| Incompatible | Retirer ou renommer un champ, changer un sens, durcir une règle → version MAJEURE. |
| Négociation | L'appelant lit C1 de la cible, prend la **plus haute version commune**. Aucune version commune → refus `NoCommonContractVersion`. |
| Dépréciation | Annoncée dans C1 avec une date de fin ; durée minimale : **6 mois et deux versions mineures** de contrat (D-18). |
| Garde-fou | La CI détecte toute rupture de compatibilité (comparaison des schémas OpenAPI, JSON Schema, AsyncAPI) et la refuse sans changement de version majeure. |

## Niveaux de conformité

| Niveau | Exige |
|---|---|
| `base` | C1, C2, C6 |
| `delegation` | `base` + C3, C4 |
| `events` | `base` + C5 |
| `full` | Tous |

Un Control Plane déclare son niveau dans C1 ; le [kit de conformité](13-tests-et-conformite.md) le vérifie. Un
Federation Plane peut refuser d'admettre un Control Plane dont le niveau est insuffisant pour les capacités annoncées.

## Réalisation dans le Kernel de référence

| Contrat | Comment le Kernel de référence le réalise |
|---|---|
| C1 | Document généré à partir de la configuration du Control Plane, publié par le pont de fédération. |
| C2 | Généré à partir des définitions de capacités Crossplane marquées comme capacités EGEN. |
| C3 | Le pont de fédération reçoit la demande, l'opérateur crée la ressource `EgenOperation`. |
| C4 | Les Conditions de la ressource `EgenOperation` et des ressources suivies. |
| C5 | L'opérateur publie sur NATS au format CloudEvents. |
| C6 | Génération des schémas depuis une source unique ; contrôle de rupture en CI. |

## Exemple de bout en bout

Étape A de l'exemple de [03](03-architecture-generale.md) : la Saga du Federation Plane envoie la requête C3 ci-dessus
au Control Plane Transversal. Celui-ci répond `202` avec une Operation `Pending`, la fait passer à `Running`
(délégation au moteur de stockage), puis `Succeeded` ; il publie `operation.accepted.v1`, puis
`operation.succeeded.v1`. Si la même requête est rejouée avec la même clé, il renvoie `200` et l'Operation
existante, sans créer un second espace de stockage.
