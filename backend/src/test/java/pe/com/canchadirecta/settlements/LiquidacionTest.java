package pe.com.canchadirecta.settlements;

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
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * Integracion de la liquidacion de ingresos del propietario (CU04.8 / CU05.2 / CU05.4, RN-07).
 *
 * <p>Cubre: calculo del bruto, comision y neto sobre reservas pagadas y confirmadas; que no
 * se liquide dos veces la misma reserva; exclusion de reservas sin pago, sin confirmar o
 * canceladas; autorizacion por propietario; y validaciones del periodo.</p>
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LiquidacionTest {

    @Inject
    EntityManager em;

    private String token;
    private Instant base;
    private LocalDate hoy;

    @BeforeEach
    void preparar() {
        token = registrarEIniciarSesion("liquidador", "Liqui", "Dador", "922333444");
        base = Instant.now().truncatedTo(ChronoUnit.MINUTES)
                .plus(ThreadLocalRandom.current().nextInt(2, 60), ChronoUnit.HOURS);
        hoy = LocalDate.now(ZoneOffset.UTC);
    }

    // ------------------------------------------------------------- liquidacion

    @Test
    void generaLiquidacionConComisionYDetalle() {
        Long idReserva = reservaPagadaYConfirmada(2);

        Response respuesta = generar(hoy.toString(), hoy.plusDays(30).toString());
        respuesta.then()
                .statusCode(201)
                .body("periodoInicio", equalTo(hoy.toString()))
                .body("periodoFin", equalTo(hoy.plusDays(30).toString()))
                .body("montoBruto", equalTo(80.00f))
                .body("montoComision", equalTo(8.00f))
                .body("montoNeto", equalTo(72.00f))
                .body("estado", equalTo("PENDIENTE"))
                .body("detalles", hasSize(1))
                .body("detalles[0].reservaId", equalTo(idReserva.intValue()))
                .body("detalles[0].porcentajeComision", equalTo(10.00f))
                .body("detalles[0].montoComision", equalTo(8.00f))
                .body("detalles[0].montoNeto", equalTo(72.00f));

        Long idLiquidacion = ((Number) respuesta.path("id")).longValue();

        given().auth().oauth2(token).when().get("/api/v1/liquidaciones")
                .then().statusCode(200).body("$", hasSize(1))
                .body("[0].id", equalTo(idLiquidacion.intValue()));

        given().auth().oauth2(token).when().get("/api/v1/liquidaciones/" + idLiquidacion)
                .then().statusCode(200).body("detalles", hasSize(1));

        assertEquals(1, detallesDe(idReserva), "RN-07: la reserva aparece en una sola liquidacion.");
    }

    @Test
    void noLiquidaUnaReservaDosVeces() {
        reservaPagadaYConfirmada(2);

        generar(hoy.toString(), hoy.plusDays(30).toString()).then().statusCode(201);
        // Segunda generacion del mismo periodo: no quedan reservas liquidables.
        generar(hoy.toString(), hoy.plusDays(30).toString())
                .then().statusCode(409).body("code", equalTo("CONFLICT"));
    }

    @Test
    void noLiquidaReservasSinPago() {
        Long establecimiento = crearEstablecimiento();
        Long cancha = crearCancha(establecimiento);
        Instant inicio = base.plus(2, ChronoUnit.DAYS);
        reservar(cancha, inicio, inicio.plus(1, ChronoUnit.HOURS));

        generar(hoy.toString(), hoy.plusDays(30).toString())
                .then().statusCode(409).body("code", equalTo("CONFLICT"));
    }

    @Test
    void noLiquidaReservasAunNoConfirmadas() {
        Long establecimiento = crearEstablecimiento();
        Long cancha = crearCancha(establecimiento);
        Instant inicio = base.plus(2, ChronoUnit.DAYS);
        Long idReserva = idDe(reservar(cancha, inicio, inicio.plus(1, ChronoUnit.HOURS)));
        pagar(idReserva);
        // Sin confirmar (sigue PENDIENTE), no se liquida aunque este pagada.

        generar(hoy.toString(), hoy.plusDays(30).toString())
                .then().statusCode(409).body("code", equalTo("CONFLICT"));
    }

    @Test
    void excluyeReservasCanceladas() {
        // Inicio a 3 h: se puede cancelar (minimo 2 h) pero fuera de la ventana gratuita (24 h),
        // por lo que el pago queda APROBADO y la reserva CANCELADA: no debe liquidarse.
        Long establecimiento = crearEstablecimiento();
        Long cancha = crearCancha(establecimiento);
        Instant inicio = Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES)
                .plus(ThreadLocalRandom.current().nextInt(1, 40), ChronoUnit.MINUTES);
        Long idReserva = idDe(reservar(cancha, inicio, inicio.plus(1, ChronoUnit.HOURS)));
        pagar(idReserva);
        given().auth().oauth2(token).when().post("/api/v1/reservas/" + idReserva + "/cancelar")
                .then().statusCode(200).body("estado", equalTo("CANCELADA"));
        assertEquals(1, pagosAprobadosDe(idReserva), "El pago fuera de la ventana queda APROBADO.");

        generar(hoy.toString(), hoy.plusDays(30).toString())
                .then().statusCode(409).body("code", equalTo("CONFLICT"));
    }

    // ------------------------------------------------------------ autorizacion

    @Test
    void otroUsuarioNoVeLaLiquidacion() {
        reservaPagadaYConfirmada(2);
        Long idLiquidacion = ((Number) generar(hoy.toString(), hoy.plusDays(30).toString())
                .then().statusCode(201).extract().path("id")).longValue();

        String otro = registrarEIniciarSesion("ajeno", "Ajeno", "Curioso", "900555666");

        given().auth().oauth2(otro).when().get("/api/v1/liquidaciones/" + idLiquidacion)
                .then().statusCode(403).body("code", equalTo("FORBIDDEN"));
        given().auth().oauth2(otro).when().get("/api/v1/liquidaciones")
                .then().statusCode(200).body("$", hasSize(0));
    }

    @Test
    void generarSinPropietarioDevuelve409() {
        String sinPerfil = registrarEIniciarSesion("sinexp", "Sin", "Establecimiento", "900777888");
        given().auth().oauth2(sinPerfil).contentType(ContentType.JSON)
                .body(Map.of("periodoInicio", hoy.toString(), "periodoFin", hoy.plusDays(30).toString()))
                .when().post("/api/v1/liquidaciones")
                .then().statusCode(409).body("code", equalTo("CONFLICT"));
    }

    @Test
    void sinTokenDevuelve401() {
        given().when().get("/api/v1/liquidaciones").then().statusCode(401);
        given().contentType(ContentType.JSON)
                .body(Map.of("periodoInicio", hoy.toString(), "periodoFin", hoy.plusDays(30).toString()))
                .when().post("/api/v1/liquidaciones").then().statusCode(401);
    }

    @Test
    void liquidacionInexistenteDevuelve404() {
        given().auth().oauth2(token).when().get("/api/v1/liquidaciones/999999999")
                .then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }

    // -------------------------------------------------------------- validacion

    @Test
    void periodoInvalidoDevuelve400() {
        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("periodoInicio", hoy.plusDays(5).toString(), "periodoFin", hoy.toString()))
                .when().post("/api/v1/liquidaciones")
                .then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));

        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("periodoInicio", "2026-13-40", "periodoFin", hoy.toString()))
                .when().post("/api/v1/liquidaciones")
                .then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));

        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("periodoFin", hoy.toString()))
                .when().post("/api/v1/liquidaciones")
                .then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
    }

    // -------------------------------------------------------------- utilidades

    private Response generar(String periodoInicio, String periodoFin) {
        return given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("periodoInicio", periodoInicio, "periodoFin", periodoFin))
                .when().post("/api/v1/liquidaciones")
                .then().extract().response();
    }

    /** Reserva pagada y confirmada por el propietario (el mismo usuario titular). */
    private Long reservaPagadaYConfirmada(int dias) {
        Long establecimiento = crearEstablecimiento();
        Long cancha = crearCancha(establecimiento);
        Instant inicio = base.plus(dias, ChronoUnit.DAYS);
        Long idReserva = idDe(reservar(cancha, inicio, inicio.plus(1, ChronoUnit.HOURS)));
        pagar(idReserva);
        given().auth().oauth2(token).when().post("/api/v1/reservas/" + idReserva + "/confirmar")
                .then().statusCode(200).body("estado", equalTo("CONFIRMADA"));
        return idReserva;
    }

    private Response reservar(Long cancha, Instant inicio, Instant fin) {
        return given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("canchaId", cancha, "inicio", inicio.toString(), "fin", fin.toString()))
                .when().post("/api/v1/reservas")
                .then().statusCode(201).extract().response();
    }

    private void pagar(Long idReserva) {
        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("medioPago", "TARJETA"))
                .when().post("/api/v1/reservas/" + idReserva + "/pago")
                .then().statusCode(201).body("estado", equalTo("APROBADO"));
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

    private int detallesDe(Long idReserva) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM detalle_liquidacion WHERE id_reserva = :id")
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
