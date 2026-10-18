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
const ADMIN = { correo: process.env.ADMIN_CORREO, password: process.env.ADMIN_PASSWORD };
if (!ADMIN.correo || !ADMIN.password) {
  console.error('Define ADMIN_CORREO y ADMIN_PASSWORD de una cuenta PLATFORM_ADMIN.');
  process.exit(2);
}
fs.mkdirSync(EVID, { recursive: true });

const stamp = Date.now();
const owner = {
  nombres: 'Owner',
  apellidos: `E2E ${stamp}`,
  correo: `e2e.owner.${stamp}@canchadirecta.pe`,
  password: 'ClaveSegura123',
};
const documento = `E2E${String(stamp).slice(-8)}`;
const promo = `PROMO${String(stamp).slice(-5)}`;
const fallos = [];
const ok = (m) => console.log(`[PASS] ${m}`);
const fallo = (m) => {
  console.log(`[FAIL] ${m}`);
  fallos.push(m);
};

/** Lunes de la semana siguiente en hora de Lima: la rejilla del panel empieza el lunes. */
function proximoLunesLima() {
  const lima = new Date(Date.now() - 5 * 3600 * 1000);
  const delta = (8 - lima.getUTCDay()) % 7 || 7;
  lima.setUTCDate(lima.getUTCDate() + delta);
  return lima.toISOString().slice(0, 10);
}

async function api(path, { method = 'GET', body, token } = {}) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const texto = await res.text();
  let data = null;
  try {
    data = texto ? JSON.parse(texto) : null;
  } catch {
    data = texto;
  }
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status}: ${texto.slice(0, 200)}`);
  return data;
}

// Setup: owner pendiente (la validacion se hara por la UI del admin)
const reg = await api('/auth/register', {
  method: 'POST',
  body: {
    nombres: owner.nombres,
    apellidos: owner.apellidos,
    correo: owner.correo,
    telefono: '988777666',
    password: owner.password,
    confirmacionPassword: owner.password,
  },
});
owner.id = reg.id;
console.log(`Owner registrado id=${reg.id}`);
const loginOwner = await api('/auth/login', { method: 'POST', body: { correo: owner.correo, password: owner.password } });
await api('/propietarios/solicitudes', {
  method: 'POST',
  token: loginOwner.accessToken,
  body: { documentoIdentidad: documento },
});

const browser = await chromium.launch({ executablePath: CHROME, headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'es-PE' });
const page = await context.newPage();
const errores = [];
page.on('pageerror', (e) => errores.push(`pageerror: ${e.message}`));
page.on('response', (r) => {
  const esperado =
    (/\/propietarios\/me$/.test(r.url()) && r.status() === 404) ||
    (/\/admin\/promociones$/.test(r.url()) && r.status() === 409);
  if (r.status() >= 400 && !esperado) errores.push(`HTTP ${r.status()} ${r.url()}`);
});
page.on('console', (m) => {
  const texto = m.text();
  if (m.type() === 'error' && !texto.includes('favicon') && !texto.includes('Failed to load resource')) {
    errores.push(`console: ${texto}`);
  }
});

async function login(correo, password) {
  await page.goto(`${BASE}/auth/login`, { waitUntil: 'networkidle' });
  await page.locator('input[type="email"]').fill(correo);
  await page.locator('input[type="password"]').fill(password);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await page.waitForURL(`${BASE}/`, { timeout: 20000 });
}

try {
  // 1. Admin valida al propietario desde la UI y asigna OWNER
  await login(ADMIN.correo, ADMIN.password);
  await page.goto(`${BASE}/admin/propietarios`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: 'Propietarios', exact: true }).waitFor();
  await page.getByRole('button', { name: 'PENDIENTE', exact: true }).click();
  const fila = page.locator('article.fila', { hasText: owner.correo });
  await fila.waitFor({ timeout: 20000 });
  await fila.getByRole('button', { name: 'Validar' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Validar' }).click();
  await page.getByText('Rol OWNER asignado al propietario.').waitFor({ timeout: 20000 });
  ok('admin valida propietario y asigna rol OWNER desde la UI');

  const meOwner = await api('/users/me', {
    token: (
      await api('/auth/login', { method: 'POST', body: { correo: owner.correo, password: owner.password } })
    ).accessToken,
  });
  if (meOwner.roles.includes('OWNER')) ok('backend confirma rol OWNER');
  else fallo(`OWNER ausente en roles: ${meOwner.roles.join(',')}`);

  // 2. Panel del propietario
  await page.locator('.admin-side__salir').click();
  await login(owner.correo, owner.password);
  await page.goto(`${BASE}/panel`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: 'Tu operación, bajo control.' }).waitFor({ timeout: 20000 });
  ok('panel del propietario accesible tras validacion');

  // El modo claro/oscuro se cambia desde el propio panel y persiste (ADR-0046).
  await page.getByRole('button', { name: 'Cambiar a modo oscuro' }).click();
  await page.waitForFunction(() => document.documentElement.getAttribute('data-theme') === 'dark');
  await page.screenshot({ path: `${EVID}/panel-oscuro-1440.png`, fullPage: false });
  await page.reload({ waitUntil: 'networkidle' });
  await page.getByRole('button', { name: 'Cambiar a modo claro' }).waitFor({ timeout: 20000 });
  await page.waitForFunction(() => document.documentElement.getAttribute('data-theme') === 'dark');
  ok('el modo oscuro se cambia desde el panel y persiste al recargar');
  await page.getByRole('button', { name: 'Cambiar a modo claro' }).click();
  await page.waitForFunction(() => !document.documentElement.hasAttribute('data-theme'));
  ok('el panel vuelve a modo claro');

  const nombreEst = `Establecimiento E2E ${stamp}`;
  await page.locator('label:has-text("Nombre") input').first().fill(nombreEst);
  await page.locator('label:has-text("Documento de identidad") input').fill(documento);
  await page.locator('label:has-text("Distrito") select').selectOption({ index: 1 });
  await page.locator('label:has-text("Dirección") input').fill('Av. Prueba 123');
  await page.getByRole('button', { name: 'Crear establecimiento' }).click();
  await page.getByRole('heading', { name: nombreEst, exact: true }).waitFor({ timeout: 20000 });
  ok('establecimiento creado desde el panel');

  await page.evaluate(() => localStorage.removeItem('cd.establecimiento'));
  await page.reload({ waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: nombreEst, exact: true }).waitFor({ timeout: 20000 });
  ok('el panel descubre el establecimiento sin ID manual');

  // Editar establecimiento desde el panel
  await page.getByRole('button', { name: 'Editar datos' }).click();
  const formEst = page.locator('form:has(input[formcontrolname="nombre"])').first();
  await formEst.locator('label:has-text("Dirección") input').fill('Av. Prueba 456');
  await formEst.getByRole('button', { name: 'Guardar' }).click();
  await page.locator('text=Av. Prueba 456').waitFor({ timeout: 20000 });
  ok('establecimiento editado desde el panel');

  const nombreCancha = `Cancha E2E ${String(stamp).slice(-6)}`;
  const formCancha = page.locator('section:has(h2:text("Nueva cancha"))');
  await formCancha.locator('label:has-text("Nombre") input').fill(nombreCancha);
  await formCancha.locator('label:has-text("Capacidad") input').fill('12');
  await formCancha.locator('label:has-text("Precio base") input').fill('85');
  await page.getByRole('button', { name: 'Crear cancha' }).click();
  await page.getByText(nombreCancha).first().waitFor({ timeout: 20000 });
  ok('cancha creada desde el panel');

  // Editar cancha
  const filaCancha = page.locator('li', { hasText: nombreCancha }).first();
  await filaCancha.getByRole('button', { name: 'Editar' }).click();
  const formEdicion = filaCancha.locator('form.edicion');
  await formEdicion.waitFor();
  await formEdicion.locator('label:has-text("Precio base") input').fill('90');
  await formEdicion.getByRole('button', { name: 'Guardar' }).click();
  await page.getByText(/S\/\s*90\.00\/h/).first().waitFor({ timeout: 20000 });
  ok('cancha editada desde el panel');

  // Fotos
  await filaCancha.getByRole('button', { name: 'Fotos' }).click();
  const urlFoto = 'https://picsum.photos/seed/canchadirecta-e2e/800/600';
  await filaCancha.locator('label:has-text("URL de la foto") input').fill(urlFoto);
  await filaCancha.getByRole('button', { name: 'Agregar', exact: true }).click();
  await filaCancha.locator('ul.fotos img').first().waitFor({ timeout: 20000 });
  ok('foto agregada desde el panel');

  // Vista previa en la misma pestaña y retorno a la configuracion
  const hrefPreview = await filaCancha.getByRole('link', { name: 'Vista previa' }).getAttribute('href');
  const idCancha = Number(hrefPreview?.match(/canchas\/(\d+)/)?.[1]);
  await filaCancha.getByRole('link', { name: 'Vista previa' }).click();
  await page.waitForURL(`**/canchas/${idCancha}?from=panel`, { timeout: 20000 });
  await page.getByRole('link', { name: 'Volver a configurar' }).waitFor();
  ok('vista previa abre en la misma pestaña con boton de retorno');

  const miniaturas = page.locator('.galeria__miniaturas .miniatura');
  if ((await miniaturas.count()) >= 1) ok('la galeria muestra todas las miniaturas');
  else fallo('la galeria no muestra miniaturas');
  await page.waitForFunction(
    () => {
      const img = document.querySelector('.galeria__principal img');
      return !!img && img.complete && img.naturalWidth > 0;
    },
    { timeout: 20000 },
  );
  ok('la foto principal carga correctamente');
  await page.waitForFunction(
    () =>
      [...document.querySelectorAll('.galeria__miniaturas .miniatura img')].every(
        (img) => img.complete && img.naturalWidth > 0,
      ),
    { timeout: 20000 },
  );
  ok('todas las miniaturas cargan correctamente');
  const urlMiniatura = await miniaturas.first().locator('img').getAttribute('src');
  await miniaturas.first().click();
  const urlPrincipal = await page.locator('.galeria__principal img').getAttribute('src');
  if (urlPrincipal === urlMiniatura) ok('la miniatura actualiza la foto principal');
  else fallo('la miniatura no actualiza la foto principal');
  await page.locator('.galeria__principal').click();
  await page.getByRole('dialog', { name: 'Fotos de la cancha' }).waitFor();
  await page.keyboard.press('Escape');
  ok('la galeria abre el visor y cierra con Escape');

  await page.getByRole('link', { name: 'Volver a configurar' }).click();
  await page.waitForURL(`**/panel?editarCancha=${idCancha}`, { timeout: 20000 });
  await page.locator('form:has(input[formcontrolname="precioBase"])').first().waitFor();
  ok('vuelve al panel con la cancha en edicion');

  // Cochera
  const formCochera = page.locator('section:has(h2:text("Cocheras"))');
  await formCochera.locator('label:has-text("Nombre") input').fill('Cochera E2E');
  await formCochera.locator('label:has-text("Capacidad") input').fill('8');
  await formCochera.locator('label:has-text("Tarifa") input').fill('6');
  await page.getByRole('button', { name: 'Agregar cochera' }).click();
  await page.getByText('Cochera E2E').first().waitFor({ timeout: 20000 });
  ok('cochera creada desde el panel');

  // Agenda
  const navPanel = page.getByRole('navigation', { name: 'Menú del panel del propietario' });
  await navPanel.getByRole('link', { name: 'Agenda' }).click();
  await page.getByRole('heading', { name: 'Horario semanal recurrente' }).waitFor();
  await page.getByRole('textbox', { name: 'Inicio', exact: true }).fill('18:00');
  await page.getByRole('textbox', { name: 'Fin', exact: true }).fill('23:00');
  await page.getByRole('button', { name: 'Agregar horario' }).click();
  await page.getByText('Lunes · 18:00-23:00').first().waitFor({ timeout: 20000 });
  ok('horario agregado');

  // Ocupacion semanal del propietario: la rejilla sale del horario configurado (ADR-0043).
  await page.getByRole('button', { name: 'Semana siguiente' }).click();
  await page.getByRole('rowheader', { name: '18:00' }).waitFor({ timeout: 20000 });
  await page.getByRole('cell', { name: /18:00: libre/ }).first().waitFor({ timeout: 20000 });
  await page.getByRole('cell', { name: /: cerrado/ }).first().waitFor({ timeout: 20000 });
  ok('ocupacion semanal con horas libres y dias cerrados');
  await page.getByRole('button', { name: 'Hoy', exact: true }).click();
  await page.getByRole('button', { name: 'Semana siguiente' }).waitFor();
  ok('la rejilla vuelve a la semana actual');

  // Un cliente reserva el lunes siguiente y paga: la rejilla y la bandeja deben reflejarlo (ADR-0044).
  const cliente = {
    nombres: 'Rosa',
    apellidos: `Cliente ${stamp}`,
    correo: `e2e.cliente.${stamp}@canchadirecta.pe`,
    telefono: '955333444',
    password: 'ClaveSegura123',
  };
  await api('/auth/register', {
    method: 'POST',
    body: {
      nombres: cliente.nombres,
      apellidos: cliente.apellidos,
      correo: cliente.correo,
      telefono: cliente.telefono,
      password: cliente.password,
      confirmacionPassword: cliente.password,
    },
  });
  const loginCliente = await api('/auth/login', {
    method: 'POST',
    body: { correo: cliente.correo, password: cliente.password },
  });
  const disponibilidad = await api(
    `/canchas/${idCancha}/disponibilidad?fecha=${proximoLunesLima()}&duracionMinutos=60`,
  );
  const franja = disponibilidad.franjas[0];
  if (!franja) throw new Error('La cancha no ofrece franjas el lunes siguiente.');
  const reserva = await api('/reservas', {
    method: 'POST',
    token: loginCliente.accessToken,
    body: { canchaId: idCancha, inicio: franja.inicio, fin: franja.fin },
  });
  await api(`/reservas/${reserva.id}/pago`, {
    method: 'POST',
    token: loginCliente.accessToken,
    body: { medioPago: 'TARJETA' },
  });
  ok('un cliente reserva y paga la franja del lunes siguiente');

  await page.getByRole('button', { name: 'Semana siguiente' }).click();
  await page.getByRole('cell', { name: /18:00: reservada/ }).first().waitFor({ timeout: 20000 });
  ok('la rejilla semanal marca la franja reservada');
  await page.screenshot({ path: `${EVID}/panel-agenda-1440.png`, fullPage: false });
  await page.getByRole('button', { name: 'Hoy', exact: true }).click();

  // Promociones del propietario
  await navPanel.getByRole('link', { name: 'Promociones' }).click();
  await page.getByRole('heading', { name: 'Nueva promoción' }).waitFor();
  await page.locator('label:has-text("Código") input').fill(promo);
  await page.locator('label:has-text("Descripción") input').fill('Promo E2E');
  await page.locator('label:has-text("Desde") input').fill(new Date().toISOString().slice(0, 10));
  await page.locator('label:has-text("Hasta") input').fill(new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10));
  await page.getByRole('button', { name: 'Crear promoción' }).click();
  await page.getByText('Promo E2E').first().waitFor({ timeout: 20000 });
  ok('promocion del propietario creada');
  await page.screenshot({ path: `${EVID}/panel-promociones-1440.png`, fullPage: false });

  // Actividades del establecimiento
  await navPanel.getByRole('link', { name: 'Actividades' }).click();
  await page.getByRole('heading', { name: 'Nueva actividad' }).waitFor();
  const inicioActividad = new Date(Date.now() + 4 * 86400000);
  inicioActividad.setHours(18, 0, 0, 0);
  const finActividad = new Date(inicioActividad.getTime() + 2 * 3600000);
  const local = (fecha) =>
    `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(
      fecha.getDate(),
    ).padStart(2, '0')}T${String(fecha.getHours()).padStart(2, '0')}:${String(fecha.getMinutes()).padStart(2, '0')}`;
  await page.locator('label:has-text("Nombre") input').fill('Actividad E2E');
  await page.locator('label:has-text("Descripción") textarea').fill('Torneo abierto de prueba');
  await page.locator('label:has-text("Inicio") input').fill(local(inicioActividad));
  await page.locator('label:has-text("Fin") input').fill(local(finActividad));
  await page.locator('label:has-text("Cupos") input').fill('12');
  await page.locator('label:has-text("Precio") input').fill('25');
  await page.getByRole('button', { name: 'Publicar actividad' }).click();
  await page.getByText('Actividad E2E').first().waitFor({ timeout: 20000 });
  ok('actividad publicada desde el panel del propietario');
  await page.getByRole('button', { name: 'Ver inscritos' }).first().click();
  await page.getByText('Todavía no hay inscritos confirmados.').waitFor({ timeout: 20000 });
  ok('el propietario consulta los inscritos de la actividad');
  await page.screenshot({ path: `${EVID}/panel-actividades-1440.png`, fullPage: false });

  // Metricas del establecimiento
  await navPanel.getByRole('link', { name: 'Métricas' }).click();
  await page.getByRole('heading', { name: 'Métricas del establecimiento' }).waitFor();
  await page.getByText('Reservas recibidas').waitFor();
  await Promise.all([
    page.waitForResponse((r) => r.url().includes('/metricas') && r.status() === 200),
    page.getByRole('button', { name: '7 días' }).click(),
  ]);
  await page.locator('.kpi').first().waitFor();
  await page.getByText('Ocupación estimada').waitFor();
  ok('metricas del panel con KPIs y periodo');
  await page.screenshot({ path: `${EVID}/panel-metricas-1440.png`, fullPage: false });

  // Reservas recibidas: el propietario ve al titular y el estado de su pago (ADR-0044)
  await navPanel.getByRole('link', { name: 'Reservas' }).click();
  await page.getByText(`Rosa Cliente ${stamp}`).waitFor({ timeout: 20000 });
  await page.getByText('Pagado').first().waitFor();
  await page
    .getByRole('link', { name: `Llamar a Rosa Cliente ${stamp} al ${cliente.telefono}` })
    .waitFor();
  ok('la bandeja del propietario muestra al titular y su pago');
  await page.screenshot({ path: `${EVID}/panel-reservas-1440.png`, fullPage: false });

  // 3. Administracion: tabs nuevas
  await page.locator('.panel-side__salir').click();
  await login(ADMIN.correo, ADMIN.password);
  await page.goto(`${BASE}/admin`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: 'Visión global de la plataforma' }).waitFor();
  const navAdmin = page.getByRole('navigation', { name: 'Menú de administración' });
  await page.getByText('Volumen aprobado').waitFor();
  ok('admin dashboard de visión global renderiza');
  await page.screenshot({ path: `${EVID}/admin-dashboard-1440.png`, fullPage: false });

  await navAdmin.getByRole('link', { name: 'Reportes' }).click();
  await page.getByRole('heading', { name: 'Reportes de plataforma' }).waitFor();
  await page.getByText('Ingresos aprobados').waitFor();
  ok('admin consulta reportes agregados de plataforma');
  await page.screenshot({ path: `${EVID}/admin-reportes-1440.png`, fullPage: false });

  await navAdmin.getByRole('link', { name: 'Configuración' }).click();
  await page.getByRole('heading', { name: 'Configuración de la plataforma' }).waitFor();
  await page.getByRole('button', { name: 'Guardar cambios' }).click();
  await page.getByText('Configuración guardada.').waitFor({ timeout: 20000 });
  ok('admin consulta y guarda la configuración de plataforma');
  await page.screenshot({ path: `${EVID}/admin-configuracion-1440.png`, fullPage: false });

  await navAdmin.getByRole('link', { name: 'Propietarios' }).click();
  await page.getByText(owner.correo).first().waitFor({ timeout: 20000 });
  ok('admin lista propietarios con el owner validado');

  await navAdmin.getByRole('link', { name: 'Reclamos y soporte' }).click();
  await page.locator('button.reclamo').first().waitFor({ timeout: 20000 });
  await page.locator('button.reclamo').first().click();
  await page.getByRole('heading', { name: 'Seguimientos' }).waitFor();
  ok('admin consulta reclamo y seguimientos');

  const asuntoTicket = `Ticket mesa de ayuda ${String(stamp).slice(-5)}`;
  await page.locator('label:has-text("ID de usuario") input').fill(String(owner.id));
  await page.locator('label:has-text("Asunto") input').fill(asuntoTicket);
  await page.locator('label:has-text("Descripción") textarea').fill('Consulta creada desde la mesa de ayuda.');
  await page.getByRole('button', { name: 'Crear ticket' }).click();
  await page.getByText(asuntoTicket).first().waitFor({ timeout: 20000 });
  ok('admin crea ticket desde la mesa de ayuda');

  await navAdmin.getByRole('link', { name: 'Usuarios' }).click();
  await page.getByText(owner.correo).first().waitFor({ timeout: 20000 });
  const filaUsuario = page.locator('tr', { hasText: owner.correo }).first();
  await filaUsuario.getByRole('button', { name: 'Roles' }).click();
  const modalRoles = page.getByRole('dialog');
  await modalRoles.waitFor({ timeout: 10000 });
  await modalRoles.getByRole('heading', { name: /Roles de / }).waitFor();
  ok('admin abre el modal de roles');
  await page.keyboard.press('Escape');
  await modalRoles.waitFor({ state: 'detached' });

  await navAdmin.getByRole('link', { name: 'Promociones' }).click();
  await page.getByRole('heading', { name: 'Promociones de plataforma' }).waitFor();
  await page.getByRole('heading', { name: 'Nueva promoción global' }).waitFor();
  const codigoGlobal = `GLOBAL${String(stamp).slice(-4)}`;
  await page.locator('label:has-text("Código") input').fill(codigoGlobal);
  await page.locator('label:has-text("Descripción") input').fill('Promo global E2E');
  await page.locator('label:has-text("Desde") input').fill(new Date().toISOString().slice(0, 10));
  await page.locator('label:has-text("Hasta") input').fill(new Date(Date.now() + 20 * 86400000).toISOString().slice(0, 10));
  await page.getByRole('button', { name: 'Crear promoción' }).click();
  await page.getByText(codigoGlobal).first().waitFor({ timeout: 20000 });
  ok('admin crea promocion global');

  await navAdmin.getByRole('link', { name: 'Cocheras' }).click();
  await page.getByText('Cochera E2E').first().waitFor({ timeout: 20000 });
  ok('admin lista cocheras');

  await navAdmin.getByRole('link', { name: 'Supervisión' }).click();
  await page.getByText(nombreEst).first().waitFor({ timeout: 20000 });
  const filaSede = page.locator('tr', { hasText: nombreEst }).first();
  await filaSede.getByRole('button', { name: 'Desactivar' }).click();
  await page.getByText('Establecimiento inactivo.').waitFor({ timeout: 20000 });
  await filaSede.getByRole('button', { name: 'Activar' }).click();
  await page.getByText('Establecimiento activo.').waitFor({ timeout: 20000 });
  ok('admin activa y desactiva sedes');
  await page.getByRole('button', { name: 'reservas', exact: true }).click();
  await page.getByRole('columnheader', { name: 'Titular' }).waitFor();
  await page.getByRole('button', { name: 'pagos', exact: true }).click();
  await page.getByRole('columnheader', { name: 'Medio' }).waitFor();
  ok('admin supervisa reservas y pagos');
  await page.screenshot({ path: `${EVID}/admin-supervision-1440.png`, fullPage: false });

  await navAdmin.getByRole('link', { name: 'Transacciones' }).click();
  await page.getByRole('heading', { name: 'Transacciones y comisiones' }).waitFor();
  await page.getByText('Total procesado').waitFor();
  ok('admin consulta transacciones y comisiones');

  await navAdmin.getByRole('link', { name: 'Actividades' }).click();
  await page.getByRole('heading', { name: 'Actividades de la plataforma' }).waitFor();
  await page.getByText('Actividad E2E').first().waitFor({ timeout: 20000 });
  ok('admin supervisa actividades de la plataforma');

  await navAdmin.getByRole('link', { name: 'Auditoría' }).click();
  await page.getByRole('columnheader', { name: 'Acción' }).waitFor();
  const filasAuditoria = await page.locator('table.tabla tbody tr').count();
  if (filasAuditoria > 0) ok(`admin consulta auditoria (${filasAuditoria} eventos)`);
  else fallo('auditoria sin eventos');

  // 4. Recepcion: la administracion vincula al operador y este opera su turno
  const adminToken = (
    await api('/auth/login', { method: 'POST', body: { correo: ADMIN.correo, password: ADMIN.password } })
  ).accessToken;
  const operador = {
    correo: `e2e.recep.${stamp}@canchadirecta.pe`,
    password: 'ClaveSegura123',
  };
  const regRecep = await api('/auth/register', {
    method: 'POST',
    body: {
      nombres: 'Operador',
      apellidos: `E2E ${stamp}`,
      correo: operador.correo,
      telefono: '977666555',
      password: operador.password,
      confirmacionPassword: operador.password,
    },
  });
  operador.id = regRecep.id;
  await api(`/admin/usuarios/${operador.id}/roles`, {
    method: 'POST',
    token: adminToken,
    body: { roles: ['USER', 'OPERADOR'] },
  });
  const sedesAdmin = await api('/admin/establecimientos?limite=200', { token: adminToken });
  const sede = sedesAdmin.find((s) => s.nombre === nombreEst);
  if (!sede) throw new Error(`no se encontro la sede ${nombreEst} para vincular al operador`);

  await navAdmin.getByRole('link', { name: 'Operadores' }).click();
  await page.getByRole('heading', { level: 2, name: 'Operadores de recepción' }).waitFor({ timeout: 20000 });
  await page.locator('label:has-text("Sede") select').selectOption(String(sede.id));
  await page.locator('label:has-text("Buscar usuario por correo") input').fill(operador.correo);
  const filaCandidato = page.locator('li', { hasText: operador.correo }).first();
  await filaCandidato.waitFor({ timeout: 20000 });
  await filaCandidato.getByRole('button', { name: 'Vincular' }).click();
  await page.getByText('ahora opera esta sede.').waitFor({ timeout: 20000 });
  ok('admin vincula un operador de recepcion a la sede');
  await page.screenshot({ path: `${EVID}/admin-operadores-1440.png`, fullPage: false });

  await page.locator('.admin-side__salir').click();
  await login(operador.correo, operador.password);
  await page.goto(`${BASE}/recepcion`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: 'Turno actual' }).waitFor({ timeout: 20000 });
  await page.getByText('Reservas del día').waitFor({ timeout: 20000 });
  ok('el operador entra al portal de recepcion');
  await page.screenshot({ path: `${EVID}/recepcion-turno-1440.png`, fullPage: false });

  const navRecep = page.getByRole('navigation', { name: 'Menú de recepción' });
  await navRecep.getByRole('link', { name: 'Agenda del día' }).click();
  await page.locator('input[type="date"]').waitFor({ timeout: 20000 });
  ok('agenda de recepcion carga para el operador');
  await page.screenshot({ path: `${EVID}/recepcion-agenda-1440.png`, fullPage: false });

  // El personal de la sede bloquea una franja sin depender del propietario (RN-03, ADR-0042).
  const seccionBloqueos = page.locator('section', {
    has: page.getByRole('heading', { name: 'Bloqueos de la fecha' }),
  });
  const formBloqueo = seccionBloqueos.locator('form');
  await formBloqueo.getByLabel('Desde').fill('10:00');
  await formBloqueo.getByLabel('Hasta').fill('11:00');
  await formBloqueo.getByLabel('Motivo').fill('Mantenimiento E2E');
  await formBloqueo.getByRole('button', { name: 'Bloquear franja' }).click();
  await seccionBloqueos.getByText('Mantenimiento E2E').waitFor({ timeout: 20000 });
  ok('el operador bloquea una franja de una cancha de su sede');
  await page.screenshot({ path: `${EVID}/recepcion-bloqueo-1440.png`, fullPage: false });

  const filaBloqueo = seccionBloqueos.locator('li', { hasText: 'Mantenimiento E2E' });
  await filaBloqueo.getByRole('button', { name: 'Levantar' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Levantar' }).click();
  await seccionBloqueos.getByText('Mantenimiento E2E').waitFor({ state: 'detached', timeout: 20000 });
  ok('el operador levanta el bloqueo que creo');

  await navRecep.getByRole('link', { name: 'Reserva presencial' }).click();
  await page.getByRole('button', { name: 'Registrar reserva' }).waitFor({ timeout: 20000 });
  ok('formulario de reserva presencial disponible');

  await navRecep.getByRole('link', { name: 'Cobros' }).click();
  await page.getByRole('heading', { name: 'Cobros', exact: true }).waitFor({ timeout: 20000 });
  ok('cobros de recepcion disponibles');

  await navRecep.getByRole('link', { name: 'Incidencias' }).click();
  await page.getByRole('heading', { name: 'Reportar incidencia' }).waitFor({ timeout: 20000 });
  ok('incidencias de recepcion disponibles');
} catch (error) {
  fallo(`excepcion: ${error.message}`);
  await page.screenshot({ path: `${EVID}/fallo-owner-admin.png`, fullPage: false }).catch(() => {});
} finally {
  await browser.close();
}

if (errores.length) {
  console.log('Errores de red/consola:');
  errores.forEach((e) => console.log(`  - ${e}`));
  fallos.push(`${errores.length} error(es) de red`);
}
console.log(fallos.length ? `\nE2E owner/admin con ${fallos.length} fallo(s).` : '\nE2E owner/admin OK.');
process.exit(fallos.length ? 1 : 0);
