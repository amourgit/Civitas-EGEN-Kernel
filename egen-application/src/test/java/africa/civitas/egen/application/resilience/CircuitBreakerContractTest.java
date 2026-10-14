package africa.civitas.egen.application.resilience;

import africa.civitas.egen.domain.model.ResiliencyPolicy;
import africa.civitas.egen.domain.model.ResiliencyPolicy.CircuitBreakerSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rejoue la partie "circuit breaker" (scope = breaker) de
 * {@code sdk/contract/resiliency-contract.json} — le meme fichier que
 * rejouent le SDK Java et le SDK Python (docs/architecture/12, "Semantique
 * normative de la resilience"). Si ce test et un SDK divergent, l'un des
 * deux a rompu le contrat.
 */
class CircuitBreakerContractTest {

    private static final String CONTRACT = "sdk/contract/resiliency-contract.json";

    static JsonNode contract() throws IOException {
        String override = System.getProperty("egen.contract.path");
        Path path = override != null ? Path.of(override) : locate();
        return new ObjectMapper().readTree(Files.readString(path));
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
        throw new IllegalStateException("Contrat introuvable : " + CONTRACT
                + " (remonter depuis " + Path.of("").toAbsolutePath() + ", ou fixer -Degen.contract.path)");
    }

    private static final class FakeClock extends Clock {
        Instant now = Instant.parse("2026-10-09T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestFactory
    Stream<DynamicTest> everyBreakerScenarioOfTheSharedContractBehavesAsSpecified() throws IOException {
        JsonNode contract = contract();
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode scenario : contract.get("scenarios")) {
            if (!"breaker".equals(scenario.get("scope").asText())) {
                continue;
            }
            tests.add(DynamicTest.dynamicTest(scenario.get("name").asText(), () -> replay(scenario)));
        }
        assertTrue(tests.size() >= 10, "le contrat doit porter au moins 10 scenarios de circuit breaker");
        return tests.stream();
    }

    private static void replay(JsonNode scenario) {
        JsonNode policy = scenario.get("policy");
        assertEquals(1, policy.get("retry").get("maxAttempts").asInt(),
                "un scenario scope=breaker n'utilise jamais le retry");
        FakeClock clock = new FakeClock();
        CircuitBreaker breaker = new CircuitBreaker(new CircuitBreakerSpec(
                policy.get("circuitBreaker").get("failureThreshold").asInt(),
                Duration.ofMillis(policy.get("circuitBreaker").get("openDurationMillis").asLong())), clock);

        int index = 0;
        for (JsonNode step : scenario.get("steps")) {
            index++;
            if (step.has("advanceMillis")) {
                clock.now = clock.now.plusMillis(step.get("advanceMillis").asLong());
                continue;
            }
            String outcome = step.get("call").get(0).asText();
            JsonNode expect = step.get("expect");
            String context = scenario.get("name").asText() + " / etape " + index;

            String result;
            int attempts;
            if (!breaker.tryAcquire()) {
                result = "rejected:circuit_open";
                attempts = 0;
            } else {
                attempts = 1;
                if (outcome.chars().allMatch(Character::isDigit)) {
                    int status = Integer.parseInt(outcome);
                    if (CallClassification.isRetryableFailure(status)) {
                        breaker.recordFailure();
                    } else {
                        breaker.recordSuccess();
                    }
                    result = "status:" + status;
                } else {
                    breaker.recordFailure(); // timeout, network
                    result = "error:" + outcome;
                }
            }
            assertEquals(expect.get("result").asText(), result, context);
            assertEquals(expect.get("attemptsMade").asInt(), attempts, context);
            assertEquals(expect.get("breaker").asText(), breaker.state().name(), context);
        }
    }

    @org.junit.jupiter.api.Test
    void theContractDefaultsMatchTheDomainDefaults() throws IOException {
        JsonNode defaults = contract().get("defaults");
        ResiliencyPolicy domain = ResiliencyPolicy.defaults();

        assertEquals(defaults.get("timeoutMillis").asLong(), domain.timeout().toMillis());
        assertEquals(defaults.get("retry").get("maxAttempts").asInt(), domain.retry().maxAttempts());
        assertEquals(defaults.get("retry").get("backoff").asText().toUpperCase(), domain.retry().backoff().name());
        assertEquals(defaults.get("retry").get("baseDelayMillis").asLong(), domain.retry().baseDelay().toMillis());
        assertEquals(defaults.get("circuitBreaker").get("failureThreshold").asInt(),
                domain.circuitBreaker().failureThreshold());
        assertEquals(defaults.get("circuitBreaker").get("openDurationMillis").asLong(),
                domain.circuitBreaker().openDuration().toMillis());
    }
}
