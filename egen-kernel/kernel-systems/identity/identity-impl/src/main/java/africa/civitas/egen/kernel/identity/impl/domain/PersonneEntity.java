package africa.civitas.egen.kernel.identity.impl.domain;

import africa.civitas.egen.kernel.identity.api.domain.StatutVerificationIdentite;
import africa.civitas.egen.kernel.identity.api.domain.StatutVital;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entite JPA de la Personne (A1.1). Jamais exposee en dehors de identity-impl — tout
 * consommateur externe manipule uniquement {@link africa.civitas.egen.kernel.identity.api.domain.Personne}.
 */
@Entity
@Table(name = "identity_personne")
public class PersonneEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "identifiant_civil_reference", nullable = false, unique = true, length = 100)
    public String identifiantCivilReference;

    @Column(name = "nom_naissance", nullable = false, length = 200)
    public String nomNaissance;

    @Column(name = "nom_usage", length = 200)
    public String nomUsage;

    @ElementCollection
    @CollectionTable(name = "identity_personne_prenoms", joinColumns = @JoinColumn(name = "personne_id"))
    @OrderColumn(name = "ordre")
    @Column(name = "prenom", nullable = false, length = 100)
    public List<String> prenoms = new ArrayList<>();

    @Column(name = "date_naissance", nullable = false)
    public LocalDate dateNaissance;

    @Column(name = "lieu_naissance", length = 200)
    public String lieuNaissance;

    @Column(name = "genre_declare", length = 50)
    public String genreDeclare;

    @ElementCollection
    @CollectionTable(name = "identity_personne_nationalites", joinColumns = @JoinColumn(name = "personne_id"))
    @Column(name = "code_pays", nullable = false, length = 3)
    public List<String> codesPaysNationalite = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "identity_personne_langues_preferees", joinColumns = @JoinColumn(name = "personne_id"))
    @Column(name = "code_langue", nullable = false, length = 10)
    public List<String> codesLanguePreferee = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_vital", nullable = false, length = 20)
    public StatutVital statutVital;

    @Column(name = "telephone_principal", length = 50)
    public String telephonePrincipal;

    @Column(name = "email_secours", length = 255)
    public String emailSecours;

    @Column(name = "photo_reference_url", length = 500)
    public String photoReferenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_verification_identite", nullable = false, length = 30)
    public StatutVerificationIdentite statutVerificationIdentite;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
