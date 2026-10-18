package pe.com.canchadirecta.venues;

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
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Edicion (CU04.1) y baja logica (CU04.2) de establecimientos y canchas, y comparacion de
 * canchas (CU02.2).
 */
@QuarkusTest
class EdicionYBajaVenuesTest {

    @Inject
    EntityManager em;

    @Test
    void elPropietarioEditaSuEstablecimientoYSuCancha() {
        String dueno = nuevoUsuario("dueno.edita");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idCancha = crearCancha(dueno, idEstablecimiento, "Cancha antes " + System.nanoTime(),
                new BigDecimal("70.00"));
        String nombreEstablecimiento = "Complejo Editado " + System.nanoTime();

        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of(
                        "idDistrito", idDistrito(),
                        "nombre", nombreEstablecimiento,
                        "direccion", "Av. Nueva 999",
                        "referencia", "Al costado del estadio"))
                .when().put("/api/v1/establecimientos/" + idEstablecimiento)
                .then().statusCode(200)
                .body("id", equalTo(idEstablecimiento.intValue()))
                .body("nombre", equalTo(nombreEstablecimiento))
                .body("direccion", equalTo("Av. Nueva 999"))
                .body("estado", equalTo("ACTIVO"));

        String nombreCancha = "Cancha despues " + System.nanoTime();
        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of(
                        "nombre", nombreCancha,
                        "descripcion", "Grass sintetico renovado",
                        "capacidad", 20,
                        "precioBase", new BigDecimal("95.50")))
                .when().put("/api/v1/canchas/" + idCancha)
                .then().statusCode(200)
                .body("id", equalTo(idCancha.intValue()))
                .body("nombre", equalTo(nombreCancha))
                .body("capacidad", equalTo(20))
                .body("estado", equalTo("ACTIVA"));

        given().when().get("/api/v1/canchas/" + idCancha)
                .then().statusCode(200)
                .body("nombre", equalTo(nombreCancha))
                .body("descripcion", equalTo("Grass sintetico renovado"))
                .body("capacidad", equalTo(20))
                .body("precioBase", equalTo(95.50f))
                .body("establecimiento", equalTo(nombreEstablecimiento))
                .body("direccion", equalTo("Av. Nueva 999"));

        given().auth().oauth2(dueno).when()
                .get("/api/v1/establecimientos/" + idEstablecimiento)
                .then().statusCode(200)
                .body("id", equalTo(idEstablecimiento.intValue()))
                .body("idDistrito", equalTo(idDistrito().intValue()))
                .body("nombre", equalTo(nombreEstablecimiento))
                .body("direccion", equalTo("Av. Nueva 999"))
                .body("referencia", equalTo("Al costado del estadio"))
                .body("estado", equalTo("ACTIVO"));

        given().auth().oauth2(dueno).when()
                .get("/api/v1/establecimientos/mios")
                .then().statusCode(200)
                .body("id", hasItem(idEstablecimiento.intValue()))
                .body("nombre", hasItem(nombreEstablecimiento));

        given().auth().oauth2(nuevoUsuario("dueno.ajeno.mios")).when()
                .get("/api/v1/establecimientos/mios")
                .then().statusCode(200)
                .body("id", not(hasItem(idEstablecimiento.intValue())));

        given().when().get("/api/v1/canchas?q=" + nombreCancha)
                .then().statusCode(200)
                .body("id", hasItem(idCancha.intValue()));
    }

    @Test
    void rechazaEdicionesAjenasEInvalidas() {
        String dueno = nuevoUsuario("dueno.rechaza");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idCancha = crearCancha(dueno, idEstablecimiento, "Cancha original " + System.nanoTime(),
                new BigDecimal("70.00"));
        Long otraCancha = crearCancha(dueno, idEstablecimiento, "Cancha hermana " + System.nanoTime(),
                new BigDecimal("80.00"));
        String nombreHermana = given().when().get("/api/v1/canchas/" + otraCancha)
                .then().statusCode(200).extract().path("nombre");
        String otro = nuevoUsuario("otro.rechaza");

        given().auth().oauth2(otro).contentType(ContentType.JSON)
                .body(Map.of("idDistrito", idDistrito(), "nombre", "Intruso",
                        "direccion", "Av. Intruso 1"))
                .when().put("/api/v1/establecimientos/" + idEstablecimiento)
                .then().statusCode(403);

        given().auth().oauth2(otro).contentType(ContentType.JSON)
                .body(Map.of("nombre", "Intruso", "capacidad", 10,
                        "precioBase", new BigDecimal("50.00")))
                .when().put("/api/v1/canchas/" + idCancha)
                .then().statusCode(403);

        given().auth().oauth2(otro)
                .when().delete("/api/v1/canchas/" + idCancha)
                .then().statusCode(403);

        given().auth().oauth2(otro)
                .when().delete("/api/v1/establecimientos/" + idEstablecimiento)
                .then().statusCode(403);

        given().auth().oauth2(otro)
                .when().get("/api/v1/establecimientos/" + idEstablecimiento)
                .then().statusCode(403);

        given().auth().oauth2(dueno)
                .when().get("/api/v1/establecimientos/999999999")
                .then().statusCode(404);

        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of("nombre", "Precio invalido", "capacidad", 10,
                        "precioBase", new BigDecimal("-1.00")))
                .when().put("/api/v1/canchas/" + idCancha)
                .then().statusCode(400);

        // El nombre de cancha es unico dentro del establecimiento.
        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of("nombre", nombreHermana, "capacidad", 10,
                        "precioBase", new BigDecimal("50.00")))
                .when().put("/api/v1/canchas/" + idCancha)
                .then().statusCode(409);

        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of("nombre", "Fantasma", "capacidad", 10,
                        "precioBase", new BigDecimal("50.00")))
                .when().put("/api/v1/canchas/999999999")
                .then().statusCode(404);
    }

    @Test
    void laBajaDeCanchaLaOcultaYBloqueaNuevasReservas() {
        String dueno = nuevoUsuario("dueno.baja");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        String nombre = "Cancha baja " + System.nanoTime();
        Long idCancha = crearCancha(dueno, idEstablecimiento, nombre, new BigDecimal("60.00"));

        given().auth().oauth2(dueno)
                .when().delete("/api/v1/canchas/" + idCancha)
                .then().statusCode(204);

        given().when().get("/api/v1/canchas?q=" + nombre)
                .then().statusCode(200)
                .body("id", not(hasItem(idCancha.intValue())));

        given().when().get("/api/v1/canchas/" + idCancha)
                .then().statusCode(200)
                .body("estado", equalTo("INACTIVA"));

        String cliente = nuevoUsuario("cliente.baja");
        given().auth().oauth2(cliente)
                .when().get("/api/v1/canchas/" + idCancha + "/disponibilidad?fecha="
                        + java.time.LocalDate.now().plusDays(1) + "&duracionMinutos=60")
                .then().statusCode(200)
                .body("franjas", hasSize(0));

        Instant inicio = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        given().auth().oauth2(cliente).contentType(ContentType.JSON)
                .body(Map.of("canchaId", idCancha, "inicio", inicio.toString(),
                        "fin", inicio.plus(1, ChronoUnit.HOURS).toString()))
                .when().post("/api/v1/reservas")
                .then().statusCode(409)
                .body("code", equalTo("SLOT_UNAVAILABLE"));

        given().auth().oauth2(dueno)
                .when().delete("/api/v1/canchas/" + idCancha)
                .then().statusCode(404);
    }

    @Test
    void laBajaDelEstablecimientoDesactivaSusCanchas() {
        String dueno = nuevoUsuario("dueno.baja.complejo");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        String marca = "Cancha complejo " + System.nanoTime();
        Long primera = crearCancha(dueno, idEstablecimiento, marca + " A", new BigDecimal("60.00"));
        Long segunda = crearCancha(dueno, idEstablecimiento, marca + " B", new BigDecimal("70.00"));

        given().auth().oauth2(dueno)
                .when().delete("/api/v1/establecimientos/" + idEstablecimiento)
                .then().statusCode(204);

        given().when().get("/api/v1/canchas?q=" + marca)
                .then().statusCode(200)
                .body("size()", equalTo(0));

        for (Long id : List.of(primera, segunda)) {
            given().when().get("/api/v1/canchas/" + id)
                    .then().statusCode(200)
                    .body("estado", equalTo("INACTIVA"));
        }

        given().auth().oauth2(dueno)
                .when().delete("/api/v1/establecimientos/" + idEstablecimiento)
                .then().statusCode(404);
    }

    @Test
    void noPermiteDarDeBajaConReservasVigentesPendientes() {
        String dueno = nuevoUsuario("dueno.vigente");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idCancha = crearCancha(dueno, idEstablecimiento, "Cancha reservada " + System.nanoTime(),
                new BigDecimal("60.00"));
        String cliente = nuevoUsuario("cliente.vigente");

        Instant inicio = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Long idReserva = ((Number) given().auth().oauth2(cliente).contentType(ContentType.JSON)
                .body(Map.of("canchaId", idCancha, "inicio", inicio.toString(),
                        "fin", inicio.plus(1, ChronoUnit.HOURS).toString()))
                .when().post("/api/v1/reservas")
                .then().statusCode(201)
                .body("id", notNullValue())
                .extract().path("id")).longValue();

        given().auth().oauth2(dueno)
                .when().delete("/api/v1/canchas/" + idCancha)
                .then().statusCode(409)
                .body("code", equalTo("CONFLICT"));

        given().auth().oauth2(dueno)
                .when().delete("/api/v1/establecimientos/" + idEstablecimiento)
                .then().statusCode(409)
                .body("code", equalTo("CONFLICT"));

        given().auth().oauth2(cliente)
                .when().post("/api/v1/reservas/" + idReserva + "/cancelar")
                .then().statusCode(200);

        given().auth().oauth2(dueno)
                .when().delete("/api/v1/canchas/" + idCancha)
                .then().statusCode(204);
    }

    @Test
    void comparaCanchasEnElOrdenPedido() {
        String dueno = nuevoUsuario("dueno.compara");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long primera = crearCancha(dueno, idEstablecimiento, "Compara A " + System.nanoTime(),
                new BigDecimal("55.00"));
        Long segunda = crearCancha(dueno, idEstablecimiento, "Compara B " + System.nanoTime(),
                new BigDecimal("85.00"));

        given().when().get("/api/v1/canchas/comparar?ids=" + segunda + "," + primera)
                .then().statusCode(200)
                .body("$", hasSize(2))
                .body("[0].id", equalTo(segunda.intValue()))
                .body("[0].precioBase", equalTo(85.00f))
                .body("[1].id", equalTo(primera.intValue()))
                .body("[1].precioBase", equalTo(55.00f));

        given().when().get("/api/v1/canchas/comparar?ids=" + primera)
                .then().statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));

        given().when().get("/api/v1/canchas/comparar?ids=" + primera + "," + primera)
                .then().statusCode(400);

        given().when().get("/api/v1/canchas/comparar?ids=" + primera + ",abc")
                .then().statusCode(400);

        given().when().get("/api/v1/canchas/comparar?ids=" + primera + ",999999999")
                .then().statusCode(404);
    }

    // ---------------------------------------------------------------- utilidades

    private Long crearEstablecimiento(String token) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito(),
                        "nombre", "Complejo Gestion " + System.nanoTime(),
                        "direccion", "Av. Gestion 100",
                        "referencia", "Frente al parque"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long crearCancha(String token, Long idEstablecimiento, String nombre, BigDecimal precio) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("descripcion", "Futbol 7 sintetico");
        cuerpo.put("capacidad", 14);
        cuerpo.put("precioBase", precio);
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
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
                .body(Map.of(
                        "nombres", "Usuario",
                        "apellidos", "Gestion",
                        "correo", correo,
                        "telefono", "955777" + Math.floorMod(System.nanoTime(), 1000),
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
