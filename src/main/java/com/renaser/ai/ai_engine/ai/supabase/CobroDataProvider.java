package com.renaser.ai.ai_engine.ai.supabase;

import java.util.List;

public interface CobroDataProvider {

    // Cobros de un cliente puntual, filtrados por nombre (entityId del agente Collections)
    List<CobroRecord> getCobrosByCliente(String cliente);
}
