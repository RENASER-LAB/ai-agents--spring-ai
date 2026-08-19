package com.renaser.ai.ai_engine.ai.supabase;

import java.util.List;

public interface EntregableDataProvider {

    // Entregables pendientes de revisión, los más antiguos primero (Auditor)
    List<EntregableRecord> getEntregablesPendientes();
}
