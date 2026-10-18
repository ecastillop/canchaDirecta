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

/** Integracion de la recuperacion de contrasena (solicitud + confirmacion). */
@QuarkusTest
class RecuperacionPasswordTest {

    private static final String PASSWORD = "ClaveSegura123";
    private static final String PASSWORD_NUEVA = "NuevaClave456";

    @Inject
    NotificadorCuentaFalso notificador;

    @BeforeEach
    void limpiar() {
        notificador.limpiar();
    }

    private String registrar() {
        String correo = "reset." + System.nanoTime() + "@canchadirecta.pe";
        given().contentType(ContentType.JSON)
                .body(Map.of("nombres", "Elena", "apellidos", "Ruiz", "correo", correo,
                        "telefono", "966555444", "password", PASSWORD,
                        "confirmacionPassword", PASSWORD))
                .when().post("/api/v1/auth/register")
                .then().statusCode(201);
        return correo;
    }

    private String login(String correo, String password, int esperado) {
        var respuesta = given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo, "password", password))
                .when().post("/api/v1/auth/login")
                .then().statusCode(esperado);
        return esperado == 200 ? respuesta.extract().path("refreshToken") : null;
    }

    private void solicitar(String correo) {
        given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo))
                .when().post("/api/v1/auth/password-reset/request")
                .then().statusCode(202);
    }

    @Test
    void cambiaLaPasswordYRevocaLasSesionesVigentes() {
        String correo = registrar();
        String refreshAnterior = login(correo, PASSWORD, 200);
        solicitar(correo);

        String token = notificador.tokenDe(correo);
        org.junit.jupiter.api.Assertions.assertNotNull(token, "el notificador debio recibir un token");

        given().contentType(ContentType.JSON)
                .body(Map.of("token", token, "nuevaPassword", PASSWORD_NUEVA,
                        "confirmacionPassword", PASSWORD_NUEVA))
                .when().post("/api/v1/auth/password-reset/confirm")
                .then().statusCode(204);

        login(correo, PASSWORD_NUEVA, 200);
        login(correo, PASSWORD, 401);

        // Las sesiones abiertas antes del cambio dejan de servir
        given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", refreshAnterior))
                .when().post("/api/v1/auth/refresh")
                .then().statusCode(401);
    }

    @Test
    void elTokenNoSePuedeReutilizar() {
        String correo = registrar();
        solicitar(correo);
        String token = notificador.tokenDe(correo);

        given().contentType(ContentType.JSON)
                .body(Map.of("token", token, "nuevaPassword", PASSWORD_NUEVA,
                        "confirmacionPassword", PASSWORD_NUEVA))
                .when().post("/api/v1/auth/password-reset/confirm")
                .then().statusCode(204);

        given().contentType(ContentType.JSON)
                .body(Map.of("token", token, "nuevaPassword", "OtraClave789",
                        "confirmacionPassword", "OtraClave789"))
                .when().post("/api/v1/auth/password-reset/confirm")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void solicitarConCorreoInexistenteDevuelve202SinEmitirToken() {
        String correo = "fantasma." + System.nanoTime() + "@canchadirecta.pe";

        solicitar(correo);

        org.junit.jupiter.api.Assertions.assertNull(notificador.tokenDe(correo));
    }

    @Test
    void confirmarConTokenInvalidoDevuelve400() {
        given().contentType(ContentType.JSON)
                .body(Map.of("token", "token-invalido-" + System.nanoTime(),
                        "nuevaPassword", PASSWORD_NUEVA,
                        "confirmacionPassword", PASSWORD_NUEVA))
                .when().post("/api/v1/auth/password-reset/confirm")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void confirmarConConfirmacionDistintaDevuelve400() {
        String correo = registrar();
        solicitar(correo);
        String token = notificador.tokenDe(correo);
        org.junit.jupiter.api.Assertions.assertNotNull(token);

        given().contentType(ContentType.JSON)
                .body(Map.of("token", token, "nuevaPassword", PASSWORD_NUEVA,
                        "confirmacionPassword", "NoCoincide999"))
                .when().post("/api/v1/auth/password-reset/confirm")
                .then()
                .statusCode(400)
                .body("detail", notNullValue());
    }
}
