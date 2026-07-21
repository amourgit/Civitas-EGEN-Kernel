package africa.civitas.egen.kernel.organization.impl.infrastructure;

import africa.civitas.egen.kernel.organization.impl.domain.FermetureTransitiveCelluleEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Depot de la Fermeture Transitive de Cellule (A2.5). C'est le seul endroit du Kernel
 * qui ecrit dans cette table — jamais un service metier directement.
 */
@ApplicationScoped
public class FermetureTransitiveCelluleRepository
        implements PanacheRepositoryBase<FermetureTransitiveCelluleEntity, UUID> {

    /**
     * Enregistre la creation d'une Cellule dans la Fermeture Transitive : une ligne
     * reflexive (profondeur 0), puis une ligne pour chaque ancetre du parent (si un
     * parent est fourni), a profondeur+1. Algorithme standard d'insertion en closure
     * table — aucune recursion applicative n'est jamais necessaire, ni ici ni a la
     * lecture.
     */
    public void enregistrerCreationCellule(UUID celluleId, UUID celluleParentId) {
        FermetureTransitiveCelluleEntity reflexive = new FermetureTransitiveCelluleEntity();
        reflexive.id = UUID.randomUUID();
        reflexive.celluleAncetreId = celluleId;
        reflexive.celluleDescendanteId = celluleId;
        reflexive.profondeur = 0;
        persist(reflexive);

        if (celluleParentId == null) {
            return;
        }

        List<FermetureTransitiveCelluleEntity> ancetresDuParent =
                list("celluleDescendanteId", celluleParentId);

        for (FermetureTransitiveCelluleEntity ancetre : ancetresDuParent) {
            FermetureTransitiveCelluleEntity nouvelleLigne = new FermetureTransitiveCelluleEntity();
            nouvelleLigne.id = UUID.randomUUID();
            nouvelleLigne.celluleAncetreId = ancetre.celluleAncetreId;
            nouvelleLigne.celluleDescendanteId = celluleId;
            nouvelleLigne.profondeur = ancetre.profondeur + 1;
            persist(nouvelleLigne);
        }
    }

    /** @return les identifiants de tous les descendants d'une Cellule, profondeur &gt; 0 exclusivement. */
    public List<UUID> listerDescendantIds(UUID celluleId) {
        return list("celluleAncetreId = ?1 and profondeur > 0", celluleId).stream()
                .map(f -> f.celluleDescendanteId)
                .collect(Collectors.toList());
    }

    /**
     * @return les identifiants de tous les ancetres d'une Cellule, du plus proche
     *         (profondeur 1) au plus eloigne — ordre exige par la regle de resolution
     *         des Derogations du Systeme Politique (B1).
     */
    public List<UUID> listerAncetreIdsOrdonnesParProximite(UUID celluleId) {
        return list("celluleDescendanteId = ?1 and profondeur > 0 order by profondeur asc", celluleId).stream()
                .map(f -> f.celluleAncetreId)
                .collect(Collectors.toList());
    }
}
