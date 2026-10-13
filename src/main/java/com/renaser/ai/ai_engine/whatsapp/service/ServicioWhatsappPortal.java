package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.EnlaceWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.WhatsappDelProceso;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que el candidato hace con WhatsApp desde el portal (V73): ver en qué está cada proceso,
 * pulsar «Escribir por WhatsApp», «Dejar de recibir», y lo que pasa al cambiar el celular.
 */
public interface ServicioWhatsappPortal {

    /**
     * La tarjeta de cada una de estas postulaciones suyas, por id. De una vez para toda la lista:
     * «Mis procesos» la pide en cada visita.
     *
     * @param personaId    de quién son (todas son de la misma persona)
     * @param postulaciones las suyas que se van a pintar
     */
    Map<Long, WhatsappDelProceso> tarjetasDe(Long personaId, List<Postulacion> postulaciones);

    /**
     * «Escribir por WhatsApp»: guarda su consentimiento, anuncia su número al bot para esta
     * postulación y devuelve el enlace con el mensaje ya escrito.
     *
     * <p>409 ({@link WhatsappNoDisponibleException}) si la vacante no es de RENASER o la beta está
     * apagada, si el proceso terminó, si la cuenta no tiene celular ({@code SIN_CELULAR}) o si su
     * número se dio de baja. 404 si la postulación no es suya.
     */
    EnlaceWhatsapp escribir(ContextoUsuario quien, UUID postulacion, String ip, String userAgent);

    /** «Dejar de recibir»: cierra el vínculo de esta postulación y se lo cuenta al bot. */
    WhatsappDelProceso dejarDeRecibir(ContextoUsuario quien, UUID postulacion);

    /**
     * El celular de la cuenta cambió: lo vinculado o anunciado con el anterior se cierra y se
     * revoca. Lo llama «Mi cuenta», dentro de su transacción.
     */
    void alCambiarElCelular(Long personaId, String anterior, String nuevo);
}
