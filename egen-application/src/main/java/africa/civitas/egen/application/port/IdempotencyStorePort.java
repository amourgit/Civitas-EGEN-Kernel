package africa.civitas.egen.application.port;

import java.time.Instant;
import java.util.Optional;

/**
 * Garde les reponses des mutations pour les rejouer quand la meme cle d'idempotence revient, y compris quand la
 * seconde requete arrive sur un AUTRE exemplaire du Kernel (docs/architecture/13, « Idempotence » ; phase P0,
 * etape P0-3). Une implementation partagee (PostgreSQL) rend cela possible ; une implementation en memoire sert aux
 * tests et au developpement avec un seul exemplaire.
 */
public interface IdempotencyStorePort {

    /**
     * @param cacheKey cle de cache (appelant, methode, chemin et valeur de l'en-tete, voir {@code IdempotencyCacheKey})
     * @param now      instant courant : une entree expiree est invisible
     * @return la reponse gardee, ou vide si la cle est inconnue ou expiree
     * @throws IdempotencyStoreException si le stockage est inaccessible
     */
    Optional<StoredResponse> find(String cacheKey, Instant now);

    /**
     * Garde une reponse. Les entrees expirees sont retirees au passage.
     *
     * @return {@code false} si rien n'a ete garde : la cle existe deja (la premiere reponse est conservee) ou la
     *         capacite maximale est atteinte (la requete a ete traitee normalement, seule sa reponse n'est plus
     *         rejouable : un appelant ne peut pas faire grossir le stockage sans limite)
     * @throws IdempotencyStoreException si le stockage est inaccessible
     */
    boolean save(String cacheKey, StoredResponse response, Instant now, Instant expiresAt);
}
