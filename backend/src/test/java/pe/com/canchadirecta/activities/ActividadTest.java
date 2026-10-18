package pe.com.canchadirecta.activities;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

/**
 * Actividades e inscripciones: publicacion por el propietario, cupos, inscripcion del
 * cliente, listado de participantes y cancelacion.
 */
@QuarkusTest
class ActividadTest {

    @Inject
    EntityManager em;

    @Test
    void elPropietarioPublicaYElClienteSeInscribe() {
        String dueno = nuevoUsuario("dueno.actividad");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Instant inicio = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant fin = inicio.plus(2, ChronoUnit.HOURS);

        Long idActividad = crearActividad(dueno, idEstablecimiento, "Torneo relampago", inicio, fin, 2);

        given().when().get("/api/v1/actividades")
                .then().statusCode(200)
                .body("id", hasItem(idActividad.intValue()));

        given().when().get("/api/v1/actividades/" + idActividad)
                .then().statusCode(200)
                .body("nombre", equalTo("Torneo relampago"))
                .body("establecimiento", containsString("Complejo Actividad"))
                .body("cupos", equalTo(2))
                .body("inscritos", equalTo(0))
                .body("cuposDisponibles", equalTo(2))
                .body("estado", equalTo("ACTIVA"));

        String cliente = nuevoUsuario("cliente.actividad");
        given().auth().oauth2(cliente).contentType(ContentType.JSON)
                .when().post("/api/v1/actividades/" + idActividad + "/inscripciones")
                .then().statusCode(201)
                .body("estado", equalTo("CONFIRMADA"))
                .body("idActividad", equalTo(idActividad.intValue()));

        given().auth().oauth2(cliente).contentType(ContentType.JSON)
                .when().post("/api/v1/actividades/" + idActividad + "/inscripciones")
                .then().statusCode(409);

        given().when().get("/api/v1/actividades/" + idActividad)
                .then().statusCode(200)
                .body("inscritos", equalTo(1))
                .body("cuposDisponibles", equalTo(1));

        given().auth().oauth2(cliente).when().get("/api/v1/inscripciones/mias")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].idActividad", equalTo(idActividad.intValue()))
                .body("[0].estado", equalTo("CONFIRMADA"));

        given().auth().oauth2(dueno).when().get("/api/v1/actividades/" + idActividad + "/inscripciones")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].correoUsuario", containsString("cliente.actividad"))
                .body("[0].nombreUsuario", containsString("Usuario"));

        given().auth().oauth2(cliente).when().get("/api/v1/actividades/" + idActividad + "/inscripciones")
                .then().statusCode(403);

        // El cliente cancela y puede volver a inscribirse mientras haya cupo.
        given().auth().oauth2(cliente).when()
                .delete("/api/v1/actividades/" + idActividad + "/inscripciones")
                .then().statusCode(204);
        given().auth().oauth2(cliente).contentType(ContentType.JSON)
                .when().post("/api/v1/actividades/" + idActividad + "/inscripciones")
                .then().statusCode(201);

        // El propietario cancela la actividad: sale del catalogo y las inscripciones quedan canceladas.
        given().auth().oauth2(dueno).when().delete("/api/v1/actividades/" + idActividad)
                .then().statusCode(204);
        given().when().get("/api/v1/actividades")
                .then().statusCode(200)
                .body("id", not(hasItem(idActividad.intValue())));
        given().auth().oauth2(cliente).when().get("/api/v1/inscripciones/mias")
                .then().statusCode(200)
                .body("[0].estado", equalTo("CANCELADA"));
    }

    @Test
    void controlaCuposPermisosYValidaciones() {
        String dueno = nuevoUsuario("dueno.cupos");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Instant inicio = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant fin = inicio.plus(1, ChronoUnit.HOURS);
        Long idActividad = crearActividad(dueno, idEstablecimiento, "Clase de prueba", inicio, fin, 1);

        String primero = nuevoUsuario("cliente.primero");
        String segundo = nuevoUsuario("cliente.segundo");
        given().auth().oauth2(primero).when().post("/api/v1/actividades/" + idActividad + "/inscripciones")
                .then().statusCode(201);
        given().auth().oauth2(segundo).when().post("/api/v1/actividades/" + idActividad + "/inscripciones")
                .then().statusCode(409);

        String intruso = nuevoUsuario("dueno.intruso");
        Long otroEstablecimiento = crearEstablecimiento(intruso);
        given().auth().oauth2(intruso).contentType(ContentType.JSON)
                .body(cuerpoActividad("Actividad ajena", inicio, fin, 1))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/actividades")
                .then().statusCode(403);

        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(cuerpoActividad("Actividad ajena", inicio, fin, 1))
                .when().post("/api/v1/establecimientos/" + otroEstablecimiento + "/actividades")
                .then().statusCode(403);

        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(cuerpoActividad("Cupos invalidos", inicio, fin, 0))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/actividades")
                .then().statusCode(400);

        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(cuerpoActividad("Fechas invalidas", fin, inicio, 1))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/actividades")
                .then().statusCode(400);

        given().when().get("/api/v1/actividades/999999999")
                .then().statusCode(404);
    }

    private Long crearActividad(String token, Long idEstablecimiento, String nombre,
                                Instant inicio, Instant fin, int cupos) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(cuerpoActividad(nombre, inicio, fin, cupos))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/actividades")
                .then().statusCode(201)
                .extract().path("id")).longValue();
    }

    private static Map<String, Object> cuerpoActividad(String nombre, Instant inicio, Instant fin, int cupos) {
        return Map.of(
                "nombre", nombre,
                "descripcion", "Actividad de prueba",
                "inicio", inicio.toString(),
                "fin", fin.toString(),
                "cupos", cupos,
                "precio", new BigDecimal("15.00"));
    }

    private Long crearEstablecimiento(String token) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito(),
                        "nombre", "Complejo Actividad " + System.nanoTime(),
                        "direccion", "Av. Actividad 123",
                        "referencia", "Cancha principal"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long idDistrito() {
        Object valor = QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery("SELECT MIN(id_distrito) FROM distrito").getSingleResult());
        return ((Number) valor).longValue();
    }

    private String nuevoUsuario(String prefijo) {
        String correo = prefijo + "." + System.nanoTime() + "@canchadirecta.pe";
        given().contentType(ContentType.JSON)
                .body(Map.of(
                        "nombres", "Usuario",
                        "apellidos", "Actividad",
                        "correo", correo,
                        "telefono", "977888" + Math.floorMod(System.nanoTime(), 1000),
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
        return String.valueOf(java.util.concurrent.ThreadLocalRandom.current()
                .nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
    }
}
