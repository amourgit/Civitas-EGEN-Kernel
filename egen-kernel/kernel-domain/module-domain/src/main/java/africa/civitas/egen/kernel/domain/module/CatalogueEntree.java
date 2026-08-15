package africa.civitas.egen.kernel.domain.module;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.Objects;
import java.util.UUID;

/**
 * Une entree du Catalogue — un module qu'EGEN sait proposer sur ce deploiement,
 * independamment de tout Contexte metier (§B.2 : "l'ensemble des modules que la
 * plateforme EGEN sait proposer"). Une entree au Catalogue ne signifie ni qu'un
 * Contexte l'a souscrite ({@link Souscription}) ni qu'un autre Contexte l'a activee
 * ({@link Activation}) — seulement qu'elle existe et peut l'etre.
 *
 * @param moduleId identifiant stable, partage avec {@code ManifesteExtension.moduleId}
 *                 une fois ce module physiquement charge par kernel-plugin-engine
 * @param nom nom lisible, destine a une interface d'administration
 * @param description description courte, destinee a une interface d'administration
 */
public record CatalogueEntree(
        UUID id,
        ModuleId moduleId,
        String nom,
        String description,
        Tracabilite tracabilite) {

    public CatalogueEntree {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(moduleId, "moduleId ne peut pas etre nul.");
        if (nom == null || nom.isBlank()) {
            throw new ModuleDomainException("nom ne peut pas etre vide.");
        }
        Objects.requireNonNull(description, "description ne peut pas etre nulle (chaine vide acceptee).");
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }
}
