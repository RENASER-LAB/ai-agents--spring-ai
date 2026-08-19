package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Arma el Perfil de Talento (RF-65) y cierra la etapa.
 *
 * <p><b>El resultado no es una nota, es un retrato.</b> Adecuación, potencial, alto
 * rendimiento y —la que más importa— confianza de la evidencia, que es lo que distingue a
 * quien fue evaluado a fondo de quien apenas dejó rastro. Más los hallazgos, que la Regla 1
 * del doc 03 prohíbe mezclar entre sí.
 *
 * <p>Corre el último porque no lee nada nuevo: solo mira lo que ya calificaron el currículum,
 * las preguntas cerradas (por código) y las abiertas.
 *
 * <p><b>No decide si alguien se contrata.</b> Al terminar, la postulación pasa a
 * {@code PERFIL_POR_CONFIRMAR}, que es donde una persona mira y decide.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentePotencialRiesgo implements AgenteSeleccion {

    public static final String CODIGO = "POTENCIAL_RIESGO";

    private static final String OBJETIVO = "Armar el Perfil de Talento a partir de lo ya calificado";

    // Público: ver la nota en AgenteEvidenciaCv
    public static final String FORMATO = """
            Responde SOLO con un objeto json con esta forma exacta:
            {
              "adecuacion": <numero de 0 a 100: encaje con ESTE puesto>,
              "potencial": <numero de 0 a 100: hasta donde puede llegar>,
              "altoRendimiento": <numero de 0 a 100: senales de rendimiento sostenido>,
              "confianzaEvidencia": <numero de 0 a 100: cuanta evidencia real hubo. Obligatorio>,
              "resumen": "<el retrato en tres o cuatro frases, en lenguaje normal>",
              "hallazgos": [
                {"tipo": "FORTALEZA|RIESGO_CRITICO|RIESGO_DESARROLLABLE|PREFERENCIA|FALTA_EVIDENCIA",
                 "descripcion": "<que es>",
                 "evidencia": "<en que te basas>",
                 "esCanalizable": true|false,
                 "sugerencia": "<que hacer con eso, o null>"}
              ],
              "alertas": [
                {"tipo": "DEMASIADO_IDEAL", "descripcion": "<que llama la atencion>"}
              ]
            }
            Reglas del formato:
            - confianzaEvidencia nunca puede faltar.
            - Los cinco tipos de hallazgo no se mezclan. Un riesgo desarrollable es algo que
              la persona hace mal y se puede corregir; una falta de evidencia es algo que no
              sabemos. No son lo mismo.
            - "alertas" solo admite DEMASIADO_IDEAL. Las contradicciones las detecta el
              sistema comparando respuestas, no tu.
            """;

    private final PuenteCalificacionIa puente;
    private final EjecutorAgenteIa ejecutor;

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public void ejecutar(TrabajoIa trabajo) {
        InsumoPerfil insumo = puente.insumoPerfil(trabajo.getPostulacionId());
        log.info("POTENCIAL_RIESGO arma el perfil de la postulación {} (currículum={}, "
                        + "cerradas={}, abiertas={})", trabajo.getPostulacionId(),
                insumo.notaCurriculum(), insumo.notaPreguntasCerradas(),
                insumo.notaRespuestasAbiertas());

        EjecutorAgenteIa.Ejecutado<ResultadoPerfil> salida =
                ejecutor.ejecutar(trabajo, OBJETIVO, FORMATO, insumo, ResultadoPerfil.class,
                        !ColaCalificacionIaImpl.RAPIDA.equals(trabajo.getModo()));
        puente.cerrarPerfilIntegral(trabajo.getPostulacionId(), salida.ejecucionIaId(),
                salida.resultado());
    }
}
