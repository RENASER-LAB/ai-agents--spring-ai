package com.renaser.ai.ai_engine.ai.supabase;

public record ActividadRecord(
        String titulo,
        String estado,
        String prioridad,
        String bloqueo,
        Integer avance,
        String limite
) {}
