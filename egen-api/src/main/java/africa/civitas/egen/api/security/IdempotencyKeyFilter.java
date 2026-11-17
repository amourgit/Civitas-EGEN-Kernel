package africa.civitas.egen.api.security;

import java.util.TreeMap;
import java.util.Optional;
import java.util.Map;
import java.util.List;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.inject.Inject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import africa.civitas.egen.application.port.StoredResponse;
import africa.civitas.egen.application.port.IdempotencyStorePort;
import africa.civitas.egen.application.port.IdempotencyStoreException;
import africa.civitas.egen.application.port.ErrorText;
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
import java.util.TreeSet;

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
 * <p>Les reponses a rejouer sont gardees par un {@link IdempotencyStorePort} : en production, PostgreSQL (la meme
 * base que le Registry), ce qui permet a une requete rejouee de tomber sur un AUTRE exemplaire du Kernel (phase P0,
 * etape P0-3 ; docs/architecture/13, « Idempotence »). Une cle n'est gardee que pour les reponses 2xx, pendant 10
 * minutes. Deux requetes SIMULTANEES portant la meme cle, avant que la premiere reponse ne soit gardee, sont toutes
 * deux traitees : limite connue, inchangee depuis l'implementation en memoire (docs/architecture/13).</p>
 */
@Provider
@IdempotencyRequired
@Priority(Priorities.HEADER_DECORATOR)
public class IdempotencyKeyFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String HEADER = "Idempotency-Key";
    private static final String CACHE_KEY_PROPERTY = "egen.idempotency.cacheKey";
    private static final System.Logger LOG = System.getLogger(IdempotencyKeyFilter.class.getName());
    private static final Duration TTL = Duration.ofMinutes(10);
    // Les noms d'en-tete HTTP sont insensibles a la casse (RFC 7230, section
    // 3.2) — TreeSet avec CASE_INSENSITIVE_ORDER plutot qu'un Set.of ordinaire.
    private static final Set<String> REPLAY_EXCLUDED_HEADERS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    static {
        REPLAY_EXCLUDED_HEADERS.add("Content-Length");
        REPLAY_EXCLUDED_HEADERS.add("Date");
        REPLAY_EXCLUDED_HEADERS.add("Content-Type"); // rejoue a part, a partir du type enregistre
    }

    @Inject
    IdempotencyStorePort store;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String key = requestContext.getHeaderString(HEADER);
        if (key == null || key.isBlank()) {
            throw new BadRequestException("En-tete " + HEADER + " obligatoire sur cette mutation");
        }
        // L'appelant fait partie de la clé : voir IdempotencyCacheKey (sans lui, un appelant pourrait recevoir la
        // réponse d'un autre, et sa propre requête ne serait jamais exécutée).
        java.security.Principal caller = requestContext.getSecurityContext().getUserPrincipal();
        String cacheKey = IdempotencyCacheKey.of(caller == null ? null : caller.getName(),
                requestContext.getMethod(), requestContext.getUriInfo().getPath(), key);
        Optional<StoredResponse> stored;
        try {
            stored = store.find(cacheKey, Instant.now());
        } catch (IdempotencyStoreException e) {
            // Impossible de savoir si cette mutation a deja eu lieu : on la refuse plutot que de risquer de la
            // refaire (docs/architecture/13, « Idempotence »).
            LOG.log(System.Logger.Level.WARNING, "Stockage d''idempotence indisponible (503) : {0}", ErrorText.of(e));
            throw new ServiceUnavailableException("Stockage d'idempotence indisponible : la mutation est refusee");
        }
        if (stored.isPresent()) {
            requestContext.abortWith(toReplay(stored.get()));
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
        if (status < 200 || status >= 300) {
            return;
        }
        try {
            // Toute la reponse originale est rejouee a l'identique en cas de repetition de la cle, pas seulement son
            // corps : un en-tete comme Location (voir ServiceResource.declare()) fait partie du contrat de la reponse
            // au meme titre que le corps JSON. Content-Length et Date sont exclus expres : le conteneur les recalcule
            // a partir des octets REELLEMENT ecrits au moment du rejeu.
            Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            responseContext.getHeaders().forEach((name, values) -> {
                if (!REPLAY_EXCLUDED_HEADERS.contains(name)) {
                    headers.put(name, values.stream().map(String::valueOf).toList());
                }
            });
            Object entity = responseContext.getEntity();
            String body = entity == null ? null : objectMapper.writeValueAsString(entity);
            MediaType mediaType = responseContext.getMediaType();
            String type = mediaType != null ? mediaType.toString() : (body != null ? MediaType.APPLICATION_JSON : null);
            Instant now = Instant.now();
            store.save((String) cacheKey, new StoredResponse(status, type, body, headers), now, now.plus(TTL));
        } catch (JsonProcessingException | IdempotencyStoreException e) {
            // La mutation a reussi : on ne la fait pas echouer. Seul son rejeu n'est plus possible.
            LOG.log(System.Logger.Level.WARNING, "Reponse non gardee pour le rejeu : {0}", ErrorText.of(e));
        }
    }

    private static Response toReplay(StoredResponse stored) {
        Response.ResponseBuilder builder = Response.status(stored.status());
        if (stored.body() != null) {
            builder.entity(stored.body());
        }
        if (stored.mediaType() != null) {
            builder.type(stored.mediaType());
        }
        stored.headers().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        return builder.header("Idempotency-Replayed", "true").build();
    }
}
