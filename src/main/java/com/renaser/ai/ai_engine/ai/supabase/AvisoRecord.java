package com.renaser.ai.ai_engine.ai.supabase;

public record AvisoRecord(
        String titulo,
        String detalle,
        String sev,
        String area,
        String responsable,
        Boolean leida
) {}
