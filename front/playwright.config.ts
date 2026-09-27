import { defineConfig, devices } from '@playwright/test';

// Tests de bout en bout (SPEC.md §10). Lot 0 : fumée sur le front SSR seul.
// PLAYWRIGHT_CHROMIUM_PATH : navigateur déjà installé (sinon `npx playwright install chromium`).
const port = Number(process.env['E2E_PORT'] ?? 4300);
const executablePath = process.env['PLAYWRIGHT_CHROMIUM_PATH'] || undefined;

export default defineConfig({
  testDir: './e2e',
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://localhost:${port}`,
    locale: 'fr-FR',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'], launchOptions: { executablePath } } },
    { name: 'mobile', use: { ...devices['Pixel 7'], viewport: { width: 375, height: 812 }, launchOptions: { executablePath } } },
  ],
  webServer: {
    command: `npm run build && node dist/front/server/server.mjs`,
    // NG_ALLOWED_HOSTS : hôtes acceptés par le serveur SSR (protection SSRF d'Angular), à fixer au déploiement
    env: { PORT: String(port), NG_ALLOWED_HOSTS: 'localhost' },
    url: `http://localhost:${port}`,
    reuseExistingServer: !process.env['CI'],
    timeout: 180_000,
  },
});
