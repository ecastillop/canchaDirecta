package pe.com.canchadirecta.reception;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

/** Portal de recepcion (Bloque D, ADR-0038): permiso por sede, agenda, walk-in, check-in y cobros. */
@QuarkusTest
class RecepcionTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @Inject
    EntityManager em;

    private record Sesion(String correo, String token, Long id) {
    }

    @Test
    void operaLaSedeAsignadaConAgendaWalkinCheckinCobrosEIncidencias() {
        Sesion dueno = usuario("dueno.recepcion");
        Long idEstablecimiento = crearEstablecimiento(dueno.token());
        Long idCancha = crearCancha(dueno.token(), idEstablecimiento);
        Sesion cliente = usuario("cliente.recepcion");
        Sesion operador = usuario("operador.recepcion");
        Sesion admin = admin("admin.recepcion");

        // Sin el rol OPERADOR el portal esta cerrado.
        given().auth().oauth2(operador.token())
                .when().get("/api/v1/recepcion/establecimientos")
                .then().statusCode(403);

        // El administrador lo vincula a la sede y le otorga el rol OPERADOR.
        given().auth().oauth2(admin.token()).contentType(ContentType.JSON)
                .body(Map.of("idUsuario", operador.id(), "cargo", "Recepcion"))
                .when().post("/api/v1/admin/establecimientos/" + idEstablecimiento + "/operadores")
                .then().statusCode(201).body("$", hasSize(1))
                .body("[0].cargo", equalTo("Recepcion"));

        Sesion operadorConRol = sesion(operador.correo());
        given().auth().oauth2(operadorConRol.token())
                .when().get("/api/v1/recepcion/establecimientos")
                .then().statusCode(200).body("$", hasSize(1))
                .body("[0].id", equalTo(idEstablecimiento.intValue()));

        given().auth().oauth2(operadorConRol.token())
                .when().get("/api/v1/recepcion/resumen")
                .then().statusCode(200)
                .body("reservasDelDia", greaterThanOrEqualTo(0))
                .body("canchasDisponibles", equalTo(1));

        // Reserva presencial con pago recibido.
        Instant inicio = Instant.now().plus(3, ChronoUnit.HOURS);
        Long reserva = ((Number) given().auth().oauth2(operadorConRol.token()).contentType(ContentType.JSON)
                .body(walkin(idEstablecimiento, idCancha, cliente.id(), inicio, 60, true))
                .when().post("/api/v1/recepcion/reservas")
                .then().statusCode(201)
                .body("pagoEstado", equalTo("APROBADO"))
                .body("estado", equalTo("PENDIENTE"))
                .extract().path("id")).longValue();

        LocalDate dia = LocalDate.ofInstant(inicio, ZONA);
        given().auth().oauth2(operadorConRol.token())
                .when().get("/api/v1/recepcion/agenda?fecha=" + dia)
                .then().statusCode(200).body("$", hasSize(1))
                .body("[0].id", equalTo(reserva.intValue()));

        given().auth().oauth2(operadorConRol.token())
                .when().get("/api/v1/recepcion/reservas?estado=PENDIENTE")
                .then().statusCode(200).body("$", hasSize(1))
                .body("[0].id", equalTo(reserva.intValue()));
        given().auth().oauth2(operadorConRol.token())
                .when().get("/api/v1/recepcion/canchas?idEstablecimiento=" + idEstablecimiento)
                .then().statusCode(200).body("$", hasSize(1))
                .body("[0].id", equalTo(idCancha.intValue()));

        // Confirmar y hacer check-in de una reserva cercana.
        given().auth().oauth2(dueno.token())
                .when().post("/api/v1/reservas/" + reserva + "/confirmar")
                .then().statusCode(200);
        Long enCurso = ((Number) given().auth().oauth2(operadorConRol.token()).contentType(ContentType.JSON)
                .body(walkin(idEstablecimiento, idCancha, cliente.id(), Instant.now().plus(90, ChronoUnit.MINUTES),
                        60, false))
                .when().post("/api/v1/recepcion/reservas")
                .then().statusCode(201).extract().path("id")).longValue();
        given().auth().oauth2(dueno.token())
                .when().post("/api/v1/reservas/" + enCurso + "/confirmar")
                .then().statusCode(200);

        given().auth().oauth2(operadorConRol.token())
                .when().post("/api/v1/recepcion/reservas/" + enCurso + "/checkin")
                .then().statusCode(200).body("estado", equalTo("FINALIZADA"));
        given().auth().oauth2(operadorConRol.token())
                .when().post("/api/v1/recepcion/reservas/" + enCurso + "/checkin")
                .then().statusCode(409);

        // Cobro de un walk-in pendiente y rechazo de un segundo cobro.
        given().auth().oauth2(operadorConRol.token()).contentType(ContentType.JSON)
                .body(Map.of("medioPago", "EFECTIVO"))
                .when().post("/api/v1/recepcion/reservas/" + enCurso + "/cobro")
                .then().statusCode(409);
        Long pendiente = ((Number) given().auth().oauth2(operadorConRol.token()).contentType(ContentType.JSON)
                .body(walkin(idEstablecimiento, idCancha, cliente.id(), Instant.now().plus(4, ChronoUnit.HOURS),
                        60, false))
                .when().post("/api/v1/recepcion/reservas")
                .then().statusCode(201).extract().path("id")).longValue();
        given().auth().oauth2(operadorConRol.token()).contentType(ContentType.JSON)
                .body(Map.of("medioPago", "YAPE"))
                .when().post("/api/v1/recepcion/reservas/" + pendiente + "/cobro")
                .then().statusCode(200).body("pagoEstado", equalTo("APROBADO"));
        given().auth().oauth2(operadorConRol.token()).contentType(ContentType.JSON)
                .body(Map.of("medioPago", "EFECTIVO"))
                .when().post("/api/v1/recepcion/reservas/" + pendiente + "/cobro")
                .then().statusCode(409);

        // Incidencias operativas.
        given().auth().oauth2(operadorConRol.token()).contentType(ContentType.JSON)
                .body(Map.of("asunto", "Cancha mojada", "descripcion", "Se solicito mantenimiento.",
                        "idReserva", pendiente))
                .when().post("/api/v1/recepcion/incidencias")
                .then().statusCode(201).body("estado", equalTo("ABIERTO"));
        given().auth().oauth2(operadorConRol.token())
                .when().get("/api/v1/recepcion/incidencias")
                .then().statusCode(200).body("$", hasSize(1));

        // Un usuario sin sede asignada no accede a la agenda.
        Sesion intruso = usuario("intruso.recepcion");
        given().auth().oauth2(intruso.token())
                .when().get("/api/v1/recepcion/agenda?idEstablecimiento=" + idEstablecimiento)
                .then().statusCode(403);
        given().when().get("/api/v1/recepcion/establecimientos").then().statusCode(401);

        // Al quitar la asignacion, el operador pierde el acceso.
        given().auth().oauth2(admin.token())
                .when().delete("/api/v1/admin/establecimientos/" + idEstablecimiento
                        + "/operadores/" + operador.id())
                .then().statusCode(200).body("$", hasSize(0));
        given().auth().oauth2(operadorConRol.token())
                .when().get("/api/v1/recepcion/agenda?idEstablecimiento=" + idEstablecimiento)
                .then().statusCode(403);
    }

    @Test
    void elWalkinExigeClienteRegistradoYCanchaDeLaSede() {
        Sesion dueno = usuario("dueno.recepcion2");
        Long idEstablecimiento = crearEstablecimiento(dueno.token());
        Long idCancha = crearCancha(dueno.token(), idEstablecimiento);
        Sesion otroDueno = usuario("dueno.recepcion2b");
        Long otraCancha = crearCancha(otroDueno.token(), crearEstablecimiento(otroDueno.token()));

        Sesion operador = usuario("operador.recepcion2");
        Sesion admin = admin("admin.recepcion2");
        given().auth().oauth2(admin.token()).contentType(ContentType.JSON)
                .body(Map.of("idUsuario", operador.id()))
                .when().post("/api/v1/admin/establecimientos/" + idEstablecimiento + "/operadores")
                .then().statusCode(201);
        Sesion conRol = sesion(operador.correo());

        given().auth().oauth2(conRol.token()).contentType(ContentType.JSON)
                .body(walkin(idEstablecimiento, idCancha, null, Instant.now().plus(2, ChronoUnit.HOURS), 60, false))
                .when().post("/api/v1/recepcion/reservas")
                .then().statusCode(400);

        Sesion cliente = usuario("cliente.recepcion2");
        given().auth().oauth2(conRol.token()).contentType(ContentType.JSON)
                .body(walkin(idEstablecimiento, otraCancha, cliente.id(),
                        Instant.now().plus(2, ChronoUnit.HOURS), 60, false))
                .when().post("/api/v1/recepcion/reservas")
                .then().statusCode(403);

        given().auth().oauth2(conRol.token())
                .when().get("/api/v1/recepcion/clientes?q=Usuario")
                .then().statusCode(200).body("$", not(hasSize(0)));
    }

    private Map<String, Object> walkin(Long idEstablecimiento, Long idCancha, Long idCliente, Instant inicio,
                                       int minutos, boolean pagoRecibido) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idEstablecimiento", idEstablecimiento);
        cuerpo.put("idCancha", idCancha);
        cuerpo.put("idCliente", idCliente);
        cuerpo.put("fecha", LocalDate.ofInstant(inicio, ZONA).toString());
        cuerpo.put("horaInicio", LocalTime.ofInstant(inicio, ZONA).withNano(0).toString());
        cuerpo.put("duracionMinutos", minutos);
        cuerpo.put("pagoRecibido", pagoRecibido);
        return cuerpo;
    }

    private Sesion usuario(String prefijo) {
        return sesion(registrar(prefijo));
    }

    private Sesion admin(String prefijo) {
        String correo = registrar(prefijo);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "INSERT INTO usuario_rol (id_usuario, id_rol) "
                                + "SELECT u.id_usuario, r.id_rol FROM usuario u, rol r "
                                + "WHERE u.correo = :correo AND r.nombre = 'PLATFORM_ADMIN'")
                .setParameter("correo", correo).executeUpdate());
        return sesion(correo);
    }

    private String registrar(String prefijo) {
        String correo = prefijo + "." + System.nanoTime() + "@canchadirecta.pe";
        given().contentType(ContentType.JSON)
                .body(Map.of(
                        "nombres", "Usuario",
                        "apellidos", "Recepcion",
                        "correo", correo,
                        "telefono", "977888" + Math.floorMod(System.nanoTime(), 1000),
                        "password", "ClaveSegura123",
                        "confirmacionPassword", "ClaveSegura123"))
                .when().post("/api/v1/auth/register")
                .then().statusCode(201);
        return correo;
    }

    private Sesion sesion(String correo) {
        String token = given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo, "password", "ClaveSegura123"))
                .when().post("/api/v1/auth/login")
                .then().statusCode(200).extract().path("accessToken");
        return new Sesion(correo, token, idUsuario(correo));
    }

    private Long idUsuario(String correo) {
        Object valor = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                        "SELECT id_usuario FROM usuario WHERE correo = :correo")
                .setParameter("correo", correo).getSingleResult());
        return ((Number) valor).longValue();
    }

    private Long crearEstablecimiento(String token) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito(),
                        "nombre", "Complejo Recepcion " + System.nanoTime(),
                        "direccion", "Av. Recepcion 100",
                        "referencia", "Ingreso principal"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long crearCancha(String token, Long idEstablecimiento) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "nombre", "Cancha recepcion " + System.nanoTime(),
                        "descripcion", "Futbol 7 sintetico",
                        "capacidad", 14,
                        "precioBase", new BigDecimal("70.00")))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/canchas")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long idDistrito() {
        Object valor = QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery("SELECT MIN(id_distrito) FROM distrito").getSingleResult());
        return ((Number) valor).longValue();
    }

    private static String documentoUnico() {
        return String.valueOf(java.util.concurrent.ThreadLocalRandom.current()
                .nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
    }
}
