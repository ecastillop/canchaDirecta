package pe.com.canchadirecta.bookings.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.com.canchadirecta.bookings.application.dto.CanchaInfo;
import pe.com.canchadirecta.bookings.application.dto.DisponibilidadDia;
import pe.com.canchadirecta.bookings.application.dto.FranjaDisponible;
import pe.com.canchadirecta.bookings.application.dto.HorarioSemanal;
import pe.com.canchadirecta.bookings.application.dto.OcupacionFranja;
import pe.com.canchadirecta.bookings.application.port.in.ConsultarDisponibilidadUseCase;
import pe.com.canchadirecta.bookings.application.port.out.ReservaRepositorio;
import pe.com.canchadirecta.bookings.domain.model.FranjaHoraria;
import pe.com.canchadirecta.shared.application.port.out.ConfiguracionPlataforma;
import pe.com.canchadirecta.shared.application.port.out.Reloj;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calculo de disponibilidad de una cancha para una fecha local.
 *
 * <p>La ventana de atencion sale de `horario_cancha` (hora local del establecimiento).
 * Mientras la cancha no tenga ningun horario configurado se usa la ventana por defecto de
 * configuracion (`cd.reservas.ventana-defecto-*`); en cuanto el propietario define agenda,
 * los dias sin horarios quedan cerrados.</p>
 *
 * <p>La fecha se interpreta en la zona de operacion y las franjas se devuelven como
 * instantes UTC, igual que el resto de la API.</p>
 */
@ApplicationScoped
public class ConsultarDisponibilidadService implements ConsultarDisponibilidadUseCase {

    private final ReservaRepositorio reservas;
    private final Reloj reloj;
    private final ConfiguracionPlataforma configuracion;
    private final ZoneId zona;
    private final LocalTime ventanaDefectoInicio;
    private final LocalTime ventanaDefectoFin;

    @Inject
    public ConsultarDisponibilidadService(
            ReservaRepositorio reservas,
            Reloj reloj,
            ConfiguracionPlataforma configuracion,
            @ConfigProperty(name = "cd.zona-horaria", defaultValue = "America/Lima") String zonaHoraria,
            @ConfigProperty(name = "cd.reservas.ventana-defecto-inicio", defaultValue = "06:00") String inicioDefecto,
            @ConfigProperty(name = "cd.reservas.ventana-defecto-fin", defaultValue = "23:00") String finDefecto) {
        this.reservas = reservas;
        this.reloj = reloj;
        this.configuracion = configuracion;
        this.zona = ZoneId.of(zonaHoraria);
        this.ventanaDefectoInicio = LocalTime.parse(inicioDefecto);
        this.ventanaDefectoFin = LocalTime.parse(finDefecto);
    }

    @Override
    public DisponibilidadDia consultar(Long idCancha, LocalDate fecha, long duracionMinutos) {
        if (idCancha == null || fecha == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La cancha y la fecha son obligatorias.");
        }
        if (duracionMinutos < FranjaHoraria.MINUTOS_MINIMOS || duracionMinutos > FranjaHoraria.MINUTOS_MAXIMOS) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La duracion debe estar entre " + FranjaHoraria.MINUTOS_MINIMOS + " y "
                            + FranjaHoraria.MINUTOS_MAXIMOS + " minutos.");
        }

        Instant ahora = reloj.ahora();
        long diasAnticipacionMax = configuracion.diasAnticipacionMax();
        LocalDate hoy = LocalDate.ofInstant(ahora, zona);
        if (fecha.isBefore(hoy)) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "No se puede consultar la disponibilidad de una fecha pasada.");
        }
        if (fecha.isAfter(hoy.plusDays(diasAnticipacionMax))) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "Solo se puede consultar con " + diasAnticipacionMax + " dias de anticipacion como maximo.");
        }

        CanchaInfo cancha = reservas.buscarCancha(idCancha)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La cancha no existe."));
        if (!cancha.reservable()) {
            return new DisponibilidadDia(idCancha, fecha, zona.getId(), duracionMinutos, List.of(), List.of());
        }

        // Regla de agenda: mientras la cancha no tenga ningun horario activo se usa la
        // ventana por defecto; en cuanto tiene agenda, solo se abre lo que ella define y un
        // dia sin horarios queda cerrado (ver ADR-0015).
        int diaIso = fecha.getDayOfWeek().getValue();
        List<HorarioSemanal> agenda = reservas.horariosDe(idCancha);
        List<HorarioSemanal> horarios = agenda.isEmpty()
                ? List.of(new HorarioSemanal(diaIso, ventanaDefectoInicio, ventanaDefectoFin, null))
                : agenda.stream().filter(horario -> horario.diaSemana() == diaIso).toList();

        // La ocupacion se lee para el dia local completo: las reservas pueden cruzar
        // la medianoche UTC aunque la fecha sea local.
        List<OcupacionFranja> ocupacion = reservas.ocupacionEn(idCancha, inicioDelDia(fecha), inicioDelDia(fecha.plusDays(1)));
        List<FranjaHoraria> ocupadas = ocupacion.stream().map(OcupacionFranja::franja).toList();

        List<FranjaDisponible> franjas = new ArrayList<>();
        for (HorarioSemanal horario : horarios) {
            BigDecimal precio = horario.precio() != null ? horario.precio() : cancha.precioBase();
            List<FranjaHoraria> bloques = FranjaHoraria.bloquesLibres(
                    aUtc(fecha, horario.inicio()), aUtc(fecha, horario.fin()), ocupadas, duracionMinutos);
            for (FranjaHoraria bloque : bloques) {
                if (bloque.inicio().isBefore(ahora)) {
                    continue; // no se ofrece lo que ya empezo (misma regla que al reservar)
                }
                franjas.add(new FranjaDisponible(bloque, precio));
            }
        }
        franjas.sort(Comparator.comparing(franja -> franja.franja().inicio()));

        return new DisponibilidadDia(idCancha, fecha, zona.getId(), duracionMinutos, franjas, ocupacion);
    }

    private Instant inicioDelDia(LocalDate fecha) {
        return fecha.atStartOfDay(zona).toInstant();
    }

    private Instant aUtc(LocalDate fecha, LocalTime hora) {
        return fecha.atTime(hora).atZone(zona).toInstant();
    }
}
