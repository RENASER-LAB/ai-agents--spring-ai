package com.renaser.ai.ai_engine.seguridad.service;

import com.renaser.ai.ai_engine.seguridad.config.PropiedadesSeguridad;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;

// Emite y valida los tokens con las piezas de Nimbus que ya trae el classpath
// (oauth2-resource-server). Sin librerías nuevas: no se añade jjwt.
@Service
public class ServicioToken {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final PropiedadesSeguridad propiedades;

    public ServicioToken(JwtEncoder encoder, JwtDecoder decoder, PropiedadesSeguridad propiedades) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.propiedades = propiedades;
    }

    public String emitir(Long usuarioId, Long organizacionId, String tipo) {
        int minutos = "CANDIDATO".equals(tipo)
                ? propiedades.getMinutosTokenCandidato()
                : propiedades.getMinutosTokenEquipo();
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("renaser-seleccion")
                .subject(String.valueOf(usuarioId))
                .claim("org", organizacionId)
                .claim("tipo", tipo)
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(minutos * 60L))
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }

    // Devuelve los claims si el token es válido y no venció; si no, lanza JwtException
    public Jwt validar(String token) {
        return decoder.decode(token);
    }
}
