package africa.civitas.egen.kernel.identity;

import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;

import java.util.UUID;

/**
 * Le service Niveau 1 de la capacite Identite primitive — la seule implementation qui
 * existera jamais (contrairement a l'Identite riche, Niveau 2, pluggable via
 * {@code identity-provider-api}). Son unique responsabilite : reconnaitre le sujet
 * bootstrap et fabriquer des {@link KernelSubject} coherents, sans jamais savoir quoi
 * que ce soit de plus sur "qui" se cache derriere un identifiant ordinaire — cette
 * connaissance appartient entierement au provider Identite Niveau 2 charge.
 *
 * <p>Ce service ne persiste rien : reconnaitre le sujet bootstrap est une comparaison
 * a une constante ({@link KernelSubject#BOOTSTRAP_ID}), jamais une consultation d'une
 * base de donnees. C'est un choix delibere — le sujet bootstrap doit rester
 * reconnaissable meme si la base de donnees n'est pas encore accessible.
 */
public interface KernelSubjectService {

    /** @return vrai si {@code id} est l'identifiant reserve du sujet bootstrap. */
    boolean estBootstrap(UUID id);

    /**
     * Fabrique le {@link KernelSubject} correspondant a {@code id}, en detectant
     * correctement s'il s'agit du sujet bootstrap ou d'un sujet ordinaire.
     */
    KernelSubject resoudre(UUID id);

    /**
     * Represente ce sujet comme {@link Acteur}, pour le Socle de Traçabilite —
     * toujours {@code Acteur.systeme(...)}, jamais {@code Acteur.personne(...)} :
     * a ce niveau, le Kernel ignore delibrement si ce sujet correspond a une
     * Personne reelle une fois un provider Identite charge (cette connaissance
     * n'est jamais la sienne). Le sujet bootstrap produit le label constant
     * {@code "kernel-bootstrap"} ; tout autre sujet produit {@code
     * "kernel-sujet:" + id}, pour rester retrouvable dans un Journal ou un audit
     * sans jamais affirmer une correspondance Personne qui n'est pas du ressort de
     * ce primitif.
     */
    Acteur versActeur(KernelSubject sujet);
}

