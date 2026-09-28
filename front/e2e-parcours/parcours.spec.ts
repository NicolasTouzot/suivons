import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

// Données : fixtures/demo/parcours.sql (extrait réel des flux DECP de juin 2026).
const WCAG_AA = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];
const normaliser = (texte: string | null) => (texte ?? '').replace(/[\u202f\u00a0]/g, ' ');

test.describe('parcours : rechercher une entreprise, voir l\'argent reçu, ouvrir la source', () => {
  test('de la recherche par SIRET à la source officielle', async ({ page }) => {
    await page.goto('/');
    await page.getByLabel('SIREN ou SIRET').fill('329 338 883 00989');
    await page.getByRole('button', { name: 'Rechercher' }).click();

    await expect(page).toHaveURL(/\/entreprises\/329338883$/);
    await expect(page.getByRole('heading', { level: 1 })).toContainText('COLAS FRANCE');
    await expect(page.getByText('Construction de routes et autoroutes')).toBeVisible();
    const montant = page.getByRole('region', { name: 'Argent public tracé' });
    await expect(montant).toContainText('25 flux en 2026');

    const tableau = page.getByRole('table');
    await expect(tableau.getByRole('row')).toHaveCount(21);
    await expect(page.getByText(/^Page 1 sur 2$/)).toBeVisible();
    const source = tableau.getByRole('link', { name: /DECP/ }).first();
    await expect(source).toHaveAttribute('href', /^https:\/\/data\.economie\.gouv\.fr\/explore\/dataset\/decp-2022-marches-valides\//);
    await expect(source).toHaveAttribute('target', '_blank');

    await page.getByRole('button', { name: 'Suivante' }).click();
    await expect(page.getByText(/^Page 2 sur 2$/)).toBeVisible();
    await expect(tableau.getByRole('row')).toHaveCount(6);
  });

  test('la fiche ne déborde pas horizontalement (le tableau défile seul)', async ({ page }) => {
    await page.goto('/entreprises/329338883');
    await expect(page.getByRole('table')).toBeVisible();
    const largeurs = await page.evaluate(() => [document.documentElement.scrollWidth, window.innerWidth]);
    expect(largeurs[0]).toBeLessThanOrEqual(largeurs[1]);
  });

  test('un montant hors des bornes plausibles est affiché mais exclu des totaux', async ({ page }) => {
    await page.goto('/entreprises/408537249');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('MEDLINE INTERNATIONAL FRANCE');
    await expect(page.getByRole('region', { name: 'Argent public tracé' })).toContainText('exclus des totaux');
    await expect(page.getByText('Montant hors des bornes plausibles, exclu des totaux').first()).toBeVisible();
  });

  test("le nom d'un entrepreneur individuel n'est jamais affiché", async ({ page }) => {
    await page.goto('/entreprises/900000001');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Entreprise non nommée');
    const montant = page.getByRole('region', { name: 'Argent public tracé' });
    expect(normaliser(await montant.textContent())).toContain('42 000 €');
  });

  test('une entreprise sans flux tracé affiche un message explicite', async ({ page }) => {
    await page.goto('/entreprises/552032534');
    await expect(page.getByRole('status')).toContainText('Aucun flux tracé pour ce SIREN');
  });

  for (const colorScheme of ['light', 'dark'] as const) {
    test(`fiche sans violation d'accessibilité WCAG AA (thème ${colorScheme})`, async ({ page }) => {
      await page.emulateMedia({ colorScheme });
      await page.goto('/entreprises/329338883');
      await expect(page.getByRole('table')).toBeVisible();
      const resultats = await new AxeBuilder({ page }).withTags(WCAG_AA).analyze();
      expect(resultats.violations).toEqual([]);
    });
  }
});
