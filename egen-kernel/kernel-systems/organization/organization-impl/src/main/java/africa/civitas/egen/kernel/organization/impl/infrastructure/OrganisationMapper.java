package africa.civitas.egen.kernel.organization.impl.infrastructure;

import africa.civitas.egen.kernel.organization.api.domain.Organisation;
import africa.civitas.egen.kernel.organization.impl.domain.OrganisationEntity;

public final class OrganisationMapper {

    private OrganisationMapper() {
    }

    public static Organisation toDomain(OrganisationEntity entity) {
        return new Organisation(
                entity.id, entity.raisonSociale, entity.sigle, entity.typeOrganisation,
                entity.secteurActivitePrincipal, entity.codePaysRattachementJuridique,
                entity.identifiantJuridique, entity.codeDeviseReference, entity.codesLanguesOfficielles,
                entity.identifiantFuseauHoraireReference, entity.modeleSectorielOrigineId,
                entity.statut, entity.dateAdhesion, entity.urlIdentiteVisuelle,
                entity.tracabilite.toDomain());
    }
}
