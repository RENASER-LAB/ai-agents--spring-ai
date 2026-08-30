package com.renaser.ai.ai_engine.solicitud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// Los contratos de la Solicitud de Talento.
public final class DtosSolicitud {

    private DtosSolicitud() {}

    public record CrearSolicitud(
            @NotNull Long areaId,
            @NotNull Long puestoId,
            @NotBlank String urgencia,
            String nivelPuestoCodigo,
            String familiaCodigo,
            @NotBlank String resultadoPrincipal,
            @NotBlank String motivo,
            @NotBlank String consecuenciaNoContratar,
            LocalDate requeridaPara,
            @NotBlank String analisisCapacidad,
            String capacidadesIndispensables,
            String capacidadesAprendibles,
            String modalidad,
            String horario,
            String compensacion,
            Long responsableUsuarioId,
            @NotNull @Size(min = 3, max = 5,
                    message = "Una solicitud lleva entre 3 y 5 resultados esperados")
            List<ResultadoEsperadoDto> resultadosEsperados) {}

    public record ResultadoEsperadoDto(@NotBlank String descripcion, String indicador) {}

    public record SolicitudResumen(Long id, String estado, String urgencia, Long areaId,
                                   Long puestoId, String puestoNombre,
                                   String nivelPuestoCodigo, String familiaCodigo,
                                   String resultadoPrincipal, Instant creadoEn) {}

    public record SolicitudDetalle(SolicitudResumen resumen, String motivo,
                                   String consecuenciaNoContratar, String analisisCapacidad,
                                   String capacidadesIndispensables, String capacidadesAprendibles,
                                   LocalDate requeridaPara, String nivelPuestoCodigo, String familiaCodigo,
                                   List<ResultadoEsperadoDto> resultadosEsperados) {}

    public record Decidir(@NotBlank String motivo) {}
}
