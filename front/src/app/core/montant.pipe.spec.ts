import { MontantPipe } from './montant.pipe';

/** Espaces fines insécables et espaces insécables du format français, normalisées pour la lisibilité. */
const normaliser = (texte: string) => texte.replace(/[\u202f\u00a0]/g, ' ');

describe('MontantPipe', () => {
  const pipe = new MontantPipe();

  it('affiche un montant exact en euros entiers, au format français', () => {
    expect(normaliser(pipe.transform(45837596))).toBe('45 837 596 €');
    expect(normaliser(pipe.transform(0))).toBe('0 €');
  });

  it('affiche un montant compact pour les chiffres principaux', () => {
    expect(normaliser(pipe.transform(45837596, 'compact'))).toBe('45,8 M €');
    expect(normaliser(pipe.transform(241265104, 'compact'))).toBe('241,3 M €');
  });

  it('affiche un tiret pour un montant absent', () => {
    expect(pipe.transform(null)).toBe('—');
    expect(pipe.transform(undefined)).toBe('—');
  });
});
