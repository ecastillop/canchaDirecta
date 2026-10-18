package pe.com.canchadirecta.venues.application.port.in;

import pe.com.canchadirecta.venues.domain.model.Cancha;
import pe.com.canchadirecta.venues.domain.model.CatalogoCancha;

import java.math.BigDecimal;
import java.util.List;

/**
 * Casos de uso del catalogo de canchas y de sus atributos administrables por el propietario.
 */
public interface CatalogoCanchasUseCase {

    List<CatalogoCancha.Distrito> distritos(String texto, int limite);

    List<CatalogoCancha.Item> deportes();

    List<CatalogoCancha.Item> caracteristicas();

    CatalogoCancha.Pagina buscarCanchas(Long idDistrito, Long idEstablecimiento, Long idDeporte,
                                        BigDecimal precioMax, String texto, int limite, int desplazamiento);

    CatalogoCancha.Detalle detalle(Long idCancha);

    /**
     * Ficha de 2 a 4 canchas para compararlas (CU02.2), en el mismo orden solicitado.
     * Devuelve `404` si alguna no existe y `400` si la seleccion no es valida.
     */
    List<CatalogoCancha.Detalle> comparar(List<Long> idsCancha);

    CatalogoCancha.Detalle actualizarDeportes(Long idCancha, Long idUsuario, List<Long> idsDeporte);

    CatalogoCancha.Detalle actualizarCaracteristicas(Long idCancha, Long idUsuario, List<Long> idsCaracteristica);

    CatalogoCancha.Foto agregarFoto(Long idCancha, Long idUsuario, String url, int orden, boolean principal);

    void eliminarFoto(Long idCancha, Long idUsuario, Long idFoto);
}
