package africa.civitas.egen.kernel.bootstrap;

import africa.civitas.egen.kernel.bootstrap.boot.KernelBootSequence;
import africa.civitas.egen.kernel.bootstrap.boot.RapportDemarrage;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;

/**
 * Le point d'entree reel du Kernel EGEN — le seul module qui a le droit de dependre
 * de tous les systemes Niveau 0/1 et des providers Niveau 2 "system" a la fois
 * (voir le pom.xml de ce module pour le detail des dependances et l'exception
 * assumee a la regle d'isolation -impl).
 *
 * <p>Aucune logique metier ici : cette classe ne fait que declencher la sequence de
 * demarrage ({@link KernelBootSequence}, cablee par {@code config.KernelBootConfig})
 * et journaliser son bilan. Quarkus lui-meme initialise tous les beans CDI des
 * systemes Niveau 0/1 avant que {@link #run} ne soit invoque — l'ordre du DAG
 * (aucun systeme ne demarre avant ses dependances) est garanti par le graphe de
 * dependances CDI, pas par du code ecrit ici.
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
