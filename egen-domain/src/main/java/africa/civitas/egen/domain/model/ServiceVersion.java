package africa.civitas.egen.domain.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Version semantique (SemVer 2.0.0) d'un service. Voir
 * docs/architecture/05-modele-de-domaine.md et
 * docs/architecture/06-service-manifest.md.
 *
 * <p>Refuse de se construire si la chaine ne respecte pas SemVer — invariant
 * applique dans le constructeur, comme pour tout Value Object du domaine.</p>
 */
public record ServiceVersion(int major, int minor, int patch, String preRelease)
        implements Comparable<ServiceVersion> {

    // Version simplifiee de la regex SemVer officielle (semver.org) : suffisante
    // pour distinguer major.minor.patch[-preRelease], sans les metadonnees de build
    // ("+...") qui n'affectent jamais la precedence et ne sont pas necessaires au
    // Kernel pour l'instant.
    private static final Pattern SEMVER_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$");

    public ServiceVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new InvalidServiceVersionException(
                    "Les composants d'une ServiceVersion ne peuvent pas etre negatifs");
        }
    }

    /**
     * Analyse une chaine SemVer (ex. "2.4.0" ou "2.4.0-rc.1").
     *
     * @throws InvalidServiceVersionException si la chaine ne respecte pas SemVer
     */
    public static ServiceVersion parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidServiceVersionException("ServiceVersion ne peut pas etre vide");
        }
        Matcher matcher = SEMVER_PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new InvalidServiceVersionException(
                    "ServiceVersion invalide : \"" + raw + "\" — attendu un format "
                            + "SemVer (MAJOR.MINOR.PATCH[-preRelease]), ex. \"2.4.0\"");
        }
        return new ServiceVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                matcher.group(4));
    }

    public boolean isPreRelease() {
        return preRelease != null && !preRelease.isBlank();
    }

    @Override
    public int compareTo(ServiceVersion other) {
        int cmp = Integer.compare(this.major, other.major);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.minor, other.minor);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.patch, other.patch);
        if (cmp != 0) return cmp;
        // Par convention SemVer, une version sans pre-release a une precedence
        // plus haute qu'une version avec pre-release, a major.minor.patch egaux.
        if (this.isPreRelease() && !other.isPreRelease()) return -1;
        if (!this.isPreRelease() && other.isPreRelease()) return 1;
        if (this.isPreRelease()) {
            // Arrivee ici, other.isPreRelease() est necessairement vrai aussi :
            // s'il etait faux, la condition juste au-dessus
            // (this.isPreRelease() && !other.isPreRelease()) aurait deja
            // retourne -1. Qodana signalait cette verification redondante
            // ("Constant values").
            return this.preRelease.compareTo(other.preRelease);
        }
        return 0;
    }

    @Override
    public String toString() {
        return isPreRelease()
                ? "%d.%d.%d-%s".formatted(major, minor, patch, preRelease)
                : "%d.%d.%d".formatted(major, minor, patch);
    }

    /** Signale une chaine de version qui ne respecte pas SemVer. */
    public static final class InvalidServiceVersionException extends IllegalArgumentException {
        public InvalidServiceVersionException(String message) {
            super(message);
        }
    }
}
