package africa.civitas.egen.modules.business.organization.api.affiliation.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Modele de lecture du Lexique des Mandats — symetrique au Lexique Organisationnel
 * (A2.2), la meme souverainete appliquee cette fois au vocabulaire des roles/titres
 * qu'une Organisation utilise ("Chef de Departement" chez l'une, "Team Lead" chez
 * l'autre), plutot que de figer un jeu de Mandats universel au niveau plateforme.
 */
public record LexiqueDesMandats(
        UUID id,
        UUID organisationId,
        String nom,
        String description,
        UUID modeleSectorielOrigineId,
        Tracabilite tracabilite) {

    public LexiqueDesMandats {
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
