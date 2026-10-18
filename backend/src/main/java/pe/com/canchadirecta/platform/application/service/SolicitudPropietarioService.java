package pe.com.canchadirecta.platform.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.com.canchadirecta.platform.application.port.in.SolicitudPropietarioUseCase;
import pe.com.canchadirecta.platform.application.port.out.AdminPlataformaRepositorio;
import pe.com.canchadirecta.platform.domain.model.PropietarioAdmin;
import pe.com.canchadirecta.shared.domain.DomainException;
import pe.com.canchadirecta.shared.domain.ErrorCodes;

import java.util.Optional;

/**
 * Solicitud de alta como propietario (CU01.6). La validacion la hace la administracion
 * (CU06.2); esta pieza solo registra la peticion y evita documentos duplicados.
 */
@ApplicationScoped
public class SolicitudPropietarioService implements SolicitudPropietarioUseCase {

    private final AdminPlataformaRepositorio admin;

    @Inject
    public SolicitudPropietarioService(AdminPlataformaRepositorio admin) {
        this.admin = admin;
    }

    @Override
    public PropietarioAdmin solicitar(Long idUsuario, String documentoIdentidad) {
        String documento = normalizar(documentoIdentidad);
        Optional<PropietarioAdmin> actual = admin.propietarioDeUsuario(idUsuario);
        if (actual.isPresent()) {
            if (!documento.equals(actual.get().documento())) {
                throw new DomainException(ErrorCodes.CONFLICTO,
                        "Ya existe una solicitud de propietario para esta cuenta.");
            }
            return actual.get();
        }
        if (admin.documentoPropietarioExiste(documento)) {
            throw new DomainException(ErrorCodes.CONFLICTO,
                    "Ese documento de identidad ya esta registrado por otro propietario.");
        }
        Long id = admin.registrarSolicitudPropietario(idUsuario, documento);
        return admin.propietario(id)
                .orElseThrow(() -> new DomainException(ErrorCodes.NO_ENCONTRADO,
                        "No se pudo registrar la solicitud."));
    }

    @Override
    public Optional<PropietarioAdmin> consultar(Long idUsuario) {
        return admin.propietarioDeUsuario(idUsuario);
    }

    private static String normalizar(String documento) {
        if (documento == null || documento.isBlank()) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El documento de identidad es obligatorio.");
        }
        String valor = documento.trim();
        if (valor.length() > 20) {
            throw new DomainException(ErrorCodes.DATOS_INVALIDOS,
                    "El documento de identidad supera 20 caracteres.");
        }
        return valor;
    }
}
