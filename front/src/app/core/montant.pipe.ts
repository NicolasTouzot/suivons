import { Pipe, PipeTransform } from '@angular/core';

const EXACT = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });
const COMPACT = new Intl.NumberFormat('fr-FR', {
  style: 'currency',
  currency: 'EUR',
  notation: 'compact',
  maximumFractionDigits: 1,
});

/**
 * Seul formatage des montants de l'interface (CLAUDE.md, SPEC.md §9.2) : euros entiers, `exact` par défaut,
 * `compact` pour les chiffres principaux (« 45,8 M€ »). Un montant absent s'affiche « — ».
 */
@Pipe({ name: 'montant' })
export class MontantPipe implements PipeTransform {
  transform(valeur: number | null | undefined, format: 'exact' | 'compact' = 'exact'): string {
    if (valeur === null || valeur === undefined) {
      return '—';
    }
    return (format === 'compact' ? COMPACT : EXACT).format(valeur);
  }
}
