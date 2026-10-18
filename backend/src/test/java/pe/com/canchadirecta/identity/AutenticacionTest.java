package pe.com.canchadirecta.identity;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

/** Integracion de login (JWT) y del endpoint protegido /users/me. */
@QuarkusTest
class AutenticacionTest {

    private String registrar(String correo) {
        given().contentType(ContentType.JSON)
                .body(Map.of(
                        "nombres", "Luis",
                        "apellidos", "Gomez",
                        "correo", correo,
                        "telefono", "988777666",
                        "password", "ClaveSegura123",
                        "confirmacionPassword", "ClaveSegura123"))
                .when().post("/api/v1/auth/register")
                .then().statusCode(201);
        return correo;
    }

    private String correoUnico() {
        return "luis." + System.nanoTime() + "@canchadirecta.pe";
    }

    @Test
    void loginEmiteAccessYRefreshToken() {
        String correo = registrar(correoUnico());

        given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo, "password", "ClaveSegura123"))
                .when().post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .body("tokenType", equalTo("Bearer"))
                .body("expiresIn", greaterThan(0));
    }

    @Test
    void loginConPasswordIncorrectaDevuelve401() {
        String correo = registrar(correoUnico());

        given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo, "password", "ClaveIncorrecta999"))
                .when().post("/api/v1/auth/login")
                .then()
                .statusCode(401)
                .body("code", equalTo("INVALID_CREDENTIALS"));
    }

    @Test
    void loginConCorreoInexistenteDevuelve401() {
        given().contentType(ContentType.JSON)
                .body(Map.of("correo", "nadie." + System.nanoTime() + "@canchadirecta.pe",
                        "password", "ClaveSegura123"))
                .when().post("/api/v1/auth/login")
                .then()
                .statusCode(401)
                .body("code", equalTo("INVALID_CREDENTIALS"));
    }

    @Test
    void meSinTokenDevuelve401() {
        given().when().get("/api/v1/users/me").then().statusCode(401);
    }

    @Test
    void meConTokenDevuelveDatosDelUsuario() {
        String correo = registrar(correoUnico());

        String accessToken = given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo, "password", "ClaveSegura123"))
                .when().post("/api/v1/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");

        given().auth().oauth2(accessToken)
                .when().get("/api/v1/users/me")
                .then()
                .statusCode(200)
                .body("correo", equalTo(correo))
                .body("id", notNullValue());
    }
}
