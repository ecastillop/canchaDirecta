package pe.com.canchadirecta.helpcenter.application.port.in;

import pe.com.canchadirecta.helpcenter.domain.model.AyudaCategoria;

import java.util.List;

/** Casos de uso del centro de ayuda. */
public interface AyudaUseCase {

    List<AyudaCategoria> listar(String texto);
}
