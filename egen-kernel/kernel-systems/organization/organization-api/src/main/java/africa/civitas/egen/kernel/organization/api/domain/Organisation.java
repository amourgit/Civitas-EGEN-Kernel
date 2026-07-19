package africa.civitas.egen.kernel.organization.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Modele de lecture d'une Organisation (A2.1) — le royaume souverain, racine
 * juridique de tout ce qui suit dans EGEN.
 *
 * @param codePaysRattachementJuridique code ISO alpha-2 du pays de rattachement (reference
 *                                       vers B4.Pays, jamais un import de type — voir la
 *                                       regle d'independance des piliers du Niveau 0)
 * @param codeDeviseReference code ISO 4217 de la devise de reference (reference vers B4.Devise)
 * @param codesLanguesOfficielles codes ISO 639 des langues officielles, au moins une
 * @param identifiantFuseauHoraireReference identifiant IANA du fuseau horaire de reference
 * @param modeleSectorielOrigineId le Modele Sectoriel adopte a la creation, absent si
 *                                  le Lexique a ete construit entierement a la main
 */
public record Organisation(
        UUID id,
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
        StatutOrganisation statut,
        LocalDate dateAdhesion,
        String urlIdentiteVisuelle,
        Tracabilite tracabilite) {

    public Organisation {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        requireNonBlank(raisonSociale, "raisonSociale");
        requireNonBlank(sigle, "sigle");
        Objects.requireNonNull(typeOrganisation, "typeOrganisation ne peut pas etre nul.");
        requireNonBlank(secteurActivitePrincipal, "secteurActivitePrincipal");
        requireNonBlank(codePaysRattachementJuridique, "codePaysRattachementJuridique");
        requireNonBlank(identifiantJuridique, "identifiantJuridique");
        requireNonBlank(codeDeviseReference, "codeDeviseReference");
        if (codesLanguesOfficielles == null || codesLanguesOfficielles.isEmpty()) {
            throw new IllegalArgumentException("codesLanguesOfficielles doit contenir au moins un element.");
        }
        codesLanguesOfficielles = List.copyOf(codesLanguesOfficielles);
        requireNonBlank(identifiantFuseauHoraireReference, "identifiantFuseauHoraireReference");
        Objects.requireNonNull(statut, "statut ne peut pas etre nul.");
        Objects.requireNonNull(dateAdhesion, "dateAdhesion ne peut pas etre nulle.");
        if (dateAdhesion.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("dateAdhesion ne peut pas etre dans le futur.");
        }
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    private static void requireNonBlank(String value, String champ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(champ + " ne peut pas etre vide.");
        }
    }

    /** @return le Modele Sectoriel d'origine, absent si le Lexique a ete construit a la main. */
    public Optional<UUID> modeleSectorielOrigine() {
        return Optional.ofNullable(modeleSectorielOrigineId);
    }
}
