package africa.civitas.egen.modules.business.organization.api.exception;

import java.util.UUID;

public class CelluleIntrouvableException extends RuntimeException {

    public CelluleIntrouvableException(UUID id) {
        super("Aucune Cellule trouvee pour l'identifiant : " + id);
    }
}
