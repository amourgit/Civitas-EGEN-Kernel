package africa.civitas.egen.modules.business.organization.api.affiliation.exception;

import java.util.UUID;

public class LexiqueDesMandatsIntrouvableException extends RuntimeException {

    public LexiqueDesMandatsIntrouvableException(UUID id) {
        super("Aucun Lexique des Mandats trouve pour l'identifiant : " + id);
    }
}
