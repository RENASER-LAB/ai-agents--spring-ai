package com.renaser.ai.ai_engine.whatsapp.service.impl;

import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion;

/**
 * El modo apagado: no hay bot. Nada se llama y el portal no ofrece el botón.
 *
 * <p>Si algo llegara a pedir un envío igualmente —un aviso encolado antes de apagarlo—, falla
 * como definitivo: no hay a quién reintentárselo.
 */
public class ClienteConversacionApagado implements ClienteConversacion {

    private static final String SIN_BOT = "El bot de WhatsApp está apagado (app.whatsapp.modo)";

    @Override
    public void anunciar(Espera espera) {
        throw new FalloDelBot(SIN_BOT, false);
    }

    @Override
    public void avisar(Aviso aviso) {
        throw new FalloDelBot(SIN_BOT, false);
    }

    @Override
    public void revocar(Revocacion revocacion) {
        throw new FalloDelBot(SIN_BOT, false);
    }

    @Override
    public boolean estaEncendido() {
        return false;
    }
}
