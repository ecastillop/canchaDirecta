package pe.com.canchadirecta.platform;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import pe.com.canchadirecta.shared.application.port.out.ConfiguracionPlataforma;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Configuracion persistente de plataforma (ADR-0037). */
@QuarkusTest
class ConfiguracionPlataformaTest {

    @Inject
    EntityManager em;

    @Inject
    ConfiguracionPlataforma configuracion;

    @Test
    void consultaYActualizaLaConfiguracionYValidaRangos() {
        String admin = nuevoAdmin("admin.config");
        JsonPath original = given().auth().oauth2(admin)
                .when().get("/api/v1/admin/configuracion")
                .then().statusCode(200)
                .body("comisionPorcentaje", org.hamcrest.Matchers.greaterThanOrEqualTo(0f))
                .body("diasAnticipacionMax", org.hamcrest.Matchers.greaterThan(0))
                .extract().jsonPath();
        assertNotNull(original.getString("fechaActualizacion"));

        try {
            given().auth().oauth2(admin).contentType(ContentType.JSON)
                    .body(payload("12.50", 48, 3, 120, "ayuda@canchadirecta.pe"))
                    .when().put("/api/v1/admin/configuracion")
                    .then().statusCode(200)
                    .body("comisionPorcentaje", org.hamcrest.Matchers.equalTo(12.5f))
                    .body("horasCancelacionGratis", org.hamcrest.Matchers.equalTo(48))
                    .body("horasMinimasCancelacion", org.hamcrest.Matchers.equalTo(3))
                    .body("diasAnticipacionMax", org.hamcrest.Matchers.equalTo(120))
                    .body("correoSoporte", org.hamcrest.Matchers.equalTo("ayuda@canchadirecta.pe"));

            given().auth().oauth2(admin)
                    .when().get("/api/v1/admin/configuracion")
                    .then().statusCode(200)
                    .body("comisionPorcentaje", org.hamcrest.Matchers.equalTo(12.5f))
                    .body("diasAnticipacionMax", org.hamcrest.Matchers.equalTo(120));

            // El puerto compartido ve el cambio sin reiniciar (ADR-0037).
            assertEquals(0, new BigDecimal("12.50").compareTo(configuracion.comisionPorcentaje()));
            assertEquals(48, configuracion.horasCancelacionGratis());
            assertEquals(3, configuracion.horasMinimasCancelacion());
            assertEquals(120, configuracion.diasAnticipacionMax());
            assertEquals("ayuda@canchadirecta.pe", configuracion.correoSoporte());

            given().auth().oauth2(admin).contentType(ContentType.JSON)
                    .body(payload("101", 24, 2, 365, "soporte@canchadirecta.pe"))
                    .when().put("/api/v1/admin/configuracion")
                    .then().statusCode(400);
            given().auth().oauth2(admin).contentType(ContentType.JSON)
                    .body(payload("10", 24, 2, 365, "no-es-correo"))
                    .when().put("/api/v1/admin/configuracion")
                    .then().statusCode(400);
            given().auth().oauth2(admin).contentType(ContentType.JSON)
                    .body(payload("10", 24, 2, 0, "soporte@canchadirecta.pe"))
                    .when().put("/api/v1/admin/configuracion")
                    .then().statusCode(400);

            String usuario = nuevoUsuario("user.config");
            given().auth().oauth2(usuario)
                    .when().get("/api/v1/admin/configuracion")
                    .then().statusCode(403);
            given().when().get("/api/v1/admin/configuracion")
                    .then().statusCode(401);
        } finally {
            given().auth().oauth2(admin).contentType(ContentType.JSON)
                    .body(payload(original.getString("comisionPorcentaje"),
                            original.getLong("horasCancelacionGratis"),
                            original.getLong("horasMinimasCancelacion"),
                            original.getLong("diasAnticipacionMax"),
                            original.getString("correoSoporte")))
                    .when().put("/api/v1/admin/configuracion")
                    .then().statusCode(200);
        }
    }

    private static Map<String, Object> payload(String comision, long horasGratis, long horasMinimas,
                                               long dias, String correo) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("comisionPorcentaje", new BigDecimal(comision));
        cuerpo.put("horasCancelacionGratis", horasGratis);
        cuerpo.put("horasMinimasCancelacion", horasMinimas);
        cuerpo.put("diasAnticipacionMax", dias);
        cuerpo.put("correoSoporte", correo);
        return cuerpo;
    }

    private String nuevoAdmin(String prefijo) {
        String correo = prefijo + "." + System.nanoTime() + "@canchadirecta.pe";
        registrar(correo, "Admin");
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "INSERT INTO usuario_rol (id_usuario, id_rol) "
                                + "SELECT u.id_usuario, r.id_rol FROM usuario u, rol r "
                                + "WHERE u.correo = :correo AND r.nombre = 'PLATFORM_ADMIN'")
                .setParameter("correo", correo).executeUpdate());
        return iniciarSesion(correo);
    }

    private String nuevoUsuario(String prefijo) {
        String correo = prefijo + "." + System.nanoTime() + "@canchadirecta.pe";
        registrar(correo, "Usuario");
        return iniciarSesion(correo);
    }

    private void registrar(String correo, String rol) {
        given().contentType(ContentType.JSON)
                .body(Map.of(
                        "nombres", rol,
                        "apellidos", "Config",
                        "correo", correo,
                        "telefono", "988666" + Math.floorMod(System.nanoTime(), 1000),
                        "password", "ClaveSegura123",
                        "confirmacionPassword", "ClaveSegura123"))
                .when().post("/api/v1/auth/register")
                .then().statusCode(201);
    }

    private String iniciarSesion(String correo) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo, "password", "ClaveSegura123"))
                .when().post("/api/v1/auth/login")
                .then().statusCode(200).extract().path("accessToken");
    }
}
