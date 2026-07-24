package africa.civitas.egen.kernel.moduleregistry.command;

import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.Objects;

/** Demande d'enregistrement d'un module au Catalogue — le tout premier palier (§B.11). */
public record EnregistrerModuleCommand(
        ModuleId moduleId,
        String nom,
        String description,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public EnregistrerModuleCommand {
        Objects.requireNonNull(moduleId, "moduleId ne peut pas etre nul.");
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("nom ne peut pas etre vide.");
        }
        Objects.requireNonNull(description, "description ne peut pas etre nulle (chaine vide acceptee).");
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
