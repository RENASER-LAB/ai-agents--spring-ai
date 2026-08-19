package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoDatos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDatos;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Saca los datos del candidato del currículum: quién es, cómo se le escribe, cuánto lleva
 * trabajando. <b>No puntúa nada.</b>
 *
 * <p>Existe porque el panel enseñaba notas y explicaciones pero ningún dato de la persona, y
 * para saber a quién llamar hacía falta abrir el PDF. Con esto la tanda se ve entera.
 *
 * <p><b>Nunca razona.</b> Copiar un nombre de un texto no exige deliberar, y es el
 * razonamiento lo que cuesta los segundos: en los agentes que sí puntúan son cuatro de cada
 * cinco tokens que el modelo escribe. Por eso este agente tarda un puñado de segundos y los
 * otros decenas.
 *
 * <p>Lee la misma versión recortada del currículum que los demás, así que la edad, el sexo y
 * el estado civil no le llegan aunque estuvieran en el archivo.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgenteDatosCv implements AgenteSeleccion {

    public static final String CODIGO = "DATOS_CV";

    private static final String OBJETIVO = "Sacar los datos del candidato del currículum";

    // Compacto a propósito: lo que el modelo escribe es lo que cuesta el tiempo. Ni
    // explicaciones ni justificaciones, que aquí no aportan nada y triplicarían la espera.
    public static final String FORMATO = """
            Responde SOLO con un objeto json con esta forma exacta:
            {
              "nombre": "<nombre completo, o null>",
              "email": "<correo, o null>",
              "telefono": "<telefono, o null>",
              "perfilResumen": "<una sola oracion sobre su perfil profesional>",
              "habilidades": ["<hasta cinco, las mas relevantes para el puesto>"],
              "experienciaMesesTotal": <numero de meses, o null si no se puede calcular>,
              "ultimoPuesto": "<el puesto mas reciente, o null>",
              "ultimaEmpresa": "<la empresa mas reciente, o null>",
              "ultimaMesesDuracion": <cuantos meses duro, o null>,
              "educacionMaxima": "<el nivel mas alto alcanzado, o null>"
            }
            Si un dato no esta en el curriculum, pon null. No lo deduzcas y no lo inventes.
            No agregues ningun campo mas, ni explicaciones, ni comentarios.
            """;

    private final PuenteCalificacionIa puente;
    private final EjecutorAgenteIa ejecutor;

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public void ejecutar(TrabajoIa trabajo) {
        InsumoDatos insumo = puente.insumoDatos(trabajo.getPostulacionId());
        log.info("DATOS_CV lee el currículum de la postulación {} ({} caracteres)",
                trabajo.getPostulacionId(), insumo.curriculum().length());

        // false: este agente nunca razona, sea cual sea la pasada. No hay nada que deliberar.
        EjecutorAgenteIa.Ejecutado<ResultadoDatos> salida =
                ejecutor.ejecutar(trabajo, OBJETIVO, FORMATO, insumo, ResultadoDatos.class, false);
        puente.guardarDatos(trabajo.getPostulacionId(), salida.ejecucionIaId(), salida.resultado());
    }
}
