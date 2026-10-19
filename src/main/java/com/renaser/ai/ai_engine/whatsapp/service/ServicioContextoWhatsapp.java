package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Contexto;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosContextoWhatsapp.Listado;

/**
 * La consulta v2 del bot (V76): {@code POST /api/v2/integraciones/whatsapp/contexto}, firmada como
 * el resto de la integración. Ver el contrato v2, C-1 a C-3.
 *
 * <p>Solo lee: no cambia estados, intentos, relojes ni consentimiento. Cada consulta vuelve a
 * comprobar número, vínculo, postulación, organización y vigencia; una referencia escrita en el
 * chat no concede nada.
 */
public interface ServicioContextoWhatsapp {

    /**
     * Las postulaciones consultables de ese número, de 9 en 9 y las más recientes primero: las de
     * vínculo ACTIVO en carrera y las terminadas con vínculo activo al terminar, durante 30 días.
     * La lista vacía es «sin procesos consultables», no un descarte.
     *
     * @param cursor nulo para la primera página
     * @throws IllegalArgumentException      si el teléfono no es E.164 (400 VALIDACION)
     * @throws SinAccesoWhatsappException si el cursor no es de ese número o no es un cursor (404)
     */
    Listado listar(String telefono, String cursor);

    /**
     * El contexto autorizado de una postulación de ese número: la ficha pública, el procedimiento,
     * en qué va, qué le toca y su resultado.
     *
     * @throws IllegalArgumentException      si el teléfono no es E.164 (400 VALIDACION)
     * @throws SinAccesoWhatsappException si la referencia no existe, es ajena o ya no se puede
     *                                       consultar (404, el mismo en los tres casos)
     */
    Contexto detalle(String telefono, String postulacionRef);
}
