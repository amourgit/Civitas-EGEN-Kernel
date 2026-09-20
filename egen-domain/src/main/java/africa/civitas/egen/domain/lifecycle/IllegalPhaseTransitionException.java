package africa.civitas.egen.domain.lifecycle;

/**
 * Signale une transition de {@link Phase} non autorisee par la
 * {@link LifecycleStateMachine} — voir docs/architecture/18-anti-patterns.md :
 * toute transition doit etre rejetee par du code, jamais seulement par
 * convention.
 */
public final class IllegalPhaseTransitionException extends IllegalStateException {

    public IllegalPhaseTransitionException(Phase from, Phase to) {
        super("Transition interdite : " + from + " -> " + to);
    }
}
