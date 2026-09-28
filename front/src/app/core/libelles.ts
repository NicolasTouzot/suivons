import { Canal, NatureMontant } from './api/generated';

/** Libellés neutres de l'interface (SPEC.md §9.5). */
export const LIBELLES_CANAL: Record<Canal, string> = {
  [Canal.Marche]: 'Commande publique',
  [Canal.AideEtat]: "Aides d'État",
  [Canal.FondsUe]: 'Fonds européens',
};

export const LIBELLES_NATURE: Record<NatureMontant, string> = {
  [NatureMontant.Ferme]: 'Montant attribué',
  [NatureMontant.Plafond]: 'Montant maximal possible',
  [NatureMontant.Partage]: 'Montant partagé entre co-titulaires',
  [NatureMontant.Inconnu]: 'Montant non publié',
};

/** SIREN lu depuis une saisie (SIREN ou SIRET, espaces tolérés) ; vide si ce n'est ni l'un ni l'autre. */
export function lireSiren(saisie: string): string | null {
  const chiffres = saisie.replace(/[\s.-]/g, '');
  if (/^\d{9}$/.test(chiffres)) {
    return chiffres;
  }
  if (/^\d{14}$/.test(chiffres)) {
    return chiffres.slice(0, 9);
  }
  return null;
}
