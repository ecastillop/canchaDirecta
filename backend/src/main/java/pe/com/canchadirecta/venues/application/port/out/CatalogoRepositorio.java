package pe.com.canchadirecta.venues.application.port.out;

import pe.com.canchadirecta.venues.domain.model.CatalogoCancha;
import pe.com.canchadirecta.venues.domain.model.FichaEspacio;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida del catalogo de canchas (SQL nativo, ADR-0012).
 */
public interface CatalogoRepositorio {

    List<CatalogoCancha.Distrito> distritos(String texto, int limite);

    List<CatalogoCancha.Item> deportes();

    List<CatalogoCancha.Item> caracteristicas();

    Optional<CatalogoCancha.Detalle> detalle(Long idCancha);

    Optional<FichaEspacio.Espacio> fichaEspacio(Long idEstablecimiento);

    void reemplazarDeportes(Long idCancha, List<Long> idsDeporte);

    void reemplazarCaracteristicas(Long idCancha, List<Long> idsCaracteristica);

    long contarDeportes(List<Long> idsDeporte);

    long contarCaracteristicas(List<Long> idsCaracteristica);

    boolean existeOrdenFoto(Long idCancha, int orden);

    Long agregarFoto(Long idCancha, String url, int orden, boolean principal);

    boolean eliminarFoto(Long idCancha, Long idFoto);
}
