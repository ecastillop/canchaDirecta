import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { AuthStore } from './auth.store';
import { authGuard, roleGuard } from './auth.guards';

describe('auth guards', () => {
  let autenticado = false;
  let roles: string[] = [];

  const store = {
    init: async () => undefined,
    isAuthenticated: () => autenticado,
    roles: () => roles,
  };

  beforeEach(() => {
    autenticado = false;
    roles = [];
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthStore, useValue: store }],
    });
  });

  function ejecutar(guard: ReturnType<typeof roleGuard> | typeof authGuard) {
    return TestBed.runInInjectionContext(() => guard({} as never, { url: '/panel' } as never));
  }

  it('authGuard redirige a login con redirect cuando no hay sesion', async () => {
    const resultado = await ejecutar(authGuard);
    const router = TestBed.inject(Router);
    expect(resultado).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(resultado as UrlTree)).toBe('/auth/login?redirect=%2Fpanel');
  });

  it('authGuard permite el paso con sesion', async () => {
    autenticado = true;
    await expect(ejecutar(authGuard)).resolves.toBe(true);
  });

  it('roleGuard permite cuando tiene uno de los roles', async () => {
    autenticado = true;
    roles = ['OWNER'];
    await expect(ejecutar(roleGuard(['OWNER', 'PLATFORM_ADMIN']))).resolves.toBe(true);
  });

  it('roleGuard manda a sin-acceso si falta el rol', async () => {
    autenticado = true;
    roles = ['USUARIO'];
    const resultado = await ejecutar(roleGuard(['PLATFORM_ADMIN']));
    const router = TestBed.inject(Router);
    expect(router.serializeUrl(resultado as UrlTree)).toBe('/sin-acceso');
  });
});
