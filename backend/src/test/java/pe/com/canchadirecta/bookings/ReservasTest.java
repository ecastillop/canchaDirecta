package pe.com.canchadirecta.bookings;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integracion del modulo de reservas.
 *
 * <p>El test central reproduce la regla RN-01: lanzar peticiones HTTP simultaneas a la
 * misma franja de la misma cancha y comprobar que se crea exactamente una reserva
 * (el resto recibe 409 SLOT_UNAVAILABLE).</p>
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservasTest {

    private static final int PETICIONES_SIMULTANEAS = 30;

    /** Zona de operacion configurada en `application.properties` (`cd.zona-horaria`). */
    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    /** Ventana por defecto cuando la cancha no tiene `horario_cancha` para ese dia. */
    private static final int HORA_APERTURA_DEFECTO = 6;

    /** Mismo formato que usa el adaptador para leer/escribir los `DATETIME` en UTC. */
    private static final DateTimeFormatter LITERAL_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    @TestHTTPResource("/")
    URI raiz;

    @Inject
    EntityManager em;

    private String token;
    private String tokenReservante;
    private Long idEstablecimiento;
    private Long idCancha;
    private Instant base;
    private final AtomicBoolean preparado = new AtomicBoolean(false);

    /**
     * La preparacion se hace en `@BeforeEach` (no en `@BeforeAll`) porque el puerto
     * aleatorio del servidor de pruebas se publica a RestAssured antes de cada test;
     * una `@BeforeAll` enviaria peticiones al puerto por defecto.
     */
    @BeforeEach
    void preparar() {
        if (!preparado.compareAndSet(false, true)) {
            return;
        }
        token = registrarEIniciarSesion("dueno", "Rosa", "Quispe", "977555444");

        // Usuario aparte solo para comprobar el listado de reservas propias.
        tokenReservante = registrarEIniciarSesion("jugador", "Mario", "Rios", "966444333");

        Long idDistrito = QuarkusTransaction.requiringNew().call(() ->
                ((Number) em.createNativeQuery("SELECT MIN(id_distrito) FROM distrito").getSingleResult()).longValue());

        idEstablecimiento = ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito,
                        "nombre", "Complejo Deportivo " + System.nanoTime(),
                        "direccion", "Av. Los Deportes 123",
                        "referencia", "Frente al parque"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201)
                .body("id", notNullValue())
                .extract().path("id")).longValue();

        idCancha = crearCancha("Cancha sintetica 1");

        // Franja base: pasada manana a las X:00, dentro de la ventana de anticipacion.
        // El desplazamiento aleatorio en minutos evita que dos ejecuciones seguidas
        // reserven la misma franja (la base de datos persiste entre ejecuciones).
        base = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS)
                .plus(ThreadLocalRandom.current().nextInt(3, 12) * 5L, ChronoUnit.MINUTES);
    }

    @Test
    void soloUnaReservaSeCreaConPeticionesSimultaneas() throws Exception {
        Instant inicio = base.plus(4, ChronoUnit.HOURS);
        Instant fin = inicio.plus(1, ChronoUnit.HOURS);

        List<Integer> codigos = dispararSimultaneas(inicio, fin, PETICIONES_SIMULTANEAS);

        long creadas = codigos.stream().filter(c -> c == 201).count();
        long conflicto = codigos.stream().filter(c -> c == 409).count();

        assertEquals(1, creadas, "Debe crearse exactamente una reserva. Codigos: " + codigos);
        assertEquals(PETICIONES_SIMULTANEAS - 1, conflicto, "El resto debe recibir 409. Codigos: " + codigos);
        assertEquals(1, contarReservasVigentes(idCancha, inicio, fin),
                "La base de datos debe contener exactamente una reserva vigente en la franja.");
    }

    @Test
    void reservaSecuencialEnLaMismaFranjaDevuelve409() {
        Instant inicio = base.plus(6, ChronoUnit.HOURS);
        Instant fin = inicio.plus(1, ChronoUnit.HOURS);

        crear(inicio, fin).then().statusCode(201);
        crear(inicio, fin).then()
                .statusCode(409)
                .body("code", equalTo("SLOT_UNAVAILABLE"));
    }

    @Test
    void franjasContiguasNoSeSolapan() {
        Instant inicio = base.plus(8, ChronoUnit.HOURS);

        crear(inicio, inicio.plus(1, ChronoUnit.HOURS)).then().statusCode(201);
        // Empieza justo cuando termina la anterior: no hay solapamiento.
        crear(inicio.plus(1, ChronoUnit.HOURS), inicio.plus(2, ChronoUnit.HOURS)).then().statusCode(201);
        // Se solapa en 30 minutos con la segunda: rechazada.
        crear(inicio.plus(1, ChronoUnit.HOURS), inicio.plus(90, ChronoUnit.MINUTES)).then()
                .statusCode(409)
                .body("code", equalTo("SLOT_UNAVAILABLE"));
    }

    @Test
    void franjaInvalidaDevuelve400() {
        Instant inicio = base.plus(10, ChronoUnit.HOURS);

        // fin <= inicio
        crear(inicio, inicio).then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        // menos de 30 minutos
        crear(inicio, inicio.plus(10, ChronoUnit.MINUTES)).then().statusCode(400);
        // mas de 180 minutos
        crear(inicio, inicio.plus(4, ChronoUnit.HOURS)).then().statusCode(400);
        // en el pasado
        crear(Instant.now().minus(2, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.HOURS))
                .then().statusCode(400);
    }

    @Test
    void canchaInexistenteDevuelve404() {
        Instant inicio = base.plus(12, ChronoUnit.HOURS);
        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("canchaId", 999_999_999, "inicio", inicio.toString(),
                        "fin", inicio.plus(1, ChronoUnit.HOURS).toString()))
                .when().post("/api/v1/reservas")
                .then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void sinTokenDevuelve401() {
        Instant inicio = base.plus(13, ChronoUnit.HOURS);
        given().contentType(ContentType.JSON)
                .body(Map.of("canchaId", idCancha, "inicio", inicio.toString(),
                        "fin", inicio.plus(1, ChronoUnit.HOURS).toString()))
                .when().post("/api/v1/reservas")
                .then().statusCode(401);
    }

    @Test
    void misReservasConservaLaFranjaSolicitada() {
        // Usuario propio del escenario: `mias` solo devuelve las reservas del titular, y
        // asi el listado no depende de las reservas que creen otros tests de la clase.
        String tokenCliente = registrarEIniciarSesion("cliente", "Ana", "Soto", "955111222");
        Instant inicio = base.plus(14, ChronoUnit.HOURS);
        Instant fin = inicio.plus(2, ChronoUnit.HOURS);

        given().auth().oauth2(tokenCliente).contentType(ContentType.JSON)
                .body(Map.of("canchaId", idCancha, "inicio", inicio.toString(), "fin", fin.toString()))
                .when().post("/api/v1/reservas")
                .then().statusCode(201);

        given().auth().oauth2(tokenCliente)
                .when().get("/api/v1/reservas/mias")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].canchaId", equalTo(idCancha.intValue()))
                .body("[0].inicio", equalTo(inicio.toString()))
                .body("[0].fin", equalTo(fin.toString()))
                .body("[0].estado", equalTo("PENDIENTE"))
                .body("[0].montoCancha", equalTo(80.00f));
    }

    @Test
    void laDisponibilidadDistingueReservaYBloqueo() {
        Long cancha = crearCancha("Cancha con motivos");
        LocalDate fecha = LocalDate.ofInstant(base, ZONA);
        Instant reservado = fecha.atTime(9, 0).atZone(ZONA).toInstant();
        Instant bloqueado = fecha.atTime(12, 0).atZone(ZONA).toInstant();
        Instant libre = fecha.atTime(15, 0).atZone(ZONA).toInstant();

        crearEnComo(tokenReservante, cancha, reservado, reservado.plus(1, ChronoUnit.HOURS))
                .then().statusCode(201);
        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "inicio", bloqueado.toString(),
                        "fin", bloqueado.plus(1, ChronoUnit.HOURS).toString(),
                        "motivo", "Mantenimiento de la cancha"))
                .when().post("/api/v1/canchas/" + cancha + "/bloqueos")
                .then().statusCode(201);

        Map<String, Object> dia = disponibilidadCruda(cancha, fecha);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ocupadas = (List<Map<String, Object>>) dia.get("ocupadas");
        assertEquals(2, ocupadas.size(), "Debe informar la reserva y el bloqueo: " + ocupadas);
        assertEquals("RESERVADA", estadoOcupada(ocupadas, reservado), "La reserva ocupa cupo.");
        assertEquals("BLOQUEADA", estadoOcupada(ocupadas, bloqueado), "El bloqueo ocupa cupo.");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> franjas = (List<Map<String, Object>>) dia.get("franjas");
        List<String> inicios = inicios(franjas);
        assertFalse(inicios.contains(reservado.toString()), "La reserva no debe ofrecerse.");
        assertFalse(inicios.contains(bloqueado.toString()), "El bloqueo no debe ofrecerse.");
        assertTrue(inicios.contains(libre.toString()), "La hora libre debe seguir disponible.");

        // La disponibilidad publica informa el motivo, nunca la nota interna del bloqueo (ADR-0047).
        assertFalse(String.valueOf(dia.get("ocupadas")).contains("Mantenimiento"),
                "No debe filtrarse la nota del bloqueo.");
    }

    // ------------------------------------------------------- disponibilidad

    @Test
    void disponibilidadDevuelveLasFranjasLibresDeLaVentanaPorDefecto() {
        Long cancha = crearCancha("Cancha disponibilidad");
        LocalDate fecha = LocalDate.ofInstant(base, ZONA);
        Instant mediodia = fecha.atTime(12, 0).atZone(ZONA).toInstant();

        List<Map<String, Object>> franjas = franjasCrudas(cancha, fecha, 60);

        // La ventana por defecto es 06:00-23:00 local: 17 bloques de 60 minutos.
        assertEquals(17, franjas.size(), "Debe ofrecer la ventana por defecto completa: " + franjas);
        assertEquals(fecha.atTime(HORA_APERTURA_DEFECTO, 0).atZone(ZONA).toInstant().toString(),
                franjas.get(0).get("inicio"), "El primer bloque debe empezar en la hora de apertura.");
        assertEquals(0, new BigDecimal(String.valueOf(franjas.get(0).get("precio")))
                        .compareTo(new BigDecimal("80.00")),
                "Sin horario configurado rige el precio base de la cancha.");

        // Reservar el bloque de 12:00 local solo retira ese bloque.
        crearEn(cancha, mediodia, mediodia.plus(1, ChronoUnit.HOURS)).then().statusCode(201);

        List<String> libres = inicios(franjasCrudas(cancha, fecha, 60));
        assertFalse(libres.contains(mediodia.toString()), "El bloque reservado no debe ofrecerse.");
        assertTrue(libres.contains(mediodia.plus(1, ChronoUnit.HOURS).toString()), "El bloque contiguo sigue libre.");
        assertTrue(libres.contains(mediodia.minus(1, ChronoUnit.HOURS).toString()), "El bloque anterior sigue libre.");
    }

    @Test
    void disponibilidadConParametrosInvalidosDevuelve400Y404SiLaCanchaNoExiste() {
        LocalDate objetivo = LocalDate.ofInstant(Instant.now(), ZONA).plusDays(2);
        String ruta = "/api/v1/canchas/" + idCancha + "/disponibilidad";

        // Sin fecha
        given().when().get(ruta).then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        // Formato invalido
        given().queryParam("fecha", "2026-13-45").when().get(ruta).then().statusCode(400);
        // Fecha pasada
        given().queryParam("fecha", LocalDate.ofInstant(Instant.now(), ZONA).minusDays(1).toString())
                .when().get(ruta).then().statusCode(400);
        // Fuera de la ventana de anticipacion (365 dias)
        given().queryParam("fecha", objetivo.plusDays(400).toString())
                .when().get(ruta).then().statusCode(400);
        // Duracion fuera del rango 30-180
        given().queryParam("fecha", objetivo.toString()).queryParam("duracionMinutos", 10)
                .when().get(ruta).then().statusCode(400);

        // Cancha inexistente
        given().queryParam("fecha", objetivo.toString())
                .when().get("/api/v1/canchas/999999999/disponibilidad")
                .then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }

    @Test
    void canchaNoActivaNoOfreceFranjas() {
        Long cancha = crearCancha("Cancha en mantenimiento");
        LocalDate fecha = LocalDate.ofInstant(base, ZONA);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "UPDATE cancha SET estado = 'MANTENIMIENTO' WHERE id_cancha = :id")
                .setParameter("id", cancha.intValue())
                .executeUpdate());

        assertEquals(0, franjasCrudas(cancha, fecha, 60).size(),
                "Una cancha que no esta ACTIVA no debe ofrecer franjas.");
    }

    // --------------------------------------------------------- cancelacion

    @Test
    void cancelarReservaLiberaLaFranjaYSePuedeVolverAReservar() {
        Long cancha = crearCancha("Cancha cancelacion");
        LocalDate fecha = LocalDate.ofInstant(base, ZONA);
        Instant inicio = fecha.atTime(12, 0).atZone(ZONA).toInstant();
        Instant fin = inicio.plus(1, ChronoUnit.HOURS);

        Long idReserva = idDe(crearEn(cancha, inicio, fin).then().statusCode(201).extract().response());
        assertFalse(inicios(franjasCrudas(cancha, fecha, 60)).contains(inicio.toString()));

        cancelar(idReserva, token).then()
                .statusCode(200)
                .body("estado", equalTo("CANCELADA"))
                .body("inicio", equalTo(inicio.toString()));

        // La franja vuelve a ofrecerse y puede reservarse de nuevo.
        assertTrue(inicios(franjasCrudas(cancha, fecha, 60)).contains(inicio.toString()),
                "Al cancelar, la franja debe volver a la disponibilidad.");
        crearEn(cancha, inicio, fin).then().statusCode(201);

        // Segundo intento sobre la reserva ya cancelada: conflicto.
        cancelar(idReserva, token).then().statusCode(409).body("code", equalTo("CONFLICT"));

        // El historial conserva la traza de la creacion y de la cancelacion.
        assertEquals(2, QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM historial_reserva WHERE id_reserva = :id")
                .setParameter("id", idReserva).getSingleResult()).intValue()));
    }

    @Test
    void cancelarUnaReservaAjenaDevuelve403() {
        Long cancha = crearCancha("Cancha reserva ajena");
        LocalDate fecha = LocalDate.ofInstant(base, ZONA);
        Instant inicio = fecha.atTime(15, 0).atZone(ZONA).toInstant();

        Response creada = given().auth().oauth2(tokenReservante).contentType(ContentType.JSON)
                .body(Map.of("canchaId", cancha, "inicio", inicio.toString(),
                        "fin", inicio.plus(1, ChronoUnit.HOURS).toString()))
                .when().post("/api/v1/reservas")
                .then().statusCode(201).extract().response();

        cancelar(idDe(creada), token).then().statusCode(403).body("code", equalTo("FORBIDDEN"));
        // El titular si puede cancelarla.
        cancelar(idDe(creada), tokenReservante).then().statusCode(200).body("estado", equalTo("CANCELADA"));
    }

    @Test
    void cancelarDentroDeLaVentanaMinimaDevuelve409() {
        Long cancha = crearCancha("Cancha ventana de cancelacion");
        Instant inicio = Instant.now().plus(1, ChronoUnit.HOURS);

        Response creada = crearEn(cancha, inicio, inicio.plus(1, ChronoUnit.HOURS))
                .then().statusCode(201).extract().response();

        cancelar(idDe(creada), token).then().statusCode(409).body("code", equalTo("CONFLICT"));
    }

    @Test
    void cancelarSinTokenDevuelve401YReservaInexistenteDevuelve404() {
        given().when().post("/api/v1/reservas/1/cancelar").then().statusCode(401);
        cancelar(999_999_999L, token).then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }

    // ------------------------------------------- reservas recibidas (HU-008-2)

    @Test
    void elPropietarioConfirmaLaReservaRecibidaEnSuCancha() {
        Long cancha = crearCancha("Cancha con confirmacion");
        LocalDate fecha = LocalDate.ofInstant(base, ZONA);
        Instant inicio = fecha.atTime(10, 0).atZone(ZONA).toInstant();
        Long idReserva = idDe(crearEnComo(tokenReservante, cancha, inicio, inicio.plus(1, ChronoUnit.HOURS))
                .then().statusCode(201).extract().response());

        assertEquals("PENDIENTE", estadoDe(recibidas(token, "PENDIENTE"), idReserva),
                "La solicitud recien creada debe aparecer en la bandeja del propietario.");

        confirmar(idReserva, token).then().statusCode(200)
                .body("estado", equalTo("CONFIRMADA"))
                .body("inicio", equalTo(inicio.toString()));

        assertNull(estadoDe(recibidas(token, "PENDIENTE"), idReserva), "Ya no debe figurar como pendiente.");
        assertEquals("CONFIRMADA", estadoDe(recibidas(token, "CONFIRMADA"), idReserva));
        assertEquals("CONFIRMADA", estadoDe(recibidas(token, null), idReserva), "Sin filtro se listan todos los estados.");
        assertEquals("CONFIRMADA", estadoDe(
                given().auth().oauth2(tokenReservante).when().get("/api/v1/reservas/mias")
                        .then().statusCode(200).extract().jsonPath().getList("$"), idReserva),
                "El titular debe ver la decision del propietario.");

        assertEquals(2, entradasDeHistorial(idReserva), "Debe quedar traza de creacion y de confirmacion.");
    }

    @Test
    void atenderUnaReservaNoPendienteDevuelve409() {
        Long cancha = crearCancha("Cancha atencion repetida");
        LocalDate fecha = LocalDate.ofInstant(base, ZONA);
        Instant inicio = fecha.atTime(13, 0).atZone(ZONA).toInstant();
        Long idReserva = idDe(crearEnComo(tokenReservante, cancha, inicio, inicio.plus(1, ChronoUnit.HOURS))
                .then().statusCode(201).extract().response());

        confirmar(idReserva, token).then().statusCode(200);
        confirmar(idReserva, token).then().statusCode(409).body("code", equalTo("CONFLICT"));
        rechazar(idReserva, token, "Llego tarde").then().statusCode(409).body("code", equalTo("CONFLICT"));
        assertEquals(2, entradasDeHistorial(idReserva), "Un intento rechazado no debe dejar traza.");
    }

    @Test
    void elPropietarioRechazaLaReservaYLaFranjaVuelveAOfrecerse() {
        Long cancha = crearCancha("Cancha con rechazo");
        LocalDate fecha = LocalDate.ofInstant(base, ZONA);
        Instant inicio = fecha.atTime(14, 0).atZone(ZONA).toInstant();
        Long idReserva = idDe(crearEnComo(tokenReservante, cancha, inicio, inicio.plus(1, ChronoUnit.HOURS))
                .then().statusCode(201).extract().response());
        assertFalse(inicios(franjasCrudas(cancha, fecha, 60)).contains(inicio.toString()));

        rechazar(idReserva, token, "Mantenimiento imprevisto de la cancha").then()
                .statusCode(200)
                .body("estado", equalTo("RECHAZADA"))
                .body("inicio", equalTo(inicio.toString()));

        assertEquals("RECHAZADA", estadoDe(recibidas(token, "RECHAZADA"), idReserva));
        assertTrue(inicios(franjasCrudas(cancha, fecha, 60)).contains(inicio.toString()),
                "Al rechazar, la franja debe volver a la disponibilidad.");
        crearEnComo(tokenReservante, cancha, inicio, inicio.plus(1, ChronoUnit.HOURS)).then().statusCode(201);

        assertTrue(ultimaObservacion(idReserva).contains("Mantenimiento imprevisto de la cancha"),
                "El motivo debe quedar registrado en la traza.");
    }

    @Test
    void atenderReservasAjenasDevuelve403() {
        Long cancha = crearCancha("Cancha decision ajena");
        LocalDate fecha = LocalDate.ofInstant(base, ZONA);
        Instant inicio = fecha.atTime(16, 0).atZone(ZONA).toInstant();
        Long idReserva = idDe(crearEnComo(tokenReservante, cancha, inicio, inicio.plus(1, ChronoUnit.HOURS))
                .then().statusCode(201).extract().response());

        // El titular de la reserva no decide sobre ella: es cliente, no propietario.
        confirmar(idReserva, tokenReservante).then().statusCode(403).body("code", equalTo("FORBIDDEN"));
        rechazar(idReserva, tokenReservante, null).then().statusCode(403).body("code", equalTo("FORBIDDEN"));

        // Otro propietario, con establecimiento y cancha propios, tampoco alcanza reservas ajenas.
        String tokenOtro = registrarEIniciarSesion("otrodueno", "Julia", "Paredes", "955333222");
        crearEstablecimientoPropio(tokenOtro);
        confirmar(idReserva, tokenOtro).then().statusCode(403).body("code", equalTo("FORBIDDEN"));
        rechazar(idReserva, tokenOtro, "No es mi cancha").then().statusCode(403).body("code", equalTo("FORBIDDEN"));
        assertEquals(0, recibidas(tokenOtro, null).size(), "Su bandeja no debe mostrar reservas de otras canchas.");

        assertEquals("PENDIENTE", estadoDe(recibidas(token, null), idReserva), "La reserva debe seguir pendiente.");
    }

    @Test
    void bandejaDelPropietarioFiltraPorCanchaYRango() {
        // Dos canchas del mismo propietario y una reserva en cada una, en dias distintos.
        Long canchaA = crearCancha("Cancha bandeja A");
        Long canchaB = crearCancha("Cancha bandeja B");
        LocalDate fecha = LocalDate.ofInstant(base, ZONA);
        Instant inicioA = fecha.atTime(9, 0).atZone(ZONA).toInstant();
        Instant inicioB = fecha.plusDays(1).atTime(9, 0).atZone(ZONA).toInstant();
        Long reservaA = idDe(crearEnComo(tokenReservante, canchaA, inicioA, inicioA.plus(1, ChronoUnit.HOURS))
                .then().statusCode(201).extract().response());
        Long reservaB = idDe(crearEnComo(tokenReservante, canchaB, inicioB, inicioB.plus(1, ChronoUnit.HOURS))
                .then().statusCode(201).extract().response());

        // Sin filtros siguen apareciendo las dos.
        assertEquals("PENDIENTE", estadoDe(recibidas(token, null), reservaA));
        assertEquals("PENDIENTE", estadoDe(recibidas(token, null), reservaB));

        // Filtro por cancha: solo la de esa cancha.
        List<Map<String, Object>> soloA = recibidas(token, null, canchaA, null, null);
        assertEquals("PENDIENTE", estadoDe(soloA, reservaA));
        assertNull(estadoDe(soloA, reservaB), "No debe mezclar canchas.");

        // Filtro por rango: la ventana del dia de la reserva A solo la contiene a ella.
        List<Map<String, Object>> delDia = recibidas(token, null, null,
                inicioA.minus(4, ChronoUnit.HOURS), inicioA.plus(2, ChronoUnit.HOURS));
        assertEquals("PENDIENTE", estadoDe(delDia, reservaA));
        assertNull(estadoDe(delDia, reservaB), "La reserva de otro dia no debe aparecer.");

        // Rango sin solape con ninguna reserva.
        assertEquals(0, recibidas(token, null, null,
                inicioA.plus(20, ChronoUnit.DAYS), inicioA.plus(21, ChronoUnit.DAYS)).size(),
                "Un rango futuro sin reservas debe venir vacio.");
    }

    @Test
    void laBandejaDelPropietarioMuestraAlTitularYSuUltimoPago() {
        Long cancha = crearCancha("Cancha con titular visible");
        LocalDate fecha = LocalDate.ofInstant(base, ZONA);
        Instant inicio = fecha.atTime(16, 0).atZone(ZONA).toInstant();
        Long idReserva = idDe(crearEnComo(tokenReservante, cancha, inicio, inicio.plus(1, ChronoUnit.HOURS))
                .then().statusCode(201).extract().response());

        // El propietario sabe a quien atiende antes de decidir sobre la solicitud (ADR-0044).
        Map<String, Object> solicitada = recibida(recibidas(token, null), idReserva);
        assertEquals("Mario Rios", solicitada.get("cliente"));
        assertEquals("966444333", solicitada.get("telefono"));
        assertNull(solicitada.get("pagoEstado"), "Sin pagos registrados todavia.");

        // Cuando el titular paga, el propietario ve el estado de ese ultimo pago.
        given().auth().oauth2(tokenReservante).contentType(ContentType.JSON)
                .body(Map.of("medioPago", "TARJETA"))
                .when().post("/api/v1/reservas/" + idReserva + "/pago")
                .then().statusCode(201).body("estado", equalTo("APROBADO"));
        assertEquals("APROBADO", recibida(recibidas(token, null), idReserva).get("pagoEstado"));
    }

    @Test
    void atenderReservaConDatosInvalidosFallaCon400o404() {
        given().when().get("/api/v1/reservas/recibidas").then().statusCode(401);
        given().when().post("/api/v1/reservas/1/confirmar").then().statusCode(401);

        confirmar(999_999_999L, token).then().statusCode(404).body("code", equalTo("NOT_FOUND"));
        rechazar(999_999_999L, token, null).then().statusCode(404).body("code", equalTo("NOT_FOUND"));

        given().auth().oauth2(token).queryParam("estado", "VOLANDO")
                .when().get("/api/v1/reservas/recibidas")
                .then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        given().auth().oauth2(token).queryParam("desde", "el lunes")
                .when().get("/api/v1/reservas/recibidas")
                .then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        given().auth().oauth2(token).contentType(ContentType.JSON).body(Map.of("motivo", "x".repeat(201)))
                .when().post("/api/v1/reservas/1/rechazar")
                .then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void noSePuedeAtenderUnaReservaQueYaEmpezo() {
        Long cancha = crearCancha("Cancha reserva iniciada");
        Instant inicio = Instant.now().minus(30, ChronoUnit.MINUTES);

        // No se puede crear por HTTP una reserva en el pasado: se inserta directamente.
        Long idReserva = QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("INSERT INTO reserva "
                            + "(id_usuario, id_cancha, inicio, fin, monto_cancha, monto_descuento, estado) "
                            + "SELECT u.id_usuario, :idCancha, :inicio, :fin, 80.00, 0, 'PENDIENTE' "
                            + "FROM usuario u WHERE u.correo LIKE 'jugador.%' "
                            + "ORDER BY u.id_usuario DESC LIMIT 1")
                    .setParameter("idCancha", cancha.intValue())
                    .setParameter("inicio", LITERAL_UTC.format(LocalDateTime.ofInstant(inicio, ZoneOffset.UTC)))
                    .setParameter("fin", LITERAL_UTC.format(LocalDateTime.ofInstant(inicio.plus(1, ChronoUnit.HOURS), ZoneOffset.UTC)))
                    .executeUpdate();
            return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
        });

        confirmar(idReserva, token).then().statusCode(409).body("code", equalTo("CONFLICT"));
        rechazar(idReserva, token, null).then().statusCode(409).body("code", equalTo("CONFLICT"));
        assertEquals("PENDIENTE", estadoDe(recibidas(token, null), idReserva), "La reserva debe seguir pendiente.");
    }

    // ------------------------------------------------- revocacion (L-14)

    @Test
    void elPropietarioRevocaUnaReservaConfirmadaYLaFranjaVuelveAOfrecerse() {
        Long cancha = crearCancha("Cancha revocacion");
        LocalDate fecha = LocalDate.ofInstant(base, ZONA);
        Instant inicio = fecha.atTime(11, 0).atZone(ZONA).toInstant();
        Long idReserva = idDe(crearEnComo(tokenReservante, cancha, inicio, inicio.plus(1, ChronoUnit.HOURS))
                .then().statusCode(201).extract().response());

        confirmar(idReserva, token).then().statusCode(200).body("estado", equalTo("CONFIRMADA"));
        assertFalse(inicios(franjasCrudas(cancha, fecha, 60)).contains(inicio.toString()));

        revocar(idReserva, token, "Cambio imprevisto de mantenimiento").then()
                .statusCode(200)
                .body("estado", equalTo("RECHAZADA"))
                .body("inicio", equalTo(inicio.toString()));

        assertEquals("RECHAZADA", estadoDe(recibidas(token, "RECHAZADA"), idReserva));
        assertTrue(inicios(franjasCrudas(cancha, fecha, 60)).contains(inicio.toString()),
                "Al revocar, la franja debe volver a la disponibilidad.");
        crearEnComo(tokenReservante, cancha, inicio, inicio.plus(1, ChronoUnit.HOURS)).then().statusCode(201);

        assertTrue(ultimaObservacion(idReserva).contains("Cambio imprevisto de mantenimiento"),
                "El motivo de la revocacion debe quedar en la traza.");
        assertEquals(3, entradasDeHistorial(idReserva),
                "Creacion, confirmacion y revocacion deben quedar registradas.");
    }

    @Test
    void revocarUnaReservaNoConfirmadaDevuelve409() {
        Long cancha = crearCancha("Cancha revocacion pendiente");
        LocalDate fecha = LocalDate.ofInstant(base, ZONA);
        Instant inicio = fecha.atTime(17, 0).atZone(ZONA).toInstant();
        Long idReserva = idDe(crearEnComo(tokenReservante, cancha, inicio, inicio.plus(1, ChronoUnit.HOURS))
                .then().statusCode(201).extract().response());

        // Una solicitud aun PENDIENTE no se revoca: se rechaza.
        revocar(idReserva, token, null).then().statusCode(409).body("code", equalTo("CONFLICT"));

        confirmar(idReserva, token).then().statusCode(200);
        revocar(idReserva, token, null).then().statusCode(200).body("estado", equalTo("RECHAZADA"));
        // Ya no esta confirmada: un segundo intento da conflicto.
        revocar(idReserva, token, null).then().statusCode(409).body("code", equalTo("CONFLICT"));
        assertEquals(3, entradasDeHistorial(idReserva), "Los intentos fallidos no deben dejar traza.");
    }

    @Test
    void revocarReservasAjenasDevuelve403YReservaInexistenteDevuelve404() {
        given().contentType(ContentType.JSON).body(Map.of())
                .when().post("/api/v1/reservas/1/revocar").then().statusCode(401);

        Long cancha = crearCancha("Cancha revocacion ajena");
        LocalDate fecha = LocalDate.ofInstant(base, ZONA);
        Instant inicio = fecha.atTime(9, 0).atZone(ZONA).toInstant();
        Long idReserva = idDe(crearEnComo(tokenReservante, cancha, inicio, inicio.plus(1, ChronoUnit.HOURS))
                .then().statusCode(201).extract().response());
        confirmar(idReserva, token).then().statusCode(200);

        // El titular no revoca su propia reserva: es cliente, no propietario.
        revocar(idReserva, tokenReservante, null).then().statusCode(403).body("code", equalTo("FORBIDDEN"));

        // Otro propietario tampoco alcanza reservas de canchas ajenas.
        String tokenOtro = registrarEIniciarSesion("otrodueno2", "Julia", "Paredes", "955333221");
        crearEstablecimientoPropio(tokenOtro);
        revocar(idReserva, tokenOtro, null).then().statusCode(403).body("code", equalTo("FORBIDDEN"));

        revocar(999_999_999L, token, null).then().statusCode(404).body("code", equalTo("NOT_FOUND"));
        assertEquals("CONFIRMADA", estadoDe(recibidas(token, "CONFIRMADA"), idReserva),
                "La reserva debe seguir confirmada.");
    }

    // ---------------------------------------------------------------- utilidades

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

    private io.restassured.response.Response crear(Instant inicio, Instant fin) {
        return crearEn(idCancha, inicio, fin);
    }

    private Response crearEn(Long cancha, Instant inicio, Instant fin) {
        return given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("canchaId", cancha, "inicio", inicio.toString(), "fin", fin.toString()))
                .when().post("/api/v1/reservas")
                .then().extract().response();
    }

    /** Crea la reserva con el token indicado (permite que el titular no sea el propietario). */
    private Response crearEnComo(String tokenUsado, Long cancha, Instant inicio, Instant fin) {
        return given().auth().oauth2(tokenUsado).contentType(ContentType.JSON)
                .body(Map.of("canchaId", cancha, "inicio", inicio.toString(), "fin", fin.toString()))
                .when().post("/api/v1/reservas")
                .then().extract().response();
    }

    private Response confirmar(Long idReserva, String tokenUsado) {
        return given().auth().oauth2(tokenUsado)
                .when().post("/api/v1/reservas/" + idReserva + "/confirmar")
                .then().extract().response();
    }

    private Response rechazar(Long idReserva, String tokenUsado, String motivo) {
        Map<String, String> cuerpo = motivo == null ? Map.of() : Map.of("motivo", motivo);
        return given().auth().oauth2(tokenUsado).contentType(ContentType.JSON)
                .body(cuerpo)
                .when().post("/api/v1/reservas/" + idReserva + "/rechazar")
                .then().extract().response();
    }

    private Response revocar(Long idReserva, String tokenUsado, String motivo) {
        Map<String, String> cuerpo = motivo == null ? Map.of() : Map.of("motivo", motivo);
        return given().auth().oauth2(tokenUsado).contentType(ContentType.JSON)
                .body(cuerpo)
                .when().post("/api/v1/reservas/" + idReserva + "/revocar")
                .then().extract().response();
    }

    /** Bandeja del propietario; `estado` nulo no aplica filtro. */
    private List<Map<String, Object>> recibidas(String tokenUsado, String estado) {
        return recibidas(tokenUsado, estado, null, null, null);
    }

    /** Bandeja del propietario con filtros opcionales de cancha y rango de instantes. */
    private List<Map<String, Object>> recibidas(String tokenUsado, String estado, Long idCancha,
                                                Instant desde, Instant hasta) {
        var peticion = given().auth().oauth2(tokenUsado);
        if (estado != null) {
            peticion = peticion.queryParam("estado", estado);
        }
        if (idCancha != null) {
            peticion = peticion.queryParam("idCancha", idCancha);
        }
        if (desde != null) {
            peticion = peticion.queryParam("desde", desde.toString());
        }
        if (hasta != null) {
            peticion = peticion.queryParam("hasta", hasta.toString());
        }
        return peticion.when().get("/api/v1/reservas/recibidas")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
    }

    /** Estado de una reserva concreta dentro de una lista, o `null` si no aparece. */
    private static String estadoDe(List<Map<String, Object>> reservas, Long idReserva) {
        return reservas.stream()
                .filter(reserva -> ((Number) reserva.get("id")).longValue() == idReserva)
                .map(reserva -> String.valueOf(reserva.get("estado")))
                .findFirst().orElse(null);
    }

    /** Fila de la bandeja del propietario para una reserva concreta. */
    private static Map<String, Object> recibida(List<Map<String, Object>> reservas, Long idReserva) {
        return reservas.stream()
                .filter(reserva -> ((Number) reserva.get("id")).longValue() == idReserva)
                .findFirst().orElseThrow(() -> new AssertionError("La reserva no esta en la bandeja."));
    }

    private long entradasDeHistorial(Long idReserva) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM historial_reserva WHERE id_reserva = :id")
                .setParameter("id", idReserva).getSingleResult()).longValue());
    }

    /** Ultima observacion registrada en el historial de la reserva. */
    private String ultimaObservacion(Long idReserva) {
        return String.valueOf(QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                        "SELECT observacion FROM historial_reserva WHERE id_reserva = :id "
                                + "ORDER BY id_historial DESC LIMIT 1")
                .setParameter("id", idReserva).getSingleResult()));
    }

    /** Establecimiento con una cancha de otro propietario (aislamiento entre cuentas). */
    private void crearEstablecimientoPropio(String tokenUsado) {
        Long idDistrito = QuarkusTransaction.requiringNew().call(() ->
                ((Number) em.createNativeQuery("SELECT MIN(id_distrito) FROM distrito").getSingleResult()).longValue());
        Long id = ((Number) given().auth().oauth2(tokenUsado).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito,
                        "nombre", "Complejo Ajeno " + System.nanoTime(),
                        "direccion", "Av. Ajena 456",
                        "referencia", "Sin referencia"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201).extract().path("id")).longValue();

        given().auth().oauth2(tokenUsado).contentType(ContentType.JSON)
                .body(Map.of(
                        "nombre", "Cancha ajena " + System.nanoTime(),
                        "descripcion", "Futbol 5",
                        "capacidad", 10,
                        "precioBase", new BigDecimal("60.00")))
                .when().post("/api/v1/establecimientos/" + id + "/canchas")
                .then().statusCode(201);
    }

    private Response cancelar(Long idReserva, String tokenUsado) {
        return given().auth().oauth2(tokenUsado)
                .when().post("/api/v1/reservas/" + idReserva + "/cancelar")
                .then().extract().response();
    }

    /** Crea una cancha nueva en el establecimiento del test (aisla cada escenario). */
    private Long crearCancha(String nombre) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "nombre", nombre + " " + System.nanoTime(),
                        "descripcion", "Futbol 7 con grass sintetico",
                        "capacidad", 14,
                        "precioBase", new BigDecimal("80.00")))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/canchas")
                .then().statusCode(201)
                .body("id", notNullValue())
                .extract().path("id")).longValue();
    }

    /** Consulta la disponibilidad publica y devuelve las franjas como mapas JSON. */
    private List<Map<String, Object>> franjasCrudas(Long cancha, LocalDate fecha, int duracionMinutos) {        return given()
                .queryParam("fecha", fecha.toString())
                .queryParam("duracionMinutos", duracionMinutos)
                .when().get("/api/v1/canchas/" + cancha + "/disponibilidad")
                .then()
                .statusCode(200)
                .body("canchaId", equalTo(cancha.intValue()))
                .body("fecha", equalTo(fecha.toString()))
                .body("zonaHoraria", equalTo(ZONA.getId()))
                .body("duracionMinutos", equalTo(duracionMinutos))
                .extract().jsonPath().getList("franjas");
    }

    private static List<String> inicios(List<Map<String, Object>> franjas) {
        return franjas.stream().map(franja -> String.valueOf(franja.get("inicio"))).toList();
    }

    /** Respuesta cruda de disponibilidad: franjas libres y tramos ocupados con su motivo. */
    private Map<String, Object> disponibilidadCruda(Long cancha, LocalDate fecha) {
        return given()
                .queryParam("fecha", fecha.toString())
                .queryParam("duracionMinutos", 60)
                .when().get("/api/v1/canchas/" + cancha + "/disponibilidad")
                .then().statusCode(200)
                .extract().jsonPath().getMap("$");
    }

    /** Motivo reportado para el tramo ocupado que empieza en `inicio`, o null si no figura. */
    private static String estadoOcupada(List<Map<String, Object>> ocupadas, Instant inicio) {
        return ocupadas.stream()
                .filter(ocupada -> String.valueOf(ocupada.get("inicio")).equals(inicio.toString()))
                .map(ocupada -> String.valueOf(ocupada.get("estado")))
                .findFirst().orElse(null);
    }

    private static Long idDe(Response respuesta) {
        return ((Number) respuesta.path("id")).longValue();
    }

    /**
     * Lanza `total` peticiones identicas en paralelo con un `CountDownLatch` como
     * disparador comun. Se usa el cliente HTTP de la JDK (y no RestAssured) para no
     * quedar limitados por el tamano del pool de conexiones del cliente de pruebas.
     */
    private List<Integer> dispararSimultaneas(Instant inicio, Instant fin, int total) throws Exception {
        String cuerpo = "{\"canchaId\":" + idCancha + ",\"inicio\":\"" + inicio + "\",\"fin\":\"" + fin + "\"}";
        HttpClient cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        ExecutorService pool = Executors.newFixedThreadPool(total);
        CountDownLatch listos = new CountDownLatch(total);
        CountDownLatch disparo = new CountDownLatch(1);
        List<Future<Integer>> futuros = new ArrayList<>(total);

        try {
            for (int i = 0; i < total; i++) {
                futuros.add(pool.submit(() -> {
                    HttpRequest peticion = HttpRequest.newBuilder(URI.create(url("/api/v1/reservas")))
                            .timeout(Duration.ofSeconds(60))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                            .build();
                    listos.countDown();
                    disparo.await();
                    return cliente.send(peticion, HttpResponse.BodyHandlers.ofString()).statusCode();
                }));
            }
            assertTrue(listos.await(30, TimeUnit.SECONDS), "Los hilos no se prepararon a tiempo.");
            disparo.countDown();

            List<Integer> codigos = new ArrayList<>(total);
            for (Future<Integer> futuro : futuros) {
                codigos.add(futuro.get(120, TimeUnit.SECONDS));
            }
            return codigos;
        } finally {
            pool.shutdownNow();
        }
    }

    private int contarReservasVigentes(Long idCancha, Instant inicio, Instant fin) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM reserva WHERE id_cancha = :idCancha "
                                + "AND estado IN ('PENDIENTE','CONFIRMADA') "
                                + "AND inicio < :fin AND fin > :inicio")
                .setParameter("idCancha", idCancha.intValue())
                .setParameter("inicio", aLiteralUtc(inicio))
                .setParameter("fin", aLiteralUtc(fin))
                .getSingleResult()).intValue());
    }

    private static String aLiteralUtc(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZoneOffset.UTC).format(LITERAL_UTC);
    }

    /** Documento de identidad distinto en cada ejecucion (hay restriccion de unicidad). */
    private static String documentoUnico() {
        return String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
    }

    private String url(String ruta) {
        String base = raiz.toString();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + ruta;
    }
}
