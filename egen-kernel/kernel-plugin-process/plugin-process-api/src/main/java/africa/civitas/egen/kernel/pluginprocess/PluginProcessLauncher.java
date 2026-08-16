package africa.civitas.egen.kernel.pluginprocess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Lance un processus plugin et attend son {@link PluginProcessHandshake} sur sa
 * sortie standard, avec un delai borne — jamais une attente indefinie.
 *
 * <p>La sortie d'erreur du processus est heritee de celle de l'hote (visible dans
 * ses propres logs, jamais perdue) ; sa sortie standard, elle, n'est jamais lue
 * au-dela de la premiere ligne par cette classe — un processus plugin bien ecrit
 * n'y ecrit d'ailleurs jamais rien d'autre que cette ligne unique.
 *
 * <p>En cas d'echec a n'importe quelle etape (delai depasse, processus termine
 * avant d'avoir parle, ligne recue mais malformee), le processus est toujours
 * arrete de force avant que l'exception ne remonte — jamais de processus orphelin
 * laisse tourner apres un lancement rate.
 */
public final class PluginProcessLauncher {

    private final Duration delaiHandshake;

    public PluginProcessLauncher(Duration delaiHandshake) {
        if (delaiHandshake == null || delaiHandshake.isNegative() || delaiHandshake.isZero()) {
            throw new IllegalArgumentException("delaiHandshake doit etre strictement positif.");
        }
        this.delaiHandshake = delaiHandshake;
    }

    /**
     * @param commande la commande complete a executer (ex. {@code java -jar
     *                 plugin-runtime.jar})
     * @param repertoireTravail le repertoire de travail du processus lance
     * @throws PluginProcessException si le processus ne peut pas etre lance, ne
     *                                  transmet pas de handshake valide dans le
     *                                  delai imparti, ou se termine avant de l'avoir
     *                                  fait
     */
    public PluginProcessHandle lancer(List<String> commande, Path repertoireTravail) {
        Objects.requireNonNull(commande, "commande ne peut pas etre nulle.");
        if (commande.isEmpty()) {
            throw new IllegalArgumentException("commande ne peut pas etre vide.");
        }
        Objects.requireNonNull(repertoireTravail, "repertoireTravail ne peut pas etre nul.");

        ProcessBuilder constructeur = new ProcessBuilder(commande)
                .directory(repertoireTravail.toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT);

        Process processus;
        try {
            processus = constructeur.start();
        } catch (IOException e) {
            throw new PluginProcessException(
                    "Impossible de lancer le processus plugin (" + commande + ") : " + e.getMessage(), e);
        }

        String ligneHandshake = attendreHandshake(processus);

        if (ligneHandshake == null) {
            int codeSortie = processus.isAlive() ? -1 : processus.exitValue();
            processus.destroyForcibly();
            throw new PluginProcessException(
                    "Le processus plugin s'est termine avant d'envoyer son handshake (code de sortie "
                            + codeSortie + ").");
        }

        PluginProcessHandshake handshake;
        try {
            handshake = PluginProcessHandshake.depuisLigne(ligneHandshake);
        } catch (PluginProcessException e) {
            processus.destroyForcibly();
            throw e;
        }

        return new PluginProcessHandle(processus, handshake);
    }

    private String attendreHandshake(Process processus) {
        try (var executeur = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> lecture = executeur.submit(() -> lireUneLigne(processus));
            try {
                return lecture.get(delaiHandshake.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                processus.destroyForcibly();
                lecture.cancel(true);
                throw new PluginProcessException(
                        "Le processus plugin n'a pas envoye son handshake dans le delai imparti ("
                                + delaiHandshake + ").", e);
            } catch (ExecutionException e) {
                processus.destroyForcibly();
                throw new PluginProcessException(
                        "Echec de lecture du handshake du processus plugin : " + e.getCause(), e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                processus.destroyForcibly();
                throw new PluginProcessException("Interrompu en attendant le handshake du processus plugin.", e);
            }
        }
    }

    private static String lireUneLigne(Process processus) throws IOException {
        try (var lecteur = new BufferedReader(
                new InputStreamReader(processus.getInputStream(), StandardCharsets.UTF_8))) {
            return lecteur.readLine();
        }
    }
}
