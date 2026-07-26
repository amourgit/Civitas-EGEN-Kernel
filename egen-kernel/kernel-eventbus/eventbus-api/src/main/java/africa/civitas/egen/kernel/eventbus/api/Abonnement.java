package africa.civitas.egen.kernel.eventbus.api;

import java.util.Objects;
import java.util.UUID;

/**
 * La preuve d'une souscription au Bus d'Evenements — l'equivalent, pour ce module,
 * de {@code ExtensionDecouverte} dans kernel-plugin-engine : un handle explicite,
 * jamais un simple identifiant technique, pour que le retrait d'un module (au
 * dechargement, voir {@code kernel-plugin-engine}) puisse retirer toutes ses
 * souscriptions d'un seul geste via {@link EventBus#desabonnerToutPour}.
 *
 * @param id identifiant opaque de cette souscription
 * @param moduleId le module au nom duquel cette souscription a ete faite
 * @param description forme humainement lisible de ce qui est ecoute (un type exact
 *                     ou un prefixe), utile pour un diagnostic ou une interface
 *                     d'administration
 */
public record Abonnement(UUID id, String moduleId, String description) {

    public Abonnement {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId ne peut pas etre vide.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description ne peut pas etre vide.");
        }
    }
}
