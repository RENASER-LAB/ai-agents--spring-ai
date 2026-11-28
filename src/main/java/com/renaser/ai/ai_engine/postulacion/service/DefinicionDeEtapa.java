package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import java.util.Optional;

/**
 * El contrato de una etapa del embudo (V95): lo único que hay que programar para que una etapa
 * nueva funcione en cualquier recorrido.
 *
 * <p>Hay una implementación por cada etapa del catálogo ({@code etapa}), registrada como bean de
 * Spring; {@link RegistroDeEtapas} las reúne por código y no deja arrancar la aplicación si a una
 * etapa del catálogo le falta la suya. Ni la máquina de estados ni el avance automático conocen
 * las etapas por su nombre: preguntan aquí.
 *
 * <p>Lo que pasa al entrar a una etapa pasa igual venga de donde venga —«Avanzar», el pase
 * automático, el cierre de otra etapa o un movimiento a mano— porque todos entran por
 * {@link AvanceDelRecorrido}. Moverse dentro de la misma etapa no llama a nada.
 *
 * <p>Ver la guía «Cómo agregar una etapa» en docs/.
 */
public interface DefinicionDeEtapa {

    /** El código de la etapa en el catálogo, por ejemplo {@code PRUEBA_PUESTO}. */
    String codigo();

    /**
     * El momento por el que se entra a la etapa ({@code POR_HABILITAR}, {@code TURNO_CANDIDATO},
     * {@code CALIFICANDO} o {@code POR_CONFIRMAR}). Vacío: el primero que tenga en el catálogo.
     */
    default Optional<String> momentoDeEntrada() {
        return Optional.empty();
    }

    /**
     * Si la vacante tiene con qué llenar la etapa. Lo pregunta el pase automático antes de mover a
     * nadie: sin prueba publicada, por ejemplo, la persona espera a que alguien la mire en vez de
     * entrar a una etapa vacía.
     */
    default boolean listaParaEntrar(Vacante vacante) {
        return true;
    }

    /**
     * Si antes de entrar hay que bloquear la fila de la postulación y comprobar que nadie la movió.
     * Hace falta cuando entrar CREA algo que no admite duplicados (el intento de la prueba, el
     * periodo de validación): dos entradas a la vez no deben crear ni mover dos veces.
     */
    default boolean bloqueaLaFila() {
        return false;
    }

    /**
     * Antes de la transición, en la misma transacción: crea lo que la persona necesita en la
     * etapa y puede llevarla a otro paso de ella. Si falla, no se mueve nada ni sale ningún aviso.
     *
     * @param entrada la postulación, su vacante, el estado al que se pidió entrar y el motivo
     * @return el estado al que entra de verdad y el motivo que queda escrito
     */
    default Destino alEntrar(Entrada entrada) {
        return new Destino(entrada.destino(), entrada.motivo());
    }

    /**
     * Después de la transición, en la misma transacción: lo que depende de que la persona ya esté
     * dentro (en la Simulación, mirar si hay una sesión con cupo para ella).
     *
     * @param estado el estado al que entró
     */
    default void trasEntrar(Postulacion postulacion, String estado) {
        // Casi ninguna etapa necesita nada después de entrar.
    }

    /** Lo que recibe {@link #alEntrar}. */
    record Entrada(Postulacion postulacion, Vacante vacante, String destino, String motivo) {}

    /** El estado al que entra de verdad la postulación, y el motivo que queda escrito. */
    record Destino(String estado, String motivo) {}
}
