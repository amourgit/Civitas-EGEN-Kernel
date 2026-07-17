package africa.civitas.egen.kernel.identity.api.service;

import africa.civitas.egen.kernel.identity.api.command.EnregistrerHistoriqueIdentiteCommand;
import africa.civitas.egen.kernel.identity.api.domain.HistoriqueIdentite;

import java.util.List;
import java.util.UUID;

/** Contrat de service du Systeme Identite pour l'Historique d'Identite. */
public interface HistoriqueIdentiteService {

    /** Enregistre une nouvelle entree d'historique pour une Personne existante. */
    HistoriqueIdentite enregistrer(EnregistrerHistoriqueIdentiteCommand commande);

    /** Liste l'historique complet d'une Personne, du plus ancien au plus recent. */
    List<HistoriqueIdentite> pourPersonne(UUID personneId);
}
