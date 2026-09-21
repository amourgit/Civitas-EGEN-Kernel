package africa.civitas.egen.domain.lifecycle;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Machine a etats du cycle de vie d'un service — voir
 * docs/architecture/09-cycle-de-vie.md. Chaque transition non explicitement
 * autorisee ici est rejetee par du code (garde-fou de
 * docs/architecture/02-principes-fondamentaux.md), pas seulement documentee.
 *
 * <p>Table reduite pour cette etape du Kernel (voir
 * docs/architecture/19-feuille-de-route.md, Phase 1) :</p>
 * <pre>
 * DECLARED   -&gt; REGISTERED
 * REGISTERED -&gt; CONFIGURED, FAILED
 * CONFIGURED -&gt; DEPLOYING, FAILED
 * DEPLOYING  -&gt; RUNNING, FAILED
 * RUNNING    -&gt; DEGRADED, UPDATING, STOPPING, FAILED
 * DEGRADED   -&gt; RUNNING, STOPPING, FAILED
 * FAILED     -&gt; DEPLOYING, STOPPING     (nouvelle tentative, ou nettoyage)
 * UPDATING   -&gt; RUNNING, ROLLED_BACK, FAILED
 * ROLLED_BACK-&gt; RUNNING
 * STOPPING   -&gt; STOPPED
 * STOPPED    -&gt; DEPLOYING, REMOVING     (redeploiement explicite, ou suppression)
 * REMOVING   -&gt; REMOVED
 * REMOVED    -&gt; (terminal)
 * </pre>
 * <p>V0 vs table complete : la boucle de reconciliation ne pilote
 * automatiquement aujourd'hui que DECLARED..DEPLOYING..RUNNING/DEGRADED et
 * STOPPING..STOPPED (arret gracieux, voir 09.3) ; UPDATING, ROLLED_BACK,
 * REMOVING et REMOVED existent et sont valides ici, mais attendent les use
 * cases qui les declenchent (mise a jour, suppression — voir
 * docs/architecture/19-feuille-de-route.md), jamais atteints par
 * anticipation.</p>
 */
public final class LifecycleStateMachine {

    private static final Map<Phase, Set<Phase>> ALLOWED_TRANSITIONS = buildTransitionTable();

    private static Map<Phase, Set<Phase>> buildTransitionTable() {
        Map<Phase, Set<Phase>> table = new EnumMap<>(Phase.class);
        table.put(Phase.DECLARED, EnumSet.of(Phase.REGISTERED));
        table.put(Phase.REGISTERED, EnumSet.of(Phase.CONFIGURED, Phase.FAILED));
        table.put(Phase.CONFIGURED, EnumSet.of(Phase.DEPLOYING, Phase.FAILED));
        table.put(Phase.DEPLOYING, EnumSet.of(Phase.RUNNING, Phase.FAILED));
        table.put(Phase.RUNNING, EnumSet.of(Phase.DEGRADED, Phase.UPDATING, Phase.STOPPING, Phase.FAILED));
        table.put(Phase.DEGRADED, EnumSet.of(Phase.RUNNING, Phase.STOPPING, Phase.FAILED));
        table.put(Phase.FAILED, EnumSet.of(Phase.DEPLOYING, Phase.STOPPING));
        table.put(Phase.UPDATING, EnumSet.of(Phase.RUNNING, Phase.ROLLED_BACK, Phase.FAILED));
        table.put(Phase.ROLLED_BACK, EnumSet.of(Phase.RUNNING));
        table.put(Phase.STOPPING, EnumSet.of(Phase.STOPPED));
        table.put(Phase.STOPPED, EnumSet.of(Phase.DEPLOYING, Phase.REMOVING));
        table.put(Phase.REMOVING, EnumSet.of(Phase.REMOVED));
        table.put(Phase.REMOVED, EnumSet.noneOf(Phase.class));
        return Map.copyOf(table);
    }

    /**
     * Verifie qu'une transition de {@code from} vers {@code to} est autorisee.
     *
     * @throws IllegalPhaseTransitionException si la transition n'est pas dans la table
     */
    public void assertTransitionAllowed(Phase from, Phase to) {
        if (from == to) {
            return; // rester dans la meme phase (ex. re-observation) est toujours permis
        }
        Set<Phase> allowedTargets = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowedTargets.contains(to)) {
            throw new IllegalPhaseTransitionException(from, to);
        }
    }

    /** Variante booleenne pour les appelants qui ne veulent pas gerer l'exception. */
    public boolean isTransitionAllowed(Phase from, Phase to) {
        if (from == to) {
            return true;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
