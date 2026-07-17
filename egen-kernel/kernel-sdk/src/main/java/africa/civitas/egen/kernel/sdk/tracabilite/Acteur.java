package africa.civitas.egen.kernel.sdk.tracabilite;

import java.util.UUID;

/**
 * Represente l'auteur d'une creation ou d'une modification, tel qu'exige par le Socle
 * de Traçabilite : "Cree par — la Personne a l'origine de la creation, ou la mention
 * 'Systeme' si l'action est automatisee/synchronisee."
 *
 * <p>Exactement un des deux champs est renseigne selon {@link #type()} — jamais les
 * deux, jamais aucun. Utiliser {@link #personne(UUID)} ou {@link #systeme(String)}
 * plutot que le constructeur canonique directement.
 *
 * @param type la nature de l'acteur
 * @param personneId renseigne uniquement si {@code type == PERSONNE}
 * @param systemeLabel renseigne uniquement si {@code type == SYSTEME}
 *                      (ex. {@code "synchronisation-keycloak"}, {@code "import-nightly"})
 */
public record Acteur(ActeurType type, UUID personneId, String systemeLabel) {

    public Acteur {
        if (type == null) {
            throw new IllegalArgumentException("Le type d'un Acteur ne peut pas etre nul.");
        }
        switch (type) {
            case PERSONNE -> {
                if (personneId == null) {
                    throw new IllegalArgumentException(
                            "Un Acteur de type PERSONNE doit porter un personneId.");
                }
                if (systemeLabel != null) {
                    throw new IllegalArgumentException(
                            "Un Acteur de type PERSONNE ne doit pas porter de systemeLabel.");
                }
            }
            case SYSTEME -> {
                if (systemeLabel == null || systemeLabel.isBlank()) {
                    throw new IllegalArgumentException(
                            "Un Acteur de type SYSTEME doit porter un systemeLabel non vide.");
                }
                if (personneId != null) {
                    throw new IllegalArgumentException(
                            "Un Acteur de type SYSTEME ne doit pas porter de personneId.");
                }
            }
        }
    }

    /** Construit un Acteur representant une Personne humaine identifiee. */
    public static Acteur personne(UUID personneId) {
        return new Acteur(ActeurType.PERSONNE, personneId, null);
    }

    /** Construit un Acteur representant un processus systeme/automatise nomme. */
    public static Acteur systeme(String label) {
        return new Acteur(ActeurType.SYSTEME, null, label);
    }
}
