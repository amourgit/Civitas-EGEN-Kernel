package africa.civitas.egen.kernel.referencedata.api.service;

import africa.civitas.egen.kernel.referencedata.api.command.EnregistrerUniteMesureCommand;
import africa.civitas.egen.kernel.referencedata.api.domain.CategorieUniteMesure;
import africa.civitas.egen.kernel.referencedata.api.domain.UniteMesure;

import java.util.List;

public interface UniteMesureService {

    UniteMesure enregistrer(EnregistrerUniteMesureCommand commande);

    List<UniteMesure> listerParCategorie(CategorieUniteMesure categorie);

    List<UniteMesure> lister();
}
