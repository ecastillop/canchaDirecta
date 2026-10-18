import { TestBed } from '@angular/core/testing';
import { ThemeService } from '../../../core/theme/theme.service';
import { CdThemeToggleComponent } from './cd-theme-toggle.component';

describe('CdThemeToggleComponent', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    TestBed.resetTestingModule();
  });

  async function crear() {
    await TestBed.configureTestingModule({ imports: [CdThemeToggleComponent] }).compileComponents();
    const fixture = TestBed.createComponent(CdThemeToggleComponent);
    fixture.detectChanges();
    const boton = (fixture.nativeElement as HTMLElement).querySelector('button') as HTMLButtonElement;
    return { fixture, boton };
  }

  it('alterna el modo oscuro y lo aplica al documento', async () => {
    const { fixture, boton } = await crear();

    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
    expect(boton.getAttribute('aria-pressed')).toBe('false');
    expect(boton.getAttribute('aria-label')).toBe('Cambiar a modo oscuro');

    boton.click();
    TestBed.tick();
    fixture.detectChanges();

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(boton.getAttribute('aria-pressed')).toBe('true');
    expect(boton.getAttribute('aria-label')).toBe('Cambiar a modo claro');
    expect(localStorage.getItem('cd.modo')).toBe('oscuro');

    boton.click();
    TestBed.tick();
    fixture.detectChanges();

    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
    expect(localStorage.getItem('cd.modo')).toBe('claro');
  });

  it('comparte la preferencia con el resto de la aplicacion', async () => {
    const { boton } = await crear();
    const theme = TestBed.inject(ThemeService);

    boton.click();
    TestBed.tick();

    expect(theme.oscuro()).toBe(true);
    expect(theme.modo()).toBe('oscuro');
  });
});
