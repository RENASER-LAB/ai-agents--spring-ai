package com.renaser.ai.ai_engine.postulacion.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// Los contratos con que el equipo mira y mueve una postulación.
public final class DtosPostulacion {

    private DtosPostulacion() {}

    public record FilaBandeja(Long postulacionId, String uuid, String candidato, String vacante,
                              String estado, String estadoNombre, String esperaA,
                              String grupoPrioridad, long diasSinCambio) {}

    public record FichaPostulacion(Long id, String uuid, String candidato, String correo,
                                   String vacante, String estado, String estadoNombre,
                                   String grupoPrioridad, String motivoCierre,
                                   String resultadoOrgulloso, List<String> enlaces,
                                   Long archivoCvId, Instant creadoEn, Instant movidoEn) {}

    public record PasoHistorial(String estadoAnterior, String estadoNuevo, Long usuarioId,
                                boolean fueElSistema, boolean fuePorLote, String motivo,
                                Instant ocurridaEn) {}

    public record Transicionar(@NotBlank String estadoDestino,
                               @NotBlank String motivo,
                               String motivoCierre) {}

    public record ConfirmarAvance(@NotBlank String motivo) {}

    /**
     * Reabrir una evaluación. Los días son opcionales: sin ellos manda el parámetro
     * {@code dias_plazo_evaluacion}, que es donde Renaser lo cambia sin desplegar.
     */
    public record ReabrirEvaluacion(Integer dias, @NotBlank String motivo) {}

    public record ConteoEmbudo(Map<String, Long> porEstado) {}

    /**
     * Por donde bajarse un archivo sin pasar por el backend.
     *
     * @param url      enlace firmado: sirve para este archivo y nada mas
     * @param expiraEn a partir de aqui deja de funcionar. Es corto a proposito: el enlace
     *                 no vuelve a preguntar quien eres, asi que es tan secreto como el
     *                 propio curriculum
     * @param nombre   como llamarlo al guardarlo
     */
    public record EnlaceArchivo(String url, Instant expiraEn, String nombre) {}
}
