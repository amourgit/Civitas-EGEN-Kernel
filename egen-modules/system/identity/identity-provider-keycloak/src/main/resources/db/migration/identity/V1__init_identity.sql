-- Provider Identite — Keycloak (Niveau 2, egen-modules/system/identity). Ancien
-- Systeme A1 du Kernel, repositionne lors du refactoring vers la Charte v3 : ce
-- contenu a toujours ete un provider Keycloak riche, jamais le primitif Niveau 1 du
-- Kernel (KernelSubject, encore a concevoir — point 3, toujours ouvert).
--
-- Convention de versionnement Flyway : ce module gere sa propre sequence V1..Vn
-- (flyway.locations = db/migration/identity, table flyway_schema_history_identity),
-- independante des autres modules. L'ancienne convention platform-wide unique (V1
-- identity, V2 reference-data, V3 organization, V4 affiliation, V5 policy) est
-- abandonnee : elle ne se justifiait que tant que ces systemes etaient tous des
-- artefacts pairs du Kernel candidats a etre combines dans une meme base de tests
-- (ex. l'ancien affiliation-impl, qui declarait identity-impl en dependance de scope
-- test pour ses tests d'integration reels). Le module business Organization fusionne
-- (qui porte desormais Rattachements) reproduit ce meme besoin en declarant
-- identity-provider-keycloak en dependance de scope test — voir son pom.xml.
--
-- Cree Personne, Compte, Historique d'Identite, et leurs collections d'elements.
-- Chaque table porte integralement le Socle de Traçabilite.

CREATE TABLE identity_personne (
    id                              UUID PRIMARY KEY,
    identifiant_civil_reference     VARCHAR(100) NOT NULL UNIQUE,
    nom_naissance                   VARCHAR(200) NOT NULL,
    nom_usage                       VARCHAR(200),
    date_naissance                  DATE NOT NULL,
    lieu_naissance                  VARCHAR(200),
    genre_declare                   VARCHAR(50),
    statut_vital                    VARCHAR(20) NOT NULL,
    telephone_principal             VARCHAR(50),
    email_secours                   VARCHAR(255),
    photo_reference_url             VARCHAR(500),
    statut_verification_identite    VARCHAR(30) NOT NULL,

    -- Socle de Traçabilite
    cree_le                         TIMESTAMPTZ NOT NULL,
    cree_par_type                   VARCHAR(20) NOT NULL,
    cree_par_personne_id            UUID,
    cree_par_systeme_label          VARCHAR(100),
    modifie_le                      TIMESTAMPTZ NOT NULL,
    modifie_par_type                VARCHAR(20) NOT NULL,
    modifie_par_personne_id         UUID,
    modifie_par_systeme_label       VARCHAR(100),
    version                         BIGINT NOT NULL,
    origine_donnee                  VARCHAR(30) NOT NULL,
    motif_derniere_modification     TEXT,
    supprime_le                     TIMESTAMPTZ,
    supprime_par_type               VARCHAR(20),
    supprime_par_personne_id        UUID,
    supprime_par_systeme_label      VARCHAR(100)
);

CREATE TABLE identity_personne_prenoms (
    personne_id UUID NOT NULL REFERENCES identity_personne(id) ON DELETE CASCADE,
    ordre       INT  NOT NULL,
    prenom      VARCHAR(100) NOT NULL,
    PRIMARY KEY (personne_id, ordre)
);

CREATE TABLE identity_personne_nationalites (
    personne_id UUID NOT NULL REFERENCES identity_personne(id) ON DELETE CASCADE,
    code_pays   VARCHAR(3) NOT NULL,
    PRIMARY KEY (personne_id, code_pays)
);

CREATE TABLE identity_personne_langues_preferees (
    personne_id UUID NOT NULL REFERENCES identity_personne(id) ON DELETE CASCADE,
    code_langue VARCHAR(10) NOT NULL,
    PRIMARY KEY (personne_id, code_langue)
);

CREATE TABLE identity_compte (
    id                              UUID PRIMARY KEY,
    keycloak_id                     VARCHAR(100) NOT NULL UNIQUE,
    identifiant_connexion           VARCHAR(255) NOT NULL UNIQUE,
    type_compte                     VARCHAR(30) NOT NULL,
    statut_compte                   VARCHAR(20) NOT NULL,
    derniere_connexion_reussie      TIMESTAMPTZ,
    methode_authentification        VARCHAR(20) NOT NULL,
    personne_id                     UUID NOT NULL REFERENCES identity_personne(id),
    date_expiration_prevue          TIMESTAMPTZ,

    cree_le                         TIMESTAMPTZ NOT NULL,
    cree_par_type                   VARCHAR(20) NOT NULL,
    cree_par_personne_id            UUID,
    cree_par_systeme_label          VARCHAR(100),
    modifie_le                      TIMESTAMPTZ NOT NULL,
    modifie_par_type                VARCHAR(20) NOT NULL,
    modifie_par_personne_id         UUID,
    modifie_par_systeme_label       VARCHAR(100),
    version                         BIGINT NOT NULL,
    origine_donnee                  VARCHAR(30) NOT NULL,
    motif_derniere_modification     TEXT,
    supprime_le                     TIMESTAMPTZ,
    supprime_par_type               VARCHAR(20),
    supprime_par_personne_id        UUID,
    supprime_par_systeme_label      VARCHAR(100)
);

CREATE INDEX idx_identity_compte_personne_id ON identity_compte(personne_id);

CREATE TABLE identity_historique_identite (
    id                              UUID PRIMARY KEY,
    personne_id                     UUID NOT NULL REFERENCES identity_personne(id),
    type_changement                 VARCHAR(40) NOT NULL,
    valeur_precedente               TEXT NOT NULL,
    valeur_nouvelle                 TEXT NOT NULL,
    piece_justificative_ref         UUID,
    date_effet                      DATE NOT NULL,

    cree_le                         TIMESTAMPTZ NOT NULL,
    cree_par_type                   VARCHAR(20) NOT NULL,
    cree_par_personne_id            UUID,
    cree_par_systeme_label          VARCHAR(100),
    modifie_le                      TIMESTAMPTZ NOT NULL,
    modifie_par_type                VARCHAR(20) NOT NULL,
    modifie_par_personne_id         UUID,
    modifie_par_systeme_label       VARCHAR(100),
    version                         BIGINT NOT NULL,
    origine_donnee                  VARCHAR(30) NOT NULL,
    motif_derniere_modification     TEXT,
    supprime_le                     TIMESTAMPTZ,
    supprime_par_type               VARCHAR(20),
    supprime_par_personne_id        UUID,
    supprime_par_systeme_label      VARCHAR(100)
);

CREATE INDEX idx_identity_historique_personne_id ON identity_historique_identite(personne_id);
