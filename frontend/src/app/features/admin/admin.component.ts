import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal, computed } from '@angular/core';
import { FormsModule, ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Dialog } from '@angular/cdk/dialog';
import { firstValueFrom } from 'rxjs';
import {
  AdminApi,
  AdminCochera,
  AdminEstablecimiento,
  AdminPago,
  AdminPromocion,
  AdminPropietario,
  AdminReclamo,
  AdminReserva,
  AdminUsuario,
  Auditoria,
} from '../../core/api/admin.api';
import { toApiError } from '../../core/http/api-error';
import { CdButtonDirective } from '../../shared/ui/button/cd-button.directive';
import { ConfirmService } from '../../shared/ui/confirm/confirm.service';
import { CdInputDirective } from '../../shared/ui/input/cd-input.directive';
import { CdEmptyStateComponent } from '../../shared/ui/states/cd-empty-state.component';
import { CdErrorStateComponent } from '../../shared/ui/states/cd-error-state.component';
import { CdSkeletonComponent } from '../../shared/ui/skeleton/cd-skeleton.component';
import { CdPaginatorComponent } from '../../shared/ui/pagination/cd-paginator.component';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { AdminRolesDialogComponent } from './admin-roles-dialog.component';

type Tab = 'propietarios' | 'reclamos' | 'usuarios' | 'promociones' | 'cocheras' | 'supervision' | 'auditoria';
type SupervisionTab = 'establecimientos' | 'reservas' | 'pagos';

@Component({
  selector: 'app-admin',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    CdButtonDirective,
    CdInputDirective,
    CdSkeletonComponent,
    CdEmptyStateComponent,
    CdErrorStateComponent,
    CdPaginatorComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cd-container cd-page">
      <header class="cd-page__header">
        <h1 class="cd-page__title">Administración de plataforma</h1>
        <p class="cd-muted">Valida propietarios, modera reclamos y supervisa la operación.</p>
      </header>

      <nav class="tabs" aria-label="Secciones de administración">
        @for (item of tabs; track item.id) {
          <button
            type="button"
            [class.activo]="tab() === item.id"
            [attr.aria-pressed]="tab() === item.id"
            (click)="cambiarTab(item.id)"
          >
            {{ item.etiqueta }}
          </button>
        }
      </nav>

      @if (cargando()) {
        <div class="cd-stack">
          @for (i of [1, 2, 3]; track i) {
            <cd-skeleton height="90px" radius="12px" />
          }
        </div>
      } @else if (error()) {
        <cd-error-state [message]="error()!" (retry)="recargar()" />
      } @else {
        <!-- PROPIETARIOS -->
        @if (tab() === 'propietarios') {
          <div class="filtros" role="group" aria-label="Filtrar propietarios">
            @for (estado of ['', 'PENDIENTE', 'VALIDADO', 'RECHAZADO']; track estado) {
              <button
                type="button"
                class="chip"
                [class.chip--activo]="filtroPropietarios() === estado"
                (click)="filtrarPropietarios(estado)"
              >
                {{ estado || 'Todos' }}
              </button>
            }
          </div>
          @if (propietarios().length === 0) {
            <cd-empty-state title="Sin solicitudes" message="No hay propietarios con ese filtro." />
          } @else {
            <div class="cd-stack">
              @for (propietario of propietariosPagina(); track propietario.id) {
                <article class="cd-card fila">
                  <div>
                    <strong>{{ propietario.nombreCompleto }}</strong>
                    <p class="cd-xs cd-muted">
                      {{ propietario.correo }} · documento {{ propietario.documento }} · usuario
                      #{{ propietario.idUsuario }}
                    </p>
                    <span class="cd-badge" [class]="clasePropietario(propietario.estadoValidacion)">
                      {{ propietario.estadoValidacion }}
                    </span>
                  </div>
                  <div class="acciones">
                    @if (propietario.estadoValidacion !== 'VALIDADO') {
                      <button cdButton size="sm" type="button" (click)="validar(propietario, 'VALIDADO')">
                        Validar
                      </button>
                    }
                    @if (propietario.estadoValidacion !== 'RECHAZADO') {
                      <button cdButton variant="danger" size="sm" type="button" (click)="iniciarRechazo(propietario.id)">
                        Rechazar
                      </button>
                    }
                  </div>
                  @if (rechazandoId() === propietario.id) {
                    <div class="motivo">
                      <label class="cd-field">
                        <span class="cd-field__label">Motivo del rechazo (obligatorio)</span>
                        <input cdInput [(ngModel)]="motivoRechazo" maxlength="500" />
                      </label>
                      <div class="cd-row">
                        <button cdButton variant="danger" size="sm" type="button" (click)="confirmarRechazo(propietario)">
                          Confirmar rechazo
                        </button>
                        <button cdButton variant="ghost" size="sm" type="button" (click)="rechazandoId.set(null)">
                          Cancelar
                        </button>
                      </div>
                    </div>
                  }
                </article>
              }
            </div>
            <cd-paginator
              [pagina]="paginaPropietarios()"
              [total]="propietarios().length"
              [tamano]="tamanoPagina"
              etiqueta="Paginación de propietarios"
              (paginaChange)="paginaPropietarios.set($event)"
            />
          }
        }
        @if (tab() === 'reclamos') {
          <div class="dos-columnas">
            <div class="cd-stack">
              @for (reclamo of reclamosPagina(); track reclamo.id) {
                <button type="button" class="reclamo" [class.activo]="reclamoSel()?.id === reclamo.id" (click)="seleccionarReclamo(reclamo)">
                  <span class="cd-row cd-row--between">
                    <strong>{{ reclamo.asunto }}</strong>
                    <span class="cd-badge">{{ reclamo.estado }}</span>
                  </span>
                  <span class="cd-xs cd-muted">
                    #{{ reclamo.id }} · {{ reclamo.fechaRegistro | date: 'd MMM yyyy, HH:mm' }} · usuario
                    #{{ reclamo.idUsuario }}
                  </span>
                </button>
              } @empty {
                <cd-empty-state title="Sin reclamos" message="No hay reclamos registrados." />
              }
              <cd-paginator
                [pagina]="paginaReclamos()"
                [total]="reclamos().length"
                [tamano]="tamanoPagina"
                etiqueta="Paginación de reclamos"
                (paginaChange)="paginaReclamos.set($event)"
              />
            </div>

            @if (reclamoSel(); as r) {
              <article class="cd-card">
                <h2 class="seccion">{{ r.asunto }}</h2>
                <p class="cd-small">{{ r.descripcion }}</p>
                <p class="cd-xs cd-muted">
                  Usuario #{{ r.idUsuario }} @if (r.idReserva) { · reserva #{{ r.idReserva }} }
                </p>

                <h3 class="subseccion">Seguimientos</h3>
                @if (r.seguimientos?.length) {
                  <ul class="seguimientos">
                    @for (s of r.seguimientos; track s.id) {
                      <li>
                        {{ s.comentario }}
                        <span class="cd-xs cd-muted">{{ s.fecha | date: 'd MMM, HH:mm' }}</span>
                      </li>
                    }
                  </ul>
                } @else {
                  <p class="cd-xs cd-muted">Sin seguimientos.</p>
                }

                <div class="cd-stack formulario">
                  <label class="cd-field">
                    <span class="cd-field__label">Nuevo estado</span>
                    <select cdInput [(ngModel)]="nuevoEstado">
                      <option value="EN_REVISION">En revisión</option>
                      <option value="RESUELTO">Resuelto</option>
                      <option value="CERRADO">Cerrado</option>
                    </select>
                  </label>
                  <label class="cd-field">
                    <span class="cd-field__label">Comentario</span>
                    <textarea cdInput [(ngModel)]="comentarioAdmin" rows="2" maxlength="800"></textarea>
                  </label>
                  <div class="cd-row">
                    <button cdButton size="sm" type="button" (click)="cambiarEstadoReclamo()">Cambiar estado</button>
                    <button cdButton variant="secondary" size="sm" type="button" (click)="comentarAdmin()">
                      Agregar comentario
                    </button>
                  </div>
                </div>
              </article>
            }
          </div>
        }

        <!-- USUARIOS -->
        @if (tab() === 'usuarios') {
          <form class="buscador" (ngSubmit)="buscarUsuarios()">
            <label class="cd-field">
              <span class="cd-field__label">Buscar por correo</span>
              <input cdInput name="correoAdmin" [(ngModel)]="correoBusqueda" type="search" placeholder="correo@dominio.com" />
            </label>
            <button cdButton type="submit">Buscar</button>
          </form>
          @if (usuarios().length === 0) {
            <cd-empty-state title="Sin usuarios" message="No hay usuarios que coincidan con la busqueda." />
          } @else {
            <div class="tabla-scroll">
              <table class="tabla">
                <thead>
                  <tr>
                    <th scope="col">Usuario</th>
                    <th scope="col">Correo</th>
                    <th scope="col">Teléfono</th>
                    <th scope="col">Roles</th>
                    <th scope="col">Estado</th>
                    <th scope="col">Registro</th>
                    <th scope="col">Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  @for (usuario of usuariosPagina(); track usuario.id) {
                    <tr>
                      <td>{{ usuario.nombreCompleto }}</td>
                      <td>{{ usuario.correo }}</td>
                      <td>{{ usuario.telefono ?? '—' }}</td>
                      <td>{{ usuario.roles.join(', ') }}</td>
                      <td><span class="cd-badge">{{ usuario.estado }}</span></td>
                      <td>{{ usuario.fechaRegistro | date: 'd MMM yyyy' }}</td>
                      <td>
                        <button cdButton variant="secondary" size="sm" type="button" (click)="editarRoles(usuario)">
                          Roles
                        </button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <cd-paginator
              [pagina]="paginaUsuarios()"
              [total]="usuarios().length"
              [tamano]="tamanoPagina"
              etiqueta="Paginación de usuarios"
              (paginaChange)="paginaUsuarios.set($event)"
            />
          }
        }

        <!-- PROMOCIONES -->
        @if (tab() === 'promociones') {
          <section class="cd-card">
            <h2 class="seccion">Promociones de plataforma</h2>
            @if (promocionesAdmin().length === 0) {
              <cd-empty-state title="Sin promociones" message="Crea una promoción global o desde el panel del propietario." />
            } @else {
              <ul class="lista-admin">
                @for (promocion of promocionesAdminPagina(); track promocion.id) {
                  <li>
                    <div>
                      <strong>{{ promocion.codigo }}</strong>
                      <span class="cd-badge" [class]="promocion.estado === 'ACTIVA' ? 'cd-badge--success' : 'cd-badge'">
                        {{ promocion.estado }}
                      </span>
                      <p class="cd-xs cd-muted">
                        {{ promocion.descripcion }} ·
                        @if (promocion.tipoDescuento === 'PORCENTAJE') {
                          {{ promocion.valor }}%
                        } @else {
                          {{ promocion.valor | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}
                        }
                        · {{ promocion.fechaInicio | date: 'd MMM' }} - {{ promocion.fechaFin | date: 'd MMM yyyy' }}
                        · {{ promocion.canchas.length }} cancha(s)
                      </p>
                    </div>
                    <button cdButton variant="secondary" size="sm" type="button" (click)="cambiarEstadoPromocion(promocion)">
                      {{ promocion.estado === 'ACTIVA' ? 'Desactivar' : 'Activar' }}
                    </button>
                  </li>
                }
              </ul>
              <cd-paginator
                [pagina]="paginaPromociones()"
                [total]="promocionesAdmin().length"
                [tamano]="tamanoPagina"
                etiqueta="Paginación de promociones"
                (paginaChange)="paginaPromociones.set($event)"
              />
            }
          </section>

          <section class="cd-card formulario">
            <h2 class="seccion">Nueva promoción global</h2>
            <form class="cd-stack" [formGroup]="promocionForm" (ngSubmit)="crearPromocion()" novalidate>
              <div class="dos">
                <label class="cd-field">
                  <span class="cd-field__label">Código</span>
                  <input cdInput formControlName="codigo" maxlength="30" />
                </label>
                <label class="cd-field">
                  <span class="cd-field__label">Descripción</span>
                  <input cdInput formControlName="descripcion" maxlength="200" />
                </label>
              </div>
              <div class="tres">
                <label class="cd-field">
                  <span class="cd-field__label">Tipo</span>
                  <select cdInput formControlName="tipoDescuento">
                    <option value="PORCENTAJE">Porcentaje</option>
                    <option value="MONTO">Monto fijo</option>
                  </select>
                </label>
                <label class="cd-field">
                  <span class="cd-field__label">Valor</span>
                  <input cdInput type="number" min="0.01" step="0.01" formControlName="valor" />
                </label>
                <label class="cd-field">
                  <span class="cd-field__label">Desde</span>
                  <input cdInput type="date" formControlName="fechaInicio" />
                </label>
                <label class="cd-field">
                  <span class="cd-field__label">Hasta</span>
                  <input cdInput type="date" formControlName="fechaFin" />
                </label>
              </div>
              <button cdButton type="submit" [loading]="guardandoPromocion()">Crear promoción</button>
            </form>
          </section>
        }

        <!-- COCHERAS -->
        @if (tab() === 'cocheras') {
          @if (cocherasAdmin().length === 0) {
            <cd-empty-state title="Sin cocheras" message="Los propietarios aún no registran cocheras." />
          } @else {
            <div class="tabla-scroll">
              <table class="tabla">
                <thead>
                  <tr>
                    <th scope="col">ID</th>
                    <th scope="col">Establecimiento</th>
                    <th scope="col">Cochera</th>
                    <th scope="col">Capacidad</th>
                    <th scope="col">Tarifa</th>
                    <th scope="col">Estado</th>
                    <th scope="col">Acción</th>
                  </tr>
                </thead>
                <tbody>
                  @for (cochera of cocherasAdminPagina(); track cochera.id) {
                    <tr>
                      <td>{{ cochera.id }}</td>
                      <td>{{ cochera.establecimiento }}</td>
                      <td>{{ cochera.nombre }}</td>
                      <td>{{ cochera.capacidad }}</td>
                      <td>{{ cochera.tarifaHora | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</td>
                      <td><span class="cd-badge">{{ cochera.estado }}</span></td>
                      <td>
                        <button cdButton variant="secondary" size="sm" type="button" (click)="cambiarEstadoCochera(cochera)">
                          {{ cochera.estado === 'ACTIVA' ? 'Desactivar' : 'Activar' }}
                        </button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <cd-paginator
              [pagina]="paginaCocheras()"
              [total]="cocherasAdmin().length"
              [tamano]="tamanoPagina"
              etiqueta="Paginación de cocheras"
              (paginaChange)="paginaCocheras.set($event)"
            />
          }
        }

        <!-- SUPERVISION -->
        @if (tab() === 'supervision') {
          <div class="filtros" role="group" aria-label="Tipo de supervisión">
            @for (sub of supervisionTabs; track sub) {
              <button
                type="button"
                class="chip"
                [class.chip--activo]="supervision() === sub"
                (click)="cambiarSupervision(sub)"
              >
                {{ sub }}
              </button>
            }
          </div>

          @if (supervision() === 'establecimientos') {
            <div class="tabla-scroll">
              <table class="tabla">
                <thead>
                  <tr>
                    <th scope="col">ID</th>
                    <th scope="col">Establecimiento</th>
                    <th scope="col">Distrito</th>
                    <th scope="col">Propietario</th>
                    <th scope="col">Canchas</th>
                    <th scope="col">Estado</th>
                  </tr>
                </thead>
                <tbody>
                  @for (est of establecimientosPagina(); track est.id) {
                    <tr>
                      <td>{{ est.id }}</td>
                      <td>{{ est.nombre }}</td>
                      <td>{{ est.distrito }}</td>
                      <td>{{ est.propietario }}</td>
                      <td>{{ est.canchas }}</td>
                      <td>{{ est.estado }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <cd-paginator
              [pagina]="paginaSupervision()"
              [total]="establecimientos().length"
              [tamano]="tamanoPagina"
              etiqueta="Paginación de establecimientos"
              (paginaChange)="paginaSupervision.set($event)"
            />
          }

          @if (supervision() === 'reservas') {
            <div class="tabla-scroll">
              <table class="tabla">
                <thead>
                  <tr>
                    <th scope="col">ID</th>
                    <th scope="col">Cancha</th>
                    <th scope="col">Titular</th>
                    <th scope="col">Inicio</th>
                    <th scope="col">Monto</th>
                    <th scope="col">Estado</th>
                  </tr>
                </thead>
                <tbody>
                  @for (reserva of reservasAdminPagina(); track reserva.id) {
                    <tr>
                      <td>{{ reserva.id }}</td>
                      <td>{{ reserva.cancha }} / {{ reserva.establecimiento }}</td>
                      <td>{{ reserva.titular }}</td>
                      <td>{{ reserva.inicio | date: 'd MMM yyyy, HH:mm' }}</td>
                      <td>{{ reserva.montoCancha | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</td>
                      <td>{{ reserva.estado }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <cd-paginator
              [pagina]="paginaSupervision()"
              [total]="reservasAdmin().length"
              [tamano]="tamanoPagina"
              etiqueta="Paginación de reservas"
              (paginaChange)="paginaSupervision.set($event)"
            />
          }

          @if (supervision() === 'pagos') {
            <div class="tabla-scroll">
              <table class="tabla">
                <thead>
                  <tr>
                    <th scope="col">ID</th>
                    <th scope="col">Reserva</th>
                    <th scope="col">Monto</th>
                    <th scope="col">Medio</th>
                    <th scope="col">Estado</th>
                    <th scope="col">Reembolsado</th>
                    <th scope="col">Fecha</th>
                  </tr>
                </thead>
                <tbody>
                  @for (pago of pagosAdminPagina(); track pago.id) {
                    <tr>
                      <td>{{ pago.id }}</td>
                      <td>#{{ pago.idReserva }}</td>
                      <td>{{ pago.monto | currency: 'PEN' : 'symbol-narrow' : '1.2-2' }}</td>
                      <td>{{ pago.medioPago }}</td>
                      <td>{{ pago.estado }}</td>
                      <td>{{ pago.reembolsado ? 'Si' : 'No' }}</td>
                      <td>{{ pago.fechaPago | date: 'd MMM yyyy, HH:mm' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <cd-paginator
              [pagina]="paginaSupervision()"
              [total]="pagosAdmin().length"
              [tamano]="tamanoPagina"
              etiqueta="Paginación de pagos"
              (paginaChange)="paginaSupervision.set($event)"
            />
          }
        }

        <!-- AUDITORIA -->
        @if (tab() === 'auditoria') {
          @if (auditoria().length === 0) {
            <cd-empty-state title="Sin eventos" message="La auditoria registra validaciones y moderaciones." />
          } @else {
            <div class="tabla-scroll">
              <table class="tabla">
                <thead>
                  <tr>
                    <th scope="col">Fecha</th>
                    <th scope="col">Usuario</th>
                    <th scope="col">Acción</th>
                    <th scope="col">Entidad</th>
                    <th scope="col">Detalle</th>
                  </tr>
                </thead>
                <tbody>
                  @for (evento of auditoriaPagina(); track evento.id) {
                    <tr>
                      <td>{{ evento.fechaEvento | date: 'd MMM yyyy, HH:mm' }}</td>
                      <td>{{ evento.idUsuario ?? '—' }}</td>
                      <td>{{ evento.accion }}</td>
                      <td>{{ evento.entidad }} #{{ evento.idEntidad ?? '—' }}</td>
                      <td>{{ evento.detalle }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <cd-paginator
              [pagina]="paginaAuditoria()"
              [total]="auditoria().length"
              [tamano]="tamanoPagina"
              etiqueta="Paginación de auditoría"
              (paginaChange)="paginaAuditoria.set($event)"
            />
          }
        }
      }
    </div>
  `,
  styles: `
    .tabs {
      display: flex;
      flex-wrap: wrap;
      gap: var(--cd-space-1);
      border-bottom: 1px solid var(--cd-color-border);
      margin-bottom: var(--cd-space-4);
    }
    .tabs button {
      padding: 10px 14px;
      background: transparent;
      border: 0;
      border-bottom: 3px solid transparent;
      color: var(--cd-color-text-muted);
      font-weight: 600;
      font-size: var(--cd-text-sm);
      cursor: pointer;
    }
    .tabs button.activo {
      color: var(--cd-color-primary);
      border-bottom-color: var(--cd-color-accent);
    }
    .filtros {
      display: flex;
      gap: var(--cd-space-2);
      flex-wrap: wrap;
      margin-bottom: var(--cd-space-3);
    }
    .chip {
      padding: 8px 14px;
      border-radius: var(--cd-radius-pill);
      border: 1px solid var(--cd-color-border);
      background: var(--cd-color-surface);
      cursor: pointer;
      font-size: var(--cd-text-sm);
      font-weight: 600;
      color: var(--cd-color-text-muted);
    }
    .chip--activo {
      background: var(--cd-color-primary);
      border-color: var(--cd-color-primary);
      color: var(--cd-color-on-primary);
    }
    .fila {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--cd-space-3);
      flex-wrap: wrap;
    }
    .acciones {
      display: flex;
      gap: var(--cd-space-2);
      flex-wrap: wrap;
    }
    .motivo {
      flex-basis: 100%;
      display: grid;
      gap: var(--cd-space-2);
      padding-top: var(--cd-space-2);
      border-top: 1px solid var(--cd-color-border);
    }
    .dos-columnas {
      display: grid;
      gap: var(--cd-space-4);
    }
    .reclamo {
      display: grid;
      gap: var(--cd-space-1);
      text-align: left;
      padding: var(--cd-space-3);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-sm);
      background: var(--cd-color-surface);
      cursor: pointer;
    }
    .reclamo.activo {
      border-color: var(--cd-color-accent-strong);
    }
    .seccion {
      font-size: var(--cd-text-base);
      margin-bottom: var(--cd-space-2);
    }
    .subseccion {
      font-size: var(--cd-text-sm);
      margin-top: var(--cd-space-4);
      margin-bottom: var(--cd-space-2);
    }
    .seguimientos {
      list-style: none;
      padding: 0;
      display: grid;
      gap: var(--cd-space-2);
      font-size: var(--cd-text-sm);
    }
    .seguimientos li {
      display: flex;
      justify-content: space-between;
      gap: var(--cd-space-2);
      padding: var(--cd-space-2);
      background: var(--cd-color-surface-alt);
      border-radius: var(--cd-radius-sm);
      flex-wrap: wrap;
    }
    .formulario {
      margin-top: var(--cd-space-4);
      padding-top: var(--cd-space-4);
      border-top: 1px solid var(--cd-color-border);
    }
    .buscador {
      display: flex;
      gap: var(--cd-space-2);
      align-items: end;
      flex-wrap: wrap;
      margin-bottom: var(--cd-space-3);
    }
    .tabla-scroll {
      overflow-x: auto;
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-md);
      background: var(--cd-color-surface);
    }
    .tabla {
      width: 100%;
      border-collapse: collapse;
      min-width: 720px;
      font-size: var(--cd-text-sm);
    }
    .tabla th,
    .tabla td {
      padding: var(--cd-space-3);
      border-bottom: 1px solid var(--cd-color-border);
      text-align: left;
      vertical-align: top;
    }
    .tabla thead th {
      background: var(--cd-color-surface-alt);
      font-size: var(--cd-text-xs);
      text-transform: uppercase;
      color: var(--cd-color-text-muted);
    }
    .lista-admin {
      list-style: none;
      padding: 0;
      display: grid;
      gap: var(--cd-space-2);
      margin-top: var(--cd-space-3);
    }
    .lista-admin li {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--cd-space-3);
      padding: var(--cd-space-3);
      border: 1px solid var(--cd-color-border);
      border-radius: var(--cd-radius-sm);
      flex-wrap: wrap;
    }
    .lista-admin .cd-badge {
      margin-left: var(--cd-space-2);
    }
    .formulario {
      margin-top: var(--cd-space-4);
    }
    .dos,
    .tres {
      display: grid;
      gap: var(--cd-space-3);
    }
    @media (min-width: 1024px) {
      .dos-columnas {
        grid-template-columns: 1fr 1.1fr;
        align-items: start;
      }
    }
    @media (min-width: 640px) {
      .dos {
        grid-template-columns: 1fr 2fr;
      }
      .tres {
        grid-template-columns: 1fr 1fr 1fr 1fr;
      }
    }
  `,
})
export class AdminComponent implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  protected readonly tabs: Array<{ id: Tab; etiqueta: string }> = [
    { id: 'propietarios', etiqueta: 'Propietarios' },
    { id: 'reclamos', etiqueta: 'Reclamos' },
    { id: 'usuarios', etiqueta: 'Usuarios' },
    { id: 'promociones', etiqueta: 'Promociones' },
    { id: 'cocheras', etiqueta: 'Cocheras' },
    { id: 'supervision', etiqueta: 'Supervisión' },
    { id: 'auditoria', etiqueta: 'Auditoría' },
  ];
  protected readonly supervisionTabs: SupervisionTab[] = ['establecimientos', 'reservas', 'pagos'];

  protected readonly tab = signal<Tab>('propietarios');
  protected readonly supervision = signal<SupervisionTab>('establecimientos');
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly propietarios = signal<AdminPropietario[]>([]);
  protected readonly filtroPropietarios = signal('');
  protected readonly rechazandoId = signal<number | null>(null);
  protected motivoRechazo = '';
  protected readonly paginaPropietarios = signal(1);
  protected readonly propietariosPagina = computed(() => this.paginar(this.propietarios(), this.paginaPropietarios()));

  protected readonly reclamos = signal<AdminReclamo[]>([]);
  protected readonly reclamoSel = signal<AdminReclamo | null>(null);
  protected readonly paginaReclamos = signal(1);
  protected readonly reclamosPagina = computed(() => this.paginar(this.reclamos(), this.paginaReclamos()));
  protected nuevoEstado = 'EN_REVISION';
  protected comentarioAdmin = '';

  protected readonly usuarios = signal<AdminUsuario[]>([]);
  protected correoBusqueda = '';
  protected readonly paginaUsuarios = signal(1);
  protected readonly usuariosPagina = computed(() => this.paginar(this.usuarios(), this.paginaUsuarios()));
  protected readonly rolesDisponibles = ['USER', 'OWNER', 'PLATFORM_ADMIN'];

  protected readonly establecimientos = signal<AdminEstablecimiento[]>([]);
  protected readonly reservasAdmin = signal<AdminReserva[]>([]);
  protected readonly pagosAdmin = signal<AdminPago[]>([]);
  protected readonly paginaSupervision = signal(1);
  protected readonly establecimientosPagina = computed(() =>
    this.paginar(this.establecimientos(), this.paginaSupervision()),
  );
  protected readonly reservasAdminPagina = computed(() =>
    this.paginar(this.reservasAdmin(), this.paginaSupervision()),
  );
  protected readonly pagosAdminPagina = computed(() =>
    this.paginar(this.pagosAdmin(), this.paginaSupervision()),
  );
  protected readonly promocionesAdmin = signal<AdminPromocion[]>([]);
  protected readonly cocherasAdmin = signal<AdminCochera[]>([]);
  protected readonly paginaPromociones = signal(1);
  protected readonly paginaCocheras = signal(1);
  protected readonly promocionesAdminPagina = computed(() =>
    this.paginar(this.promocionesAdmin(), this.paginaPromociones()),
  );
  protected readonly cocherasAdminPagina = computed(() =>
    this.paginar(this.cocherasAdmin(), this.paginaCocheras()),
  );
  protected readonly guardandoPromocion = signal(false);
  protected readonly auditoria = signal<Auditoria[]>([]);
  protected readonly paginaAuditoria = signal(1);
  protected readonly auditoriaPagina = computed(() => this.paginar(this.auditoria(), this.paginaAuditoria()));
  protected readonly tamanoPagina = 10;

  private readonly fb = inject(FormBuilder);
  private readonly dialog = inject(Dialog);

  protected readonly promocionForm = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.maxLength(30)]],
    descripcion: ['', [Validators.required, Validators.maxLength(200)]],
    tipoDescuento: ['PORCENTAJE' as 'PORCENTAJE' | 'MONTO', [Validators.required]],
    valor: [10, [Validators.required, Validators.min(0.01)]],
    fechaInicio: ['', [Validators.required]],
    fechaFin: ['', [Validators.required]],
  });

  ngOnInit(): void {
    void this.cargarTab();
  }

  protected cambiarTab(tab: Tab): void {
    this.tab.set(tab);
    this.paginaPropietarios.set(1);
    this.paginaReclamos.set(1);
    this.paginaUsuarios.set(1);
    this.paginaPromociones.set(1);
    this.paginaCocheras.set(1);
    this.paginaSupervision.set(1);
    this.paginaAuditoria.set(1);
    void this.cargarTab();
  }

  protected cambiarSupervision(sub: SupervisionTab): void {
    this.supervision.set(sub);
    this.paginaSupervision.set(1);
    void this.cargarTab();
  }

  protected filtrarPropietarios(estado: string): void {
    this.filtroPropietarios.set(estado);
    this.paginaPropietarios.set(1);
    void this.cargarTab();
  }

  private paginar<T>(items: T[], pagina: number): T[] {
    const desde = (pagina - 1) * this.tamanoPagina;
    return items.slice(desde, desde + this.tamanoPagina);
  }

  protected clasePropietario(estado: AdminPropietario['estadoValidacion']): string {
    return estado === 'VALIDADO'
      ? 'cd-badge cd-badge--success'
      : estado === 'RECHAZADO'
        ? 'cd-badge cd-badge--danger'
        : 'cd-badge cd-badge--warning';
  }

  protected iniciarRechazo(id: number): void {
    this.rechazandoId.set(id);
    this.motivoRechazo = '';
  }

  protected async validar(propietario: AdminPropietario, estado: 'VALIDADO' | 'RECHAZADO'): Promise<void> {
    const ok = await this.confirm.confirm({
      title: estado === 'VALIDADO' ? 'Validar propietario' : 'Rechazar propietario',
      message:
        estado === 'VALIDADO'
          ? `Se habilitara a ${propietario.nombreCompleto} para publicar canchas.`
          : `Se rechazara la solicitud de ${propietario.nombreCompleto}.`,
      confirmText: estado === 'VALIDADO' ? 'Validar' : 'Rechazar',
      danger: estado === 'RECHAZADO',
    });
    if (!ok) {
      return;
    }
    this.api.validarPropietario(propietario.id, estado).subscribe({
      next: async (actualizado) => {
        this.propietarios.update((lista) => lista.map((p) => (p.id === actualizado.id ? actualizado : p)));
        if (estado === 'VALIDADO') {
          await this.asignarRolOwner(actualizado.idUsuario);
        }
        this.toast.success(estado === 'VALIDADO' ? 'Propietario validado.' : 'Solicitud rechazada.');
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected confirmarRechazo(propietario: AdminPropietario): void {
    const motivo = this.motivoRechazo.trim();
    if (!motivo) {
      this.toast.error('El motivo es obligatorio para rechazar.');
      return;
    }
    this.api.validarPropietario(propietario.id, 'RECHAZADO', motivo).subscribe({
      next: (actualizado) => {
        this.propietarios.update((lista) => lista.map((p) => (p.id === actualizado.id ? actualizado : p)));
        this.rechazandoId.set(null);
        this.toast.success('Solicitud rechazada.');
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected seleccionarReclamo(reclamo: AdminReclamo): void {
    this.api.reclamo(reclamo.id).subscribe({
      next: (detalle) => {
        this.reclamoSel.set(detalle);
        this.nuevoEstado = detalle.estado === 'CERRADO' ? 'CERRADO' : 'EN_REVISION';
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected cambiarEstadoReclamo(): void {
    const reclamo = this.reclamoSel();
    if (!reclamo) {
      return;
    }
    this.api.cambiarEstadoReclamo(reclamo.id, this.nuevoEstado, this.comentarioAdmin.trim() || undefined).subscribe({
      next: () => {
        this.comentarioAdmin = '';
        this.toast.success('Estado del reclamo actualizado.');
        void this.cargarTab();
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected comentarAdmin(): void {
    const reclamo = this.reclamoSel();
    const comentario = this.comentarioAdmin.trim();
    if (!reclamo || !comentario) {
      this.toast.error('Escribe un comentario.');
      return;
    }
    this.api.comentarReclamo(reclamo.id, comentario).subscribe({
      next: () => {
        this.comentarioAdmin = '';
        this.toast.success('Comentario agregado.');
        this.seleccionarReclamo(reclamo);
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected buscarUsuarios(): void {
    this.paginaUsuarios.set(1);
    void this.cargarUsuarios();
  }

  protected crearPromocion(): void {
    if (this.promocionForm.invalid) {
      this.promocionForm.markAllAsTouched();
      return;
    }
    const value = this.promocionForm.getRawValue();
    this.guardandoPromocion.set(true);
    this.api
      .crearPromocion({
        codigo: value.codigo.trim().toUpperCase(),
        descripcion: value.descripcion.trim(),
        tipoDescuento: value.tipoDescuento,
        valor: value.valor,
        fechaInicio: value.fechaInicio,
        fechaFin: value.fechaFin,
      })
      .subscribe({
        next: (promocion) => {
          this.promocionesAdmin.update((lista) => [promocion, ...lista]);
          this.guardandoPromocion.set(false);
          this.promocionForm.reset({
            codigo: '',
            descripcion: '',
            tipoDescuento: 'PORCENTAJE',
            valor: 10,
            fechaInicio: '',
            fechaFin: '',
          });
          this.toast.success('Promocion creada.');
        },
        error: (error) => {
          this.toast.error(toApiError(error).message);
          this.guardandoPromocion.set(false);
        },
      });
  }

  protected cambiarEstadoPromocion(promocion: AdminPromocion): void {
    const estado = promocion.estado === 'ACTIVA' ? 'INACTIVA' : 'ACTIVA';
    this.api.estadoPromocion(promocion.id, estado).subscribe({
      next: (actualizada) => {
        this.promocionesAdmin.update((lista) => lista.map((p) => (p.id === actualizada.id ? actualizada : p)));
        this.toast.success(`Promocion ${estado.toLowerCase()}.`);
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected cambiarEstadoCochera(cochera: AdminCochera): void {
    const estado = cochera.estado === 'ACTIVA' ? 'INACTIVA' : 'ACTIVA';
    this.api.estadoCochera(cochera.id, estado).subscribe({
      next: (actualizada) => {
        this.cocherasAdmin.update((lista) => lista.map((c) => (c.id === actualizada.id ? actualizada : c)));
        this.toast.success(`Cochera ${estado.toLowerCase()}.`);
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected editarRoles(usuario: AdminUsuario): void {
    const ref = this.dialog.open<string[] | undefined>(AdminRolesDialogComponent, {
      data: { usuario, disponibles: this.rolesDisponibles },
      ariaLabelledBy: 'roles-dialog-title',
      autoFocus: 'dialog',
    });
    ref.closed.subscribe((roles) => {
      if (roles) {
        this.guardarRoles(usuario, roles);
      }
    });
  }

  protected guardarRoles(usuario: AdminUsuario, roles: string[]): void {
    this.api.cambiarRoles(usuario.id, roles).subscribe({
      next: (actualizado) => {
        this.usuarios.update((lista) => lista.map((u) => (u.id === actualizado.id ? actualizado : u)));
        this.toast.success('Roles actualizados.');
      },
      error: (error) => this.toast.error(toApiError(error).message),
    });
  }

  protected recargar(): void {
    void this.cargarTab();
  }

  private async cargarTab(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      switch (this.tab()) {
        case 'propietarios':
          this.propietarios.set(await firstValueFrom(this.api.propietarios(this.filtroPropietarios() || undefined)));
          break;
        case 'reclamos':
          this.reclamos.set(await firstValueFrom(this.api.reclamos()));
          break;
        case 'usuarios':
          await this.cargarUsuarios();
          break;
        case 'promociones':
          this.promocionesAdmin.set(await firstValueFrom(this.api.promociones()));
          break;
        case 'cocheras':
          this.cocherasAdmin.set(await firstValueFrom(this.api.cocheras()));
          break;
        case 'supervision':
          await this.cargarSupervision();
          break;
        case 'auditoria':
          this.auditoria.set(await firstValueFrom(this.api.auditoria()));
          break;
      }
    } catch (error) {
      this.error.set(toApiError(error).message);
    } finally {
      this.cargando.set(false);
    }
  }

  private async cargarUsuarios(): Promise<void> {
    this.usuarios.set(await firstValueFrom(this.api.usuarios(this.correoBusqueda.trim() || undefined)));
  }

  /** Validar al propietario no otorga el rol: se asigna OWNER explicitamente. */
  private async asignarRolOwner(idUsuario: number): Promise<void> {
    try {
      const usuario = await firstValueFrom(this.api.usuario(idUsuario));
      if (!usuario.roles.includes('OWNER')) {
        await firstValueFrom(this.api.cambiarRoles(idUsuario, [...usuario.roles, 'OWNER']));
        this.toast.success('Rol OWNER asignado al propietario.');
      }
    } catch (error) {
      this.toast.error(`Propietario validado, pero no se pudo asignar OWNER: ${toApiError(error).message}`);
    }
  }

  private async cargarSupervision(): Promise<void> {
    switch (this.supervision()) {
      case 'establecimientos':
        this.establecimientos.set(await firstValueFrom(this.api.establecimientos()));
        break;
      case 'reservas':
        this.reservasAdmin.set(await firstValueFrom(this.api.reservas()));
        break;
      case 'pagos':
        this.pagosAdmin.set(await firstValueFrom(this.api.pagos()));
        break;
    }
  }
}
