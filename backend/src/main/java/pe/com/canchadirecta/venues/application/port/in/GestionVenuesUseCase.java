package pe.com.canchadirecta.venues.application.port.in;

import pe.com.canchadirecta.venues.application.dto.ActualizarCanchaCommand;
import pe.com.canchadirecta.venues.application.dto.ActualizarEstablecimientoCommand;
import pe.com.canchadirecta.venues.application.dto.RegistrarCanchaCommand;
import pe.com.canchadirecta.venues.application.dto.RegistrarCocheraCommand;
import pe.com.canchadirecta.venues.application.dto.RegistrarEstablecimientoCommand;
import pe.com.canchadirecta.venues.domain.model.Cancha;
import pe.com.canchadirecta.venues.domain.model.Cochera;
import pe.com.canchadirecta.venues.domain.model.Establecimiento;

import java.util.List;

/** Casos de uso de establecimientos, canchas y cocheras. */
public interface GestionVenuesUseCase {

    Long registrarEstablecimiento(Long idUsuario, RegistrarEstablecimientoCommand command);

    Long registrarCancha(Long idUsuario, RegistrarCanchaCommand command);

    List<Cancha> listarCanchas(Long idDistrito, int limite);

    /** Datos de un establecimiento propio (vista de gestion del propietario). */
    Establecimiento obtenerEstablecimiento(Long idUsuario, Long idEstablecimiento);

    /** Establecimientos del usuario autenticado, para que el panel no dependa del ID manual. */
    List<Establecimiento> listarEstablecimientosPropios(Long idUsuario);

    /** IDs de canchas favoritas del usuario. */
    List<Long> listarFavoritos(Long idUsuario);

    void agregarFavorito(Long idUsuario, Long idCancha);

    void quitarFavorito(Long idUsuario, Long idCancha);

    /** Edita los datos de un establecimiento propio (CU04.1). */
    Establecimiento actualizarEstablecimiento(Long idUsuario, Long idEstablecimiento,
                                              ActualizarEstablecimientoCommand command);

    /** Edita los datos de una cancha propia (CU04.1). */
    Cancha actualizarCancha(Long idUsuario, Long idCancha, ActualizarCanchaCommand command);

    /** Baja logica del establecimiento; sus canchas dejan de ofrecerse (CU04.2). */
    void darDeBajaEstablecimiento(Long idUsuario, Long idEstablecimiento);

    /** Baja logica de una cancha propia (CU04.2). */
    void darDeBajaCancha(Long idUsuario, Long idCancha);

    /** Registra una cochera (RN-08) en un establecimiento propio. */
    Long registrarCochera(Long idUsuario, RegistrarCocheraCommand command);

    /** Cocheras de un establecimiento propio (vista de gestion del propietario). */
    List<Cochera> listarCocheras(Long idUsuario, Long idEstablecimiento);

    /** Baja logica de una cochera propia. */
    void desactivarCochera(Long idUsuario, Long idEstablecimiento, Long idCochera);
}
