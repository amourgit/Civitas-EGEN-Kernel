package africa.civitas.egen.kernel.identity.impl.infrastructure;

import africa.civitas.egen.kernel.identity.api.domain.Personne;
import africa.civitas.egen.kernel.identity.impl.domain.PersonneEntity;

public final class PersonneMapper {

    private PersonneMapper() {
    }

    public static Personne toDomain(PersonneEntity entity) {
        return new Personne(
                entity.id,
                entity.identifiantCivilReference,
                entity.nomNaissance,
                entity.nomUsage,
                entity.prenoms,
                entity.dateNaissance,
                entity.lieuNaissance,
                entity.genreDeclare,
                entity.codesPaysNationalite,
                entity.codesLanguePreferee,
                entity.statutVital,
                entity.telephonePrincipal,
                entity.emailSecours,
                entity.photoReferenceUrl,
                entity.statutVerificationIdentite,
                entity.tracabilite.toDomain());
    }
}
