package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoRespuestas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoEvaluador;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Califica las respuestas del cuestionario técnico de una vacante (etapa 2 del método).
 *
 * <p>Hace lo mismo que {@link AgenteEvaluador} con el banco CAZATALENTOS —contar los cuatro
 * criterios, sin poner el número— y por eso <b>reutiliza su {@code FORMATO_CRITERIOS} tal
 * cual</b>: el contrato con el modelo es el mismo y duplicarlo sería tener dos versiones de
 * la misma regla, que es como acaban divergiendo.
 *
 * <p>⚠️ <b>Entonces, ¿por qué una clase aparte y no el mismo agente?</b> Porque el código del
 * agente es la llave con la que la cola ordena su trabajo, y compartirlo rompía tres cosas a
 * la vez, las tres en silencio:
 *
 * <ol>
 *   <li>{@code RegistroTrabajosIa.crearSiHaceFalta} busca el <b>último</b> trabajo de
 *       {@code (postulación, agente, modo)} para no repetir lo hecho. Con los dos exámenes
 *       bajo el mismo código, encolar el del perfil integral encontraría el técnico ya
 *       terminado y <b>no correría nunca</b>: una postulación sin nota de currículum y sin
 *       ningún error a la vista.
 *   <li>{@code A_LA_VEZ} incluye al evaluador: la barrera que espera a que todos acaben para
 *       armar el Perfil de Talento contaría un trabajo que no es suyo, y se dispararía antes
 *       o después de tiempo.
 *   <li>{@code seSalta} da por hecho que si no hay evaluación entregada no hay nada que
 *       calificar, mirando la columna del perfil integral. En una vacante con la evaluación
 *       del banco apagada —legal, y probablemente lo normal con el cuestionario técnico— se
 *       tragaría este trabajo con un mensaje de nivel informativo.
 * </ol>
 *
 * <p>Un código propio los resuelve por construcción: carril de deduplicación aparte, fuera de
 * la barrera y fuera de ese atajo. Lo que cuesta son estas treinta líneas.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgenteCuestionarioTecnico implements AgenteSeleccion {

    public static final String CODIGO_AGENTE = "EVALUADOR_TECNICO";

    private static final String OBJETIVO =
            "Calificar las respuestas del cuestionario técnico contando los cuatro criterios";

    private final EjecutorAgenteIa ejecutor;
    private final PuenteCalificacionIa puente;

    @Override
    public String codigo() {
        return CODIGO_AGENTE;
    }

    @Override
    public void ejecutar(TrabajoIa trabajo) {
        InsumoRespuestas insumo = puente.insumoRespuestasTecnicas(trabajo.getPostulacionId());
        if (insumo.respuestas().isEmpty()) {
            // Se entregó sin contestar nada: no hay nada que leerle al modelo, pero sí hay
            // nota que poner —todo ceros—, y de eso se encarga la calificación de la etapa.
            log.info("EVALUADOR_TECNICO: la postulación {} entregó el cuestionario sin una sola "
                    + "respuesta escrita; no se llama al modelo", trabajo.getPostulacionId());
            puente.cerrarNotaTecnica(trabajo.getPostulacionId());
            return;
        }
        log.info("EVALUADOR_TECNICO califica {} respuestas de la postulación {}",
                insumo.respuestas().size(), trabajo.getPostulacionId());

        // El mismo contrato que el banco CAZATALENTOS: el modelo declara los criterios y el
        // código cuenta. Un cuestionario de vacante siempre es CRITERIOS (lo fija la V42), así
        // que aquí no hay nada que elegir.
        EjecutorAgenteIa.Ejecutado<ResultadoEvaluador> salida = ejecutor.ejecutar(
                trabajo, OBJETIVO, AgenteEvaluador.FORMATO_CRITERIOS, insumo,
                ResultadoEvaluador.class);
        puente.guardarNotasTecnicas(trabajo.getPostulacionId(), salida.ejecucionIaId(),
                salida.resultado());
    }
}
