package africa.civitas.egen.kernel.referencedata.api.service;

import africa.civitas.egen.kernel.referencedata.api.command.AjouterMandatModeleCommand;
import africa.civitas.egen.kernel.referencedata.api.domain.MandatModele;

import java.util.List;
import java.util.UUID;

public interface MandatModeleService {

    MandatModele ajouter(AjouterMandatModeleCommand commande);

    List<MandatModele> listerParModeleSectoriel(UUID modeleSectorialId);
}
