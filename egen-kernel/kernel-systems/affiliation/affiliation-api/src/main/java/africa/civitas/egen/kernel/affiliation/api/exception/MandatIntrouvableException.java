package africa.civitas.egen.kernel.affiliation.api.exception;

import java.util.UUID;

public class MandatIntrouvableException extends RuntimeException {

    public MandatIntrouvableException(UUID id) {
        super("Aucun Mandat trouve pour l'identifiant : " + id);
    }
}
