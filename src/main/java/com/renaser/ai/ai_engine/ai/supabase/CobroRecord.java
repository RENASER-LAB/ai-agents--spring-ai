package com.renaser.ai.ai_engine.ai.supabase;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record CobroRecord(
        String id,
        String cliente,
        String concepto,
        String tipo,
        BigDecimal total,
        BigDecimal pagado,
        BigDecimal pendiente,
        @JsonProperty("cuota_actual") Integer cuotaActual,
        Integer cuotas,
        String vence,
        String estado
) {}
