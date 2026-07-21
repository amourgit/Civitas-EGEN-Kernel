package africa.civitas.egen.kernel.policy.api.exception;

import java.util.UUID;

public class PolitiqueIntrouvableException extends RuntimeException {

    public PolitiqueIntrouvableException(UUID id) {
        super("Aucune Politique trouvee pour l'identifiant : " + id);
    }
}
