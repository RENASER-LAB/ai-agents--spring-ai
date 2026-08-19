package com.renaser.ai.ai_engine.ai.supabase;

import java.math.BigDecimal;

public record EventoRecord(
        String id,
        String nombre,
        BigDecimal ingresos,
        BigDecimal egresos
) {}
