package africa.civitas.egen.adapter.nomad;

import africa.civitas.egen.application.port.AllocationInfo;
import africa.civitas.egen.domain.model.ServiceId;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adresse et port d'une allocation (docs/architecture/07, Deployment Port ; 20, etape 5) contre des reponses JSON
 * a la FORME REELLE de l'API HTTP de Nomad. Le test de niveau 3 ne pouvait pas les couvrir (Nomad serveur seul,
 * aucune allocation) : le Kernel lisait l'ancienne forme {@code Resources.Networks} dans un STUB de liste qui
 * ne porte aucune ressource, n'enregistrait donc jamais aucune instance dans Consul, et aucun service
 * n'atteignait RUNNING — revele par le test de niveau 5.
 */
class NomadAllocationNetworkTest {

    private static final ServiceId SERVICE = ServiceId.of("news-service");

    private HttpServer nomad;
    private final Map<String, String> responses = new HashMap<>();
    private final Map<String, Integer> statuses = new HashMap<>();
    private final List<String> requested = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void startNomad() throws IOException {
        nomad = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        nomad.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requested.add(path);
            byte[] body = responses.getOrDefault(path, "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statuses.getOrDefault(path, 200), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        nomad.start();
    }

    @AfterEach
    void stopNomad() {
        nomad.stop(0);
    }

    private NomadDeploymentAdapter adapter() {
        return new NomadDeploymentAdapter(URI.create("http://127.0.0.1:" + nomad.getAddress().getPort()), null);
    }

    /** Ce que renvoie GET /v1/job/{id}/allocations : des STUBS, sans aucune ressource. */
    private void stubs(String... allocations) {
        responses.put("/v1/job/news-service/allocations", "[" + String.join(",", allocations) + "]");
    }

    private static String stub(String id, String clientStatus) {
        return "{\"ID\":\"" + id + "\",\"JobID\":\"news-service\",\"ClientStatus\":\"" + clientStatus
                + "\",\"DesiredStatus\":\"run\",\"CreateTime\":1760000000000000000,\"TaskStates\":{}}";
    }

    private static final String SHARED_NETWORKS = "\"Networks\":[{\"Mode\":\"host\",\"Device\":\"eth0\",\"CIDR\":\"\","
            + "\"IP\":\"10.1.0.87\",\"MBits\":0,\"DNS\":null,\"ReservedPorts\":null,"
            + "\"DynamicPorts\":[{\"Label\":\"http\",\"Value\":28123,\"To\":0,\"HostNetwork\":\"default\"}]}]";

    /** Allocation detaillee de Nomad >= 1.0 pour un job a network de GROUPE. */
    private static String modernAllocation(String id) {
        return "{\"ID\":\"" + id + "\",\"ClientStatus\":\"running\",\"Resources\":{\"CPU\":100,\"MemoryMB\":64,"
                + "\"Networks\":null},\"AllocatedResources\":{\"Tasks\":{\"news-service\":{\"Networks\":null}},"
                + "\"Shared\":{" + SHARED_NETWORKS + ",\"DiskMB\":300,"
                + "\"Ports\":[{\"Label\":\"http\",\"Value\":28123,\"To\":0,\"HostIP\":\"10.1.0.87\"}]}}}";
    }

    @Test
    void aRunningAllocationYieldsTheHostAddressAndTheDynamicPortOfTheGroupNetwork() {
        stubs(stub("alloc-1", "running"));
        responses.put("/v1/allocation/alloc-1", modernAllocation("alloc-1"));

        List<AllocationInfo> allocations = adapter().listAllocations(SERVICE);

        assertEquals(1, allocations.size());
        assertEquals("alloc-1", allocations.get(0).allocationId());
        assertEquals("running", allocations.get(0).status());
        assertEquals("10.1.0.87", allocations.get(0).address());
        assertEquals(28123, allocations.get(0).port());
        assertTrue(allocations.get(0).hasNetworkInfo(), "sans cela le Kernel n'enregistre jamais l'instance dans Consul");
    }

    @Test
    void theNetworksFormIsUsedWhenThePortsListIsAbsent() {
        stubs(stub("alloc-1", "running"));
        responses.put("/v1/allocation/alloc-1", "{\"ID\":\"alloc-1\",\"AllocatedResources\":{\"Shared\":{"
                + SHARED_NETWORKS + "}}}");

        AllocationInfo allocation = adapter().listAllocations(SERVICE).get(0);

        assertEquals("10.1.0.87", allocation.address());
        assertEquals(28123, allocation.port());
    }

    @Test
    void theLegacyTaskLevelFormIsStillReadAsALastResort() {
        stubs(stub("alloc-1", "running"));
        responses.put("/v1/allocation/alloc-1", "{\"ID\":\"alloc-1\",\"Resources\":{\"Networks\":[{\"IP\":\"192.168.5.4\","
                + "\"DynamicPorts\":[{\"Label\":\"http\",\"Value\":31000}]}]}}");

        AllocationInfo allocation = adapter().listAllocations(SERVICE).get(0);

        assertEquals("192.168.5.4", allocation.address());
        assertEquals(31000, allocation.port());
    }

    @Test
    void thePortLabelledHttpIsChosenAmongSeveral() {
        stubs(stub("alloc-1", "running"));
        responses.put("/v1/allocation/alloc-1", "{\"AllocatedResources\":{\"Shared\":{\"Ports\":["
                + "{\"Label\":\"metrics\",\"Value\":20001,\"To\":0,\"HostIP\":\"10.1.0.87\"},"
                + "{\"Label\":\"http\",\"Value\":20002,\"To\":0,\"HostIP\":\"10.1.0.87\"}]}}}");

        assertEquals(20002, adapter().listAllocations(SERVICE).get(0).port());
    }

    @Test
    void onlyRunningAllocationsAreReadInDetail() {
        stubs(stub("alloc-run", "running"), stub("alloc-old", "complete"), stub("alloc-bad", "failed"));
        responses.put("/v1/allocation/alloc-run", modernAllocation("alloc-run"));

        List<AllocationInfo> allocations = adapter().listAllocations(SERVICE);

        assertEquals(3, allocations.size());
        assertTrue(allocations.get(0).hasNetworkInfo());
        assertFalse(allocations.get(1).hasNetworkInfo());
        assertFalse(allocations.get(2).hasNetworkInfo());
        assertFalse(requested.contains("/v1/allocation/alloc-old"));
        assertFalse(requested.contains("/v1/allocation/alloc-bad"));
    }

    @Test
    void anAllocationCollectedMeanwhileHasNoEndpointAndIsNotAnError() {
        stubs(stub("alloc-1", "running"));
        statuses.put("/v1/allocation/alloc-1", 404);

        AllocationInfo allocation = adapter().listAllocations(SERVICE).get(0);

        assertFalse(allocation.hasNetworkInfo());
        assertEquals("running", allocation.status());
    }

    @Test
    void aRunningAllocationWithoutAnyNetworkInformationHasNoEndpoint() {
        stubs(stub("alloc-1", "running"));
        responses.put("/v1/allocation/alloc-1", "{\"ID\":\"alloc-1\",\"AllocatedResources\":{\"Shared\":{\"Networks\":[]}}}");

        assertFalse(adapter().listAllocations(SERVICE).get(0).hasNetworkInfo());
    }

    @Test
    void anUnknownJobHasNoAllocations() {
        statuses.put("/v1/job/news-service/allocations", 404);

        assertTrue(adapter().listAllocations(SERVICE).isEmpty());
    }

    @Test
    void aServerErrorOnTheDetailIsReportedNotSwallowed() {
        stubs(stub("alloc-1", "running"));
        statuses.put("/v1/allocation/alloc-1", 500);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> adapter().listAllocations(SERVICE));
    }
}
