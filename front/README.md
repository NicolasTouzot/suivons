# front

Application Angular 22 (composants standalone, signals, zoneless, rendu serveur). Conventions : [`../CLAUDE.md`](../CLAUDE.md).

Prérequis : Node 24 (`.nvmrc`), Java 25 via le wrapper Gradle (génération du client d'API).

```bash
npm ci
npm run generate:api   # client Angular généré depuis ../contract/openapi.yaml (lancé aussi avant start, build et test)
npm start              # serveur de dev
npm test               # TU (Vitest)
npm run lint           # ESLint (angular-eslint)
npm run e2e            # TS Playwright + axe-core (construit et lance le serveur SSR)
```

- Serveur SSR : `node dist/front/server/server.mjs`, avec `PORT` et `NG_ALLOWED_HOSTS` (hôtes acceptés, protection SSRF d'Angular).
- Playwright : `PLAYWRIGHT_CHROMIUM_PATH` pour utiliser un Chromium déjà installé, sinon `npx playwright install chromium`.
- Le client généré (`src/app/core/api/generated`) n'est pas versionné et ne se modifie jamais à la main.
