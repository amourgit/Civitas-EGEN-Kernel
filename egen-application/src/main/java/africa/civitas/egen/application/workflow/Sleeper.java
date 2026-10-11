package africa.civitas.egen.application.workflow;

import java.time.Duration;

/** Attente entre deux tentatives — injectable pour que les tests ne dorment jamais. */
@FunctionalInterface
public interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;

    static Sleeper threadSleep() {
        return duration -> Thread.sleep(duration.toMillis());
    }
}
