package africa.civitas.egen.modules.business.organization.api.affiliation.exception;

import java.util.UUID;

public class MandatIntrouvableException extends RuntimeException {

    public MandatIntrouvableException(UUID id) {
        super("Aucun Mandat trouve pour l'identifiant : " + id);
    }
}
