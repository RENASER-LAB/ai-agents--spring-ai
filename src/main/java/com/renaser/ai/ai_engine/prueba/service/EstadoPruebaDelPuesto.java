package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;

import java.math.BigDecimal;

/**
 * En qué punto está la prueba del puesto de un candidato, dicho de una vez para que ninguna
 * pantalla tenga que deducirlo.
 *
 * <p><b>Por qué existe.</b> El ranking enseñaba un guion cuando faltaba la nota de etapa, y
 * ese hueco tenía dos causas que no se parecen en nada: <b>no la terminó</b> —sigue sin
 * entregar, o el sistema la cerró al vencer el plazo— o <b>la entregó y todavía no tiene
 * nota</b>, porque faltan criterios de la rúbrica por calificar. La primera es trabajo del
 * candidato; la segunda es trabajo del equipo. Un solo texto para las dos mandaba a perseguir
 * a quien ya había hecho lo suyo.
 *
 * <p><b>Se calcula, no se guarda.</b> Sale de la nota de etapa y del intento tal como estén en
 * ese momento: abrir o descargar el ranking no escribe nada, y volver a abrirlo después de una
 * entrega o de una calificación da el valor nuevo sin que nadie tenga que actualizar un campo.
 *
 * <p><b>No son los estados de la postulación.</b> {@code PRUEBA_POR_CONFIRMAR} y compañía
 * dicen dónde está parado el proceso, no si hay nota: son códigos internos y no se reutilizan
 * como texto de interfaz.
 */
public enum EstadoPruebaDelPuesto {

    /** Tiene nota de la etapa. Manda sobre todo lo demás, incluso si la entrega fue automática. */
    CALIFICADA,

    /**
     * La entregó una persona y su rúbrica todavía no ha dejado nota.
     *
     * <p>Es el único caso en el que el equipo tiene algo que hacer: hay entrega que mirar.
     */
    PENDIENTE_CALIFICACION,

    /**
     * No llegó a haber una entrega del candidato: el intento sigue sin entregar
     * —{@code PENDIENTE} o {@code EN_CURSO}— o lo cerró el sistema al vencer el plazo.
     */
    INCOMPLETA,

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

    /**
     * El estado de esa prueba, con la prioridad que manda: primero la nota, después la entrega.
     *
     * <p>⚠️ <b>Un cero es una nota</b>, así que lo que decide es que {@code notaDeLaEtapa}
     * exista y no que sea mayor que cero. Tratar el cero como ausencia convertiría a quien
     * sacó cero en alguien que no rindió.
     *
     * <p>⚠️ <b>Lo que separa «no la terminó» de «falta calificarla» es {@code entregadoEn}
     * junto con {@code esEntregaAutomatica}</b>, no el estado de la postulación. Una entrega
     * automática es el sistema cerrando el intento al vencer el plazo, no el candidato
     * diciendo «ya está»: por eso cuenta como incompleta mientras no haya nota.
     *
     * @param notaDeLaEtapa la nota de {@code PRUEBA_PUESTO}, o {@code null} si todavía no hay
     * @param intento       el intento de esa postulación, o {@code null} si no hay ninguno
     */
    public static EstadoPruebaDelPuesto de(BigDecimal notaDeLaEtapa, IntentoPrueba intento) {
        if (notaDeLaEtapa != null) return CALIFICADA;
        if (intento == null) return NO_APLICA;
        if (intento.getEntregadoEn() == null) return INCOMPLETA;
        return intento.isEsEntregaAutomatica() ? INCOMPLETA : PENDIENTE_CALIFICACION;
    }
}
