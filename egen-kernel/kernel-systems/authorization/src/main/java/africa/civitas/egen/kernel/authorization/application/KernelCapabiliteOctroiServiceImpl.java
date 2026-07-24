package africa.civitas.egen.kernel.authorization.application;

import africa.civitas.egen.kernel.authorization.command.AccorderCapaciteCommand;
import africa.civitas.egen.kernel.authorization.command.RevoquerCapaciteCommand;
import africa.civitas.egen.kernel.authorization.domain.KernelCapabiliteOctroi;
import africa.civitas.egen.kernel.authorization.exception.CapaciteAdministrationRefuseeException;
import africa.civitas.egen.kernel.authorization.exception.OctroiDejaActifException;
import africa.civitas.egen.kernel.authorization.exception.OctroiIntrouvableException;
import africa.civitas.egen.kernel.authorization.infrastructure.KernelCapabiliteOctroiEntity;
import africa.civitas.egen.kernel.authorization.infrastructure.KernelCapabiliteOctroiMapper;
import africa.civitas.egen.kernel.authorization.infrastructure.KernelCapabiliteOctroiRepository;
import africa.civitas.egen.kernel.identity.KernelSubjectService;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.authorization.service.KernelCapabiliteOctroiService;
import africa.civitas.egen.kernel.sdk.permission.authorization.DecisionNoyau;
import africa.civitas.egen.kernel.sdk.permission.authorization.KernelCapability;
import africa.civitas.egen.kernel.sdk.permission.authorization.KernelPermissionCheck;
import africa.civitas.egen.kernel.sdk.permission.identity.KernelSubject;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * L'unique implementation de l'administration des octrois de capacites noyau.
 *
 * <p>Chaque operation commence par verifier, via {@link KernelPermissionCheck}, que le
 * sujet demandeur detient lui-meme {@link KernelCapability#ADMINISTRER_CAPACITES_NOYAU}
 * (ou est le sujet bootstrap) — c'est la seule maniere dont ce systeme evite d'etre
 * sa propre faille : administrer les droits d'autrui exige deja un droit.
 */
@ApplicationScoped
public class KernelCapabiliteOctroiServiceImpl implements KernelCapabiliteOctroiService {

    @Inject
    KernelCapabiliteOctroiRepository repository;

    @Inject
    KernelPermissionCheck permissionCheck;

    @Inject
    KernelSubjectService kernelSubjectService;

    @Override
    @Transactional
    public KernelCapabiliteOctroi accorder(AccorderCapaciteCommand commande) {
        exigerDroitAdministration(commande.sujetDemandeur());

        if (repository.existeOctroiActif(commande.sujetBeneficiaireId(), commande.capacite())) {
            throw new OctroiDejaActifException(
                    "Un octroi actif existe deja pour le sujet " + commande.sujetBeneficiaireId()
                            + " et la capacite " + commande.capacite() + ".");
        }

        KernelCapabiliteOctroiEntity entity = new KernelCapabiliteOctroiEntity();
        entity.id = UUID.randomUUID();
        entity.sujetId = commande.sujetBeneficiaireId();
        entity.capacite = commande.capacite();

        Acteur demandePar = kernelSubjectService.versActeur(commande.sujetDemandeur());
        Tracabilite tracabilite = Tracabilite.initiale(demandePar, commande.origineDonnee());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(tracabilite);

        repository.persist(entity);
        return KernelCapabiliteOctroiMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void revoquer(RevoquerCapaciteCommand commande) {
        exigerDroitAdministration(commande.sujetDemandeur());

        KernelCapabiliteOctroiEntity entity = repository.findByIdOptional(commande.octroiId())
                .orElseThrow(() -> new OctroiIntrouvableException(
                        "Aucun octroi ne correspond a l'identifiant " + commande.octroiId() + "."));

        Tracabilite actuelle = entity.tracabilite.toDomain();
        if (actuelle.estSupprimee()) {
            throw new OctroiIntrouvableException(
                    "L'octroi " + commande.octroiId() + " est deja revoque.");
        }

        Acteur revoquePar = kernelSubjectService.versActeur(commande.sujetDemandeur());
        Tracabilite miseAJour = actuelle.avecSuppressionLogique(revoquePar, commande.motif());
        entity.tracabilite = TracabiliteEmbeddable.fromDomain(miseAJour);
    }

    @Override
    public List<KernelCapabiliteOctroi> listerPourSujet(UUID sujetId) {
        return repository.listerPourSujet(sujetId).stream()
                .map(KernelCapabiliteOctroiMapper::toDomain)
                .toList();
    }

    private void exigerDroitAdministration(KernelSubject sujetDemandeur) {
        DecisionNoyau decision = permissionCheck.verifier(
                sujetDemandeur, KernelCapability.ADMINISTRER_CAPACITES_NOYAU);
        if (!decision.autorise()) {
            throw new CapaciteAdministrationRefuseeException(
                    "Le sujet " + sujetDemandeur.id() + " ne peut pas administrer les capacites "
                            + "noyau d'un autre sujet : " + decision.motif());
        }
    }
}
