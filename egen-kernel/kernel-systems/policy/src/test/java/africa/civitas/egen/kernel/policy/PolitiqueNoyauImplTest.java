package africa.civitas.egen.kernel.policy;

import africa.civitas.egen.kernel.sdk.permission.authorization.DecisionNoyau;
import africa.civitas.egen.kernel.sdk.permission.policy.PolitiqueNoyauQuestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolitiqueNoyauImplTest {

    private final PolitiqueNoyauImpl politique = new PolitiqueNoyauImpl();

    @ParameterizedTest
    @EnumSource(PolitiqueNoyauQuestion.class)
    void alwaysRefusesRegardlessOfWhichQuestionIsAsked(PolitiqueNoyauQuestion question) {
        DecisionNoyau decision = politique.resoudre(question);

        assertFalse(decision.autorise());
    }

    @ParameterizedTest
    @EnumSource(PolitiqueNoyauQuestion.class)
    void alwaysNamesTheQuestionInTheMotif(PolitiqueNoyauQuestion question) {
        DecisionNoyau decision = politique.resoudre(question);

        assertTrue(decision.motif().length() > 10);
    }

    @Test
    void rejectsANullQuestion() {
        assertThrows(IllegalArgumentException.class, () -> politique.resoudre(null));
    }
}
