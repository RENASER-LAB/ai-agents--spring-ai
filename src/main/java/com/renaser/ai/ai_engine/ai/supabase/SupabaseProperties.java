package com.renaser.ai.ai_engine.ai.supabase;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "supabase")
@Data
public class SupabaseProperties {

    private String url;
    private String serviceRoleKey;
}
