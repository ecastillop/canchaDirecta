import { TestBed } from '@angular/core/testing';
import { ReservaRecepcion } from '../../core/api/recepcion.api';
import { RecepcionReservaCardComponent } from './recepcion-reserva-card.component';

function reserva(cambios: Partial<ReservaRecepcion>): ReservaRecepcion {
  return {
    id: 1,
    idEstablecimiento: 1,
    establecimiento: 'Complejo Demo',
    idCancha: 1,
    cancha: 'Cancha 1',
    inicio: '2026-10-20T18:00:00Z',
    fin: '2026-10-20T19:00:00Z',
    estado: 'CONFIRMADA',
    pagoEstado: 'APROBADO',
    total: 90,
    idCliente: 2,
    cliente: 'Lucia Torres',
    telefono: '999111222',
    ...cambios,
  };
}

describe('RecepcionReservaCardComponent', () => {
  async function crear(datos: ReservaRecepcion) {
    await TestBed.configureTestingModule({ imports: [RecepcionReservaCardComponent] }).compileComponents();
    const fixture = TestBed.createComponent(RecepcionReservaCardComponent);
    fixture.componentRef.setInput('reserva', datos);
    fixture.detectChanges();
    const raiz = fixture.nativeElement as HTMLElement;
    const boton = (texto: string) =>
      [...raiz.querySelectorAll('button')].find((nodo) => nodo.textContent?.trim() === texto) ?? null;
    return { fixture, raiz, boton };
  }

  it('ofrece cobrar y no check-in cuando la reserva esta pendiente', async () => {
    const { boton } = await crear(reserva({ estado: 'PENDIENTE', pagoEstado: null }));

    expect(boton('Registrar cobro')).not.toBeNull();
    expect(boton('Check-in')).toBeNull();
  });

  it('permite el check-in de una reserva confirmada y pagada en mostrador', async () => {
    const { boton } = await crear(reserva({ estado: 'CONFIRMADA', pagoEstado: 'APROBADO' }));

    expect(boton('Check-in')).not.toBeNull();
    expect(boton('Registrar cobro')).toBeNull();
  });

  it('permite cobrar y hacer check-in de una confirmada sin pago', async () => {
    const { boton } = await crear(reserva({ estado: 'CONFIRMADA', pagoEstado: null }));

    expect(boton('Registrar cobro')).not.toBeNull();
    expect(boton('Check-in')).not.toBeNull();
  });

  it('no ofrece acciones sobre una reserva finalizada', async () => {
    const { boton } = await crear(reserva({ estado: 'FINALIZADA', pagoEstado: 'APROBADO' }));

    expect(boton('Check-in')).toBeNull();
    expect(boton('Registrar cobro')).toBeNull();
  });
});
