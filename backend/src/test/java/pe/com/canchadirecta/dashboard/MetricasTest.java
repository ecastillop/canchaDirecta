package pe.com.canchadirecta.dashboard;

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
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

/** Metricas del panel del propietario: reservas, ingresos, serie y proximas reservas. */
@QuarkusTest
class MetricasTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @Inject
    EntityManager em;

    @Test
    void resumeReservasIngresosYOcupacionDelEstablecimiento() {
        String dueno = nuevoUsuario("dueno.metricas");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idCancha = crearCancha(dueno, idEstablecimiento);

        String cliente = nuevoUsuario("cliente.metricas");
        Long confirmada = reservar(cliente, idCancha, 10);
        Long pendiente = reservar(cliente, idCancha, 12);

        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .when().post("/api/v1/reservas/" + confirmada + "/confirmar")
                .then().statusCode(200);

        // Las metricas agregan por fecha de juego: se mueven dos reservas al dia anterior.
        LocalDate ayer = LocalDate.now(ZONA).minusDays(1);
        moverFranja(confirmada, ayer.atTime(10, 0).atZone(ZONA).toInstant(), 60);
        moverFranja(pendiente, ayer.atTime(12, 0).atZone(ZONA).toInstant(), 60);
        Long proxima = reservar(cliente, idCancha, 14);

        given().auth().oauth2(dueno)
                .when().get("/api/v1/establecimientos/" + idEstablecimiento + "/metricas?dias=7")
                .then().statusCode(200)
                .body("idEstablecimiento", equalTo(idEstablecimiento.intValue()))
                .body("dias", equalTo(7))
                .body("resumen.totalReservas", equalTo(3))
                .body("resumen.confirmadas", equalTo(1))
                .body("resumen.pendientes", equalTo(2))
                .body("resumen.ingresos", equalTo(70.00f))
                .body("resumen.horasReservadas", equalTo(2.0f))
                .body("resumen.ocupacionPorcentaje", greaterThan(0.0f))
                .body("resumen.canchasActivas", equalTo(1))
                .body("serie", hasSize(7))
                .body("canchas", hasSize(1))
                .body("canchas[0].idCancha", equalTo(idCancha.intValue()))
                .body("canchas[0].reservas", equalTo(3))
                .body("proximas", hasSize(1))
                .body("proximas[0].idReserva", equalTo(proxima.intValue()));

        given().auth().oauth2(dueno)
                .when().get("/api/v1/establecimientos/" + idEstablecimiento + "/metricas?dias=7")
                .then().statusCode(200)
                .body("proximas[0].idReserva", greaterThan(0))
                .body("proximas[0].cliente", org.hamcrest.Matchers.containsString("Usuario"))
                .body("proximas[0].cancha", org.hamcrest.Matchers.containsString("Cancha metrica"));

        String intruso = nuevoUsuario("intruso.metricas");
        given().auth().oauth2(intruso)
                .when().get("/api/v1/establecimientos/" + idEstablecimiento + "/metricas")
                .then().statusCode(403);

        given().when().get("/api/v1/establecimientos/" + idEstablecimiento + "/metricas")
                .then().statusCode(401);

        // La ventana sin reservas devuelve ceros y una serie completa.
        given().auth().oauth2(dueno)
                .when().get("/api/v1/establecimientos/" + idEstablecimiento + "/metricas?dias=400")
                .then().statusCode(200)
                .body("dias", equalTo(365))
                .body("serie", hasSize(365))
                .body("resumen.totalReservas", greaterThanOrEqualTo(3));
    }

    /** Reubica una reserva en el pasado para probar la ventana de metricas. */
    private void moverFranja(Long idReserva, Instant inicio, long minutos) {
        String ini = pe.com.canchadirecta.shared.adapter.out.persistence.SqlUtc.aLiteral(inicio);
        String fin = pe.com.canchadirecta.shared.adapter.out.persistence.SqlUtc.aLiteral(
                inicio.plus(minutos, ChronoUnit.MINUTES));
        QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                        "UPDATE reserva SET inicio = :inicio, fin = :fin WHERE id_reserva = :id")
                .setParameter("inicio", ini)
                .setParameter("fin", fin)
                .setParameter("id", idReserva)
                .executeUpdate());
    }

    private Long reservar(String token, Long idCancha, int hora) {
        LocalDate fecha = LocalDate.ofInstant(Instant.now().plus(2, ChronoUnit.DAYS), ZONA);
        Instant inicio = fecha.atTime(hora, 0).atZone(ZONA).toInstant();
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("canchaId", idCancha);
        cuerpo.put("inicio", inicio.toString());
        cuerpo.put("fin", inicio.plus(1, ChronoUnit.HOURS).toString());
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(cuerpo)
                .when().post("/api/v1/reservas")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long crearEstablecimiento(String token) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito(),
                        "nombre", "Complejo Metricas " + System.nanoTime(),
                        "direccion", "Av. Metricas 100",
                        "referencia", "Ingreso principal"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long crearCancha(String token, Long idEstablecimiento) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "nombre", "Cancha metrica " + System.nanoTime(),
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
                        "apellidos", "Metricas",
                        "correo", correo,
                        "telefono", "966777" + Math.floorMod(System.nanoTime(), 1000),
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
        return String.valueOf(java.util.concurrent.ThreadLocalRandom.current()
                .nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
    }
}
