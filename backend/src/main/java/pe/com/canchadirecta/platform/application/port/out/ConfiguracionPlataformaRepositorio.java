package pe.com.canchadirecta.platform.application.port.out;

import pe.com.canchadirecta.platform.domain.model.ConfiguracionPlataformaAdmin;

import java.time.Instant;

/** Lectura y escritura de la configuracion de plataforma (tabla clave/valor). */
public interface ConfiguracionPlataformaRepositorio {

    ConfiguracionPlataformaAdmin cargar();

    /** Guarda los cinco valores y marca la fecha de actualizacion. */
    ConfiguracionPlataformaAdmin guardar(ConfiguracionPlataformaAdmin configuracion, Instant momento);
}
