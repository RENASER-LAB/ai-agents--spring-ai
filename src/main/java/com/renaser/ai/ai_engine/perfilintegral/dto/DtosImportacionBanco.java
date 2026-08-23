package com.renaser.ai.ai_engine.perfilintegral.dto;

import java.math.BigDecimal;
import java.util.List;

// Lo que viaja entre el lector del Excel y el servicio de importación, y lo que el panel
// recibe de vuelta. Los records Fila* son el archivo ya digerido: texto plano con la fila
// de origen a cuestas, para que cualquier error posterior pueda decir de dónde salió.
//
// A diferencia de DtosBancoPreguntas, aquí no hay anotaciones de validación: el lector
// valida él mismo recolectando TODOS los errores en vez de parar en el primero, porque a
// quien sube un archivo de 190 preguntas le sirve la lista completa, no un rechazo por
// entrega.
public final class DtosImportacionBanco {

    private DtosImportacionBanco() {}

    /** Un problema del archivo, con la hoja y la fila donde está. */
    public record ErrorDeImportacion(String hoja, int fila, String mensaje) {}

    /** Una fila de la hoja Preguntas, tal como se leyó. */
    public record FilaPregunta(
            int fila,
            String codigo,
            String tipo,
            String enunciado,
            String situacion,
            Short peso,
            boolean esEliminatoria,
            /** Los códigos de dimensión ya resueltos contra el catálogo («Qué mide»). */
            List<String> dimensiones,
            Short casosPedidos,
            String logicaInterna) {}

    /** Una fila de la hoja Opciones. La letra no viene: se sintetiza a, b, c… al insertar. */
    public record FilaOpcion(
            int fila,
            String codigoPregunta,
            String texto,
            Double puntaje,
            BigDecimal valor,
            boolean esDistractor,
            Short ordenCorrecto) {}

    /** Una fila de la hoja Campos de caso (CD). El orden es el de las filas. */
    public record FilaCampoCaso(
            int fila,
            String codigoPregunta,
            String etiqueta,
            String validacion) {}

    /** Una fila de la hoja Rangos (V). El orden es el de las filas. */
    public record FilaRango(
            int fila,
            String codigoPregunta,
            String condicion,
            BigDecimal puntaje,
            boolean generaBandera) {}

    /** Una fila de la hoja Pares, todavía con códigos: los ids llegan al insertar. */
    public record FilaPar(
            int fila,
            String codigoA,
            String codigoB,
            BigDecimal penalizacionPorcentaje,
            Short separacionMinimaItems,
            String condicion) {}

    /** El archivo entero, digerido. Si {@code errores} trae algo, lo demás no se usa. */
    public record BancoLeido(
            List<FilaPregunta> preguntas,
            List<FilaOpcion> opciones,
            List<FilaCampoCaso> camposCaso,
            List<FilaRango> rangos,
            List<FilaPar> pares,
            List<ErrorDeImportacion> errores) {}

    /** Lo que el panel recibe tras importar: el borrador creado y sus conteos. */
    public record ResultadoImportacion(
            Long versionBancoId,
            String etiqueta,
            int preguntas,
            int opciones,
            int camposCaso,
            int rangos,
            int pares,
            int dimensionesAsignadas) {}
}
