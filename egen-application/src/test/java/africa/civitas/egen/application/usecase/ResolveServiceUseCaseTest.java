package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.ResolvedInstance;
import africa.civitas.egen.application.port.ResolvedInstances;
import africa.civitas.egen.application.port.ServiceInstanceRegistration;
import africa.civitas.egen.application.resilience.ResiliencyPolicyProvider;
import africa.civitas.egen.domain.model.ResiliencyPolicy;
import africa.civitas.egen.domain.model.ServiceId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolveServiceUseCaseTest {

    private static final ServiceId NEWS = ServiceId.of("news-service");

    private static final DiscoveryPort DISCOVERY = new DiscoveryPort() {
        @Override
        public void register(ServiceInstanceRegistration registration) {
        }

        @Override
        public void deregister(ServiceId id, String instanceId) {
        }

        @Override
        public ResolvedInstances resolve(ServiceId id) {
            return id.equals(NEWS)
                    ? new ResolvedInstances(List.of(new ResolvedInstance("news-1", "10.0.0.7", 8080)))
                    : ResolvedInstances.empty();
        }
    };

    @Test
    void returnsTheHealthyInstancesTogetherWithTheTargetsPolicy() {
        ResiliencyPolicy policy = new ResiliencyPolicy(Duration.ofSeconds(10), null, null);
        ResolveServiceUseCase useCase = new ResolveServiceUseCaseImpl(DISCOVERY, target -> policy);

        ServiceResolution resolution = useCase.resolve(NEWS);

        assertEquals(NEWS, resolution.serviceId());
        assertEquals(1, resolution.instances().instances().size());
        assertEquals("10.0.0.7", resolution.instances().instances().get(0).address());
        assertEquals(policy, resolution.resiliency());
    }

    @Test
    void aServiceWithoutInstancesStillCarriesItsPolicy() {
        ResolveServiceUseCase useCase = new ResolveServiceUseCaseImpl(DISCOVERY,
                ResiliencyPolicyProvider.defaultsOnly());

        ServiceResolution resolution = useCase.resolve(ServiceId.of("ghost-service"));

        assertTrue(resolution.instances().instances().isEmpty());
        assertEquals(ResiliencyPolicy.defaults(), resolution.resiliency());
    }
}
