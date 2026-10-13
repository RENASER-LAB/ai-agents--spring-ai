package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Si una postulación puede usar WhatsApp: la beta (V73).
 *
 * <p>Tres condiciones, y faltando una no se anuncia, no se vincula y no se avisa:
 * <ol>
 *   <li>la vacante es de RENASER (la organización plataforma): durante la beta, solo ella;</li>
 *   <li>el interruptor {@value #PARAMETRO} de la plataforma está encendido;</li>
 *   <li>hay un bot detrás ({@code app.whatsapp.modo} no es «apagado»).</li>
 * </ol>
 * Los vínculos que ya están activos siguen recibiendo sus novedades si escriben: eso no lo
 * decide esto, sino que el número siga vinculado.
 */
@Component
@RequiredArgsConstructor
public class BetaWhatsapp {

    public static final String PARAMETRO = "whatsapp_novedades_activo";

    private final DuenoDelInstrumento duenos;
    private final ServicioParametros parametros;
    private final ClienteConversacion cliente;

    /** El interruptor y el bot, sin mirar la vacante. */
    public boolean encendida() {
        return cliente.estaEncendido()
                && parametros.booleano(duenos.plataforma().getId(), PARAMETRO, false);
    }

    /** ¿Es de RENASER? */
    public boolean esDeLaPlataforma(Long organizacionId) {
        return organizacionId != null && organizacionId.equals(duenos.plataforma().getId());
    }

    /** Las tres a la vez: la postulación de esta organización puede usar WhatsApp. */
    public boolean ofrecePara(Long organizacionId) {
        return esDeLaPlataforma(organizacionId) && encendida();
    }
}
