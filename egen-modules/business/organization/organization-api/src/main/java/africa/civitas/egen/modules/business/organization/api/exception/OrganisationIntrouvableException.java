package africa.civitas.egen.modules.business.organization.api.exception;

import java.util.UUID;

public class OrganisationIntrouvableException extends RuntimeException {

    public OrganisationIntrouvableException(UUID id) {
        super("Aucune Organisation trouvee pour l'identifiant : " + id);
    }
}
