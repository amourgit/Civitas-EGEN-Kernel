# 08 — Le Registry EGEN — catalogue logique, pas une base concurrente

Le Registry EGEN est la **projection lisible** de l'ensemble des
`DesiredState` + `ServiceStatus` connus du Kernel. Ce n'est **pas** un
second système de service discovery (ce rôle appartient à Consul, voir
[07](07-ports-et-adapters.md#discovery-port)) : c'est la mémoire
déclarative d'EGEN.

```
RegistryStorePort (port secondaire)
├── save(DesiredState)
├── findById(ServiceId): Optional<DesiredState>
├── findAll(filter): List<DesiredState>
├── saveStatus(ServiceId, ServiceStatus)
├── history(ServiceId): List<DesiredState>     // audit trail des générations successives
└── dependencyGraphSnapshot(): DependencyGraph
```

## Implémentation retenue

Une base relationnelle (PostgreSQL) pour la persistance transactionnelle
du `DesiredState` (avec verrouillage optimiste sur `generation` pour
éviter les races d'écriture concurrentes), + un cache en mémoire du
`DependencyGraph` reconstruit à chaque changement pour des requêtes de
planification rapides. Ce choix est un détail d'implémentation encapsulé
derrière `RegistryStorePort` — remplaçable (ex. par etcd si un jour EGEN
doit tourner en mode distribué multi-instance avec forte cohérence) sans
toucher au domaine.

## Distinction essentielle

Consul répond à *« quelles instances de `news-service` sont vivantes et
où ? »* (état opérationnel volatile). Le Registry EGEN répond à *« quels
services sont déclarés dans cet écosystème, avec quelles versions et
dépendances attendues ? »* (état déclaratif durable). Confondre les deux
est l'erreur n°1 à surveiller en revue de code — voir
[18 — Anti-patterns](18-anti-patterns.md).
