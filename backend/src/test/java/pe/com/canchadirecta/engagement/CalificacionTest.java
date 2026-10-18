package pe.com.canchadirecta.engagement;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import pe.com.canchadirecta.bookings.application.port.in.FinalizarReservasUseCase;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Calificaciones de reservas finalizadas (CU03.8 calificar / CU02.5 ver, RN-04).
 *
 * <p>Cada prueba usa su propia cancha para que el promedio y el total del listado publico
 * sean deterministas. La reserva se lleva a `FINALIZADA` cerrando su franja por SQL y
 * ejecutando el caso de uso de finalizacion (el temporizador esta apagado en `test`).</p>
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CalificacionTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @TestHTTPResource("/")
    URI raiz;

    @Inject
    EntityManager em;

    @Inject
    FinalizarReservasUseCase finalizacion;

    private String tokenDueno;
    private String tokenTitular;
    private String tokenAjeno;
    private Long idDistrito;
    private LocalDate fecha;
    private final AtomicBoolean preparado = new AtomicBoolean(false);

    @BeforeEach
    void preparar() {
        if (!preparado.compareAndSet(false, true)) {
            return;
        }
        tokenDueno = registrarEIniciarSesion("dueno.cal", "Pablo", "Rojas", "955333440");
        tokenTitular = registrarEIniciarSesion("titular.cal", "Ana", "Leon", "944444440");
        tokenAjeno = registrarEIniciarSesion("ajeno.cal", "Luis", "Diaz", "933555440");
        idDistrito = QuarkusTransaction.requiringNew().call(() ->
                ((Number) em.createNativeQuery("SELECT MIN(id_distrito) FROM distrito").getSingleResult()).longValue());
        fecha = LocalDate.ofInstant(Instant.now().plus(3, ChronoUnit.DAYS), ZONA);
    }

    @Test
    void calificaUnaReservaFinalizadaYLaExponeEnLaCancha() {
        Long idCancha = nuevaCancha();
        Long idReserva = reservaFinalizada(idCancha, tokenTitular);

        given().auth().oauth2(tokenTitular).contentType(ContentType.JSON)
                .body(Map.of("puntaje", 5, "comentario", "Excelente cesped y atencion."))
                .when().post("/api/v1/reservas/" + idReserva + "/calificacion")
                .then().statusCode(201)
                .body("reservaId", equalTo(idReserva.intValue()))
                .body("puntaje", equalTo(5))
                .body("comentario", equalTo("Excelente cesped y atencion."))
                .body("fechaRegistro", org.hamcrest.Matchers.notNullValue());

        // Publica, sin token: promedio y listado de esa cancha.
        given().queryParam("limite", 20)
                .when().get("/api/v1/canchas/" + idCancha + "/calificaciones")
                .then().statusCode(200)
                .body("canchaId", equalTo(idCancha.intValue()))
                .body("total", equalTo(1))
                .body("calificaciones.size()", equalTo(1))
                .body("calificaciones[0].puntaje", equalTo(5));
    }

    @Test
    void noCalificaUnaReservaQueAunNoFinaliza() {
        Long idCancha = nuevaCancha();
        Long idReserva = reservaConfirmadaVigente(idCancha, tokenTitular);

        given().auth().oauth2(tokenTitular).contentType(ContentType.JSON)
                .body(Map.of("puntaje", 4))
                .when().post("/api/v1/reservas/" + idReserva + "/calificacion")
                .then().statusCode(409);
    }

    @Test
    void noPermiteDosCalificacionesDeLaMismaReserva() {
        Long idCancha = nuevaCancha();
        Long idReserva = reservaFinalizada(idCancha, tokenTitular);

        given().auth().oauth2(tokenTitular).contentType(ContentType.JSON)
                .body(Map.of("puntaje", 4))
                .when().post("/api/v1/reservas/" + idReserva + "/calificacion")
                .then().statusCode(201);

        given().auth().oauth2(tokenTitular).contentType(ContentType.JSON)
                .body(Map.of("puntaje", 5))
                .when().post("/api/v1/reservas/" + idReserva + "/calificacion")
                .then().statusCode(409); // RN-04
    }

    @Test
    void noPermiteQueOtroUsuarioCalifiqueReservaAjena() {
        Long idCancha = nuevaCancha();
        Long idReserva = reservaFinalizada(idCancha, tokenTitular);

        given().auth().oauth2(tokenAjeno).contentType(ContentType.JSON)
                .body(Map.of("puntaje", 1))
                .when().post("/api/v1/reservas/" + idReserva + "/calificacion")
                .then().statusCode(403);
    }

    @Test
    void rechazaPuntajeFueraDeRango() {
        Long idCancha = nuevaCancha();
        Long idReserva = reservaFinalizada(idCancha, tokenTitular);

        given().auth().oauth2(tokenTitular).contentType(ContentType.JSON)
                .body(Map.of("puntaje", 6))
                .when().post("/api/v1/reservas/" + idReserva + "/calificacion")
                .then().statusCode(400);
    }

    @Test
    void respondeNoEncontradoConReservaOCanchaInexistente() {
        given().auth().oauth2(tokenTitular).contentType(ContentType.JSON)
                .body(Map.of("puntaje", 5))
                .when().post("/api/v1/reservas/999999999/calificacion")
                .then().statusCode(404);

        given().when().get("/api/v1/canchas/999999999/calificaciones")
                .then().statusCode(404);
    }

    @Test
    void elPromedioReflejaTodasLasCalificacionesDeLaCancha() {
        Long idCancha = nuevaCancha();
        Long r1 = reservaFinalizada(idCancha, tokenTitular);
        Long r2 = reservaFinalizada(idCancha, tokenTitular);

        calificar(r1, tokenTitular, 5);
        calificar(r2, tokenTitular, 4);

        double promedio = given().when().get("/api/v1/canchas/" + idCancha + "/calificaciones")
                .then().statusCode(200)
                .body("total", equalTo(2))
                .body("calificaciones.size()", greaterThanOrEqualTo(2))
                .extract().jsonPath().getDouble("promedio");
        assertEquals(4.5, promedio, 0.0001);
    }

    // ---------------------------------------------------------------- utilidades

    private void calificar(Long idReserva, String token, int puntaje) {
        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("puntaje", puntaje))
                .when().post("/api/v1/reservas/" + idReserva + "/calificacion")
                .then().statusCode(201);
    }

    /** Crea un establecimiento + cancha nuevos bajo el propietario y devuelve el id de la cancha. */
    private Long nuevaCancha() {
        Long idEstablecimiento = ((Number) given().auth().oauth2(tokenDueno).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito,
                        "nombre", "Complejo Calificacion " + System.nanoTime(),
                        "direccion", "Av. Resena 300",
                        "referencia", "Frente al parque"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201).extract().path("id")).longValue();

        return ((Number) given().auth().oauth2(tokenDueno).contentType(ContentType.JSON)
                .body(Map.of(
                        "nombre", "Cancha calificacion " + System.nanoTime(),
                        "descripcion", "Futbol 7 con grass sintetico",
                        "capacidad", 14,
                        "precioBase", new BigDecimal("70.00")))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/canchas")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    /** Reserva confirmada cuya franja aun no empezo (no es calificable todavia). */
    private Long reservaConfirmadaVigente(Long idCancha, String tokenTitular) {
        Instant inicio = fecha.atTime(9, 0).atZone(ZONA).toInstant();
        Long idReserva = crearReserva(idCancha, tokenTitular, inicio);
        given().auth().oauth2(tokenDueno)
                .when().post("/api/v1/reservas/" + idReserva + "/confirmar")
                .then().statusCode(200);
        return idReserva;
    }

    /** Reserva confirmada que se cerro en el pasado y se finalizo (calificable). */
    private Long reservaFinalizada(Long idCancha, String tokenTitular) {
        Instant inicio = fecha.atTime(10, 0).atZone(ZONA).toInstant();
        Long idReserva = crearReserva(idCancha, tokenTitular, inicio);
        given().auth().oauth2(tokenDueno)
                .when().post("/api/v1/reservas/" + idReserva + "/confirmar")
                .then().statusCode(200);

        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "UPDATE reserva SET inicio = TIMESTAMPADD(HOUR, -3, UTC_TIMESTAMP(6)), "
                                + "fin = TIMESTAMPADD(HOUR, -2, UTC_TIMESTAMP(6)) WHERE id_reserva = :id")
                .setParameter("id", idReserva).executeUpdate());
        finalizacion.finalizarConcluidas(200);
        return idReserva;
    }

    private Long crearReserva(Long idCancha, String token, Instant inicio) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("canchaId", idCancha);
        cuerpo.put("inicio", inicio.toString());
        cuerpo.put("fin", inicio.plus(1, ChronoUnit.HOURS).toString());
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(cuerpo)
                .when().post("/api/v1/reservas")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private String registrarEIniciarSesion(String prefijo, String nombres, String apellidos, String telefono) {
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
