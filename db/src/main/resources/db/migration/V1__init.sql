-- Socle du modèle de données (SPEC.md §6) : extensions et schémas, sans table.

-- Recherche floue et insensible aux accents sur les noms (SPEC.md §7.4)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE SCHEMA ops;   -- exploitation : sources, exécutions, rejets, signalements
CREATE SCHEMA raw;   -- données brutes telles que reçues (rejouables)
CREATE SCHEMA core;  -- modèle normalisé : entreprises, payeurs, flux
CREATE SCHEMA mart;  -- agrégats pré-calculés servis par l'API

COMMENT ON SCHEMA ops IS 'Exploitation : sources, exécutions, rejets, signalements';
COMMENT ON SCHEMA raw IS 'Données brutes telles que reçues, rejouables';
COMMENT ON SCHEMA core IS 'Modèle normalisé : entreprises, payeurs, flux';
COMMENT ON SCHEMA mart IS 'Agrégats pré-calculés servis par l''API';
