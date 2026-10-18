import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-palette');
  });

  it('aplica la paleta al documento y la persiste', () => {
    const service = TestBed.inject(ThemeService);
    service.setPalette('arena');
    TestBed.tick();
    expect(document.documentElement.getAttribute('data-palette')).toBe('arena');
    expect(localStorage.getItem('cd.palette')).toBe('arena');
  });

  it('ignora valores invalidos guardados', () => {
    localStorage.setItem('cd.palette', 'inventada');
    const service = TestBed.inject(ThemeService);
    expect(service.palette()).toBe('cancha');
  });
});
