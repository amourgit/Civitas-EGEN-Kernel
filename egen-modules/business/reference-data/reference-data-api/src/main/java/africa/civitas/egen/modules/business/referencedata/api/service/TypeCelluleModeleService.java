package africa.civitas.egen.modules.business.referencedata.api.service;

import africa.civitas.egen.modules.business.referencedata.api.command.AjouterTypeCelluleModeleCommand;
import africa.civitas.egen.modules.business.referencedata.api.domain.TypeCelluleModele;

import java.util.List;
import java.util.UUID;

public interface TypeCelluleModeleService {

    TypeCelluleModele ajouter(AjouterTypeCelluleModeleCommand commande);

    List<TypeCelluleModele> listerParModeleSectoriel(UUID modeleSectorialId);
}
