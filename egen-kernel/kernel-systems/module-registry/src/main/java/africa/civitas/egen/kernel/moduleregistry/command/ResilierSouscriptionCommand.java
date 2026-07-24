package africa.civitas.egen.kernel.moduleregistry.command;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;

import java.util.UUID;

/** Demande de resiliation d'une Souscription active — toujours une suppression logique. */
public record ResilierSouscriptionCommand(UUID souscriptionId, Acteur demandePar, String motif) {

    public ResilierSouscriptionCommand {
        if (souscriptionId == null) {
            throw new IllegalArgumentException("souscriptionId ne peut pas etre nul.");
        }
        if (demandePar == null) {
            throw new IllegalArgumentException("demandePar ne peut pas etre nul.");
        }
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("motif ne peut pas etre vide.");
        }
    }
}
