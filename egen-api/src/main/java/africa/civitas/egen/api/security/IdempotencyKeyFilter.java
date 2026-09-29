package africa.civitas.egen.api.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * En-tete {@code Idempotency-Key} obligatoire sur toute mutation de l'API
 * de controle (voir docs/architecture/13-api-et-contrats.md, "Idempotency-Key
 * (en-tete HTTP) obligatoire sur toutes les mutations") : un agent ou un
 * client CI qui retente un appel apres un timeout reseau doit pouvoir le
 * faire sans risque de double-declaration.
 *
 * <p>Porte par Name Binding (voir {@link IdempotencyRequired}) plutot que
 * par une correspondance de chemin/methode faite a la main dans ce filtre —
 * voir le javadoc de {@link IdempotencyRequired} pour le raisonnement
 * complet (instabilite documentee de {@code UriInfo.getPath()} entre
 * versions de la specification).</p>
 *
 * <p>Implementation V1, deliberement simple et documentee comme telle :
 * cache en memoire du processus, borne par TTL — suffisant pour une
 * instance unique du Kernel de controle (aucune exigence de haute
 * disponibilite multi-instance n'est encore posee par
 * docs/architecture/19-feuille-de-route.md a ce stade). Un remplacement
 * par un stockage partage (ex. la meme base PostgreSQL que le Registry)
 * sera necessaire le jour ou plusieurs instances du Kernel tournent
 * derriere un equilibreur de charge — non fait par anticipation
 * (garde-fou n3, docs/architecture/02-principes-fondamentaux.md).</p>
 */
@Provider
@IdempotencyRequired
@Priority(Priorities.HEADER_DECORATOR)
public class IdempotencyKeyFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String HEADER = "Idempotency-Key";
    private static final String CACHE_KEY_PROPERTY = "egen.idempotency.cacheKey";
    private static final Duration TTL = Duration.ofMinutes(10);
    // Les noms d'en-tete HTTP sont insensibles a la casse (RFC 7230, section
    // 3.2) — TreeSet avec CASE_INSENSITIVE_ORDER plutot qu'un Set.of ordinaire.
    private static final Set<String> REPLAY_EXCLUDED_HEADERS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    static {
        REPLAY_EXCLUDED_HEADERS.add("Content-Length");
        REPLAY_EXCLUDED_HEADERS.add("Date");
    }

    private final ConcurrentHashMap<String, CachedResponse> cache = new ConcurrentHashMap<>();

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String key = requestContext.getHeaderString(HEADER);
        if (key == null || key.isBlank()) {
            throw new BadRequestException("En-tete " + HEADER + " obligatoire sur cette mutation");
        }
        String cacheKey = requestContext.getMethod() + " " + requestContext.getUriInfo().getPath() + " " + key;
        evictExpired();
        CachedResponse cached = cache.get(cacheKey);
        if (cached != null) {
            requestContext.abortWith(cached.toResponse());
            return;
        }
        requestContext.setProperty(CACHE_KEY_PROPERTY, cacheKey);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object cacheKey = requestContext.getProperty(CACHE_KEY_PROPERTY);
        if (cacheKey == null) {
            return;
        }
        int status = responseContext.getStatus();
        if (status >= 200 && status < 300) {
            // Toute la reponse originale est rejouee a l'identique en cas de
            // repetition de la cle, pas seulement son corps : un en-tete
            // comme Location (voir ServiceResource.declare()) fait partie du
            // contrat de la reponse au meme titre que le corps JSON.
            // Content-Length et Date sont exclus expres : ce sont des
            // en-tetes que le conteneur doit recalculer lui-meme a partir
            // des octets REELLEMENT ecrits au moment du rejeu (une valeur
            // Content-Length perimee provenant de la premiere serialisation
            // pourrait ne plus correspondre au corps effectivement renvoye).
            MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
            responseContext.getHeaders().forEach((name, values) -> {
                if (!REPLAY_EXCLUDED_HEADERS.contains(name)) {
                    headers.put(name, values);
                }
            });
            cache.put((String) cacheKey, new CachedResponse(status, responseContext.getEntity(), headers, Instant.now()));
        }
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minus(TTL);
        cache.values().removeIf(entry -> entry.storedAt().isBefore(cutoff));
    }

    private record CachedResponse(int status, Object entity, MultivaluedMap<String, Object> headers, Instant storedAt) {
        Response toResponse() {
            Response.ResponseBuilder builder = Response.status(status);
            if (entity != null) {
                builder.entity(entity);
            }
            headers.forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
            return builder.header("Idempotency-Replayed", "true").build();
        }
    }
}
