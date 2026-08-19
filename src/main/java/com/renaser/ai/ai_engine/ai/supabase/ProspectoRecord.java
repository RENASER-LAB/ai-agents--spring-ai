package com.renaser.ai.ai_engine.ai.supabase;

import java.math.BigDecimal;

public record ProspectoRecord(
        String id,
        String nombre,
        String etapa,
        String origen,
        BigDecimal valor,
        String responsable
) {}
