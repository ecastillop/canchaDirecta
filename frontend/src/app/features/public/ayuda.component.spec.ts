import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AyudaCategoria } from '../../core/api/ayuda.models';
import { AyudaComponent } from './ayuda.component';

const CATEGORIAS: AyudaCategoria[] = [
  {
    id: 1,
    nombre: 'Reservas',
    slug: 'reservas',
    articulos: [
      { id: 1, idCategoria: 1, pregunta: '¿Cómo reservo?', respuesta: 'Elige una franja disponible.' },
    ],
  },
  {
    id: 2,
    nombre: 'Pagos y reembolsos',
    slug: 'pagos',
    articulos: [
      {
        id: 2,
        idCategoria: 2,
        pregunta: '¿Cuándo recibo un reembolso?',
        respuesta: 'Se genera un reembolso simulado.',
      },
    ],
  },
];

describe('AyudaComponent', () => {
  it('muestra las preguntas y filtra por texto', async () => {
    await TestBed.configureTestingModule({
      imports: [AyudaComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    const fixture = TestBed.createComponent(AyudaComponent);
    fixture.detectChanges();

    const http = TestBed.inject(HttpTestingController);
    http.expectOne((req) => req.url.endsWith('/ayuda')).flush(CATEGORIAS);
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('¿Cómo reservo?');
    expect(root.textContent).toContain('¿Cuándo recibo un reembolso?');

    const input = root.querySelector('input[type="search"]') as HTMLInputElement;
    input.value = 'reembolso';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(root.textContent).not.toContain('¿Cómo reservo?');
    expect(root.textContent).toContain('¿Cuándo recibo un reembolso?');
  });
});
