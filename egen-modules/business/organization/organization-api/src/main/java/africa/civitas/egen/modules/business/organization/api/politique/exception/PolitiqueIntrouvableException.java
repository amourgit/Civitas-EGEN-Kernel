package africa.civitas.egen.modules.business.organization.api.politique.exception;

import java.util.UUID;

public class PolitiqueIntrouvableException extends RuntimeException {

    public PolitiqueIntrouvableException(UUID id) {
        super("Aucune Politique trouvee pour l'identifiant : " + id);
    }
}
