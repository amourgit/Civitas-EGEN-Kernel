package africa.civitas.egen.kernel.sdk.event;

import java.util.regex.Pattern;

/**
 * Identifie un type d'evenement publie sur le Bus d'Evenements de la plateforme
 * (Kafka ou NATS selon le choix operationnel — le Kernel ne connait que ce contrat,
 * jamais le protocole concret).
 *
 * <p>Le nom suit une convention hierarchique en points, du systeme emetteur vers le
 * fait precis : {@code "organisation.affectation.terminee"},
 * {@code "identite.personne.creee"}. Cette convention permet, plus tard, des
 * souscriptions par prefixe sans avoir a enumerer chaque type un par un.
 *
 * @param name le nom hierarchique du type d'evenement, en minuscules, segments
 *             separes par un point, chaque segment en kebab-case si necessaire
 *             (ex. {@code "organisation.affectation.terminee"})
 */
public record EventType(String name) {

    private static final Pattern NAME_PATTERN =
            Pattern.compile("^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+$");

    public EventType {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom d'un EventType ne peut pas etre vide.");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Le nom d'un EventType doit suivre le format 'systeme.entite.fait' "
                            + "en minuscules (ex. 'organisation.affectation.terminee'), recu : " + name);
        }
    }

    /**
     * @return le segment "systeme" d'origine de ce type d'evenement (premier segment
     *         avant le premier point), utile pour un routage ou un filtrage grossier.
     */
    public String systemeOrigine() {
        return name.substring(0, name.indexOf('.'));
    }
}
