package pe.com.canchadirecta.bookings.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.com.canchadirecta.bookings.application.dto.CanchaInfo;
import pe.com.canchadirecta.bookings.application.dto.CocheraDisponible;
import pe.com.canchadirecta.bookings.application.dto.CocheraInfo;
import pe.com.canchadirecta.bookings.application.port.in.ConsultarCocherasUseCase;
import pe.com.canchadirecta.bookings.application.port.out.ReservaRepositorio;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Cupo de cocheras de una cancha para una franja (RN-08).
 *
 * <p>Las cocheras pertenecen al establecimiento de la cancha. Para cada cochera activa se
 * descuenta la ocupacion de las reservas de cochera vigentes que se solapan con la franja
 * solicitada; `cupoDisponible` es `capacidad - ocupadas` (nunca negativo). Es el dato que
 * el usuario consulta antes de elegir la cochera opcional.</p>
 */
@ApplicationScoped
public class ConsultarCocherasService implements ConsultarCocherasUseCase {

    private final ReservaRepositorio reservas;

    @Inject
    public ConsultarCocherasService(ReservaRepositorio reservas) {
        this.reservas = reservas;
    }

    @Override
    public List<CocheraDisponible> consultar(Long idCancha, Instant inicio, Instant fin) {
        if (idCancha == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La cancha es obligatoria.");
        }
        if (inicio == null || fin == null || !inicio.isBefore(fin)) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El rango de la consulta debe tener un inicio anterior al fin.");
        }

        CanchaInfo cancha = reservas.buscarCancha(idCancha)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La cancha no existe."));

        List<CocheraInfo> cocheras = reservas.cocherasDeEstablecimiento(cancha.idEstablecimiento());
        List<CocheraDisponible> disponibles = new ArrayList<>(cocheras.size());
        for (CocheraInfo cochera : cocheras) {
            int ocupadas = reservas.ocupacionCochera(cochera.id(), inicio, fin);
            disponibles.add(new CocheraDisponible(cochera.id(), cochera.nombre(), cochera.capacidad(),
                    ocupadas, cochera.tarifaHora()));
        }
        return disponibles;
    }
}
