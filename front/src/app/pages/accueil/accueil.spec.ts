import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { Accueil } from './accueil';

describe('Accueil', () => {
  async function render() {
    await TestBed.configureTestingModule({ imports: [Accueil], providers: [provideRouter([])] }).compileComponents();
    const fixture = TestBed.createComponent(Accueil);
    await fixture.whenStable();
    const router = TestBed.inject(Router);
    const navigation = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    return { fixture, page: fixture.nativeElement as HTMLElement, navigation };
  }

  function saisir(page: HTMLElement, valeur: string): void {
    const champ = page.querySelector('input') as HTMLInputElement;
    champ.value = valeur;
    champ.dispatchEvent(new Event('input'));
    (page.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
  }

  it('affiche le titre principal et un champ étiqueté', async () => {
    const { page } = await render();
    expect(page.querySelector('h1')?.textContent).toContain('argent public');
    expect(page.querySelector('label[for="recherche-siren"]')?.textContent).toContain('SIREN ou SIRET');
  });

  it("ouvre la fiche de l'entreprise pour un SIREN ou un SIRET", async () => {
    const { page, navigation } = await render();
    saisir(page, '329 338 883 05392');
    expect(navigation).toHaveBeenCalledWith(['/entreprises', '329338883']);
  });

  it('signale une saisie qui n\'est pas un SIREN', async () => {
    const { fixture, page, navigation } = await render();
    saisir(page, 'COLAS');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(navigation).not.toHaveBeenCalled();
    expect(page.querySelector('[role="alert"]')?.textContent).toContain('9 chiffres');
    expect(page.querySelector('input')?.getAttribute('aria-invalid')).toBe('true');
  });
});
