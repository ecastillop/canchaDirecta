package pe.com.canchadirecta.platform;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Administracion de cocheras y promociones (CU06.5).
 */
@QuarkusTest
class AdminCocherasPromocionesTest {

    @Inject
    EntityManager em;

    @Test
    void unUsuarioNormalNoAccedeAAdministracion() {
        String usuario = nuevoUsuario("user.cp");
        given().auth().oauth2(usuario)
                .when().get("/api/v1/admin/promociones")
                .then().statusCode(403);
        given().auth().oauth2(usuario)
                .when().get("/api/v1/admin/cocheras")
                .then().statusCode(403);
    }

    @Test
    void creaListaYDesactivaUnaPromocion() {
        String admin = nuevoAdmin("admin.promo");
        String dueno = nuevoUsuario("dueno.promo");
        Long idCancha = crearCancha(dueno, crearEstablecimiento(dueno));
        // El codigo es unico en la base compartida (Modo B): se usa un sufijo amplio para no
        // colisionar con las promociones que dejan otras ejecuciones.
        String codigo = "VERANO" + Math.floorMod(System.nanoTime(), 1_000_000_000L);

        Long id = ((Number) given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(promocion(codigo, "PORCENTAJE", new BigDecimal("15.00"), List.of(idCancha)))
                .when().post("/api/v1/admin/promociones")
                .then().statusCode(201)
                .body("estado", equalTo("ACTIVA"))
                .body("canchas", hasSize(1))
                .body("canchas[0]", equalTo(idCancha.intValue()))
                .extract().path("id")).longValue();

        List<Map<String, Object>> promociones = given().auth().oauth2(admin)
                .when().get("/api/v1/admin/promociones?estado=ACTIVA&limite=200")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        assertNotNull(promociones.stream()
                .filter(p -> ((Number) p.get("id")).longValue() == id)
                .findFirst().orElse(null));

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "INACTIVA"))
                .when().post("/api/v1/admin/promociones/" + id + "/estado")
                .then().statusCode(200)
                .body("estado", equalTo("INACTIVA"));

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "INACTIVA"))
                .when().post("/api/v1/admin/promociones/" + id + "/estado")
                .then().statusCode(409);

        List<Map<String, Object>> auditoria = given().auth().oauth2(admin)
                .when().get("/api/v1/admin/auditoria?limite=10")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        assertNotNull(auditoria.stream()
                .filter(a -> "PROMOCION_INACTIVA".equals(a.get("accion")))
                .findFirst().orElse(null));
    }

    @Test
    void validaElAltaDePromociones() {
        String admin = nuevoAdmin("admin.promo2");
        String dueno = nuevoUsuario("dueno.promo2");
        Long idCancha = crearCancha(dueno, crearEstablecimiento(dueno));
        String codigo = "DUP" + Math.floorMod(System.nanoTime(), 100_000);

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(promocion(codigo, "MONTO", new BigDecimal("10.00"), List.of(idCancha)))
                .when().post("/api/v1/admin/promociones")
                .then().statusCode(201);

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(promocion(codigo, "MONTO", new BigDecimal("10.00"), List.of(idCancha)))
                .when().post("/api/v1/admin/promociones")
                .then().statusCode(409);

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(promocion("TIPO" + Math.floorMod(System.nanoTime(), 100_000),
                        "GRATIS", new BigDecimal("10.00"), List.of()))
                .when().post("/api/v1/admin/promociones")
                .then().statusCode(400);

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(promocion("PCT" + Math.floorMod(System.nanoTime(), 100_000),
                        "PORCENTAJE", new BigDecimal("150.00"), List.of()))
                .when().post("/api/v1/admin/promociones")
                .then().statusCode(400);

        Map<String, Object> invertida = promocion("FECHA" + Math.floorMod(System.nanoTime(), 100_000),
                "MONTO", new BigDecimal("10.00"), List.of());
        invertida.put("fechaInicio", LocalDate.now().plusDays(10).toString());
        invertida.put("fechaFin", LocalDate.now().toString());
        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(invertida)
                .when().post("/api/v1/admin/promociones")
                .then().statusCode(400);

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(promocion("CANCHA" + Math.floorMod(System.nanoTime(), 100_000),
                        "MONTO", new BigDecimal("10.00"), List.of(999_999_999L)))
                .when().post("/api/v1/admin/promociones")
                .then().statusCode(404);
    }

    @Test
    void listaYDesactivaUnaCochera() {
        String admin = nuevoAdmin("admin.cochera");
        String dueno = nuevoUsuario("dueno.cochera");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idCochera = crearCochera(dueno, idEstablecimiento);

        given().auth().oauth2(admin)
                .when().get("/api/v1/admin/cocheras?estado=ACTIVA&limite=200")
                .then().statusCode(200)
                .body("id", hasItem(idCochera.intValue()))
                .body("estado", org.hamcrest.Matchers.everyItem(equalTo("ACTIVA")));

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "INACTIVA"))
                .when().post("/api/v1/admin/cocheras/" + idCochera + "/estado")
                .then().statusCode(200)
                .body("estado", equalTo("INACTIVA"));

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "INACTIVA"))
                .when().post("/api/v1/admin/cocheras/" + idCochera + "/estado")
                .then().statusCode(409);

        given().auth().oauth2(admin).contentType(ContentType.JSON)
                .body(Map.of("estado", "ACTIVA"))
                .when().post("/api/v1/admin/cocheras/999999999/estado")
                .then().statusCode(404);
    }

    // ---------------------------------------------------------------- utilidades

    private static Map<String, Object> promocion(String codigo, String tipo, BigDecimal valor,
                                                 List<Long> canchas) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("codigo", codigo);
        cuerpo.put("descripcion", "Promocion de prueba " + codigo);
        cuerpo.put("tipoDescuento", tipo);
        cuerpo.put("valor", valor);
        cuerpo.put("fechaInicio", LocalDate.now().toString());
        cuerpo.put("fechaFin", LocalDate.now().plusDays(30).toString());
        cuerpo.put("canchas", canchas);
        return cuerpo;
    }

    private Long crearEstablecimiento(String token) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito(),
                        "nombre", "Complejo CP " + System.nanoTime(),
                        "direccion", "Av. Promocion 600",
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

    private Long crearCochera(String token, Long idEstablecimiento) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "nombre", "Cochera " + System.nanoTime(),
                        "capacidad", 10,
                        "tarifaHora", new BigDecimal("5.00")))
                .when().post("/api/v1/establecimientos/" + idEstablecimiento + "/cocheras")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long idDistrito() {
        Object valor = QuarkusTransaction.requiringNew().call(() ->
                em.createNativeQuery("SELECT MIN(id_distrito) FROM distrito").getSingleResult());
        return ((Number) valor).longValue();
    }

    private String nuevoAdmin(String prefijo) {
        String correo = prefijo + "." + System.nanoTime() + "@canchadirecta.pe";
        registrar(correo, "Admin", "Catalogo", "944555" + Math.floorMod(System.nanoTime(), 1000));
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "INSERT INTO usuario_rol (id_usuario, id_rol) "
                                + "SELECT u.id_usuario, r.id_rol FROM usuario u, rol r "
                                + "WHERE u.correo = :correo AND r.nombre = 'PLATFORM_ADMIN'")
                .setParameter("correo", correo).executeUpdate());
        String token = iniciarSesion(correo);
        given().auth().oauth2(token)
                .when().get("/api/v1/users/me")
                .then().statusCode(200)
                .body("roles", hasItem("PLATFORM_ADMIN"));
        return token;
    }

    private String nuevoUsuario(String prefijo) {
        String correo = prefijo + "." + System.nanoTime() + "@canchadirecta.pe";
        registrar(correo, "Dueno", "Catalogo", "955444" + Math.floorMod(System.nanoTime(), 1000));
        return iniciarSesion(correo);
    }

    private static void registrar(String correo, String nombres, String apellidos, String telefono) {
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
    }

    private static String iniciarSesion(String correo) {
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
