import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Actividad } from '../../core/api/actividades.models';
import { ActividadesComponent } from './actividades.component';

const ACTIVIDAD: Actividad = {
  id: 3,
  idEstablecimiento: 8,
  establecimiento: 'Complejo Norte',
  distrito: 'Lima',
  nombre: 'Torneo relámpago',
  descripcion: 'Fútbol 7',
  inicio: '2026-11-01T18:00:00Z',
  fin: '2026-11-01T20:00:00Z',
  cupos: 10,
  inscritos: 4,
  cuposDisponibles: 6,
  precio: 20,
  estado: 'ACTIVA',
};

describe('ActividadesComponent', () => {
  it('lista las actividades publicas con sus cupos', async () => {
    await TestBed.configureTestingModule({
      imports: [ActividadesComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    const fixture = TestBed.createComponent(ActividadesComponent);
    fixture.detectChanges();

    const http = TestBed.inject(HttpTestingController);
    http.expectOne((req) => req.url.endsWith('/actividades')).flush([ACTIVIDAD]);
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Torneo relámpago');
    expect(texto).toContain('6 cupos libres');
    expect(texto).toContain('Complejo Norte');
  });
});
