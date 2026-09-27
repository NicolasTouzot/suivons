-- Rôles applicatifs (SPEC.md §11.4, ADR 0005). Rôles de groupe sans connexion : chaque environnement
-- crée ses utilisateurs de connexion et les rend membres de ces rôles (mots de passe hors dépôt).
-- Le schéma appartient à l'utilisateur qui exécute les migrations, seul à détenir les droits DDL.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'suivons_ingestion') THEN
        CREATE ROLE suivons_ingestion NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'suivons_api') THEN
        CREATE ROLE suivons_api NOLOGIN;
    END IF;
END
$$;

-- Personne ne crée d'objet dans public
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- Ingestion : lecture et écriture des données, jamais de DDL
GRANT USAGE ON SCHEMA ops, raw, core, mart TO suivons_ingestion;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ops, raw, core, mart TO suivons_ingestion;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA ops, raw, core, mart TO suivons_ingestion;
REVOKE INSERT, UPDATE, DELETE ON ops.source FROM suivons_ingestion;

ALTER DEFAULT PRIVILEGES IN SCHEMA ops, raw, core, mart
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO suivons_ingestion;
ALTER DEFAULT PRIVILEGES IN SCHEMA ops, raw, core, mart
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO suivons_ingestion;

-- API : lecture seule des données servies (core, mart) et de l'état des sources ; aucun accès à raw
-- ni aux rejets. L'écriture de ops.signalement sera accordée avec la table (lot 6).
GRANT USAGE ON SCHEMA ops, core, mart TO suivons_api;
GRANT SELECT ON ALL TABLES IN SCHEMA core, mart TO suivons_api;
GRANT SELECT ON ops.source, ops.ingestion_run TO suivons_api;

ALTER DEFAULT PRIVILEGES IN SCHEMA core, mart GRANT SELECT ON TABLES TO suivons_api;
