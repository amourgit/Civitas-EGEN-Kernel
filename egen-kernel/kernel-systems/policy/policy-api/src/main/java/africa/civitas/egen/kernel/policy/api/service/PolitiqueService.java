package africa.civitas.egen.kernel.policy.api.service;

import africa.civitas.egen.kernel.policy.api.command.CreerPolitiqueCommand;
import africa.civitas.egen.kernel.policy.api.domain.Politique;
import africa.civitas.egen.kernel.policy.api.domain.ValeurEffective;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolitiqueService {

    /**
     * Cree une Politique. L'implementation doit verifier l'existence du Contexte
     * (Organisation ou Cellule selon {@code contexteNature}) via organization-api
     * avant toute ecriture.
     */
    Politique creer(CreerPolitiqueCommand commande);

    Optional<Politique> trouverParId(UUID id);

    List<Politique> listerParContexte(UUID contexteId);

    /**
     * Resout la valeur effective d'une Politique pour une Cellule precise, en
     * appliquant la regle : la Derogation la plus proche dans l'arbre l'emporte
     * toujours. Remonte la chaine d'ancetres de la Cellule (la Cellule elle-meme
     * d'abord, puis ses ancetres du plus proche au plus eloigne) a la recherche
     * d'une Derogation en vigueur ; a defaut, retourne la valeur de la Politique
     * elle-meme.
     */
    ValeurEffective resoudrePourCellule(UUID politiqueId, UUID celluleId);
}
