package africa.civitas.egen.sdk;

/** Attente entre deux tentatives — injectable pour que les tests ne dorment jamais. */
@FunctionalInterface
public interface Sleeper {
    void sleep(long millis) throws InterruptedException;

    static Sleeper threadSleep() {
        return Thread::sleep;
    }
}
