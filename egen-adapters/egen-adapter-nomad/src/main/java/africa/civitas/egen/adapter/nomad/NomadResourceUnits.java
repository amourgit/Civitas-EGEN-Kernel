package africa.civitas.egen.adapter.nomad;

import africa.civitas.egen.application.port.DeploymentException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversion des unites brutes d'un {@code DeploymentSpec} (ex. "500m",
 * "512Mi") vers les unites attendues par l'API Nomad (CPU en MHz entier,
 * memoire en MB entier). Voir docs/architecture/07-ports-et-adapters.md,
 * "Pieges connus" : un champ mal forme echoue silencieusement ou produit un
 * comportement de scheduling inattendu — ces conversions sont donc isolees
 * ici et testees explicitement.
 *
 * <p><b>Limite V1 assumee</b> : Nomad exprime le CPU en MHz absolus, sans
 * notion standardisee de "milli-coeur" comme Kubernetes. Ce module traite le
 * suffixe "m" comme une valeur MHz directe (ex. "500m" -&gt; 500 MHz) — une
 * approximation pragmatique a affiner avec un profilage materiel reel plutot
 * qu'une conversion milli-coeur/MHz universelle qui n'existe pas.</p>
 */
final class NomadResourceUnits {

    private static final Pattern CPU_PATTERN = Pattern.compile("^(\\d+)m?$");
    private static final Pattern MEMORY_PATTERN = Pattern.compile("^(\\d+)(Mi|Gi)$");

    private NomadResourceUnits() {
    }

    static int parseCpuMhz(String cpu) {
        Matcher matcher = CPU_PATTERN.matcher(cpu.trim());
        if (!matcher.matches()) {
            throw new DeploymentException(
                    "DeploymentSpec.cpu invalide pour l'adapter Nomad : \"" + cpu
                            + "\" — attendu un entier optionnellement suffixe de \"m\" (ex. \"500m\")");
        }
        return Integer.parseInt(matcher.group(1));
    }

    static int parseMemoryMb(String memory) {
        Matcher matcher = MEMORY_PATTERN.matcher(memory.trim());
        if (!matcher.matches()) {
            throw new DeploymentException(
                    "DeploymentSpec.memory invalide pour l'adapter Nomad : \"" + memory
                            + "\" — attendu un entier suffixe de \"Mi\" ou \"Gi\" (ex. \"512Mi\")");
        }
        int value = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2);
        if ("Gi".equals(unit)) {
            return value * 1024;
        }
        return value; // "Mi" : deja exprime en MB
    }
}
