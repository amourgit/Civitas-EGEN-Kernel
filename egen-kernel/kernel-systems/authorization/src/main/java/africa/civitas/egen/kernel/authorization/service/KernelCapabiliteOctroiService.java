package africa.civitas.egen.kernel.authorization.service;

import africa.civitas.egen.kernel.authorization.command.AccorderCapaciteCommand;
import africa.civitas.egen.kernel.authorization.command.RevoquerCapaciteCommand;
import africa.civitas.egen.kernel.authorization.domain.KernelCapabiliteOctroi;
import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;

import java.util.List;
import java.util.UUID;

/**
 * Administre les octrois de capacites noyau — la seule maniere, en dehors du sujet
 * bootstrap, pour un {@link KernelSubject} d'obtenir une {@code KernelCapability}.
 */
public interface KernelCapabiliteOctroiService {

    /**
     * @throws africa.civitas.egen.kernel.authorization.exception.CapaciteAdministrationRefuseeException
     *         si {@code commande.sujetDemandeur()} n'a pas le droit d'administrer les capacites
     * @throws africa.civitas.egen.kernel.authorization.exception.OctroiDejaActifException
     *         si un octroi actif existe deja pour ce sujet beneficiaire et cette capacite
     */
    KernelCapabiliteOctroi accorder(AccorderCapaciteCommand commande);

    /**
     * @throws africa.civitas.egen.kernel.authorization.exception.CapaciteAdministrationRefuseeException
     *         si {@code commande.sujetDemandeur()} n'a pas le droit d'administrer les capacites
     * @throws africa.civitas.egen.kernel.authorization.exception.OctroiIntrouvableException
     *         si l'octroi cible est introuvable ou deja revoque
     */
    void revoquer(RevoquerCapaciteCommand commande);

    /** @return tous les octrois (actifs et revoques) d'un sujet, les plus recents d'abord. */
    List<KernelCapabiliteOctroi> listerPourSujet(UUID sujetId);
}
