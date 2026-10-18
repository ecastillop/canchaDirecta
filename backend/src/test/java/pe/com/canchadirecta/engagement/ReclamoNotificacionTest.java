package pe.com.canchadirecta.engagement;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reclamos de soporte (CU06.6) y avisos in-app (F06).
 *
 * <p>Cada prueba crea sus propios usuarios para que la bandeja de avisos y el listado de
 * reclamos sean deterministas.</p>
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReclamoNotificacionTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @TestHTTPResource("/")
    URI raiz;

    @Inject
    EntityManager em;

    private Long idDistrito;
    private LocalDate fecha;

    @BeforeAll
    void preparar() {
        idDistrito = QuarkusTransaction.requiringNew().call(() ->
                ((Number) em.createNativeQuery("SELECT MIN(id_distrito) FROM distrito").getSingleResult()).longValue());
        fecha = LocalDate.ofInstant(Instant.now().plus(3, ChronoUnit.DAYS), ZONA);
    }

    @Test
    void creaYListaUnReclamoPropio() {
        String titular = nuevoUsuario("titular.rec", "Ana", "Leon", "944444450");

        Long id = given().auth().oauth2(titular).contentType(ContentType.JSON)
                .body(Map.of("asunto", "Cobro duplicado",
                        "descripcion", "Me cobraron dos veces la misma reserva."))
                .when().post("/api/v1/reclamos")
                .then().statusCode(201)
                .body("estado", equalTo("ABIERTO"))
                .body("asunto", equalTo("Cobro duplicado"))
                .body("idReserva", equalTo(null))
                .extract().jsonPath().getLong("id");

        given().auth().oauth2(titular)
                .when().get("/api/v1/reclamos/mios")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(id.intValue()))
                .body("[0].estado", equalTo("ABIERTO"));
    }

    @Test
    void asociaElReclamoAUnaReservaPropiaYRechazaLaAjena() {
        String dueno = nuevoUsuario("dueno.rec", "Pablo", "Rojas", "955333450");
        String titular = nuevoUsuario("titular.rec2", "Luis", "Diaz", "933555450");
        String ajeno = nuevoUsuario("ajeno.rec2", "Rosa", "Vega", "922555450");
        Long idReserva = reservaConfirmada(nuevaCancha(dueno), titular);

        given().auth().oauth2(titular).contentType(ContentType.JSON)
                .body(Map.of("idReserva", idReserva, "asunto", "Cancha en mal estado",
                        "descripcion", "El cesped tenia huecos."))
                .when().post("/api/v1/reclamos")
                .then().statusCode(201)
                .body("idReserva", equalTo(idReserva.intValue()));

        given().auth().oauth2(ajeno).contentType(ContentType.JSON)
                .body(Map.of("idReserva", idReserva, "asunto", "Reclamo ajeno",
                        "descripcion", "No es mi reserva."))
                .when().post("/api/v1/reclamos")
                .then().statusCode(403);
    }

    @Test
    void rechazaReservaInexistenteYValidaciones() {
        String titular = nuevoUsuario("titular.rec3", "Ana", "Leon", "944444451");

        given().auth().oauth2(titular).contentType(ContentType.JSON)
                .body(Map.of("idReserva", 999999999, "asunto", "Reserva fantasma",
                        "descripcion", "No existe."))
                .when().post("/api/v1/reclamos")
                .then().statusCode(404);

        given().auth().oauth2(titular).contentType(ContentType.JSON)
                .body(Map.of("asunto", " ", "descripcion", "Sin asunto"))
                .when().post("/api/v1/reclamos")
                .then().statusCode(400);

        given().auth().oauth2(titular).contentType(ContentType.JSON)
                .body(Map.of("asunto", "Sin descripcion"))
                .when().post("/api/v1/reclamos")
                .then().statusCode(400);
    }

    @Test
    void elDetalleIncluyeLosSeguimientos() {
        String titular = nuevoUsuario("titular.rec4", "Ana", "Leon", "944444452");
        Long id = crearReclamo(titular);

        given().auth().oauth2(titular).contentType(ContentType.JSON)
                .body(Map.of("comentario", "Adjunto el comprobante del cobro."))
                .when().post("/api/v1/reclamos/" + id + "/seguimientos")
                .then().statusCode(201)
                .body("comentario", equalTo("Adjunto el comprobante del cobro."));

        given().auth().oauth2(titular)
                .when().get("/api/v1/reclamos/" + id)
                .then().statusCode(200)
                .body("id", equalTo(id.intValue()))
                .body("seguimientos.size()", equalTo(1))
                .body("seguimientos[0].comentario", equalTo("Adjunto el comprobante del cobro."));
    }

    @Test
    void noPermiteVerNiComentarReclamosAjenos() {
        String titular = nuevoUsuario("titular.rec5", "Ana", "Leon", "944444453");
        String ajeno = nuevoUsuario("ajeno.rec5", "Luis", "Diaz", "933555453");
        Long id = crearReclamo(titular);

        given().auth().oauth2(ajeno)
                .when().get("/api/v1/reclamos/" + id)
                .then().statusCode(403);

        given().auth().oauth2(ajeno).contentType(ContentType.JSON)
                .body(Map.of("comentario", "Intromision"))
                .when().post("/api/v1/reclamos/" + id + "/seguimientos")
                .then().statusCode(403);
    }

    @Test
    void unReclamoCerradoNoAdmiteComentarios() {
        String titular = nuevoUsuario("titular.rec6", "Ana", "Leon", "944444454");
        Long id = crearReclamo(titular);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "UPDATE reclamo SET estado = 'CERRADO' WHERE id_reclamo = :id")
                .setParameter("id", id).executeUpdate());

        given().auth().oauth2(titular).contentType(ContentType.JSON)
                .body(Map.of("comentario", "Una mas"))
                .when().post("/api/v1/reclamos/" + id + "/seguimientos")
                .then().statusCode(409);
    }

    @Test
    void laConfirmacionDeUnaReservaGeneraUnAviso() {
        String dueno = nuevoUsuario("dueno.rec7", "Pablo", "Rojas", "955333451");
        String titular = nuevoUsuario("titular.rec7", "Ana", "Leon", "944444455");
        Long idReserva = reservaConfirmada(nuevaCancha(dueno), titular);

        given().auth().oauth2(titular)
                .when().get("/api/v1/notificaciones")
                .then().statusCode(200)
                .body("noLeidas", greaterThanOrEqualTo(1))
                .body("notificaciones[0].titulo", equalTo("Reserva confirmada"))
                .body("notificaciones[0].leida", equalTo(false))
                .body("notificaciones[0].mensaje", org.hamcrest.Matchers.containsString(String.valueOf(idReserva)));
    }

    @Test
    void marcaUnAvisoComoLeidoYNoTocaLosAjenos() {
        String dueno = nuevoUsuario("dueno.rec8", "Pablo", "Rojas", "955333452");
        String titular = nuevoUsuario("titular.rec8", "Ana", "Leon", "944444456");
        String ajeno = nuevoUsuario("ajeno.rec8", "Luis", "Diaz", "933555454");
        reservaConfirmada(nuevaCancha(dueno), titular);

        var bandeja = given().auth().oauth2(titular)
                .when().get("/api/v1/notificaciones")
                .then().statusCode(200)
                .extract().jsonPath();
        long noLeidasAntes = bandeja.getLong("noLeidas");
        long idAviso = bandeja.getLong("notificaciones[0].id");

        given().auth().oauth2(ajeno)
                .when().post("/api/v1/notificaciones/" + idAviso + "/leida")
                .then().statusCode(404);

        given().auth().oauth2(titular)
                .when().post("/api/v1/notificaciones/" + idAviso + "/leida")
                .then().statusCode(204);

        var despues = given().auth().oauth2(titular)
                .when().get("/api/v1/notificaciones")
                .then().statusCode(200)
                .body("notificaciones[0].leida", equalTo(true))
                .extract().jsonPath();
        assertEquals(noLeidasAntes - 1, despues.getLong("noLeidas"));
    }

    // ---------------------------------------------------------------- utilidades

    private Long crearReclamo(String token) {
        return given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("asunto", "Consulta", "descripcion", "Necesito ayuda con mi reserva."))
                .when().post("/api/v1/reclamos")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private Long nuevaCancha(String tokenDueno) {
        Long idEstablecimiento = ((Number) given().auth().oauth2(tokenDueno).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito,
                        "nombre", "Complejo Soporte " + System.nanoTime(),
                        "direccion", "Av. Reclamo 400",
                        "referencia", "Frente al parque"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201).extract().path("id")).longValue();

        return ((Number) given().auth().oauth2(tokenDueno).contentType(ContentType.JSON)
                .body(Map.of(
                        "nombre", "Cancha soporte " + System.nanoTime(),
                        "descripcion", "Futbol 7 con grass sintetico",
                        "capacidad", 14,
                        "precioBase", new BigDecimal("70.00")))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/canchas")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    /** Reserva pagada y confirmada; el dueno recibe el aviso correspondiente. */
    private Long reservaConfirmada(Long idCancha, String tokenTitular) {
        return reservaConfirmada(idCancha, tokenTitular, 19);
    }

    private Long reservaConfirmada(Long idCancha, String tokenTitular, int hora) {
        Instant inicio = fecha.atTime(hora, 0).atZone(ZONA).toInstant();
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("canchaId", idCancha);
        cuerpo.put("inicio", inicio.toString());
        cuerpo.put("fin", inicio.plus(1, ChronoUnit.HOURS).toString());
        Long id = ((Number) given().auth().oauth2(tokenTitular).contentType(ContentType.JSON)
                .body(cuerpo)
                .when().post("/api/v1/reservas")
                .then().statusCode(201).extract().path("id")).longValue();

        String tokenDueno = duenoDe(idCancha);
        given().auth().oauth2(tokenDueno)
                .when().post("/api/v1/reservas/" + id + "/confirmar")
                .then().statusCode(200);
        return id;
    }

    /** Token del propietario de la cancha; las pruebas crean una cancha por caso. */
    private String duenoDe(Long idCancha) {
        String correo = (String) QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                        "SELECT u.correo FROM cancha c JOIN establecimiento e ON e.id_establecimiento = c.id_establecimiento "
                                + "JOIN propietario p ON p.id_propietario = e.id_propietario "
                                + "JOIN usuario u ON u.id_usuario = p.id_usuario WHERE c.id_cancha = :id")
                .setParameter("id", idCancha).getSingleResult());
        return iniciarSesion(correo);
    }

    private String nuevoUsuario(String prefijo, String nombres, String apellidos, String telefono) {
        String correo = prefijo + "." + System.nanoTime() + "@canchadirecta.pe";
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
        return iniciarSesion(correo);
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
