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
 * <p><b>Tres corren a la vez y el cuarto espera a los tres.</b> Leer los datos del
 * currículum, puntuarlo y calificar las respuestas de la evaluación son tres cosas
 * independientes: leen fuentes distintas y escriben tablas distintas. El Perfil de Talento sí
 * necesita lo que dejaron los tres, y por eso va después. En fila costaba ocho minutos y
 * medio por candidato; a la vez cuesta lo que cueste el más lento.
 *
 * <p><b>Un paso que falla no para a los demás.</b> Antes sí: un currículum escaneado del que
 * no sale texto cortaba la fila y dejaba al candidato en {@code PERFIL_CALIFICANDO} para
 * siempre, con su examen de cincuenta preguntas ya calificado y sin nadie que lo resumiera.
 * Ahora el retrato se arma igual, con lo que sí se pudo leer, y queda escrito en el registro
 * qué faltaba. Lo único que no se hace es armarlo cuando no salió bien ni un solo paso: sobre
 * la nada no hay retrato que armar.
 *
 * <p><b>Si la IA falla se reintenta y nunca se inventa una nota</b> (Regla 3 del doc 03). Un
 * paso que se agota en reintentos no deja nota ninguna, y lo que dependiera solo de él se
 * queda sin llenar.
 */
public interface ColaCalificacionIa {

    /**
     * Arranca la calificación de una postulación que acaba de entregar su evaluación.
     *
     * <p>Es idempotente: llamarla dos veces no duplica trabajos ni recalifica lo ya hecho.
     *
     * <p><b>No se encola todo siempre.</b> Si el currículum ya se leyó en una criba, esa
     * parte no se repite: se encola solo lo que de verdad falta, que en ese caso es el
     * evaluador. Y si no falta nada pero el retrato se quedó sin hacer, se pide el retrato:
     * antes esos candidatos se calificaban para siempre, porque el primer paso ya estaba
     * hecho y no se encolaba nada.
     *
     * @return true si quedó algo en la cola; false si no había nada pendiente que hacer o si
     *         ya hay un trabajo vivo. Quien lo llame no debe decir «encolado» sin mirarlo.
     */
    boolean encolarPerfilIntegral(Long postulacionId);

    /**
     * Vuelve a calificar las respuestas AUNQUE ya estén calificadas.
     *
     * <p>Es la herramienta de calibración: cuando cambia una señal de 0 o un peso, las
     * calificaciones viejas son las del instrumento viejo, y {@code encolarPerfilIntegral}
     * las vería TERMINADAS y no haría nada. Esto crea el trabajo del evaluador igual; al
     * terminar, la barrera rehace el Perfil de Talento como siempre.
     *
     * @return true si quedó en la cola; false si hay uno vivo ahora mismo (no se paga dos
     *         veces) o la calificación está apagada.
     */
    boolean reencolarEvaluador(Long postulacionId);

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
     *
     * @return true si quedó algo en la cola
     */
    boolean encolarCribaCv(Long postulacionId);

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
     *
     * @return true si quedó algo en la cola
     */
    boolean encolarCribaRapida(Long postulacionId);

    /**
     * Segunda pasada: cuidadosa, solo sobre los de arriba.
     *
     * <p>Vuelve a puntuar con el modelo que razona y rehace el Perfil de Talento. Es la que
     * manda: pisa las notas de la primera, que eran provisionales.
     *
     * <p>Se pide por separado y no se encadena a la primera a propósito. Cuál es «arriba»
     * depende de cómo salió la tanda entera, y eso no se sabe hasta que la primera termina.
     *
     * @return true si quedó algo en la cola
     */
    boolean encolarCribaFina(Long postulacionId);

    /**
     * Pide que la IA califique la prueba del puesto que el candidato ya entregó.
     *
     * <p><b>No se encadena a nada.</b> Corre sola, y no entra ni sale de la fila del Perfil
     * Integral: lo que califica es otra etapa, con otra rúbrica y otra escala. Por eso
     * tampoco cuenta para {@link #comoVa}, que sigue hablando solo del retrato del candidato.
     *
     * <p><b>Se pide, no se dispara sola al entregar.</b> Es la misma decisión que ya se tomó
     * con la criba de currículums: cada llamada al modelo cuesta dinero, y quién y cuándo se
     * califica es de quien lleva la vacante. Entregar la prueba deja la postulación en
     * «calificando», y desde ahí una persona puede pedir esto o calificarla a mano.
     *
     * @return true si quedó algo en la cola; false si ya está calificada o hay un trabajo vivo
     */
    /**
     * Pide SOLO la lectura de datos del currículum (agente DATOS_CV), sin calificar nada.
     *
     * <p>Existe para el perfil del candidato: al postular se lee el currículum para
     * proponerle sus datos, y calificar sigue siendo una decisión aparte (y de pago) que
     * toma el panel. Idempotente como los demás: si esa postulación ya tiene su ficha
     * leída, no se paga otra lectura.
     */
    boolean encolarDatosCv(Long postulacionId);

    boolean encolarPruebaPuesto(Long postulacionId);

    /**
     * Pide las preguntas de la conversación final de la simulación.
     *
     * <p>No califica nada: prepara el guion de los quince minutos finales de la sesión. Se
     * puede pedir varias veces —después de ajustar una nota, por ejemplo— y las preguntas
     * que ya se hicieron y se contestaron se quedan como estaban.
     *
     * @return true si quedó algo en la cola
     */
    boolean encolarPreguntasSimulacion(Long postulacionId);

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
     * <p><b>Se mira solo la última pasada.</b> Una postulación puede tener una pasada rápida
     * terminada y una fina fallida; mirarlas juntas diría «terminada» y presentaría como
     * definitivas unas notas que son provisionales. Lo que vale es cómo fue el último intento.
     *
     * @return {@code EN_CURSO} si queda algún trabajo vivo, {@code FALLIDA} si la última
     *         pasada se agotó en reintentos, {@code TERMINADA} si llegó al final, o
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
     * En qué punto va <b>solo la lectura del currículum</b> (agente {@code DATOS_CV}) de una
     * postulación.
     *
     * <p>Existe porque {@link #comoVa} contesta otra pregunta: cómo va el RETRATO, mirando
     * los cuatro agentes juntos. Servía para el ranking y no sirve para esto — un evaluador
     * que falla dejaba «FALLIDA» aunque el currículum se hubiera leído perfectamente, y un
     * retrato terminado sin ficha nunca se distinguía de uno en marcha. El perfil del
     * candidato le enseña al dueño en qué punto está SU archivo, y ahí esas dos respuestas
     * no son intercambiables.
     *
     * @return SIN_EMPEZAR (nadie la ha pedido), EN_CURSO, TERMINADA o FALLIDA
     */
    String comoVaLaLectura(Long postulacionId);

    /**
     * En qué punto va la calificación de un candidato.
     *
     * @param comoVa SIN_EMPEZAR, EN_CURSO, TERMINADA o FALLIDA
     * @param pasada FINA, RAPIDA o vacío si todavía no hay retrato
     */
    record Estado(String comoVa, String pasada) {
    }
}
