package africa.civitas.egen.kernel.bootstrap;

import africa.civitas.egen.kernel.bootstrap.boot.KernelBootSequence;
import africa.civitas.egen.kernel.bootstrap.boot.RapportDemarrage;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;

/**
 * Le point d'entree reel du Kernel EGEN — la racine de composition (voir le
 * pom.xml de ce module pour le detail des dependances assemblees ici).
 *
 * <p>Aucune logique metier ici : cette classe ne fait que declencher la sequence de
 * demarrage ({@link KernelBootSequence}, cablee par {@code config.KernelBootConfig})
 * et journaliser son bilan. Quarkus lui-meme initialise tous les beans CDI avant que
 * {@link #run} ne soit invoque — l'ordre de demarrage (aucun composant ne demarre
 * avant ses dependances) est garanti par le graphe de dependances CDI, pas par du
 * code ecrit ici.
 */
@QuarkusMain
public class EgenKernelApplication implements QuarkusApplication {

    @Inject
    KernelBootSequence kernelBootSequence;

    @Override
    public int run(String... args) {
        Log.info("EGEN Kernel — demarrage.");

        RapportDemarrage rapport = kernelBootSequence.demarrer();
        Log.info("EGEN Kernel — " + rapport.resume());

        Log.info("EGEN Kernel — pret.");
        Quarkus.waitForExit();
        return 0;
    }
}
