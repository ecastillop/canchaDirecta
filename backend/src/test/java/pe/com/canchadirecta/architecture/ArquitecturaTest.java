package pe.com.canchadirecta.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica las reglas de dependencia de ADR-0001 sin dependencias extra: lee el codigo fuente y
 * falla si una capa rompe la direccion de las dependencias.
 *
 * <p>Quarkus es el framework del backend; la regla aplica a {@code domain}, que debe ser Java puro.
 */
class ArquitecturaTest {

    private static final String PAQUETE_BASE = "pe/com/canchadirecta";
    private static final Set<String> FRAMEWORKS =
            Set.of("jakarta.", "io.quarkus.", "org.hibernate.", "org.jboss.");

    @Test
    void domainNoImportaElFramework() throws IOException {
        List<String> violaciones = new ArrayList<>();
        for (Path fuente : fuentes()) {
            String relativa = relativa(fuente);
            if (!relativa.contains("/domain/")) {
                continue;
            }
            for (String importacion : importaciones(fuente)) {
                if (FRAMEWORKS.stream().anyMatch(importacion::startsWith)) {
                    violaciones.add(relativa + " -> " + importacion);
                }
            }
        }
        assertTrue(violaciones.isEmpty(),
                "domain debe ser Java puro (sin JPA/REST/Quarkus):\n" + String.join("\n", violaciones));
    }

    @Test
    void domainNoDependeDeOtrosModulos() throws IOException {
        List<String> violaciones = new ArrayList<>();
        for (Path fuente : fuentes()) {
            String relativa = relativa(fuente);
            if (!relativa.contains("/domain/")) {
                continue;
            }
            String modulo = relativa.substring(0, relativa.indexOf('/'));
            for (String importacion : importaciones(fuente)) {
                String destino = moduloDe(importacion);
                if (destino != null && !destino.equals(modulo) && !destino.equals("shared")) {
                    violaciones.add(relativa + " -> " + importacion);
                }
            }
        }
        assertTrue(violaciones.isEmpty(),
                "domain no debe importar otros modulos (usar IDs y puertos):\n" + String.join("\n", violaciones));
    }

    @Test
    void ningunModuloImportaPersistenciaDeOtro() throws IOException {
        List<String> violaciones = new ArrayList<>();
        for (Path fuente : fuentes()) {
            String relativa = relativa(fuente);
            String modulo = relativa.substring(0, relativa.indexOf('/'));
            for (String importacion : importaciones(fuente)) {
                String destino = moduloDe(importacion);
                if (destino != null
                        && !destino.equals(modulo)
                        && !destino.equals("shared")
                        && importacion.contains(".adapter.out.persistence.")) {
                    violaciones.add(relativa + " -> " + importacion);
                }
            }
        }
        assertTrue(violaciones.isEmpty(),
                "un modulo no debe importar entidades JPA de otro:\n" + String.join("\n", violaciones));
    }

    private static List<Path> fuentes() throws IOException {
        Path raiz = raiz();
        try (Stream<Path> caminos = Files.walk(raiz)) {
            return caminos.filter(camino -> camino.toString().endsWith(".java")).toList();
        }
    }

    private static Path raiz() {
        for (String candidata : List.of("src/main/java/" + PAQUETE_BASE, "backend/src/main/java/" + PAQUETE_BASE)) {
            Path ruta = Path.of(candidata);
            if (Files.isDirectory(ruta)) {
                return ruta;
            }
        }
        throw new IllegalStateException(
                "No se encontro el codigo fuente; ejecutar las pruebas desde backend/ o la raiz del proyecto.");
    }

    private static String relativa(Path fuente) {
        return raiz().relativize(fuente).toString().replace('\\', '/');
    }

    private static List<String> importaciones(Path fuente) throws IOException {
        return Files.readAllLines(fuente).stream()
                .map(String::trim)
                .filter(linea -> linea.startsWith("import "))
                .map(linea -> linea.substring("import ".length()).replace(";", "").trim())
                .filter(linea -> !linea.startsWith("static "))
                .toList();
    }

    /** Devuelve el modulo destino de una importacion del proyecto, o {@code null} si es externa. */
    private static String moduloDe(String importacion) {
        String prefijo = "pe.com.canchadirecta.";
        if (!importacion.startsWith(prefijo)) {
            return null;
        }
        String resto = importacion.substring(prefijo.length());
        int punto = resto.indexOf('.');
        return punto > 0 ? resto.substring(0, punto) : null;
    }
}
