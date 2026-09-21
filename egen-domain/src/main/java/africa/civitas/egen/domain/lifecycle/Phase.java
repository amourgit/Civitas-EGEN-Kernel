package africa.civitas.egen.domain.lifecycle;

/**
 * Phase macro du cycle de vie d'un service — table complete (voir
 * docs/architecture/09-cycle-de-vie.md, §9.1). La table de transitions
 * autorisees vit dans {@link LifecycleStateMachine} ; toutes les phases
 * existent ici des la Phase 2, mais seules celles qu'un mecanisme reel pilote
 * (DEPLOYING/RUNNING/DEGRADED via Deployment+Discovery, STOPPING/STOPPED via
 * l'arret gracieux) sont atteintes automatiquement par la reconciliation
 * aujourd'hui — UPDATING/ROLLED_BACK/REMOVING/REMOVED sont valides par la
 * machine a etats mais attendent les use cases qui les declenchent (voir
 * docs/architecture/19-feuille-de-route.md).
 */
public enum Phase {
    DECLARED,
    REGISTERED,
    CONFIGURED,
    DEPLOYING,
    RUNNING,
    DEGRADED,
    UPDATING,
    ROLLED_BACK,
    STOPPING,
    STOPPED,
    REMOVING,
    REMOVED,
    FAILED
}
