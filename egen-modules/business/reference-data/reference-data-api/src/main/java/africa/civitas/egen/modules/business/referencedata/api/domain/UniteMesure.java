package africa.civitas.egen.modules.business.referencedata.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Modele de lecture d'une Unite de Mesure (B4.5).
 *
 * @param facteurConversion facteur de conversion vers l'unite pivot de sa categorie
 *                           (ex. 1000 pour le kilometre si le metre est l'unite pivot
 *                           de LONGUEUR), strictement positif
 */
public record UniteMesure(
        UUID id,
        String nom,
        String symbole,
        CategorieUniteMesure categorie,
        BigDecimal facteurConversion,
        Tracabilite tracabilite) {

    public UniteMesure {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("nom ne peut pas etre vide.");
        }
        if (symbole == null || symbole.isBlank()) {
            throw new IllegalArgumentException("symbole ne peut pas etre vide.");
        }
        Objects.requireNonNull(categorie, "categorie ne peut pas etre nulle.");
        Objects.requireNonNull(facteurConversion, "facteurConversion ne peut pas etre nul.");
        if (facteurConversion.signum() <= 0) {
            throw new IllegalArgumentException(
                    "facteurConversion doit etre strictement positif, recu : " + facteurConversion);
        }
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }
}
