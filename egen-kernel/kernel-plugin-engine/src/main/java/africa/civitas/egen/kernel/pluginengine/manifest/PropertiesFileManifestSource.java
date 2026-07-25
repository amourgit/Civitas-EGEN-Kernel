package africa.civitas.egen.kernel.pluginengine.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Lit un Manifeste d'Extension depuis un fichier {@code .properties} sur disque —
 * l'implementation livree de {@link ManifestSource}. Meme format de base que le
 * descripteur natif de PF4J.
 */
public final class PropertiesFileManifestSource implements ManifestSource {

    private final Path chemin;

    public PropertiesFileManifestSource(Path chemin) {
        if (chemin == null) {
            throw new IllegalArgumentException("chemin ne peut pas etre nul.");
        }
        this.chemin = chemin;
    }

    @Override
    public Map<String, String> donneesBrutes() {
        if (!Files.isRegularFile(chemin)) {
            throw new ManifestReadException(
                    "Aucun fichier de Manifeste trouve a l'emplacement : " + chemin);
        }

        Properties proprietes = new Properties();
        try (InputStream flux = Files.newInputStream(chemin)) {
            proprietes.load(flux);
        } catch (IOException e) {
            throw new ManifestReadException(
                    "Impossible de lire le fichier de Manifeste " + chemin + " : " + e.getMessage(), e);
        }

        Map<String, String> donnees = new HashMap<>();
        for (String cle : proprietes.stringPropertyNames()) {
            donnees.put(cle, proprietes.getProperty(cle));
        }
        return donnees;
    }
}
