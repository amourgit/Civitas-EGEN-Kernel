package africa.civitas.egen.kernel.bootstrap.boot;

import africa.civitas.egen.kernel.pluginengine.lifecycle.PluginLifecycleManager;
import africa.civitas.egen.kernel.pluginengine.lifecycle.ResultatChargement;
import africa.civitas.egen.kernel.pluginengine.loader.CandidatModule;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * La sequence de demarrage du Kernel — decouvrir les candidats du repertoire de
 * plugins, puis tenter de charger chacun. Charger un module est une operation
 * administrative simple (voir {@link PluginLifecycleManager}) : cette sequence ne
 * consulte, ni ne suppose, aucune notion de perimetre applicatif ou d'autorisation
 * pour le faire.
 *
 * <p>Classe volontairement simple (pas de bean CDI ici) : instanciable a la main
 * dans les tests, comme {@code PluginLifecycleManager} lui-meme.
 */
public final class KernelBootSequence {

    private final PluginDirectoryScanner scanner;
    private final PluginLifecycleManager pluginLifecycleManager;
    private final Path repertoirePlugins;

    public KernelBootSequence(
            PluginDirectoryScanner scanner,
            PluginLifecycleManager pluginLifecycleManager,
            Path repertoirePlugins) {
        this.scanner = Objects.requireNonNull(scanner, "scanner ne peut pas etre nul.");
        this.pluginLifecycleManager = Objects.requireNonNull(pluginLifecycleManager, "pluginLifecycleManager ne peut pas etre nul.");
        this.repertoirePlugins = Objects.requireNonNull(repertoirePlugins, "repertoirePlugins ne peut pas etre nul.");
    }

    public RapportDemarrage demarrer() {
        List<CandidatModule> candidats = scanner.scanner(repertoirePlugins);

        List<String> charges = new ArrayList<>();
        List<RapportDemarrage.Echec> echecs = new ArrayList<>();

        for (CandidatModule candidat : candidats) {
            ResultatChargement resultat = pluginLifecycleManager.charger(candidat);

            switch (resultat) {
                case ResultatChargement.Succes succes -> charges.add(succes.manifeste().moduleId());
                case ResultatChargement.Echec echec ->
                        echecs.add(new RapportDemarrage.Echec(candidat.cheminPlugin().toString(), echec.motif()));
            }
        }

        return new RapportDemarrage(candidats.size(), List.copyOf(charges), List.copyOf(echecs));
    }
}
