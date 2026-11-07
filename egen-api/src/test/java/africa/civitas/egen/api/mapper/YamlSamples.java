package africa.civitas.egen.api.mapper;

/**
 * Documents YAML d'essai pour les tests de limites (constat K10, docs/architecture/24). Chaque document « d'attaque » est
 * un YAML <b>bien formé</b> qui ne viole qu'UNE limite : sans cela, un test attendant un refus réussirait pour la
 * mauvaise raison (un document simplement mal écrit).
 */
final class YamlSamples {

    private YamlSamples() {
    }

    /** Un document ordinaire, de la forme d'un manifeste. */
    static String valid() {
        return "metadata:\n  name: news-service\n  version: 1.0.0\nitems:\n  - a\n  - b\n";
    }

    /** Un seul scalaire de {@code characters} caractères : viole la limite de taille. */
    static String ofSize(int characters) {
        return "key: " + "x".repeat(characters) + "\n";
    }

    /** {@code depth} niveaux de dictionnaires imbriqués, en style bloc (indentation). */
    static String deepBlock(int depth) {
        StringBuilder yaml = new StringBuilder();
        for (int level = 0; level < depth; level++) {
            yaml.append("  ".repeat(level)).append('k').append(level).append(":\n");
        }
        yaml.append("  ".repeat(depth)).append("leaf: 1\n");
        return yaml.toString();
    }

    /** {@code depth} tableaux imbriqués, en style flux : {@code a: [[[...]]]}. */
    static String deepFlow(int depth) {
        return "a: " + "[".repeat(depth) + "]".repeat(depth) + "\n";
    }

    /** La même clé écrite deux fois. */
    static String duplicateKey() {
        return "a: 1\na: 2\n";
    }

    /** La « bombe YAML » classique : chaque niveau référence 9 fois le précédent par alias. */
    static String aliasBomb() {
        StringBuilder yaml = new StringBuilder();
        yaml.append("a: &a [\"lol\",\"lol\",\"lol\",\"lol\",\"lol\",\"lol\",\"lol\",\"lol\",\"lol\"]\n");
        String previous = "a";
        for (char level = 'b'; level <= 'i'; level++) {
            yaml.append(level).append(": &").append(level).append(" [");
            for (int i = 0; i < 9; i++) {
                yaml.append(i == 0 ? "" : ",").append('*').append(previous);
            }
            yaml.append("]\n");
            previous = String.valueOf(level);
        }
        return yaml.toString();
    }

    /** Un alias qui se référence lui-même. */
    static String selfReferencing() {
        return "a: &a [*a]\n";
    }
}
