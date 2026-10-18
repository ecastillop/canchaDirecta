package pe.com.canchadirecta.engagement.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.engagement.application.port.in.ModerarReclamosUseCase;
import pe.com.canchadirecta.engagement.application.port.in.ReclamosUseCase;
import pe.com.canchadirecta.engagement.application.port.out.ReclamoRepositorio;
import pe.com.canchadirecta.engagement.domain.model.Reclamo;
import pe.com.canchadirecta.engagement.domain.model.ReclamoSeguimiento;
import pe.com.canchadirecta.shared.application.port.out.AvisosUsuario;
import pe.com.canchadirecta.shared.application.port.out.RegistroAuditoria;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Moderacion de reclamos por la administracion (F07 sobre CU06.6).
 *
 * <p>Transiciones admitidas: `ABIERTO` y `RESUELTO` pueden pasar a `EN_REVISION`; desde
 * `ABIERTO`/`EN_REVISION`/`RESUELTO` se puede `RESOLVER` o `CERRAR`; `CERRADO` es terminal.
 * Repetir el estado actual es `409`. El cambio se aplica de forma condicional al estado leido,
 * de modo que dos moderadores simultaneos no pisan la decision del otro.</p>
 */
@ApplicationScoped
public class ModerarReclamosService implements ModerarReclamosUseCase {

    private static final int LIMITE_DEFECTO = 50;

    private static final Map<Reclamo.Estado, Set<Reclamo.Estado>> TRANSICIONES = Map.of(
            Reclamo.Estado.ABIERTO, EnumSet.of(Reclamo.Estado.EN_REVISION, Reclamo.Estado.RESUELTO,
                    Reclamo.Estado.CERRADO),
            Reclamo.Estado.EN_REVISION, EnumSet.of(Reclamo.Estado.RESUELTO, Reclamo.Estado.CERRADO),
            Reclamo.Estado.RESUELTO, EnumSet.of(Reclamo.Estado.EN_REVISION, Reclamo.Estado.CERRADO),
            Reclamo.Estado.CERRADO, EnumSet.noneOf(Reclamo.Estado.class));

    private final ReclamoRepositorio reclamos;
    private final AvisosUsuario avisos;
    private final RegistroAuditoria auditoria;
    private final Reloj reloj;

    @Inject
    public ModerarReclamosService(ReclamoRepositorio reclamos, AvisosUsuario avisos,
                                  RegistroAuditoria auditoria, Reloj reloj) {
        this.reclamos = reclamos;
        this.avisos = avisos;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Override
    public List<Reclamo> listar(String estado, int limite) {
        return reclamos.listar(estadoDe(estado), limite > 0 ? limite : LIMITE_DEFECTO);
    }

    @Override
    public ReclamosUseCase.Detalle detalle(Long idReclamo) {
        Reclamo reclamo = buscar(idReclamo);
        return new ReclamosUseCase.Detalle(reclamo, reclamos.seguimientos(reclamo.id()));
    }

    @Override
    @Transactional
    public Reclamo cambiarEstado(Long idReclamo, Long idAdmin, String estado, String comentario) {
        Reclamo actual = buscar(idReclamo);
        Reclamo.Estado nuevo = estadoDe(estado);
        if (nuevo == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El estado es obligatorio.");
        }
        if (!TRANSICIONES.getOrDefault(actual.estado(), Set.of()).contains(nuevo)) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "No se puede pasar de " + actual.estado() + " a " + nuevo + ".");
        }
        if (!reclamos.cambiarEstado(actual.id(), actual.estado(), nuevo)) {
            throw new DomainException(ErrorCodes.CONFLICTO, "El reclamo cambio mientras se moderaba.");
        }
        if (comentario != null && !comentario.trim().isEmpty()) {
            reclamos.agregarSeguimiento(new ReclamoSeguimiento(null, actual.id(), idAdmin,
                    comentario, reloj.ahora()));
        }
        avisos.avisar(actual.idUsuario(), "Reclamo actualizado",
                "Tu reclamo #" + actual.id() + " paso a " + nuevo + ".");
        auditoria.registrar(idAdmin, "RECLAMO_" + nuevo.name(), "reclamo",
                String.valueOf(actual.id()),
                "de=" + actual.estado() + " a=" + nuevo
                        + (comentario == null || comentario.isBlank() ? "" : " comentario=" + comentario.trim()));
        return new Reclamo(actual.id(), actual.idUsuario(), actual.idReserva(), actual.asunto(),
                actual.descripcion(), nuevo, actual.fechaRegistro());
    }

    @Override
    @Transactional
    public ReclamoSeguimiento responder(Long idReclamo, Long idAdmin, String comentario) {
        Reclamo actual = buscar(idReclamo);
        if (actual.estado() == Reclamo.Estado.CERRADO) {
            throw new DomainException(ErrorCodes.CONFLICTO, "El reclamo esta cerrado.");
        }
        ReclamoSeguimiento seguimiento = reclamos.agregarSeguimiento(
                new ReclamoSeguimiento(null, actual.id(), idAdmin, comentario, reloj.ahora()));
        avisos.avisar(actual.idUsuario(), "Respuesta a tu reclamo",
                "La administracion respondio tu reclamo #" + actual.id() + ".");
        auditoria.registrar(idAdmin, "RECLAMO_RESPUESTA", "reclamo",
                String.valueOf(actual.id()), "comentario=" + comentario.trim());
        return seguimiento;
    }

    private Reclamo buscar(Long idReclamo) {
        if (idReclamo == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El reclamo es obligatorio.");
        }
        return reclamos.buscar(idReclamo)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "El reclamo no existe."));
    }

    /** `null`/vacio = sin filtro; un valor desconocido es 400. */
    private static Reclamo.Estado estadoDe(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        String limpio = estado.trim().toUpperCase(Locale.ROOT);
        for (Reclamo.Estado candidato : Reclamo.Estado.values()) {
            if (candidato.name().equals(limpio)) {
                return candidato;
            }
        }
        throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "Estado de reclamo desconocido: " + estado + ".");
    }
}
