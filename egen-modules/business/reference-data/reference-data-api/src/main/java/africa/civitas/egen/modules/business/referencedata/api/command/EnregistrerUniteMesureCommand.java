package africa.civitas.egen.modules.business.referencedata.api.command;

import africa.civitas.egen.modules.business.referencedata.api.domain.CategorieUniteMesure;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.math.BigDecimal;
import java.util.Objects;

public record EnregistrerUniteMesureCommand(
        String nom,
        String symbole,
        CategorieUniteMesure categorie,
        BigDecimal facteurConversion,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public EnregistrerUniteMesureCommand {
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
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
