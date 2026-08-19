package com.renaser.ai.ai_engine.ai.supabase;

// Agregado de las siete fuentes de datos de Supabase. Lo consume SupabaseController, que
// realmente necesita las siete. Cada AgentContextProvider debe depender del sub-interfaz
// puntual que usa (MotorDataProvider, CobroDataProvider, ...), no de este agregado completo.
public interface SupabaseDataService extends
        MotorDataProvider,
        CobroDataProvider,
        ActividadDataProvider,
        ProspectoDataProvider,
        EventoDataProvider,
        EntregableDataProvider,
        AvisoDataProvider {
}
