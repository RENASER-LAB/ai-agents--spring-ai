package com.renaser.ai.ai_engine.vacante.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

// Los contratos de vacantes, puestos y requisitos.
public final class DtosVacante {

    private DtosVacante() {}

    public record GuardarVacante(
            @NotNull Long solicitudTalentoId,
            @NotNull Long puestoId,
            @NotBlank String titulo,
            @NotBlank String descripcion,
            String proposito,
            String responsabilidades,
            String requisitos,
            String modalidad,
            String horario,
            String ubicacion,
            String compensacionPublica,
            @NotBlank String tipoCierre,
            Integer plazas,
            Instant abreEn,
            Instant cierraEn,
            @NotNull Long responsableUsuarioId) {}

    public record VacantePanel(Long id, String titulo, String estado, String tipoCierre,
                               Long puestoId, Long solicitudTalentoId, Long responsableUsuarioId,
                               Instant publicadaEn, Instant cerradaEn) {}

    public record GuardarRequisito(@NotBlank String descripcion, @NotBlank String regla) {}

    public record RequisitoPanel(Long id, String descripcion, String regla, boolean esActivo) {}

    public record GuardarPuesto(@NotBlank String codigo, @NotBlank String nombre,
                                @NotBlank String nivelPuestoCodigo, @NotBlank String familiaCodigo) {}

    // Listar puestos NO puede devolver `GuardarPuesto`: ese es el cuerpo de entrada y no
    // lleva `id`. Sin el id, quien consulte el catálogo no puede crear una vacante, que
    // pide `puestoId`. Es un contrato de salida y por eso es un record aparte.
    public record PuestoResponse(Long id, String codigo, String nombre,
                                 String nivelPuestoCodigo, String familiaCodigo) {}

    // El cuerpo de cerrar una vacante: el mismo {"motivo": "..."} de siempre. Tiene su propio
    // record y no comparte el de solicitudes para que cada dominio sea dueño de sus contratos.
    public record CerrarVacante(@NotBlank String motivo) {}

    // Qué evaluación responderá quien postule a esta vacante
    public record AsignarPlantilla(@NotNull Long plantillaEvaluacionId) {}

    // Qué versión de la prueba del puesto rendirá quien llegue a esa etapa
    public record AsignarPlantillaPrueba(@NotNull Long versionPlantillaPruebaId) {}
}
