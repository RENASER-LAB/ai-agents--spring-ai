package com.renaser.ai.ai_engine.ai.service;

/**
 * La cola que hace que una postulación se califique sola.
 *
 * <p>Cuando el candidato entrega su evaluación, el código puntúa lo cerrado al momento y la
 * postulación queda en {@code PERFIL_CALIFICANDO}. Lo que falta —leer el currículum,
 * calificar lo abierto y armar el Perfil de Talento— tarda decenas de segundos y depende de
 * un servicio externo, así que no puede hacerse dentro de la petición del candidato. De eso
 * se encarga esto.
 *
 * <p><b>Los tres agentes corren en fila, no a la vez.</b> El Perfil de Talento necesita lo
 * que dejaron los otros dos, así que igual habría que esperarlos; y en fila hay un solo
 * trabajo vivo por postulación, que es lo que hace que reintentar sea trivial en vez de
 * tener que decidir qué hacer cuando uno de dos paralelos falla.
 *
 * <p><b>Si la IA falla se reintenta y nunca se inventa una nota</b> (Regla 3 del doc 03). La
 * postulación se queda en {@code PERFIL_CALIFICANDO} hasta que haya resultado de verdad.
 */
public interface ColaCalificacionIa {

    /**
     * Arranca la calificación de una postulación que acaba de entregar su evaluación.
     *
     * <p>Es idempotente: llamarla dos veces no duplica trabajos ni recalifica lo ya hecho.
     */
    void encolarPerfilIntegral(Long postulacionId);

    /**
     * Arranca la criba: leer el currículum y armar el Perfil de Talento con solo eso.
     *
     * <p>Es el mismo recorrido que el de arriba, pero para quien todavía no ha respondido
     * nada. Sirve para ordenar una tanda de currículums recién llegados y ver a quién vale
     * la pena invitar a la evaluación, que es la primera decisión real de una convocatoria.
     *
     * <p><b>El evaluador se salta solo</b>: sin respuestas no tiene qué puntuar. Y la nota
     * del Perfil Integral sale entonces del currículum a solas, porque el reparto entre
     * componentes reparte solo lo que existe.
     *
     * <p>Igual de idempotente: pedirla dos veces no duplica trabajos. Y si más tarde el
     * candidato entrega su evaluación, {@link #encolarPerfilIntegral} recalifica con todo.
     */
    void encolarCribaCv(Long postulacionId);

    /**
     * Primera pasada: rápida, sobre todos.
     *
     * <p>Saca los datos del candidato y lo puntúa con el modelo que <b>no razona</b>. Una
     * tanda de diez tarda medio minuto en vez de veinte, y sirve para lo que hace falta
     * aquí: ordenar y separar la mitad de abajo, donde la decisión es fácil.
     *
     * <p>No sirve para decidir a quién se contrata. Medido sobre los mismos diez
     * currículums, solo tres quedan en la misma posición que con el modelo que razona, y
     * este ve menos riesgos críticos. Para eso está la segunda.
     */
    void encolarCribaRapida(Long postulacionId);

    /**
     * Segunda pasada: cuidadosa, solo sobre los de arriba.
     *
     * <p>Vuelve a puntuar con el modelo que razona y rehace el Perfil de Talento. Es la que
     * manda: pisa las notas de la primera, que eran provisionales.
     *
     * <p>Se pide por separado y no se encadena a la primera a propósito. Cuál es «arriba»
     * depende de cómo salió la tanda entera, y eso no se sabe hasta que la primera termina.
     */
    void encolarCribaFina(Long postulacionId);

    /**
     * Ejecuta un trabajo concreto. Lo llama el listener de la cola, y también el sondeo.
     *
     * <p>No lanza excepción: el resultado —bien, a reintentar o fallido— queda escrito en
     * {@code trabajo_ia} y en {@code ejecucion_ia}.
     */
    void ejecutar(Long trabajoIaId);

    /**
     * Vuelve a empujar lo que se quedó atascado: mensajes que se perdieron y trabajos que
     * alguien tomó y no terminó porque el proceso murió a mitad.
     */
    void reintentarAtascados();

    /**
     * En qué punto va la calificación de una postulación, según sus trabajos.
     *
     * <p>Existe para que quien pregunte no tenga que mirar {@code trabajo_ia} por su cuenta:
     * la cola es la única que sabe cuántos agentes van en fila y cuál toca ahora.
     *
     * <p><b>El estado de la postulación no sirve como señal.</b> Solo pasa a
     * {@code PERFIL_CALIFICANDO} cuando el candidato entrega su evaluación, así que una
     * calificación pedida desde el panel corre sin que ese estado cambie: preguntarle a la
     * postulación diría «no hay nada» mientras los tres agentes están trabajando.
     *
     * @return {@code EN_CURSO} si queda algún trabajo vivo, {@code FALLIDA} si el último se
     *         agotó en reintentos, {@code TERMINADA} si los tres acabaron, o
     *         {@code SIN_EMPEZAR} si nadie ha pedido nada todavía.
     */
    String comoVa(Long postulacionId);

    /**
     * Con qué pasada está calificado ahora mismo: {@code FINA}, {@code RAPIDA} o vacío.
     *
     * <p>Lo pide la pantalla para no enseñar como definitivo lo que todavía es provisional.
     * Una nota de la pasada rápida y una de la fina se ven igual —un número— y no valen lo
     * mismo, así que hay que poder distinguirlas.
     */
    String pasadaDe(Long postulacionId);

    /**
     * Lo mismo que {@link #comoVa} y {@link #pasadaDe}, pero de una tanda entera y en una
     * sola consulta.
     *
     * <p><b>Existe por una razón de peso, no por elegancia.</b> El ranking pinta una fila
     * por candidato y preguntaba dos veces por cada uno; con cien postulantes eran
     * doscientas consultas solo para dos columnas, en la pantalla que existe justamente
     * para mirar la tanda completa.
     *
     * @return una entrada por postulación pedida, siempre; nunca falta ninguna
     */
    java.util.Map<Long, Estado> estadoDe(java.util.List<Long> postulacionIds);

    /**
     * En qué punto va la calificación de un candidato.
     *
     * @param comoVa SIN_EMPEZAR, EN_CURSO, TERMINADA o FALLIDA
     * @param pasada FINA, RAPIDA o vacío si todavía no hay retrato
     */
    record Estado(String comoVa, String pasada) {
    }
}
