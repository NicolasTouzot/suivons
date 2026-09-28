import { lireSiren } from './libelles';

describe('lireSiren', () => {
  it('accepte un SIREN ou un SIRET, avec ou sans espaces', () => {
    expect(lireSiren('329338883')).toBe('329338883');
    expect(lireSiren('329 338 883')).toBe('329338883');
    expect(lireSiren('329 338 883 05392')).toBe('329338883');
  });

  it("refuse ce qui n'est ni un SIREN ni un SIRET", () => {
    expect(lireSiren('')).toBeNull();
    expect(lireSiren('COLAS')).toBeNull();
    expect(lireSiren('3293388')).toBeNull();
  });
});
