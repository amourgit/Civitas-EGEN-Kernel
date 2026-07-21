package africa.civitas.egen.kernel.policy.api.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Resultat de la resolution d'une Politique pour une Cellule precise — voit
 * {@link africa.civitas.egen.kernel.policy.api.service.PolitiqueService#resoudrePourCellule}.
 *
 * @param valeur la valeur effective a appliquer — celle de la Derogation la plus
 *               proche si une s'applique, sinon celle de la Politique elle-meme
 * @param derogationAppliqueeId absent si aucune Derogation ne s'applique (la valeur
 *                               vient directement de la Politique)
 */
public record ValeurEffective(String valeur, UUID derogationAppliqueeId) {

    public ValeurEffective {
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("valeur ne peut pas etre vide.");
        }
    }

    public Optional<UUID> derogationAppliquee() {
        return Optional.ofNullable(derogationAppliqueeId);
    }

    public boolean resulteDuneDerogation() {
        return derogationAppliqueeId != null;
    }
}
