package africa.civitas.egen.kernel.sdk.contexte;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ContexteTest {

    /**
     * Implementation minimale utilisee uniquement pour verifier que le contrat
     * Contexte est utilisable tel quel par un consommateur qui n'a jamais vu
     * organization-impl — exactement la situation d'un module metier externe.
     */
    private record TestContexte(UUID id, ContexteNature nature) implements Contexte {
    }

    @Test
    void anOrganisationContexteExposesItsNature() {
        UUID id = UUID.randomUUID();
        Contexte contexte = new TestContexte(id, ContexteNature.ORGANISATION);

        assertEquals(id, contexte.id());
        assertEquals(ContexteNature.ORGANISATION, contexte.nature());
    }

    @Test
    void aCelluleContexteIsDistinguishableFromAnOrganisationContexte() {
        Contexte organisation = new TestContexte(UUID.randomUUID(), ContexteNature.ORGANISATION);
        Contexte cellule = new TestContexte(UUID.randomUUID(), ContexteNature.CELLULE);

        assertNotEquals(organisation.nature(), cellule.nature());
    }

    @Test
    void contexteNatureStaysDeliberatelyLimitedToTheTwoKnownSupertypes() {
        // Ce test echoue volontairement si quelqu'un ajoute une valeur a l'enum sans
        // passer par la decision explicite documentee dans ContexteNature — un
        // troisieme type de Contexte (ex. un Projet transverse) doit etre tranche au
        // niveau du Systeme Organisationnel (A2), jamais ajoute au fil de l'eau.
        assertEquals(2, ContexteNature.values().length,
                "Un nouveau type de Contexte doit etre une decision explicite d'architecture, "
                        + "pas un ajout silencieux — voir la javadoc de ContexteNature.");
    }
}
