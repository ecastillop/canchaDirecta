import { chromium } from 'playwright-core';
import fs from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Revision de accesibilidad por teclado y estructura.
 * Requiere el frontend en marcha y, para las areas privadas, una cuenta PLATFORM_ADMIN.
 *
 *   $env:BASE='http://localhost:8083'
 *   $env:ADMIN_CORREO='admin@canchadirecta.pe'
 *   $env:ADMIN_PASSWORD='...'
 *   npm run test:a11y
 *
 * Para auditar solo lo publico y las pantallas de acceso (que el guard de invitado
 * esconde con sesion iniciada), se ejecuta sin credenciales:
 *
 *   $env:A11Y_SIN_SESION='1'; npm run test:a11y
 */
const BASE = process.env.BASE ?? 'http://localhost:4300';
const EVID = resolve(dirname(fileURLToPath(import.meta.url)), '../../docs/evidencias/ui');
const CHROME =
  process.env.CHROME_PATH ??
  (process.platform === 'win32'
    ? 'C:/Program Files/Google/Chrome/Application/chrome.exe'
    : '/usr/bin/google-chrome');
const SIN_SESION = process.env.A11Y_SIN_SESION === '1';
if (!SIN_SESION && (!process.env.ADMIN_CORREO || !process.env.ADMIN_PASSWORD)) {
  console.error('Define ADMIN_CORREO y ADMIN_PASSWORD de una cuenta PLATFORM_ADMIN, o A11Y_SIN_SESION=1.');
  process.exit(2);
}
fs.mkdirSync(EVID, { recursive: true });

const issues = [];
const info = (m) => console.log(`[INFO] ${m}`);
const ok = (m) => console.log(`[PASS] ${m}`);
const mal = (pagina, m) => {
  console.log(`[FAIL] ${pagina}: ${m}`);
  issues.push(`${pagina}: ${m}`);
};

const browser = await chromium.launch({ executablePath: CHROME, headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const erroresConsola = [];
page.on('console', (msg) => {
  if (msg.type() === 'error') erroresConsola.push(msg.text());
});

if (!SIN_SESION) {
  await page.goto(`${BASE}/auth/login`, { waitUntil: 'networkidle' });
  await page.locator('input[formcontrolname="correo"]').fill(process.env.ADMIN_CORREO);
  await page.locator('input[formcontrolname="password"]').fill(process.env.ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL('**/', { timeout: 20000 });
}

/** Pantallas publicas y de acceso: alcanzables sin iniciar sesion. */
const rutasPublicas = [
  '/',
  '/canchas',
  '/canchas/comparar',
  '/actividades',
  '/como-funciona',
  '/ayuda',
  '/canchas/1',
  '/establecimientos/1',
  '/auth/login',
  '/auth/registro',
  '/auth/recuperar',
  '/auth/verificar',
];

const rutas = SIN_SESION
  ? rutasPublicas
  : [
      '/',
      '/canchas',
      '/actividades',
      '/como-funciona',
      '/ayuda',
      '/canchas/1',
      '/establecimientos/1',
      '/mi-resumen',
      '/mis-reservas',
      '/mis-inscripciones',
      '/favoritos',
      '/perfil',
      '/notificaciones',
      '/reclamos',
      '/panel',
      '/panel/metricas',
      '/panel/reservas',
      '/panel/agenda',
      '/panel/actividades',
      '/panel/promociones',
      '/panel/liquidaciones',
      '/admin',
      '/admin/usuarios',
      '/admin/transacciones',
      '/admin/actividades',
      '/admin/reportes',
      '/admin/configuracion',
      '/admin/operadores',
      '/recepcion',
      '/recepcion/agenda',
      '/recepcion/reservas',
      '/recepcion/nueva',
      '/recepcion/cobros',
      '/recepcion/incidencias',
    ];

for (const ruta of rutas) {
  await page.goto(`${BASE}${ruta}`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(250);
  const res = await page.evaluate(() => {
    const nombre = (el) =>
      (el.getAttribute('aria-label') || el.getAttribute('title') || el.textContent || '')
        .replace(/\s+/g, ' ')
        .trim();

    const h1 = [...document.querySelectorAll('h1')];
    const headings = [...document.querySelectorAll('h1,h2,h3,h4,h5,h6')].map((h) => ({
      nivel: +h.tagName[1],
      texto: h.textContent.replace(/\s+/g, ' ').trim().slice(0, 50),
    }));
    let salto = null;
    for (let i = 1; i < headings.length; i++) {
      if (headings[i].nivel - headings[i - 1].nivel > 1) {
        salto = `${headings[i - 1].nivel}->${headings[i].nivel} en "${headings[i].texto}"`;
        break;
      }
    }

    const sinNombre = [];
    for (const el of document.querySelectorAll('button,a[href]')) {
      if (!nombre(el)) sinNombre.push(el.outerHTML.slice(0, 90));
    }

    const sinEtiqueta = [];
    for (const el of document.querySelectorAll('input:not([type="hidden"]),select,textarea')) {
      const etiquetado =
        (el.labels && el.labels.length > 0) ||
        el.getAttribute('aria-label') ||
        el.getAttribute('aria-labelledby') ||
        el.getAttribute('placeholder');
      if (!etiquetado) sinEtiqueta.push(el.outerHTML.slice(0, 90));
    }

    const imagenesSinAlt = [...document.querySelectorAll('img:not([alt])')].map((i) =>
      i.outerHTML.slice(0, 80),
    );

    const ids = [...document.querySelectorAll('[id]')].map((e) => e.id);
    const duplicados = [...new Set(ids.filter((id, i) => ids.indexOf(id) !== i))];

    return {
      h1: h1.length,
      salto,
      sinNombre,
      sinEtiqueta,
      imagenesSinAlt,
      duplicados,
      main: !!document.querySelector('main#contenido'),
      mains: document.querySelectorAll('main').length,
      skip: !!document.querySelector('.cd-skip-link'),
      tablasSinNombre: [...document.querySelectorAll('table')].filter(
        (t) =>
          !t.querySelector('caption') &&
          !t.getAttribute('aria-label') &&
          !t.getAttribute('aria-labelledby'),
      ).length,
      dialogosSinNombre: [...document.querySelectorAll('[role="dialog"]')].filter(
        (d) => !d.getAttribute('aria-label') && !d.getAttribute('aria-labelledby'),
      ).length,
      ocultoConFoco: [...document.querySelectorAll('[aria-hidden="true"]')].some((e) =>
        e.querySelector(
          'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])',
        ),
      ),
    };
  });

  if (res.h1 !== 1) mal(ruta, `${res.h1} elementos h1 (se espera 1)`);
  if (res.salto) mal(ruta, `salto de nivel de encabezado ${res.salto}`);
  if (res.sinNombre.length) mal(ruta, `${res.sinNombre.length} boton(es)/enlace(s) sin nombre accesible`);
  if (res.sinEtiqueta.length) mal(ruta, `${res.sinEtiqueta.length} campo(s) sin etiqueta`);
  if (res.imagenesSinAlt.length) mal(ruta, `${res.imagenesSinAlt.length} imagen(es) sin alt`);
  if (res.duplicados.length) mal(ruta, `ids duplicados: ${res.duplicados.join(', ')}`);
  if (!res.main) mal(ruta, 'falta <main id="contenido">');
  if (res.mains > 1) mal(ruta, `${res.mains} elementos main (landmark duplicado)`);
  if (!res.skip) mal(ruta, 'falta enlace de salto');
  if (res.tablasSinNombre) mal(ruta, `${res.tablasSinNombre} tabla(s) sin nombre accesible`);
  if (res.dialogosSinNombre) mal(ruta, `${res.dialogosSinNombre} dialogo(s) sin nombre accesible`);
  if (res.ocultoConFoco) mal(ruta, 'hay controles enfocables dentro de aria-hidden="true"');
  if (
    res.h1 === 1 &&
    !res.salto &&
    !res.sinNombre.length &&
    !res.sinEtiqueta.length &&
    !res.imagenesSinAlt.length &&
    !res.duplicados.length &&
    res.main &&
    res.mains === 1 &&
    res.skip &&
    !res.tablasSinNombre &&
    !res.dialogosSinNombre &&
    !res.ocultoConFoco
  ) {
    ok(`${ruta}: estructura accesible`);
  }
}

await page.goto(`${BASE}/canchas`, { waitUntil: 'networkidle' });
await page.keyboard.press('Tab');
await page.screenshot({ path: resolve(EVID, 'a11y-foco-skip-link.png'), fullPage: false });
let salio = false;
for (let i = 0; i < 25; i++) {
  await page.keyboard.press('Tab');
  const activo = await page.evaluate(() => document.activeElement !== document.body);
  if (!activo) {
    salio = true;
    break;
  }
}
if (!salio) ok('25 Tabs recorren controles sin perder el foco');
else mal('/canchas', 'el foco se pierde durante el recorrido de teclado');

await page.goto(`${BASE}/canchas`, { waitUntil: 'networkidle' });
await page.keyboard.press('Tab');
const primerFoco = await page.evaluate(() => document.activeElement?.className ?? '');
if (String(primerFoco).includes('cd-skip-link')) ok('el primer Tab llega al enlace de salto');
else mal('/canchas', `el primer Tab no llega al enlace de salto (${primerFoco})`);

await page.keyboard.press('Enter');
await page.waitForTimeout(150);
const focoTrasSalto = await page.evaluate(() => document.activeElement?.id);
if (focoTrasSalto === 'contenido') ok('el enlace de salto lleva el foco a <main>');
else mal('/canchas', `el enlace de salto deja el foco en #${focoTrasSalto}`);

// Cambio de pantalla: el foco debe pasar al contenido nuevo para que el lector
// de pantalla anuncie el contexto y el teclado no quede en un enlace obsoleto.
await page.locator('nav[aria-label="Navegación principal"] a', { hasText: 'Ayuda' }).click();
await page.waitForURL('**/ayuda', { timeout: 20000 });
await page.waitForTimeout(300);
const focoNavegacion = await page.evaluate(() => ({
  id: document.activeElement?.id,
  dentro: !!document.activeElement?.closest('main'),
}));
if (focoNavegacion.id === 'contenido' || focoNavegacion.dentro) {
  ok('al cambiar de pantalla el foco pasa al contenido');
} else {
  mal('/ayuda', 'el foco se queda en la pantalla anterior tras navegar');
}

if (!SIN_SESION) {
  await page.goto(`${BASE}/admin`, { waitUntil: 'networkidle' });
  const navAdmin = page.getByRole('navigation', { name: 'Menú de administración' });
  await navAdmin.getByRole('link', { name: 'Usuarios' }).click();
  await page.locator('table.tabla tbody tr').first().waitFor({ timeout: 20000 });
  await page.locator('table.tabla tbody tr').first().getByRole('button', { name: 'Roles' }).click();
  await page.getByRole('dialog').waitFor();
  await page.screenshot({ path: resolve(EVID, 'a11y-modal-roles.png'), fullPage: false });
  await page.keyboard.press('Escape');

  await navAdmin.getByRole('link', { name: 'Auditoría' }).click();
  await page
    .getByRole('navigation', { name: 'Paginación de auditoría' })
    .waitFor({ timeout: 20000 });
  await page.screenshot({ path: resolve(EVID, 'a11y-paginacion-auditoria.png'), fullPage: false });
}

if (erroresConsola.length) {
  info(`errores de consola detectados: ${erroresConsola.length} (se espera 404 de /propietarios/me sin solicitud)`);
} else {
  ok('sin errores de consola durante el recorrido');
}

await browser.close();
if (issues.length) {
  console.log(`\nA11Y: FALLO (${issues.length})`);
  process.exit(1);
}
console.log('\nA11Y: OK');
