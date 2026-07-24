package africa.civitas.egen.kernel.sdk.permission.policy;

/**
 * Les questions fermees auxquelles la Politique-noyau doit repondre par defaut,
 * avant qu'aucun module de gouvernance Niveau 2 ne soit charge (Charte v3, §A.5 et
 * §A.3 : "le noyau a besoin d'une resolution de politique par defaut... avant meme
 * qu'un module de gouvernance metier ne soit charge").
 *
 * <p>Chacune de ces questions correspond a un moment reel et concret du cycle de vie
 * du Kernel ou une decision doit etre prise alors qu'aucune autorite Niveau 2
 * competente n'a encore statue. Ce n'est jamais une question de politique
 * organisationnelle (§B.12 — une Organisation ou une Cellule qui configure ses
 * propres regles) : il s'agit exclusivement du comportement de secours du Kernel
 * lui-meme.
 *
 * <p>Cet ensemble est ferme, au meme titre que {@link
 * africa.civitas.egen.kernel.sdk.permission.authorization.KernelCapability} : y
 * ajouter une question est une decision d'architecture, jamais une extension livree
 * par un module metier.
 */
public enum PolitiqueNoyauQuestion {

    /**
     * La construction d'un {@code ManifesteExtension} candidat a echoue (a leve une
     * {@code ManifestValidationException}) — que faire du module concerne ?
     * Rappel : un {@code ManifesteExtension} deja construit est toujours valide par
     * construction (voir son constructeur canonique) ; cette question porte sur la
     * TENTATIVE de construction, avant qu'elle ne reussisse ou echoue.
     */
    ECHEC_CONSTRUCTION_MANIFESTE,

    /**
     * Aucune Activation (Systeme B2, Catalogue/Souscription/Activation) n'a pu etre
     * tranchee pour un module Souscrit, dans un Contexte (Organisation ou Cellule)
     * donne — le module doit-il tourner malgre cette absence de decision explicite ?
     */
    ACTIVATION_NON_RESOLUE,

    /**
     * Un {@code KernelPermissionCheck} n'a trouve aucun octroi explicite pour le
     * sujet et la capacite demandes (et ce sujet n'est pas le sujet bootstrap).
     */
    CAPACITE_NOYAU_NON_ACCORDEE,

    /**
     * Un module de gouvernance Niveau 2 qui devrait normalement repondre a une
     * question donnee (ex. l'Autorisation riche, SpiceDB) n'est pas joignable ou pas
     * encore charge — cas generique, au-dela des trois questions specifiques
     * ci-dessus, pour toute situation ou le Kernel doit statuer seul faute de mieux.
     */
    GOUVERNANCE_NIVEAU2_INDISPONIBLE
}
