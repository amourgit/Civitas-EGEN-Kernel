package africa.civitas.egen.kernel.authorization.application;

import africa.civitas.egen.kernel.authorization.infrastructure.KernelCapabiliteOctroiRepository;
import africa.civitas.egen.kernel.sdk.permission.authorization.DecisionNoyau;
import africa.civitas.egen.kernel.sdk.permission.authorization.KernelCapability;
import africa.civitas.egen.kernel.sdk.permission.authorization.KernelPermissionCheck;
import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;
import africa.civitas.egen.kernel.sdk.permission.policy.PolitiqueNoyau;
import africa.civitas.egen.kernel.sdk.permission.policy.PolitiqueNoyauQuestion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * L'unique implementation du primitif Authorization (Niveau 1). Fail-closed par
 * construction : voir {@link KernelPermissionCheck} pour le contrat exact.
 */
@ApplicationScoped
public class KernelPermissionCheckImpl implements KernelPermissionCheck {

    @Inject
    KernelCapabiliteOctroiRepository repository;

    @Inject
    PolitiqueNoyau politiqueNoyau;

    @Override
    public DecisionNoyau verifier(KernelSubject sujet, KernelCapability capacite) {
        if (sujet == null) {
            throw new IllegalArgumentException("sujet ne peut pas etre nul.");
        }
        if (capacite == null) {
            throw new IllegalArgumentException("capacite ne peut pas etre nulle.");
        }

        if (sujet.bootstrap()) {
            return DecisionNoyau.autorise(
                    "Sujet bootstrap : autorise pour toute capacite noyau, par construction "
                            + "(aucun octroi explicite n'est possible ni necessaire).");
        }

        if (repository.existeOctroiActif(sujet.id(), capacite)) {
            return DecisionNoyau.autorise(
                    "Octroi actif trouve pour le sujet " + sujet.id() + " et la capacite " + capacite + ".");
        }

        return politiqueNoyau.resoudre(PolitiqueNoyauQuestion.CAPACITE_NOYAU_NON_ACCORDEE);
    }
}
