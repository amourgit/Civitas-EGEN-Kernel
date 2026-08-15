package africa.civitas.egen.kernel.domain.module;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.Objects;
import java.util.UUID;

/**
 * Le troisieme et dernier palier de la cascade (§B.11) : un module Souscrit,
 * effectivement mis en marche dans un Contexte precis.
 *
 * <p>{@code contexteId} reference l'identifiant d'un Contexte par un simple UUID nu,
 * jamais par le type {@code Contexte} lui-meme ni par un import d'un module Niveau 2 :
 * module-registry est Niveau 0, et ne doit jamais dependre d'un module Niveau 2.
 * Consequence assumee : ce domaine ne verifie jamais lui-meme qu'un Contexte donne
 * appartient bien a un ensemble plus large ayant une Souscription active pour ce
 * module — cette verification croisee a lieu au niveau service (ActivationService,
 * qui recoit explicitement les deux identifiants), jamais ici. Comment plusieurs
 * Contextes s'articulent entre eux (hierarchie, ensemble plat, autre modele) est une
 * question entierement etrangere a ce domaine.
 *
 * <p>Une desactivation est toujours une suppression logique, jamais physique — voir
 * {@link Tracabilite#estSupprimee()}.
 */
public record Activation(
        UUID id,
        UUID contexteId,
        ModuleId moduleId,
        Tracabilite tracabilite) {

    public Activation {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(contexteId, "contexteId ne peut pas etre nul.");
        Objects.requireNonNull(moduleId, "moduleId ne peut pas etre nul.");
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    /** @return vrai si cette Activation est active (ni desactivee, ni jamais supprimee logiquement). */
    public boolean active() {
        return !tracabilite.estSupprimee();
    }
}
