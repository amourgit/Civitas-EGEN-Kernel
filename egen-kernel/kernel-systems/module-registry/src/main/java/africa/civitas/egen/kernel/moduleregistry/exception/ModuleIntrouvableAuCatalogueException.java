package africa.civitas.egen.kernel.moduleregistry.exception;

/**
 * Leve quand une Souscription ou une Activation est demandee pour un module absent
 * du Catalogue — le premier palier de la cascade (§B.11) n'est jamais franchissable.
 */
public class ModuleIntrouvableAuCatalogueException extends RuntimeException {

    public ModuleIntrouvableAuCatalogueException(String message) {
        super(message);
    }
}
