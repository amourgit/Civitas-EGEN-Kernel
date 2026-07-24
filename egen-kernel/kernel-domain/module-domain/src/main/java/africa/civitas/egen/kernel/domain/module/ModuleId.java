package africa.civitas.egen.kernel.domain.module;

import java.util.regex.Pattern;

/**
 * Identifiant stable d'un module, en kebab-case — la meme forme et la meme regle de
 * validation que {@code ManifesteExtension.moduleId} (kernel-sdk), volontairement
 * dupliquee plutot que partagee : kernel-sdk (le contrat du mecanisme de Manifeste)
 * et kernel-domain/module-domain (le vocabulaire metier de Catalogue/Souscription/
 * Activation) repondent a deux questions differentes qui, par coincidence,
 * contraignent la meme forme de chaine — ce n'est pas une raison de les coupler.
 *
 * <p>Utilise partout dans ce module plutot qu'un {@code String} nu : {@link
 * CatalogueEntree}, {@link Souscription} et {@link Activation} portent tous une
 * reference vers un module par ce type, jamais par chaine libre.
 */
public record ModuleId(String valeur) {

    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9]*(-[a-z0-9]+)*$");

    public ModuleId {
        if (valeur == null || valeur.isBlank()) {
            throw new ModuleDomainException("Un identifiant de module ne peut pas etre vide.");
        }
        if (!PATTERN.matcher(valeur).matches()) {
            throw new ModuleDomainException(
                    "Un identifiant de module doit etre en kebab-case (ex. 'reconnaissance-faciale'), recu : "
                            + valeur);
        }
    }

    @Override
    public String toString() {
        return valeur;
    }
}
