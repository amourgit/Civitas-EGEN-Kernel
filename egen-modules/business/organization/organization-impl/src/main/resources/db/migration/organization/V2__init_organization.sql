-- Convention de versionnement Flyway : CORRECTION apportee apres un premier echec
-- CI (Flyway refuse deux fichiers de version 1 dans un meme jeu de migrations
-- resolu, meme s'ils appartiennent a deux tables d'historique differentes). La
-- premiere version de ce refactoring affirmait a tort que chaque module pouvait
-- reprendre une sequence V1..Vn totalement independante en toutes circonstances :
-- c'est vrai pour un module jamais combine avec un autre dans une meme execution
-- Flyway (voir reference-data, qui n'a besoin des migrations d'aucun autre module),
-- mais FAUX des qu'un module combine ses locations avec celles d'un autre pour ses
-- tests d'integration reels — c'est le cas ici : organization-impl declare
-- classpath:db/migration/identity en plus de sa propre location (voir
-- application.properties), pour que ses tests du sous-domaine affiliation
-- disposent de vraies tables identity. Ce module commence donc a V2, parce que V1
-- est deja pris par identity-provider-keycloak dans cet ensemble combine — la table
-- d'historique reste neanmoins bien distincte (flyway_schema_history_organization),
-- et en production, ou seule la location organization est chargee, ce trou
-- (commencer a V2) est parfaitement valide pour Flyway.
--
-- Module business Organization (Niveau 2) — Organisation, Cellule, Rattachements,
-- Politique organisationnelle. Fusion actee par la Charte v3 (§C.1) de trois anciens
-- systemes du Kernel : Organisationnel (A2, ce fichier), Rattachements (A3, voir
-- V3__init_affiliation.sql) et Politique organisationnelle (§B.12, voir
-- V4__init_politique_organisationnelle.sql — a ne jamais confondre avec la future
-- Politique-noyau du Kernel, Niveau 1, qui vivra dans egen-kernel/kernel-systems/policy
-- sous un nom distinct des lors qu'elle sera concue).
--
-- Organisation, Lexique Organisationnel, Type de Cellule, Cellule (arbre recursif),
-- Fermeture Transitive (derivee, sans Socle de Traçabilite), Tutelle, Succession
-- Organisationnelle. Chaque table (sauf la Fermeture Transitive) porte integralement
-- le Socle de Traçabilite.
--
-- Sur la Tutelle (org_tutelle plus bas) : relation strictement intra-instance —
-- elle hierarchise/regroupe des Organisations au sein d'une seule et meme instance
-- deployee (une entreprise = une instance = son propre EGEN complet, decision actee
-- le 22 juillet 2026). Aucune federation inter-instances n'est prevue ni necessaire :
-- le mono-tenant par instance reste total.

CREATE TABLE org_organisation (
    id                                  UUID PRIMARY KEY,
    raison_sociale                      VARCHAR(200) NOT NULL,
    sigle                               VARCHAR(30) NOT NULL,
    type_organisation                   VARCHAR(20) NOT NULL,
    secteur_activite_principal          VARCHAR(150) NOT NULL,
    code_pays_rattachement_juridique    VARCHAR(2) NOT NULL,
    identifiant_juridique               VARCHAR(100) NOT NULL UNIQUE,
    code_devise_reference               VARCHAR(3) NOT NULL,
    identifiant_fuseau_horaire_reference VARCHAR(100) NOT NULL,
    modele_sectoriel_origine_id         UUID,
    statut                              VARCHAR(20) NOT NULL,
    date_adhesion                       DATE NOT NULL,
    url_identite_visuelle               VARCHAR(500),

    cree_le                             TIMESTAMPTZ NOT NULL,
    cree_par_type                       VARCHAR(20) NOT NULL,
    cree_par_personne_id                UUID,
    cree_par_systeme_label              VARCHAR(100),
    modifie_le                          TIMESTAMPTZ NOT NULL,
    modifie_par_type                    VARCHAR(20) NOT NULL,
    modifie_par_personne_id             UUID,
    modifie_par_systeme_label           VARCHAR(100),
    version                             BIGINT NOT NULL,
    origine_donnee                      VARCHAR(30) NOT NULL,
    motif_derniere_modification         TEXT,
    supprime_le                         TIMESTAMPTZ,
    supprime_par_type                   VARCHAR(20),
    supprime_par_personne_id            UUID,
    supprime_par_systeme_label          VARCHAR(100)
);

CREATE TABLE org_organisation_langues_officielles (
    organisation_id UUID NOT NULL REFERENCES org_organisation(id) ON DELETE CASCADE,
    code_langue     VARCHAR(10) NOT NULL,
    PRIMARY KEY (organisation_id, code_langue)
);

CREATE TABLE org_lexique_organisationnel (
    id                              UUID PRIMARY KEY,
    organisation_id                 UUID NOT NULL REFERENCES org_organisation(id),
    nom                             VARCHAR(150) NOT NULL,
    description                     TEXT NOT NULL,
    modele_sectoriel_origine_id     UUID,

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

CREATE INDEX idx_org_lexique_organisation ON org_lexique_organisationnel(organisation_id);

CREATE TABLE org_type_cellule (
    id                              UUID PRIMARY KEY,
    lexique_id                      UUID NOT NULL REFERENCES org_lexique_organisationnel(id),
    libelle                         VARCHAR(100) NOT NULL,
    description                     TEXT NOT NULL,
    niveau_indicatif                INT NOT NULL,
    type_cellule_modele_origine_id  UUID,
    statut                          VARCHAR(20) NOT NULL,

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

CREATE INDEX idx_org_type_cellule_lexique ON org_type_cellule(lexique_id);

CREATE TABLE org_type_cellule_parents_autorises (
    type_cellule_id        UUID NOT NULL REFERENCES org_type_cellule(id) ON DELETE CASCADE,
    type_cellule_parent_id UUID NOT NULL,
    PRIMARY KEY (type_cellule_id, type_cellule_parent_id)
);

CREATE TABLE org_cellule (
    id                              UUID PRIMARY KEY,
    organisation_id                 UUID NOT NULL REFERENCES org_organisation(id),
    cellule_parent_id               UUID REFERENCES org_cellule(id),
    type_cellule_id                 UUID NOT NULL REFERENCES org_type_cellule(id),
    nom                             VARCHAR(200) NOT NULL,
    code_interne                    VARCHAR(50) NOT NULL,
    description                     TEXT,
    code_pays_localisation          VARCHAR(2),
    adresse_physique                VARCHAR(300),
    statut                          VARCHAR(20) NOT NULL,
    valid_du                        DATE NOT NULL,
    valid_au                        DATE,

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
    supprime_par_systeme_label      VARCHAR(100),

    CONSTRAINT uq_org_cellule_org_code UNIQUE (organisation_id, code_interne)
);

CREATE INDEX idx_org_cellule_organisation ON org_cellule(organisation_id);
CREATE INDEX idx_org_cellule_parent ON org_cellule(cellule_parent_id);

-- Fermeture Transitive : entierement derivee, jamais de Socle de Traçabilite.
CREATE TABLE org_fermeture_transitive_cellule (
    id                       UUID PRIMARY KEY,
    cellule_ancetre_id       UUID NOT NULL REFERENCES org_cellule(id),
    cellule_descendante_id   UUID NOT NULL REFERENCES org_cellule(id),
    profondeur               INT NOT NULL,

    CONSTRAINT uq_org_fermeture_ancetre_descendant UNIQUE (cellule_ancetre_id, cellule_descendante_id)
);

CREATE INDEX idx_org_fermeture_ancetre ON org_fermeture_transitive_cellule(cellule_ancetre_id);
CREATE INDEX idx_org_fermeture_descendant ON org_fermeture_transitive_cellule(cellule_descendante_id);

CREATE TABLE org_tutelle (
    id                              UUID PRIMARY KEY,
    cellule_racine_id               UUID NOT NULL REFERENCES org_cellule(id),
    organisation_id                 UUID NOT NULL REFERENCES org_organisation(id),
    nature                          VARCHAR(20) NOT NULL,
    tutelle_principale              BOOLEAN NOT NULL,
    date_debut                      DATE NOT NULL,
    date_fin                        DATE,
    acte_justificatif_ref           UUID,

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

CREATE INDEX idx_org_tutelle_cellule_racine ON org_tutelle(cellule_racine_id);

CREATE TABLE org_succession_organisationnelle (
    id                              UUID PRIMARY KEY,
    nature                          VARCHAR(30) NOT NULL,
    date_effet                      DATE NOT NULL,
    motif_decision_reference        TEXT,

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

CREATE TABLE org_succession_origine (
    succession_id UUID NOT NULL REFERENCES org_succession_organisationnelle(id) ON DELETE CASCADE,
    cellule_id    UUID NOT NULL,
    PRIMARY KEY (succession_id, cellule_id)
);

CREATE TABLE org_succession_heritiere (
    succession_id UUID NOT NULL REFERENCES org_succession_organisationnelle(id) ON DELETE CASCADE,
    cellule_id    UUID NOT NULL,
    PRIMARY KEY (succession_id, cellule_id)
);
