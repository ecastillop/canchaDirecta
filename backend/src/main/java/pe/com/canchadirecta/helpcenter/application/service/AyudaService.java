package pe.com.canchadirecta.helpcenter.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.com.canchadirecta.helpcenter.application.port.in.AyudaUseCase;
import pe.com.canchadirecta.helpcenter.application.port.out.AyudaRepositorio;
import pe.com.canchadirecta.helpcenter.domain.model.AyudaCategoria;

import java.util.List;

/** Centro de ayuda publico: preguntas frecuentes agrupadas por categoria. */
@ApplicationScoped
public class AyudaService implements AyudaUseCase {

    private final AyudaRepositorio repositorio;

    @Inject
    public AyudaService(AyudaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public List<AyudaCategoria> listar(String texto) {
        String limpio = texto == null ? null : texto.trim();
        return repositorio.listar(limpio == null || limpio.isEmpty() ? null : limpio);
    }
}
