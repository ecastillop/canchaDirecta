package pe.com.canchadirecta.identity.adapter.in.rest;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import pe.com.canchadirecta.identity.adapter.in.rest.dto.ConfirmarRecuperacionRequest;
import pe.com.canchadirecta.identity.adapter.in.rest.dto.IniciarSesionRequest;
import pe.com.canchadirecta.identity.adapter.in.rest.dto.LogoutRequest;
import pe.com.canchadirecta.identity.adapter.in.rest.dto.RefreshTokenRequest;
import pe.com.canchadirecta.identity.adapter.in.rest.dto.ReenviarVerificacionRequest;
import pe.com.canchadirecta.identity.adapter.in.rest.dto.RegistrarUsuarioRequest;
import pe.com.canchadirecta.identity.adapter.in.rest.dto.SolicitarRecuperacionRequest;
import pe.com.canchadirecta.identity.adapter.in.rest.dto.TokenResponse;
import pe.com.canchadirecta.identity.adapter.in.rest.dto.UsuarioResponse;
import pe.com.canchadirecta.identity.adapter.in.rest.dto.VerificarCorreoRequest;
import pe.com.canchadirecta.identity.application.dto.IniciarSesionCommand;
import pe.com.canchadirecta.identity.application.dto.RegistrarUsuarioCommand;
import pe.com.canchadirecta.identity.application.dto.TokenEmitidoDto;
import pe.com.canchadirecta.identity.application.dto.UsuarioCreadoDto;
import pe.com.canchadirecta.identity.application.port.in.AutenticarUseCase;
import pe.com.canchadirecta.identity.application.port.in.CerrarSesionUseCase;
import pe.com.canchadirecta.identity.application.port.in.ConfirmarRecuperacionUseCase;
import pe.com.canchadirecta.identity.application.port.in.ReenviarVerificacionUseCase;
import pe.com.canchadirecta.identity.application.port.in.RegistrarUsuarioUseCase;
import pe.com.canchadirecta.identity.application.port.in.RenovarTokenUseCase;
import pe.com.canchadirecta.identity.application.port.in.SolicitarRecuperacionUseCase;
import pe.com.canchadirecta.identity.application.port.in.VerificarCorreoUseCase;

/** Adaptador de entrada REST para autenticacion/cuentas. Sin reglas de negocio. */
@Path("/api/v1/auth")
@Tag(name = "auth", description = "Registro, sesion y cuenta")
public class AuthResource {

    private final RegistrarUsuarioUseCase registrarUsuario;
    private final AutenticarUseCase autenticar;
    private final RenovarTokenUseCase renovarToken;
    private final CerrarSesionUseCase cerrarSesion;
    private final SolicitarRecuperacionUseCase solicitarRecuperacion;
    private final ConfirmarRecuperacionUseCase confirmarRecuperacion;
    private final VerificarCorreoUseCase verificarCorreo;
    private final ReenviarVerificacionUseCase reenviarVerificacion;

    @Inject
    public AuthResource(RegistrarUsuarioUseCase registrarUsuario, AutenticarUseCase autenticar,
                        RenovarTokenUseCase renovarToken, CerrarSesionUseCase cerrarSesion,
                        SolicitarRecuperacionUseCase solicitarRecuperacion,
                        ConfirmarRecuperacionUseCase confirmarRecuperacion,
                        VerificarCorreoUseCase verificarCorreo,
                        ReenviarVerificacionUseCase reenviarVerificacion) {
        this.registrarUsuario = registrarUsuario;
        this.autenticar = autenticar;
        this.renovarToken = renovarToken;
        this.cerrarSesion = cerrarSesion;
        this.solicitarRecuperacion = solicitarRecuperacion;
        this.confirmarRecuperacion = confirmarRecuperacion;
        this.verificarCorreo = verificarCorreo;
        this.reenviarVerificacion = reenviarVerificacion;
    }

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Registra una cuenta de usuario")
    public Response registrar(@Valid RegistrarUsuarioRequest request) {
        UsuarioCreadoDto creado = registrarUsuario.registrar(new RegistrarUsuarioCommand(
                request.nombres(), request.apellidos(), request.correo(),
                request.telefono(), request.password(), request.confirmacionPassword()));

        return Response.created(UriBuilder.fromPath("/api/v1/users/{id}")
                        .build(creado.id()))
                .entity(UsuarioResponse.desde(creado))
                .build();
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Inicia sesion y emite tokens")
    public Response login(@Valid IniciarSesionRequest request) {
        TokenEmitidoDto tokens = autenticar.autenticar(
                new IniciarSesionCommand(request.correo(), request.password()));
        return Response.ok(TokenResponse.desde(tokens)).build();
    }

    @POST
    @Path("/refresh")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Renueva el token de acceso rotando el refresh token")
    public Response refresh(@Valid RefreshTokenRequest request) {
        TokenEmitidoDto tokens = renovarToken.renovar(request.refreshToken());
        return Response.ok(TokenResponse.desde(tokens)).build();
    }

    @POST
    @Path("/logout")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Cierra la sesion revocando el refresh token")
    public Response logout(LogoutRequest request) {
        cerrarSesion.cerrar(request == null ? null : request.refreshToken());
        return Response.noContent().build();
    }

    @POST
    @Path("/email/verify")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Verifica el correo con el token recibido (CU01.2)")
    public Response verificarCorreo(@Valid VerificarCorreoRequest request) {
        verificarCorreo.verificar(request.token());
        return Response.noContent().build();
    }

    @POST
    @Path("/email/resend")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Reenvia la verificacion de correo (CU01.2; no revela si la cuenta existe)")
    public Response reenviarVerificacion(@Valid ReenviarVerificacionRequest request) {
        reenviarVerificacion.reenviar(request.correo());
        return Response.accepted().build();
    }

    @POST
    @Path("/password-reset/request")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Solicita el enlace de recuperacion (no revela si la cuenta existe)")
    public Response solicitarRecuperacion(@Valid SolicitarRecuperacionRequest request) {
        solicitarRecuperacion.solicitar(request.correo());
        // 202 siempre: la respuesta no distingue entre correo existente e inexistente
        return Response.accepted().build();
    }

    @POST
    @Path("/password-reset/confirm")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Cambia la contrasena usando el token de recuperacion")
    public Response confirmarRecuperacion(@Valid ConfirmarRecuperacionRequest request) {
        confirmarRecuperacion.confirmar(request.token(), request.nuevaPassword(),
                request.confirmacionPassword());
        return Response.noContent().build();
    }
}
