import { chromium } from 'playwright-core';
import fs from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE = process.env.BASE ?? 'http://localhost:4300';
const API = process.env.API ?? 'http://localhost:8089/api/v1';
const EVID = resolve(dirname(fileURLToPath(import.meta.url)), '../../docs/evidencias/ui');
const CHROME =
  process.env.CHROME_PATH ??
  (process.platform === 'win32'
    ? 'C:/Program Files/Google/Chrome/Application/chrome.exe'
    : '/usr/bin/google-chrome');
fs.mkdirSync(EVID, { recursive: true });

const stamp = Date.now();
const user = {
  nombres: 'E2E',
  apellidos: `Prueba ${stamp}`,
  correo: `e2e.ui.${stamp}@canchadirecta.pe`,
  telefono: '999888777',
  password: 'ClaveSegura123',
};

const fallos = [];
const ok = (msg) => console.log(`[PASS] ${msg}`);
const fallo = (msg) => {
  console.log(`[FAIL] ${msg}`);
  fallos.push(msg);
};

function fechaLima(offsetDias = 0) {
  const fecha = new Date(Date.now() + offsetDias * 86400000);
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(fecha);
}

async function buscarFranja() {
  const canchas = await (await fetch(`${API}/canchas?limite=100`)).json();
  for (const cancha of canchas.slice(0, 30)) {
    for (let dia = 0; dia < 7; dia++) {
      const fecha = fechaLima(dia);
      const res = await fetch(
        `${API}/canchas/${cancha.id}/disponibilidad?fecha=${fecha}&duracionMinutos=60`,
      );
      if (!res.ok) continue;
      const data = await res.json();
      if (data.franjas?.length) {
        return { cancha, fecha, franja: data.franjas[0] };
      }
    }
  }
  return null;
}

async function crearActividadDemo() {
  const marca = Date.now();
  const organizer = {
    correo: `e2e.organizador.${marca}@canchadirecta.pe`,
    password: 'ClaveSegura123',
  };
  const registro = await fetch(`${API}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      nombres: 'Organizador',
      apellidos: `E2E ${marca}`,
      correo: organizer.correo,
      telefono: '988777666',
      password: organizer.password,
      confirmacionPassword: organizer.password,
    }),
  });
  if (!registro.ok) throw new Error(`registro organizador ${registro.status}`);
  const login = await (await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(organizer),
  })).json();
  const distritos = await (await fetch(`${API}/ubicaciones/distritos?limite=1`)).json();
  const establecimiento = await (await fetch(`${API}/establecimientos`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${login.accessToken}` },
    body: JSON.stringify({
      documentoIdentidad: String(700000000000000000n + BigInt(marca % 1000000)),
      idDistrito: distritos[0].id,
      nombre: `Complejo E2E ${marca}`,
      direccion: 'Av. Actividades 100',
      referencia: 'Ingreso principal',
    }),
  })).json();
  const inicio = new Date(Date.now() + 86400000);
  inicio.setHours(18, 0, 0, 0);
  const fin = new Date(inicio.getTime() + 2 * 3600000);
  const actividad = await (await fetch(`${API}/establecimientos/${establecimiento.id}/actividades`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${login.accessToken}` },
    body: JSON.stringify({
      nombre: `Torneo E2E ${marca}`,
      descripcion: 'Actividad creada para la prueba de extremo a extremo.',
      inicio: inicio.toISOString(),
      fin: fin.toISOString(),
      cupos: 10,
      precio: 20,
    }),
  })).json();
  return actividad;
}

const franja = await buscarFranja();
if (!franja) {
  console.error('No hay franjas disponibles en los proximos 7 dias; no se puede probar la reserva.');
  process.exit(2);
}
console.log(`Franja elegida: cancha ${franja.cancha.id} (${franja.cancha.nombre}) ${franja.fecha} ${franja.franja.inicio}`);

const browser = await chromium.launch({ executablePath: CHROME, headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'es-PE' });
const page = await context.newPage();
const errores = [];
page.on('pageerror', (e) => errores.push(`pageerror: ${e.message}`));
page.on('response', (r) => {
  const esperado = /\/propietarios\/me$/.test(r.url()) && r.status() === 404;
  if (r.status() >= 400 && !esperado) errores.push(`HTTP ${r.status()} ${r.url()}`);
});
page.on('console', (m) => {
  const texto = m.text();
  if (m.type() === 'error' && !texto.includes('favicon') && !texto.includes('Failed to load resource')) {
    errores.push(`console: ${texto}`);
  }
});

try {
  // 1. Home
  await page.goto(BASE, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: /El espacio ideal/ }).waitFor();
  ok('home renderiza');

  // 1b. Centro de ayuda publico con busqueda
  await page.goto(`${BASE}/ayuda`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: /¿En qué podemos ayudarte/ }).waitFor({ timeout: 20000 });
  await page.getByRole('searchbox', { name: 'Buscar en el centro de ayuda' }).fill('reembolso');
  await page.getByText('¿Cuándo recibo un reembolso?').click();
  await page.getByText(/reembolso simulado por el monto correspondiente/).waitFor();
  ok('centro de ayuda con busqueda y respuestas desplegables');
  await page.screenshot({ path: `${EVID}/centro-ayuda-1440.png`, fullPage: true });

  // 2. Registro por UI: parte de una ruta protegida y debe volver a ella
  await page.goto(`${BASE}/mis-reservas`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: 'Iniciar sesión' }).waitFor({ timeout: 20000 });
  await page.getByRole('link', { name: 'Regístrate' }).click();
  await page.getByRole('heading', { name: 'Crear cuenta' }).waitFor();
  await page.locator('label:has-text("Nombres") input').first().fill(user.nombres);
  await page.locator('label:has-text("Apellidos") input').first().fill(user.apellidos);
  await page.locator('label:has-text("Correo") input').first().fill(user.correo);
  await page.locator('label:has-text("Teléfono") input').first().fill(user.telefono);
  await page.locator('label:has-text("Contraseña") input').first().fill(user.password);
  await page.locator('label:has-text("Confirmar contraseña") input').first().fill(user.password);
  await page.getByRole('button', { name: 'Crear cuenta' }).click();
  await page.waitForURL(`${BASE}/mis-reservas`, { timeout: 20000 });
  await page.locator('.cuenta-side').waitFor({ timeout: 20000 });
  ok('registro por UI con auto-login y retorno a la ruta original');

  // 3. Login por UI
  await page.locator('.cuenta-side__salir').click();
  await page.waitForURL(`${BASE}/`, { timeout: 20000 });
  await page.goto(`${BASE}/auth/login`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: 'Iniciar sesión' }).waitFor();
  const pagina = (await page.locator('body').innerText()).toLowerCase();
  if (!pagina.includes('proyecto académico') && !pagina.includes('universidad')) ok('sin referencias al proyecto universitario');
  else fallo('la interfaz aun menciona el proyecto universitario');
  await page.locator('input[type="email"]').fill(user.correo);
  await page.locator('input[type="password"]').fill(user.password);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(`${BASE}/`, { timeout: 20000 });
  await page.locator('summary.cuenta-btn').waitFor({ timeout: 20000 });
  ok('login por UI y topbar con usuario');

  // 3b. Actividades: listado publico, inscripcion y mis inscripciones
  const actividadDemo = await crearActividadDemo();
  await page.goto(`${BASE}/actividades`, { waitUntil: 'networkidle' });
  await page.getByText('Próximas actividades').waitFor();
  const tarjetaActividad = page.locator('article.actividad').filter({ hasText: actividadDemo.nombre }).first();
  await tarjetaActividad.waitFor({ timeout: 20000 });
  await tarjetaActividad.getByRole('button', { name: 'Inscribirme' }).click();
  await tarjetaActividad.getByText('Inscrito').waitFor({ timeout: 20000 });
  ok('inscripcion a actividad desde el listado publico');

  await page.goto(`${BASE}/mis-inscripciones`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: 'Mis inscripciones' }).waitFor();
  await page.getByText(actividadDemo.nombre).first().waitFor({ timeout: 20000 });
  ok('la actividad aparece en mis inscripciones');
  await page.screenshot({ path: `${EVID}/mis-inscripciones-1440.png`, fullPage: true });

  // 4. Catalogo y detalle
  await page.goto(`${BASE}/canchas`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: /Tu próxima cancha/ }).waitFor();
  const tarjetas = await page.locator('article.tarjeta-cancha').count();
  if (tarjetas > 0) ok(`catalogo lista ${tarjetas} canchas`);
  else fallo('catalogo sin tarjetas');

  const resumenPagina = page.locator('.resultados__top [role="status"]');
  await resumenPagina.waitFor();
  const totalTexto = await resumenPagina.textContent();
  await page.getByRole('button', { name: 'Página 2', exact: true }).click();
  await page.locator('.resultados__top [role="status"]', { hasText: 'Mostrando 13–24' }).waitFor({ timeout: 20000 });
  ok(`paginacion del catalogo OK (${totalTexto?.trim()})`);
  await page.locator('nav.paginacion').screenshot({ path: `${EVID}/catalogo-paginacion.png` });
  await page.getByRole('button', { name: 'Página 1', exact: true }).click();
  await page.locator('.resultados__top [role="status"]', { hasText: 'Mostrando 1–12' }).waitFor();
  await page.screenshot({ path: `${EVID}/catalogo-1440.png`, fullPage: false });

  // Favoritos: guardar desde el catalogo y verlo en la pagina de favoritos
  const primeraTarjeta = page.locator('article.tarjeta-cancha').first();
  const nombreFavorito = (await primeraTarjeta.locator('h3').textContent())?.trim() ?? '';
  await primeraTarjeta.getByRole('button', { name: 'Agregar a favoritos' }).click();
  await page.goto(`${BASE}/favoritos`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: nombreFavorito }).waitFor({ timeout: 20000 });
  ok('favoritos guarda y muestra la cancha');

  await page.goto(`${BASE}/canchas/${franja.cancha.id}`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: franja.cancha.nombre }).waitFor({ timeout: 20000 });

  // Ficha del espacio: enlace desde el detalle de la cancha
  await page.locator('.enlace-espacio').click();
  await page.waitForURL(`**/establecimientos/${franja.cancha.idEstablecimiento}`, { timeout: 20000 });
  await page.getByRole('heading', { name: franja.cancha.establecimiento }).waitFor({ timeout: 20000 });
  await page.getByRole('heading', { name: 'Canchas del espacio' }).waitFor();
  const canchasEspacio = await page.locator('article.cancha').count();
  if (canchasEspacio > 0) ok(`ficha del espacio lista ${canchasEspacio} canchas`);
  else fallo('ficha del espacio sin canchas');
  await page.screenshot({ path: `${EVID}/espacio-1440.png`, fullPage: true });
  await page.goBack({ waitUntil: 'networkidle' });

  await page.getByText('Reserva tu horario').waitFor();
  await page.getByRole('heading', { name: 'Disponibilidad semanal' }).waitFor({ timeout: 20000 });
  const celdas = await page.locator('.celda--libre').count();
  if (celdas > 0) {
    await page.locator('.celda--libre').first().click();
    await page.locator('.resumen').waitFor({ timeout: 10000 });
    const reservar = page.getByRole('button', { name: 'Reservar', exact: true });
    await reservar.waitFor();
    if (await reservar.isDisabled()) throw new Error('Reservar sigue deshabilitado tras elegir una franja');
  }
  const etiquetaSemana = await page.locator('.semana__titulo').innerText();
  await page.getByRole('button', { name: 'Semana siguiente', exact: true }).click();
  await page.waitForFunction(
    (inicial) => document.querySelector('.semana__titulo')?.textContent?.trim() !== inicial,
    etiquetaSemana,
    { timeout: 20000 },
  );
  await page.getByRole('button', { name: 'Semana anterior' }).click();
  await page.locator('.semana__titulo').filter({ hasText: etiquetaSemana }).waitFor();
  ok(`detalle con calendario semanal (${etiquetaSemana}, ${celdas} franjas visibles)`);
  await page.screenshot({ path: `${EVID}/detalle-calendario-1440.png`, fullPage: true });

  // 5. Reserva + pago demo
  const reservaUrl = `${BASE}/reservar/${franja.cancha.id}?inicio=${encodeURIComponent(
    franja.franja.inicio,
  )}&fin=${encodeURIComponent(franja.franja.fin)}&precio=${franja.franja.precio}`;
  await page.goto(reservaUrl, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: 'Confirmar reserva' }).waitFor();
  await page.getByRole('button', { name: 'Confirmar reserva' }).click();
  await page.getByRole('heading', { name: 'Pago simulado' }).waitFor({ timeout: 20000 });
  ok('reserva creada, paso de pago');

  await page.getByRole('button', { name: 'Pagar ahora' }).click();
  await page.getByRole('heading', { name: 'Reserva registrada' }).waitFor({ timeout: 20000 });
  ok('pago demo registrado');
  await page.screenshot({ path: `${EVID}/reserva-pago-1440.png`, fullPage: false });

  // 6. Mis reservas
  await page.getByRole('link', { name: 'Ir a mis reservas' }).click();
  await page.getByRole('heading', { name: 'Mis reservas' }).waitFor();
  await page.getByText(franja.cancha.nombre).first().waitFor({ timeout: 20000 });
  ok('mis reservas muestra la reserva creada');
  await page.screenshot({ path: `${EVID}/mis-reservas-1440.png`, fullPage: false });

  // 6b. Dashboard del cliente
  await page.goto(`${BASE}/mi-resumen`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: /Hola,/ }).waitFor();
  await page.getByRole('heading', { name: 'Tu próxima reserva' }).waitFor();
  await page.getByText(franja.cancha.nombre).first().waitFor({ timeout: 20000 });
  ok('dashboard del cliente con próxima reserva y pendientes');
  await page.screenshot({ path: `${EVID}/mi-resumen-1440.png`, fullPage: true });

  // 7. Perfil
  await page.goto(`${BASE}/perfil`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: 'Mi perfil' }).waitFor();
  await page.getByRole('heading', { name: 'Cuenta de propietario' }).waitFor();
  ok('perfil con solicitud de propietario');

  // 8. Reclamo
  await page.goto(`${BASE}/reclamos`, { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: 'Nuevo reclamo' }).click();
  await page.locator('label:has-text("Asunto") input').fill('Prueba E2E');
  await page.locator('label:has-text("Descripción") textarea').fill('Reclamo generado por la prueba automatizada.');
  await page.getByRole('button', { name: 'Registrar reclamo' }).click();
  await page.getByText('Prueba E2E').first().waitFor({ timeout: 20000 });
  ok('reclamo creado y listado');

  // 9. Notificaciones
  await page.goto(`${BASE}/notificaciones`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: 'Notificaciones' }).waitFor();
  ok('notificaciones renderiza');

  // 10. Responsive (layout movil)
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(BASE, { waitUntil: 'networkidle' });
  await page.getByRole('button', { name: 'Abrir menú de navegación' }).click();
  await page.getByRole('navigation', { name: 'Navegación móvil' }).waitFor();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  if (overflow <= 1) ok('sin overflow horizontal a 390px');
  else fallo(`overflow horizontal de ${overflow}px a 390px`);
  await page.screenshot({ path: `${EVID}/home-390.png`, fullPage: false });
} catch (error) {
  fallo(`excepcion: ${error.message}`);
  await page.screenshot({ path: `${EVID}/fallo-e2e.png`, fullPage: false }).catch(() => {});
} finally {
  await browser.close();
}

if (errores.length) {
  console.log('Errores de consola:');
  errores.forEach((e) => console.log(`  - ${e}`));
  fallos.push(`${errores.length} error(es) de consola`);
}

console.log(fallos.length ? `\nE2E con ${fallos.length} fallo(s).` : '\nE2E OK: todos los flujos verificados.');
process.exit(fallos.length ? 1 : 0);
