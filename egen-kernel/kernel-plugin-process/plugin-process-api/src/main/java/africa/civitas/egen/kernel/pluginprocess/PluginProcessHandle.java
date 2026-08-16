package africa.civitas.egen.kernel.pluginprocess;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Un processus plugin vivant, dont le handshake a deja ete recu et valide — jamais
 * construit directement, seulement via {@link PluginProcessLauncher#lancer}.
 */
public final class PluginProcessHandle {

    private final Process processus;
    private final PluginProcessHandshake handshake;

    PluginProcessHandle(Process processus, PluginProcessHandshake handshake) {
        this.processus = Objects.requireNonNull(processus, "processus ne peut pas etre nul.");
        this.handshake = Objects.requireNonNull(handshake, "handshake ne peut pas etre nul.");
    }

    public PluginProcessHandshake handshake() {
        return handshake;
    }

    public boolean estVivant() {
        return processus.isAlive();
    }

    /**
     * Demande un arret propre (signal de terminaison), puis force l'arret si le
     * processus n'a pas quitte dans {@code delaiArretPropre} — jamais une attente
     * indefinie, jamais un processus laisse orphelin.
     */
    public void arreter(Duration delaiArretPropre) {
        Objects.requireNonNull(delaiArretPropre, "delaiArretPropre ne peut pas etre nul.");
        if (!processus.isAlive()) {
            return;
        }
        processus.destroy();
        try {
            boolean termine = processus.waitFor(delaiArretPropre.toMillis(), TimeUnit.MILLISECONDS);
            if (!termine) {
                processus.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            processus.destroyForcibly();
        }
    }

    /** @throws IllegalThreadStateException si le processus est toujours vivant — voir {@link #estVivant()} avant. */
    public int codeSortie() {
        return processus.exitValue();
    }
}
