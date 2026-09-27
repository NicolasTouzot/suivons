import { provideHttpClient, withFetch } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';

import { provideApi } from './core/api/generated';
import { routes } from './app.routes';

/** Base de l'API : serveur déclaré dans contract/openapi.yaml. */
export const API_BASE_PATH = '/api/v1';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideClientHydration(withEventReplay()),
    provideHttpClient(withFetch()),
    provideApi(API_BASE_PATH),
  ],
};
