package africa.civitas.egen.kernel.organization.api.exception;

import java.util.UUID;

public class OrganisationIntrouvableException extends RuntimeException {

    public OrganisationIntrouvableException(UUID id) {
        super("Aucune Organisation trouvee pour l'identifiant : " + id);
    }
}
