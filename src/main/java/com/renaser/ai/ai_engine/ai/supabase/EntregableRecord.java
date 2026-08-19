package com.renaser.ai.ai_engine.ai.supabase;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EntregableRecord(
        String nombre,
        String tipo,
        String estado,
        String version,
        @JsonProperty("created_at") String createdAt
) {}
