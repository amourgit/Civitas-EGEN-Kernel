package africa.civitas.egen.modules.business.referencedata.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.Objects;
import java.util.UUID;

/**
 * Modele de lecture d'un Modele Sectoriel (B4.6) — un gabarit de Lexique
 * Organisationnel pre-rempli par secteur (Education superieure, Sante,
 * Entreprise...), propose en libre adoption a la creation d'une Organisation. Une
 * fois copie dans le Lexique Organisationnel d'une Organisation (Systeme A2), il vit
 * sa vie independamment — ce Modele n'est jamais qu'un point de depart.
 *
 * @param versionModele version du gabarit, permet de faire evoluer un Modele Sectoriel
 *                       sans casser les Organisations qui ont deja adopte une version anterieure
 */
public record ModeleSectoriel(
        UUID id,
        String nomSecteur,
        String description,
        String versionModele,
        StatutModeleSectoriel statut,
        Tracabilite tracabilite) {

    public ModeleSectoriel {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        if (nomSecteur == null || nomSecteur.isBlank()) {
            throw new IllegalArgumentException("nomSecteur ne peut pas etre vide.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description ne peut pas etre vide.");
        }
        if (versionModele == null || versionModele.isBlank()) {
            throw new IllegalArgumentException("versionModele ne peut pas etre vide.");
        }
        Objects.requireNonNull(statut, "statut ne peut pas etre nul.");
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }
}
