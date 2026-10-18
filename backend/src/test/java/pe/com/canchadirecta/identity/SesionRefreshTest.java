package pe.com.canchadirecta.identity;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/** Integracion de rotacion de refresh token y cierre de sesion. */
@QuarkusTest
class SesionRefreshTest {

    private static final String PASSWORD = "ClaveSegura123";

    private String registrarYLoguear() {
        String correo = "ses." + System.nanoTime() + "@canchadirecta.pe";
        given().contentType(ContentType.JSON)
                .body(Map.of("nombres", "Rosa", "apellidos", "Diaz", "correo", correo,
                        "telefono", "977666555", "password", PASSWORD,
                        "confirmacionPassword", PASSWORD))
                .when().post("/api/v1/auth/register")
                .then().statusCode(201);

        given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo, "password", PASSWORD))
                .when().post("/api/v1/auth/login")
                .then().statusCode(200);
        return correo;
    }

    private Map<String, String> login(String correo) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo, "password", PASSWORD))
                .when().post("/api/v1/auth/login")
                .then().statusCode(200)
                .extract().jsonPath().getMap("", String.class, String.class);
    }

    private String refrescar(String refreshToken) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", refreshToken))
                .when().post("/api/v1/auth/refresh")
                .then().statusCode(200).extract().path("refreshToken");
    }

    @Test
    void refreshValidoRotaElToken() {
        String correo = registrarYLoguear();
        Map<String, String> sesion = login(correo);

        given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", sesion.get("refreshToken")))
                .when().post("/api/v1/auth/refresh")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", not(equalTo(sesion.get("refreshToken"))));
    }

    @Test
    void reutilizarUnRefreshYaRotadoDevuelve401() {
        String correo = registrarYLoguear();
        String original = login(correo).get("refreshToken");

        refrescar(original);

        given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", original))
                .when().post("/api/v1/auth/refresh")
                .then()
                .statusCode(401)
                .body("code", equalTo("UNAUTHENTICATED"));
    }

    @Test
    void reutilizacionRevocaTodasLasSesionesDelUsuario() {
        String correo = registrarYLoguear();
        String original = login(correo).get("refreshToken");
        String rotado = refrescar(original);

        // Reutilizar el token viejo dispara la revocacion global
        given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", original))
                .when().post("/api/v1/auth/refresh")
                .then().statusCode(401);

        // El token vigente (el rotado) tambien deja de servir
        given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", rotado))
                .when().post("/api/v1/auth/refresh")
                .then()
                .statusCode(401)
                .body("code", equalTo("UNAUTHENTICATED"));
    }

    @Test
    void logoutRevocaElRefreshYDevuelve204() {
        String correo = registrarYLoguear();
        String refresh = login(correo).get("refreshToken");

        given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", refresh))
                .when().post("/api/v1/auth/logout")
                .then().statusCode(204);

        given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", refresh))
                .when().post("/api/v1/auth/refresh")
                .then().statusCode(401);
    }

    @Test
    void refreshDeTokenInexistenteDevuelve401() {
        given().contentType(ContentType.JSON)
                .body(Map.of("refreshToken", "token-que-no-existe-1234567890"))
                .when().post("/api/v1/auth/refresh")
                .then()
                .statusCode(401)
                .body("code", equalTo("UNAUTHENTICATED"));
    }
}
