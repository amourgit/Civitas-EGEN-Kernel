package africa.civitas.egen.kernel.moduleregistry.application;

import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.moduleregistry.infrastructure.ActivationRepository;
import africa.civitas.egen.kernel.moduleregistry.service.ModuleActivationResolver;
import africa.civitas.egen.kernel.sdk.permission.authorization.DecisionNoyau;
import africa.civitas.egen.kernel.sdk.permission.policy.PolitiqueNoyau;
import africa.civitas.egen.kernel.sdk.permission.policy.PolitiqueNoyauQuestion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class ModuleActivationResolverImpl implements ModuleActivationResolver {

    @Inject
    ActivationRepository repository;

    @Inject
    PolitiqueNoyau politiqueNoyau;

    @Override
    public DecisionNoyau estActifPour(UUID celluleId, ModuleId moduleId) {
        if (celluleId == null) {
            throw new IllegalArgumentException("celluleId ne peut pas etre nul.");
        }
        if (moduleId == null) {
            throw new IllegalArgumentException("moduleId ne peut pas etre nul.");
        }

        if (repository.existeActive(celluleId, moduleId.valeur())) {
            return DecisionNoyau.autorise(
                    "Activation active trouvee pour la Cellule " + celluleId
                            + " et le module '" + moduleId + "'.");
        }

        return politiqueNoyau.resoudre(PolitiqueNoyauQuestion.ACTIVATION_NON_RESOLUE);
    }
}
