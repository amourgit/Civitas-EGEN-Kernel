package africa.civitas.egen.modules.business.referencedata.api.exception;

/** Levee lorsqu'une commande d'enregistrement entre en conflit avec un code deja existant. */
public class ReferentielConflitException extends RuntimeException {

    public ReferentielConflitException(String message) {
        super(message);
    }
}
