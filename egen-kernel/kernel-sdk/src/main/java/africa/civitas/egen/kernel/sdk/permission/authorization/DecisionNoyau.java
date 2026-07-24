package africa.civitas.egen.kernel.sdk.permission.authorization;

/**
 * Le resultat d'une decision noyau — jamais un simple booleen, toujours accompagne
 * d'un motif. Reutilise a l'identique par {@code KernelPermissionCheck} (Systeme
 * Authorization) et par {@code PolitiqueNoyau} (Systeme Policy) : les deux repondent a
 * la meme question de fond ("le Kernel autorise-t-il ceci, et pourquoi ?"), donc les
 * deux partagent la meme forme de reponse plutot que d'inventer chacun la leur.
 *
 * <p>Ce n'est pas un detail cosmetique : le Journal des Decisions d'Acces (E3.1, voir
 * l'anatomie du Kernel) journalise "le chemin de raisonnement complet, pas juste le
 * resultat" pour les decisions SpiceDB — la meme exigence s'applique ici, et {@link
 * #motif()} obligatoire est ce qui la rend possible des le Niveau 1, avant meme que
 * SpiceDB ne soit disponible.
 *
 * @param autorise le verdict
 * @param motif justification humainement lisible, jamais vide — y compris quand
 *              {@link #autorise()} est vrai : "pourquoi autorise" merite une trace
 *              tout autant que "pourquoi refuse"
 */
public record DecisionNoyau(boolean autorise, String motif) {

    public DecisionNoyau {
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException(
                    "Une DecisionNoyau doit toujours porter un motif non vide, "
                            + "que la decision autorise ou refuse.");
        }
    }

    /** Construit une decision d'autorisation, avec son motif. */
    public static DecisionNoyau autorise(String motif) {
        return new DecisionNoyau(true, motif);
    }

    /** Construit une decision de refus, avec son motif. */
    public static DecisionNoyau refuse(String motif) {
        return new DecisionNoyau(false, motif);
    }
}
