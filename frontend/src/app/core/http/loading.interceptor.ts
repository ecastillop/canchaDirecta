import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs';
import { LoadingService } from './loading.service';

/** Barra global de progreso; se omite con la cabecera `X-Silent: 1`. */
export const loadingInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.headers.has('X-Silent')) {
    return next(req.clone({ headers: req.headers.delete('X-Silent') }));
  }
  const loading = inject(LoadingService);
  loading.start();
  return next(req).pipe(finalize(() => loading.end()));
};
