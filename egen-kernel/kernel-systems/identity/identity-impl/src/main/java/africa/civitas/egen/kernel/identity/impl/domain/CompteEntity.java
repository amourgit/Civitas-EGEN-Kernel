package africa.civitas.egen.kernel.identity.impl.domain;

import africa.civitas.egen.kernel.identity.api.domain.MethodeAuthentification;
import africa.civitas.egen.kernel.identity.api.domain.StatutCompte;
import africa.civitas.egen.kernel.identity.api.domain.TypeCompte;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entite JPA du Compte (A1.2). Jamais exposee en dehors de identity-impl.
 */
@Entity
@Table(name = "identity_compte")
public class CompteEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "keycloak_id", nullable = false, unique = true, length = 100)
    public String keycloakId;

    @Column(name = "identifiant_connexion", nullable = false, unique = true, length = 255)
    public String identifiantConnexion;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_compte", nullable = false, length = 30)
    public TypeCompte typeCompte;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_compte", nullable = false, length = 20)
    public StatutCompte statutCompte;

    @Column(name = "derniere_connexion_reussie")
    public Instant derniereConnexionReussie;

    @Enumerated(EnumType.STRING)
    @Column(name = "methode_authentification", nullable = false, length = 20)
    public MethodeAuthentification methodeAuthentification;

    @Column(name = "personne_id", nullable = false)
    public UUID personneId;

    @Column(name = "date_expiration_prevue")
    public Instant dateExpirationPrevue;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
