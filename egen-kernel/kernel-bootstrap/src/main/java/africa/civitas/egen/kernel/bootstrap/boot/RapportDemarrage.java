package africa.civitas.egen.kernel.bootstrap.boot;

import java.util.List;
import java.util.Objects;

/**
 * Le bilan d'une passe de decouverte et de chargement au demarrage — jamais une
 * exception globale : un module refuse (Souscription absente, Manifeste invalide...)
 * n'empeche jamais le demarrage du Kernel ni le chargement des autres candidats.
 * Chaque refus reste une decision de gouvernance normale, deja motivee par {@link
 * africa.civitas.egen.kernel.pluginengine.lifecycle.ResultatChargement}.
 */
public record RapportDemarrage(int candidatsTrouves, List<String> modulesCharges, List<Echec> echecs) {

    public record Echec(String candidat, String motif) {
        public Echec {
            Objects.requireNonNull(candidat, "candidat ne peut pas etre nul.");
            Objects.requireNonNull(motif, "motif ne peut pas etre nul.");
        }
    }

    public RapportDemarrage {
        Objects.requireNonNull(modulesCharges, "modulesCharges ne peut pas etre nul.");
        Objects.requireNonNull(echecs, "echecs ne peut pas etre nul.");
    }

    public String resume() {
        return "Decouverte : " + candidatsTrouves + " candidat(s) — "
                + modulesCharges.size() + " charge(s) [" + String.join(", ", modulesCharges) + "], "
                + echecs.size() + " refuse(s) ou en echec"
                + (echecs.isEmpty() ? "." : " [" + resumeEchecs() + "].");
    }

    private String resumeEchecs() {
        return echecs.stream()
                .map(e -> e.candidat() + " : " + e.motif())
                .reduce((a, b) -> a + " ; " + b)
                .orElse("");
    }
}
