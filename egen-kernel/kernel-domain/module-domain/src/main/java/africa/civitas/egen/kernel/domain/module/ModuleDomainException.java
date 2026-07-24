package africa.civitas.egen.kernel.domain.module;

/**
 * Levee quand une construction dans le domaine du Systeme Modules (B2 — Catalogue,
 * Souscription, Activation) viole une invariant. Miroir, pour ce domaine, de {@code
 * ManifestValidationException} (kernel-sdk) — meme discipline : un objet de ce
 * domaine qui existe en memoire est toujours valide par construction.
 */
public class ModuleDomainException extends RuntimeException {

    public ModuleDomainException(String message) {
        super(message);
    }
}
