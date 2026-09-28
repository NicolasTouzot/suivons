import { defineConfig, devices } from '@playwright/test';

// Parcours de bout en bout sur la stack complète (SPEC.md §10) : base migrée et chargée avec
// fixtures/demo/parcours.sql, API et front SSR démarrés (docker compose --profile stack up).
// PARCOURS_URL : adresse du front (par défaut celle du docker-compose).
const executablePath = process.env['PLAYWRIGHT_CHROMIUM_PATH'] || undefined;

export default defineConfig({
  testDir: './e2e-parcours',
  forbidOnly: !!process.env['CI'],
  retries: 0,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never', outputFolder: 'playwright-report-parcours' }]] : 'list',
  use: {
    baseURL: process.env['PARCOURS_URL'] ?? 'http://localhost:4000',
    locale: 'fr-FR',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'], launchOptions: { executablePath } } },
    { name: 'mobile', use: { ...devices['Pixel 7'], viewport: { width: 375, height: 812 }, launchOptions: { executablePath } } },
  ],
});
