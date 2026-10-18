package pe.com.canchadirecta.platform;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Gestion de usuarios, roles y estado de cuenta (CU06.1).
 */
@QuarkusTest
class AdminUsuariosTest {

    @Inject
    EntityManager em;

    @Test
    void unUsuarioNormalNoGestionaUsuarios() {
        String usuario = nuevoUsuario("user.gest");
        given().auth().oauth2(usuario)
                .when().get("/api/v1/admin/usuarios")
                .then().statusCode(403);
        given().auth().oauth2(usuario).contentType(ContentType.JSON)
                .body(Map.of("roles", List.of("PLATFORM_ADMIN")))
                .when().post("/api/v1/admin/usuarios/1/roles")
                .then().statusCode(403);
    }

    @Test
    void listaYFiltraUsuarios() {
        String admin = nuevoAdmin("admin.lista");
        String correo = "lista." + System.nanoTime() + "@canchadirecta.pe";
        registrar(correo, "Lista", "Usuario", "911222333");

        List<Map<String, Object>> usuarios = given().auth().oauth2(admin)
                .when().get("/api/v1/admin/usuarios?correo=" + correo)
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].correo", equalTo(correo))
                .body("[0].estado", equalTo("ACTIVO"))
                .body("[0].roles", equalTo(List.of("USER")))
                .extract().jsonPath().getList("$");
        long id = ((Number) usuarios.get(0).get("id")).longValue();

        given().auth().oauth2(admin)
                .when().get("/api/v1/admin/usuarios/" + id)
                .then().statusCode(200)
                .body("correo", equalTo(correo));

        given().auth().oauth2(admin)
                .when().get("/api/v1/admin/usuarios?rol=PLATFORM_ADMIN&limite=200")
                .then().statusCode(200)
                .body("roles", org.hamcrest.Matchers.everyItem(hasItem("PLATFORM_ADMIN")));

        given().auth().oauth2(admin)
                .when().get("/api/v1/admin/usuarios?estado=INVENTADO")
                .then().statusCode(400);
        given().auth().oauth2(admin)
                .when().get("/api/v1/admin/usuarios?rol=INVENTADO")
                .then().statusCode(400);
        given().auth().oauth2(admin)
                .when().get("/api/v1/admin/usuarios/999999999")
                .then().statusCode(404);
    }

    @Test
    void cambiaRolesAvisaYAudita() {
        String admin = nuevoAdmin("admin.roles");
        String correo = "roles." + System.nanoTime() + "@canchadirecta.pe";
        String usuario = registrarYEntrar(correo, "Roles", "Usuario");
        long id = idDe(admin, correo);

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("roles", List.of("USER", "OWNER")))
                .when().post("/api/v1/admin/usuarios/" + id + "/roles")
                .then().statusCode(200)
                .body("roles", hasItems("USER", "OWNER"));

        // L-20: al cambiar roles se revocan los access tokens ya emitidos
        given().auth().oauth2(usuario)
                .when().get("/api/v1/users/me")
                .then().statusCode(401)
                .body("code", equalTo("UNAUTHENTICATED"));

        String nuevoToken = iniciarSesion(correo);
        given().auth().oauth2(nuevoToken)
                .when().get("/api/v1/users/me")
                .then().statusCode(200)
                .body("roles", hasItems("USER", "OWNER"));

        given().auth().oauth2(nuevoToken)
                .when().get("/api/v1/notificaciones")
                .then().statusCode(200)
                .body("notificaciones[0].titulo", equalTo("Tus roles cambiaron"));

        List<Map<String, Object>> auditoria = given().auth().oauth2(admin)
                .when().get("/api/v1/admin/auditoria?limite=10")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        Map<String, Object> entrada = auditoria.stream()
                .filter(a -> "USUARIO_ROLES_CAMBIADOS".equals(a.get("accion")))
                .findFirst().orElse(null);
        assertNotNull(entrada);
        assertEquals(String.valueOf(id), entrada.get("idEntidad"));

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("roles", List.of("SUPERADMIN")))
                .when().post("/api/v1/admin/usuarios/" + id + "/roles")
                .then().statusCode(400);
        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("roles", List.of()))
                .when().post("/api/v1/admin/usuarios/" + id + "/roles")
                .then().statusCode(400);
    }

    @Test
    void desactivaYReactivaLaCuenta() {
        String admin = nuevoAdmin("admin.estado");
        String correo = "estado." + System.nanoTime() + "@canchadirecta.pe";
        registrar(correo, "Estado", "Usuario", "911222444");
        long id = idDe(admin, correo);

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "INACTIVO"))
                .when().post("/api/v1/admin/usuarios/" + id + "/estado")
                .then().statusCode(200)
                .body("estado", equalTo("INACTIVO"));

        given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo, "password", "ClaveSegura123"))
                .when().post("/api/v1/auth/login")
                .then().statusCode(403);

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "INACTIVO"))
                .when().post("/api/v1/admin/usuarios/" + id + "/estado")
                .then().statusCode(409);

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "ACTIVO"))
                .when().post("/api/v1/admin/usuarios/" + id + "/estado")
                .then().statusCode(200)
                .body("estado", equalTo("ACTIVO"));

        assertNotNull(iniciarSesion(correo));

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "INVENTADO"))
                .when().post("/api/v1/admin/usuarios/" + id + "/estado")
                .then().statusCode(400);

        List<Map<String, Object>> auditoria = given().auth().oauth2(admin)
                .when().get("/api/v1/admin/auditoria?limite=10")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        assertNotNull(auditoria.stream()
                .filter(a -> "USUARIO_ESTADO_CAMBIADO".equals(a.get("accion")))
                .findFirst().orElse(null));
    }

    // ---------------------------------------------------------------- utilidades

    private long idDe(String tokenAdmin, String correo) {
        List<Map<String, Object>> usuarios = given().auth().oauth2(tokenAdmin)
                .when().get("/api/v1/admin/usuarios?correo=" + correo)
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        assertEquals(1, usuarios.size());
        return ((Number) usuarios.get(0).get("id")).longValue();
    }

    private String nuevoAdmin(String prefijo) {
        String correo = prefijo + "." + System.nanoTime() + "@canchadirecta.pe";
        registrar(correo, "Admin", "Usuarios", "922333444");
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "INSERT INTO usuario_rol (id_usuario, id_rol) "
                                + "SELECT u.id_usuario, r.id_rol FROM usuario u, rol r "
                                + "WHERE u.correo = :correo AND r.nombre = 'PLATFORM_ADMIN'")
                .setParameter("correo", correo).executeUpdate());
        String token = iniciarSesion(correo);
        given().auth().oauth2(token)
                .when().get("/api/v1/users/me")
                .then().statusCode(200)
                .body("roles", hasItem("PLATFORM_ADMIN"));
        return token;
    }

    private String nuevoUsuario(String prefijo) {
        String correo = prefijo + "." + System.nanoTime() + "@canchadirecta.pe";
        return registrarYEntrar(correo, "Normal", "Usuario");
    }

    private String registrarYEntrar(String correo, String nombres, String apellidos) {
        registrar(correo, nombres, apellidos, telefonoUnico());
        return iniciarSesion(correo);
    }

    private static void registrar(String correo, String nombres, String apellidos, String telefono) {
        given().contentType(ContentType.JSON)
                .body(Map.of(
                        "nombres", nombres,
                        "apellidos", apellidos,
                        "correo", correo,
                        "telefono", telefono,
                        "password", "ClaveSegura123",
                        "confirmacionPassword", "ClaveSegura123"))
                .when().post("/api/v1/auth/register")
                .then().statusCode(201);
    }

    private static String iniciarSesion(String correo) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo, "password", "ClaveSegura123"))
                .when().post("/api/v1/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");
    }

    private static String telefonoUnico() {
        return "9" + (10_000_000L + Math.floorMod(System.nanoTime(), 89_999_999L));
    }
}
