package africa.civitas.egen.kernel.authorization.application;

import africa.civitas.egen.kernel.authorization.command.AccorderCapaciteCommand;
import africa.civitas.egen.kernel.authorization.command.RevoquerCapaciteCommand;
import africa.civitas.egen.kernel.authorization.domain.KernelCapabiliteOctroi;
import africa.civitas.egen.kernel.authorization.exception.CapaciteAdministrationRefuseeException;
import africa.civitas.egen.kernel.authorization.exception.OctroiDejaActifException;
import africa.civitas.egen.kernel.authorization.exception.OctroiIntrouvableException;
import africa.civitas.egen.kernel.sdk.permission.authorization.KernelCapability;
import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class KernelCapabiliteOctroiServiceImplTest {

    @Inject
    KernelCapabiliteOctroiServiceImpl service;

    @Test
    @TestTransaction
    void bootstrapCanGrantACapacityToAnOrdinarySubject() {
        UUID beneficiaire = UUID.randomUUID();

        KernelCapabiliteOctroi octroi = service.accorder(new AccorderCapaciteCommand(
                beneficiaire, KernelCapability.CHARGER_MODULE,
                KernelSubject.bootstrap(), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(beneficiaire, octroi.sujetId());
        assertTrue(octroi.actif());
    }

    @Test
    @TestTransaction
    void grantingTheSameCapacityTwiceToTheSameSubjectIsRejected() {
        UUID beneficiaire = UUID.randomUUID();
        service.accorder(new AccorderCapaciteCommand(
                beneficiaire, KernelCapability.CHARGER_MODULE,
                KernelSubject.bootstrap(), OrigineDonnee.SAISIE_MANUELLE));

        assertThrows(OctroiDejaActifException.class, () -> service.accorder(new AccorderCapaciteCommand(
                beneficiaire, KernelCapability.CHARGER_MODULE,
                KernelSubject.bootstrap(), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void anOrdinarySubjectWithoutTheAdministrationCapacityCannotGrantAnything() {
        KernelSubject sansDroit = KernelSubject.nouveau();

        assertThrows(CapaciteAdministrationRefuseeException.class, () -> service.accorder(
                new AccorderCapaciteCommand(UUID.randomUUID(), KernelCapability.CHARGER_MODULE,
                        sansDroit, OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void aSubjectGrantedTheAdministrationCapacityCanInTurnGrantOthers() {
        KernelSubject administrateur = KernelSubject.nouveau();
        service.accorder(new AccorderCapaciteCommand(
                administrateur.id(), KernelCapability.ADMINISTRER_CAPACITES_NOYAU,
                KernelSubject.bootstrap(), OrigineDonnee.SAISIE_MANUELLE));

        UUID beneficiaire = UUID.randomUUID();
        KernelCapabiliteOctroi octroi = service.accorder(new AccorderCapaciteCommand(
                beneficiaire, KernelCapability.DECHARGER_MODULE, administrateur, OrigineDonnee.SAISIE_MANUELLE));

        assertTrue(octroi.actif());
    }

    @Test
    @TestTransaction
    void revokingAnActiveGrantMarksItInactiveButNeverDeletesIt() {
        UUID beneficiaire = UUID.randomUUID();
        KernelCapabiliteOctroi octroi = service.accorder(new AccorderCapaciteCommand(
                beneficiaire, KernelCapability.ENREGISTRER_EXTENSION,
                KernelSubject.bootstrap(), OrigineDonnee.SAISIE_MANUELLE));

        service.revoquer(new RevoquerCapaciteCommand(
                octroi.id(), KernelSubject.bootstrap(), "fin de mission"));

        List<KernelCapabiliteOctroi> historique = service.listerPourSujet(beneficiaire);
        assertEquals(1, historique.size());
        assertFalse(historique.get(0).actif());
    }

    @Test
    @TestTransaction
    void revokingAnUnknownGrantIsRejected() {
        assertThrows(OctroiIntrouvableException.class, () -> service.revoquer(
                new RevoquerCapaciteCommand(UUID.randomUUID(), KernelSubject.bootstrap(), "motif")));
    }

    @Test
    @TestTransaction
    void revokingAnAlreadyRevokedGrantIsRejected() {
        UUID beneficiaire = UUID.randomUUID();
        KernelCapabiliteOctroi octroi = service.accorder(new AccorderCapaciteCommand(
                beneficiaire, KernelCapability.CHARGER_MODULE,
                KernelSubject.bootstrap(), OrigineDonnee.SAISIE_MANUELLE));
        service.revoquer(new RevoquerCapaciteCommand(octroi.id(), KernelSubject.bootstrap(), "premiere revocation"));

        assertThrows(OctroiIntrouvableException.class, () -> service.revoquer(
                new RevoquerCapaciteCommand(octroi.id(), KernelSubject.bootstrap(), "seconde tentative")));
    }

    @Test
    @TestTransaction
    void aSubjectCanBeGrantedTheSameCapacityAgainAfterAPriorRevocation() {
        UUID beneficiaire = UUID.randomUUID();
        KernelCapabiliteOctroi premier = service.accorder(new AccorderCapaciteCommand(
                beneficiaire, KernelCapability.CHARGER_MODULE,
                KernelSubject.bootstrap(), OrigineDonnee.SAISIE_MANUELLE));
        service.revoquer(new RevoquerCapaciteCommand(premier.id(), KernelSubject.bootstrap(), "revoque"));

        KernelCapabiliteOctroi second = service.accorder(new AccorderCapaciteCommand(
                beneficiaire, KernelCapability.CHARGER_MODULE,
                KernelSubject.bootstrap(), OrigineDonnee.SAISIE_MANUELLE));

        assertTrue(second.actif());
        assertEquals(2, service.listerPourSujet(beneficiaire).size());
    }
}
