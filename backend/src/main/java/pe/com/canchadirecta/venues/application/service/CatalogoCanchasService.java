package pe.com.canchadirecta.venues.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;
import pe.com.canchadirecta.venues.application.port.in.CatalogoCanchasUseCase;
import pe.com.canchadirecta.venues.application.port.out.CatalogoRepositorio;
import pe.com.canchadirecta.venues.application.port.out.VenuesRepositorio;
import pe.com.canchadirecta.venues.domain.model.Cancha;
import pe.com.canchadirecta.venues.domain.model.CatalogoCancha;
import pe.com.canchadirecta.venues.domain.model.FichaEspacio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Catalogo de canchas (F02/F03): busqueda publica, ficha con atributos y gestion de deportes,
 * caracteristicas y fotos por el propietario de la cancha.
 */
@ApplicationScoped
public class CatalogoCanchasService implements CatalogoCanchasUseCase {

    private static final int LIMITE_DEFECTO = 20;
    private static final int MINIMO_COMPARACION = 2;
    private static final int MAXIMO_COMPARACION = 4;

    private final CatalogoRepositorio catalogo;
    private final VenuesRepositorio venues;

    @Inject
    public CatalogoCanchasService(CatalogoRepositorio catalogo, VenuesRepositorio venues) {
        this.catalogo = catalogo;
        this.venues = venues;
    }

    @Override
    public List<CatalogoCancha.Distrito> distritos(String texto, int limite) {
        return catalogo.distritos(texto, limite(limite));
    }

    @Override
    public List<CatalogoCancha.Item> deportes() {
        return catalogo.deportes();
    }

    @Override
    public List<CatalogoCancha.Item> caracteristicas() {
        return catalogo.caracteristicas();
    }

    @Override
    public CatalogoCancha.Pagina buscarCanchas(Long idDistrito, Long idEstablecimiento, Long idDeporte,
                                               BigDecimal precioMax, String texto, String orden, int limite,
                                               int desplazamiento) {
        if (precioMax != null && precioMax.signum() <= 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El precio maximo debe ser mayor que cero.");
        }
        if (desplazamiento < 0) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El desplazamiento no puede ser negativo.");
        }
        CatalogoCancha.Filtro filtro = new CatalogoCancha.Filtro(idDistrito, idEstablecimiento, idDeporte,
                precioMax, texto == null || texto.isBlank() ? null : texto.trim(), orden(orden), limite(limite),
                desplazamiento);
        return new CatalogoCancha.Pagina(venues.buscarCanchas(filtro), venues.contarCanchas(filtro));
    }

    private static String orden(String orden) {
        if (orden == null || orden.isBlank()) {
            return "destacados";
        }
        return switch (orden) {
            case "precio", "valoracion", "destacados" -> orden;
            default -> throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El orden debe ser destacados, precio o valoracion.");
        };
    }

    @Override
    public CatalogoCancha.Detalle detalle(Long idCancha) {
        return catalogo.detalle(idCancha)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO, "La cancha no existe."));
    }

    @Override
    public FichaEspacio.Espacio fichaEspacio(Long idEstablecimiento) {
        if (idEstablecimiento == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El establecimiento es obligatorio.");
        }
        return catalogo.fichaEspacio(idEstablecimiento)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO,
                        "El establecimiento no existe."));
    }

    @Override
    public List<CatalogoCancha.Detalle> comparar(List<Long> idsCancha) {
        if (idsCancha == null || idsCancha.size() < MINIMO_COMPARACION
                || idsCancha.size() > MAXIMO_COMPARACION) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "Seleccione entre " + MINIMO_COMPARACION + " y " + MAXIMO_COMPARACION
                            + " canchas para comparar.");
        }
        if (new HashSet<>(idsCancha).size() != idsCancha.size()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "No se puede repetir una cancha.");
        }
        List<CatalogoCancha.Detalle> fichas = new ArrayList<>(idsCancha.size());
        for (Long id : idsCancha) {
            fichas.add(detalle(id));
        }
        return fichas;
    }

    @Override
    @Transactional
    public CatalogoCancha.Detalle actualizarDeportes(Long idCancha, Long idUsuario, List<Long> idsDeporte) {
        exigirPropietario(idCancha, idUsuario);
        List<Long> ids = limpiar(idsDeporte);
        if (!ids.isEmpty() && catalogo.contarDeportes(ids) != ids.size()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "Algun deporte no existe.");
        }
        catalogo.reemplazarDeportes(idCancha, ids);
        return detalle(idCancha);
    }

    @Override
    @Transactional
    public CatalogoCancha.Detalle actualizarCaracteristicas(Long idCancha, Long idUsuario,
                                                            List<Long> idsCaracteristica) {
        exigirPropietario(idCancha, idUsuario);
        List<Long> ids = limpiar(idsCaracteristica);
        if (!ids.isEmpty() && catalogo.contarCaracteristicas(ids) != ids.size()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "Alguna caracteristica no existe.");
        }
        catalogo.reemplazarCaracteristicas(idCancha, ids);
        return detalle(idCancha);
    }

    @Override
    @Transactional
    public CatalogoCancha.Foto agregarFoto(Long idCancha, Long idUsuario, String url, int orden,
                                           boolean principal) {
        exigirPropietario(idCancha, idUsuario);
        if (url == null || url.isBlank() || url.trim().length() > 500) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "La URL de la foto es obligatoria y no puede superar 500 caracteres.");
        }
        if (orden < 1 || orden > 99) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "El orden de la foto debe estar entre 1 y 99.");
        }
        if (catalogo.existeOrdenFoto(idCancha, orden)) {
            throw new DomainException(ErrorCodes.CONFLICTO, "Ya existe una foto con ese orden.");
        }
        Long id = catalogo.agregarFoto(idCancha, url.trim(), orden, principal);
        return new CatalogoCancha.Foto(id, url.trim(), orden, principal);
    }

    @Override
    @Transactional
    public void eliminarFoto(Long idCancha, Long idUsuario, Long idFoto) {
        exigirPropietario(idCancha, idUsuario);
        if (!catalogo.eliminarFoto(idCancha, idFoto)) {
            throw new DomainException(ErrorCodes.NO_ENCONTRADO, "La foto no existe.");
        }
    }

    private void exigirPropietario(Long idCancha, Long idUsuario) {
        if (idCancha == null) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS, "La cancha es obligatoria.");
        }
        if (!venues.esPropietarioDeCancha(idCancha, idUsuario)) {
            throw new DomainException(ErrorCodes.SIN_PERMISO, "La cancha pertenece a otro propietario.");
        }
    }

    private static List<Long> limpiar(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        List<Long> limpios = new ArrayList<>();
        for (Long id : ids) {
            if (id != null && !limpios.contains(id)) {
                limpios.add(id);
            }
        }
        return limpios;
    }

    private static int limite(int pedido) {
        return pedido > 0 ? pedido : LIMITE_DEFECTO;
    }
}
