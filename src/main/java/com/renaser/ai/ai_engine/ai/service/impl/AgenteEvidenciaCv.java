package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoCv;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoCv;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Puntúa el currículum con los ocho criterios (RF-43).
 *
 * <p><b>Lo que lee no es el currículum entero.</b> Es la versión recortada, sin foto, edad,
 * sexo ni estado civil, que prepara el módulo de selección antes de dárselo (RF-41). Este
 * agente no tiene forma de pedir el original.
 *
 * <p>El peso de cada criterio cambia según el nivel del puesto y viaja en el propio insumo,
 * para que el modelo sepa qué se está mirando con más atención. La cuenta final, en cambio,
 * la hace el código: el modelo pone las ocho notas, no la nota.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgenteEvidenciaCv implements AgenteSeleccion {

    public static final String CODIGO = "EVIDENCIA_CV";

    private static final String OBJETIVO =
            "Puntuar el currículum contra los ocho criterios y clasificar sus afirmaciones";

    // El formato se pega a la instrucción que administra Dirección. Va aquí y no en la base
    // porque no es una decisión de negocio: es el contrato técnico con el lector de JSON, y
    // si alguien lo edita desde el panel sin querer, nada se puede guardar.
    // Público a propósito: es el contrato de este agente con el modelo, y la prueba que
    // llama a DeepSeek de verdad tiene que mandar exactamente esto y no una copia parecida.
    public static final String FORMATO = """
            Responde SOLO con un objeto json con esta forma exacta:
            {
              "criterios": [
                {"codigo": "<el codigo del criterio, tal cual>",
                 "puntaje": <numero de 0 a 100>,
                 "explicacion": "<por que esa nota, en una o dos frases>",
                 "evidencia": "<la frase del curriculum en la que te basas>"}
              ],
              "afirmaciones": [
                {"texto": "<lo que el candidato afirma>",
                 "clasificacion": "DEMOSTRADA|DECLARADA|CONTRADICHA|FALTA_INFO",
                 "preguntaValidacion": "<que habria que preguntarle, o null>"}
              ],
              "confianza": <numero de 0 a 100: cuanto te fias de lo que acabas de leer>
            }
            Pon una entrada por cada criterio que recibas, ni mas ni menos. Si de alguno no
            hay nada en que basarse, dilo en la explicacion y baja la confianza; no lo omitas
            y no le pongas cero por no saber.
            """;

    private final PuenteCalificacionIa puente;
    private final EjecutorAgenteIa ejecutor;

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public void ejecutar(TrabajoIa trabajo) {
        InsumoCv insumo = puente.insumoCv(trabajo.getPostulacionId());
        log.info("EVIDENCIA_CV lee el currículum de la postulación {} ({} caracteres, {} criterios)",
                trabajo.getPostulacionId(), insumo.curriculum().length(), insumo.criterios().size());

        EjecutorAgenteIa.Ejecutado<ResultadoCv> salida =
                ejecutor.ejecutar(trabajo, OBJETIVO, FORMATO, insumo, ResultadoCv.class,
                        !ColaCalificacionIaImpl.RAPIDA.equals(trabajo.getModo()));
        puente.guardarEvidenciaCv(trabajo.getPostulacionId(), salida.ejecucionIaId(),
                salida.resultado());
    }
}
