package com.renaser.ai.ai_engine.prueba.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class DtosCalificacionPrueba {

    private DtosCalificacionPrueba() {}

    public record PonerNotaCriterio(
            @NotNull Double puntaje,
            @NotBlank String explicacion) {}

    public record NotaCriterioResponse(
            Long criterioId, String nombre, Double puntosMaximos,
            Double puntaje, String explicacion, String origen) {}

    /**
     * Una pregunta del cuestionario con lo que contestó el candidato.
     *
     * <p>No lleva {@code revela}, que es la señal interna de qué mide cada pregunta: quien
     * revisa juzga la respuesta, y saber qué se buscaba la condiciona. Tampoco el id del
     * intento: desde el panel se entra por la postulación.
     *
     * <p>{@code respuesta} es null si dejó esa pregunta en blanco, que no es lo mismo que
     * haberla contestado vacía y hay que poder distinguirlo.
     */
    public record RespuestaDePrueba(
            Long preguntaId, String codigo, Integer orden, String tipo,
            String enunciado, String respuesta, Instant respondidaEn) {}

    /**
     * Una de las cosas que la prueba pedía entregar, y cómo llegó.
     *
     * <p><b>Salen todas las pedidas, también las que no entregó.</b> Que faltara la tercera
     * es justo lo que hay que poder ver, y un hueco en la lista no se lee: se lee una lista
     * más corta, que parece completa.
     *
     * <p><b>{@code enlace} y {@code archivoId} viajan solo con {@code descargar_entregables}.</b>
     * Quien abre la ficha ve QUÉ entregó y cuándo; para llegar al contenido hace falta el
     * mismo permiso en los dos casos. El archivo ya lo pedía —la descarga y el enlace firmado
     * lo exigen—, y el enlace tenía que pedir lo mismo: en la prueba de marketing el vídeo de
     * sustentación se entrega como enlace, así que dejarlo pasar con el permiso flojo abriría
     * por la puerta de al lado justo lo que la otra cierra.
     *
     * <p>{@code porQueNoSeVe} lo dice con palabras cuando no hay contenido que enseñar —no lo
     * entregó, el archivo ya no está guardado, o falta el permiso—, porque un hueco callado y
     * un «no entregó» se leen igual y no son lo mismo.
     */
    public record EntregaDeLaPrueba(
            Long entregableRequeridoId, String nombre, String detalle, String formato,
            boolean esObligatorio, boolean loEntrego, String enlace, Long archivoId,
            String archivoNombre, Integer version, Instant subidoEn, String porQueNoSeVe) {}

    /** Lo que se contesta al pedirle al agente que califique. */
    public record CalificacionIaEncolada(String estado, String mensaje) {}

    /**
     * Ponerle a UN candidato la fecha en que se le cierra la prueba.
     *
     * <p>Es una fecha y no un número de días a propósito: lo que se quiere decir es «hasta el
     * domingo a medianoche», y eso mismo para todos. Los días se cuentan desde que cada uno
     * empieza, así que dan una fecha distinta por persona.
     */
    public record DefinirPlazoPrueba(@NotNull Instant venceEn, @NotBlank String motivo) {}

    public record PlazoPrueba(Long postulacionId, Instant venceEn, boolean yaEmpezo) {}
}
