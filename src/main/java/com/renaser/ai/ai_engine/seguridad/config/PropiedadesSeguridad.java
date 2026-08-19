package com.renaser.ai.ai_engine.seguridad.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.seguridad")
@Getter @Setter
public class PropiedadesSeguridad {

    // Clave HMAC para firmar los tokens. Vive en application-secrets.yaml o en una
    // variable de entorno; nunca en el repositorio. Mínimo 32 bytes.
    private String jwtSecreto;

    private int minutosTokenCandidato = 120;
    private int minutosTokenEquipo = 480;

    // El login de desarrollo del panel emite tokens de equipo sin RENASER OS.
    // En producción se apaga y la identidad del equipo viene de RENASER OS.
    private boolean devLoginActivo = true;
}
