package africa.civitas.egen.modules.business.referencedata.api.service;

import africa.civitas.egen.modules.business.referencedata.api.command.EnregistrerLangueCommand;
import africa.civitas.egen.modules.business.referencedata.api.domain.Langue;

import java.util.List;
import java.util.Optional;

public interface LangueService {

    Langue enregistrer(EnregistrerLangueCommand commande);

    Optional<Langue> trouverParCode(String codeIso639);

    List<Langue> lister();
}
