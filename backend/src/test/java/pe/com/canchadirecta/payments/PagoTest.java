package pe.com.canchadirecta.payments;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integracion del pago simulado y la politica de reembolso (CU03.4 / CU05.1 / CU05.3, RN-05,
 * L-12).
 *
 * <p>Cubre: cobro aprobado con su evento, rechazo determinista del simulador y reintento,
 * un unico pago aprobado por reserva (RN-05), autorizacion del titular, monto total con
 * cochera, y la politica economica al cancelar (dentro/fuera de la ventana gratuita) y al
 * rechazar el propietario.</p>
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PagoTest {

    @Inject
    EntityManager em;

    private String token;
    private Instant base;

    @BeforeEach
    void preparar() {
        token = registrarEIniciarSesion("pagador", "Pago", "Prueba", "911222333");
        base = Instant.now().truncatedTo(ChronoUnit.MINUTES)
                .plus(ThreadLocalRandom.current().nextInt(2, 60), ChronoUnit.HOURS);
    }

    // ------------------------------------------------------------- pago simulado

    @Test
    void pagoAprobadoRegistraElEventoYSePuedeConsultar() {
        Long idReserva = idDe(reservaFutura(2, ChronoUnit.DAYS));

        pagar(idReserva, "TARJETA", null)
                .then()
                .statusCode(201)
                .body("reservaId", equalTo(idReserva.intValue()))
                .body("monto", equalTo(80.00f))
                .body("estado", equalTo("APROBADO"))
                .body("codigoOperacion", equalTo("SIM-8000"))
                .body("fechaPago", notNullValue());

        given().auth().oauth2(token).when().get("/api/v1/reservas/" + idReserva + "/pago")
                .then().statusCode(200)
                .body("estado", equalTo("APROBADO"))
                .body("reembolsos", hasSize(0));

        assertEquals(2, eventosDe(idReserva), "Debe haber un evento SOLICITADO y otro APROBADO.");
    }

    @Test
    void unSegundoPagoAprobadoDevuelve409() {
        Long idReserva = idDe(reservaFutura(2, ChronoUnit.DAYS));
        pagar(idReserva, "TARJETA", null).then().statusCode(201);

        pagar(idReserva, "EFECTIVO", null).then()
                .statusCode(409).body("code", equalTo("CONFLICT"));
        assertEquals(1, pagosAprobadosDe(idReserva), "RN-05: un solo pago aprobado por reserva.");
    }

    @Test
    void pagoRechazadoNoOcupaElCupoYPermiteReintentar() {
        Long idReserva = idDe(reservaFutura(2, ChronoUnit.DAYS));

        pagar(idReserva, "TARJETA", "RECHAZA-001").then()
                .statusCode(201).body("estado", equalTo("RECHAZADO"));

        // Un rechazo no consume el unico cupo APROBADO: el titular puede reintentar.
        pagar(idReserva, "TARJETA", null).then().statusCode(201).body("estado", equalTo("APROBADO"));
        assertEquals(1, pagosAprobadosDe(idReserva));
    }

    @Test
    void elPagoIncluyeElMontoDeLaCochera() {
        Long establecimiento = crearEstablecimiento();
        Long cancha = crearCancha(establecimiento);
        Long cochera = crearCochera(establecimiento, "5.00");
        Instant inicio = base.plus(2, ChronoUnit.DAYS);
        Long idReserva = idDe(reservar(cancha, inicio, inicio.plus(1, ChronoUnit.HOURS), cochera));

        // 80.00 de cancha + 5.00 de una hora de cochera.
        pagar(idReserva, "TARJETA", null).then().statusCode(201).body("monto", equalTo(85.00f));
    }

    // ------------------------------------------------------------ autorizacion

    @Test
    void soloElTitularPuedePagarYConsultar() {
        String otro = registrarEIniciarSesion("intrusopago", "Otro", "Titular", "900111222");
        Long idReserva = idDe(reservaFutura(2, ChronoUnit.DAYS));

        given().auth().oauth2(otro).contentType(ContentType.JSON)
                .body(Map.of("medioPago", "TARJETA"))
                .when().post("/api/v1/reservas/" + idReserva + "/pago")
                .then().statusCode(403).body("code", equalTo("FORBIDDEN"));

        given().auth().oauth2(otro).when().get("/api/v1/reservas/" + idReserva + "/pago")
                .then().statusCode(403).body("code", equalTo("FORBIDDEN"));

        given().contentType(ContentType.JSON).body(Map.of("medioPago", "TARJETA"))
                .when().post("/api/v1/reservas/" + idReserva + "/pago")
                .then().statusCode(401);
    }

    @Test
    void pagoDeReservaInexistenteDevuelve404() {
        pagar(999_999_999L, "TARJETA", null)
                .then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void medioDePagoVacioDevuelve400() {
        Long idReserva = idDe(reservaFutura(2, ChronoUnit.DAYS));
        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("medioPago", ""))
                .when().post("/api/v1/reservas/" + idReserva + "/pago")
                .then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
    }

    // ----------------------------------------------------- politica de reembolso

    @Test
    void cancelarDentroDeLaVentanaGratuitaReembolsaElTotal() {
        Long idReserva = idDe(reservaFutura(2, ChronoUnit.DAYS));
        pagar(idReserva, "TARJETA", null).then().statusCode(201);

        given().auth().oauth2(token).when().post("/api/v1/reservas/" + idReserva + "/cancelar")
                .then().statusCode(200).body("estado", equalTo("CANCELADA"));

        given().auth().oauth2(token).when().get("/api/v1/reservas/" + idReserva + "/pago")
                .then().statusCode(200)
                .body("estado", equalTo("REEMBOLSADO"))
                .body("reembolsos", hasSize(1))
                .body("reembolsos[0].monto", equalTo(80.00f))
                .body("reembolsos[0].estado", equalTo("PROCESADO"));

        // SOLICITADO + APROBADO + REEMBOLSADO.
        assertEquals(3, eventosDe(idReserva));
    }

    @Test
    void cancelarFueraDeLaVentanaGratuitaNoReembolsa() {
        // Inicio a 3 h: se puede cancelar (ventana minima 2 h) pero ya fuera de la gratuita (24 h).
        Long idReserva = idDe(reservaEnHoras(3));
        pagar(idReserva, "TARJETA", null).then().statusCode(201);

        given().auth().oauth2(token).when().post("/api/v1/reservas/" + idReserva + "/cancelar")
                .then().statusCode(200).body("estado", equalTo("CANCELADA"));

        given().auth().oauth2(token).when().get("/api/v1/reservas/" + idReserva + "/pago")
                .then().statusCode(200)
                .body("estado", equalTo("APROBADO"))
                .body("reembolsos", hasSize(0));
    }

    @Test
    void rechazoDelPropietarioReembolsaElTotal() {
        Long idReserva = idDe(reservaFutura(2, ChronoUnit.DAYS));
        pagar(idReserva, "TARJETA", null).then().statusCode(201);

        // El mismo usuario es propietario de la cancha y titular de la reserva.
        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("motivo", "Mantenimiento imprevisto"))
                .when().post("/api/v1/reservas/" + idReserva + "/rechazar")
                .then().statusCode(200).body("estado", equalTo("RECHAZADA"));

        given().auth().oauth2(token).when().get("/api/v1/reservas/" + idReserva + "/pago")
                .then().statusCode(200)
                .body("estado", equalTo("REEMBOLSADO"))
                .body("reembolsos", hasSize(1))
                .body("reembolsos[0].estado", equalTo("PROCESADO"));
    }

    @Test
    void reservaSinPagoConsultadaDevuelve404() {
        Long idReserva = idDe(reservaFutura(2, ChronoUnit.DAYS));
        given().auth().oauth2(token).when().get("/api/v1/reservas/" + idReserva + "/pago")
                .then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }

    // -------------------------------------------------------------- utilidades

    private Response pagar(Long idReserva, String medioPago, String codigoOperacion) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("medioPago", medioPago);
        if (codigoOperacion != null) {
            cuerpo.put("codigoOperacion", codigoOperacion);
        }
        return given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(cuerpo)
                .when().post("/api/v1/reservas/" + idReserva + "/pago")
                .then().extract().response();
    }

    /** Reserva en propio establecimiento/cancha con la cochera indicada (opcional). */
    private Response reservaFutura(int cantidad, ChronoUnit unidad) {
        Long establecimiento = crearEstablecimiento();
        Long cancha = crearCancha(establecimiento);
        Instant inicio = base.plus(cantidad, unidad);
        return reservar(cancha, inicio, inicio.plus(1, ChronoUnit.HOURS), null);
    }

    private Response reservaEnHoras(int horas) {
        Long establecimiento = crearEstablecimiento();
        Long cancha = crearCancha(establecimiento);
        Instant inicio = Instant.now().plus(horas, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES)
                .plus(ThreadLocalRandom.current().nextInt(1, 40), ChronoUnit.MINUTES);
        return reservar(cancha, inicio, inicio.plus(1, ChronoUnit.HOURS), null);
    }

    private Response reservar(Long cancha, Instant inicio, Instant fin, Long cocheraId) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("canchaId", cancha);
        cuerpo.put("inicio", inicio.toString());
        cuerpo.put("fin", fin.toString());
        if (cocheraId != null) {
            cuerpo.put("cocheraId", cocheraId);
        }
        return given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(cuerpo)
                .when().post("/api/v1/reservas")
                .then().statusCode(201).extract().response();
    }

    private String registrarEIniciarSesion(String prefijo, String nombres, String apellidos, String telefono) {
        String correo = prefijo + "." + System.nanoTime() + "@canchadirecta.pe";
        given().contentType(ContentType.JSON)
                .body(Map.of("nombres", nombres, "apellidos", apellidos, "correo", correo,
                        "telefono", telefono, "password", "ClaveSegura123",
                        "confirmacionPassword", "ClaveSegura123"))
                .when().post("/api/v1/auth/register").then().statusCode(201);

        return given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo, "password", "ClaveSegura123"))
                .when().post("/api/v1/auth/login").then().statusCode(200)
                .extract().path("accessToken");
    }

    private Long crearEstablecimiento() {
        Long idDistrito = QuarkusTransaction.requiringNew().call(() ->
                ((Number) em.createNativeQuery("SELECT MIN(id_distrito) FROM distrito").getSingleResult()).longValue());
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("documentoIdentidad", documentoUnico(), "idDistrito", idDistrito,
                        "nombre", "Complejo " + System.nanoTime(), "direccion", "Av. Los Deportes 123",
                        "referencia", "Frente al parque"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long crearCancha(Long idEstablecimiento) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("nombre", "Cancha " + System.nanoTime(), "descripcion", "Futbol 7",
                        "capacidad", 14, "precioBase", new BigDecimal("80.00")))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/canchas")
                .then().statusCode(201).body("id", notNullValue())
                .extract().path("id")).longValue();
    }

    private Long crearCochera(Long idEstablecimiento, String tarifa) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("nombre", "Cochera " + System.nanoTime(), "capacidad", 2,
                        "tarifaHora", new BigDecimal(tarifa)))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/cocheras")
                .then().statusCode(201).body("id", notNullValue())
                .extract().path("id")).longValue();
    }

    private int eventosDe(Long idReserva) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM pago_evento WHERE id_reserva = :id")
                .setParameter("id", idReserva).getSingleResult()).intValue());
    }

    private int pagosAprobadosDe(Long idReserva) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM pago WHERE id_reserva = :id AND estado = 'APROBADO'")
                .setParameter("id", idReserva).getSingleResult()).intValue());
    }

    private static Long idDe(Response respuesta) {
        return ((Number) respuesta.path("id")).longValue();
    }

    private static String documentoUnico() {
        return String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
    }
}
