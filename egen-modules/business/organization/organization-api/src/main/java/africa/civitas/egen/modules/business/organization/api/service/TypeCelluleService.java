package africa.civitas.egen.modules.business.organization.api.service;

import africa.civitas.egen.modules.business.organization.api.command.CreerTypeCelluleCommand;
import africa.civitas.egen.modules.business.organization.api.domain.TypeCellule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TypeCelluleService {

    TypeCellule creer(CreerTypeCelluleCommand commande);

    Optional<TypeCellule> trouverParId(UUID id);

    List<TypeCellule> listerParLexique(UUID lexiqueId);
}
