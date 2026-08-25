package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.DesgloseEvaluacion;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

/**
 * El desglose de la evaluación del banco, para el panel.
 *
 * <p>Hasta ahora la nota de la evaluación solo salía mezclada dentro de la nota de la etapa
 * del Perfil Integral (28 puntos de 40 en la versión de pesos vigente), y el equipo no tenía
 * dónde ver <b>por qué</b>: qué respondió el candidato en las abiertas, qué citó la IA como
 * evidencia, y cómo le fue en lo cerrado.
 *
 * <p>Es de solo lectura, como el resto de lo que ve el panel: ajustar una nota tiene su
 * propio camino con motivo y rastro.
 */
public interface ServicioDesgloseEvaluacion {

    DesgloseEvaluacion ver(ContextoUsuario quien, Long postulacionId);
}
