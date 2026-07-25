package africa.civitas.egen.kernel.pluginengine.registry;

import africa.civitas.egen.kernel.sdk.extension.ExtensionPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le registre vivant des extensions actuellement chargees, tenu par le moteur de
 * plugins — jamais par PF4J directement : cette indirection est ce qui permettrait,
 * un jour, de remplacer le mecanisme physique de chargement ({@link
 * africa.civitas.egen.kernel.pluginengine.loader.PluginLoader}) sans que quoi que ce
 * soit qui consulte ce registre n'ait a changer.
 *
 * <p>Thread-safe : un {@link africa.civitas.egen.kernel.pluginengine.lifecycle.PluginLifecycleManager}
 * peut, en toute rigueur, etre sollicite pour charger et decharger des modules
 * concurremment.
 */
public final class ExtensionRegistry {

    private final Map<Class<? extends ExtensionPoint>, List<ExtensionDecouverte>> parPoint =
            new ConcurrentHashMap<>();

    public synchronized void enregistrer(ExtensionDecouverte decouverte) {
        if (decouverte == null) {
            throw new IllegalArgumentException("decouverte ne peut pas etre nulle.");
        }
        parPoint.computeIfAbsent(decouverte.point(), p -> new ArrayList<>()).add(decouverte);
    }

    public synchronized void enregistrerToutes(List<ExtensionDecouverte> decouvertes) {
        if (decouvertes == null) {
            throw new IllegalArgumentException("decouvertes ne peut pas etre nul.");
        }
        decouvertes.forEach(this::enregistrer);
    }

    /**
     * Retire toutes les extensions fournies par {@code moduleId} — appele
     * systematiquement au dechargement d'un module, pour qu'aucune extension d'un
     * module absent ne reste consultable.
     *
     * @return le nombre d'extensions retirees
     */
    public synchronized int desenregistrerToutPour(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId ne peut pas etre vide.");
        }
        int[] compteur = {0};
        parPoint.replaceAll((point, liste) -> {
            List<ExtensionDecouverte> restantes = liste.stream()
                    .filter(d -> {
                        boolean estDuModule = d.moduleId().equals(moduleId);
                        if (estDuModule) {
                            compteur[0]++;
                        }
                        return !estDuModule;
                    })
                    .toList();
            return new ArrayList<>(restantes);
        });
        parPoint.values().removeIf(List::isEmpty);
        return compteur[0];
    }

    /**
     * @return les implementations actuellement enregistrees pour {@code point},
     *         triees par priorite croissante (voir {@link
     *         africa.civitas.egen.kernel.sdk.extension.Extension#priority()}) —
     *         jamais nul, une liste vide si aucune extension ne sert ce point.
     */
    @SuppressWarnings("unchecked")
    public <T extends ExtensionPoint> List<T> obtenir(Class<T> point) {
        if (point == null) {
            throw new IllegalArgumentException("point ne peut pas etre nul.");
        }
        List<ExtensionDecouverte> decouvertes = parPoint.getOrDefault(point, List.of());
        return decouvertes.stream()
                .sorted(Comparator.comparingInt(ExtensionDecouverte::priority))
                .map(d -> (T) d.instance())
                .toList();
    }

    public int nombreDePointsServis() {
        return parPoint.size();
    }

    public int nombreTotalDeExtensions() {
        return parPoint.values().stream().mapToInt(List::size).sum();
    }
}
