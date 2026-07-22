package africa.civitas.egen.modules.business.organization.api.domain;

/**
 * Nature d'une Tutelle. Fixee au niveau plateforme (decision d'architecture) —
 * contrairement au Lexique Organisationnel, cette nomenclature n'est pas souveraine
 * par Organisation, car ses valeurs varient peu d'un secteur a l'autre.
 */
public enum NatureTutelle {
    ADMINISTRATIVE,
    ACADEMIQUE,
    FINANCIERE,
    SANITAIRE,
    AUTRE
}
