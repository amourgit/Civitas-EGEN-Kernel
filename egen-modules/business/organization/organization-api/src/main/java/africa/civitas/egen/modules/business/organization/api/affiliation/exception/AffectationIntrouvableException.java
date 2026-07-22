package africa.civitas.egen.modules.business.organization.api.affiliation.exception;

import java.util.UUID;

public class AffectationIntrouvableException extends RuntimeException {

    public AffectationIntrouvableException(UUID id) {
        super("Aucune Affectation trouvee pour l'identifiant : " + id);
    }
}
