package africa.civitas.egen.kernel.referencedata.api.exception;

import java.util.UUID;

/** Levee lorsqu'une commande reference un Modele Sectoriel inexistant. */
public class ModeleSectorielIntrouvableException extends RuntimeException {

    public ModeleSectorielIntrouvableException(UUID id) {
        super("Aucun Modele Sectoriel trouve pour l'identifiant : " + id);
    }
}
