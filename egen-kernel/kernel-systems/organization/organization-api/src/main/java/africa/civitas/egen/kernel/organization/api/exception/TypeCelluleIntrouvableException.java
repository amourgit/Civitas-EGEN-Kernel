package africa.civitas.egen.kernel.organization.api.exception;

import java.util.UUID;

public class TypeCelluleIntrouvableException extends RuntimeException {

    public TypeCelluleIntrouvableException(UUID id) {
        super("Aucun Type de Cellule trouve pour l'identifiant : " + id);
    }
}
