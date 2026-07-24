package africa.civitas.egen.kernel.moduleregistry.exception;

/** Leve quand un module est deja au Catalogue avec le meme identifiant. */
public class ModuleDejaAuCatalogueException extends RuntimeException {

    public ModuleDejaAuCatalogueException(String message) {
        super(message);
    }
}
