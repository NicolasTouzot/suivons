package fr.suivons.ingestion.sirene.naf;

/** Sous-classe d'une nomenclature NAF (ex. `62.01Z` en NAF rév. 2, `62.10Y` en NAF 2025). */
public record CodeNaf(String code, String libelle) {
}
