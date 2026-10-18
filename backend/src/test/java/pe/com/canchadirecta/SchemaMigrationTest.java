package pe.com.canchadirecta;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que Flyway aplica las migraciones sobre el MySQL real al arrancar la
 * aplicacion y que existen las 28 tablas fuente + las extensiones geograficas.
 * Requiere variables de entorno de conexion (DB_*), ver .env.example.
 */
@QuarkusTest
class SchemaMigrationTest {

    private static final Set<String> SOURCE_TABLES = Set.of(
            "distrito", "usuario", "rol", "usuario_rol", "propietario", "establecimiento",
            "deporte", "caracteristica", "cancha", "cancha_deporte", "cancha_caracteristica",
            "foto_cancha", "horario_cancha", "bloqueo_cancha", "promocion", "cancha_promocion",
            "reserva", "historial_reserva", "cochera", "reserva_cochera", "pago", "reembolso",
            "liquidacion", "detalle_liquidacion", "calificacion", "notificacion", "reclamo", "auditoria"
    );

    @Inject
    DataSource dataSource;

    @Test
    void las28TablasFuenteYExtensionesExisten() throws Exception {
        Set<String> tables = new HashSet<>();
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")) {
            while (rs.next()) {
                tables.add(rs.getString(1).toLowerCase());
            }
        }
        for (String t : SOURCE_TABLES) {
            assertTrue(tables.contains(t), "Falta la tabla fuente: " + t);
        }
        // Extensiones geograficas (primer avance)
        assertTrue(tables.contains("departamento"), "Falta tabla departamento");
        assertTrue(tables.contains("provincia"), "Falta tabla provincia");
        // Flyway debe haber creado su historial
        assertTrue(tables.contains("flyway_schema_history"), "Falta flyway_schema_history");
    }
}
