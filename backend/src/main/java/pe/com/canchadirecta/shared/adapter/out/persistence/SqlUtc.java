package pe.com.canchadirecta.shared.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Conversion de instantes a los literales de `DATETIME(6)` del esquema (ADR-0014).
 *
 * <p>Las columnas del esquema son `DATETIME` sin zona y el proyecto las trata como UTC en
 * todos los modulos. Escribir y leer con literales explicitos evita que Hibernate o el
 * driver reinterpreten la hora con la zona de la JVM (L-04): es la unica convencion que
 * hace que escribir un instante y volver a leerlo devuelva exactamente el mismo valor.</p>
 */
public final class SqlUtc {

    /** Formato de `DATETIME(6)` en UTC, apto como literal para MySQL. */
    private static final DateTimeFormatter LITERAL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    /** Serializa el instante como el literal UTC que MySQL guarda en un `DATETIME`. */
    public static String aLiteral(Instant instante) {
        return LocalDateTime.ofInstant(instante, ZoneOffset.UTC).format(LITERAL);
    }

    /** Interpreta un `DATETIME` leido (siempre en UTC) como el instante que representa. */
    public static Instant aInstant(Object valor) {
        if (valor instanceof Instant instante) {
            return instante;
        }
        if (valor instanceof LocalDateTime local) {
            return local.toInstant(ZoneOffset.UTC);
        }
        if (valor instanceof java.sql.Timestamp marca) {
            return marca.toInstant();
        }
        String iso = String.valueOf(valor).trim().replace(' ', 'T');
        if (!iso.endsWith("Z")) {
            iso = iso + "Z";
        }
        return Instant.parse(iso);
    }

    private SqlUtc() {
    }
}
