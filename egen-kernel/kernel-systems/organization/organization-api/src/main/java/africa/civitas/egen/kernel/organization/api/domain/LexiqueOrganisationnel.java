package africa.civitas.egen.kernel.organization.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Modele de lecture d'un Lexique Organisationnel (A2.2) — le referentiel de
 * vocabulaire propre a une Organisation. Une Organisation peut posseder plusieurs
 * Lexiques (ex. un Lexique academique distinct d'un Lexique administratif).
 */
public record LexiqueOrganisationnel(
        UUID id,
        UUID organisationId,
        String nom,
        String description,
        UUID modeleSectorielOrigineId,
        Tracabilite tracabilite) {

    public LexiqueOrganisationnel {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(organisationId, "organisationId ne peut pas etre nul.");
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("nom ne peut pas etre vide.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description ne peut pas etre vide.");
        }
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    public Optional<UUID> modeleSectorielOrigine() {
        return Optional.ofNullable(modeleSectorielOrigineId);
    }
}
