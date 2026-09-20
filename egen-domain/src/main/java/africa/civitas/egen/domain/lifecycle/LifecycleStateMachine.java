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
 * REGISTERED -&gt; DEPLOYING, FAILED    (FAILED : ex. echec d'adapter au premier appel de create())
 * DEPLOYING  -&gt; RUNNING, FAILED
 * RUNNING    -&gt; FAILED, STOPPED
 * FAILED     -&gt; DEPLOYING            (nouvelle tentative)
 * STOPPED    -&gt; DEPLOYING            (redeploiement explicite)
 * </pre>
 * <p>Les transitions vers DEGRADED, UPDATING, STOPPING, REMOVING et REMOVED
 * rejoignent cette table quand les phases correspondantes sont ajoutees a
 * {@link Phase} — jamais par anticipation.</p>
 */
public final class LifecycleStateMachine {

    private static final Map<Phase, Set<Phase>> ALLOWED_TRANSITIONS = buildTransitionTable();

    private static Map<Phase, Set<Phase>> buildTransitionTable() {
        Map<Phase, Set<Phase>> table = new EnumMap<>(Phase.class);
        table.put(Phase.DECLARED, EnumSet.of(Phase.REGISTERED));
        table.put(Phase.REGISTERED, EnumSet.of(Phase.DEPLOYING, Phase.FAILED));
        table.put(Phase.DEPLOYING, EnumSet.of(Phase.RUNNING, Phase.FAILED));
        table.put(Phase.RUNNING, EnumSet.of(Phase.FAILED, Phase.STOPPED));
        table.put(Phase.FAILED, EnumSet.of(Phase.DEPLOYING));
        table.put(Phase.STOPPED, EnumSet.of(Phase.DEPLOYING));
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
