package africa.civitas.egen.modules.business.organization.api.exception;

import java.util.UUID;

public class TypeCelluleIntrouvableException extends RuntimeException {

    public TypeCelluleIntrouvableException(UUID id) {
        super("Aucun Type de Cellule trouve pour l'identifiant : " + id);
    }
}
