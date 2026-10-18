package pe.com.canchadirecta.platform;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;

/** Reportes agregados de plataforma para PLATFORM_ADMIN. */
@QuarkusTest
class AdminReporteTest {

    @Inject
    EntityManager em;

    @Test
    void exponeElReporteYExigeRolDeAdministrador() {
        String admin = nuevoAdmin("admin.reporte");
        given().auth().oauth2(admin)
                .when().get("/api/v1/admin/reportes?dias=7")
                .then().statusCode(200)
                .body("dias", equalTo(7))
                .body("serie", hasSize(7))
                .body("resumen.usuarios", greaterThan(0))
                .body("resumen.usuariosNuevos", greaterThan(-1))
                .body("resumen.establecimientosActivos", greaterThan(-1))
                .body("resumen.reservasPeriodo", greaterThan(-1));

        given().auth().oauth2(admin)
                .when().get("/api/v1/admin/reportes?dias=400")
                .then().statusCode(200)
                .body("dias", equalTo(365))
                .body("serie", hasSize(365));

        String usuario = nuevoUsuario("user.reporte");
        given().auth().oauth2(usuario)
                .when().get("/api/v1/admin/reportes")
                .then().statusCode(403);

        given().when().get("/api/v1/admin/reportes")
                .then().statusCode(401);
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
                        "apellidos", "Reporte",
                        "correo", correo,
                        "telefono", "988777" + Math.floorMod(System.nanoTime(), 1000),
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
