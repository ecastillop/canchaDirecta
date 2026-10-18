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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
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
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integracion de la cochera opcional (RN-08).
 *
 * <p>Cubre: coherencia con el establecimiento de la cancha, cupo por capacidad con
 * reservas de cochera solapadas, liberacion del cupo al cancelar y el caso concurrente
 * (varias canchas compitiendo por la misma cochera al mismo tiempo: gana exactamente el
 * numero de cupos, el resto recibe 409).</p>
 *
 * <p>Cada test crea su propio establecimiento para que las aserciones sobre el listado de
 * cocheras no dependan de otras pruebas.</p>
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CocheraTest {

    /** Mismo formato que usa el adaptador para escribir los `DATETIME` en UTC. */
    private static final DateTimeFormatter LITERAL_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    @TestHTTPResource("/")
    URI raiz;

    @Inject
    EntityManager em;

    private String token;
    private Instant base;
    private final AtomicBoolean preparado = new AtomicBoolean(false);

    @BeforeEach
    void preparar() {
        if (!preparado.compareAndSet(false, true)) {
            return;
        }
        token = registrarEIniciarSesion("garaje", "Luz", "Mendoza", "944333222");

        // Franja base: pasado manana a las X:00, con desplazamiento aleatorio en minutos
        // para que dos ejecuciones seguidas no compitan por la misma franja.
        base = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS)
                .plus(ThreadLocalRandom.current().nextInt(3, 12) * 5L, ChronoUnit.MINUTES);
    }

    // ------------------------------------------------- reserva con cochera

    @Test
    void reservaConCocheraDevuelveElMontoYDescuentaElCupo() {
        Long establecimiento = crearEstablecimiento(token);
        Long cancha = crearCancha(token, establecimiento, "Cancha cochera");
        Long cochera = crearCochera(token, establecimiento, "Cochera A", 2, "10.00");
        Instant inicio = base.plus(1, ChronoUnit.HOURS);
        Instant fin = inicio.plus(1, ChronoUnit.HOURS);

        reservar(token, cancha, inicio, fin, cochera)
                .then()
                .statusCode(201)
                .body("cocheraId", equalTo(cochera.intValue()))
                .body("montoCochera", equalTo(10.00f));

        assertEquals(1, reservasDeCochera(cochera), "Debe registrarse una reserva de cochera.");
        assertEquals(1, cupoDe(cancha, cochera, inicio, fin), "El cupo debe descontar la reserva.");
        assertEquals(2, cupoDe(cancha, cochera, fin, fin.plus(1, ChronoUnit.HOURS)),
                "Una franja contigua no descuenta cupo (intervalos semiabiertos).");
    }

    @Test
    void reservaDeOraYMediaCalculaElMontoProporcional() {
        Long establecimiento = crearEstablecimiento(token);
        Long cancha = crearCancha(token, establecimiento, "Cancha media hora");
        Long cochera = crearCochera(token, establecimiento, "Cochera por hora", 1, "10.00");
        Instant inicio = base.plus(2, ChronoUnit.HOURS);

        // 90 minutos con tarifa 10.00/h -> 15.00.
        reservar(token, cancha, inicio, inicio.plus(90, ChronoUnit.MINUTES), cochera)
                .then().statusCode(201).body("montoCochera", equalTo(15.00f));
    }

    @Test
    void reservaSinCocheraNoRegistraCochera() {
        Long establecimiento = crearEstablecimiento(token);
        Long cancha = crearCancha(token, establecimiento, "Cancha sin cochera");
        Long cochera = crearCochera(token, establecimiento, "Cochera sin uso", 1, "5.00");
        Instant inicio = base.plus(2, ChronoUnit.HOURS);

        reservar(token, cancha, inicio, inicio.plus(1, ChronoUnit.HOURS), null)
                .then().statusCode(201).body("cocheraId", nullValue());

        assertEquals(0, reservasDeCochera(cochera), "Una reserva sin cochera no crea reserva de cochera.");
    }

    // ------------------------------------------------------- capacidad (cupo)

    @Test
    void cocheraLlenaDevuelve409SLOTUNAVAILABLE() {
        // Capacidad 1 y dos canchas distintas del mismo establecimiento: RN-01 no interviene
        // (son canchas diferentes); lo que se llena es la cochera.
        Long establecimiento = crearEstablecimiento(token);
        Long canchaA = crearCancha(token, establecimiento, "Cancha lleno A");
        Long canchaB = crearCancha(token, establecimiento, "Cancha lleno B");
        Long cochera = crearCochera(token, establecimiento, "Cochera unica", 1, "5.00");
        Instant inicio = base.plus(3, ChronoUnit.HOURS);
        Instant fin = inicio.plus(1, ChronoUnit.HOURS);

        reservar(token, canchaA, inicio, fin, cochera).then().statusCode(201);
        reservar(token, canchaB, inicio, fin, cochera).then()
                .statusCode(409)
                .body("code", equalTo("SLOT_UNAVAILABLE"));

        assertEquals(1, reservasDeCochera(cochera));
        // La cancha B sigue libre: lo que se agoto fue la cochera, no la cancha.
        assertEquals(0, contarReservasVigentes(canchaB, inicio, fin));
    }

    @Test
    void intervalosParcialesRespetanLaCapacidadDeDos() {
        Long establecimiento = crearEstablecimiento(token);
        Long canchaA = crearCancha(token, establecimiento, "Cancha parcial A");
        Long canchaB = crearCancha(token, establecimiento, "Cancha parcial B");
        Long canchaC = crearCancha(token, establecimiento, "Cancha parcial C");
        Long cochera = crearCochera(token, establecimiento, "Cochera doble", 2, "8.00");

        Instant inicio = base.plus(4, ChronoUnit.HOURS);
        // A ocupa 12:00-13:00, B se solapa parcialmente 12:30-13:30 (cupo 2 -> entra),
        // C se solapa 12:45-13:45 (ya no queda cupo -> 409).
        reservar(token, canchaA, inicio, inicio.plus(1, ChronoUnit.HOURS), cochera).then().statusCode(201);
        reservar(token, canchaB, inicio.plus(30, ChronoUnit.MINUTES),
                inicio.plus(90, ChronoUnit.MINUTES), cochera).then().statusCode(201);
        reservar(token, canchaC, inicio.plus(45, ChronoUnit.MINUTES),
                inicio.plus(105, ChronoUnit.MINUTES), cochera).then()
                .statusCode(409).body("code", equalTo("SLOT_UNAVAILABLE"));

        assertEquals(2, reservasDeCochera(cochera));
    }

    @Test
    void cancelarLiberaElCupoDeLaCochera() {
        Long establecimiento = crearEstablecimiento(token);
        Long canchaA = crearCancha(token, establecimiento, "Cancha libera A");
        Long canchaB = crearCancha(token, establecimiento, "Cancha libera B");
        Long cochera = crearCochera(token, establecimiento, "Cochera libera", 1, "7.00");
        Instant inicio = base.plus(5, ChronoUnit.HOURS);
        Instant fin = inicio.plus(1, ChronoUnit.HOURS);

        Long idReserva = idDe(reservar(token, canchaA, inicio, fin, cochera).then().statusCode(201)
                .extract().response());
        reservar(token, canchaB, inicio, fin, cochera).then().statusCode(409);

        given().auth().oauth2(token).when().post("/api/v1/reservas/" + idReserva + "/cancelar")
                .then().statusCode(200).body("estado", equalTo("CANCELADA"));

        // Al liberar el cupo, la cancha B puede reservar con cochera.
        assertEquals(1, cupoDe(canchaB, cochera, inicio, fin), "El cupo debe volver a estar libre.");
        reservar(token, canchaB, inicio, fin, cochera).then().statusCode(201);
    }

    // ---------------------------------------------------------- coherencia

    @Test
    void cocheraDeOtroEstablecimientoDevuelve400() {
        String otroToken = registrarEIniciarSesion("otrogaraje", "Ivan", "Torres", "933222111");
        Long establecimiento = crearEstablecimiento(token);
        Long otroEstablecimiento = crearEstablecimiento(otroToken);
        Long cocheraAjena = crearCochera(otroToken, otroEstablecimiento, "Cochera ajena", 1, "9.00");
        Long cancha = crearCancha(token, establecimiento, "Cancha propia");
        Instant inicio = base.plus(6, ChronoUnit.HOURS);

        reservar(token, cancha, inicio, inicio.plus(1, ChronoUnit.HOURS), cocheraAjena)
                .then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        assertEquals(0, reservasDeCochera(cocheraAjena));
    }

    @Test
    void cocheraDesactivadaNoSeOfreceNiSePuedeReservar() {
        Long establecimiento = crearEstablecimiento(token);
        Long cancha = crearCancha(token, establecimiento, "Cancha inactiva");
        Long cochera = crearCochera(token, establecimiento, "Cochera a cerrar", 1, "5.00");
        Instant inicio = base.plus(7, ChronoUnit.HOURS);

        given().auth().oauth2(token)
                .when().delete("/api/v1/establecimientos/" + establecimiento + "/cocheras/" + cochera)
                .then().statusCode(204);

        reservar(token, cancha, inicio, inicio.plus(1, ChronoUnit.HOURS), cochera)
                .then().statusCode(409).body("code", equalTo("SLOT_UNAVAILABLE"));
        assertTrue(cuposDe(cancha, inicio, inicio.plus(1, ChronoUnit.HOURS)).isEmpty(),
                "La cochera inactiva no debe ofrecerse.");
    }

    @Test
    void cocheraInexistenteDevuelve404() {
        Long establecimiento = crearEstablecimiento(token);
        Long cancha = crearCancha(token, establecimiento, "Cancha 404");
        Instant inicio = base.plus(8, ChronoUnit.HOURS);

        reservar(token, cancha, inicio, inicio.plus(1, ChronoUnit.HOURS), 999_999_999L)
                .then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }

    // ------------------------------------------------- gestion del propietario

    @Test
    void soloElPropietarioGestionaSusCocheras() {
        String otroToken = registrarEIniciarSesion("intruso", "Sara", "Nunez", "922111000");
        Long establecimiento = crearEstablecimiento(token);
        Long cochera = crearCochera(token, establecimiento, "Cochera reservada", 3, "6.00");

        given().auth().oauth2(token).when()
                .get("/api/v1/establecimientos/" + establecimiento + "/cocheras")
                .then().statusCode(200).body("$", hasSize(1))
                .body("[0].id", equalTo(cochera.intValue()))
                .body("[0].capacidad", equalTo(3))
                .body("[0].tarifaHora", equalTo(6.00f))
                .body("[0].estado", equalTo("ACTIVA"));

        given().auth().oauth2(otroToken).when()
                .get("/api/v1/establecimientos/" + establecimiento + "/cocheras")
                .then().statusCode(403).body("code", equalTo("FORBIDDEN"));
        given().auth().oauth2(otroToken).contentType(ContentType.JSON)
                .body(Map.of("nombre", "Cochera robada", "capacidad", 1, "tarifaHora", new BigDecimal("1.00")))
                .when().post("/api/v1/establecimientos/" + establecimiento + "/cocheras")
                .then().statusCode(403).body("code", equalTo("FORBIDDEN"));

        // Validacion del cuerpo.
        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of("nombre", "", "capacidad", 0, "tarifaHora", new BigDecimal("-1.00")))
                .when().post("/api/v1/establecimientos/" + establecimiento + "/cocheras")
                .then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        given().contentType(ContentType.JSON)
                .body(Map.of("nombre", "Sin token", "capacidad", 1, "tarifaHora", new BigDecimal("1.00")))
                .when().post("/api/v1/establecimientos/" + establecimiento + "/cocheras")
                .then().statusCode(401);
    }

    @Test
    void consultaDeCuposConParametrosInvalidosFalla() {
        Long establecimiento = crearEstablecimiento(token);
        Long cancha = crearCancha(token, establecimiento, "Cancha parametros");
        String ruta = "/api/v1/canchas/" + cancha + "/cocheras";

        given().when().get(ruta).then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        given().queryParam("inicio", base.toString()).queryParam("fin", base.toString())
                .when().get(ruta).then().statusCode(400);
        given().queryParam("inicio", "no-es-fecha").queryParam("fin", base.toString())
                .when().get(ruta).then().statusCode(400);
        given().queryParam("inicio", base.toString()).queryParam("fin", base.plus(1, ChronoUnit.HOURS).toString())
                .when().get("/api/v1/canchas/999999999/cocheras")
                .then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }

    // ------------------------------------------------------------ concurrencia

    @Test
    void soloLosCuposDisponiblesReservanConPeticionesSimultaneas() throws Exception {
        // 4 canchas del mismo establecimiento compiten por una cochera de capacidad 2 en la
        // misma franja: exactamente 2 deben reservar con cochera; las otras 2 reciben 409.
        Long establecimiento = crearEstablecimiento(token);
        List<Long> canchas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            canchas.add(crearCancha(token, establecimiento, "Cancha concurrencia " + i));
        }
        Long cochera = crearCochera(token, establecimiento, "Cochera concurrida", 2, "11.00");
        Instant inicio = base.plus(9, ChronoUnit.HOURS);
        Instant fin = inicio.plus(1, ChronoUnit.HOURS);

        List<Integer> codigos = dispararSimultaneas(canchas, cochera, inicio, fin);

        long creadas = codigos.stream().filter(c -> c == 201).count();
        long conflicto = codigos.stream().filter(c -> c == 409).count();
        assertEquals(2, creadas, "Solo los cupos disponibles deben reservar. Codigos: " + codigos);
        assertEquals(2, conflicto, "El resto debe recibir 409. Codigos: " + codigos);
        assertEquals(2, reservasDeCochera(cochera),
                "La base de datos debe contener exactamente dos reservas de cochera.");
    }

    // -------------------------------------------------------------- utilidades

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

    private Long crearEstablecimiento(String tokenUsado) {
        Long idDistrito = QuarkusTransaction.requiringNew().call(() ->
                ((Number) em.createNativeQuery("SELECT MIN(id_distrito) FROM distrito").getSingleResult()).longValue());
        return ((Number) given().auth().oauth2(tokenUsado).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito,
                        "nombre", "Complejo " + System.nanoTime(),
                        "direccion", "Av. Los Deportes 123",
                        "referencia", "Frente al parque"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long crearCancha(String tokenUsado, Long idEstablecimiento, String nombre) {
        return ((Number) given().auth().oauth2(tokenUsado).contentType(ContentType.JSON)
                .body(Map.of(
                        "nombre", nombre + " " + System.nanoTime(),
                        "descripcion", "Futbol 7",
                        "capacidad", 14,
                        "precioBase", new BigDecimal("80.00")))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/canchas")
                .then().statusCode(201).body("id", notNullValue())
                .extract().path("id")).longValue();
    }

    private Long crearCochera(String tokenUsado, Long idEstablecimiento, String nombre, int capacidad,
                              String tarifa) {
        return ((Number) given().auth().oauth2(tokenUsado).contentType(ContentType.JSON)
                .body(Map.of("nombre", nombre + " " + System.nanoTime(),
                        "capacidad", capacidad,
                        "tarifaHora", new BigDecimal(tarifa)))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/cocheras")
                .then().statusCode(201).body("id", notNullValue())
                .extract().path("id")).longValue();
    }

    /** Reserva con cochera opcional; `cocheraId` nulo reserva solo la cancha. */
    private Response reservar(String tokenUsado, Long cancha, Instant inicio, Instant fin, Long cocheraId) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("canchaId", cancha);
        cuerpo.put("inicio", inicio.toString());
        cuerpo.put("fin", fin.toString());
        if (cocheraId != null) {
            cuerpo.put("cocheraId", cocheraId);
        }
        return given().auth().oauth2(tokenUsado).contentType(ContentType.JSON)
                .body(cuerpo)
                .when().post("/api/v1/reservas")
                .then().extract().response();
    }

    private List<Map<String, Object>> cuposDe(Long cancha, Instant inicio, Instant fin) {
        return given()
                .queryParam("inicio", inicio.toString())
                .queryParam("fin", fin.toString())
                .when().get("/api/v1/canchas/" + cancha + "/cocheras")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
    }

    private int cupoDe(Long cancha, Long cochera, Instant inicio, Instant fin) {
        return cuposDe(cancha, inicio, fin).stream()
                .filter(cupo -> ((Number) cupo.get("id")).longValue() == cochera)
                .map(cupo -> ((Number) cupo.get("cupoDisponible")).intValue())
                .findFirst().orElseThrow(() -> new AssertionError("La cochera no aparece en la consulta."));
    }

    private int reservasDeCochera(Long idCochera) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM reserva_cochera WHERE id_cochera = :id")
                .setParameter("id", idCochera.intValue()).getSingleResult()).intValue());
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

    private static Long idDe(Response respuesta) {
        return ((Number) respuesta.path("id")).longValue();
    }

    private static String documentoUnico() {
        return String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
    }

    /**
     * Dispara una peticion por cancha (misma cochera y franja) en paralelo con un
     * `CountDownLatch` como disparador comun, usando el cliente HTTP de la JDK (no
     * RestAssured) para no quedar limitado por el pool de conexiones de pruebas.
     */
    private List<Integer> dispararSimultaneas(List<Long> canchas, Long cochera, Instant inicio, Instant fin)
            throws Exception {
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        ExecutorService pool = Executors.newFixedThreadPool(canchas.size());
        CountDownLatch listos = new CountDownLatch(canchas.size());
        CountDownLatch disparo = new CountDownLatch(1);
        List<Future<Integer>> futuros = new ArrayList<>(canchas.size());

        try {
            for (Long cancha : canchas) {
                String cuerpo = "{\"canchaId\":" + cancha + ",\"inicio\":\"" + inicio + "\",\"fin\":\"" + fin
                        + "\",\"cocheraId\":" + cochera + "}";
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

            List<Integer> codigos = new ArrayList<>(canchas.size());
            for (Future<Integer> futuro : futuros) {
                codigos.add(futuro.get(120, TimeUnit.SECONDS));
            }
            return codigos;
        } finally {
            pool.shutdownNow();
        }
    }

    private String url(String ruta) {
        String base = raiz.toString();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + ruta;
    }
}
