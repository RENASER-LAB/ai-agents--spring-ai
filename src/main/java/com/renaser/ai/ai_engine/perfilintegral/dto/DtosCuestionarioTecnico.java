package com.renaser.ai.ai_engine.perfilintegral.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

// El cuestionario técnico del método CAZATALENTOS: lo que recibe el REDACTOR, lo que
// devuelve, y lo que ve el dueño en su panel.
public class DtosCuestionarioTecnico {

    /** Un bloque de la estructura fija: qué se pide y sobre qué tema. */
    public record BloquePedido(String bloque, int cantidad, String tema) {}

    /** La ficha tal como la escribió el dueño: el insumo entero del REDACTOR. */
    public record FichaDelDueno(
            String resultadoEsperado,
            String riesgoContado,
            String diaReal,
            String epocaDorada,
            String estructura,
            String autonomia,
            String jefeDirecto,
            String loIncomodo,
            String requerimientos,
            String espejo,
            Integer genteEnEmpresa,
            Integer genteACargo,
            String riesgo1,
            String riesgo2,
            String riesgo3,
            String riesgo4,
            String eliminatoria1,
            String eliminatoria2,
            String requerimiento1,
            String requerimiento2,
            String requerimiento3,
            String familias) {}

    public record InsumoRedactor(
            String nivel,
            String tituloVacante,
            String descripcionVacante,
            FichaDelDueno ficha,
            List<BloquePedido> estructura) {}

    /** Una pregunta tal como la propone el REDACTOR. Sin puntaje: aquí no se puntúa nada. */
    public record PreguntaGenerada(
            String codigo,
            String bloque,
            String bloqueEtiqueta,
            String enunciado,
            String c3Esperado,
            String c4Esperado,
            String senalDeCero,
            Boolean presencial) {}

    public record ResultadoRedactor(List<PreguntaGenerada> preguntas) {}

    // ---------- Lo que ve el dueño ----------

    public record PreguntaDelCuestionario(
            Long id,
            String codigo,
            String bloque,
            String enunciado,
            String c3Esperado,
            String c4Esperado,
            String senalDeCero,
            boolean presencial,
            Integer orden) {}

    /**
     * generacion: SIN_PEDIR · EN_CURSO · FALLIDA · LISTA — el último trabajo del REDACTOR.
     * desactualizado: la ficha cambió después de generar este cuestionario.
     */
    public record CuestionarioResponse(
            Long versionBancoId,
            String estado,
            boolean desactualizado,
            String generacion,
            List<PreguntaDelCuestionario> preguntas) {}

    public record CorregirPreguntaTecnica(
            @NotBlank String enunciado,
            String c3Esperado,
            String c4Esperado,
            String senalDeCero) {}
}
