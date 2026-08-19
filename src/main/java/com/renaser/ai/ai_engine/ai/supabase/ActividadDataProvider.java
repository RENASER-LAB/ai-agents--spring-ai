package com.renaser.ai.ai_engine.ai.supabase;

import java.util.List;

public interface ActividadDataProvider {

    // Actividades bloqueadas en toda la empresa (radar de cuellos de botella para Operations)
    List<ActividadRecord> getActividadesBloqueadas();
}
