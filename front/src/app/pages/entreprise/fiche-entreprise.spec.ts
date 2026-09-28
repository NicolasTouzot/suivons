import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import {
  Canal,
  EntrepriseSynthese,
  EtatEntreprise,
  NatureMontant,
  PageFlux,
  provideApi,
  QualiteMontant,
} from '../../core/api/generated';
import { FicheEntreprise } from './fiche-entreprise';

const SYNTHESE: EntrepriseSynthese = {
  siren: '329338883',
  denomination: 'COLAS FRANCE',
  nomMasque: false,
  activite: { code: '42.11Z', nomenclature: 'NAFRev2', libelle: 'Construction de routes et autoroutes' },
  commune: 'PARIS',
  departement: '75',
  etat: EtatEntreprise.Active,
  montants: { montantFerme: 45837596, montantPlafond: 241265104 },
  nombreFlux: 178,
  nombreFluxAberrants: 1,
  periode: { premiereAnnee: 2026, derniereAnnee: 2026 },
  canaux: [{ canal: Canal.Marche, nombreFlux: 178, montants: { montantFerme: 45837596, montantPlafond: 241265104 } }],
  lienAnnuaire: 'https://annuaire-entreprises.data.gouv.fr/entreprise/329338883',
  derniereMiseAJour: '2026-09-28T09:42:14Z',
};

const FLUX: PageFlux = {
  elements: [
    {
      identifiant: 'A|M1|32933888305392',
      canal: Canal.Marche,
      date: '2026-06-30',
      payeur: { identifiant: '20003986500106', nom: null },
      objet: 'FOURNITURE ENROBES A CHAUD',
      montantFerme: null,
      montantPlafond: 100000,
      nature: NatureMontant.Plafond,
      qualite: QualiteMontant.Ok,
      source: {
        code: 'DECP',
        libelle: 'Données essentielles de la commande publique',
        url: 'https://data.economie.gouv.fr/explore/dataset/decp-2022-marches-valides/table/?q=M1',
        extraitLe: '2026-09-28T09:42:14Z',
      },
    },
  ],
  page: 1,
  size: 20,
  total: 21,
  derniereMiseAJour: '2026-09-28T09:42:14Z',
};

const normaliser = (texte: string | null | undefined) => (texte ?? '').replace(/[\u202f\u00a0]/g, ' ');

describe('FicheEntreprise', () => {
  async function render(siren: string) {
    await TestBed.configureTestingModule({
      imports: [FicheEntreprise],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), provideApi('/api/v1')],
    }).compileComponents();
    const fixture = TestBed.createComponent(FicheEntreprise);
    fixture.componentRef.setInput('siren', siren);
    fixture.detectChanges();
    return { fixture, http: TestBed.inject(HttpTestingController), page: fixture.nativeElement as HTMLElement };
  }

  async function stabiliser(fixture: Awaited<ReturnType<typeof render>>['fixture']) {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('affiche l\'identité, le montant tracé et les flux avec leur source', async () => {
    const { fixture, http, page } = await render('329338883');
    http.expectOne('/api/v1/entreprises/329338883').flush(SYNTHESE);
    http.expectOne((r) => r.url === '/api/v1/entreprises/329338883/flux' && r.params.get('page') === '1').flush(FLUX);
    await stabiliser(fixture);

    expect(page.querySelector('h1')?.textContent).toContain('COLAS FRANCE');
    expect(normaliser(page.querySelector('.chiffre')?.textContent)).toContain('45,8 M €');
    expect(normaliser(page.querySelector('.montant')?.textContent)).toContain('241,3 M €');
    expect(page.querySelector('.montant')?.textContent).toContain('exclu des totaux');
    const lien = page.querySelector('td a') as HTMLAnchorElement;
    expect(lien.href).toContain('data.economie.gouv.fr');
    expect(lien.textContent).toContain('DECP');
    expect(normaliser(page.querySelector('tbody')?.textContent)).toContain('100 000 €');
    expect(page.querySelector('tbody')?.textContent).toContain('Montant maximal possible');
    expect(page.querySelector('.pagination')?.textContent).toContain('Page 1 sur 2');
    http.verify();
  });

  it("n'affiche jamais le nom d'une entreprise non nommable", async () => {
    const { fixture, http, page } = await render('900000001');
    http.expectOne('/api/v1/entreprises/900000001')
      .flush({ ...SYNTHESE, siren: '900000001', denomination: null, nomMasque: true });
    http.expectOne((r) => r.url === '/api/v1/entreprises/900000001/flux').flush({ ...FLUX, total: 1 });
    await stabiliser(fixture);

    expect(page.querySelector('h1')?.textContent).toContain('Entreprise non nommée');
    expect(page.textContent).not.toContain('COLAS FRANCE');
  });

  it('explique l\'absence de flux tracé', async () => {
    const { fixture, http, page } = await render('356000000');
    http.expectOne('/api/v1/entreprises/356000000')
      .flush({ status: 404, detail: 'Aucun flux tracé' }, { status: 404, statusText: 'Not Found' });
    http.expectOne((r) => r.url === '/api/v1/entreprises/356000000/flux')
      .flush({ status: 404 }, { status: 404, statusText: 'Not Found' });
    await stabiliser(fixture);

    expect(page.querySelector('[role="status"]')?.textContent).toContain('Aucun flux tracé');
  });

  it("n'emploie que des termes neutres", async () => {
    const { fixture, http, page } = await render('329338883');
    http.expectOne('/api/v1/entreprises/329338883').flush(SYNTHESE);
    http.expectOne((r) => r.url === '/api/v1/entreprises/329338883/flux').flush({
      ...FLUX,
      elements: [{ ...FLUX.elements[0], qualite: QualiteMontant.Aberrant }],
    });
    await stabiliser(fixture);

    const texte = page.textContent?.toLowerCase() ?? '';
    for (const interdit of ['fraude', 'corruption', 'suspect', 'scandale']) {
      expect(texte).not.toContain(interdit);
    }
  });
});
