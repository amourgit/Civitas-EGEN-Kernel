package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.port.ConfigurationException;
import africa.civitas.egen.application.port.ConfigurationPort;
import africa.civitas.egen.application.port.MessagingException;
import africa.civitas.egen.application.port.MessagingPort;
import africa.civitas.egen.application.port.ResolvedConfig;
import africa.civitas.egen.application.port.SecretReference;
import africa.civitas.egen.application.port.SecretsException;
import africa.civitas.egen.application.port.SecretsPort;
import africa.civitas.egen.application.port.TopicSpec;
import africa.civitas.egen.domain.model.ConfigurationSpec;
import africa.civitas.egen.domain.model.EventSpec;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.TargetEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ce qu'un manifeste demande a l'environnement en dehors du deploiement
 * lui-meme : sa configuration et ses secrets (Condition {@code ConfigResolved})
 * et ses flux d'evenements (Condition {@code MessagingBindingsReady}) — voir
 * docs/architecture/09-cycle-de-vie.md et 04-moteur-de-reconciliation.md.
 *
 * <p><b>Jamais une valeur de secret</b> : seule l'EXISTENCE d'une reference est
 * verifiee ({@link SecretsPort#resolve} renvoie de quoi la monter, pas son
 * contenu) et les messages produits ne citent que des noms de cles, jamais
 * une valeur de configuration (docs/architecture/07, "Secrets Port").</p>
 */
public final class ServiceBindings {

    /** Resultat destine a une Condition : {@code reason} est stable (machine), {@code message} lisible. */
    public record Outcome(boolean ok, String reason, String message) {
        static Outcome ok(String reason, String message) {
            return new Outcome(true, reason, message);
        }

        static Outcome failed(String reason, String message) {
            return new Outcome(false, reason, message);
        }
    }

    private final SecretsPort secrets;
    private final ConfigurationPort configuration;
    private final MessagingPort messaging;

    private ServiceBindings(SecretsPort secrets, ConfigurationPort configuration, MessagingPort messaging) {
        this.secrets = secrets;
        this.configuration = configuration;
        this.messaging = messaging;
    }

    public static ServiceBindings of(SecretsPort secrets, ConfigurationPort configuration, MessagingPort messaging) {
        return new ServiceBindings(Objects.requireNonNull(secrets, "secrets"),
                Objects.requireNonNull(configuration, "configuration"), Objects.requireNonNull(messaging, "messaging"));
    }

    /**
     * Aucun port branche : un manifeste qui ne demande ni configuration ni
     * evenements converge normalement ; un manifeste qui en demande obtient
     * une Condition {@code False} explicite plutot qu'un echec silencieux.
     */
    public static ServiceBindings unavailable() {
        return new ServiceBindings(null, null, null);
    }

    /**
     * Nom DETERMINISTE du flux d'un type d'evenement : {@code EGEN-} puis le type
     * dont les points deviennent {@code ~}. Le point est interdit dans un nom de
     * flux NATS et {@code ~} ne fait pas partie des caracteres d'un type
     * (voir {@link EventSpec}) : l'encodage est donc injectif — deux types
     * distincts ne partagent jamais un flux.
     */
    public static String streamName(String eventType) {
        return "EGEN-" + eventType.replace('.', '~');
    }

    // ------------------------------------------------------------------ ConfigResolved

    public Outcome resolveConfiguration(ServiceId id, TargetEnvironment environment, ConfigurationSpec spec) {
        if (spec.isEmpty()) {
            return Outcome.ok("NothingToResolve", "Le manifeste ne declare ni configuration ni secret");
        }
        if ((!spec.secretsRefs().isEmpty() && secrets == null)
                || (!spec.required().isEmpty() && configuration == null)) {
            return Outcome.failed("PortNotConfigured",
                    "Le manifeste demande une configuration ou des secrets mais aucun port n'est branche");
        }

        List<String> unresolvedSecrets = new ArrayList<>();
        for (ConfigurationSpec.SecretReferenceSpec ref : spec.secretsRefs()) {
            try {
                secrets.resolve(new SecretReference(ref.key(), ref.provider(), ref.path()));
            } catch (SecretsException e) {
                // Le message de l'adapter cite le chemin de la reference, jamais une valeur.
                unresolvedSecrets.add(ref.key() + " (" + e.getMessage() + ")");
            }
        }
        if (!unresolvedSecrets.isEmpty()) {
            return Outcome.failed("SecretUnresolved", "references de secrets non resolues : "
                    + String.join("; ", unresolvedSecrets));
        }

        if (spec.required().isEmpty()) {
            return Outcome.ok("Resolved", spec.secretsRefs().size() + " reference(s) de secret resolue(s)");
        }
        ResolvedConfig resolved;
        try {
            resolved = configuration.resolve(id, environment);
        } catch (ConfigurationException e) {
            return Outcome.failed("ConfigurationUnavailable", "configuration d'environnement illisible : " + e.getMessage());
        }
        List<String> missing = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (ConfigurationSpec.Key key : spec.required()) {
            String value = resolved.values().containsKey(key.key()) ? resolved.values().get(key.key()) : key.defaultValue();
            if (value == null) {
                missing.add(key.key());
            } else if (!key.accepts(value)) {
                invalid.add(key.key() + " (attendu : " + key.type() + ")"); // jamais la valeur elle-meme
            }
        }
        if (!missing.isEmpty()) {
            return Outcome.failed("RequiredKeyMissing", "cles requises sans valeur ni defaut : " + String.join(", ", missing));
        }
        if (!invalid.isEmpty()) {
            return Outcome.failed("InvalidConfigurationValue", "valeurs du mauvais type : " + String.join(", ", invalid));
        }
        return Outcome.ok("Resolved", spec.required().size() + " cle(s) requise(s) et "
                + spec.secretsRefs().size() + " reference(s) de secret resolues");
    }

    // ------------------------------------------------------------------ MessagingBindingsReady

    /** Idempotent : garantit un flux par type d'evenement publie OU consomme (voir doc 12, "Flux d'evenements"). */
    public Outcome ensureEventStreams(EventSpec events) {
        List<String> types = events.allTypes();
        if (types.isEmpty()) {
            return Outcome.ok("NoEventsDeclared", "Le manifeste ne declare aucun evenement");
        }
        if (messaging == null) {
            return Outcome.failed("PortNotConfigured", "Le manifeste declare des evenements mais aucun port de messagerie n'est branche");
        }
        try {
            for (String type : types) {
                messaging.createTopicOrStream(new TopicSpec(streamName(type), type));
            }
        } catch (MessagingException e) {
            return Outcome.failed("MessagingUnavailable", "flux d'evenements non garantis : " + e.getMessage());
        }
        return Outcome.ok("StreamsEnsured", types.size() + " flux d'evenements garanti(s)");
    }
}
