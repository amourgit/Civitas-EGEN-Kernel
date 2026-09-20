package africa.civitas.egen.domain.lifecycle;

/**
 * Phase macro du cycle de vie d'un service (voir
 * docs/architecture/09-cycle-de-vie.md). Ensemble reduit pour cette etape du
 * Kernel — DECLARED, REGISTERED, DEPLOYING, RUNNING, FAILED, STOPPED — les
 * phases DEGRADED, UPDATING, STOPPING, REMOVING et REMOVED rejoignent cet
 * enum quand leurs mecanismes respectifs sont construits (arret gracieux,
 * mise a jour, suppression — voir docs/architecture/19-feuille-de-route.md),
 * jamais par anticipation.
 */
public enum Phase {
    DECLARED,
    REGISTERED,
    DEPLOYING,
    RUNNING,
    FAILED,
    STOPPED
}
