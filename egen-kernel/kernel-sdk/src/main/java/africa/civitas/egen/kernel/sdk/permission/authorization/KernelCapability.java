package africa.civitas.egen.kernel.sdk.permission.authorization;

/**
 * Les capacites noyau — un ensemble ferme, jamais etendu par un module Niveau 2,
 * exactement comme un ensemble de {@code CAP_*} POSIX est fixe par le noyau Linux et
 * non par les paquets installes par-dessus (Charte v3, §A.5).
 *
 * <p>Une capacite noyau ne concerne jamais une decision metier (est-ce que Samuel peut
 * editer ce Document ? — ca, c'est le Systeme d'Autorisation riche, {@code
 * authorization-provider-spicedb}, Niveau 2). Elle concerne exclusivement le droit
 * d'agir directement sur le mecanisme du Kernel lui-meme : charger un module, en
 * decharger un, enregistrer un point d'extension, ou administrer qui detient ces
 * memes capacites. Ces quatre-la, et seulement ces quatre-la, existent avant meme que
 * Keycloak ou SpiceDB ne soient disponibles — c'est justement pour ca qu'elles doivent
 * rester fermees : les ouvrir a l'extension reviendrait a laisser un module metier
 * definir lui-meme ce que le noyau a le droit de faire, ce qui est l'inverse exact du
 * paradigme (Charte v3, §A.1).
 */
public enum KernelCapability {

    /** Charger un module (l'activer effectivement dans le processus Kernel). */
    CHARGER_MODULE,

    /** Decharger un module actif. */
    DECHARGER_MODULE,

    /** Enregistrer une implementation face a un {@code ExtensionPoint} du Kernel. */
    ENREGISTRER_EXTENSION,

    /**
     * Accorder ou revoquer une capacite noyau a un autre {@link
     * africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject} que soi-meme.
     * Sans cette capacite explicite, seul le sujet bootstrap peut administrer les
     * capacites d'autrui — voir la regle fail-closed de {@code KernelPermissionCheck}.
     */
    ADMINISTRER_CAPACITES_NOYAU
}
