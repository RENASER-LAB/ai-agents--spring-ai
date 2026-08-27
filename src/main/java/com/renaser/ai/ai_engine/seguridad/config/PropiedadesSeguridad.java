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

    // El login de desarrollo del panel emite tokens de equipo sin contraseña. Apagado
    // por defecto y en todo entorno de verdad: solo lo encienden application-local.yaml
    // y las pruebas de integración, explícitamente. Estuvo encendido por defecto y eso
    // significaba que cualquier despliegue que olvidara apagarlo regalaba el panel.
    private boolean devLoginActivo = false;
}
