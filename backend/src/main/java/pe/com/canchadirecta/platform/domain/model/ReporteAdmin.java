package pe.com.canchadirecta.platform.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Reporte agregado de la plataforma para el panel de administracion. */
public record ReporteAdmin(
        int dias,
        Instant desde,
        Instant hasta,
        Resumen resumen,
        List<PuntoDia> serie,
        List<EstadoReserva> estados,
        List<EstablecimientoTop> establecimientos
) {

    public record Resumen(
            long usuarios,
            long usuariosNuevos,
            long propietariosValidados,
            long establecimientosActivos,
            long reservasPeriodo,
            BigDecimal ingresosAprobados,
            long pagosAprobados,
            long reclamosAbiertos
    ) {
    }

    public record PuntoDia(
            LocalDate fecha,
            long reservas,
            long usuariosNuevos,
            BigDecimal ingresos
    ) {
    }

    public record EstadoReserva(
            String estado,
            long total
    ) {
    }

    public record EstablecimientoTop(
            Long id,
            String nombre,
            String distrito,
            long reservas,
            BigDecimal ingresos
    ) {
    }
}
