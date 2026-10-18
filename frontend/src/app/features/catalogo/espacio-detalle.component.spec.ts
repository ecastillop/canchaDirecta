import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FichaEspacio } from '../../core/api/venues.models';
import { EspacioDetalleComponent } from './espacio-detalle.component';

const FICHA: FichaEspacio = {
  id: 7,
  nombre: 'Complejo Norte',
  direccion: 'Av. Norte 123',
  referencia: 'Frente al parque',
  estado: 'ACTIVO',
  distrito: { id: 1, nombre: 'Lima', provincia: 'Lima', departamento: 'Lima' },
  servicios: [{ nombre: 'Vestuarios', valor: '' }],
  fotos: [],
  reputacion: { total: 0, promedio: 0 },
  precioDesde: 50,
  canchas: [
    {
      id: 11,
      nombre: 'Cancha 1',
      capacidad: 14,
      precioBase: 50,
      estado: 'ACTIVA',
      deportes: [{ id: 1, nombre: 'Fútbol' }],
      fotoPrincipal: null,
      reputacion: { total: 0, promedio: 0 },
    },
  ],
};

describe('EspacioDetalleComponent', () => {
  it('muestra la ficha del espacio y sus canchas', async () => {
    await TestBed.configureTestingModule({
      imports: [EspacioDetalleComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    const fixture = TestBed.createComponent(EspacioDetalleComponent);
    fixture.componentRef.setInput('id', '7');
    fixture.detectChanges();

    const http = TestBed.inject(HttpTestingController);
    http.expectOne((req) => req.url.endsWith('/establecimientos/7/ficha')).flush(FICHA);
    await fixture.whenStable();
    fixture.detectChanges();

    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('Complejo Norte');
    expect(texto).toContain('Cancha 1');
    expect(texto).toContain('Vestuarios');
  });
});
