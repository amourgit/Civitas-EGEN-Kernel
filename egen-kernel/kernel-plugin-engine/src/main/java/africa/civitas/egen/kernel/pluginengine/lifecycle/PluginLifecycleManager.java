package africa.civitas.egen.kernel.pluginengine.lifecycle;

import africa.civitas.egen.kernel.pluginengine.loader.CandidatModule;
import africa.civitas.egen.kernel.pluginengine.loader.PluginLoadException;
import africa.civitas.egen.kernel.pluginengine.loader.PluginLoader;
import africa.civitas.egen.kernel.pluginengine.manifest.ManifestReadException;
import africa.civitas.egen.kernel.pluginengine.manifest.ManifestReader;
import africa.civitas.egen.kernel.pluginengine.registry.ExtensionDecouverte;
import africa.civitas.egen.kernel.pluginengine.registry.ExtensionRegistry;
import africa.civitas.egen.kernel.sdk.manifest.ManifestValidationException;
import africa.civitas.egen.kernel.sdk.manifest.ManifesteExtension;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'orchestrateur du mode d'extension embarque (Charte d'Architecture, §6) — le
 * seul point d'entree pour charger ou decharger un module. Jamais appele
 * directement par un service quelconque : uniquement depuis kernel-bootstrap, au
 * demarrage, ou depuis une future interface d'administration.
 *
 * <p>Le flux exact de {@link #charger}, dans cet ordre strict, jamais permute :
 * <ol>
 *   <li>{@link ManifestReader} — le Manifeste du candidat se lit-il et se
 *       construit-il valablement ?</li>
 *   <li>Dependances — chaque module que le Manifeste declare requerir est-il deja
 *       charge ?</li>
 *   <li>Seulement alors, {@link PluginLoader#charger} — le chargement physique.</li>
 * </ol>
 *
 * <p>Ce gestionnaire ne prend aucune decision de gouvernance : charger ou decharger
 * un module est une operation administrative simple, jamais soumise a une
 * autorisation ou une politique portee par le Kernel lui-meme — voir la Charte,
 * chapitre 4 ("Ce que le Kernel n'est pas"). Toute politique plus fine sur qui a le
 * droit de declencher ce chargement reste la responsabilite de qui deploie et
 * exploite le Kernel, jamais la sienne.
 *
 * <p>Cette classe ne prend jamais de dependance directe sur PF4J, Quarkus/CDI mis a
 * part pour son propre cycle de vie de bean : {@link PluginLoader} est une
 * interface, injectee comme les autres. Elle est deliberement instanciable a la main
 * (constructeur simple, sans conteneur) — voir les tests, qui n'ont besoin ni de
 * Quarkus ni de Docker pour couvrir l'intégralite de cette logique de decision.
 */
@ApplicationScoped
public class PluginLifecycleManager {

    private final ManifestReader manifestReader;
    private final ExtensionRegistry extensionRegistry;
    private final PluginLoader pluginLoader;

    private final Map<String, ManifesteExtension> manifestesCharges = new ConcurrentHashMap<>();

    @Inject
    public PluginLifecycleManager(
            ManifestReader manifestReader,
            ExtensionRegistry extensionRegistry,
            PluginLoader pluginLoader) {
        this.manifestReader = requireNonNull(manifestReader, "manifestReader");
        this.extensionRegistry = requireNonNull(extensionRegistry, "extensionRegistry");
        this.pluginLoader = requireNonNull(pluginLoader, "pluginLoader");
    }

    /**
     * Tente de charger {@code candidat}.
     *
     * @throws PluginLoadException uniquement pour un echec technique imprevu du
     *                               chargement physique lui-meme — jamais pour un
     *                               echec attendu (Manifeste invalide, dependance
     *                               manquante), toujours restitue comme
     *                               {@link ResultatChargement.Echec}
     */
    public ResultatChargement charger(CandidatModule candidat) {
        if (candidat == null) {
            throw new IllegalArgumentException("candidat ne peut pas etre nul.");
        }

        ManifesteExtension manifeste;
        try {
            manifeste = manifestReader.lire(candidat.manifestSource());
        } catch (ManifestReadException | ManifestValidationException e) {
            return new ResultatChargement.Echec(
                    "Manifeste invalide ou illisible : " + e.getMessage());
        }

        if (manifestesCharges.containsKey(manifeste.moduleId())) {
            return new ResultatChargement.Echec(
                    "Le module '" + manifeste.moduleId() + "' est deja charge.");
        }

        List<String> dependancesManquantes = manifeste.dependencies().stream()
                .filter(dep -> !pluginLoader.estCharge(dep))
                .toList();
        if (!dependancesManquantes.isEmpty()) {
            return new ResultatChargement.Echec(
                    "Le module '" + manifeste.moduleId() + "' requiert les modules suivants, "
                            + "non charges : " + String.join(", ", dependancesManquantes) + ".");
        }

        List<ExtensionDecouverte> decouvertes = pluginLoader.charger(manifeste.moduleId(), candidat.cheminPlugin());
        extensionRegistry.enregistrerToutes(decouvertes);
        manifestesCharges.put(manifeste.moduleId(), manifeste);

        return new ResultatChargement.Succes(manifeste, decouvertes.size());
    }

    /**
     * Tente de decharger le module {@code moduleId}. Refuse tant qu'un autre module
     * charge declare en dependre — jamais de dechargement en cascade implicite :
     * chaque dechargement reste un acte explicite et delibere.
     */
    public ResultatDechargement decharger(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId ne peut pas etre vide.");
        }

        if (!manifestesCharges.containsKey(moduleId)) {
            return new ResultatDechargement.Echec("Le module '" + moduleId + "' n'est pas charge.");
        }

        List<String> dependants = manifestesCharges.values().stream()
                .filter(m -> m.dependencies().contains(moduleId))
                .map(ManifesteExtension::moduleId)
                .toList();
        if (!dependants.isEmpty()) {
            return new ResultatDechargement.Echec(
                    "Impossible de decharger '" + moduleId + "' : les modules suivants en dependent "
                            + "encore : " + String.join(", ", dependants) + ".");
        }

        int extensionsRetirees = extensionRegistry.desenregistrerToutPour(moduleId);
        pluginLoader.decharger(moduleId);
        manifestesCharges.remove(moduleId);

        return new ResultatDechargement.Succes(extensionsRetirees);
    }

    /**
     * Enregistrement manuel d'une extension isolee, sans passer par le chargement
     * complet d'un module.
     */
    public void enregistrerExtensionManuelle(ExtensionDecouverte decouverte) {
        if (decouverte == null) {
            throw new IllegalArgumentException("decouverte ne peut pas etre nulle.");
        }
        extensionRegistry.enregistrer(decouverte);
    }

    public Optional<ManifesteExtension> manifestePour(String moduleId) {
        return Optional.ofNullable(manifestesCharges.get(moduleId));
    }

    public List<String> modulesCharges() {
        return List.copyOf(manifestesCharges.keySet());
    }

    private static <T> T requireNonNull(T valeur, String nom) {
        if (valeur == null) {
            throw new IllegalArgumentException(nom + " ne peut pas etre nul.");
        }
        return valeur;
    }
}
