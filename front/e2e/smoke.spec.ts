import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

const WCAG_AA = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

test.describe('fumée', () => {
  test('la page est rendue côté serveur', async ({ request }) => {
    const response = await request.get('/');
    expect(response.status()).toBe(200);
    const html = await response.text();
    expect(html).toContain('<html lang="fr"');
    expect(html).toContain('argent public une entreprise a-t-elle reçu');
  });

  test("l'accueil s'affiche en français", async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveTitle('Suivre Notre Argent');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('argent public');
    await expect(page.getByRole('main')).toBeVisible();
  });

  for (const colorScheme of ['light', 'dark'] as const) {
    test(`aucune violation d'accessibilité WCAG AA (thème ${colorScheme})`, async ({ page }) => {
      await page.emulateMedia({ colorScheme });
      await page.goto('/');
      const results = await new AxeBuilder({ page }).withTags(WCAG_AA).analyze();
      expect(results.violations).toEqual([]);
    });
  }
});
