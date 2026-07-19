package africa.civitas.egen.kernel.organization.api.command;

import africa.civitas.egen.kernel.organization.api.domain.TypeOrganisation;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CreerOrganisationCommand(
        String raisonSociale,
        String sigle,
        TypeOrganisation typeOrganisation,
        String secteurActivitePrincipal,
        String codePaysRattachementJuridique,
        String identifiantJuridique,
        String codeDeviseReference,
        List<String> codesLanguesOfficielles,
        String identifiantFuseauHoraireReference,
        UUID modeleSectorielOrigineId,
        String urlIdentiteVisuelle,
        Acteur demandePar,
        OrigineDonnee origineDonnee) {

    public CreerOrganisationCommand {
        if (raisonSociale == null || raisonSociale.isBlank()) {
            throw new IllegalArgumentException("raisonSociale ne peut pas etre vide.");
        }
        if (sigle == null || sigle.isBlank()) {
            throw new IllegalArgumentException("sigle ne peut pas etre vide.");
        }
        Objects.requireNonNull(typeOrganisation, "typeOrganisation ne peut pas etre nul.");
        if (secteurActivitePrincipal == null || secteurActivitePrincipal.isBlank()) {
            throw new IllegalArgumentException("secteurActivitePrincipal ne peut pas etre vide.");
        }
        if (codePaysRattachementJuridique == null || codePaysRattachementJuridique.isBlank()) {
            throw new IllegalArgumentException("codePaysRattachementJuridique ne peut pas etre vide.");
        }
        if (identifiantJuridique == null || identifiantJuridique.isBlank()) {
            throw new IllegalArgumentException("identifiantJuridique ne peut pas etre vide.");
        }
        if (codeDeviseReference == null || codeDeviseReference.isBlank()) {
            throw new IllegalArgumentException("codeDeviseReference ne peut pas etre vide.");
        }
        if (codesLanguesOfficielles == null || codesLanguesOfficielles.isEmpty()) {
            throw new IllegalArgumentException("codesLanguesOfficielles doit contenir au moins un element.");
        }
        codesLanguesOfficielles = List.copyOf(codesLanguesOfficielles);
        if (identifiantFuseauHoraireReference == null || identifiantFuseauHoraireReference.isBlank()) {
            throw new IllegalArgumentException("identifiantFuseauHoraireReference ne peut pas etre vide.");
        }
        Objects.requireNonNull(demandePar, "demandePar ne peut pas etre nul.");
        Objects.requireNonNull(origineDonnee, "origineDonnee ne peut pas etre nulle.");
    }
}
