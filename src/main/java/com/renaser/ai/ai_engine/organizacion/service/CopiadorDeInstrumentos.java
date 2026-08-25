package com.renaser.ai.ai_engine.organizacion.service;

import java.util.Map;

/**
 * La copia que ejecuta «encender una bandera»: el instrumento publicado de la plataforma
 * pasa a ser de la empresa, tal cual está hoy.
 *
 * <p>Se copia lo PUBLICADO y la copia nace PUBLICADA — sin limbo: la empresa personaliza
 * partiendo de un método que ya funciona, y sus vacantes siguen evaluando sin cortes.
 * Cada copia guarda {@code copiada_de_version_id} para saber de qué versión salió. Las
 * evaluaciones en vuelo no se ven afectadas: sus preguntas están fijadas por id.
 *
 * <p>Devuelve cuántas filas se copiaron por tabla: es lo que se audita, y lo que las
 * pruebas comparan — una copia que pierde filas por el camino no avisa de otra forma.
 */
public interface CopiadorDeInstrumentos {

    /** El banco completo: cada versión publicada con sus preguntas, claves y reglas. */
    Map<String, Integer> copiarBanco(Long organizacionDestino);

    /** La última versión publicada de pesos, con sus cuatro repartos. */
    Map<String, Integer> copiarPesos(Long organizacionDestino);

    /** Las plantillas de evaluación publicadas, con sus cuotas. */
    Map<String, Integer> copiarPlantillasEvaluacion(Long organizacionDestino);

    /** Las pruebas del puesto: cada plantilla activa con su última versión publicada. */
    Map<String, Integer> copiarPruebas(Long organizacionDestino);
}
