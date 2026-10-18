import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminReporte } from '../../core/api/admin.api';
import { AdminReportesComponent } from './admin-reportes.component';

const REPORTE: AdminReporte = {
  dias: 30,
  desde: '2026-10-01T05:00:00Z',
  hasta: '2026-10-31T05:00:00Z',
  resumen: {
    usuarios: 120,
    usuariosNuevos: 8,
    propietariosValidados: 4,
    establecimientosActivos: 6,
    reservasPeriodo: 32,
    ingresosAprobados: 1500.5,
    pagosAprobados: 20,
    reclamosAbiertos: 2,
  },
  serie: [
    { fecha: '2026-10-17', reservas: 5, usuariosNuevos: 2, ingresos: 300 },
    { fecha: '2026-10-18', reservas: 3, usuariosNuevos: 1, ingresos: 200 },
  ],
  estados: [
    { estado: 'CONFIRMADA', total: 20 },
    { estado: 'PENDIENTE', total: 12 },
  ],
  establecimientos: [
    { id: 1, nombre: 'Complejo Norte', distrito: 'Lima', reservas: 12, ingresos: 900 },
  ],
};

describe('AdminReportesComponent', () => {
  it('muestra los KPIs, estados y establecimientos top', async () => {
    await TestBed.configureTestingModule({
      imports: [AdminReportesComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    const fixture = TestBed.createComponent(AdminReportesComponent);
    fixture.detectChanges();

    const http = TestBed.inject(HttpTestingController);
    http.expectOne((req) => req.url.endsWith('/admin/reportes')).flush(REPORTE);
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Usuarios');
    expect(texto).toContain('Reservas del periodo');
    expect(texto).toContain('CONFIRMADA');
    expect(texto).toContain('Complejo Norte');
  });
});
