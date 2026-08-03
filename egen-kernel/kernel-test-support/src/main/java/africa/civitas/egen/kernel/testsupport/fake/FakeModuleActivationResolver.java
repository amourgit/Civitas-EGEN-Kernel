package africa.civitas.egen.kernel.testsupport.fake;

import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.moduleregistry.service.ModuleActivationResolver;
import africa.civitas.egen.kernel.sdk.permission.authorization.DecisionNoyau;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Doublure de test de {@link ModuleActivationResolver} — refuse par defaut, comme le
 * veut le fail-closed reel. Version canonique, partagee entre kernel-plugin-engine
 * et kernel-bootstrap (aucun cycle de reacteur ici : ni l'un ni l'autre n'est
 * consomme, en retour, par kernel-test-support).
 */
public final class FakeModuleActivationResolver implements ModuleActivationResolver {

    private final Set<String> actifs = new HashSet<>();

    public FakeModuleActivationResolver activerPour(UUID celluleId, ModuleId moduleId) {
        actifs.add(cle(celluleId, moduleId));
        return this;
    }

    @Override
    public DecisionNoyau estActifPour(UUID celluleId, ModuleId moduleId) {
        if (actifs.contains(cle(celluleId, moduleId))) {
            return DecisionNoyau.autorise("Activation de test presente.");
        }
        return DecisionNoyau.refuse("Aucune Activation de test pour " + celluleId + "/" + moduleId + ".");
    }

    private static String cle(UUID celluleId, ModuleId moduleId) {
        return celluleId + "|" + moduleId;
    }
}
