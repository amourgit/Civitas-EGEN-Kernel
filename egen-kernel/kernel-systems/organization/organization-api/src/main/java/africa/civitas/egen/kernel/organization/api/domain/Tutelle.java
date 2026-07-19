package africa.civitas.egen.kernel.organization.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Modele de lecture d'une Tutelle (A2.6) — le rattachement d'un Etablissement
 * (Cellule racine) a une ou plusieurs Organisations, avec une nature precise. C'est
 * ce qui permet de representer un CHU rattache a la fois a un Ministere de la Sante
 * et a une Universite.
 *
 * @param celluleRacineId l'Etablissement concerne — doit etre une Cellule sans parent
 * @param organisationId l'Organisation exercant cette tutelle
 * @param tutellePrincipale une seule Tutelle principale par Etablissement
 */
public record Tutelle(
        UUID id,
        UUID celluleRacineId,
        UUID organisationId,
        NatureTutelle nature,
        boolean tutellePrincipale,
        LocalDate dateDebut,
        LocalDate dateFin,
        UUID acteJustificatifRef,
        Tracabilite tracabilite) {

    public Tutelle {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(celluleRacineId, "celluleRacineId ne peut pas etre nul.");
        Objects.requireNonNull(organisationId, "organisationId ne peut pas etre nul.");
        Objects.requireNonNull(nature, "nature ne peut pas etre nulle.");
        Objects.requireNonNull(dateDebut, "dateDebut ne peut pas etre nulle.");
        if (dateFin != null && dateFin.isBefore(dateDebut)) {
            throw new IllegalArgumentException("dateFin ne peut pas etre anterieure a dateDebut.");
        }
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    public Optional<LocalDate> dateFinEventuelle() {
        return Optional.ofNullable(dateFin);
    }

    public Optional<UUID> acteJustificatif() {
        return Optional.ofNullable(acteJustificatifRef);
    }
}
