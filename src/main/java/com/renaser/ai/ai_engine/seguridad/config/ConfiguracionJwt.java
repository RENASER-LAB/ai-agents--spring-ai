package com.renaser.ai.ai_engine.seguridad.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

// Los beans del JWT viven APARTE de las cadenas de seguridad: si estuvieran juntos
// habría un ciclo (cadenas -> filtro -> ServicioToken -> beans de aquí).
@Configuration
@RequiredArgsConstructor
public class ConfiguracionJwt {

    private final PropiedadesSeguridad propiedades;

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveHmac()));
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(claveHmac())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private SecretKey claveHmac() {
        String secreto = propiedades.getJwtSecreto();
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
            // Fallar al arrancar, no al primer login: una clave corta es una clave rota
            throw new IllegalStateException("""
                    Falta app.seguridad.jwt-secreto (mínimo 32 bytes). \
                    Genera una con `openssl rand -base64 48` y ponla en application-secrets.yaml \
                    (hay plantilla en application-secrets.yaml.example)""");
        }
        return new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
