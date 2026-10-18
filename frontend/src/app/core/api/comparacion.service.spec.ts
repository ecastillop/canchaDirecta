import { TestBed } from '@angular/core/testing';
import { ComparacionService } from './comparacion.service';

describe('ComparacionService', () => {
  let service: ComparacionService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(ComparacionService);
  });

  it('agrega, quita y limita a 4 canchas', () => {
    [1, 2, 3, 4, 5].forEach((id) => service.toggle(id));
    expect(service.ids()).toEqual([1, 2, 3, 4]);
    expect(service.cantidad()).toBe(4);
    service.toggle(2);
    expect(service.tiene(2)).toBe(false);
    service.limpiar();
    expect(service.cantidad()).toBe(0);
  });

  it('requiere al menos 2 para comparar', () => {
    service.toggle(1);
    expect(service.puedeComparar()).toBe(false);
    service.toggle(2);
    expect(service.puedeComparar()).toBe(true);
  });

  it('persiste en sessionStorage', () => {
    service.toggle(7);
    expect(sessionStorage.getItem('cd.comparar')).toBe('[7]');
  });
});
