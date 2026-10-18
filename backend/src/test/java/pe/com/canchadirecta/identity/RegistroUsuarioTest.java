package pe.com.canchadirecta.identity;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integracion del registro de usuarios contra el MySQL real (perfil test).
 * Se usan correos unicos para no interferir entre ejecuciones.
 */
@QuarkusTest
class RegistroUsuarioTest {

    private static Map<String, Object> cuerpo(String correo, String password, String confirmacion) {
        return Map.of(
                "nombres", "Ana",
                "apellidos", "Perez",
                "correo", correo,
                "telefono", "999888777",
                "password", password,
                "confirmacionPassword", confirmacion);
    }

    @Test
    void registraCuentaNuevaConRolUser() {
        String correo = "ana." + System.nanoTime() + "@canchadirecta.pe";

        given().contentType(ContentType.JSON)
                .body(cuerpo(correo, "ClaveSegura123", "ClaveSegura123"))
                .when().post("/api/v1/auth/register")
                .then()
                .statusCode(201)
                .body("correo", equalTo(correo))
                .body("estado", equalTo("ACTIVO"))
                .body("roles", hasItem("USER"))
                .body("id", notNullValue())
                .body("claveHash", not(containsString("ClaveSegura123")));
    }

    @Test
    void rechazaCorreoDuplicadoCon409() {
        String correo = "dup." + System.nanoTime() + "@canchadirecta.pe";

        given().contentType(ContentType.JSON)
                .body(cuerpo(correo, "ClaveSegura123", "ClaveSegura123"))
                .when().post("/api/v1/auth/register")
                .then().statusCode(201);

        given().contentType(ContentType.JSON)
                .body(cuerpo(correo, "ClaveSegura123", "ClaveSegura123"))
                .when().post("/api/v1/auth/register")
                .then()
                .statusCode(409)
                .body("code", equalTo("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void rechazaPasswordCortaCon400() {
        String correo = "corta." + System.nanoTime() + "@canchadirecta.pe";

        given().contentType(ContentType.JSON)
                .body(cuerpo(correo, "123", "123"))
                .when().post("/api/v1/auth/register")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void rechazaConfirmacionDistintaCon400() {
        String correo = "nomatch." + System.nanoTime() + "@canchadirecta.pe";

        given().contentType(ContentType.JSON)
                .body(cuerpo(correo, "ClaveSegura123", "OtraClave456"))
                .when().post("/api/v1/auth/register")
                .then()
                .statusCode(400)
                .body("detail", containsString("confirmaci"));
    }
}
