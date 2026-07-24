package africa.civitas.egen.kernel.moduleregistry.command;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;

import java.util.UUID;

/** Demande de desactivation d'une Activation active — toujours une suppression logique. */
public record DesactiverModuleCommand(UUID activationId, Acteur demandePar, String motif) {

    public DesactiverModuleCommand {
        if (activationId == null) {
            throw new IllegalArgumentException("activationId ne peut pas etre nul.");
        }
        if (demandePar == null) {
            throw new IllegalArgumentException("demandePar ne peut pas etre nul.");
        }
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("motif ne peut pas etre vide.");
        }
    }
}
