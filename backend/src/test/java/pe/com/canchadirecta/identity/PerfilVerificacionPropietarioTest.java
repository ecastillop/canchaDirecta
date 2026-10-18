package pe.com.canchadirecta.identity;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** CU01.2 (verificacion de correo), CU01.5 (perfil editable) y CU01.6 (solicitud de propietario). */
@QuarkusTest
class PerfilVerificacionPropietarioTest {

    private static final String PASSWORD = "ClaveSegura123";

    @Inject
    NotificadorCuentaFalso notificador;

    @BeforeEach
    void limpiar() {
        notificador.limpiar();
    }

    @Test
    void elRegistroEmiteTokenYPermiteVerificarElCorreo() {
        String correo = registrar();
        String token = notificador.tokenVerificacionDe(correo);
        assertNotNull(token, "el registro debe emitir un token EMAIL_VERIFY");

        String access = iniciarSesion(correo);
        given().auth().oauth2(access).when().get("/api/v1/users/me")
                .then().statusCode(200)
                .body("correoVerificado", equalTo(false));

        given().contentType(ContentType.JSON)
                .body(Map.of("token", token))
                .when().post("/api/v1/auth/email/verify")
                .then().statusCode(204);

        given().auth().oauth2(access).when().get("/api/v1/users/me")
                .then().statusCode(200)
                .body("correoVerificado", equalTo(true));

        // El token es de un solo uso
        given().contentType(ContentType.JSON)
                .body(Map.of("token", token))
                .when().post("/api/v1/auth/email/verify")
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));

        given().contentType(ContentType.JSON)
                .body(Map.of("token", "token-invalido-" + System.nanoTime()))
                .when().post("/api/v1/auth/email/verify")
                .then().statusCode(400);
    }

    @Test
    void reenviarEmiteUnNuevoTokenYNoRevelaCuentasInexistentes() {
        String correo = registrar();
        String primero = notificador.tokenVerificacionDe(correo);

        given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo))
                .when().post("/api/v1/auth/email/resend")
                .then().statusCode(202);

        String segundo = notificador.tokenVerificacionDe(correo);
        assertNotEquals(primero, segundo);

        given().contentType(ContentType.JSON)
                .body(Map.of("token", segundo))
                .when().post("/api/v1/auth/email/verify")
                .then().statusCode(204);

        // El anterior dejo de valer al reemitirse
        given().contentType(ContentType.JSON)
                .body(Map.of("token", primero))
                .when().post("/api/v1/auth/email/verify")
                .then().statusCode(400);

        String fantasma = "verifica.fantasma." + System.nanoTime() + "@canchadirecta.pe";
        given().contentType(ContentType.JSON)
                .body(Map.of("correo", fantasma))
                .when().post("/api/v1/auth/email/resend")
                .then().statusCode(202);
        assertNull(notificador.tokenVerificacionDe(fantasma));
    }

    @Test
    void elUsuarioActualizaSuPerfil() {
        String correo = registrar();
        String access = iniciarSesion(correo);

        given().auth().oauth2(access).contentType(ContentType.JSON)
                .body(Map.of("nombres", "Ana Maria", "apellidos", "Torres Vega",
                        "telefono", "999888777"))
                .when().put("/api/v1/users/me")
                .then().statusCode(200)
                .body("nombres", equalTo("Ana Maria"))
                .body("apellidos", equalTo("Torres Vega"))
                .body("telefono", equalTo("999888777"))
                .body("correo", equalTo(correo));

        given().auth().oauth2(access).when().get("/api/v1/users/me")
                .then().statusCode(200)
                .body("nombres", equalTo("Ana Maria"))
                .body("telefono", equalTo("999888777"));

        given().auth().oauth2(access).contentType(ContentType.JSON)
                .body(Map.of("nombres", " ", "apellidos", "Torres"))
                .when().put("/api/v1/users/me")
                .then().statusCode(400);

        given().contentType(ContentType.JSON)
                .body(Map.of("nombres", "Sin", "apellidos", "Token"))
                .when().put("/api/v1/users/me")
                .then().statusCode(401);
    }

    @Test
    void solicitaAltaDePropietarioYConsultaSuEstado() {
        String correo = registrar();
        String access = iniciarSesion(correo);
        String documento = documentoUnico();

        var creada = given().auth().oauth2(access).contentType(ContentType.JSON)
                .body(Map.of("documentoIdentidad", documento))
                .when().post("/api/v1/propietarios/solicitudes")
                .then().statusCode(201)
                .body("documento", equalTo(documento))
                .body("estadoValidacion", equalTo("PENDIENTE"))
                .body("id", notNullValue())
                .extract();
        int idSolicitud = creada.path("id");

        // Repetir la solicitud con el mismo documento devuelve la existente
        given().auth().oauth2(access).contentType(ContentType.JSON)
                .body(Map.of("documentoIdentidad", documento))
                .when().post("/api/v1/propietarios/solicitudes")
                .then().statusCode(201)
                .body("id", equalTo(idSolicitud));

        given().auth().oauth2(access).when().get("/api/v1/propietarios/me")
                .then().statusCode(200)
                .body("id", equalTo(idSolicitud))
                .body("estadoValidacion", equalTo("PENDIENTE"));

        // Otro usuario no puede reutilizar el documento ni cambiar el suyo
        String otroAccess = iniciarSesion(registrar());
        given().auth().oauth2(otroAccess).contentType(ContentType.JSON)
                .body(Map.of("documentoIdentidad", documento))
                .when().post("/api/v1/propietarios/solicitudes")
                .then().statusCode(409)
                .body("code", equalTo("CONFLICT"));

        given().auth().oauth2(otroAccess).when().get("/api/v1/propietarios/me")
                .then().statusCode(404);

        given().auth().oauth2(otroAccess).contentType(ContentType.JSON)
                .body(Map.of("documentoIdentidad", documentoUnico()))
                .when().post("/api/v1/propietarios/solicitudes")
                .then().statusCode(201);

        given().auth().oauth2(otroAccess).contentType(ContentType.JSON)
                .body(Map.of("documentoIdentidad", documentoUnico()))
                .when().post("/api/v1/propietarios/solicitudes")
                .then().statusCode(409);
    }

    // ---------------------------------------------------------------- utilidades

    private String registrar() {
        String correo = "cuenta." + System.nanoTime() + "@canchadirecta.pe";
        given().contentType(ContentType.JSON)
                .body(Map.of("nombres", "Usuario", "apellidos", "Prueba", "correo", correo,
                        "telefono", "966555444", "password", PASSWORD,
                        "confirmacionPassword", PASSWORD))
                .when().post("/api/v1/auth/register")
                .then().statusCode(201);
        return correo;
    }

    private String iniciarSesion(String correo) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo, "password", PASSWORD))
                .when().post("/api/v1/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");
    }

    private static String documentoUnico() {
        return String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
    }
}
