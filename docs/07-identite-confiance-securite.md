# 07 — Identité, confiance et sécurité

## À quoi sert ce document
Spécifier comment on identifie, authentifie et autorise, comment la confiance s'établit entre Control Planes, comment
les droits se délèguent sans privilège excessif, comment on suspend un système compromis, et quelles exigences de
sécurité s'appliquent à tout composant.

## Ce qu'il ne couvre pas
Le format des contrats ([04](04-contrats-egen.md)) ni le détail d'exploitation des outils (fiches de
[moteurs/](moteurs/README.md)).

## Quatre notions à ne jamais confondre (P-07)

| Notion | Question | Exemple |
|---|---|---|
| **Identification** | Qui prétend agir ? | Le Control Plane `spiffe://univ-x.civitas.africa/egen/cp/metier`. |
| **Authentification** | Cette identité est-elle prouvée ? | Un certificat de courte durée signé par le domaine de confiance. |
| **Autorisation** | A-t-elle le droit de faire **cette** chose ? | « L'opérateur de la Plateforme peut demander `object-storage` sur `univ-x/rh` ». |
| **Confiance** | Accorde-t-on crédit aux déclarations de ce domaine ? | La confiance accordée au domaine `univ-x.civitas.africa`. |

Une identité prouvée **n'autorise rien** par elle-même (G-07). La fédération de confiance dit « je fais confiance à
l'émetteur de ces certificats », pas « je fais confiance à cet appelant pour cette opération » : l'autorisation reste
une décision explicite.

## Les briques et leur rôle

| Besoin | Brique | Rôle |
|---|---|---|
| Identité des **charges de travail** (Control Planes, services) | **SPIFFE/SPIRE** | Délivre des certificats de courte durée (SVID : « SPIFFE Verifiable Identity Document ») ; chaque domaine de confiance a sa propre autorité. |
| Confiance **entre domaines** | **Fédération SPIFFE** | Échange des *trust bundles* (ensembles de clés publiques d'un domaine) ; révocation = retrait du bundle. |
| Identité des **personnes** | **Keycloak** | Connexion, jetons des utilisateurs. |
| **Délégation** « au nom de » | Échange de jetons (RFC 8693) via Keycloak | Produit un jeton à portée réduite et courte durée qui porte la chaîne « agit pour » (personne, agent). À vérifier (VR-04). |
| **Droits fins** (qui peut quoi sur quoi) | **SpiceDB** | Modèle de relations ; réponse à « cet acteur peut-il demander cette capacité sur ce périmètre ? ». |
| **Politiques locales** de ressources | RBAC et règles d'admission Kubernetes | Voir [05](05-kernel-de-reference.md). |
| **Secrets** | **OpenBao** | Stockage et distribution ; aucune valeur dans les ressources (P-09). |

## Identités et domaines de confiance

- Chaque Control Plane possède **son domaine de confiance** (exemple : `univ-x.civitas.africa`), avec sa propre
  autorité SPIRE : aucune autorité centrale unique.
- Identité d'un Control Plane : `spiffe://<domaine>/egen/cp/<nom>` ; d'un service : `spiffe://<domaine>/ns/<espace>/svc/<nom>`.
- Les certificats sont de **courte durée** et renouvelés automatiquement. Révoquer = cesser de renouveler et retirer
  le bundle : l'accès disparaît à l'expiration (de l'ordre de minutes ou d'heures, durée à fixer en R3.1).

## Délégation d'autorisations sans privilège excessif

1. Marie s'authentifie auprès de Keycloak (jeton de personne).
2. L'opérateur de la Plateforme échange ce jeton contre un jeton **limité** : une capacité, un périmètre, quelques
   minutes ; il porte l'indication « agit pour Marie ».
3. Chaque Control Plane traversé **revérifie** : certificat de l'appelant, jeton, politique locale, SpiceDB.
4. Un agent d'intelligence artificielle suit le même chemin ; la chaîne « agit pour » est conservée dans les traces
   et l'audit ([10](10-evenements-observabilite-audit.md)), ce qui permet de tracer ses actions à travers plusieurs
   Control Planes.

Un jeton n'est jamais renvoyé tel quel au Control Plane suivant : il est **échangé** à chaque saut pour réduire sa
portée (à vérifier, VR-04).

## Politiques globales et locales

| Niveau | Porté par | Exemple |
|---|---|---|
| Fédéré | SpiceDB du Federation Plane | « Les opérateurs de Plateforme peuvent demander `workload-runtime` sur `univ-x/*`. » |
| Local | RBAC, admission, SpiceDB local | « Aucun service sans durcissement dans l'espace `univ-x-rh`. » |

Règle d'articulation : **une demande doit satisfaire les deux**. Le niveau local peut **restreindre** davantage que
le niveau fédéré, **jamais** l'inverse. Le Control Plane cible décide en dernier.

## Schéma de droits fédéré (esquisse à valider en R3.3)

Entités : `controlplane`, `capability`, `scope`, `operator` (service ou personne), `federation`. Relations :
`operator#can_request@capability`, `scope#member@scope` (hiérarchie), `controlplane#offers@capability`,
`federation#admin@operator`. La limite de profondeur de résolution des relations est à lire dans la documentation
officielle de SpiceDB (VR-10).

## Suspension rapide d'un système compromis

1. Décision de **suspension** au Federation Plane : le routeur refuse tout (immédiat).
2. Retrait des clés de confiance du système (**révocation**) : les nouvelles connexions échouent.
3. Les certificats déjà émis expirent d'eux-mêmes.
4. Les jetons délégués, de très courte durée, expirent ; les workflows en cours qui l'impliquent passent en
   compensation.
5. Trace d'audit complète de la décision et de ses causes.

## Exigences de sécurité applicables à tout composant

| Exigence | Contenu |
|---|---|
| Refus par défaut | Tout chemin, toute opération non déclarés sont refusés (G-09). |
| Aucun secret dans les sorties | Ni journaux, ni erreurs, ni événements, ni réponses (G-08). Les essais utilisent des **canaris** (fausses valeurs reconnaissables, ex. `CANARI-…`) pour prouver l'absence de fuite. |
| Erreurs bornées | 500 caractères au plus, noms seulement. |
| Moindre privilège | Les comptes de service n'ont que les verbes nécessaires ; un contrôle d'accès ne doit pas exiger un droit plus large que sa fonction (ex. lister les pods plutôt que les déploiements). |
| Durcissement des charges | Niveau `restricted` des normes de sécurité des pods ; utilisateur non root ; système de fichiers racine en lecture seule ; capacités retirées. |
| Vérification de l'audience des jetons | Un jeton n'est accepté que s'il est destiné à ce service. |
| Courte durée | Certificats et jetons de courte durée, renouvellement automatique. |
| Chaîne d'approvisionnement | Les paquets d'extensions et images sont signés et vérifiés (VR-16). |
| Journal d'audit | Toute décision d'autorisation et toute opération de gouvernance est enregistrée ([10](10-evenements-observabilite-audit.md)). |

## Intégration avec des systèmes d'identité externes

Keycloak peut fédérer des annuaires d'entreprise ; les mécanismes concrets (SAML, OIDC, annuaires) relèvent de la
configuration de Keycloak et de la Plateforme, pas du Kernel. Le Kernel n'exige d'un système externe que de
présenter des contrats C1 à C6 et une identité de charge de travail vérifiable.

## Exemple de bout en bout

Étape 6 de [03](03-architecture-generale.md) : la Saga appelle le Control Plane Transversal. Elle présente son certificat
SPIFFE (domaine de la fédération, reconnu par fédération de confiance) et un jeton échangé « agit pour Marie, capacité
`object-storage`, périmètre `univ-x/rh`, 5 minutes ». Le Control Plane Transversal vérifie le certificat, l'audience du
jeton, SpiceDB, puis ses règles d'admission locales. Une seule de ces vérifications qui échoue donne un `403 PolicyDenied`
journalisé. Si l'on suspend la Saga au Federation Plane, le routeur refuse immédiatement la suite.
