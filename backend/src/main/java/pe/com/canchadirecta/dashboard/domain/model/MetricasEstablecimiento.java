package pe.com.canchadirecta.dashboard.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Metricas de operacion de un establecimiento para el panel del propietario. */
public record MetricasEstablecimiento(
        Long idEstablecimiento,
        int dias,
        Instant desde,
        Instant hasta,
        Resumen resumen,
        List<PuntoDia> serie,
        List<CanchaMetrica> canchas,
        List<ReservaProxima> proximas
) {

    public record Resumen(
            long totalReservas,
            long confirmadas,
            long pendientes,
            long canceladas,
            BigDecimal ingresos,
            double horasReservadas,
            double ocupacionPorcentaje,
            int canchasActivas
    ) {
    }

    public record PuntoDia(
            LocalDate fecha,
            long reservas,
            BigDecimal ingresos
    ) {
    }

    public record CanchaMetrica(
            Long idCancha,
            String nombre,
            long reservas,
            BigDecimal ingresos
    ) {
    }

    public record ReservaProxima(
            Long idReserva,
            String cliente,
            String cancha,
            Instant inicio,
            Instant fin,
            String estado,
            BigDecimal monto
    ) {
    }
}
