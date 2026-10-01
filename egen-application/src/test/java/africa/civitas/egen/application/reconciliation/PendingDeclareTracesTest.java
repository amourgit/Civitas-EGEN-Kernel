package africa.civitas.egen.application.reconciliation;

import africa.civitas.egen.application.port.TraceContext;
import africa.civitas.egen.domain.model.ServiceId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Voir docs/architecture/15-observabilite.md, "chaque appel entrant sur
 * l'API de controle propage son traceparent jusqu'au premier appel reseau" —
 * cette classe est le mecanisme qui rend cela possible malgre le decouplage
 * asynchrone entre l'API et la reconciliation (voir sa javadoc).
 */
class PendingDeclareTracesTest {

    private static final ServiceId ID = ServiceId.of("news-service");
    private static final TraceContext CONTEXT =
            new TraceContext("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

    @Test
    void aFreshInstanceHasNothingPending() {
        PendingDeclareTraces traces = new PendingDeclareTraces();
        assertTrue(traces.peek(ID, 1L).isEmpty());
    }

    @Test
    void recordingNullNeverKeepsAnEntry() {
        PendingDeclareTraces traces = new PendingDeclareTraces();
        traces.record(ID, 1L, null);
        assertTrue(traces.peek(ID, 1L).isEmpty());
    }

    @Test
    void peekReturnsTheContextOnlyForTheExactGenerationRecorded() {
        PendingDeclareTraces traces = new PendingDeclareTraces();
        traces.record(ID, 1L, CONTEXT);

        assertEquals(CONTEXT, traces.peek(ID, 1L).orElseThrow());
        assertTrue(traces.peek(ID, 2L).isEmpty(), "une generation differente ne doit jamais matcher");
        assertTrue(traces.peek(ServiceId.of("other-service"), 1L).isEmpty());
    }

    @Test
    void aNewerDeclareSupersedesAnOlderPendingGenerationWithoutAnyExplicitCleanup() {
        PendingDeclareTraces traces = new PendingDeclareTraces();
        traces.record(ID, 1L, CONTEXT);

        TraceContext secondContext =
                new TraceContext("00-5cf92f3577b34da6a3ce929d0e0e4737-00f067aa0ba902b8-01");
        traces.record(ID, 2L, secondContext);

        // La generation 1, perimee, ne peut plus jamais etre retrouvee —
        // aucune fuite entre deux Declare successifs du meme service.
        assertTrue(traces.peek(ID, 1L).isEmpty());
        assertEquals(secondContext, traces.peek(ID, 2L).orElseThrow());
    }

    @Test
    void clearRemovesTheEntryRegardlessOfWhichGenerationIsAskedAfterwards() {
        PendingDeclareTraces traces = new PendingDeclareTraces();
        traces.record(ID, 1L, CONTEXT);

        traces.clear(ID);

        assertTrue(traces.peek(ID, 1L).isEmpty());
    }

    @Test
    void clearingAnUnknownServiceIsANoOp() {
        PendingDeclareTraces traces = new PendingDeclareTraces();
        traces.clear(ID); // ne doit lever aucune exception
        assertTrue(traces.peek(ID, 1L).isEmpty());
    }
}
