-- Politique organisationnelle (§B.12 de la Charte v3), desormais sous-domaine
-- "politique" du module business Organization.
--
-- Correction actee lors du refactoring vers la Charte v3 : ce contenu portait
-- auparavant l'etiquette "Systeme B1" et vivait dans kernel-systems/policy, comme
-- s'il s'agissait de la Politique-noyau du Kernel (le vrai B1, Niveau 1 — resolution
-- fail-closed avant chargement d'un module de gouvernance). Ce n'est pas le cas :
-- Politique + Derogation, avec resolution "la Derogation la plus proche dans l'arbre
-- l'emporte" sur un Contexte (Organisation/Cellule), sont exactement la Politique
-- organisationnelle du §B.12 — un concept qui "vit entierement dans le module
-- Organisation", pas B1. La vraie Politique-noyau (Niveau 1) reste entierement a
-- concevoir (point 3, toujours ouvert) et portera un nom distinct dans le code
-- (kernel.policy vs organization.politique, comme l'exige la Charte) pour ne plus
-- jamais entretenir cette confusion.
--
-- Politique, Derogation. Chaque table porte integralement le Socle de Traçabilite.
--
-- Renumerotee V4 dans la sequence de ce module (voir la note de convention
-- dans V2__init_organization.sql — ce module combine ses migrations avec celles
-- d'identity-provider-keycloak pour ses tests, d'ou le decalage a partir de V2).
-- Anciennement V5 dans l'ancienne sequence globale platform-wide.

CREATE TABLE pol_politique (
    id                              UUID PRIMARY KEY,
    contexte_id                     UUID NOT NULL,
    contexte_nature                 VARCHAR(20) NOT NULL,
    domaine                         VARCHAR(30) NOT NULL,
    nom_regle                       VARCHAR(150) NOT NULL,
    valeur                          TEXT NOT NULL,
    statut                          VARCHAR(20) NOT NULL,
    date_entree_vigueur             DATE NOT NULL,

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

CREATE INDEX idx_pol_politique_contexte ON pol_politique(contexte_id);

CREATE TABLE pol_derogation (
    id                              UUID PRIMARY KEY,
    politique_id                    UUID NOT NULL REFERENCES pol_politique(id),
    cellule_derogatoire_id          UUID NOT NULL,
    valeur                          TEXT NOT NULL,
    justification                   TEXT NOT NULL,
    date_entree_vigueur             DATE NOT NULL,
    date_fin                        DATE,

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

    CONSTRAINT uq_pol_derogation_politique_cellule UNIQUE (politique_id, cellule_derogatoire_id)
);

CREATE INDEX idx_pol_derogation_politique ON pol_derogation(politique_id);
