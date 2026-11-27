package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.CambioDePesosHecho;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.CambioDePesosPrevisto;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.PesoEnEstaVacante;

import java.util.List;

/**
 * Qué versión de pesos rige la decisión de una vacante, y cambiarla con candidatos dentro
 * (V74, specs/pesos-con-candidatos-y-fin-del-prestamo.md, parte A).
 *
 * <p>Hasta la V74 la versión no se cambiaba desde la primera postulación: cambiarla solo
 * movía las notas futuras y dejaba a unos medidos con una vara y a otros con otra. Ahora se
 * cambia y <b>se recalcula a todos a la vez</b>, sin IA y sin mover a nadie de etapa: la vara
 * cambia, pero para todos (docs/DECISION-UNA-VACANTE-UNA-VERSION.md).
 */
public interface ServicioPesosDeLaVacante {

    /**
     * Lo que pasaría al cambiar a esa versión: cuántas personas se recalcularían y qué pesos
     * cambian. No escribe nada. Es lo que enseña la confirmación del panel.
     */
    CambioDePesosPrevisto preverCambio(ContextoUsuario quien, Long vacanteId, Long versionPesosId);

    /**
     * Pone la vacante en esa versión y recalcula en la misma operación a todos sus candidatos
     * con nota. Si algo falla a mitad no se guarda nada. Elegir la que ya tiene no hace nada.
     *
     * <p>409 con la vacante cerrada, archivada o eliminada, y con una versión en borrador;
     * 404 con una versión que no es de la empresa ni de la plataforma que le presta.
     */
    CambioDePesosHecho cambiar(ContextoUsuario quien, Long vacanteId, Long versionPesosId);

    /**
     * «Cómo pesa en esta vacante» (V95): por cada etapa con peso en la versión, su peso en la
     * versión y el repartido entre las etapas del recorrido; las que la vacante no tiene, sin el
     * segundo. Ver {@link com.renaser.ai.ai_engine.vacante.service.PesosDelRecorrido}. Vacía si la
     * versión no tiene pesos de etapa. Quien llama ya comprobó que puede ver la vacante.
     */
    List<PesoEnEstaVacante> enEstaVacante(Long vacanteId, Long versionPesosId);
}
