package africa.civitas.egen.bootstrap.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Regle d'architecture <b>A4</b> (docs/architecture/22-frontiere-avec-la-plateforme.md, 22.7) : le Kernel
 * ne connait aucun concept metier de la Plateforme.
 *
 * <p>« Aucun type, paquet ni champ de {@code egen-domain}, {@code egen-application}, {@code egen-api} ni
 * {@code egen-bootstrap} dont le nom correspond (sans tenir compte de la casse) a {@code cell},
 * {@code cellule}, {@code tenant}, {@code organization}, {@code organisation}, {@code assignment},
 * {@code affectation}, {@code catalog}, {@code catalogue} ou {@code domainContract} n'est accepte. »
 * Les modules {@code egen-adapters/*} sont exclus (l'API « catalog » de Consul y est legitime).</p>
 *
 * <p>« Correspond » se lit mot a mot : un identifiant est decoupe en mots (camelCase, soulignes, chiffres) et
 * viole la regle si l'un d'eux est un terme interdit — {@code TenantId}, {@code cellId} et
 * {@code ServiceCatalog} sont refuses, {@code Cellular} ou {@code organizational} ne le sont pas.
 * {@code domainContract}, compose de deux mots, est cherche tel quel dans l'identifiant.</p>
 *
 * <p>La regle est une classe a part pour que le test puisse la rejouer sur des classes volontairement
 * fautives et prouver qu'elle echoue (docs 17, niveau 4).</p>
 */
final class PlatformBoundaryRule {

    static final Set<String> FORBIDDEN_WORDS = Set.of(
            "cell", "cellule", "tenant", "organization", "organisation",
            "assignment", "affectation", "catalog", "catalogue");

    static final String FORBIDDEN_COMPOUND = "domaincontract";

    /** Les quatre modules soumis a la regle ; les adapters en sont exclus. */
    static final List<String> KERNEL_PACKAGES = List.of(
            "africa.civitas.egen.domain",
            "africa.civitas.egen.application",
            "africa.civitas.egen.api",
            "africa.civitas.egen.bootstrap");

    private PlatformBoundaryRule() {
    }

    /** Les violations, triees et sans doublon ; vide si la regle est respectee. */
    static List<String> violations(JavaClasses classes) {
        TreeSet<String> violations = new TreeSet<>();
        for (JavaClass javaClass : classes) {
            if (!isKernel(javaClass.getPackageName())) {
                continue;
            }
            StringBuilder packagePrefix = new StringBuilder();
            for (String segment : javaClass.getPackageName().split("\\.")) {
                packagePrefix.append(packagePrefix.length() == 0 ? "" : ".").append(segment);
                if (matches(segment)) {
                    violations.add("paquet " + packagePrefix);
                }
            }
            if (!javaClass.isAnonymousClass() && matches(javaClass.getSimpleName())) {
                violations.add("type " + javaClass.getName());
            }
            for (JavaField field : javaClass.getFields()) {
                if (matches(field.getName())) {
                    violations.add("champ " + javaClass.getName() + "#" + field.getName());
                }
            }
        }
        return List.copyOf(violations);
    }

    /** Vrai si l'identifiant porte un terme interdit. */
    static boolean matches(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        if (identifier.toLowerCase(Locale.ROOT).replace("_", "").contains(FORBIDDEN_COMPOUND)) {
            return true;
        }
        return Arrays.stream(identifier.split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[^A-Za-z0-9]+"))
                .map(word -> word.toLowerCase(Locale.ROOT))
                .anyMatch(FORBIDDEN_WORDS::contains);
    }

    private static boolean isKernel(String packageName) {
        return KERNEL_PACKAGES.stream().anyMatch(root -> packageName.equals(root) || packageName.startsWith(root + "."));
    }
}
