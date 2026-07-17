package africa.civitas.egen.kernel.identity.api.exception;

import java.util.UUID;

/** Levee lorsqu'une commande reference une Personne inexistante. */
public class PersonneIntrouvableException extends RuntimeException {

    public PersonneIntrouvableException(UUID personneId) {
        super("Aucune Personne trouvee pour l'identifiant : " + personneId);
    }
}
