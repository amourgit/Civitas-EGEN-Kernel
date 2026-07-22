package africa.civitas.egen.modules.business.referencedata.api.service;

import africa.civitas.egen.modules.business.referencedata.api.command.EnregistrerFuseauHoraireCommand;
import africa.civitas.egen.modules.business.referencedata.api.domain.FuseauHoraire;

import java.util.List;
import java.util.Optional;

public interface FuseauHoraireService {

    FuseauHoraire enregistrer(EnregistrerFuseauHoraireCommand commande);

    Optional<FuseauHoraire> trouverParIdentifiantIana(String identifiantIana);

    List<FuseauHoraire> lister();
}
