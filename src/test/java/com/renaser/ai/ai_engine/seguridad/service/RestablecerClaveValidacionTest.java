package com.renaser.ai.ai_engine.seguridad.service;

import com.renaser.ai.ai_engine.portal.dto.DtosPortal;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las reglas de la contraseña nueva, en la puerta: se rechazan antes de tocar el enlace, así
 * que un error aquí nunca lo gasta (AC-11). Ocho en el portal y doce en el panel, las mismas
 * que al registrarse y al aceptar una invitación; y sin espacios en los bordes, que se avisa
 * en vez de recortarse en silencio.
 */
@DisplayName("Las reglas de la contraseña nueva")
class RestablecerClaveValidacionTest {

    private static jakarta.validation.ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void armar() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrar() {
        fabrica.close();
    }

    private static <T> Set<String> mensajes(T datos) {
        return validador.validate(datos).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("AC-11 · portal: menos de 8 no pasa; 8 sí")
    void portalOcho() {
        assertThat(mensajes(new DtosPortal.RestablecerClave("t", "corta12")))
                .contains("La contraseña necesita al menos 8 caracteres");
        assertThat(mensajes(new DtosPortal.RestablecerClave("t", "ocho1234"))).isEmpty();
    }

    @Test
    @DisplayName("AC-11 · panel: menos de 12 no pasa; 12 sí")
    void panelDoce() {
        assertThat(mensajes(new DtosSeguridad.RestablecerClave("t", "once1234567")))
                .contains("La contraseña necesita al menos 12 caracteres");
        assertThat(mensajes(new DtosSeguridad.RestablecerClave("t", "doce12345678"))).isEmpty();
    }

    @Test
    @DisplayName("un espacio al principio o al final se rechaza y se dice; uno en medio vale")
    void espaciosEnLosBordes() {
        String mensaje = "La contraseña no puede empezar ni terminar con un espacio";
        assertThat(mensajes(new DtosPortal.RestablecerClave("t", " una-clave-larga"))).contains(mensaje);
        assertThat(mensajes(new DtosPortal.RestablecerClave("t", "una-clave-larga "))).contains(mensaje);
        assertThat(mensajes(new DtosPortal.RestablecerClave("t", "una-clave-larga\n"))).contains(mensaje);
        assertThat(mensajes(new DtosSeguridad.RestablecerClave("t", "\tuna-clave-de-panel"))).contains(mensaje);
        assertThat(mensajes(new DtosPortal.RestablecerClave("t", "una clave larga"))).isEmpty();
    }

    @Test
    @DisplayName("F-01 · más de 72 bytes no pasa, con un motivo en español; se cuentan bytes, no letras")
    void elTopeDeBcrypt() {
        String mensaje = "La contraseña es demasiado larga. Usa como máximo 72 caracteres; "
                + "las letras con tilde, la ñ y los emojis cuentan por más de uno.";
        // 72 bytes justos pasan, en las dos puertas
        assertThat(mensajes(new DtosPortal.RestablecerClave("t", "x".repeat(72)))).isEmpty();
        assertThat(mensajes(new DtosPortal.RestablecerClave("t", "ñ".repeat(36)))).isEmpty();
        assertThat(mensajes(new DtosSeguridad.RestablecerClave("t", "x".repeat(72)))).isEmpty();
        // Uno más, no
        assertThat(mensajes(new DtosPortal.RestablecerClave("t", "x".repeat(73)))).containsExactly(mensaje);
        assertThat(mensajes(new DtosSeguridad.RestablecerClave("t", "x".repeat(73)))).containsExactly(mensaje);
        // 40 «ñ» son 40 letras y 80 bytes: pasa el mínimo y no cabe
        assertThat(mensajes(new DtosPortal.RestablecerClave("t", "ñ".repeat(40)))).containsExactly(mensaje);
        assertThat(mensajes(new DtosSeguridad.RestablecerClave("t", "ñ".repeat(40)))).containsExactly(mensaje);
        // 19 emojis: 38 posiciones en Java y 76 bytes
        assertThat(mensajes(new DtosPortal.RestablecerClave("t", "🔑".repeat(19)))).containsExactly(mensaje);
        assertThat(mensaje).doesNotContainIgnoringCase("bytes");
    }

    @Test
    @DisplayName("vacía no pasa, y un token vacío no es cosa de la validación: es un 401")
    void vaciaYTokenVacio() {
        assertThat(mensajes(new DtosPortal.RestablecerClave("t", ""))).contains("Escribe la contraseña nueva");
        assertThat(mensajes(new DtosPortal.RestablecerClave(null, "ocho1234"))).isEmpty();
        assertThat(mensajes(new DtosSeguridad.RestablecerClave("", "doce12345678"))).isEmpty();
    }
}
