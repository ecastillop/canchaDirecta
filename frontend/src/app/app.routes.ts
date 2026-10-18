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
        path: 'actividades',
        title: 'Actividades · CanchaDirecta',
        loadComponent: () =>
          import('./features/public/actividades.component').then((m) => m.ActividadesComponent),
      },
      {
        path: 'ayuda',
        title: 'Centro de ayuda · CanchaDirecta',
        loadComponent: () => import('./features/public/ayuda.component').then((m) => m.AyudaComponent),
      },
      {
        path: 'como-funciona',
        title: 'Cómo funciona · CanchaDirecta',
        loadComponent: () =>
          import('./features/public/como-funciona.component').then((m) => m.ComoFuncionaComponent),
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
        path: 'establecimientos/:id',
        title: 'Ficha del espacio — CanchaDirecta',
        loadComponent: () =>
          import('./features/catalogo/espacio-detalle.component').then((m) => m.EspacioDetalleComponent),
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
            path: 'mi-resumen',
            title: 'Mi resumen · CanchaDirecta',
            loadComponent: () =>
              import('./features/cuenta/mi-resumen.component').then((m) => m.MiResumenComponent),
          },
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
            path: 'mis-inscripciones',
            title: 'Mis inscripciones · CanchaDirecta',
            loadComponent: () =>
              import('./features/cuenta/mis-inscripciones.component').then((m) => m.MisInscripcionesComponent),
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
            path: 'metricas',
            title: 'Métricas del establecimiento · CanchaDirecta',
            loadComponent: () =>
              import('./features/panel/panel-metricas.component').then((m) => m.PanelMetricasComponent),
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
            path: 'actividades',
            title: 'Actividades del establecimiento · CanchaDirecta',
            loadComponent: () =>
              import('./features/panel/panel-actividades.component').then((m) => m.PanelActividadesComponent),
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
        path: 'recepcion',
        canActivate: [roleGuard(['OPERADOR', 'OWNER', 'PLATFORM_ADMIN'])],
        loadComponent: () =>
          import('./features/recepcion/recepcion-layout.component').then((m) => m.RecepcionLayoutComponent),
        children: [
          {
            path: '',
            title: 'Turno actual · CanchaDirecta',
            loadComponent: () =>
              import('./features/recepcion/recepcion-resumen.component').then((m) => m.RecepcionResumenComponent),
          },
          {
            path: 'agenda',
            title: 'Agenda del día · CanchaDirecta',
            loadComponent: () =>
              import('./features/recepcion/recepcion-agenda.component').then((m) => m.RecepcionAgendaComponent),
          },
          {
            path: 'reservas',
            title: 'Reservas · CanchaDirecta',
            loadComponent: () =>
              import('./features/recepcion/recepcion-reservas.component').then((m) => m.RecepcionReservasComponent),
          },
          {
            path: 'nueva',
            title: 'Reserva presencial · CanchaDirecta',
            loadComponent: () =>
              import('./features/recepcion/recepcion-nueva-reserva.component').then(
                (m) => m.RecepcionNuevaReservaComponent,
              ),
          },
          {
            path: 'cobros',
            title: 'Cobros · CanchaDirecta',
            loadComponent: () =>
              import('./features/recepcion/recepcion-cobros.component').then((m) => m.RecepcionCobrosComponent),
          },
          {
            path: 'incidencias',
            title: 'Incidencias · CanchaDirecta',
            loadComponent: () =>
              import('./features/recepcion/recepcion-incidencias.component').then(
                (m) => m.RecepcionIncidenciasComponent,
              ),
          },
        ],
      },
      {
        path: 'admin',
        canActivate: [roleGuard(['PLATFORM_ADMIN'])],
        loadComponent: () =>
          import('./features/admin/admin-layout.component').then((m) => m.AdminLayoutComponent),
        children: [
          {
            path: '',
            title: 'Visión global · CanchaDirecta',
            loadComponent: () =>
              import('./features/admin/admin-dashboard.component').then((m) => m.AdminDashboardComponent),
          },
          {
            path: 'propietarios',
            title: 'Propietarios · CanchaDirecta',
            data: { seccion: 'propietarios' },
            loadComponent: () => import('./features/admin/admin.component').then((m) => m.AdminComponent),
          },
          {
            path: 'reclamos',
            title: 'Reclamos · CanchaDirecta',
            data: { seccion: 'reclamos' },
            loadComponent: () => import('./features/admin/admin.component').then((m) => m.AdminComponent),
          },
          {
            path: 'usuarios',
            title: 'Usuarios · CanchaDirecta',
            data: { seccion: 'usuarios' },
            loadComponent: () => import('./features/admin/admin.component').then((m) => m.AdminComponent),
          },
          {
            path: 'promociones',
            title: 'Promociones · CanchaDirecta',
            data: { seccion: 'promociones' },
            loadComponent: () => import('./features/admin/admin.component').then((m) => m.AdminComponent),
          },
          {
            path: 'cocheras',
            title: 'Cocheras · CanchaDirecta',
            data: { seccion: 'cocheras' },
            loadComponent: () => import('./features/admin/admin.component').then((m) => m.AdminComponent),
          },
          {
            path: 'supervision',
            title: 'Supervisión · CanchaDirecta',
            data: { seccion: 'supervision' },
            loadComponent: () => import('./features/admin/admin.component').then((m) => m.AdminComponent),
          },
          {
            path: 'operadores',
            title: 'Operadores de recepción · CanchaDirecta',
            loadComponent: () =>
              import('./features/admin/admin-operadores.component').then((m) => m.AdminOperadoresComponent),
          },
          {
            path: 'transacciones',
            title: 'Transacciones · CanchaDirecta',
            loadComponent: () =>
              import('./features/admin/admin-transacciones.component').then((m) => m.AdminTransaccionesComponent),
          },
          {
            path: 'actividades',
            title: 'Actividades · CanchaDirecta',
            loadComponent: () =>
              import('./features/admin/admin-actividades.component').then((m) => m.AdminActividadesComponent),
          },
          {
            path: 'reportes',
            title: 'Reportes · CanchaDirecta',
            loadComponent: () =>
              import('./features/admin/admin-reportes.component').then((m) => m.AdminReportesComponent),
          },
          {
            path: 'auditoria',
            title: 'Auditoría · CanchaDirecta',
            data: { seccion: 'auditoria' },
            loadComponent: () => import('./features/admin/admin.component').then((m) => m.AdminComponent),
          },
          {
            path: 'configuracion',
            title: 'Configuración · CanchaDirecta',
            loadComponent: () =>
              import('./features/admin/admin-configuracion.component').then((m) => m.AdminConfiguracionComponent),
          },
        ],
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
