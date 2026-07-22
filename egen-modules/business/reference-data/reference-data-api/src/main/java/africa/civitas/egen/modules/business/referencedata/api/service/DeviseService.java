package africa.civitas.egen.modules.business.referencedata.api.service;

import africa.civitas.egen.modules.business.referencedata.api.command.EnregistrerDeviseCommand;
import africa.civitas.egen.modules.business.referencedata.api.domain.Devise;

import java.util.List;
import java.util.Optional;

public interface DeviseService {

    Devise enregistrer(EnregistrerDeviseCommand commande);

    Optional<Devise> trouverParCode(String codeIso4217);

    List<Devise> lister();
}
