package com.renaser.ai.ai_engine.prueba.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

// Los contratos de administración de la prueba del puesto. Misma convención que el
// resto: sufijo Response, MapStruct, y las validaciones de formato en el propio record
// para que un valor fuera de catálogo salga en 400, no en un 500 de la base.
public final class DtosPlantillaPrueba {

    private DtosPlantillaPrueba() {}

    public record CrearPlantilla(@NotBlank String nombre, Long puestoId) {}

    public record PlantillaResponse(Long id, String nombre, Long puestoId, boolean esActiva) {}

    public record CrearVersion(
            @NotBlank String enunciado,
            String materiales,
            String herramientasPermitidas,
            @NotBlank @Pattern(regexp = "CRONOMETRADA|PLAZO_ABIERTO",
                    message = "modalidad debe ser CRONOMETRADA o PLAZO_ABIERTO")
            String modalidad,
            Integer duracionMinutos,
            Integer plazoDias,
            Integer minutoCambioMin,
            Integer minutoCambioMax,
            Integer minutosExtra) {}

    public record VersionResponse(
            Long id, Long plantillaPruebaId, Integer version, String enunciado,
            String modalidad, Integer duracionMinutos, Integer plazoDias,
            Integer minutoCambioMin, Integer minutoCambioMax, String estado, Instant publicadaEn) {}

    public record CrearVariante(@NotBlank String texto) {}

    public record VarianteResponse(Long id, String texto, Integer orden) {}

    public record CrearPreguntaPrueba(
            @NotBlank String codigo, @NotBlank String enunciado,
            @NotBlank @Pattern(regexp = "PREVIA|UNIVERSAL|ESPECIFICA",
                    message = "tipo debe ser PREVIA, UNIVERSAL o ESPECIFICA")
            String tipo,
            Long puestoId, String revela) {}

    public record PreguntaPruebaResponse(
            Long id, String codigo, String enunciado, String tipo, Long puestoId) {}

    public record ElegirPregunta(@NotNull Long preguntaPruebaId) {}

    public record CrearEntregableRequerido(
            @NotBlank String nombre, @NotBlank String detalle,
            @NotBlank @Pattern(regexp = "ARCHIVO|ENLACE|CUALQUIERA",
                    message = "formato debe ser ARCHIVO, ENLACE o CUALQUIERA")
            String formato,
            @NotNull Boolean esObligatorio) {}

    public record EntregableRequeridoResponse(
            Long id, String nombre, String detalle, String formato, boolean esObligatorio) {}

    // La rúbrica reutiliza `criterio`: cada fila declara sus puntos y cómo se verifica.
    // Que sumen 100 se comprueba al publicar (RF-89), no al guardar el borrador.
    public record CrearCriterioRubrica(
            @NotBlank String codigo, @NotBlank String nombre, String descripcion,
            @NotNull Double puntos,
            @NotBlank @Pattern(regexp = "SISTEMA|AGENTE|PERSONA",
                    message = "metodoVerificacion debe ser SISTEMA, AGENTE o PERSONA")
            String metodoVerificacion) {}

    public record CriterioRubricaResponse(
            Long id, String codigo, String nombre, Double puntos, String metodoVerificacion) {}

    public record VersionCompleta(
            VersionResponse version, List<VarianteResponse> variantes,
            List<PreguntaPruebaResponse> preguntas, List<EntregableRequeridoResponse> entregables,
            List<CriterioRubricaResponse> rubrica) {}
}
