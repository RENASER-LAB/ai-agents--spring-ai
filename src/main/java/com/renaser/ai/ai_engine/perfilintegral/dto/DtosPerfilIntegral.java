package com.renaser.ai.ai_engine.perfilintegral.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Lo que el panel enseña del Perfil Integral de un candidato.
 *
 * <p>Es de <b>solo lectura</b>: aquí no se ajusta ninguna nota. Cambiar una nota tiene su
 * propio camino, porque exige motivo escrito y deja rastro de quién la cambió.
 *
 * <p>Estos contratos son del panel del equipo, no del portal. El candidato nunca ve su
 * puntaje ni la explicación del modelo.
 */
public final class DtosPerfilIntegral {

    private DtosPerfilIntegral() {}

    /**
     * El retrato completo. Puede llegar a medias y eso es información, no un error: si la
     * IA todavía no ha corrido, {@code perfil} viene vacío y {@code estadoCalificacion}
     * dice por qué.
     */
    public record PerfilIntegralResponse(
            Long postulacionId,
            String estadoCalificacion,
            String resumen,
            BigDecimal adecuacion,
            BigDecimal potencial,
            BigDecimal altoRendimiento,
            BigDecimal confianzaEvidencia,
            BigDecimal notaEtapa,
            Instant actualizadoEn,
            List<HallazgoResponse> hallazgos,
            List<NotaCriterioResponse> notasCriterio,
            List<AlertaResponse> alertas) {}

    // El tipo no es decorativo: la Regla 1 del documento 03 prohíbe mezclarlos. Un riesgo
    // que se puede corregir y una falta de evidencia no son lo mismo, y el panel los pinta
    // distinto para que nadie los confunda al decidir.
    public record HallazgoResponse(String tipo, String descripcion, String evidencia,
                                   boolean esCanalizable, String sugerencia) {}

    // La explicación viaja siempre: una nota sin ella no se guarda, así que tampoco se
    // enseña sola. Quien revisa tiene que poder ver en qué se basó el modelo.
    // El peso viaja con la nota, y no es decoracion: es lo que explica de donde sale el
    // numero final. Un 90 en un criterio que pesa 25 y un 90 en uno que pesa 5 se leen
    // igual en pantalla y no valen lo mismo.
    public record NotaCriterioResponse(String criterio, BigDecimal puntaje,
                                       BigDecimal maximo, BigDecimal peso,
                                       String explicacion, String origen) {}

    // Una alerta no descarta a nadie: es una pregunta para la conversación final.
    public record AlertaResponse(String tipo, String descripcion, Instant creadoEn) {}

    /** Lo que responde pedir que se vuelva a calificar. */
    public record CalificacionEncoladaResponse(String estado, String mensaje) {}

    /**
     * Quién es el candidato, sacado de su currículum por el agente que no puntúa.
     *
     * <p>No lleva edad, sexo ni estado civil: el agente lee la versión recortada del
     * currículum y esos datos no le llegan.
     */
    public record DatosCandidato(String nombre, String email, String telefono,
                                 String perfilResumen, String habilidades,
                                 Integer experienciaMesesTotal, String ultimoPuesto,
                                 String ultimaEmpresa, String educacionMaxima) {}

    /** Lo que responde pedir una pasada sobre la tanda entera. */
    public record PasadaEncolada(String estado, int candidatos, String mensaje) {}

    /**
     * La tanda de una convocatoria, ordenada de más apto a menos.
     *
     * <p>Es la pantalla que contesta «¿a quién invito primero?». Manda el grupo de
     * prioridad y no la nota: alguien con 92 y un riesgo crítico no va por delante de
     * alguien con 88 y ninguno, y ordenar solo por número escondería justo eso.
     */
    public record RankingVacante(
            Long vacanteId,
            String vacante,
            String puesto,
            String nivelPuesto,
            int total,
            int conPasadaFina,
            int calificados,
            int enCurso,
            int fallidos,
            List<FilaRanking> filas) {}

    /**
     * Un candidato en la tanda. Los números pueden venir vacíos y eso es información: la
     * IA todavía no llegó a esa fila, o falló y no se le inventó una nota.
     */
    public record FilaRanking(
            int puesto,
            Long postulacionId,
            String uuid,
            String candidato,
            String correo,
            String estado,
            String estadoNombre,
            String estadoCalificacion,
            // FINA, RAPIDA o vacío. Una nota de la rápida es provisional.
            String pasada,
            // Cómo se llama su archivo. Es lo que permite dar con el currículum
            // en la carpeta donde vive, sin tener que servirlo desde aquí.
            String archivoNombre,
            DatosCandidato datos,
            String grupoPrioridad,
            BigDecimal notaEtapa,
            BigDecimal notaCurriculum,
            BigDecimal adecuacion,
            BigDecimal potencial,
            BigDecimal altoRendimiento,
            BigDecimal confianzaEvidencia,
            String resumen,
            int riesgosCriticos,
            int fortalezas,
            int alertas,
            Instant actualizadoEn,
            List<NotaCriterioResponse> notasCriterio) {}
}
