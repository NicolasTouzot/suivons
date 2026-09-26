# 0004 — Référentiel entreprises : API d'abord, pas de copie de SIRENE

- Statut : Accepté
- Date : 2026-09-26

## Contexte

La v0.1 de la spec prévoyait de charger le stock SIRENE complet (~30 M unités) dans `core.entreprise`. Principe retenu par le porteur du projet : **une donnée accessible par API n'est pas recopiée en base**.

L'API Sirene 3.11 de l'INSEE (plan « Accès public », clé API) est limitée à **30 requêtes/min et 2 000/h**. Elle accepte des requêtes multicritères renvoyant jusqu'à 1 000 unités par appel. L'API Recherche d'entreprises (DINUM) offre une recherche floue sans clé.

Certains besoins ne peuvent pas reposer sur un appel en direct :
- l'autocomplétion (F1) : 10 résultats par frappe, avec commune et NAF, et un tri par total tracé ;
- le filtrage RGPD (diffusion partielle, personnes physiques) dans les agrégats du mart ;
- la recherche floue interne du rattachement.

## Décision

1. **Pas de copie du stock SIRENE.** `core.entreprise` ne contient que les entreprises **bénéficiaires d'au moins un flux**, avec les seuls champs justifiés au `SPEC.md` §6.3 : `siren`, `denomination`, `naf_code`, `commune_siege`, `departement_siege`, `etat`, `diffusible`, `personne_physique`, `rafraichi_le`.
2. **Identité détaillée lue en direct** via l'API Sirene (endpoint `/entreprises/{siren}/identite`), avec un cache de 24 h, puisque l'INSEE met à jour une fois par nuit.
3. **Rafraîchissement hebdomadaire** du référentiel minimal par requêtes groupées (1 000 SIREN par appel), qui relit notamment le statut de diffusion.
4. **Rattachement** : existence des SIREN vérifiée via l'API Sirene ; candidats par nom via l'API Recherche d'entreprises ; score calculé localement.
5. **Recherche** : locale sur les bénéficiaires ; repli sur l'API Recherche d'entreprises pour les entreprises sans flux.
6. Nouveau module `referentiel-client` : clients HTTP, respect des quotas, cache, disjoncteur. Il n'écrit jamais en base.
7. La nomenclature NAF (`core.naf`, ~730 codes) est conservée localement : c'est une donnée de référence statique, pas une donnée d'entreprise.

## Conséquences

- Volumétrie et coût d'ingestion fortement réduits : quelques centaines de milliers de lignes au lieu de ~30 M.
- **Dépendance à deux API externes.** Si elles sont indisponibles, l'identité est dégradée et le repli de recherche absent ; **les montants restent toujours servis**.
- **Risque de quota** (robots d'indexation sur les fiches SSR) : cache, disjoncteur, mode dégradé ; demande de quota supérieur à l'INSEE si besoin (`SPEC.md` §14).
- **Idempotence** : les réponses d'API évoluent dans le temps, donc un rattachement établi est réutilisé lors d'un rejeu et n'est recalculé que sur demande.
- Tests : API simulées par WireMock (quota dépassé, erreurs, latence) ; aucun appel réel en CI.
- Secret `INSEE_API_KEY` en variable d'environnement uniquement.
- Point ouvert : depuis l'environnement cloud, `recherche-entreprises.api.gouv.fr` coupe les connexions ; à résoudre au spike.
