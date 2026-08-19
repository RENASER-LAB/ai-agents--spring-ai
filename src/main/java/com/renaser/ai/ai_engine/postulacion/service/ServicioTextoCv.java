package com.renaser.ai.ai_engine.postulacion.service;

/**
 * El currículum convertido a texto, en sus dos versiones.
 *
 * <p><b>Por qué existe.</b> La IA no puede leer el archivo tal como lo subió el candidato:
 * antes hay que ocultar foto, edad, sexo y estado civil (RF-41). Sin esta pieza el agente
 * {@code EVIDENCIA_CV} no puede correr, porque no tendría nada que leer que se pueda
 * enseñar.
 *
 * <p><b>Las dos versiones se guardan.</b> {@code cv.texto_extraido} es el texto completo,
 * que solo ve el equipo (RF-42); {@code cv.texto_anonimizado} es el recortado, y es el
 * único que sale hacia el modelo. Guardar los dos es lo que permite demostrar después que
 * la regla se cumplió.
 *
 * <p>La foto no necesita borrarse: al pasar el archivo a texto las imágenes se quedan
 * fuera solas, así que a la IA nunca le llega una cara.
 */
public interface ServicioTextoCv {

    /**
     * Deja listo el texto anonimizado del currículum de esta postulación y lo devuelve.
     *
     * <p>Se hace una sola vez: si ya estaba hecho, devuelve lo guardado. Es a propósito,
     * porque un reintento de la IA no debe volver a leer el archivo del disco.
     *
     * @throws IllegalStateException si no hay currículum, si el archivo ya se borró o si
     *                               del archivo no se pudo sacar texto. <b>Nunca devuelve
     *                               vacío para salir del paso</b>: sin texto no hay nota, y
     *                               el trabajo de IA queda pendiente en vez de inventarse
     *                               una (Regla 3 del doc 03).
     */
    String prepararParaIa(Long postulacionId);
}
