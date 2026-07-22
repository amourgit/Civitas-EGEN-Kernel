package africa.civitas.egen.modules.business.referencedata.api.service;

import africa.civitas.egen.modules.business.referencedata.api.command.EnregistrerPaysCommand;
import africa.civitas.egen.modules.business.referencedata.api.domain.Pays;

import java.util.List;
import java.util.Optional;

public interface PaysService {

    Pays enregistrer(EnregistrerPaysCommand commande);

    Optional<Pays> trouverParCodeAlpha2(String codeAlpha2);

    List<Pays> lister();
}
