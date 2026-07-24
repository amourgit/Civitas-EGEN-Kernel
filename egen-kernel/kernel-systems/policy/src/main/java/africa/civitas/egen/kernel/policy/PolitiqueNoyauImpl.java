package africa.civitas.egen.kernel.policy;

import africa.civitas.egen.kernel.sdk.permission.authorization.DecisionNoyau;
import africa.civitas.egen.kernel.sdk.permission.policy.PolitiqueNoyau;
import africa.civitas.egen.kernel.sdk.permission.policy.PolitiqueNoyauQuestion;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * L'unique implementation de la Politique-noyau (Niveau 1) — et la plus simple de
 * tout le Kernel : elle ne fait rien d'autre que refuser, systematiquement, en
 * nommant la question posee dans le motif. Voir {@link PolitiqueNoyau} pour la raison
 * de fond de cette rigidite totale — ce n'est pas une implementation provisoire en
 * attendant "mieux", c'est la totalite de ce qu'une Politique-noyau doit faire.
 *
 * <p>Aucune persistance, aucune configuration, aucun etat : chaque appel est pur et
 * deterministe. C'est precisement ce qui permet a ce module de fonctionner avant que
 * PostgreSQL, Keycloak ou SpiceDB ne soient joignables.
 */
@ApplicationScoped
public class PolitiqueNoyauImpl implements PolitiqueNoyau {

    @Override
    public DecisionNoyau resoudre(PolitiqueNoyauQuestion question) {
        if (question == null) {
            throw new IllegalArgumentException("question ne peut pas etre nulle.");
        }
        return DecisionNoyau.refuse(motifPour(question));
    }

    private static String motifPour(PolitiqueNoyauQuestion question) {
        return switch (question) {
            case ECHEC_CONSTRUCTION_MANIFESTE -> "Politique-noyau : la construction du Manifeste "
                    + "d'Extension a echoue — le module candidat n'est pas charge, par defaut.";
            case ACTIVATION_NON_RESOLUE -> "Politique-noyau : aucune Activation tranchee pour ce "
                    + "Contexte — le module Souscrit est considere inactif, par defaut.";
            case CAPACITE_NOYAU_NON_ACCORDEE -> "Politique-noyau : aucun octroi de capacite noyau "
                    + "trouve pour ce sujet — l'action est refusee, par defaut.";
            case GOUVERNANCE_NIVEAU2_INDISPONIBLE -> "Politique-noyau : aucun module de gouvernance "
                    + "Niveau 2 competent n'est disponible pour statuer — refus par defaut.";
        };
    }
}
