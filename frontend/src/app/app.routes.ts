import { Routes } from '@angular/router';
import { guestGuard, authGuard, roleGuard } from './core/auth/auth.guards';
import { CuentaLayoutComponent } from './features/cuenta/cuenta-layout.component';
import { ShellComponent } from './shared/layout/shell.component';

export const routes: Routes = [
  {
    path: 'auth',
    loadComponent: () => import('./features/auth/auth-layout.component').then((m) => m.AuthLayoutComponent),
    canActivate: [guestGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'login' },
      {
        path: 'login',
        title: 'Iniciar sesion · CanchaDirecta',
        loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
      },
      {
        path: 'registro',
        title: 'Crear cuenta · CanchaDirecta',
        loadComponent: () => import('./features/auth/registro.component').then((m) => m.RegistroComponent),
      },
      {
        path: 'verificar',
        title: 'Verificar correo · CanchaDirecta',
        loadComponent: () => import('./features/auth/verificar.component').then((m) => m.VerificarComponent),
      },
      {
        path: 'recuperar',
        title: 'Recuperar contrasena · CanchaDirecta',
        loadComponent: () => import('./features/auth/recuperar.component').then((m) => m.RecuperarComponent),
      },
    ],
  },
  {
    path: '',
    component: ShellComponent,
    children: [
      {
        path: '',
        title: 'CanchaDirecta · Reserva canchas en Lima',
        loadComponent: () => import('./features/public/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'canchas',
        title: 'Canchas · CanchaDirecta',
        loadComponent: () => import('./features/catalogo/catalogo.component').then((m) => m.CatalogoComponent),
      },
      {
        path: 'canchas/comparar',
        title: 'Comparar canchas · CanchaDirecta',
        loadComponent: () => import('./features/catalogo/comparar.component').then((m) => m.CompararComponent),
      },
      {
        path: 'canchas/:id',
        title: 'Detalle de cancha · CanchaDirecta',
        loadComponent: () =>
          import('./features/catalogo/cancha-detalle.component').then((m) => m.CanchaDetalleComponent),
      },
      {
        path: 'reservar/:canchaId',
        title: 'Confirmar reserva · CanchaDirecta',
        canActivate: [authGuard],
        loadComponent: () => import('./features/reservas/reservar.component').then((m) => m.ReservarComponent),
      },
      {
        path: '',
        component: CuentaLayoutComponent,
        canActivate: [authGuard],
        children: [
          {
            path: 'mis-reservas',
            title: 'Mis reservas · CanchaDirecta',
            loadComponent: () =>
              import('./features/reservas/mis-reservas.component').then((m) => m.MisReservasComponent),
          },
          {
            path: 'mis-reservas/:id',
            title: 'Detalle de reserva · CanchaDirecta',
            loadComponent: () =>
              import('./features/reservas/reserva-detalle.component').then((m) => m.ReservaDetalleComponent),
          },
          {
            path: 'perfil',
            title: 'Mi perfil · CanchaDirecta',
            loadComponent: () => import('./features/cuenta/perfil.component').then((m) => m.PerfilComponent),
          },
          {
            path: 'notificaciones',
            title: 'Notificaciones · CanchaDirecta',
            loadComponent: () =>
              import('./features/cuenta/notificaciones.component').then((m) => m.NotificacionesComponent),
          },
          {
            path: 'favoritos',
            title: 'Favoritos · CanchaDirecta',
            loadComponent: () => import('./features/cuenta/favoritos.component').then((m) => m.FavoritosComponent),
          },
          {
            path: 'reclamos',
            title: 'Reclamos · CanchaDirecta',
            loadComponent: () => import('./features/cuenta/reclamos.component').then((m) => m.ReclamosComponent),
          },
          {
            path: 'reclamos/:id',
            title: 'Detalle de reclamo · CanchaDirecta',
            loadComponent: () =>
              import('./features/cuenta/reclamo-detalle.component').then((m) => m.ReclamoDetalleComponent),
          },
        ],
      },
      {
        path: 'panel',
        canActivate: [roleGuard(['OWNER', 'PLATFORM_ADMIN'])],
        loadComponent: () => import('./features/panel/panel-layout.component').then((m) => m.PanelLayoutComponent),
        children: [
          {
            path: '',
            title: 'Panel del propietario · CanchaDirecta',
            loadComponent: () =>
              import('./features/panel/panel-resumen.component').then((m) => m.PanelResumenComponent),
          },
          {
            path: 'reservas',
            title: 'Reservas recibidas · CanchaDirecta',
            loadComponent: () =>
              import('./features/panel/panel-reservas.component').then((m) => m.PanelReservasComponent),
          },
          {
            path: 'agenda',
            title: 'Agenda · CanchaDirecta',
            loadComponent: () =>
              import('./features/panel/panel-agenda.component').then((m) => m.PanelAgendaComponent),
          },
          {
            path: 'promociones',
            title: 'Promociones · CanchaDirecta',
            loadComponent: () =>
              import('./features/panel/panel-promociones.component').then((m) => m.PanelPromocionesComponent),
          },
          {
            path: 'liquidaciones',
            title: 'Liquidaciones · CanchaDirecta',
            loadComponent: () =>
              import('./features/panel/panel-liquidaciones.component').then((m) => m.PanelLiquidacionesComponent),
          },
        ],
      },
      {
        path: 'admin',
        title: 'Administracion · CanchaDirecta',
        canActivate: [roleGuard(['PLATFORM_ADMIN'])],
        loadComponent: () => import('./features/admin/admin.component').then((m) => m.AdminComponent),
      },
      {
        path: 'sin-acceso',
        title: 'Sin acceso · CanchaDirecta',
        loadComponent: () => import('./features/public/sin-acceso.component').then((m) => m.SinAccesoComponent),
      },
    ],
  },
  {
    path: '**',
    title: 'Pagina no encontrada · CanchaDirecta',
    loadComponent: () => import('./features/public/no-encontrado.component').then((m) => m.NoEncontradoComponent),
  },
];
