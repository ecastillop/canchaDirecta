/**
 * Contraste de los controles de los portales en modo claro y oscuro (WCAG AA, 4.5:1).
 *
 * Recorre la cuenta del cliente, el panel del propietario, el portal de recepcion y la
 * administracion, en ambos modos, y mide el contraste real (color calculado sobre el fondo
 * efectivo) de botones, distintivos y acciones de cabecera. Falla si alguno queda por
 * debajo de 4.5:1.
 *
 * Prerrequisitos: frontend y backend en marcha con los datos demo
 * (`scripts/demo-reset.ps1 -Force`) y una cuenta PLATFORM_ADMIN para la ruta /admin.
 *
 * Uso:
 *   $env:BASE='http://localhost:8083'
 *   $env:ADMIN_CORREO='admin@canchadirecta.pe'; $env:ADMIN_PASSWORD='...'
 *   node e2e/contraste-modos.mjs
 */
import { chromium } from 'playwright-core';

const BASE = process.env.BASE ?? 'http://localhost:4300';
const CHROME =
  process.env.CHROME_PATH ??
  (process.platform === 'win32'
    ? 'C:/Program Files/Google/Chrome/Application/chrome.exe'
    : '/usr/bin/google-chrome');
const DEMO_PASSWORD = process.env.DEMO_PASSWORD ?? 'DemoCancha2026';

if (!process.env.ADMIN_CORREO || !process.env.ADMIN_PASSWORD) {
  console.error('Define ADMIN_CORREO y ADMIN_PASSWORD de una cuenta PLATFORM_ADMIN.');
  process.exit(2);
}

const casos = [
  { nombre: 'cuenta del cliente', correo: 'cliente@canchadirecta.pe', ruta: '/mi-resumen' },
  { nombre: 'panel del propietario', correo: 'propietario@canchadirecta.pe', ruta: '/panel' },
  { nombre: 'portal de recepcion', correo: 'operador@canchadirecta.pe', ruta: '/recepcion' },
  { nombre: 'administracion', correo: process.env.ADMIN_CORREO, ruta: '/admin', clave: process.env.ADMIN_PASSWORD },
];
const modos = ['claro', 'oscuro'];
const MINIMO = 4.5;

const issues = [];
const ok = (m) => console.log(`[PASS] ${m}`);
const fallo = (m) => {
  console.log(`[FAIL] ${m}`);
  issues.push(m);
};

/** Contraste de los controles visibles de la pagina (color calculado sobre el fondo efectivo). */
function medirContraste() {
  const aRgb = (texto) => (texto.match(/[\d.]+/g) ?? []).map(Number).slice(0, 3);
  const luminancia = (rgb) => {
    const [r, g, b] = rgb.map((v) => {
      const s = v / 255;
      return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  };
  const fondoEfectivo = (nodo) => {
    let actual = nodo;
    while (actual) {
      const color = getComputedStyle(actual).backgroundColor;
      const rgb = aRgb(color);
      const alpha = color.startsWith('rgba') ? Number((color.match(/[\d.]+/g) ?? [0, 0, 0, 1])[3]) : 1;
      if (rgb.length === 3 && alpha > 0.95) return rgb;
      actual = actual.parentElement;
    }
    return [255, 255, 255];
  };

  const visibles = [...document.querySelectorAll('.cd-btn, .cd-badge, .portal-actions a, .portal-actions button')];
  return visibles
    .filter((nodo) => {
      const estilo = getComputedStyle(nodo);
      const deshabilitado = nodo.disabled || nodo.getAttribute('aria-disabled') === 'true';
      return !deshabilitado && estilo.visibility !== 'hidden' && Number(estilo.opacity) > 0.9 && nodo.offsetParent !== null;
    })
    .map((nodo) => {
      const estilo = getComputedStyle(nodo);
      const frente = aRgb(estilo.color);
      const fondo = fondoEfectivo(nodo);
      const l1 = luminancia(frente);
      const l2 = luminancia(fondo);
      return {
        texto: (nodo.textContent ?? '').replace(/\s+/g, ' ').trim().slice(0, 32) || nodo.getAttribute('aria-label') || '(sin texto)',
        clase: (nodo.className || '').toString().slice(0, 40),
        ratio: Math.round(((Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05)) * 100) / 100,
        color: estilo.color,
        fondo: `rgb(${fondo.join(',')})`,
      };
    });
}

const browser = await chromium.launch({ executablePath: CHROME, headless: true });

for (const caso of casos) {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, locale: 'es-PE' });
  try {
    await page.goto(`${BASE}/auth/login`, { waitUntil: 'networkidle' });
    await page.locator('input[formcontrolname="correo"]').fill(caso.correo);
    await page.locator('input[formcontrolname="password"]').fill(caso.clave ?? DEMO_PASSWORD);
    await page.getByRole('button', { name: 'Ingresar' }).click();
    await page.waitForURL('**/', { timeout: 20000 });

    for (const modo of modos) {
      await page.evaluate((valor) => localStorage.setItem('cd.modo', valor), modo);
      await page.goto(`${BASE}${caso.ruta}`, { waitUntil: 'networkidle' });
      await page.waitForFunction(
        (valor) => (document.documentElement.getAttribute('data-theme') ?? '') === (valor === 'oscuro' ? 'dark' : ''),
        modo,
        { timeout: 20000 },
      );
      await page.waitForTimeout(300);

      const medidos = await page.evaluate(medirContraste);
      const bajos = medidos.filter((fila) => fila.ratio < MINIMO);
      if (bajos.length === 0) {
        ok(`${caso.nombre} (${modo}): controles con contraste >= ${MINIMO}:1`);
      } else {
        for (const fila of bajos) {
          fallo(
            `${caso.nombre} (${modo}): "${fila.texto}" ${fila.ratio}:1 (${fila.color} sobre ${fila.fondo}) [${fila.clase}]`,
          );
        }
      }
    }
  } catch (error) {
    fallo(`${caso.nombre}: ${error.message}`);
  } finally {
    await page.close();
  }
}

await browser.close();
if (issues.length) {
  console.log(`\nCONTRASTE: FALLO (${issues.length})`);
  process.exit(1);
}
console.log('\nCONTRASTE: OK');
