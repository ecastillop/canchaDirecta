package pe.com.canchadirecta.venues;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agenda del propietario: horarios semanales de atencion (HU-005) y bloqueos operativos
 * (RN-03), incluido su efecto sobre la disponibilidad publica y sobre la reserva.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgendaTest {

    /** Zona de operacion configurada en `application.properties` (`cd.zona-horaria`). */
    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @TestHTTPResource("/")
    URI raiz;

    @Inject
    EntityManager em;

    private String token;
    private String tokenAjeno;
    private Long idEstablecimiento;
    private final AtomicBoolean preparado = new AtomicBoolean(false);

    /**
     * La preparacion va en `@BeforeEach` (no en `@BeforeAll`) porque el puerto aleatorio
     * del servidor de pruebas solo se publica a RestAssured antes de cada test.
     */
    @BeforeEach
    void preparar() {
        if (!preparado.compareAndSet(false, true)) {
            return;
        }
        token = registrarEIniciarSesion("dueno.agenda", "Elena", "Vargas", "955666777");
        tokenAjeno = registrarEIniciarSesion("ajeno.agenda", "Julio", "Paredes", "955888999");

        Long idDistrito = QuarkusTransaction.requiringNew().call(() ->
                ((Number) em.createNativeQuery("SELECT MIN(id_distrito) FROM distrito").getSingleResult()).longValue());

        idEstablecimiento = ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito,
                        "nombre", "Complejo Agenda " + System.nanoTime(),
                        "direccion", "Av. Los Deportes 456",
                        "referencia", "Al costado del estadio"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201)
                .extract().path("id")).longValue();
    }

    // ----------------------------------------------------------- horarios

    @Test
    void elPropietarioGestionaSusHorariosYSePublican() {
        Long cancha = crearCancha("Cancha con horarios");

        Long idLunes = idDe(crearHorario(cancha, 1, "08:00", "12:00", "50.00")
                .then().statusCode(201).body("id", notNullValue()).extract().response());

        // El catalogo de horarios es publico (lo consume la web antes de reservar).
        given().when().get("/api/v1/canchas/" + cancha + "/horarios")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].diaSemana", equalTo(1))
                .body("[0].horaInicio", equalTo("08:00:00"))
                .body("[0].horaFin", equalTo("12:00:00"))
                .body("[0].activo", equalTo(true));

        // Mismo rango y rango solapado: 409 (si no, habria dos precios para la misma hora).
        crearHorario(cancha, 1, "08:00", "12:00", "50.00").then().statusCode(409)
                .body("code", equalTo("CONFLICT"));
        crearHorario(cancha, 1, "11:00", "13:00", null).then().statusCode(409)
                .body("code", equalTo("CONFLICT"));

        // Otro dia de la semana no molesta, y el precio es opcional.
        Long idMartes = idDe(crearHorario(cancha, 2, "08:00", "12:00", null)
                .then().statusCode(201).extract().response());

        // Validaciones de forma.
        crearHorario(cancha, 8, "08:00", "12:00", null).then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
        crearHorario(cancha, 3, "12:00", "08:00", null).then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
        crearHorario(cancha, 3, "09:00", "09:00", null).then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));

        // Baja logica: desaparece del catalogo y una segunda baja es 404.
        given().auth().oauth2(token).when().delete("/api/v1/canchas/" + cancha + "/horarios/" + idLunes)
                .then().statusCode(204);
        given().when().get("/api/v1/canchas/" + cancha + "/horarios")
                .then().statusCode(200).body("$", hasSize(1)).body("[0].diaSemana", equalTo(2));
        given().auth().oauth2(token).when().delete("/api/v1/canchas/" + cancha + "/horarios/" + idLunes)
                .then().statusCode(404).body("code", equalTo("NOT_FOUND"));

        // Tras la baja se puede volver a registrar ese rango.
        crearHorario(cancha, 1, "08:00", "12:00", "60.00").then().statusCode(201);

        // El horario borrado ya no existe para esa cancha.
        given().auth().oauth2(token).when()
                .delete("/api/v1/canchas/" + cancha + "/horarios/" + idMartes)
                .then().statusCode(204);
    }

    @Test
    void disponibilidadUsaElHorarioConfiguradoYNoLaVentanaPorDefecto() {
        Long cancha = crearCancha("Cancha con horario propio");
        LocalDate fecha = LocalDate.now(ZONA).plusDays(2);
        int dia = fecha.getDayOfWeek().getValue();

        crearHorario(cancha, dia, "14:00", "16:00", "50.00").then().statusCode(201);

        List<Map<String, Object>> franjas = franjasCrudas(cancha, fecha, 60);

        assertEquals(List.of(
                        fecha.atTime(14, 0).atZone(ZONA).toInstant().toString(),
                        fecha.atTime(15, 0).atZone(ZONA).toInstant().toString()),
                inicios(franjas), "Solo debe ofrecerse la ventana del horario configurado.");
        assertEquals(0, new BigDecimal(String.valueOf(franjas.get(0).get("precio")))
                        .compareTo(new BigDecimal("50.00")),
                "Con horario configurado rige el precio del horario.");
    }

    @Test
    void unaCanchaConAgendaCierraLosDiasSinHorario() {
        Long cancha = crearCancha("Cancha con agenda parcial");
        LocalDate fecha = LocalDate.now(ZONA).plusDays(2);
        int dia = fecha.getDayOfWeek().getValue();
        int otroDia = dia == 1 ? 2 : 1;

        crearHorario(cancha, otroDia, "09:00", "11:00", null).then().statusCode(201);

        assertEquals(0, franjasCrudas(cancha, fecha, 60).size(),
                "Con agenda configurada, un dia sin horario queda cerrado.");
    }

    // ----------------------------------------------------------- bloqueos

    @Test
    void elBloqueoImpideReservarYSeLiberaAlLevantarlo() {
        Long cancha = crearCancha("Cancha con bloqueo");
        LocalDate fecha = LocalDate.now(ZONA).plusDays(2);
        Instant inicio = fecha.atTime(12, 0).atZone(ZONA).toInstant();
        Instant fin = inicio.plus(1, ChronoUnit.HOURS);

        Long idBloqueo = idDe(crearBloqueo(cancha, inicio, fin, "Mantenimiento de grass")
                .then().statusCode(201).body("id", notNullValue()).extract().response());

        // RN-03: la franja ni se ofrece ni se puede reservar.
        assertFalse(inicios(franjasCrudas(cancha, fecha, 60)).contains(inicio.toString()),
                "La franja bloqueada no debe ofrecerse.");
        reservar(tokenAjeno, cancha, inicio, fin).then().statusCode(409)
                .body("code", equalTo("SLOT_UNAVAILABLE"));

        // Un bloqueo que se solapa con otro es 409.
        crearBloqueo(cancha, inicio.plusSeconds(1800), fin.plusSeconds(1800), "Otro motivo")
                .then().statusCode(409).body("code", equalTo("CONFLICT"));

        // El propietario puede consultar su agenda de bloqueos.
        given().auth().oauth2(token)
                .queryParam("desde", inicio.minus(1, ChronoUnit.HOURS).toString())
                .queryParam("hasta", fin.plus(1, ChronoUnit.HOURS).toString())
                .when().get("/api/v1/canchas/" + cancha + "/bloqueos")
                .then().statusCode(200).body("$", hasSize(1))
                .body("[0].motivo", equalTo("Mantenimiento de grass"))
                .body("[0].inicio", equalTo(inicio.toString()));

        // Levantar el bloqueo devuelve la franja al catalogo y a la reserva.
        given().auth().oauth2(token).when().delete("/api/v1/canchas/" + cancha + "/bloqueos/" + idBloqueo)
                .then().statusCode(204);
        assertTrue(inicios(franjasCrudas(cancha, fecha, 60)).contains(inicio.toString()),
                "Sin bloqueo la franja vuelve a ofrecerse.");
        reservar(tokenAjeno, cancha, inicio, fin).then().statusCode(201);

        // Y ahora la franja ya no se puede bloquear: tiene una reserva vigente.
        crearBloqueo(cancha, inicio, fin, "Mantenimiento de grass")
                .then().statusCode(409).body("code", equalTo("CONFLICT"));

        // Un bloqueo levantado dos veces es 404.
        given().auth().oauth2(token).when().delete("/api/v1/canchas/" + cancha + "/bloqueos/" + idBloqueo)
                .then().statusCode(404).body("code", equalTo("NOT_FOUND"));

        // Validaciones de forma: rango invertido y motivo vacio.
        crearBloqueo(cancha, fin, inicio, "Rango invertido").then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
        crearBloqueo(cancha, fin, fin.plus(1, ChronoUnit.HOURS), " ").then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    // ------------------------------------------------- autorizacion

    @Test
    void laAgendaExigeSerElPropietario() {
        Long cancha = crearCancha("Cancha ajena");
        Long idHorario = idDe(crearHorario(cancha, 1, "08:00", "12:00", "50.00")
                .then().statusCode(201).extract().response());

        // Consultar horarios es publico; el resto exige autenticacion.
        given().when().get("/api/v1/canchas/" + cancha + "/horarios").then().statusCode(200);
        given().contentType(ContentType.JSON).body(cuerpoHorario(2, "08:00", "12:00", null))
                .when().post("/api/v1/canchas/" + cancha + "/horarios").then().statusCode(401);
        given().when().delete("/api/v1/canchas/" + cancha + "/horarios/" + idHorario).then().statusCode(401);
        given().when().get("/api/v1/canchas/" + cancha + "/bloqueos").then().statusCode(401);

        // Un usuario autenticado que no es propietario recibe 403.
        given().auth().oauth2(tokenAjeno).contentType(ContentType.JSON)
                .body(cuerpoHorario(2, "08:00", "12:00", null))
                .when().post("/api/v1/canchas/" + cancha + "/horarios").then().statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
        given().auth().oauth2(tokenAjeno).when()
                .delete("/api/v1/canchas/" + cancha + "/horarios/" + idHorario)
                .then().statusCode(403).body("code", equalTo("FORBIDDEN"));
        given().auth().oauth2(tokenAjeno)
                .when().get("/api/v1/canchas/" + cancha + "/bloqueos").then().statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
        given().auth().oauth2(tokenAjeno).contentType(ContentType.JSON)
                .body(Map.of("inicio", Instant.now().plus(2, ChronoUnit.DAYS).toString(),
                        "fin", Instant.now().plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS).toString(),
                        "motivo", "Intento"))
                .when().post("/api/v1/canchas/" + cancha + "/bloqueos").then().statusCode(403)
                .body("code", equalTo("FORBIDDEN"));

        // Cancha inexistente: 404 incluso para el propietario autenticado.
        given().auth().oauth2(token).when().get("/api/v1/canchas/999999999/horarios")
                .then().statusCode(404).body("code", equalTo("NOT_FOUND"));
        given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(cuerpoHorario(1, "08:00", "12:00", null))
                .when().post("/api/v1/canchas/999999999/horarios")
                .then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    }

    // ----------------------------------------------------------- helpers

    private Response crearHorario(Long cancha, int diaSemana, String inicio, String fin, String precio) {
        return given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(cuerpoHorario(diaSemana, inicio, fin, precio))
                .when().post("/api/v1/canchas/" + cancha + "/horarios");
    }

    /** Cuerpo de alta de horario; el precio es opcional (si falta rige el precio base). */
    private static Map<String, Object> cuerpoHorario(int diaSemana, String inicio, String fin, String precio) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("diaSemana", diaSemana);
        cuerpo.put("horaInicio", inicio);
        cuerpo.put("horaFin", fin);
        if (precio != null) {
            cuerpo.put("precio", new BigDecimal(precio));
        }
        return cuerpo;
    }

    private Response crearBloqueo(Long cancha, Instant inicio, Instant fin, String motivo) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("inicio", inicio.toString());
        cuerpo.put("fin", fin.toString());
        cuerpo.put("motivo", motivo);
        return given().auth().oauth2(token).contentType(ContentType.JSON).body(cuerpo)
                .when().post("/api/v1/canchas/" + cancha + "/bloqueos");
    }

    private Response reservar(String quien, Long cancha, Instant inicio, Instant fin) {
        return given().auth().oauth2(quien).contentType(ContentType.JSON)
                .body(Map.of("canchaId", cancha, "inicio", inicio.toString(), "fin", fin.toString()))
                .when().post("/api/v1/reservas");
    }

    private List<Map<String, Object>> franjasCrudas(Long cancha, LocalDate fecha, int duracionMinutos) {
        return given()
                .queryParam("fecha", fecha.toString())
                .queryParam("duracionMinutos", duracionMinutos)
                .when().get("/api/v1/canchas/" + cancha + "/disponibilidad")
                .then()
                .statusCode(200)
                .body("canchaId", equalTo(cancha.intValue()))
                .extract().jsonPath().getList("franjas");
    }

    private static List<String> inicios(List<Map<String, Object>> franjas) {
        return franjas.stream().map(franja -> String.valueOf(franja.get("inicio"))).toList();
    }

    private static Long idDe(Response respuesta) {
        return ((Number) respuesta.path("id")).longValue();
    }

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

    private static String registrarEIniciarSesion(String prefijo, String nombres, String apellidos, String telefono) {
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
