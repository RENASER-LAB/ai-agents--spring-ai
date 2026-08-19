package com.renaser.ai.ai_engine.perfilintegral.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

// Los contratos del banco de preguntas. Convención nueva para el hito 2: sufijo Response
// y mapeo por MapStruct (ver mapper/), a diferencia del hito 1 que mapea a mano. Se parte
// por sub-dominio para no crecer un solo archivo.
//
// Los @Pattern en los catálogos cerrados son a propósito: en el hito 1 un valor inválido
// en un campo así (ej. urgencia) rompía con un 500 de Postgres en vez de un 400 claro,
// porque el DTO no validaba antes de llegar a la base.
public final class DtosBancoPreguntas {

    private DtosBancoPreguntas() {}

    public record CrearVersionBanco(
            @NotBlank @Pattern(regexp = "NIVEL|ALINEACION",
                    message = "tipoBanco debe ser NIVEL o ALINEACION")
            String tipoBanco,
            String nivelPuestoCodigo,
            @NotBlank String etiqueta) {}

    public record VersionBancoResponse(
            Long id,
            String tipoBanco,
            String nivelPuestoCodigo,
            String etiqueta,
            String estado,
            Instant publicadaEn) {}

    public record CrearPregunta(
            @NotBlank String codigo,
            String bloque,
            @NotBlank @Pattern(regexp = "ESTILO|SITUACION|CONDUCTUAL|MICROCASO|DILEMA|CONSISTENCIA",
                    message = "tipo debe ser uno de los 6 tipos de pregunta")
            String tipo,
            @NotBlank String enunciado,
            String situacion,
            @NotNull Boolean esPuntuable,
            @NotNull Integer orden) {}

    public record PreguntaResponse(
            Long id,
            Long versionBancoId,
            String codigo,
            String tipo,
            String enunciado,
            boolean esPuntuable,
            Integer orden) {}

    public record CrearOpcion(
            @NotBlank @Pattern(regexp = "[A-Z]", message = "letra debe ser una sola letra mayúscula")
            String letra,
            @NotBlank String texto,
            Double puntaje) {}

    public record OpcionResponse(
            Long id,
            Long preguntaId,
            String letra,
            String texto,
            Double puntaje) {}
}
