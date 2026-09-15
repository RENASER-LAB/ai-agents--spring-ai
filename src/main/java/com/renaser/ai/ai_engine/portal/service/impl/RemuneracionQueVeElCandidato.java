package com.renaser.ai.ai_engine.portal.service.impl;

import com.renaser.ai.ai_engine.portal.dto.DtosPortal.Pretension;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.RemuneracionPublica;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.service.Remuneracion;

import java.math.BigDecimal;

/**
 * Traduce el sueldo de una vacante a lo que el portal enseña.
 *
 * <p>Vive en el portal y no en el dominio a propósito: {@code Remuneracion} conoce los
 * números y las reglas, y no tiene por qué conocer el contrato de esta pantalla. Este archivo
 * es la costura entre los dos, y es el único sitio donde una vacante se convierte en algo que
 * el candidato puede leer.
 *
 * <p>Lo usan los tres sitios que enseñan dinero al candidato —el tablón, el detalle de una
 * vacante y la lista de sus postulaciones— para que los tres digan exactamente lo mismo.
 */
final class RemuneracionQueVeElCandidato {

    private RemuneracionQueVeElCandidato() {}

    /**
     * Lo que paga esta vacante, tal como puede verse desde fuera.
     *
     * <p>Una vacante OCULTA devuelve la constante, no {@code null}: el portal tiene que poder
     * decir «no la publica» en voz alta. Un hueco donde debería ir el sueldo se lee como un
     * fallo de carga, y además es el dato que explica por qué el formulario de postular no le
     * va a exigir declarar el suyo.
     */
    static RemuneracionPublica de(Vacante vacante) {
        if (vacante == null || !Remuneracion.laEnsena(vacante)) {
            return RemuneracionPublica.OCULTA;
        }
        return new RemuneracionPublica(
                Remuneracion.tipoDe(vacante),
                vacante.getRemuneracionMin(),
                vacante.getRemuneracionMax(),
                vacante.getRemuneracionMoneda(),
                Remuneracion.escribir(vacante),
                // ⚠️ La marca de «actualizado el …» SOLO viaja si la vacante enseña el sueldo.
                // Una vacante que pasó de RANGO a OCULTA tiene la marca puesta, y mandarla
                // aquí haría que el portal dijera «actualizado el martes» junto a «no la
                // publica»: la fecha de un dato que ya no se puede ver.
                vacante.getRemuneracionActualizadaEn());
    }

    /**
     * Lo que ESTA persona dijo que quería ganar en esta postulación.
     *
     * <p>{@code null} cuando la vacante tenía el sueldo oculto —o cuando postuló antes de que
     * esto existiera—, y eso no es un hueco: es el trato cumpliéndose. Quien lo pinte tiene
     * que decirlo con esas palabras.
     */
    static Pretension pretensionDe(Postulacion postulacion) {
        BigDecimal monto = postulacion.getPretensionMonto();
        if (monto == null) {
            return null;
        }
        String moneda = postulacion.getPretensionMoneda();
        return new Pretension(monto, moneda, Remuneracion.escribirPretension(monto, moneda));
    }
}
