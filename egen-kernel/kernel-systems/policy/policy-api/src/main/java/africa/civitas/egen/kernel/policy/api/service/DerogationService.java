package africa.civitas.egen.kernel.policy.api.service;

import africa.civitas.egen.kernel.policy.api.command.CreerDerogationCommand;
import africa.civitas.egen.kernel.policy.api.domain.Derogation;

import java.util.List;
import java.util.UUID;

public interface DerogationService {

    Derogation creer(CreerDerogationCommand commande);

    List<Derogation> listerParPolitique(UUID politiqueId);
}
