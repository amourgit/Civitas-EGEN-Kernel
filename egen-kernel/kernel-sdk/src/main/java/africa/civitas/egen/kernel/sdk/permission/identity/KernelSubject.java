package africa.civitas.egen.kernel.sdk.permission.identity;

import java.util.UUID;

/**
 * Le sujet minimal du Kernel — l'equivalent UID/GID de la Charte v3 (§A.5, Niveau 1).
 *
 * <p>Un {@code KernelSubject} n'est rien d'autre qu'un identifiant opaque. Il ne porte
 * aucun nom, aucun email, aucun profil : cette richesse-la appartient entierement a la
 * capacite Identite riche (aujourd'hui {@code egen-modules/system/identity/
 * identity-provider-keycloak}, potentiellement d'autres providers demain), jamais au
 * Kernel lui-meme. Exactement comme un UID Linux est "juste un numero" pour le noyau,
 * qui ignore tout de qui se cache derriere tant qu'un module utilisateur (NSS, LDAP...)
 * ne le lui dit pas — un {@code KernelSubject} est "juste un identifiant" pour le
 * Kernel EGEN, qui ignore tout de la Personne qu'il designe eventuellement une fois un
 * provider Identite charge.
 *
 * <p>Le champ {@link #bootstrap()} distingue LE sujet reserve qui existe avant meme que
 * quoi que ce soit d'autre ne soit charge — l'equivalent d'UID 0 (root). Son identifiant
 * ({@link #BOOTSTRAP_ID}) est une constante fixe et connue de tous, jamais generee : un
 * sujet bootstrap doit etre reconnaissable sans avoir besoin d'interroger quoi que ce
 * soit. Il ne peut exister qu'un seul sujet bootstrap par instance — {@link #bootstrap()}
 * retourne toujours la meme instance canonique.
 *
 * <p>Ce contrat ne dit jamais si un identifiant "existe" ou "est valide" au sens metier
 * (a-t-il une Personne correspondante, un Compte actif...) — cette responsabilite
 * appartient au provider Identite charge, jamais a ce primitif. Le Kernel accepte un
 * {@code KernelSubject} pour ce qu'il est : un identifiant, rien de plus.
 *
 * @param id identifiant opaque du sujet — {@link #BOOTSTRAP_ID} pour le sujet bootstrap,
 *           n'importe quel autre UUID sinon (typiquement, mais jamais necessairement,
 *           le meme UUID qu'une Personne une fois un provider Identite en place)
 * @param bootstrap vrai uniquement pour le sujet reserve du tout premier demarrage
 */
public record KernelSubject(UUID id, boolean bootstrap) {

    /**
     * Identifiant fixe et reserve du sujet bootstrap — jamais genere aleatoirement,
     * exactement comme UID 0 n'est jamais "attribue" mais toujours le meme sur tout
     * systeme Linux. Ne jamais attribuer cet identifiant a un autre sujet.
     */
    public static final UUID BOOTSTRAP_ID = new UUID(0L, 0L);

    public KernelSubject {
        if (id == null) {
            throw new IllegalArgumentException("L'identifiant d'un KernelSubject ne peut pas etre nul.");
        }
        if (bootstrap && !id.equals(BOOTSTRAP_ID)) {
            throw new IllegalArgumentException(
                    "Un KernelSubject bootstrap doit obligatoirement porter l'identifiant "
                            + "reserve KernelSubject.BOOTSTRAP_ID, jamais un identifiant genere.");
        }
        if (!bootstrap && id.equals(BOOTSTRAP_ID)) {
            throw new IllegalArgumentException(
                    "L'identifiant reserve KernelSubject.BOOTSTRAP_ID ne peut designer "
                            + "qu'un sujet bootstrap (bootstrap = true).");
        }
    }

    /** @return le sujet bootstrap canonique — l'unique sujet valide avant tout autre chargement. */
    public static KernelSubject sujetBootstrap() {
        return new KernelSubject(BOOTSTRAP_ID, true);
    }

    /**
     * Construit un sujet ordinaire (non bootstrap) a partir d'un identifiant deja
     * connu — typiquement l'identifiant d'une Personne resolue par un provider
     * Identite. Le Kernel ne verifie ni ne suppose rien de plus sur cet identifiant.
     */
    public static KernelSubject de(UUID id) {
        return new KernelSubject(id, false);
    }

    /** Construit un sujet ordinaire portant un identifiant nouvellement genere. */
    public static KernelSubject nouveau() {
        return de(UUID.randomUUID());
    }
}
