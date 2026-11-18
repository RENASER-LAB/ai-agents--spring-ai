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

    /**
     * El plazo que rige HOY para esta persona, para poder enseñarlo antes de tocarlo.
     *
     * <p>Hasta ahora la ficha ofrecía el campo de fecha en blanco sobre cuatro situaciones que
     * no se parecen en nada —todavía no llegó a la etapa, no la ha abierto, la está haciendo,
     * ya la entregó— y quien lo usaba no sabía qué estaba cambiando.
     *
     * <ul>
     *   <li>{@code existeIntento} falso = no hay prueba del puesto que mirar: o no ha llegado
     *       a la etapa, o su vacante rinde el cuestionario técnico, que no usa
     *       {@code intento_prueba}. Lo distingue {@code instrumento}.
     *   <li>{@code venceEn} vacío = todavía no tiene fecha; se le calculará al abrirla. <b>No
     *       es un error</b>, y los datos antiguos son así.
     *   <li>{@code origen} dice de dónde sale esa fecha: {@code VACANTE} (la común de la
     *       convocatoria), {@code RELOJ} (la calculó el servidor al empezar) o {@code PROPIO}
     *       (se la puso alguien a mano, y mover la de la vacante ya no la toca).
     *   <li>{@code instrumento} es el de la vacante: {@code PLANTILLA} o
     *       {@code CUESTIONARIO_TECNICO}.
     *   <li>{@code sinCompletar} (V89): si no la completó —se le acabó el tiempo con huecos o se
     *       le pasó la fecha sin abrirla—, cuándo, qué le faltó y si se le puede dar más tiempo.
     *       Nulo en cualquier otro caso.
     *   <li>{@code masTiempo} (V89): la última vez que se le dio más tiempo, mientras la prueba
     *       siga abierta. Nulo si nunca se le dio, o si ya entregó o volvió a vencer.
     * </ul>
     */
    public record PlazoVigente(boolean existeIntento, Instant venceEn, String origen,
                               Instant iniciadoEn, Instant entregadoEn, String instrumento,
                               SinCompletar sinCompletar, MasTiempo masTiempo) {

        /** Sin lo de la V89: el de antes. */
        public PlazoVigente(boolean existeIntento, Instant venceEn, String origen,
                            Instant iniciadoEn, Instant entregadoEn, String instrumento) {
            this(existeIntento, venceEn, origen, iniciadoEn, entregadoEn, instrumento, null, null);
        }
    }

    /**
     * Quien no la completó (V89), con lo que la ficha dice arriba y lo que decide el botón
     * «Darle más tiempo».
     *
     * @param seAcaboEn   cuándo se le acabó el tiempo: su fecha límite
     * @param sinAbrir    si nunca la abrió («No la abrió»)
     * @param queFalto    «le faltaron 4 preguntas y «Tablero.xlsx»»; nulo si no la abrió o no
     *                    se pudo contar
     * @param puedeMover  si quien mira tiene {@code mover_postulacion}: sin él el botón no sale
     * @param bloqueo     por qué el botón sale apagado —«Vacante archivada», «Proceso
     *                    cerrado»—, o nulo si se puede usar
     */
    public record SinCompletar(Instant seAcaboEn, boolean sinAbrir, String queFalto,
                               boolean puedeMover, String bloqueo) {}

    /** La última vez que se le dio más tiempo (V89): cuándo, quién y por qué. */
    public record MasTiempo(Instant dadoEn, String dadoPor, String motivo) {}

    /**
     * «Darle más tiempo» a quien no completó la prueba (V89): una fecha y hora futura, el motivo
     * obligatorio y si se le avisa. {@code avisar} nulo es avisar: es lo que llega marcado.
     */
    public record DarMasTiempo(@NotNull Instant venceEn, @NotBlank String motivo, Boolean avisar) {}

    /**
     * Lo que quedó tras darle más tiempo (V89).
     *
     * @param estadoPrueba {@code EN_CURSO} si ya la había abierto, {@code SIN_EMPEZAR} si no
     * @param avisadoPor   por dónde se le avisa: «correo», «campana» y, con vínculo activo,
     *                     «WhatsApp». Vacío si se decidió no avisarle
     */
    public record MasTiempoDado(Long postulacionId, Instant venceEn, String estadoPrueba,
                                java.util.List<String> avisadoPor) {}
}
