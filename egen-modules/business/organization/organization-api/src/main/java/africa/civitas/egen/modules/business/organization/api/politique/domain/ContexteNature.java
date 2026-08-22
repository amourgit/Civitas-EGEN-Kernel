package africa.civitas.egen.modules.business.organization.api.politique.domain;

/**
 * Nature concrete du Contexte porteur d'une {@link Politique} — precise si {@code
 * contexteId} designe une Organisation ou une Cellule.
 *
 * <p>Anciennement porte par {@code kernel-sdk} (sous le meme nom), avant la passe de
 * neutralisation du Kernel : {@code Contexte} y est reduit a son strict minimum
 * ({@code UUID id()}), et cette distinction — comme toute connaissance de ce qu'un
 * Contexte represente concretement — appartient desormais entierement au module
 * Niveau 2 qui la definit. C'est exactement le cas ici : {@code Politique} est un
 * concept du domaine Organisation, et cette enumeration avec lui.
 */
public enum ContexteNature {
    ORGANISATION,
    CELLULE
}
