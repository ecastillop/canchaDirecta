# Diseño de fusión CanchaDirecta

## Referencia

- `CanchaDirecta_STANDALONE.html`: lenguaje visual editorial (fondo bosque, acento lima,
  tipografía display, tarjetas limpias y navegación superior).
- Producto funcional actual: carrusel de fotos, calendario semanal, selección de día y hora,
  precios por franja y comparación de canchas.

## Principios

1. **Una sola identidad:** bosque + lima + tipografía Outfit/DM Sans, con superficies claras.
2. **El producto real es la fuente de datos:** no se porta el estado falso del standalone.
3. **Mantener lo que ya funciona:** carrusel, calendario semanal, precios y comparación.
4. **Navegación explícita:** toda vista previa tiene retorno visible a configuración.
5. **Accesibilidad:** foco visible, contraste, jerarquía de encabezados y teclado.

## Decisiones visuales

- Fondo principal `#f7f8f4`; hero y footer `#0f191e`; acento `#c5f56b`; verde profundo `#167a70`.
- Tipografía: `Outfit` para títulos, `DM Sans` para texto; ambas locales en `public/fonts`.
- Header blanco translúcido, logo lima y CTA oscuro.
- Catálogo: hero oscuro + filtros laterales + tarjetas con arte abstracto por deporte
  (las fotos reales se reservan para el detalle, donde sí aportan información).
- Detalle: galería con carrusel y miniaturas, secciones limpias y widget de reserva sticky.
- Panel/administración heredan tokens; el foco de esta fase es la experiencia pública.

## Qué se conserva del producto anterior

- Galería/carrusel de fotos con visor, miniaturas y fallback.
- Calendario semanal con días anteriores/siguientes, navegación de semanas y cambio de mes.
- Selección de día y de franja horaria con precio visible.
- Comparación de hasta 4 canchas.
- Reserva, pago demo, panel y administración existentes.

## Fuera de alcance de esta fase

- Portar las pantallas de cliente/propietario/administrador del standalone.
- Sustituir la fuente de datos real por el almacenamiento local del prototipo.
- Modo oscuro dedicado (la paleta "Nocturno" es un primer acercamiento).
