package africa.civitas.egen.modules.business.organization.api.service;

import africa.civitas.egen.modules.business.organization.api.command.CreerTutelleCommand;
import africa.civitas.egen.modules.business.organization.api.domain.Tutelle;

import java.util.List;
import java.util.UUID;

public interface TutelleService {

    /**
     * Cree une Tutelle. Si {@code commande.tutellePrincipale()} est vrai, echoue si
     * une Tutelle principale existe deja pour ce {@code celluleRacineId}.
     */
    Tutelle creer(CreerTutelleCommand commande);

    List<Tutelle> listerParCellule(UUID celluleRacineId);
}
