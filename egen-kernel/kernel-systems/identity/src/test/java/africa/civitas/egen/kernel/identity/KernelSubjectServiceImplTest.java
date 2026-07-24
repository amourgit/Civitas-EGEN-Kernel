package africa.civitas.egen.kernel.identity;

import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.ActeurType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelSubjectServiceImplTest {

    private final KernelSubjectServiceImpl service = new KernelSubjectServiceImpl();

    @Test
    void recognizesTheBootstrapId() {
        assertTrue(service.estBootstrap(KernelSubject.BOOTSTRAP_ID));
    }

    @Test
    void rejectsAnOrdinaryIdAsBootstrap() {
        assertFalse(service.estBootstrap(UUID.randomUUID()));
    }

    @Test
    void estBootstrapRejectsANullId() {
        assertThrows(IllegalArgumentException.class, () -> service.estBootstrap(null));
    }

    @Test
    void resoudreBuildsTheCanonicalBootstrapSubject() {
        KernelSubject sujet = service.resoudre(KernelSubject.BOOTSTRAP_ID);

        assertEquals(KernelSubject.bootstrap(), sujet);
    }

    @Test
    void resoudreBuildsAnOrdinarySubjectForAnyOtherId() {
        UUID id = UUID.randomUUID();
        KernelSubject sujet = service.resoudre(id);

        assertEquals(id, sujet.id());
        assertFalse(sujet.bootstrap());
    }

    @Test
    void versActeurAlwaysProducesASystemActorNeverAPersonActor() {
        Acteur acteurBootstrap = service.versActeur(KernelSubject.bootstrap());
        Acteur acteurOrdinaire = service.versActeur(KernelSubject.nouveau());

        assertEquals(ActeurType.SYSTEME, acteurBootstrap.type());
        assertEquals(ActeurType.SYSTEME, acteurOrdinaire.type());
    }

    @Test
    void versActeurUsesAFixedLabelForTheBootstrapSubject() {
        Acteur acteur = service.versActeur(KernelSubject.bootstrap());

        assertEquals("kernel-bootstrap", acteur.systemeLabel());
    }

    @Test
    void versActeurEmbedsTheIdForAnOrdinarySubject() {
        KernelSubject sujet = KernelSubject.nouveau();
        Acteur acteur = service.versActeur(sujet);

        assertEquals("kernel-sujet:" + sujet.id(), acteur.systemeLabel());
    }

    @Test
    void versActeurRejectsANullSubject() {
        assertThrows(IllegalArgumentException.class, () -> service.versActeur(null));
    }
}

