package pe.com.canchadirecta.engagement;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;

/**
 * Moderacion de reclamos por `PLATFORM_ADMIN` (F07 sobre CU06.6).
 *
 * <p>El rol viaja en el claim `groups` del JWT, asi que se promueve al usuario en la base y se
 * vuelve a iniciar sesion para obtener un token con el rol.</p>
 */
@QuarkusTest
class ModeracionReclamoTest {

    @Inject
    EntityManager em;

    @Test
    void unUsuarioNormalNoPuedeModerar() {
        String usuario = nuevoUsuario("user.mod");
        given().auth().oauth2(usuario)
                .when().get("/api/v1/admin/reclamos")
                .then().statusCode(403);
    }

    @Test
    void elAdminListaYFiltraReclamos() {
        String titular = nuevoUsuario("user.list");
        String admin = nuevoAdmin("admin.list");
        Long primero = crearReclamo(titular, "Consulta uno");
        Long segundo = crearReclamo(titular, "Consulta dos");

        given().auth().oauth2(admin)
                .when().get("/api/v1/admin/reclamos")
                .then().statusCode(200)
                .body("id", org.hamcrest.Matchers.hasItems(primero.intValue(), segundo.intValue()));

        given().auth().oauth2(admin)
                .when().get("/api/v1/admin/reclamos?estado=ABIERTO")
                .then().statusCode(200)
                .body("id", org.hamcrest.Matchers.hasItems(primero.intValue(), segundo.intValue()))
                .body("estado", everyItem(equalTo("ABIERTO")));

        given().auth().oauth2(admin)
                .when().get("/api/v1/admin/reclamos?estado=CERRADO")
                .then().statusCode(200)
                .body("estado", everyItem(equalTo("CERRADO")));

        given().auth().oauth2(admin)
                .when().get("/api/v1/admin/reclamos?estado=INVENTADO")
                .then().statusCode(400);
    }

    @Test
    void cambiaElEstadoAnotaComentarioYAvisaAlTitular() {
        String titular = nuevoUsuario("user.estado");
        String admin = nuevoAdmin("admin.estado");
        Long id = crearReclamo(titular, "Quiero revision");

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "EN_REVISION", "comentario", "Estamos revisando el caso."))
                .when().post("/api/v1/admin/reclamos/" + id + "/estado")
                .then().statusCode(200)
                .body("estado", equalTo("EN_REVISION"));

        given().auth().oauth2(titular)
                .when().get("/api/v1/reclamos/" + id)
                .then().statusCode(200)
                .body("estado", equalTo("EN_REVISION"))
                .body("seguimientos", hasSize(1))
                .body("seguimientos[0].comentario", equalTo("Estamos revisando el caso."));

        given().auth().oauth2(titular)
                .when().get("/api/v1/notificaciones")
                .then().statusCode(200)
                .body("notificaciones[0].titulo", equalTo("Reclamo actualizado"))
                .body("notificaciones[0].mensaje", containsString("EN_REVISION"));
    }

    @Test
    void rechazaTransicionesInvalidasYRepetirEstado() {
        String titular = nuevoUsuario("user.trans");
        String admin = nuevoAdmin("admin.trans");
        Long id = crearReclamo(titular, "Para cerrar");

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "CERRADO"))
                .when().post("/api/v1/admin/reclamos/" + id + "/estado")
                .then().statusCode(200)
                .body("estado", equalTo("CERRADO"));

        // CERRADO es terminal.
        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "EN_REVISION"))
                .when().post("/api/v1/admin/reclamos/" + id + "/estado")
                .then().statusCode(409);

        // Repetir el estado actual tampoco es una transicion.
        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "ABIERTO"))
                .when().post("/api/v1/admin/reclamos/" + id + "/estado")
                .then().statusCode(409);

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "INVENTADO"))
                .when().post("/api/v1/admin/reclamos/" + id + "/estado")
                .then().statusCode(400);
    }

    @Test
    void respondeAlTitularYNoRespondeUnReclamoCerrado() {
        String titular = nuevoUsuario("user.resp");
        String admin = nuevoAdmin("admin.resp");
        Long id = crearReclamo(titular, "Necesito respuesta");

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("comentario", "Reembolsamos el cobro duplicado."))
                .when().post("/api/v1/admin/reclamos/" + id + "/seguimientos")
                .then().statusCode(201)
                .body("comentario", equalTo("Reembolsamos el cobro duplicado."));

        given().auth().oauth2(titular)
                .when().get("/api/v1/notificaciones")
                .then().statusCode(200)
                .body("notificaciones[0].titulo", equalTo("Respuesta a tu reclamo"));

        given().auth().oauth2(admin)
                .when().get("/api/v1/admin/reclamos/" + id)
                .then().statusCode(200)
                .body("seguimientos", hasSize(1));

        given().auth().oauth2(admin)
                .when().get("/api/v1/admin/reclamos/999999999")
                .then().statusCode(404);

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "CERRADO"))
                .when().post("/api/v1/admin/reclamos/" + id + "/estado")
                .then().statusCode(200);

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("comentario", "Una mas"))
                .when().post("/api/v1/admin/reclamos/" + id + "/seguimientos")
                .then().statusCode(409);
    }

    // ---------------------------------------------------------------- utilidades

    private Long crearReclamo(String token, String asunto) {
        return given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("asunto", asunto, "descripcion", "Descripcion de la consulta."))
                .when().post("/api/v1/reclamos")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private String nuevoUsuario(String prefijo) {
        String correo = prefijo + "." + System.nanoTime() + "@canchadirecta.pe";
        registrar(correo);
        return iniciarSesion(correo);
    }

    /** Registra, le asigna `PLATFORM_ADMIN` en la base y vuelve a iniciar sesion para el claim. */
    private String nuevoAdmin(String prefijo) {
        String correo = prefijo + "." + System.nanoTime() + "@canchadirecta.pe";
        registrar(correo);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "INSERT INTO usuario_rol (id_usuario, id_rol) "
                                + "SELECT u.id_usuario, r.id_rol FROM usuario u, rol r "
                                + "WHERE u.correo = :correo AND r.nombre = 'PLATFORM_ADMIN'")
                .setParameter("correo", correo).executeUpdate());
        String token = iniciarSesion(correo);
        given().auth().oauth2(token)
                .when().get("/api/v1/users/me")
                .then().statusCode(200)
                .body("roles", org.hamcrest.Matchers.hasItem("PLATFORM_ADMIN"));
        return token;
    }

    private static void registrar(String correo) {
        given().contentType(ContentType.JSON)
                .body(Map.of(
                        "nombres", "Admin",
                        "apellidos", "Prueba",
                        "correo", correo,
                        "telefono", "966555" + Math.floorMod(System.nanoTime(), 1000),
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
}
