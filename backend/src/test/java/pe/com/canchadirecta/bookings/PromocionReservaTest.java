package pe.com.canchadirecta.bookings;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Promociones del propietario (CU04.6) y su aplicacion al precio de la reserva.
 */
@QuarkusTest
class PromocionReservaTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @Inject
    EntityManager em;

    @Test
    void elPropietarioCreaYListaSuPromocion() {
        String dueno = nuevoUsuario("dueno.promo");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idCancha = crearCancha(dueno, idEstablecimiento);

        Long id = crearPromocion(dueno, idEstablecimiento, "VERANO" + sufijo(), "PORCENTAJE",
                new BigDecimal("10.00"), 201);

        given().auth().oauth2(dueno)
                .when().get("/api/v1/establecimientos/" + idEstablecimiento + "/promociones")
                .then().statusCode(200)
                .body("id", org.hamcrest.Matchers.hasItem(id.intValue()))
                .body("canchas[0]", org.hamcrest.Matchers.hasItem(idCancha.intValue()));

        String otro = nuevoUsuario("otro.promo");
        given().auth().oauth2(otro)
                .when().get("/api/v1/establecimientos/" + idEstablecimiento + "/promociones")
                .then().statusCode(403);
        given().auth().oauth2(otro).contentType(ContentType.JSON)
                .body(cuerpoPromocion("AJENA" + sufijo(), "MONTO", new BigDecimal("5.00")))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/promociones")
                .then().statusCode(403);
    }

    @Test
    void unPorcentajeReduceElMontoDeLaReservaYDelPago() {
        String dueno = nuevoUsuario("dueno.pct");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idCancha = crearCancha(dueno, idEstablecimiento);
        String codigo = "PCT10" + sufijo();
        // La promocion empieza manana; se evalua contra la fecha de la reserva (+3 dias), no contra hoy.
        Map<String, Object> cuerpo = cuerpoPromocion(codigo, "PORCENTAJE", new BigDecimal("10.00"));
        cuerpo.put("fechaInicio", LocalDate.now().plusDays(1).toString());
        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(cuerpo)
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/promociones")
                .then().statusCode(201);

        String titular = nuevoUsuario("titular.pct");
        Long idReserva = reservar(titular, idCancha, codigo, 19);

        given().auth().oauth2(titular)
                .when().get("/api/v1/reservas/mias")
                .then().statusCode(200)
                .body("find { it.id == " + idReserva + " }.montoCancha", equalTo(70.00f))
                .body("find { it.id == " + idReserva + " }.montoDescuento", equalTo(7.00f));

        given().auth().oauth2(titular).contentType(ContentType.JSON)
                .body(Map.of("medioPago", "TARJETA"))
                .when().post("/api/v1/reservas/" + idReserva + "/pago")
                .then().statusCode(201)
                .body("monto", equalTo(63.00f))
                .body("estado", equalTo("APROBADO"));
    }

    @Test
    void unDescuentoFijoNoSuperaElPrecioDeLaCancha() {
        String dueno = nuevoUsuario("dueno.fijo");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idCancha = crearCancha(dueno, idEstablecimiento);
        String codigo = "FIJO100" + sufijo();
        crearPromocion(dueno, idEstablecimiento, codigo, "MONTO", new BigDecimal("100.00"), 201);

        String titular = nuevoUsuario("titular.fijo");
        Long idReserva = reservar(titular, idCancha, codigo, 19);

        given().auth().oauth2(titular)
                .when().get("/api/v1/reservas/mias")
                .then().statusCode(200)
                .body("find { it.id == " + idReserva + " }.montoDescuento", equalTo(70.00f));
    }

    @Test
    void respetaLosLimitesDeCanjeYLiberaUsosAlCancelar() {
        String dueno = nuevoUsuario("dueno.limite");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idCancha = crearCancha(dueno, idEstablecimiento);
        String codigo = "LIM1" + sufijo();
        Map<String, Object> cuerpo = cuerpoPromocion(codigo, "MONTO", new BigDecimal("5.00"));
        cuerpo.put("limiteUsos", 1);
        cuerpo.put("limitePorUsuario", 1);
        Long idPromocion = ((Number) given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(cuerpo)
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/promociones")
                .then().statusCode(201)
                .body("limiteUsos", equalTo(1))
                .body("limitePorUsuario", equalTo(1))
                .body("usos", equalTo(0))
                .extract().path("id")).longValue();

        String titularA = nuevoUsuario("titular.limite.a");
        String titularB = nuevoUsuario("titular.limite.b");
        Long reservaA = reservar(titularA, idCancha, codigo, 10);

        given().auth().oauth2(dueno)
                .when().get("/api/v1/establecimientos/" + idEstablecimiento + "/promociones")
                .then().statusCode(200)
                .body("find { it.id == " + idPromocion + " }.usos", equalTo(1));

        given().auth().oauth2(titularB).contentType(ContentType.JSON)
                .body(cuerpoReserva(idCancha, 12, codigo))
                .when().post("/api/v1/reservas")
                .then().statusCode(409);

        given().auth().oauth2(titularA).contentType(ContentType.JSON)
                .body(cuerpoReserva(idCancha, 14, codigo))
                .when().post("/api/v1/reservas")
                .then().statusCode(409);

        // Al cancelar la reserva, el unico uso vuelve a quedar disponible.
        given().auth().oauth2(titularA)
                .when().post("/api/v1/reservas/" + reservaA + "/cancelar")
                .then().statusCode(200);

        given().auth().oauth2(titularB).contentType(ContentType.JSON)
                .body(cuerpoReserva(idCancha, 12, codigo))
                .when().post("/api/v1/reservas")
                .then().statusCode(201);
    }

    @Test
    void aplicaElLimitePorUsuarioSinLimiteGlobal() {
        String dueno = nuevoUsuario("dueno.usuario");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idCancha = crearCancha(dueno, idEstablecimiento);
        String codigo = "USER1" + sufijo();
        Map<String, Object> cuerpo = cuerpoPromocion(codigo, "MONTO", new BigDecimal("5.00"));
        cuerpo.put("limitePorUsuario", 1);
        given().auth().oauth2(dueno).contentType(ContentType.JSON).body(cuerpo)
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/promociones")
                .then().statusCode(201);

        String titularA = nuevoUsuario("titular.usuario.a");
        String titularB = nuevoUsuario("titular.usuario.b");
        reservar(titularA, idCancha, codigo, 16);
        given().auth().oauth2(titularA).contentType(ContentType.JSON)
                .body(cuerpoReserva(idCancha, 17, codigo))
                .when().post("/api/v1/reservas")
                .then().statusCode(409);
        reservar(titularB, idCancha, codigo, 18);
    }

    @Test
    void rechazaCodigosInexistentesInactivosOAjenos() {
        String dueno = nuevoUsuario("dueno.mal");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idCancha = crearCancha(dueno, idEstablecimiento);
        String codigoApagable = "APAG" + sufijo();
        Long idPromocion = crearPromocion(dueno, idEstablecimiento, codigoApagable, "MONTO",
                new BigDecimal("10.00"), 201);
        String titular = nuevoUsuario("titular.mal");

        given().auth().oauth2(titular).contentType(ContentType.JSON)
                .body(cuerpoReserva(idCancha, 19, "NOEXISTE" + sufijo()))
                .when().post("/api/v1/reservas")
                .then().statusCode(409);

        String dueno2 = nuevoUsuario("dueno.otro");
        Long idEst2 = crearEstablecimiento(dueno2);
        Long idCancha2 = crearCancha(dueno2, idEst2);
        String codigoAjeno = "AJENA" + sufijo();
        crearPromocion(dueno2, idEst2, codigoAjeno, "MONTO", new BigDecimal("10.00"), 201);

        given().auth().oauth2(titular).contentType(ContentType.JSON)
                .body(cuerpoReserva(idCancha, 19, codigoAjeno))
                .when().post("/api/v1/reservas")
                .then().statusCode(409);

        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of("estado", "INACTIVA"))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento
                        + "/promociones/" + idPromocion + "/estado")
                .then().statusCode(200)
                .body("estado", equalTo("INACTIVA"));

        given().auth().oauth2(titular).contentType(ContentType.JSON)
                .body(cuerpoReserva(idCancha, 20, codigoApagable))
                .when().post("/api/v1/reservas")
                .then().statusCode(409);
    }

    /** Sufijo unico por ejecucion para no chocar con el codigo UNIQUE de promocion. */
    private static String sufijo() {
        return Long.toString(System.nanoTime() % 1_000_000L, 36).toUpperCase();
    }

    // ---------------------------------------------------------------- utilidades

    private Long crearPromocion(String token, Long idEstablecimiento, String codigo, String tipo,
                                BigDecimal valor, int esperado) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(cuerpoPromocion(codigo, tipo, valor))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/promociones")
                .then().statusCode(esperado)
                .body("estado", equalTo("ACTIVA"))
                .extract().path("id")).longValue();
    }

    private static Map<String, Object> cuerpoPromocion(String codigo, String tipo, BigDecimal valor) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("codigo", codigo);
        cuerpo.put("descripcion", "Promocion " + codigo);
        cuerpo.put("tipoDescuento", tipo);
        cuerpo.put("valor", valor);
        cuerpo.put("fechaInicio", LocalDate.now().toString());
        cuerpo.put("fechaFin", LocalDate.now().plusDays(30).toString());
        return cuerpo;
    }

    private Long reservar(String token, Long idCancha, String codigo, int hora) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(cuerpoReserva(idCancha, hora, codigo))
                .when().post("/api/v1/reservas")
                .then().statusCode(201)
                .extract().path("id")).longValue();
    }

    private static Map<String, Object> cuerpoReserva(Long idCancha, int hora, String codigo) {
        LocalDate fecha = LocalDate.ofInstant(Instant.now().plus(3, ChronoUnit.DAYS), ZONA);
        Instant inicio = fecha.atTime(hora, 0).atZone(ZONA).toInstant();
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("canchaId", idCancha);
        cuerpo.put("inicio", inicio.toString());
        cuerpo.put("fin", inicio.plus(1, ChronoUnit.HOURS).toString());
        cuerpo.put("codigoPromocion", codigo);
        return cuerpo;
    }

    private Long crearEstablecimiento(String token) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito(),
                        "nombre", "Complejo Promo " + System.nanoTime(),
                        "direccion", "Av. Descuento 700",
                        "referencia", "Frente al parque"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long crearCancha(String token, Long idEstablecimiento) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "nombre", "Cancha promo " + System.nanoTime(),
                        "descripcion", "Futbol 7 sintetico",
                        "capacidad", 14,
                        "precioBase", new BigDecimal("70.00")))
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
                        "apellidos", "Promo",
                        "correo", correo,
                        "telefono", "933444" + Math.floorMod(System.nanoTime(), 1000),
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
