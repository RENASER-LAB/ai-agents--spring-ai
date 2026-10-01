package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.AjustarCriterio;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.NoCompleto;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.PruebaDelCandidato;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

/**
 * La prueba técnica escrita en el editor (V67) desde la ficha del candidato: el desglose por
 * criterio, el ajuste a mano de la parte calificada y la lista de quienes no la completaron.
 *
 * <p>No se mezcla con {@code ServicioCalificacionPrueba}, que es la de las plantillas: aquí
 * la nota de un criterio es su parte automática más su parte calificada, y se guarda por el
 * id del criterio de esa versión.
 */
public interface ServicioCalificacionPruebaPropia {

    /** La prueba de un candidato, criterio por criterio (punto 12). */
    PruebaDelCandidato prueba(ContextoUsuario quien, Long postulacionId);

    /**
     * Ajusta (o pone) la parte calificada de un criterio, de 0 a sus puntos y con motivo
     * (punto 9). La nota de la IA queda a la vista y la IA no vuelve a tocarla. Si con esto
     * la prueba queda entera, se escribe la nota de la etapa y pasa a «por confirmar».
     */
    PruebaDelCandidato ajustar(ContextoUsuario quien, Long postulacionId, Long criterioId,
                               AjustarCriterio datos);

    /** Quienes dejaron vencer el tiempo con algo sin responder (decisión 11). */
    List<NoCompleto> noCompletaron(ContextoUsuario quien, Long vacanteId);
}
