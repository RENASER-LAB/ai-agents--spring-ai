package com.renaser.ai.ai_engine.ai.prompt.impl;

import com.renaser.ai.ai_engine.ai.prompt.PoliticaDeterminismo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Reparte la temperatura entre los agentes, y deja escrito hasta dónde llega eso.
 *
 * <h2>Lo que se arregla</h2>
 *
 * <p>Los agentes que ponen un número —{@code DATOS_CV}, {@code EVIDENCIA_CV},
 * {@code EVALUADOR}, {@code POTENCIAL_RIESGO} y {@code PRUEBA_PUESTO}— van a <b>temperatura
 * cero</b>. A cero el modelo elige siempre la palabra más probable en vez de sortear entre
 * las candidatas, y eso es lo que hace que dos corridas del mismo currículum se parezcan. La
 * evidencia publicada es clara en la dirección: a temperatura 0 el mismo veredicto se repite
 * más del 95% de las veces, y a temperatura 1 baja al 70%.
 *
 * <h2>Lo que NO se arregla, y hay que decirlo</h2>
 *
 * <p><b>Ni a temperatura cero hay reproducibilidad exacta.</b> Esto no es un descuido del
 * código ni algo que se pueda apretar más desde aquí: son tres cosas que pasan dentro del
 * proveedor y que nosotros no tocamos.
 *
 * <ul>
 *   <li><b>Las sumas en coma flotante dependen del tamaño del lote.</b> El proveedor junta
 *       peticiones de clientes distintos para aprovechar la máquina, y sumar los mismos
 *       números en distinto orden da resultados que se separan en el último decimal. Ese
 *       último decimal, de vez en cuando, cambia qué palabra gana.
 *   <li><b>El enrutado de expertos del modelo.</b> Estos modelos no encienden toda la red
 *       para cada palabra: encienden unos pocos «expertos». A qué experto va cada palabra se
 *       decide con el lote entero delante, así que depende de con quién te tocó compartir
 *       viaje.
 *   <li><b>El reparto de carga entre réplicas.</b> La misma pregunta puede caer en máquinas
 *       distintas, con versiones de librerías o hardware que no suman exactamente igual.
 * </ul>
 *
 * <p>Ninguna de las tres está bajo nuestro control, y ninguna se arregla con configuración.
 * Por eso la frase que resume la literatura es que la temperatura cero es <b>necesaria pero
 * no suficiente</b>: sin ella no hay ninguna posibilidad de repetir un resultado, y con ella
 * hay una posibilidad muy alta, no una garantía.
 *
 * <p><b>Qué significa en la práctica para un reclamo.</b> Se puede prometer que dos corridas
 * del mismo currículum, con el mismo agente y el mismo modelo, van a coincidir casi siempre y
 * a quedar muy cerca cuando no coincidan. No se puede prometer que salga el mismo número al
 * decimal. Quien conteste un reclamo tiene que decirlo así, y lo que de verdad sostiene la
 * respuesta es la bitácora de {@code ejecucion_ia}: ahí queda qué se envió, qué contestó el
 * modelo y con qué instrucción, y eso sí es exacto.
 *
 * <h2>La semilla todavía no viaja</h2>
 *
 * <p>La semilla está fijada y es configurable, pero <b>hoy no llega al proveedor</b>: ni
 * {@code DeepSeekChatOptions} ni {@code DeepSeekApi.ChatCompletionRequest} de Spring AI 2.0
 * tienen campo {@code seed}, y la API de DeepSeek tampoco lo documenta entre sus parámetros.
 * Se deja escrita igualmente por dos motivos: para que el valor esté decidido y versionado el
 * día que el proveedor lo acepte, y para que nadie vuelva a preguntarse si se configuró.
 *
 * <p><b>No se puede leer esto como «las corridas están sembradas».</b> No lo están. Lo único
 * que hoy sostiene la repetibilidad es la temperatura cero, con el límite de arriba.
 *
 * <h2>Por qué no se toca top_p</h2>
 *
 * <p>DeepSeek documenta que se mueve {@code temperature} <b>o</b> {@code top_p}, no los dos:
 * son dos formas de estrechar la misma elección y usarlas juntas hace que ninguna de las dos
 * signifique lo que dice. El proyecto no fijaba {@code top_p} en ningún sitio y se deja así a
 * propósito. Si algún día hace falta, se quita la temperatura primero.
 */
@Component
public class PoliticaDeterminismoImpl implements PoliticaDeterminismo {

    /**
     * Los agentes que escriben para una persona, no para una hoja de cálculo.
     *
     * <p>{@code SIMULACION} redacta las tres a cinco preguntas de la conversación final. Su
     * salida no entra en ninguna nota: es un guion que alguien va a leer en voz alta. A
     * temperatura cero saldrían preguntas planas y, peor, calcadas entre candidatos, porque
     * ante insumos parecidos el modelo escogería siempre la misma formulación. Justo lo que
     * no sirve: si dos personas llegan a la conversación final con el mismo guion, la
     * conversación deja de distinguirlas.
     *
     * <p>{@code NARRATIVE_MESSAGE} es el del motor de agentes y hace lo mismo con los
     * mensajes de marca: convierte una intención ya aprobada en texto.
     *
     * <p>Se libran de la temperatura cero, no de la instrucción: los dos siguen obligados a
     * salir del insumo y a no inventar hechos. Eso lo sostiene el prompt, no la temperatura.
     */
    private static final Set<String> REDACTAN_PARA_UNA_PERSONA =
            Set.of("SIMULACION", "NARRATIVE_MESSAGE");

    private final double temperaturaPuntuacion;
    private final double temperaturaRedaccion;
    private final Integer semilla;

    public PoliticaDeterminismoImpl(
            @Value("${renaser.ai.chat.temperatura-puntuacion:0.0}") double temperaturaPuntuacion,
            @Value("${renaser.ai.chat.temperatura-redaccion:1.0}") double temperaturaRedaccion,
            @Value("${renaser.ai.chat.semilla:20260820}") Integer semilla) {
        this.temperaturaPuntuacion = temperaturaPuntuacion;
        this.temperaturaRedaccion = temperaturaRedaccion;
        this.semilla = semilla;
    }

    @Override
    public double temperaturaDe(String codigoAgente) {
        return redactaParaUnaPersona(codigoAgente) ? temperaturaRedaccion : temperaturaPuntuacion;
    }

    @Override
    public boolean redactaParaUnaPersona(String codigoAgente) {
        // Sin código conocido se cae del lado seguro: temperatura cero. Un agente nuevo que
        // nadie clasificó es, casi seguro, uno que puntúa; y si redacta, lo peor que pasa es
        // que sus textos salgan planos hasta que alguien lo añada a la lista de arriba.
        return codigoAgente != null && REDACTAN_PARA_UNA_PERSONA.contains(codigoAgente);
    }

    @Override
    public Integer semilla() {
        return semilla;
    }
}
