package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * En qué punto está la prueba del puesto de un candidato, dicho de una vez para que ninguna
 * pantalla tenga que deducirlo.
 *
 * <p><b>Por qué existe.</b> El ranking enseñaba un guion cuando faltaba la nota de etapa, y
 * ese hueco tenía causas que no se parecen en nada: <b>no la ha empezado</b>, <b>la está
 * haciendo</b>, <b>se le acabó el tiempo sin completarla</b> o <b>la entregó y todavía no tiene
 * nota</b>. Un solo texto para todas mandaba a perseguir a quien ya había hecho lo suyo, o
 * llamaba «incompleta» a quien la estaba haciendo (V89).
 *
 * <p><b>Se calcula, no se guarda.</b> Sale de la nota de etapa, del intento y de la hora tal
 * como estén en ese momento: abrir o descargar el ranking no escribe nada, y volver a abrirlo
 * después de una entrega, una calificación o de que se le pase la fecha da el valor nuevo sin
 * que nadie tenga que actualizar un campo.
 *
 * <p><b>No son los estados de la postulación.</b> {@code PRUEBA_POR_CONFIRMAR} y compañía
 * dicen dónde está parado el proceso, no si hay nota: son códigos internos y no se reutilizan
 * como texto de interfaz.
 */
public enum EstadoPruebaDelPuesto {

    /** Tiene nota de la etapa. Manda sobre todo lo demás, incluso si la entrega fue automática. */
    CALIFICADA,

    /**
     * Se entregó —a mano, o sola al vencer con todo respondido— y su rúbrica todavía no ha
     * dejado nota. En la prueba de plantilla, también la que se entregó sola al vencer.
     *
     * <p>Es el único caso en el que el equipo tiene algo que hacer: hay entrega que mirar.
     */
    PENDIENTE_CALIFICACION,

    /**
     * «No la completó» (V89): se le acabó el tiempo con algo sin responder o sin subir (la
     * prueba del editor, {@code no_completada}), o se le pasó la fecha sin haberla abierto.
     *
     * <p>Antes de la V89 se llamaba {@code INCOMPLETA} y juntaba también a quien no la había
     * empezado y a quien la estaba haciendo. Cambió de nombre para que ningún lector viejo la
     * confunda con lo de antes.
     */
    NO_COMPLETADA,

    /** La empezó y su tiempo sigue corriendo (V89). */
    EN_CURSO,

    /** La tiene y no la ha abierto, con plazo vigente o sin fecha (V89). */
    SIN_EMPEZAR,

    /**
     * No hay intento del que hablar.
     *
     * <p>Es lo normal en una tanda a medio recorrer —la postulación no ha llegado a la etapa
     * técnica— y también en las vacantes que rinden el cuestionario técnico, que no usan la
     * prueba del puesto. <b>Nunca se confunde con una entrega pendiente de calificar</b>: sin
     * intento no hay nada entregado.
     *
     * <p>Es además el valor neutro cuando el detalle del intento no se puede consultar: una
     * fila sin dato se conserva diciendo que no sabe, en vez de tumbar el ranking entero.
     */
    NO_APLICA;

    /** Lo mismo que {@link #de(BigDecimal, IntentoPrueba, Instant)}, con la hora de ahora. */
    public static EstadoPruebaDelPuesto de(BigDecimal notaDeLaEtapa, IntentoPrueba intento) {
        return de(notaDeLaEtapa, intento, Instant.now());
    }

    /**
     * El estado de esa prueba, con la prioridad que manda: la nota, la entrega, lo que quedó
     * sin completar, lo que está en curso y lo que no se abrió.
     *
     * <p>⚠️ <b>Un cero es una nota</b>, así que lo que decide es que {@code notaDeLaEtapa}
     * exista y no que sea mayor que cero. Tratar el cero como ausencia convertiría a quien
     * sacó cero en alguien que no rindió.
     *
     * <p>⚠️ <b>Una entrega automática con todo respondido es una entrega</b>: la del editor
     * solo se entrega sola si no le falta nada, y la de plantilla se entrega sola siempre.
     * Las dos esperan nota. La que venció con huecos se cerró como {@code no_completada}.
     *
     * @param notaDeLaEtapa la nota de {@code PRUEBA_PUESTO}, o {@code null} si todavía no hay
     * @param intento       el intento de esa postulación, o {@code null} si no hay ninguno
     * @param ahora         la hora con la que se decide si a quien no la abrió se le pasó la fecha
     */
    public static EstadoPruebaDelPuesto de(BigDecimal notaDeLaEtapa, IntentoPrueba intento,
                                           Instant ahora) {
        if (notaDeLaEtapa != null) return CALIFICADA;
        if (intento == null) return NO_APLICA;
        if (intento.quedoSinCompletar(ahora)) return NO_COMPLETADA;
        if (intento.getEntregadoEn() != null) return PENDIENTE_CALIFICACION;
        return intento.getIniciadoEn() != null ? EN_CURSO : SIN_EMPEZAR;
    }
}
