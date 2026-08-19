package com.renaser.ai.ai_engine.integracion.soporte;

// Jackson 2, que es el que usan las pruebas de integración (el código de producción
// va con Jackson 3, el de Spring Boot 4).
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Arma el cuerpo con el que un test responde una pregunta, según el formato del banco v3.
 *
 * <p>Antes bastaba con mandar la primera opción: todas las preguntas se respondían igual. Los
 * formatos del v3 no — un SJT-R califica cada opción del 1 al 5, un EF-4 marca dos, un SEC
 * ordena los cinco pasos — y el validador rechaza lo que no tenga su forma.
 *
 * <p>Responde <b>bien</b>, no óptimamente: lo que estos tests comprueban es el recorrido, no
 * la nota. Para probar la nota están las pruebas de {@code FormulasBancoV3}.
 */
public final class RespuestaV3 {

    private RespuestaV3() {
    }

    /** El cuerpo JSON con el que responder esa pregunta, sea del formato que sea. */
    public static String para(JsonNode pregunta) {
        String tipo = pregunta.has("tipo") ? pregunta.get("tipo").asText() : "";
        List<Long> ids = new ArrayList<>();
        JsonNode opciones = pregunta.get("opciones");
        if (opciones != null) {
            opciones.forEach(o -> ids.add(o.get("id").asLong()));
        }

        return switch (tipo) {
            case "EF-4" -> ids.size() >= 2
                    ? """
                      {"detalle":{"mas":%d,"menos":%d},"segundos":30}"""
                      .formatted(ids.get(0), ids.get(1))
                    : texto();
            case "SJT-R" -> {
                StringJoiner califica = new StringJoiner(",", "{", "}");
                ids.forEach(id -> califica.add("\"%d\":3".formatted(id)));
                yield ids.isEmpty() ? texto()
                        : """
                          {"detalle":{"calificaciones":%s},"segundos":30}""".formatted(califica);
            }
            case "SEC" -> {
                StringJoiner orden = new StringJoiner(",", "[", "]");
                ids.forEach(id -> orden.add(String.valueOf(id)));
                yield ids.isEmpty() ? texto()
                        : """
                          {"detalle":{"orden":%s},"segundos":30}""".formatted(orden);
            }
            case "INV", "DE" -> """
                    {"detalle":{"marcadas":[%s]},"segundos":30}"""
                    .formatted(ids.isEmpty() ? "" : String.valueOf(ids.get(0)));
            case "CD" -> """
                    {"detalle":{"campos":{"1":"reclamo de cliente","2":"plan de mejora escrito"}},\
                    "segundos":30}""";
            default -> ids.isEmpty()
                    ? texto()
                    : """
                      {"opcionId":%d,"segundos":30}""".formatted(ids.get(0));
        };
    }

    private static String texto() {
        return """
               {"texto":"Un caso concreto: reduje el tiempo de cierre midiendo antes y después, \
               y dejé el proceso documentado","segundos":90}""";
    }
}
