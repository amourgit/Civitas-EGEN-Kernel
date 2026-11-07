package africa.civitas.egen.adapter.kubernetes;

import africa.civitas.egen.application.port.AllocationInfo;
import africa.civitas.egen.application.port.DeploymentException;
import africa.civitas.egen.domain.model.DeploymentObservation;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de niveau 3 (docs/architecture/17-strategie-de-tests.md) : {@link KubernetesDeploymentAdapter}
 * contre un VRAI cluster Kubernetes (k3s dans Testcontainers), avec de vrais pods qui demarrent.
 *
 * <p><b>Qui fait quoi, dans l'ordre.</b> (1) k3s demarre. (2) Le test cree un namespace {@code egen}, un
 * compte de service et un {@code Role} qui porte EXACTEMENT les droits listes dans
 * docs/architecture/07-ports-et-adapters.md (« Droits minimaux ») : si ces droits ne suffisaient pas, un
 * test ci-dessous echouerait avec « Forbidden ». (3) L'adapter s'authentifie par jeton porteur de ce compte
 * de service, et verifie le certificat de l'API avec l'autorite de certification du cluster (la
 * verification TLS n'est jamais desactivee). (4) Les tests, ordonnes, suivent la vie d'un service :
 * creation, convergence, idempotence, scale, mise a jour, retour arriere, redemarrage, arret, suppression.
 * Pour verifier CE QUE Kubernetes a reellement enregistre, le test interroge aussi le cluster directement
 * avec {@code kubectl} (executee dans le conteneur k3s).</p>
 *
 * <p><b>Prerequis d'environnement.</b> Docker doit autoriser un conteneur privilegie (k3s l'exige), et le
 * cluster doit pouvoir telecharger les images {@code nginx:1.26.0} et {@code nginx:1.27.0} depuis Docker
 * Hub. Sans acces reseau, les tests de convergence echouent avec un delai depasse explicite.</p>
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KubernetesDeploymentAdapterIT {

    private static final String NAMESPACE = "egen";
    private static final int CONTAINER_PORT = 8080;
    private static final String IMAGE_OLD = "nginx:1.26.0";
    private static final String IMAGE_NEW = "nginx:1.27.0";
    private static final ServiceId SERVICE = ServiceId.of("it-news");
    private static final String KUBECONFIG = "--kubeconfig=/etc/rancher/k3s/k3s.yaml";

    private static final int KUBE_SECURE_PORT = 6443;

    // Version k3s verifiee : la release v1.31.2+k3s1 existe sur github.com/k3s-io/k3s (le « + » devient « - »
    // dans l'etiquette Docker).
    @Container
    static final GenericContainer<?> K3S = newK3sContainer();

    /**
     * Un conteneur k3s configure comme le fait {@code org.testcontainers.k3s.K3sContainer} (code source
     * verifie en 1.20.4 et 2.0.4, configuration identique). On ne depend pas du module k3s : voir la note
     * « PIEGE » du pom de ce module.
     */
    private static GenericContainer<?> newK3sContainer() {
        GenericContainer<?> k3s = new GenericContainer<>("rancher/k3s:v1.31.2-k3s1");
        k3s.withExposedPorts(KUBE_SECURE_PORT)
                .withPrivilegedMode(true) // k3s lance containerd : il lui faut un conteneur privilegie
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withCgroupnsMode("host"))
                .withFileSystemBind("/sys/fs/cgroup", "/sys/fs/cgroup", BindMode.READ_WRITE)
                .withTmpFs(Map.of("/run", "", "/var/run", ""))
                .withCommand("server", "--disable=traefik", "--tls-san=" + k3s.getHost())
                .waitingFor(Wait.forLogMessage(".*Node controller sync successful.*", 1))
                .withStartupTimeout(Duration.ofMinutes(3));
        return k3s;
    }

    private static final String RBAC = """
            apiVersion: v1
            kind: Namespace
            metadata:
              name: egen
            ---
            apiVersion: v1
            kind: ServiceAccount
            metadata:
              name: egen-kernel
              namespace: egen
            ---
            apiVersion: rbac.authorization.k8s.io/v1
            kind: Role
            metadata:
              name: egen-kernel
              namespace: egen
            rules:
              - apiGroups: ["apps"]
                resources: ["deployments"]
                verbs: ["get", "create", "patch", "delete"]
              - apiGroups: ["apps"]
                resources: ["deployments/scale"]
                verbs: ["get", "patch"]
              - apiGroups: ["apps"]
                resources: ["replicasets"]
                verbs: ["list"]
              - apiGroups: [""]
                resources: ["pods"]
                verbs: ["list"]
            ---
            apiVersion: rbac.authorization.k8s.io/v1
            kind: RoleBinding
            metadata:
              name: egen-kernel
              namespace: egen
            subjects:
              - kind: ServiceAccount
                name: egen-kernel
                namespace: egen
            roleRef:
              apiGroup: rbac.authorization.k8s.io
              kind: Role
              name: egen-kernel
            """;

    @TempDir
    static Path tempDir;

    private static KubernetesDeploymentAdapter adapter;
    private static String firstPodName;
    private static String podBeforeRestart;

    @BeforeAll
    static void connectWithLeastPrivilege() throws Exception {
        K3S.copyFileToContainer(Transferable.of(RBAC), "/tmp/egen-rbac.yaml");
        kubectl("apply", "-f", "/tmp/egen-rbac.yaml");
        String token = kubectl("-n", NAMESPACE, "create", "token", "egen-kernel", "--duration=2h");

        String caBase64 = kubectl("config", "view", "--raw", "--minify", "-o",
                "jsonpath={.clusters[0].cluster.certificate-authority-data}");
        Path caFile = tempDir.resolve("k3s-ca.pem");
        Files.write(caFile, Base64.getDecoder().decode(caBase64));

        URI address = URI.create("https://" + K3S.getHost() + ":" + K3S.getMappedPort(KUBE_SECURE_PORT));
        adapter = new KubernetesDeploymentAdapter(new KubernetesConfig(address, NAMESPACE, token, caFile, CONTAINER_PORT));
    }

    private static DeploymentSpec spec(String image) {
        return new DeploymentSpec("kubernetes", image, "100m", "64Mi", new ReplicaRange(1, 3));
    }

    // ------------------------------------------------------------------ scenario

    @Test
    @Order(1)
    void createStoresOneDeploymentOwnedByTheKernelWithOnlyTheDocumentedRights() throws Exception {
        adapter.create(SERVICE, spec(IMAGE_OLD));

        assertEquals("it-news", kubectl("-n", NAMESPACE, "get", "deployment", "it-news", "-o", "jsonpath={.metadata.name}"));
        assertEquals("egen-kernel", kubectl("-n", NAMESPACE, "get", "deployment", "it-news", "-o",
                "jsonpath={.metadata.labels.app\\.kubernetes\\.io/managed-by}"));
        assertTrue(kubectl("-n", NAMESPACE, "get", "deployment", "it-news", "-o",
                "jsonpath={.metadata.managedFields[*].manager}").contains("egen-kernel"),
                "le champ « fieldManager » du server-side apply doit etre egen-kernel");
        assertEquals(IMAGE_OLD, deploymentImage());

        // Constat K9 : ce que Kubernetes a REELLEMENT enregistre. Le test de convergence suivant prouve en plus que
        // l'image nginx demarre bel et bien avec ces restrictions.
        assertEquals("RuntimeDefault", kubectl("-n", NAMESPACE, "get", "deployment", "it-news", "-o",
                "jsonpath={.spec.template.spec.securityContext.seccompProfile.type}"));
        assertEquals("false", kubectl("-n", NAMESPACE, "get", "deployment", "it-news", "-o",
                "jsonpath={.spec.template.spec.automountServiceAccountToken}"));
        assertEquals("false", kubectl("-n", NAMESPACE, "get", "deployment", "it-news", "-o",
                "jsonpath={.spec.template.spec.containers[0].securityContext.allowPrivilegeEscalation}"));
    }

    @Test
    @Order(2)
    void theRealPodStartsAndTheObservationConverges() {
        eventually(Duration.ofMinutes(4), "1 pod voulu, en cours et pret", () -> {
            DeploymentObservation o = adapter.getStatus(SERVICE);
            return o.desiredCount() == 1 && o.runningCount() == 1 && o.healthyCount() == 1 && o.failedCount() == 0;
        });

        List<AllocationInfo> allocations = adapter.listAllocations(SERVICE);
        assertEquals(1, allocations.size());
        AllocationInfo pod = allocations.get(0);
        assertEquals("running", pod.status());
        assertFalse(pod.address().isBlank(), "un pod en cours doit avoir une IP");
        assertEquals(CONTAINER_PORT, pod.port());
        firstPodName = pod.allocationId();
    }

    @Test
    @Order(3)
    void createTwiceChangesNothing() {
        adapter.create(SERVICE, spec(IMAGE_OLD));

        eventually(Duration.ofSeconds(60), "toujours 1 pod pret", () -> adapter.getStatus(SERVICE).healthyCount() == 1);
        assertEquals(List.of(firstPodName),
                adapter.listAllocations(SERVICE).stream().map(AllocationInfo::allocationId).toList(),
                "un second create identique ne doit pas recreer le pod");
    }

    @Test
    @Order(4)
    void scaleChangesTheNumberOfPods() {
        adapter.scale(SERVICE, 2);

        eventually(Duration.ofMinutes(2), "2 pods voulus et prets", () -> {
            DeploymentObservation o = adapter.getStatus(SERVICE);
            return o.desiredCount() == 2 && o.healthyCount() == 2;
        });
        assertEquals(2, adapter.listAllocations(SERVICE).size());
    }

    @Test
    @Order(5)
    void updateRollsOutTheNewImageAndResetsReplicasToTheSpecMinimum() throws Exception {
        adapter.update(SERVICE, spec(IMAGE_NEW));

        assertEquals(IMAGE_NEW, deploymentImage());
        // Meme semantique que l'adapter Nomad : update ecrit replicas = min de la spec (07, « Mapping »).
        eventually(Duration.ofMinutes(4), "rolling update termine : 1 pod, pret, sur la nouvelle image", () ->
                adapter.getStatus(SERVICE).desiredCount() == 1 && adapter.listAllocations(SERVICE).size() == 1
                        && adapter.getStatus(SERVICE).healthyCount() == 1);
    }

    @Test
    @Order(6)
    void rollbackGoesBackToThePreviousVersionLikeKubectlRolloutUndo() throws Exception {
        adapter.rollback(SERVICE, ServiceVersion.parse("1.26.0"));

        assertEquals(IMAGE_OLD, deploymentImage());
        eventually(Duration.ofMinutes(4), "retour arriere termine : 1 pod pret", () ->
                adapter.listAllocations(SERVICE).size() == 1 && adapter.getStatus(SERVICE).healthyCount() == 1);
    }

    @Test
    @Order(7)
    void rollbackToAVersionThatNeverRanFailsAndChangesNothing() throws Exception {
        assertThrows(DeploymentException.class, () -> adapter.rollback(SERVICE, ServiceVersion.parse("9.9.9")));

        assertEquals(IMAGE_OLD, deploymentImage());
    }

    @Test
    @Order(8)
    void restartReplacesThePodThroughTheTemplateAnnotation() throws Exception {
        podBeforeRestart = adapter.listAllocations(SERVICE).get(0).allocationId();

        adapter.restart(SERVICE);

        // Verifie EMPIRIQUEMENT le fait « annotation restartedAt » marque « a verifier » dans le doc 07.
        assertFalse(kubectl("-n", NAMESPACE, "get", "deployment", "it-news", "-o",
                "jsonpath={.spec.template.metadata.annotations.kubectl\\.kubernetes\\.io/restartedAt}").isBlank());
        eventually(Duration.ofMinutes(3), "un NOUVEAU pod, pret", () -> {
            List<AllocationInfo> pods = adapter.listAllocations(SERVICE);
            return pods.size() == 1 && !pods.get(0).allocationId().equals(podBeforeRestart)
                    && adapter.getStatus(SERVICE).healthyCount() == 1;
        });
        assertNotEquals(podBeforeRestart, adapter.listAllocations(SERVICE).get(0).allocationId());
    }

    @Test
    @Order(9)
    void stopScalesToZeroButKeepsTheDeployment() throws Exception {
        adapter.stop(SERVICE);

        eventually(Duration.ofMinutes(2), "0 pod voulu et plus aucune allocation", () ->
                adapter.getStatus(SERVICE).desiredCount() == 0 && adapter.listAllocations(SERVICE).isEmpty());
        assertEquals("it-news", kubectl("-n", NAMESPACE, "get", "deployment", "it-news", "-o", "jsonpath={.metadata.name}"),
                "stop garde le Deployment (et son historique) : c'est remove() qui le supprime");
    }

    @Test
    @Order(10)
    void removeDeletesTheDeploymentAndEveryOperationAfterwardsIsIdempotent() {
        adapter.remove(SERVICE);

        eventually(Duration.ofMinutes(1), "Deployment disparu", () ->
                DeploymentObservation.empty().equals(adapter.getStatus(SERVICE)));
        adapter.remove(SERVICE); // deja absent : pas une erreur
        adapter.stop(SERVICE);   // idem
        assertTrue(adapter.listAllocations(SERVICE).isEmpty());
    }

    // ------------------------------------------------------------------ helpers

    private static String deploymentImage() throws Exception {
        return kubectl("-n", NAMESPACE, "get", "deployment", "it-news", "-o",
                "jsonpath={.spec.template.spec.containers[0].image}");
    }

    /** Execute kubectl DANS le conteneur k3s (kubeconfig administrateur) ; echoue si le code de sortie n'est pas 0. */
    private static String kubectl(String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 2];
        command[0] = "kubectl";
        command[1] = KUBECONFIG;
        System.arraycopy(args, 0, command, 2, args.length);
        ExecResult result = K3S.execInContainer(StandardCharsets.UTF_8, command);
        assertEquals(0, result.getExitCode(), "kubectl " + String.join(" ", args) + " : " + result.getStderr());
        return result.getStdout().trim();
    }

    /** Attend qu'une condition devienne vraie ; en cas d'echec, donne l'etat observe pour comprendre. */
    private static void eventually(Duration timeout, String expectation, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        Supplier<String> snapshot = () -> {
            try {
                return adapter.getStatus(SERVICE) + " / " + adapter.listAllocations(SERVICE);
            } catch (RuntimeException e) {
                return "etat illisible : " + e.getMessage();
            }
        };
        while (System.nanoTime() < deadline) {
            try {
                if (condition.getAsBoolean()) {
                    return;
                }
            } catch (RuntimeException ignored) {
                // l'API peut repondre en erreur transitoire pendant un rollout : on reessaie jusqu'au delai
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrompu en attendant : " + expectation, e);
            }
        }
        throw new AssertionError("Delai de " + timeout.toSeconds() + " s depasse en attendant : " + expectation
                + " — dernier etat : " + snapshot.get());
    }
}
