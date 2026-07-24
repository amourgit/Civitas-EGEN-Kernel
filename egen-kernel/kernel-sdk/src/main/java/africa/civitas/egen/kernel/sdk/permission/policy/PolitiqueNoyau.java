package africa.civitas.egen.kernel.sdk.permission.policy;

import africa.civitas.egen.kernel.sdk.permission.authorization.DecisionNoyau;

/**
 * La Politique-noyau — le vrai B1 de la Charte v3 (§A.3, §A.5), a ne jamais confondre
 * avec la Politique organisationnelle (§B.12, qui vit entierement dans le module
 * business Organization sous {@code organization.politique}). Les deux portent le
 * meme mot dans le langage courant ; ce n'est pas le meme systeme, et cette
 * distinction de nom (dans le code : {@code kernel.policy} contre {@code
 * organization.politique}) est une exigence explicite de la Charte, pas une nuance de
 * style.
 *
 * <p>La Politique organisationnelle repond a "quelles regles cette Organisation ou
 * cette Cellule s'est-elle donnees ?" — une question metier, Niveau 2, avec
 * configuration et Derogation en cascade. La Politique-noyau repond a une question
 * radicalement plus etroite et plus rigide : "que fait le Kernel, par defaut, quand
 * aucun module de gouvernance Niveau 2 competent n'a encore statue ?" — une question
 * Niveau 1, sans aucune configuration possible.
 *
 * <p><b>Fail-closed par contrat, sans aucune exception</b> : une implementation de ce
 * contrat DOIT toujours repondre {@link DecisionNoyau#refuse(String)} pour toute
 * question qu'on lui pose. Ce n'est pas une valeur par defaut parmi d'autres — c'est
 * la totalite du comportement attendu. Le jour ou une reponse differente devient
 * possible pour une question donnee, c'est qu'un module de gouvernance Niveau 2
 * competent a pris le relais sur CETTE question precise (ex. Souscription/Activation
 * tranchee par module-registry pour {@link PolitiqueNoyauQuestion#ACTIVATION_NON_RESOLUE})
 * — et c'est ce module Niveau 2, jamais la Politique-noyau elle-meme, qui rend alors
 * la vraie decision. La Politique-noyau ne "s'assouplit" jamais ; elle est simplement
 * court-circuitee par une autorite competente.
 */
public interface PolitiqueNoyau {

    /**
     * @param question la question fermee posee
     * @return toujours {@link DecisionNoyau#refuse(String)}, avec un motif qui nomme
     *         explicitement la question posee — jamais {@link DecisionNoyau#autorise(String)}
     */
    DecisionNoyau resoudre(PolitiqueNoyauQuestion question);
}
