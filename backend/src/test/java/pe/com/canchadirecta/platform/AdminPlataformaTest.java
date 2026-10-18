package pe.com.canchadirecta.platform;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Administracion de plataforma (F07): validacion de propietarios (CU06.2), supervision de solo
 * lectura (CU06.3/CU06.4) y auditoria (CU06.7).
 */
@QuarkusTest
class AdminPlataformaTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @Inject
    EntityManager em;

    @Test
    void unUsuarioNormalNoAccedeAAdministracion() {
        String usuario = nuevoUsuario("user.admin", "Regular", "User");
        given().auth().oauth2(usuario)
                .when().get("/api/v1/admin/propietarios")
                .then().statusCode(403);
        given().auth().oauth2(usuario)
                .when().get("/api/v1/admin/establecimientos")
                .then().statusCode(403);
        given().auth().oauth2(usuario)
                .when().get("/api/v1/admin/auditoria")
                .then().statusCode(403);
    }

    @Test
    void validaPropietarioAvisaYAudita() {
        String admin = nuevoAdmin("admin.val");
        String correoDueno = "dueno.val." + System.nanoTime() + "@canchadirecta.pe";
        String dueno = registrarYEntrar(correoDueno, "Dueno", "Val");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idPropietario = propietarioDe(idEstablecimiento);

        String estado = given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "VALIDADO"))
                .when().post("/api/v1/admin/propietarios/" + idPropietario + "/validacion")
                .then().statusCode(200)
                .body("estadoValidacion", equalTo("VALIDADO"))
                .body("correo", equalTo(correoDueno))
                .extract().path("estadoValidacion");
        assertEquals("VALIDADO", estado);

        given().auth().oauth2(dueno)
                .when().get("/api/v1/notificaciones")
                .then().statusCode(200)
                .body("notificaciones[0].titulo", equalTo("Propietario validado"));

        List<Map<String, Object>> auditoria = given().auth().oauth2(admin)
                .when().get("/api/v1/admin/auditoria?limite=10")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        Map<String, Object> ultima = auditoria.get(0);
        assertEquals("PROPIETARIO_VALIDADO", ultima.get("accion"));
        assertEquals("propietario", ultima.get("entidad"));
        assertEquals(String.valueOf(idPropietario), ultima.get("idEntidad"));

        // VALIDADO es terminal para la validacion.
        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "VALIDADO"))
                .when().post("/api/v1/admin/propietarios/" + idPropietario + "/validacion")
                .then().statusCode(409);
    }

    @Test
    void rechazarExigeMotivoYAvisaAlPropietario() {
        String admin = nuevoAdmin("admin.rech");
        String correoDueno = "dueno.rech." + System.nanoTime() + "@canchadirecta.pe";
        String dueno = registrarYEntrar(correoDueno, "Dueno", "Rech");
        Long idPropietario = propietarioDe(crearEstablecimiento(dueno));

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "RECHAZADO"))
                .when().post("/api/v1/admin/propietarios/" + idPropietario + "/validacion")
                .then().statusCode(400);

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "RECHAZADO", "motivo", "Documento ilegible"))
                .when().post("/api/v1/admin/propietarios/" + idPropietario + "/validacion")
                .then().statusCode(200)
                .body("estadoValidacion", equalTo("RECHAZADO"));

        given().auth().oauth2(dueno)
                .when().get("/api/v1/notificaciones")
                .then().statusCode(200)
                .body("notificaciones[0].titulo", equalTo("Validacion rechazada"))
                .body("notificaciones[0].mensaje", org.hamcrest.Matchers.containsString("Documento ilegible"));

        // Un rechazo puede corregirse y volver a validarse.
        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "VALIDADO"))
                .when().post("/api/v1/admin/propietarios/" + idPropietario + "/validacion")
                .then().statusCode(200)
                .body("estadoValidacion", equalTo("VALIDADO"));
    }

    @Test
    void supervisaEstablecimientosReservasYPagos() {
        String admin = nuevoAdmin("admin.sup");
        String dueno = nuevoUsuario("dueno.sup", "Dueno", "Sup");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idCancha = crearCancha(dueno, idEstablecimiento);
        String titular = nuevoUsuario("titular.sup", "Titular", "Sup");
        Long idReserva = crearReserva(titular, idCancha, 19);
        Long idPago = pagar(titular, idReserva);

        List<Map<String, Object>> establecimientos = given().auth().oauth2(admin)
                .when().get("/api/v1/admin/establecimientos?limite=200")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        Map<String, Object> establecimiento = establecimientos.stream()
                .filter(e -> ((Number) e.get("id")).longValue() == idEstablecimiento)
                .findFirst().orElse(null);
        assertNotNull(establecimiento);
        assertEquals(1, ((Number) establecimiento.get("canchas")).intValue());

        List<Map<String, Object>> reservas = given().auth().oauth2(admin)
                .when().get("/api/v1/admin/reservas?estado=PENDIENTE&limite=200")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        Map<String, Object> reserva = reservas.stream()
                .filter(r -> ((Number) r.get("id")).longValue() == idReserva)
                .findFirst().orElse(null);
        assertNotNull(reserva);
        assertEquals("Titular Sup", reserva.get("titular"));

        List<Map<String, Object>> pagos = given().auth().oauth2(admin)
                .when().get("/api/v1/admin/pagos?limite=200")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        Map<String, Object> pago = pagos.stream()
                .filter(p -> ((Number) p.get("id")).longValue() == idPago)
                .findFirst().orElse(null);
        assertNotNull(pago);
        assertEquals(0, new BigDecimal(String.valueOf(pago.get("reembolsado"))).signum());
        assertEquals("APROBADO", pago.get("estado"));
    }

    // ---------------------------------------------------------------- utilidades

    private Long propietarioDe(Long idEstablecimiento) {
        return ((Number) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                        "SELECT id_propietario FROM establecimiento WHERE id_establecimiento = :id")
                .setParameter("id", idEstablecimiento).getSingleResult())).longValue();
    }

    private Long crearEstablecimiento(String token) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito(),
                        "nombre", "Complejo Admin " + System.nanoTime(),
                        "direccion", "Av. Plataforma 500",
                        "referencia", "Frente al parque"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long crearCancha(String token, Long idEstablecimiento) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "nombre", "Cancha admin " + System.nanoTime(),
                        "descripcion", "Futbol 7 sintetico",
                        "capacidad", 14,
                        "precioBase", new BigDecimal("70.00")))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/canchas")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long crearReserva(String token, Long idCancha, int hora) {
        LocalDate fecha = LocalDate.ofInstant(Instant.now().plus(3, ChronoUnit.DAYS), ZONA);
        Instant inicio = fecha.atTime(hora, 0).atZone(ZONA).toInstant();
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("canchaId", idCancha);
        cuerpo.put("inicio", inicio.toString());
        cuerpo.put("fin", inicio.plus(1, ChronoUnit.HOURS).toString());
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(cuerpo)
                .when().post("/api/v1/reservas")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long pagar(String token, Long idReserva) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("medioPago", "TARJETA"))
                .when().post("/api/v1/reservas/" + idReserva + "/pago")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long idDistrito() {
        Object valor = QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery("SELECT MIN(id_distrito) FROM distrito").getSingleResult());
        return ((Number) valor).longValue();
    }

    private String nuevoAdmin(String prefijo) {
        String correo = prefijo + "." + System.nanoTime() + "@canchadirecta.pe";
        registrar(correo, "Admin", "Plataforma", "977666" + Math.floorMod(System.nanoTime(), 1000));
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

    private String nuevoUsuario(String prefijo, String nombres, String apellidos) {
        String correo = prefijo + "." + System.nanoTime() + "@canchadirecta.pe";
        return registrarYEntrar(correo, nombres, apellidos);
    }

    private String registrarYEntrar(String correo, String nombres, String apellidos) {
        registrar(correo, nombres, apellidos, "966777" + Math.floorMod(System.nanoTime(), 1000));
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

    private static String documentoUnico() {
        return String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
    }
}
