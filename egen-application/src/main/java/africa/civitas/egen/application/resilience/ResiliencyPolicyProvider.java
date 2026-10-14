package africa.civitas.egen.application.resilience;

import africa.civitas.egen.domain.model.ResiliencyPolicy;
import africa.civitas.egen.domain.model.ServiceId;

/**
 * Politique de resilience d'un service CIBLE (voir docs/architecture/06,
 * "Resilience declaree par la cible"). Ne renvoie jamais {@code null} : un
 * service sans bloc declare, ou inconnu, recoit
 * {@link ResiliencyPolicy#defaults()}.
 */
@FunctionalInterface
public interface ResiliencyPolicyProvider {

    ResiliencyPolicy policyFor(ServiceId target);

    static ResiliencyPolicyProvider defaultsOnly() {
        return target -> ResiliencyPolicy.defaults();
    }
}
