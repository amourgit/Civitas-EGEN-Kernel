package africa.civitas.egen.kernel.bootstrap.boot;

import africa.civitas.egen.kernel.pluginengine.lifecycle.PluginLifecycleManager;
import africa.civitas.egen.kernel.pluginengine.lifecycle.ResultatChargement;
import africa.civitas.egen.kernel.pluginengine.loader.CandidatModule;
import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * La sequence de demarrage du Kernel — decouvrir les candidats du repertoire de
 * plugins, puis tenter de charger chacun, pour le compte du sujet bootstrap (voir
 * {@link KernelSubject#sujetBootstrap()} : c'est exactement le moment ou aucun autre
 * sujet ne peut encore exister).
 *
 * <p><b>Decision de conception assumee sur le Contexte cible</b> : {@link
 * africa.civitas.egen.kernel.moduleregistry.service.ModuleActivationResolver}, consulte
 * par {@code PluginLifecycleManager}, verifie l'Activation d'un module pour UN
 * Contexte precis. Au tout premier demarrage, avant qu'aucun Contexte metier ne soit
 * necessairement consultable, cette sequence utilise un unique Contexte racine,
 * configure ({@code egen.kernel.contexte-racine}) — jamais decouvert dynamiquement.
 * Charger un module pour d'autres Contextes, au fil de l'exploitation reelle,
 * restera une operation administrative posterieure au demarrage, hors scope de
 * cette sequence — voir le README pour le detail de cette simplification assumee
 * pour cette premiere livraison.
 *
 * <p>Classe volontairement simple (pas de bean CDI ici) : instanciable a la main
 * dans les tests, comme {@code PluginLifecycleManager} lui-meme.
 */
public final class KernelBootSequence {

    private final PluginDirectoryScanner scanner;
    private final PluginLifecycleManager pluginLifecycleManager;
    private final Path repertoirePlugins;
    private final UUID contexteRacine;

    public KernelBootSequence(
            PluginDirectoryScanner scanner,
            PluginLifecycleManager pluginLifecycleManager,
            Path repertoirePlugins,
            UUID contexteRacine) {
        this.scanner = Objects.requireNonNull(scanner, "scanner ne peut pas etre nul.");
        this.pluginLifecycleManager = Objects.requireNonNull(pluginLifecycleManager, "pluginLifecycleManager ne peut pas etre nul.");
        this.repertoirePlugins = Objects.requireNonNull(repertoirePlugins, "repertoirePlugins ne peut pas etre nul.");
        this.contexteRacine = Objects.requireNonNull(contexteRacine, "contexteRacine ne peut pas etre nul.");
    }

    public RapportDemarrage demarrer() {
        List<CandidatModule> candidats = scanner.scanner(repertoirePlugins);

        List<String> charges = new ArrayList<>();
        List<RapportDemarrage.Echec> echecs = new ArrayList<>();

        for (CandidatModule candidat : candidats) {
            ResultatChargement resultat = pluginLifecycleManager.charger(
                    candidat, KernelSubject.sujetBootstrap(), contexteRacine);

            switch (resultat) {
                case ResultatChargement.Succes succes -> charges.add(succes.manifeste().moduleId());
                case ResultatChargement.Echec echec ->
                        echecs.add(new RapportDemarrage.Echec(candidat.cheminPlugin().toString(), echec.motif()));
            }
        }

        return new RapportDemarrage(candidats.size(), List.copyOf(charges), List.copyOf(echecs));
    }
}
