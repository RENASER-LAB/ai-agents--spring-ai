package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.Novedades;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.PeticionVinculo;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.Rechazo;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.Vinculo;

/**
 * Lo que el bot le pide a EX (V73): las tres rutas de {@code /api/v1/integraciones/whatsapp}.
 * Solo llegan aquí las llamadas con la firma del bot. Ver docs/CONTRATO-NOVEDADES-POR-WHATSAPP.md.
 */
public interface ServicioIntegracionWhatsapp {

    /**
     * Lo que contesta {@code POST /vinculos}: el vínculo (200) o el rechazo con su estado HTTP y
     * el texto que el bot debe contestar.
     */
    record ResultadoVinculo(int estadoHttp, Vinculo vinculo, Rechazo rechazo) {

        public static ResultadoVinculo vinculado(Vinculo vinculo) {
            return new ResultadoVinculo(200, vinculo, null);
        }

        public static ResultadoVinculo rechazado(int estadoHttp, String codigo, String texto) {
            return new ResultadoVinculo(estadoHttp, null, new Rechazo(codigo, texto));
        }
    }

    /**
     * Escribió desde un número anunciado: todas las postulaciones anunciadas para ese número
     * quedan vinculadas, diga lo que diga el mensaje. Es idempotente: devuelve todas las
     * postulaciones activas vinculadas a ese número después de la llamada (también las que ya lo
     * estaban) y sus novedades, y repetida devuelve lo mismo.
     *
     * @throws IllegalArgumentException si el teléfono no es E.164 (400)
     */
    ResultadoVinculo vincular(PeticionVinculo peticion);

    /**
     * Las novedades de cada proceso activo vinculado a ese número; la lista puede ir vacía.
     *
     * @throws IllegalArgumentException si el teléfono no es E.164 (400)
     */
    Novedades novedades(String telefono);

    /**
     * Escribió BAJA: todo lo de ese número se desvincula y el número queda suprimido.
     *
     * @return cuántos vínculos cerró (0 si no había nada: no es un error)
     * @throws IllegalArgumentException si el teléfono no es E.164 (400)
     */
    int baja(String telefono);
}
