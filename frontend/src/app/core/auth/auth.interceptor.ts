import {
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { Injector, inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthStore } from './auth.store';

const RUTAS_SIN_REFRESH = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout', '/auth/password-reset'];

function esRutaDeAutenticacion(url: string): boolean {
  return RUTAS_SIN_REFRESH.some((ruta) => url.includes(ruta));
}

function conToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  if (!token || req.headers.has('Authorization')) {
    return req;
  }
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

/**
 * Adjunta el access token y, ante un 401, refresca UNA vez y reintenta la
 * peticion original. Si el refresh falla, limpia la sesion y manda a login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const injector = inject(Injector);

  if (esRutaDeAutenticacion(req.url)) {
    return next(req);
  }

  const store = injector.get(AuthStore);
  return next(conToken(req, store.accessToken)).pipe(
    catchError((error: unknown) => {
      const es401 = error instanceof HttpErrorResponse && error.status === 401;
      if (!es401) {
        return throwError(() => error);
      }
      return from(store.refreshSession()).pipe(
        switchMap((ok) => {
          if (!ok) {
            redirigirALogin(injector);
            return throwError(() => error);
          }
          return next(conToken(req, store.accessToken));
        }),
        catchError((retryError: unknown) => {
          redirigirALogin(injector);
          return throwError(() => retryError);
        }),
      );
    }),
  );
};

function redirigirALogin(injector: Injector): void {
  const store = injector.get(AuthStore);
  const router = injector.get(Router);
  store.clearSession();
  const actual = router.url;
  void router.navigate(['/auth/login'], {
    queryParams: actual.startsWith('/auth') ? {} : { redirect: actual },
  });
}
