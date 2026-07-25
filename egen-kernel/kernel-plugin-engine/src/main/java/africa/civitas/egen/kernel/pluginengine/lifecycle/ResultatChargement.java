package africa.civitas.egen.kernel.pluginengine.lifecycle;

import africa.civitas.egen.kernel.sdk.manifest.ManifesteExtension;

/**
 * Le resultat d'une tentative de chargement — jamais une exception pour un refus
 * attendu (capacite manquante, module non actif, Manifeste invalide) : ce sont des
 * issues normales du mecanisme, pas des anomalies. Seule une {@link
 * africa.civitas.egen.kernel.pluginengine.loader.PluginLoadException} imprevue
 * (JAR corrompu...) remonte encore comme exception, une fois toutes les
 * autorisations dejà acquises.
 */
public sealed interface ResultatChargement {

    record Succes(ManifesteExtension manifeste, int extensionsEnregistrees) implements ResultatChargement {
    }

    record Echec(String motif) implements ResultatChargement {
    }

    default boolean reussi() {
        return this instanceof Succes;
    }
}
