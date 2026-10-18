import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { OwnerApi } from '../../core/api/owner.api';
import { OwnerStore } from '../../core/api/owner.store';
import { Cochera, Establecimiento } from '../../core/api/owner.models';
import { Foto } from '../../core/api/venues.models';
import { VenuesApi } from '../../core/api/venues.api';
import { Distrito } from '../../core/api/venues.models';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { ConfirmService } from '../../shared/ui/confirm/confirm.service';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { ToastService } from '../../shared/ui/toast/toast.service';

const FOTO_FALLBACK =
  'data:image/svg+xml;charset=UTF-8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 240"><rect width="320" height="240" fill="#e8edf4"/><path d="M40 180l60-70 50 55 35-42 75 87z" fill="#b7c3d3"/><text x="160" y="220" text-anchor="middle" font-family="Segoe UI,Arial" font-size="14" fill="#5b6b80">Foto no disponible</text></svg>',
  );

@Component({
  selector: 'app-panel-resumen',
  imports: [ReactiveFormsModule, RouterLink, CurrencyPipe, CdButtonDirective, CdInputDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!store.establecimientoId()) {
      <section class="cd-card">
        <h2 class="seccion">Registrar mi establecimiento</h2>
        <p class="cd-small cd-muted">
          Necesitas un establecimiento para publicar tus canchas. Al crearlo aparecerá automáticamente
          en tu panel y podrás administrarlo desde cualquier dispositivo.
        </p>
        @if (error()) {
          <p class="alerta" role="alert">{{ error() }}</p>
        }
        <form class="cd-stack" [formGroup]="establecimientoForm" (ngSubmit)="crearEstablecimiento()" novalidate>
          <div class="dos">
            <label class="cd-field">
              <span class="cd-field__label">Nombre</span>
              <input cdInput formControlName="nombre" maxlength="120" />
            </label>
            <label class="cd-field">
              <span class="cd-field__label">Documento de identidad</span>
              <input cdInput formControlName="documentoIdentidad" maxlength="20" />
            </label>
          </div>
          <label class="cd-field">
            <span class="cd-field__label">Distrito</span>
            <select cdInput formControlName="idDistrito">
              <option [ngValue]="null">Selecciona un distrito</option>
              @for (distrito of distritos(); track distrito.id) {
                <option [ngValue]="distrito.id">{{ distrito.nombre }} ({{ distrito.provincia }})</option>
              }
            </select>
          </label>
          <label class="cd-field">
            <span class="cd-field__label">Dirección</span>
            <input cdInput formControlName="direccion" maxlength="200" />
          </label>
          <label class="cd-field">
            <span class="cd-field__label">Referencia <span class="optional">(opcional)</span></span>
            <input cdInput formControlName="referencia" maxlength="200" />
          </label>
          <button cdButton type="submit" [loading]="guardando()">Crear establecimiento</button>
        </form>

        <p class="cd-xs cd-muted actualizar">
          ¿Ya creaste tu establecimiento en otro dispositivo?
          <button class="enlace" type="button" (click)="store.recargarEstablecimientos()">Actualizar lista</button>
        </p>
      </section>
    } @else {
      <div class="cd-stack">
        <section class="cd-card">
          <div class="cd-row cd-row--between">
            <div>
              <h2 class="seccion">
                {{ establecimiento()?.nombre || 'Establecimiento #' + store.establecimientoId() }}
              </h2>
              @if (store.establecimientos().length > 1) {
                <label class="cd-field selector">
                  <span class="cd-field__label">Establecimiento activo</span>
                  <select
                    cdInput
                    [value]="store.establecimientoId()"
                    (change)="seleccionarEstablecimiento($event)"
                  >
                    @for (item of store.establecimientos(); track item.id) {
                      <option [value]="item.id">
                        {{ item.nombre }}{{ item.estado === 'INACTIVO' ? ' (inactivo)' : '' }}
                      </option>
                    }
                  </select>
                </label>
              }
              @if (establecimiento(); as e) {
                <p class="cd-small cd-muted">
                  {{ e.direccion }} · {{ nombreDistrito(e.idDistrito) }} · {{ e.estado }}
                </p>
                @if (e.referencia) {
                  <p class="cd-xs cd-muted">Referencia: {{ e.referencia }}</p>
                }
              } @else {
                <p class="cd-small cd-muted">Las canchas publicadas bajo este establecimiento aparecen abajo.</p>
              }
            </div>
            <div class="cd-row">
              @if (establecimiento()) {
                <button cdButton variant="secondary" size="sm" type="button" (click)="abrirEdicionEstablecimiento()">
                  Editar datos
                </button>
                <button cdButton variant="danger" size="sm" type="button" (click)="eliminarEstablecimiento()">
                  Dar de baja
                </button>
              }
              <button cdButton variant="ghost" size="sm" type="button" (click)="store.recargarEstablecimientos()">
                Actualizar
              </button>
            </div>
          </div>
          @if (error()) {
            <p class="alerta" role="alert">{{ error() }}</p>
          }
          @if (editandoEstablecimiento()) {
            <form class="edicion" [formGroup]="editarEstablecimientoForm" (ngSubmit)="guardarEstablecimiento()" novalidate>
              <div class="dos">
                <label class="cd-field">
                  <span class="cd-field__label">Nombre</span>
                  <input cdInput formControlName="nombre" maxlength="120" />
                </label>
                <label class="cd-field">
                  <span class="cd-field__label">Distrito</span>
                  <select cdInput formControlName="idDistrito">
                    <option [ngValue]="null">Selecciona un distrito</option>
                    @for (distrito of distritos(); track distrito.id) {
                      <option [ngValue]="distrito.id">{{ distrito.nombre }} ({{ distrito.provincia }})</option>
                    }
                  </select>
                </label>
              </div>
              <label class="cd-field">
                <span class="cd-field__label">Dirección</span>
                <input cdInput formControlName="direccion" maxlength="200" />
              </label>
              <label class="cd-field">
                <span class="cd-field__label">Referencia <span class="optional">(opcional)</span></span>
                <input cdInput formControlName="referencia" maxlength="200" />
              </label>
              <div class="cd-row">
                <button cdButton size="sm" type="submit" [loading]="guardando()">Guardar</button>
                <button cdButton variant="ghost" size="sm" type="button" (click)="editandoEstablecimiento.set(false)">
                  Cancelar
                </button>
              </div>
            </form>
          }
        </section>

        <section class="cd-card">
          <h2 class="seccion">Canchas ({{ store.canchas().length }})</h2>
          <p class="cd-xs cd-muted">
            “Vista previa” abre el detalle público en esta misma pestaña. Allí verás un botón
            “Volver a configurar” para regresar a esta cancha.
          </p>
          @if (store.canchas().length === 0) {
            <p class="cd-small cd-muted">Aún no hay canchas. Crea la primera para abrir tu agenda.</p>
          } @else {
            <ul class="canchas">
              @for (cancha of store.canchas(); track cancha.id) {
                <li [id]="'cancha-' + cancha.id">
                  <div>
                    <strong>{{ cancha.nombre }}</strong>
                    <p class="cd-xs cd-muted">
                      Capacidad {{ cancha.capacidad }} ·
                      {{ cancha.precioBase | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}/h ·
                      {{ cancha.estado }}
                    </p>
                  </div>
                  <div class="cd-row">
                    <a [routerLink]="['/panel/agenda']" [queryParams]="{ cancha: cancha.id }" class="cd-btn cd-btn--secondary cd-btn--sm">
                      Agenda
                    </a>
                    <a
                      [routerLink]="['/canchas', cancha.id]"
                      [queryParams]="{ from: 'panel' }"
                      class="cd-btn cd-btn--ghost cd-btn--sm"
                      title="Abre el detalle público de esta cancha"
                    >
                      Vista previa
                    </a>
                    <button cdButton variant="ghost" size="sm" type="button" (click)="abrirFotos(cancha.id)">
                      Fotos
                    </button>
                    <button cdButton variant="secondary" size="sm" type="button" (click)="abrirEdicion(cancha.id)">
                      Editar
                    </button>
                    <button cdButton variant="danger" size="sm" type="button" (click)="eliminarCancha(cancha.id)">
                      Eliminar
                    </button>
                  </div>

                  @if (editandoId() === cancha.id) {
                    <form class="edicion" [formGroup]="editarForm" (ngSubmit)="guardarEdicion(cancha.id)" novalidate>
                      <div class="dos">
                        <label class="cd-field">
                          <span class="cd-field__label">Nombre</span>
                          <input cdInput formControlName="nombre" maxlength="100" />
                        </label>
                        <label class="cd-field">
                          <span class="cd-field__label">Capacidad</span>
                          <input cdInput type="number" min="1" max="100" formControlName="capacidad" />
                        </label>
                      </div>
                      <div class="dos">
                        <label class="cd-field">
                          <span class="cd-field__label">Precio base por hora (S/)</span>
                          <input cdInput type="number" min="0" step="1" formControlName="precioBase" />
                        </label>
                        <label class="cd-field">
                          <span class="cd-field__label">Descripción</span>
                          <input cdInput formControlName="descripcion" maxlength="500" />
                        </label>
                      </div>
                      <div class="cd-row">
                        <button cdButton size="sm" type="submit" [loading]="guardando()">Guardar</button>
                        <button cdButton variant="ghost" size="sm" type="button" (click)="editandoId.set(null)">
                          Cancelar
                        </button>
                      </div>
                    </form>
                  }

                  @if (fotosCanchaId() === cancha.id) {
                    <div class="edicion">
                      <h3 class="subseccion">Fotos de {{ cancha.nombre }}</h3>
                      @if (fotos().length === 0) {
                        <p class="cd-xs cd-muted">Sin fotos. Agrega una URL pública (JPG/PNG/WebP).</p>
                      } @else {
                        <ul class="fotos">
                          @for (foto of fotos(); track foto.id) {
                            <li>
                              <img [src]="foto.url" [alt]="'Foto de ' + cancha.nombre" loading="lazy" (error)="usarFallback($event)" />
                              <span class="cd-xs">Orden {{ foto.orden }}{{ foto.principal ? ' · principal' : '' }}</span>
                              <button cdButton variant="danger" size="sm" type="button" (click)="eliminarFoto(cancha.id, foto.id)">
                                Quitar
                              </button>
                            </li>
                          }
                        </ul>
                      }
                      <form class="cd-row" [formGroup]="fotoForm" (ngSubmit)="agregarFoto(cancha.id)" novalidate>
                        <label class="cd-field foto-url">
                          <span class="cd-field__label">URL de la foto</span>
                          <input cdInput formControlName="url" placeholder="https://..." maxlength="500" />
                        </label>
                        <label class="cd-field foto-orden">
                          <span class="cd-field__label">Orden</span>
                          <input cdInput type="number" min="1" max="99" formControlName="orden" />
                        </label>
                        <label class="cd-field cd-field--check">
                          <input type="checkbox" formControlName="principal" />
                          <span class="cd-small">Principal</span>
                        </label>
                        <button cdButton variant="secondary" size="sm" type="submit" [loading]="guardando()">Agregar</button>
                      </form>
                    </div>
                  }
                </li>
              }
            </ul>
          }
        </section>

        <section class="cd-card">
          <h2 class="seccion">Nueva cancha</h2>
          @if (error()) {
            <p class="alerta" role="alert">{{ error() }}</p>
          }
          <form class="cd-stack" [formGroup]="canchaForm" (ngSubmit)="crearCancha()" novalidate>
            <div class="dos">
              <label class="cd-field">
                <span class="cd-field__label">Nombre</span>
                <input cdInput formControlName="nombre" maxlength="100" />
              </label>
              <label class="cd-field">
                <span class="cd-field__label">Capacidad</span>
                <input cdInput type="number" min="1" max="100" formControlName="capacidad" />
              </label>
            </div>
            <div class="dos">
              <label class="cd-field">
                <span class="cd-field__label">Precio base por hora (S/)</span>
                <input cdInput type="number" min="0" step="1" formControlName="precioBase" />
              </label>
              <label class="cd-field">
                <span class="cd-field__label">Descripción <span class="optional">(opcional)</span></span>
                <input cdInput formControlName="descripcion" maxlength="500" />
              </label>
            </div>
            <button cdButton type="submit" [loading]="guardando()">Crear cancha</button>
          </form>
        </section>

        <section class="cd-card">
          <h2 class="seccion">Cocheras</h2>
          @if (cocheras().length === 0) {
            <p class="cd-small cd-muted">Sin cocheras registradas. Puedes ofrecer estacionamiento opcional.</p>
          } @else {
            <ul class="canchas">
              @for (cochera of cocheras(); track cochera.id) {
                <li>
                  <div>
                    <strong>{{ cochera.nombre }}</strong>
                    <p class="cd-xs cd-muted">
                      Capacidad {{ cochera.capacidad }} ·
                      {{ cochera.tarifaHora | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}/h · {{ cochera.estado }}
                    </p>
                  </div>
                  <button cdButton variant="danger" size="sm" type="button" (click)="eliminarCochera(cochera.id)">
                    Eliminar
                  </button>
                </li>
              }
            </ul>
          }

          <form class="cd-stack formulario" [formGroup]="cocheraForm" (ngSubmit)="crearCochera()" novalidate>
            <h3 class="subseccion">Nueva cochera</h3>
            <div class="tres">
              <label class="cd-field">
                <span class="cd-field__label">Nombre</span>
                <input cdInput formControlName="nombre" maxlength="100" />
              </label>
              <label class="cd-field">
                <span class="cd-field__label">Capacidad</span>
                <input cdInput type="number" min="1" formControlName="capacidad" />
              </label>
              <label class="cd-field">
                <span class="cd-field__label">Tarifa por hora (S/)</span>
                <input cdInput type="number" min="0" step="1" formControlName="tarifaHora" />
              </label>
            </div>
            <button cdButton variant="secondary" type="submit" [loading]="guardando()">Agregar cochera</button>
          </form>
        </section>
      </div>
    }
  `,
  styles: `
    .seccion {
      font-size: var(--cd-text-base);
      margin-bottom: var(--cd-space-2);
    }
    .selector {
      max-width: 340px;
      margin-top: var(--cd-space-2);
    }
    .actualizar {
      margin-top: var(--cd-space-4);
    }
    .enlace {
      padding: 0;
      border: 0;
      background: none;
      color: var(--cd-color-primary);
      font: inherit;
      text-decoration: underline;
      cursor: pointer;
    }
    .subseccion {
      font-size: var(--cd-text-sm);
      margin-bottom: var(--cd-space-2);
    }
    .dos {
      display: grid;
      gap: var(--cd-space-3);
    }
    .tres {
      display: grid;
      gap: var(--cd-space-3);
    }
    .formulario {
      margin-top: var(--cd-space-4);
      padding-top: var(--cd-space-4);
      border-top: 1px solid var(--cd-color-border);
    }
    .canchas {
      list-style: none;
      padding: 0;
      display: grid;
      gap: var(--cd-space-2);
    }
    .canchas li {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--cd-space-3);
      padding: var(--cd-space-3);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-sm);
      flex-wrap: wrap;
    }
    .canchas .cd-btn {
      text-decoration: none;
    }
    .edicion {
      flex-basis: 100%;
      display: grid;
      gap: var(--cd-space-2);
      padding-top: var(--cd-space-3);
      border-top: 1px dashed var(--cd-color-border);
    }
    .fotos {
      list-style: none;
      padding: 0;
      display: grid;
      gap: var(--cd-space-2);
    }
    .fotos li {
      display: flex;
      align-items: center;
      gap: var(--cd-space-3);
      flex-wrap: wrap;
    }
    .fotos img {
      width: 96px;
      height: 64px;
      object-fit: cover;
      border-radius: var(--cd-radius-sm);
    }
    .foto-url {
      flex: 1 1 260px;
    }
    .foto-orden {
      max-width: 90px;
    }
    .alerta {
      margin-bottom: var(--cd-space-3);
      padding: var(--cd-space-3);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-danger-soft);
      color: var(--cd-color-danger);
      font-size: var(--cd-text-sm);
      font-weight: 600;
    }
    .separador {
      margin-block: var(--cd-space-4);
    }
    @media (min-width: 640px) {
      .dos {
        grid-template-columns: 1fr 1fr;
      }
      .tres {
        grid-template-columns: 1fr 1fr 1fr;
      }
    }
  `,
})
export class PanelResumenComponent implements OnInit {
  private readonly ownerApi = inject(OwnerApi);
  private readonly venues = inject(VenuesApi);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  protected readonly store = inject(OwnerStore);
  private edicionDesdeQuery = false;

  protected readonly distritos = signal<Distrito[]>([]);
  protected readonly establecimiento = signal<Establecimiento | null>(null);
  protected readonly cocheras = signal<Cochera[]>([]);
  protected readonly editandoEstablecimiento = signal(false);
  protected readonly editandoId = signal<number | null>(null);
  protected readonly fotosCanchaId = signal<number | null>(null);
  protected readonly fotos = signal<Foto[]>([]);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly establecimientoForm = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(120)]],
    documentoIdentidad: ['', [Validators.required, Validators.maxLength(20)]],
    idDistrito: [null as number | null, [Validators.required]],
    direccion: ['', [Validators.required, Validators.maxLength(200)]],
    referencia: [''],
  });

  protected readonly editarEstablecimientoForm = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(120)]],
    idDistrito: [null as number | null, [Validators.required]],
    direccion: ['', [Validators.required, Validators.maxLength(200)]],
    referencia: [''],
  });

  protected readonly canchaForm = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    capacidad: [10, [Validators.required, Validators.min(1), Validators.max(100)]],
    precioBase: [80, [Validators.required, Validators.min(0)]],
    descripcion: [''],
  });

  protected readonly cocheraForm = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    capacidad: [10, [Validators.required, Validators.min(1)]],
    tarifaHora: [5, [Validators.required, Validators.min(0)]],
  });

  protected readonly editarForm = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(100)]],
    capacidad: [10, [Validators.required, Validators.min(1), Validators.max(100)]],
    precioBase: [80, [Validators.required, Validators.min(0)]],
    descripcion: [''],
  });

  protected readonly fotoForm = this.fb.nonNullable.group({
    url: ['', [Validators.required, Validators.maxLength(500)]],
    orden: [1, [Validators.required, Validators.min(1), Validators.max(99)]],
    principal: [false],
  });

  ngOnInit(): void {
    void this.cargarDistritos();
  }

  constructor() {
    effect(() => {
      const id = this.store.establecimientoId();
      if (id) {
        void this.cargarEstablecimiento();
        void this.cargarCocheras();
      } else {
        this.establecimiento.set(null);
        this.cocheras.set([]);
      }
    });
    effect(() => {
      const canchas = this.store.canchas();
      if (this.edicionDesdeQuery) {
        return;
      }
      const id = Number(this.route.snapshot.queryParamMap.get('editarCancha'));
      if (!Number.isFinite(id) || id <= 0 || !canchas.some((cancha) => cancha.id === id)) {
        return;
      }
      this.edicionDesdeQuery = true;
      void this.abrirEdicion(id).then(() => {
        setTimeout(() => {
          document.getElementById(`cancha-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }, 80);
      });
    });
  }

  protected nombreDistrito(id: number): string {
    return this.distritos().find((distrito) => distrito.id === id)?.nombre ?? `Distrito #${id}`;
  }

  protected seleccionarEstablecimiento(event: Event): void {
    const id = Number((event.target as HTMLSelectElement).value);
    if (!Number.isFinite(id) || id <= 0) {
      return;
    }
    this.editandoEstablecimiento.set(false);
    this.error.set(null);
    this.store.usarEstablecimiento(id);
  }

  protected usarFallback(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (img.dataset['fallback'] === 'true') {
      return;
    }
    img.dataset['fallback'] = 'true';
    img.src = FOTO_FALLBACK;
  }

  protected crearEstablecimiento(): void {
    if (this.establecimientoForm.invalid) {
      this.establecimientoForm.markAllAsTouched();
      return;
    }
    const value = this.establecimientoForm.getRawValue();
    this.guardando.set(true);
    this.error.set(null);
    this.ownerApi
      .crearEstablecimiento({
        documentoIdentidad: value.documentoIdentidad.trim(),
        idDistrito: value.idDistrito!,
        nombre: value.nombre.trim(),
        direccion: value.direccion.trim(),
        referencia: value.referencia.trim() || null,
      })
      .subscribe({
        next: (respuesta) => {
          this.guardando.set(false);
          this.store.usarEstablecimiento(respuesta.id);
          void this.store.recargarEstablecimientos();
          this.toast.success('Establecimiento creado.');
        },
        error: (error) => {
          this.error.set(toApiError(error).message);
          this.guardando.set(false);
        },
      });
  }

  protected abrirEdicionEstablecimiento(): void {
    const actual = this.establecimiento();
    if (!actual) {
      return;
    }
    this.editarEstablecimientoForm.reset({
      nombre: actual.nombre,
      idDistrito: actual.idDistrito,
      direccion: actual.direccion,
      referencia: actual.referencia ?? '',
    });
    this.error.set(null);
    this.editandoEstablecimiento.set(true);
  }

  protected guardarEstablecimiento(): void {
    const id = this.store.establecimientoId();
    if (!id) {
      return;
    }
    if (this.editarEstablecimientoForm.invalid) {
      this.editarEstablecimientoForm.markAllAsTouched();
      return;
    }
    const value = this.editarEstablecimientoForm.getRawValue();
    this.guardando.set(true);
    this.error.set(null);
    this.ownerApi
      .actualizarEstablecimiento(id, {
        idDistrito: value.idDistrito!,
        nombre: value.nombre.trim(),
        direccion: value.direccion.trim(),
        referencia: value.referencia.trim() || null,
      })
      .subscribe({
        next: (actualizado) => {
          this.guardando.set(false);
          this.establecimiento.set(actualizado);
          this.editandoEstablecimiento.set(false);
          this.toast.success('Establecimiento actualizado.');
          void this.store.recargarEstablecimientos();
          void this.store.cargarCanchas();
        },
        error: (error) => {
          this.error.set(toApiError(error).message);
          this.guardando.set(false);
        },
      });
  }

  protected async eliminarEstablecimiento(): Promise<void> {
    const id = this.store.establecimientoId();
    if (!id) {
      return;
    }
    const ok = await this.confirm.confirm({
      title: 'Dar de baja el establecimiento',
      message:
        'El establecimiento y sus canchas dejarán de mostrarse en el catálogo. No se puede deshacer desde la aplicación.',
      confirmText: 'Dar de baja',
      danger: true,
    });
    if (!ok) {
      return;
    }
    this.ownerApi.eliminarEstablecimiento(id).subscribe({
      next: () => {
        this.store.usarEstablecimiento(null);
        this.establecimiento.set(null);
        this.editandoEstablecimiento.set(false);
        void this.store.recargarEstablecimientos();
        this.toast.success('Establecimiento dado de baja.');
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  private async cargarEstablecimiento(): Promise<void> {
    const id = this.store.establecimientoId();
    if (!id) {
      this.establecimiento.set(null);
      return;
    }
    try {
      const actual = await firstValueFrom(this.ownerApi.obtenerEstablecimiento(id));
      if (this.store.establecimientoId() === id) {
        this.establecimiento.set(actual);
      }
    } catch (error) {
      if (this.store.establecimientoId() === id) {
        this.establecimiento.set(null);
        this.error.set(toApiError(error).message);
      }
    }
  }

  protected crearCancha(): void {
    const idEstablecimiento = this.store.establecimientoId();
    if (!idEstablecimiento) {
      return;
    }
    if (this.canchaForm.invalid) {
      this.canchaForm.markAllAsTouched();
      return;
    }
    const value = this.canchaForm.getRawValue();
    this.guardando.set(true);
    this.error.set(null);
    this.ownerApi
      .crearCancha(idEstablecimiento, {
        nombre: value.nombre.trim(),
        capacidad: value.capacidad,
        precioBase: value.precioBase,
        descripcion: value.descripcion.trim() || null,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.canchaForm.reset({ nombre: '', capacidad: 10, precioBase: 80, descripcion: '' });
          this.toast.success('Cancha creada.');
          void this.store.cargarCanchas();
        },
        error: (error) => {
          this.error.set(toApiError(error).message);
          this.guardando.set(false);
        },
      });
  }

  protected async abrirEdicion(id: number): Promise<void> {
    try {
      const detalle = await firstValueFrom(this.venues.detalle(id));
      this.editarForm.reset({
        nombre: detalle.nombre,
        capacidad: detalle.capacidad,
        precioBase: detalle.precioBase,
        descripcion: detalle.descripcion ?? '',
      });
      this.editandoId.set(id);
    } catch (error) {
      this.toast.error(toApiError(error).message);
    }
  }

  protected guardarEdicion(id: number): void {
    if (this.editarForm.invalid) {
      this.editarForm.markAllAsTouched();
      return;
    }
    const value = this.editarForm.getRawValue();
    this.guardando.set(true);
    this.error.set(null);
    this.ownerApi
      .actualizarCancha(id, {
        nombre: value.nombre.trim(),
        capacidad: value.capacidad,
        precioBase: value.precioBase,
        descripcion: value.descripcion.trim() || null,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.editandoId.set(null);
          this.toast.success('Cancha actualizada.');
          void this.store.cargarCanchas();
          if (this.fotosCanchaId() === id) {
            void this.cargarFotos(id);
          }
        },
        error: (error) => {
          this.error.set(toApiError(error).message);
          this.guardando.set(false);
        },
      });
  }

  protected async abrirFotos(id: number): Promise<void> {
    this.fotosCanchaId.set(this.fotosCanchaId() === id ? null : id);
    this.fotoForm.reset({ url: '', orden: 1, principal: false });
    if (this.fotosCanchaId() === id) {
      await this.cargarFotos(id);
    }
  }

  protected agregarFoto(id: number): void {
    if (this.fotoForm.invalid) {
      this.fotoForm.markAllAsTouched();
      return;
    }
    const value = this.fotoForm.getRawValue();
    this.guardando.set(true);
    this.ownerApi
      .agregarFoto(id, {
        url: value.url.trim(),
        orden: value.orden,
        principal: value.principal,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.toast.success('Foto agregada.');
          void this.cargarFotos(id);
        },
        error: (error) => {
          this.toast.error(toApiError(error).message);
          this.guardando.set(false);
        },
      });
  }

  protected async eliminarFoto(idCancha: number, idFoto: number): Promise<void> {
    const ok = await this.confirm.confirm({
      title: 'Quitar foto',
      message: 'La foto dejará de mostrarse en el detalle público.',
      confirmText: 'Quitar',
      danger: true,
    });
    if (!ok) {
      return;
    }
    this.ownerApi.eliminarFoto(idCancha, idFoto).subscribe({
      next: () => {
        this.toast.success('Foto eliminada.');
        void this.cargarFotos(idCancha);
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  private async cargarFotos(idCancha: number): Promise<void> {
    try {
      const detalle = await firstValueFrom(this.venues.detalle(idCancha));
      this.fotos.set(detalle.fotos);
    } catch (error) {
      this.toast.error(toApiError(error).message);
    }
  }

  protected async eliminarCancha(id: number): Promise<void> {    const ok = await this.confirm.confirm({
      title: 'Eliminar cancha',
      message: 'La cancha se dará de baja lógica. No se puede eliminar si tiene reservas vigentes.',
      confirmText: 'Eliminar',
      danger: true,
    });
    if (!ok) {
      return;
    }
    this.ownerApi.eliminarCancha(id).subscribe({
      next: () => {
        this.toast.success('Cancha eliminada.');
        void this.store.cargarCanchas();
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected crearCochera(): void {
    const idEstablecimiento = this.store.establecimientoId();
    if (!idEstablecimiento) {
      return;
    }
    if (this.cocheraForm.invalid) {
      this.cocheraForm.markAllAsTouched();
      return;
    }
    const value = this.cocheraForm.getRawValue();
    this.guardando.set(true);
    this.error.set(null);
    this.ownerApi
      .crearCochera(idEstablecimiento, {
        nombre: value.nombre.trim(),
        capacidad: value.capacidad,
        tarifaHora: value.tarifaHora,
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.cocheraForm.reset({ nombre: '', capacidad: 10, tarifaHora: 5 });
          this.toast.success('Cochera agregada.');
          void this.cargarCocheras();
        },
        error: (error) => {
          this.error.set(toApiError(error).message);
          this.guardando.set(false);
        },
      });
  }

  protected async eliminarCochera(id: number): Promise<void> {
    const idEstablecimiento = this.store.establecimientoId();
    if (!idEstablecimiento) {
      return;
    }
    const ok = await this.confirm.confirm({
      title: 'Eliminar cochera',
      message: 'La cochera se dará de baja lógica.',
      confirmText: 'Eliminar',
      danger: true,
    });
    if (!ok) {
      return;
    }
    this.ownerApi.eliminarCochera(idEstablecimiento, id).subscribe({
      next: () => {
        this.toast.success('Cochera eliminada.');
        void this.cargarCocheras();
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  private async cargarCocheras(): Promise<void> {
    const idEstablecimiento = this.store.establecimientoId();
    if (!idEstablecimiento) {
      this.cocheras.set([]);
      return;
    }
    try {
      this.cocheras.set(await firstValueFrom(this.ownerApi.cocheras(idEstablecimiento)));
    } catch (error) {
      this.error.set(toApiError(error).message);
    }
  }

  private async cargarDistritos(): Promise<void> {
    try {
      this.distritos.set(await firstValueFrom(this.venues.distritos()));
    } catch {
      // el formulario de establecimiento queda sin opciones si falla el catalogo
    }
  }
}
