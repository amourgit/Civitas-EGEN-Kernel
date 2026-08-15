package africa.civitas.egen.kernel.domain.module;

import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;

import java.util.Objects;
import java.util.UUID;

/**
 * Ce qu'un Contexte a choisi d'acquerir dans le Catalogue (§B.2). Toujours a
 * l'echelle du Contexte souscripteur pris dans son ensemble, jamais d'un Contexte
 * plus restreint qui en dependrait — voir {@link Activation} pour le palier suivant.
 *
 * <p>{@code contexteId} reference l'identifiant d'un Contexte par un simple UUID nu,
 * jamais par le type {@code Contexte} lui-meme ni par un import d'un module Niveau 2 :
 * module-registry est Niveau 0, et ne doit jamais dependre d'un module Niveau 2. La
 * resolution "ce Contexte existe-t-il reellement" est une responsabilite du code
 * appelant (kernel-bootstrap ou une future interface d'administration), jamais de ce
 * domaine.
 *
 * <p>Une revocation de Souscription est toujours une suppression logique, jamais
 * physique — voir {@link Tracabilite#estSupprimee()}.
 */
public record Souscription(
        UUID id,
        UUID contexteId,
        ModuleId moduleId,
        Tracabilite tracabilite) {

    public Souscription {
        Objects.requireNonNull(id, "id ne peut pas etre nul.");
        Objects.requireNonNull(contexteId, "contexteId ne peut pas etre nul.");
        Objects.requireNonNull(moduleId, "moduleId ne peut pas etre nul.");
        Objects.requireNonNull(tracabilite, "tracabilite ne peut pas etre nulle.");
    }

    /** @return vrai si cette Souscription est active (ni resiliee, ni jamais supprimee logiquement). */
    public boolean active() {
        return !tracabilite.estSupprimee();
    }
}
