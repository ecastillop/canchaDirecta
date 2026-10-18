package pe.com.canchadirecta.venues;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Catalogo avanzado (F02/F03): distritos, deportes, ficha con atributos, filtros y gestion de
 * deportes/caracteristicas/fotos por el propietario.
 */
@QuarkusTest
class CatalogoCanchaTest {

    @Inject
    EntityManager em;

    @Test
    void exponeLosCatalogosPublicos() {
        given().when().get("/api/v1/ubicaciones/distritos?limite=20")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("[0].provincia", equalTo("Lima"))
                .body("[0].departamento", equalTo("Lima"));

        given().when().get("/api/v1/deportes")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));

        given().when().get("/api/v1/caracteristicas")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void elListadoPublicoIncluyeDistritoFotoServiciosYReputacion() {
        String dueno = nuevoUsuario("dueno.listado");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        String nombre = "Cancha listado " + System.nanoTime();
        Long idCancha = crearCancha(dueno, idEstablecimiento, nombre, new BigDecimal("55.00"));
        Long idDeporte = primerId("/api/v1/deportes");
        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of("ids", List.of(idDeporte)))
                .when().put("/api/v1/canchas/" + idCancha + "/deportes")
                .then().statusCode(200);
        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of("url", "https://picsum.photos/seed/listado/800/600", "orden", 1, "principal", true))
                .when().post("/api/v1/canchas/" + idCancha + "/fotos")
                .then().statusCode(201);

        given().when().get("/api/v1/canchas?q=" + nombre + "&limite=1")
                .then().statusCode(200)
                .body("[0].id", equalTo(idCancha.intValue()))
                .body("[0].distrito", equalTo("Lima"))
                .body("[0].fotoPrincipal", org.hamcrest.Matchers.containsString("picsum"))
                .body("[0].totalCalificaciones", equalTo(0))
                .body("[0].promedioCalificacion", equalTo(0.0f))
                .body("[0].deportes", hasSize(1));
    }

    @Test
    void elClienteGestionaSusFavoritos() {
        String cliente = nuevoUsuario("cliente.favoritos");
        String dueno = nuevoUsuario("dueno.favoritos");
        Long idCancha = crearCancha(dueno, crearEstablecimiento(dueno),
                "Cancha favorita " + System.nanoTime(), new BigDecimal("60.00"));

        given().auth().oauth2(cliente).when().post("/api/v1/favoritos/" + idCancha)
                .then().statusCode(204);
        given().auth().oauth2(cliente).when().get("/api/v1/favoritos")
                .then().statusCode(200).body("$", hasItem(idCancha.intValue()));
        given().auth().oauth2(cliente).when().delete("/api/v1/favoritos/" + idCancha)
                .then().statusCode(204);
        given().auth().oauth2(cliente).when().get("/api/v1/favoritos")
                .then().statusCode(200).body("$", org.hamcrest.Matchers.not(hasItem(idCancha.intValue())));
        given().when().get("/api/v1/favoritos").then().statusCode(401);
    }

    @Test
    void elPropietarioCompletaLaFichaYSeVeEnElDetalle() {
        String dueno = nuevoUsuario("dueno.ficha");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idCancha = crearCancha(dueno, idEstablecimiento, "Cancha ficha " + System.nanoTime(),
                new BigDecimal("70.00"));
        Long idDeporte = primerId("/api/v1/deportes");
        Long idCaracteristica = primerId("/api/v1/caracteristicas");

        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of("ids", List.of(idDeporte)))
                .when().put("/api/v1/canchas/" + idCancha + "/deportes")
                .then().statusCode(200)
                .body("deportes", hasSize(1))
                .body("deportes[0].id", equalTo(idDeporte.intValue()));

        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of("ids", List.of(idCaracteristica)))
                .when().put("/api/v1/canchas/" + idCancha + "/caracteristicas")
                .then().statusCode(200)
                .body("caracteristicas", hasSize(1));

        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of("url", "https://cdn.canchadirecta.pe/foto-" + idCancha + ".jpg",
                        "orden", 1, "principal", true))
                .when().post("/api/v1/canchas/" + idCancha + "/fotos")
                .then().statusCode(201)
                .body("principal", equalTo(true));

        given().when().get("/api/v1/canchas/" + idCancha)
                .then().statusCode(200)
                .body("id", equalTo(idCancha.intValue()))
                .body("establecimiento", org.hamcrest.Matchers.containsString("Complejo Catalogo"))
                .body("distrito.departamento", equalTo("Lima"))
                .body("deportes", hasSize(1))
                .body("caracteristicas", hasSize(1))
                .body("fotos", hasSize(1))
                .body("fotos[0].principal", equalTo(true))
                .body("reputacion.total", equalTo(0));

        String otro = nuevoUsuario("otro.ficha");
        given().auth().oauth2(otro).contentType(ContentType.JSON)
                .body(Map.of("ids", List.of(idDeporte)))
                .when().put("/api/v1/canchas/" + idCancha + "/deportes")
                .then().statusCode(403);
    }

    @Test
    void laFichaDelEspacioAgrupaCanchasServiciosFotosYReputacion() {
        String dueno = nuevoUsuario("dueno.espacio");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idCancha = crearCancha(dueno, idEstablecimiento, "Cancha espacio A " + System.nanoTime(),
                new BigDecimal("50.00"));
        Long idSegunda = crearCancha(dueno, idEstablecimiento, "Cancha espacio B " + System.nanoTime(),
                new BigDecimal("80.00"));
        Long idDeporte = primerId("/api/v1/deportes");
        Long idCaracteristica = primerId("/api/v1/caracteristicas");

        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of("ids", List.of(idDeporte)))
                .when().put("/api/v1/canchas/" + idCancha + "/deportes")
                .then().statusCode(200);
        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of("ids", List.of(idCaracteristica)))
                .when().put("/api/v1/canchas/" + idCancha + "/caracteristicas")
                .then().statusCode(200);
        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of("url", "https://cdn.canchadirecta.pe/espacio-" + idCancha + ".jpg",
                        "orden", 1, "principal", true))
                .when().post("/api/v1/canchas/" + idCancha + "/fotos")
                .then().statusCode(201);

        given().when().get("/api/v1/establecimientos/" + idEstablecimiento + "/ficha")
                .then().statusCode(200)
                .body("id", equalTo(idEstablecimiento.intValue()))
                .body("distrito.departamento", equalTo("Lima"))
                .body("estado", equalTo("ACTIVO"))
                .body("canchas", hasSize(2))
                .body("canchas[0].id", equalTo(idCancha.intValue()))
                .body("canchas[0].deportes", hasSize(1))
                .body("canchas[0].fotoPrincipal", org.hamcrest.Matchers.containsString("espacio"))
                .body("servicios", hasSize(1))
                .body("fotos", hasSize(1))
                .body("reputacion.total", equalTo(0))
                .body("precioDesde", equalTo(50.0f))
                .body("canchas[1].id", equalTo(idSegunda.intValue()));

        given().when().get("/api/v1/establecimientos/999999999/ficha")
                .then().statusCode(404);
    }

    @Test
    void filtraPorDeportePrecioYTexto() {
        String dueno = nuevoUsuario("dueno.filtro");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idDeporte = primerId("/api/v1/deportes");
        String marca = "Filtro" + System.nanoTime();
        Long idCancha = crearCancha(dueno, idEstablecimiento, marca, new BigDecimal("60.00"));
        Long idOtra = crearCancha(dueno, idEstablecimiento, "Otra" + System.nanoTime(),
                new BigDecimal("120.00"));

        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of("ids", List.of(idDeporte)))
                .when().put("/api/v1/canchas/" + idCancha + "/deportes")
                .then().statusCode(200);

        given().when().get("/api/v1/canchas?idDeporte=" + idDeporte + "&precioMax=100&q=" + marca)
                .then().statusCode(200)
                .body("id", hasItem(idCancha.intValue()))
                .body("id", org.hamcrest.Matchers.not(hasItem(idOtra.intValue())));

        given().when().get("/api/v1/canchas?q=" + System.nanoTime())
                .then().statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    void validaDeportesYFotos() {
        String dueno = nuevoUsuario("dueno.valida");
        Long idEstablecimiento = crearEstablecimiento(dueno);
        Long idCancha = crearCancha(dueno, idEstablecimiento, "Cancha valida " + System.nanoTime(),
                new BigDecimal("70.00"));

        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of("ids", List.of(999_999_999L)))
                .when().put("/api/v1/canchas/" + idCancha + "/deportes")
                .then().statusCode(400);

        Long idFoto = ((Number) given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of("url", "https://cdn.canchadirecta.pe/f1.jpg", "orden", 1, "principal", true))
                .when().post("/api/v1/canchas/" + idCancha + "/fotos")
                .then().statusCode(201)
                .extract().path("id")).longValue();

        given().auth().oauth2(dueno).contentType(ContentType.JSON)
                .body(Map.of("url", "https://cdn.canchadirecta.pe/f2.jpg", "orden", 1, "principal", false))
                .when().post("/api/v1/canchas/" + idCancha + "/fotos")
                .then().statusCode(409);

        given().auth().oauth2(dueno)
                .when().delete("/api/v1/canchas/" + idCancha + "/fotos/" + idFoto)
                .then().statusCode(204);

        given().when().get("/api/v1/canchas/" + idCancha)
                .then().statusCode(200)
                .body("fotos", hasSize(0));

        given().auth().oauth2(dueno)
                .when().delete("/api/v1/canchas/" + idCancha + "/fotos/" + idFoto)
                .then().statusCode(404);
    }

    // ---------------------------------------------------------------- utilidades

    private Long primerId(String ruta) {
        List<Map<String, Object>> items = given().when().get(ruta)
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
        assertNotNull(items);
        return ((Number) items.get(0).get("id")).longValue();
    }

    private Long crearEstablecimiento(String token) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "documentoIdentidad", documentoUnico(),
                        "idDistrito", idDistrito(),
                        "nombre", "Complejo Catalogo " + System.nanoTime(),
                        "direccion", "Av. Catalogo 800",
                        "referencia", "Frente al parque"))
                .when().post("/api/v1/establecimientos")
                .then().statusCode(201).extract().path("id")).longValue();
    }

    private Long crearCancha(String token, Long idEstablecimiento, String nombre, BigDecimal precio) {
        return ((Number) given().auth().oauth2(token).contentType(ContentType.JSON)
                .body(Map.of(
                        "nombre", nombre,
                        "descripcion", "Futbol 7 sintetico",
                        "capacidad", 14,
                        "precioBase", precio))
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
                        "apellidos", "Catalogo",
                        "correo", correo,
                        "telefono", "955666" + Math.floorMod(System.nanoTime(), 1000),
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
