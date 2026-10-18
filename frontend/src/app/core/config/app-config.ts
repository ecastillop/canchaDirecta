/** Configuración de la aplicación frontend. */
export const APP_CONFIG = {
  /** En dev llega al backend por el proxy de `ng serve`; en prod por nginx. */
  apiBaseUrl: '/api/v1',
  zonaHoraria: 'America/Lima',
  locale: 'es-PE',
  moneda: 'PEN',
  /** Banner permanente de pago de demostración. */
  demoPago: true,
  /** Debe coincidir con `cd.reservas.dias-anticipacion-max` del backend. */
  diasAnticipacionMax: 365,
} as const;
