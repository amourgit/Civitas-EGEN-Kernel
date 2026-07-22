package africa.civitas.egen.modules.system.identity.keycloak.domain;

import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;

import africa.civitas.egen.modules.system.identity.api.domain.TypeChangementIdentite;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Entite JPA de l'Historique d'Identite (A1.3). Jamais exposee en dehors de identity-provider-keycloak.
 */
@Entity
@Table(name = "identity_historique_identite")
public class HistoriqueIdentiteEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "personne_id", nullable = false)
    public UUID personneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_changement", nullable = false, length = 40)
    public TypeChangementIdentite typeChangement;

    @Column(name = "valeur_precedente", nullable = false, columnDefinition = "text")
    public String valeurPrecedente;

    @Column(name = "valeur_nouvelle", nullable = false, columnDefinition = "text")
    public String valeurNouvelle;

    @Column(name = "piece_justificative_ref")
    public UUID pieceJustificativeRef;

    @Column(name = "date_effet", nullable = false)
    public LocalDate dateEffet;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
