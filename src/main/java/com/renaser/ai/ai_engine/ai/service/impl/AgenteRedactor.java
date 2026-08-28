package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.AgenteSeleccion;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.InsumoRedactor;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.ResultadoRedactor;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteRedactor;
import com.renaser.ai.ai_engine.perfilintegral.service.RecetaCuestionarioTecnico;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redacta el borrador del cuestionario técnico de una vacante desde su ficha (etapa 2 del
 * método CAZATALENTOS, ver docs/DISENO-PRUEBA-TECNICA-FICHA-Y-REDACTOR.md).
 *
 * <p><b>El único agente que no trabaja sobre una postulación:</b> su referencia es la
 * vacante. Y el único cuyo texto no entra en ninguna nota — la regla del sistema que lo
 * hace admisible: lo que escribe no toca a ningún candidato hasta que el dueño lo publica.
 *
 * <p><b>La aduana manda.</b> Lo que el modelo devuelve pasa por la receta
 * ({@code RecetaCuestionarioTecnico.validar}); si no cuadra, se le devuelve UNA vez con
 * los errores delante, y si persiste, el trabajo falla — un borrador a medias jamás se
 * guarda como si estuviera bien.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgenteRedactor implements AgenteSeleccion {

    public static final String CODIGO_AGENTE = "REDACTOR";

    private static final String OBJETIVO =
            "Redactar el borrador del cuestionario técnico de la vacante";

    public static final String FORMATO = """
            Responde SOLO con un objeto json con esta forma exacta:
            {
              "preguntas": [
                {"codigo": "T01",
                 "bloque": "<el bloque de la estructura, tal cual te llego: EXPERIENCIA,
                            RIESGO_1, RIESGO_2, RIESGO_3, REQUERIMIENTO, DILEMA o PRESENCIAL>",
                 "bloqueEtiqueta": "<como lo vera el dueño, p. ej. 'Riesgo 1 — Caja y efectivo'>",
                 "enunciado": "<la pregunta, con el vocabulario del rubro de la ficha>",
                 "c3Esperado": "<el dato duro que debe aparecer en una buena respuesta>",
                 "c4Esperado": "<la parte incomoda que quien lo vivio menciona>",
                 "senalDeCero": "<la respuesta que vale 0 por si sola>",
                 "presencial": <true SOLO en el bloque PRESENCIAL>}
              ]
            }
            Genera EXACTAMENTE las preguntas que pide la estructura, en su orden, con
            codigos T01, T02... En el bloque PRESENCIAL deja c3Esperado, c4Esperado y
            senalDeCero en null: la muestra de trabajo no se califica en el formulario.
            """;

    private final PuenteRedactor puente;
    private final EjecutorAgenteIa ejecutor;

    @Override
    public String codigo() {
        return CODIGO_AGENTE;
    }

    @Override
    public void ejecutar(TrabajoIa trabajo) {
        Long vacanteId = trabajo.getReferenciaId();
        InsumoRedactor insumo = puente.insumo(vacanteId);
        log.info("REDACTOR escribe el cuestionario {} de la vacante {} ({} preguntas)",
                insumo.nivel(), vacanteId, RecetaCuestionarioTecnico.totalPreguntas(insumo.nivel()));

        EjecutorAgenteIa.Ejecutado<ResultadoRedactor> salida =
                ejecutor.ejecutar(trabajo, OBJETIVO, FORMATO, insumo, ResultadoRedactor.class);
        List<String> errores = validar(insumo, salida.resultado());

        if (!errores.isEmpty()) {
            // Una segunda oportunidad con los errores delante. Una sola: si tampoco así
            // cuadra, que el trabajo falle y se vea — reintentar a ciegas es pagar por
            // el mismo error.
            log.warn("El borrador de la vacante {} no pasó la aduana ({} errores); se le "
                    + "devuelve al modelo una vez", vacanteId, errores.size());
            salida = ejecutor.ejecutar(trabajo, OBJETIVO,
                    FORMATO + conLosErrores(errores), insumo, ResultadoRedactor.class);
            errores = validar(insumo, salida.resultado());
        }
        if (!errores.isEmpty()) {
            throw new IllegalStateException("El borrador no pasó la aduana tras la "
                    + "corrección: " + String.join(" · ", errores));
        }

        puente.guardarBorrador(vacanteId, salida.resultado());
    }

    private static List<String> validar(InsumoRedactor insumo, ResultadoRedactor resultado) {
        if (resultado == null || resultado.preguntas() == null) {
            return List.of("el modelo no devolvió ninguna pregunta");
        }
        return RecetaCuestionarioTecnico.validar(insumo.nivel(), resultado.preguntas());
    }

    private static String conLosErrores(List<String> errores) {
        return "\nTu intento anterior tuvo estos errores. Corrígelos TODOS y responde de "
                + "nuevo el json completo:\n- " + String.join("\n- ", errores) + "\n";
    }
}
