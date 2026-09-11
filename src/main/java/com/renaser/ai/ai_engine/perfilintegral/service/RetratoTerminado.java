package com.renaser.ai.ai_engine.perfilintegral.service;

/**
 * La calificación del Perfil Integral de esta postulación acaba de terminar y guardarse.
 *
 * <p>⚠️ <b>Es el único evento de Spring del proyecto, y eso es a propósito.</b> Aquí todo lo
 * demás se llama por su nombre, con la dependencia escrita en el constructor y vigilada por
 * las reglas de arquitectura. Un evento es lo contrario: quien lo publica no sabe quién
 * escucha, y por eso hay que decir aquí en qué consiste todo el mecanismo.
 *
 * <p><b>Quién lo publica:</b> {@code PuenteCalificacionIaImpl}, al cerrar el Perfil
 * Integral, después de guardar el retrato y la nota.
 *
 * <p><b>Quién lo escucha:</b> {@code PaseAutomaticoTrasCalificar}, y nadie más.
 *
 * <p><b>Por qué no es una llamada normal.</b> Avanzar a la etapa técnica exige crear lo que
 * el candidato va a rendir, y eso pasa por {@code ServicioEvaluacion}, que a su vez usa la
 * cola de la IA, que a su vez usa el puente. Es un círculo cerrado, y Spring se niega a
 * arrancar con uno. El evento lo rompe por donde no duele: <b>a quien escucha no lo inyecta
 * nadie</b>, así que es una hoja del grafo y puede depender de lo que quiera.
 *
 * <p>⚠️ <b>El día que alguien inyecte al que escucha desde otro sitio, vuelve el círculo y
 * la aplicación deja de arrancar.</b> Si hace falta llamarlo desde otro lado, lo que se
 * mueve es la lógica a un colaborador aparte, no la dependencia.
 *
 * @param postulacionId a quién se acaba de calificar. Viaja el id y no la entidad: quien
 *                      escucha corre después del commit y tiene que releerla, no arrastrar
 *                      una copia vieja al otro lado de la frontera de la transacción.
 */
public record RetratoTerminado(Long postulacionId) {
}
