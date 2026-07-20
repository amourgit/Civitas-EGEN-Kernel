package africa.civitas.egen.kernel.affiliation.api.exception;

import java.util.UUID;

public class AffectationIntrouvableException extends RuntimeException {

    public AffectationIntrouvableException(UUID id) {
        super("Aucune Affectation trouvee pour l'identifiant : " + id);
    }
}
