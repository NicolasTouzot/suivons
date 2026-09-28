import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';

describe('App', () => {
  async function render(): Promise<HTMLElement> {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('affiche la marque, qui ramène à l\'accueil', async () => {
    const page = await render();
    expect(page.querySelector('header a[href="/"]')?.textContent).toContain('Suivre Notre Argent');
  });

  it('expose les repères de navigation accessibles', async () => {
    const page = await render();
    expect(page.querySelector('header')).not.toBeNull();
    expect(page.querySelector('main#contenu')).not.toBeNull();
    expect(page.querySelector('footer')).not.toBeNull();
    expect(page.querySelector('a[href="#contenu"]')?.textContent).toContain('Aller au contenu');
  });

  it("n'emploie que des termes neutres", async () => {
    const texte = (await render()).textContent?.toLowerCase() ?? '';
    for (const interdit of ['fraude', 'corruption', 'suspect', 'scandale']) {
      expect(texte).not.toContain(interdit);
    }
  });
});
