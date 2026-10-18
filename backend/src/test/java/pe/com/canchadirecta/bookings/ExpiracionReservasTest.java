package pe.com.canchadirecta.bookings;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import pe.com.canchadirecta.bookings.application.port.in.ExpirarReservasUseCase;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Expiracion del hold de pago (F05, ADR-0022).
 *
 * <p>Reproduce el job de expiracion: una solicitud `PENDIENTE` sin pago `APROBADO` que
 * supera el hold de su establecimiento pasa a `EXPIRADA` y libera la franja. El job tiene
 * el temporizador desactivado en el perfil `test`, asi que las pruebas invocan el caso de
 * uso directamente (deterministas). El paso del tiempo se simula retrocediendo
 * `reserva.fecha_creacion` por SQL.</p>
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpiracionReservasTest {

    /** Zona de operacion configurada en `application.properties` (`cd.zona-horaria`). */
    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    /** Hold por defecto del proyecto (`cd.reservas.hold-pago-min`). */
    private static final int HOLD_DEFECTO_MIN = 10;

    @TestHTTPResource("/")
    URI raiz;

    @Inject
    EntityManager em;

    @Inject
    ExpirarReservasUseCase expiracion;

    private String tokenDueno;
    private String tokenTitular;
    private Long idEstablecimiento;
    private Long idCancha;
    private LocalDate fecha;
    private final AtomicBoolean preparado = new AtomicBoolean(false);

    @BeforeEach
    void preparar() {
        if (!preparado.compareAndSet(false, true)) {
            return;
        }
        tokenDueno = registrarEIniciarSesion("dueno.exp", "Rosa", "Quispe", "977555110");
        tokenTitular = registrarEIniciarSesion("titular.exp", "Mario", "Rios", "966444110");

        Long idDistrito = QuarkusTransaction.requiringNew().call(() ->
                ((Number) em.createNativeQuery("SELECT MIN(id_distrito) FROM distrito").getSingleResult()).longValue());

        idEstablecimiento = ((Number) given().auth().oauth2(tokenDueno).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito,
                        "nombre", "Complejo Hold " + System.nanoTime(),
                        "direccion", "Av. Hold 100",
                        "referencia", "Frente al parque"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201).extract().path("id")).longValue();

        idCancha = ((Number) given().auth().oauth2(tokenDueno).contentType(ContentType.JSON)
                .body(Map.of(
                        "nombre", "Cancha hold " + System.nanoTime(),
                        "descripcion", "Futbol 7 con grass sintetico",
                        "capacidad", 14,
                        "precioBase", new BigDecimal("80.00")))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/canchas")
                .then().statusCode(201).extract().path("id")).longValue();

        // Estado inicial conocido: hold por defecto (por si una ejecucion anterior dejo una
        // fila de configuracion con otro valor).
        configurarHold(HOLD_DEFECTO_MIN);
        fecha = LocalDate.ofInstant(Instant.now().plus(2, ChronoUnit.DAYS), ZONA);
    }

    @Test
    void expiraUnaReservaPendienteSinPagoCuyoHoldVencio() {
        Instant inicio = fecha.atTime(12, 0).atZone(ZONA).toInstant();
        Long idReserva = crearReserva(inicio);

        // 10 horas atras: muy por encima de cualquier hold (por defecto 10 minutos).
        retrocederCreacion(idReserva, 600);

        int expiradas = expiracion.expirarVencidas(200);

        assertTrue(expiradas >= 1, "Debe expirar al menos la reserva vencida de este escenario.");
        assertEquals("EXPIRADA", estadoDeReserva(idReserva));
        assertTrue(iniciosDeFranjas().contains(inicio.toString()),
                "Al expirar, la franja debe volver a la disponibilidad.");
        assertTrue(ultimaObservacion(idReserva).contains("Expirada"),
                "La expiracion debe quedar registrada en el historial.");
        assertEquals(2, entradasDeHistorial(idReserva), "Creacion y expiracion deben quedar registradas.");
    }

    @Test
    void noExpiraUnaReservaDentroDelHold() {
        Instant inicio = fecha.atTime(13, 0).atZone(ZONA).toInstant();
        Long idReserva = crearReserva(inicio);

        expiracion.expirarVencidas(200);

        assertEquals("PENDIENTE", estadoDeReserva(idReserva),
                "Una solicitud reciente sigue dentro del hold de pago.");
    }

    @Test
    void noExpiraUnaReservaConPagoAprobadoAunqueVencioElHold() {
        Instant inicio = fecha.atTime(14, 0).atZone(ZONA).toInstant();
        Long idReserva = crearReserva(inicio);

        given().auth().oauth2(tokenTitular).contentType(ContentType.JSON)
                .body(Map.of("medioPago", "TARJETA"))
                .when().post("/api/v1/reservas/" + idReserva + "/pago")
                .then().statusCode(201).body("estado", equalTo("APROBADO"));

        retrocederCreacion(idReserva, 600);

        expiracion.expirarVencidas(200);

        assertEquals("PENDIENTE", estadoDeReserva(idReserva),
                "Una reserva con pago aprobado no debe expirar.");
    }

    @Test
    void respetaElHoldConfiguradoPorElEstablecimiento() {
        configurarHold(180);
        try {
            Instant inicio = fecha.atTime(15, 0).atZone(ZONA).toInstant();
            Long idReserva = crearReserva(inicio);
            retrocederCreacion(idReserva, 60); // 60 < 180: aun dentro del hold configurado.

            expiracion.expirarVencidas(200);

            assertEquals("PENDIENTE", estadoDeReserva(idReserva),
                    "Con hold de 180 minutos, 60 minutos no vencen.");
        } finally {
            configurarHold(HOLD_DEFECTO_MIN);
        }
    }

    // ---------------------------------------------------------------- utilidades

    private Long crearReserva(Instant inicio) {
        return ((Number) given().auth().oauth2(tokenTitular).contentType(ContentType.JSON)
                .body(Map.of("canchaId", idCancha, "inicio", inicio.toString(),
                        "fin", inicio.plus(1, ChronoUnit.HOURS).toString()))
                .when().post("/api/v1/reservas")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    /** Simula el paso del tiempo moviendo `fecha_creacion` `minutos` atras en el reloj de la base. */
    private void retrocederCreacion(Long idReserva, long minutos) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "UPDATE reserva SET fecha_creacion = TIMESTAMPADD(MINUTE, -:minutos, NOW(6)) "
                                + "WHERE id_reserva = :id")
                .setParameter("minutos", minutos)
                .setParameter("id", idReserva)
                .executeUpdate());
    }

    private void configurarHold(int minutos) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "INSERT INTO config_establecimiento (id_establecimiento, hold_pago_min) "
                                + "VALUES (:id, :hold) "
                                + "ON DUPLICATE KEY UPDATE hold_pago_min = :hold")
                .setParameter("id", idEstablecimiento.intValue())
                .setParameter("hold", minutos)
                .executeUpdate());
    }

    /** Estado de la reserva segun el listado del titular. */
    private String estadoDeReserva(Long idReserva) {
        List<Map<String, Object>> reservas = given().auth().oauth2(tokenTitular)
                .when().get("/api/v1/reservas/mias")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        return reservas.stream()
                .filter(reserva -> ((Number) reserva.get("id")).longValue() == idReserva)
                .map(reserva -> String.valueOf(reserva.get("estado")))
                .findFirst().orElse(null);
    }

    private List<String> iniciosDeFranjas() {
        List<Map<String, Object>> franjas = given()
                .queryParam("fecha", fecha.toString())
                .queryParam("duracionMinutos", 60)
                .when().get("/api/v1/canchas/" + idCancha + "/disponibilidad")
                .then().statusCode(200)
                .extract().jsonPath().getList("franjas");
        return franjas.stream().map(franja -> String.valueOf(franja.get("inicio"))).toList();
    }

    private long entradasDeHistorial(Long idReserva) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM historial_reserva WHERE id_reserva = :id")
                .setParameter("id", idReserva).getSingleResult()).longValue());
    }

    private String ultimaObservacion(Long idReserva) {
        return String.valueOf(QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                        "SELECT observacion FROM historial_reserva WHERE id_reserva = :id "
                                + "ORDER BY id_historial DESC LIMIT 1")
                .setParameter("id", idReserva).getSingleResult()));
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

    /** Documento de identidad distinto en cada ejecucion (hay restriccion de unicidad). */
    private static String documentoUnico() {
        return String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
    }
}
