package africa.civitas.egen.api.rest;

import africa.civitas.egen.application.port.ErrorText;
import africa.civitas.egen.api.dto.ServiceConfigurationDto;
import africa.civitas.egen.api.dto.ConfigurationPublishedDto;
import africa.civitas.egen.api.dto.ConfigurationRejectedDto;
import africa.civitas.egen.api.security.EgenSecurityContext;
import africa.civitas.egen.api.security.IdempotencyRequired;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.application.port.TraceContext;
import africa.civitas.egen.application.port.TraceSpan;
import africa.civitas.egen.application.usecase.ConfigurationStoreUnavailableException;
import africa.civitas.egen.application.usecase.InvalidConfigurationException;
import africa.civitas.egen.application.usecase.PublishConfigurationUseCase;
import africa.civitas.egen.application.usecase.PublishedConfiguration;
import africa.civitas.egen.application.usecase.ReadConfigurationUseCase;
import africa.civitas.egen.application.usecase.ServiceConfiguration;
import africa.civitas.egen.application.usecase.ServiceNotFoundException;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.TargetEnvironment;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.lang.System.Logger.Level;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Publication et lecture de la configuration technique d'un service — evolution <b>A3</b> ; le contrat complet
 * est la source unique de docs/architecture/13-api-et-contrats.md (« Publication de configuration »).
 *
 * <p>Publier ({@code PUT}) est une modification, reservee aux administrateurs ; lire ({@code GET}) est ouvert a
 * tout appelant authentifie (docs 14). Le droit est verifie <b>avant toute lecture du corps</b> et avant de
 * chercher le service : le corps est donc recu comme flux brut et n'est consomme qu'apres. Le Kernel transporte des paires cle/valeur
 * typees par le manifeste ; il ne leur donne aucun sens metier (docs 22.5).</p>
 */
@Path("/api/v1/services/{id}/configuration")
@Authenticated
public class ServiceConfigurationResource {

    private static final System.Logger LOG = System.getLogger(ServiceConfigurationResource.class.getName());

    /** Une configuration technique tient en quelques ko ; au-dela, le corps est refuse sans etre lu en entier. */
    static final int MAX_BODY_BYTES = 1024 * 1024;

    // Les cles dupliquees sont une erreur du client, jamais un « dernier gagne » silencieux.
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    private final PublishConfigurationUseCase publishUseCase;
    private final ReadConfigurationUseCase readUseCase;
    private final RegistryStorePort registryStorePort;
    private final ObservabilityPort observabilityPort;
    private final EgenSecurityContext securityContext;
    private final String defaultEnvironment;

    @Inject
    public ServiceConfigurationResource(PublishConfigurationUseCase publishUseCase,
                                         ReadConfigurationUseCase readUseCase,
                                         RegistryStorePort registryStorePort,
                                         ObservabilityPort observabilityPort,
                                         EgenSecurityContext securityContext,
                                         @ConfigProperty(name = "egen.environment", defaultValue = "development")
                                         String defaultEnvironment) {
        this.publishUseCase = publishUseCase;
        this.readUseCase = readUseCase;
        this.registryStorePort = registryStorePort;
        this.observabilityPort = observabilityPort;
        this.securityContext = securityContext;
        this.defaultEnvironment = defaultEnvironment;
    }

    @PUT
    @IdempotencyRequired
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response publish(@PathParam("id") String id, @QueryParam("environment") String environment,
                            @HeaderParam("traceparent") String traceparent, InputStream body) {
        try (TraceSpan span = startHttpSpan("http.publishConfiguration", traceparent)) {
            span.setAttribute("service.id", id);
            try {
                // Modifier = administrateur (docs/architecture/14-securite.md), verifie AVANT toute lecture du corps
                // et avant de chercher le service : un non-administrateur recoit 403, meme pour un id inconnu.
                securityContext.assertAdmin();
                ServiceId serviceId = ServiceId.of(id);
                requireDeclared(serviceId); // 404 si inconnu, avant de lire le corps
                TargetEnvironment target = environmentOf(environment);

                Map<String, String> values;
                try {
                    values = readValues(body);
                } catch (InvalidBody e) {
                    return rejected(List.of(new ConfigurationRejectedDto.Violation(
                            e.key, "INVALID_VALUE", e.getMessage())));
                }

                PublishedConfiguration published = publishUseCase.publish(serviceId, target, values);
                String url = configurationUrl(serviceId, target);
                return Response.accepted(new ConfigurationPublishedDto(
                                published.serviceId().value(), published.environment().name(),
                                published.version(), url))
                        .header("Location", url)
                        .build();
            } catch (InvalidConfigurationException e) {
                return rejected(e.violations().stream()
                        .map(v -> new ConfigurationRejectedDto.Violation(v.key(), v.rule().name(), v.message()))
                        .toList());
            } catch (ServiceNotFoundException e) {
                throw new NotFoundException(e.getMessage());
            } catch (ConfigurationStoreUnavailableException e) {
                span.recordError(e);
                // La cause (code HTTP et corps de Consul, ou nom de l'exception reseau) va au journal (ecart E2).
                LOG.log(Level.WARNING, "Publication de configuration refusee (503) pour {0} : {1}", id,
                        ErrorText.of(e.getCause() != null ? e.getCause() : e));
                throw new ServiceUnavailableException("Store de configuration indisponible : rien n'a ete publie");
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            } catch (RuntimeException e) {
                span.recordError(e);
                throw e;
            }
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ServiceConfigurationDto read(@PathParam("id") String id, @QueryParam("environment") String environment,
                                 @HeaderParam("traceparent") String traceparent) {
        try (TraceSpan span = startHttpSpan("http.getConfiguration", traceparent)) {
            span.setAttribute("service.id", id);
            try {
                // Lire = tout appelant authentifie (docs/architecture/14-securite.md) : seul le 404 s'applique.
                ServiceId serviceId = ServiceId.of(id);
                requireDeclared(serviceId);
                ServiceConfiguration configuration = readUseCase.read(serviceId, environmentOf(environment));
                return new ServiceConfigurationDto(configuration.serviceId().value(), configuration.environment().name(),
                        configuration.version(), new TreeMap<>(configuration.values()));
            } catch (ServiceNotFoundException e) {
                throw new NotFoundException(e.getMessage());
            } catch (ConfigurationStoreUnavailableException e) {
                span.recordError(e);
                throw new ServiceUnavailableException("Store de configuration indisponible");
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            } catch (RuntimeException e) {
                span.recordError(e);
                throw e;
            }
        }
    }

    // ------------------------------------------------------------------ details

    /** 404 si le service n'est pas declare. */
    private void requireDeclared(ServiceId id) {
        registryStorePort.findById(id)
                .orElseThrow(() -> new NotFoundException("Service inconnu : " + id.value()));
    }

    private TargetEnvironment environmentOf(String environment) {
        return TargetEnvironment.of(environment != null && !environment.isBlank() ? environment : defaultEnvironment);
    }

    private static String configurationUrl(ServiceId id, TargetEnvironment environment) {
        return "/api/v1/services/" + id.value() + "/configuration?environment="
                + URLEncoder.encode(environment.name(), StandardCharsets.UTF_8);
    }

    private static Response rejected(List<ConfigurationRejectedDto.Violation> violations) {
        return Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                .entity(new ConfigurationRejectedDto("Configuration refusee", violations))
                .build();
    }

    /** Corps illisible ou hors contrat : le message nomme la cle et la regle, jamais la valeur. */
    private static final class InvalidBody extends RuntimeException {
        final String key;

        InvalidBody(String key, String message) {
            super(message);
            this.key = key;
        }
    }

    /**
     * {@code { "values": { "<cle>": <valeur> } }} : chaque valeur est un scalaire JSON (texte, booleen ou nombre)
     * converti en texte. Un objet, un tableau, {@code null} ou un champ de premier niveau autre que
     * {@code values} est refuse (docs 13).
     */
    private static Map<String, String> readValues(InputStream body) {
        JsonNode root;
        try {
            byte[] bytes = body.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new InvalidBody("$", "le corps depasse " + MAX_BODY_BYTES + " octets");
            }
            root = JSON.readTree(bytes);
        } catch (IOException e) {
            throw new InvalidBody("$", "corps JSON illisible");
        }
        if (root == null || !root.isObject()) {
            throw new InvalidBody("$", "le corps doit etre un objet { \"values\": { ... } }");
        }
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!name.equals("values")) {
                throw new InvalidBody(name, "champ de premier niveau non supporte (seul \"values\" est accepte)");
            }
        }
        JsonNode values = root.get("values");
        if (values == null || !values.isObject()) {
            throw new InvalidBody("values", "le champ \"values\" est obligatoire et doit etre un objet");
        }

        Map<String, String> converted = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> entries = values.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            JsonNode value = entry.getValue();
            if (value.isTextual() || value.isBoolean() || value.isNumber()) {
                converted.put(entry.getKey(), value.asText());
            } else {
                throw new InvalidBody(entry.getKey(), "la valeur doit etre un scalaire JSON (texte, booleen ou nombre) ; "
                        + (value.isNull() ? "null" : value.isArray() ? "un tableau" : "un objet") + " est refuse");
            }
        }
        return converted;
    }

    private TraceSpan startHttpSpan(String operationName, String traceparent) {
        TraceContext parent = (traceparent == null || traceparent.isBlank()) ? null : new TraceContext(traceparent);
        return observabilityPort.startSpan(operationName, parent);
    }
}
