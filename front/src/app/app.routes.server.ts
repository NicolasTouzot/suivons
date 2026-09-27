import { RenderMode, ServerRoute } from '@angular/ssr';

// Rendu serveur à la demande (SPEC.md §7.4) : pages indexables, données à jour après chaque ingestion.
export const serverRoutes: ServerRoute[] = [
  {
    path: '**',
    renderMode: RenderMode.Server,
  },
];
