package africa.civitas.egen.kernel.pluginengine.lifecycle;

/** Le resultat d'une tentative de dechargement — meme discipline que {@link ResultatChargement}. */
public sealed interface ResultatDechargement {

    record Succes(int extensionsRetirees) implements ResultatDechargement {
    }

    record Echec(String motif) implements ResultatDechargement {
    }

    default boolean reussi() {
        return this instanceof Succes;
    }
}
