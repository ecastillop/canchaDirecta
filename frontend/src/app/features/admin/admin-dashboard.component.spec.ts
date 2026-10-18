import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AdminDashboardComponent } from './admin-dashboard.component';

describe('AdminDashboardComponent', () => {
  it('muestra KPIs, pendientes y últimas operaciones', async () => {
    await TestBed.configureTestingModule({
      imports: [AdminDashboardComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    const fixture = TestBed.createComponent(AdminDashboardComponent);
    fixture.detectChanges();

    const http = TestBed.inject(HttpTestingController);
    http.expectOne((req) => req.url.includes('/admin/reportes')).flush({
      dias: 30,
      desde: '2026-10-01T05:00:00Z',
      hasta: '2026-10-31T05:00:00Z',
      resumen: {
        usuarios: 120,
        usuariosNuevos: 8,
        propietariosValidados: 4,
        establecimientosActivos: 6,
        reservasPeriodo: 32,
        ingresosAprobados: 1500,
        pagosAprobados: 20,
        reclamosAbiertos: 2,
      },
      serie: [],
      estados: [],
      establecimientos: [],
    });
    http.expectOne((req) => req.url.includes('/admin/propietarios')).flush([
      {
        id: 1,
        idUsuario: 9,
        nombreCompleto: 'Dueño Pendiente',
        correo: 'pendiente@canchadirecta.pe',
        documento: '12345678',
        estadoValidacion: 'PENDIENTE',
      },
    ]);
    http.expectOne((req) => req.url.includes('/admin/reservas')).flush([
      {
        id: 10,
        cancha: 'Cancha 1',
        establecimiento: 'Complejo Norte',
        titular: 'Ana Torres',
        inicio: '2026-10-19T18:00:00Z',
        fin: '2026-10-19T19:00:00Z',
        montoCancha: 70,
        montoCochera: null,
        estado: 'CONFIRMADA',
      },
    ]);
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Visión global de la plataforma');
    expect(texto).toContain('Dueño Pendiente');
    expect(texto).toContain('Complejo Norte');
    expect(texto).toContain('Ana Torres');
  });
});
