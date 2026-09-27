# API Recherche d'entreprises (DINUM) — service d'appui

> Spike du 2026-09-27 (lot 0, étape 9) : **non testable depuis l'environnement cloud**. Constats tirés de la documentation OpenAPI fournie par le porteur.

| | |
|---|---|
| Base | `https://recherche-entreprises.api.gouv.fr` (sans clé) |
| Endpoints | `GET /search?q=…` (texte ou SIREN/SIRET), `GET /near_point` |
| Limites | **7 requêtes/s par IP, 30 requêtes/s par ASN** ; `429` + `Retry-After` ; `User-Agent` descriptif recommandé |
| Pagination | `per_page` ≤ 25 |
| Périmètre | entreprises diffusibles uniquement (les non-diffusibles sont absentes) |
| Utile pour le rattachement | `nom_complet`, `siege` (adresse, commune, code postal), `activite_principale`, `nature_juridique`, `etat_administratif`, `score` (avec `minimal=true&include=score`), `matching_etablissements` |

## Accès depuis le cloud

Connexion TLS établie puis coupée par l'amont (`ws_closed_mid_exchange`, 39 octets reçus), alors que `annuaire-entreprises.data.gouv.fr` répond. Selon le porteur, l'API fonctionne depuis d'autres hébergements : le blocage vient de la **détection des robots** par le fournisseur sur cet environnement cloud. Même comportement pour `www.data.gouv.fr`.

## Conséquences

- Spike réel à faire en session locale (temps de réponse, qualité du score pour le rattachement Kohesio et TAM).
- **Hébergement de production** : la limite par ASN et la détection des robots s'appliquent à notre hébergeur : accès à vérifier depuis l'hébergeur retenu (SPEC §14, lot 7) ; mode dégradé obligatoire (SPEC §8 : le repli de recherche peut se dégrader, jamais les montants).
