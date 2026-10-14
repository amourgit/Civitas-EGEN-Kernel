package africa.civitas.egen.sdk;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rejoue TOUS les scenarios de {@code sdk/contract/resiliency-contract.json} —
 * le meme fichier que rejouent le Kernel (scope=breaker) et le SDK Python
 * (docs/architecture/12, "Semantique normative de la resilience").
 */
class ResiliencyContractTest {

    private static final String CONTRACT = "sdk/contract/resiliency-contract.json";

    @SuppressWarnings("unchecked")
    static Map<String, Object> contract() throws IOException {
        String override = System.getProperty("egen.contract.path");
        Path path = override != null ? Path.of(override) : locate();
        return Json.parseObject(Files.readString(path));
    }

    private static Path locate() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(CONTRACT);
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Contrat introuvable : " + CONTRACT + " (ou fixer -Degen.contract.path)");
    }

    @SuppressWarnings("unchecked")
    @TestFactory
    Stream<DynamicTest> everyScenarioOfTheSharedContractBehavesAsSpecified() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (Object item : (List<Object>) contract().get("scenarios")) {
            Map<String, Object> scenario = (Map<String, Object>) item;
            tests.add(DynamicTest.dynamicTest(scenario.get("name") + " [" + scenario.get("scope") + "]",
                    () -> replay(scenario)));
        }
        assertTrue(tests.size() >= 20, "le contrat doit porter au moins 20 scenarios");
        return tests.stream();
    }

    @SuppressWarnings("unchecked")
    private static void replay(Map<String, Object> scenario) throws InterruptedException {
        ResiliencyPolicy policy = ResiliencyPolicy.fromJson(scenario.get("policy"));
        AtomicLong clock = new AtomicLong(1_000_000);
        List<Long> sleeps = new ArrayList<>();
        CircuitBreaker breaker = new CircuitBreaker(policy.circuitBreaker(), clock::get);
        // Le sleeper n'avance PAS l'horloge : seul "advanceMillis" le fait (determinisme inter-langages).
        ResilienceExecutor executor = new ResilienceExecutor(policy.retry(), breaker, sleeps::add);

        int index = 0;
        for (Object item : (List<Object>) scenario.get("steps")) {
            index++;
            Map<String, Object> step = (Map<String, Object>) item;
            if (step.containsKey("advanceMillis")) {
                clock.addAndGet(((Number) step.get("advanceMillis")).longValue());
                continue;
            }
            List<Object> script = (List<Object>) step.get("call");
            Map<String, Object> expect = (Map<String, Object>) step.get("expect");
            String context = scenario.get("name") + " / etape " + index;
            sleeps.clear();

            ResilienceExecutor.Result result = executor.run(attempt -> {
                assertTrue(attempt < script.size(), context + " : tentative " + attempt + " hors script");
                return outcome((String) script.get(attempt));
            });

            assertEquals(expect.get("result"), result.render(), context);
            assertEquals(((Number) expect.get("attemptsMade")).intValue(), result.attemptsMade(), context);
            List<Long> expectedSleeps = new ArrayList<>();
            for (Object s : (List<Object>) expect.get("sleepsMillis")) {
                expectedSleeps.add(((Number) s).longValue());
            }
            assertEquals(expectedSleeps, sleeps, context);
            assertEquals(expect.get("breaker"), breaker.state().name(), context);
        }
    }

    private static Outcome outcome(String code) {
        return code.chars().allMatch(Character::isDigit) ? Outcome.ofStatus(Integer.parseInt(code)) : Outcome.ofError(code);
    }

    @SuppressWarnings("unchecked")
    @Test
    void theContractDefaultsMatchTheSdkDefaults() throws IOException {
        Map<String, Object> defaults = (Map<String, Object>) contract().get("defaults");
        ResiliencyPolicy fromContract = ResiliencyPolicy.fromJson(defaults);

        assertEquals(ResiliencyPolicy.defaults(), fromContract);
        assertEquals(ResiliencyPolicy.MAX_BACKOFF_MILLIS, ((Number) contract().get("maxBackoffMillis")).longValue());
    }
}
