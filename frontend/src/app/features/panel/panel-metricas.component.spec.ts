import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MetricasEstablecimiento } from '../../core/api/metricas.models';
import { PanelMetricasComponent } from './panel-metricas.component';

const METRICAS: MetricasEstablecimiento = {
  idEstablecimiento: 5,
  dias: 30,
  desde: '2026-10-01T05:00:00Z',
  hasta: '2026-10-31T05:00:00Z',
  resumen: {
    totalReservas: 12,
    confirmadas: 8,
    pendientes: 2,
    canceladas: 2,
    ingresos: 560,
    horasReservadas: 12,
    ocupacionPorcentaje: 33.3,
    canchasActivas: 1,
  },
  serie: [{ fecha: '2026-10-18', reservas: 3, ingresos: 210 }],
  canchas: [{ idCancha: 9, nombre: 'Cancha Central', reservas: 12, ingresos: 560 }],
  proximas: [
    {
      idReserva: 1,
      cliente: 'Ana Torres',
      cancha: 'Cancha Central',
      inicio: '2026-10-19T18:00:00Z',
      fin: '2026-10-19T19:00:00Z',
      estado: 'CONFIRMADA',
      monto: 70,
    },
  ],
};

describe('PanelMetricasComponent', () => {
  afterEach(() => localStorage.removeItem('cd.establecimiento'));

  it('muestra los KPIs y las proximas reservas', async () => {
    localStorage.setItem('cd.establecimiento', '5');
    await TestBed.configureTestingModule({
      imports: [PanelMetricasComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    const fixture = TestBed.createComponent(PanelMetricasComponent);
    fixture.detectChanges();

    const http = TestBed.inject(HttpTestingController);
    http.expectOne((req) => req.url.endsWith('/establecimientos/5/metricas')).flush(METRICAS);
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Reservas recibidas');
    expect(texto).toContain('33.3%');
    expect(texto).toContain('Cancha Central');
    expect(texto).toContain('Ana Torres');
  });
});
