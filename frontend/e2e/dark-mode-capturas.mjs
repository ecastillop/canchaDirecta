// Verifica el modo claro/oscuro: aplica el modo, recorre las rutas clave y guarda
// capturas en docs/evidencias/ui. Uso:
//   $env:BASE='http://localhost:8083'; $env:ADMIN_CORREO=...; $env:ADMIN_PASSWORD=...; node e2e/dark-mode-capturas.mjs
//   $env:MODO='claro'  (por defecto 'oscuro')
import { chromium } from 'playwright-core';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE = process.env.BASE ?? 'http://localhost:8083';
const MODO = process.env.MODO === 'claro' ? 'claro' : 'oscuro';
const EVID = resolve(dirname(fileURLToPath(import.meta.url)), '../../docs/evidencias/ui');
const CHROME =
  process.env.CHROME_PATH ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe';

const browser = await chromium.launch({ executablePath: CHROME });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

await page.goto(`${BASE}/auth/login`, { waitUntil: 'networkidle' });
await page.locator('input[type="email"]').fill(process.env.ADMIN_CORREO);
await page.locator('input[type="password"]').fill(process.env.ADMIN_PASSWORD);
await page.getByRole('button', { name: 'Ingresar' }).click();
await page.waitForURL(`${BASE}/`, { timeout: 20000 });

// Aplica el modo de forma persistente (es lo mismo que hace el toggle de la topbar).
await page.evaluate((modo) => localStorage.setItem('cd.modo', modo), MODO);

const rutas = [
  ['/', 'home'],
  ['/panel/metricas', 'panel-metricas'],
  ['/admin', 'admin-dashboard'],
  ['/recepcion', 'recepcion'],
  ['/mi-resumen', 'mi-resumen'],
];

const esperado = MODO === 'oscuro' ? 'dark' : null;
const fallos = [];
for (const [ruta, nombre] of rutas) {
  await page.goto(`${BASE}${ruta}`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(900);
  const estado = await page.evaluate(() => ({
    theme: document.documentElement.getAttribute('data-theme'),
    fondo: getComputedStyle(document.body).backgroundColor,
    texto: getComputedStyle(document.body).color,
  }));
  const ok = estado.theme === esperado;
  console.log(
    `${ok ? '[PASS]' : '[FAIL]'} ${nombre}: data-theme=${estado.theme} fondo=${estado.fondo} texto=${estado.texto}`,
  );
  if (!ok) fallos.push(nombre);
  await page.screenshot({ path: `${EVID}/${MODO}-${nombre}-1440.png` });
}

// El modo elegido debe sobrevivir a una recarga.
await page.goto(`${BASE}/`, { waitUntil: 'networkidle' });
const themeTrasRecarga = await page.evaluate(() =>
  document.documentElement.getAttribute('data-theme'),
);
if (themeTrasRecarga === esperado) console.log('[PASS] el modo persiste al recargar');
else {
  console.log(`[FAIL] el modo no persiste (data-theme=${themeTrasRecarga})`);
  fallos.push('persistencia');
}

await browser.close();
console.log(fallos.length === 0 ? `MODO ${MODO.toUpperCase()} OK` : `MODO ${MODO}: fallos en ${fallos.join(', ')}`);
process.exit(fallos.length === 0 ? 0 : 1);
