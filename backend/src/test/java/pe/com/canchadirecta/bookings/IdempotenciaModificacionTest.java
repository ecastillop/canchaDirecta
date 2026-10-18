package pe.com.canchadirecta.bookings;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** CU03.2 (idempotencia de la creacion) y CU03.7 (reprogramacion de reservas). */
@QuarkusTest
class IdempotenciaModificacionTest {

    @Inject
    EntityManager em;

    @Test
    void laClaveDeIdempotenciaEvitaReservasDuplicadas() {
        String cliente = nuevoUsuario("cliente.idem");
        Long cancha = canchaDe(nuevoUsuario("dueno.idem"));
        Instant inicio = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        String clave = "idem-" + System.nanoTime();

        int primera = reservar(cliente, cancha, inicio, 60, clave)
                .statusCode(201).body("id", notNullValue()).extract().path("id");

        int repetida = reservar(cliente, cancha, inicio, 60, clave)
                .statusCode(201).body("id", notNullValue()).extract().path("id");
        assertEquals(primera, repetida, "la repeticion debe devolver la misma reserva");

        int otraClave = reservar(cliente, cancha, inicio.plus(3, ChronoUnit.HOURS), 60,
                clave + "-b").statusCode(201).extract().path("id");
        assertNotEquals(primera, otraClave);

        given().auth().oauth2(cliente).when().get("/api/v1/reservas/mias")
                .then().statusCode(200).body("size()", equalTo(2));

        given().auth().oauth2(cliente).contentType(ContentType.JSON)
                .header("Idempotency-Key", "x".repeat(81))
                .body(cuerpo(cancha, inicio.plus(5, ChronoUnit.HOURS), 60))
                .when().post("/api/v1/reservas")
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void reprogramaLaReservaVigenteYLiberaLaFranjaAnterior() {
        String cliente = nuevoUsuario("cliente.reprog");
        String otro = nuevoUsuario("otro.reprog");
        Long cancha = canchaDe(nuevoUsuario("dueno.reprog"));
        Instant inicio = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

        int idReserva = reservar(cliente, cancha, inicio, 60, null)
                .statusCode(201).extract().path("id");

        Instant nueva = inicio.plus(2, ChronoUnit.HOURS);
        given().auth().oauth2(cliente).contentType(ContentType.JSON)
                .body(Map.of("inicio", nueva.toString(),
                        "fin", nueva.plus(60, ChronoUnit.MINUTES).toString()))
                .when().post("/api/v1/reservas/" + idReserva + "/modificar")
                .then().statusCode(200)
                .body("inicio", equalTo(nueva.toString()))
                .body("estado", equalTo("PENDIENTE"));

        // La franja original quedo libre para otro titular
        reservar(otro, cancha, inicio, 60, null).statusCode(201);

        // Cambiar la duracion no esta permitido en la reprogramacion
        given().auth().oauth2(cliente).contentType(ContentType.JSON)
                .body(Map.of("inicio", inicio.plus(5, ChronoUnit.HOURS).toString(),
                        "fin", inicio.plus(6, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES).toString()))
                .when().post("/api/v1/reservas/" + idReserva + "/modificar")
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));

        // Un tercer usuario no puede mover una reserva ajena
        given().auth().oauth2(otro).contentType(ContentType.JSON)
                .body(Map.of("inicio", inicio.plus(7, ChronoUnit.HOURS).toString(),
                        "fin", inicio.plus(8, ChronoUnit.HOURS).toString()))
                .when().post("/api/v1/reservas/" + idReserva + "/modificar")
                .then().statusCode(403);

        // Una reserva cancelada ya no se reprograma
        given().auth().oauth2(cliente)
                .when().post("/api/v1/reservas/" + idReserva + "/cancelar")
                .then().statusCode(200);
        given().auth().oauth2(cliente).contentType(ContentType.JSON)
                .body(Map.of("inicio", inicio.plus(9, ChronoUnit.HOURS).toString(),
                        "fin", inicio.plus(10, ChronoUnit.HOURS).toString()))
                .when().post("/api/v1/reservas/" + idReserva + "/modificar")
                .then().statusCode(409)
                .body("code", equalTo("CONFLICT"));
    }

    // ---------------------------------------------------------------- utilidades

    private io.restassured.response.ValidatableResponse reservar(String token, Long cancha,
                                                                 Instant inicio, long minutos,
                                                                 String clave) {
        var peticion = given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(cuerpo(cancha, inicio, minutos));
        if (clave != null) {
            peticion = peticion.header("Idempotency-Key", clave);
        }
        return peticion.when().post("/api/v1/reservas").then();
    }

    private static Map<String, Object> cuerpo(Long cancha, Instant inicio, long minutos) {
        return Map.of("canchaId", cancha, "inicio", inicio.toString(),
                "fin", inicio.plus(minutos, ChronoUnit.MINUTES).toString());
    }

    private Long canchaDe(String dueno) {
        Long idEstablecimiento = ((Number) given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito(),
                        "nombre", "Complejo Reservas " + System.nanoTime(),
                        "direccion", "Av. Reservas 10"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201).extract().path("id")).longValue();
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", "Cancha idem " + System.nanoTime());
        cuerpo.put("descripcion", "Futbol 7");
        cuerpo.put("capacidad", 14);
        cuerpo.put("precioBase", new BigDecimal("60.00"));
        return ((Number) given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(cuerpo)
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/canchas")
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
                .body(Map.of("nombres", "Usuario", "apellidos", "Reservas", "correo", correo,
                        "telefono", "955777" + Math.floorMod(System.nanoTime(), 1000),
                        "password", "ClaveSegura123",
                        "confirmacionPassword", "ClaveSegura123"))
                .when().post("/api/v1/auth/register")
                .then().statusCode(201);
        return given().contentType(ContentType.JSON)
                .body(Map.of("correo", correo, "password", "ClaveSegura123"))
                .when().post("/api/v1/auth/login")
                .then().statusCode(200).extract().path("accessToken");
    }

    private static String documentoUnico() {
        return String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
    }
}
