package africa.civitas.egen.kernel.pluginengine.testsupport;

import africa.civitas.egen.kernel.sdk.permission.authorization.DecisionNoyau;
import africa.civitas.egen.kernel.sdk.permission.authorization.KernelCapability;
import africa.civitas.egen.kernel.sdk.permission.authorization.KernelPermissionCheck;
import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;

import java.util.HashSet;
import java.util.Set;

/**
 * Doublure de test de {@link KernelPermissionCheck}, fidele au contrat reel : le
 * sujet bootstrap est toujours autorise ; tout autre sujet doit avoir ete
 * explicitement autorise via {@link #autoriser} pour la capacite precise demandee.
 */
public final class FakeKernelPermissionCheck implements KernelPermissionCheck {

    private final Set<String> autorisations = new HashSet<>();

    public FakeKernelPermissionCheck autoriser(KernelSubject sujet, KernelCapability capacite) {
        autorisations.add(cle(sujet, capacite));
        return this;
    }

    @Override
    public DecisionNoyau verifier(KernelSubject sujet, KernelCapability capacite) {
        if (sujet.bootstrap()) {
            return DecisionNoyau.autorise("Sujet bootstrap (test).");
        }
        if (autorisations.contains(cle(sujet, capacite))) {
            return DecisionNoyau.autorise("Octroi de test present.");
        }
        return DecisionNoyau.refuse("Aucun octroi de test pour " + sujet.id() + "/" + capacite + ".");
    }

    private static String cle(KernelSubject sujet, KernelCapability capacite) {
        return sujet.id() + "|" + capacite;
    }
}
