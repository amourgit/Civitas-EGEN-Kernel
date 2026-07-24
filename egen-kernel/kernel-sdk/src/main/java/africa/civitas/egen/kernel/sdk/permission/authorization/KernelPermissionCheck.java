package africa.civitas.egen.kernel.sdk.permission.authorization;

import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;

/**
 * Verifie si un {@link KernelSubject} detient une {@link KernelCapability} donnee —
 * le pendant Niveau 1 de "verification de permission minimale", a cote de
 * l'Identity primitif, avant que Keycloak ou SpiceDB ne soient disponibles (Charte
 * v3, §A.5).
 *
 * <p><b>Fail-closed par contrat</b> : toute implementation de cette interface DOIT
 * refuser par defaut. L'absence d'une preuve explicite d'autorisation n'est jamais
 * interpretee comme une autorisation implicite — seul le sujet bootstrap ({@link
 * KernelSubject#bootstrap()}) est autorise sans qu'aucun octroi explicite n'existe
 * (il ne peut pas y en avoir, puisqu'aucun sujet ne peut lui en accorder avant que lui
 * n'existe). Tout autre sujet doit disposer d'un octroi trouvable, sans quoi la
 * reponse est necessairement {@link DecisionNoyau#refuse(String)}.
 *
 * <p>Cette interface ne dit rien de COMMENT un octroi est memorise ou retire — c'est
 * la responsabilite de l'implementation Niveau 1 (voir {@code kernel-systems/
 * authorization}). Aucun module Niveau 2 ne doit jamais fournir sa propre
 * implementation de ce contrat : contrairement a l'Autorisation riche (SpiceDB), ce
 * n'est pas pluggable — c'est precisement ce qui en fait un primitif.
 */
public interface KernelPermissionCheck {

    /**
     * @param sujet le sujet dont on verifie les droits
     * @param capacite la capacite noyau demandee
     * @return une decision toujours motivee ; {@link DecisionNoyau#autorise()} ne peut
     *         etre vrai que pour le sujet bootstrap ou pour un sujet disposant d'un
     *         octroi explicite et non revoque pour cette capacite precise
     */
    DecisionNoyau verifier(KernelSubject sujet, KernelCapability capacite);
}
