package pe.com.canchadirecta.helpcenter;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

/** Centro de ayuda publico: categorias, preguntas y busqueda. */
@QuarkusTest
class AyudaTest {

    @Test
    void exponeLasCategoriasConSusPreguntas() {
        given().when().get("/api/v1/ayuda")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(5))
                .body("find { it.slug == 'pagos' }.nombre", equalTo("Pagos y reembolsos"))
                .body("find { it.slug == 'pagos' }.articulos.size()", greaterThanOrEqualTo(3))
                .body("find { it.slug == 'reservas' }.articulos.size()", greaterThanOrEqualTo(3));
    }

    @Test
    void buscaPorTextoEnPreguntaYRespuesta() {
        given().when().get("/api/v1/ayuda?texto=reembolso")
                .then().statusCode(200)
                .body("$", not(empty()))
                .body("find { it.slug == 'pagos' }.articulos.pregunta",
                        org.hamcrest.Matchers.hasItem("¿Cuándo recibo un reembolso?"));

        given().when().get("/api/v1/ayuda?texto=zzzz-no-existe")
                .then().statusCode(200)
                .body("$", hasSize(0));
    }
}
