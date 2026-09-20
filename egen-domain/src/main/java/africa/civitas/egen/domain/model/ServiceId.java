package africa.civitas.egen.domain.model;

import java.util.regex.Pattern;

/**
 * Identifiant stable d'un service dans l'ecosysteme EGEN (ex. "news-service").
 * Voir docs/architecture/05-modele-de-domaine.md.
 *
 * <p>Invariant applique a la construction (pas dans une couche de validation
 * separee) : un identifiant de service suit la meme convention que les noms
 * DNS/Kubernetes — minuscules, chiffres, tirets, sans tiret en tete/queue.
 * Cette convention garantit qu'un ServiceId est toujours utilisable tel quel
 * comme nom de job Nomad, nom de service Consul, ou sujet NATS.</p>
 */
public record ServiceId(String value) {

    private static final Pattern VALID_PATTERN =
            Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");

    public ServiceId {
        if (value == null || value.isBlank()) {
            throw new InvalidServiceIdException("ServiceId ne peut pas etre vide");
        }
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new InvalidServiceIdException(
                    "ServiceId invalide : \"" + value + "\" — attendu : minuscules, "
                            + "chiffres et tirets, sans tiret en tete ou en queue "
                            + "(ex. \"news-service\")");
        }
    }

    public static ServiceId of(String value) {
        return new ServiceId(value);
    }

    @Override
    public String toString() {
        return value;
    }

    /** Signale un identifiant de service qui ne respecte pas la convention. */
    public static final class InvalidServiceIdException extends IllegalArgumentException {
        public InvalidServiceIdException(String message) {
            super(message);
        }
    }
}
