package africa.civitas.egen.modules.business.organization.api.politique.service;

import africa.civitas.egen.modules.business.organization.api.politique.command.CreerDerogationCommand;
import africa.civitas.egen.modules.business.organization.api.politique.domain.Derogation;

import java.util.List;
import java.util.UUID;

public interface DerogationService {

    Derogation creer(CreerDerogationCommand commande);

    List<Derogation> listerParPolitique(UUID politiqueId);
}
