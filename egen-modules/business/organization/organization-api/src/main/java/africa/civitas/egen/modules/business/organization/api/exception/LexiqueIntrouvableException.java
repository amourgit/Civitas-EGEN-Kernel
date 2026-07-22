package africa.civitas.egen.modules.business.organization.api.exception;

import java.util.UUID;

public class LexiqueIntrouvableException extends RuntimeException {

    public LexiqueIntrouvableException(UUID id) {
        super("Aucun Lexique Organisationnel trouve pour l'identifiant : " + id);
    }
}
