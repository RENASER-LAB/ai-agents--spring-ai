package com.renaser.ai.ai_engine.vacante.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

// La ficha de vacante del método CAZATALENTOS. El formulario es el guion de la
// conversación con el dueño: aquí viajan sus palabras, no lenguaje de recursos humanos.
public class DtosFichaVacante {

    // Todo opcional: BORRADOR admite «a medias mientras el dueño la piensa». Qué la
    // vuelve COMPLETA lo decide el servicio y lo cuenta la respuesta.
    public record GuardarFicha(
            String q1Resultado,
            String q2Riesgo,
            String q3DiaReal,
            String q4EpocaDorada,
            String q5Estructura,
            String q6Autonomia,
            String q7JefeDirecto,
            String q8LoIncomodo,
            String q9Requerimientos,
            String q10Espejo,

            @PositiveOrZero(message = "genteEnEmpresa no puede ser negativa")
            Integer genteEnEmpresa,
            @PositiveOrZero(message = "genteACargo no puede ser negativa")
            Integer genteACargo,

            // El orden ES la velocidad de daño y lo decide el dueño.
            String riesgo1,
            String riesgo2,
            String riesgo3,
            String riesgo4,

            String eliminatoria1,
            String eliminatoria2,

            String requerimiento1,
            String requerimiento2,
            String requerimiento3,

            @Pattern(regexp = "F[1-7](,F[1-7])*",
                    message = "familias debe ser F1..F7 separadas por coma, p. ej. F4 o F4,F1")
            String familias) {}

    /** La versión de pesos que corresponde al tamaño derivado, para asignarla con un clic. */
    public record PesosSugeridos(Long id, String etiqueta, boolean yaAsignada) {}

    public record FichaResponse(
            Long id,
            Long vacanteId,
            String q1Resultado,
            String q2Riesgo,
            String q3DiaReal,
            String q4EpocaDorada,
            String q5Estructura,
            String q6Autonomia,
            String q7JefeDirecto,
            String q8LoIncomodo,
            String q9Requerimientos,
            String q10Espejo,
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
            String familias,
            String tamano,
            String estado,
            Instant actualizadoEn,
            PesosSugeridos pesosSugeridos) {}
}
