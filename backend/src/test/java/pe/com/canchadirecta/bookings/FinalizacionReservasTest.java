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
import pe.com.canchadirecta.bookings.application.port.in.FinalizarReservasUseCase;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cierre automatico de reservas confirmadas ya concluidas (L-15).
 *
 * <p>Reproduce el job de finalizacion: una reserva `CONFIRMADA` cuyo bloque ya termino pasa
 * a `FINALIZADA` sin tocar el importe. El job tiene el temporizador desactivado en el perfil
 * `test`, asi que las pruebas invocan el caso de uso directamente (deterministas). El paso
 * del tiempo se simula moviendo `inicio`/`fin` al pasado por SQL, contra el reloj de la base
 * en UTC (las franjas se guardan como literales UTC, ADR-0014).</p>
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinalizacionReservasTest {

    /** Zona de operacion configurada en `application.properties` (`cd.zona-horaria`). */
    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @TestHTTPResource("/")
    URI raiz;

    @Inject
    EntityManager em;

    @Inject
    FinalizarReservasUseCase finalizacion;

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
        tokenDueno = registrarEIniciarSesion("dueno.fin", "Pablo", "Rojas", "955111220");
        tokenTitular = registrarEIniciarSesion("titular.fin", "Ana", "Leon", "944222220");

        Long idDistrito = QuarkusTransaction.requiringNew().call(() ->
                ((Number) em.createNativeQuery("SELECT MIN(id_distrito) FROM distrito").getSingleResult()).longValue());

        idEstablecimiento = ((Number) given().auth().oauth2(tokenDueno).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito,
                        "nombre", "Complejo Cierre " + System.nanoTime(),
                        "direccion", "Av. Cierre 200",
                        "referencia", "Al costado del estadio"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201).extract().path("id")).longValue();

        idCancha = ((Number) given().auth().oauth2(tokenDueno).contentType(ContentType.JSON)
                .body(Map.of(
                        "nombre", "Cancha cierre " + System.nanoTime(),
                        "descripcion", "Futbol 8 con grass sintetico",
                        "capacidad", 16,
                        "precioBase", new BigDecimal("90.00")))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/canchas")
                .then().statusCode(201).extract().path("id")).longValue();

        fecha = LocalDate.ofInstant(Instant.now().plus(3, ChronoUnit.DAYS), ZONA);
    }

    @Test
    void finalizaUnaReservaConfirmadaCuyoFinYaPaso() {
        Instant inicio = fecha.atTime(9, 0).atZone(ZONA).toInstant();
        Long idReserva = crearYConfirmarReserva(inicio);

        retrocederFranja(idReserva, 3, 2);

        int finalizadas = finalizacion.finalizarConcluidas(200);

        assertTrue(finalizadas >= 1, "Debe finalizar al menos la reserva concluida de este escenario.");
        assertEquals("FINALIZADA", estadoDeReserva(idReserva));
        assertTrue(ultimaObservacion(idReserva).contains("Finalizada"),
                "El cierre debe quedar registrado en el historial.");
        assertEquals(3, entradasDeHistorial(idReserva),
                "Creacion, confirmacion y finalizacion deben quedar registradas.");
    }

    @Test
    void noFinalizaUnaReservaConfirmadaVigente() {
        Instant inicio = fecha.atTime(10, 0).atZone(ZONA).toInstant();
        Long idReserva = crearYConfirmarReserva(inicio);

        finalizacion.finalizarConcluidas(200);

        assertEquals("CONFIRMADA", estadoDeReserva(idReserva),
                "Una reserva cuya franja aun no termina sigue confirmada.");
    }

    @Test
    void noFinalizaUnaReservaPendienteAunqueSuFinYaPaso() {
        Instant inicio = fecha.atTime(11, 0).atZone(ZONA).toInstant();
        Long idReserva = crearReserva(inicio); // queda PENDIENTE (sin confirmar)

        retrocederFranja(idReserva, 3, 2);

        finalizacion.finalizarConcluidas(200);

        assertEquals("PENDIENTE", estadoDeReserva(idReserva),
                "El cierre solo aplica a reservas confirmadas; una pendiente la expira el hold.");
    }

    @Test
    void noFinalizaLaFranjaDeOtraReservaMasReciente() {
        // Dos confirmadas: solo la que ya concluyo debe cerrarse.
        Instant concluida = fecha.atTime(12, 0).atZone(ZONA).toInstant();
        Instant vigente = fecha.atTime(13, 0).atZone(ZONA).toInstant();
        Long idConcluida = crearYConfirmarReserva(concluida);
        Long idVigente = crearYConfirmarReserva(vigente);

        retrocederFranja(idConcluida, 3, 2);

        finalizacion.finalizarConcluidas(200);

        assertEquals("FINALIZADA", estadoDeReserva(idConcluida));
        assertEquals("CONFIRMADA", estadoDeReserva(idVigente),
                "El lote no debe arrastrar reservas cuya franja sigue vigente.");
    }

    // ---------------------------------------------------------------- utilidades

    private Long crearReserva(Instant inicio) {
        return ((Number) given().auth().oauth2(tokenTitular).contentType(ContentType.JSON)
                .body(Map.of("canchaId", idCancha, "inicio", inicio.toString(),
                        "fin", inicio.plus(1, ChronoUnit.HOURS).toString()))
                .when().post("/api/v1/reservas")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long crearYConfirmarReserva(Instant inicio) {
        Long idReserva = crearReserva(inicio);
        given().auth().oauth2(tokenDueno)
                .when().post("/api/v1/reservas/" + idReserva + "/confirmar")
                .then().statusCode(200).body("estado", org.hamcrest.Matchers.equalTo("CONFIRMADA"));
        return idReserva;
    }

    /** Simula la conclusion del bloque: mueve `inicio`/`fin` `horasInicio`/`horasFin` atras en UTC. */
    private void retrocederFranja(Long idReserva, long horasInicio, long horasFin) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "UPDATE reserva SET inicio = TIMESTAMPADD(HOUR, -:hi, UTC_TIMESTAMP(6)), "
                                + "fin = TIMESTAMPADD(HOUR, -:hf, UTC_TIMESTAMP(6)) "
                                + "WHERE id_reserva = :id")
                .setParameter("hi", horasInicio)
                .setParameter("hf", horasFin)
                .setParameter("id", idReserva)
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
