package com.renaser.ai.ai_engine.perfilintegral.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * El contrato entre el motor de agentes y la selección de personal.
 *
 * <p><b>Qué es esto.</b> El motor de agentes vive bajo {@code ai/} y no conoce ni una sola
 * tabla de selección. Para calificar necesita dos cosas: que alguien le pase los datos del
 * candidato («insumos») y que alguien guarde lo que el modelo devuelva («resultados»). Estos
 * records son ese ida y vuelta, y {@code PuenteCalificacionIa} es la única puerta.
 *
 * <p><b>Un solo juego de records, no dos.</b> Los mismos que se le enseñan al modelo son los
 * que se leen de su respuesta. Así no hay dos definiciones que se puedan desincronizar, y
 * cualquiera puede ver de un vistazo qué se manda y qué se espera de vuelta.
 *
 * <p><b>Las escalas, que son fáciles de confundir:</b>
 * <ul>
 *   <li>Los criterios del currículum van de <b>0 a 100</b> cada uno, y luego se combinan con
 *       su peso por nivel (RF-43), que suma 100.
 *   <li>Las respuestas abiertas van de <b>0 a 4</b>, que es la guía del documento (RF-55) y
 *       lo que la base exige en {@code nota_respuesta}.
 *   <li>La confianza y las tres notas del Perfil de Talento van de <b>0 a 100</b>.
 * </ul>
 */
public final class DtosCalificacionIa {

    private DtosCalificacionIa() {
    }

    // ==================== EVIDENCIA_CV ====================

    /** Lo que se le enseña al agente que lee el currículum. */
    public record InsumoCv(
            String puesto,
            String nivelPuesto,
            String queBuscaLaVacante,
            /* Ya anonimizado: sin foto, edad, sexo ni estado civil (RF-41) */
            String curriculum,
            String resultadoDelQueSeSienteOrgulloso,
            List<String> enlaces,
            List<CriterioConPeso> criterios) {
    }

    /** Lo que se le da al agente que solo extrae datos: el currículum y poco más. */
    public record InsumoDatos(String puesto, String curriculum) {
    }

    /**
     * La ficha que devuelve. Todo puede venir en null y eso es correcto: el currículum de
     * quien no puso su teléfono no tiene teléfono, y ahí no hay nada que adivinar.
     */
    public record ResultadoDatos(
            String nombre,
            String email,
            String telefono,
            String perfilResumen,
            List<String> habilidades,
            Integer experienciaMesesTotal,
            String ultimoPuesto,
            String ultimaEmpresa,
            Integer ultimaMesesDuracion,
            String educacionMaxima,
            // Las listas del perfil (instruccion v2). Un trabajo lanzado con la v1 devuelve
            // JSON sin ellas y llegan null: dato_cv no las necesita y el perfil simplemente
            // no propone listas. Nada se rompe por su ausencia.
            List<ExperienciaLeida> experiencia,
            List<EducacionLeida> educacion,
            List<IdiomaLeido> idiomas,
            List<CertificacionLeida> certificaciones) {
    }

    /** Un empleo tal como lo leyo el modelo. Fechas en texto AAAA-MM: las parsea (y
     * descarta si no cuadran) quien propone al perfil, no el deserializador. */
    public record ExperienciaLeida(String puesto, String empresa, String desde, String hasta,
                                   String descripcion) {
    }

    public record EducacionLeida(String titulo, String institucion, String nivel,
                                 String desde, String hasta) {
    }

    public record IdiomaLeido(String idioma, String nivel) {
    }

    public record CertificacionLeida(String nombre, String entidad, String emitidaEn,
                                     String venceEn) {
    }

    public record CriterioConPeso(String codigo, String nombre, String queMide, BigDecimal peso) {
    }

    /** Lo que devuelve. */
    public record ResultadoCv(
            List<NotaCriterioIa> criterios,
            List<AfirmacionIa> afirmaciones,
            BigDecimal confianza) {
    }

    /** puntaje de 0 a 100. La explicación es obligatoria: sin ella no se guarda (RF-150). */
    public record NotaCriterioIa(String codigo, BigDecimal puntaje, String explicacion,
                                 String evidencia) {
    }

    /** clasificacion: DEMOSTRADA · DECLARADA · CONTRADICHA · FALTA_INFO (RF-45). */
    public record AfirmacionIa(String texto, String clasificacion, String preguntaValidacion) {
    }

    // ==================== EVALUADOR ====================

    public record InsumoRespuestas(String puesto, String nivelPuesto,
                                   List<RespuestaAbierta> respuestas) {
    }

    public record RespuestaAbierta(Long respuestaId, String tipoDePregunta, String pregunta,
                                   String situacion, List<String> queMide, String respuesta) {
    }

    public record ResultadoEvaluador(List<NotaRespuestaIa> notas) {
    }

    /** puntaje de 0 a 4. explicacion y evidenciaCitada obligatorias (RF-56). */
    public record NotaRespuestaIa(Long respuestaId, BigDecimal puntaje, String explicacion,
                                  String evidenciaCitada, BigDecimal confianza) {
    }

    // ==================== POTENCIAL_RIESGO ====================

    public record InsumoPerfil(
            String puesto,
            String nivelPuesto,
            String queBuscaLaVacante,
            BigDecimal notaCurriculum,
            List<NotaCriterioIa> criteriosDelCurriculum,
            BigDecimal notaPreguntasCerradas,
            int cuantasPreguntasCerradas,
            BigDecimal notaRespuestasAbiertas,
            List<NotaRespuestaIa> respuestasAbiertas,
            List<String> alertasYaDetectadas) {
    }

    public record ResultadoPerfil(
            BigDecimal adecuacion,
            BigDecimal potencial,
            BigDecimal altoRendimiento,
            BigDecimal confianzaEvidencia,
            String resumen,
            List<HallazgoIa> hallazgos,
            List<AlertaIa> alertas) {
    }

    /** tipo: FORTALEZA · RIESGO_CRITICO · RIESGO_DESARROLLABLE · PREFERENCIA · FALTA_EVIDENCIA. */
    public record HallazgoIa(String tipo, String descripcion, String evidencia,
                             Boolean esCanalizable, String sugerencia) {
    }

    /** tipo: CONTRADICCION · DEMASIADO_IDEAL. Una alerta nunca descarta a nadie (RF-64). */
    public record AlertaIa(String tipo, String descripcion) {
    }
}
