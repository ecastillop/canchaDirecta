package pe.com.canchadirecta.identity;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** S-4 (limite de intentos de login) y L-20 (revocacion de access tokens). */
@QuarkusTest
class EndurecimientoAccesoTest {

    private static final String PASSWORD = "ClaveSegura123";

    @Inject
    NotificadorCuentaFalso notificador;

    @BeforeEach
    void limpiar() {
        notificador.limpiar();
    }

    @Test
    void bloqueaTemporalmenteTrasCincoIntentosFallidos() {
        String correo = registrar();
        for (int i = 0; i < 5; i++) {
            given().contentType(ContentType.JSON)
                    .body(Map.of("correo", correo, "password", "Incorrecta" + i + "999"))
                    .when().post("/api/v1/auth/login")
                    .then().statusCode(401);
        }

        // Incluso con la contrasena correcta, la ventana de bloqueo responde 429
        given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo, "password", PASSWORD))
                .when().post("/api/v1/auth/login")
                .then().statusCode(429)
                .body("code", equalTo("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void otroCorreoNoSeVeAfectadoPorLosFallosDeUnaCuenta() {
        String bloqueada = registrar();
        String libre = registrar();
        for (int i = 0; i < 5; i++) {
            given().contentType(ContentType.JSON)
                    .body(Map.of("correo", bloqueada, "password", "Mala" + i + "000"))
                    .when().post("/api/v1/auth/login")
                    .then().statusCode(401);
        }
        given().contentType(ContentType.JSON)
                .body(Map.of("correo", libre, "password", PASSWORD))
                .when().post("/api/v1/auth/login")
                .then().statusCode(200);
    }

    @Test
    void cambiarLaPasswordRevocaElAccessTokenVigente() {
        String correo = registrar();
        String access = given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo, "password", PASSWORD))
                .when().post("/api/v1/auth/login")
                .then().statusCode(200).extract().path("accessToken");

        given().auth().oauth2(access).when().get("/api/v1/users/me").then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo))
                .when().post("/api/v1/auth/password-reset/request")
                .then().statusCode(202);
        String token = notificador.tokenDe(correo);
        assertNotNull(token);

        given().contentType(ContentType.JSON)
                .body(Map.of("token", token, "nuevaPassword", "NuevaClave456",
                        "confirmacionPassword", "NuevaClave456"))
                .when().post("/api/v1/auth/password-reset/confirm")
                .then().statusCode(204);

        given().auth().oauth2(access).when().get("/api/v1/users/me")
                .then().statusCode(401)
                .body("code", equalTo("UNAUTHENTICATED"));
    }

    private String registrar() {
        String correo = "endurece." + System.nanoTime() + "@canchadirecta.pe";
        given().contentType(ContentType.JSON)
                .body(Map.of("nombres", "Usuario", "apellidos", "Prueba", "correo", correo,
                        "telefono", "966555444", "password", PASSWORD,
                        "confirmacionPassword", PASSWORD))
                .when().post("/api/v1/auth/register")
                .then().statusCode(201);
        return correo;
    }
}
