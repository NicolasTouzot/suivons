import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { join } from 'node:path';

const browserDistFolder = join(import.meta.dirname, '../browser');

const app = express();
const angularApp = new AngularNodeAppEngine();

/**
 * Relais de l'API (lecture seule) : le navigateur et le rendu serveur appellent `/api/v1` sur l'origine du site,
 * transmis à l'API Spring (`API_URL`, par défaut celle du profil de développement).
 */
const apiUrl = process.env['API_URL'] ?? 'http://localhost:8080';

app.use('/api', async (req, res) => {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.status(405).set('Allow', 'GET, HEAD').end();
    return;
  }
  try {
    const reponse = await fetch(new URL(req.originalUrl, apiUrl), {
      method: req.method,
      headers: { accept: req.get('accept') ?? 'application/json' },
    });
    res.status(reponse.status);
    for (const entete of ['content-type', 'retry-after', 'cache-control']) {
      const valeur = reponse.headers.get(entete);
      if (valeur) {
        res.set(entete, valeur);
      }
    }
    res.send(Buffer.from(await reponse.arrayBuffer()));
  } catch {
    // API injoignable : erreur RFC 9457, affichée comme une indisponibilité momentanée
    res.status(502).type('application/problem+json').send({ status: 502, title: 'Bad Gateway', detail: 'API injoignable' });
  }
});

/**
 * Serve static files from /browser
 */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
  }),
);

/**
 * Handle all other requests by rendering the Angular application.
 */
app.use((req, res, next) => {
  angularApp
    .handle(req)
    .then((response) =>
      response ? writeResponseToNodeResponse(response, res) : next(),
    )
    .catch(next);
});

/**
 * Start the server if this module is the main entry point, or it is ran via PM2.
 * The server listens on the port defined by the `PORT` environment variable, or defaults to 4000.
 */
if (isMainModule(import.meta.url) || process.env['pm_id']) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }

    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}

/**
 * Request handler used by the Angular CLI (for dev-server and during build) or Firebase Cloud Functions.
 */
export const reqHandler = createNodeRequestHandler(app);
