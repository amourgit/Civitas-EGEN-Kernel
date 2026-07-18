package africa.civitas.egen.kernel.referencedata.api.service;

import africa.civitas.egen.kernel.referencedata.api.command.AjouterTypeCelluleModeleCommand;
import africa.civitas.egen.kernel.referencedata.api.domain.TypeCelluleModele;

import java.util.List;
import java.util.UUID;

public interface TypeCelluleModeleService {

    TypeCelluleModele ajouter(AjouterTypeCelluleModeleCommand commande);

    List<TypeCelluleModele> listerParModeleSectoriel(UUID modeleSectorialId);
}
