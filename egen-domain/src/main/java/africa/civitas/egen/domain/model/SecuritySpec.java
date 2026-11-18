package africa.civitas.egen.domain.model;

/**
 * Durcissement du conteneur demande par le proprietaire du service (docs/architecture/06, « deployment.security »,
 * constat K9). Chaque champ est facultatif : un champ {@code null} n'est pas pose par l'adapter, un champ present
 * l'est tel quel, y compris {@code false}.
 *
 * <p>Le domaine ne connait pas le moteur de conteneurs : il ne fait que porter et valider ces valeurs. Leur
 * traduction (par exemple en {@code securityContext} Kubernetes) est une responsabilite d'adapter.</p>
 *
 * @param runAsNonRoot           refuser de demarrer un programme qui tourne en administrateur
 * @param runAsUser              numero d'utilisateur du programme, de 1 a 2147483647 (jamais 0, qui est root)
 * @param readOnlyRootFilesystem interdire l'ecriture sur le disque du conteneur
 * @param dropAllCapabilities    retirer tous les privileges Linux du conteneur
 */
public record SecuritySpec(Boolean runAsNonRoot, Integer runAsUser, Boolean readOnlyRootFilesystem,
                           Boolean dropAllCapabilities) {

    public SecuritySpec {
        if (runAsUser != null && runAsUser < 1) {
            throw new IllegalArgumentException("deployment.security.runAsUser doit etre compris entre 1 et "
                    + Integer.MAX_VALUE + " (recu : " + runAsUser + " ; 0 est l'administrateur root)");
        }
    }

    /** Vrai quand aucun champ n'est renseigne : equivalent a l'absence de bloc. */
    public boolean isEmpty() {
        return runAsNonRoot == null && runAsUser == null && readOnlyRootFilesystem == null
                && dropAllCapabilities == null;
    }
}
