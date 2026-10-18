import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Reserva } from '../../core/api/reservas.models';
import { CanchaDetalle } from '../../core/api/venues.models';
import { MiResumenComponent } from './mi-resumen.component';

const RESERVA: Reserva = {
  id: 1,
  canchaId: 7,
  inicio: new Date(Date.now() + 86400000).toISOString(),
  fin: new Date(Date.now() + 90000000).toISOString(),
  montoCancha: 70,
  montoDescuento: 5,
  estado: 'CONFIRMADA',
  fechaCreacion: new Date().toISOString(),
  cocheraId: null,
  montoCochera: null,
};

const CANCHA = {
  id: 7,
  nombre: 'Cancha Central',
  distrito: { id: 1, nombre: 'Lima', provincia: 'Lima', departamento: 'Lima' },
} as unknown as CanchaDetalle;

describe('MiResumenComponent', () => {
  it('muestra la proxima reserva y los avisos', async () => {
    await TestBed.configureTestingModule({
      imports: [MiResumenComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    const fixture = TestBed.createComponent(MiResumenComponent);
    fixture.detectChanges();

    const http = TestBed.inject(HttpTestingController);
    http.expectOne((req) => req.url.includes('/reservas/mias')).flush([RESERVA]);
    http.expectOne((req) => req.url.includes('/inscripciones/mias')).flush([]);
    http.expectOne((req) => req.url.includes('/reclamos/mios')).flush([]);
    http.expectOne((req) => req.url.includes('/notificaciones')).flush({
      noLeidas: 2,
      total: 1,
      notificaciones: [
        {
          id: 1,
          titulo: 'Reserva confirmada',
          mensaje: 'Tu reserva fue confirmada.',
          leida: false,
          fechaEnvio: new Date().toISOString(),
        },
      ],
    });
    http.expectOne((req) => req.url.includes('/favoritos')).flush([]);

    await new Promise((resolve) => setTimeout(resolve, 0));
    http.expectOne((req) => req.url.includes('/canchas/7')).flush(CANCHA);
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Tu próxima reserva');
    expect(texto).toContain('Cancha Central');
    expect(texto).toContain('Reserva confirmada');
    expect(texto).toContain('2');
  });
});
