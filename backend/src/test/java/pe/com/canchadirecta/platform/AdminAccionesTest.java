package pe.com.canchadirecta.platform;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

/** Acciones administrativas de sedes y actividades (Bloque B). */
@QuarkusTest
class AdminAccionesTest {

    @Inject
    EntityManager em;

    @Test
    void activaDesactivaSedesYCancelaActividades() {
        String admin = nuevoAdmin("admin.acciones");
        String dueno = nuevoUsuario("dueno.acciones");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Instant inicio = Instant.now().plus(5, ChronoUnit.DAYS);
        Long idActividad = ((Number) given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of(
                        "nombre", "Actividad admin " + System.nanoTime(),
                        "descripcion", "Prueba",
                        "inicio", inicio.toString(),
                        "fin", inicio.plus(1, ChronoUnit.HOURS).toString(),
                        "cupos", 10,
                        "precio", new BigDecimal("10.00")))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/actividades")
                .then().statusCode(201).extract().path("id")).longValue();

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "INACTIVO"))
                .when().post("/api/v1/admin/establecimientos/" + idEstablecimiento + "/estado")
                .then().statusCode(200)
                .body("estado", equalTo("INACTIVO"));

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "INACTIVO"))
                .when().post("/api/v1/admin/establecimientos/" + idEstablecimiento + "/estado")
                .then().statusCode(409);

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "ACTIVO"))
                .when().post("/api/v1/admin/establecimientos/" + idEstablecimiento + "/estado")
                .then().statusCode(200)
                .body("estado", equalTo("ACTIVO"));

        given().auth().oauth2(admin).when().get("/api/v1/admin/actividades?limite=200")
                .then().statusCode(200)
                .body("id", hasItem(idActividad.intValue()));

        given().auth().oauth2(admin).when().delete("/api/v1/admin/actividades/" + idActividad)
                .then().statusCode(204);

        given().auth().oauth2(admin).when().get("/api/v1/admin/actividades?limite=200")
                .then().statusCode(200)
                .body("find { it.id == " + idActividad + " }.estado", equalTo("CANCELADA"));

        given().auth().oauth2(dueno).when().delete("/api/v1/admin/actividades/" + idActividad)
                .then().statusCode(403);
    }

    @Test
    void confirmaYRechazaReservasDesdeAdministracion() {
        String admin = nuevoAdmin("admin.reservas");
        String dueno = nuevoUsuario("dueno.reservas");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idCancha = crearCancha(dueno, idEstablecimiento);
        String cliente = nuevoUsuario("cliente.reservas");
        Long confirmar = reservar(cliente, idCancha, 10);
        Long rechazar = reservar(cliente, idCancha, 12);

        given().auth().oauth2(admin).when()
                .post("/api/v1/admin/reservas/" + confirmar + "/confirmar")
                .then().statusCode(200)
                .body("estado", equalTo("CONFIRMADA"));

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("motivo", "Pago no reconocido"))
                .when().post("/api/v1/admin/reservas/" + rechazar + "/rechazar")
                .then().statusCode(200)
                .body("estado", equalTo("RECHAZADA"));

        Long idUsuario = ((Number) given().auth().oauth2(cliente)
                .when().get("/api/v1/users/me")
                .then().statusCode(200).extract().path("id")).longValue();
        String asunto = "Ticket admin " + System.nanoTime();
        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of(
                        "idUsuario", idUsuario,
                        "asunto", asunto,
                        "descripcion", "Consulta registrada desde la mesa de ayuda."))
                .when().post("/api/v1/admin/reclamos")
                .then().statusCode(201)
                .body("estado", equalTo("ABIERTO"));

        given().auth().oauth2(admin).when().get("/api/v1/admin/reclamos?limite=200")
                .then().statusCode(200)
                .body("asunto", hasItem(asunto));
    }

    private Long crearCancha(String token, Long idEstablecimiento) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "nombre", "Cancha acciones " + System.nanoTime(),
                        "descripcion", "Futbol 7",
                        "capacidad", 14,
                        "precioBase", new BigDecimal("70.00")))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/canchas")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long reservar(String token, Long idCancha, int hora) {
        Instant inicio = Instant.now().plus(3, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.DAYS).plus(hora, ChronoUnit.HOURS);
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "canchaId", idCancha,
                        "inicio", inicio.toString(),
                        "fin", inicio.plus(1, ChronoUnit.HOURS).toString()))
                .when().post("/api/v1/reservas")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long crearEstablecimiento(String token) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito(),
                        "nombre", "Complejo Admin " + System.nanoTime(),
                        "direccion", "Av. Admin 100",
                        "referencia", "Ingreso principal"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long idDistrito() {
        Object valor = QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery("SELECT MIN(id_distrito) FROM distrito").getSingleResult());
        return ((Number) valor).longValue();
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
                        "apellidos", "Acciones",
                        "correo", correo,
                        "telefono", "977666" + Math.floorMod(System.nanoTime(), 1000),
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

    private static String documentoUnico() {
        return String.valueOf(java.util.concurrent.ThreadLocalRandom.current()
                .nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
    }
}
