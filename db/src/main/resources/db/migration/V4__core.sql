-- Schéma core : nomenclature NAF, référentiel minimal des bénéficiaires, payeurs, flux (SPEC.md §6.3).

-- Nomenclature d'activités : NAF rév. 2 et NAF 2025 (en vigueur au 01/01/2027), chargées depuis l'INSEE
CREATE TABLE core.naf (
    nomenclature text NOT NULL,
    code         text NOT NULL,
    libelle      text NOT NULL,
    PRIMARY KEY (nomenclature, code),
    CONSTRAINT naf_nomenclature_ck CHECK (nomenclature IN ('NAFRev2', 'NAF2025'))
);

COMMENT ON TABLE core.naf IS 'Nomenclature NAF (sous-classes), référentiel public statique';

-- Référentiel minimal : bénéficiaires d'au moins un flux uniquement (ADR 0004)
CREATE TABLE core.entreprise (
    siren             char(9)     PRIMARY KEY,
    denomination      text,
    naf_code          text,
    naf_nomenclature  text,
    commune_siege     text,
    departement_siege text,
    etat              text        NOT NULL,
    diffusible        boolean     NOT NULL,
    personne_physique boolean     NOT NULL,
    rafraichi_le      timestamptz NOT NULL,
    CONSTRAINT entreprise_siren_ck CHECK (siren ~ '^[0-9]{9}$'),
    CONSTRAINT entreprise_etat_ck CHECK (etat IN ('ACTIVE', 'CESSEE')),
    CONSTRAINT entreprise_naf_ck CHECK ((naf_code IS NULL) = (naf_nomenclature IS NULL)),
    CONSTRAINT entreprise_naf_nomenclature_ck CHECK (naf_nomenclature IN ('NAFRev2', 'NAF2025'))
);

COMMENT ON TABLE core.entreprise IS 'Référentiel minimal des bénéficiaires (ADR 0004) ; identité détaillée lue via l''API Sirene';
COMMENT ON COLUMN core.entreprise.diffusible IS 'Faux si statutDiffusionUniteLegale = P : aucune donnée nominative exposée';
COMMENT ON COLUMN core.entreprise.personne_physique IS 'Vrai si catégorie juridique 1000 (entrepreneur individuel) : aucune donnée nominative exposée';

CREATE TABLE core.payeur (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    identifiant  text NOT NULL UNIQUE,
    nom          text,
    type         text NOT NULL,
    CONSTRAINT payeur_type_ck CHECK (type IN ('ETAT', 'OPERATEUR', 'COLLECTIVITE', 'HOPITAL', 'UE', 'AUTRE'))
);

COMMENT ON TABLE core.payeur IS 'Organismes payeurs : acheteurs publics, autorités d''octroi, programmes UE';

-- Flux : un versement ou engagement d'argent public, partitionné par année (SPEC §6.3, §11.1).
-- PostgreSQL impose la clé de partition dans toute contrainte d'unicité : l'unicité est déclarée sur
-- (source_code, source_record_id, annee) ; l'unicité sur (source_code, source_record_id) seul, qui fonde
-- l'idempotence, est garantie par ingestion-core (un changement d'année remplace la ligne existante).
CREATE TABLE core.flux (
    id                      bigint GENERATED ALWAYS AS IDENTITY,
    canal                   text          NOT NULL,
    beneficiaire_siren      char(9)       REFERENCES core.entreprise (siren),
    beneficiaire_nom_source text,
    payeur_id               bigint        REFERENCES core.payeur (id),
    objet                   text,
    date_flux               date          NOT NULL,
    annee                   smallint      NOT NULL,
    montant_ferme           bigint,
    montant_plafond         bigint,
    nature_montant          text          NOT NULL,
    qualite                 text          NOT NULL DEFAULT 'OK',
    rattachement            text          NOT NULL,
    confiance_rattachement  numeric(4, 3),
    source_code             text          NOT NULL REFERENCES ops.source (code),
    source_record_id        text          NOT NULL,
    source_url              text          NOT NULL,
    run_id                  bigint        NOT NULL REFERENCES ops.ingestion_run (id),
    extrait_le              timestamptz   NOT NULL,
    PRIMARY KEY (id, annee),
    CONSTRAINT flux_source_record_uk UNIQUE (source_code, source_record_id, annee),
    CONSTRAINT flux_canal_ck CHECK (canal IN ('MARCHE', 'AIDE_ETAT', 'FONDS_UE')),
    CONSTRAINT flux_annee_ck CHECK (annee = extract(YEAR FROM date_flux)),
    CONSTRAINT flux_nature_ck CHECK (nature_montant IN ('FERME', 'PLAFOND', 'PARTAGE', 'INCONNU')),
    CONSTRAINT flux_qualite_ck CHECK (qualite IN ('OK', 'ABERRANT')),
    CONSTRAINT flux_rattachement_ck CHECK (rattachement IN ('SIREN_SOURCE', 'RESOLU_AUTO', 'NON_RESOLU')),
    -- Montants : entiers en euros, jamais négatifs ; borne ferme ≤ borne plafond
    CONSTRAINT flux_montants_positifs_ck CHECK (montant_ferme >= 0 AND montant_plafond >= 0),
    CONSTRAINT flux_bornes_ck CHECK (montant_ferme <= montant_plafond),
    -- Seul un montant de nature INCONNU peut être absent
    CONSTRAINT flux_plafond_ck CHECK (nature_montant = 'INCONNU' OR montant_plafond IS NOT NULL),
    CONSTRAINT flux_ferme_ck CHECK (nature_montant <> 'FERME' OR montant_ferme = montant_plafond),
    -- Rattachement : un flux rattaché a un SIREN, un flux non résolu n'en a jamais (SPEC §6.5)
    CONSTRAINT flux_rattachement_siren_ck CHECK ((rattachement = 'NON_RESOLU') = (beneficiaire_siren IS NULL)),
    CONSTRAINT flux_confiance_ck CHECK (confiance_rattachement BETWEEN 0 AND 1),
    CONSTRAINT flux_confiance_source_ck CHECK (rattachement <> 'SIREN_SOURCE' OR confiance_rattachement = 1)
) PARTITION BY RANGE (annee);

COMMENT ON TABLE core.flux IS 'Flux d''argent public, chacun relié à sa source (provenance obligatoire)';

-- Une partition par année de 2010 à 2030, plus une partition par défaut (dates hors plage, à contrôler)
DO $$
BEGIN
    FOR a IN 2010..2030 LOOP
        EXECUTE format('CREATE TABLE core.flux_%s PARTITION OF core.flux FOR VALUES FROM (%s) TO (%s)', a, a, a + 1);
    END LOOP;
END
$$;

CREATE TABLE core.flux_hors_plage PARTITION OF core.flux DEFAULT;

CREATE INDEX flux_beneficiaire_idx ON core.flux (beneficiaire_siren);
CREATE INDEX flux_payeur_idx ON core.flux (payeur_id);
CREATE INDEX flux_run_idx ON core.flux (run_id);
