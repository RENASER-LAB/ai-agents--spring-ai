package com.renaser.ai.ai_engine.vacante.repository;

import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

/**
 * Escribir una nota con los pesos de la vacante sin que un cambio de versión se cuele a mitad
 * (V74, specs/pesos-con-candidatos-y-fin-del-prestamo.md, AC-05; hallazgo QA-02).
 *
 * <p><b>El problema.</b> Cambiar los pesos bloquea la vacante ({@code FOR NO KEY UPDATE}),
 * le pone la versión nueva y recalcula a todos en la misma transacción. Una nota que llegaba
 * en ese rato —la IA que estaba en curso, un ajuste a mano, el facilitador— leía la vacante
 * sin esperar, con la versión de antes, esperaba después a la fila de su nota y, al confirmar
 * el cambio, la pisaba con la cuenta vieja: 61 con la versión 7 en una vacante ya en la 8.
 *
 * <p><b>La regla.</b> Quien escribe una nota que depende de los pesos, o que dice con qué
 * versión se calculó, pide aquí la vacante <b>antes de leer o escribir ninguna nota</b>. Su
 * fila queda compartida ({@code FOR SHARE}) hasta el final de la transacción:
 * <ul>
 *   <li>si hay un cambio de pesos a mitad, espera a que confirme y sale con la versión
 *       nueva;</li>
 *   <li>si el cambio llega después, es él quien espera a que la nota termine, y la recalcula
 *       ya con ella dentro.</li>
 * </ul>
 * Dos notas a la vez no se esperan entre sí: el cerrojo compartido no choca con otro igual.
 *
 * <p>⚠️ <b>La vacante primero, siempre.</b> El cambio de pesos bloquea la vacante y después
 * las notas y las postulaciones; quien pida aquí la vacante después de haber escrito una de
 * esas filas puede cruzarse con él. Por eso va al principio de cada escritura y no al
 * final.
 *
 * <p>⚠️ <b>Tampoco después de mover la postulación (QA-03).</b> Entregar una prueba o una
 * evaluación pasa la postulación a «calificando» y en la misma transacción escribe la nota que
 * ya se puede calcular. Pedir la vacante al escribir la nota, con la postulación ya movida, es
 * el cruce de arriba: el cambio de pesos espera a la postulación y la entrega a la vacante.
 * Esos caminos la piden con {@link #bloquearParaCalificarYReleer} al empezar, antes de tocar
 * el intento, la evaluación o la postulación; la segunda petición, ya al escribir la nota, no
 * espera a nadie, porque la transacción ya la tiene.
 *
 * <p>⚠️ <b>Y lo que se escribe después de esperar parte de la fila releída (QA-04).</b> Quien
 * cargó la postulación antes de esperar tiene en memoria la de antes del cambio: con su grupo
 * viejo, y sin lo que el equipo hiciera en ese rato. {@link #bloquearParaCalificarYReleer} la
 * relee ya con el cerrojo, y la entidad solo escribe las columnas que cambia
 * ({@code @DynamicUpdate} en {@code Postulacion}).
 */
public interface CerrojoDeLosPesos {

    /**
     * Comparte la fila de la vacante hasta el final de la transacción y deja {@code vacante}
     * con la versión de pesos confirmada.
     *
     * <p>Si la entidad ya estaba cargada de antes en esta transacción —el guardián del panel
     * la lee para comprobar el alcance— y un cambio de pesos confirmó mientras tanto, se
     * relee: Hibernate devolvería la copia vieja sin preguntar.
     *
     * <p>Fuera de una transacción de escritura no hace nada: no hay nada que escribir con
     * esa versión, y un {@code FOR SHARE} no se admite en una de solo lectura.
     */
    void bloquearParaCalificar(Vacante vacante);

    /**
     * Lo mismo, por el id de la vacante, para quien llega por una postulación y todavía no ha
     * escrito nada: la entrega de la prueba o de la evaluación, el ajuste a mano, la nota de
     * la IA. Si la vacante ya estaba cargada en esta transacción, sale releída igual que con
     * {@link #bloquearParaCalificar(Vacante)}.
     */
    void bloquearParaCalificarPorId(Long vacanteId);

    /**
     * El cerrojo de la vacante de esta postulación y, ya con él, la postulación releída de la
     * base (QA-04, QA-05).
     *
     * <p><b>Para quien la cargó antes de esperar</b> —la entrega, el ajuste a mano, la IA que
     * cierra— y después la va a escribir. Si un cambio de pesos confirmó mientras esperaba,
     * la fila ya no es la que tiene en memoria: el cambio le reescribió el grupo, y el equipo
     * pudo moverla o descartarla en ese rato. Lo que decida a partir de aquí —si sigue en la
     * etapa, si ya terminó, qué grupo le toca— lo decide con la fila de verdad.
     *
     * <p>Lo que la transacción tuviera pendiente se escribe antes de releer, para no perderlo,
     * y siempre después de la vacante: el orden vacante → postulación se mantiene.
     *
     * <p>Fuera de una transacción de escritura no hace nada, como los otros dos.
     */
    void bloquearParaCalificarYReleer(Postulacion postulacion);
}
