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
    //
    // Apagado salvo que alguien lo encienda a propósito. Hasta el 21/08/2026 el valor por
    // defecto era «encendido», y como ningún entorno lo apagaba, el desplegado lo tenía
    // abierto: bastaba un identificador plausible para sacar un token con TALENTO,
    // DIRECCION y ADMINISTRADOR. Un valor por defecto inseguro solo protege a quien se
    // acuerda de cambiarlo, y el entorno que se olvida es siempre el que mira a internet.
    private boolean devLoginActivo = false;

    // ¿Se sirven Swagger y el api-docs sin identificarse?
    //
    // Mismo criterio: apagado salvo que se encienda. El api-docs son 122 KB que enseñan
    // cada ruta del sistema, y es el primer eslabón para encontrar el dev-login de arriba.
    // Se enciende en application-local.yaml, donde es una herramienta y no una filtración.
    private boolean documentacionPublica = false;
}
