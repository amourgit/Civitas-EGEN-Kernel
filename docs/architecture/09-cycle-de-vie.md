# 09 — Gestion du cycle de vie — machine à états complète

## États (phases)

```
DECLARED ──▶ REGISTERED ──▶ CONFIGURED ──▶ DEPLOYING ──▶ RUNNING ──┬──▶ DEGRADED ──▶ RUNNING
                                               │                    │        │
                                               │                    └──▶ FAILED
                                               ▼
                                            FAILED
RUNNING/DEGRADED ──▶ UPDATING ──▶ RUNNING (ou ROLLED_BACK ──▶ RUNNING si échec)
RUNNING/DEGRADED/FAILED ──▶ STOPPING ──▶ STOPPED
STOPPED ──▶ REMOVING ──▶ REMOVED
```

| Phase | Signification | Condition de sortie (calculée par la réconciliation) |
|---|---|---|
| `DECLARED` | Le manifeste a été validé et persisté. Aucune action déléguée encore. | Passe à `REGISTERED` dès que le premier cycle de réconciliation le prend en charge. |
| `REGISTERED` | Le service existe dans le Registry EGEN, ses dépendances sont résolues. | Passe à `CONFIGURED` une fois la configuration/les secrets résolus avec succès. |
| `CONFIGURED` | Configuration et secrets prêts à être injectés. | Passe à `DEPLOYING` dès que le plan d'exécution (voir [04](04-moteur-de-reconciliation.md), étape Compose) est prêt et que les dépendances **required** sont elles-mêmes `RUNNING`. |
| `DEPLOYING` | `DeploymentPort.create/update` a été appelé, en attente de convergence. | `RUNNING` si `ObservedState.deployment.healthyCount >= desiredCount` **et** `DiscoveryObservation` confirme des instances saines. `FAILED` si le déploiement échoue au-delà du seuil de retry. |
| `RUNNING` | État nominal — désiré et observé convergent. | `DEGRADED` si une partie des instances devient malsaine ; `UPDATING` sur nouvelle génération du manifeste ; `STOPPING` sur demande d'arrêt. |
| `DEGRADED` | Convergence partielle (ex. 3 instances saines sur 5 désirées). | Retour `RUNNING` si la convergence se rétablit ; `FAILED` si aucune amélioration après un délai configurable. |
| `FAILED` | Le Kernel ne parvient pas à converger malgré les retries. | Nécessite une action corrective (nouveau manifeste, intervention infra) ou reste en `FAILED` avec alerting actif — **jamais de retry infini silencieux**, le compteur d'échecs est plafonné et visible. |
| `UPDATING` | Nouvelle génération en cours de déploiement par-dessus une version `RUNNING`/`DEGRADED`. | `RUNNING` sur succès ; `ROLLED_BACK` puis `RUNNING` si `updateStrategy.autoRevert` déclenche un retour arrière automatique (mappé sur le mécanisme natif de rollback Nomad, voir [07.1](07-ports-et-adapters.md#deployment-port)). |
| `STOPPING` | Arrêt gracieux en cours (deregister Consul avant arrêt Nomad — ordre important, voir plus bas). | `STOPPED` une fois toutes les instances arrêtées. |
| `STOPPED` | Aucune instance active, mais le manifeste reste dans le Registry (peut être redéployé). | `DEPLOYING` sur redemande explicite, ou `REMOVING` sur suppression définitive. |
| `REMOVING` | Suppression en cours (purge Nomad, désenregistrement Consul, nettoyage des topics/streams si demandé). | `REMOVED`. |
| `REMOVED` | Suppression terminée. Conservé en historique pour audit, filtré des vues actives par défaut. | Terminal. |

## Conditions

En complément de la phase (état macro), chaque `ServiceStatus` porte une
liste de `Condition` — plus fine, multi-dimensionnelle, et qui ne perd pas
d'information au fil des transitions (modèle inspiré des
`status.conditions` Kubernetes) :

```json
{
  "type": "DependenciesSatisfied",
  "status": "False",
  "reason": "VersionConstraintUnmet",
  "message": "notification-service is at 1.1.0, but >=1.2.0 is required",
  "lastTransitionTime": "2026-09-16T10:00:00Z"
}
```

Types de `Condition` en V1 : `ManifestValid`, `DependenciesSatisfied`,
`DeploymentReady`, `DiscoveryReady`, `MessagingBindingsReady`,
`ConfigResolved`. Cette granularité permet à un opérateur humain de
diagnostiquer *en un coup d'œil* pourquoi un service reste bloqué en
`CONFIGURED` au lieu de `DEPLOYING`, sans devoir fouiller les logs bruts.

## Ordre d'arrêt gracieux — un piège classique

Lors du passage `RUNNING → STOPPING`, l'ordre des opérations déléguées
compte :

1. **D'abord** `DiscoveryPort.deregister()` — retire l'instance de la
   liste des instances saines de Consul, pour que les clients cessent de
   lui envoyer du nouveau trafic.
2. **Attendre** `lifecycle.shutdown.gracePeriod` (défini dans le
   manifeste) — laisse le temps aux requêtes en vol de se terminer
   (`drainConnections`).
3. **Ensuite** `DeploymentPort.stop()` — arrête réellement le
   processus/conteneur via Nomad.

Inverser cet ordre (arrêter Nomad avant de désenregistrer Consul) provoque
des erreurs 502/connexions refusées côté clients pendant la fenêtre de
propagation du health check — c'est l'erreur la plus fréquente en
production sur ce genre de plateforme et elle est testée explicitement
(voir [17 — Stratégie de tests, niveau 3](17-strategie-de-tests.md)).
