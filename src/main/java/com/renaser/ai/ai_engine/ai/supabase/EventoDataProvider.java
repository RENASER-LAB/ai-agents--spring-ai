package com.renaser.ai.ai_engine.ai.supabase;

import java.util.List;

public interface EventoDataProvider {

    // Un evento puntual, filtrado por nombre (entityId del agente Event)
    List<EventoRecord> getEventoByNombre(String nombre);
}
