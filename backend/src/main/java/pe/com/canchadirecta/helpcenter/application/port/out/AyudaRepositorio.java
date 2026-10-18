package pe.com.canchadirecta.helpcenter.application.port.out;

import pe.com.canchadirecta.helpcenter.domain.model.AyudaCategoria;

import java.util.List;

/** Lectura del centro de ayuda (contenido publicado). */
public interface AyudaRepositorio {

    List<AyudaCategoria> listar(String texto);
}
