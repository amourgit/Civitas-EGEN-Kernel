package africa.civitas.egen.kernel.sdk.contexte;

import java.util.UUID;

/**
 * Contrat public d'un Contexte — le supertype unifie d'Organisation et de Cellule.
 *
 * <p>Tout systeme du Kernel qui a une portee variable (Politique, Souscription,
 * Activation, Ressource, Affectation...) reference un {@code contexteId} unique plutot
 * qu'une paire nullable "Organisation OU Cellule". Ce module SDK n'expose que ce
 * contrat minimal : l'identifiant et la nature. L'implementation reelle (les tables
 * Organisation et Cellule, avec tous leurs attributs) vit exclusivement dans
 * {@code organization-impl} (Systeme A2) et n'est jamais visible depuis kernel-sdk.
 *
 * <p>Un module metier qui a besoin de savoir "a quel Contexte suis-je rattache" ne
 * manipule que cette interface — jamais une entite JPA d'Organisation ou de Cellule.
 */
public interface Contexte {

    /** Identifiant unique du Contexte, stable dans le temps. */
    UUID id();

    /** Nature du Contexte : Organisation ou Cellule. */
    ContexteNature nature();
}
