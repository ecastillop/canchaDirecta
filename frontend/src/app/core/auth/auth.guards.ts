import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStore } from './auth.store';

export const authGuard: CanActivateFn = async (_route, state) => {
  const store = inject(AuthStore);
  const router = inject(Router);
  await store.init();
  if (store.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/auth/login'], { queryParams: { redirect: state.url } });
};

/** Exige al menos uno de los roles indicados. */
export function roleGuard(roles: string[]): CanActivateFn {
  return async (_route, state) => {
    const store = inject(AuthStore);
    const router = inject(Router);
    await store.init();
    if (!store.isAuthenticated()) {
      return router.createUrlTree(['/auth/login'], { queryParams: { redirect: state.url } });
    }
    const propios = store.roles();
    if (roles.some((rol) => propios.includes(rol))) {
      return true;
    }
    return router.createUrlTree(['/sin-acceso']);
  };
}

/** Evita que un usuario ya autenticado vuelva a las pantallas de acceso. */
export const guestGuard: CanActivateFn = async () => {
  const store = inject(AuthStore);
  const router = inject(Router);
  await store.init();
  return store.isAuthenticated() ? router.createUrlTree(['/']) : true;
};
