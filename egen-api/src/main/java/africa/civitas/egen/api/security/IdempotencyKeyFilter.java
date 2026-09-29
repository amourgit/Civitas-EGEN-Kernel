package africa.civitas.egen.api.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * En-tete {@code Idempotency-Key} obligatoire sur toute mutation de l'API
 * de controle (voir docs/architecture/13-api-et-contrats.md, "Idempotency-Key
 * (en-tete HTTP) obligatoire sur toutes les mutations") : un agent ou un
 * client CI qui retente un appel apres un timeout reseau doit pouvoir le
 * faire sans risque de double-declaration.
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
@Priority(Priorities.HEADER_DECORATOR)
public class IdempotencyKeyFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String HEADER = "Idempotency-Key";
    private static final String CACHE_KEY_PROPERTY = "egen.idempotency.cacheKey";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Duration TTL = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, CachedResponse> cache = new ConcurrentHashMap<>();

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String method = requestContext.getMethod();
        if (!MUTATING_METHODS.contains(method) || !requestContext.getUriInfo().getPath().startsWith("api/v1/services")) {
            return;
        }
        String key = requestContext.getHeaderString(HEADER);
        if (key == null || key.isBlank()) {
            throw new BadRequestException("En-tete " + HEADER + " obligatoire sur cette mutation");
        }
        String cacheKey = method + " " + requestContext.getUriInfo().getPath() + " " + key;
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
            cache.put((String) cacheKey, new CachedResponse(status, responseContext.getEntity(),
                    responseContext.getMediaType(), Instant.now()));
        }
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minus(TTL);
        cache.values().removeIf(entry -> entry.storedAt().isBefore(cutoff));
    }

    private record CachedResponse(int status, Object entity, jakarta.ws.rs.core.MediaType mediaType, Instant storedAt) {
        Response toResponse() {
            Response.ResponseBuilder builder = Response.status(status);
            if (entity != null) {
                builder.entity(entity);
            }
            if (mediaType != null) {
                builder.type(mediaType);
            }
            return builder.header("Idempotency-Replayed", "true").build();
        }
    }
}
