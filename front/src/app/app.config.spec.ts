import { TestBed } from '@angular/core/testing';

import { API_BASE_PATH, appConfig } from './app.config';
import { BASE_PATH } from './core/api/generated';

describe('appConfig', () => {
  it("branche le client généré sur la base de l'API du contrat", () => {
    TestBed.configureTestingModule({ providers: appConfig.providers });
    expect(TestBed.inject(BASE_PATH)).toBe(API_BASE_PATH);
    expect(API_BASE_PATH).toBe('/api/v1');
  });
});
