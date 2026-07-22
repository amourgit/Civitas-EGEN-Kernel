package africa.civitas.egen.modules.business.organization.api.affiliation.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Modele de lecture d'une Delegation (A3.3) — le transfert temporaire d'un Mandat
 * porte par une Affectation vers une autre Personne, sans jamais rompre l'Affectation
 * d'origine. Le cas du Recteur qui part en vacances et delegue au Vice-Recteur : une
 * entite de premiere classe, temporelle et traçable, plutot qu'une bascule manuelle
 * des droits qu'on oublierait de revoquer au retour.
 *
 * @param affectationOrigineId l'Affectation dont le Mandat est delegue
 * @param personneBeneficiaireId la Personne qui recoit temporairement le Mandat
 * @param actionsCouvertes precision des actions couvertes, obligatoire si
 *                          {@code etendue == PARTIELLE}, sinon absent
 */
public record Delegation(
        UUID id,
        UUID affectationOrigineId,
        UUID personneBeneficiaireId,
        EtendueDelegation etendue,
        String actionsCouvertes,
        LocalDate dateDebut,
        LocalDate dateFin,
        MotifDelegation motif,
        StatutDelegation statut,
        Tracabilite tracabilite) {

    public Delegation {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(affectationOrigineId, "affectationOrigineId ne peut pas etre nul.");
        Objects.requireNonNull(personneBeneficiaireId, "personneBeneficiaireId ne peut pas etre nul.");
        Objects.requireNonNull(etendue, "etendue ne peut pas etre nulle.");
        if (etendue == EtendueDelegation.PARTIELLE && (actionsCouvertes == null || actionsCouvertes.isBlank())) {
            throw new IllegalArgumentException(
                    "actionsCouvertes est obligatoire lorsque etendue == PARTIELLE.");
        }
        if (etendue == EtendueDelegation.TOTALE && actionsCouvertes != null) {
            throw new IllegalArgumentException(
                    "actionsCouvertes ne doit pas etre renseigne lorsque etendue == TOTALE.");
        }
        Objects.requireNonNull(dateDebut, "dateDebut ne peut pas etre nulle.");
        Objects.requireNonNull(dateFin, "dateFin ne peut pas etre nulle : une Delegation est toujours bornee.");
        if (dateFin.isBefore(dateDebut)) {
            throw new IllegalArgumentException("dateFin ne peut pas etre anterieure a dateDebut.");
        }
        Objects.requireNonNull(motif, "motif ne peut pas etre nul.");
        Objects.requireNonNull(statut, "statut ne peut pas etre nul.");
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    public Optional<String> actionsCouvertesEventuelles() {
        return Optional.ofNullable(actionsCouvertes);
    }
}
