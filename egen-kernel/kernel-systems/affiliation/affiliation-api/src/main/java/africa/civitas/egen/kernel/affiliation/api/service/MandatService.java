package africa.civitas.egen.kernel.affiliation.api.service;

import africa.civitas.egen.kernel.affiliation.api.command.CreerMandatCommand;
import africa.civitas.egen.kernel.affiliation.api.domain.Mandat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MandatService {

    Mandat creer(CreerMandatCommand commande);

    Optional<Mandat> trouverParId(UUID id);

    List<Mandat> listerParLexique(UUID lexiqueMandatsId);
}
